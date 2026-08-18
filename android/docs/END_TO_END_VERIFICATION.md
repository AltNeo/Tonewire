# End-to-end and performance verification

These tools intentionally distinguish a phone emulator from the physical/network TV. Runtime
control traffic must travel through the production LAN protocol; ADB is used only to install,
launch UI, and collect evidence.

## Implemented product surface

The phone now supports NSD discovery and manual `IPv4:port` connection, six-digit pairing, token
storage, a live/bypassed control, and dynamically populated `SeekBar` controls. The TV exposes a
random TCP port through DNS-SD and its status screen, authenticates a paired phone, and dispatches
`state.get`, `eq.enable`, and `eq.band.set` to the global Equalizer.

The current Views are created programmatically and do not have resource IDs. The harness therefore
uses the intentionally stable visible controls: connection content description, `Connect`, `Pair`,
`LIVE`, `EQ ON`/`EQ BYPASSED`, and each slider's frequency content description. This must be
revalidated after UI copy changes. Pairing uses the real LAN connection; ADB only taps/types into
the phone UI and collects evidence.

## Safe workflow

1. From `android/`, build the APKs. Start the named AVD without wiping it:

   ```powershell
   .\tools\start-medium-phone-emulator.ps1
   ```

2. Identify targets before installing. The emulator serial must start with `emulator-`; specify
   the TV's explicitly, for example `192.0.2.42:5555`.

   ```powershell
   .\tools\install-debug-apks.ps1 -PhoneSerial emulator-5554 -TvSerial 192.0.2.42:5555
   ```

3. Capture a baseline before pairing or playback. This takes screenshots/UI XML, package data,
   PSS, CPU samples, network counters, full logcat and AudioFlinger/audio-service dumps. It does
   not send UI input or change EQ.

   ```powershell
   .\tools\collect-evidence.ps1 -PhoneSerial emulator-5554 -TvSerial 192.0.2.42:5555
   ```

4. Copy `tools/control-config.template.json` outside version control and set `manualTvAddress` to
   the TV IP and **Tonewire port displayed by the TV status screen** (not ADB port 5555). Run the
   attended harness. It opens manual-IP connection, prompts for the short-lived TV code without
   writing it to disk, snapshots the phone-visible EQ state, applies a cut-only curve, pauses for
   listening, then restores every original slider and bypass state in `finally`.

   ```powershell
   .\tools\run-production-e2e.ps1 -PhoneSerial emulator-5554 -TvSerial 192.0.2.42:5555 `
       -ControlConfig .\local-control-config.json -AllowAudioMutation
   ```

The harness accepts only zero-or-negative test-curve millibels. Do not interrupt it after the curve
is applied; press Enter after the listening check so its restore block can run. Its per-run evidence
directory includes `original-phone-state.json`, timestamps for applying the curve/restoring it, and
an explicit `RESTORE_FAILED.txt` if automatic recovery could not complete.

## Evidence interpretation

`collect-evidence.ps1` captures AudioFlinger, audio-service routing, PSS, CPU samples, package
state, logcat, UI XML/screenshots, and network counters without UI input. Review
`tv-audioflinger.txt` for a session-0 Equalizer chain before and during the test, `tv-meminfo.txt`
for PSS, `tv-cpu.txt` for idle/load CPU, and network dumps for unexpected traffic. A continuous PCM
playback path must remain active; document direct/passthrough results separately.

`latency.json` parses only the phone's monotonic `TW_SENT`/`TW_ACK` markers. It pairs the sent
revision with the acknowledgement revision plus one and the same command type, and uses the
acknowledgement's `elapsed_ms` value. This avoids comparing device wall clocks. The result is a
conservative command-to-apply upper bound: the TV emits `TW_APPLIED` before its acknowledgement,
but the phone-side RTT also includes return transport and handling. Inspect TV `TW_APPLIED` records
by revision/type to prove application occurred before the ACK; do not subtract cross-device
timestamps.

## Checklist

- [ ] Confirm target identity: emulator phone and explicit CloudWalker network TV.
- [ ] Build/install debug APKs and retain install output.
- [ ] Capture baseline evidence before launching the service.
- [ ] Note the Tonewire port from the TV status screen and put it in local config.
- [ ] Pair with the short-lived code; confirm no ADB port is used for commands.
- [ ] Confirm `original-phone-state.json` is written before audio changes.
- [ ] Verify unauthenticated and stale-revision requests are rejected.
- [ ] Use attended, cut-only UI slider steps; collect revision acknowledgements.
- [ ] Verify session-0 AudioFlinger chain, playback continuity, final state, p50/p95.
- [ ] Let the harness restore the exact saved state; verify its restore timestamp, then collect final evidence.
- [ ] Repeat for built-in speakers; defer USB until the device is enumerated.
- [ ] Disable network ADB on the TV when development ends.

Run the parser self-test with:

```powershell
.\tools\tests\Tonewire.Tools.Tests.ps1
```
