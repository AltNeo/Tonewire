#!/usr/bin/env bash
# Drives the Tonewire phone app on an emulator against a real TV,
# for verification that the production LAN protocol works end to end.
#
# The emulator is reached through an SSH tunnel to the emulator's own ADB port,
# because an adb *server* started inside an SSH session does not survive it.
#
# Usage:
#   ./phone-harness.sh preflight
#   ./phone-harness.sh install
#   ./phone-harness.sh launch
#   ./phone-harness.sh screenshot <outfile>
#   ./phone-harness.sh ui
#   ./phone-harness.sh tap <text>
#   ./phone-harness.sh type <text>
#   ./phone-harness.sh logcat <outfile>
#   ./phone-harness.sh state
#
set -uo pipefail

ADB=${ADB:-/home/altneo/Android/Sdk/platform-tools/adb}
PHONE_SERIAL=${PHONE_SERIAL:-127.0.0.1:5555}
TV_HOST=${TV_HOST:-}
TV_PORT=${TV_PORT:-49321}
REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
APK="$REPO_ROOT/phone/build/outputs/apk/debug/phone-debug.apk"
TUNNEL=/tmp/opencode/ww-emu-connect.sh

die() { echo "ERROR: $*" >&2; exit 1; }
info() { echo "==> $*"; }

phone_adb() { "$ADB" -s "$PHONE_SERIAL" "$@"; }

ensure_phone() {
    local state
    state=$("$ADB" devices | awk -v s="$PHONE_SERIAL" '$1 == s { print $2 }')
    if [ "$state" != "device" ]; then
        info "phone not connected (state=${state:-absent}); bringing the tunnel up"
        [ -x "$TUNNEL" ] || die "missing tunnel helper: $TUNNEL"
        "$TUNNEL" up >/dev/null 2>&1
        sleep 3
        state=$("$ADB" devices | awk -v s="$PHONE_SERIAL" '$1 == s { print $2 }')
    fi
    [ "$state" = "device" ] || die "phone $PHONE_SERIAL is not available (state=${state:-absent})"
}

cmd_preflight() {
    ensure_phone
    info "phone identity"
    phone_adb shell 'getprop ro.product.model; getprop ro.build.version.sdk; getprop sys.boot_completed' | tr -d '\r'
    info "phone build flavour (must be an emulator, not a physical phone)"
    local qemu
    qemu=$(phone_adb shell getprop ro.kernel.qemu | tr -d '\r')
    [ "$qemu" = "1" ] || die "refusing to drive a physical device (ro.kernel.qemu=$qemu)"
    info "installed Tonewire phone package"
    phone_adb shell 'pm list packages | grep tonewire' | tr -d '\r' || echo "  (not installed)"
    info "phone -> TV TCP reachability on the production port"
    if phone_adb shell "echo | nc -w 3 $TV_HOST $TV_PORT >/dev/null 2>&1 && echo REACHABLE || echo UNREACHABLE" | tr -d '\r' | grep -q REACHABLE; then
        echo "  $TV_HOST:$TV_PORT reachable"
    else
        echo "  $TV_HOST:$TV_PORT NOT reachable — is the TV service running?"
        return 1
    fi
}

cmd_install() {
    ensure_phone
    [ -f "$APK" ] || die "missing APK: $APK (build it first)"
    info "installing $(basename "$APK")"
    phone_adb install -r "$APK" 2>&1 | tail -2
}

cmd_launch() {
    ensure_phone
    phone_adb logcat -c 2>/dev/null || true
    info "launching PhoneMainActivity"
    phone_adb shell am start -W -n com.tonewire.phone/.PhoneMainActivity 2>&1 | tail -5
}

cmd_ui() {
    ensure_phone
    phone_adb shell uiautomator dump /sdcard/tonewire-window.xml >/dev/null 2>&1
    phone_adb shell cat /sdcard/tonewire-window.xml 2>/dev/null | tr -d '\r'
}

cmd_screenshot() {
    local out=${1:-/tmp/opencode/phone.png}
    ensure_phone
    mkdir -p "$(dirname "$out")"
    phone_adb exec-out screencap -p > "$out"
    info "screenshot: $out ($(stat -c%s "$out") bytes)"
}

# Finds a node whose text or content-desc matches, then taps its centre.
cmd_tap() {
    local needle=$1 xml
    [ -n "$needle" ] || die "tap requires text to match"
    ensure_phone
    xml=$(cmd_ui)
    local bounds
    bounds=$(printf '%s' "$xml" | python3 -c '
import re, sys
needle = sys.argv[1].lower()
xml = sys.stdin.read()
for node in re.findall(r"<node[^>]*>", xml):
    text = re.search(r"text=\"([^\"]*)\"", node)
    desc = re.search(r"content-desc=\"([^\"]*)\"", node)
    values = [v.group(1).lower() for v in (text, desc) if v]
    if any(needle in v for v in values):
        b = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", node)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            print(f"{(x1 + x2) // 2} {(y1 + y2) // 2}")
            break
' "$needle")
    [ -n "$bounds" ] || die "no UI node matching '$needle'"
    info "tapping '$needle' at $bounds"
    phone_adb shell input tap $bounds
}

cmd_type() {
    local text=$1
    [ -n "$text" ] || die "type requires text"
    ensure_phone
    phone_adb shell input text "$text"
}

cmd_logcat() {
    local out=${1:-/tmp/opencode/phone-logcat.txt}
    ensure_phone
    mkdir -p "$(dirname "$out")"
    phone_adb logcat -d -v threadtime > "$out" 2>/dev/null
    info "logcat: $out ($(wc -l < "$out") lines)"
}

# Prints the phone app's visible state summary from the UI hierarchy.
cmd_state() {
    local xml
    xml=$(cmd_ui)
    printf '%s' "$xml" | python3 -c '
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
' || true
}

usage() { sed -n '2,18p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

case "${1:-}" in
    preflight)  cmd_preflight ;;
    install)    cmd_install ;;
    launch)     cmd_launch ;;
    ui)         cmd_ui ;;
    state)      cmd_state ;;
    screenshot) shift; cmd_screenshot "$@" ;;
    tap)        shift; cmd_tap "$@" ;;
    type)       shift; cmd_type "$@" ;;
    logcat)     shift; cmd_logcat "$@" ;;
    ""|-h|--help) usage ;;
    *) die "unknown command: $1" ;;
esac
