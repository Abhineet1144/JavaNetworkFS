package netfs.net;

import netfs.config.ServerConfig;
import netfs.handler.ClientHandler;
import netfs.handler.ServerOperationStateHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ThreadFactory;

public class FileSystemServer {
    private static ServerOperationStateHandler operationStateHandler;
    private static ServerConfig config;
    private static boolean running = false;
    private static boolean shouldStop;
    private static int threadsUsed = 0;

    public static void start(ServerConfig config) throws IOException, InterruptedException {
        if (running) {
            throw new IllegalStateException("Server already running");
        }
        if (shouldStop) {
            throw new IllegalStateException("Server requested to stop");
        }

        FileSystemServer.config = config;
        operationStateHandler = new ServerOperationStateHandler();
        ThreadFactory threadFactory = config.getThreadBuilder().factory();
        try (ServerSocket serverSocket = new ServerSocket(config.getPort())) {
            while (!shouldStop) {
                Socket client = serverSocket.accept();
                while (threadsUsed > config.getMaxThreads()) {
                    Thread.sleep(3000);
                }
                threadFactory.newThread(new ClientHandler(client)).start();
            }
        }
    }

    public static void markSocketClose() {
        threadsUsed--;
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
    }

    public static ServerOperationStateHandler getOperationStateHandler() {
        return operationStateHandler;
    }
}
