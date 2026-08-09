package netfs.operations;

import java.io.IOException;
import java.io.PrintWriter;

import netfs.client.ServerConnection;
import netfs.diskio.JNFSInputStream;
import netfs.diskio.KernelFSHandler;

public class RmdirOperation extends Operation {

    private final String path;

    public RmdirOperation(String path) {
        this.path = path;
        operationType = OperationType.RMDIR;
    }

    @Override
    public void execute(ServerConnection connection) throws IOException {
        var i = connection.getInputStream();
        sendRequest("rmdir:" + path, connection);
        String resp = JNFSInputStream.readLine(i);
        if (isSuccess(resp)) {
            KernelFSHandler.map.remove(path);
        }
    }
}
