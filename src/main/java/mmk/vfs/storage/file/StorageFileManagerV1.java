package mmk.vfs.storage.file;

import mmk.vfs.exceptions.ObjectClosedException;
import mmk.vfs.exceptions.OutOfStorage;
import mmk.vfs.exceptions.StorageBlockNotAllocated;
import mmk.vfs.exceptions.StorageCorrupted;
import mmk.vfs.impl.StorageFileImpl;
import mmk.vfs.locks.*;
import mmk.vfs.storage.blocks.BlockStorageManager;
import mmk.vfs.storage.blocks.StorageBlock;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Storage File Manager V1.
 * Uses one Storage Block as Block Allocation Table for Groups of 'BlockSize / 4' Storage Blocks.
 * First block in each group is BAT, so it is not used for actual storage and must not be ever allocated or interacted directly.
 */
public class StorageFileManagerV1 implements StorageFileManager {
    private static final int BLOCK_ID_EMPTY_BLOCK = 0;
    private static final int BLOCK_ID_LAST_BLOCK = -1;
    private static final int MAXIMUM_BLOCKS = Integer.MAX_VALUE - 1;
    private static final boolean USE_ADDITIONAL_SAFETY_CHECK_WHEN_ACCESSING_STORAGE_FILES = false;

    private BlockStorageManager mBlockStorageManager;
    private int mBlocksPerGroup;
    private AccessProviderManager<Integer> mLockManager = new AccessProviderManager<>(ReadWriteAccessProvider::new);
    private volatile boolean mIsClosed = false;
    private final Set<StorageFile> mOpenedFiles = new HashSet<>();
    private final InternalApi mInternalApi;

    public StorageFileManagerV1(BlockStorageManager blockStorageManager) throws IOException {
        mBlockStorageManager = blockStorageManager;
        int blockSize = blockStorageManager.getBlockSize();
        mBlocksPerGroup = blockSize / 4;

        mInternalApi = new InternalApi();

        ensureRootDirPresence();
    }

    private int getStorageGroupIndex(int storageBlockIdNoBat) {
        return storageBlockIdNoBat / mBlocksPerGroup;
    }

    private int getStorageInGroupIndex(int storageBlockIdNoBat) {
        return storageBlockIdNoBat - (getStorageGroupIndex(storageBlockIdNoBat) * mBlocksPerGroup);
    }

    private int getBATBlockIndex(int storageBlockIdNoBat) {
        return getStorageGroupIndex(storageBlockIdNoBat) * mBlocksPerGroup;
    }

    private int getStorageBlockIndexByIndex(int storageBlockIdNoBat) {
        return storageBlockIdNoBat;
    }

    private void ensureRootDirPresence() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
        int storageIndex = getRootDirectoryStorageIndex();
        int batIndex = getStorageGroupIndex(storageIndex);
        int inBatIndex = getStorageInGroupIndex(storageIndex);

        try (StorageBlock batStorageBlock = getBATStorageBlock(batIndex)) {
            batStorageBlock.claim(LockType.WRITE_LOCK);
            batStorageBlock.ensureCapacity();

            batStorageBlock.readFully(4 * inBatIndex, buffer.array(), 0, buffer.capacity());

            int nextBlockInfo = buffer.getInt();
            if (nextBlockInfo == BLOCK_ID_EMPTY_BLOCK) {
                buffer.position(0);
                buffer.putInt(BLOCK_ID_LAST_BLOCK);

                batStorageBlock.write(4 * inBatIndex, buffer.array(), 0, buffer.capacity());
            }
        }
        try (StorageBlock storageBlock = getStorageBlock(storageIndex)) {
            storageBlock.claim(LockType.WRITE_LOCK);
            storageBlock.ensureCapacity();
        }
    }

    @Override
    public int getRootDirectoryStorageIndex() {
        return 1;
    }

    @Override
    public synchronized StorageFile getStorageFile(int storageFileId) throws IOException {
        if (mIsClosed) throw new ObjectClosedException();

        int inBatIndex = getStorageInGroupIndex(storageFileId);
        if (inBatIndex == 0) {
            throw new StorageCorrupted("Trying to access BAT sections with method for accessing Data sections");
        }

        if (USE_ADDITIONAL_SAFETY_CHECK_WHEN_ACCESSING_STORAGE_FILES) {
            int batIndex = getStorageGroupIndex(storageFileId);
            try (StorageBlock storageBlock = getBATStorageBlock(batIndex)) {
                storageBlock.claim(LockType.READ_LOCK);

                ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);

                storageBlock.readFully(4 * inBatIndex, buffer.array(), 0, buffer.capacity());

                int nextBlockInfo = buffer.getInt();
                if (nextBlockInfo == BLOCK_ID_EMPTY_BLOCK) {
                    throw new StorageBlockNotAllocated("There is no such file");
                }
            }
        }

        StorageFileImpl storageFile = new StorageFileImpl(mInternalApi, storageFileId);
        mOpenedFiles.add(storageFile);
        return storageFile;
    }

    private int findAndClaimEmptyBlock() throws IOException {
        int checkBlockIdx = 0; // skip first BAT and root dir

        ByteBuffer buffer = ByteBuffer.allocate(4 * 64).order(ByteOrder.BIG_ENDIAN);
        byte[] bufferArray = buffer.array();

        do {
            try (StorageBlock batStorageBlock = getBATStorageBlock(getStorageGroupIndex(checkBlockIdx))) {
                batStorageBlock.claim(LockType.WRITE_LOCK);
                batStorageBlock.ensureCapacity();

                int bufferOffset = 0;
                int bufferFill;
                int readOffset = 0;
                while (bufferOffset < buffer.capacity()) {
                    int read = batStorageBlock.read(readOffset, bufferArray, bufferOffset, buffer.capacity() - bufferOffset);
                    if (read == -1) break;
                    bufferFill = bufferOffset + read;

                    int parseOffset = 0;
                    for (; bufferFill - parseOffset >= 4; ++checkBlockIdx, parseOffset += 4) {
                        if (checkBlockIdx >= MAXIMUM_BLOCKS) break;

                        int nextBlockInfo = buffer.getInt(parseOffset);

                        if (getStorageInGroupIndex(checkBlockIdx) == 0) {
                            continue;
                        }

                        if (nextBlockInfo == BLOCK_ID_EMPTY_BLOCK) {
                            // reserve space for newly found empty block
                            try (StorageBlock storageBlock = getStorageBlock(checkBlockIdx)) {
                                storageBlock.ensureCapacity();
                            }

                            // mark block as used (last block of some Storage File)
                            // this is done after reservation in case of failed reservation.
                            buffer.putInt(parseOffset, BLOCK_ID_LAST_BLOCK);
                            batStorageBlock.write(readOffset + parseOffset, bufferArray, parseOffset, 4);

                            return checkBlockIdx;
                        }
                    }

                    int remainder = bufferFill - parseOffset;
                    if (remainder > 0 && parseOffset > 0) {
                        System.arraycopy(bufferArray, parseOffset, bufferArray, 0, remainder);
                    }
                    bufferOffset = remainder;

                    if (checkBlockIdx >= MAXIMUM_BLOCKS) break;
                    readOffset += read;
                }
            }
        } while (checkBlockIdx < MAXIMUM_BLOCKS);

        throw new OutOfStorage("Out Of Storage Blocks");
    }

    @Override
    public synchronized StorageFile createNewFile() throws IOException {
        if (mIsClosed) throw new ObjectClosedException();

        int emptyBlockIdx = findAndClaimEmptyBlock();
        StorageFileImpl storageFile = new StorageFileImpl(mInternalApi, emptyBlockIdx);
        mOpenedFiles.add(storageFile);
        return storageFile;
    }

    private synchronized void onFileClosed(StorageFile file) {
        mOpenedFiles.remove(file);
    }

    @Override
    public synchronized void freeStorage(int storageFileId) throws IOException {
        if (mIsClosed) throw new ObjectClosedException();

        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);

        AccessProvider locker = mLockManager.getLockerForPath(storageFileId);
        locker.addReference();
        Lock lock = null;

        try {
            lock = locker.claimWrite();

            if (lock == null) {
                throw new IllegalStateException("Freeing opened file");
            }

            int storagePointer = storageFileId;
            while (true) {
                int batIndex = getStorageGroupIndex(storagePointer);
                int inBatIndex = getStorageInGroupIndex(storagePointer);
                if (inBatIndex == 0) {
                    throw new StorageCorrupted("Trying to access BAT sections with method for accessing Data sections");
                }

                int nextBlockId;
                try (StorageBlock storageBlock = getBATStorageBlock(batIndex)) {
                    storageBlock.claim(LockType.WRITE_LOCK);
                    try {
                        storageBlock.readFully(4 * inBatIndex, buffer.array(), 0, buffer.capacity());
                        buffer.position(0);

                        int nextBlockIdLookup = buffer.getInt();
                        if (nextBlockIdLookup == BLOCK_ID_EMPTY_BLOCK) {
                            break;
                        }

                        nextBlockId = nextBlockIdLookup;

                        buffer.putInt(0, 0);
                        storageBlock.write(4 * inBatIndex, buffer.array(), 0, buffer.capacity());
                    } finally {
                        storageBlock.release();
                    }
                }

                if (nextBlockId == BLOCK_ID_LAST_BLOCK) {
                    break;
                }
                storagePointer = nextBlockId;
            }
        } catch (InterruptedException | InterruptedIOException exception) {
            throw new InterruptedIOException("File deletion interrupted, VFS corrupted (Can't free used storage blocks now)");
        } finally {
            if (lock != null) lock.release();
            locker.removeReference();
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            mIsClosed = true;

            for (StorageFile storageFile : new ArrayList<>(mOpenedFiles)) {
                try {
                    storageFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        mBlockStorageManager.close();
    }

    private int getCurrentCapacity(int storageFileId) throws IOException {
        int storagePointer = storageFileId;
        int lengthInBlocks = 0;
        while (true) {
            ++lengthInBlocks;
            int nextBlockId = getNextStorageBlockIndex(storagePointer);

            if (nextBlockId == BLOCK_ID_LAST_BLOCK) {
                break;
            }
            storagePointer = nextBlockId;
        }

        return lengthInBlocks * mBlockStorageManager.getBlockSize();
    }

    private StorageBlock getBATStorageBlock(int batIndex) throws IOException {
        return mBlockStorageManager.getStorageBlock(getBATBlockIndex(batIndex));
    }

    private StorageBlock getStorageBlock(int storageIdNoBat) throws IOException {
        if (getStorageInGroupIndex(storageIdNoBat) == 0) {
            throw new StorageCorrupted("Trying to open BAT sections with method for opening Data sections");
        }
        return mBlockStorageManager.getStorageBlock(getStorageBlockIndexByIndex(storageIdNoBat));
    }

    private int getNextStorageBlockIndex(int storageBlockIdNoBat) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);

        int batIndex = getStorageGroupIndex(storageBlockIdNoBat);
        int inBatIndex = getStorageInGroupIndex(storageBlockIdNoBat);
        if (inBatIndex == 0) {
            throw new StorageCorrupted("Trying to access BAT sections with method for accessing Data sections");
        }

        try (StorageBlock storageBlock = getBATStorageBlock(batIndex)) {
            storageBlock.claim(LockType.READ_LOCK);
            storageBlock.readFully(4 * inBatIndex, buffer.array(), 0, buffer.capacity());
        }

        int nextBlockId = buffer.getInt(0);
        if (nextBlockId == BLOCK_ID_EMPTY_BLOCK) {
            throw new StorageCorrupted("Trying to get next block from empty block");
        }
        return nextBlockId;
    }

    private int getBlockSize() {
        return mBlockStorageManager.getBlockSize();
    }

    private boolean isLastBlockId(int storageBlockId) {
        return BLOCK_ID_LAST_BLOCK != storageBlockId;
    }

    private synchronized int extendFileFromBlock(int storageBlockId) throws IOException {
        if (mIsClosed) throw new ObjectClosedException();

        int newBlockId = findAndClaimEmptyBlock();

        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);

        boolean success = false;

        int batIndex = getStorageGroupIndex(storageBlockId);
        int inBatIndex = getStorageInGroupIndex(storageBlockId);
        if (inBatIndex == 0) {
            throw new StorageCorrupted("Trying to access BAT sections with method for accessing Data sections");
        }

        try (StorageBlock storageBlock = getBATStorageBlock(batIndex)) {
            storageBlock.claim(LockType.WRITE_LOCK);
            storageBlock.readFully(4 * inBatIndex, buffer.array(), 0, buffer.capacity());
            int currentNextBlockId = buffer.getInt(0);
            if (currentNextBlockId != BLOCK_ID_LAST_BLOCK) {
                throw new StorageCorrupted("Trying to extend storage from non-last block");
            }
            buffer.putInt(0, newBlockId);
            storageBlock.write(4 * inBatIndex, buffer.array(), 0, buffer.capacity());
            success = true;
        } catch (InterruptedIOException exception) {
            throw new InterruptedIOException("File extension interrupted, VFS corrupted (Allocated block is not referenced, can't be used nor reused)");
        } finally {
            if (!success) {
                freeStorage(newBlockId);
            }
        }
        return newBlockId;
    }

    private class InternalApi implements StorageFileImpl.StorageFileManagerInternalApi {
        @Override
        public int getCurrentCapacity(int storageFileId) throws IOException {
            return StorageFileManagerV1.this.getCurrentCapacity(storageFileId);
        }

        @Override
        public StorageBlock getStorageBlock(int storageBlockId) throws IOException {
            return StorageFileManagerV1.this.getStorageBlock(storageBlockId);
        }

        @Override
        public int getNextStorageBlockIndex(int storageBlockId) throws IOException {
            return StorageFileManagerV1.this.getNextStorageBlockIndex(storageBlockId);
        }

        @Override
        public int getBlockSize() {
            return StorageFileManagerV1.this.getBlockSize();
        }

        @Override
        public boolean isLastBlockId(int storageBlockId) {
            return StorageFileManagerV1.this.isLastBlockId(storageBlockId);
        }

        @Override
        public int extendFileFromBlock(int storageBlockId) throws IOException {
            return StorageFileManagerV1.this.extendFileFromBlock(storageBlockId);
        }

        @Override
        public AccessProviderManager<Integer> getLockManager() {
            return mLockManager;
        }

        @Override
        public void onFileClosed(StorageFile file) {
            StorageFileManagerV1.this.onFileClosed(file);
        }
    }
}
