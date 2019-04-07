package mmk.vfs.util;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Utility class for reading/creating headers in files.
 */
public final class HeaderUtil {
    private HeaderUtil() {
    }

    /**
     * Read header from file if present or create new header and write it.
     *
     * @param fileChannel       file to read/write header
     * @param offset            offset in file where header must start
     * @param length            length of header to read/write
     * @param headerInitializer function to fill header with default values
     * @param readHeader        function to read header bytes and fill header object
     * @param writeHeader       function to write header bytes based on header object
     * @throws IOException i/O exception happened during read/write operations
     */
    public static void readOrCreateHeader(
            FileChannel fileChannel,
            int offset,
            int length,
            Runnable headerInitializer,
            HeaderFunction readHeader,
            HeaderFunction writeHeader
    ) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        if (fileChannel.size() > offset) {
            if (fileChannel.size() < offset + length) {
                throw getMalformedFileException("header size too small");
            }

            while (buffer.hasRemaining()) {
                if (-1 == fileChannel.read(buffer)) {
                    throw new EOFException();
                }
            }
            buffer.rewind().limit(buffer.capacity()); // entire row might be replaced by clear(), but word 'clear' is misleading

            readHeader.apply(buffer);
        }
        else {
            headerInitializer.run();
            writeHeader.apply(buffer);

            buffer.rewind().limit(buffer.capacity()); // entire row might be replaced by clear(), but word 'clear' is misleading
            while (buffer.hasRemaining()) {
                fileChannel.write(buffer);
            }
        }
    }

    /**
     * Utility function to create MalformedFileException instance in case of invalid headers in file.
     *
     * @param description description message to append into exception message
     * @return new MalformedFileException
     */
    public static IOException getMalformedFileException(String description) {
        return new IOException("Malformed file: " + description);
    }

    /**
     * Function to read or write data from/to header.
     */
    public interface HeaderFunction {
        /**
         * Read or write header from/to byte buffer.
         *
         * @param buffer buffer with header bytes for read operation, empty buffer for write operation
         * @throws IOException I/O happened during read/write operation
         */
        void apply(ByteBuffer buffer) throws IOException;
    }
}
