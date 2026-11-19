import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import com.shilapi.xcertplay.media.H52CruiseMode;
import com.shilapi.xcertplay.media.GeelyClusterMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Runs real production classes against recording Android test doubles; no vehicle I/O. */
public final class H52CruiseModeTest {
    private static int checks;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    private static void packet(Intent intent, int state) {
        check("AUTONAVI_STANDARD_BROADCAST_SEND".equals(intent.getAction()), "action");
        check(intent.getPackage() == null, "state must also reach IPKServer, not extraservice only");
        check(intent.getIntExtra("KEY_TYPE", -1) == 10019, "status packet, not guidance");
        check(intent.getIntExtra("EXTRA_STATE", -1) == state, "expected state " + state);
        check(!intent.hasExtra("ICON") && !intent.hasExtra("NEXT_ROAD_NAME"), "no invented guidance");
    }

    private static void clearPacket(Intent intent) {
        check("AUTONAVI_STANDARD_BROADCAST_SEND".equals(intent.getAction()), "clear action");
        check("com.neusoft.shockwave.instrumentserver".equals(intent.getPackage()), "clear must target IPKServer only");
        check(intent.getIntExtra("KEY_TYPE", -1) == 10001, "clear key type");
        check(" ".equals(intent.getStringExtra("CUR_ROAD_NAME")), "current road must be exactly one ASCII space");
        check("实时路况巡航".equals(intent.getStringExtra("NEXT_ROAD_NAME")), "next road must be fixed cruise label");
        check(intent.getIntExtra("SEG_REMAIN_DIS", -1) == 0, "clear segment distance");
        check(intent.getIntExtra("ROUTE_REMAIN_DIS", -1) == 0, "clear remaining distance");
        check(intent.getIntExtra("ROUTE_ALL_DIS", -1) == 0, "clear total distance");
        check(intent.getIntExtra("ROUTE_REMAIN_TIME", -1) == 0, "clear remaining time");
        check(intent.getIntExtra("ICON", 0) == -1, "clear direction");
        check(!intent.getBooleanExtra("ARRIVE_STATUS", true), "clear arrival flag");
        check("{\"path_info\":[]}".equals(intent.getStringExtra("EXTRA_ROAD_INFO")), "path info must be exact valid JSON, with no literal backslashes");
        check("0米".equals(intent.getStringExtra("ROUTE_REMAIN_DIS_AUTO")), "formatted distance unchanged");
        check("0分钟".equals(intent.getStringExtra("ROUTE_REMAIN_TIME_AUTO")), "formatted time unchanged");
        check(intent.getIntExtra("ROUNG_ABOUT_NUM", -1) == 0, "roundabout value unchanged");
        check(!intent.hasExtra("EXTRA_STATE"), "clear must not change navigation state");
        check(!intent.hasExtra("TYPE"), "no unverified cruise-type switch");
    }

    public static final class TestContext extends Context {
        public final List<Intent> broadcasts = new ArrayList<Intent>();
        @Override public Context getApplicationContext() { return this; }
        @Override public void sendBroadcast(Intent intent) { broadcasts.add(intent); }
    }

    private static void lifecycle() {
        TestContext ctx = new TestContext();
        H52CruiseMode cruise = new H52CruiseMode(ctx);
        cruise.disable(); cruise.suspend(); cruise.resume();
        check(ctx.broadcasts.isEmpty(), "disabled operations are no-ops");
        cruise.enable();
        check(cruise.isActive() && cruise.isClusterNavEnabled(), "enabled flags");
        check(ctx.broadcasts.size() == 1, "one start");
        packet(ctx.broadcasts.get(0), 8);
        cruise.enable();
        check(ctx.broadcasts.size() == 1, "enable is idempotent");
        cruise.suspend();
        check(cruise.isActive() && !cruise.isClusterNavEnabled(), "suspend preserves intent");
        packet(ctx.broadcasts.get(1), 9);
        cruise.suspend();
        check(ctx.broadcasts.size() == 2, "suspend is idempotent");
        cruise.resume();
        packet(ctx.broadcasts.get(2), 8);
        cruise.resume();
        check(ctx.broadcasts.size() == 3, "resume is idempotent");
        cruise.disable();
        packet(ctx.broadcasts.get(3), 9);
        check(!cruise.isActive() && !cruise.isClusterNavEnabled(), "disabled flags");
        cruise.disable();
        check(ctx.broadcasts.size() == 4, "disable is idempotent");
        cruise.enable(); cruise.suspend();
        int before = ctx.broadcasts.size();
        cruise.disable();
        check(ctx.broadcasts.size() == before, "disable after suspend does not duplicate stop");
        for (int i = 0; i < ctx.broadcasts.size(); i++) packet(ctx.broadcasts.get(i), i % 2 == 0 ? 8 : 9);
        System.out.println("PASS cruise lifecycle and broadcast recipients");
    }

    public static final class Frame {
        private final int mode;
        public Frame() { this(1); }
        public Frame(int mode) { this.mode = mode; }
        public int getMessageId() { return 0x5201; }
        public byte[] getPayload() { return new byte[] {(byte) mode}; }
    }

    private static Object call(Object bridge, String name) throws Exception {
        Method method = bridge.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(bridge);
    }

    private static Object bridge(TestContext ctx) throws Exception {
        Class<?> type = Class.forName("com.shilapi.xcertplay.media.H52NavigationBridge");
        Constructor<?> constructor = type.getDeclaredConstructor(Context.class);
        constructor.setAccessible(true);
        return constructor.newInstance(ctx);
    }

    private static void frame(Object bridge, int mode) throws Exception {
        Method accept = bridge.getClass().getDeclaredMethod("accept", Object.class);
        accept.setAccessible(true);
        accept.invoke(bridge, new Frame(mode));
    }

    private static void states(TestContext ctx, int... expected) {
        check(ctx.broadcasts.size() == expected.length, "status count expected=" + expected.length
                + " actual=" + ctx.broadcasts.size());
        for (int i = 0; i < expected.length; i++) packet(ctx.broadcasts.get(i), expected[i]);
        ctx.broadcasts.clear();
    }

    private static void secondActivationAndRepeatedRoutes() throws Exception {
        TestContext ctx = new TestContext();
        Object bridge = bridge(ctx);
        call(bridge, "enableCruise");
        ctx.broadcasts.clear();
        frame(bridge, 2); // Maneuver with no route-state flag: second activation branch.
        check(!GeelyClusterMap.isCruiseMode(), "second activation must suspend cruise drawing");
        states(ctx, 9, 8);
        frame(bridge, 3); // CLEAR.
        check(ctx.broadcasts.size() == 3, "clear, stop and resume");
        clearPacket(ctx.broadcasts.get(0)); packet(ctx.broadcasts.get(1), 9); packet(ctx.broadcasts.get(2), 8);
        ctx.broadcasts.clear();
        check(GeelyClusterMap.isCruiseMode(), "CLEAR restores cruise");
        frame(bridge, 3); states(ctx); // Repeated CLEAR is silent.
        frame(bridge, 4); // New maneuver after endedSinceLastManeuver was set.
        states(ctx, 9, 8);
        frame(bridge, 3);
        check(ctx.broadcasts.size() == 3, "second clear, stop and resume");
        clearPacket(ctx.broadcasts.get(0)); packet(ctx.broadcasts.get(1), 9); packet(ctx.broadcasts.get(2), 8);
        ctx.broadcasts.clear();
        call(bridge, "disableCruise");
        System.out.println("PASS second activation, repeated CLEAR and two consecutive routes");
    }

    private static void replacementAndSessionEnd() throws Exception {
        TestContext ctx = new TestContext();
        Object old = bridge(ctx);
        call(old, "enableCruise");
        ctx.broadcasts.clear();
        call(old, "stopForReplacement");
        Object current = bridge(ctx);
        check((Boolean) call(current, "isCruiseActive"), "replacement retains cruise intent");
        ctx.broadcasts.clear();
        frame(current, 1); states(ctx, 9, 8);
        // An old bridge cannot close or revive the new bridge.
        call(old, "stop"); call(old, "stopSession"); states(ctx);
        frame(current, 3);
        check(ctx.broadcasts.size() == 3, "replacement clear, stop and resume");
        clearPacket(ctx.broadcasts.get(0)); packet(ctx.broadcasts.get(1), 9); packet(ctx.broadcasts.get(2), 8);
        ctx.broadcasts.clear();
        call(current, "stopSession"); states(ctx, 9);
        check(!GeelyClusterMap.isCruiseMode(), "disconnect in cruise disables drawing");
        call(current, "stopSession"); frame(current, 4); states(ctx);
        Object next = bridge(ctx);
        check(!(Boolean) call(next, "isCruiseActive"), "disconnected session cannot leak cruise intent");
        call(next, "enableCruise"); ctx.broadcasts.clear();
        frame(next, 1); states(ctx, 9, 8);
        call(next, "stopSession"); states(ctx, 9);
        check(!GeelyClusterMap.isCruiseMode(), "disconnect in navigation never restores cruise");
        Object reconnect = bridge(ctx);
        call(reconnect, "enableCruise"); states(ctx, 8);
        call(reconnect, "stopSession"); states(ctx, 9);
        System.out.println("PASS bridge replacement, stale callbacks, disconnect and reconnect");
    }

    private static void canceledDuringNavigation() throws Exception {
        Class<?> type = Class.forName("com.shilapi.xcertplay.media.H52NavigationBridge");
        TestContext ctx = new TestContext();
        Object current = bridge(ctx);
        call(current, "enableCruise"); states(ctx, 8);
        frame(current, 1); states(ctx, 9, 8);
        type.getMethod("disableCruiseStatic").invoke(null);
        states(ctx); // Do not stop an active route when cruise is canceled.
        frame(current, 3);
        check(ctx.broadcasts.size() == 2, "canceled route clear then stop");
        clearPacket(ctx.broadcasts.get(0)); packet(ctx.broadcasts.get(1), 9);
        ctx.broadcasts.clear();
        check(!GeelyClusterMap.isCruiseMode(), "canceled cruise must not restore after CLEAR");
        call(current, "stopSession"); states(ctx);
        System.out.println("PASS cancel during navigation preserves route and prevents cruise restoration");
    }

    private static void staticIntentAndWatchdog() throws Exception {
        Class<?> type = Class.forName("com.shilapi.xcertplay.media.H52NavigationBridge");
        type.getMethod("disableCruiseStatic").invoke(null);
        type.getMethod("enableCruiseStatic").invoke(null);
        type.getMethod("disableCruiseStatic").invoke(null);
        TestContext ctx = new TestContext();
        Object current = bridge(ctx);
        states(ctx);
        check(!(Boolean) call(current, "isCruiseActive"), "disable clears pending intent");
        frame(current, 1); states(ctx, 8);
        type.getMethod("enableCruiseStatic").invoke(null); // onResume during navigation.
        states(ctx);
        check(!GeelyClusterMap.isCruiseMode(), "resume during route does not open cruise gate");
        frame(current, 3);
        check(ctx.broadcasts.size() == 3, "watchdog clear, stop and resume");
        clearPacket(ctx.broadcasts.get(0)); packet(ctx.broadcasts.get(1), 9); packet(ctx.broadcasts.get(2), 8);
        ctx.broadcasts.clear();
        frame(current, 4); states(ctx, 9, 8);
        type.getMethod("enableCruiseStatic").invoke(null);
        states(ctx);
        frame(current, 5); // No explicit CLEAR: watchdog sees inactive route.
        call(current, "checkRouteEnded");
        check(ctx.broadcasts.size() == 3, "watchdog clear, stop and resume");
        clearPacket(ctx.broadcasts.get(0)); packet(ctx.broadcasts.get(1), 9); packet(ctx.broadcasts.get(2), 8);
        ctx.broadcasts.clear();
        call(current, "checkRouteEnded"); states(ctx);
        call(current, "stopSession"); states(ctx, 9);
        type.getMethod("enableCruiseStatic").invoke(null);
        Object reconnected = bridge(ctx);
        states(ctx, 8);
        call(reconnected, "stopSession"); states(ctx, 9);
        System.out.println("PASS pending cancellation, onResume during navigation and watchdog recovery");
    }

    private static void navigationHandoff() throws Exception {
        Class<?> bridgeType = Class.forName("com.shilapi.xcertplay.media.H52NavigationBridge");
        Constructor<?> constructor = bridgeType.getDeclaredConstructor(Context.class);
        constructor.setAccessible(true);
        TestContext ctx = new TestContext();
        Object bridge = constructor.newInstance(ctx);
        bridgeType.getMethod("enableCruise").invoke(bridge);
        packet(ctx.broadcasts.get(0), 8);
        ctx.broadcasts.clear();
        Method accept = bridgeType.getDeclaredMethod("accept", Object.class);
        accept.setAccessible(true);
        accept.invoke(bridge, new Frame());
        check(ctx.broadcasts.size() == 2, "handoff must send stop then start, no guidance");
        packet(ctx.broadcasts.get(0), 9);
        packet(ctx.broadcasts.get(1), 8);
        check(!GeelyClusterMap.isCruiseMode(), "cruise draw gate suspended");
        Method stop = bridgeType.getDeclaredMethod("stop");
        stop.setAccessible(true);
        ctx.broadcasts.clear();
        stop.invoke(bridge);
        check(ctx.broadcasts.size() == 3, "clear, route stop and cruise resume");
        clearPacket(ctx.broadcasts.get(0));
        packet(ctx.broadcasts.get(1), 9);
        packet(ctx.broadcasts.get(2), 8);
        check(GeelyClusterMap.isCruiseMode(), "cruise draw gate resumed");
        bridgeType.getMethod("disableCruise").invoke(bridge);
        System.out.println("PASS actual bridge handoff order and return to cruise (synthetic route)");
    }

    private static void actualGuidanceAfterBlanking() throws Exception {
        TestContext ctx = new TestContext();
        Object current = bridge(ctx);
        call(current, "enableCruise");
        states(ctx, 8); // Initial cruise remains unchanged: no clearing packet yet.
        for (int round = 0; round < 2; round++) {
            frame(current, 6); // Synthetic route with actual nonempty roads and distances.
            check(ctx.broadcasts.size() == 3, "navigation sends stop, start and actual guidance");
            packet(ctx.broadcasts.get(0), 9);
            packet(ctx.broadcasts.get(1), 8);
            Intent real = ctx.broadcasts.get(2);
            check(real.getPackage() == null, "actual guidance retains its original recipients");
            check(real.getIntExtra("KEY_TYPE", -1) == 10001, "actual guidance key");
            check("Test Current Road".equals(real.getStringExtra("CUR_ROAD_NAME")), "actual current road not blanked");
            check("Test Next Road".equals(real.getStringExtra("NEXT_ROAD_NAME")), "actual next road not blanked");
            check(real.getIntExtra("SEG_REMAIN_DIS", -1) == 114, "actual segment distance preserved");
            check(real.getIntExtra("ROUTE_REMAIN_DIS", -1) == 3300, "actual remaining distance preserved");
            check(real.getIntExtra("ROUTE_ALL_DIS", -1) == 3300, "actual total distance preserved");
            check(real.getIntExtra("ROUTE_REMAIN_TIME", -1) == 660, "actual remaining time preserved");
            check(real.getIntExtra("ICON", -1) == 3, "actual right turn preserved");
            check("3.3公里".equals(real.getStringExtra("ROUTE_REMAIN_DIS_AUTO")), "actual formatted distance preserved");
            check("11分钟".equals(real.getStringExtra("ROUTE_REMAIN_TIME_AUTO")), "actual formatted time preserved");
            check(!real.getBooleanExtra("ARRIVE_STATUS", true), "actual arrival flag preserved");
            ctx.broadcasts.clear();
            frame(current, 3);
            check(ctx.broadcasts.size() == 3, "one clear then stop then resume per route end");
            clearPacket(ctx.broadcasts.get(0));
            packet(ctx.broadcasts.get(1), 9);
            packet(ctx.broadcasts.get(2), 8);
            ctx.broadcasts.clear();
            frame(current, 3); states(ctx);
            call(current, "enableCruise"); states(ctx); // Resume must not repeatedly clear.
            check(GeelyClusterMap.isCruiseMode(), "cruise rendering remains enabled");
        }
        call(current, "stopSession"); states(ctx, 9);
        System.out.println("PASS actual guidance preserved across two route/space-clear cycles");
    }

    private static void field(String name, Object value) throws Exception {
        Field f = GeelyClusterMap.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    private static void zeroFrameDiagnostics() throws Exception {
        field("handler", new Handler(Looper.getMainLooper()));
        field("sessionActive", true);
        field("surfaceTexture", null);
        field("hasFrame", false);
        field("routeActive", false);
        field("cruiseMode", true);
        field("frames", 0);
        field("draws", 0);
        field("statsSince", Long.valueOf(10000));
        SystemClock.now = 20000;
        Field loop = GeelyClusterMap.class.getDeclaredField("KEEP_ALIVE");
        loop.setAccessible(true);
        Log.messages.clear();
        ((Runnable) loop.get(null)).run();
        boolean found = false;
        for (String message : Log.messages) {
            if (message.contains("probe=r7-ipk1") && message.contains("frames=0/10s")
                    && message.contains("draws=0/10s") && message.contains("session=true")
                    && message.contains("surface=false") && message.contains("hasFrame=false")
                    && message.contains("route=false") && message.contains("cruise=true")
                    && message.contains("shouldDraw=false")) found = true;
        }
        check(found, "zero-frame active session must print diagnostic state");
        field("sessionActive", false); field("cruiseMode", false);
        SystemClock.now = 30000;
        Log.messages.clear();
        ((Runnable) loop.get(null)).run();
        check(Log.messages.isEmpty(), "inactive idle renderer must not keep logging");
        System.out.println("PASS zero-frame diagnostics without invoking native rendering");
    }

    public static class ReflectionParent {
        private int inheritedField = 8;
        public int inheritedGetter() { return 8; }
    }

    public static final class ReflectionA extends ReflectionParent {
        private int value = 1;
        private boolean fail;
        public int getter() {
            if (fail) throw new IllegalStateException("synthetic getter failure");
            return value;
        }
    }

    public static final class ReflectionB {
        private int value = 9;
        public int getter() { return value; }
    }

    private static Object bridgeField(Object bridge, String name) throws Exception {
        Field f = bridge.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(bridge);
    }

    private static void cachedReflection() throws Exception {
        Class<?> type = Class.forName("com.shilapi.xcertplay.media.H52NavigationBridge");
        Method get = type.getDeclaredMethod("get", Object.class, String.class);
        Method getField = type.getDeclaredMethod("getField", Object.class, String.class);
        get.setAccessible(true); getField.setAccessible(true);
        ReflectionA a = new ReflectionA();
        ReflectionB b = new ReflectionB();
        check(Integer.valueOf(1).equals(getField.invoke(null, a, "value")), "private field readable");
        check(Integer.valueOf(1).equals(get.invoke(null, a, "getter")), "getter readable");
        Field fields = type.getDeclaredField("FIELD_CACHE"), methods = type.getDeclaredField("METHOD_CACHE");
        fields.setAccessible(true); methods.setAccessible(true);
        java.util.Map<?, ?> fieldCache = (java.util.Map<?, ?>) fields.get(null);
        java.util.Map<?, ?> methodCache = (java.util.Map<?, ?>) methods.get(null);
        Object cachedField = ((java.util.Map<?, ?>) fieldCache.get(ReflectionA.class)).get("value");
        Object cachedMethod = ((java.util.Map<?, ?>) methodCache.get(ReflectionA.class)).get("getter");
        for (int i = 2; i <= 11; i++) {
            a.value = i;
            check(Integer.valueOf(i).equals(getField.invoke(null, a, "value")), "cache metadata, not field value");
            check(Integer.valueOf(i).equals(get.invoke(null, a, "getter")), "cache metadata, not getter result");
            check(cachedField == ((java.util.Map<?, ?>) fieldCache.get(ReflectionA.class)).get("value"), "reuse Field identity");
            check(cachedMethod == ((java.util.Map<?, ?>) methodCache.get(ReflectionA.class)).get("getter"), "reuse Method identity");
        }
        check(Integer.valueOf(9).equals(getField.invoke(null, b, "value")), "field cache separated by class");
        check(Integer.valueOf(9).equals(get.invoke(null, b, "getter")), "method cache separated by class");
        check(Integer.valueOf(8).equals(get.invoke(null, a, "inheritedGetter")), "public inherited method remains supported");
        check(getField.invoke(null, a, "inheritedField") == null, "declared-field behavior unchanged");
        check(getField.invoke(null, a, "missing") == null, "missing field remains null");
        check(get.invoke(null, a, "missing") == null, "missing method remains null");
        check(((java.util.Map<?, ?>) fieldCache.get(ReflectionA.class)).containsKey("missing"), "negative field lookup cached");
        check(((java.util.Map<?, ?>) methodCache.get(ReflectionA.class)).containsKey("missing"), "negative method lookup cached");
        check(getField.invoke(null, a, "missing") == null && get.invoke(null, a, "missing") == null, "repeat missing lookup harmless");
        a.fail = true;
        check(get.invoke(null, a, "getter") == null, "throwing getter keeps null fallback");
        a.fail = false;
        check(Integer.valueOf(11).equals(get.invoke(null, a, "getter")), "getter failure does not poison cached method");
        check(get.invoke(null, new Object[] {null, "getter"}) == null, "null object remains null");
        check(getField.invoke(null, new Object[] {null, "value"}) == null, "null field target remains null");
        System.out.println("PASS reflection reuse, live values, runtime classes and error fallbacks");
    }

    private static int diagnosticCount(String prefix) {
        int count = 0;
        for (String message : com.shilapi.xcertplay.ui.H52Diagnostics.messages) {
            if (message.startsWith(prefix)) count++;
        }
        return count;
    }

    private static long diagnosticEvents(String samplePrefix, String pendingPrefix) {
        long total = 0;
        for (String message : com.shilapi.xcertplay.ui.H52Diagnostics.messages) {
            if (message.startsWith(samplePrefix)) {
                total += Long.parseLong(message.substring(message.lastIndexOf(" n=") + 3));
            } else if (message.startsWith(pendingPrefix)) {
                total += Long.parseLong(message.substring(pendingPrefix.length()));
            }
        }
        return total;
    }

    private static void sampledDiagnostics() throws Exception {
        TestContext ctx = new TestContext();
        Object current = bridge(ctx);
        SystemClock.now = 0;
        com.shilapi.xcertplay.ui.H52Diagnostics.messages.clear();
        Object acceptMethod = null;
        for (int i = 0; i < 150; i++) {
            SystemClock.now = i * 67L;
            frame(current, 6);
            Object resolved = bridgeField(current, "routeAcceptMethod");
            if (i == 0) acceptMethod = resolved;
            check(resolved != null && resolved == acceptMethod, "reuse route accept Method");
        }
        check(ctx.broadcasts.size() == 151, "150 frames still send 150 guidance plus one start");
        packet(ctx.broadcasts.get(0), 8);
        for (int i = 1; i < ctx.broadcasts.size(); i++) {
            Intent p = ctx.broadcasts.get(i);
            check(p.getIntExtra("KEY_TYPE", -1) == 10001 && p.getIntExtra("SEG_REMAIN_DIS", -1) == 114
                    && p.getIntExtra("ROUTE_REMAIN_DIS", -1) == 3300
                    && p.getIntExtra("ROUTE_REMAIN_TIME", -1) == 660
                    && p.getIntExtra("ICON", -1) == 3
                    && "Test Current Road".equals(p.getStringExtra("CUR_ROAD_NAME"))
                    && "Test Next Road".equals(p.getStringExtra("NEXT_ROAD_NAME")), "guidance is never sampled");
        }
        check(diagnosticCount("NAV input=") == 5, "150 inputs produce five timed samples");
        check(diagnosticCount("NAV out icon=") == 5, "150 outputs produce five timed samples");
        check(diagnosticCount("NAV state=8") == 1, "navigation start logged immediately");
        check(com.shilapi.xcertplay.ui.H52Diagnostics.messages.contains("NAV input=20993 parsed=UPDATE n=30"), "sample includes aggregated count");
        int beforeEcho = ctx.broadcasts.size();
        Method echo = current.getClass().getDeclaredMethod("onForeignGuidance", Intent.class);
        echo.setAccessible(true);
        for (int i = 0; i < 60; i++) echo.invoke(current, new Intent("AUTONAVI_STANDARD_BROADCAST_SEND").putExtra("KEY_TYPE", 10001));
        check(ctx.broadcasts.size() == beforeEcho + 60, "foreign guidance replies not throttled");
        check(diagnosticCount("NAV out icon=") == 5, "echo uses same output log budget");
        check(diagnosticCount("NAV echo n=") == 2, "existing echo diagnostic milestones preserved");
        echo.invoke(current, new Intent("AUTONAVI_STANDARD_BROADCAST_SEND").putExtra("KEY_TYPE", 10001)
                .putExtra(com.shilapi.xcertplay.media.H52GuidanceEcho.MARKER, true));
        check(ctx.broadcasts.size() == beforeEcho + 60, "own guidance still ignored");
        frame(current, 3);
        check(diagnosticCount("NAV input=20993 parsed=CLEAR") == 1, "CLEAR bypasses sample interval");
        check(diagnosticCount("NAV state=9") == 1, "navigation end always logged");
        check(diagnosticCount("NAV clear external") == 1, "cleanup always logged");
        check(diagnosticCount("NAV out pending=") == 1, "unreported output count flushed on end");
        check(diagnosticEvents("NAV input=", "NAV input pending=") == 151L, "input aggregate conserves 150 updates plus CLEAR");
        check(diagnosticEvents("NAV out icon=", "NAV out pending=") == 210L, "output aggregate conserves 150 updates plus 60 echo replies");
        int inBefore = diagnosticCount("NAV input="), outBefore = diagnosticCount("NAV out icon=");
        frame(current, 6);
        check(diagnosticCount("NAV input=") == inBefore + 1, "new route first input logged without delay");
        check(diagnosticCount("NAV out icon=") == outBefore + 1, "new route first output logged without delay");
        call(current, "stopSession");
        System.out.println("PASS 150-frame/10s simulation: frequent input/output logs 300 -> 10; all broadcasts preserved");
    }

    private static void diagnosticLifecycleFlush() throws Exception {
        TestContext ctx = new TestContext();
        Object old = bridge(ctx);
        SystemClock.now = 100000;
        com.shilapi.xcertplay.ui.H52Diagnostics.messages.clear();
        frame(old, 6); frame(old, 6); frame(old, 6);
        Object replacement = bridge(ctx); // Retiring the old owner flushes pending diagnostics.
        check(diagnosticEvents("NAV input=", "NAV input pending=") == 3L, "replacement flushes all pending inputs");
        check(diagnosticEvents("NAV out icon=", "NAV out pending=") == 3L, "replacement flushes all pending outputs");
        com.shilapi.xcertplay.ui.H52Diagnostics.messages.clear();
        frame(old, 6);
        check(com.shilapi.xcertplay.ui.H52Diagnostics.messages.isEmpty(), "closed bridge cannot log new inputs");
        frame(replacement, 6); frame(replacement, 6);
        call(replacement, "stopSession");
        check(diagnosticEvents("NAV input=", "NAV input pending=") == 2L, "disconnect flushes pending inputs");
        check(diagnosticEvents("NAV out icon=", "NAV out pending=") == 2L, "disconnect flushes pending outputs");
        check(diagnosticCount("NAV state=9") == 1, "disconnect stop diagnostic immediate");
        System.out.println("PASS diagnostic count conservation on replacement and disconnect");
    }

    private static void diagnosticGateBoundaries() throws Exception {
        Class<?> type = Class.forName("com.shilapi.xcertplay.media.H52NavigationBridge$DiagnosticGate");
        Constructor<?> ctor = type.getDeclaredConstructor(); ctor.setAccessible(true);
        Object gate = ctor.newInstance();
        Method allow = type.getDeclaredMethod("allow", long.class, boolean.class); allow.setAccessible(true);
        check((Boolean) allow.invoke(gate, 0L, false), "first sample at zero uptime");
        check(!(Boolean) allow.invoke(gate, 1999L, false), "before exact interval suppressed");
        check((Boolean) allow.invoke(gate, 2000L, false), "exact 2s interval allowed");
        check((Boolean) allow.invoke(gate, 2001L, true), "forced event immediate");
        check(!(Boolean) allow.invoke(gate, 2001L, false), "same time repeat suppressed");
        Field reported = type.getDeclaredField("reported"); reported.setAccessible(true);
        check((Boolean) allow.invoke(gate, 4001L, false), "interval measured from forced event");
        check(reported.getLong(gate) == 2L, "aggregate includes suppressed calls");
        System.out.println("PASS deterministic diagnostic interval boundaries");
    }

    public static void main(String[] args) throws Exception {
        lifecycle();
        navigationHandoff();
        secondActivationAndRepeatedRoutes();
        replacementAndSessionEnd();
        staticIntentAndWatchdog();
        canceledDuringNavigation();
        actualGuidanceAfterBlanking();
        zeroFrameDiagnostics();
        cachedReflection();
        sampledDiagnostics();
        diagnosticLifecycleFlush();
        diagnosticGateBoundaries();
        System.out.println("ALL PASS: " + checks + " assertions; host doubles only, not an on-car test");
    }
}
