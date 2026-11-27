# Verification log

Dated records of what was actually run, against what. Claims elsewhere in the docs should be
traceable to an entry here.

## 2026-11-26 — milestone 0.2 complete

**Commit:** `b354301` (branch `autonomous-dev`, based on `master` at `e8487a6`).

**Clean-clone check.** The branch was cloned fresh into `/tmp/opencode/clean-clone` and built there,
so nothing could pass merely because of uncommitted files or a stale build directory:

```
git clone <worktree> /tmp/opencode/clean-clone
cd android && ./gradlew --no-daemon verify build
```

Result: `BUILD SUCCESSFUL`, 182 tasks, **20 test classes, 74 tests, 0 failures**.
Artifacts: `tv-release-unsigned.apk` 47,336 bytes; `phone-release-unsigned.apk` 39,920 bytes.
`android/gradlew` is stored as mode 100755 in git, so the `chmod +x` step in CI is redundant
but harmless.

**On the physical TV** (`<TV-IP>:5555`, Allwinner UHD Streaming STB, Android 12 / API 31):

| Gate | Result |
| --- | --- |
| Service starts from a cold launch | `ServiceRecord` present, `isForeground=true` |
| Equalizer attaches | AudioFlinger: 1 effect chain, session 0, NXP Equalizer, `Ctrl yes`, 44.1 kHz PCM float on `AUDIO_DEVICE_OUT_SPEAKER` |
| Capabilities discovered | 5 bands, -1500..1500 mB, 10 firmware presets, `control=true` |
| Survives activity close | After `KEYCODE_HOME`, activity not resumed, service still running, one process |
| Enable state persists | `enabled=true` and `enabled=false` each survived force-stop plus cold launch, with independent kernel readback |
| Pairing round trip | `pair.request` → `pair.accept` → `auth.token` → `state.snapshot` over the real LAN |
| Reconnect without re-pairing | Force-stop and relaunch authenticated from the stored token |
| Truthful snapshot | Phone showed `+3.0 dB, 0, 0, 0, +3.0 dB` while the TV read `[300, 0, 0, 0, 300]`; `tv_name` "Living Room TV", `route_name` "Speakers" |
| Live push, two clients | Client A advanced to rev 7; client B's pushes stayed at its own rev 0; B's next command at rev 0 was accepted; A received the push at rev 7. No stale loop. Latency 55–64 ms |
| Stable identity | Same `tv_id` in DNS-SD TXT, `hello`, and phone storage; survived a TV app restart |
| Final state | `enabled=false`, levels `[300, 0, 0, 0, 300]`, `1 Effect Chains`, neutral `Baseline` persisted |

**Not verified, stated plainly:**

- Changed-IP multicast rediscovery. A direct TXT query to the TV answers; a multicast browse from
  <DEV-HOST> returns no records, and the emulator's NAT does not carry multicast. The adoption decision is
  covered by JVM tests, the network path is not.
- Route-change live push. The callbacks are wired but no vendor output change was performed.
- `engine=no_control` and `engine=unavailable` snapshots. JVM-covered only; the device never entered
  those states during testing.

## How to reproduce the environment

See [`android/docs/BUILDING.md`](../android/docs/BUILDING.md) for the toolchain. Two pitfalls that
cost real time during development and are worth knowing:

- **Match your adb version to the adb server you talk to.** A 34.x client against a 37.x server fails
  with `protocol fault (couldn't read status)`. Use the platform-tools adb that matches the server.
- **`ADB_SERVER_PORT` is not read by adb.** Pass the port explicitly with `-P`, or you will silently
  talk to the wrong server and see the wrong device list.

When upgrading an installed debug build, always `adb install -r` and never uninstall: the TV keeps its
pairing tokens in app data, and a signature mismatch means uninstalling, which erases them.

## 2026-11-27 — hardening

**Commit:** `36dad33`. Built from a **fresh clone** with `clean verify build`: **134 tests, 0
failures/errors/skips across 29 test classes.**

### What the hardening found

| Area | Outcome |
| --- | --- |
| W1 FGS policy | `FgsPolicy` helper + manifest-contract test. **Mutation-verified**: deleting `FOREGROUND_SERVICE_SPECIAL_USE` from the manifest makes the test fail. |
| W2 restore correctness | **Five real defects found and fixed.** Most important: when the original state was *bypassed* but cached levels were boosts, restore wrote boosted bands **while enabled**, then bypassed — a brief audible boost. Now bypass first, confirm it took, then write bands, then read back every band and the enable state. |
| W3 pairing claims | Evidence-only. Existing tests plus prior six-frame device evidence satisfied the SECURITY.md claims, so nothing was reimplemented. A negative result, reported as such. |
| W4 protocol fixtures | 18 golden frame tests pinning every v1 message the TV can emit. |

### Rejected during review

W2's first draft contained assertions that assumed an invalid setup order. They were rejected and
checked against the real Android signatures rather than accepted as a green test encoding a wrong
assumption.

### Also proven

The `428b1eb` recovery regression was demonstrated against the real previous source: the old code
sent 5 `state.get` for 5 `engine_unavailable` rejections (expected 0). Fixed source sends 0. Not a
compilation artefact.

### Still unverified, stated plainly

- **API 34 runtime.** The TV is API 31. The FGS branch is build-time and JVM verified only.
- **Abrupt process death mid-restore.** Only graceful teardown is covered; `onDestroy` is not
  guaranteed by Android and no test can make it so.
- **Hardware failure injection.** Control-loss paths are unit tested against a fake effect; no test
  forces the real NXP effect to fail.
- **NAT multicast rediscovery.** Unchanged from the previous entry.

Final TV state: foreground service and notification running, one NXP session-0 effect chain,
persisted `Baseline|300|0|0|0|300` with `enabled=false`, independent hardware readback
`[300, 0, 0, 0, 300]`.
