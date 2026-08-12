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
 * <p>Operations are recorded only while persistence is enabled. Disabling persistence
 * immediately clears the registry so disabled mode retains no operation entries.</p>
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
        if (!persist) {
            return null;
        }
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
        if (!persist) {
            return;
        }
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
        if (!value) {
            ENTRIES.clear();
        }
    }

    public static boolean isPersist() {
        return persist;
    }

    /** Removes older completed entries when persistence is disabled. */
    public static void sweep() {
        if (!persist) {
            ENTRIES.clear();
        }
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
