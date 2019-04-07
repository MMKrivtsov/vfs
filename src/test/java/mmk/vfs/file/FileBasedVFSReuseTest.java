package mmk.vfs.file;

import mmk.vfs.FileOpenMode;
import mmk.vfs.VFSFile;
import mmk.vfs.VirtualFileSystem;
import mmk.vfs.file.FileBasedVirtualFileSystem;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public class FileBasedVFSReuseTest {
    private File getNewStorageFile() {
        File mTestFile = new File("test.vfs");
        if (mTestFile.exists()) {
            Assert.assertTrue("Previous storage file must be file, not directory", mTestFile.isFile());
            Assert.assertTrue("Previous storage file must be removed", mTestFile.delete());
        }
        return mTestFile;
    }

    @Test
    public void testVFSWriteAndSeparateRead() throws IOException {
        File storageFile = getNewStorageFile();
        String dirName = "directory";
        String fileName = "file.ext";
        String filePath = dirName + "/" + fileName;

        int bytesToWrite = 1000;
        byte[] byteSequence = new byte[bytesToWrite];
        for (int i = 0; i < bytesToWrite; ++i) {
            byteSequence[i] = (byte) i;
        }

        try (VirtualFileSystem vfs = FileBasedVirtualFileSystem.open(storageFile)) {
            Assert.assertTrue("Root file must always exist", vfs.exists("/"));
            vfs.createDir(dirName);
            vfs.createFile(filePath);
            try (VFSFile file = vfs.openFile(filePath, FileOpenMode.READ_WRITE)) {
                file.write(byteSequence, 0, bytesToWrite);
            }
        }

        try (VirtualFileSystem vfs = FileBasedVirtualFileSystem.open(storageFile)) {
            Assert.assertTrue("Root file must always exist", vfs.exists("/"));
            try (VFSFile file = vfs.openFile(filePath, FileOpenMode.READ)) {
                Assert.assertEquals("File size must match write length", bytesToWrite, file.getLength());

                try {
                    file.write(byteSequence, 0, bytesToWrite);
                    Assert.fail("Write to read-opened file not failed, but it must");
                } catch (IOException ignored) {
                }

                byte[] readBuffer = new byte[100];
                ByteArrayOutputStream readAssembler = new ByteArrayOutputStream();
                int read;
                while ((read = file.read(readBuffer, 0, readBuffer.length)) != -1) {
                    readAssembler.write(readBuffer, 0, read);
                }
                Assert.assertEquals("Read length must match write length", bytesToWrite, readAssembler.size());
                Assert.assertArrayEquals("File contents in read must match bytes in write", byteSequence, readAssembler.toByteArray());
            }
        }
    }
}
