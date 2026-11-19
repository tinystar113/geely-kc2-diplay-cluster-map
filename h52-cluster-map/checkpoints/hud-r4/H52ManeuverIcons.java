package com.shilapi.xcertplay.media;

/**
 * Apple iAP2 RouteGuidanceManeuverType -> AMap ICON code (+ ROUNG_ABOUT_NUM) for the H52 HUD and cluster.
 *
 * AMap ICON vocabulary (stock Gaode broadcast, consumed by extraservice/IPKServer):
 *  1 car, 2 left, 3 right, 4 left-front, 5 right-front, 6 left-back, 7 right-back, 8 U-turn,
 *  9 straight, 10 waypoint, 11 enter roundabout, 12 leave roundabout, 15 destination.
 * China drives on the right, so U-turns use 8 and roundabouts the counter-clockwise icons.
 * Pure logic (no Android) so it is unit-tested on the PC.
 */
public final class H52ManeuverIcons {
    public static final int CAR = 1, LEFT = 2, RIGHT = 3, LEFT_FRONT = 4, RIGHT_FRONT = 5, LEFT_BACK = 6,
            RIGHT_BACK = 7, UTURN = 8, STRAIGHT = 9, ROUNDABOUT_ENTER = 11, ROUNDABOUT_LEAVE = 12, DESTINATION = 15;

    private static final int ROUNDABOUT_EXIT_FIRST = 28, ROUNDABOUT_EXIT_LAST = 46;

    private H52ManeuverIcons() {}

    public static int icon(int type) {
        if (type >= ROUNDABOUT_EXIT_FIRST && type <= ROUNDABOUT_EXIT_LAST) return ROUNDABOUT_ENTER;
        switch (type) {
            case 1: case 20: return LEFT;
            case 2: case 21: return RIGHT;
            case 4: case 18: case 19: case 26: return UTURN;
            case 6: return ROUNDABOUT_ENTER;
            case 7: return ROUNDABOUT_LEAVE;
            case 8: return RIGHT_FRONT;                    // off-ramp (Chinese exits are on the right)
            case 10: case 12: case 24: case 25: case 27: return DESTINATION;
            case 11: return CAR;                           // proceed to the route
            case 13: case 22: case 49: case 52: return LEFT_FRONT;
            case 14: case 23: case 50: case 53: return RIGHT_FRONT;
            case 47: return LEFT_BACK;
            case 48: return RIGHT_BACK;
            default: return STRAIGHT;                      // 0, 3, 5, 9 on-ramp, 15-17 ferry, 51, unknown
        }
    }

    /** Roundabout exit number for "take exit N" maneuvers (types 28..46 = exits 1..19), else 0. */
    public static int roundaboutExit(int type) {
        return type >= ROUNDABOUT_EXIT_FIRST && type <= ROUNDABOUT_EXIT_LAST ? type - ROUNDABOUT_EXIT_FIRST + 1 : 0;
    }
}
