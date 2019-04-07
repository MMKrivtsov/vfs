package mmk.vfs.storage.file;

import java.io.IOException;

/**
 * Manager for handling to access to Storage Files. Instances of this class manage allocation of blocks and their distribution
 * between Files, that allows getting handles to StorageFile instances, allocate new Files, extend existing Files, free
 * deleted Files.
 * It is not mandatory for storage file indexes to match block indexes in any way.
 */
public interface StorageFileManager {
    /**
     * Open handle to StorageFile by index.
     *
     * @param fileIdx index of StorageFile
     * @return handle to StorageFile
     * @throws IOException I/O exception happened during opening new handle
     */
    StorageFile getStorageFile(int fileIdx) throws IOException;

    /**
     * Allocate new file storage and open handle to it.
     *
     * @return handle to new file storage
     * @throws IOException I/O exception happened during allocation
     */
    StorageFile createNewFile() throws IOException;

    /**
     * Free all blocks of StorageFile.
     *
     * @param storageFileId index of StorageFile
     * @throws IOException I/O exception happened during free operation
     */
    void freeStorage(int storageFileId) throws IOException;

    /**
     * Close all StorageFiles, underlying block storage and this manager.
     */
    void close();

    /**
     * Get StorageFile index of root directory file.
     *
     * @return StorageFile index of root directory file
     */
    int getRootDirectoryStorageIndex();
}
