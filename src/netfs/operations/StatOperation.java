package netfs.operations;

import java.io.IOException;
import java.io.PrintWriter;

import netfs.client.ServerConnection;
import netfs.diskio.JNFSInputStream;
import netfs.handler.CommandConsts;

public class StatOperation extends Operation {

    private final String path;
    private String response;

    public StatOperation(String path) {
        this.path = path;
        operationType = OperationType.STAT;
    }

    @Override
    public void execute(ServerConnection connection) throws IOException {
        new PrintWriter(connection.getOutputStream(), true)
                .println(CommandConsts.Prefixes.STAT_CMD + path);
        response = JNFSInputStream.readLine(connection.getInputStream());
        if (response == null) {
            throw new IOException("Connection closed while reading stat for " + path);
        }
    }

    public String getResponse() {
        return response;
    }
}
