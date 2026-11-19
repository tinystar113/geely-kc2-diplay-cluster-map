package com.shilapi.xcertplay.media;

/**
 * Decides when DiPlay must resend its guidance because another sender overwrote it.
 *
 * The stock Gaode (com.autonavi.amapauto) runs in the background and, while the car moves, sends a
 * cruise-mode AUTONAVI_STANDARD_BROADCAST_SEND (KEY_TYPE 10001, CUR_ROAD_NAME = current road,
 * NEXT_ROAD_NAME null, distances 0) about once a second. IPKServer shows whichever packet came last,
 * so the cluster flickered between DiPlay's next road and the stock current road. It cannot be
 * stopped or disabled without root, so DiPlay answers each foreign guidance packet with its own.
 * DiPlay's own packets carry MARKER so they are not answered. Pure logic, unit-tested on the PC.
 */
public final class H52GuidanceEcho {
    public static final String MARKER = "DIPLAY_H52";
    public static final int KEY_GUIDANCE = 10001;

    private H52GuidanceEcho() {}

    public static boolean shouldReassert(boolean navigating, int keyType, boolean ownPacket) {
        return navigating && !ownPacket && keyType == KEY_GUIDANCE;
    }
}
