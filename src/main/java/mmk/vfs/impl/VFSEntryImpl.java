package mmk.vfs.impl;

import mmk.vfs.VFSEntry;
import mmk.vfs.directories.DirectoryEntry;
import mmk.vfs.directories.DirectoryHandler;
import mmk.vfs.exceptions.FileAlreadyOpenException;
import mmk.vfs.locks.LockType;
import mmk.vfs.locks.ReadWriteLockContainer;

import java.io.IOException;

public class VFSEntryImpl implements VFSEntry {
    private final String mFileName;
    final String mFilePath;
    final VirtualFileSystemImpl mVfs;
    int mParentDirectoryId;
    int mParentDirectoryOffset;
    boolean mIsClosed;
    ReadWriteLockContainer mLockContainer;

    public VFSEntryImpl(String fileName, String filePath, VirtualFileSystemImpl vfs, int parentDirectoryFileIdx, int parentDirectoryOffset) {
        mFileName = fileName;
        mFilePath = filePath;
        mVfs = vfs;
        mIsClosed = false;
        mParentDirectoryId = parentDirectoryFileIdx;
        mParentDirectoryOffset = parentDirectoryOffset;
    }

    public void lock(LockType lockType) throws IOException {
        if (mIsClosed) throw new IllegalStateException("Already closed");

        if (mLockContainer == null) {
            mLockContainer = new ReadWriteLockContainer(mVfs.getEntityLockManager().getLockerForPath(mFilePath));
        }

        if (!mLockContainer.tryClaimLock(lockType)) {
            throw new FileAlreadyOpenException("Can't acquire " + lockType + " lock");
        }
    }

    protected int getStorageContainerId() throws IOException {
        if (mIsClosed) throw new IllegalStateException("Already closed");

        return mVfs.getStorageIdForEntry(this);
    }

    public String getName() {
        return mFileName;
    }

    public String getPath() {
        return mFilePath;
    }

    public synchronized void close() throws IOException {
        if (!mIsClosed) {
            mIsClosed = true;
            mVfs.entryClosed(this);
        }
        if (mLockContainer != null) {
            mLockContainer.close();
        }
    }

    @Override
    public int hashCode() {
        return mFileName.hashCode() * 63 + mParentDirectoryId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj.getClass() != getClass()) {
            return false;
        }
        return ((VFSEntryImpl) obj).mParentDirectoryId == mParentDirectoryId &&
               ((VFSEntryImpl) obj).mFileName.equals(mFileName);
    }

    public void freeStorage() throws IOException {
        if (mIsClosed) throw new IllegalStateException("Already closed");

        int entryStorageStartIdx;

        try (DirectoryHandler directoryHandler = mVfs.getDirectoryHandlerFactory().createNewHandler(mVfs.getStorage().getStorageFile(mParentDirectoryId))) {
            DirectoryEntry entry = directoryHandler.readEntry(mParentDirectoryOffset);
            entryStorageStartIdx = entry.getStorageStartIdx();
            directoryHandler.removeEntry(entry.getParentDirectoryIndex());
        }

        if (entryStorageStartIdx != mVfs.getDirectoryHandlerFactory().getNoStorageFileIndex()) {
            mVfs.getStorage().freeStorage(entryStorageStartIdx);
        }
    }
}
