package netfs.handler;

public interface OperationHandler {
    void addIOOperationState(long id, String name);

    void addMetaGetOperationState(long id, String name);

    void updateDetails();

    void incrementBytesRead() ;

    void incrementBytesWritten();

    void addBytesRead(long val);

    void addBytesWritten(long val);

    long getBytesRead();

    long getBytesWrite();
}
