package netfs.state;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A single tracked file-system operation (server-side request or client-side FUSE
 * operation) shown in the UI's Stats tab.
 */
public class OperationLogEntry {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final long id = SEQUENCE.incrementAndGet();
    private final OperationSource source;
    private final String operation;
    private final String detail;
    private final long startTime = System.currentTimeMillis();

    private volatile OperationStatus status = OperationStatus.RUNNING;
    private volatile long endTime;
    private volatile String errorMessage;

    OperationLogEntry(OperationSource source, String operation, String detail) {
        this.source = source;
        this.operation = operation;
        this.detail = detail == null ? "" : detail;
    }

    void markCompleted() {
        this.endTime = System.currentTimeMillis();
        this.status = OperationStatus.COMPLETED;
    }

    void markFailed(String message) {
        this.endTime = System.currentTimeMillis();
        this.errorMessage = message;
        this.status = OperationStatus.FAILED;
    }

    /** Finalizes this entry using an already-known outcome/duration reported remotely. */
    void finalizeRemote(OperationStatus status, long durationMillis, String errorMessage) {
        this.endTime = this.startTime + Math.max(0, durationMillis);
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public long getId() {
        return id;
    }

    public OperationSource getSource() {
        return source;
    }

    public String getOperation() {
        return operation;
    }

    public String getDetail() {
        return detail;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public OperationStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getDurationMillis() {
        long end = endTime == 0 ? System.currentTimeMillis() : endTime;
        return end - startTime;
    }
}
