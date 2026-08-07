import netfs.config.ClientConfig;
import netfs.net.FileSystemClient;

public class Tmp1 {
    public static void main(String[] args) {
        ClientConfig clientConfig = new ClientConfig("/tmp/netfs1", "localhost", 10002, true);
        FileSystemClient fileSystemClient = new FileSystemClient(clientConfig);
        fileSystemClient.start();
    }
}