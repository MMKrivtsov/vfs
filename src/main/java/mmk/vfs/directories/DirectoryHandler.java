package mmk.vfs.directories;

import java.io.IOException;

public interface DirectoryHandler extends AutoCloseable {
    int getStorageContainerId();

    void rewind();

    DirectoryEntry readNextEntry() throws IOException;

    void removeEntry(int position) throws IOException;

    void addEntry(DirectoryEntry entry) throws IOException;

    void updateEntry(DirectoryEntry entry) throws IOException;

    DirectoryEntry readEntry(int entryPosition) throws IOException;

    void close() throws IOException;
}
