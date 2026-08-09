package netfs.operations;

import java.io.IOException;
import java.io.PrintWriter;

import netfs.client.ServerConnection;
import netfs.diskio.JNFSInputStream;
import netfs.diskio.KernelFSHandler;

public class TruncateOperation extends Operation {

    private final String path;
    private final long size;

    public TruncateOperation(String path, long size) {
        this.path = path;
        this.size = size;
        operationType = OperationType.TRUNCATE;
    }

    @Override
    public void execute(ServerConnection connection) throws IOException {
            var i = connection.getInputStream();
            PrintWriter printWriter = new PrintWriter(connection.getOutputStream(), true);
            printWriter.println("truncate:" + path);
            printWriter.println(size);
            String resp = JNFSInputStream.readLine(i);
            if (isSuccess(resp)) {
                KernelFSHandler.map.remove(path);
                KernelFSHandler.map.put(path, resp);
            }
    }
}
