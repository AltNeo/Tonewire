package com.tonewire.tv;

/** Interface for the LAN server/UI layer; callers receive only runtime-discovered capabilities. */
public interface TvEqualizerController {
    EqualizerEngine.State getState();
    String getActivePresetName();
    RouteSnapshot getRoutes();
    int setBandLevel(int band, int requestedMillibels);
    void setEnabled(boolean enabled);
    void applyPreset(EqPreset preset);
    void setRoutePreset(String fingerprint, EqPreset preset);
    void setAutostartEnabled(boolean enabled);
}
