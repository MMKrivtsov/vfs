package mmk.vfs.impl;

import mmk.vfs.*;
import mmk.vfs.directories.DirectoryEntry;
import mmk.vfs.directories.DirectoryEntryType;
import mmk.vfs.directories.DirectoryHandler;
import mmk.vfs.directories.DirectoryHandlerFactory;
import mmk.vfs.exceptions.DirectoryNotEmptyException;
import mmk.vfs.exceptions.FileAlreadyExistsException;
import mmk.vfs.exceptions.RootDirectoryModificationException;
import mmk.vfs.exceptions.VFSClosedException;
import mmk.vfs.locks.AccessProviderManager;
import mmk.vfs.locks.FileAccessProvider;
import mmk.vfs.locks.LockType;
import mmk.vfs.storage.file.StorageFile;
import mmk.vfs.storage.file.StorageFileManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.*;

public class VirtualFileSystemImpl implements VirtualFileSystem {
    public static final int MIN_BLOCK_SIZE = 1 << 8;
    public static final int MAX_BLOCK_SIZE = 1 << 16;
    public static final int DEFAULT_BLOCK_SIZE = 1 << 8;

    private boolean mIsClosed = false;

    private final Object mFileTreeModificationLock = new Object();

    private final StorageFileManager mStorage;
    private final DirectoryHandlerFactory mDirectoryHandlerFactory;

    private final AccessProviderManager<String> mAccessProviderManager;
    private final Set<VFSEntry> mOpenedEntries = new HashSet<>();

    public VirtualFileSystemImpl(StorageFileManager storage, DirectoryHandlerFactory directoryHandlerFactory) {
        mStorage = storage;
        mDirectoryHandlerFactory = directoryHandlerFactory;
        mAccessProviderManager = new AccessProviderManager<>(FileAccessProvider::new);
    }

    private static String[] parsePath(String path) {
        String[] pathParts = path.replace("\\", "/").split("/");

        boolean needToCompact = false;
        for (int i = 0; i < pathParts.length; ++i) {
            pathParts[i] = pathParts[i].trim();
            if (pathParts[i].isEmpty() || ".".equals(pathParts[i]) || "..".equals(pathParts[i])) {
                needToCompact = true;
            }
        }
        if (needToCompact) {
            int ptr = 0;
            String[] pathPartsTmp = new String[pathParts.length];
            for (String pathPart : pathParts) {
                if (pathPart.isEmpty()) {
                    ptr = 0;
                    continue;
                }
                else if (".".equals(pathPart)) {
                    continue;
                }
                else if ("..".equals(pathPart)) {
                    if (ptr == 0) throw new IllegalArgumentException("Trying to go higher that root directory");
                    --ptr;
                }
                pathPartsTmp[ptr] = pathPart;
                ++ptr;
            }
            pathParts = Arrays.copyOf(pathPartsTmp, ptr);
        }
        return pathParts;
    }

    private static String concatPath(String[] path, int length) {
        StringJoiner pathStr = new StringJoiner("/");
        pathStr.add("");
        for (int i = 0; i < length; ++i) {
            pathStr.add(path[i]);
        }
        return pathStr.toString();
    }

    // called from synchronized code
    private VFSEntryImpl findEntry(String[] path, int pathLength) throws IOException {
        VFSEntryImpl entry = new VFSDirectoryImpl(this);

        for (int i = 0; i < pathLength; ++i) {
            VFSDirectoryImpl directory;
            if (entry instanceof VFSDirectoryImpl) {
                directory = (VFSDirectoryImpl) entry;
            }
            else {
                return null;
            }

            VFSEntry entryInDir = null;
            directory.lock(LockType.READ_LOCK);
            try {
                VFSEntry nextEntry;
                while ((nextEntry = directory.readNextInternalEntry()) != null) {
                    if (nextEntry.getName().equals(path[i])) {
                        entryInDir = nextEntry;
                        break;
                    }
                }
            } finally {
                directory.close();
            }

            if (entryInDir == null) {
                return null;
            }
            entry = (VFSEntryImpl) entryInDir;
        }

        return entry;
    }

    private VFSFileImpl findFileEntry(String[] path, int pathLength) throws IOException {
        VFSEntryImpl entry = findEntry(path, pathLength);
        return (entry instanceof VFSFileImpl) ? (VFSFileImpl) entry : null;
    }

    private VFSDirectoryImpl findDirEntry(String[] path, int pathLength) throws IOException {
        VFSEntry entry = findEntry(path, pathLength);
        return (entry instanceof VFSDirectoryImpl) ? (VFSDirectoryImpl) entry : null;
    }

    public boolean exists(String path) throws IOException {
        String[] parsedPath = parsePath(path);
        if (parsedPath.length == 0) return true;

        synchronized (mFileTreeModificationLock) {
            if (mIsClosed) throw new VFSClosedException();

            return findEntry(parsedPath, parsedPath.length) != null;
        }
    }

    public void createDir(String path) throws IOException {
        String[] parsedPath = parsePath(path);
        if (parsedPath.length == 0) throw new RootDirectoryModificationException("Root directory already exists");

        synchronized (mFileTreeModificationLock) {
            if (mIsClosed) throw new VFSClosedException();

            VFSDirectoryImpl dirEntry = findDirEntry(parsedPath, parsedPath.length - 1);
            if (dirEntry == null) {
                throw new FileNotFoundException("Not a directory: " + concatPath(parsedPath, parsedPath.length - 1));
            }
            try {
                DirectoryEntry newDirectoryEntry = new DirectoryEntry();
                newDirectoryEntry.setFileLength(0);
                newDirectoryEntry.setStorageStartIdx(mDirectoryHandlerFactory.getNoStorageFileIndex());
                newDirectoryEntry.setEntryName(parsedPath[parsedPath.length - 1]);
                newDirectoryEntry.setFileType(DirectoryEntryType.DIRECTORY);

                dirEntry.lock(LockType.READ_LOCK);
                VFSEntry nextEntry;
                while ((nextEntry = dirEntry.readNextInternalEntry()) != null) {
                    if (nextEntry.getName().equals(newDirectoryEntry.getEntryName())) {
                        throw new FileAlreadyExistsException();
                    }
                }
                dirEntry.addEntry(newDirectoryEntry);
            } finally {
                dirEntry.close();
            }
        }
    }

    public VFSDirectory openDir(String path) throws IOException {
        String[] parsedPath = parsePath(path);

        synchronized (mFileTreeModificationLock) {
            if (mIsClosed) throw new VFSClosedException();

            VFSDirectoryImpl dirEntry = findDirEntry(parsedPath, parsedPath.length);
            if (dirEntry == null) throw new FileNotFoundException("File not found");

            dirEntry.lock(LockType.READ_LOCK);
            mOpenedEntries.add(dirEntry);

            return dirEntry;
        }
    }

    public void createFile(String path) throws IOException {
        String[] parsedPath = parsePath(path);
        if (parsedPath.length == 0)
            throw new RootDirectoryModificationException("Can't create file in place of root directory");

        synchronized (mFileTreeModificationLock) {
            if (mIsClosed) throw new VFSClosedException();

            VFSDirectoryImpl dirEntry = findDirEntry(parsedPath, parsedPath.length - 1);
            if (dirEntry == null) {
                throw new IllegalArgumentException("Not a directory: " + concatPath(parsedPath, parsedPath.length - 1));
            }
            try {
                DirectoryEntry newDirectoryEntry = new DirectoryEntry();
                newDirectoryEntry.setFileLength(0);
                newDirectoryEntry.setStorageStartIdx(mDirectoryHandlerFactory.getNoStorageFileIndex());
                newDirectoryEntry.setEntryName(parsedPath[parsedPath.length - 1]);
                newDirectoryEntry.setFileType(DirectoryEntryType.FILE);

                dirEntry.lock(LockType.READ_LOCK);
                VFSEntry nextEntry;
                while ((nextEntry = dirEntry.readNextInternalEntry()) != null) {
                    if (nextEntry.getName().equals(newDirectoryEntry.getEntryName())) {
                        throw new FileAlreadyExistsException();
                    }
                }
                dirEntry.addEntry(newDirectoryEntry);
            } finally {
                dirEntry.close();
            }
        }
    }

    public VFSFile openFile(String path, FileOpenMode fileOpenMode) throws IOException {
        String[] parsedPath = parsePath(path);
        if (parsedPath.length == 0) throw new RootDirectoryModificationException("Can't open root directory as file");

        synchronized (mFileTreeModificationLock) {
            if (mIsClosed) throw new VFSClosedException();

            VFSFileImpl entry = findFileEntry(parsedPath, parsedPath.length);
            if (entry == null) throw new FileNotFoundException("File not found");

            LockType lockTypeFromFileOpenType = null;

            if (FileOpenMode.READ == fileOpenMode) {
                lockTypeFromFileOpenType = LockType.READ_LOCK;
            }
            else if (FileOpenMode.READ_WRITE == fileOpenMode) {
                lockTypeFromFileOpenType = LockType.WRITE_LOCK;
            }

            entry.lock(lockTypeFromFileOpenType);
            mOpenedEntries.add(entry);
            return entry;
        }
    }

    public void delete(String path) throws IOException {
        String[] parsedPath = parsePath(path);
        if (parsedPath.length == 0) throw new RootDirectoryModificationException("Can't delete root directory");

        synchronized (mFileTreeModificationLock) {
            if (mIsClosed) throw new VFSClosedException();

            VFSDirectoryImpl parentDirectory = findDirEntry(parsedPath, parsedPath.length - 1);
            if (parentDirectory == null) throw new FileNotFoundException("Directory not found");

            try {
                VFSEntryImpl entryToDelete = null;
                VFSEntry nextEntry;
                while ((nextEntry = parentDirectory.readNextInternalEntry()) != null) {
                    if (nextEntry.getName().equals(parsedPath[parsedPath.length - 1])) {
                        entryToDelete = (VFSEntryImpl) nextEntry;
                        break;
                    }
                }

                if (entryToDelete == null) {
                    throw new FileNotFoundException("There is no file or directory at " + concatPath(parsedPath, parsedPath.length));
                }

                if (entryToDelete instanceof VFSDirectoryImpl) {
                    VFSDirectoryImpl subDir = (VFSDirectoryImpl) entryToDelete;
                    if (subDir.readNextEntry() != null) {
                        throw new DirectoryNotEmptyException();
                    }
                }

                entryToDelete.freeStorage();
                parentDirectory.removeEntry(parsedPath[parsedPath.length - 1]);
            } finally {
                parentDirectory.close();
            }
        }
    }

    public void close() {
        synchronized (mFileTreeModificationLock) {
            if (!mIsClosed) {
                mIsClosed = true;

                for (VFSEntry entry : new ArrayList<>(mOpenedEntries)) {
                    try {
                        entry.close();
                    } catch (IOException ignored) {
                    }
                }

                if (mStorage != null) {
                    mStorage.close();
                }
            }
        }
    }

    StorageFileManager getStorage() {
        return mStorage;
    }

    DirectoryHandlerFactory getDirectoryHandlerFactory() {
        return mDirectoryHandlerFactory;
    }

    int allocateStorageForEntry(VFSEntryImpl entry) throws IOException {
        try (DirectoryHandler handler = mDirectoryHandlerFactory.createNewHandler(mStorage.getStorageFile(entry.mParentDirectoryId))) {
            DirectoryEntry dirEntry = handler.readEntry(entry.mParentDirectoryOffset);

            StorageFile newFile = mStorage.createNewFile();
            dirEntry.setStorageStartIdx(newFile.getStorageStartIdx());
            newFile.close();

            try {
                handler.updateEntry(dirEntry);
            } catch (InterruptedIOException exception) {
                throw new InterruptedIOException("File creation interrupted, VFS corrupted (Allocated block is not referenced, can't be used nor reused)");
            }

            return newFile.getStorageStartIdx();
        }
    }

    int getStorageIdForEntry(VFSEntryImpl entry) throws IOException {
        try (DirectoryHandler handler = mDirectoryHandlerFactory.createNewHandler(mStorage.getStorageFile(entry.mParentDirectoryId))) {
            DirectoryEntry dirEntry = handler.readEntry(entry.mParentDirectoryOffset);
            return dirEntry.getStorageStartIdx();
        }
    }

    int getRootDirectoryStorageIndex() {
        return mStorage.getRootDirectoryStorageIndex();
    }

    int getFileLengthFor(VFSFileImpl file) throws IOException {
        try (DirectoryHandler handler = mDirectoryHandlerFactory.createNewHandler(mStorage.getStorageFile(file.mParentDirectoryId))) {
            DirectoryEntry dirEntry = handler.readEntry(file.mParentDirectoryOffset);
            return dirEntry.getFileLength();
        }
    }

    void entryClosed(VFSEntryImpl vfsEntry) {
        synchronized (mFileTreeModificationLock) {
            mOpenedEntries.remove(vfsEntry);
        }
    }

    AccessProviderManager<String> getAccessProviderManager() {
        return mAccessProviderManager;
    }

}
