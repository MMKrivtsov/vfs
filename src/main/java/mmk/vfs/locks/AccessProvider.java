package mmk.vfs.locks;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Lock manager of single entity. Allows getting one or several shared locks or single exclusive.
 */
public abstract class AccessProvider {
    private final Runnable mDoOnNoReferences;

    private int mReferences = 0;

    public AccessProvider(Runnable doOnNoReferences) {
        mDoOnNoReferences = doOnNoReferences;
    }

    protected Lock createNewLock(boolean writeLock) {
        return new Lock(this::releaseLock, writeLock);
    }

    /**
     * Acquire shared read lock.
     *
     * @return claimed lock
     */
    public abstract Lock claimRead() throws InterruptedException;

    /**
     * Acquire exclusive write lock.
     *
     * @return claimed lock
     */
    public abstract Lock claimWrite() throws InterruptedException;

    protected abstract void releaseLock(Lock lock);

    public synchronized void addReference() {
        ++mReferences;
    }

    public synchronized void removeReference() {
        if (mReferences > 0) {
            --mReferences;
        }
        if (mReferences == 0) {
            mDoOnNoReferences.run();
        }
    }

    public synchronized boolean isReferenced() {
        return mReferences != 0;
    }
}
