package netfs.config;

public class ClientConfig {
    private final String mountPoint;
    private final String host;
    private final int port;
    private final boolean mountOptions;
    private final long maxCache;
    private final long cacheSize;

    public ClientConfig(String mountPoint, String host, int port, boolean mountOptions, long maxCache, long cacheSize) {
        this.mountPoint = mountPoint;
        this.host = host;
        this.port = port;
        this.mountOptions = mountOptions;
        this.maxCache = maxCache;
        this.cacheSize = cacheSize;
    }

    public String getMountPoint() {
        return mountPoint;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean getMountOptions() {
        return mountOptions;
    }

    public long getMaxCache() {
        return maxCache;
    }

    public long getCacheSize() {
        return cacheSize;
    }
}
