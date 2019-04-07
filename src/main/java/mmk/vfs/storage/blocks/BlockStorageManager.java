package mmk.vfs.storage.blocks;

import java.io.IOException;

/**
 * Manager for handling access to Storage Blocks. Allows getting size of each block and getting each block by its index.
 */
public interface BlockStorageManager extends AutoCloseable {
    /**
     * Get size of each block.
     *
     * @return size of each block in bytes
     */
    int getBlockSize();

    /**
     * Get Storage Block by index.
     *
     * @param blockId zero-based index of storage block
     * @return handle of Storage Block
     * @throws IOException I/O operation happened trying to access Storage Block
     */
    StorageBlock getStorageBlock(int blockId) throws IOException;

    /**
     * Close this storage manager, preventing further access to storage blocks and freeing underlying storage.
     */
    void close();
}
