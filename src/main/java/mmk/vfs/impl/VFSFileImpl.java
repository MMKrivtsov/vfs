package mmk.vfs.impl;

import mmk.vfs.VFSFile;
import mmk.vfs.directories.DirectoryEntry;
import mmk.vfs.directories.DirectoryHandler;
import mmk.vfs.storage.file.StorageFile;

import java.io.EOFException;
import java.io.IOException;

public class VFSFileImpl extends VFSEntryImpl implements VFSFile {
    private int mCurrentFileOffset = 0;
    private StorageFile mStorageFile = null;
    private int mCurrentFileLength = -1;

    public VFSFileImpl(String fileName, String filePath, VirtualFileSystemImpl vfs, int parentDirectoryFileIdx, int parentDirectoryOffset) {
        super(fileName, filePath, vfs, parentDirectoryFileIdx, parentDirectoryOffset);
    }

    @Override
    public synchronized int getLength() throws IOException {
        if (mIsClosed) throw new IllegalStateException("Already closed");
        if (mCurrentFileLength != -1) {
            return mCurrentFileLength;
        }
        mCurrentFileLength = mVfs.getFileLengthFor(this);
        return mCurrentFileLength;
    }

    @Override
    public synchronized void seek(int offsetFromStart) {
        if (mIsClosed) throw new IllegalStateException("Already closed");
        mCurrentFileOffset = offsetFromStart;
    }

    @Override
    public synchronized int read(byte[] buffer, int bufferOffset, int length) throws IOException {
        if (mIsClosed) throw new IllegalStateException("Already closed");
        if (!mLockContainer.isReadLocked()) {
            throw new IOException("File is not opened for read");
        }

        prepareStorageContainer(false);

        int fileLength = getLength();
        if (mCurrentFileOffset >= fileLength) {
            return -1;
        }
        else if (mCurrentFileOffset + length > fileLength) {
            length = fileLength - mCurrentFileOffset;
        }
        if (length == 0) {
            return 0;
        }

        int read = mStorageFile.readBlock(mCurrentFileOffset, buffer, bufferOffset, length);
        if (read > 0) {
            mCurrentFileOffset += read;
        }
        return read;
    }

    @Override
    public synchronized void write(byte[] buffer, int bufferOffset, int length) throws IOException {
        if (mIsClosed) throw new IllegalStateException("Already closed");
        if (!mLockContainer.isWriteLocked()) {
            throw new IOException("File is not opened for writing");
        }

        if (length <= 0) return;
        prepareStorageContainer(true);

        mStorageFile.writeBlock(mCurrentFileOffset, buffer, bufferOffset, length);
        mCurrentFileOffset += length;

        mCurrentFileLength = Math.max(getLength(), mCurrentFileOffset);
    }

    private void updateFileLength() throws IOException {
        StorageFile storageFile = mVfs.getStorage().getStorageFile(mParentDirectoryId);
        try (DirectoryHandler directoryHandler = mVfs.getDirectoryHandlerFactory().createNewHandler(storageFile)) {
            DirectoryEntry entry = directoryHandler.readEntry(mParentDirectoryOffset);
            int fileLengthInDirectory = entry.getFileLength();
            if (mCurrentFileLength > fileLengthInDirectory) {
                entry.setFileLength(mCurrentFileLength);
                directoryHandler.updateEntry(entry);
            }
        }
    }

    private void prepareStorageContainer(boolean canCreate) throws IOException {
        if (mIsClosed) throw new IllegalStateException("Already closed");
        if (mStorageFile == null) {
            int storageContainerId = getStorageContainerId();
            if (storageContainerId == mVfs.getDirectoryHandlerFactory().getNoStorageFileIndex()) {
                if (canCreate) {
                    storageContainerId = mVfs.allocateStorageForEntry(this);
                }
                else {
                    throw new EOFException();
                }
            }
            mStorageFile = mVfs.getStorage().getStorageFile(storageContainerId);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        flush();
        super.close();
    }

    private synchronized void flush() throws IOException {
        if (mCurrentFileLength > -1) {
            updateFileLength();
        }
    }

}
