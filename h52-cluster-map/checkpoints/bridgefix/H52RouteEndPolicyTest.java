import com.shilapi.xcertplay.media.H52RouteEndPolicy;

/** Plain-JDK test (no JUnit available): java H52RouteEndPolicyTest → "ALL PASS" or exit 1. */
public class H52RouteEndPolicyTest {
    private static final long S = 1_000_000_000L;           // one second in ns
    private static int failures;

    public static void main(String[] args) {
        long now = 1000 * S;

        // Active route with fresh frames and a maneuver list: not ended.
        check("fresh guidance continues", null, H52RouteEndPolicy.endReason(true, null, now - 1 * S, now));

        // iPhone sends a brief empty list while rerouting: keep going for < 5 s.
        check("empty list 4 s keeps route", null, H52RouteEndPolicy.endReason(true, now - 4 * S, now - 1 * S, now));
        // Empty list held for 5 s or more: the app ended the route without state 0/2.
        check("empty list 5 s ends route", "empty-list", H52RouteEndPolicy.endReason(true, now - 5 * S, now - 1 * S, now));

        // Parked at a light: no route frames for 60 s is still a route (observed 48 s gaps while parked).
        check("60 s without frames keeps route", null, H52RouteEndPolicy.endReason(true, null, now - 60 * S, now));
        // No route frames for 180 s: the app stopped guiding.
        check("180 s without frames ends route", "stale", H52RouteEndPolicy.endReason(true, null, now - 180 * S, now));

        // Route state says inactive (e.g. cleared between ticks): ended.
        check("inactive route state ends route", "inactive", H52RouteEndPolicy.endReason(false, null, now - 1 * S, now));

        // No frame seen yet (lastFrameNs = 0): never "stale" from a zero timestamp.
        check("no frame yet is not stale", null, H52RouteEndPolicy.endReason(true, null, 0L, now));

        if (failures == 0) System.out.println("ALL PASS");
        else { System.out.println(failures + " FAILED"); System.exit(1); }
    }

    private static void check(String name, String expected, String actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        System.out.println((ok ? "PASS " : "FAIL ") + name + " (expected " + expected + ", got " + actual + ")");
        if (!ok) failures++;
    }
}
