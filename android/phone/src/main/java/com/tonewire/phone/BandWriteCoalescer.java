package com.tonewire.phone;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Coalesces band edits into one batch per flush window.
 *
 * <p>A single pending band is not enough: RESET writes every band in a loop, and a fast two-slider
 * or two-finger drag can touch several bands before the next flush. Keeping only the most recent
 * band would silently drop the others, so the last level per band index is retained and every
 * touched band is delivered.
 *
 * <p>Not thread safe. It is used only from the UI thread.
 */
public final class BandWriteCoalescer {
    /** Receives the coalesced writes. */
    public interface Sink {
        void write(int bandIndex, int levelMillibels);
    }

    private final Map<Integer, Integer> pending = new LinkedHashMap<>();
    private boolean scheduled;

    /** Records the newest level for a band and returns true when the caller must schedule a flush. */
    public boolean record(int bandIndex, int levelMillibels) {
        if (bandIndex < 0) throw new IllegalArgumentException("band index must not be negative");
        pending.put(bandIndex, levelMillibels);
        if (scheduled) return false;
        scheduled = true;
        return true;
    }

    /** Delivers every pending band to the sink, in first-touched order, then clears. */
    public void flush(Sink sink) {
        if (sink == null) throw new NullPointerException("sink");
        scheduled = false;
        if (pending.isEmpty()) return;
        // Copy first so a sink that re-enters record() cannot corrupt the iteration.
        Map<Integer, Integer> batch = new LinkedHashMap<>(pending);
        pending.clear();
        for (Map.Entry<Integer, Integer> entry : batch.entrySet()) {
            sink.write(entry.getKey(), entry.getValue());
        }
    }

    /** True when a flush is already scheduled. */
    public boolean isScheduled() { return scheduled; }

    /** Number of distinct bands waiting to be written. */
    public int pendingCount() { return pending.size(); }

    /** Drops everything pending, for example when the connection is lost. */
    public void clear() { pending.clear(); scheduled = false; }
}
