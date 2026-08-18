package com.tonewire.tv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TonewirePresetsTest {
    private static final EqCapabilities CAPABILITIES = new EqCapabilities(-300, 250,
            new int[] {60_000, 230_000, 910_000, 3_600_000, 14_000_000}, new int[5][2], new String[0]);

    @Test public void flatCurveUsesZeroForEveryRuntimeBand() {
        assertArrayEquals(new int[] {0, 0, 0, 0, 0},
                TonewirePresets.create(TonewirePresets.FLAT_REFERENCE, CAPABILITIES).getLevelsMillibels());
    }

    @Test public void dialogueAndNightCurvesAreConservativeAndClamped() {
        int[] dialogue = TonewirePresets.create(TonewirePresets.CLEAR_DIALOGUE, CAPABILITIES).getLevelsMillibels();
        int[] night = TonewirePresets.create(TonewirePresets.NIGHT_WATCH, CAPABILITIES).getLevelsMillibels();
        assertArrayEquals(new int[] {-150, -150, 200, 0, 0}, dialogue);
        assertArrayEquals(new int[] {-300, -300, 100, -100, -300}, night);
        for (int level : night) assertTrue(level >= -300 && level <= 250);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownPhonePresetId() {
        TonewirePresets.create("unsafe_bass", CAPABILITIES);
    }
}
