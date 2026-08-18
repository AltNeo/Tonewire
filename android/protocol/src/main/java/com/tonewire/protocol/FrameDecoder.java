package com.tonewire.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Incremental decoder that accepts arbitrary TCP read boundaries without unbounded buffering. */
public final class FrameDecoder {
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    public List<ProtocolMessage> accept(byte[] bytes, int offset, int length) {
        if (bytes == null || offset < 0 || length < 0 || offset > bytes.length - length) throw new IllegalArgumentException("invalid byte range");
        List<ProtocolMessage> messages = new ArrayList<>();
        for (int i = offset; i < offset + length; i++) {
            byte value = bytes[i];
            if (value < 0) throw fail();
            if (value == '\r') throw fail();
            if (value == '\n') {
                String frame = new String(pending.toByteArray(), StandardCharsets.US_ASCII) + "\n";
                messages.add(ProtocolCodec.decode(frame));
                pending.reset();
            }
            else { if (pending.size() >= ProtocolLimits.MAX_FRAME_BYTES - 1) throw fail(); pending.write(value); }
        }
        return messages;
    }
    public void reset() { pending.reset(); }
    private IllegalArgumentException fail() { pending.reset(); return new IllegalArgumentException("malformed or oversized frame"); }
}
