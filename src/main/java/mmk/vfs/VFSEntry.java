package mmk.vfs;

import java.io.IOException;

/**
 * Base of file system entry (file and directory) handles .
 */
public interface VFSEntry extends AutoCloseable {
    /**
     * Get entry (file / directory) name.
     *
     * @return entry name
     */
    String getName();

    /**
     * Close this handle, releasing all claimed resources.
     *
     * @throws IOException I/O exception happened during close operation
     */
    void close() throws IOException;
}
