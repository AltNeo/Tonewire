package com.tonewire.tv.network;

import com.tonewire.protocol.ProtocolMessage;
import com.tonewire.protocol.ProtocolMessages;
import com.tonewire.protocol.ProtocolTypes;
import com.tonewire.tv.AudioRoute;
import com.tonewire.tv.EqualizerEngine;
import com.tonewire.tv.EqPreset;
import com.tonewire.tv.TonewirePresets;
import com.tonewire.tv.TvEqualizerController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Protocol-to-engine adapter. It deliberately contains no audio lifecycle ownership. */
public final class EngineCommandDispatcher implements CommandDispatcher {
    private final TvEqualizerController controller;

    public EngineCommandDispatcher(TvEqualizerController controller) { this.controller = controller; }

    @Override public ProtocolMessage dispatch(ProtocolMessage command) {
        try {
            if (ProtocolTypes.STATE_GET.equals(command.getType())) return snapshot(command.getRevision());
            if (ProtocolTypes.EQ_ENABLE.equals(command.getType())) {
                controller.setEnabled(Boolean.parseBoolean(command.requireField("enabled")));
                return snapshot(command.getRevision() + 1);
            }
            if (ProtocolTypes.EQ_BAND_SET.equals(command.getType())) {
                int band = Integer.parseInt(command.requireField("band"));
                int level = controller.setBandLevel(band, Integer.parseInt(command.requireField("level_mb")));
                return ProtocolMessages.eqBandAck(command.getRevision() + 1, band, level);
            }
            if (ProtocolTypes.EQ_PRESET_APPLY.equals(command.getType())) {
                EqualizerEngine.State state = controller.getState();
                if (state == null) return ProtocolMessages.error(command.getRevision(), "engine_unavailable", "Equalizer is starting");
                AudioRoute route = controller.getRoutes().getSelectedRoute();
                if (route == null) return ProtocolMessages.error(command.getRevision(), "route_unavailable", "Output route is ambiguous or disconnected");
                EqPreset preset = TonewirePresets.create(command.requireField("preset_id"), state.capabilities);
                controller.applyPreset(preset);
                controller.setRoutePreset(route.getFingerprint(), preset);
                return snapshot(command.getRevision() + 1);
            }
            if (ProtocolTypes.PING.equals(command.getType())) return ProtocolMessages.pong(command.requireField("nonce"));
            return ProtocolMessages.error(command.getRevision(), "unsupported_command", "This TV command is unavailable");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ProtocolMessages.error(command.getRevision(), "invalid_command", exception.getMessage() == null ? "Command failed" : exception.getMessage());
        }
    }

    private ProtocolMessage snapshot(long revision) {
        EqualizerEngine.State state = controller.getState();
        if (state == null) return ProtocolMessages.error(revision, "engine_unavailable", "Equalizer is starting");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("enabled", Boolean.toString(state.enabled));
        fields.put("route_id", routeId());
        fields.put("preset_name", controller.getActivePresetName());
        fields.put("levels_mb", join(state.levelsMillibels));
        fields.put("centers_hz", join(state.capabilities.getCenterFrequenciesMillihertz(), 1000));
        fields.put("min_mb", Integer.toString(state.capabilities.getMinimumMillibels()));
        fields.put("max_mb", Integer.toString(state.capabilities.getMaximumMillibels()));
        return new ProtocolMessage(ProtocolTypes.STATE_SNAPSHOT, revision, fields);
    }

    private String routeId() {
        AudioRoute selected = controller.getRoutes().getSelectedRoute();
        return selected == null ? "unknown" : selected.getKind().name().toLowerCase(java.util.Locale.US);
    }
    private static String join(int[] values) { return join(values, 1); }
    private static String join(int[] values, int divisor) {
        StringBuilder joined = new StringBuilder();
        for (int value : values) { if (joined.length() > 0) joined.append(','); joined.append(value / divisor); }
        return joined.toString();
    }
}
