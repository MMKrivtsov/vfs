package mmk.vfs.directories;

import mmk.vfs.VirtualFileSystem;
import mmk.vfs.impl.VirtualFileSystemImpl;
import mmk.vfs.locks.LockType;
import mmk.vfs.storage.InMemoryBlockStorageManager;
import mmk.vfs.storage.blocks.BlockStorageManager;
import mmk.vfs.storage.file.StorageFile;
import mmk.vfs.storage.file.StorageFileManager;
import mmk.vfs.storage.file.StorageFileManagerV1;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class DirectoryHandlerV1Test {
    private VirtualFileSystem createVirtualFileSystem() throws IOException {
        int blockSize = 256;
        BlockStorageManager blockStorageManager = new InMemoryBlockStorageManager(blockSize, blockSize);
        StorageFileManager containerStorage = new StorageFileManagerV1(blockStorageManager);
        DirectoryHandlerFactory directoryHandlerFactory = new DirectoryHandlerFactoryV1();
        return new VirtualFileSystemImpl(containerStorage, directoryHandlerFactory);
    }

    @Test
    public void testVFSFileNameMaxLength() throws IOException {
        String filename24chars = "123456789ABCDEFGHIJKLMNO";
        try (VirtualFileSystem vfs = createVirtualFileSystem()) {
            vfs.createFile(filename24chars);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVFSFileNameLengthMoreThanLimit() throws IOException {
        String filename25chars = "123456789ABCDEFGHIJKLMNOP";
        try (VirtualFileSystem vfs = createVirtualFileSystem()) {
            vfs.createFile(filename25chars);
        }
    }

    @Test
    public void testVFSDirectoryWriteRead() throws IOException {
        int file0Length = 1234;
        int file0Storage = 1;
        String file0Name = "file1.ext";

        int file1Length = 2345;
        int file1Storage = 2;
        String file1Name = "file2.ext";

        int file2Length = 3456;
        int file2Storage = 3;
        String file2Name = "file3.ext";

        int file3Length = 4567;
        int file3Storage = 4;

        DirectoryHandlerFactory directoryHandlerFactory = new DirectoryHandlerFactoryV1();
        StorageFile storage = new TestStorageFile();
        try (DirectoryHandler directoryHandler = directoryHandlerFactory.createNewHandler(storage)) {
            Assert.assertNull(directoryHandler.readNextEntry());

            DirectoryEntry entry = new DirectoryEntry();

            entry.setFileType(DirectoryEntryType.FILE);
            entry.setFileLength(file0Length);
            entry.setStorageStartIdx(file0Storage);
            entry.setEntryName(file0Name);
            directoryHandler.addEntry(entry);

            entry.setFileType(DirectoryEntryType.DIRECTORY);
            entry.setFileLength(file1Length);
            entry.setStorageStartIdx(file1Storage);
            entry.setEntryName(file1Name);
            directoryHandler.addEntry(entry);

            entry.setFileType(DirectoryEntryType.FILE);
            entry.setFileLength(file2Length);
            entry.setStorageStartIdx(file2Storage);
            entry.setEntryName(file2Name);
            directoryHandler.addEntry(entry);

            entry = directoryHandler.readEntry(0);
            Assert.assertEquals(DirectoryEntryType.FILE, entry.getFileType());
            Assert.assertEquals(file0Length, entry.getFileLength());
            Assert.assertEquals(file0Storage, entry.getStorageStartIdx());
            Assert.assertEquals(file0Name, entry.getEntryName());
            Assert.assertEquals(0, entry.getParentDirectoryIndex());

            entry = directoryHandler.readEntry(1);
            Assert.assertEquals(DirectoryEntryType.DIRECTORY, entry.getFileType());
            Assert.assertEquals(0, entry.getFileLength());
            Assert.assertEquals(file1Storage, entry.getStorageStartIdx());
            Assert.assertEquals(file1Name, entry.getEntryName());
            Assert.assertEquals(1, entry.getParentDirectoryIndex());

            entry = directoryHandler.readEntry(2);
            Assert.assertEquals(DirectoryEntryType.FILE, entry.getFileType());
            Assert.assertEquals(file2Length, entry.getFileLength());
            Assert.assertEquals(file2Storage, entry.getStorageStartIdx());
            Assert.assertEquals(file2Name, entry.getEntryName());
            Assert.assertEquals(2, entry.getParentDirectoryIndex());

            entry.setStorageStartIdx(file3Storage);
            entry.setFileLength(file3Length);
            directoryHandler.updateEntry(entry);

            entry = directoryHandler.readEntry(2);
            Assert.assertEquals(DirectoryEntryType.FILE, entry.getFileType());
            Assert.assertEquals(file3Length, entry.getFileLength());
            Assert.assertEquals(file3Storage, entry.getStorageStartIdx());
            Assert.assertEquals(file2Name, entry.getEntryName());
            Assert.assertEquals(2, entry.getParentDirectoryIndex());

            directoryHandler.removeEntry(0);
            entry = directoryHandler.readEntry(0);
            Assert.assertEquals(DirectoryEntryType.EMPTY, entry.getFileType());
        }
    }

    class TestStorageFile implements StorageFile {
        byte[] mContents = new byte[0];

        @Override
        public int getStorageStartIdx() {
            return 1;
        }

        @Override
        public void claimLock(LockType lockType) {
        }

        @Override
        public void releaseLock() {
        }

        @Override
        public int getCurrentCapacity() {
            return mContents.length;
        }

        @Override
        public int readBlock(int fileOffset, byte[] readBuffer, int bufferOffset, int length) throws IOException {
            if (mContents == null) throw new IOException("Already closed");

            if (fileOffset >= mContents.length) {
                return -1;
            }

            length = Math.min(length, mContents.length - fileOffset);
            System.arraycopy(mContents, fileOffset, readBuffer, bufferOffset, length);

            return length;
        }

        @Override
        public void writeBlock(int fileOffset, byte[] writeBuffer, int bufferOffset, int length) throws IOException {
            if (mContents == null) throw new IOException("Already closed");
            int end = fileOffset + length;
            if (end > mContents.length) {
                byte[] newContents = new byte[end];
                System.arraycopy(mContents, 0, newContents, 0, mContents.length);
                mContents = newContents;
            }

            System.arraycopy(writeBuffer, bufferOffset, mContents, fileOffset, length);
        }

        @Override
        public void close() {
            mContents = null;
        }
    }
}
