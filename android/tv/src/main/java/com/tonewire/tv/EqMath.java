package com.tonewire.tv;

/** Integer-only EQ conversions and bounds checks, safe for the low-memory TV process. */
public final class EqMath {
    private EqMath() {}

    public static int decibelsToMillibels(int decibels) {
        if (decibels > Integer.MAX_VALUE / 100) return Integer.MAX_VALUE;
        if (decibels < Integer.MIN_VALUE / 100) return Integer.MIN_VALUE;
        return decibels * 100;
    }

    public static int clampMillibels(int value, int minimum, int maximum) {
        if (minimum > maximum) throw new IllegalArgumentException("minimum exceeds maximum");
        return Math.max(minimum, Math.min(maximum, value));
    }
}
