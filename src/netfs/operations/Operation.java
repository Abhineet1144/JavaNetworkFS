package netfs.operations;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;

import netfs.client.ServerConnection;

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

    protected PrintWriter sendRequest(String req, ServerConnection connection) {
        PrintWriter printWriter = new PrintWriter(connection.getOutputStream(), true);
        printWriter.println(req);
        return printWriter;
    }
}