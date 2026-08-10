import netfs.config.ClientConfig;
import netfs.net.FileSystemClient;

public class Tmp1 {
    public static void main(String[] args) {
        ClientConfig clientConfig = new ClientConfig("/tmp/netfs1", "100.81.82.21", 10002, true, 0, 30, 3);
        FileSystemClient fileSystemClient = new FileSystemClient(clientConfig);
        fileSystemClient.start();
    }
}