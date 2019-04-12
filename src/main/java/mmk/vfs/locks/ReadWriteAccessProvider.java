package mmk.vfs.locks;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Storage File / Storage Block access provider. Allows getting shared read and exclusive write locks in blocking mode.
 */
public class ReadWriteAccessProvider extends AccessProvider {
    private final ReentrantReadWriteLock mReadWriteLock;

    public ReadWriteAccessProvider(Runnable doOnNoReferences) {
        super(doOnNoReferences);
        mReadWriteLock = new ReentrantReadWriteLock();
    }

    public Lock claimRead() throws InterruptedException {
        mReadWriteLock.readLock().lockInterruptibly();
        return createNewLock(false);
    }

    public Lock claimWrite() throws InterruptedException {
        mReadWriteLock.writeLock().lockInterruptibly();
        return createNewLock(true);
    }

    protected void releaseLock(Lock lock) {
        if (lock.isWriteLock()) {
            mReadWriteLock.writeLock().unlock();
        }
        else {
            mReadWriteLock.readLock().unlock();
        }
    }
}
