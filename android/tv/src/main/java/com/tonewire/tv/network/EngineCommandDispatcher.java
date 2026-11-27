package com.tonewire.tv.network;

import com.tonewire.protocol.ProtocolMessage;
import com.tonewire.protocol.ProtocolMessages;
import com.tonewire.protocol.ProtocolTypes;
import com.tonewire.tv.AudioRoute;
import com.tonewire.tv.RouteSnapshot;
import com.tonewire.tv.EqualizerEngine;
import com.tonewire.tv.EqPreset;
import com.tonewire.tv.TonewirePresets;
import com.tonewire.tv.TvEqualizerController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Protocol-to-engine adapter. It deliberately contains no audio lifecycle ownership. */
public final class EngineCommandDispatcher implements CommandDispatcher {
    private final TvEqualizerController controller;
    private final String tvName;

    public EngineCommandDispatcher(TvEqualizerController controller) { this(controller, "TV"); }
    public EngineCommandDispatcher(TvEqualizerController controller, String tvName) {
        this.controller = controller;
        this.tvName = tvName;
    }

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
            if (ProtocolTypes.EQ_PRESET_SAVE.equals(command.getType()) || ProtocolTypes.EQ_PRESET_DELETE.equals(command.getType())) {
                if (!controller.supportsSavedPresets()) return ProtocolMessages.error(command.getRevision(), "unsupported_command", "Saved presets are unavailable");
                if (ProtocolTypes.EQ_PRESET_SAVE.equals(command.getType())) controller.savePreset(command.requireField("name"));
                else controller.deletePreset(command.requireField("preset_id"));
                return snapshot(command.getRevision() + 1);
            }
            if (ProtocolTypes.EQ_PRESET_APPLY.equals(command.getType())) {
                EqualizerEngine.State state = controller.getState();
                if (state == null) return ProtocolMessages.error(command.getRevision(), "engine_unavailable", "Equalizer is starting");
                AudioRoute route = controller.getRoutes().getSelectedRoute();
                EqPreset preset = controller.resolvePreset(command.requireField("preset_id"), state.capabilities);
                controller.applyPreset(preset);
                // A global EQ can apply a preset even when the active output is unknown.
                // Do not assign it to an arbitrary route when several distinct outputs exist.
                if (route != null) controller.setRoutePreset(route.getFingerprint(), preset);
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
        RouteSnapshot routes = controller.getRoutes();
        AudioRoute selected = routes.getSelectedRoute();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("features", controller.supportsSavedPresets() ? "presets" : "");
        fields.put("presets", com.tonewire.protocol.PresetCatalog.encode(controller.getPresets()));
        fields.put("tv_name", tvName);
        fields.put("route_id", selected == null ? "unknown" : selected.getKind().name().toLowerCase(java.util.Locale.US));
        fields.put("route_name", selected != null ? selected.getDisplayName()
                : routes.getRoutes().isEmpty() ? "No output connected" : "Multiple outputs");
        fields.put("preset_name", TonewirePresets.displayName(controller.getActivePresetName()));
        fields.put("engine", state == null ? "unavailable" : !state.hasControl ? "no_control"
                : routes.isAmbiguous() ? "route_ambiguous" : "ok");
        fields.put("enabled", Boolean.toString(state != null && state.enabled));
        fields.put("levels_mb", state == null ? "" : join(state.levelsMillibels));
        fields.put("centers_hz", state == null ? "" : join(state.capabilities.getCenterFrequenciesMillihertz(), 1000));
        fields.put("min_mb", state == null ? "0" : Integer.toString(state.capabilities.getMinimumMillibels()));
        fields.put("max_mb", state == null ? "0" : Integer.toString(state.capabilities.getMaximumMillibels()));
        return new ProtocolMessage(ProtocolTypes.STATE_SNAPSHOT, revision, fields);
    }

    private static String join(int[] values) { return join(values, 1); }
    private static String join(int[] values, int divisor) {
        StringBuilder joined = new StringBuilder();
        for (int value : values) { if (joined.length() > 0) joined.append(','); joined.append(value / divisor); }
        return joined.toString();
    }
}
