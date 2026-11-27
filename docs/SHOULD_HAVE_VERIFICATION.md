# Should-have verification

Primary configuration: one HDMI output on the API 31 Allwinner Living Room TV.
S1 is complete under the reduced scope through M3: equivalent descriptors resolve
stably; distinct second outputs do not reject global presets. No priority ladder.

## S3 — pairing and revocation

`./gradlew --no-daemon verify build` passed (80 JVM tests, zero failures).
On-device negative pairing evidence: missing device_id -> invalid_device_id;
missing peer_id -> invalid_peer_id; four wrong guesses -> invalid_or_expired_code;
fifth and sixth -> rate_limited. JVM tests verify the 30-second boundary, code
rotation, refresh not bypassing lockout, successful pairing resetting the counter,
credential-store clearing/failure, and failed commands not consuming revisions.
Phone resynchronizes rejected revisions; old failed-mutation consumption is removed.

Forget all phones uses a TV confirmation, durable token clearing and connection
closure serialized against authentication and commands. The destructive revoke /
re-pair device flow is not yet exercised; existing phone credential was preserved.
No audio commands were sent. Evidence: `/tmp/opencode/s3/`.

## S2 — TV-owned saved presets

Full verify/build passed (86 JVM tests, zero failures). Three built-ins and up to
eight custom curves are advertised in v1 snapshots using `features=presets` and
`presets=id:base64url(UTF-8 name):builtIn,...`. Names are 1–48 characters; control
characters and the storage delimiter `|` are rejected. Duplicate names are rejected.
New phones hide Save/Delete when the capability is absent and retain legacy Apply.

On the real TV, an authenticated client saved `S2 test, café: late`, rejected a
duplicate without consuming revision 1, restarted the app, observed the saved entry,
and deleted it. The final catalog contained only the original three built-ins.
Saving/deleting never applies a curve; no audio mutation was performed. Phone and
TV APK upgrades retained data. Repository tests cover the eight-custom limit,
built-in protection, persistence and deletion without changing the current sound.
Catalog/phone parser tests cover delimiter/Unicode names and capability fallback.
End-to-end phone dialog taps and custom Apply remain unverified at this checkpoint.
Evidence: `/tmp/opencode/s2/`.

## S4 — read-only TV sound status

Full verify/build passed (89 JVM tests, zero failures). The TV screen reads the
same authoritative snapshot as phones and shows EQ on/bypassed, friendly output,
preset, engine health and authenticated phone count. Paired TVs hide the code
behind Pair a phone; unpaired TVs show it automatically. Buttons remain D-pad
focusable and the content scrolls on smaller displays. Forget remains confirmed.

The real TV screenshot shows Living Room TV, EQ bypassed, Speakers · UHD Streaming
STB, Baseline, Equalizer ready, and 1 phone connected. No code is shown in this
paired steady state. The existing service and notification remain running. JVM
presentation tests cover startup/unavailable, lost control, and pairing visibility.
Evidence: `/tmp/opencode/s4/status.png`, `focus.png` and `verify.log`.

### S3 follow-up device evidence

Forget all phones was exercised through its TV confirmation. A live authenticated
raw client was disconnected, and its old token received authentication_failed on a
new socket. The legitimate phone then re-paired through its normal dialog and
returned to LIVE, without pm clear or losing its endpoint.

The re-pair check exposed a pre-existing frame-batch bug: an unauthenticated
heartbeat could skip a following pair.request in the same TCP read. Continuing the
batch after authentication_required fixes it. A real socket test coalescing
state.get + pair.request received authentication_required then pair.accept.
Evidence: `/tmp/opencode/s3/revocation-evidence.txt`, `batch-repair-evidence.txt`,
`repair-evidence.txt`, and `phone-repaired.xml`.

## S5 — headroom guard

Full verify/build passed (93 JVM tests, zero failures). Make cut-only subtracts the
positive peak uniformly, never raises already cut-only curves, and rejects a shift
that cannot preserve the shape within hardware limits. Warning threshold is
strictly above +600 mb (+6 dB). No preamp or additional effect is created.

Real phone UI test while bypassed: [300,0,0,0,300] -> [0,-300,-300,-300,0] mb,
independently read from the physical effect. Complete preferences and hardware were
restored exactly, leaving Baseline, enabled=false, [300,0,0,0,300] with the service
running. The device-local restore watchdog was cancelled after verification.
High-boost warning and impossible-range behavior are JVM-covered; no >+6 dB boost
was introduced on the TV to render the warning, and acoustic clipping/listening
validation is not claimed. Evidence: `/tmp/opencode/s5/device-evidence.txt`,
`recovery-journal.jsonl`, and `verify-final.log`.

## Final phone integration and recovery check

Phone Save current as, custom Apply, and Delete were exercised through the real
phone Views and production LAN protocol. The saved curve matched the bypassed
baseline, so Apply did not change the audible state. The temporary preset was
removed and full TV preferences restored exactly. Evidence:
`/tmp/opencode/s2/phone-ui-evidence.txt`, `phone-saved-catalog.xml`,
`phone-deleted-catalog.xml`, and `final-restoration.txt`.

Rejected preset commands now remain visible in a dialog while state resynchronizes;
a real blank-name Save produced the dialog after a successful fresh snapshot.
A startup race also exposed immediate retries of engine_unavailable. Recovery now
allows at most one immediate resync until a valid snapshot and waits for push or
heartbeat while the engine starts, avoiding request/dialog floods. Two regression
tests cover these boundaries. The final full verify/build passed with 95 unique
JVM tests (TV 53, phone 31, protocol 11), zero failures, plus Android lint and builds.
Evidence: `/tmp/opencode/s2/verify-rejection-feedback.log`, `rejection-dialog.png`,
`final-phone-log.txt`.

Final steady state: foreground TV service running; Baseline|300|0|0|0|300 persisted,
enabled=false; independent hardware [300,0,0,0,300] with EQ bypassed. The legitimate
phone is paired with a newly stored credential following the deliberate S3 revoke
check; no phone data clear was used. Discarded raw-test credentials were removed.
No test presets, restore watchdogs or changed audio state remain. S1 is done by
reduced scope; S2–S5 implementation and the described device checks are complete.
The +6 dB warning boundary and impossible-range headroom behavior are JVM-only;
subjective acoustic clipping/listening validation is not claimed. No priority
ladder, passthrough/vendor change, TV reboot or out-of-scope feature was added.
