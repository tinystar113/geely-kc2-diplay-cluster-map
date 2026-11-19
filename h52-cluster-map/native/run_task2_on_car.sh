#!/usr/bin/env bash
# Task 2 on-car checks (plan Steps 4 and 5). Car connected over adb, no navigation running.
#   bash run_task2_on_car.sh cycle   -> bars twice (8 s each, 3 s gap), cluster traffic per second
#   bash run_task2_on_car.sh grid    -> calibration grid for 60 s (take a photo of the cluster)
set -u
export MSYS_NO_PATHCONV=1
ADB="${ADB:-$HOME/platform-tools-dl/platform-tools/adb.exe}"
HERE="$(cd "$(dirname "$0")" && pwd -W)"
MODE="${1:-cycle}"
OUT="$HERE/results"
mkdir -p "$OUT"
"$ADB" get-state >/dev/null 2>&1 || { echo "car not connected over adb"; exit 1; }
"$ADB" push "$HERE/out/vfb_selftest" /data/local/tmp/vfb_selftest >/dev/null
"$ADB" shell chmod 755 /data/local/tmp/vfb_selftest

if [ "$MODE" = grid ]; then
  echo "== calibration grid for 60 s - PHOTOGRAPH THE CLUSTER"
  "$ADB" shell '/data/local/tmp/vfb_selftest grid 60; echo exit=$?' | tee "$OUT/grid.txt"
  exit 0
fi

echo "== cycle test: bars 8 s, 3 s gap, bars 8 s - WATCH THE CLUSTER"
# One adb session so the traffic sampler lives as long as the test.
"$ADB" shell '( i=0; while [ $i -lt 26 ]; do echo "NET $(date +%H:%M:%S) $(grep usb0 /proc/net/dev)"; i=$((i+1)); sleep 1; done ) & NP=$!
sleep 1
/data/local/tmp/vfb_selftest cycle 8 2>&1 | while read l; do echo "$(date +%H:%M:%S) $l"; done; echo "exit=$?"
wait $NP' > "$OUT/cycle_raw.txt" 2>&1
grep -v '^NET' "$OUT/cycle_raw.txt"
echo "== cluster link bytes/s (draw windows should be > 300000, gap and after < 5000)"
awk '/^NET/{tx=$12; if(p!="") printf "%s %d\n", $2, tx-p; p=tx}' "$OUT/cycle_raw.txt" | tr '\n' ' '; echo
