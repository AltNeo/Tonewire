package com.tonewire.phone;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class OptimisticRevisionTest {
    @Test public void burstCommandsGetDistinctExpectedRevisionsBeforeAcknowledgements() {
        OptimisticRevision revisions = new OptimisticRevision();
        assertEquals(0L, revisions.allocateExpected());
        assertEquals(1L, revisions.allocateExpected());
        assertEquals(2L, revisions.allocateExpected());
    }

    @Test public void lateAcknowledgementCannotMoveRevisionBackwards() {
        OptimisticRevision revisions = new OptimisticRevision();
        revisions.allocateExpected(); revisions.allocateExpected(); revisions.allocateExpected();
        revisions.observe(1L);
        assertEquals(3L, revisions.current());
        revisions.observe(4L);
        assertEquals(4L, revisions.current());
    }
}
