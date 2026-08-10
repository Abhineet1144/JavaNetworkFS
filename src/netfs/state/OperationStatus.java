package netfs.state;

/** Lifecycle state of an {@link OperationLogEntry}. */
public enum OperationStatus {
    RUNNING,
    COMPLETED,
    FAILED
}
