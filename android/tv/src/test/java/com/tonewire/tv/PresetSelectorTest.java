package com.tonewire.tv;

import static org.junit.Assert.assertSame;

import org.junit.Test;

public final class PresetSelectorTest {
    @Test public void selectsCompatibleRouteCurveBeforeDefault() {
        EqPreset route = new EqPreset("USB", new int[] {-100, 0, 0, 0, -100});
        EqPreset fallback = new EqPreset("Default", new int[] {0, 0, 0, 0, 0});
        assertSame(route, PresetSelector.select(route, fallback, 5));
    }
    @Test public void rejectsStaleBandCountAndUsesCompatibleDefault() {
        EqPreset stale = new EqPreset("old", new int[] {0, 0, 0});
        EqPreset fallback = new EqPreset("Default", new int[] {0, 0, 0, 0, 0});
        assertSame(fallback, PresetSelector.select(stale, fallback, 5));
    }
}
