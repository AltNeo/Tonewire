package com.tonewire.phone;

import static org.junit.Assert.assertEquals;

import java.util.Collections;

import org.junit.Test;

public final class PhoneStateTest {
    @Test public void bandLevelIsClampedToAdvertisedLimits() {
        PhoneState.Band band = new PhoneState.Band(0, 60, -1200, 900, 1400);
        assertEquals(900, band.levelMillibels);
    }

    @Test public void updateRetainsTvProvidedCapabilities() {
        PhoneState.Band band = new PhoneState.Band(3, 3600, -1500, 1500, 0);
        PhoneState state = new PhoneState(PhoneState.Connection.CONNECTED, "live", "TV", "Speaker",
                "Custom", true, 8L, Collections.singletonList(band)).withBandLevel(3, -300);
        assertEquals(3600, state.bands.get(0).centerHz);
        assertEquals(-300, state.bands.get(0).levelMillibels);
    }
}
