package mmk.vfs.locks;

import java.util.ArrayList;

/**
 * Lock manager of single entity. Allows getting one or several shared locks or single exclusive.
 */
public class EntityLocker {
    private static final long DEFAULT_WAIT_TIMEOUT = 100L;

    private final Runnable mDoOnNoReferences;
    private Object mResource;

    private int mReferences = 0;
    private final ArrayList<Lock> mReadLocks = new ArrayList<>();
    private Lock mWriteLock = null;

    public EntityLocker(Runnable doOnNoReferences, Object resource) {
        mDoOnNoReferences = doOnNoReferences;
        mResource = resource;
    }

    private Lock createNewLock(boolean writeLock) {
        return new Lock(this::releaseLock, writeLock);
    }

    /**
     * Try acquiring shared lock.
     *
     * @return claimed lock on success or null if lock can't be claimed
     */
    public synchronized Lock tryClaimRead() {
        if (mWriteLock != null) return null;

        Lock lock = createNewLock(false);
        mReadLocks.add(lock);
        return lock;
    }

    private synchronized boolean canClaimRead(Lock currentLock) {
        if (currentLock != null) {
            return !currentLock.isWriteLock() || mWriteLock == currentLock;
        }
        else {
            return mWriteLock == null;
        }
    }

    /**
     * Try acquiring shared lock, wait until success.
     *
     * @return claimed lock
     */
    public synchronized Lock claimRead(Lock currentLock) throws InterruptedException {
        while (!canClaimRead(currentLock)) {
            this.wait(DEFAULT_WAIT_TIMEOUT);
        }

        if (currentLock == null || currentLock.isWriteLock()) {
            Lock lock = createNewLock(false);

            if (currentLock != null) {
                if (mWriteLock == currentLock) {
                    mWriteLock = null;
                }
                else {
                    throw new IllegalStateException("Can't downgrade write lock when it is not current active write lock");
                }
            }
            mReadLocks.add(lock);

            return lock;
        }
        return currentLock;
    }

    /**
     * Try acquiring exclusive lock.
     *
     * @return claimed lock on success or null if lock can't be claimed
     */
    public synchronized Lock tryClaimWrite() {
        if (mWriteLock != null || !mReadLocks.isEmpty()) return null;

        Lock lock = createNewLock(true);
        mWriteLock = lock;
        return lock;
    }

    private synchronized boolean canClaimWrite(Lock currentLock) {
        if (currentLock != null) {
            return mWriteLock == null || mWriteLock == currentLock;
        }
        else {
            return mWriteLock != null || mReadLocks.isEmpty();
        }
    }

    /**
     * Try acquiring exclusive lock, wait until success.
     *
     * @return claimed lock
     */
    public synchronized Lock claimWrite(Lock currentLock) throws InterruptedException {
        while (!canClaimWrite(currentLock)) {
            this.wait(DEFAULT_WAIT_TIMEOUT);
        }

        if (currentLock == null || !currentLock.isWriteLock()) {
            Lock lock = createNewLock(true);

            if (currentLock != null) {
                mReadLocks.remove(currentLock);
            }
            mWriteLock = lock;

            return lock;
        }

        return currentLock;
    }

    private synchronized void releaseLock(Lock lock) {
        if (lock.isWriteLock()) {
            if (mWriteLock == lock) {
                mWriteLock = null;
            }
        }
        else {
            mReadLocks.remove(lock);
        }
        this.notifyAll();
    }

    public synchronized boolean isAnyLocked() {
        return mWriteLock != null || !mReadLocks.isEmpty();
    }

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

    public boolean isReferenced() {
        return mReferences != 0;
    }
}
