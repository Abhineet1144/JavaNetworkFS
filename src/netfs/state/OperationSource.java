package netfs.state;

/** Which side of the app produced an {@link OperationLogEntry}. */
public enum OperationSource {
    HOST,
    MOUNT
}
