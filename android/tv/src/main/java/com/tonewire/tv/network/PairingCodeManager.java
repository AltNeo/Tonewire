package com.tonewire.tv.network;

import java.security.SecureRandom;

/** Generates a short-lived, displayable pairing code without retaining a long-lived secret. */
public final class PairingCodeManager {
    public static final long DEFAULT_TTL_MILLIS = 5 * 60 * 1000L;

    private final SecureRandom random;
    private final long ttlMillis;
    public static final int MAX_WRONG_CODES = 5;
    public static final long LOCK_MILLIS = 30_000L;
    public enum Result { MATCH, INVALID, RATE_LIMITED }
    private int wrongCodes;
    private long lockedUntilMillis;
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
            String next = String.format(java.util.Locale.US, "%06d", random.nextInt(1_000_000));
            if (next.equals(code)) next = String.format(java.util.Locale.US, "%06d", (Integer.parseInt(next) + 1) % 1_000_000);
            code = next;
            expiresAtMillis = nowMillis + ttlMillis;
        }
        return code;
    }

    public synchronized boolean matches(String candidate, long nowMillis) {
        return verify(candidate, nowMillis) == Result.MATCH;
    }

    public synchronized Result verify(String candidate, long nowMillis) {
        if (nowMillis < lockedUntilMillis) return Result.RATE_LIMITED;
        if (lockedUntilMillis != 0) { lockedUntilMillis = 0; wrongCodes = 0; }
        if (candidate != null && candidate.equals(currentCode(nowMillis))) {
            wrongCodes = 0;
            return Result.MATCH;
        }
        if (++wrongCodes >= MAX_WRONG_CODES) {
            lockedUntilMillis = nowMillis + LOCK_MILLIS;
            refresh(nowMillis);
            return Result.RATE_LIMITED;
        }
        return Result.INVALID;
    }

    public synchronized long lockRemainingMillis(long nowMillis) {
        return Math.max(0, lockedUntilMillis - nowMillis);
    }

    public synchronized long remainingMillis(long nowMillis) {
        currentCode(nowMillis);
        return Math.max(0L, expiresAtMillis - nowMillis);
    }

    public synchronized void refresh(long nowMillis) {
        expiresAtMillis = 0;
        currentCode(nowMillis);
    }
}
