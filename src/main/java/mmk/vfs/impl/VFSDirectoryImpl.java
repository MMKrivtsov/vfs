package mmk.vfs.impl;

import mmk.vfs.VFSDirectory;
import mmk.vfs.VFSEntry;
import mmk.vfs.directories.DirectoryEntry;
import mmk.vfs.directories.DirectoryHandler;
import mmk.vfs.exceptions.EntryClosedException;
import mmk.vfs.storage.file.StorageFile;

import java.io.IOException;

import static mmk.vfs.directories.DirectoryEntryType.DIRECTORY;
import static mmk.vfs.directories.DirectoryEntryType.FILE;

public class VFSDirectoryImpl extends VFSEntryImpl implements VFSDirectory {
    private DirectoryHandler mDirectoryHandler;
    private boolean isRootDir = false;

    public VFSDirectoryImpl(VirtualFileSystemImpl vfs) {
        super("", "", vfs, -1, -1);
        isRootDir = true;
    }

    public VFSDirectoryImpl(String fileName, String filePath, VirtualFileSystemImpl vfs, int parentDirectoryFileIdx, int parentDirectoryOffset) {
        super(fileName, filePath, vfs, parentDirectoryFileIdx, parentDirectoryOffset);
    }

    @Override
    protected int getStorageContainerId() throws IOException {
        if (isRootDir) {
            return mVfs.getRootDirectoryStorageIndex();
        }
        return super.getStorageContainerId();
    }

    @Override
    public synchronized void close() throws IOException {
        if (mDirectoryHandler != null) {
            mDirectoryHandler.close();
        }
        super.close();
    }

    private void ensureDirectoryHandlerExists() throws IOException {
        if (mDirectoryHandler == null) {
            int storageContainerId = getStorageContainerId();
            if (storageContainerId == mVfs.getDirectoryHandlerFactory().getNoStorageFileIndex()) {
                storageContainerId = mVfs.allocateStorageForEntry(this);
            }
            StorageFile storageFile = mVfs.getStorage().getStorageFile(storageContainerId);
            mDirectoryHandler = mVfs.getDirectoryHandlerFactory().createNewHandler(storageFile);
        }
    }

    private DirectoryEntry readNextChild() throws IOException {
        ensureDirectoryHandlerExists();
        return mDirectoryHandler.readNextEntry();
    }

    void addEntry(DirectoryEntry newDirectoryEntry) throws IOException {
        ensureDirectoryHandlerExists();
        mDirectoryHandler.addEntry(newDirectoryEntry);
    }

    void removeEntry(String name) throws IOException {
        ensureDirectoryHandlerExists();
        mDirectoryHandler.rewind();

        DirectoryEntry nextEntry;
        while ((nextEntry = mDirectoryHandler.readNextEntry()) != null) {
            if ((FILE == nextEntry.getFileType() || DIRECTORY == nextEntry.getFileType()) && nextEntry.getEntryName().equals(name)) {
                mDirectoryHandler.removeEntry(nextEntry.getParentDirectoryIndex());
                break;
            }
        }
    }

    @Override
    public void rewind() throws IOException {
        ensureDirectoryHandlerExists();
        mDirectoryHandler.rewind();
    }

    @Override
    public DirEntry readNextEntry() throws IOException {
        DirectoryEntry nextEntry = readNextChild();
        if (nextEntry != null) {
            if (FILE == nextEntry.getFileType()) {
                return new DirFile() {
                    @Override
                    public int getLength() {
                        return nextEntry.getFileLength();
                    }

                    @Override
                    public String getName() {
                        return nextEntry.getEntryName();
                    }
                };
            }
            else if (DIRECTORY == nextEntry.getFileType()) {
                return nextEntry::getEntryName;
            }
        }
        return null;
    }

    VFSEntry readNextInternalEntry() throws IOException {
        if (mIsClosed) throw new EntryClosedException();

        DirectoryEntry nextEntry = readNextChild();
        if (nextEntry != null) {
            int storageContainerId = mDirectoryHandler.getStorageContainerId();

            String fileName = nextEntry.getEntryName();
            String path = mFilePath + "/" + fileName;
            if (FILE == nextEntry.getFileType()) {
                return new VFSFileImpl(fileName, path, mVfs, storageContainerId, nextEntry.getParentDirectoryIndex());
            }
            else if (DIRECTORY == nextEntry.getFileType()) {
                return new VFSDirectoryImpl(fileName, path, mVfs, storageContainerId, nextEntry.getParentDirectoryIndex());
            }
        }

        return null;
    }
}
