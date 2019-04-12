package mmk.vfs.impl;

import mmk.vfs.locks.AccessProviderManager;
import mmk.vfs.locks.LockType;
import mmk.vfs.locks.AccessController;
import mmk.vfs.storage.blocks.StorageBlock;
import mmk.vfs.storage.file.StorageFile;

import java.io.IOException;
import java.io.InterruptedIOException;

public class StorageFileImpl implements StorageFile {
    private final StorageFileManagerInternalApi mFileStorage;
    private final int mStorageIndex;
    private final AccessController mLockContainer;

    private StorageBlock mCurrentStorageBlock = null;
    private int mBlockSequence;
    private int mCurrentBlockSequenceStorageId;

    public StorageFileImpl(StorageFileManagerInternalApi fileStorage, int storageIndex) {
        mFileStorage = fileStorage;
        mStorageIndex = storageIndex;
        mBlockSequence = 0;
        mLockContainer = new AccessController(fileStorage.getLockManager().getLockerForPath(mStorageIndex));
    }

    @Override
    public int getStorageStartIdx() {
        return mStorageIndex;
    }

    @Override
    public synchronized void claimLock(LockType lockType) throws InterruptedIOException {
        mLockContainer.claimLock(lockType);
    }

    @Override
    public synchronized void releaseLock() {
        mLockContainer.releaseLock();
    }

    @Override
    public int getCurrentCapacity() throws IOException {
        return mFileStorage.getCurrentCapacity(mStorageIndex);
    }

    @Override
    public synchronized int readBlock(int fileOffset, byte[] readBuffer, int bufferOffset, int length) throws IOException {
        int totalRead;

        int sequenceId = getBlockSequenceForFileOffset(fileOffset);
        if (mBlockSequence != sequenceId || mCurrentStorageBlock == null) {
            changeStorageBlock(sequenceId, false);
        }
        if (mBlockSequence < sequenceId) {
            return -1;
        }

        int blockOffset = getBlockOffsetForFileOffset(fileOffset);
        int blockReadLength = Math.min(mFileStorage.getBlockSize() - blockOffset, length);
        mCurrentStorageBlock.claim(LockType.READ_LOCK);
        try {
            totalRead = mCurrentStorageBlock.read(blockOffset, readBuffer, bufferOffset, blockReadLength);
        } finally {
            mCurrentStorageBlock.release();
        }

        return totalRead;
    }

    @Override
    public synchronized void writeBlock(int fileOffset, byte[] writeBuffer, int bufferOffset, int length) throws IOException {
        int totalWrite = 0;
        while (totalWrite < length) {
            int offset = fileOffset + totalWrite;
            int sequenceId = getBlockSequenceForFileOffset(offset);
            if (mBlockSequence != sequenceId || mCurrentStorageBlock == null) {
                changeStorageBlock(sequenceId, true);
            }
            int blockOffset = getBlockOffsetForFileOffset(offset);
            int blockWriteLength = Math.min(mFileStorage.getBlockSize() - blockOffset, length - totalWrite);
            mCurrentStorageBlock.claim(LockType.WRITE_LOCK);
            try {
                //mCurrentStorageBlock.ensureCapacity(); // this is expected to be done on new block allocation, maybe remove this line
                mCurrentStorageBlock.write(blockOffset, writeBuffer, bufferOffset + totalWrite, blockWriteLength);
            } finally {
                mCurrentStorageBlock.release();
            }
            totalWrite += blockWriteLength;
        }
    }

    // called from synchronized code
    private void changeStorageBlock(int sequenceId, boolean canCreate) throws IOException {
        if (sequenceId < mBlockSequence || mCurrentStorageBlock == null) {
            mBlockSequence = 0;
            mCurrentBlockSequenceStorageId = mStorageIndex;
        }
        while (mBlockSequence < sequenceId) {
            int nextStorageBlockIndex = mFileStorage.getNextStorageBlockIndex(mCurrentBlockSequenceStorageId);
            if (!mFileStorage.isLastBlockId(nextStorageBlockIndex)) {
                if (canCreate) {
                    nextStorageBlockIndex = mFileStorage.extendFileFromBlock(mCurrentBlockSequenceStorageId);
                }
                else {
                    return;
                }
            }
            mCurrentBlockSequenceStorageId = nextStorageBlockIndex;
            ++mBlockSequence;
        }
        if (mCurrentStorageBlock != null) {
            mCurrentStorageBlock.close();
        }
        mCurrentStorageBlock = mFileStorage.getStorageBlock(mCurrentBlockSequenceStorageId);
    }

    @Override
    public synchronized void close() {
        if (mCurrentStorageBlock != null) {
            mCurrentStorageBlock.close();
            mCurrentStorageBlock = null;
        }
        mFileStorage.onFileClosed(this);
        mLockContainer.close();
    }

    private int getBlockSequenceForFileOffset(int fileOffset) {
        return fileOffset / mFileStorage.getBlockSize();
    }

    private int getBlockOffsetForFileOffset(int fileOffset) {
        return fileOffset - getBlockSequenceForFileOffset(fileOffset) * mFileStorage.getBlockSize();
    }

    public interface StorageFileManagerInternalApi {
        /**
         * Get current capacity for file. Equals length of file's StorageBlocks sequence length times block size.
         * <p>
         * This API method MUST NOT be used outside of StorageFile implementation.
         *
         * @param storageFileId StorageFile index
         * @return capacity of file
         * @throws IOException I/O exception happened during operation
         */
        int getCurrentCapacity(int storageFileId) throws IOException;

        /**
         * Open handle for storage block by storage block index.
         * <p>
         * This API method MUST NOT be used outside of StorageFile implementation.
         *
         * @param storageBlockId storage block index, which must be acquired by means of getNextStorageBlockIndex()
         * @return handle tro storage block
         * @throws IOException I/O exception happened during operation
         */
        StorageBlock getStorageBlock(int storageBlockId) throws IOException;

        /**
         * Get index of next StorageBlock by index of current storage block.
         * <p>
         * This API method MUST NOT be used outside of StorageFile implementation.
         *
         * @param storageBlockId index of storage block
         * @return index of next storage block
         * @throws IOException I/O exception happened during operation
         */
        int getNextStorageBlockIndex(int storageBlockId) throws IOException;

        /**
         * Get block size of underlying block storage.
         * <p>
         * This API method MUST NOT be used outside of StorageFile implementation.
         *
         * @return size of each Storage Block
         */
        int getBlockSize();

        /**
         * Check if next storage block index is actually pointing to next block or current block was last block.
         * <p>
         * This API method MUST NOT be used outside of StorageFile implementation.
         *
         * @param storageBlockId index of storage block
         * @return true if this storage block index means there is no next block, false if this index should point to valid storage block
         */
        boolean isLastBlockId(int storageBlockId);

        /**
         * Acquire new storage block to extend file from provided storage block.
         * Provided StorageBlock index MUST point to last block of StorageFile sequence.
         * <p>
         * This API method MUST NOT be used outside of StorageFile implementation.
         *
         * @param storageBlockId index of last block in StorageFile's Block sequence
         * @return index of newly allocated StorageBlock
         * @throws IOException I/O exception happened during operation
         */
        int extendFileFromBlock(int storageBlockId) throws IOException;

        /**
         * Get lock manager for controlling access to StorageFiles.
         * <p>
         * This API method MUST NOT be used outside of StorageFile implementation.
         *
         * @return lock manager for controlling access to StorageFiles
         */
        AccessProviderManager<Integer> getLockManager();

        /**
         * Notify Manager that file handle was closed.
         *
         * @param file file which was closed
         */
        void onFileClosed(StorageFile file);
    }
}
