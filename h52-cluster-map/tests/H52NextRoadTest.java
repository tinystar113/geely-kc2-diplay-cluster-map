import com.shilapi.xcertplay.media.H52NextRoad;

/** Plain-JDK test (no JUnit available): java H52NextRoadTest → "ALL PASS" or exit 1. */
public class H52NextRoadTest {
    private static int failures;

    public static void main(String[] args) {
        H52NextRoad r = new H52NextRoad();

        check("full update gives the next road", "大石路", r.update(0, "大石路"));
        // Partial maneuver update (no road field) must not switch to anything else.
        check("partial update keeps the next road", "大石路", r.update(0, ""));
        check("null road keeps the next road", "大石路", r.update(0, null));
        check("\"null\" string keeps the next road", "大石路", r.update(0, "null"));
        check("whitespace keeps the next road", "大石路", r.update(0, "  "));

        // Next maneuver becomes active.
        check("new maneuver without a name is empty, not the old road", "", r.update(1, ""));
        check("new maneuver name arrives", "通惠大道", r.update(1, "通惠大道"));
        check("same maneuver renamed (reroute)", "大石支路", r.update(1, "大石支路"));
        check("back to an earlier index uses its own name", "大石路", r.update(0, ""));

        check("unknown index is empty", "", r.update(-1, "X"));

        r.clear();
        check("after clear, old names are gone", "", r.update(0, ""));

        if (failures == 0) System.out.println("ALL PASS");
        else { System.out.println(failures + " FAILED"); System.exit(1); }
    }

    private static void check(String name, String expected, String actual) {
        boolean ok = expected.equals(actual);
        System.out.println((ok ? "PASS " : "FAIL ") + name + " (expected '" + expected + "', got '" + actual + "')");
        if (!ok) failures++;
    }
}
