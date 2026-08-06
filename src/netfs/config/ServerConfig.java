package netfs.config;

import java.io.File;
import java.lang.Thread.Builder;

public class ServerConfig {
    private final File sharedFolder;
    private final int port;
    private final long maxSize;
    private final int maxThreads;
    private Builder threadBuilder;

    public ServerConfig(long maxSize, int port, int maxThreads, File sharedFile) {
        this(sharedFile, maxSize, port, maxThreads, Thread.ofVirtual());
    }

    public ServerConfig(File sharedFile, long maxSize, int port, int maxThreads, Builder threadBuilder) {
        this.sharedFolder = sharedFile;
        this.maxSize = maxSize;
        this.port = port;
        this.maxThreads = maxThreads;
        this.threadBuilder = threadBuilder;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public int getPort() {
        return port;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public Builder getThreadBuilder() {
        return threadBuilder;
    }

    public File getSharedFolder() {
        return sharedFolder;
    }
}
