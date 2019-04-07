package mmk.vfs.directories;

import java.util.Objects;

/**
 * Entry in Directory 'file'.
 */
public class DirectoryEntry {
    /**
     * Name of entryy
     */
    private String mEntryName;
    /**
     * Type of this entry.
     */
    private DirectoryEntryType mFileType;
    /**
     * File length if type is FILE.
     */
    private int mFileLength;
    /**
     * StorageFile index where contents of this file start
     */
    private int mStorageStartIdx;
    /**
     * Index of this entry in parent directory. Must only be assigned by DirectoryHandler.
     */
    private int mParentDirectoryIndex;

    public DirectoryEntry() {
    }

    public String getEntryName() {
        return mEntryName;
    }

    public void setEntryName(String mEntryName) {
        this.mEntryName = mEntryName;
    }

    public DirectoryEntryType getFileType() {
        return mFileType;
    }

    public void setFileType(DirectoryEntryType mFileType) {
        this.mFileType = mFileType;
    }

    public int getFileLength() {
        return mFileLength;
    }

    public void setFileLength(int mFileLength) {
        this.mFileLength = mFileLength;
    }

    public int getStorageStartIdx() {
        return mStorageStartIdx;
    }

    public void setStorageStartIdx(int mStorageStartIdx) {
        this.mStorageStartIdx = mStorageStartIdx;
    }

    public int getParentDirectoryIndex() {
        return mParentDirectoryIndex;
    }

    public void setParentDirectoryIndex(int parentDirectoryIndex) {
        mParentDirectoryIndex = parentDirectoryIndex;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj.getClass() == getClass()) {
            DirectoryEntry other = (DirectoryEntry) obj;
            return Objects.equals(mEntryName, other.mEntryName) &&
                   Objects.equals(mFileType, other.mFileType) &&
                   mFileLength == other.mFileLength &&
                   mStorageStartIdx == other.mStorageStartIdx &&
                   mParentDirectoryIndex == other.mParentDirectoryIndex;
        }
        return false;
    }
}
