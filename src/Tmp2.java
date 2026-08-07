import netfs.config.ServerConfig;
import netfs.net.FileSystemServer;

import java.io.File;
import java.io.IOException;

public class Tmp2 {
    public static void main(String[] args) throws IOException, InterruptedException {
        FileSystemServer.start(new ServerConfig(1000000, 10002, 111, new File("/run/media/abhineet/56A4064AA4062CD5")));
    }
}
