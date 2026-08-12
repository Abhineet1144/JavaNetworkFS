package netfs.operations;

import java.io.IOException;
import java.io.PrintWriter;

import netfs.client.ServerConnection;
import netfs.diskio.JNFSInputStream;
import netfs.diskio.KernelFSHandler;

public class RenameOperation extends Operation {

    private final String oldPath;
    private final String newPath;

    public RenameOperation(String oldPath, String newPath) {
        this.oldPath = oldPath;
        this.newPath = newPath;
        operationType = OperationType.RENAME;
    }

    @Override
    public String getDetail() {
        return oldPath + " -> " + newPath;
    }

    @Override
    public void execute(ServerConnection connection) throws IOException {
        var i = connection.getInputStream();
        sendRequest("rename:" + oldPath, connection);
        sendRequest(newPath, connection);
        String resp = JNFSInputStream.readLine(i);
        if (isSuccess(resp)) {
            KernelFSHandler.map.remove(oldPath);
            KernelFSHandler.map.put(newPath, resp);
        }
    }

}
