package netfs.cache;

import java.util.LinkedHashMap;

/** Bounded LRU cache for file read windows. */
public final class CacheManager {
    private static final Object LOCK = new Object();
    private static final LinkedHashMap<String, CacheBlock> cache =
            new LinkedHashMap<>(16, 0.75f, true);
    private static int cacheSize;
    private static int maxFileCache;
    private static Thread cleanupThread;

    public static void start(int cacheSize, int maxFileCache) {
        CacheManager.cacheSize = Math.max(0, cacheSize);
        CacheManager.maxFileCache = Math.max(0, maxFileCache);
        synchronized (LOCK) {
            cache.clear();
            if (cleanupThread != null) {
                cleanupThread.interrupt();
            }
            cleanupThread = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Thread.sleep(1000);
                        clearCache();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }, "jnfs-cache-cleaner");
            cleanupThread.setDaemon(true);
            cleanupThread.start();
        }
    }

    public static void allocate(String path, long currentOffset, CacheBlock block) {
        if (maxFileCache == 0 || block == null || block.getData().length == 0) {
            return;
        }
        synchronized (LOCK) {
            cache.put(path, block);
            while (cache.size() > maxFileCache) {
                cache.remove(cache.keySet().iterator().next());
            }
        }
    }

    public static boolean deallocate(String path) {
        synchronized (LOCK) {
            return cache.remove(path) != null;
        }
    }

    public static CacheBlock getCache(String path) {
        synchronized (LOCK) {
            CacheBlock block = cache.get(path);
            if (block != null && block.isExpired()) {
                cache.remove(path);
                return null;
            }
            return block;
        }
    }

    public static int getCacheSize() {
        return cacheSize;
    }

    public static void clearCache() {
        synchronized (LOCK) {
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }
    }

    public static void evict(String path) {
        cache.remove(path);
    }
}
