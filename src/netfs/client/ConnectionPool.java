package netfs.client;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import netfs.operations.Operation;

public class ConnectionPool {
    private static ArrayList<ServerConnection> serverConnections;
    private static BlockingQueue<Operation> operationQueue = new LinkedBlockingQueue<>();

    public static void start(int totalServerConnectors) {
        serverConnections = new ArrayList<>(totalServerConnectors);

        for (int i = 0; i < totalServerConnectors; i++) {
            ServerConnection connection = new ServerConnection();

            Thread thread = new Thread(connection);
            thread.start();

            serverConnections.add(connection);
        }
    }

    public static BlockingQueue<Operation> getOperationQueue() {
        return operationQueue;
    }
}
