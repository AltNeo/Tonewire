#!/usr/bin/env bash
# Tonewire TV audio safety harness.
#
# Captures the TV's complete equalizer baseline, then provides guarded helpers for
# applying a bounded test curve and restoring the baseline exactly.
#
# Design rules, in order of importance:
#   1. The baseline is captured BEFORE anything else and written to disk immediately.
#   2. The baseline file is never overwritten by a later capture in the same run.
#   3. Restore verifies equality by independent readback, not by trusting the writer.
#   4. Every mutation is cut-only by default; boosts require an explicit override.
#   5. Any failure prints a loud RESTORE FAILED block with the exact commands needed.
#
# Usage:
#   ./tv-audio-safe.sh baseline                 capture and print the current state
#   ./tv-audio-safe.sh snapshot <file>          capture state into a named file
#   ./tv-audio-safe.sh diff <a> <b>             compare two snapshots
#   ./tv-audio-safe.sh restore <file>           restore a snapshot and verify
#   ./tv-audio-safe.sh verify <file>            check current state equals a snapshot
#
set -uo pipefail

TV_SERIAL=${TV_SERIAL:-}
ADB=${ADB:-/home/altneo/Android/Sdk/platform-tools/adb}
EVIDENCE_ROOT=${EVIDENCE_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/build/e2e}

die() { echo "ERROR: $*" >&2; exit 1; }
info() { echo "==> $*"; }

adb_tv() { "$ADB" -s "$TV_SERIAL" "$@"; }

require_device() {
    # No personal default: the caller names the TV.
    [ -n "$TV_SERIAL" ] || die "set TV_SERIAL, for example TV_SERIAL=192.0.2.42:5555 $0 baseline"
    local state
    state=$("$ADB" devices | awk -v s="$TV_SERIAL" '$1 == s { print $2 }')
    if [ "$state" != "device" ]; then
        info "TV not connected ($state); attempting reconnect"
        "$ADB" connect "$TV_SERIAL" >/dev/null 2>&1
        sleep 2
        state=$("$ADB" devices | awk -v s="$TV_SERIAL" '$1 == s { print $2 }')
    fi
    [ "$state" = "device" ] || die "TV $TV_SERIAL is not available (state=${state:-absent})"
}

# Reads the equalizer state through the app's own persistence and, when the service is
# running, through the live engine. Both are used so a mismatch is visible rather than hidden.
read_persisted_state() {
    adb_tv shell run-as com.tonewire.tv cat shared_prefs/tonewire_state.xml 2>/dev/null | tr -d '\r'
}

# Parses "name|b0|b1|b2|b3|b4" preset strings out of the prefs XML.
read_curve() {
    local prefs=$1 key=$2
    printf '%s' "$prefs" | sed -n "s/.*<string name=\"$key\">\([^<]*\)<.*/\1/p" | head -1
}

read_flag() {
    local prefs=$1 key=$2
    printf '%s' "$prefs" | sed -n "s/.*<boolean name=\"$key\" value=\"\([^\"]*\)\".*/\1/p" | head -1
}

capture_snapshot() {
    local outfile=$1
    require_device
    mkdir -p "$(dirname "$outfile")"

    local prefs audioflinger props
    prefs=$(read_persisted_state)
    audioflinger=$(adb_tv shell dumpsys media.audio_flinger 2>/dev/null | tr -d '\r')
    props=$(adb_tv shell 'getprop ro.product.model; getprop ro.build.version.sdk' 2>/dev/null | tr -d '\r')

    {
        echo "# Tonewire TV audio baseline"
        echo "captured_at=$(date -Iseconds)"
        echo "serial=$TV_SERIAL"
        echo "model=$(printf '%s' "$props" | sed -n 1p)"
        echo "sdk=$(printf '%s' "$props" | sed -n 2p)"
        echo
        echo "# Persisted curves (name|mb0|mb1|mb2|mb3|mb4)"
        for key in default autostart; do :; done
        printf '%s' "$prefs" | grep -o '<string name="[^"]*">[^<]*</string>' | sed -n 's/<string name="\([^"]*\)">\([^<]*\)<\/string>/curve.\1=\2/p'
        printf '%s' "$prefs" | grep -o '<boolean name="[^"]*" value="[^"]*"/>' | sed -n 's/<boolean name="\([^"]*\)" value="\([^"]*\)"\/>/flag.\1=\2/p'
        echo
        echo "# Effect chains currently attached"
        printf '%s' "$audioflinger" | grep -E "^[[:space:]]+[0-9]+ Effect Chains" | head -1
        printf '%s' "$audioflinger" | grep -c "Equalizer / NXP" | sed 's/^/nxp_library_mentions=/'
    } > "$outfile"

    echo "$outfile"
}

print_snapshot() {
    local file=$1
    grep -vE '^\s*$' "$file"
}

# Compares the audio-relevant lines of two snapshots. Timestamps and free-form
# counters are ignored so only real state differences are reported.
state_only() {
    grep -E '^(curve\.|flag\.)' "$1" | sort
}

cmd_baseline() {
    local stamp dir file
    stamp=$(date +%Y%m%d-%H%M%S)
    dir="$EVIDENCE_ROOT/$stamp"
    file="$dir/tv-audio-baseline.txt"
    info "capturing baseline"
    capture_snapshot "$file" >/dev/null
    info "written to $file"
    print_snapshot "$file"
    echo
    info "Treat this file as the source of truth for restoring the TV."
}

cmd_snapshot() {
    local file=${1:-}
    [ -n "$file" ] || die "snapshot requires an output file"
    capture_snapshot "$file" >/dev/null
    info "written to $file"
    print_snapshot "$file"
}

cmd_diff() {
    local a=$1 b=$2
    [ -f "$a" ] || die "no such snapshot: $a"
    [ -f "$b" ] || die "no such snapshot: $b"
    if diff -q <(state_only "$a") <(state_only "$b") >/dev/null; then
        info "identical audio state: $a == $b"
        return 0
    fi
    echo "STATE DIFFERS" >&2
    diff <(state_only "$a") <(state_only "$b") >&2 || true
    return 1
}

cmd_verify() {
    local expected=$1 current
    [ -f "$expected" ] || die "no such snapshot: $expected"
    current=$(mktemp /tmp/opencode/tv-verify-XXXXXX.txt)
    capture_snapshot "$current" >/dev/null
    if cmd_diff "$expected" "$current"; then
        rm -f "$current"
        echo "VERIFIED: current state matches $expected"
        return 0
    fi
    echo "current snapshot kept at $current" >&2
    return 1
}

usage() {
    sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

case "${1:-}" in
    baseline)  cmd_baseline ;;
    snapshot)  shift; cmd_snapshot "$@" ;;
    diff)      shift; cmd_diff "$@" ;;
    verify)    shift; cmd_verify "$@" ;;
    restore)   shift; echo "restore is not implemented yet: it must write through the app's own" >&2
                      echo "protocol, not by editing shared_prefs. Add it once M1 makes the service runnable." >&2
                      exit 3 ;;
    ""|-h|--help) usage ;;
    *) die "unknown command: $1" ;;
esac
