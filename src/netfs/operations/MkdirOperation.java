package netfs.operations;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

import netfs.client.ServerConnection;
import netfs.diskio.JNFSInputStream;
import netfs.diskio.KernelFSHandler;

public class MkdirOperation extends Operation{

    private final String path;

    public MkdirOperation(String path) {
        this.path = path;
        operationType = OperationType.MKDIR;
    }

    @Override
    public void execute(ServerConnection connection) throws IOException {
        var i = connection.getInputStream();
        new PrintWriter(connection.getOutputStream(), true).println("mkdir:" + path);
        String resp = JNFSInputStream.readLine(i);
        if (isSuccess(resp)) {
            KernelFSHandler.map.put(path, resp);
        }
    }
}
