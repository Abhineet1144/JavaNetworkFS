package netfs.operations;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

import netfs.client.ServerConnection;
import netfs.diskio.JNFSInputStream;
import netfs.diskio.KernelFSHandler;

public class CreateOperation extends Operation {

    private final String path;

    public CreateOperation(String path) {
        this.path = path;
        operationType = OperationType.CREATE;
    }

    @Override
    public void execute(ServerConnection connection) throws IOException {
        var i = connection.getInputStream();
        new PrintWriter(connection.getOutputStream(), true).println("create:" + path);
        String resp = JNFSInputStream.readLine(i);
        if (isSuccess(resp)) {
            KernelFSHandler.map.put(path, "1:0");
        }
    }
}
