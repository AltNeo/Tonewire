package com.tonewire.protocol;

/** Stable message type names for the LAN protocol. */
public final class ProtocolTypes {
    public static final String HELLO = "hello";
    public static final String PAIR_REQUEST = "pair.request";
    public static final String PAIR_ACCEPT = "pair.accept";
    public static final String PAIR_REJECT = "pair.reject";
    public static final String AUTH_TOKEN = "auth.token";
    public static final String STATE_GET = "state.get";
    public static final String STATE_SNAPSHOT = "state.snapshot";
    public static final String EQ_ENABLE = "eq.enable";
    public static final String EQ_BAND_SET = "eq.band.set";
    public static final String EQ_BAND_ACK = "eq.band.ack";
    public static final String EQ_PRESET_SAVE = "eq.preset.save";
    public static final String EQ_PRESET_DELETE = "eq.preset.delete";
    public static final String EQ_PRESET_APPLY = "eq.preset.apply";
    public static final String ROUTE_CHANGED = "route.changed";
    public static final String ERROR = "error";
    public static final String PING = "ping";
    public static final String PONG = "pong";
    private ProtocolTypes() { }
}
