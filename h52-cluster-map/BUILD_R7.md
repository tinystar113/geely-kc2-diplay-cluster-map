# Building H52 Cluster Map r7 - Cruise Mode

## Changes from r6

### New Files
- `java-bridge/com/shilapi/xcertplay/media/H52CruiseMode.java` - Cruise mode control
- `tests/H52CruiseModeTest.java` - Unit tests for H52CruiseMode (12 tests)
- `tests/H52CruiseIntegrationTest.java` - Integration tests (8 tests)
- `CRUISE_MODE_DESIGN.md` - Architecture and design documentation
- `BUILD_R7.md` - This file

### Modified Files
- `java/com/shilapi/xcertplay/media/GeelyClusterMap.java`
  - Added `cruiseMode` static field
  - Added `setCruiseMode(boolean)` and `isCruiseMode()` methods
  - Modified `KEEP_ALIVE` to draw when `routeActive || cruiseMode`
  - Enhanced logging to show mode (nav/cruise/idle)

- `java-bridge/com/shilapi/xcertplay/media/H52NavigationBridge.java`
  - Added `H52CruiseMode cruiseMode` field
  - Added `enableCruise()`, `disableCruise()`, `isCruiseActive()` methods
  - Modified `stop()` to resume cruise if it was active
  - Modified route activation to suspend cruise when navigation starts

### Smali Hooks Required (TODO)
- `android_tree/classes2/com/shilapi/xcertplay/CarPlayHostActivity.smali`
  - Add call to `enableCruise()` after CarPlay connection established
  - Add call to `disableCruise()` when CarPlay disconnects

## Build Instructions

### Step 1: Verify Source Files

```bash
cd C:\Users\Hasee\Desktop\Car\h52-cluster-map

# Check new files exist
ls java-bridge\com\shilapi\xcertplay\media\H52CruiseMode.java
ls tests\H52CruiseModeTest.java
ls tests\H52CruiseIntegrationTest.java

# Verify modifications
git diff java\com\shilapi\xcertplay\media\GeelyClusterMap.java
git diff java-bridge\com\shilapi\xcertplay\media\H52NavigationBridge.java
```

### Step 2: Run Tests (Optional but Recommended)

```bash
cd tests

# Compile and run H52CruiseMode unit tests
javac -cp .;..\stubs\android.jar H52CruiseModeTest.java
java H52CruiseModeTest

# Expected output: ✅ All H52CruiseMode tests passed (12/12)

# Run integration tests (requires mock setup)
# javac -cp .;..\stubs\android.jar H52CruiseIntegrationTest.java
# java H52CruiseIntegrationTest
# Expected output: ✅ All H52 Cruise Integration tests passed (8/8)
```

### Step 3: Build r7 APK

```bash
cd C:\Users\Hasee\Desktop\Car\h52-cluster-map\build

# Build with updated Java sources
python -I build_h52.py --out ..\out\DiPlay-H52-v1.6.20-cluster-map-r7.apk ^
    --java ..\java ^
    --java ..\java-bridge ^
    --native ..\native

# Expected output:
# ✓ Base APK verified
# ✓ Compiled Java sources (GeelyClusterMap + 6 bridge classes)
# ✓ Assembled classes2.dex and classes5.dex
# ✓ Added libdiplay_vfb.so
# ✓ Signed APK
# Output: ..\out\DiPlay-H52-v1.6.20-cluster-map-r7.apk
```

### Step 4: Verify Build

```bash
# Check APK structure
python -I -c "import zipfile; z=zipfile.ZipFile('../out/DiPlay-H52-v1.6.20-cluster-map-r7.apk'); print('\n'.join([e.filename for e in z.filelist if 'H52' in e.filename or 'Geely' in e.filename]))"

# Expected to include:
# - GeelyClusterMap classes in classes5/
# - H52NavigationBridge, H52CruiseMode in classes5/
# - lib/armeabi-v7a/libdiplay_vfb.so

# Verify signature
C:\Users\Hasee\android-build-tools\build-tools-34\apksigner.bat verify ^
    --print-certs ..\out\DiPlay-H52-v1.6.20-cluster-map-r7.apk

# Check file size (should be ~8.3-8.4 MB)
dir ..\out\DiPlay-H52-v1.6.20-cluster-map-r7.apk
```

### Step 5: Static Code Check

```bash
# Decompile and verify cruise mode methods exist
C:\Users\Hasee\android-build-tools\build-tools-34\dexdump.exe ^
    -d ..\out\DiPlay-H52-v1.6.20-cluster-map-r7.apk classes5.dex | ^
    grep -A5 "H52CruiseMode\|setCruiseMode\|enableCruise"

# Should show:
# - H52CruiseMode.<init>
# - H52CruiseMode.enable()
# - H52CruiseMode.disable()
# - H52CruiseMode.suspend()
# - H52CruiseMode.resume()
# - GeelyClusterMap.setCruiseMode()
# - H52NavigationBridge.enableCruise()
# - H52NavigationBridge.disableCruise()
```

## Installation on Car

### Preparation

1. **Backup current APK:**
   ```bash
   adb pull /data/app/com.shihab.diplay.repair1-*/base.apk backup-r6.apk
   ```

2. **Copy r7 APK to car:**
   ```bash
   adb push out\DiPlay-H52-v1.6.20-cluster-map-r7.apk /sdcard/
   ```

### Installation Method A: Update Install (Recommended)

Since r7 uses the same signing key as r6, it can be installed as an update:

```bash
adb install -r /sdcard/DiPlay-H52-v1.6.20-cluster-map-r7.apk

# Expected: Success
# No uninstall needed, no re-pairing needed
```

### Installation Method B: Clean Install

If update fails or you want to start fresh:

```bash
# Uninstall old version
adb uninstall com.shihab.diplay.repair1

# Install r7
adb install /sdcard/DiPlay-H52-v1.6.20-cluster-map-r7.apk

# Note: Will require re-pairing CarPlay
```

## Testing on Car

### Phase 1: Basic Cruise Mode (20 minutes)

#### Test 1.1: Cruise Enable/Disable
1. Connect CarPlay (no route)
2. Check logcat for cruise mode activation:
   ```bash
   adb logcat | grep -E "DiPlay-H52-Cruise|DiPlay-H52-ClusterMap"
   ```
3. **Expected logs:**
   ```
   DiPlay-H52-Cruise: enabling cruise mode cluster display
   DiPlay-H52-Cruise: cluster nav state=8 (started)
   DiPlay-H52-ClusterMap: cruise=true
   DiPlay-H52-ClusterMap: draws=148/10s frames=149/10s mode=cruise
   ```
4. **Verify:** Cluster shows some content (even if not perfect yet)
5. Disconnect CarPlay
6. **Expected logs:**
   ```
   DiPlay-H52-Cruise: disabling cruise mode cluster display
   DiPlay-H52-Cruise: cluster nav state=9 (stopped)
   DiPlay-H52-ClusterMap: cruise=false
   DiPlay-H52-ClusterMap: draws=0/10s frames=0/10s mode=idle
   ```

#### Test 1.2: Verify No Regression in Navigation
1. Connect CarPlay
2. Start navigation (Gaode or Apple Maps)
3. **Expected logs:**
   ```
   DiPlay-H52-Cruise: suspending cruise mode (navigation started)
   DiPlay-H52-Navi: NAV state=8
   DiPlay-H52-ClusterMap: cruise=false
   DiPlay-H52-ClusterMap: route=true
   DiPlay-H52-ClusterMap: draws=147/10s frames=320/10s mode=nav
   ```
4. **Verify:** Cluster shows navigation map correctly (same as r6)
5. **Verify:** HUD shows turn arrows
6. **Verify:** Status bar shows distance/ETA

### Phase 2: Cruise-Navigation Transitions (30 minutes)

#### Test 2.1: Cruise → Navigation → Cruise
1. Connect CarPlay (cruise active)
2. Wait 10 seconds, verify cruise drawing
3. Start navigation
4. **Expected:** Smooth transition to navigation, cluster shows nav map
5. End navigation
6. **Expected logs:**
   ```
   DiPlay-H52-Navi: NAV state=9
   DiPlay-H52-Cruise: resuming cruise mode (navigation ended)
   DiPlay-H52-Cruise: cluster nav state=8 (started)
   DiPlay-H52-ClusterMap: route=false
   DiPlay-H52-ClusterMap: cruise=true
   ```
7. **Verify:** Cluster returns to cruise mode automatically

#### Test 2.2: Multiple Cycles
1. Repeat cruise → nav → cruise 3 times
2. **Verify:** Each transition is clean
3. **Verify:** No stuck states
4. **Verify:** Logs show correct state=8/9 sequences

### Phase 3: Edge Cases (15 minutes)

#### Test 3.1: Connect During Navigation
1. Start with CarPlay disconnected
2. Start navigation on phone
3. Connect CarPlay
4. **Expected:** No cruise mode, goes straight to navigation

#### Test 3.2: Disconnect During Navigation
1. CarPlay connected with active navigation
2. Disconnect CarPlay
3. **Expected:** Navigation ends, no cruise mode
4. **Expected logs:** Only state=9, no cruise activation

#### Test 3.3: Stock Gaode Interaction
1. Enable cruise mode
2. Drive (triggers stock Gaode cruise broadcasts)
3. **Expected:** Cruise mode stays stable
4. **Expected:** No flicker (r6 echo should still work)

### Phase 4: Performance Check (10 minutes)

1. Monitor frame rates during cruise vs navigation:
   ```bash
   adb logcat | grep "draws="
   ```
2. **Expected cruise:** ~148-150 draws/10s, frames ≈ draws
3. **Expected navigation:** ~100-150 draws/10s, frames 300+/10s
4. Check network usage:
   ```bash
   adb shell cat /proc/net/dev | grep usb0
   ```
5. **Expected cruise:** ~0.8-1.0 MB/s when drawing
6. **Expected navigation:** ~0.8-1.0 MB/s when drawing

### Phase 5: Long-Term Stability (Optional, 1+ hour)

1. Leave CarPlay connected in cruise mode
2. Drive with cruise for 30+ minutes
3. Start/end navigation multiple times
4. **Verify:** No memory leaks, no crashes
5. **Verify:** Frame rates stay consistent

## Expected Behavior Summary

### Working Correctly ✅
- Cruise mode enables automatically when CarPlay connects
- Cluster displays content during cruise (without navigation)
- Navigation still works exactly like r6
- Transitions between cruise and navigation are smooth
- Cruise resumes after navigation ends
- Clean disable when CarPlay disconnects

### Known Limitations (By Design)
- Content in cruise mode: Shows whatever is on decoder surface (needs stream 0 support for proper content)
- No user preference yet: Always enabled when CarPlay connects
- Continuous drawing uses battery: Acceptable for CarPlay usage

### Issues to Watch For
- Stuck in cruise mode after disconnect → Check disableCruise() hook
- Navigation doesn't suspend cruise → Check accept() suspend logic
- Cruise doesn't resume after nav → Check stop() resume logic
- Cluster flickers → Check r6 echo still working

## Rollback Plan

If r7 has issues:

```bash
# Reinstall r6
adb install -r backup-r6.apk

# Or from out directory
adb install -r out\DiPlay-H52-v1.6.20-cluster-map-r6.apk
```

## Troubleshooting

### Issue: Cruise mode doesn't enable
**Check:**
```bash
adb logcat | grep "H52CruiseMode\|enableCruise"
```
**Likely cause:** Hook not added to CarPlayHostActivity
**Solution:** Verify smali hooks are present

### Issue: Cluster shows nothing in cruise mode
**Check:**
```bash
adb logcat | grep "draws="
```
**If draws=0:** Decoder not feeding surface, check stream 0 setup
**If draws>0:** Cluster not receiving stream, check usb0 bandwidth

### Issue: Navigation doesn't work
**Check:**
```bash
adb logcat | grep "NAV state="
```
**Should see:** state=8 when nav starts, state=9 when ends
**If stuck:** Cruise suspend/resume logic may have bug

### Issue: Crash on startup
**Check:**
```bash
adb logcat | grep -A10 "FATAL"
```
**Likely cause:** Missing H52CruiseMode class or method
**Solution:** Rebuild with --java ..\java-bridge

## Files Checklist

Before declaring r7 complete:

- [x] H52CruiseMode.java written and tested
- [x] GeelyClusterMap.java modified (cruise mode support)
- [x] H52NavigationBridge.java modified (cruise integration)
- [x] H52CruiseModeTest.java written (12 tests)
- [x] H52CruiseIntegrationTest.java written (8 tests)
- [x] CRUISE_MODE_DESIGN.md documented
- [x] BUILD_R7.md written (this file)
- [ ] CarPlayHostActivity.smali hooks added
- [ ] r7 APK built successfully
- [ ] r7 APK installed on car
- [ ] Phase 1 tests passed (basic enable/disable)
- [ ] Phase 2 tests passed (transitions)
- [ ] Phase 3 tests passed (edge cases)
- [ ] progress.md updated with r7 session notes
- [ ] Update memory: h52-cluster-hud-navigation.md with r7 status

## Next Steps After r7

1. **Stream 0 Support:** Modify decoder to send CarPlay main screen to cluster in cruise mode
2. **User Preference:** Add SharedPreferences toggle for cruise mode enable/disable
3. **Smart Enable:** Only enable cruise when car is moving (GPS-based)
4. **Power Optimization:** Reduce frame rate to 10fps in cruise mode
5. **Content Selection:** Let user choose: CarPlay screen, music art, or simple GPS view
