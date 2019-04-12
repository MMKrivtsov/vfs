package mmk.vfs.file;

import mmk.vfs.VirtualFileSystem;
import mmk.vfs.directories.DirectoryHandlerFactory;
import mmk.vfs.directories.DirectoryHandlerFactoryV1;
import mmk.vfs.file.headers.ContainerStorageHeader;
import mmk.vfs.file.headers.ContainerStorageHeaderV1;
import mmk.vfs.file.headers.DirectoryHandlerHeader;
import mmk.vfs.file.headers.DirectoryHandlerHeaderV1;
import mmk.vfs.impl.VirtualFileSystemImpl;
import mmk.vfs.storage.blocks.BlockStorageManager;
import mmk.vfs.storage.blocks.FileBlockStorageManager;
import mmk.vfs.storage.file.StorageFileManager;
import mmk.vfs.storage.file.StorageFileManagerV1;
import mmk.vfs.util.HeaderUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/**
 * Utility class for creation of Virtual File System using file for data storage.
 */
public final class FileBasedVirtualFileSystem {
    private static final byte[] FILE_HEADER_MAGIC = new byte[]{(byte) 'V', (byte) 'F', (byte) 'S', (byte) 'F'};

    private static final byte HEADER_IMPLEMENTATION_VERSION_1 = 1;
    private static final byte STORAGE_IMPLEMENTATION_VERSION_1 = 1;
    private static final byte DIRECTORY_IMPLEMENTATION_VERSION_1 = 1;

    /**
     * Open File-based Virtual File System.
     *
     * @param backingFile file where VFS should store its contents
     * @return opened VFS
     * @throws IOException I/O exception happened while opening VFS
     */
    public static VirtualFileSystem open(File backingFile) throws IOException {
        return open(backingFile, VirtualFileSystemImpl.DEFAULT_BLOCK_SIZE);
    }

    /**
     * Open File-based Virtual File System.
     *
     * @param backingFile file where VFS should store its contents
     * @param blockSize   size of allocation blocks used to store files inside VFS
     * @return opened VFS
     * @throws IOException I/O exception happened while opening VFS
     */
    public static VirtualFileSystem open(File backingFile, int blockSize) throws IOException {
        if (blockSize < VirtualFileSystemImpl.MIN_BLOCK_SIZE) {
            throw new IllegalArgumentException("Block Size must not be less than " + VirtualFileSystemImpl.MIN_BLOCK_SIZE);
        }
        else if (blockSize > VirtualFileSystemImpl.MAX_BLOCK_SIZE) {
            throw new IllegalArgumentException("Block Size must not be less than " + VirtualFileSystemImpl.MAX_BLOCK_SIZE);
        }
        if (backingFile.isDirectory()) {
            throw new IllegalArgumentException("File provided is a directory, can't be used");
        }

        FileChannel fileChannel = FileChannel.open(backingFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);

        StorageFileManager storage = null;
        DirectoryHandlerFactory directoryHandlerFactory = null;
        try {
            VfsHeader vfsHeader = new VfsHeader();
            HeaderUtil.readOrCreateHeader(
                    fileChannel, 0, 8,
                    () -> {
                        vfsHeader.mVersion = HEADER_IMPLEMENTATION_VERSION_1;
                        vfsHeader.mStorageVersion = STORAGE_IMPLEMENTATION_VERSION_1;
                        vfsHeader.mDirectoryVersion = DIRECTORY_IMPLEMENTATION_VERSION_1;
                    },
                    vfsHeader::read,
                    vfsHeader::write
            );

            ContainerStorageHeader storageHeader = getStorageImplementationHeader(vfsHeader.mStorageVersion);
            DirectoryHandlerHeader directoryHeader = getDirectoryHandlerImplementationHeader(vfsHeader.mStorageVersion);

            int totalHeadersLength = 8 + storageHeader.getLength() + directoryHeader.getLength();

            int storageHeaderOffsetInFile = 8;
            HeaderUtil.readOrCreateHeader(
                    fileChannel, storageHeaderOffsetInFile, storageHeader.getLength(),
                    () -> {
                        int paddedStartOffset = (totalHeadersLength + blockSize - 1) / blockSize;
                        paddedStartOffset *= blockSize;

                        storageHeader.mBlockSize = blockSize;
                        storageHeader.mFileStartOffset = paddedStartOffset;
                    },
                    storageHeader::read,
                    storageHeader::write
            );

            int directoryHeaderOffsetInFile = storageHeaderOffsetInFile + storageHeader.getLength();
            HeaderUtil.readOrCreateHeader(
                    fileChannel, directoryHeaderOffsetInFile, directoryHeader.getLength(),
                    () -> {},
                    directoryHeader::read,
                    directoryHeader::write
            );

            BlockStorageManager blockStorageManager = new FileBlockStorageManager(fileChannel, storageHeader.mFileStartOffset, storageHeader.mBlockSize);
            storage = getStorageImplementation(vfsHeader.mStorageVersion, blockStorageManager);
            directoryHandlerFactory = getDirectoryHandlerImplementation(vfsHeader.mDirectoryVersion);
        } catch (IOException exc) {
            if (storage != null) {
                storage.close();
            }
            try {
                fileChannel.close();
            } catch (Exception ignored) {
            }
            throw exc;
        }

        return new VirtualFileSystemImpl(storage, directoryHandlerFactory);
    }

    private static ContainerStorageHeader getStorageImplementationHeader(byte version) {
        if (version == STORAGE_IMPLEMENTATION_VERSION_1) {
            return new ContainerStorageHeaderV1();
        }
        else {
            throw getUnsupportedVersionException("Content storage version " + Integer.toHexString(version & 0xFF));
        }
    }

    private static StorageFileManager getStorageImplementation(byte version, BlockStorageManager blockStorageManager) throws IOException {
        if (version == STORAGE_IMPLEMENTATION_VERSION_1) {
            return new StorageFileManagerV1(blockStorageManager);
        }
        else {
            throw getUnsupportedVersionException("Content storage version " + Integer.toHexString(version & 0xFF));
        }
    }

    private static DirectoryHandlerHeader getDirectoryHandlerImplementationHeader(byte version) {
        if (version == DIRECTORY_IMPLEMENTATION_VERSION_1) {
            return new DirectoryHandlerHeaderV1();
        }
        else {
            throw getUnsupportedVersionException("Directory records version " + Integer.toHexString(version & 0xFF));
        }
    }

    private static DirectoryHandlerFactory getDirectoryHandlerImplementation(byte version) throws IOException {
        if (version == STORAGE_IMPLEMENTATION_VERSION_1) {
            return new DirectoryHandlerFactoryV1();
        }
        else {
            throw getUnsupportedVersionException("Content storage version " + Integer.toHexString(version & 0xFF));
        }
    }

    private static IllegalArgumentException getUnsupportedVersionException(String description) {
        return new IllegalArgumentException("Unsupported version: " + description);
    }

    private static class VfsHeader {
        byte mVersion;
        byte mStorageVersion;
        byte mDirectoryVersion;

        void read(ByteBuffer buffer) throws IOException {
            byte[] bufferArray = buffer.array();

            for (int i = 0; i < 4; ++i) {
                if (bufferArray[i] != FILE_HEADER_MAGIC[i]) {
                    throw HeaderUtil.getMalformedFileException("Incorrect header magic sequence");
                }
            }

            mVersion = bufferArray[4];
            mStorageVersion = bufferArray[5];
            mDirectoryVersion = bufferArray[6];
        }

        void write(ByteBuffer buffer) {
            byte[] bufferArray = buffer.array();
            System.arraycopy(FILE_HEADER_MAGIC, 0, bufferArray, 0, 4);
            bufferArray[4] = mVersion;
            bufferArray[5] = mStorageVersion;
            bufferArray[6] = mDirectoryVersion;
            bufferArray[7] = 0;
        }
    }
}
