package com.tonewire.tv;

import com.tonewire.protocol.ProtocolCodec;
import com.tonewire.protocol.ProtocolMessage;
import com.tonewire.protocol.ProtocolMessages;
import com.tonewire.protocol.ProtocolTypes;
import com.tonewire.tv.network.EngineCommandDispatcher;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public final class EngineSnapshotTest {
    @Test public void snapshotCarriesFriendlyNamesAndActualHardwareInProtocolV1() {
        Controller controller = new Controller();
        ProtocolMessage snapshot = snapshot(controller);
        assertEquals(ProtocolTypes.STATE_SNAPSHOT, snapshot.getType());
        assertEquals("Living Room TV", snapshot.requireField("tv_name"));
        assertEquals("HDMI · Living Room display", snapshot.requireField("route_name"));
        assertEquals("hdmi", snapshot.requireField("route_id"));
        assertEquals("Clear dialogue", snapshot.requireField("preset_name"));
        assertEquals("ok", snapshot.requireField("engine"));
        assertEquals("true", snapshot.requireField("enabled"));
        assertEquals("-100,0", snapshot.requireField("levels_mb"));
        assertEquals("60,230", snapshot.requireField("centers_hz"));
        assertEquals("-1500", snapshot.requireField("min_mb"));
        assertEquals("1500", snapshot.requireField("max_mb"));
        String frame = ProtocolCodec.encode(snapshot);
        assertTrue(frame.startsWith("1\tstate.snapshot\t"));
        assertEquals("Living Room TV", ProtocolCodec.decode(frame).requireField("tv_name"));
    }

    @Test public void lostControlReportsNoControlEvenWhenRouteIsAlsoAmbiguous() {
        Controller controller = new Controller();
        controller.state = new EqualizerEngine.State(controller.capabilities, new int[] {-100, 0}, true, false);
        controller.routes = new RouteSnapshot(Collections.emptyList());
        assertEquals("no_control", snapshot(controller).requireField("engine"));
    }

    @Test public void distinctOutputsReportAmbiguityWithoutInventingAnActiveOutput() {
        Controller controller = new Controller();
        controller.routes = new RouteSnapshot(Arrays.asList(controller.route,
                new AudioRoute(AudioRoute.Kind.BLUETOOTH, "Headphones", "remote", 9)));
        ProtocolMessage snapshot = snapshot(controller);
        assertEquals("route_ambiguous", snapshot.requireField("engine"));
        assertEquals("Multiple outputs", snapshot.requireField("route_name"));
    }

    @Test public void equivalentDescriptorsAreHealthyAndKeepHumanReadableRoute() {
        Controller controller = new Controller();
        controller.routes = new RouteSnapshot(Arrays.asList(controller.route,
                new AudioRoute(AudioRoute.Kind.HDMI, "Living Room display", "hdmi:0", 9)));
        assertEquals("ok", snapshot(controller).requireField("engine"));
        assertEquals("HDMI · Living Room display", snapshot(controller).requireField("route_name"));
    }

    @Test public void noOutputsHasExplicitDisconnectedNameAndAmbiguousEngine() {
        Controller controller = new Controller();
        controller.routes = new RouteSnapshot(Collections.emptyList());
        assertEquals("route_ambiguous", snapshot(controller).requireField("engine"));
        assertEquals("No output connected", snapshot(controller).requireField("route_name"));
    }

    @Test public void unavailableEngineStillReturnsStatusSnapshotWithNoInventedBands() {
        Controller controller = new Controller();
        controller.state = null;
        ProtocolMessage snapshot = snapshot(controller);
        assertEquals(ProtocolTypes.STATE_SNAPSHOT, snapshot.getType());
        assertEquals("unavailable", snapshot.requireField("engine"));
        assertEquals("Living Room TV", snapshot.requireField("tv_name"));
        assertEquals("", snapshot.requireField("levels_mb"));
        assertEquals("", snapshot.requireField("centers_hz"));
    }

    @Test public void presetDisplayNamesCoverBuiltInsAndPreserveUserNames() {
        assertEquals("Flat reference", TonewirePresets.displayName("flat_reference"));
        assertEquals("Night watch", TonewirePresets.displayName("night_watch"));
        assertEquals("Custom", TonewirePresets.displayName("custom"));
        assertEquals("My evening curve", TonewirePresets.displayName("My evening curve"));
    }

    @Test public void deviceNameFallsBackToModelOnlyWhenUnsetOrBlank() {
        assertEquals("Living Room TV", TvIdentity.name(" Living Room TV ", "UHD Streaming STB"));
        assertEquals("UHD Streaming STB", TvIdentity.name(null, "UHD Streaming STB"));
        assertEquals("UHD Streaming STB", TvIdentity.name("  ", "UHD Streaming STB"));
        assertEquals("TV", TvIdentity.name(null, null));
    }

    @Test public void unnamedOutputUsesReadableKindInsteadOfFingerprint() {
        assertEquals("Speakers", new AudioRoute(AudioRoute.Kind.SPEAKER, "", "", 1).getDisplayName());
        assertEquals("USB headset", new AudioRoute(AudioRoute.Kind.USB_HEADSET, "", "card=2", 2).getDisplayName());
    }

    private ProtocolMessage snapshot(Controller controller) {
        return new EngineCommandDispatcher(controller, "Living Room TV").dispatch(ProtocolMessages.stateGet(0));
    }

    private static final class Controller implements TvEqualizerController {
        final EqCapabilities capabilities = new EqCapabilities(-1500, 1500,
                new int[] {60_000, 230_000}, new int[2][2], new String[0]);
        final AudioRoute route = new AudioRoute(AudioRoute.Kind.HDMI, "Living Room display", "hdmi:0", 2);
        EqualizerEngine.State state = new EqualizerEngine.State(capabilities, new int[] {-100, 0}, true, true);
        RouteSnapshot routes = new RouteSnapshot(Collections.singletonList(route));
        @Override public EqualizerEngine.State getState() { return state; }
        @Override public String getActivePresetName() { return "clear_dialogue"; }
        @Override public RouteSnapshot getRoutes() { return routes; }
        @Override public int setBandLevel(int band, int requestedMillibels) { return requestedMillibels; }
        @Override public void setEnabled(boolean enabled) { }
        @Override public void applyPreset(EqPreset preset) { }
        @Override public void setRoutePreset(String fingerprint, EqPreset preset) { }
        @Override public void setAutostartEnabled(boolean enabled) { }
    }
}
