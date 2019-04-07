package mmk.vfs.file.headers;

public abstract class ContainerStorageHeader extends BaseHeader {
    public int mBlockSize;
    public int mFileStartOffset;

    protected ContainerStorageHeader(int length) {
        super(length);
    }
}
