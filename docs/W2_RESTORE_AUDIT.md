# W2 restoration audit

Date: 2026-11-27. Scope: `EqualizerEngine` teardown restoration. No device/ADB/network access.
Red tests were reviewed and the narrow fix explicitly authorized before behavior changed.

## Verdicts before -> after

| Case | Before | After |
| --- | --- | --- |
| Control lost mid-restore | **BROKEN**: cached control checked once; writes continued after loss; errors swallowed. | **FIXED**: live `hasControl()` checked before every restore write; loss aborts, releases/resets, then throws. |
| Abrupt death before `onDestroy` | **UNMITIGATED LIMIT**: no durable pre-attach snapshot; `onDestroy` not guaranteed. | **UNCHANGED LIMIT**: no journal or restore-on-start. Post-kill hardware state is firmware-dependent and unverified; no definite state is claimed. |
| Open throws after partial attach | Release happened, but cleanup failure could mask the original exception. | **FIXED**: release/reset always runs; cleanup failure is suppressed on the original open exception. |
| Preset/route applied after original snapshot | **PASS**: snapshot taken in `open()` before service applies stored state. | **PASS, covered**: later applies do not replace the original snapshot; final readback matches it. |
| Pre-existing original vs persisted desired state | **PASS separation**: rollback targets hardware-at-attach, not persisted desired state. | **PASS, unchanged**: graceful teardown restores pre-attach state; next start reapplies desired state. |
| Rollback error observability | **BROKEN**: band exceptions swallowed; enable status ignored; no caller signal. | **FIXED**: control/band/enable/readback/release failures surface as exceptions after mandatory cleanup. Service logging is separate ownership. |
| Transient boost ordering when originally bypassed | **BROKEN**: boosted original bands written while enabled, then bypass. | **FIXED**: bypass first, confirm status and `getEnabled()` readback, then restore bands. |
| User `setEnabled(boolean)` status | **BROKEN**: returned `int` ignored; failure could look applied. | **FIXED**: non-`SUCCESS` throws; snapshot keeps the real readback state. |

Platform check: `Equalizer.setBandLevel(short,short)` is `void` and throws; `AudioEffect.setEnabled`
returns `int`; `hasControl()` is `boolean`; `release()` is `void` (SDK platform 34 `javap`).

## Fix summary

- Live control check before the bypass, each band write, and the enable write.
- Bypass-first when `originalEnabled=false`; status and enabled readback must confirm it before any
  boosted band is written.
- `setEnabled` status checked both in restore and in the public user command.
- Final band-level and enabled readback rejects silent no-op restores.
- Release and field reset always run, including restore or release failure; public failure throws
  only after cleanup.
- `open()` preserves its original exception and suppresses any cleanup failure onto it.
- Test seam only: nested `Effect`/`EffectFactory` with an Android adapter inside
  `EqualizerEngine.java`; public constructor and native attachment path unchanged.

## Evidence

Red, pre-fix (9 tests, 6 failed, 3 passed), copied before fixes to
`/tmp/opencode/hardening/w2-red/`:
`EqualizerEngine.prefix.java`, `EqualizerEngineRestoreTest.java`, `red-tests-4.log`,
`TEST-com.tonewire.tv.EqualizerEngineRestoreTest.xml`.

```
cd android
JAVA_HOME=/home/altneo/.local/jdk/jdk-17.0.21.1+2 \
ANDROID_HOME=/home/altneo/Android/Sdk ANDROID_SDK_ROOT=/home/altneo/Android/Sdk \
./gradlew --no-daemon :tv:testDebugUnitTest \
  --tests 'com.tonewire.tv.EqualizerEngineRestoreTest' --console=plain
```

Green focused, 15 tests, 0 failures, `BUILD SUCCESSFUL`:
`/tmp/opencode/w2-restore/restore-tests-green-final.log`.

Full TV suite, 68 tests, 0 failures, `BUILD SUCCESSFUL`:
`/tmp/opencode/w2-restore/full-tv-tests-final.log`.

Lint, `BUILD SUCCESSFUL`: `/tmp/opencode/w2-restore/tv-lint-final.log`.

## Limits / unverified

- No physical device: audible transients and vendor effect behavior are not observed.
- Abrupt death/power loss remains unmitigated; no claim that sound survives, reverts, or stays at a
  desired curve.
- Integrated service cleanup catches and logs an unverifiable restore; observer-stop and preference-flush
  failures cannot skip the release attempt. The engine releases and resets before throwing.
  A persistent on-screen recovery flow is outside this audit.
- Whether real firmware can consistently lie in every readback, or whether `release()` can throw on
  this TV, is unverified; both paths are covered with the fake.
- Readback is a best-effort engine check, not a substitute for external device verification.

## Files changed

- `android/tv/src/main/java/com/tonewire/tv/EqualizerEngine.java`
- `android/tv/src/test/java/com/tonewire/tv/EqualizerEngineRestoreTest.java` (new)
- `docs/W2_RESTORE_AUDIT.md` (new)
- `android/tv/src/main/java/com/tonewire/tv/TvEqualizerService.java` (integrated separately)
