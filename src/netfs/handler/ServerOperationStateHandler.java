package netfs.handler;

import netfs.handler.state.ServerOperation;

import java.util.LinkedHashMap;
import java.util.Map;

public class ServerOperationStateHandler {
    private final Map<Long, ServerOperation> serverOperations;

    public ServerOperationStateHandler() {
        serverOperations = new LinkedHashMap<>();
    }

    public void addIOOperationState(long id, String name) {
        serverOperations.put(id, new ServerOperation(name, System.currentTimeMillis(), 0L));
        updateDetails();
    }

    public void addMetaGetOperationState(long id, String name) {
        serverOperations.put(id, new ServerOperation(name, System.currentTimeMillis(), -1L));
        updateDetails();
    }

    public void updateDetails() {
        System.out.println(serverOperations);
    }
}
