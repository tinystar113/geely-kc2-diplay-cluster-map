import static org.junit.Assert.*;

/**
 * Integration tests for H52NavigationBridge cruise mode coordination.
 * Tests the interaction between H52CruiseMode, H52NavigationBridge, and GeelyClusterMap.
 */
public class H52CruiseIntegrationTest {

    // Test 1: Enable cruise calls GeelyClusterMap.setCruiseMode(true)
    public static void testEnableCruiseSetsClusterMapMode() {
        MockGeelyClusterMap.reset();
        TestContext ctx = new TestContext();
        H52NavigationBridge bridge = new H52NavigationBridge(ctx);

        bridge.enableCruise();

        assertTrue("cruise should be active", bridge.isCruiseActive());
        assertTrue("GeelyClusterMap cruise mode should be enabled", MockGeelyClusterMap.cruiseMode);
        assertEquals("should send state=8", 1, ctx.broadcasts.size());
        assertEquals(8, ctx.broadcasts.get(0).getIntExtra("EXTRA_STATE", -1));

        System.out.println("✓ Test 1: enableCruise sets GeelyClusterMap cruise mode");
    }

    // Test 2: Disable cruise calls GeelyClusterMap.setCruiseMode(false)
    public static void testDisableCruiseClearsClusterMapMode() {
        MockGeelyClusterMap.reset();
        TestContext ctx = new TestContext();
        H52NavigationBridge bridge = new H52NavigationBridge(ctx);

        bridge.enableCruise();
        MockGeelyClusterMap.reset();
        ctx.broadcasts.clear();

        bridge.disableCruise();

        assertFalse("cruise should be inactive", bridge.isCruiseActive());
        assertFalse("GeelyClusterMap cruise mode should be disabled", MockGeelyClusterMap.cruiseMode);
        assertEquals("should send state=9", 1, ctx.broadcasts.size());
        assertEquals(9, ctx.broadcasts.get(0).getIntExtra("EXTRA_STATE", -1));

        System.out.println("✓ Test 2: disableCruise clears GeelyClusterMap cruise mode");
    }

    // Test 3: Navigation start during cruise suspends cruise and enables route mode
    public static void testNavigationStartSuspendsCruise() {
        MockGeelyClusterMap.reset();
        TestContext ctx = new TestContext();
        H52NavigationBridge bridge = new H52NavigationBridge(ctx);

        bridge.enableCruise();
        assertTrue("cruise should be active", bridge.isCruiseActive());
        assertTrue("cluster cruise mode should be on", MockGeelyClusterMap.cruiseMode);

        ctx.broadcasts.clear();
        MockGeelyClusterMap.reset();

        // Simulate navigation start (route frame with maneuver)
        RouteFrame frame = RouteFrame.navigationStart();
        bridge.accept(frame);

        assertTrue("cruise should still be active (suspended)", bridge.isCruiseActive());
        assertFalse("cluster cruise mode should be off", MockGeelyClusterMap.cruiseMode);
        assertTrue("cluster route mode should be on", MockGeelyClusterMap.routeActive);

        // Should send nav state=8 for real navigation
        boolean hasNavStart = false;
        for (Intent intent : ctx.broadcasts) {
            if (intent.getIntExtra("KEY_TYPE", -1) == 10019
                && intent.getIntExtra("EXTRA_STATE", -1) == 8) {
                hasNavStart = true;
            }
        }
        assertTrue("should send navigation state=8", hasNavStart);

        System.out.println("✓ Test 3: Navigation start suspends cruise");
    }

    // Test 4: Navigation end during cruise resumes cruise mode
    public static void testNavigationEndResumesCruise() {
        MockGeelyClusterMap.reset();
        TestContext ctx = new TestContext();
        H52NavigationBridge bridge = new H52NavigationBridge(ctx);

        // Start with cruise
        bridge.enableCruise();

        // Start navigation (suspends cruise)
        RouteFrame navStart = RouteFrame.navigationStart();
        bridge.accept(navStart);

        ctx.broadcasts.clear();
        MockGeelyClusterMap.reset();

        // End navigation
        bridge.stop();

        assertTrue("cruise should be active", bridge.isCruiseActive());
        assertTrue("cluster cruise mode should be re-enabled", MockGeelyClusterMap.cruiseMode);
        assertFalse("cluster route mode should be off", MockGeelyClusterMap.routeActive);

        // Should send nav state=9 then cruise state=8
        int state9Count = 0, state8Count = 0;
        for (Intent intent : ctx.broadcasts) {
            if (intent.getIntExtra("KEY_TYPE", -1) == 10019) {
                int state = intent.getIntExtra("EXTRA_STATE", -1);
                if (state == 9) state9Count++;
                if (state == 8) state8Count++;
            }
        }
        assertTrue("should send state=9 (nav end)", state9Count > 0);
        assertTrue("should send state=8 (cruise resume)", state8Count > 0);

        System.out.println("✓ Test 4: Navigation end resumes cruise");
    }

    // Test 5: Navigation without prior cruise stays disabled after route ends
    public static void testNavigationWithoutCruiseStaysDisabled() {
        MockGeelyClusterMap.reset();
        TestContext ctx = new TestContext();
        H52NavigationBridge bridge = new H52NavigationBridge(ctx);

        // Start navigation WITHOUT enabling cruise first
        RouteFrame navStart = RouteFrame.navigationStart();
        bridge.accept(navStart);

        assertTrue("route should be active", MockGeelyClusterMap.routeActive);
        assertFalse("cruise should not be active", bridge.isCruiseActive());

        // End navigation
        bridge.stop();

        assertFalse("cruise should still be disabled", bridge.isCruiseActive());
        assertFalse("cluster cruise mode should be off", MockGeelyClusterMap.cruiseMode);
        assertFalse("cluster route mode should be off", MockGeelyClusterMap.routeActive);

        System.out.println("✓ Test 5: Navigation without cruise stays disabled after end");
    }

    // Test 6: Enable cruise during active navigation has no effect
    public static void testEnableCruiseDuringNavigationIgnored() {
        MockGeelyClusterMap.reset();
        TestContext ctx = new TestContext();
        H52NavigationBridge bridge = new H52NavigationBridge(ctx);

        // Start navigation
        RouteFrame navStart = RouteFrame.navigationStart();
        bridge.accept(navStart);

        ctx.broadcasts.clear();

        // Try to enable cruise during navigation
        bridge.enableCruise();

        assertFalse("cruise should not be active during navigation", bridge.isCruiseActive());
        assertFalse("cluster cruise mode should be off", MockGeelyClusterMap.cruiseMode);
        assertTrue("cluster route mode should still be on", MockGeelyClusterMap.routeActive);
        assertEquals("should not send cruise broadcast", 0, ctx.broadcasts.size());

        System.out.println("✓ Test 6: Enable cruise during navigation is ignored");
    }

    // Test 7: Multiple cruise enable/navigation/disable cycles
    public static void testMultipleCycles() {
        MockGeelyClusterMap.reset();
        TestContext ctx = new TestContext();
        H52NavigationBridge bridge = new H52NavigationBridge(ctx);

        // Cycle 1: Cruise → Nav → Cruise
        bridge.enableCruise();
        assertTrue("cruise 1 active", bridge.isCruiseActive());

        bridge.accept(RouteFrame.navigationStart());
        assertTrue("nav 1 active", MockGeelyClusterMap.routeActive);

        bridge.stop();
        assertTrue("cruise 1 resumed", bridge.isCruiseActive());
        assertTrue("cluster cruise 1", MockGeelyClusterMap.cruiseMode);

        // Cycle 2: Nav → Cruise continues
        bridge.accept(RouteFrame.navigationStart());
        assertTrue("nav 2 active", MockGeelyClusterMap.routeActive);
        assertFalse("cluster cruise 2 off", MockGeelyClusterMap.cruiseMode);

        bridge.stop();
        assertTrue("cruise 2 resumed", bridge.isCruiseActive());

        // Disable cruise
        bridge.disableCruise();
        assertFalse("cruise disabled", bridge.isCruiseActive());
        assertFalse("cluster cruise off", MockGeelyClusterMap.cruiseMode);

        System.out.println("✓ Test 7: Multiple cruise/navigation cycles work correctly");
    }

    // Test 8: Cruise mode doesn't interfere with guidance broadcasts
    public static void testCruiseDoesNotSendGuidance() {
        MockGeelyClusterMap.reset();
        TestContext ctx = new TestContext();
        H52NavigationBridge bridge = new H52NavigationBridge(ctx);

        bridge.enableCruise();

        // Check that no KEY_TYPE 10001 (guidance) broadcasts were sent
        for (Intent intent : ctx.broadcasts) {
            assertNotEquals("cruise should not send guidance (10001)",
                10001, intent.getIntExtra("KEY_TYPE", -1));
        }

        // Only KEY_TYPE 10019 (nav mode control) should be present
        boolean hasNavControl = false;
        for (Intent intent : ctx.broadcasts) {
            if (intent.getIntExtra("KEY_TYPE", -1) == 10019) {
                hasNavControl = true;
            }
        }
        assertTrue("should only send nav control (10019)", hasNavControl);

        System.out.println("✓ Test 8: Cruise mode doesn't send guidance broadcasts");
    }

    public static void main(String[] args) {
        System.out.println("Running H52 Cruise Integration tests...\n");

        testEnableCruiseSetsClusterMapMode();
        testDisableCruiseClearsClusterMapMode();
        testNavigationStartSuspendsCruise();
        testNavigationEndResumesCruise();
        testNavigationWithoutCruiseStaysDisabled();
        testEnableCruiseDuringNavigationIgnored();
        testMultipleCycles();
        testCruiseDoesNotSendGuidance();

        System.out.println("\n✅ All H52 Cruise Integration tests passed (8/8)");
    }

    // Mock GeelyClusterMap that tracks setCruiseMode/setRouteActive calls
    static class MockGeelyClusterMap {
        static boolean cruiseMode = false;
        static boolean routeActive = false;

        static void reset() {
            cruiseMode = false;
            routeActive = false;
        }

        public static void setCruiseMode(boolean active) {
            cruiseMode = active;
        }

        public static void setRouteActive(boolean active) {
            routeActive = active;
        }

        public static void onNavStatus(int state) {
            if (state == 8) setRouteActive(true);
            else if (state == 9) setRouteActive(false);
        }
    }

    // Minimal test helpers (copied from H52CruiseModeTest)
    static class TestContext extends android.content.ContextWrapper {
        final java.util.List<Intent> broadcasts = new java.util.ArrayList<>();

        TestContext() {
            super(null);
        }

        @Override
        public android.content.Context getApplicationContext() {
            return this;
        }

        @Override
        public void sendBroadcast(Intent intent) {
            broadcasts.add((Intent) intent.clone());
        }
    }

    static class Intent {
        private String action;
        private String pkg;
        private final java.util.Map<String, Object> extras = new java.util.HashMap<>();

        public Intent(String action) {
            this.action = action;
        }

        public String getAction() { return action; }

        public Intent setPackage(String pkg) {
            this.pkg = pkg;
            return this;
        }

        public String getPackage() { return pkg; }

        public Intent putExtra(String key, int value) {
            extras.put(key, value);
            return this;
        }

        public int getIntExtra(String key, int def) {
            Object val = extras.get(key);
            return val instanceof Integer ? (Integer) val : def;
        }

        @Override
        public Intent clone() {
            Intent copy = new Intent(action);
            copy.pkg = pkg;
            copy.extras.putAll(extras);
            return copy;
        }
    }

    // Minimal RouteFrame for testing
    static class RouteFrame {
        private int messageId;
        private byte[] payload;

        static RouteFrame navigationStart() {
            RouteFrame f = new RouteFrame();
            f.messageId = 0x5201;
            f.payload = new byte[100]; // Simplified: real payload would trigger route parsing
            return f;
        }

        public int getMessageId() { return messageId; }
        public byte[] getPayload() { return payload; }
    }
}
