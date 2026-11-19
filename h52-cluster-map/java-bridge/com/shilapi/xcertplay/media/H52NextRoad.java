package com.shilapi.xcertplay.media;

import java.util.HashMap;
import java.util.Map;

/**
 * Stable NEXT_ROAD_NAME for the cluster status bar.
 *
 * iAP2 RouteGuidanceManeuverUpdate frames can be partial: an update without AfterManeuverRoadName
 * replaces the stored maneuver with an empty road, and BydHudRouteState.roadFor() then falls back to
 * the CURRENT road. The cluster showed that as the next road, so the status bar flickered between the
 * current and the next road. This keeps the last non-empty road name per maneuver index and never
 * substitutes the current road. Pure logic (no Android) so it is unit-tested on the PC.
 */
public final class H52NextRoad {
    private final Map<Integer, String> roadByIndex = new HashMap<Integer, String>();

    /**
     * @param index     active maneuver index, or -1 when unknown
     * @param afterRoad the active maneuver's AfterManeuverRoadName as stored now (may be empty/null)
     * @return the road name to publish as NEXT_ROAD_NAME ("" when none is known for this maneuver)
     */
    public String update(int index, String afterRoad) {
        if (index < 0) return "";
        String road = clean(afterRoad);
        if (road.length() > 0) roadByIndex.put(index, road);
        String known = roadByIndex.get(index);
        return known != null ? known : "";
    }

    public void clear() {
        roadByIndex.clear();
    }

    private static String clean(String s) {
        if (s == null) return "";
        String t = s.trim();
        return "null".equals(t) ? "" : t;
    }
}
