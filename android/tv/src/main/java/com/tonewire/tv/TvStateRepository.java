package com.tonewire.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small SharedPreferences store with a single debounced write path. */
public final class TvStateRepository {
    private static final String FILE = "tonewire_state";
    private static final String KEY_AUTOSTART = "autostart";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_DEFAULT = "default";
    private static final String SAVED_PREFIX = "saved.";
    private static final String ROUTE_PREFIX = "route.";
    private static final long WRITE_DEBOUNCE_MS = 750L;
    private final SharedPreferences preferences;
    interface WriteScheduler {
        void cancel(Runnable write);
        void schedule(Runnable write, long delayMillis);
    }
    private final WriteScheduler scheduler;
    private final Map<String, EqPreset> pendingRoutes = new LinkedHashMap<>();
    private final Runnable pendingWrite = new Runnable() { @Override public void run() {
        SharedPreferences.Editor editor = preferences.edit().putBoolean(KEY_AUTOSTART, autostart)
                .putString(KEY_DEFAULT, encode(defaultPreset));
        writePendingRoutes(editor);
        editor.apply();
        pendingRoutes.clear();
        dirty = false;
    } };
    private boolean autostart;
    private EqPreset defaultPreset;
    private boolean dirty;

    public TvStateRepository(Context context, Handler handler) {
        this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE), new WriteScheduler() {
            @Override public void cancel(Runnable write) { handler.removeCallbacks(write); }
            @Override public void schedule(Runnable write, long delayMillis) { handler.postDelayed(write, delayMillis); }
        });
    }

    TvStateRepository(SharedPreferences preferences, WriteScheduler scheduler) {
        this.preferences = preferences;
        this.scheduler = scheduler;
        autostart = preferences.getBoolean(KEY_AUTOSTART, true);
        defaultPreset = decode("Default", preferences.getString(KEY_DEFAULT, null));
    }
    /** Missing enable preference preserves the firmware state on upgrades. */
    public boolean getEnabled(boolean hardwareDefault) { return preferences.getBoolean(KEY_ENABLED, hardwareDefault); }

    /** Commit curve and enable together before acknowledging a user mutation. */
    public void saveCurrent(EqPreset preset, boolean enabled) {
        SharedPreferences.Editor editor = preferences.edit().putString(KEY_DEFAULT, encode(preset))
                .putBoolean(KEY_ENABLED, enabled);
        writePendingRoutes(editor);
        if (!editor.commit()) {
            throw new IllegalStateException("Could not persist equalizer state");
        }
        defaultPreset = preset;
        pendingRoutes.clear();
    }

    public java.util.List<com.tonewire.protocol.PresetCatalog.Entry> presetCatalog() {
        java.util.List<com.tonewire.protocol.PresetCatalog.Entry> catalog = new java.util.ArrayList<>(TonewirePresets.catalog());
        java.util.List<com.tonewire.protocol.PresetCatalog.Entry> saved = new java.util.ArrayList<>();
        for (String key : preferences.getAll().keySet()) {
            if (!key.startsWith(SAVED_PREFIX)) continue;
            EqPreset preset = decode("Saved preset", preferences.getString(key, null));
            if (preset != null) saved.add(new com.tonewire.protocol.PresetCatalog.Entry(key.substring(SAVED_PREFIX.length()), preset.getName(), false));
        }
        saved.sort(java.util.Comparator.comparing(entry -> entry.name.toLowerCase(java.util.Locale.ROOT)));
        catalog.addAll(saved);
        return catalog;
    }

    public EqPreset savedPreset(String id) { return decode("Saved preset", preferences.getString(SAVED_PREFIX + id, null)); }

    public String saveNamedPreset(String name, int[] levels) {
        String id = "custom_" + java.util.UUID.randomUUID().toString();
        com.tonewire.protocol.PresetCatalog.Entry entry = new com.tonewire.protocol.PresetCatalog.Entry(id, name, false);
        java.util.List<com.tonewire.protocol.PresetCatalog.Entry> catalog = presetCatalog();
        if (catalog.size() >= 11) throw new IllegalArgumentException("Eight saved presets already exist. Delete one first.");
        for (com.tonewire.protocol.PresetCatalog.Entry existing : catalog) {
            if (existing.name.equalsIgnoreCase(entry.name)) throw new IllegalArgumentException("A preset with that name already exists");
        }
        if (!preferences.edit().putString(SAVED_PREFIX + id, encode(new EqPreset(entry.name, levels))).commit()) {
            throw new IllegalStateException("Could not save preset");
        }
        return id;
    }

    public void deleteNamedPreset(String id) {
        if (id == null || !id.startsWith("custom_") || savedPreset(id) == null) throw new IllegalArgumentException("Only saved presets can be deleted");
        if (!preferences.edit().remove(SAVED_PREFIX + id).commit()) throw new IllegalStateException("Could not delete preset");
    }

    public boolean isAutostartEnabled() { return autostart; }
    public void setAutostartEnabled(boolean enabled) { autostart = enabled; scheduleWrite(); }
    public EqPreset getDefaultPreset() { return defaultPreset; }
    public void setDefaultPreset(EqPreset preset) { defaultPreset = preset; scheduleWrite(); }
    public EqPreset getRoutePreset(String fingerprint) {
        return pendingRoutes.containsKey(fingerprint) ? pendingRoutes.get(fingerprint)
                : decode("Route", preferences.getString(ROUTE_PREFIX + fingerprint, null));
    }
    public void setRoutePreset(String fingerprint, EqPreset preset) {
        pendingRoutes.put(fingerprint, preset);
        scheduleWrite();
    }
    /** Edits update the fallback and the identified output in the same debounced write. */
    public void saveEditedPreset(AudioRoute route, EqPreset preset) {
        defaultPreset = preset;
        if (route != null) pendingRoutes.put(route.getFingerprint(), preset);
        scheduleWrite();
    }
    public void flush() { scheduler.cancel(pendingWrite); if (dirty) pendingWrite.run(); }

    private void writePendingRoutes(SharedPreferences.Editor editor) {
        for (Map.Entry<String, EqPreset> route : pendingRoutes.entrySet()) {
            editor.putString(ROUTE_PREFIX + route.getKey(), encode(route.getValue()));
        }
    }

    private void scheduleWrite() { dirty = true; scheduler.cancel(pendingWrite); scheduler.schedule(pendingWrite, WRITE_DEBOUNCE_MS); }
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
