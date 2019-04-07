package mmk.vfs.file.headers;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

public class ContainerStorageHeaderV1 extends ContainerStorageHeader {
    private static final int HEADER_LENGTH = 8;

    public ContainerStorageHeaderV1() {
        super(HEADER_LENGTH);
    }

    public void read(ByteBuffer buffer) {
        IntBuffer intBuffer = buffer.order(ByteOrder.BIG_ENDIAN).asIntBuffer();
        mBlockSize = intBuffer.get();
        mFileStartOffset = intBuffer.get();
    }

    public void write(ByteBuffer buffer) {
        IntBuffer intBuffer = buffer.order(ByteOrder.BIG_ENDIAN).asIntBuffer();
        intBuffer.put(mBlockSize);
        intBuffer.put(mFileStartOffset);
    }
}
