package com.tonewire.phone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable view state. Band metadata is always supplied by the TV after a connection. */
public final class PhoneState {
    public enum Connection { DISCONNECTED, DISCOVERING, CONNECTING, PAIRING, CONNECTED, ERROR }

    public static final class Band {
        public final int index;
        public final int centerHz;
        public final int minMillibels;
        public final int maxMillibels;
        public final int levelMillibels;

        public Band(int index, int centerHz, int minMillibels, int maxMillibels, int levelMillibels) {
            this.index = index;
            this.centerHz = centerHz;
            this.minMillibels = minMillibels;
            this.maxMillibels = maxMillibels;
            this.levelMillibels = clamp(levelMillibels, minMillibels, maxMillibels);
        }

        public Band withLevel(int level) {
            return new Band(index, centerHz, minMillibels, maxMillibels, level);
        }
    }

    public final Connection connection;
    public final String status;
    public final String tvName;
    public final String route;
    public final String preset;
    public final boolean enabled;
    public final long revision;
    public final List<Band> bands;

    public PhoneState(Connection connection, String status, String tvName, String route, String preset,
            boolean enabled, long revision, List<Band> bands) {
        this.connection = connection;
        this.status = status;
        this.tvName = tvName;
        this.route = route;
        this.preset = preset;
        this.enabled = enabled;
        this.revision = revision;
        this.bands = Collections.unmodifiableList(new ArrayList<>(bands));
    }

    public static PhoneState initial() {
        return new PhoneState(Connection.DISCONNECTED, "Find your TV to begin", "No TV selected",
                "Waiting for TV", "No preset", false, 0L, Collections.emptyList());
    }

    public PhoneState withConnection(Connection value, String message) {
        return new PhoneState(value, message, tvName, route, preset, enabled, revision, bands);
    }

    public PhoneState withBandLevel(int index, int level) {
        List<Band> replacement = new ArrayList<>(bands);
        for (int i = 0; i < replacement.size(); i++) {
            if (replacement.get(i).index == index) {
                replacement.set(i, replacement.get(i).withLevel(level));
                break;
            }
        }
        return new PhoneState(connection, status, tvName, route, preset, enabled, revision, replacement);
    }

    public PhoneState withEnabled(boolean value) {
        return new PhoneState(connection, status, tvName, route, preset, value, revision, bands);
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
