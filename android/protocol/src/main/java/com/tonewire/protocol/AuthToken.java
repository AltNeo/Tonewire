package com.tonewire.protocol;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** A 256-bit pairing secret. Persist its Base64 form only in encrypted platform storage. */
public final class AuthToken {
    private final byte[] bytes;
    private AuthToken(byte[] bytes) { this.bytes = bytes; }
    public static AuthToken generate(SecureRandom random) {
        if (random == null) throw new NullPointerException("random");
        byte[] bytes = new byte[ProtocolLimits.TOKEN_BYTES]; random.nextBytes(bytes); return new AuthToken(bytes);
    }
    public static AuthToken fromBase64(String value) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            if (decoded.length != ProtocolLimits.TOKEN_BYTES) throw new IllegalArgumentException("token must be 256 bits");
            return new AuthToken(decoded);
        } catch (IllegalArgumentException exception) { throw new IllegalArgumentException("invalid token", exception); }
    }
    public String toBase64() { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    byte[] copyBytes() { return Arrays.copyOf(bytes, bytes.length); }
}
