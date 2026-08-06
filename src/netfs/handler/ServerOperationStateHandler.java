package netfs.handler;

import netfs.handler.state.ServerOperation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class ServerOperationStateHandler implements OperationHandler {
    private final Map<Long, ServerOperation> serverOperations;

    private AtomicLong bytesRead = new AtomicLong();
    private AtomicLong bytesWrite = new AtomicLong();

    public ServerOperationStateHandler() {
        serverOperations = new LinkedHashMap<>();
    }

    @Override
    public void addIOOperationState(long id, String name) {
        serverOperations.put(id, new ServerOperation(name, System.currentTimeMillis(), 0L));
        updateDetails();
    }

    @Override
    public void addMetaGetOperationState(long id, String name) {
        serverOperations.put(id, new ServerOperation(name, System.currentTimeMillis(), -1L));
        updateDetails();
    }

    @Override
    public void updateDetails() {
        System.out.println(serverOperations);
    }

    @Override
    public void incrementBytesRead() {
        bytesRead.incrementAndGet();
    }

    @Override
    public void incrementBytesWritten() {
        bytesWrite.incrementAndGet();
    }

    @Override
    public void addBytesRead(long val) {
        bytesRead.addAndGet(val);
    }

    @Override
    public void addBytesWritten(long val) {
        bytesWrite.addAndGet(val);
    }

    @Override
    public long getBytesRead() {
        return bytesRead.longValue();
    }

    @Override
    public long getBytesWrite() {
        return bytesWrite.longValue();
    }
}
