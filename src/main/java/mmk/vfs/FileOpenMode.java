package mmk.vfs;

/**
 * File access mode.
 */
public enum FileOpenMode {
    /**
     * File opened for read only. Requests shared lock on file, so file can be opened for read access several times simultaneously.
     * File can't be opened for Read access while it is already opened for read-write access.
     */
    READ,
    /**
     * File opened for read and write. Requests exclusive lock on file, which forbids parallel opens of any type.
     * File can't be opened for Read-Write access while it is already opened for read or read-write access.
     */
    READ_WRITE
}
