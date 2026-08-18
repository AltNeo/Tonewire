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

    private static final class FixedRandom extends SecureRandom {
        private int value = 42;
        @Override public int nextInt(int bound) { return value++; }
    }
}
