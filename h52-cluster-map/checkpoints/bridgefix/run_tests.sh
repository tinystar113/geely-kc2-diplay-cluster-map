#!/usr/bin/env bash
# Compile and run the plain-JDK unit tests for the bridge fix (no Android needed).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
JDK="C:/Users/Hasee/android-build-tools/jdk17/bin"
OUT="$HERE/out"
rm -rf "$OUT"; mkdir -p "$OUT"
"$JDK/javac.exe" -encoding UTF-8 -d "$OUT" \
  "$ROOT/java-bridge/com/shilapi/xcertplay/media/H52RouteEndPolicy.java" "$HERE/H52RouteEndPolicyTest.java"
"$JDK/java.exe" -cp "$OUT" H52RouteEndPolicyTest
