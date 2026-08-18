package com.tonewire.tv;

import android.media.AudioDeviceInfo;

import java.util.Locale;

/** Public-API-only output route descriptor and stable-ish fingerprint. */
public final class AudioRoute {
    public enum Kind { SPEAKER, USB_DEVICE, USB_HEADSET, HDMI, HDMI_ARC, BLUETOOTH, UNKNOWN }

    private final Kind kind;
    private final String productName;
    private final String address;
    private final int deviceId;

    public AudioRoute(Kind kind, String productName, String address, int deviceId) {
        this.kind = kind;
        this.productName = sanitize(productName);
        this.address = sanitize(address);
        this.deviceId = deviceId;
    }

    public static AudioRoute from(AudioDeviceInfo device) {
        return new AudioRoute(kindForType(device.getType()), String.valueOf(device.getProductName()),
                device.getAddress(), device.getId());
    }

    public Kind getKind() { return kind; }
    public String getProductName() { return productName; }
    public String getAddress() { return address; }
    public int getDeviceId() { return deviceId; }

    public String getFingerprint() {
        String identity = !address.isEmpty() ? address : (!productName.isEmpty() ? productName : "id-" + deviceId);
        return kind.name().toLowerCase(Locale.US) + ":" + identity.toLowerCase(Locale.US);
    }

    public static Kind kindForType(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return Kind.SPEAKER;
            case AudioDeviceInfo.TYPE_USB_DEVICE: return Kind.USB_DEVICE;
            case AudioDeviceInfo.TYPE_USB_HEADSET: return Kind.USB_HEADSET;
            case AudioDeviceInfo.TYPE_HDMI: return Kind.HDMI;
            case AudioDeviceInfo.TYPE_HDMI_ARC: return Kind.HDMI_ARC;
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return Kind.BLUETOOTH;
            default: return Kind.UNKNOWN;
        }
    }

    private static String sanitize(String text) { return text == null ? "" : text.trim(); }
}
