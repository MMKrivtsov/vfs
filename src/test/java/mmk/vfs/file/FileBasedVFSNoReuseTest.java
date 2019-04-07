/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package mmk.vfs.file;

import mmk.vfs.VirtualFileSystem;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;

public class FileBasedVFSNoReuseTest {
    private File getNewStorageFile() {
        File mTestFile = new File("test.vfs");
        if (mTestFile.exists()) {
            Assert.assertTrue("Previous storage file must be file, not directory", mTestFile.isFile());
            Assert.assertTrue("Previous storage file must be removed", mTestFile.delete());
        }
        return mTestFile;
    }

    @Test
    public void testVFSCreation() throws IOException {
        try (VirtualFileSystem vfs = FileBasedVirtualFileSystem.open(getNewStorageFile())) {
            Assert.assertTrue("Root file must always exist", vfs.exists("/"));
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testVFSCreationTwice() throws IOException {
        try (VirtualFileSystem vfs1 = FileBasedVirtualFileSystem.open(getNewStorageFile());
             VirtualFileSystem vfs2 = FileBasedVirtualFileSystem.open(getNewStorageFile())) {
            Assert.assertTrue("Root file must always exist", vfs1.exists("/"));
            Assert.assertTrue("Root file must always exist", vfs2.exists("/"));
        }
    }
}