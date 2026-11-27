package com.tonewire.tv;

import com.tonewire.protocol.ProtocolMessage;
import com.tonewire.protocol.ProtocolTypes;
import java.util.Map;

/** Read-only presentation of the same authoritative snapshot sent to phones. */
final class TvSoundStatus {
    final String eq;
    final String output;
    final String preset;
    final String health;
    private TvSoundStatus(String eq, String output, String preset, String health) {
        this.eq = eq; this.output = output; this.preset = preset; this.health = health;
    }
    static TvSoundStatus from(ProtocolMessage snapshot) {
        if (snapshot == null || !ProtocolTypes.STATE_SNAPSHOT.equals(snapshot.getType())) {
            return new TvSoundStatus("EQ unavailable", "Output unavailable", "Preset unavailable", "Equalizer is starting or unavailable");
        }
        Map<String, String> fields = snapshot.getFields();
        String engine = fields.getOrDefault("engine", "unavailable");
        String health;
        switch (engine) {
            case "ok": health = "Equalizer ready"; break;
            case "no_control": health = "Another app controls the equalizer"; break;
            case "route_ambiguous": health = "Active output is unclear · check connected outputs"; break;
            default: health = "Equalizer unavailable";
        }
        return new TvSoundStatus("unavailable".equals(engine) ? "EQ unavailable"
                : Boolean.parseBoolean(fields.get("enabled")) ? "EQ on" : "EQ bypassed",
                fields.getOrDefault("route_name", "Output unavailable"),
                fields.getOrDefault("preset_name", "Custom"), health);
    }
    static boolean showPairingCode(boolean requested, int pairedPhoneCount) { return requested || pairedPhoneCount == 0; }
}
