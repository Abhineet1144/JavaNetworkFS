package netfs.config;

public class ClientConfig {
    private final String mountPoint;
    private final String host;
    private final int port;
    private final boolean mountOptions;

    public ClientConfig(String mountPoint, String host, int port, boolean mountOptions) {
        this.mountPoint = mountPoint;
        this.host = host;
        this.port = port;
        this.mountOptions = mountOptions;
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
}
