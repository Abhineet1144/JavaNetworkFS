package netfs.net;

import netfs.config.ClientConfig;

/**
 * Headless entry point that runs a {@link FileSystemClient} mount in its own JVM
 * process. Launched as a child process by the UI's Mount tab: jnr-fuse's native FUSE
 * callbacks reliably crash the JVM with a native SIGSEGV when JavaFX is loaded in the
 * same process (see jnr-fuse GitHub issue #162 - "JVM crash with jnr-fuse and
 * javafx"), so the mount is kept in a separate, JavaFX-free process instead.
 */
public final class MountWorker {

    private MountWorker() {
    }

    public static void main(String[] args) {
        if (args.length != 7) {
            System.err.println("Usage: MountWorker <mountPoint> <host> <port> <mountOptions> "
                    + "<cacheSize> <maxFileCache> <maxServerConnector>");
            System.exit(2);
        }

        String mountPoint = args[0];
        String host = args[1];
        int port = Integer.parseInt(args[2]);
        boolean mountOptions = Boolean.parseBoolean(args[3]);
        int cacheSize = Integer.parseInt(args[4]);
        int maxFileCache = Integer.parseInt(args[5]);
        int maxServerConnector = Integer.parseInt(args[6]);

        ClientConfig config = new ClientConfig(mountPoint, host, port, mountOptions,
                cacheSize, maxFileCache, maxServerConnector);
        new FileSystemClient(config).start();
    }
}
