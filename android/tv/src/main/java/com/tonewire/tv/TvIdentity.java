package com.tonewire.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;
import android.os.Build;
import android.provider.Settings;

/** Uses the same friendly TV name in snapshots and DNS-SD advertisements. */
public final class TvIdentity {
    private TvIdentity() { }

    public static String id(Context context) {
        return id(context.getSharedPreferences("tonewire.identity", Context.MODE_PRIVATE));
    }

    static synchronized String id(SharedPreferences preferences) {
        String existing = preferences.getString("tv_id", "");
        if (!existing.isEmpty()) return existing;
        String created = UUID.randomUUID().toString();
        if (!preferences.edit().putString("tv_id", created).commit()) {
            throw new IllegalStateException("Could not persist TV identity");
        }
        return created;
    }

    public static String name(Context context) {
        String configured = null;
        try { configured = Settings.Global.getString(context.getContentResolver(), Settings.Global.DEVICE_NAME); }
        catch (SecurityException ignored) { /* Model fallback remains available on restricted firmware. */ }
        return name(configured, Build.MODEL);
    }

    static String name(String configured, String model) {
        if (configured != null && !configured.trim().isEmpty()) return configured.trim();
        return model == null || model.trim().isEmpty() ? "TV" : model.trim();
    }
}
