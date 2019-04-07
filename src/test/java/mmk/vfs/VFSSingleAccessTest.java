package mmk.vfs;

import mmk.vfs.directories.DirectoryHandlerFactory;
import mmk.vfs.directories.DirectoryHandlerFactoryV1;
import mmk.vfs.exceptions.FileAlreadyExistsException;
import mmk.vfs.impl.VirtualFileSystemImpl;
import mmk.vfs.storage.blocks.BlockStorageManager;
import mmk.vfs.storage.InMemoryBlockStorageManager;
import mmk.vfs.storage.file.StorageFileManager;
import mmk.vfs.storage.file.StorageFileManagerV1;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class VFSSingleAccessTest {
    static final int BLOCK_SIZE = 256;

    private VirtualFileSystem createVirtualFileSystem() throws IOException {
        return createVirtualFileSystem(BLOCK_SIZE);
    }

    private VirtualFileSystem createVirtualFileSystem(int blockReadLimit) throws IOException {
        BlockStorageManager blockStorageManager = new InMemoryBlockStorageManager(256, blockReadLimit);
        StorageFileManager containerStorage = new StorageFileManagerV1(blockStorageManager);
        DirectoryHandlerFactory directoryHandlerFactory = new DirectoryHandlerFactoryV1();
        return new VirtualFileSystemImpl(containerStorage, directoryHandlerFactory);
    }

    @Test
    public void testVFSCreateManyFiles() throws IOException {
        byte[] someBytes = new byte[]{0x12, 0x34, 0x56, 0x78, (byte) 0x9A};

        try (VirtualFileSystem vfs = createVirtualFileSystem()) {
            Assert.assertTrue("Root file must always exist", vfs.exists("/"));

            int filesToWrite = 4 * BLOCK_SIZE / 64;
            for (int i = 0; i < filesToWrite; ++i) {
                String fileName = Integer.toHexString(i);
                vfs.createFile(fileName);
                try (VFSFile file = vfs.openFile(fileName, FileOpenMode.READ_WRITE)) {
                    file.write(someBytes, 0, someBytes.length);
                }
            }
        }
    }

    @Test
    public void testVFSCreateWriteReadFile() throws IOException {
        testVFSCreateWriteReadFile(BLOCK_SIZE, 1024);
    }

    @Test
    public void testVFSCreateWriteReadFileWithLimitedReads() throws IOException {
        testVFSCreateWriteReadFile(25, 1024);
    }

    @Test
    public void testVFSCreateWriteReadFileWithVeryLimitedReads() throws IOException {
        testVFSCreateWriteReadFile(1, 32);
    }

    public void testVFSCreateWriteReadFile(int blockReadLimit, int bytesToWrite) throws IOException {
        String filename = "file.ext";
        byte[] byteSequence = new byte[bytesToWrite];
        for (int i = 0; i < bytesToWrite; ++i) {
            byteSequence[i] = (byte) i;
        }

        try (VirtualFileSystem vfs = createVirtualFileSystem(blockReadLimit)) {
            Assert.assertTrue("Root file must always exist", vfs.exists("/"));

            Assert.assertFalse("Must not contain file on new VFS", vfs.exists(filename));
            vfs.createFile(filename);
            Assert.assertTrue("File must exist after creation", vfs.exists(filename));

            VFSFile file = vfs.openFile(filename, FileOpenMode.READ_WRITE);
            Assert.assertNotNull("File should be opened successfully", file);
            Assert.assertEquals(filename, file.getName());
            Assert.assertEquals("File length should be zero after creation", 0, file.getLength());

            file.write(byteSequence, 0, bytesToWrite);
            file.close();

            file = vfs.openFile(filename, FileOpenMode.READ);
            Assert.assertEquals("File size must match write length", bytesToWrite, file.getLength());

            try {
                file.write(byteSequence, 0, bytesToWrite);
                Assert.fail("Write to read-opened file not failed, but it must");
            } catch (IOException ignored) {
                // expected exception: can't write to read-opened file
            }

            byte[] readBuffer = new byte[100];
            ByteArrayOutputStream readAssembler = new ByteArrayOutputStream();
            int read;
            while ((read = file.read(readBuffer, 0, readBuffer.length)) != -1) {
                readAssembler.write(readBuffer, 0, read);
            }

            Assert.assertEquals("Read length must match write length", bytesToWrite, readAssembler.size());
            Assert.assertArrayEquals("File contents in read must match bytes in write", byteSequence, readAssembler.toByteArray());

            file.close();
        }
    }

    @Test
    public void testVFSCreateRemoveDirectory() throws IOException {
        String dirName = "directory";
        String filename = "file.ext";
        String filePath = dirName + "/" + filename;

        try (VirtualFileSystem vfs = createVirtualFileSystem()) {
            System.out.println("VFS Created");
            Assert.assertTrue("Root file must always exist", vfs.exists("/"));

            Assert.assertFalse("Must not contain directory on new VFS", vfs.exists(dirName));
            vfs.createDir(dirName);
            Assert.assertTrue("Directory must exist after creation", vfs.exists(dirName));

            Assert.assertFalse("File should not exist in new directory", vfs.exists(filePath));
            vfs.createFile(filePath);
            Assert.assertTrue("File should exist after creation", vfs.exists(filePath));

            try (VFSDirectory directory = vfs.openDir(dirName)) {
                for (VFSDirectory.DirEntry entry : directory.getAllChilds()) {
                    System.out.println(entry.getName());
                }
            }

            try {
                vfs.delete(dirName);
                Assert.fail("Directory could not be deletable when it contains file");
            } catch (IOException ignored) {
            }

            Assert.assertTrue("File should still exist in directory after failed delete", vfs.exists(filePath));
            vfs.delete(filePath);
            Assert.assertFalse("File should no longer exist after it is deleted", vfs.exists(filePath));

            vfs.delete(dirName);
            Assert.assertFalse("Directory should no longer exist after it is deleted", vfs.exists(dirName));
        }
    }

    @Test(expected = FileNotFoundException.class)
    public void testVFSOpenMissingFile() throws IOException {
        String filename = "missing.file";

        try (VirtualFileSystem vfs = createVirtualFileSystem()) {
            Assert.assertTrue("Root file must always exist", vfs.exists("/"));
            Assert.assertFalse("Must not contain file on new VFS", vfs.exists(filename));
            try (VFSFile file = vfs.openFile(filename, FileOpenMode.READ)) {
                file.getLength();
            }
        }
    }

    @Test(expected = FileAlreadyExistsException.class)
    public void testFileCreateTwice() throws IOException {
        String filename = "file.ext";

        try (VirtualFileSystem vfs = createVirtualFileSystem()) {
            vfs.createFile(filename);
            vfs.createFile(filename);
        }
    }

    @Test
    public void testFileSeek() throws IOException {
        String filename = "file.ext";

        int offset = 8;
        byte[] write = new byte[24];
        for (int i = 0; i < write.length; ++i) {
            write[i] = (byte) (i + 1);
        }
        byte[] expectation = new byte[write.length + offset];
        System.arraycopy(write, 0, expectation, offset, write.length);

        try (VirtualFileSystem vfs = createVirtualFileSystem()) {
            vfs.createFile(filename);
            VFSFile file = vfs.openFile(filename, FileOpenMode.READ_WRITE);
            file.seek(offset);
            file.write(write, 0, write.length);
            file.seek(0);

            byte[] readBuffer = new byte[100];
            ByteArrayOutputStream readAssembler = new ByteArrayOutputStream();
            int read;
            while ((read = file.read(readBuffer, 0, readBuffer.length)) != -1) {
                readAssembler.write(readBuffer, 0, read);
            }

            Assert.assertEquals(expectation.length, readAssembler.size());
            Assert.assertArrayEquals(expectation, readAssembler.toByteArray());
        }
    }
}
