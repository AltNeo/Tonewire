package com.tonewire.tv;
import com.tonewire.protocol.ProtocolMessage;
import com.tonewire.protocol.ProtocolMessages;
import com.tonewire.protocol.ProtocolTypes;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;
public final class TvSoundStatusTest {
    @Test public void showsBypassAndFriendlyOutputAndPresetWithoutTouchingEngine() {
        TvSoundStatus status = TvSoundStatus.from(new ProtocolMessage(ProtocolTypes.STATE_SNAPSHOT, 0,
                Map.of("enabled", "false", "engine", "ok", "route_name", "HDMI · TV", "preset_name", "Night watch")));
        assertEquals("EQ bypassed", status.eq); assertEquals("HDMI · TV", status.output);
        assertEquals("Night watch", status.preset); assertEquals("Equalizer ready", status.health);
    }
    @Test public void unavailableAndLostControlAreExplicit() {
        assertEquals("EQ unavailable", TvSoundStatus.from(ProtocolMessages.error(0, "engine_unavailable", "starting")).eq);
        TvSoundStatus lost = TvSoundStatus.from(new ProtocolMessage(ProtocolTypes.STATE_SNAPSHOT, 0,
                Map.of("enabled", "true", "engine", "no_control")));
        assertEquals("EQ on", lost.eq); assertEquals("Another app controls the equalizer", lost.health);
    }
    @Test public void pairingCodeIsAutomaticOnlyWithoutPairedPhones() {
        assertTrue(TvSoundStatus.showPairingCode(false, 0));
        assertFalse(TvSoundStatus.showPairingCode(false, 1));
        assertTrue(TvSoundStatus.showPairingCode(true, 1));
    }
}
