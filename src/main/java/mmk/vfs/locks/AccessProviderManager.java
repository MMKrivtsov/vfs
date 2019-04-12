package mmk.vfs.locks;

import javax.xml.ws.Provider;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lock manager for multiple objects.
 *
 * @param <T> type of resource identifier
 */
public class AccessProviderManager<T> {
    private final Map<T, AccessProvider> mLockTable = new ConcurrentHashMap<>();
    private final AccessProviderConstructor mAccessProviderConstructor;

    public AccessProviderManager(AccessProviderConstructor accessProviderConstructor) {
        mAccessProviderConstructor = accessProviderConstructor;
    }

    /**
     * Get lock manager for resource.
     *
     * @param resourceId resource to get lock manager for
     * @return lock manager for resource
     */
    public AccessProvider getLockerForPath(T resourceId) {
        return mLockTable.computeIfAbsent(resourceId, p -> mAccessProviderConstructor.newInstance(() -> checkLockerForRemoval(p)));
    }

    /**
     * Check resource lock manager and remove it if it is not used anymore.
     *
     * @param resourceId resource to check (and possibly remove) lock manager for
     */
    public void checkLockerForRemoval(T resourceId) {
        mLockTable.computeIfPresent(resourceId, (p, locker) -> locker.isReferenced() ? locker : null);
    }

    public interface AccessProviderConstructor {
        AccessProvider newInstance(Runnable doOnNoReferences);
    }
}
