package com.tonewire.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ProtocolCodecTest {
    @Test public void roundTripPreservesStructuredUnicodeFields() {
        ProtocolMessage expected = new ProtocolMessage(ProtocolTypes.ERROR, 7, Map.of("code", "bad_request", "detail", "gain=+3\n東京"));
        assertEquals(expected, ProtocolCodec.decode(ProtocolCodec.encode(expected)));
    }

    @Test public void decoderBuffersPartialTcpReads() {
        ProtocolMessage message = ProtocolMessages.eqBandSet(9, 3, -1200);
        byte[] frame = ProtocolCodec.encodeBytes(message);
        FrameDecoder decoder = new FrameDecoder();
        assertTrue(decoder.accept(frame, 0, 3).isEmpty());
        assertTrue(decoder.accept(frame, 3, frame.length - 4).isEmpty());
        assertEquals(List.of(message), decoder.accept(frame, frame.length - 1, 1));
    }

    @Test public void decoderEmitsMultipleFramesFromOneRead() {
        byte[] first = ProtocolCodec.encodeBytes(ProtocolMessages.ping("abcdefghijklmnop"));
        byte[] second = ProtocolCodec.encodeBytes(ProtocolMessages.pong("qrstuvwxyzABCDEF"));
        byte[] joined = new byte[first.length + second.length];
        System.arraycopy(first, 0, joined, 0, first.length); System.arraycopy(second, 0, joined, first.length, second.length);
        assertEquals(2, new FrameDecoder().accept(joined, 0, joined.length).size());
    }

    @Test public void malformedFramesAreRejectedAndBufferIsCleared() {
        FrameDecoder decoder = new FrameDecoder();
        assertThrows(IllegalArgumentException.class, () -> decoder.accept("bad\r\n".getBytes(StandardCharsets.US_ASCII), 0, 5));
        byte[] valid = ProtocolCodec.encodeBytes(ProtocolMessages.ping("abcdefghijklmnop"));
        assertEquals(1, decoder.accept(valid, 0, valid.length).size());
        assertThrows(IllegalArgumentException.class, () -> ProtocolCodec.decode("1\thello\t-1\n"));
        assertThrows(IllegalArgumentException.class, () -> ProtocolCodec.decode("1\thello\t0\tbad=***\n"));
    }

    @Test public void decoderRejectsOversizedUnterminatedFrame() {
        byte[] data = new byte[ProtocolLimits.MAX_FRAME_BYTES];
        java.util.Arrays.fill(data, (byte) 'x');
        assertThrows(IllegalArgumentException.class, () -> new FrameDecoder().accept(data, 0, data.length));
    }

    @Test public void authProofVerifiesButCannotBeReusedForOtherServerNonce() {
        AuthToken token = AuthToken.generate(new SecureRandom());
        ProtocolMessage proof = ProtocolAuthenticator.authToken(token, "phone-1", "abcdefghijklmnop", "qrstuvwxyzABCDEF");
        assertTrue(ProtocolAuthenticator.verify(token, proof, "qrstuvwxyzABCDEF"));
        assertFalse(ProtocolAuthenticator.verify(token, proof, "ponmlkjihgfedcba"));
        ProtocolMessage tampered = new ProtocolMessage(ProtocolTypes.AUTH_TOKEN, 0, Map.of("peer_id", "phone-2", "client_nonce", "abcdefghijklmnop", "server_nonce", "qrstuvwxyzABCDEF", "mac", proof.requireField("mac")));
        assertFalse(ProtocolAuthenticator.verify(token, tampered, "qrstuvwxyzABCDEF"));
    }

    @Test public void pairAcceptTokenRoundTripsIntoTheServerAuthProof() {
        AuthToken token = AuthToken.generate(new SecureRandom());
        ProtocolMessage pairAccept = ProtocolMessages.pairAccept("tonewire_tv", "token_1", token);
        ProtocolMessage receivedAccept = ProtocolCodec.decode(ProtocolCodec.encode(pairAccept));

        AuthToken receivedToken = AuthToken.fromBase64(receivedAccept.requireField("token"));
        ProtocolMessage proof = ProtocolAuthenticator.authToken(receivedToken, "phone_123",
                "abcdefghijklmnop", "qrstuvwxyzABCDEF");
        ProtocolMessage receivedProof = ProtocolCodec.decode(ProtocolCodec.encode(proof));

        assertTrue(ProtocolAuthenticator.verify(token, receivedProof, "qrstuvwxyzABCDEF"));
    }

    @Test public void authTokenAcceptsAndroidUrlSafePaddedEncoding() {
        byte[] tokenBytes = new byte[ProtocolLimits.TOKEN_BYTES];
        new SecureRandom().nextBytes(tokenBytes);
        String padded = java.util.Base64.getUrlEncoder().encodeToString(tokenBytes);
        AuthToken parsed = AuthToken.fromBase64(padded);
        assertTrue(ProtocolAuthenticator.verify(parsed, ProtocolAuthenticator.authToken(parsed, "phone_1",
                "abcdefghijklmnop", "qrstuvwxyzABCDEF"), "qrstuvwxyzABCDEF"));
    }

    @Test public void factoriesValidateCommandsAndRevisions() {
        assertEquals("-1200", ProtocolMessages.eqBandSet(4, 2, -1200).requireField("level_mb"));
        assertThrows(IllegalArgumentException.class, () -> ProtocolMessages.eqBandSet(4, 32, 0));
        assertThrows(IllegalArgumentException.class, () -> ProtocolMessages.pairRequest("phone", "12345", "abcdefghijklmnop"));
        RevisionTracker tracker = new RevisionTracker(4);
        assertTrue(tracker.accepts(4)); assertFalse(tracker.accepts(3)); assertEquals(5, tracker.advance());
        assertThrows(IllegalArgumentException.class, () -> tracker.updateFromSnapshot(4));
    }
}
