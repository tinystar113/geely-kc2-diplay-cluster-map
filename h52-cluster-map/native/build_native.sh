#!/usr/bin/env bash
# Build vfb_selftest (CLI) and libdiplay_vfb.so (JNI) for the H52 (armv7, Android 4.3 / API 18).
set -euo pipefail
NDK="C:/Users/Hasee/android-ndk-dl/android-ndk-r21e/toolchains/llvm/prebuilt/windows-x86_64"
CC=("$NDK/bin/clang.exe" --target=armv7a-linux-androideabi18 --sysroot="$NDK/sysroot" -O2 -Wall -Werror)
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/out"
mkdir -p "$OUT"
cd "$HERE"
LIBS=(-lEGL -lGLESv2 -landroid -ldl -llog)

"${CC[@]}" -fPIE -pie -o "$OUT/vfb_selftest" vfb_selftest.c vfb_renderer.c "${LIBS[@]}"
"${CC[@]}" -fPIC -shared -Wl,-soname,libdiplay_vfb.so -o "$OUT/libdiplay_vfb.so" vfb_jni.c vfb_renderer.c "${LIBS[@]}"
"$NDK/bin/llvm-strip.exe" --strip-unneeded "$OUT/libdiplay_vfb.so" "$OUT/vfb_selftest"
echo "built:"; ls -l "$OUT"
echo "NEEDED (libdiplay_vfb.so):"; "$NDK/bin/llvm-readelf.exe" -d "$OUT/libdiplay_vfb.so" | grep NEEDED
