package netfs.state;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-wide cumulative byte counters for file data moved by READ and WRITE
 * operations, tracked separately for the Host and Mount sides. Plain Java only (no
 * JavaFX dependency) so it can be used from the headless Mount worker process too,
 * mirroring the {@link OperationLog} setup: the Mount worker keeps its own local
 * totals and relays them to the UI process (see {@code netfs.ui.MountTab}), which
 * mirrors them into its own copy of this class via {@link #setReadBytes} /
 * {@link #setWriteBytes} since static state isn't shared across processes.
 *
 * <p>The Stats tab polls the totals roughly once a second and derives read/write
 * throughput from the delta between polls.</p>
 */
public final class TransferStats {

    private static final Map<OperationSource, AtomicLong> READ_BYTES = new EnumMap<>(OperationSource.class);
    private static final Map<OperationSource, AtomicLong> WRITE_BYTES = new EnumMap<>(OperationSource.class);

    static {
        for (OperationSource source : OperationSource.values()) {
            READ_BYTES.put(source, new AtomicLong());
            WRITE_BYTES.put(source, new AtomicLong());
        }
    }

    private TransferStats() {
    }

    public static void addReadBytes(OperationSource source, long bytes) {
        if (bytes > 0) {
            READ_BYTES.get(source).addAndGet(bytes);
        }
    }

    public static void addWriteBytes(OperationSource source, long bytes) {
        if (bytes > 0) {
            WRITE_BYTES.get(source).addAndGet(bytes);
        }
    }

    /** Overwrites the running total outright - used to mirror an already-cumulative
     * total reported by the separate Mount worker process. */
    public static void setReadBytes(OperationSource source, long total) {
        READ_BYTES.get(source).set(total);
    }

    public static void setWriteBytes(OperationSource source, long total) {
        WRITE_BYTES.get(source).set(total);
    }

    public static long getReadBytes(OperationSource source) {
        return READ_BYTES.get(source).get();
    }

    public static long getWriteBytes(OperationSource source) {
        return WRITE_BYTES.get(source).get();
    }
}
