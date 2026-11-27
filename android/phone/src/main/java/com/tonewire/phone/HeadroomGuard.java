package com.tonewire.phone;

import java.util.ArrayList;
import java.util.List;

/** A uniform downward shift only; never boosts or silently reshapes a curve. */
final class HeadroomGuard {
    private HeadroomGuard() { }
    static boolean hasHighBoost(List<PhoneState.Band> bands) {
        for (PhoneState.Band band : bands) if (band.levelMillibels > 600) return true;
        return false;
    }
    static List<PhoneState.Band> cutOnly(List<PhoneState.Band> bands) {
        int peak = 0;
        for (PhoneState.Band band : bands) peak = Math.max(peak, band.levelMillibels);
        List<PhoneState.Band> shifted = new ArrayList<>();
        for (PhoneState.Band band : bands) {
            long level = (long) band.levelMillibels - peak;
            if (level < band.minMillibels || level > band.maxMillibels) {
                throw new IllegalArgumentException("This curve cannot be shifted below 0 dB without exceeding the TV's gain range. Reduce its largest boost first.");
            }
            shifted.add(band.withLevel((int) level));
        }
        return shifted;
    }
}
