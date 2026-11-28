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

The primary target is the Living Room TV: an Allwinner UHD Streaming STB (`athena-p7`),
Android 12/API 31, connected over HDMI. TV and phone require API 31 or newer; older Android
versions are no longer supported. The shared protocol remains a plain JVM library with an
API 31 Android-consumer contract. Capabilities are queried at runtime.

## Features

- Five-band live equalizer controls
- Runtime capability and gain-range discovery
- Route-specific preset recall, including edits saved to the active output
- Truthful status: TV and output names plus engine health, so a phone cannot show
  working sliders while the effect is gone
- Live state push to every connected phone, on each phone's own revision
- DNS-SD discovery with a stable TV identity, so the phone follows an IP change
- Manual IP fallback
- Up to eight named TV-owned presets, with Save/Delete controls on capable TVs
- Short-lived pairing code, five-attempt lockout, Keystore-backed tokens, and Forget all phones
- Read-only TV sound status with a D-pad-friendly pairing action
- Make cut-only curve shifting and warnings above +6 dB
- Bounded framed protocol with HMAC authentication and revisions
- Foreground TV service that survives the activity closing and restores state on restart
- Dependency-free interactive web concept at the repository root

## Preview the concept

Open [`index.html`](index.html) directly, or serve the repository locally:

```powershell
npx serve .
```

The mockup demonstrates live tuning, route switching, preset recall, first-time pairing, TV
status, and the phone navigation model. It has no build step or runtime dependencies.

## Download

Prebuilt APKs are attached to [Releases](../../releases). Each release includes both the TV app and
the phone remote, with checksums.

```bash
adb install -r tonewire-tv-<version>.apk
adb install -r tonewire-phone-<version>.apk
```

Always upgrade in place with `-r`. Uninstalling the TV app erases its paired-phone credentials and
saved presets.

See [`docs/RELEASING.md`](docs/RELEASING.md) for how releases are built and signed.

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

Historical validation on the former CloudWalker device covered its built-in-speaker PCM path: a phone emulator paired with
the TV over the production LAN protocol, changed the equalizer during playback, and restored the
original state.

The supported configuration is a single HDMI output. No multi-output priority ladder is planned.
Compressed/direct passthrough can bypass the effect and remains unverified on the primary device. Evidence and limitations
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

## Approved development scope

Implement M1–M6, then S1–S5 from the feature proposal. S1 is limited to deterministic, non-crashing
behavior when another output appears; HDMI is the supported route. Nice-to-haves N1–N4 and the
rejected list are out of scope. M1 covers genuine-entry-point foreground startup, bounded EQ-open
retries, enable-state persistence and a live status notification. See
[the development plan](docs/DEVELOPMENT_PLAN.md) for gates and guarded physical testing.
