package com.tonewire.phone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ConnectionGenerationTest {
    @Test public void newSessionInvalidatesAnOlderReconnect() {
        ConnectionGeneration guard = new ConnectionGeneration();
        long oldSession = guard.begin();
        assertTrue(guard.scheduleReconnect(oldSession));
        long newSession = guard.begin();
        assertFalse(guard.isCurrent(oldSession));
        assertTrue(guard.isCurrent(newSession));
        assertFalse(guard.beginScheduledReconnect(oldSession));
    }

    @Test public void onlyOneReconnectCanBeQueuedPerSession() {
        ConnectionGeneration guard = new ConnectionGeneration();
        long session = guard.begin();
        assertTrue(guard.scheduleReconnect(session));
        assertFalse(guard.scheduleReconnect(session));
        assertTrue(guard.beginScheduledReconnect(session));
    }
}
