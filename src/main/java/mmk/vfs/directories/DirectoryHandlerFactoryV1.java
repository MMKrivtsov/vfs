package mmk.vfs.directories;

import mmk.vfs.locks.LockType;
import mmk.vfs.storage.file.StorageFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class DirectoryHandlerFactoryV1 implements DirectoryHandlerFactory {
    @Override
    public DirectoryHandler createNewHandler(StorageFile storageFile) {
        return new DirectoryHandlerV1(storageFile);
    }

    @Override
    public int getNoStorageFileIndex() {
        return -1;
    }

    private static class DirectoryHandlerV1 implements DirectoryHandler {
        // byte 0     - type
        // byte 1-3   - reserved
        // byte 4-7   - file storage index
        // byte 8-11  - file size
        // byte 12-14 - reserved
        // byte 15    - fileName length bytes
        // byte 16-63 - fileName content
        private static final int ENTRY_LENGTH = 64;
        private static final byte TYPE_EMPTY = 0;
        private static final byte TYPE_FILE = 1;
        private static final byte TYPE_DIRECTORY = 2;
        private static final int MAX_FILENAME_LENGTH = ENTRY_LENGTH - 16;

        private static final byte[] EMPTY_FLAG_BYTE_AS_ARRAY = new byte[]{TYPE_EMPTY};
        private static final Charset FILENAME_ENCODING = StandardCharsets.UTF_16BE;

        private final StorageFile mStorageFile;
        private int mNextRecordReadId;

        public DirectoryHandlerV1(StorageFile storageFile) {
            this.mStorageFile = storageFile;

            rewind();
        }

        @Override
        public int getStorageContainerId() {
            return mStorageFile.getStorageStartIdx();
        }

        @Override
        public synchronized void rewind() {
            mNextRecordReadId = 0;
        }

        @Override
        public synchronized DirectoryEntry readNextEntry() throws IOException {
            DirectoryEntry entry;

            mStorageFile.claimLock(LockType.READ_LOCK);
            try {
                do {
                    entry = readEntry(mNextRecordReadId++);
                }
                while (entry != null && entry.getFileType() != DirectoryEntryType.FILE && entry.getFileType() != DirectoryEntryType.DIRECTORY);
            } finally {
                mStorageFile.releaseLock();
            }

            return entry;
        }

        @Override
        public synchronized void removeEntry(int directoryIndex) throws IOException {
            mStorageFile.claimLock(LockType.WRITE_LOCK);
            try {
                mStorageFile.writeBlock(directoryIndex * ENTRY_LENGTH, EMPTY_FLAG_BYTE_AS_ARRAY, 0, 1);
            } finally {
                mStorageFile.releaseLock();
            }
        }

        @Override
        public synchronized void addEntry(DirectoryEntry entry) throws IOException {
            if (DirectoryEntryType.EMPTY == entry.getFileType()) {
                throw new IllegalArgumentException("Can't write empty entry");
            }
            if (entry.getEntryName().isEmpty()) {
                throw new IllegalArgumentException("Can't write entry with empty name");
            }
            byte[] fileNameBytes = entry.getEntryName().getBytes(FILENAME_ENCODING);
            if (fileNameBytes.length > MAX_FILENAME_LENGTH) {
                throw new IllegalArgumentException("Can't write files with name longer than " + (MAX_FILENAME_LENGTH / 2) + " characters long");
            }

            ByteBuffer mainEntryByteBufferWrap = ByteBuffer.allocate(ENTRY_LENGTH);
            if (DirectoryEntryType.FILE == entry.getFileType()) {
                mainEntryByteBufferWrap.put(0, TYPE_FILE);
                mainEntryByteBufferWrap.putInt(8, entry.getFileLength());
            }
            else if (DirectoryEntryType.DIRECTORY == entry.getFileType()) {
                mainEntryByteBufferWrap.put(0, TYPE_DIRECTORY);
            }
            else {
                throw new IllegalArgumentException("Write of unsupported directory entry type " + entry.getFileType());
            }

            mainEntryByteBufferWrap.putInt(4, entry.getStorageStartIdx());
            mainEntryByteBufferWrap.put(15, (byte) fileNameBytes.length);
            mainEntryByteBufferWrap.position(16);
            mainEntryByteBufferWrap.put(fileNameBytes, 0, fileNameBytes.length);

            mStorageFile.claimLock(LockType.WRITE_LOCK);
            try {
                int entryIndex = 0;
                int entryOffset = 0;
                byte[] typeCheckingBuffer = new byte[1];
                int currentCapacity = mStorageFile.getCurrentCapacity();
                while (true) {
                    if (entryOffset >= currentCapacity) break;
                    mStorageFile.readBlock(entryOffset, typeCheckingBuffer, 0, 1);
                    if (typeCheckingBuffer[0] == TYPE_EMPTY) break;

                    ++entryIndex;
                    entryOffset = entryIndex * ENTRY_LENGTH;
                }

                mStorageFile.writeBlock(entryOffset, mainEntryByteBufferWrap.array(), 0, mainEntryByteBufferWrap.capacity());
            } finally {
                mStorageFile.releaseLock();
            }
        }

        @Override
        public synchronized void updateEntry(DirectoryEntry entry) throws IOException {
            int entryOffset = entry.getParentDirectoryIndex() * ENTRY_LENGTH;

            mStorageFile.claimLock(LockType.WRITE_LOCK);
            try {
                // update bytes 4-11
                ByteBuffer mainEntryByteBufferWrap = ByteBuffer.allocate(12);
                mainEntryByteBufferWrap.putInt(0, entry.getStorageStartIdx());
                mainEntryByteBufferWrap.putInt(4, entry.getFileLength());
                mStorageFile.writeBlock(entryOffset + 4, mainEntryByteBufferWrap.array(), 0, 8);
            } finally {
                mStorageFile.releaseLock();
            }
        }

        private boolean readFullyIfNoEof(int entryOffset, byte[] array, int offset, int length) throws IOException {
            int totalRead = 0;
            int read;

            while (totalRead < length) {
                read = mStorageFile.readBlock(entryOffset + totalRead, array, offset + totalRead, length - totalRead);
                if (read < 0) return false;
                totalRead += read;
            }

            return true;
        }

        @Override
        public synchronized DirectoryEntry readEntry(int entryPosition) throws IOException {
            return doReadEntry(entryPosition, true);
        }

        @Override
        public synchronized void close() throws IOException {
            mStorageFile.close();
        }

        public synchronized DirectoryEntry doReadEntry(int entryPosition, boolean claimLock) throws IOException {
            int entryOffset = entryPosition * ENTRY_LENGTH;

            DirectoryEntry entry = new DirectoryEntry();

            ByteBuffer mainEntryByteBufferWrap = ByteBuffer.allocate(ENTRY_LENGTH);

            if (claimLock) {
                mStorageFile.claimLock(LockType.READ_LOCK);
            }
            try {
                if (!readFullyIfNoEof(entryOffset, mainEntryByteBufferWrap.array(), 0, mainEntryByteBufferWrap.capacity())) {
                    return null;
                }
            } finally {
                if (claimLock) {
                    mStorageFile.releaseLock();
                }
            }

            byte type = mainEntryByteBufferWrap.get(0);
            if (type != TYPE_FILE && type != TYPE_DIRECTORY) {
                entry.setFileType(DirectoryEntryType.EMPTY);
                return entry;
            }

            entry.setParentDirectoryIndex(entryPosition);
            if (type == TYPE_DIRECTORY) {
                entry.setFileType(DirectoryEntryType.DIRECTORY);
            }
            else {
                entry.setFileType(DirectoryEntryType.FILE);
                entry.setFileLength(mainEntryByteBufferWrap.getInt(8));
            }

            entry.setStorageStartIdx(mainEntryByteBufferWrap.getInt(4));

            int nameLengthBytes = mainEntryByteBufferWrap.get(15);
            byte[] nameBytes = new byte[nameLengthBytes];
            mainEntryByteBufferWrap.position(16);
            mainEntryByteBufferWrap.get(nameBytes, 0, nameBytes.length);
            entry.setEntryName(new String(nameBytes, FILENAME_ENCODING));

            return entry;
        }

    }

}
