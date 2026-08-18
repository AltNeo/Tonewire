package com.tonewire.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** An immutable, transport-neutral Tonewire protocol envelope. */
public final class ProtocolMessage {
    public static final int VERSION = 1;
    private final String type;
    private final long revision;
    private final Map<String, String> fields;

    /**
     * Compatibility constructor for pre-v1 callers. New code should use typed fields or
     * {@link ProtocolMessages}; the payload is encoded as the single {@code payload} field.
     */
    @Deprecated
    public ProtocolMessage(String type, String payload) {
        this(type, 0, Collections.singletonMap("payload", Objects.requireNonNull(payload, "payload")));
    }

    public ProtocolMessage(String type, long revision, Map<String, String> fields) {
        ProtocolLimits.requireMessageType(type);
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        Objects.requireNonNull(fields, "fields");
        if (fields.size() > ProtocolLimits.MAX_FIELDS) {
            throw new IllegalArgumentException("too many fields");
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            ProtocolLimits.requireField(entry.getKey(), entry.getValue());
            copy.put(entry.getKey(), entry.getValue());
        }
        this.type = type;
        this.revision = revision;
        this.fields = Collections.unmodifiableMap(copy);
    }

    public String getType() { return type; }
    public long getRevision() { return revision; }
    public Map<String, String> getFields() { return fields; }
    /** @deprecated use {@link #getFields()} or a message factory. */
    @Deprecated public String getPayload() { return fields.getOrDefault("payload", ""); }
    public String requireField(String name) {
        String value = fields.get(name);
        if (value == null) throw new IllegalArgumentException("missing field: " + name);
        return value;
    }

    @Override public boolean equals(Object object) {
        if (!(object instanceof ProtocolMessage)) return false;
        ProtocolMessage other = (ProtocolMessage) object;
        return revision == other.revision && type.equals(other.type) && fields.equals(other.fields);
    }
    @Override public int hashCode() { return Objects.hash(type, revision, fields); }
    @Override public String toString() { return "ProtocolMessage{" + type + ", revision=" + revision + "}"; }
}
