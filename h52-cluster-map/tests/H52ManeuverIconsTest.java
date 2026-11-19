import com.shilapi.xcertplay.media.H52ManeuverIcons;

/** Plain-JDK test: Apple iAP2 RouteGuidanceManeuverType -> AMap ICON (+ roundabout exit) for the H52 HUD/cluster. */
public class H52ManeuverIconsTest {
    // AMap ICON codes used by the stock Gaode / extraservice / IPKServer
    static final int CAR = 1, LEFT = 2, RIGHT = 3, LEFT_FRONT = 4, RIGHT_FRONT = 5, LEFT_BACK = 6, RIGHT_BACK = 7,
            UTURN = 8, STRAIGHT = 9, WAYPOINT = 10, ROUNDABOUT_ENTER = 11, ROUNDABOUT_LEAVE = 12, DESTINATION = 15;
    private static int failures;

    public static void main(String[] args) {
        icon("0 no turn", 0, STRAIGHT);
        icon("1 left", 1, LEFT);
        icon("2 right", 2, RIGHT);
        icon("3 straight", 3, STRAIGHT);
        icon("4 U-turn", 4, UTURN);
        icon("5 follow road", 5, STRAIGHT);
        icon("6 enter roundabout", 6, ROUNDABOUT_ENTER);
        icon("7 exit roundabout", 7, ROUNDABOUT_LEAVE);
        icon("8 off-ramp: slight right, not 'waypoint'", 8, RIGHT_FRONT);
        icon("9 on-ramp: straight, not 'waypoint'", 9, STRAIGHT);
        icon("10 arrive end of navigation", 10, DESTINATION);
        icon("11 proceed to route", 11, CAR);
        icon("12 arrive at destination", 12, DESTINATION);
        icon("13 keep left", 13, LEFT_FRONT);
        icon("14 keep right", 14, RIGHT_FRONT);
        icon("15 enter ferry: straight, not 'waypoint'", 15, STRAIGHT);
        icon("16 exit ferry", 16, STRAIGHT);
        icon("17 change ferry", 17, STRAIGHT);
        icon("18 U-turn and proceed", 18, UTURN);
        icon("19 U-turn when possible: U-turn, not 'enter roundabout'", 19, UTURN);
        icon("20 left at end of road", 20, LEFT);
        icon("21 right at end of road", 21, RIGHT);
        icon("22 highway off-ramp left", 22, LEFT_FRONT);
        icon("23 highway off-ramp right", 23, RIGHT_FRONT);
        icon("24 arrive, destination on left", 24, DESTINATION);
        icon("25 arrive, destination on right", 25, DESTINATION);
        icon("26 U-turn", 26, UTURN);
        icon("27 arrive end of directions", 27, DESTINATION);
        icon("28 roundabout exit 1: enter roundabout", 28, ROUNDABOUT_ENTER);
        icon("46 roundabout exit 19: enter roundabout", 46, ROUNDABOUT_ENTER);
        icon("47 sharp left", 47, LEFT_BACK);
        icon("48 sharp right", 48, RIGHT_BACK);
        icon("49 slight left", 49, LEFT_FRONT);
        icon("50 slight right", 50, RIGHT_FRONT);
        icon("51 change highway (no side)", 51, STRAIGHT);
        icon("52 change highway left", 52, LEFT_FRONT);
        icon("53 change highway right", 53, RIGHT_FRONT);
        icon("unknown type stays straight", 99, STRAIGHT);

        exit("28 -> exit 1", 28, 1);
        exit("30 -> exit 3", 30, 3);
        exit("46 -> exit 19", 46, 19);
        exit("6 enter roundabout: no exit number", 6, 0);
        exit("1 left: no exit number", 1, 0);
        exit("27 is not a roundabout", 27, 0);
        exit("47 is not a roundabout", 47, 0);

        if (failures == 0) System.out.println("ALL PASS");
        else { System.out.println(failures + " FAILED"); System.exit(1); }
    }

    private static void icon(String name, int type, int expected) { check("icon " + name, expected, H52ManeuverIcons.icon(type)); }
    private static void exit(String name, int type, int expected) { check("exit " + name, expected, H52ManeuverIcons.roundaboutExit(type)); }

    private static void check(String name, int expected, int actual) {
        boolean ok = expected == actual;
        System.out.println((ok ? "PASS " : "FAIL ") + name + " (expected " + expected + ", got " + actual + ")");
        if (!ok) failures++;
    }
}
