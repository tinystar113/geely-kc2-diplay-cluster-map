#!/system/bin/sh
# HUD / cluster probe for Geely H52 (Android 4.3). Read-only except for test broadcasts.
# Runs from the same USB trigger as the installer done.sh. Writes everything to $USB/hud_probe/.
#
# Timeline after the script starts (watch the clock on the head unit):
#   0:00 - 3:00  CAPTURE: start the STOCK Gaode navigation and run a SIMULATED route
#                (模拟导航) so arrows appear on the cluster / HUD. Logs are recorded.
#   3:00         Close / stop the stock navigation (结束导航).
#   3:30 - 4:30  FAKE TEST: script sends fake guidance. Watch the cluster and HUD.
#   4:30         Script sends "navigation ended" and finishes.

OUT="$USB/hud_probe"
mkdir -p "$OUT"
LOG="$OUT/run.txt"
echo "PROBE START $(date)" > "$LOG"

# ---------- 1. System information ----------
{
  echo "=== getprop (build) ==="; getprop ro.build.display.id; getprop ro.product.device
  echo "=== processes ==="; ps | grep -E "naviservice|instrumentserver|autonavi|amap|KC2|ecarx"
  echo "=== displays ==="; dumpsys display
  echo "=== naviservice receivers ==="; dumpsys package ecarx.naviservice
  echo "=== instrumentserver ==="; dumpsys package com.neusoft.shockwave.instrumentserver
} > "$OUT/1_system.txt" 2>&1
pm list packages > "$OUT/1_packages.txt" 2>&1
echo "system info done $(date)" >> "$LOG"

# ---------- 2. Capture stock navigation (180 s) ----------
logcat -c
logcat -v time > "$OUT/2_stock_logcat.txt" 2>&1 &
LPID=$!
sleep 180
dumpsys activity broadcasts > "$OUT/2_stock_broadcasts.txt" 2>&1
kill $LPID
echo "stock capture done $(date)" >> "$LOG"

# ---------- 3. Fake guidance test ----------
sleep 30   # time to close stock navigation
logcat -c
logcat -v time > "$OUT/3_fake_logcat.txt" 2>&1 &
LPID=$!

A=AUTONAVI_STANDARD_BROADCAST_SEND
# Navigation started (KEY_TYPE 10019, EXTRA_STATE 8)
am broadcast -a $A --ei KEY_TYPE 10019 --ei EXTRA_STATE 8 >> "$LOG" 2>&1

# 60 x 1 s guidance updates. Arrow changes every 15 s:
#   0-14 s left (2), 15-29 s right (3), 30-44 s straight (9), 45-59 s roundabout exit 2 (11)
I=0
DIST=800
while [ $I -lt 60 ]; do
  if   [ $I -lt 15 ]; then ICON=2;  RA=0
  elif [ $I -lt 30 ]; then ICON=3;  RA=0
  elif [ $I -lt 45 ]; then ICON=9;  RA=0
  else                     ICON=11; RA=2
  fi
  am broadcast -a $A \
    --ei KEY_TYPE 10001 --ei TYPE 0 \
    --ei ICON $ICON --ei NEW_ICON $ICON --ei ROUNG_ABOUT_NUM $RA \
    --ei SEG_REMAIN_DIS $DIST --ei SEG_REMAIN_TIME 60 \
    --ei ROUTE_REMAIN_DIS 5000 --ei ROUTE_REMAIN_TIME 600 --ei ROUTE_ALL_DIS 6000 \
    --es CUR_ROAD_NAME DiPlayTest --es NEXT_ROAD_NAME HudTestRoad \
    --ei CAR_DIRECTION 90 --ei LIMITED_SPEED 60 > /dev/null 2>&1
  echo "sent i=$I icon=$ICON dist=$DIST $(date)" >> "$LOG"
  DIST=$((DIST - 10))
  I=$((I + 1))
  sleep 1
done

# Navigation ended (KEY_TYPE 10019, EXTRA_STATE 9)
am broadcast -a $A --ei KEY_TYPE 10019 --ei EXTRA_STATE 9 >> "$LOG" 2>&1
sleep 3
dumpsys activity broadcasts > "$OUT/3_fake_broadcasts.txt" 2>&1
kill $LPID

echo "PROBE DONE $(date)" >> "$LOG"
exit 0
