package mmk.vfs.locks;

import java.io.InterruptedIOException;

/**
 * Utility class for wrapping lock claiming, storage and release to lessen code redundancy in other files.
 */
public class AccessController {
    private final AccessProvider mLocker;
    private boolean mClosed;
    private transient Lock mLock;

    public AccessController(AccessProvider locker) {
        this.mLocker = locker;
        mClosed = false;
        mLocker.addReference();
    }

    public synchronized boolean claimLock(LockType lockType) throws InterruptedIOException {
        if (mClosed) throw new IllegalStateException("Lock already closed");

        if (mLock != null) throw new IllegalStateException("Lock already claimed");

        if (lockType == LockType.READ_LOCK) {
            try {
                mLock = mLocker.claimRead();
            } catch (InterruptedException e) {
                throw new InterruptedIOException();
            }
        }
        else if (lockType == LockType.WRITE_LOCK) {
            try {
                mLock = mLocker.claimWrite();
            } catch (InterruptedException e) {
                throw new InterruptedIOException();
            }
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
