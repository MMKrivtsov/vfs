package mmk.vfs.locks;

import java.util.concurrent.Semaphore;

/**
 * Storage File / Storage Block access provider. Allows getting shared read and exclusive write locks in blocking mode.
 */
public class ReadWriteAccessProvider extends AccessProvider {
    private Semaphore mSemaphore;

    public ReadWriteAccessProvider(Runnable doOnNoReferences) {
        super(doOnNoReferences);

        mSemaphore = new Semaphore(Integer.MAX_VALUE, true);
    }

    public Lock claimRead() throws InterruptedException {
        mSemaphore.acquire(1);
        return createNewLock(false);
    }

    public Lock claimWrite() throws InterruptedException {
        mSemaphore.acquire(Integer.MAX_VALUE);
        return createNewLock(true);
    }

    protected void releaseLock(Lock lock) {
        if (lock.isWriteLock()) {
            mSemaphore.release(Integer.MAX_VALUE);
        }
        else {
            mSemaphore.release(1);
        }
    }
}
