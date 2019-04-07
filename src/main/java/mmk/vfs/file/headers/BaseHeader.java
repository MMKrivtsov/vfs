package mmk.vfs.file.headers;

import java.nio.ByteBuffer;

public abstract class BaseHeader {
    private final int mLength;

    protected BaseHeader(int length) {
        mLength = length;
    }

    public final int getLength() {
        return mLength;
    }

    public abstract void read(ByteBuffer byteBuffer);

    public abstract void write(ByteBuffer byteBuffer);
}
