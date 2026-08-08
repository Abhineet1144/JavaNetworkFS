import netfs.config.ServerConfig;
import netfs.net.FileSystemServer;

import java.io.File;
import java.io.IOException;

public class Tmp2 {
    public static void main(String[] args) throws IOException, InterruptedException {
        FileSystemServer.start(new ServerConfig(1000000, 10002, 111, new File("/mnt/wwn-0x5000c500c67d4454-part1")));
    }
}
