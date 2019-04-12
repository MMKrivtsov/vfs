package mmk.vfs.storage;

import mmk.vfs.locks.AccessProviderManager;
import mmk.vfs.locks.LockType;
import mmk.vfs.locks.AccessController;
import mmk.vfs.locks.ReadWriteAccessProvider;
import mmk.vfs.storage.blocks.BlockStorageManager;
import mmk.vfs.storage.blocks.StorageBlock;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class InMemoryBlockStorageManager implements BlockStorageManager {
    private final int mBlockSize;
    private final int mBlockReadLimit;

    private final List<byte[]> mBlocks = new ArrayList<>();
    private AccessProviderManager<Integer> mLockManager = new AccessProviderManager<>(ReadWriteAccessProvider::new);

    public InMemoryBlockStorageManager(int blockSize, int blockReadLimit) {
        mBlockSize = blockSize;
        if (blockReadLimit <= 0) throw new IllegalArgumentException("Block Read Limit must be positive");
        mBlockReadLimit = blockReadLimit;
    }

    @Override
    public int getBlockSize() {
        return mBlockSize;
    }

    @Override
    public StorageBlock getStorageBlock(int blockId) throws IOException {
        return new StorageBlockInMemory(blockId);
    }

    @Override
    public void close() {
        mBlocks.clear();
    }

    private class StorageBlockInMemory implements StorageBlock {
        private final int mBlockId;
        private final AccessController mLockContainer;

        public StorageBlockInMemory(int blockId) {
            mBlockId = blockId;
            mLockContainer = new AccessController(mLockManager.getLockerForPath(blockId));
        }

        @Override
        public void claim(LockType lockType) throws InterruptedIOException {
            mLockContainer.claimLock(lockType);
        }

        @Override
        public void release() {
            mLockContainer.releaseLock();
        }

        @Override
        public int read(int blockOffset, byte[] buffer, int bufferOffset, int length) throws IOException {
            if (mBlockId < mBlocks.size()) {
                byte[] blockBytes = mBlocks.get(mBlockId);

                if (blockOffset >= mBlockSize) {
                    return -1;
                }
                length = Math.min(length, mBlockSize - blockOffset);
                length = Math.min(mBlockReadLimit, length);

                System.arraycopy(blockBytes, blockOffset, buffer, bufferOffset, length);

                StringJoiner bytesString = new StringJoiner(":");
                for (int i = 0; i < length; ++i) {
                    int b = buffer[bufferOffset + i] & 0xFF;
                    bytesString.add((b < 0x10 ? "0" : "") + Integer.toHexString(b));
                }
//                System.out.println(String.format(Locale.getDefault(), "Read  of block #%d from %d for %d bytes: %s", mBlockId, blockOffset, length, bytesString.toString()));
            }
            return length;
        }

        @Override
        public void write(int blockOffset, byte[] buffer, int bufferOffset, int length) throws IOException {
            if (mBlockId < mBlocks.size()) {
                byte[] blockBytes = mBlocks.get(mBlockId);

                if (blockOffset + length > mBlockSize) {
                    throw new IOException("Writing after block boundary");
                }
                length = Math.min(length, mBlockSize - blockOffset);

                System.arraycopy(buffer, bufferOffset, blockBytes, blockOffset, length);

                StringJoiner bytesString = new StringJoiner(":");
                for (int i = 0; i < length; ++i) {
                    int b = buffer[bufferOffset + i] & 0xFF;
                    bytesString.add((b < 0x10 ? "0" : "") + Integer.toHexString(b));
                }
//                System.out.println(String.format(Locale.getDefault(), "Write of block #%d from %d for %d bytes: %s", mBlockId, blockOffset, length, bytesString.toString()));
            } else {
                throw new IOException("Not enough storage capacity");
            }
        }

        @Override
        public void close() {
            mLockContainer.close();
        }

        @Override
        public void ensureCapacity() throws IOException {
            while (mBlockId >= mBlocks.size()) {
                mBlocks.add(new byte[mBlockSize]);
            }
        }
    }
}
