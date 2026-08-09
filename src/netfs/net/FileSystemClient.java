package netfs.net;
import netfs.client.ConnectionPool;
import netfs.config.ClientConfig;
import netfs.diskio.KernelFSHandler;
import java.nio.file.Paths;

public class FileSystemClient {

    private KernelFSHandler kernelFSHandler;
    private ClientConfig clientConfig;

    public FileSystemClient(ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
        kernelFSHandler = new KernelFSHandler(clientConfig.getHost(), clientConfig.getPort(),
                clientConfig.getCacheSize(), clientConfig.getMaxFileCache());
    }

    public void start() {
        ConnectionPool.start(clientConfig.getMaxServerConnector());
        System.out.println("Mounting drive at: " + clientConfig.getMountPoint());

        // Mount options:
        // - true: run in foreground (so console stays open)
        // - false: run in background
       kernelFSHandler.mount(Paths.get(clientConfig.getMountPoint()), true, false, new String[] {
               "-o", "max_read=131072",   // 1MB
               "-o", "max_write=131072"
       });
    }

    public ClientConfig getClientConfig() {
        return clientConfig;
    }
}
