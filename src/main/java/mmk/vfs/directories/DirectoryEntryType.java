package mmk.vfs.directories;

/**
 * Type of entry inside of directory 'file'
 */
public enum DirectoryEntryType {
    /**
     * Empty entry, either not used yet, or already deleted.
     */
    EMPTY,
    /**
     * File entry
     */
    FILE,
    /**
     * Directory Entry
     */
    DIRECTORY
    // Where might be also extension types, ex. to support longer file names
}
