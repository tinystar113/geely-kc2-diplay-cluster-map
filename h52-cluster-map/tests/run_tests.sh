#!/usr/bin/env bash
# Compile and run the plain-JDK unit tests for the bridge fixes (no Android needed).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
JDK="C:/Users/Hasee/android-build-tools/jdk17/bin"
OUT="$HERE/out"
SRC="$ROOT/java-bridge/com/shilapi/xcertplay/media"
rm -rf "$OUT"; mkdir -p "$OUT"
"$JDK/javac.exe" -encoding UTF-8 -d "$OUT" \
  "$SRC/H52RouteEndPolicy.java" "$SRC/H52ManeuverIcons.java" "$SRC/H52NextRoad.java" "$SRC/H52GuidanceEcho.java" \
  "$HERE/H52RouteEndPolicyTest.java" "$HERE/H52ManeuverIconsTest.java" "$HERE/H52NextRoadTest.java" \
  "$HERE/H52GuidanceEchoTest.java"
echo "== H52RouteEndPolicyTest"; "$JDK/java.exe" -cp "$OUT" H52RouteEndPolicyTest | tail -1
echo "== H52ManeuverIconsTest";  "$JDK/java.exe" -cp "$OUT" H52ManeuverIconsTest
echo "== H52NextRoadTest";       "$JDK/java.exe" -cp "$OUT" H52NextRoadTest
echo "== H52GuidanceEchoTest";   "$JDK/java.exe" -cp "$OUT" H52GuidanceEchoTest
