package mmk.vfs.locks;

import java.util.function.Consumer;

/**
 * Handle of claimed lock. Can be released.
 */
public class Lock {
    private final Consumer<Lock> mReleaseFunction;
    private final boolean mWriteLock;
    private boolean mClaimed = true;

    public Lock(Consumer<Lock> releaseFunction, boolean writeLock) {
        mReleaseFunction = releaseFunction;
        mWriteLock = writeLock;
    }

    /**
     * Release this lock.
     */
    public void release() {
        if (mClaimed) {
            mReleaseFunction.accept(this);
            mClaimed = false;
        }
    }

    /**
     * Get whether this ock is exclusive or shared.
     *
     * @return true if this lock is exclusive
     */
    public boolean isWriteLock() {
        return mWriteLock;
    }
}
