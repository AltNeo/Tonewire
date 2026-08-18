package com.tonewire.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Factories and validation for all version-one message payloads. */
public final class ProtocolMessages {
    private ProtocolMessages() { }

    public static ProtocolMessage hello(String deviceId, String role, String nonce) {
        requireId(deviceId, "deviceId"); requireOneOf(role, "phone", "tv"); requireNonce(nonce);
        return message(ProtocolTypes.HELLO, 0, "device_id", deviceId, "role", role, "nonce", nonce);
    }
    public static ProtocolMessage pairRequest(String deviceId, String pairingCode, String nonce) {
        requireId(deviceId, "deviceId"); requirePairingCode(pairingCode); requireNonce(nonce);
        return message(ProtocolTypes.PAIR_REQUEST, 0, "device_id", deviceId, "code", pairingCode, "nonce", nonce);
    }
    public static ProtocolMessage pairAccept(String peerId, String tokenId) {
        requireId(peerId, "peerId"); requireId(tokenId, "tokenId");
        return message(ProtocolTypes.PAIR_ACCEPT, 0, "peer_id", peerId, "token_id", tokenId);
    }
    /** Use only during the short-lived pairing exchange after the TV has verified the displayed code. */
    public static ProtocolMessage pairAccept(String peerId, String tokenId, AuthToken token) {
        if (token == null) throw new NullPointerException("token");
        ProtocolMessage accept = pairAccept(peerId, tokenId);
        Map<String, String> fields = new LinkedHashMap<>(accept.getFields());
        // Keep this name aligned with the peer implementation and the v1 wire contract.
        fields.put("token", token.toBase64());
        return new ProtocolMessage(ProtocolTypes.PAIR_ACCEPT, 0, fields);
    }
    public static ProtocolMessage pairReject(String reason) { return message(ProtocolTypes.PAIR_REJECT, 0, "reason", safeReason(reason)); }
    public static ProtocolMessage stateGet(long revision) {
        return new ProtocolMessage(ProtocolTypes.STATE_GET, revision, Collections.<String, String>emptyMap());
    }
    public static ProtocolMessage stateSnapshot(long revision, boolean enabled, String routeId, List<Integer> bandLevelsMb) {
        requireId(routeId, "routeId"); if (bandLevelsMb.isEmpty() || bandLevelsMb.size() > 32) throw new IllegalArgumentException("invalid bands");
        StringBuilder levels = new StringBuilder();
        for (Integer level : bandLevelsMb) { if (level == null) throw new IllegalArgumentException("null band level"); if (levels.length() > 0) levels.append(','); levels.append(level); }
        return message(ProtocolTypes.STATE_SNAPSHOT, revision, "enabled", Boolean.toString(enabled), "route_id", routeId, "levels_mb", levels.toString());
    }
    public static ProtocolMessage eqEnable(long expectedRevision, boolean enabled) { return message(ProtocolTypes.EQ_ENABLE, expectedRevision, "enabled", Boolean.toString(enabled)); }
    public static ProtocolMessage eqBandSet(long expectedRevision, int band, int levelMb) {
        if (band < 0 || band > 31) throw new IllegalArgumentException("invalid band");
        return message(ProtocolTypes.EQ_BAND_SET, expectedRevision, "band", Integer.toString(band), "level_mb", Integer.toString(levelMb));
    }
    public static ProtocolMessage eqBandAck(long revision, int band, int appliedLevelMb) {
        if (band < 0 || band > 31) throw new IllegalArgumentException("invalid band");
        return message(ProtocolTypes.EQ_BAND_ACK, revision, "band", Integer.toString(band), "level_mb", Integer.toString(appliedLevelMb));
    }
    public static ProtocolMessage presetApply(long expectedRevision, String presetId) { requireId(presetId, "presetId"); return message(ProtocolTypes.EQ_PRESET_APPLY, expectedRevision, "preset_id", presetId); }
    public static ProtocolMessage routeChanged(long revision, String routeId) { requireId(routeId, "routeId"); return message(ProtocolTypes.ROUTE_CHANGED, revision, "route_id", routeId); }
    public static ProtocolMessage error(long revision, String code, String detail) { requireId(code, "code"); return message(ProtocolTypes.ERROR, revision, "code", code, "detail", safeReason(detail)); }
    public static ProtocolMessage ping(String nonce) { requireNonce(nonce); return message(ProtocolTypes.PING, 0, "nonce", nonce); }
    public static ProtocolMessage pong(String nonce) { requireNonce(nonce); return message(ProtocolTypes.PONG, 0, "nonce", nonce); }

    static ProtocolMessage message(String type, long revision, String... fields) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < fields.length; i += 2) map.put(fields[i], fields[i + 1]);
        return new ProtocolMessage(type, revision, map);
    }
    static void requireId(String value, String label) { if (value == null || !value.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("invalid " + label); }
    static void requireNonce(String nonce) { if (nonce == null || !nonce.matches("[A-Za-z0-9_-]{16,128}")) throw new IllegalArgumentException("invalid nonce"); }
    static void requirePairingCode(String code) { if (code == null || !code.matches("[0-9]{6}")) throw new IllegalArgumentException("invalid pairing code"); }
    private static void requireOneOf(String value, String first, String second) { if (!first.equals(value) && !second.equals(value)) throw new IllegalArgumentException("invalid role"); }
    private static String safeReason(String reason) {
        if (reason == null || reason.trim().isEmpty() || reason.length() > 256) {
            throw new IllegalArgumentException("invalid reason");
        }
        return reason;
    }
}
