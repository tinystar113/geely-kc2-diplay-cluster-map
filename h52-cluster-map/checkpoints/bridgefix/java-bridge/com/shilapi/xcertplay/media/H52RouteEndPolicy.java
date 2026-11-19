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
}
