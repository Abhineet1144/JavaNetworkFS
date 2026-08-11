package netfs.net;

import netfs.config.ServerConfig;
import netfs.handler.ClientHandler;
import netfs.handler.ServerOperationStateHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class FileSystemServer {
    private static ServerOperationStateHandler operationStateHandler;
    private static ServerConfig config;
    private static volatile boolean running = false;
    private static volatile boolean shouldStop;
    private static volatile ServerSocket activeServerSocket;
    private static final AtomicInteger threadsUsed = new AtomicInteger();

    public static void start(ServerConfig config) throws IOException, InterruptedException {
        if (running) {
            throw new IllegalStateException("Server already running");
        }

        FileSystemServer.config = config;
        running = true;
        shouldStop = false;
        threadsUsed.set(0);
        operationStateHandler = new ServerOperationStateHandler();
        ThreadFactory threadFactory = config.getThreadBuilder().factory();
        try (ServerSocket serverSocket = new ServerSocket(config.getPort())) {
            activeServerSocket = serverSocket;
            System.out.println("[SERVER] Listening on port " + config.getPort()
                    + ", sharedFolder=" + config.getSharedFolder());
            while (!shouldStop) {
                Socket client;
                try {
                    client = serverSocket.accept();
                } catch (SocketException ex) {
                    if (shouldStop) {
                        break;
                    }
                    throw ex;
                }
                System.out.println("[SERVER] Accepted client: " + client.getRemoteSocketAddress());
                while (config.getMaxThreads() > 0 && threadsUsed.get() >= config.getMaxThreads()) {
                    Thread.sleep(3000);
                }
                threadsUsed.incrementAndGet();
                threadFactory.newThread(new ClientHandler(client)).start();
            }
            System.out.println("[SERVER] Stopped");
        } finally {
            activeServerSocket = null;
            running = false;
            shouldStop = false;
        }
    }

    public static void markSocketClose() {
        threadsUsed.updateAndGet(value -> Math.max(0, value - 1));
    }

    public static ServerConfig getConfig() {
        return config;
    }

    public static boolean shouldStop() {
        return shouldStop;
    }

    public static boolean isRunning() {
        return running;
    }

    public static void signalToStop() {
        shouldStop = true;
        ServerSocket serverSocket = activeServerSocket;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // Closing is best-effort; the server loop will observe shouldStop.
            }
        }
    }

    public static ServerOperationStateHandler getOperationStateHandler() {
        return operationStateHandler;
    }
}
