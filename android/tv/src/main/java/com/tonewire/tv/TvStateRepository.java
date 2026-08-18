package com.tonewire.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

/** Small SharedPreferences store with a single debounced write path. */
public final class TvStateRepository {
    private static final String FILE = "tonewire_state";
    private static final String KEY_AUTOSTART = "autostart";
    private static final String KEY_DEFAULT = "default";
    private static final String ROUTE_PREFIX = "route.";
    private static final long WRITE_DEBOUNCE_MS = 750L;
    private final SharedPreferences preferences;
    private final Handler handler;
    private final Runnable pendingWrite = new Runnable() { @Override public void run() { dirty = false; preferences.edit().putBoolean(KEY_AUTOSTART, autostart).putString(KEY_DEFAULT, encode(defaultPreset)).apply(); } };
    private boolean autostart;
    private EqPreset defaultPreset;
    private boolean dirty;

    public TvStateRepository(Context context, Handler handler) {
        preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        this.handler = handler;
        autostart = preferences.getBoolean(KEY_AUTOSTART, true);
        defaultPreset = decode("Default", preferences.getString(KEY_DEFAULT, null));
    }
    public boolean isAutostartEnabled() { return autostart; }
    public void setAutostartEnabled(boolean enabled) { autostart = enabled; scheduleWrite(); }
    public EqPreset getDefaultPreset() { return defaultPreset; }
    public void setDefaultPreset(EqPreset preset) { defaultPreset = preset; scheduleWrite(); }
    public EqPreset getRoutePreset(String fingerprint) { return decode("Route", preferences.getString(ROUTE_PREFIX + fingerprint, null)); }
    public void setRoutePreset(String fingerprint, EqPreset preset) { preferences.edit().putString(ROUTE_PREFIX + fingerprint, encode(preset)).apply(); }
    public void flush() { handler.removeCallbacks(pendingWrite); if (dirty) pendingWrite.run(); }

    private void scheduleWrite() { dirty = true; handler.removeCallbacks(pendingWrite); handler.postDelayed(pendingWrite, WRITE_DEBOUNCE_MS); }
    private static String encode(EqPreset preset) {
        if (preset == null) return "";
        StringBuilder encoded = new StringBuilder(preset.getName().replace("|", " "));
        for (int level : preset.getLevelsMillibels()) encoded.append('|').append(level);
        return encoded.toString();
    }
    private static EqPreset decode(String fallbackName, String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        String[] parts = encoded.split("\\|");
        if (parts.length < 2) return null;
        int[] levels = new int[parts.length - 1];
        try {
            for (int i = 1; i < parts.length; i++) levels[i - 1] = Integer.parseInt(parts[i]);
            return new EqPreset(parts[0].isEmpty() ? fallbackName : parts[0], levels);
        } catch (NumberFormatException invalid) { return null; }
    }
}
