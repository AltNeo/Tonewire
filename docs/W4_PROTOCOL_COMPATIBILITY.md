# W4: v1 protocol golden-frame compatibility

Scope: reviewed, literal ASCII v1 frames for every message type the TV currently emits, plus the
modeled-but-unemitted `route.changed` and a synthetic unknown-field tolerance case. No protocol
version bump and no production code changes.

## Inventory (source: TvNetworkServer, AuthenticatedConnection, EngineCommandDispatcher)

Emitted today, 8 types:

| type | producer | notes |
| --- | --- | --- |
| `hello` | TvNetworkServer `handleClient` | server hello plus additive `tv_id` field |
| `pair.accept` | TvNetworkServer pairing branch | hand-built `peer_id`, `token_id`, `token` (Android URL_SAFE padded Base64) |
| `pair.reject` | TvNetworkServer pairing branch | `invalid_or_expired_code`, `rate_limited`, `storage_unavailable`, `invalid_device_id` |
| `auth.token` | TvNetworkServer auth branch | `status=ok` acknowledgement |
| `state.snapshot` | EngineCommandDispatcher `snapshot` | current 12-field dispatcher set |
| `eq.band.ack` | EngineCommandDispatcher | `band`, applied `level_mb` |
| `error` | both | default dispatcher `engine_unavailable`, dispatcher `invalid_command`, auth/revision errors |
| `pong` | EngineCommandDispatcher | nonce echo |

Modeled but not currently emitted: `route.changed` (factory exists in `ProtocolMessages`; no
production emission path). Fixture is labelled `modeled-not-emitted` in `cases.tsv`.

## Golden resources

`android/protocol/src/test/resources/v1/` (13 cases, the compatibility source of truth):

- hello-tv.txt
- pair-accept.txt
- pair-reject.txt
- auth-token-ok.txt
- state-snapshot-healthy.txt
- state-snapshot-engine-unavailable.txt
- eq-band-ack.txt
- pong.txt
- error-engine-unavailable.txt
- error-invalid-command.txt
- route-changed.txt (modeled-not-emitted)
- state-snapshot-legacy-minimal.txt (factory-only legacy helper)
- state-snapshot-unknown-field.txt (synthetic tolerance)

`android/tv/src/test/resources/v1/` holds only the 5 frames produced by the dispatcher
(healthy/unavailable snapshot, band ack, pong, invalid-command error), so the TV-contract copy is
explicitly the dispatcher subset rather than a duplicate of the full protocol set.

All frames are literal reviewed bytes with real TAB separators and a trailing LF. They are never
generated from `ProtocolCodec` at test time. Deterministic values only (fixed nonce, fixed
32-byte token, fixed IDs); no secrets.

## Tests

`android/protocol/src/test/java/com/tonewire/protocol/V1GoldenFramesTest.java` — 12 test methods,
13 golden frames. Decodes and byte-compares against production factories where a factory exists,
checks `token_id` is the Java `String.hashCode` hex of the stored token, verifies the manifest
enumerates exactly the 8 emitted types, and verifies an unknown additive field does not disturb
known fields (and re-encodes identically).

`android/tv/src/test/java/com/tonewire/tv/TvProtocolGoldenFramesTest.java` — 6 test methods,
5 dispatcher frames. Byte-exact comparison of current `EngineCommandDispatcher` output for healthy
and engine-unavailable snapshots, band ack, pong, and the invalid-command error path, using the
package-visible `EqualizerEngine.State` constructor and a fake `TvEqualizerController`.

Case count and test count are separate: **18 fixture cases** across the two resource sets
(13 protocol + 5 TV, with the dispatcher subset intentionally duplicated), covered by
**18 test methods** (12 protocol + 6 TV). The all-frames literal validation method re-covers every
protocol case, and the TV manifest method re-covers each dispatcher frame.

## Documented divergence: legacy snapshot helper

`ProtocolMessages.stateSnapshot` remains the legacy minimal factory (`enabled`, `route_id`,
`levels_mb` only). It is **not authoritative** for what the TV sends: the dispatcher adds
`features`, `presets`, `tv_name`, `route_name`, `preset_name`, `engine`, `centers_hz`,
`min_mb`, `max_mb`. The tests pin the legacy bytes separately and assert the field-set divergence
rather than pretending the helper describes the current wire contract.

## Verification

Commands (from `android/`):

```
./gradlew :protocol:test :tv:testDebugUnitTest --offline
```

Result: BUILD SUCCESSFUL; `:protocol:test` 23 tests (V1GoldenFramesTest 12), `:tv:testDebugUnitTest`
80 tests (TvProtocolGoldenFramesTest 6); 0 failures, 0 errors, 0 skipped.

## Coverage limits

- No device, emulator, ADB, TV, or network connection was used. No empirical hardware claim is made.
- The 4 server-only frames (`hello`, `pair.accept`, `pair.reject`, `auth.token`) are exercised as
  literal bytes through decode/factory parity only. `TvNetworkServer` hand-builds its hello and
  pair.accept maps and is not constructed in these JVM tests (it requires Android `Context`/NSD),
  so a future change to that server-side field construction would not automatically fail these
  tests. Closing that needs an Android/instrumentation or refactor seam, out of scope here.
- `pair.accept` production bytes differ from the `ProtocolMessages.pairAccept(..., AuthToken)`
  factory: the server emits Android padded URL_SAFE Base64 while the factory emits unpadded. The
  test compares decoded field values and explicitly does not treat the factory as a byte oracle for
  this frame.
- Old-APK/phone compatibility was not tested end to end; only codec/factory/dispatcher behavior
  is pinned. Unknown-field tolerance is shown at the codec/message layer, not across a real client.
