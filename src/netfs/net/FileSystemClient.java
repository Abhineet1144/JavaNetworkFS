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
        ConnectionPool.start(clientConfig.getMaxServerConnector(), clientConfig.getHost(), clientConfig.getPort());
        System.out.println("[CLIENT] Mounting drive at: " + clientConfig.getMountPoint());

        // Mount options:
        // - true: run in foreground (so console stays open)
        // - false: run in background
        // "-s": force libfuse's single-threaded dispatch loop. Multi-threaded FUSE
        // dispatch crashes the JVM with a native SIGSEGV inside libfuse's jffi/libffi
        // callback trampoline on this libfuse/JDK combination (see hs_err_pid*.log).
       kernelFSHandler.mount(Paths.get(clientConfig.getMountPoint()), true, false, new String[] {
               "-s",
               "-o", "max_read=131072",   // 1MB
               "-o", "max_write=131072"
       });
    }

    public ClientConfig getClientConfig() {
        return clientConfig;
    }

    public KernelFSHandler getKernelFSHandler() {
        return kernelFSHandler;
    }
}
