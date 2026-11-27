package com.tonewire.tv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.tonewire.protocol.ProtocolCodec;
import com.tonewire.protocol.ProtocolMessage;
import com.tonewire.protocol.ProtocolMessages;
import com.tonewire.protocol.ProtocolTypes;
import com.tonewire.tv.network.EngineCommandDispatcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.junit.Test;

/**
 * Binds the current production dispatcher to reviewed literal v1 frames.
 *
 * <p>Only frames the TV actually emits are covered here. Server-only frames (hello, auth.token,
 * pair.accept, pair.reject) need a real socket/TvNetworkServer and are covered as literal resource
 * bytes by {@code :protocol:test} instead. The legacy
 * {@link ProtocolMessages#stateSnapshot} helper is deliberately not the dispatcher contract.
 */
public final class TvProtocolGoldenFramesTest {
    private static final String ROOT = "v1/";

    @Test public void dispatcherHealthySnapshotMatchesGoldenFrameExactly() throws IOException {
        ProtocolMessage snapshot = new EngineCommandDispatcher(new Controller(), "Living Room TV")
                .dispatch(ProtocolMessages.stateGet(0));
        assertEquals(ProtocolTypes.STATE_SNAPSHOT, snapshot.getType());
        assertEquals("ok", snapshot.requireField("engine"));
        assertEquals("presets", snapshot.requireField("features"));
        assertEquals(12, snapshot.getFields().size());
        assertArrayEquals(golden("state-snapshot-healthy.txt"), ProtocolCodec.encodeBytes(snapshot));
    }

    @Test public void dispatcherUnavailableSnapshotMatchesGoldenFrameExactly() throws IOException {
        Controller controller = new Controller();
        controller.state = null;
        ProtocolMessage snapshot = new EngineCommandDispatcher(controller, "Living Room TV")
                .dispatch(ProtocolMessages.stateGet(0));
        assertEquals("unavailable", snapshot.requireField("engine"));
        assertEquals("", snapshot.requireField("levels_mb"));
        assertArrayEquals(golden("state-snapshot-engine-unavailable.txt"), ProtocolCodec.encodeBytes(snapshot));
    }

    @Test public void dispatcherBandAckAndPongMatchGoldenFramesExactly() throws IOException {
        EngineCommandDispatcher dispatcher = new EngineCommandDispatcher(new Controller(), "Living Room TV");
        assertArrayEquals(golden("eq-band-ack.txt"),
                ProtocolCodec.encodeBytes(dispatcher.dispatch(ProtocolMessages.eqBandSet(4, 2, -1200))));
        assertArrayEquals(golden("pong.txt"),
                ProtocolCodec.encodeBytes(dispatcher.dispatch(ProtocolMessages.ping("abcdefghijklmnop"))));
    }

    @Test public void dispatcherInvalidCommandErrorMatchesGoldenFrameExactly() throws IOException {
        // The fake controller rejects the band; the dispatcher translates that to invalid_command.
        Controller controller = new Controller();
        controller.rejectBand = true;
        ProtocolMessage error = new EngineCommandDispatcher(controller, "Living Room TV")
                .dispatch(ProtocolMessages.eqBandSet(4, 1, 0));
        assertEquals(ProtocolTypes.ERROR, error.getType());
        assertEquals("invalid_command", error.requireField("code"));
        assertArrayEquals(golden("error-invalid-command.txt"), ProtocolCodec.encodeBytes(error));
    }

    @Test public void dispatcherSnapshotDivergesFromLegacyStateSnapshotHelper() throws IOException {
        ProtocolMessage legacy = ProtocolMessages.stateSnapshot(3, true, "speaker",
                java.util.List.of(0, 0, 0, 0, 0));
        ProtocolMessage current = new EngineCommandDispatcher(new Controller(), "Living Room TV")
                .dispatch(ProtocolMessages.stateGet(0));
        assertEquals(3, legacy.getFields().size());
        assertFalse(legacy.getFields().equals(current.getFields()));
        assertFalse(current.getFields().containsKey("payload"));
        assertTrue(current.getFields().containsKey("presets"));
        assertTrue(current.getFields().containsKey("tv_name"));
    }

    @Test public void tvFixtureManifestCoversFiveDispatcherFrames() throws IOException {
        String manifest = new String(resource(ROOT + "cases.tsv"), StandardCharsets.US_ASCII);
        int cases = 0;
        for (String line : manifest.split("\n")) {
            if (line.isEmpty() || line.startsWith("file\t")) continue;
            String[] columns = line.split("\t", -1);
            assertEquals(4, columns.length);
            assertEquals("emitted", columns[2]);
            assertNotNull(ProtocolCodec.decode(new String(golden(columns[0]), StandardCharsets.US_ASCII)));
            cases++;
        }
        assertEquals(5, cases);
    }

    private static byte[] golden(String file) throws IOException {
        return resource(ROOT + file);
    }

    private static byte[] resource(String path) throws IOException {
        try (InputStream input = TvProtocolGoldenFramesTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull("missing test resource " + path, input);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[512];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private static final class Controller implements TvEqualizerController {
        final EqCapabilities capabilities = new EqCapabilities(-1500, 1500,
                new int[] {60_000, 230_000}, new int[2][2], new String[0]);
        final AudioRoute route = new AudioRoute(AudioRoute.Kind.HDMI, "Living Room display", "hdmi:0", 2);
        EqualizerEngine.State state = new EqualizerEngine.State(capabilities, new int[] {-100, 0}, true, true);
        RouteSnapshot routes = new RouteSnapshot(Collections.singletonList(route));
        boolean rejectBand;

        @Override public EqualizerEngine.State getState() { return state; }
        @Override public String getActivePresetName() { return "clear_dialogue"; }
        @Override public RouteSnapshot getRoutes() { return routes; }
        @Override public boolean supportsSavedPresets() { return true; }
        @Override public int setBandLevel(int band, int requestedMillibels) {
            if (rejectBand) throw new IllegalArgumentException("invalid band");
            return requestedMillibels;
        }
        @Override public void setEnabled(boolean enabled) { }
        @Override public void applyPreset(EqPreset preset) { }
        @Override public void setRoutePreset(String fingerprint, EqPreset preset) { }
        @Override public void setAutostartEnabled(boolean enabled) { }
    }
}
