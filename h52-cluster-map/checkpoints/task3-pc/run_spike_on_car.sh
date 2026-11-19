#!/usr/bin/env bash
# Task 3 Steps 1 + 3 on the car: decode-to-texture spike + keep-alive check (~60 s).
# Phases (watch the cluster): A 0-20 s sample video plays | B 20-40 s last frame held |
#                             C 40-50 s cluster back to normal | D stop.
set -u
export MSYS_NO_PATHCONV=1
ADB="${ADB:-$HOME/platform-tools-dl/platform-tools/adb.exe}"
HERE="$(cd "$(dirname "$0")" && pwd -W)"
OUT="$HERE/results"
mkdir -p "$OUT"
"$ADB" get-state >/dev/null 2>&1 || { echo "car not connected over adb"; exit 1; }
"$ADB" install -r "$HERE/out/diplay-cluster-spike.apk" | tail -1

echo "== spike running ~60 s - WATCH THE CLUSTER"
"$ADB" shell 'logcat -c; logcat -v time -s DiPlay-Spike DiPlay-H52-ClusterMap DiPlay-H52-VFB AndroidRuntime libc DEBUG > /data/local/tmp/spike_log.txt & LP=$!
am start -n com.diplay.spike/.SpikeActivity > /dev/null
i=0; while [ $i -lt 60 ]; do echo "NET $(date +%H:%M:%S) $(grep usb0 /proc/net/dev)"; i=$((i+1)); sleep 1; done
kill $LP' > "$OUT/spike_net.txt" 2>&1
"$ADB" pull /data/local/tmp/spike_log.txt "$OUT/spike_log.txt" >/dev/null
echo "== log"
grep -E "PHASE|RESULT|available|decoder|first frame|draws=|route=|open|closed|FATAL|failed|SIGSEGV" "$OUT/spike_log.txt" | cut -c1-160
echo "== cluster link bytes/s (A,B > 300000; C,D < 5000)"
awk '/^NET/{tx=$12; if(p!="") printf "%s %d\n", $2, tx-p; p=tx}' "$OUT/spike_net.txt" | tr '\n' ' '; echo
