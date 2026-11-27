package com.tonewire.phone;
import org.junit.Test;
import static org.junit.Assert.*;

public final class EndpointRediscoveryTest {
    // Documentation-reserved TEST-NET ranges, so the fixtures cannot resemble a real device.
    private static final String OLD_HOST = "192.0.2.10";
    private static final String NEW_HOST = "192.0.2.11";

    @Test public void sameIdentityAtChangedAddressIsAdopted() {
        assertTrue(EndpointRediscovery.shouldAdopt("tv-123", OLD_HOST, 49321,
                "tv-123", NEW_HOST, 49321));
    }
    @Test public void anotherTvCannotReplaceSelectedEndpoint() {
        assertFalse(EndpointRediscovery.shouldAdopt("tv-123", OLD_HOST, 49321,
                "tv-456", NEW_HOST, 49321));
    }
    @Test public void unchangedEndpointDoesNotTriggerReconnectLoop() {
        assertFalse(EndpointRediscovery.shouldAdopt("tv-123", OLD_HOST, 49321,
                "tv-123", OLD_HOST, 49321));
    }
    @Test public void matchingIdentityWithChangedPortIsAdopted() {
        assertTrue(EndpointRediscovery.shouldAdopt("tv-123", OLD_HOST, 49321,
                "tv-123", OLD_HOST, 49322));
    }
    @Test public void absentLegacyIdsAndInvalidEndpointsCannotStealSavedEndpoint() {
        assertFalse(EndpointRediscovery.shouldAdopt("", OLD_HOST, 49321, "", NEW_HOST, 49321));
        assertFalse(EndpointRediscovery.shouldAdopt("tv-123", OLD_HOST, 49321, "", NEW_HOST, 49321));
        assertFalse(EndpointRediscovery.shouldAdopt("tv-123", OLD_HOST, 49321, "tv-123", "", 49321));
        assertFalse(EndpointRediscovery.shouldAdopt("tv-123", OLD_HOST, 49321, "tv-123", "new", 65536));
    }
}
