package com.tonewire.phone;

import com.tonewire.protocol.ProtocolCodec;
import com.tonewire.protocol.ProtocolMessage;
import com.tonewire.protocol.ProtocolTypes;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public final class EngineSnapshotParsingTest {
    @Test public void exactUnavailableTvSnapshotDecodesToWarningAndZeroBands() {
        PhoneState state = TvConnectionClient.stateFrom(snapshot("unavailable", "", ""));
        assertEquals(PhoneState.Connection.CONNECTED, state.connection);
        assertEquals("Living Room TV", state.tvName);
        assertEquals("HDMI · Display", state.route);
        assertTrue(state.bands.isEmpty());
        assertEquals("The TV equalizer is unavailable. Changes cannot be applied.", state.status);
        assertFalse(TvConnectionClient.engineReady(state));
    }

    @Test public void healthWarningsDisableControlsWhileHealthySnapshotKeepsLiveBands() {
        for (String engine : new String[] {"no_control", "route_ambiguous", "future_status"}) {
            PhoneState state = TvConnectionClient.stateFrom(snapshot(engine, "-100,0", "60,230"));
            assertFalse(TvConnectionClient.engineReady(state));
            assertFalse("Live local control".equals(state.status));
        }
        PhoneState healthy = TvConnectionClient.stateFrom(snapshot("ok", "-100,0", "60,230"));
        assertEquals(2, healthy.bands.size());
        assertEquals(-100, healthy.bands.get(0).levelMillibels);
        assertTrue(TvConnectionClient.engineReady(healthy));
    }

    @Test public void savedPresetControlsRequireAdvertisedCapabilityAndCatalogSurvivesEdits() {
        ProtocolMessage legacy = snapshot("ok", "-100,0", "60,230");
        assertFalse(TvConnectionClient.stateFrom(legacy).supportsSavedPresets);
        Map<String, String> fields = new LinkedHashMap<>(legacy.getFields());
        fields.put("features", "presets");
        fields.put("presets", com.tonewire.protocol.PresetCatalog.encode(java.util.Collections.singletonList(
                new com.tonewire.protocol.PresetCatalog.Entry("custom_test", "Movie, late: night", false))));
        PhoneState current = TvConnectionClient.stateFrom(new ProtocolMessage(ProtocolTypes.STATE_SNAPSHOT, 0, fields));
        assertTrue(current.supportsSavedPresets);
        assertEquals("Movie, late: night", current.presets.get(0).name);
        assertFalse(current.presets.get(0).builtIn);
        assertTrue(current.withBandLevel(0, -200).supportsSavedPresets);
        assertEquals(1, current.withEnabled(false).presets.size());
    }

    private ProtocolMessage snapshot(String engine, String levels, String centers) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("tv_name", "Living Room TV");
        fields.put("route_id", "hdmi");
        fields.put("route_name", "HDMI · Display");
        fields.put("preset_name", "Custom");
        fields.put("engine", engine);
        fields.put("enabled", "false");
        fields.put("levels_mb", levels);
        fields.put("centers_hz", centers);
        fields.put("min_mb", levels.isEmpty() ? "0" : "-1500");
        fields.put("max_mb", levels.isEmpty() ? "0" : "1500");
        // Exercise the real v1 codec and production phone mapper, without constructing Android UI.
        return ProtocolCodec.decode(ProtocolCodec.encode(new ProtocolMessage(ProtocolTypes.STATE_SNAPSHOT, 0, fields)));
    }
}
