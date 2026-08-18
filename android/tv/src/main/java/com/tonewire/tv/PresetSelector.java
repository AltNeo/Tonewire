package com.tonewire.tv;

/** Selects only compatible route curves; incompatible saved data falls back safely. */
public final class PresetSelector {
    private PresetSelector() {}

    public static EqPreset select(EqPreset routePreset, EqPreset defaultPreset, int bandCount) {
        if (isCompatible(routePreset, bandCount)) return routePreset;
        return isCompatible(defaultPreset, bandCount) ? defaultPreset : null;
    }

    private static boolean isCompatible(EqPreset preset, int bandCount) {
        return preset != null && preset.getLevelsMillibels().length == bandCount;
    }
}
