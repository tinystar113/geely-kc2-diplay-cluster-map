import com.shilapi.xcertplay.media.H52GuidanceEcho;

/** Plain-JDK test (no JUnit available): java H52GuidanceEchoTest → "ALL PASS" or exit 1. */
public class H52GuidanceEchoTest {
    private static int failures;

    public static void main(String[] args) {
        check("stock Gaode cruise packet while navigating is answered", true,
                H52GuidanceEcho.shouldReassert(true, 10001, false));
        check("own packet is never answered (no loop)", false,
                H52GuidanceEcho.shouldReassert(true, 10001, true));
        check("not navigating: leave the stock Gaode alone", false,
                H52GuidanceEcho.shouldReassert(false, 10001, false));
        check("status packet 10019 is not answered", false,
                H52GuidanceEcho.shouldReassert(true, 10019, false));
        check("lane info 13012 is not answered", false,
                H52GuidanceEcho.shouldReassert(true, 13012, false));

        if (failures == 0) System.out.println("ALL PASS");
        else { System.out.println(failures + " FAILED"); System.exit(1); }
    }

    private static void check(String name, boolean expected, boolean actual) {
        boolean ok = expected == actual;
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) failures++;
    }
}
