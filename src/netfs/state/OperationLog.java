package netfs.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Central, UI-agnostic registry of in-flight and recent file-system operations for both
 * the Host (server) and Mount (client) sides. Plain Java only - no JavaFX dependency -
 * so it can be used by headless entry points too.
 *
 * <p>By default completed operations are pruned periodically (see {@link #sweep()}) to
 * keep the log small. Failed operations are never auto-pruned; they stick around until
 * {@link #clear()} is called explicitly. Setting {@link #setPersist(boolean)} to true
 * disables auto-pruning of completed operations as well.</p>
 */
public final class OperationLog {

    // Kept modest so the Stats table (and the underlying JavaFX TableView refresh)
    // stays responsive - a few hundred rows is plenty for a live operations view.
    private static final int MAX_ENTRIES = 500;

    private static final Queue<OperationLogEntry> ENTRIES = new ConcurrentLinkedQueue<>();
    private static volatile boolean persist = false;

    private OperationLog() {
    }

    public static OperationLogEntry start(OperationSource source, String operation, String detail) {
        OperationLogEntry entry = new OperationLogEntry(source, operation, detail);
        ENTRIES.add(entry);
        trimIfNeeded();
        return entry;
    }

    public static void complete(OperationLogEntry entry) {
        if (entry != null) {
            entry.markCompleted();
        }
    }

    public static void fail(OperationLogEntry entry, String message) {
        if (entry != null) {
            entry.markFailed(message);
        }
    }

    /**
     * Records an operation that already finished elsewhere (e.g. in the separate Mount
     * worker process) with a known duration, instead of tracking it live via
     * {@link #start}/{@link #complete}/{@link #fail}.
     */
    public static void recordRemote(OperationSource source, String operation, String detail,
            OperationStatus status, long durationMillis, String errorMessage) {
        OperationLogEntry entry = new OperationLogEntry(source, operation, detail);
        entry.finalizeRemote(status, durationMillis, errorMessage);
        ENTRIES.add(entry);
        trimIfNeeded();
    }

    public static List<OperationLogEntry> snapshot() {
        return new ArrayList<>(ENTRIES);
    }

    public static void setPersist(boolean value) {
        persist = value;
    }

    public static boolean isPersist() {
        return persist;
    }

    /** Removes completed (non-failed) entries when persistence is disabled. */
    public static void sweep() {
        if (persist) {
            return;
        }
        ENTRIES.removeIf(entry -> entry.getStatus() == OperationStatus.COMPLETED);
    }

    /** Clears everything, including failed entries. Used by the manual "Clear" action. */
    public static void clear() {
        ENTRIES.clear();
    }

    private static void trimIfNeeded() {
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.poll();
        }
    }
}
