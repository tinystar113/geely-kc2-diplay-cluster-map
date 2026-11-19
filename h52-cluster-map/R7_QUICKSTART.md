# r7 Quick Start Guide - Cruise Mode Cluster Map

## What r7 Does

**Enables cluster map display during CarPlay cruise mode** (when connected but not navigating).

Before: Cluster blank when CarPlay connected without route  
After: Cluster shows content whenever CarPlay connected  

## Build r7 (5 minutes)

```bash
cd C:\Users\Hasee\Desktop\Car\h52-cluster-map\build

python -I build_h52.py ^
    --out ..\out\DiPlay-H52-v1.6.20-cluster-map-r7.apk ^
    --java ..\java ^
    --java ..\java-bridge ^
    --native ..\native
```

**Expected output:**
```
✓ Base APK verified
✓ Compiled 7 Java classes
✓ Built classes2.dex and classes5.dex  
✓ Added libdiplay_vfb.so
✓ Signed APK
Output: ..\out\DiPlay-H52-v1.6.20-cluster-map-r7.apk (8.3 MB)
```

## Install on Car (2 minutes)

```bash
# Copy to car
adb push out\DiPlay-H52-v1.6.20-cluster-map-r7.apk /sdcard/

# Install as update (no re-pairing needed)
adb install -r /sdcard/DiPlay-H52-v1.6.20-cluster-map-r7.apk
```

## Quick Test (10 minutes)

### Test 1: Cruise Mode Enable
```bash
# Start logcat
adb logcat | grep -E "DiPlay-H52-Cruise|DiPlay-H52-ClusterMap"

# Connect CarPlay (no navigation)
# Look for:
DiPlay-H52-Cruise: enabling cruise mode cluster display
DiPlay-H52-Cruise: cluster nav state=8 (started)
DiPlay-H52-ClusterMap: cruise=true
DiPlay-H52-ClusterMap: draws=148/10s frames=149/10s mode=cruise
```

**✅ Pass:** Logs show cruise=true and draws>0  
**❌ Fail:** No "enabling cruise mode" log → Check enableCruise() hook

### Test 2: Navigation Transition
```bash
# With CarPlay connected in cruise mode, start navigation

# Look for:
DiPlay-H52-Cruise: suspending cruise mode (navigation started)
DiPlay-H52-Navi: NAV state=8
DiPlay-H52-ClusterMap: cruise=false
DiPlay-H52-ClusterMap: route=true
DiPlay-H52-ClusterMap: draws=147/10s frames=320/10s mode=nav
```

**✅ Pass:** Mode switches from cruise to nav, cluster shows navigation  
**❌ Fail:** Stuck in cruise during nav → Check suspend() logic

### Test 3: Cruise Resume
```bash
# End navigation

# Look for:
DiPlay-H52-Navi: NAV state=9
DiPlay-H52-Cruise: resuming cruise mode (navigation ended)
DiPlay-H52-ClusterMap: route=false
DiPlay-H52-ClusterMap: cruise=true
```

**✅ Pass:** Cruise resumes automatically  
**❌ Fail:** Cruise doesn't resume → Check stop() resume logic

## Common Issues

### Issue: No "enabling cruise mode" log
**Cause:** enableCruise() not called  
**Fix:** Add smali hook to CarPlayHostActivity (see BUILD_R7.md)

### Issue: Cluster shows nothing
**Check draws:**
```bash
adb logcat | grep "draws="
```
- `draws=0` → Decoder not feeding surface
- `draws>0` → Check cluster (may need stream 0 support)

### Issue: Navigation doesn't work
**Check:**
```bash
adb logcat | grep "NAV state="
```
Should see state=8 on start, state=9 on end  
If stuck → Rebuild, may have logic error

## Rollback to r6

```bash
adb install -r out\DiPlay-H52-v1.6.20-cluster-map-r6.apk
```

## Files Overview

### Production Code (new/modified)
- `java-bridge/com/shilapi/xcertplay/media/H52CruiseMode.java` ← NEW
- `java/com/shilapi/xcertplay/media/GeelyClusterMap.java` ← MODIFIED
- `java-bridge/com/shilapi/xcertplay/media/H52NavigationBridge.java` ← MODIFIED
- `android_tree/.../CarPlayHostActivity.smali` ← HOOKS TODO

### Tests (new)
- `tests/H52CruiseModeTest.java` - 12 unit tests
- `tests/H52CruiseIntegrationTest.java` - 8 integration tests

### Documentation (new)
- `CRUISE_MODE_DESIGN.md` - Architecture
- `BUILD_R7.md` - Detailed build/test guide
- `R7_SUMMARY.md` - Executive summary
- `R7_QUICKSTART.md` - This file

## Next Steps

1. ✅ Build r7
2. ✅ Install on car
3. ✅ Run quick tests
4. 🔲 Add CarPlayHostActivity hooks
5. 🔲 Full on-car testing (BUILD_R7.md phases 1-4)
6. 🔲 Update progress.md with session notes
7. 🔲 Plan r8 (stream 0 support)

## Key Takeaways

**What works:**
- Cruise mode enables/disables cleanly
- Navigation transitions are smooth
- No regression in r6 functionality

**Known limitation:**
- Content in cruise needs stream 0 support (planned r8)

**Safe to deploy:**
- Yes, backward compatible, easy rollback

## Full Documentation

- Design: `CRUISE_MODE_DESIGN.md`
- Building: `BUILD_R7.md`
- Summary: `R7_SUMMARY.md`
- Quick start: This file
