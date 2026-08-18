# Physical end-to-end test report

Test date: 25 July 2026. Target: CloudWalker `hi352l`, Android 9/API 28, built-in speaker route.
The Android phone emulator paired through the production LAN protocol and controlled the physical
TV; ADB was not used to transport equalizer commands.

## Result

The test passed for the built-in speaker PCM path. Spotify player `piid:23` remained started from
22:40:02.076 to 22:44:01.235. The attended EQ sweep occurred from 22:43:52 to 22:43:57 while that
playback was active. The phone sent six band commands and enable/disable controls; the TV recorded
eight corresponding `TW_APPLIED` entries (six `eq.band.set`, two `eq.enable`). The original state
was restored: bypassed, with levels `+300, 0, 0, 0, +300` millibels.

## Latency

Phone log RTT samples in milliseconds were `70, 24, 20, 14, 18, 21, 16, 17`. The nearest-rank
result is p50 **18 ms**, p95 **70 ms**, maximum **70 ms**. This meets the p95 <100 ms criterion.
It is a conservative local command-to-apply upper bound, not a cross-device timing calculation:
the device clocks are offset, while the TV's same-revision/type `TW_APPLIED` records establish that
application occurs before the phone receives its ACK.

## Runtime evidence

- TV total PSS with its status Activity visible: **42,776 KB**; private dirty: **9,228 KB**. No
  WebViews were present.
- Final settled background-service total PSS: **8,462 KB**; idle CPU: **0.0%**; Tonewire wake
  locks: **none**.
- AudioFlinger reported exactly one session-0 effect chain containing the NXP Software Ltd.
  Equalizer, 48 kHz stereo PCM float processing, on `AUDIO_DEVICE_OUT_SPEAKER`.
- The final AudioService dump shows `piid:23` paused only after the described playback interval.
- Final APK sizes: TV debug **88,903 bytes**, TV minified release **40,248 bytes**; phone debug
  **43,560 bytes**, phone minified release **20,672 bytes**.
- After installing the final APKs with app data retained, the existing credential authenticated
  without repairing, the phone returned to `LIVE`, the original curve remained restored, and the
  TV foreground service continued listening on stable port `49321` after its Activity was closed.
- A physical reboot passed headless autostart. This firmware delivered `BOOT_COMPLETED` about
  50 seconds after `sys.boot_completed`; Tonewire then recreated the foreground service, stable
  listener, authenticated network server, and single NXP session-0 effect without opening its
  Activity. The phone uses a low-rate idle heartbeat plus capped eight-second reconnect backoff so
  it detects a stale TCP link and tolerates that delayed boot.

Evidence is retained in [`android/build/e2e`](../build/e2e):
`final-phone-logcat.txt`, `final-tv-logcat.txt`, `final-phone-restored.png`,
`final-audioflinger.txt`, `final-audio-dumpsys.txt`, `stable-tv-port.png`, and
`stable-phone-live.png`.

## Limits and follow-up

The USB Bluetooth transmitter was absent during this test, so USB route fingerprinting and audible
USB validation remain pending hardware reconnection. Direct/passthrough compressed streams
(AC3/E-AC3/AC4/DTS/DTS-HD/AAC) can bypass the software effect and remain unsupported until each
path is audibly validated.
