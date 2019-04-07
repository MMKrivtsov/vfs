package mmk.vfs;

import java.io.IOException;

/**
 * Opened file handle in VFS. Allows reading and (if opened for write) - writing of contents into this file.
 */
public interface VFSFile extends VFSEntry {
    /**
     * Get current length of this file.
     *
     * @return current length of file
     * @throws IOException I/O exception happened during operation
     */
    int getLength() throws IOException;

    /**
     * Set current read/write pointer of file relative to file start.
     *
     * @param offsetFromStart offset from beginning of file
     */
    void seek(int offsetFromStart);

    /**
     * Read block of data from file. Read operation starts from current pointer in file, then pointer advances to next
     * byte after last successfully read byte.
     *
     * @param buffer       destination buffer to read into
     * @param bufferOffset offset in destination buffer where read bytes must be placed
     * @param length       length of bytes to read from file
     * @return amount of successfully read bytes, -1 if there are no more bytes left in file
     * @throws IOException I/O exception happened during operation
     */
    int read(byte[] buffer, int bufferOffset, int length) throws IOException;

    /**
     * Write block of data to file, expanding it if necessary. Write operation starts from current pointer in file, then pointer advances to next
     * byte after last successfully written byte. File must be opened for write for this method to succeed.
     *
     * @param buffer       source buffer to read bytes from
     * @param bufferOffset offset in source buffer from where bytes must be written to file
     * @param length       length of bytes to write to file
     * @throws IOException I/O exception happened during operation
     */
    void write(byte[] buffer, int bufferOffset, int length) throws IOException;
}
