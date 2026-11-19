#!/usr/bin/env bash
# Arrow sweep on the H52 HUD + cluster (~2 min). No DiPlay/CarPlay route may be active.
# Each step: AMap ICON code N shown for 4 s; HUD distance = N x 100 m, cluster road name = "ICON N".
# Then roundabout exits: ICON 11 with ROUNG_ABOUT_NUM 1..4 (distance 1101..1104 m, name "RB EXIT n").
# Film the HUD and the cluster. extraservice's interpretation is logged to results/icon_sweep_log.txt.
set -u
export MSYS_NO_PATHCONV=1
ADB="${ADB:-$HOME/platform-tools-dl/platform-tools/adb.exe}"
HERE="$(cd "$(dirname "$0")" && pwd -W)"
mkdir -p "$HERE/results"
"$ADB" get-state >/dev/null 2>&1 || { echo "car not connected over adb"; exit 1; }

"$ADB" shell 'A=AUTONAVI_STANDARD_BROADCAST_SEND
logcat -c; logcat -v time -s Extra IPKServer > /data/local/tmp/sweep_log.txt & LP=$!
show() {  # icon exit distance name
  k=0; while [ $k -lt 4 ]; do
    am broadcast -a $A --ei KEY_TYPE 10001 --ei TYPE 0 --ei ICON $1 --ei ROUNG_ABOUT_NUM $2 --ei SEG_REMAIN_DIS $3 --ei ROUTE_REMAIN_DIS 9000 --ei ROUTE_REMAIN_TIME 900 --es NEXT_ROAD_NAME "$4" >/dev/null
    k=$((k+1)); sleep 1
  done
}
am broadcast -a $A --ei KEY_TYPE 10019 --ei EXTRA_STATE 8 >/dev/null
echo "$(date +%H:%M:%S) nav started"
n=1; while [ $n -le 19 ]; do
  echo "$(date +%H:%M:%S) ICON $n"; show $n 0 $((n*100)) "ICON $n"; n=$((n+1))
done
e=1; while [ $e -le 4 ]; do
  echo "$(date +%H:%M:%S) ICON 11 exit $e"; show 11 $e $((1100+e)) "RB EXIT $e"; e=$((e+1))
done
am broadcast -a $A --ei KEY_TYPE 10019 --ei EXTRA_STATE 9 >/dev/null
echo "$(date +%H:%M:%S) nav ended"
sleep 1; kill $LP' | tee "$HERE/results/icon_sweep_steps.txt"

"$ADB" pull /data/local/tmp/sweep_log.txt "$HERE/results/icon_sweep_log.txt" >/dev/null
"$ADB" shell rm /data/local/tmp/sweep_log.txt
echo "== extraservice: direction codes it sent to the HUD/cluster"
grep -a -E "guide direction|roundabout exit|navi guide info" "$HERE/results/icon_sweep_log.txt" \
  | grep -a -o -E "guide direction +:[0-9-]+|roundabout exit number :[0-9-]+|\"direction\":\"[A-Za-z_]+\"" | uniq | tr '\n' ' '; echo
