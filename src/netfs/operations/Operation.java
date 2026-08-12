package netfs.operations;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;

import netfs.client.ServerConnection;
import netfs.diskio.JNFSOutputStream;

public abstract class Operation {

    protected OperationType operationType;

    private final CountDownLatch completion = new CountDownLatch(1);

    public abstract void execute(ServerConnection connection) throws IOException;

    public void complete() {
        completion.countDown();
    }

    public void waitForCompletion() throws InterruptedException {
        completion.await();
    }

    public boolean isSuccess(String resp) {
        return !resp.equals("F");
    }

    public OperationType getOperationType() {
        return operationType;
    }

    /** Short human-readable description (typically the target path) used for logging/UI. */
    public String getDetail() {
        return "";
    }

    protected void sendRequest(String req, ServerConnection connection) throws IOException {
        JNFSOutputStream.writeLine(connection.getOutputStream(), req);
    }
}