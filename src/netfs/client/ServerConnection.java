package netfs.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

import netfs.operations.Operation;
import netfs.operations.OperationType;
import netfs.operations.ReadOperation;
import netfs.operations.WriteOperation;
import netfs.state.OperationLog;
import netfs.state.OperationLogEntry;
import netfs.state.OperationSource;
import netfs.state.TransferStats;

public class ServerConnection implements Runnable {
    private static final long TRANSFER_EMIT_INTERVAL_NANOS = 250_000_000L;
    private static final AtomicLong LAST_TRANSFER_EMIT_NANOS = new AtomicLong();

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
                boolean verboseOperation = operation.getOperationType() != OperationType.READ;
                if (verboseOperation) {
                    System.out.println("[CLIENT] Worker " + id + " executing operation: " + operation.getOperationType());
                }
                OperationLogEntry logEntry = OperationLog.start(OperationSource.MOUNT,
                        operation.getOperationType().name(), operation.getDetail());
                long startNanos = System.nanoTime();
                try {
                    operation.execute(this);
                    OperationLog.complete(logEntry);
                    emitOpLog(operation, "COMPLETED", startNanos, null);
                    trackAndEmitTransfer(operation);
                } catch (IOException e) {
                    OperationLog.fail(logEntry, e.getMessage());
                    emitOpLog(operation, "FAILED", startNanos, e.getMessage());
                    throw e;
                } finally {
                    if (verboseOperation) {
                        System.out.println("[CLIENT] Worker " + id + " completed operation: " + operation.getOperationType());
                    }
                    operation.complete();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Prints a machine-readable line so a parent process (the UI, when this runs inside
     * the separate MountWorker process) can mirror this operation into its own
     * {@code OperationLog} for the Stats tab, since OperationLog is per-process.
     */
    private void emitOpLog(Operation operation, String status, long startNanos, String errorMessage) {
        long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
        System.out.println("##OPLOG##|MOUNT|" + operation.getOperationType().name() + "|"
                + sanitize(operation.getDetail()) + "|" + status + "|" + durationMillis + "|" + sanitize(errorMessage));
    }

    /**
     * Updates the local (Mount worker process) {@link TransferStats} totals for READ/WRITE
     * operations and prints a machine-readable line so the UI process can mirror the
     * cumulative totals into its own copy of {@link TransferStats}, since static state
     * isn't shared across processes.
     */
    private void trackAndEmitTransfer(Operation operation) {
        long bytes;
        boolean isRead;
        if (operation instanceof ReadOperation readOperation) {
            bytes = readOperation.getBytesRead();
            isRead = true;
        } else if (operation instanceof WriteOperation writeOperation) {
            bytes = writeOperation.getBytesWrote();
            isRead = false;
        } else {
            return;
        }
        if (bytes <= 0) {
            return;
        }
        if (isRead) {
            TransferStats.addReadBytes(OperationSource.MOUNT, bytes);
        } else {
            TransferStats.addWriteBytes(OperationSource.MOUNT, bytes);
        }
        long now = System.nanoTime();
        long previous = LAST_TRANSFER_EMIT_NANOS.get();
        if (now - previous >= TRANSFER_EMIT_INTERVAL_NANOS
                && LAST_TRANSFER_EMIT_NANOS.compareAndSet(previous, now)) {
            System.out.println("##XFER##|" + TransferStats.getReadBytes(OperationSource.MOUNT) + "|"
                    + TransferStats.getWriteBytes(OperationSource.MOUNT));
        }
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('|', '\u00a6').replace('\n', ' ').replace('\r', ' ');
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
