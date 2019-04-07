package mmk.vfs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * VFS Directory Entry interface. Allows reading directory contents.
 */
public interface VFSDirectory extends VFSEntry {
    /**
     * Reset this directory to first entry.
     *
     * @throws IOException I/O exception happened while accessing directory
     */
    void rewind() throws IOException;

    /**
     * Try reading next entry from this directory.
     *
     * @return next entry from directory or null if can't get more entries
     */
    DirEntry readNextEntry() throws IOException;

    /**
     * Read all childs of this directory and return them as array.
     *
     * @return array of all entries currently present in this directory.
     * @throws IOException I/O exception happened while accessing directory
     */
    default DirEntry[] getAllChilds() throws IOException {
        List<DirEntry> childs = new ArrayList<>();

        rewind();
        DirEntry nextEntry;
        while ((nextEntry = readNextEntry()) != null) {
            childs.add(nextEntry);
        }

        return childs.toArray(new DirEntry[0]);
    }

    /**
     * Entry in directory: either file or sub-directory.
     */
    interface DirEntry {
        /**
         * Get name of entry.
         *
         * @return name of entry
         */
        String getName();
    }

    /**
     * File in directory.
     */
    interface DirFile extends DirEntry {
        /**
         * Get current length of file.
         *
         * @return current length of file
         */
        int getLength();
    }
}
