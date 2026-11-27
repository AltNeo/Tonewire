package com.tonewire.tv.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.security.SecureRandom;

import org.junit.Test;

public final class PairingCodeManagerTest {
    @Test public void codeHasSixDigitsAndExpires() {
        PairingCodeManager manager = new PairingCodeManager(new FixedRandom(), 100L);
        String code = manager.currentCode(1_000L);
        assertTrue(code.matches("[0-9]{6}"));
        assertTrue(manager.matches(code, 1_099L));
        assertFalse(manager.matches(code, 1_100L));
        assertEquals(100L, manager.remainingMillis(1_100L));
    }

    @Test public void fifthWrongCodeLocksGloballyRotatesAndCannotBeBypassedByRefresh() {
        PairingCodeManager manager = new PairingCodeManager(new FixedRandom(), 300_000);
        String original = manager.currentCode(1000);
        for (int attempt = 0; attempt < 4; attempt++) {
            assertEquals(PairingCodeManager.Result.INVALID, manager.verify("999999", 1000));
        }
        assertEquals(PairingCodeManager.Result.RATE_LIMITED, manager.verify("999999", 1000));
        assertFalse(original.equals(manager.currentCode(1000)));
        assertEquals(30_000, manager.lockRemainingMillis(1000));
        manager.refresh(1001);
        assertEquals(PairingCodeManager.Result.RATE_LIMITED, manager.verify(manager.currentCode(1001), 1001));
        assertEquals(PairingCodeManager.Result.MATCH, manager.verify(manager.currentCode(31_000), 31_000));
    }

    @Test public void successfulPairingResetsWrongCodeCounter() {
        PairingCodeManager manager = new PairingCodeManager(new FixedRandom(), 300_000);
        for (int attempt = 0; attempt < 4; attempt++) manager.verify("999999", 1000);
        assertEquals(PairingCodeManager.Result.MATCH, manager.verify(manager.currentCode(1000), 1000));
        assertEquals(PairingCodeManager.Result.INVALID, manager.verify("999999", 1000));
    }

    @Test public void missingAndMalformedPeerIdsAreRejected() {
        assertFalse(TvNetworkServer.validPeerId(null));
        assertFalse(TvNetworkServer.validPeerId(""));
        assertFalse(TvNetworkServer.validPeerId("bad peer id"));
        assertTrue(TvNetworkServer.validPeerId("phone_123"));
    }

    private static final class FixedRandom extends SecureRandom {
        private int value = 42;
        @Override public int nextInt(int bound) { return value++; }
    }
}
