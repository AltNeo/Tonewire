# Tonewire technical feasibility brief

Research date: 25 July 2026

Actual-TV capability testing is documented in
[`tv_capability_report.md`](tv_capability_report.md). The global Equalizer path has now been
successfully instantiated on the target TV.

## Executive decision

This product is buildable. The implemented service has now passed an audible, real-time physical
test on the built-in speaker PCM path: it attached Android's built-in `Equalizer` to global audio
session `0`, accepted phone commands over the LAN, and restored the original state. Remaining
audible validation is USB after the transmitter is reconnected and every important direct or
passthrough playback path.

The recommended product is two Android apps:

1. A very small Android TV companion that owns the audio effect, detects output routes, keeps a
   foreground service alive, and accepts authenticated commands on the local network.
2. An Android phone remote that discovers the TV, pairs once, edits presets, and streams band
   changes over one persistent authenticated newline-framed TCP connection.

Wi-Fi should be the production transport. ADB should be limited to sideloading, diagnostics,
capability probing, and—if required—granting a one-time elevated permission. ADB-over-network is
not an appropriate daily control protocol because enabling and securing it is brittle and exposes
far more of the TV than this app needs.

## What Android 9 can and cannot do

### The lightweight route

Android exposes `android.media.audiofx.Equalizer` from API level 9. It supports immediate
`setBandLevel()` calls, reports the available number of bands and level range at runtime, and can
control an audio session. That makes real-time tuning entirely realistic: while the slider moves,
the phone sends a small band update and the TV calls `setBandLevel()` without restarting playback.

The important warning is in Android's own API documentation: attaching an Equalizer to audio
session `0` (the global output mix) is deprecated. Deprecated does not mean removed on Android 9,
but vendor implementations vary. Some TVs permit it, some apply it only to some players, and some
ignore or replace third-party effects.

Source: [Android Equalizer API](https://developer.android.com/reference/android/media/audiofx/Equalizer)

### Audio that will bypass the EQ

A normal app-level EQ operates on decoded PCM audio that travels through the Android mixer. It
cannot modify a compressed Dolby/DTS bitstream sent in passthrough mode, because that stream is
not decoded into samples the Equalizer can process. Hardware-offloaded playback can also bypass
the software effect path depending on the TV and player.

This is the biggest product risk. The first engineering milestone must test:

- YouTube and YouTube Music
- Netflix or the main streaming app used on the TV
- local playback in VLC/Kodi, with passthrough both off and on
- TV speakers
- the USB Bluetooth transmitter
- standby, reboot, and route reconnection

AOSP documents audio effects as vendor-provided libraries configured in the device audio stack,
which is why behavior is device-specific. Android 11 added better device-specific effect
attachment for manufacturers, but that improvement is not available to a regular Android 9 app.

Source: [AOSP audio effects](https://source.android.com/docs/core/audio/audio-effects)

### Why audio capture is not the Android 9 fallback

The custom-DSP technique used by projects such as RootlessJamesDSP captures and reprocesses other
apps' audio. Its own documentation warns about extra latency, applications that block capture,
and incompatibilities. The published implementation is tested on Android 10–13, and the required
playback-capture platform capability did not exist on Android 9.

For this TV, start with the hardware-backed Android `Equalizer`. Only consider a rooted/system-app
DSP if the capability probe fails and the owner accepts device modification and higher CPU cost.

Source: [RootlessJamesDSP project and limitations](https://github.com/timschneeb/RootlessJamesDSP)

## Existing products and the gap

There are mature Android phone equalizers that prove live editing and per-output presets are useful:

- Poweramp Equalizer offers live graphic/parametric EQ and per-device or per-output preset
  assignment, but only for supported players and Android audio-effect paths.
- Wavelet uses normal audio-session notifications and offers enhanced session detection through a
  one-time ADB-granted `DUMP` permission.
- RootlessJamesDSP demonstrates a deeper custom-DSP route, with higher latency and capture
  restrictions.
- New Android TV products such as SoundWave TV and TV DSP Center advertise system-wide TV EQ, but
  their public material does not show a separate, purpose-built phone EQ remote.

The differentiated product is therefore not "another EQ." It is the two-device experience:
headless TV processing, live sofa-side tuning, automatic route profiles, local-only pairing, and
clear diagnostics when the TV's audio path bypasses processing.

Sources:

- [Poweramp official product page](https://powerampapp.com/)
- [Wavelet session detection documentation](https://pittvandewitt.github.io/Wavelet/Settings/)
- [SoundWave TV](https://nexech.com/products/soundwave-tv/)

## Recommended architecture

```text
Android phone app
  UI + preset editor
  NSD discovery (_tonewire._tcp)
  authenticated framed TCP client
             |
             | same LAN, live band updates
             v
Android TV foreground service
  pairing/authentication
  tiny framed TCP server
  route observer
  preset repository
  AudioEffect adapter
             |
             v
Android audio mixer -> active PCM output
```

### TV app

- Java/Android Views, minimum SDK 28 for the Android 9 target and target SDK 34.
- A headless foreground `Service` owns the Equalizer object for its entire lifetime.
- A `BOOT_COMPLETED` receiver starts the service after reboot.
- A persistent, low-priority notification makes service lifetime honest and Android-compliant.
- `AudioManager.registerAudioDeviceCallback()` detects devices added or removed.
- Output identity is stored as a stable fingerprint using type, product name, and address where
  available. A generic USB Bluetooth transmitter is likely to appear as `TYPE_USB_DEVICE` or
  `TYPE_USB_HEADSET`, not as a Bluetooth device.
- Android NSD advertises `_tonewire._tcp` over mDNS, so the phone does not need an IP address.
- A small newline-framed TCP server listens on stable port `49321` and accepts only paired
  clients; each post-pairing connection is authenticated with a token-derived HMAC. NSD
  advertises that same port, and manual-IP fallback uses it when no port is entered.
- DataStore or SharedPreferences persists preset data only after a short debounce, never for each
  slider event.
- The TV Activity is not part of the hot path. It contains pairing, current status, diagnostics,
  and a recovery switch only.

Android's device APIs explicitly recommend querying again when outputs change and using
`AudioDeviceCallback` for these changes.

Sources:

- [AudioManager and output device changes](https://developer.android.com/reference/android/media/AudioManager.html)
- [AudioDeviceInfo types](https://developer.android.com/reference/android/media/AudioDeviceInfo.html)
- [Android Network Service Discovery](https://developer.android.com/develop/connectivity/wifi/use-nsd)

### Phone app

- Java/Android Views keeps the phone implementation aligned with the lightweight delivered stack.
- Discover TVs with NSD and provide manual IP entry as a fallback for routers that suppress mDNS.
- Pair using a six-digit, short-lived code displayed by the TV. Store the resulting random token
  in Android Keystore-backed storage.
- Maintain one persistent authenticated TCP connection while the app is visible. Reconnect with
  bounded exponential backoff.
- Coalesce slider input to at most one update per display frame. Send only the changed band.
- Show acknowledgement latency and a conspicuous "audio bypass suspected" diagnostic if values are
  accepted but a capability/test signal indicates no processed path.

### Suggested live protocol

```json
{"type":"band.set","revision":1042,"band":2,"levelMb":-100}
{"type":"band.ack","revision":1042,"appliedAtMs":18}
{"type":"route.changed","routeId":"usb:2812","presetId":"cinema-warmth"}
```

Android Equalizer levels use millibels. The phone should display decibels while the protocol uses
integers to avoid floating-point ambiguity.

## Autostart on Android 9

Android 9 requires the `FOREGROUND_SERVICE` permission for apps that target API 28 or higher.
`BOOT_COMPLETED` can start the service, after which the service must call `startForeground()`
promptly. The user may also need to exempt the app from a vendor-specific battery optimizer.

The design must not hold a wake lock continuously. The effect and socket service can sleep while
idle and wake on network/device callbacks. The TV UI process should not perform animation or
visualization in the background.

Sources:

- [Android 9 foreground-service change](https://developer.android.com/about/versions/pie/android-9.0-changes-28)
- [Background optimization](https://developer.android.com/topic/performance/background-optimization)

## Performance budget

Initial measurable targets for the TV, revised for its approximately 486 MB of physical RAM:

| Area | Target |
| --- | ---: |
| Idle CPU, screen UI closed | under 0.2% average |
| Resident memory after settling | under 15 MB |
| Network traffic when idle | zero except discovery/keepalive |
| Slider-to-effect call on LAN | p95 under 50 ms |
| Route change to preset recall | under 500 ms |
| Storage writes during a drag | zero |
| Cold boot service ready | under 5 seconds after boot delivery |

Use Android Views for the tiny TV Activity and no always-running Compose runtime, visualizer, FFT,
analytics SDK, ad SDK, cloud sync, or periodic polling. Prefer platform APIs and a compact protocol
over a large server framework.

## Delivery plan

### Phase 0 — capability probe (2–4 days)

Build a diagnostic APK, not the final product. It should enumerate available audio effects, create
an Equalizer on session 0, report bands and level ranges, sweep one band while audio plays, list
routes, and export a plain-text diagnostic report through ADB.

Exit criterion: an audible, repeatable real-time change on the required apps and both outputs.

### Phase 1 — TV engine proof (4–7 days)

Add the foreground service, boot receiver, preset persistence, route callback, bypass toggle, and
long-duration stability test. No polished UI yet.

### Phase 2 — local remote (1–2 weeks)

Add discovery, pairing, authenticated framed TCP messages, live sliders, acknowledgements, reconnect,
and route/preset state synchronization.

### Phase 3 — product UI and hardening (1–2 weeks)

Implement the phone and TV screens represented by the interactive mockup, accessibility, D-pad
focus on TV, recovery flows, battery testing, network loss, reboot, standby, USB reconnect, and
firmware-specific workarounds.

## Go/no-go test before committing to the full build

The project is a **go** if session-0 Equalizer produces an audible live change for the apps and
outputs you care about.

It is a **conditional go** if it works for PCM but not passthrough; the product must clearly state
that passthrough must be disabled.

It is a **no-go without root/OEM access** if the TV firmware blocks the global mix effect or all
important playback stays on a direct/offloaded path. In that case, alternatives are an external
DSP in the audio chain, a rooted/system build, or control of an EQ exposed by the TV manufacturer.
