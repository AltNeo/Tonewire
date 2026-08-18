package com.tonewire.protocol;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Wire-size and schema limits. Enforce these before retaining peer supplied data. */
public final class ProtocolLimits {
    public static final int MAX_FRAME_BYTES = 8 * 1024;
    public static final int MAX_FIELDS = 32;
    public static final int MAX_FIELD_NAME_BYTES = 48;
    public static final int MAX_FIELD_VALUE_BYTES = 4096;
    public static final int TOKEN_BYTES = 32;
    private static final Pattern TYPE = Pattern.compile("[a-z][a-z0-9]*(?:[.][a-z][a-z0-9]*)*");
    private static final Pattern FIELD = Pattern.compile("[a-z][a-z0-9_]*");

    private ProtocolLimits() { }

    static void requireMessageType(String type) {
        if (type == null || !TYPE.matcher(type).matches() || utf8Length(type) > 64) {
            throw new IllegalArgumentException("invalid message type");
        }
    }

    static void requireField(String name, String value) {
        if (name == null || !FIELD.matcher(name).matches() || utf8Length(name) > MAX_FIELD_NAME_BYTES) {
            throw new IllegalArgumentException("invalid field name");
        }
        if (value == null || utf8Length(value) > MAX_FIELD_VALUE_BYTES || value.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("invalid field value");
        }
    }

    static int utf8Length(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }
}
