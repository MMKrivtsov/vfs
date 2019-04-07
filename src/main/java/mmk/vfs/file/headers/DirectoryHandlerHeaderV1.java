package mmk.vfs.file.headers;

import java.nio.ByteBuffer;

public class DirectoryHandlerHeaderV1 extends DirectoryHandlerHeader {
    private static final int HEADER_LENGTH = 0;

    public DirectoryHandlerHeaderV1() {
        super(HEADER_LENGTH);
    }

    @Override
    public void read(ByteBuffer byteBuffer) {
    }

    @Override
    public void write(ByteBuffer byteBuffer) {
    }
}
