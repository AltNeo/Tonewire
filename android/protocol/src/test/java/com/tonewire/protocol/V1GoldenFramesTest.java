package com.tonewire.protocol;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/**
 * Byte-for-byte compatibility checks against reviewed v1 golden frames.
 *
 * <p>The expected bytes come from test resources, never from the codec. Test-only calls to
 * {@link ProtocolCodec#encode} compare production factories against those literal bytes; they are
 * not used to derive expectations.
 */
public final class V1GoldenFramesTest {
    private static final String ROOT = "v1/";
    private static final String TOKEN = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

    @Test public void goldenHelloFrameDecodesWithAdditiveTvId() throws IOException {
        ProtocolMessage hello = decode("hello-tv.txt");
        assertEquals(ProtocolTypes.HELLO, hello.getType());
        assertEquals(0L, hello.getRevision());
        assertEquals("tonewire_tv", hello.requireField("device_id"));
        assertEquals("tv", hello.requireField("role"));
        assertEquals(repeat('A', 32), hello.requireField("nonce"));
        assertEquals("tv_7f3a9c21", hello.requireField("tv_id"));
        assertEquals(4, hello.getFields().size());
    }

    @Test public void goldenPairAcceptCarriesTokenAndProductionDerivedTokenId() throws IOException {
        ProtocolMessage accept = decode("pair-accept.txt");
        assertEquals(ProtocolTypes.PAIR_ACCEPT, accept.getType());
        assertEquals("tonewire_tv", accept.requireField("peer_id"));
        assertEquals(TOKEN, accept.requireField("token"));
        assertEquals(Integer.toHexString(TOKEN.hashCode()), accept.requireField("token_id"));
        // The server hand-builds this frame with Android URL_SAFE (padded) Base64. The factory uses
        // unpadded AuthToken.toBase64(), so only the field values are comparable here.
        ProtocolMessage factory = ProtocolMessages.pairAccept("tonewire_tv",
                "91d4afca", AuthToken.fromBase64(TOKEN));
        assertEquals(accept.getFields().keySet(), factory.getFields().keySet());
        assertEquals(accept.requireField("peer_id"), factory.requireField("peer_id"));
        assertEquals(accept.requireField("token_id"), factory.requireField("token_id"));
        assertArrayEquals(AuthToken.fromBase64(TOKEN).copyBytes(),
                AuthToken.fromBase64(factory.requireField("token")).copyBytes());
        assertEquals(TOKEN, AuthToken.fromBase64(accept.requireField("token")).toBase64() + "=");
    }

    @Test public void goldenPairRejectFrameMatchesFactory() throws IOException {
        assertFactoryFrame("pair-reject.txt", ProtocolMessages.pairReject("invalid_or_expired_code"));
    }

    @Test public void goldenAuthTokenOkFrameDecodes() throws IOException {
        ProtocolMessage ack = decode("auth-token-ok.txt");
        assertEquals(ProtocolTypes.AUTH_TOKEN, ack.getType());
        assertEquals("ok", ack.requireField("status"));
        assertEquals(1, ack.getFields().size());
    }

    @Test public void goldenHealthySnapshotHasCurrentDispatcherFieldSet() throws IOException {
        ProtocolMessage snapshot = decode("state-snapshot-healthy.txt");
        assertEquals(ProtocolTypes.STATE_SNAPSHOT, snapshot.getType());
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("features", "presets");
        expected.put("presets", "flat_reference:RmxhdCByZWZlcmVuY2U:true,clear_dialogue:Q2xlYXIgZGlhbG9ndWU:true,night_watch:TmlnaHQgd2F0Y2g:true");
        expected.put("tv_name", "Living Room TV");
        expected.put("route_id", "hdmi");
        expected.put("route_name", "HDMI \u00b7 Living Room display");
        expected.put("preset_name", "Clear dialogue");
        expected.put("engine", "ok");
        expected.put("enabled", "true");
        expected.put("levels_mb", "-100,0");
        expected.put("centers_hz", "60,230");
        expected.put("min_mb", "-1500");
        expected.put("max_mb", "1500");
        assertEquals(expected, snapshot.getFields());
        assertEquals(expected.get("presets"), PresetCatalog.encode(PresetCatalog.decode(expected.get("presets"))));
    }

    @Test public void goldenUnavailableSnapshotKeepsIdentityAndEmptiesBands() throws IOException {
        ProtocolMessage snapshot = decode("state-snapshot-engine-unavailable.txt");
        assertEquals("unavailable", snapshot.requireField("engine"));
        assertEquals("Living Room TV", snapshot.requireField("tv_name"));
        assertEquals("Clear dialogue", snapshot.requireField("preset_name"));
        assertEquals("false", snapshot.requireField("enabled"));
        assertEquals("", snapshot.requireField("levels_mb"));
        assertEquals("", snapshot.requireField("centers_hz"));
        assertEquals("0", snapshot.requireField("min_mb"));
        assertEquals("0", snapshot.requireField("max_mb"));
    }

    @Test public void goldenEqBandAckPongAndErrorFramesMatchFactories() throws IOException {
        assertFactoryFrame("eq-band-ack.txt", ProtocolMessages.eqBandAck(5, 2, -1200));
        assertFactoryFrame("pong.txt", ProtocolMessages.pong("abcdefghijklmnop"));
        assertFactoryFrame("error-engine-unavailable.txt",
                ProtocolMessages.error(0, "engine_unavailable", "The audio service is not ready"));
        assertFactoryFrame("error-invalid-command.txt",
                ProtocolMessages.error(4, "invalid_command", "invalid band"));
    }

    @Test public void modeledRouteChangedFrameMatchesFactoryButIsNotEmittedByCurrentTv() throws IOException {
        assertFactoryFrame("route-changed.txt", ProtocolMessages.routeChanged(0, "hdmi"));
        String manifest = new String(resource(ROOT + "cases.tsv"), StandardCharsets.US_ASCII);
        assertTrue(manifest.contains("route-changed.txt\troute.changed\tmodeled-not-emitted\t"));
    }

    @Test public void legacyStateSnapshotFactoryStaysMinimalAndIsNotTheDispatcherContract() throws IOException {
        ProtocolMessage legacy = decode("state-snapshot-legacy-minimal.txt");
        assertEquals(3, legacy.getFields().size());
        assertEquals("true", legacy.requireField("enabled"));
        assertEquals("speaker", legacy.requireField("route_id"));
        assertEquals("0,0,0,0,0", legacy.requireField("levels_mb"));
        assertFactoryFrame("state-snapshot-legacy-minimal.txt",
                ProtocolMessages.stateSnapshot(3, true, "speaker", List.of(0, 0, 0, 0, 0)));
    }

    @Test public void unknownAdditiveFieldsDecodeAndKnownFieldsRemainIntact() throws IOException {
        byte[] bytes = golden("state-snapshot-unknown-field.txt");
        ProtocolMessage snapshot = ProtocolCodec.decode(new String(bytes, StandardCharsets.US_ASCII));
        assertEquals("demo-capability", snapshot.requireField("future_feature"));
        assertEquals("999", snapshot.requireField("unknown_metric"));
        assertEquals("ok", snapshot.requireField("engine"));
        assertEquals("Living Room TV", snapshot.requireField("tv_name"));
        assertEquals("-100,0", snapshot.requireField("levels_mb"));
        assertEquals("presets", snapshot.requireField("features"));
        assertArrayEquals(bytes, ProtocolCodec.encodeBytes(snapshot));
    }

    @Test public void allGoldenFramesAreLiteralTabDelimitedAsciiEndingInLf() throws IOException {
        List<String> files = manifestFiles();
        assertEquals(13, files.size());
        for (String file : files) {
            byte[] bytes = golden(file);
            String frame = new String(bytes, StandardCharsets.US_ASCII);
            assertTrue(file, frame.endsWith("\n"));
            assertFalse(file, frame.contains("\r"));
            assertTrue(file, frame.startsWith("1\t"));
            assertEquals(file, 1, count(frame, '\n'));
            assertNotNull(file, ProtocolCodec.decode(frame));
        }
    }

    @Test public void manifestEnumeratesExactlyTheEightEmittedV1Types() throws IOException {
        Set<String> emitted = new LinkedHashSet<>();
        int cases = 0;
        for (String line : new String(resource(ROOT + "cases.tsv"), StandardCharsets.US_ASCII).split("\n")) {
            if (line.isEmpty() || line.startsWith("file\t")) continue;
            String[] columns = line.split("\t", -1);
            assertEquals(4, columns.length);
            cases++;
            if ("emitted".equals(columns[2])) emitted.add(columns[1]);
        }
        assertEquals(13, cases);
        assertEquals(new LinkedHashSet<>(Arrays.asList("hello", "pair.accept", "pair.reject",
                "auth.token", "state.snapshot", "eq.band.ack", "pong", "error")), emitted);
    }

    private static void assertFactoryFrame(String file, ProtocolMessage message) throws IOException {
        assertArrayEquals(golden(file), ProtocolCodec.encodeBytes(message));
    }

    private static ProtocolMessage decode(String file) throws IOException {
        return ProtocolCodec.decode(new String(golden(file), StandardCharsets.US_ASCII));
    }

    private static byte[] golden(String file) throws IOException {
        return resource(ROOT + file);
    }

    private static List<String> manifestFiles() throws IOException {
        String manifest = new String(resource(ROOT + "cases.tsv"), StandardCharsets.US_ASCII);
        java.util.ArrayList<String> files = new java.util.ArrayList<>();
        for (String line : manifest.split("\n")) {
            if (!line.isEmpty() && !line.startsWith("file\t")) files.add(line.split("\t", -1)[0]);
        }
        return files;
    }

    private static byte[] resource(String path) throws IOException {
        try (InputStream input = V1GoldenFramesTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull("missing test resource " + path, input);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[512];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private static int count(String value, char target) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) == target) count++;
        return count;
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }
}
