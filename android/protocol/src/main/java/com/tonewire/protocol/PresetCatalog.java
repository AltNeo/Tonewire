package com.tonewire.protocol;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** v1 presets field: comma-separated id:base64url(UTF-8 name):builtIn records. */
public final class PresetCatalog {
    public static final class Entry {
        public final String id;
        public final String name;
        public final boolean builtIn;
        public Entry(String id, String name, boolean builtIn) {
            if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("invalid preset id");
            if (name == null || name.trim().isEmpty() || name.length() > 48 || name.contains("|")
                    || name.chars().anyMatch(c -> Character.isISOControl(c))) throw new IllegalArgumentException("Use a name of 1–48 characters without control characters or |");
            this.id = id; this.name = name.trim(); this.builtIn = builtIn;
        }
    }
    private PresetCatalog() { }
    public static String encode(List<Entry> entries) {
        if (entries.size() > 11) throw new IllegalArgumentException("preset catalog too large");
        StringBuilder value = new StringBuilder();
        for (Entry entry : entries) {
            if (value.length() > 0) value.append(',');
            value.append(entry.id).append(':').append(Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(entry.name.getBytes(StandardCharsets.UTF_8))).append(':').append(entry.builtIn);
        }
        return value.toString();
    }
    public static List<Entry> decode(String value) {
        if (value.isEmpty()) return Collections.emptyList();
        List<Entry> entries = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (String record : value.split(",", -1)) {
            String[] parts = record.split(":", -1);
            if (parts.length != 3 || !("true".equals(parts[2]) || "false".equals(parts[2]))) throw new IllegalArgumentException("invalid preset catalog");
            Entry entry = new Entry(parts[0], new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8), Boolean.parseBoolean(parts[2]));
            if (!ids.add(entry.id) || entries.size() == 11) throw new IllegalArgumentException("invalid preset catalog");
            entries.add(entry);
        }
        return Collections.unmodifiableList(entries);
    }
}
