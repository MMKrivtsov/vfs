package mmk.vfs.storage.blocks;

import mmk.vfs.exceptions.ObjectClosedException;
import mmk.vfs.locks.AccessProviderManager;
import mmk.vfs.locks.LockType;
import mmk.vfs.locks.AccessController;
import mmk.vfs.locks.ReadWriteAccessProvider;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * File-based storage. Stores blocks in file starting from provided offset.
 */
public class FileBlockStorageManager implements BlockStorageManager {
    private final FileChannel mFileChannel;
    private final FileLock mFileLock;

    private boolean mIsClosed = false;
    private int mDataStartOffset;
    private int mBlockSize;
    private final AccessProviderManager<Integer> mLockManager = new AccessProviderManager<>(ReadWriteAccessProvider::new);
    private final Set<StorageBlock> mOpenedStorageBlocks = new HashSet<>();

    /**
     * Constructor.
     *
     * @param fileChannel     file to use as storage
     * @param dataStartOffset offset in file where storage actually starts
     * @param blockSize       size of each block
     * @throws IOException I/O exception trying to acquire lock on file
     */
    public FileBlockStorageManager(FileChannel fileChannel, int dataStartOffset, int blockSize) throws IOException {
        mFileChannel = fileChannel;
        mFileLock = fileChannel.lock();
        mDataStartOffset = dataStartOffset;
        mBlockSize = blockSize;
    }

    @Override
    public int getBlockSize() {
        return mBlockSize;
    }

    @Override
    public synchronized StorageBlock getStorageBlock(int blockId) throws ObjectClosedException {
        if (mIsClosed) throw new ObjectClosedException();

        StorageBlock block = new FileBlock(blockId);
        mOpenedStorageBlocks.add(block);
        return block;
    }

    @Override
    public synchronized void close() {
        synchronized (this) {
            mIsClosed = true;
            for (StorageBlock block : new ArrayList<>(mOpenedStorageBlocks)) {
                block.close();
            }
        }

        if (mFileLock != null) {
            try {
                mFileLock.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            mFileChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int getRawBlockOffset(int blockIndex) {
        return blockIndex * mBlockSize;
    }

    class FileBlock implements StorageBlock {
        private int mBlockStartOffset;
        private final AccessController mLockContainer;

        public FileBlock(int blockId) {
            mBlockStartOffset = getRawBlockOffset(blockId);
            mLockContainer = new AccessController(mLockManager.getLockerForPath(blockId));
        }

        @Override
        public synchronized void claim(LockType lockType) throws InterruptedIOException {
            mLockContainer.claimLock(lockType);
        }

        @Override
        public synchronized void release() {
            mLockContainer.releaseLock();
        }

        public synchronized int read(int blockOffset, byte[] buffer, int bufferOffset, int length) throws IOException {
            if (!mLockContainer.isReadLocked()) throw new IllegalStateException("Read lock not claimed");

            synchronized (mFileChannel) {
                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, bufferOffset, length);
                mFileChannel.position(blockOffset + mBlockStartOffset + mDataStartOffset);
                return mFileChannel.read(byteBuffer);
            }
        }

        public synchronized void write(int blockOffset, byte[] buffer, int bufferOffset, int length) throws IOException {
            if (!mLockContainer.isWriteLocked()) throw new IllegalStateException("Write lock not claimed");

            synchronized (mFileChannel) {
                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, bufferOffset, length);
                int fileOffset = blockOffset + mBlockStartOffset + mDataStartOffset;
                mFileChannel.position(fileOffset);
                while (byteBuffer.hasRemaining()) {
                    mFileChannel.write(byteBuffer);
                }
                mFileChannel.force(false);
            }
        }

        @Override
        public synchronized void close() {
            release();
            mLockContainer.close();
            synchronized (FileBlockStorageManager.this) {
                mOpenedStorageBlocks.remove(this);
            }
        }

        public synchronized void ensureCapacity() throws IOException {
            synchronized (mFileChannel) {
                int minFileSize = mBlockStartOffset + mBlockSize + mDataStartOffset;
                long size;
                ByteBuffer zeroBuffer = null;
                do {
                    size = mFileChannel.size();
                    long missingBytes = minFileSize - size;
                    if (missingBytes > 0) {
                        if (zeroBuffer == null) {
                            zeroBuffer = ByteBuffer.allocate(mBlockSize);
                        }
                        else {
                            zeroBuffer.clear();
                        }
                        zeroBuffer.limit((int)Math.min(zeroBuffer.capacity(), missingBytes));
                        mFileChannel.write(zeroBuffer, size);
                    }
                } while (size < minFileSize);
            }
        }
    }

}
