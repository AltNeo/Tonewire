package com.tonewire.tv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class EqMathTest {
    @Test public void convertsDecibelsUsingIntegerMillibels() {
        assertEquals(1500, EqMath.decibelsToMillibels(15));
        assertEquals(-1500, EqMath.decibelsToMillibels(-15));
    }
    @Test public void clampsToReportedHardwareLimits() {
        assertEquals(-1500, EqMath.clampMillibels(-2000, -1500, 1500));
        assertEquals(1500, EqMath.clampMillibels(2000, -1500, 1500));
        assertEquals(300, EqMath.clampMillibels(300, -1500, 1500));
    }
}
