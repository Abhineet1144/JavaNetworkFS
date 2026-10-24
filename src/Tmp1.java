import netfs.config.ClientConfig;
import netfs.net.FileSystemClient;

public class Tmp1 {
    public static void main(String[] args) {
        ClientConfig clientConfig = new ClientConfig("/home/tejas/club", "localhost", 10002, true, 10485760, 30);
        FileSystemClient fileSystemClient = new FileSystemClient(clientConfig);
        fileSystemClient.start();
    }
}