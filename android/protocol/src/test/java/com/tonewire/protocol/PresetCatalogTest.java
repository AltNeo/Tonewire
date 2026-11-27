package com.tonewire.protocol;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PresetCatalogTest {
    @Test public void unicodeAndDelimiterNamesRoundTripThroughV1Snapshot() {
        String catalog = PresetCatalog.encode(Arrays.asList(new PresetCatalog.Entry("night_watch", "Night watch", true),
                new PresetCatalog.Entry("custom_one", "Film, café: late", false)));
        ProtocolMessage message = new ProtocolMessage(ProtocolTypes.STATE_SNAPSHOT, 0, Collections.singletonMap("presets", catalog));
        String wire = ProtocolCodec.encode(message); assertTrue(wire.startsWith("1\t"));
        java.util.List<PresetCatalog.Entry> decoded = PresetCatalog.decode(ProtocolCodec.decode(wire).requireField("presets"));
        assertEquals("Film, café: late", decoded.get(1).name);
        assertTrue(decoded.get(0).builtIn); assertFalse(decoded.get(1).builtIn);
    }
    @Test public void malformedAndDuplicateCatalogRecordsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PresetCatalog.decode("bad"));
        String one = PresetCatalog.encode(Collections.singletonList(new PresetCatalog.Entry("one", "One", true)));
        assertThrows(IllegalArgumentException.class, () -> PresetCatalog.decode(one + "," + one));
    }
}
