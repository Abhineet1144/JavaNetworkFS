package netfs.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import netfs.operations.Operation;

public class ServerConnection implements Runnable {
    private Socket socket;
    private boolean inUse;
    private int name;

    public ServerConnection(int name) {
        this.name = name;
    }

    @Override
    public void run() {
        try {
            socket = new Socket("localhost", 10002);
            System.out.println("[CLIENT] Server connection " + name + " connected");
            while (!Thread.currentThread().isInterrupted()) {
                Operation operation = ConnectionPool.getOperationQueue().take();
                System.out.println("[CLIENT] Worker " + name + " executing operation: " + operation.getOperationType());
                try {
                    operation.execute(this);
                } finally {
                    System.out.println("[CLIENT] Worker " + name + " completed operation: " + operation.getOperationType());
                    operation.complete();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public OutputStream getOutputStream() {
        try {
            return socket.getOutputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public InputStream getInputStream() {
        try {
            return socket.getInputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isInUse() {
        return inUse;
    }

    public void setInUse(boolean inUse) {
        this.inUse = inUse;
    }
}
