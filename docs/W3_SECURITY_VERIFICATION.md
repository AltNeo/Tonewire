# W3 security verification

This note records the evidence for the pairing and peer-identifier claims in
`SECURITY.md`. Production networking code was reviewed but not changed.

## JVM unit coverage

`PairingCodeManagerTest.fifthWrongCodeLocksGloballyRotatesAndCannotBeBypassedByRefresh`
checks that four wrong codes return `INVALID`, the fifth returns
`RATE_LIMITED`, the code rotates, the lock lasts 30 seconds, refreshing during
the lock does not allow the displayed code, and a correct code works after the
lock. `missingAndMalformedPeerIdsAreRejected` checks null, empty, malformed,
and valid values through `TvNetworkServer.validPeerId`. These are manager and
validator unit tests; they do not invoke `TvNetworkServer.handleClient`.

Run from `android/`:

```sh
JAVA_HOME=/home/altneo/.local/jdk/jdk-17.0.21.1+2 \
ANDROID_HOME=/home/altneo/Android/Sdk \
ANDROID_SDK_ROOT=/home/altneo/Android/Sdk \
./gradlew :tv:testDebugUnitTest
```

Run on 2026-11-27: `:tv:testDebugUnitTest` completed successfully with 74 tests,
0 failures, 0 errors, and 0 skipped; Gradle exit code was 0.

## Recorded device protocol evidence

Device evidence recorded at
`/tmp/opencode/s3/device-evidence.txt` records a prior run of
`/tmp/opencode/s3/negative-pairing.py` against the TV service. The evidence
file timestamp is 2026-11-26 23:32 IST; no separate capture date is recorded.
It shows a `pair.request` without `device_id` rejected with
`invalid_device_id`, an `auth.token` without `peer_id` rejected with
`invalid_peer_id`, and six bad pair requests receiving
`invalid_or_expired_code` for attempts one through four and `rate_limited` for
attempts five and six. The recorded run ends with
`NEGATIVE_PAIRING_GATE_PASS=true`.

This is supplied prior device evidence, not a run performed during this
verification. No device or network connection was made for this work. The
device transcript demonstrates the actual server response mapping for those
cases; the JVM tests independently cover the manager lock and peer ID
validation logic. Process restart resets the in-memory counter and is not
claimed as durable rate limiting.
