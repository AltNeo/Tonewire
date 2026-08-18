package com.tonewire.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Versioned newline-delimited codec. Fields are individually Base64URL encoded. */
public final class ProtocolCodec {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private ProtocolCodec() { }

    public static byte[] encodeBytes(ProtocolMessage message) { return encode(message).getBytes(StandardCharsets.US_ASCII); }
    public static String encode(ProtocolMessage message) {
        StringBuilder frame = new StringBuilder("1\t").append(message.getType()).append('\t').append(message.getRevision());
        for (Map.Entry<String, String> entry : message.getFields().entrySet()) {
            frame.append('\t').append(entry.getKey()).append('=').append(ENCODER.encodeToString(entry.getValue().getBytes(StandardCharsets.UTF_8)));
        }
        frame.append('\n');
        if (frame.length() > ProtocolLimits.MAX_FRAME_BYTES) throw new IllegalArgumentException("frame exceeds limit");
        return frame.toString();
    }
    public static ProtocolMessage decode(String frame) {
        if (frame == null || !frame.endsWith("\n") || frame.indexOf('\r') >= 0 || ProtocolLimits.utf8Length(frame) > ProtocolLimits.MAX_FRAME_BYTES) throw new IllegalArgumentException("invalid frame boundary");
        String[] parts = frame.substring(0, frame.length() - 1).split("\\t", -1);
        if (parts.length < 3 || !"1".equals(parts[0])) throw new IllegalArgumentException("unsupported protocol version");
        long revision;
        try { revision = Long.parseLong(parts[2]); } catch (NumberFormatException exception) { throw new IllegalArgumentException("invalid revision", exception); }
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 3; i < parts.length; i++) {
            int equals = parts[i].indexOf('=');
            if (equals <= 0 || equals != parts[i].lastIndexOf('=') || fields.containsKey(parts[i].substring(0, equals))) throw new IllegalArgumentException("invalid field encoding");
            try { fields.put(parts[i].substring(0, equals), new String(DECODER.decode(parts[i].substring(equals + 1)), StandardCharsets.UTF_8)); }
            catch (IllegalArgumentException exception) { throw new IllegalArgumentException("invalid base64 field", exception); }
        }
        return new ProtocolMessage(parts[1], revision, fields);
    }
}
