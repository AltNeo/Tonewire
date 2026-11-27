package com.tonewire.tv;

/** Small, conservative curves generated from the Equalizer actually present on the TV. */
public final class TonewirePresets {
    public static final String FLAT_REFERENCE = "flat_reference";
    public static final String CLEAR_DIALOGUE = "clear_dialogue";
    public static final String NIGHT_WATCH = "night_watch";

    public static java.util.List<com.tonewire.protocol.PresetCatalog.Entry> catalog() {
        return java.util.Arrays.asList(
                new com.tonewire.protocol.PresetCatalog.Entry(FLAT_REFERENCE, "Flat reference", true),
                new com.tonewire.protocol.PresetCatalog.Entry(CLEAR_DIALOGUE, "Clear dialogue", true),
                new com.tonewire.protocol.PresetCatalog.Entry(NIGHT_WATCH, "Night watch", true));
    }

    private TonewirePresets() {}

    public static EqPreset create(String presetId, EqCapabilities capabilities) {
        if (capabilities == null) throw new IllegalArgumentException("capabilities are required");
        int[] centers = capabilities.getCenterFrequenciesMillihertz();
        int[] levels = new int[centers.length];
        for (int band = 0; band < centers.length; band++) {
            levels[band] = EqMath.clampMillibels(targetLevel(presetId, centers[band] / 1000),
                    capabilities.getMinimumMillibels(), capabilities.getMaximumMillibels());
        }
        return new EqPreset(presetId, levels);
    }

    public static String displayName(String name) {
        if (name == null || name.trim().isEmpty() || "custom".equalsIgnoreCase(name.trim())) return "Custom";
        switch (name) {
            case FLAT_REFERENCE: return "Flat reference";
            case CLEAR_DIALOGUE: return "Clear dialogue";
            case NIGHT_WATCH: return "Night watch";
            default: return name.trim();
        }
    }

    private static int targetLevel(String presetId, int centerHertz) {
        if (FLAT_REFERENCE.equals(presetId)) return 0;
        if (CLEAR_DIALOGUE.equals(presetId)) {
            if (centerHertz <= 300) return -150;
            if (centerHertz >= 600 && centerHertz <= 3500) return 200;
            return 0;
        }
        if (NIGHT_WATCH.equals(presetId)) {
            if (centerHertz <= 300) return -500;
            if (centerHertz >= 7000) return -400;
            if (centerHertz >= 700 && centerHertz <= 3000) return 100;
            return -100;
        }
        throw new IllegalArgumentException("unknown preset_id");
    }
}
