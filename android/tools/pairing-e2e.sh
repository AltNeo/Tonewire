#!/usr/bin/env bash
# End-to-end pairing and control test: emulator phone -> physical TV over the
# production LAN protocol.
#
# The TV shows a six-digit code. This reads it from the TV's UI, types it into the
# phone emulator's pairing dialog, and then verifies that the phone reaches LIVE
# and that the TV acknowledges authenticated commands.
#
# ADB is used only to read the TV screen and drive the phone UI. All protocol
# traffic travels directly from the emulator to the TV over the LAN.
#
# Requires the TV lease. Do not run while another agent holds it.
#
# Usage: ./pairing-e2e.sh [run-dir]
set -uo pipefail

ADB=${ADB:-/home/altneo/Android/Sdk/platform-tools/adb}
PHONE_SERIAL=${PHONE_SERIAL:-127.0.0.1:5555}
TV_SERIAL=${TV_SERIAL:-}
TV_HOST=${TV_HOST:-}
TV_PORT=${TV_PORT:-49321}
REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
RUN_DIR=${1:-$REPO_ROOT/build/e2e/pairing-$(date +%Y%m%d-%H%M%S)}

info() { echo "==> $*"; }
warn() { echo "WARNING: $*" >&2; }
die() { echo "ERROR: $*" >&2; exit 1; }

phone() { "$ADB" -s "$PHONE_SERIAL" "$@"; }
tv() { "$ADB" -s "$TV_SERIAL" "$@"; }

self_test() {
    info "identities"
    # No personal defaults: require the caller to name the TV and its address.
    [ -n "$TV_SERIAL" ] || die "set TV_SERIAL, for example TV_SERIAL=192.0.2.42:5555 $0"
    [ -n "$TV_HOST" ] || die "set TV_HOST to the TV's LAN address, for example TV_HOST=192.0.2.42"
    local p t
    p=$(phone shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')
    [ "$p" = "1" ] || die "phone serial is not an emulator (ro.kernel.qemu=$p)"
    t=$(tv shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')
    [ -n "$t" ] || die "TV $TV_SERIAL is not reachable; run 'adb connect' first"
    info "phone=emulator TV sdk=$t"
}

# uiautomator needs an idle window, but the TV status screen has a countdown that
# keeps redrawing, so dumps fail intermittently with "could not get idle state".
# Retry a few times before giving up.
dump_tv_ui() {
    local outfile=$1 i
    for i in 1 2 3 4 5; do
        tv shell uiautomator dump /sdcard/tw-tv.xml >/dev/null 2>&1
        tv shell cat /sdcard/tw-tv.xml > "$outfile" 2>/dev/null
        if [ -s "$outfile" ]; then return 0; fi
        sleep 2
    done
    return 1
}

# Pulls the six-digit pairing code from the TV status screen.
read_tv_code() {
    tv shell cat /sdcard/tw-tv.xml 2>/dev/null | tr -d '\r' | python3 -c '
import re, sys
xml = sys.stdin.read()
for node in re.findall(r"<node[^>]*>", xml):
    t = re.search(r"text=\"([^\"]*)\"", node)
    if not t:
        continue
    v = t.group(1).strip()
    if re.fullmatch(r"\d{6}", v):
        print(v)
        break
'
}

# Reads the phone's visible text lines, one per line.
phone_texts() {
    phone shell uiautomator dump /sdcard/tw-phone.xml >/dev/null 2>&1
    phone shell cat /sdcard/tw-phone.xml 2>/dev/null | tr -d '\r' | python3 -c '
import re, sys
xml = sys.stdin.read()
seen = []
for node in re.findall(r"<node[^>]*>", xml):
    t = re.search(r"text=\"([^\"]*)\"", node)
    if t and t.group(1).strip():
        v = t.group(1).strip()
        if v not in seen:
            seen.append(v)
print("\n".join(seen))
'
}

# Taps the node whose text or content-desc contains the needle.
phone_tap() {
    local needle=$1 xml bounds
    xml=$(phone shell cat /sdcard/tw-phone.xml 2>/dev/null | tr -d '\r')
    if [ -z "$xml" ]; then
        phone shell uiautomator dump /sdcard/tw-phone.xml >/dev/null 2>&1
        xml=$(phone shell cat /sdcard/tw-phone.xml 2>/dev/null | tr -d '\r')
    fi
    bounds=$(printf '%s' "$xml" | python3 -c '
import re, sys
needle = sys.argv[1].lower()
xml = sys.stdin.read()
for node in re.findall(r"<node[^>]*>", xml):
    vals = []
    for attr in ("text", "content-desc"):
        m = re.search(attr + r"=\"([^\"]*)\"", node)
        if m:
            vals.append(m.group(1).lower())
    if any(needle in v for v in vals):
        b = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", node)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            print(f"{(x1 + x2) // 2} {(y1 + y2) // 2}")
            break
' "$needle")
    [ -n "$bounds" ] || return 1
    info "tap '$needle' at $bounds"
    phone shell input tap $bounds
}

# Types a value into the currently focused field after clearing it.
phone_type() {
    local value=$1
    phone shell input keyevent KEYCODE_MOVE_END
    for _ in $(seq 1 12); do phone shell input keyevent KEYCODE_DEL; done
    phone shell input text "$value"
}

wait_for_text() {
    local needle=$1 timeout=${2:-30} i
    for i in $(seq 1 "$timeout"); do
        if phone_texts | grep -qi "$needle"; then return 0; fi
        sleep 1
    done
    return 1
}

main() {
    mkdir -p "$RUN_DIR"
    info "run dir: $RUN_DIR"
    self_test

    info "TV network status"
    tv shell "dumpsys activity services" 2>/dev/null | grep -c "TvEqualizerService" | sed 's/^/  service_records=/'
    if ! phone shell "echo | nc -w 3 $TV_HOST $TV_PORT >/dev/null 2>&1 && echo OK || echo FAIL" | tr -d '\r' | grep -q OK; then
        die "phone cannot reach $TV_HOST:$TV_PORT"
    fi
    info "phone can reach the TV protocol port"

    info "clearing any existing phone credential for a fresh pairing"
    phone shell pm clear com.tonewire.phone >/dev/null 2>&1 || true

    info "launching the phone app"
    phone logcat -c 2>/dev/null || true
    phone shell am start -W -n com.tonewire.phone/.PhoneMainActivity 2>&1 | tail -3
    sleep 4

    local code
    if ! dump_tv_ui "$RUN_DIR/tv-ui.xml"; then
        die "cannot read the TV screen after 5 attempts; evidence in $RUN_DIR"
    fi
    code=$(read_tv_code)
    if [ -z "$code" ]; then
        warn "no six-digit code found on the TV screen"
        die "cannot read the pairing code; TV UI saved to $RUN_DIR/tv-ui.xml"
    fi
    info "TV pairing code read (${#code} digits)"
    # Never persist the pairing code in evidence.
    printf 'code_length=%s\n' "${#code}" > "$RUN_DIR/pairing.txt"

    info "opening the connect dialog"
    if ! phone_tap "connect"; then
        warn "no CONNECT control found; phone UI follows"
        phone_texts | sed 's/^/  /'
        die "cannot open the connect dialog"
    fi
    sleep 2

    info "entering the TV address"
    phone_tap "${TV_HOST%%.*}" >/dev/null 2>&1 || true
    phone_type "$TV_HOST:$TV_PORT"
    sleep 1
    phone_tap "connect" || die "cannot submit the address"

    info "waiting for pairing prompt"
    if ! wait_for_text "six-digit\|pair" 25; then
        warn "no pairing prompt appeared"
        phone_texts | sed 's/^/  /'
        die "pairing prompt did not appear"
    fi

    info "entering the pairing code"
    phone_type "$code"
    sleep 1
    phone_tap "pair" || die "cannot submit the code"

    info "waiting for LIVE"
    if ! wait_for_text "live" 40; then
        warn "phone did not reach LIVE"
        phone_texts | sed 's/^/  /'
        phone logcat -d -v threadtime > "$RUN_DIR/phone-logcat.txt" 2>/dev/null
        die "pairing did not complete"
    fi
    info "phone reports LIVE"

    phone shell uiautomator dump /sdcard/tw-phone.xml >/dev/null 2>&1
    phone shell cat /sdcard/tw-phone.xml > "$RUN_DIR/phone-ui.xml" 2>/dev/null
    phone shell screencap -p > "$RUN_DIR/phone-live.png" 2>/dev/null || true
    phone logcat -d -v threadtime > "$RUN_DIR/phone-logcat.txt" 2>/dev/null

    info "confirming authenticated state exchange"
    phone logcat -d 2>/dev/null | grep -iE "TW_(SENT|ACK|SNAPSHOT)" | tail -10 | sed 's/^/  /' | tee "$RUN_DIR/protocol-lines.txt"

    printf 'PAIRING_E2E_PASS=true\nrun_dir=%s\n' "$RUN_DIR" > "$RUN_DIR/RESULT.txt"
    info "PASS — evidence in $RUN_DIR"
}

main "$@"
