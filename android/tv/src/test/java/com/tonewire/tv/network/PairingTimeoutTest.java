package com.tonewire.tv.network;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PairingTimeoutTest {
    @Test public void manualConnectionUsesTheDocumentedStablePort() {
        assertTrue(TvNetworkServer.DEFAULT_PORT == 49321);
    }

    @Test public void pairingWindowCoversTheDisplayedCodeLifetimeButRemainsBounded() {
        assertTrue(TvNetworkServer.PAIRING_IDLE_TIMEOUT_MILLIS >= PairingCodeManager.DEFAULT_TTL_MILLIS);
        assertTrue(TvNetworkServer.PAIRING_IDLE_TIMEOUT_MILLIS <= 10 * 60 * 1000);
    }
}
