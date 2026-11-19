package com.shilapi.xcertplay.media;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Cruise mode cluster display for H52.
 *
 * Enables cluster map display when CarPlay is connected but not navigating. Sends minimal
 * navigation broadcast (KEY_TYPE 10019 state=8) to request the cluster's fb2 display mode.
 * No KEY_TYPE 10001 guidance packets are fabricated. Actual visibility, status bar and HUD
 * effects must be verified on the vehicle; a submitted broadcast is not an acknowledgement.
 *
 * The tested H52 path displays fb2 content in navigation mode. Cruise reuses that display
 * state without an active route. Both extraservice and the separate IPKServer must be
 * eligible to receive status broadcasts, as they are during normal navigation.
 */
public final class H52CruiseMode {
    private static final String TAG = "DiPlay-H52-Cruise";
    private static final String ACTION = "AUTONAVI_STANDARD_BROADCAST_SEND";

    private final Context context;
    private boolean cruiseModeActive = false;
    private boolean clusterNavEnabled = false;

    public H52CruiseMode(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Enable cruise mode cluster display.
     * Sends KEY_TYPE 10019 state=8 to enable cluster fb2 display without starting full navigation.
     */
    public synchronized void enable() {
        if (cruiseModeActive) return;

        Log.i(TAG, "enabling cruise mode cluster display");
        cruiseModeActive = true;

        // Enable cluster navigation mode (allows fb2 display)
        if (!clusterNavEnabled) {
            sendClusterNavState(8);
            clusterNavEnabled = true;
        }
    }

    /**
     * Disable cruise mode cluster display.
     * Sends KEY_TYPE 10019 state=9 to return cluster to normal mode.
     */
    public synchronized void disable() {
        if (!cruiseModeActive) return;

        Log.i(TAG, "disabling cruise mode cluster display");
        cruiseModeActive = false;

        if (clusterNavEnabled) {
            sendClusterNavState(9);
            clusterNavEnabled = false;
        }
    }

    /**
     * Suspend cruise mode temporarily (e.g., when real navigation starts).
     * Keeps cruise state but disables cluster nav mode.
     */
    public synchronized void suspend() {
        if (!cruiseModeActive) return;

        Log.i(TAG, "suspending cruise mode (navigation started)");

        if (clusterNavEnabled) {
            sendClusterNavState(9);
            clusterNavEnabled = false;
        }
    }

    /**
     * Resume cruise mode after suspension (e.g., when navigation ends).
     * Re-enables cluster nav mode if cruise is still active.
     */
    public synchronized void resume() {
        if (!cruiseModeActive) return;

        Log.i(TAG, "resuming cruise mode (navigation ended)");

        if (!clusterNavEnabled) {
            sendClusterNavState(8);
            clusterNavEnabled = true;
        }
    }

    /** The navigation owner sent state 9; our old display cache is no longer valid. */
    public synchronized void onNavigationStopped() {
        clusterNavEnabled = false;
    }

    /** Remember cruise intent while navigation owns the display, without emitting state 8. */
    public synchronized void requestDuringNavigation() {
        cruiseModeActive = true;
        clusterNavEnabled = false;
    }

    public synchronized boolean isActive() {
        return cruiseModeActive;
    }

    public synchronized boolean isClusterNavEnabled() {
        return clusterNavEnabled;
    }

    /**
     * Send KEY_TYPE 10019 broadcast to control cluster navigation mode.
     * state=8: NavigateStarted (enables fb2 display)
     * state=9: NavigateStopped (disables fb2 display)
     */
    private void sendClusterNavState(int state) {
        Intent intent = new Intent(ACTION);
        // Match the normal navigation broadcast: IPKServer is a separate package and must
        // also receive the display state. Sending only to extraservice does not notify it.
        intent.putExtra("KEY_TYPE", 10019);
        intent.putExtra("EXTRA_STATE", state);
        context.sendBroadcast(intent);

        // This records submission, not an acknowledgement from IPKServer or the cluster.
        Log.i(TAG, "probe=r7-ipk2 broadcast submitted state=" + state
                + " target=implicit key=10019 cruise=" + cruiseModeActive);
    }
}
