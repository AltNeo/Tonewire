package com.tonewire.phone;
import org.junit.Test;
import static org.junit.Assert.*;
public final class RejectionResyncTest {
    @Test public void unavailableEngineWaitsForPushOrHeartbeatInsteadOfFloodingRequests() {
        RejectionResync recovery = new RejectionResync();
        for (int i = 0; i < 100; i++) assertFalse(recovery.requestNow("engine_unavailable"));
    }
    @Test public void rejectedRecoveryReadDoesNotRecursivelyRequestAnotherRead() {
        RejectionResync recovery = new RejectionResync();
        assertTrue(recovery.requestNow("invalid_command"));
        assertFalse(recovery.requestNow("invalid_command"));
        assertFalse(recovery.requestNow("stale_revision"));
        recovery.receivedSnapshot();
        assertTrue(recovery.requestNow("stale_revision"));
    }
}
