package netfs.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CacheManager {
    private static final Map<String, CacheBlock> cache = new ConcurrentHashMap<>();
    private static int cacheSize;
    private static int maxFileCache;

    public static void start(int cacheSize, int maxFileCache) {
        CacheManager.cacheSize = cacheSize;
        CacheManager.maxFileCache = maxFileCache;

        Runnable cleanupTask = () -> {
            while (true) {
                clearCache();

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        };

        Thread cleanupThread = new Thread(cleanupTask);
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    public static void allocate(String path, long currentOffset, CacheBlock block) {
        CacheBlock existing = getCache(path);
        if (existing == null) {
            if (cache.size() < maxFileCache) {
                cache.put(path, block);
            }
            return;
        }
        if (cache.containsKey(path) || cache.size() < maxFileCache) {
            cache.put(path, block);
        }
    }

    public static boolean deallocate(String path) {
        return cache.remove(path) != null;
    }

    public static CacheBlock getCache(String path) {
        return cache.get(path);
    }

    public static Map<String, CacheBlock> getCache() {
        return cache;
    }

    public static int getCacheSize() {
        return cacheSize;
    }

    public static void clearCache() {
        for (Map.Entry<String, CacheBlock> entry : cache.entrySet()) {
            if (entry.getValue().isExpired()) {
                String path = entry.getKey();
                long prevCache = cache.size();
                boolean isRemoved = deallocate(entry.getKey());
                if (isRemoved) {
                    System.out.println("CACHE REMOVED: " + path + " " + prevCache + " -> " + cache.size());
                    System.gc();
                }
            }
        }
    }

    public static void evict(String path) {
        cache.remove(path);
    }
}
