package mmk.vfs.storage.blocks;

import mmk.vfs.locks.LockType;

import java.io.IOException;
import java.io.InterruptedIOException;

/**
 * Minimal data storage element available to allocate. Files in VFS are stored in one Block or split to several Blocks,
 * depending on required storage capacity.
 */
public interface StorageBlock extends AutoCloseable {
    /**
     * Acquire read/write lock for this block, preventing concurrent writes and read-writes, while allowing concurrent reads.
     *
     * @param lockType required lock type
     * @throws InterruptedIOException thread was interrupted while trying to acquire lock
     */
    void claim(LockType lockType) throws InterruptedIOException;

    /**
     * Release currently acquired lock on this block.
     */
    void release();

    /**
     * Read contents of block. Requires READ lock to be claimed first.
     * Can return before all bytes are read based on implementation details.
     *
     * @param blockOffset  offset from the start of this block
     * @param buffer       buffer to read into
     * @param bufferOffset offset in buffer to start putting read bytes
     * @param length       length of data to read
     * @return count of bytes which were actually read from block
     * @throws IOException I/O exception happened during read operation
     */
    int read(int blockOffset, byte[] buffer, int bufferOffset, int length) throws IOException;

    /**
     * Read contents of block. Requires READ lock to be claimed first. Continues read until all requested
     * bytes have been read or end of block encountered.
     *
     * @param blockOffset  offset from the start of this block
     * @param buffer       buffer to read into
     * @param bufferOffset offset in buffer to start putting read bytes
     * @param length       length of data to read
     * @return count of bytes which were actually read from block
     * @throws IOException I/O exception happened during read operation
     */
    default int readFully(int blockOffset, byte[] buffer, int bufferOffset, int length) throws IOException {
        int totalRead = 0;
        while (totalRead < length) {
            int read = read(blockOffset + totalRead, buffer, bufferOffset + totalRead, length - totalRead);
            if (read < 0) {
                break;
            }
            totalRead += read;
        }
        return totalRead;
    }

    /**
     * Write contents to block. Requires WRITE lock to be claimed first.
     *
     * @param blockOffset  offset from the start of this block
     * @param buffer       buffer to write from
     * @param bufferOffset offset in buffer to start getting bytes for write
     * @param length       length of data to write
     * @throws IOException I/O exception happened during read operation
     */
    void write(int blockOffset, byte[] buffer, int bufferOffset, int length) throws IOException;

    /**
     * Close and free this block, release lock if it was not released yet.
     */
    void close();

    /**
     * Notify underlying storage to allocate enough space for this block to be available for read/write fully.
     *
     * @throws IOException I/O happened while trying to allocate space for file
     */
    void ensureCapacity() throws IOException;
}
