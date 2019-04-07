package mmk.vfs.directories;

import mmk.vfs.storage.file.StorageFile;

/**
 * Factory for creating DirectoryHandler instances for directory StorageFile.
 */
public interface DirectoryHandlerFactory {
    /**
     * Create new DirectoryHandler instance.
     *
     * @param storageFile directory file
     * @return new handler instance
     */
    DirectoryHandler createNewHandler(StorageFile storageFile);

    /**
     * Get index, which is used in directory handlers of this factory to mark entry as having no allocated storage,
     * thus allowing empty files to not use any storage space except for the entry in Directory File.
     *
     * @return index of storage file which means entry does not have storage allocated.
     */
    int getNoStorageFileIndex();

}
