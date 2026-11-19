#!/usr/bin/env bash
# Test D: draw on the H52 cluster map surface the way the stock Gaode does.
# Usage: bash run_test_d.sh [seconds] [direct]
#   direct = skip libexternalsurfacehelper and call libui's neusoft_createDisplaySurface_vfb.
set -u
export MSYS_NO_PATHCONV=1
ADB="C:/Users/Hasee/platform-tools-dl/platform-tools/adb.exe"
HERE="$(cd "$(dirname "$0")" && pwd -W)"   # Windows-style path for adb.exe
SECS="${1:-30}"
MODE="${2:-helper}"
OUT="$HERE/testd_$(date +%H%M%S)"
mkdir -p "$OUT"

"$ADB" get-state >/dev/null 2>&1 || { echo "car not connected over adb"; exit 1; }
"$ADB" push "$HERE/vfbtest" /data/local/tmp/vfbtest >/dev/null
"$ADB" shell chmod 755 /data/local/tmp/vfbtest

# Navigation started, sent to all listeners (as in test C), plus guidance every second in the background.
"$ADB" shell "logcat -c; logcat -v time > /data/local/tmp/testd_log.txt & echo \$! > /data/local/tmp/testd_logpid
A=AUTONAVI_STANDARD_BROADCAST_SEND
am broadcast -a \$A --ei KEY_TYPE 10019 --ei EXTRA_STATE 8 >/dev/null
( i=0; while [ \$i -lt $((SECS + 5)) ]; do am broadcast -a \$A --ei KEY_TYPE 10001 --ei TYPE 0 --ei ICON 2 --ei SEG_REMAIN_DIS 200 --ei ROUTE_REMAIN_DIS 5000 --ei ROUTE_REMAIN_TIME 600 --es NEXT_ROAD_NAME TestRoad >/dev/null; i=\$((i+1)); sleep 1; done ) > /dev/null 2>&1 &
( i=0; while [ \$i -lt $((SECS + 5)) ]; do echo \"\$(date +%H:%M:%S) \$(grep usb0 /proc/net/dev)\"; i=\$((i+1)); sleep 1; done ) > /data/local/tmp/testd_net.txt 2>&1 &
sleep 2"

echo "== drawing on cluster surface for ${SECS}s (mode: $MODE) - WATCH THE CLUSTER"
if [ "$MODE" = direct ]; then
  "$ADB" shell "/data/local/tmp/vfbtest $SECS d" | tee "$OUT/vfbtest_output.txt"
else
  "$ADB" shell "/data/local/tmp/vfbtest $SECS" | tee "$OUT/vfbtest_output.txt"
fi

sleep 4
"$ADB" shell "am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND --ei KEY_TYPE 10019 --ei EXTRA_STATE 9 >/dev/null; sleep 2; kill \$(cat /data/local/tmp/testd_logpid)"
"$ADB" pull /data/local/tmp/testd_log.txt "$OUT/log.txt" >/dev/null
"$ADB" pull /data/local/tmp/testd_net.txt "$OUT/net.txt" >/dev/null

echo "== cluster link traffic per second (stock Gaode map stream was ~690000)"
awk '/usb0:/{tx=$11; if(p!="") print $1, tx-p; p=tx}' "$OUT/net.txt"
echo "== relevant log lines"
grep -a -E "ExternalSurfaceHelper|dual screens|product device|vfb|capture_screen|NavigateStarted|NavigateStopped|vfbtest" "$OUT/log.txt" | cut -c1-200 | head -30
echo "== results saved in $OUT"
