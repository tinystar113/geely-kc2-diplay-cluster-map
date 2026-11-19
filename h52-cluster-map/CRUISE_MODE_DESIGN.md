# H52 Cruise Mode Cluster Map - r7 Design

## Problem Statement

**Current limitation (r6):** Cluster map only displays during active CarPlay navigation (route active).

**Observed behavior:** AutoPlay (cn.manstep.phonemirrorBox) shows Gaode map on cluster during cruise mode (巡航模式) without active navigation.

**Root cause:** Cluster ECU only displays fb2 content when in navigation mode (KEY_TYPE 10019 EXTRA_STATE=8). Your r6 implementation only sends this during active route guidance.

## Solution: Cruise Mode Extension

Enable cluster map display when CarPlay is connected but NOT navigating, by sending minimal navigation broadcast without full guidance data.

### Architecture

```
CarPlay Connected (no route)
    ↓
H52NavigationBridge.enableCruise()
    ↓
H52CruiseMode.enable()
    ↓
Send: KEY_TYPE 10019, EXTRA_STATE=8 (enable cluster nav mode)
    ↓
GeelyClusterMap.setCruiseMode(true)
    ↓
KEEP_ALIVE draws at 15fps (routeActive OR cruiseMode)
    ↓
Cluster displays fb2 stream (CarPlay main screen or custom content)
```

### State Transitions

1. **CarPlay connects (no route):**
   - `H52NavigationBridge.enableCruise()`
   - Sends KEY_TYPE 10019 state=8
   - GeelyClusterMap starts drawing in cruise mode

2. **Navigation starts:**
   - `H52NavigationBridge.accept()` detects route
   - Calls `cruiseMode.suspend()`
   - Sends KEY_TYPE 10019 state=9 (end cruise nav mode)
   - Immediately sends state=8 again (start real nav mode)
   - GeelyClusterMap switches to route mode
   - Full guidance packets (KEY_TYPE 10001) sent

3. **Navigation ends:**
   - `H52NavigationBridge.stop()` called
   - Sends KEY_TYPE 10019 state=9 (end nav)
   - Calls `cruiseMode.resume()`
   - Sends KEY_TYPE 10019 state=8 again (re-enable cruise)
   - GeelyClusterMap switches back to cruise mode

4. **CarPlay disconnects:**
   - `H52NavigationBridge.disableCruise()`
   - Sends KEY_TYPE 10019 state=9
   - GeelyClusterMap stops drawing

### Key Design Decisions

#### Decision 1: Minimal Navigation Mode
**Problem:** Cluster needs nav mode for fb2 display, but we don't want HUD/status bar guidance in cruise.

**Solution:** Send ONLY KEY_TYPE 10019 (nav mode control). Do NOT send KEY_TYPE 10001 (guidance data) in cruise mode.

**Result:** Cluster displays fb2, but status bar/HUD remain in normal cruise state.

#### Decision 2: Suspend/Resume Pattern
**Problem:** Switching between cruise and navigation must be seamless.

**Solution:** 
- `suspend()`: Sends state=9 but keeps `cruiseModeActive=true`
- `resume()`: Sends state=8 if cruise still active
- Navigation naturally takes over during suspension

**Result:** Clean transitions with no stuck states.

#### Decision 3: GeelyClusterMap.setCruiseMode()
**Problem:** KEEP_ALIVE needs to know when to draw without routeActive.

**Solution:** Add `cruiseMode` flag alongside `routeActive`:
```java
boolean shouldDraw = surfaceTexture != null && hasFrame && (routeActive || cruiseMode);
```

**Result:** Drawing logic unified, clear logging shows mode.

#### Decision 4: No Off-By-Default
**Problem:** Users might not expect cruise mode.

**Solution:** Cruise mode requires explicit `enableCruise()` call from host activity. Feature is opt-in at integration level.

**Result:** Backward compatible, no unexpected behavior.

### Integration Points

#### Required Hook in CarPlayHostActivity (smali)

Location: After CarPlay connection established, before any navigation starts.

```smali
# In onCarPlayConnected() or equivalent
# Check if cluster is available and no route is active
invoke-static {p0}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->enableCruise()V

# In onCarPlayDisconnected() or equivalent
invoke-static {p0}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->disableCruise()V
```

#### What to Display in Cruise Mode

**Option A (Recommended):** Decode CarPlay main screen (stream 0) to cluster
- Shows CarPlay home, music apps, messages
- Gives driver access to full CarPlay interface on cluster
- Requires modifying decoder to support stream 0 → cluster surface

**Option B (Simple):** Static placeholder or simple GPS map
- Less useful but easier to implement
- Could show current location on basic map
- No additional decoder changes needed

### Testing Strategy

#### Phase 1: Basic Cruise Enable/Disable
1. Connect CarPlay (no route)
2. Verify KEY_TYPE 10019 state=8 sent
3. Verify GeelyClusterMap.setCruiseMode(true) called
4. Check logcat for "cruise=true"
5. Verify fb2 drawing active (draws>0 in stats)
6. Disconnect CarPlay
7. Verify state=9 sent, cruise=false

#### Phase 2: Navigation Transitions
1. Start in cruise mode
2. Start navigation in CarPlay
3. Verify cruise suspend → nav active
4. Verify cluster shows navigation map
5. End navigation
6. Verify cruise resume
7. Verify cluster shows cruise content again

#### Phase 3: Edge Cases
1. Start navigation before cruise enabled → no cruise
2. Disconnect during navigation → no cruise resume
3. Reconnect during navigation → no cruise until nav ends
4. Rapid connect/disconnect → clean state transitions

#### Phase 4: Stock Gaode Interaction
1. Enable cruise mode
2. Move car (trigger stock Gaode cruise broadcasts)
3. Verify DiPlay cruise mode stable
4. Start navigation
5. Verify r6 echo still works (no regression)

### Expected Logs

**Cruise enabled:**
```
DiPlay-H52-Cruise: enabling cruise mode cluster display
DiPlay-H52-Cruise: cluster nav state=8 (started)
DiPlay-H52-ClusterMap: cruise=true
DiPlay-H52-ClusterMap: draws=149/10s frames=150/10s mode=cruise
```

**Navigation starts (cruise suspended):**
```
DiPlay-H52-Cruise: suspending cruise mode (navigation started)
DiPlay-H52-Cruise: cluster nav state=9 (stopped)
DiPlay-H52-Navi: NAV state=8
DiPlay-H52-ClusterMap: cruise=false
DiPlay-H52-ClusterMap: route=true
DiPlay-H52-ClusterMap: draws=147/10s frames=320/10s mode=nav
```

**Navigation ends (cruise resumed):**
```
DiPlay-H52-Navi: NAV state=9
DiPlay-H52-Cruise: resuming cruise mode (navigation ended)
DiPlay-H52-Cruise: cluster nav state=8 (started)
DiPlay-H52-ClusterMap: route=false
DiPlay-H52-ClusterMap: cruise=true
DiPlay-H52-ClusterMap: draws=148/10s frames=149/10s mode=cruise
```

### Known Limitations

1. **Content in cruise mode:** Initial r7 will draw whatever is on the decoder surface. Full implementation requires stream 0 support.

2. **No user preference yet:** Cruise mode is always enabled when CarPlay connects. Future: add SharedPreferences toggle.

3. **Stock Gaode interaction:** Cruise mode sends state=8 continuously. Stock Gaode also runs and may send cruise broadcasts, but they only affect status bar (not cluster map display).

4. **Battery impact:** Continuous 15fps drawing uses more power than navigation-only mode. Acceptable for typical CarPlay usage patterns.

### Files Modified/Added in r7

**New files:**
- `java-bridge/com/shilapi/xcertplay/media/H52CruiseMode.java` (107 lines)

**Modified files:**
- `java/com/shilapi/xcertplay/media/GeelyClusterMap.java` (+15 lines: cruiseMode flag, setCruiseMode(), updated KEEP_ALIVE)
- `java-bridge/com/shilapi/xcertplay/media/H52NavigationBridge.java` (+35 lines: cruiseMode field, enableCruise/disableCruise/isCruiseActive, suspend/resume integration)
- `android_tree/classes2/com/shilapi/xcertplay/CarPlayHostActivity.smali` (TBD: hooks for enable/disable cruise)

**Test files (to be created):**
- `tests/H52CruiseModeTest.java`
- `tests/run_cruise_tests.sh`

### Success Criteria

- ✅ Cruise mode enables when CarPlay connects
- ✅ Cluster displays fb2 content without active navigation
- ✅ Smooth transition to navigation when route starts
- ✅ Smooth transition back to cruise when navigation ends
- ✅ Clean disable when CarPlay disconnects
- ✅ No regression in r6 navigation functionality
- ✅ No regression in r6 echo functionality
- ✅ No stuck navigation states
