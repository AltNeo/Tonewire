package com.tonewire.phone;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class HeadroomGuardTest {
    private PhoneState.Band band(int index, int level) { return new PhoneState.Band(index, 60 + index * 100, -1500, 1500, level); }
    @Test public void uniformlyShiftsPeakToZeroAndPreservesDifferences() {
        List<PhoneState.Band> original = Arrays.asList(band(0, 300), band(1, -200), band(2, 100));
        List<PhoneState.Band> shifted = HeadroomGuard.cutOnly(original);
        assertEquals(0, shifted.get(0).levelMillibels);
        assertEquals(-500, shifted.get(1).levelMillibels);
        assertEquals(-200, shifted.get(2).levelMillibels);
        assertEquals(300, original.get(0).levelMillibels);
        assertEquals(original.get(0).centerHz, shifted.get(0).centerHz);
    }
    @Test public void alreadyCutOnlyCurveIsNotRaised() {
        List<PhoneState.Band> shifted = HeadroomGuard.cutOnly(Arrays.asList(band(0, -200), band(1, -400)));
        assertEquals(-200, shifted.get(0).levelMillibels); assertEquals(-400, shifted.get(1).levelMillibels);
        assertTrue(HeadroomGuard.cutOnly(java.util.Collections.emptyList()).isEmpty());
    }
    @Test public void impossibleUniformShiftFailsBeforeAnyPartialCurveCanBeSent() {
        assertThrows(IllegalArgumentException.class, () -> HeadroomGuard.cutOnly(Arrays.asList(band(0, 1500), band(1, -1500))));
    }
    @Test public void warnsOnlyAboveSixDecibels() {
        assertFalse(HeadroomGuard.hasHighBoost(Arrays.asList(band(0, 600), band(1, -500))));
        assertTrue(HeadroomGuard.hasHighBoost(Arrays.asList(band(0, 601), band(1, 0))));
    }
}
