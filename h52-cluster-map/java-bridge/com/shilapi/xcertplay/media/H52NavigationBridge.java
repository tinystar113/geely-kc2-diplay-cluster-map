package com.shilapi.xcertplay.media;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public final class H52NavigationBridge {
    private static final String TAG = "DiPlay-H52-Navi";
    private static final String ACTION = "AUTONAVI_STANDARD_BROADCAST_SEND";

    // Cache metadata only; route and maneuver values must remain live on every frame.
    // Separate Class keys preserve getDeclaredField/getMethod behavior across runtime types.
    private static final Map<Class<?>, Map<String, Field>> FIELD_CACHE =
            new HashMap<Class<?>, Map<String, Field>>();
    private static final Map<Class<?>, Map<String, Method>> METHOD_CACHE =
            new HashMap<Class<?>, Map<String, Method>>();
    private static final Class<?>[] NO_PARAMETERS = new Class<?>[0];
    private static final long DIAGNOSTIC_INTERVAL_MS = 2000;

    private Object route;
    private Method routeAcceptMethod;
    private Class<?> routeAcceptClass;
    private final DiagnosticGate inputDiagnostics = new DiagnosticGate();
    private final DiagnosticGate outputDiagnostics = new DiagnosticGate();
    private final Context context;
    private boolean active;

    private int lastSegRemainDis = -1;
    private int lastRouteRemainDis = -1;
    private int lastRouteRemainTime = -1;
    private int lastRouteAllDis = -1;
    private int lastIcon = 9;
    private boolean lastArrived = false;
    private int lastRoundaboutExit = 0;
    private String lastNextRoad = "";
    private String lastCurRoad = "";
    private final H52NextRoad nextRoad = new H52NextRoad();

    // Route-end watchdog: some apps (Gaode in CarPlay) end a route without RouteGuidanceState 0/2.
    private static final long WATCHDOG_MS = 1000;
    private long lastFrameNs;
    /** A route ended and no maneuver arrived since: only a real next maneuver may start navigation. */
    private boolean endedSinceLastManeuver;
    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private final Runnable watchdog = new Runnable() {
        @Override public void run() { checkRouteEnded(); }
    };

    /** Answers the stock Gaode's cruise packets with DiPlay's guidance (see H52GuidanceEcho). */
    private boolean echoRegistered;
    private int echoCount;
    private final BroadcastReceiver echoReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) { onForeignGuidance(intent); }
    };

    private final H52CruiseMode cruiseMode;
    private static volatile H52NavigationBridge currentBridge;
    private static volatile boolean cruiseRequested;
    private boolean closed;

    H52NavigationBridge(Context context) {
        this.context = context.getApplicationContext();
        this.cruiseMode = new H52CruiseMode(context);
        H52NavigationBridge previous = currentBridge;
        if (previous != null) previous.stopForReplacement();
        currentBridge = this;
        trace("bridge-created");
        // Preserve the requested mode across bridge replacement, not an old object's cache.
        if (cruiseRequested) enableCruise();
    }

    private void trace(String event) {
        Log.i(TAG, "probe=r7-ipk2 event=" + event + " bridge=" + System.identityHashCode(this)
                + " current=" + (currentBridge == this) + " closed=" + closed
                + " route=" + active + " requested=" + cruiseRequested
                + " cruise=" + cruiseMode.isActive() + " displayCache=" + cruiseMode.isClusterNavEnabled());
    }

    private void startNavigation(String source) {
        if (closed || currentBridge != this || active) return;
        trace("nav-start-" + source);
        cruiseMode.suspend();
        GeelyClusterMap.setCruiseMode(false);
        active = true;
        status(8);
    }

    private synchronized void onForeignGuidance(Intent intent) {
        if (closed || currentBridge != this || intent == null) return;
        int keyType = intent.getIntExtra("KEY_TYPE", -1);
        if (!H52GuidanceEcho.shouldReassert(active, keyType, intent.hasExtra(H52GuidanceEcho.MARKER))) return;
        echoCount++;
        if (echoCount == 1 || echoCount % 60 == 0) {
            com.shilapi.xcertplay.ui.H52Diagnostics.record(context, "NAV echo n=" + echoCount
                    + " foreignCur=" + intent.getStringExtra("CUR_ROAD_NAME"));
        }
        publishGuidance();
    }

    private void setEcho(boolean on) {
        if (on == echoRegistered) return;
        try {
            if (on) {
                context.registerReceiver(echoReceiver, new IntentFilter(ACTION));
                echoCount = 0;
            } else {
                context.unregisterReceiver(echoReceiver);
            }
            echoRegistered = on;
        } catch (Exception e) {
            Log.w(TAG, "echo receiver " + (on ? "register" : "unregister") + " failed", e);
        }
    }

    synchronized void accept(Object frame) {
        if (closed || currentBridge != this || frame == null) return;
        try {
            Object msgObj = get(frame, "getMessageId");
            if (!(msgObj instanceof Number)) return;
            int message = ((Number) msgObj).intValue();
            if (message != 0x5201 && message != 0x5202) return;
            lastFrameNs = System.nanoTime();

            if (route == null) {
                route = Class.forName("com.shilapi.xcertplay.hud.BydHudRouteState").newInstance();
            }

            byte[] payload = (byte[]) get(frame, "getPayload");
            if (payload == null) return;

            if (routeAcceptMethod == null || routeAcceptClass != route.getClass()) {
                routeAcceptMethod = route.getClass().getMethod("accept", int.class, byte[].class);
                routeAcceptClass = route.getClass();
            }
            Object change = routeAcceptMethod.invoke(route, message, payload);

            // Gate before formatting or opening the diagnostics file. CLEAR is never sampled.
            if (inputDiagnostics.allow(SystemClock.uptimeMillis(), "CLEAR".equals(String.valueOf(change)))) {
                com.shilapi.xcertplay.ui.H52Diagnostics.record(context,
                        "NAV input=" + message + " parsed=" + change + " n=" + inputDiagnostics.reported);
            }

            if ("CLEAR".equals(String.valueOf(change))) {
                stop();
                return;
            }

            Object activeVal = getField(route, "routeActive");
            boolean routeIsActive = Boolean.TRUE.equals(activeVal);
            if (routeIsActive && !active && !endedSinceLastManeuver) {
                startNavigation("route-state");
            }

            Object remMeters = getField(route, "remainingMeters");
            if (remMeters instanceof Number) {
                long m = ((Number) remMeters).longValue();
                if (m >= 0) {
                    int dis = (int) Math.min(Integer.MAX_VALUE, m);
                    if (lastRouteAllDis < 0 || (dis > lastRouteAllDis + 500)) {
                        lastRouteAllDis = dis;
                    }
                    lastRouteRemainDis = dis;
                }
            }

            Object remSecs = getField(route, "remainingSeconds");
            if (remSecs instanceof Number) {
                long s = ((Number) remSecs).longValue();
                if (s >= 0) {
                    lastRouteRemainTime = (int) Math.min(Integer.MAX_VALUE, s);
                }
            }

            Object distM = getField(route, "distanceMeters");
            if (distM instanceof Number) {
                long d = ((Number) distM).longValue();
                if (d >= 0) {
                    lastSegRemainDis = (int) Math.min(Integer.MAX_VALUE, d);
                }
            }

            Object curR = getField(route, "currentRoad");
            if (curR instanceof String) {
                String s = ((String) curR).trim();
                if (s.length() > 0 && !"null".equals(s)) {
                    lastCurRoad = s;
                }
            }

            Object maneuver = get(route, "currentApple");
            if (maneuver != null) endedSinceLastManeuver = false;
            if (maneuver != null) {
                Object typeObj = get(maneuver, "getType");
                if (typeObj instanceof Number) {
                    int type = ((Number) typeObj).intValue();
                    int icon = icon(type);
                    if (icon >= 0) {
                        lastIcon = icon;
                    }
                    lastArrived = isArrival(type);
                    lastRoundaboutExit = H52ManeuverIcons.roundaboutExit(type);
                }

                // Not maneuver.getRoad(): it falls back to the current road when a partial maneuver update
                // left AfterManeuverRoadName empty, which made the cluster flicker current <-> next road.
                lastNextRoad = nextRoad.update(activeIndex(), activeAfterRoad());

                Object manDist = get(maneuver, "getDistanceMeters");
                if (manDist instanceof Number) {
                    long d = ((Number) manDist).longValue();
                    if (d >= 0) lastSegRemainDis = (int) Math.min(Integer.MAX_VALUE, d);
                }

                Object manRemM = get(maneuver, "getRemainingMeters");
                if (manRemM instanceof Number) {
                    long m = ((Number) manRemM).longValue();
                    if (m >= 0) {
                        int dis = (int) Math.min(Integer.MAX_VALUE, m);
                        if (lastRouteAllDis < 0 || (dis > lastRouteAllDis + 500)) {
                            lastRouteAllDis = dis;
                        }
                        lastRouteRemainDis = dis;
                    }
                }

                Object manRemS = get(maneuver, "getRemainingSeconds");
                if (manRemS instanceof Number) {
                    long s = ((Number) manRemS).longValue();
                    if (s >= 0) lastRouteRemainTime = (int) Math.min(Integer.MAX_VALUE, s);
                }
            }

            if (!active && H52RouteEndPolicy.mayActivate(endedSinceLastManeuver, maneuver != null, routeIsActive, hasGuidanceData())) {
                startNavigation("guidance");
            }

            if (!active) return;

            publishGuidance();
        } catch (Exception e) {
            Log.w(TAG, "Guidance rejected", e);
        }
    }

    private void publishGuidance() {
        if (!hasGuidanceData()) return;

        Intent intent = new Intent(ACTION);
        intent.putExtra("KEY_TYPE", 10001);
        intent.putExtra(H52GuidanceEcho.MARKER, true);   // our own packet: the echo receiver ignores it

        String curRoad = lastCurRoad;
        if (curRoad == null || curRoad.trim().length() == 0 || "null".equals(curRoad)) {
            curRoad = "";
        }
        intent.putExtra("CUR_ROAD_NAME", curRoad);
        intent.putExtra("NEXT_ROAD_NAME", lastNextRoad != null ? lastNextRoad : "");

        int segRemain = lastSegRemainDis >= 0 ? lastSegRemainDis : 0;
        int routeRemain = lastRouteRemainDis >= 0 ? lastRouteRemainDis : 0;
        int routeTime = lastRouteRemainTime >= 0 ? lastRouteRemainTime : 0;
        int routeAll = lastRouteAllDis >= 0 ? lastRouteAllDis : routeRemain;

        intent.putExtra("SEG_REMAIN_DIS", segRemain);
        intent.putExtra("ROUTE_REMAIN_DIS", routeRemain);
        intent.putExtra("ROUTE_REMAIN_DIS_AUTO", formatAutoDistance(routeRemain));
        intent.putExtra("ROUTE_ALL_DIS", routeAll);
        intent.putExtra("ROUTE_REMAIN_TIME", routeTime);
        intent.putExtra("ROUTE_REMAIN_TIME_AUTO", formatAutoTime(routeTime));
        intent.putExtra("ICON", lastIcon >= 0 ? lastIcon : 9);
        intent.putExtra("ARRIVE_STATUS", lastArrived);
        intent.putExtra("ROUNG_ABOUT_NUM", lastRoundaboutExit);

        String roadInfoJson = "{\"path_info\":[]}";
        try {
            org.json.JSONObject seg = new org.json.JSONObject();
            seg.put("ditance", Math.max(0, segRemain));
            seg.put("time", Math.max(0, routeTime));
            seg.put("method", lastNextRoad != null ? lastNextRoad : "");
            org.json.JSONArray pathInfo = new org.json.JSONArray();
            pathInfo.put(seg);
            org.json.JSONObject roadInfo = new org.json.JSONObject();
            roadInfo.put("path_info", pathInfo);
            roadInfoJson = roadInfo.toString();
        } catch (Throwable t) {
            Log.w(TAG, "build EXTRA_ROAD_INFO failed", t);
        }
        intent.putExtra("EXTRA_ROAD_INFO", roadInfoJson);

        context.sendBroadcast(intent);
        if (outputDiagnostics.allow(SystemClock.uptimeMillis(), false)) {
            com.shilapi.xcertplay.ui.H52Diagnostics.record(context, "NAV out icon=" + lastIcon + " dis=" + segRemain + " rem=" + routeRemain + " time=" + routeTime + " cur=" + curRoad
                    + " next=" + lastNextRoad + " idx=" + activeIndex() + " n=" + outputDiagnostics.reported);
        }
    }

    private boolean hasGuidanceData() {
        return lastRouteRemainDis >= 0 || lastSegRemainDis >= 0 || (lastNextRoad != null && lastNextRoad.length() > 0);
    }

    /** Experiment: one ASCII space per road; the cluster may still trim it and show a fallback. */
    private void clearExternalGuidance() {
        Intent intent = new Intent(ACTION);
        intent.setPackage("com.neusoft.shockwave.instrumentserver");
        intent.putExtra("KEY_TYPE", 10001);
        intent.putExtra("CUR_ROAD_NAME", " ");
        intent.putExtra("NEXT_ROAD_NAME", "实时路况巡航");
        intent.putExtra("SEG_REMAIN_DIS", 0);
        intent.putExtra("ROUTE_ALL_DIS", 0);
        intent.putExtra("ROUTE_REMAIN_DIS", 0);
        intent.putExtra("ROUTE_REMAIN_TIME", 0);
        intent.putExtra("ROUTE_REMAIN_DIS_AUTO", "0米");
        intent.putExtra("ROUTE_REMAIN_TIME_AUTO", "0分钟");
        intent.putExtra("ICON", -1);
        intent.putExtra("ARRIVE_STATUS", false);
        intent.putExtra("ROUNG_ABOUT_NUM", 0);
        intent.putExtra("EXTRA_ROAD_INFO", "{\"path_info\":[]}");
        context.sendBroadcast(intent);
        com.shilapi.xcertplay.ui.H52Diagnostics.record(context, "NAV clear external target=instrumentserver");
    }

    /** Route ended, but this CarPlay session may still own the display. */
    synchronized void stop() {
        if (closed || currentBridge != this) return;
        trace("route-stop");
        watchdogHandler.removeCallbacks(watchdog);
        if (active) {
            // IPKServer stops actively notifying after state=9. Clear its cached
            // guidance while navigation is still live, and target only IPKServer
            // so the normal HUD/extraservice path is not touched.
            clearExternalGuidance();
            status(9);
            active = false;
            endedSinceLastManeuver = true;
            cruiseMode.onNavigationStopped();
            if (cruiseRequested) {
                if (cruiseMode.isActive()) cruiseMode.resume();
                else cruiseMode.enable();
                GeelyClusterMap.setCruiseMode(true);
                trace("cruise-restored");
            }
        }
        clearGuidance();
    }

    /** Session replacement: retire the old owner without losing the requested mode. */
    public synchronized void stopForReplacement() {
        close(false);
    }

    /** Real disconnect: never run the route-end cruise restoration path. */
    public synchronized void stopSession() {
        close(true);
    }

    private void close(boolean sessionEnded) {
        if (closed) return;
        trace(sessionEnded ? "session-stop" : "bridge-replaced");
        watchdogHandler.removeCallbacks(watchdog);
        setEcho(false);
        if (currentBridge == this) {
            if (sessionEnded) cruiseRequested = false;
            if (active) {
                status(9);
                active = false;
                cruiseMode.onNavigationStopped();
            }
            cruiseMode.disable();
            GeelyClusterMap.setCruiseMode(false);
            currentBridge = null;
        }
        closed = true;
        active = false;
        clearGuidance();
    }

    private void clearGuidance() {
        flushDiagnostics();
        if (route != null) {
            try { get(route, "clear"); } catch (Exception ignored) {}
        }
        lastSegRemainDis = -1;
        lastRouteRemainDis = -1;
        lastRouteRemainTime = -1;
        lastRouteAllDis = -1;
        lastIcon = 9;
        lastArrived = false;
        lastRoundaboutExit = 0;
        lastNextRoad = "";
        lastCurRoad = "";
        nextRoad.clear();
    }

    /** Request cruise, but do not steal display ownership from an active route. */
    public synchronized void enableCruise() {
        if (closed || currentBridge != this) return;
        cruiseRequested = true;
        if (active) {
            cruiseMode.requestDuringNavigation();
            GeelyClusterMap.setCruiseMode(false);
            trace("request-during-nav");
            return;
        }
        cruiseMode.enable();
        cruiseMode.resume();
        GeelyClusterMap.setCruiseMode(true);
        trace("cruise-enabled");
    }

    public synchronized void disableCruise() {
        if (closed || currentBridge != this) return;
        cruiseRequested = false;
        cruiseMode.disable();
        GeelyClusterMap.setCruiseMode(false);
        trace("cruise-disabled");
    }

    public synchronized boolean isCruiseActive() {
        return !closed && currentBridge == this && cruiseMode.isActive();
    }

    /** Activity resume may precede bridge initialization. Retain intent, not an old object. */
    public static void enableCruiseStatic() {
        try {
            cruiseRequested = true;
            H52NavigationBridge bridge = currentBridge;
            if (bridge != null) bridge.enableCruise();
            else Log.i(TAG, "probe=r7-ipk2 cruise request pending bridge");
        } catch (Exception e) {
            Log.e(TAG, "enableCruiseStatic failed", e);
        }
    }

    public static void disableCruiseStatic() {
        try {
            cruiseRequested = false;
            H52NavigationBridge bridge = currentBridge;
            if (bridge != null) bridge.disableCruise();
            else GeelyClusterMap.setCruiseMode(false);
        } catch (Exception e) {
            Log.e(TAG, "disableCruiseStatic failed", e);
        }
    }

    private int activeIndex() {
        Object idx = getField(route, "activeIndex");
        return idx instanceof Number ? ((Number) idx).intValue() : -1;
    }

    /** AfterManeuverRoadName of the active maneuver as stored, without the current-road fallback. */
    private String activeAfterRoad() {
        Object map = getField(route, "maneuvers");
        if (!(map instanceof java.util.Map)) return "";
        Object m = ((java.util.Map<?, ?>) map).get(Integer.valueOf(activeIndex()));
        Object road = get(m, "getAfterRoad");
        return road instanceof String ? (String) road : "";
    }

    private void status(int state) {
        com.shilapi.xcertplay.ui.H52Diagnostics.record(context, "NAV state=" + state);
        context.sendBroadcast(new Intent(ACTION)
                .putExtra("KEY_TYPE", 10019).putExtra("EXTRA_STATE", state));
        GeelyClusterMap.onNavStatus(state);              // cluster map drawing follows navigation mode
        setEcho(state == 8);                             // answer stock Gaode packets only while guiding
        watchdogHandler.removeCallbacks(watchdog);
        if (state == 8) watchdogHandler.postDelayed(watchdog, WATCHDOG_MS);
    }

    /** Ends the route when the phone stopped guiding without saying so (see H52RouteEndPolicy). */
    private synchronized void checkRouteEnded() {
        if (closed || currentBridge != this || !active || route == null) return;
        Object routeActive = getField(route, "routeActive");
        Object emptySince = getField(route, "emptyListSinceNs");
        String reason = H52RouteEndPolicy.endReason(Boolean.TRUE.equals(routeActive),
                emptySince instanceof Long ? (Long) emptySince : null, lastFrameNs, System.nanoTime());
        if (reason == null) {
            watchdogHandler.postDelayed(watchdog, WATCHDOG_MS);
            return;
        }
        Log.i(TAG, "route ended by watchdog: " + reason);
        com.shilapi.xcertplay.ui.H52Diagnostics.record(context, "NAV watchdog end=" + reason);
        stop();
    }

    public static String formatAutoDistance(int meters) {
        if (meters <= 0) return "0米";
        if (meters < 1000) return meters + "米";
        int tenths = (int) (meters / 100.0 + 0.5);
        return (tenths / 10) + "." + (tenths % 10) + "公里";
    }

    public static String formatAutoTime(int seconds) {
        if (seconds <= 0) return "0分钟";
        int minutes = (seconds + 30) / 60;
        if (minutes < 60) return minutes + "分钟";
        int hours = minutes / 60;
        int remainder = minutes % 60;
        if (remainder == 0) return hours + "小时";
        return hours + "小时" + remainder + "分钟";
    }

    public static boolean isArrival(int type) {
        return type == 10 || type == 12 || type == 24 || type == 25 || type == 27;
    }

    /** Accessed only under the bridge monitor, like accept/publish/clearGuidance. */
    private static final class DiagnosticGate {
        private boolean started;
        private long lastAt;
        long pending;
        long reported;

        // Explicit non-private constructor avoids a Java 7 synthetic constructor bridge.
        DiagnosticGate() {}

        boolean allow(long now, boolean force) {
            pending++;
            if (!force && started && now - lastAt < DIAGNOSTIC_INTERVAL_MS) return false;
            started = true;
            lastAt = now;
            reported = pending;
            pending = 0;
            return true;
        }

        void reset() {
            started = false;
            lastAt = 0;
            pending = 0;
            reported = 0;
        }
    }

    private void flushDiagnostics() {
        if (inputDiagnostics.pending > 0) {
            com.shilapi.xcertplay.ui.H52Diagnostics.record(context, "NAV input pending=" + inputDiagnostics.pending);
        }
        if (outputDiagnostics.pending > 0) {
            com.shilapi.xcertplay.ui.H52Diagnostics.record(context, "NAV out pending=" + outputDiagnostics.pending);
        }
        inputDiagnostics.reset();
        outputDiagnostics.reset();
    }

    private static Field cachedField(Class<?> type, String name) {
        synchronized (FIELD_CACHE) {
            Map<String, Field> fields = FIELD_CACHE.get(type);
            if (fields == null) {
                fields = new HashMap<String, Field>();
                FIELD_CACHE.put(type, fields);
            }
            if (!fields.containsKey(name)) {
                Field field = null;
                try {
                    field = type.getDeclaredField(name);
                    field.setAccessible(true);
                } catch (Exception ignored) {
                    field = null;
                }
                fields.put(name, field);  // A missing member is stable for this loaded Class too.
            }
            return fields.get(name);
        }
    }

    private static Method cachedMethod(Class<?> type, String name) {
        synchronized (METHOD_CACHE) {
            Map<String, Method> methods = METHOD_CACHE.get(type);
            if (methods == null) {
                methods = new HashMap<String, Method>();
                METHOD_CACHE.put(type, methods);
            }
            if (!methods.containsKey(name)) {
                Method method = null;
                try {
                    method = type.getMethod(name, NO_PARAMETERS);
                } catch (Exception ignored) {
                    // Preserve the old null fallback without repeating missing-member exceptions.
                }
                methods.put(name, method);
            }
            return methods.get(name);
        }
    }

    private static Object getField(Object target, String fieldName) {
        if (target == null) return null;
        try {
            Field field = cachedField(target.getClass(), fieldName);
            return field == null ? null : field.get(target);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object get(Object object, String name) {
        if (object == null) return null;
        try {
            Method method = cachedMethod(object.getClass(), name);
            return method == null ? null : method.invoke(object);
        } catch (Exception e) {
            return null;
        }
    }

    public static int icon(int type) {
        return H52ManeuverIcons.icon(type);
    }
}
