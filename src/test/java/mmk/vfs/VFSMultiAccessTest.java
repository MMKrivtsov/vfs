package mmk.vfs;

import mmk.vfs.directories.DirectoryHandlerFactory;
import mmk.vfs.directories.DirectoryHandlerFactoryV1;
import mmk.vfs.exceptions.FileAlreadyOpenException;
import mmk.vfs.impl.VirtualFileSystemImpl;
import mmk.vfs.storage.blocks.BlockStorageManager;
import mmk.vfs.storage.InMemoryBlockStorageManager;
import mmk.vfs.storage.file.StorageFileManager;
import mmk.vfs.storage.file.StorageFileManagerV1;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class VFSMultiAccessTest {
    static final int BLOCK_SIZE = 256;

    private VirtualFileSystem createVirtualFileSystem() throws IOException {
        BlockStorageManager blockStorageManager = new InMemoryBlockStorageManager(BLOCK_SIZE, BLOCK_SIZE);
        StorageFileManager containerStorage = new StorageFileManagerV1(blockStorageManager);
        DirectoryHandlerFactory directoryHandlerFactory = new DirectoryHandlerFactoryV1();
        return new VirtualFileSystemImpl(containerStorage, directoryHandlerFactory);
    }

    @Test
    public void testConcurrentReadAccess() throws IOException {
        String filename = "file.ext";

        int bytesToWrite = 64;
        byte[] byteSequence = new byte[bytesToWrite];
        for (int i = 0; i < bytesToWrite; ++i) {
            byteSequence[i] = (byte) i;
        }

        try (VirtualFileSystem vfs = createVirtualFileSystem()) {
            vfs.createFile(filename);

            try (VFSFile file = vfs.openFile(filename, FileOpenMode.READ_WRITE)) {
                file.write(byteSequence, 0, byteSequence.length);
            }
            try (VFSFile file1 = vfs.openFile(filename, FileOpenMode.READ); VFSFile file2 = vfs.openFile(filename, FileOpenMode.READ)) {
                int read;
                byte[] readBuffer = new byte[100];

                ByteArrayOutputStream[] readAssemblers = new ByteArrayOutputStream[]{new ByteArrayOutputStream(), new ByteArrayOutputStream()};
                VFSFile[] files = new VFSFile[]{file1, file2};
                boolean[] complete = new boolean[]{false, false};

                while (!complete[0] || !complete[1]) {
                    for (int i = 0; i < 2; ++i) {
                        if (!complete[i]) {
                            read = files[i].read(readBuffer, 0, readBuffer.length);
                            if (read == -1) {
                                complete[i] = true;
                            }
                            else {
                                readAssemblers[i].write(readBuffer, 0, read);
                            }
                        }
                    }
                }

                Assert.assertEquals("Read length must match write length", bytesToWrite, readAssemblers[0].size());
                Assert.assertEquals("Read length must match write length", bytesToWrite, readAssemblers[1].size());
                Assert.assertArrayEquals("File contents in read must match bytes in write", byteSequence, readAssemblers[0].toByteArray());
                Assert.assertArrayEquals("File contents in read must match bytes in write", byteSequence, readAssemblers[1].toByteArray());

            }
        }
    }

    @Test(expected = FileAlreadyOpenException.class)
    public void testConcurrentWriteAccess() throws IOException {
        String fileName = "file.ext";

        try (VirtualFileSystem vfs = createVirtualFileSystem()) {
            vfs.createFile(fileName);
            VFSFile file1 = vfs.openFile(fileName, FileOpenMode.READ_WRITE);
            Assert.assertNotNull(file1);
            VFSFile file2 = vfs.openFile(fileName, FileOpenMode.READ_WRITE);
            Assert.fail("Should fail opening same file second time");
        }
    }

    @Test
    public void testConcurrentDirectoryAccess() throws IOException {
        String dirName = "directory";
        String fileName = "file.ext";
        String filePath = dirName + "/" + fileName;

        try (VirtualFileSystem vfs = createVirtualFileSystem()) {
            vfs.createDir(dirName);

            VFSDirectory dir = vfs.openDir(dirName);
            Assert.assertNotNull(dir);
            Assert.assertEquals(0, dir.getAllChilds().length);

            vfs.createFile(filePath);

            Assert.assertEquals(1, dir.getAllChilds().length);

            vfs.delete(filePath);
        }
    }

    @Test
    public void testDirectoryReadWhileUpdates() throws IOException {
        String dirName = "directory";
        String fileName = "file.ext";
        String filePath = dirName + "/" + fileName;

        int bytesToWrite = 64;
        byte[] byteSequence = new byte[bytesToWrite];
        for (int i = 0; i < bytesToWrite; ++i) {
            byteSequence[i] = (byte) i;
        }

        try (VirtualFileSystem vfs = createVirtualFileSystem()) {
            vfs.createDir(dirName);
            vfs.createFile(filePath);
            try (VFSDirectory dir = vfs.openDir(dirName)) {
                Assert.assertNotNull(dir);
                try (VFSFile file = vfs.openFile(filePath, FileOpenMode.READ_WRITE)) {
                    Assert.assertEquals(1, dir.getAllChilds().length);
                    Assert.assertEquals(0, file.getLength());

                    file.write(byteSequence, 0, byteSequence.length);

                    Assert.assertEquals(1, dir.getAllChilds().length);

                    Assert.assertEquals(byteSequence.length, file.getLength());
                }
                Assert.assertEquals(1, dir.getAllChilds().length);
            }
        }
    }
}
