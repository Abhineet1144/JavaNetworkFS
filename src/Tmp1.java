import netfs.client.ConnectionPool;
import netfs.config.ClientConfig;
import netfs.net.FileSystemClient;

public class Tmp1 {
    public static void main(String[] args) {
        ClientConfig clientConfig = new ClientConfig("/tmp/netfs1", "localhost", 10002, true, 10485760, 30);
        FileSystemClient fileSystemClient = new FileSystemClient(clientConfig);
        ConnectionPool.start(3);
        fileSystemClient.start();
    }
}