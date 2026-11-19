#!/usr/bin/env bash
# Task 5 session 2.
#   bash session2.sh pull-old       copy the session-1 log left on the car (/data/local/tmp/t5_log.txt)
#   bash session2.sh capture [min]  record logcat in 1-minute pieces, each copied to the PC right away
#   bash session2.sh analyze FILE   route-guidance frames + DiPlay NAV decisions + cluster-map events
set -u
export MSYS_NO_PATHCONV=1
ADB="C:/Users/Hasee/platform-tools-dl/platform-tools/adb.exe"
HERE="$(cd "$(dirname "$0")" && pwd -W)"
case "${1:-}" in
  pull-old)
    "$ADB" pull /data/local/tmp/t5_log.txt "$HERE/session1_log.txt" && wc -l "$HERE/session1_log.txt" ;;
  capture)
    MIN="${2:-10}"; "$ADB" shell logcat -c
    for i in $(seq 1 "$MIN"); do
      "$ADB" shell 'logcat -v time -t 100000 -d' > "$HERE/s2_part_$(printf %02d "$i").txt" 2>/dev/null || { echo "adb lost at minute $i"; break; }
      "$ADB" shell logcat -c; echo "minute $i saved"; sleep 60
    done
    cat "$HERE"/s2_part_*.txt > "$HERE/session2_log.txt"; echo "combined: $HERE/session2_log.txt" ;;
  analyze)
    F="${2:?log file}"
    grep -a -E "0x520[12] RouteGuidance|^\S+ \S+ I/xcertplay-usb\([ 0-9]+\):   +\[[0-9]+\]|DiPlay-H52Diag.*NAV (state|watchdog)|route ended by watchdog|DiPlay-H52-ClusterMap|NavigateSt" "$F" \
      | grep -a -v "NAV input" | cut -c1-200 ;;
  *) echo "usage: session2.sh pull-old | capture [minutes] | analyze FILE" ;;
esac
