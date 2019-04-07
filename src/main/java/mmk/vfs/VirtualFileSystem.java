package mmk.vfs;

import mmk.vfs.exceptions.DirectoryNotEmptyException;
import mmk.vfs.exceptions.RootDirectoryModificationException;
import mmk.vfs.exceptions.VFSClosedException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;

/**
 * Main interface of Virtual File Systems.
 */
public interface VirtualFileSystem extends AutoCloseable {

    /**
     * Check whether given path points to existing file/directory or not.
     *
     * @param path path to check for existence
     * @return true if this path points to existing file or directory
     * @throws VFSClosedException     if this instance of VFS was already closed
     * @throws InterruptedIOException if thread was interrupted
     */
    boolean exists(String path) throws IOException;

    /**
     * Create new directory at specified path. Method is not recursive, so parent directory must already exist.
     *
     * @param path path to create directory at
     * @throws VFSClosedException                 if this instance of VFS was already closed
     * @throws RootDirectoryModificationException if trying to create directory at path '/'
     * @throws FileNotFoundException              if parent directory of required path does not exist
     * @throws InterruptedIOException             if thread was interrupted
     * @throws IOException                        other I/O exceptions possible from underlying directory writer and file storage manager
     */
    void createDir(String path) throws IOException;

    /**
     * Open directory to read list of files from it.
     *
     * @param path path to directory, which is to be opened
     * @return directory handler to read directory contents
     * @throws VFSClosedException     if this instance of VFS was already closed
     * @throws FileNotFoundException  if there is no directory at this path
     * @throws InterruptedIOException if thread was interrupted
     * @throws IOException            other I/O exceptions possible from underlying directory writer and file storage manager
     */
    VFSDirectory openDir(String path) throws IOException;

    /**
     * Create new file at specified path. Method is not recursive, so parent directory must already exist.
     *
     * @param path path to create directory at
     * @throws VFSClosedException                 if this instance of VFS was already closed
     * @throws RootDirectoryModificationException if trying to create file at path '/'
     * @throws FileNotFoundException              if parent directory of required path does not exist
     * @throws InterruptedIOException             if thread was interrupted
     * @throws IOException                        other I/O exceptions possible from underlying directory writer and file storage manager
     */
    void createFile(String path) throws IOException;

    /**
     * Open file to read or read-and-write.
     *
     * @param path         path to file, which is to be opened
     * @param fileOpenMode file access type, see {@link FileOpenMode} for details
     * @return file handler to read and (optionally) write contents
     * @throws VFSClosedException                 if this instance of VFS was already closed
     * @throws RootDirectoryModificationException if trying to open file at path '/'
     * @throws FileNotFoundException              if there is no directory at this path
     * @throws InterruptedIOException             if thread was interrupted
     * @throws IOException                        other I/O exceptions possible from underlying directory writer and file storage manager
     */
    VFSFile openFile(String path, FileOpenMode fileOpenMode) throws IOException;

    /**
     * Delete file or directory at specified path, this path must not be opened by openDir or openFile.
     * Method is not recursive, so to delete directory, first all files in directory must be closed and deleted.
     *
     * @param path path to delete file or directory at
     * @throws VFSClosedException                 if this instance of VFS was already closed
     * @throws RootDirectoryModificationException if trying to delete path '/'
     * @throws FileNotFoundException              if there is no entry at this path
     * @throws DirectoryNotEmptyException         if this path points to non-empty directory
     * @throws IOException                        other I/O exceptions possible from underlying directory writer and file storage manager
     */
    void delete(String path) throws IOException;

    /**
     * Close this instance of VFS. Closes all opened files and directories, then closes underlying storage.
     */
    void close();
}
