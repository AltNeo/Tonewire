package com.tonewire.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.tonewire.protocol.ProtocolMessage;
import com.tonewire.protocol.ProtocolMessages;
import com.tonewire.protocol.ProtocolTypes;
import com.tonewire.tv.network.EngineCommandDispatcher;

import java.util.Collections;

import org.junit.Test;

/** Covers the dispatcher hand-off which makes a phone preset route-specific. */
public final class EnginePresetCommandTest {
    @Test public void appliesAndAssignsPresetToTheCurrentRoute() {
        FakeController controller = new FakeController();
        ProtocolMessage result = new EngineCommandDispatcher(controller)
                .dispatch(ProtocolMessages.presetApply(7, TonewirePresets.CLEAR_DIALOGUE));

        assertEquals(ProtocolTypes.STATE_SNAPSHOT, result.getType());
        assertEquals(8, result.getRevision());
        assertEquals(TonewirePresets.CLEAR_DIALOGUE, result.requireField("preset_name"));
        assertEquals("speaker:built-in", controller.assignedFingerprint);
        assertSame(controller.appliedPreset, controller.assignedPreset);
        assertSame(controller.appliedPreset, PresetSelector.select(controller.assignedPreset, null, 5));
    }

    private static final class FakeController implements TvEqualizerController {
        private final EqCapabilities capabilities = new EqCapabilities(-1500, 1500,
                new int[] {60_000, 230_000, 910_000, 3_600_000, 14_000_000}, new int[5][2], new String[0]);
        private final AudioRoute route = new AudioRoute(AudioRoute.Kind.SPEAKER, "Built-in", "", 1);
        private EqPreset appliedPreset;
        private EqPreset assignedPreset;
        private String assignedFingerprint;

        @Override public EqualizerEngine.State getState() {
            return new EqualizerEngine.State(capabilities, new int[] {0, 0, 0, 0, 0}, true, true);
        }
        @Override public String getActivePresetName() { return appliedPreset == null ? "custom" : appliedPreset.getName(); }
        @Override public RouteSnapshot getRoutes() { return new RouteSnapshot(Collections.singletonList(route)); }
        @Override public int setBandLevel(int band, int requestedMillibels) { return requestedMillibels; }
        @Override public void setEnabled(boolean enabled) { }
        @Override public void applyPreset(EqPreset preset) { appliedPreset = preset; }
        @Override public void setRoutePreset(String fingerprint, EqPreset preset) { assignedFingerprint = fingerprint; assignedPreset = preset; }
        @Override public void setAutostartEnabled(boolean enabled) { }
    }
}
