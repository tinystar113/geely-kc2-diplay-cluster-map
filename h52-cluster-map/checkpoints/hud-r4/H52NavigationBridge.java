package com.shilapi.xcertplay.media;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;

public final class H52NavigationBridge {
    private static final String TAG = "DiPlay-H52-Navi";
    private static final String ACTION = "AUTONAVI_STANDARD_BROADCAST_SEND";

    private Object route;
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

    // Route-end watchdog: some apps (Gaode in CarPlay) end a route without RouteGuidanceState 0/2.
    private static final long WATCHDOG_MS = 1000;
    private long lastFrameNs;
    /** A route ended and no maneuver arrived since: only a real next maneuver may start navigation. */
    private boolean endedSinceLastManeuver;
    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private final Runnable watchdog = new Runnable() {
        @Override public void run() { checkRouteEnded(); }
    };

    H52NavigationBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    synchronized void accept(Object frame) {
        if (frame == null) return;
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

            Object change = route.getClass().getMethod("accept", int.class, byte[].class)
                    .invoke(route, message, payload);

            com.shilapi.xcertplay.ui.H52Diagnostics.record(context, "NAV input=" + message + " parsed=" + change);

            if ("CLEAR".equals(String.valueOf(change))) {
                stop();
                return;
            }

            Object activeVal = getField(route, "routeActive");
            boolean routeIsActive = Boolean.TRUE.equals(activeVal);
            if (routeIsActive && !active && !endedSinceLastManeuver) {
                active = true;
                status(8);
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

                Object roadObj = get(maneuver, "getRoad");
                if (roadObj != null) {
                    String r = String.valueOf(roadObj).trim();
                    if (r.length() > 0 && !"null".equals(r)) {
                        lastNextRoad = r;
                    }
                }

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
                active = true;
                status(8);
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
        com.shilapi.xcertplay.ui.H52Diagnostics.record(context, "NAV out icon=" + lastIcon + " dis=" + segRemain + " rem=" + routeRemain + " time=" + routeTime + " cur=" + curRoad);
    }

    private boolean hasGuidanceData() {
        return lastRouteRemainDis >= 0 || lastSegRemainDis >= 0 || (lastNextRoad != null && lastNextRoad.length() > 0);
    }

    synchronized void stop() {
        watchdogHandler.removeCallbacks(watchdog);
        if (active) {
            status(9);
            active = false;
            endedSinceLastManeuver = true;
        }
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
    }

    private void status(int state) {
        com.shilapi.xcertplay.ui.H52Diagnostics.record(context, "NAV state=" + state);
        context.sendBroadcast(new Intent(ACTION)
                .putExtra("KEY_TYPE", 10019).putExtra("EXTRA_STATE", state));
        GeelyClusterMap.onNavStatus(state);              // cluster map drawing follows navigation mode
        watchdogHandler.removeCallbacks(watchdog);
        if (state == 8) watchdogHandler.postDelayed(watchdog, WATCHDOG_MS);
    }

    /** Ends the route when the phone stopped guiding without saying so (see H52RouteEndPolicy). */
    private synchronized void checkRouteEnded() {
        if (!active || route == null) return;
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

    private static Object getField(Object target, String fieldName) {
        if (target == null) return null;
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object get(Object object, String name) {
        if (object == null) return null;
        try {
            return object.getClass().getMethod(name).invoke(object);
        } catch (Exception e) {
            return null;
        }
    }

    public static int icon(int type) {
        return H52ManeuverIcons.icon(type);
    }
}
