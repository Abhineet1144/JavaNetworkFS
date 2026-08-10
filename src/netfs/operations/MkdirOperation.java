package netfs.operations;

import java.io.IOException;

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
    public String getDetail() {
        return path;
    }

    @Override
    public void execute(ServerConnection connection) throws IOException {
        var i = connection.getInputStream();
        sendRequest("mkdir:" + path, connection);
        String resp = JNFSInputStream.readLine(i);
        if (isSuccess(resp)) {
            KernelFSHandler.map.put(path, resp);
        }
    }
}
