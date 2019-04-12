package mmk.vfs.locks;

import java.util.concurrent.Semaphore;

/**
 * Access Provider for files. Allows getting multiple shared READ locks but only exclusive WRITE lock in non-blocking approach.
 * Returns null Lock if can't lock required lock, so locking code can handle this as 'file already opened' exception.
 */
public class FileAccessProvider extends AccessProvider {
    private Semaphore mSemaphore;

    public FileAccessProvider(Runnable doOnNoReferences) {
        super(doOnNoReferences);

        mSemaphore = new Semaphore(Integer.MAX_VALUE);
    }

    @Override
    public Lock claimRead() throws InterruptedException {
        if (mSemaphore.tryAcquire(1)) {
            return createNewLock(false);
        }
        return null;
    }

    @Override
    public Lock claimWrite() throws InterruptedException {
        if (mSemaphore.tryAcquire(Integer.MAX_VALUE)) {
            return createNewLock(true);
        }
        return null;
    }

    @Override
    protected void releaseLock(Lock lock) {
        if (lock.isWriteLock()) {
            mSemaphore.release(Integer.MAX_VALUE);
        }
        else {
            mSemaphore.release(1);
        }
    }
}
