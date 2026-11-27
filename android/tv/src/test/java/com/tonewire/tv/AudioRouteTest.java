package com.tonewire.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.media.AudioDeviceInfo;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public final class AudioRouteTest {
    @Test public void usbFingerprintPrefersPublicAddress() {
        AudioRoute route = new AudioRoute(AudioRoute.Kind.USB_DEVICE, "USB-Audio - USB Composite Device", "card=2;device=0", 14);
        assertEquals("usb_device:card=2;device=0", route.getFingerprint());
    }
    @Test public void identifiesSupportedOutputTypes() {
        assertEquals(AudioRoute.Kind.SPEAKER, AudioRoute.kindForType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER));
        assertEquals(AudioRoute.Kind.USB_HEADSET, AudioRoute.kindForType(AudioDeviceInfo.TYPE_USB_HEADSET));
    }
    @Test public void doesNotGuessWhenOutputsAreAmbiguous() {
        RouteSnapshot ambiguous = new RouteSnapshot(Arrays.asList(
                new AudioRoute(AudioRoute.Kind.SPEAKER, "speaker", "", 1),
                new AudioRoute(AudioRoute.Kind.HDMI, "tv", "", 2)));
        assertTrue(ambiguous.isAmbiguous());
        assertNull(ambiguous.getSelectedRoute());
        assertFalse(new RouteSnapshot(Collections.singletonList(
                new AudioRoute(AudioRoute.Kind.SPEAKER, "speaker", "", 1))).isAmbiguous());
    }
    @Test public void equivalentOutputsSelectStableIdentityIndependentOfEnumerationOrder() {
        AudioRoute first = new AudioRoute(AudioRoute.Kind.HDMI, "TV", "hdmi:0", 2);
        AudioRoute duplicate = new AudioRoute(AudioRoute.Kind.HDMI, "TV", "hdmi:0", 9);
        RouteSnapshot forward = new RouteSnapshot(Arrays.asList(first, duplicate));
        RouteSnapshot reverse = new RouteSnapshot(Arrays.asList(duplicate, first));
        assertFalse(forward.isAmbiguous());
        assertEquals(first, forward.getSelectedRoute());
        assertEquals(first, reverse.getSelectedRoute());
    }
}
