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
 * the cluster only while frames are swapped. While a route or cruise is active, the latest frame is
 * redrawn at a target 15 fps (CarPlay may send no frames for a still picture). With neither active,
 * drawing stops. The tested cluster path also needs the navigation display state supplied by
 * H52NavigationBridge / H52CruiseMode (status 8/9); drawing alone does not prove visible output.
 *
 * Firmware rule: the cluster window and its EGL surface/context are opened once and never torn down
 * (teardown deadlocked in the Vivante driver), so one render thread lives for the whole process and
 * only the per-session SurfaceTexture and its texture are created and released.
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
    private static boolean sessionActive;                // between start() and stop()
    private static HandlerThread thread;                 // created once, never quits
    private static Handler handler;
    private static int texture;
    private static SurfaceTexture surfaceTexture;
    private static Surface surface;
    private static final float[] matrix = new float[16];
    private static boolean hasFrame;
    private static volatile boolean routeActive;
    private static volatile boolean cruiseMode;          // cruise mode: display without active navigation
    private static int frames, draws, drawFailures;
    private static long statsSince;

    private GeelyClusterMap() {}

    static native int nativeOpen();
    static native boolean nativeDraw(int texture, float[] matrix);
    static native void nativeDeleteTexture(int texture);

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
            if (sessionActive) {
                if (host != newHost) {                   // activity recreated: re-link
                    host = newHost;
                    if (surface != null) deliver(newHost, surface);
                    Log.i(TAG, "re-linked to new host");
                }
                return;
            }
            sessionActive = true;
            host = newHost;
            if (thread == null) {
                thread = new HandlerThread("diplay-cluster-vfb");
                thread.start();
                handler = new Handler(thread.getLooper());
                handler.postDelayed(KEEP_ALIVE, KEEP_ALIVE_MS);   // one loop for the process lifetime
            }
            handler.post(OPEN_SESSION);
        }
    }

    /** Ends the session if {@code oldHost} owns it: the decoder lets go of the Surface, then it is released. */
    public static void stop(Activity oldHost) {
        final Activity owner;
        synchronized (LOCK) {
            if (!sessionActive || oldHost != host) return;
            sessionActive = false;
            owner = host;
            host = null;
        }
        deliver(owner, null);                            // decoder stops using the Surface first
        handler.post(RELEASE_SESSION);
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

    /** Enable cruise mode: display cluster map without active navigation. */
    public static void setCruiseMode(boolean active) {
        if (cruiseMode != active) Log.i(TAG, "cruise=" + active);
        cruiseMode = active;
    }

    public static boolean isCruiseMode() {
        return cruiseMode;
    }

    // ---- render thread ----

    private static final Runnable OPEN_SESSION = new Runnable() {
        @Override public void run() {
            if (surfaceTexture != null) return;          // already open (stop/start raced)
            int tex = nativeOpen();                      // opens the cluster surface on first use only
            if (tex <= 0) {
                Log.e(TAG, "open failed tex=" + tex);
                return;
            }
            SurfaceTexture st = new SurfaceTexture(tex);
            Surface s = new Surface(st);
            Activity owner;
            synchronized (LOCK) {
                owner = host;
                if (!sessionActive) {                    // stopped while opening
                    s.release();
                    st.release();
                    nativeDeleteTexture(tex);
                    return;
                }
                texture = tex;
                surfaceTexture = st;
                surface = s;
            }
            hasFrame = false;
            frames = 0;
            draws = 0;
            drawFailures = 0;
            statsSince = SystemClock.uptimeMillis();
            // API 18 calls this on an arbitrary thread; SurfaceTexture work must happen on the render thread.
            st.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                @Override public void onFrameAvailable(SurfaceTexture ignored) {
                    handler.post(LATCH_FRAME);
                }
            });
            Log.i(TAG, "session open tex=" + tex);
            if (owner != null) deliver(owner, s);
        }
    };

    private static final Runnable RELEASE_SESSION = new Runnable() {
        @Override public void run() {
            Surface s;
            SurfaceTexture st;
            int tex;
            synchronized (LOCK) {
                if (sessionActive) return;               // restarted before this ran: keep the session
                s = surface;
                st = surfaceTexture;
                tex = texture;
                surface = null;
                surfaceTexture = null;
                texture = 0;
            }
            hasFrame = false;
            if (s != null) s.release();
            if (st != null) st.release();
            if (tex > 0) nativeDeleteTexture(tex);
            Log.i(TAG, "session closed");                // cluster surface + EGL stay open by design
        }
    };

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

    /** Runs every 66 ms for the process lifetime; draws while a session has a frame and (route active OR cruise mode). */
    private static final Runnable KEEP_ALIVE = new Runnable() {
        @Override public void run() {
            boolean shouldDraw = surfaceTexture != null && hasFrame && (routeActive || cruiseMode);
            if (shouldDraw) {
                if (nativeDraw(texture, matrix)) draws++;
                else drawFailures++;
            }
            long now = SystemClock.uptimeMillis();
            if (now - statsSince >= STATS_MS) {
                boolean session;
                synchronized (LOCK) { session = sessionActive; }
                // An active session must be observable even when no frame ever arrives.
                // Drawing success is NOT proof that the physical cluster displays the image.
                if (session || routeActive || cruiseMode || frames > 0 || draws > 0 || drawFailures > 0) {
                    String mode = routeActive ? "nav" : (cruiseMode ? "cruise" : "idle");
                    Log.i(TAG, "probe=r7-ipk1 draws=" + draws + "/10s frames=" + frames
                            + "/10s failures=" + drawFailures + " mode=" + mode
                            + " session=" + session + " surface=" + (surfaceTexture != null)
                            + " hasFrame=" + hasFrame + " route=" + routeActive
                            + " cruise=" + cruiseMode + " shouldDraw=" + shouldDraw);
                }
                frames = 0;
                draws = 0;
                drawFailures = 0;
                statsSince = now;
            }
            handler.postDelayed(this, KEEP_ALIVE_MS);
        }
    };

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
