package mmk.vfs.locks;

import java.io.InterruptedIOException;

/**
 * Utility class for wrapping lock claiming, storage and release to lessen code redundancy in other files.
 */
public class ReadWriteLockContainer {
    private final EntityLocker mLocker;
    private boolean mClosed;
    private transient Lock mLock;

    public ReadWriteLockContainer(EntityLocker locker) {
        this.mLocker = locker;
        mClosed = false;
        mLocker.addReference();
    }

    public synchronized void claimLock(LockType lockType) throws InterruptedIOException {
        if (mClosed) throw new IllegalStateException("Lock already closed");

        if (lockType == LockType.READ_LOCK) {
            try {
                mLock = mLocker.claimRead(mLock);
            } catch (InterruptedException e) {
                throw new InterruptedIOException();
            }
        }
        else if (lockType == LockType.WRITE_LOCK) {
            try {
                mLock = mLocker.claimWrite(mLock);
            } catch (InterruptedException e) {
                throw new InterruptedIOException();
            }
        }
    }

    public synchronized boolean tryClaimLock(LockType lockType) {
        if (mClosed) throw new IllegalStateException("Lock already closed");

        if (mLock != null) throw new IllegalStateException("Lock already claimed");

        if (lockType == LockType.READ_LOCK) {
            mLock = mLocker.tryClaimRead();
        }
        else if (lockType == LockType.WRITE_LOCK) {
            mLock = mLocker.tryClaimWrite();
        }

        return mLock != null;
    }

    public synchronized void releaseLock() {
        if (mLock != null) {
            mLock.release();
            mLock = null;
        }
    }

    public synchronized boolean isReadLocked() {
        return mLock != null;
    }

    public synchronized boolean isWriteLocked() {
        return mLock != null && mLock.isWriteLock();
    }

    public synchronized void close() {
        if (!mClosed) {
            mClosed = true;
            releaseLock();
            mLocker.removeReference();
        }
    }
}
