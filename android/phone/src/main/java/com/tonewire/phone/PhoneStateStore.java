package com.tonewire.phone;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Small durable cache for the last safe UI snapshot; secrets are stored separately. */
public final class PhoneStateStore {
    private static final String PREFS = "tonewire.phone.state";
    private final SharedPreferences preferences;

    public PhoneStateStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveEndpoint(String host, int port, String name) {
        preferences.edit().putString("host", host).putInt("port", port).putString("name", name).apply();
    }

    public String host() { return preferences.getString("host", ""); }
    public int port() { return preferences.getInt("port", 49321); }
    public String name() { return preferences.getString("name", "Living Room TV"); }

    public void saveSnapshot(PhoneState state) {
        SharedPreferences.Editor editor = preferences.edit().putBoolean("enabled", state.enabled)
                .putString("route", state.route).putString("preset", state.preset)
                .putLong("revision", state.revision).putInt("band_count", state.bands.size());
        for (int i = 0; i < state.bands.size(); i++) {
            PhoneState.Band band = state.bands.get(i);
            editor.putInt("band_" + i + "_index", band.index).putInt("band_" + i + "_hz", band.centerHz)
                    .putInt("band_" + i + "_min", band.minMillibels)
                    .putInt("band_" + i + "_max", band.maxMillibels)
                    .putInt("band_" + i + "_level", band.levelMillibels);
        }
        editor.apply();
    }

    public PhoneState restore() {
        int count = preferences.getInt("band_count", 0);
        List<PhoneState.Band> bands = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            bands.add(new PhoneState.Band(preferences.getInt("band_" + i + "_index", i),
                    preferences.getInt("band_" + i + "_hz", 0), preferences.getInt("band_" + i + "_min", -1500),
                    preferences.getInt("band_" + i + "_max", 1500), preferences.getInt("band_" + i + "_level", 0)));
        }
        return new PhoneState(PhoneState.Connection.DISCONNECTED, "Reconnect to refresh TV state", name(),
                preferences.getString("route", "Waiting for TV"), preferences.getString("preset", "No preset"),
                preferences.getBoolean("enabled", false), preferences.getLong("revision", 0L), bands);
    }
}
