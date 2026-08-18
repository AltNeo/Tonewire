package com.tonewire.protocol;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA-256 authentication for a new TCP connection; never sends the pairing token. */
public final class ProtocolAuthenticator {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String DOMAIN = "tonewire-auth-v1";
    private ProtocolAuthenticator() { }

    public static ProtocolMessage authToken(AuthToken token, String peerId, String clientNonce, String serverNonce) {
        ProtocolMessages.requireId(peerId, "peerId"); ProtocolMessages.requireNonce(clientNonce); ProtocolMessages.requireNonce(serverNonce);
        return ProtocolMessages.message(ProtocolTypes.AUTH_TOKEN, 0, "peer_id", peerId, "client_nonce", clientNonce,
                "server_nonce", serverNonce, "mac", macBase64(token, peerId, clientNonce, serverNonce));
    }
    public static boolean verify(AuthToken token, ProtocolMessage message, String expectedServerNonce) {
        if (token == null || message == null || !ProtocolTypes.AUTH_TOKEN.equals(message.getType())) return false;
        try {
            String peerId = message.requireField("peer_id");
            String clientNonce = message.requireField("client_nonce");
            String serverNonce = message.requireField("server_nonce");
            ProtocolMessages.requireId(peerId, "peerId"); ProtocolMessages.requireNonce(clientNonce); ProtocolMessages.requireNonce(serverNonce);
            if (!constantTimeEquals(serverNonce.getBytes(StandardCharsets.US_ASCII), expectedServerNonce.getBytes(StandardCharsets.US_ASCII))) return false;
            return constantTimeEquals(Base64.getUrlDecoder().decode(message.requireField("mac")),
                    Base64.getUrlDecoder().decode(macBase64(token, peerId, clientNonce, serverNonce)));
        } catch (IllegalArgumentException exception) { return false; }
    }
    public static boolean constantTimeEquals(byte[] left, byte[] right) { return left != null && right != null && MessageDigest.isEqual(left, right); }
    private static String macBase64(AuthToken token, String peerId, String clientNonce, String serverNonce) {
        if (token == null) throw new NullPointerException("token");
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(token.copyBytes(), HMAC_ALGORITHM));
            String data = DOMAIN + '\u0000' + peerId + '\u0000' + clientNonce + '\u0000' + serverNonce;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) { throw new IllegalStateException("HMAC unavailable", exception); }
    }
}
