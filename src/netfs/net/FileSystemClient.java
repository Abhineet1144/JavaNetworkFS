package netfs.net;
import netfs.config.ClientConfig;
import netfs.diskio.KernelFSHandler;
import java.nio.file.Paths;

public class FileSystemClient {

    private KernelFSHandler kernelFSHandler;
    private ClientConfig clientConfig;

    public FileSystemClient(ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
        kernelFSHandler = new KernelFSHandler(clientConfig.getHost(), clientConfig.getPort(), clientConfig.getCacheSize(), clientConfig.getMaxCache());
    }

    public void start() {
        System.out.println("Mounting drive at: " + clientConfig.getMountPoint());

        // Mount options:
        // - true: run in foreground (so console stays open)
        // - false: run in background
       kernelFSHandler.mount(Paths.get(clientConfig.getMountPoint()), clientConfig.getMountOptions());
    }

    public ClientConfig getClientConfig() {
        return clientConfig;
    }
}
