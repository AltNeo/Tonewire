# Target TV capability report

Probe date: 25 July 2026

Endpoint tested: private LAN endpoint (redacted)

The temporary diagnostic APK was disabled during capability inspection, changed no EQ parameters,
and was removed after the test.

## Decision

The target TV supports the proposed lightweight architecture.

An ordinary Android application successfully created an `Equalizer` on global audio session `0`,
obtained control, and caused AudioFlinger to create a global session-0 effect chain. This is the
most important technical prerequisite for the project.

An audible A/B test is still required for each playback application and output route. The
capability result proves that the engine attaches correctly, but compressed passthrough or direct
hardware playback can still bypass a software effect.

## Hardware and operating system

| Property | Observed value |
| --- | --- |
| Manufacturer | CloudWalker Tech |
| Model | `hi352l` |
| SoC/platform | HiSilicon Hi3751 / `Hi3751V350` |
| CPU | Four 32-bit ARMv7 Cortex-A53-class cores |
| ABI | `armeabi-v7a`, `armeabi` |
| Android | 9 / API 28 |
| Security patch | 5 November 2019 |
| Framebuffer | 1280 × 720 at 160 dpi |
| Physical RAM | 485,884 KiB, approximately 475 MiB |
| Available RAM during test | 125,776 KiB, approximately 123 MiB |
| Data partition | 4.3 GiB total, 1.2 GiB free |

The reported build fingerprint resembles a Nexus 6 Android 7.1.1 fingerprint even though the
platform reports Android 9/API 28. Feature checks must therefore use actual API availability and
runtime probes rather than assuming the fingerprint describes the firmware accurately.

## Verified Equalizer capabilities

| Capability | Observed value |
| --- | --- |
| Global session-0 construction | Successful |
| Application has effect control | Yes |
| Initial effect state | Disabled |
| Gain range | -1500 to +1500 millibels, or -15 to +15 dB |
| Band count | 5 |
| Built-in preset count | 10 |
| Implementation | NXP Software Ltd. insert effect |
| AudioFlinger chain | One effect on global session `0` |
| Effect processing format | 48 kHz stereo PCM float |

### Bands

| Band | Center | Nominal range |
| ---: | ---: | ---: |
| 0 | 60 Hz | 30–120 Hz |
| 1 | 230 Hz | 120–460 Hz |
| 2 | 910 Hz | 460–1,800 Hz |
| 3 | 3.6 kHz | 1.8–7 kHz |
| 4 | 14 kHz | 7–20 kHz |

The current stored levels reported by the engine were `+3, 0, 0, 0, +3 dB`. The probe did not
change or enable them.

Other registered effects include Dynamic Bass Boost, Virtualizer, Loudness Enhancer, Dynamics
Processing, reverb, downmix and visualizer. Version one should expose only Equalizer and safe
bypass control until performance and clipping behavior have been characterized.

## Current playback path

During the test, active application playback used:

- Android `AudioTrack`
- 48 kHz
- stereo
- 16-bit PCM at the primary output
- built-in speaker route

This is the favorable path for real-time EQ. The audio policy also declares direct compressed
outputs for AC3, E-AC3, AC4, DTS, DTS-HD and AAC. Those direct paths may bypass the global EQ and
must be tested separately.

## USB Bluetooth transmitter

The firmware declares both `AUDIO_DEVICE_OUT_USB_DEVICE` and
`AUDIO_DEVICE_OUT_USB_HEADSET`, so route-specific USB presets are technically supported.

However, the transmitter was not present on the USB bus during this probe. AudioService history
shows a device named `USB-Audio - USB Composite Device` connecting as USB audio card 2 and then
becoming unavailable:

- 24 July: connected, then disconnected approximately 45 seconds later
- 25 July: connected, then disconnected approximately 4 seconds later

At probe time, ALSA exposed only the internal HiSilicon audio card and Android routed music to
the built-in speaker. The external dongle needs to be reconnected or powered before its live
route fingerprint and EQ behavior can be validated.

## Installed comparison applications

Two equalizer applications are installed:

- Poweramp Equalizer, package `com.maxmpz.equalizer`, build `1021`
- Equalizer, package `com.jazibkhan.equalizer`, version `6.4.0`

Poweramp already has `MODIFY_AUDIO_SETTINGS`, `FOREGROUND_SERVICE`, `RECEIVE_BOOT_COMPLETED`, and
the ADB-granted `DUMP` permission. Neither package was running during the inspection, and
AudioFlinger initially showed no active effect chain.

## Architecture consequences

- Use a native Android Views TV UI; do not keep the TV Activity alive.
- Keep one small foreground service holding the session-0 Equalizer instance.
- Set a settled-memory target below 15 MB because the entire TV has only about 475 MB RAM.
- Use the five probed bands and ±15 dB range rather than assuming capabilities.
- Clamp user boosts or apply preamp compensation to reduce clipping risk.
- Store presets against output fingerprints, especially the USB card/device identity.
- Treat compressed/direct playback as unsupported until an audible test proves otherwise.
- Avoid FFT visualizers, Compose on TV, large networking frameworks and continuous polling.

## Next validation

Run a controlled cut-only A/B test while the user listens:

1. Play familiar dialogue or music.
2. Keep the original five levels in memory.
3. Enable the global EQ.
4. Apply a clearly audible but safe cut-only curve for several seconds.
5. Alternate effect bypass and enabled state.
6. Restore the original levels and release the effect.
7. Repeat for each important app, TV speakers, and the reconnected USB transmitter.

Cut-only test curves avoid the clipping risk of large positive boosts.
