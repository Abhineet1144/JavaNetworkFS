package netfs.operations;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

import netfs.client.ServerConnection;
import netfs.diskio.JNFSInputStream;

public class MkdirOperation extends Operation{

    private final String path;
    private final Map<String, String> map;

    public MkdirOperation(String path, Map<String, String> map) {
        this.path = path;
        this.map = map;
        operationType = OperationType.MKDIR;
    }

    @Override
    public void execute(ServerConnection connection) throws IOException {
        var i = connection.getInputStream();
        new PrintWriter(connection.getOutputStream(), true).println("mkdir:" + path);
        String resp = JNFSInputStream.readLine(i);
        if (isSuccess(resp)) {
            map.put(path, resp);
        }
    }
}
