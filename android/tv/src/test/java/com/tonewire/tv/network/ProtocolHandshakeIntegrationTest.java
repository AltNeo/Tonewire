package com.tonewire.tv.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.tonewire.protocol.AuthToken;
import com.tonewire.protocol.FrameDecoder;
import com.tonewire.protocol.ProtocolAuthenticator;
import com.tonewire.protocol.ProtocolCodec;
import com.tonewire.protocol.ProtocolMessage;
import com.tonewire.protocol.ProtocolMessages;
import com.tonewire.protocol.ProtocolTypes;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/** Verifies the wire sequence the phone performs after a six-digit pairing exchange. */
public final class ProtocolHandshakeIntegrationTest {
    @Test public void pairThenAuthenticateThenRequestSnapshotAcrossPartialFrames() {
        PairingCodeManager codes = new PairingCodeManager(new SecureRandom(), 60_000L);
        String code = codes.currentCode(10L);
        ProtocolMessage pair = ProtocolMessages.pairRequest("phone_test", code, "abcdefghijklmnop");
        assertTrue(codes.matches(pair.requireField("code"), 11L));

        AuthToken token = AuthToken.generate(new SecureRandom());
        ProtocolMessage proof = ProtocolAuthenticator.authToken(token, "phone_test", "abcdefghijklmnop", "qrstuvwxyzABCDEF");
        assertTrue(ProtocolAuthenticator.verify(token, proof, "qrstuvwxyzABCDEF"));

        ProtocolMessage request = ProtocolMessages.stateGet(0L);
        FrameDecoder decoder = new FrameDecoder();
        byte[] bytes = ProtocolCodec.encode(request).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertTrue(decoder.accept(bytes, 0, 2).isEmpty());
        List<ProtocolMessage> decoded = decoder.accept(bytes, 2, bytes.length - 2);
        assertEquals(request, decoded.get(0));

        ProtocolMessage snapshot = new ProtocolMessage(ProtocolTypes.STATE_SNAPSHOT, 0L, Map.of(
                "enabled", "true", "route_id", "speaker", "levels_mb", "0,0,0,0,0",
                "centers_hz", "60,230,910,3600,14000", "min_mb", "-1500", "max_mb", "1500"));
        assertEquals("60,230,910,3600,14000", snapshot.requireField("centers_hz"));
    }
}
