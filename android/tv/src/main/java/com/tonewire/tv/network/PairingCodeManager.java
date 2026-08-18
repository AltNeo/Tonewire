package com.tonewire.tv.network;

import java.security.SecureRandom;

/** Generates a short-lived, displayable pairing code without retaining a long-lived secret. */
public final class PairingCodeManager {
    public static final long DEFAULT_TTL_MILLIS = 5 * 60 * 1000L;

    private final SecureRandom random;
    private final long ttlMillis;
    private String code;
    private long expiresAtMillis;

    public PairingCodeManager() {
        this(new SecureRandom(), DEFAULT_TTL_MILLIS);
    }

    PairingCodeManager(SecureRandom random, long ttlMillis) {
        this.random = random;
        this.ttlMillis = ttlMillis;
    }

    public synchronized String currentCode(long nowMillis) {
        if (code == null || nowMillis >= expiresAtMillis) {
            code = String.format(java.util.Locale.US, "%06d", random.nextInt(1_000_000));
            expiresAtMillis = nowMillis + ttlMillis;
        }
        return code;
    }

    public synchronized boolean matches(String candidate, long nowMillis) {
        return candidate != null && candidate.equals(currentCode(nowMillis));
    }

    public synchronized long remainingMillis(long nowMillis) {
        currentCode(nowMillis);
        return Math.max(0L, expiresAtMillis - nowMillis);
    }

    public synchronized void refresh(long nowMillis) {
        code = null;
        currentCode(nowMillis);
    }
}
