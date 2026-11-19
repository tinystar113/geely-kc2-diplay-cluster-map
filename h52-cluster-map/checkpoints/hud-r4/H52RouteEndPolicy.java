package com.shilapi.xcertplay.media;

/**
 * When an active CarPlay route should be treated as ended even though the phone never sent
 * RouteGuidanceState NoRouteSet (0) or Arrived (2) — observed with Gaode in CarPlay on the H52:
 * ending the route left the cluster in navigation mode.
 *
 * Pure logic (no Android) so it is unit-tested on the PC.
 */
public final class H52RouteEndPolicy {
    /** The phone keeps an empty maneuver list this long: route gone (brief empties happen while rerouting). */
    public static final long EMPTY_LIST_END_NS = 5_000_000_000L;
    /** No route frames this long: the phone stopped guiding (48 s gaps were observed while parked mid-route). */
    public static final long STALE_END_NS = 120_000_000_000L;

    private H52RouteEndPolicy() {}

    /**
     * @param routeActive      BydHudRouteState.routeActive
     * @param emptyListSinceNs BydHudRouteState.emptyListSinceNs (null = list not empty)
     * @param lastFrameNs      System.nanoTime() of the last 0x5201/0x5202 frame, 0 = none yet
     * @param nowNs            System.nanoTime()
     * @return null while the route continues, else the reason: "inactive", "empty-list" or "stale"
     */
    public static String endReason(boolean routeActive, Long emptyListSinceNs, long lastFrameNs, long nowNs) {
        if (!routeActive) return "inactive";
        if (emptyListSinceNs != null && nowNs - emptyListSinceNs >= EMPTY_LIST_END_NS) return "empty-list";
        if (lastFrameNs != 0L && nowNs - lastFrameNs >= STALE_END_NS) return "stale";
        return null;
    }

    /**
     * Whether an inactive bridge may start navigation (status 8).
     *
     * After a route end the phone keeps sending empty route frames; BydHudRouteState's reset
     * distanceMeters (0) then looks like guidance data, which restarted navigation 0.3 s after Gaode's
     * NoRouteSet (car log 2026-11-09 22:55:21). So once a route has ended, only a real next maneuver
     * may start the next one.
     *
     * @param endedSinceLastManeuver a route ended and no maneuver has arrived since
     * @param hasManeuver            BydHudRouteState.currentApple() != null
     * @param routeIsActive          BydHudRouteState.routeActive
     * @param hasGuidanceData        the bridge holds distance/road data
     */
    public static boolean mayActivate(boolean endedSinceLastManeuver, boolean hasManeuver,
                                      boolean routeIsActive, boolean hasGuidanceData) {
        if (hasManeuver) return true;
        if (endedSinceLastManeuver) return false;
        return routeIsActive || hasGuidanceData;
    }
}
