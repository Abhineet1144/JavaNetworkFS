package netfs.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import netfs.operations.Operation;

public class ServerConnection implements Runnable {
    private Socket socket;
    private boolean inUse;
    private int id;
    private String host;
    private int port;

    public ServerConnection(int id, String host, int port) {
        this.id = id;
        this.host = host;
        this.port = port;
    }

    @Override
    public void run() {
        try {
            socket = new Socket(host, port);
            System.out.println("[CLIENT] Server connection " + id + " connected");
            while (!Thread.currentThread().isInterrupted()) {
                Operation operation = ConnectionPool.getOperationQueue().take();
                System.out.println("[CLIENT] Worker " + id + " executing operation: " + operation.getOperationType());
                try {
                    operation.execute(this);
                } finally {
                    System.out.println("[CLIENT] Worker " + id + " completed operation: " + operation.getOperationType());
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
