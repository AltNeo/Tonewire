# Tonewire feature proposal

Baseline: 26 November 2026, branch `autonomous-dev` at `e8487a6`. Primary target is an Allwinner
`UHD Streaming STB` (`athena-p7`) on Android 12/API 31, reached over the LAN with ADB during
development. API 28 stays as a compatibility floor. This document picks features. It does not
design how to build them; [`VERIFICATION.md`](VERIFICATION.md) records what was verified and what
remains unverified.

The test for every feature: *a person on the couch wants to change the bass without hunting
through a TV menu.* Anything that does not serve that, or the trust that the change actually
happened, is out of scope.

### What was observed on the real TV (read-only, 26 Nov 2026)

These checks only read state. They changed no audio, launched nothing and sent no commands.

| Observation | Source |
| --- | --- |
| Android 12, model `UHD Streaming STB`, `com.tonewire.tv` 0.1.0 installed, `stopped=false` | `getprop`, `dumpsys package` |
| **Tonewire is not running**: no process and no service, with the TV up for 46 h | `pidof`, `dumpsys activity services`, `uptime` |
| Last recorded process exits: 2026-09-12 "kill background", 2026-08-25 app crash. None since the last boot | `dumpsys activity exit-info` |
| Exactly one framework output device: `AUDIO_DEVICE_OUT_SPEAKER` (the vendor HAL routes it to HDMI/SPDIF/codec) | `dumpsys media.audio_policy` |
| No direct/compressed output profiles at the policy level. HAL passthrough is still possible | `dumpsys media.audio_policy` |
| Bluetooth is on, so A2DP headphones or a soundbar can add a second output | `dumpsys bluetooth_manager` |
| NXP Equalizer library loaded, 0 effect chains (consistent with Tonewire not running) | brief / AudioFlinger |

**Standing constraint.** The development plan forbids changing band levels or enable state on the
physical TV unless the user explicitly lifts that rule. Below, "verify on TV" separates
**read-only** checks, which are allowed now, from **audible** checks, which need that permission. A
feature counts as fully verified only once its audible check has passed.

## Current state

v0.1.0 is a small, honest design that works, so it is worth protecting:

- **Correct engine ownership.** One foreground service owns one session-0 `Equalizer` on a
  dedicated audio-priority thread. It discovers bands, centres and gain range at runtime
  (`EqualizerEngine.discover`) and clamps every write.
- **The storage write is off the live path.** Band writes are applied immediately. Persistence is
  debounced by 750 ms (`TvStateRepository`), so a drag never writes to storage on every step.
- **Proven latency.** On the old CloudWalker TV, phone-to-apply p50 was 18 ms and p95 70 ms over the
  production protocol.
- **Light footprint.** The settled service was about 8.5 MB PSS, idle CPU 0.0%, with no wake locks.
  The minified APKs are 40 KB (TV) and 21 KB (phone), with zero runtime dependencies.
- **Sound pairing and authentication.** A six-digit code expires after 5 minutes. The token is a
  256-bit random value stored with Keystore AES-GCM on both sides. Each connection authenticates
  with an HMAC challenge, so the token never crosses the wire after pairing.
- **A forward-tolerant wire format.** Every field is a named key, so either side can add fields
  without breaking the other. The TV answers an unknown command with a non-fatal
  `unsupported_command` error (`EngineCommandDispatcher.java:44`), and the phone silently ignores
  message types it does not know. Most new features can therefore ship without a version bump.
- **Robust phone reconnects.** Generation-guarded sockets, capped backoff, and a 10 s heartbeat that
  doubles as a resync.

## Gaps and friction

Each item is a problem a user would actually hit today, with evidence.

1. **After a reboot, the TV may not be running Tonewire at all.** The STB has been up 46 h and the
   service is absent, with no process exit recorded since September. So either boot autostart did
   not fire on this firmware or the process was never created. Separately, if `Equalizer`
   construction fails once, the service calls `stopSelf()` (`TvEqualizerService.java:104`) and
   never retries. From the couch this looks like "the remote can't find the TV".
2. **EQ on/off is not remembered.** `setEnabled` (`TvEqualizerService.java:130`) changes the engine
   but never persists the value. Only the curve is saved. After a restart the curve comes back but
   the enable state is whatever the effect reports. On the old TV the effect started bypassed
   (`PHYSICAL_TEST_REPORT.md`).
3. **RESET only flattens one band.** `reset()` calls `queueBand` once per band
   (`PhoneMainActivity.java:128`), but `queueBand` holds a single pending band and overwrites it
   (`:126`). Only the last band (14 kHz) reaches the TV. The phone shows a flat curve until the next
   heartbeat snaps the bass back.
4. **Tweaks are lost when the output changes.** Band edits save only the global "Default" curve
   (`persistCurrent`, `TvEqualizerService.java:136`). Route curves are written only by
   `eq.preset.apply`. Example: apply Night watch on headphones, raise the bass, disconnect, then
   reconnect. The route recall restores the un-tweaked Night watch. The mockup's promise that
   "routes remember" holds only for the three built-in presets.
5. **With two outputs connected, presets stop working.** `RouteSnapshot` picks a route only when
   exactly one output exists (`RouteSnapshot.java:14`). Pairing Bluetooth headphones to this STB
   (Bluetooth is on) makes it two, so `eq.preset.apply` returns `route_unavailable` and route
   recall stops.
6. **The phone does not learn about changes it did not make.** The TV only ever replies. It never
   sends `route.changed`, even though the type exists. If the output changes, or a second phone
   edits, the first phone stays wrong for up to 10 s (heartbeat `state.get`) and can send edits
   based on a stale curve. Each connection also has its own `RevisionTracker`
   (`TvNetworkServer.java:118`), so two phones never see each other's revisions.
7. **The phone gets stuck if the TV's IP changes.** Once an endpoint is saved, the phone ignores
   every discovery result (`endpointInUse`, `PhoneMainActivity.java:115`). After a DHCP change it
   reconnects to the dead address forever, until the user types the new IP by hand.
8. **The UI shows raw identifiers and hides failures.** The phone reads `tv_name` and `route_name`
   (`TvConnectionClient.java:178-179`), but the TV never sends them. Users see "TV",
   `speaker`/`unknown`, and preset names like `clear_dialogue`. If another app takes the effect
   (`onControlStatusChange`, `EqualizerEngine.java:36`), the TV records it but never reports it, so
   sliders keep "working" with no effect. The notification still says "Service scaffold ready for
   engine setup" (`strings.xml:4`).
9. **The TV screen only knows about pairing.** `TvStatusActivity` shows a code and a client count.
   It does not show whether the EQ is on, which output or preset is active, or whether the engine is
   healthy. It also offers no way to forget paired phones, although the mockup's TV status view
   shows all of these.
10. **Known pairing weaknesses** (`SECURITY.md`): no attempt limit (one million codes within a
    5-minute window is brute-forceable on a LAN), a `"default"` peer-ID fallback
    (`TvNetworkServer.java:131,153`), and the revision advancing before the command succeeds
    (`:174`).
11. **The preset list is hard-coded in the phone** (`PhoneMainActivity.java:101`), and there is no
    way to save a curve the user likes. The mockup has a "New preset" item and per-output
    assignment.

## Proposed features

### Must have

High value, low risk, and verifiable on the API 31 STB.

**M1. Survives reboot with the sound intact.**
Make the API 31 box actually run Tonewire after a power cycle and come back the way the user left
it. The work: start the service only from the genuine entry points (boot and user launch), and keep
`Application.onCreate` from also starting it. Handle `ForegroundServiceStartNotAllowedException`
instead of crashing. Retry `Equalizer` construction with a short, bounded backoff instead of calling
`stopSelf()`. Persist the enable flag next to the curve. Replace the placeholder notification text
with the real state ("EQ on · Night watch").
*Story:* "When I turn the TV on, my sound is how I left it, without opening anything."
*Effort:* M.
*Verify on TV:* read-only: after a reboot, check `pidof com.tonewire.tv` and
`dumpsys activity services com.tonewire.tv`, confirm `media.audio_flinger` shows one session-0 NXP
chain, check that the logcat time from boot to ready is under the target, and confirm
`dumpsys notification` shows the new text. Audible (needs permission): enable plus a cut-only curve,
reboot, then confirm the effect is enabled with the same levels.

**M2. RESET really flattens every band.**
Send every changed band instead of keeping only the last pending one. Coalescing should be per
band, not global. That also fixes a fast two-finger or two-slider drag losing a band. This is a
phone-only change with no protocol impact.
*Story:* "When I tap RESET, all of it goes flat, not just the treble."
*Effort:* S.
*Verify:* a JVM test on the coalescer. On the TV, after RESET, the logcat `TW_APPLIED` count equals
the band count and the following `state.snapshot` shows all zeros. That second step is audible
because it writes levels, so it needs permission. Until then, verify it on a disposable target or
the fake engine.

**M3. Edits stick to the active output.**
When the user moves a band, save the result as the curve for the current route fingerprint, not
only as the global default. That makes reconnect-recall restore what the user actually heard last
on that output. The default curve is still used for outputs that have never been seen.
*Story:* "My headphone tweak is still there next time I connect the headphones."
*Effort:* S.
*Verify:* JVM tests with `TvStateRepository` and `PresetSelector`. On the TV, read-only: inspect
`shared_prefs/tonewire_state.xml` through `run-as` on the debug build to confirm the
`route.<fingerprint>` key updates after a drag. The full route-swap loop needs a second output (see
S1) and audio permission.

**M4. Truthful status in every snapshot.**
Add the fields the phone already looks for (`tv_name`, `route_name`) plus a display name for the
preset. Add `engine` with one of `ok`, `no_control`, `unavailable` or `route_ambiguous`. The phone
replaces "Changes apply as you move" with a clear warning when the engine is not `ok`, and the TV
status screen shows the same thing. This is the cheapest guard against the product's biggest risk:
sliders that move while nothing changes in the sound. The TV name comes from
`Settings.Global.DEVICE_NAME`, falling back to `Build.MODEL`, and also becomes the DNS-SD service
name instead of the hard-coded "Tonewire TV".
*Story:* "If the EQ isn't actually doing anything, my phone tells me, in words I understand."
*Effort:* S.
*Verify on TV:* read-only: `settings get global device_name`, then check the phone shows "Living
Room TV" and a readable route name. For `no_control`, use a disposable target. On the TV this needs
another effect owner, which counts as an audio mutation.

**M5. Live state push to connected phones.**
After any state change (a command from any phone, a route change, or a boot restore), the TV sends a
`state.snapshot` to every authenticated connection. Each push carries **that connection's own
revision** (see Protocol implications). The phone already handles unsolicited snapshots, so the
existing app benefits without an update.
*Story:* "When I plug in headphones, my phone shows the headphone curve straight away."
*Effort:* M. It needs a per-connection writer lock and a connection registry in `TvNetworkServer`.
*Verify on TV:* with two phone clients connected (emulator plus a second emulator or `nc` client
under test), a `state.get` from one makes the other receive a snapshot within about 100 ms (log
timestamps). Route-triggered pushes need a real output change, which counts as an audio mutation.

**M6. The phone finds the TV again after an IP change.**
Advertise a stable random `tv_id` in the DNS-SD TXT record and in the hello message. The phone
stores `tv_id` with the endpoint. When a discovery result has the same `tv_id` but a different host,
the phone adopts it and reconnects with its existing token instead of ignoring it. Manual IP entry
stays.
*Story:* "After the router restarts, the remote still works."
*Effort:* S.
*Verify on TV:* read-only: confirm the TXT record contains `tv_id` with `avahi-browse -rt
_tonewire._tcp` from <DEV-HOST>. **Speculative for the end-to-end check:** the Windows phone emulator's
NAT probably blocks multicast, so adopting the moved TV needs a physical phone. It can also be
covered by a JVM test of the rediscovery decision.

### Should have

Clear value, moderate effort.

**S1. A sensible route choice when several outputs are connected.**
When more than one output is present, pick one deterministically instead of returning null:
Bluetooth A2DP, then USB, then HDMI/ARC, then speaker, because that matches stock audio policy for
media. Expose the choice, and the fact that it was inferred, in `route_name` / `engine`. API 31 has
no public "current media route" API (`getAudioDevicesForAttributes` is API 33), so on API 33 and
later use the real answer and fall back to the priority list below that.
*Story:* "With headphones paired, my headphone preset still loads."
*Effort:* M.
*Verify on TV:* read-only: pair Bluetooth headphones, check `dumpsys media.audio_policy` for two
outputs, and check that the logcat shows the selected fingerprint. **Speculative:** whether the
priority list matches what this Allwinner HAL actually plays through can only be confirmed by ear,
which needs permission.

**S2. Save your own presets on the TV.**
Add "Save current as…" on the phone and delete for presets that are not built in. The TV stores up
to 8 named curves. The snapshot carries the preset list (ID and display name), so the phone stops
hard-coding three buttons. Applying a preset still assigns it to the current output, as today.
*Story:* "I save 'Late movie' once and pick it with one tap."
*Effort:* M. It needs two new message types (see Protocol implications).
*Verify:* JVM codec and repository tests. On the TV, read-only: save and delete, then inspect
preferences through `run-as`. Applying a preset is audible and needs permission.

**S3. Close the pairing gaps.**
Lock pairing for 30 s after 5 wrong codes and rotate the code. Reject a missing `device_id` or
`peer_id` instead of falling back to `"default"`. Advance the revision only after the command
succeeds. Add "Forget all phones" on the TV screen, which clears `SecureTokenStore` and closes
connections. This is the minimum that makes the existing threat model honest, not new security
machinery.
*Story:* "Nobody else on the Wi-Fi can guess their way in, and I can kick old phones off."
*Effort:* S.
*Verify on TV:* read-only and safe, because these are network-only paths that never touch audio.
Script 6 bad `pair.request` frames with `nc` and expect `rate_limited`. Use "Forget all phones",
then expect the next connection to fail with `authentication_failed`.

**S4. The TV status screen shows sound state.**
Extend `TvStatusActivity`, a D-pad-friendly screen, to match the mockup's TV status view. It
shows: EQ on or bypassed, the active output and preset, engine health (from M4), and the connected
phone count. The pairing code moves to a "Pair a phone" button and stays visible when no phone has
been paired yet. The screen still never changes audio. The notification gets a tap-through
`PendingIntent` to it.
*Story:* "I can glance at the TV and see whether the EQ is on and which preset is loaded."
*Effort:* S.
*Verify on TV:* read-only: launch with `am start`, then `screencap`. D-pad focus can be checked with
`input keyevent`. Launching starts the service (M1), so if that applies stored settings, this
depends on M1's no-write start path or on permission.

**S5. Headroom guard.**
Add a "Make cut-only" button on the phone. It shifts the whole curve down so the highest band sits
at 0 dB, keeping the shape and removing boost-induced clipping on small TV speakers. The phone also
shows a small warning when any band is above +6 dB. This is pure phone-side arithmetic on top of
existing `eq.band.set` messages; no preamp or second effect is involved.
*Story:* "The bass boost stops crackling, and the sound keeps the same character."
*Effort:* S.
*Verify:* JVM test of the shift. The audible check (no distortion) needs permission and is
subjective.

### Nice to have

Optional, with diminishing returns.

**N1. Quick Settings tile on the phone.** Toggles EQ on/off, and long-press applies one chosen
favourite preset, without opening the app. It reuses `TvConnectionClient` with a short-lived
connection. *Story:* "Night mode from the pull-down shade." *Effort:* M. *Verify:* phone emulator
over manual IP. The TV receives `eq.enable`, which is audible and needs permission.

**N2. Response-curve preview.** Draw the interpolated curve above the sliders, as in the mockup's
"Live response" card, using plain `Canvas` with no library. *Story:* "I can see the shape I'm
making." *Effort:* S. *Verify:* phone screenshot only. Nothing on the TV.

**N3. TV-side fallback controls.** On the status screen, add a D-pad toggle for EQ on/bypass and a
preset cycler, for when the phone is flat or lost. There are no band sliders on the TV. *Story:*
"My phone is dead, but I can still turn Night watch off." *Effort:* S. *Verify:* `input keyevent`
plus a snapshot check. It is audible and needs permission.

**N4. Diagnostics export.** The phone's Diagnostics shows the last 20 command round-trip times
(already measured in `TvConnectionClient`), the engine status (M4) and the TV build, with a Share
button that exports plain text. *Story:* "When something sounds wrong I can send one text blob."
*Effort:* S. *Verify:* phone only, plus read-only TV snapshot fields.

### Rejected / do not build

- **Audio visualizer, FFT or spectrum display.** It needs `RECORD_AUDIO`, a Visualizer effect and
  constant CPU on a low-end set-top box, and it does not help change the bass.
- **Bass Boost, Virtualizer, Loudness Enhancer, Dynamics Processing or reverb controls.** Each one
  adds an effect chain, clipping risk and vendor-specific behaviour. The five-band EQ already covers
  "more or less bass".
- **Parametric EQ or extra bands through DynamicsProcessing.** Support for it on this vendor
  library is speculative, and its value over five bands is small for a couch remote.
- **Capture-and-reprocess DSP (JamesDSP-style).** It needs MediaProjection consent after every
  boot, adds latency, and apps can block it.
- **Per-app profiles.** These need Usage Access or Accessibility to detect the foreground app,
  which is invasive for little benefit. Route-based recall covers the real need.
- **Cloud sync, accounts or remote access off the LAN.** These contradict local-first, and the user
  is on the couch on the same Wi-Fi.
- **Web or iOS remote.** A browser cannot do DNS-SD or the raw HMAC socket, and supporting it means
  a second transport (HTTP/WebSocket) and a second client to maintain.
- **Multi-TV switching on the phone.** It is one household TV today. "Pair another TV" replacing the
  current one is enough until there is a second Tonewire TV.
- **TLS or per-message MACs.** The threat model is a trusted home LAN. S3 closes the cheap holes;
  transport crypto adds key management for marginal gain. Revisit only if the threat model changes.
- **Full EQ sliders on the TV.** That rebuilds the "hunting in a TV menu" experience Tonewire
  exists to avoid. N3's bypass and preset cycling is the ceiling.
- **Scheduling, such as automatic Night watch after 22:00.** It adds alarm or exact-timing
  machinery for something one tap already does.
- **Compose, Kotlin, DI or networking libraries.** These are excluded by project constraint, and
  the APK size and memory budget depend on staying without them.
- **ADB as a control channel.** It exposes the whole TV and is brittle. ADB stays limited to
  development and diagnostics.

## Protocol implications

**Do not bump the wire version.** `ProtocolCodec.decode` rejects any frame not prefixed `1`
(`ProtocolCodec.java:27`). A v2 frame would make every installed peer drop the connection with
`invalid_frame`. Every feature above fits v1 by adding fields and types. Advertise support by
adding `features` (a comma-separated list) to the TV `hello` and the DNS-SD TXT record, alongside
`protocol=1`.

| Feature | Wire change | Compatible with v0.1 peers? |
| --- | --- | --- |
| M1, M2, M3, S5, N2 | None | Yes |
| M4 | New `state.snapshot` fields: `tv_name`, `route_name`, `preset_name` (now a display name), `engine` | Yes. Unknown fields are ignored, and the v0.1 phone already reads `tv_name`/`route_name` |
| M5 | Unsolicited `state.snapshot` | Yes, **provided each push uses the receiving connection's own revision**. The phone calls `revisions.observe(max)`, so a push carrying another connection's (or a global) higher revision would push this phone's next expected revision ahead. Every command would then fail with `stale_revision` and resync, in a loop |
| M6 | New TXT key `tv_id` and new `hello` field `tv_id` | Yes |
| S1 | None beyond M4's fields | Yes |
| S2 | New types `eq.preset.save` (`name`, `levels_mb`) and `eq.preset.delete` (`preset_id`), both state-changing. New snapshot field `presets` (`id:name,…`, at most 8 entries, well under the 4096-byte field limit) | Partly. A v0.1 TV answers `unsupported_command`, so the new phone must hide Save unless `features` contains `presets` |
| S3 | New `pair.reject` reason `rate_limited`. Missing IDs now fail instead of falling back | Mostly. A v0.1 phone always sends `device_id`/`peer_id`, so stricter checks do not break it. Revision-on-success only changes behaviour after failed commands |
| N1, N3, N4 | None (they reuse `eq.enable` and `eq.preset.apply`) | Yes |

Update `ProtocolMessages`' factories and the `ProtocolCodecTest` fixtures alongside the new fields,
so the documented v1 contract matches what is actually on the wire. Today `stateSnapshot()` already
diverges from the dispatcher's hand-built map.

## Recommended sequence

**Milestone 0.2: "Trustworthy on the STB".** Ship Must have M1–M6, nothing else.

1. **M1 first**, because nothing else matters if the service is absent. Check it read-only on
   `<TV-IP>` right away: after a reboot, the process, service, effect chain and notification
   should all be present.
2. **M2 + M3**: small, phone- and TV-local fixes that make edits behave as users expect. They need
   only JVM tests plus a disposable target until audio permission is granted.
3. **M4 → M5 → M6**: honest status first, then make it live, then make the connection survive IP
   changes. All of these are additive v1 fields, and v0.1 phones keep working.

Exit criteria for 0.2:
- the read-only TV checks for M1, M4 and M6 pass;
- every audible check is either passed with permission or explicitly reported as deferred;
- `./gradlew verify build` is green;
- APK sizes and settled PSS stay close to v0.1.

**Then 0.3**: S3 (network-only, safely testable on the TV now), S4, S1, S2, S5. Nice-to-have items
only on request.

## Open questions for the user

1. **Which outputs do you actually use on this box:** HDMI to the TV only, Bluetooth headphones or
   a soundbar, or the old USB Bluetooth dongle? This decides whether S1 (multi-output route choice)
   moves up to Must have.
2. **Does more than one phone control this TV?** If not, M5 is mainly about route changes and could
   wait until 0.3. If yes, it stays a Must have.
3. **Do you watch streaming apps through an AVR or soundbar that receives Dolby/DTS?** If yes, EQ
   bypass by passthrough is a real risk on this box, and an explicit "this content bypasses the EQ"
   hint becomes worth adding to M4.
4. **May audible checks run on the Living Room TV, such as a cut-only curve during playback with
   the original state restored afterwards?** Without that, every audio feature can only be
   verified on a disposable target, and its TV status stays "deferred".
5. **Is the CloudWalker API 28 TV still in use, or only a build floor?** If it is still used, every
   milestone needs a second physical regression pass. If not, API 28 support stays compile-and-lint
   only.
