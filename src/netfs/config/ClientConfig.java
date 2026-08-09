package netfs.config;

public class ClientConfig {
    private final String mountPoint;
    private final String host;
    private final int port;
    private final boolean mountOptions;
    private final int cacheSize;
    private final int maxFileCache;
    private final int maxServerConnector;

    public ClientConfig(String mountPoint, String host, int port, boolean mountOptions, int cacheSize, int maxFileCache,
            int maxServerConnector) {
        this.mountPoint = mountPoint;
        this.host = host;
        this.port = port;
        this.mountOptions = mountOptions;
        this.cacheSize = cacheSize;
        this.maxFileCache = maxFileCache;
        this.maxServerConnector = maxServerConnector;
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

    public int getCacheSize() {
        return cacheSize;
    }

    public int getMaxFileCache() {
        return maxFileCache;
    }

    public int getMaxServerConnector() {
        return maxServerConnector;
    }
}
