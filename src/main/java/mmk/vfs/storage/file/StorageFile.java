package mmk.vfs.storage.file;

import mmk.vfs.locks.LockType;

import java.io.IOException;
import java.io.InterruptedIOException;

/**
 * Storage File interface. Storage File is a sequence of Storage Blocks.
 */
public interface StorageFile extends AutoCloseable {
    /**
     * Get index of first block of this file.
     *
     * @return index of first block of this file
     */
    int getStorageStartIdx();

    /**
     * Acquire read/write lock for this file, preventing concurrent writes and read-writes, while allowing concurrent reads.
     *
     * @param lockType required lock type
     * @throws InterruptedIOException thread was interrupted while trying to acquire lock
     */
    void claimLock(LockType lockType) throws InterruptedIOException;

    /**
     * Release currently acquired lock on this block.
     */
    void releaseLock();

    /**
     * Get current capacity of this file. It is calculated as multiplication of block size and length of block sequence.
     *
     * @return current capacity of this file
     * @throws IOException I/O while calculating sequence length
     */
    int getCurrentCapacity() throws IOException;

    /**
     * Read contents of file. Requires READ lock to be claimed first.
     * Can return before all bytes are read based on implementation details.
     *
     * @param fileOffset   offset from the start of this block
     * @param readBuffer   buffer to read into
     * @param bufferOffset offset in buffer to start putting read bytes
     * @param length       length of data to read
     * @return count of bytes which were actually read from block
     * @throws IOException I/O exception happened during read operation
     */
    int readBlock(int fileOffset, byte[] readBuffer, int bufferOffset, int length) throws IOException;

    /**
     * Read contents of block. Requires READ lock to be claimed first. Continues read until all requested
     * bytes have been read or end of block encountered.
     *
     * @param fileOffset   offset from the start of this block
     * @param writeBuffer  buffer to read into
     * @param bufferOffset offset in buffer to start putting read bytes
     * @param length       length of data to read
     * @return count of bytes which were actually read from block
     * @throws IOException I/O exception happened during read operation
     */
    void writeBlock(int fileOffset, byte[] writeBuffer, int bufferOffset, int length) throws IOException;

    /**
     * Close and free this file handle, release lock if it was not released yet.
     */
    void close() throws IOException;
}
