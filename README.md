# Tonewire

[![Android build](https://github.com/AltNeo/Tonewire/actions/workflows/android.yml/badge.svg)](https://github.com/AltNeo/Tonewire/actions/workflows/android.yml)

Tonewire is a local-first Android phone remote and Android TV equalizer service. It provides
real-time control of a TV's session-0 hardware equalizer without requiring a cloud service or a
large TV-side user interface.

The project contains two deliverables:

- An Android TV foreground service that owns the equalizer, watches audio routes, recalls presets,
  and accepts authenticated local-network commands.
- An Android phone remote that discovers and pairs with the TV, displays advertised equalizer
  capabilities, and streams live band changes.

The verified target is a CloudWalker Android 9/API 28 TV with a five-band NXP equalizer. The
architecture queries capabilities at runtime rather than assuming that every device has five
bands.

## Features

- Five-band live equalizer controls
- Runtime capability and gain-range discovery
- Built-in speaker and USB route support
- Route-specific preset recall
- DNS-SD discovery and manual IP fallback
- Short-lived pairing code and Keystore-backed token storage
- Bounded framed protocol with HMAC authentication and revisions
- Boot-started TV service with clean state restoration
- Dependency-free interactive web concept at the repository root

## Preview the concept

Open [`index.html`](index.html) directly, or serve the repository locally:

```powershell
npx serve .
```

The mockup demonstrates live tuning, route switching, preset recall, first-time pairing, TV
status, and the phone navigation model. It has no build step or runtime dependencies.

## Build the Android apps

Requirements:

- JDK 17
- Android SDK Platform 34
- Android Build-Tools 34.0.0 or newer

From [`android/`](android/):

```powershell
.\gradlew.bat verify build
```

This runs JVM tests, Android lint, and debug/minified release builds for the protocol, TV, and
phone modules. Debug APKs are written to:

- `android/tv/build/outputs/apk/debug/tv-debug.apk`
- `android/phone/build/outputs/apk/debug/phone-debug.apk`

See [`android/docs/BUILDING.md`](android/docs/BUILDING.md) for toolchain details and
[`android/docs/END_TO_END_VERIFICATION.md`](android/docs/END_TO_END_VERIFICATION.md) for the
attended physical-device workflow.

## Physical validation

The built-in-speaker PCM path passed physical end-to-end validation: a phone emulator paired with
the TV over the production LAN protocol, changed the equalizer during playback, and restored the
original state.

USB validation remains pending reconnection of the Bluetooth transmitter. Compressed and direct
passthrough paths may bypass the software effect and remain unverified. Evidence and limitations
are documented in [`android/docs/PHYSICAL_TEST_REPORT.md`](android/docs/PHYSICAL_TEST_REPORT.md).

## Security scope

Tonewire v0.1 assumes a trusted home LAN and must not be exposed directly to the public internet.
The pairing flow uses a short-lived code, and post-pairing credentials are stored using Android
Keystore-backed encryption. Known network-hardening limitations are documented in
[`SECURITY.md`](SECURITY.md).

## Repository layout

```text
android/
  phone/       Android phone remote
  protocol/    Shared protocol models and codecs
  tv/          Android TV service and status app
  tools/       Build, installation, evidence, and E2E scripts
diagnostics/   Hardware capability probes
docs/          Feasibility research and device reports
index.html     Interactive product concept
```

## License

Released under the [MIT License](LICENSE).
