package mmk.vfs.locks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lock manager for multiple objects.
 *
 * @param <T> type of resource identifier
 */
public class EntityLockManager<T> {
    private final Map<T, EntityLocker> mLockTable = new ConcurrentHashMap<>();

    /**
     * Get lock manager for resource.
     *
     * @param resourceId resource to get lock manager for
     * @return lock manager for resource
     */
    public EntityLocker getLockerForPath(T resourceId) {
        return mLockTable.computeIfAbsent(resourceId, p -> new EntityLocker(() -> checkLockerForRemoval(p), p));
    }

    /**
     * Check resource lock manager and remove it if it is not used anymore.
     *
     * @param resourceId resource to check (and possibly remove) lock manager for
     */
    public void checkLockerForRemoval(T resourceId) {
        mLockTable.computeIfPresent(resourceId, (p, locker) -> locker.isReferenced() ? locker : null);
    }
}
