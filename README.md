# Tonewire

Local-first Android phone remote and Android TV equalizer service for real-time, session-0 control.
The physical built-in-speaker PCM path has been validated end to end: the phone emulator paired to
the TV over the production LAN protocol, changed the NXP Equalizer during Spotify playback, and
restored the original state.

## View the mockup

Open `index.html` directly in a browser, or serve the folder locally:

```powershell
npx serve .
```

The mockup includes:

- live five-band controls with a synchronized TV state
- output switching between a USB Bluetooth dongle and built-in TV speakers
- per-output preset recall
- preset, output, and TV-service phone screens
- TV status and first-time pairing screens

The implementation is dependency-free and uses no build step.

## Android app

The Android workspace is in [`android/`](android/), with Java/Android Views modules for the TV,
phone, and shared authenticated local protocol.

Build and verify from `android/`:

```powershell
.\gradlew.bat verify build
```

Start the development phone AVD, then install both debug APKs on explicit targets:

```powershell
.\tools\start-medium-phone-emulator.ps1
.\tools\install-debug-apks.ps1 -PhoneSerial emulator-5554 -TvSerial 192.0.2.42:5555
```

Tonewire uses stable local port `49321` for phone control; `5555` above is only the trusted-LAN ADB
endpoint. Detailed build, safety, and evidence workflows are in
[`android/docs/BUILDING.md`](android/docs/BUILDING.md) and
[`android/docs/END_TO_END_VERIFICATION.md`](android/docs/END_TO_END_VERIFICATION.md).

## Validation status

The built-in speaker PCM path passed physical end-to-end validation. See
[`android/docs/PHYSICAL_TEST_REPORT.md`](android/docs/PHYSICAL_TEST_REPORT.md) for evidence,
latency, memory, effect-chain, restoration, and limitations. USB validation remains pending the
USB Bluetooth transmitter being reconnected; compressed/direct passthrough remains unverified.

## Research

See [`docs/feasibility.md`](docs/feasibility.md) for the technical constraints, architecture,
existing-product research, performance targets, and staged delivery plan.
