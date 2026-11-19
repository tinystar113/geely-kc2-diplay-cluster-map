# H52 Cluster Map Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Revision 2 (2026-11-09):** audited; fixes C1–C3 and M1–M5 from the audit are folded in (see "Audit changes" at the end).

**Goal:** While a CarPlay route is active, show CarPlay's instrument-cluster map (AirPlay stream type 111) on the Geely H52 instrument cluster, the way the stock Gaode shows its map there.

**Architecture:** DiPlay advertises a second CarPlay display (type 111, `maps:/car/instrumentcluster/map`). Its decoder output goes to a `SurfaceTexture` owned by a new `GeelyClusterMap` class; a small JNI library opens the cluster surface through the car's own `/system/lib/libexternalsurfacehelper.so` and draws frames onto it with EGL/GLES2. The car's `screencap_neu` streams the cluster surface (MJPEG) only while frames are being swapped, so `GeelyClusterMap` re-draws the latest frame at a fixed 15 fps **while a route is active** and stops drawing when it ends (cluster returns to its status bar within ~1 s). Three smali hooks in `CarPlayHostActivity` route the existing (BYD-only) cluster path to `GeelyClusterMap`; one hook in `H52NavigationBridge` reports route start/stop.

**Tech Stack:** C (NDK r21e, `armv7a-linux-androideabi18`), Java 7 source level against `android-18/android.jar`, smali/baksmali 3.0.9, Android build-tools 34 (`d8`, `zipalign`, `apksigner`, `aapt`), Python 3 build script, `adb`.

**Spec:** This plan's evidence base (no separate spec document):
- Verified findings: `C:\Users\Hasee\.claude\projects\C--Users-Hasee-Desktop-Car\memory\h52-cluster-hud-navigation.md`
- Working proof of concept: `C:\Users\Hasee\Desktop\Car\hud-probe\vfbtest\vfbtest.c` (user saw colour bars on the cluster; stream ~0.5–1 MB/s while swapping, off ~1 s after swaps stop)
- Baseline build: `C:\Users\Hasee\Desktop\Car\c7a-r2-source\source.zip` (`gkui12-c7a-r2-20261028`, SHA-256 `9531cdb9…6bbf4`) + `C:\Users\Hasee\Desktop\Car\DiPlay-H52-Slim-v1.6.20-C7A-R2-BT-native-lease.apk` (SHA-256 `7bb68250…234ddb`)
- Readable reference for classes2 smali (same line numbers): `C:\Users\Hasee\Desktop\Car\DiPlay-Geely-Android43-main\common\src\main\java\com\shilapi\xcertplay\CarPlayHostActivity.kt` — cluster path `:790-919`, `attachSurface` `:4038-4045`, renderer creation `:3695-3700`
- User decision: map on the cluster **only while a CarPlay route is active**.

## Global Constraints

- Target device: Android 4.3 / API 18, `armeabi-v7a` only; package `com.shihab.diplay.repair1`.
- Cluster surface: 1920×720, native visual/format 4 (RGB565), obtained only via `_Z31getExternalDisplayNativeSurfacePP13ANativeWindow` in `/system/lib/libexternalsurfacehelper.so`, **at most once per process**.
- **Never call** `_Z19releaseNativeWindowP13ANativeWindow` (segfaults on this firmware) and **never call `eglTerminate`** (the app's UI shares `EGL_DEFAULT_DISPLAY`). Stop = destroy EGL surface + context only.
- Cluster stream request: `CarPlayClusterDisplay.config(1920, 720, 83, 0, 0, Content.MAP)` (→ 1600×600) unless Task 2's calibration (Step 5) gives a different safe area.
- **The DiPlay pref `KEY_CLUSTER_MAP` must be `true` whenever the cluster stream is advertised**; otherwise `attachSurface()` decodes stream 111 onto the main surface (audit C1).
- Keep-alive redraw rate while a route is active: 15 fps. Drawing stops within 1 s of route end.
- All `SurfaceTexture` work (`updateTexImage`, `getTransformMatrix`, release) runs on the render thread; frame-available callbacks only post to it (API 18 delivers them on an arbitrary thread).
- Build output changes only `classes2.dex`, `classes5.dex`, `assets/build_info.txt` and adds `lib/armeabi-v7a/libdiplay_vfb.so`; every other APK entry stays byte-identical to the released APK.
- `assets/build_info.txt` = `v1.6.20+1109-C7A-R2-cluster-map`.
- The rebuilt APK is signed with a new local key; installing it requires uninstalling the current DiPlay (wipes its data and pairing). Confirm with the user immediately before the uninstall.
- Existing navigation status bar / HUD path (`H52NavigationBridge` broadcasts) must keep working unchanged.
- Tools live outside the project: `C:\Users\Hasee\android-ndk-dl\`, `C:\Users\Hasee\platform-tools-dl\`, new ones under `C:\Users\Hasee\android-build-tools\`. In Git Bash, prefix `adb` calls with `MSYS_NO_PATHCONV=1` and give `adb.exe` Windows-style paths (`pwd -W`).
- No git repo exists here; "checkpoint" steps copy the working tree to `C:\Users\Hasee\Desktop\Car\h52-cluster-map\checkpoints\<task>`.

## Review Focus

1. **Static map (parked / long straight road) while routing:** cluster keeps showing the map, no flicker back to the status bar. → Task 3, Step 3 (keep-alive) and Task 5, Step 4.
2. **Main screen surface re-attached during a session (H52 re-attaches on every `surfaceChanged`):** main CarPlay video stays correct; stream 111 never lands on the main surface. → Task 5, Step 4.
3. **App inside CarPlay sends no stream-111 content (e.g. Gaode may not support the cluster map), or a route ends:** cluster shows its normal status bar, never a black or frozen map for > 2 s. → Task 5, Steps 5 and 6.
4. **CarPlay disconnects mid-route / activity recreated:** drawing stops, no crash; reconnect or a new activity brings the map back without restarting DiPlay. → Task 5, Step 6.
5. **Re-signed APK loses OEM features (no-root USB/hotspot bridge via `usbprotect`):** features work as in the released build. → Task 5, Step 8.

---

### Task 1: Reproducible rebuild of the released APK

**Files:**
- Create: `C:\Users\Hasee\Desktop\Car\h52-cluster-map\build\build_h52.py`
- Create (extracted): `C:\Users\Hasee\Desktop\Car\h52-cluster-map\android_tree\classes2\…`, `…\classes5\…` from `source.zip`

**Interfaces:**
- Produces: `python -I build_h52.py [--native <dir-with-libdiplay_vfb.so>] [--java <dir-of-.java>] --out <apk>`; compiles listed Java into classes5 (javac `-source 7 -target 7`, bootclasspath android-18 jar, d8 `--min-api 18`, baksmali into `android_tree/classes5`), assembles classes2/classes5 with smali 3.0.9 `--api 18`, swaps them into a copy of the released APK, writes `build_info.txt`, adds the native lib, zipaligns, signs with `C:\Users\Hasee\android-build-tools\diplay-local.keystore`, then asserts the changed-entries set equals the Global Constraints list.

- [ ] **Step 1: Install tools** — Temurin JDK 17 zip, build-tools 34.0.0 zip, `platform-18` zip (for `android.jar`), `smali-3.0.9-fat.jar` + `baksmali-3.0.9-fat.jar`, into `C:\Users\Hasee\android-build-tools\`; verify with `java -version`, `aapt version`.
- [ ] **Step 2: Generate the signing key** `diplay-local.keystore` (alias `diplay`, RSA 2048, 30-year validity) with `keytool`.
- [ ] **Step 3: Write `build_h52.py`** per the Interfaces block (model on the author's `build/build.py`, Windows paths).
- [ ] **Step 4: Verify a no-change rebuild.** Run with no `--java`/`--native`. Expected: changed entries ⊆ {`classes2.dex`, `classes5.dex`, `assets/build_info.txt`}; baksmali of the rebuilt `classes2.dex`/`classes5.dex` equals baksmali of the released APK's (file-for-file text compare). `aapt dump badging` shows `package: name='com.shihab.diplay.repair1'` and `sdkVersion:'18'`.
- [ ] **Step 5: Checkpoint.**

### Task 2: Native cluster renderer `libdiplay_vfb.so` + calibration

**Files:**
- Create: `C:\Users\Hasee\Desktop\Car\h52-cluster-map\native\vfb_renderer.c` (core), `vfb_jni.c` (JNI), `vfb_selftest.c` (CLI), `build_native.sh`

**Interfaces:**
- Produces (JNI, class `com.shilapi.xcertplay.media.GeelyClusterMap`):
  - `static native int nativeOpen()` → GL texture name for a `GL_TEXTURE_EXTERNAL_OES` texture (>0), or ≤0 on failure; obtains the helper window on first call only (cached in a static), creates EGL display/config (visual 4)/window surface/GLES2 context, makes it current on the calling thread.
  - `static native boolean nativeDraw(int texture, float[] texMatrix)` (JNI `(I[F)Z`; ruling in ledger) → draws the external texture full-screen (1920×720) with the 4×4 SurfaceTexture transform, `eglSwapBuffers`; false if not open.
  - `static native void nativeClose()` → unbind, destroy EGL surface + context (keep window; no release, no `eglTerminate`).
- Produces (CLI): `vfb_selftest <mode> <seconds>`; `mode=cycle` runs open→bars→close twice; `mode=grid` draws a calibration grid (lines every 10 % of width/height, each cell labelled `x%,y%` by colour code, border 4 px red).

- [ ] **Step 1: Write `vfb_selftest.c`** (`cycle` prints `cycle 1 ok` / `cycle 2 ok`; `grid` holds the grid for N s).
- [ ] **Step 2: Build and run `cycle` on the car.** Expected before the core exists: link failure.
- [ ] **Step 3: Implement `vfb_renderer.c` and `vfb_jni.c`** (EGL/GLES2 from `vfbtest.c`; OES shader with `#extension GL_OES_EGL_image_external : require`; `RegisterNatives` in `JNI_OnLoad`). Build `libdiplay_vfb.so` (`-shared -fPIC`, link `-lEGL -lGLESv2 -landroid -ldl -llog`).
- [ ] **Step 4: Rerun `cycle`.** Expected: both cycles `ok`, exit code 0, no `Segmentation fault`; usb0 tx > 300000 B/s during each draw window and < 5000 B/s in the 3 s gap; user confirms bars twice. `llvm-readelf -d libdiplay_vfb.so` NEEDED lists only system libs.
- [ ] **Step 5: Calibration (audit M4).** Run `grid 60`; user reports (photo) which grid cells are visible and which are covered by gauges/readouts. Record the visible rectangle and clear centre area in percent in `native\CALIBRATION.md`; derive the safe area for Task 4 (replacing BYD's `SAFE_AREA_PERCENT` 16/25/35/36 for this car).
- [ ] **Step 6: Checkpoint.**

### Task 3: `GeelyClusterMap` Java class + decode-to-texture spike

**Files:**
- Create: `C:\Users\Hasee\Desktop\Car\h52-cluster-map\java\com\shilapi\xcertplay\media\GeelyClusterMap.java`
- Create: `C:\Users\Hasee\Desktop\Car\h52-cluster-map\spike\DecodeSpike.java` (test-only, not shipped)

**Interfaces:**
- Consumes: Task 2 natives.
- Produces (all static, thread-safe):
  - `boolean available(android.content.Context ctx)` — true iff `/system/lib/libexternalsurfacehelper.so` exists **and** `System.loadLibrary("diplay_vfb")` succeeds; on the first `true` per install, writes DiPlay pref `KEY_CLUSTER_MAP=true` (via `AirPlayPersistence.INSTANCE.saveClusterMapEnabled(ctx, true)`, reflection) and a marker pref `geely_cluster_map_initialized=true` so a later manual disable sticks; returns false if the cluster pref was later set false, **or if the file `/sdcard/diplay_no_cluster_map` exists** (off switch: `adb shell touch /sdcard/diplay_no_cluster_map`, takes effect on the next CarPlay connection).
  - `void start(android.app.Activity host)` — idempotent; if `host` differs from the stored one, re-links (audit M3). Starts `HandlerThread("diplay-cluster-vfb")`; on it: `nativeOpen()`, `new SurfaceTexture(tex)`, `new Surface(st)`; `setOnFrameAvailableListener` → **post** to the render handler (audit M2) which calls `updateTexImage` + `getTransformMatrix` and marks a frame available; hands the `Surface` to the host by invoking its private `onClusterSurface(android.view.Surface)` via reflection on the UI thread.
  - `void onNavStatus(int state)` — 8 → `setRouteActive(true)`, 9 → `setRouteActive(false)`, anything else ignored (called from `H52NavigationBridge.status`).
  - `void setRouteActive(boolean active)` — while true **and** at least one frame has arrived, the render thread calls `nativeDraw(tex, lastMatrix)` every 66 ms (15 fps keep-alive, audit C2/M1); when false, no draws (cluster stream stops ~1 s later).
  - `void stop(android.app.Activity host)` — only if `host` is the stored host: `onClusterSurface(null)` on the UI thread, then on the render thread release `Surface`/`SurfaceTexture`, `nativeClose()`, quit thread.
  - Log tag `DiPlay-H52-ClusterMap`; logs `available=<b>`, `open tex=<n>`, `first frame`, `route=<b>`, `draws=<n>/10s frames=<n>/10s`, `closed`.

- [ ] **Step 1: Decode-to-texture spike (audit C3) — before any DiPlay hooks.** `DecodeSpike.java` (built into a throw-away debug APK `com.diplay.spike` with the same native lib): plays `C:\Users\Hasee\Desktop\Car\DiPlay-Geely-Android43-main\mobile\src\debug\assets\compat-probe\video-smoke.avcc` (existing H.264 sample) through `MediaCodec` (`video/avc`, the same decoder name the main stream uses per `DiPlay-H52-CodecTiming` log) into a `SurfaceTexture` from `nativeOpen()`, drawing with `nativeDraw`. Expected: user sees the sample video on the cluster; logcat shows ≥ 20 decoded frames/s and no `MediaCodec` error. **If this fails, stop and re-plan** (fallback options: decode to ByteBuffer + `glTexImage2D` YUV→RGB shader; lower stream to 67 %).
- [ ] **Step 2: Write `GeelyClusterMap.java`** per Interfaces (Java 7: no lambdas).
- [ ] **Step 3: Unit-level check of the keep-alive on the car** using the spike APK: feed 1 frame then stop the decoder with `setRouteActive(true)` → usb0 tx stays > 300000 B/s for 20 s; `setRouteActive(false)` → < 5000 B/s within 2 s.
- [ ] **Step 4: Build with `build_h52.py --java … --native …`** (no smali hooks yet). Expected: changed-entries assertion passes with `lib/armeabi-v7a/libdiplay_vfb.so` added; baksmali output contains `com/shilapi/xcertplay/media/GeelyClusterMap.smali`.
- [ ] **Step 5: Checkpoint.**

### Task 4: Hooks in `CarPlayHostActivity` and `H52NavigationBridge`

**Files:**
- Modify: `…\android_tree\classes2\com\shilapi\xcertplay\CarPlayHostActivity.smali` — `clusterDisplayConfig()`, `ensureClusterPresentation()`, `dismissClusterPresentation()` (Kotlin reference lines 897, 802, 875).
- Modify: `…\android_tree\classes5\com\shilapi\xcertplay\media\H52NavigationBridge.smali` — at the entry of `status(I)V` insert `invoke-static {p1}, Lcom/shilapi/xcertplay/media/GeelyClusterMap;->onNavStatus(I)V`. (Do **not** recompile `H52NavigationBridge.java`: it depends on classes whose helper jars are not in the source archive.) `GeelyClusterMap.onNavStatus(int state)` maps 8 → `setRouteActive(true)`, 9 → `setRouteActive(false)`, others ignored.

**Interfaces:**
- Consumes: `GeelyClusterMap.available(Context)/start(Activity)/stop(Activity)/onNavStatus(int)`; `CarPlayClusterDisplay.INSTANCE.config(IIIIILcom/shilapi/xcertplay/airplay/CarPlayClusterDisplay$Content;)Lcom/shilapi/xcertplay/airplay/AirPlayDisplayConfig;`, `CarPlayClusterDisplay$Content.MAP`.
- Hooks (inserted at method entry; use only `v0`–`v6`, which every hooked method's `.registers` count leaves free below the parameter registers — verify per method):
  - `clusterDisplayConfig`: if `available(this)` → return `config(1920, 720, 83, 0, 1, MAP)` (vertical step +1 from Task 2 calibration, nativeCALIBRATION.md).
  - `ensureClusterPresentation`: if `available(this)` → `start(this)`; return.
  - `dismissClusterPresentation`: if `available(this)` → `stop(this)`; then continue the original body.
- Because `available()` sets `KEY_CLUSTER_MAP=true`, the unmodified `attachSurface()` routes stream 111 to `clusterSurface` (audit C1).

- [ ] **Step 1: Apply the three smali hooks and the bridge change; rebuild.**
- [ ] **Step 2: Static check.** baksmali the built `classes2.dex`; each hooked method's first instructions call `GeelyClusterMap->available`; `.registers` unchanged or increased consistently.
- [ ] **Step 3: Checkpoint.**

### Task 5: Install and verify on the car

- [ ] **Step 1: Confirm with the user, then install.** `adb uninstall com.shihab.diplay.repair1`; `adb install` the new APK; user forgets/re-pairs the car on the iPhone and restores DiPlay settings. Expected log: `DiPlay-H52-ClusterMap: available=true`.
- [ ] **Step 2: Advertise check.** Connect CarPlay. Expected log: `airplay /info displays=[…type=110…, …type=111…]`, a SETUP for stream type 111, and `Cluster map:` no `no cluster projection display` line.
- [ ] **Step 3: No route.** Cluster shows its normal view; usb0 tx < 5000 B/s.
- [ ] **Step 4: Apple Maps route** (Review Focus 1, 2). Expected: `route=true`, `first frame`, usb0 tx 300 KB/s–1 MB/s continuously for 2 min parked; user sees the CarPlay map on the cluster without flicker; status bar + HUD still show guidance; main CarPlay screen correct after switching DiPlay to background and back (forces surface re-attach); `DiPlay-VideoStats` main stream ≥ 25 fps.
- [ ] **Step 5: Gaode-in-CarPlay route** (Review Focus 3). Record whether stream 111 carries content; expected either the map on the cluster or the normal status bar — never black/frozen for > 2 s.
- [ ] **Step 6: Route end, disconnect, reconnect** (Review Focus 3, 4). End route → cluster normal within 2 s. Start route, turn iPhone Wi-Fi off → cluster normal within 2 s, no crash; reconnect + route → map returns.
- [ ] **Step 7: Stock Gaode overlap.** Start stock Gaode navigation while the CarPlay cluster map shows → no `FATAL`/`SIGSEGV` for either pid.
- [ ] **Step 8: OEM features after re-signing** (Review Focus 5). USB wired connection and the 5 GHz hotspot entry work as in the released build.
- [ ] **Step 9: Off switch (audit M5).** `adb shell touch /sdcard/diplay_no_cluster_map`, reconnect CarPlay → `available=false`, `/info` lists only type 110, main screen correct. `adb shell rm /sdcard/diplay_no_cluster_map`, reconnect → map back. Note: with the flag set, `KEY_CLUSTER_MAP` stays true, so `clusterDisplayConfig` must also return null via the original path (`findDisplay` → null) — verify `/info` has no type 111, otherwise C1 recurs.
- [ ] **Step 10: Checkpoint** and update the memory note with results.

---

## Audit changes (revision 1 → 2)

- **C1** `attachSurface()` sends stream 111 to the main surface when `KEY_CLUSTER_MAP` is false → `available()` now sets the pref true; Global Constraint added.
- **C2** cluster stream stops ~1 s after the last swap, CarPlay sends no frames for a static picture → 15 fps keep-alive redraw while routing.
- **C3** H52 build avoids `SurfaceTexture` for the main decoder on API 18 → decode-to-texture spike is now Task 3 Step 1, with a stop-and-re-plan gate.
- **M1** user decision: map only while a route is active → `setRouteActive` driven by `H52NavigationBridge.status(8/9)` via a one-line smali call to `GeelyClusterMap.onNavStatus` (recompiling the bridge's Java is not possible without the missing helper jars).
- **M2** API 18 frame callbacks on arbitrary thread → post to render thread.
- **M3** activity recreation → `start()` re-links, `stop()` only for the current host.
- **M4** visible cluster area unknown → calibration grid (Task 2 Step 5).
- **M5** no UI toggle on this car → file-flag off switch `/sdcard/diplay_no_cluster_map` (adb-controllable, no rebuild), verified in Task 5 Step 9.
- Risks added to verification: re-signing vs OEM features (Task 5 Step 8), data wipe on uninstall (Task 5 Step 1).
