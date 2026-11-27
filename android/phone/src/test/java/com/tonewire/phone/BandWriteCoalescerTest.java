package com.tonewire.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Behavior of the band write coalescer: batch, dedupe, and never drop a touched band. */
public class BandWriteCoalescerTest {

    private static final class Recorder implements BandWriteCoalescer.Sink {
        final List<String> writes = new ArrayList<>();
        @Override public void write(int bandIndex, int levelMillibels) {
            writes.add(bandIndex + "=" + levelMillibels);
        }
    }

    @Test public void firstWriteRequestsAFlush() {
        BandWriteCoalescer coalescer = new BandWriteCoalescer();
        assertTrue(coalescer.record(0, 100));
        assertTrue(coalescer.isScheduled());
        assertFalse(coalescer.record(1, 200));
    }

    @Test public void resetDeliversEveryBand() {
        // Regression: reset() writes all bands in a loop. A single-slot implementation kept only
        // the last band, so the treble landed and the bass was silently dropped.
        BandWriteCoalescer coalescer = new BandWriteCoalescer();
        for (int band = 0; band < 5; band++) coalescer.record(band, 0);

        Recorder recorder = new Recorder();
        coalescer.flush(recorder);

        assertEquals(5, recorder.writes.size());
        assertEquals("[0=0, 1=0, 2=0, 3=0, 4=0]", recorder.writes.toString());
    }

    @Test public void repeatedEditsToOneBandSendOnlyTheNewestLevel() {
        BandWriteCoalescer coalescer = new BandWriteCoalescer();
        coalescer.record(2, -100);
        coalescer.record(2, -300);
        coalescer.record(2, -450);

        Recorder recorder = new Recorder();
        coalescer.flush(recorder);

        assertEquals(1, recorder.writes.size());
        assertEquals("2=-450", recorder.writes.get(0));
    }

    @Test public void interleavedBandsKeepFirstTouchOrderAndLastLevel() {
        BandWriteCoalescer coalescer = new BandWriteCoalescer();
        coalescer.record(4, 10);
        coalescer.record(1, 20);
        coalescer.record(4, 30);

        Recorder recorder = new Recorder();
        coalescer.flush(recorder);

        assertEquals("[4=30, 1=20]", recorder.writes.toString());
    }

    @Test public void flushClearsPendingAndAllowsRescheduling() {
        BandWriteCoalescer coalescer = new BandWriteCoalescer();
        coalescer.record(0, 50);
        coalescer.flush(new Recorder());

        assertEquals(0, coalescer.pendingCount());
        assertFalse(coalescer.isScheduled());
        assertTrue(coalescer.record(0, 60));
    }

    @Test public void flushWithNothingPendingWritesNothing() {
        BandWriteCoalescer coalescer = new BandWriteCoalescer();
        Recorder recorder = new Recorder();
        coalescer.flush(recorder);
        assertTrue(recorder.writes.isEmpty());
    }

    @Test public void clearDropsPendingWork() {
        BandWriteCoalescer coalescer = new BandWriteCoalescer();
        coalescer.record(0, 100);
        coalescer.record(1, 200);
        coalescer.clear();

        Recorder recorder = new Recorder();
        coalescer.flush(recorder);

        assertEquals(0, coalescer.pendingCount());
        assertTrue(recorder.writes.isEmpty());
        assertTrue(coalescer.record(3, 5));
    }

    @Test public void writeDuringFlushIsNotLost() {
        final BandWriteCoalescer coalescer = new BandWriteCoalescer();
        coalescer.record(0, 1);
        final List<String> writes = new ArrayList<>();
        // A sink that re-enters while flushing must not corrupt the in-flight batch.
        coalescer.flush(new BandWriteCoalescer.Sink() {
            @Override public void write(int bandIndex, int levelMillibels) {
                writes.add(bandIndex + "=" + levelMillibels);
                if (writes.size() == 1) coalescer.record(1, 2);
            }
        });

        assertEquals("[0=1]", writes.toString());
        assertEquals(1, coalescer.pendingCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeBandIndexIsRejected() {
        new BandWriteCoalescer().record(-1, 0);
    }
}
