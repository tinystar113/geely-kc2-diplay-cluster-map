package com.shilapi.xcertplay.media;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.lang.reflect.Method;

/**
 * CarPlay's instrument-cluster map (stream 111) on the Geely H52 cluster.
 *
 * The decoder renders into a SurfaceTexture owned here; libdiplay_vfb draws it onto the cluster surface
 * that the car's libexternalsurfacehelper provides (/dev/graphics/fb2). The car streams that surface to
 * the cluster only while frames are swapped, so while a route is active the latest frame is redrawn at
 * 15 fps (CarPlay sends no frames for a still picture); with no route nothing is drawn and the cluster
 * shows its normal view.
 *
 * Off switch: create /sdcard/diplay_no_cluster_map (takes effect on the next CarPlay connection).
 */
public final class GeelyClusterMap {
    private static final String TAG = "DiPlay-H52-ClusterMap";
    private static final String HELPER = "/system/lib/libexternalsurfacehelper.so";
    private static final String OFF_SWITCH = "/sdcard/diplay_no_cluster_map";
    private static final String OWN_PREFS = "geely_cluster_map";
    private static final String KEY_INITIALIZED = "geely_cluster_map_initialized";
    private static final long KEEP_ALIVE_MS = 66;       // 15 fps
    private static final long STATS_MS = 10000;

    private static final Object LOCK = new Object();
    private static Boolean supported;                    // helper present and native lib loaded
    private static boolean loggedAvailable;

    private static Activity host;
    private static HandlerThread thread;
    private static Handler handler;
    private static int texture;
    private static SurfaceTexture surfaceTexture;
    private static Surface surface;
    private static final float[] matrix = new float[16];
    private static boolean hasFrame;
    private static volatile boolean routeActive;
    private static int frames, draws;
    private static long statsSince;

    private GeelyClusterMap() {}

    static native int nativeOpen();
    static native boolean nativeDraw(int texture, float[] matrix);
    static native void nativeClose();

    /** True when this car can show the cluster map and the user has not switched it off. */
    public static boolean available(Context context) {
        synchronized (LOCK) {
            if (new File(OFF_SWITCH).exists()) return logAvailable(false, "off switch file present");
            if (supported == null) {
                boolean ok = new File(HELPER).exists();
                if (ok) {
                    try {
                        System.loadLibrary("diplay_vfb");
                    } catch (Throwable error) {
                        Log.w(TAG, "native library unavailable", error);
                        ok = false;
                    }
                }
                supported = ok;
            }
            if (!supported) return logAvailable(false, "no cluster helper on this device");
            SharedPreferences own = context.getSharedPreferences(OWN_PREFS, Context.MODE_PRIVATE);
            if (!own.getBoolean(KEY_INITIALIZED, false)) {
                // DiPlay's attachSurface() sends stream 111 to the main surface unless this pref is true.
                setDiPlayClusterPref(context, true);
                own.edit().putBoolean(KEY_INITIALIZED, true).apply();
            } else if (Boolean.FALSE.equals(getDiPlayClusterPref(context))) {
                return logAvailable(false, "cluster map pref disabled");
            }
            return logAvailable(true, "helper present");
        }
    }

    /** Opens the cluster surface (once) and hands its decoder Surface to {@code host}. Idempotent. */
    public static void start(final Activity newHost) {
        synchronized (LOCK) {
            if (thread != null) {
                if (host != newHost) {                   // activity recreated: re-link
                    host = newHost;
                    final Surface current = surface;
                    if (current != null) deliver(newHost, current);
                    Log.i(TAG, "re-linked to new host");
                }
                return;
            }
            host = newHost;
            thread = new HandlerThread("diplay-cluster-vfb");
            thread.start();
            handler = new Handler(thread.getLooper());
            handler.post(new Runnable() {
                @Override public void run() { openOnRenderThread(); }
            });
        }
    }

    /** Releases the cluster surface if {@code oldHost} owns it. */
    public static void stop(Activity oldHost) {
        final Handler h;
        final Activity owner;
        synchronized (LOCK) {
            if (thread == null || oldHost != host) return;
            owner = host;
            h = handler;
            host = null;
            thread = null;
            handler = null;
        }
        deliver(owner, null);                            // decoder stops using the Surface first
        h.post(new Runnable() {
            @Override public void run() {
                closeOnRenderThread();
                Looper.myLooper().quit();
            }
        });
    }

    /** H52NavigationBridge status: 8 = navigation started, 9 = navigation ended. */
    public static void onNavStatus(int state) {
        if (state == 8) setRouteActive(true);
        else if (state == 9) setRouteActive(false);
    }

    public static void setRouteActive(boolean active) {
        if (routeActive != active) Log.i(TAG, "route=" + active);
        routeActive = active;
    }

    // ---- render thread ----

    private static void openOnRenderThread() {
        int tex = nativeOpen();
        if (tex <= 0) {
            Log.e(TAG, "open failed tex=" + tex);
            return;
        }
        final SurfaceTexture st = new SurfaceTexture(tex);
        final Surface s = new Surface(st);
        final Handler h;
        final Activity owner;
        synchronized (LOCK) {
            h = handler;
            owner = host;
            if (h == null) {                             // stopped while opening
                s.release();
                st.release();
                nativeClose();
                return;
            }
            texture = tex;
            surfaceTexture = st;
            surface = s;
            hasFrame = false;
            frames = 0;
            draws = 0;
            statsSince = SystemClock.uptimeMillis();
        }
        // API 18 calls this on an arbitrary thread; SurfaceTexture work must happen on the render thread.
        st.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override public void onFrameAvailable(SurfaceTexture ignored) {
                Handler target;
                synchronized (LOCK) { target = handler; }
                if (target != null) target.post(LATCH_FRAME);
            }
        });
        Log.i(TAG, "open tex=" + tex);
        h.postDelayed(KEEP_ALIVE, KEEP_ALIVE_MS);
        if (owner != null) deliver(owner, s);
    }

    private static final Runnable LATCH_FRAME = new Runnable() {
        @Override public void run() {
            SurfaceTexture st = surfaceTexture;
            if (st == null) return;
            try {
                st.updateTexImage();
                st.getTransformMatrix(matrix);
            } catch (RuntimeException error) {
                Log.w(TAG, "updateTexImage failed", error);
                return;
            }
            if (!hasFrame) Log.i(TAG, "first frame");
            hasFrame = true;
            frames++;
        }
    };

    private static final Runnable KEEP_ALIVE = new Runnable() {
        @Override public void run() {
            if (surfaceTexture == null) return;
            if (routeActive && hasFrame && nativeDraw(texture, matrix)) draws++;
            long now = SystemClock.uptimeMillis();
            if (now - statsSince >= STATS_MS) {
                if (frames > 0 || draws > 0) Log.i(TAG, "draws=" + draws + "/10s frames=" + frames + "/10s");
                frames = 0;
                draws = 0;
                statsSince = now;
            }
            Handler h;
            synchronized (LOCK) { h = handler; }
            if (h != null) h.postDelayed(this, KEEP_ALIVE_MS);
        }
    };

    private static void closeOnRenderThread() {
        if (surface != null) surface.release();
        if (surfaceTexture != null) surfaceTexture.release();
        surface = null;
        surfaceTexture = null;
        texture = 0;
        hasFrame = false;
        nativeClose();
        Log.i(TAG, "closed");
    }

    // ---- helpers ----

    /** Calls the host's private onClusterSurface(Surface) on the UI thread. */
    private static void deliver(final Activity target, final Surface value) {
        Runnable call = new Runnable() {
            @Override public void run() {
                try {
                    Method m = findMethod(target.getClass(), "onClusterSurface", Surface.class);
                    m.setAccessible(true);
                    m.invoke(target, value);
                } catch (Exception error) {
                    Log.e(TAG, "onClusterSurface failed", error);
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) call.run();
        else target.runOnUiThread(call);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                // keep walking up
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static boolean logAvailable(boolean value, String reason) {
        if (!loggedAvailable) {
            Log.i(TAG, "available=" + value + " (" + reason + ")");
            loggedAvailable = true;
        }
        return value;
    }

    /** DiPlay's AirPlayPersistence (Kotlin object); absent in test apps, then treated as enabled. */
    private static Boolean getDiPlayClusterPref(Context context) {
        try {
            Class<?> c = Class.forName("com.shilapi.xcertplay.AirPlayPersistence");
            Object instance = c.getField("INSTANCE").get(null);
            return (Boolean) c.getMethod("loadClusterMapEnabled", Context.class).invoke(instance, context);
        } catch (Exception error) {
            return null;
        }
    }

    private static void setDiPlayClusterPref(Context context, boolean enabled) {
        try {
            Class<?> c = Class.forName("com.shilapi.xcertplay.AirPlayPersistence");
            Object instance = c.getField("INSTANCE").get(null);
            c.getMethod("saveClusterMapEnabled", Context.class, boolean.class).invoke(instance, context, enabled);
            Log.i(TAG, "DiPlay cluster map pref set " + enabled);
        } catch (Exception error) {
            Log.i(TAG, "DiPlay persistence not present (" + error.getClass().getSimpleName() + ")");
        }
    }
}
