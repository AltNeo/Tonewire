package com.tonewire.tv;

/** Interface for the LAN server/UI layer; callers receive only runtime-discovered capabilities. */
public interface TvEqualizerController {
    EqualizerEngine.State getState();
    String getActivePresetName();
    RouteSnapshot getRoutes();
    default boolean supportsSavedPresets() { return false; }
    default java.util.List<com.tonewire.protocol.PresetCatalog.Entry> getPresets() { return TonewirePresets.catalog(); }
    default EqPreset resolvePreset(String id, EqCapabilities capabilities) { return TonewirePresets.create(id, capabilities); }
    default void savePreset(String name) { throw new IllegalArgumentException("Saved presets are unavailable"); }
    default void deletePreset(String id) { throw new IllegalArgumentException("Saved presets are unavailable"); }
    int setBandLevel(int band, int requestedMillibels);
    void setEnabled(boolean enabled);
    void applyPreset(EqPreset preset);
    void setRoutePreset(String fingerprint, EqPreset preset);
    void setAutostartEnabled(boolean enabled);
}
