package netfs.operations;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import jnr.ffi.Pointer;
import netfs.client.ServerConnection;

public class WriteOperation extends Operation {

    private final String path;
    private final Pointer buf;
    private final long size;
    private final long offset;
    private int bytesWrote = 0;

    public WriteOperation(String path, Pointer buf, long size, long offset) {
        this.path = path;
        this.buf = buf;
        this.size = size;
        this.offset = offset;
        operationType = OperationType.WRITE;
    }

    @Override
    public String getDetail() {
        return path + " (offset=" + offset + ", size=" + size + ")";
    }

    @Override
    public void execute(ServerConnection connection) throws IOException {
        var i = connection.getOutputStream();
        byte[] dataToWrite = new byte[(int) size];
        buf.get(0, dataToWrite, 0, (int) size);
        sendRequest("write:" + path + ":" + offset + ":" + dataToWrite.length, connection);
        new DataOutputStream(i).write(dataToWrite);
        bytesWrote = (int) size;
        System.out.println("[CLIENT] Remote write sent path=" + path + ", offset=" + offset + ", bytes=" + bytesWrote);
    }

    public int getBytesWrote() {
        return bytesWrote;
    }
}
