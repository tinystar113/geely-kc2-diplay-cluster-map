"""Host-only regression checks for the r7 IPK notification probe.

Compiles the real production Java against Android API 18 and recording doubles.
No adb, no device commands, no native rendering. Doubles exist only in a temporary
build directory and are never input to APK packaging.
"""
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOOLS = Path(r"C:\Users\Hasee\android-build-tools")
JAVAC = TOOLS / "jdk17/bin/javac.exe"
JAVA = TOOLS / "jdk17/bin/java.exe"
ANDROID = TOOLS / "platform-18/android.jar"

DOUBLES = {
    "android/content/Context.java": """
package android.content;
public abstract class Context {
    public static final int MODE_PRIVATE = 0;
    public abstract Context getApplicationContext();
    public abstract void sendBroadcast(Intent intent);
    public Intent registerReceiver(BroadcastReceiver r, IntentFilter f) { return null; }
    public void unregisterReceiver(BroadcastReceiver r) {}
    public SharedPreferences getSharedPreferences(String n, int m) {
        throw new UnsupportedOperationException("preferences not under test");
    }
}
""",
    "android/content/Intent.java": """
package android.content;
public class Intent {
    private final String action;
    private String pkg;
    private final java.util.Map<String,Object> extras = new java.util.HashMap<String,Object>();
    public Intent(String action) { this.action = action; }
    public String getAction() { return action; }
    public Intent setPackage(String pkg) { this.pkg = pkg; return this; }
    public String getPackage() { return pkg; }
    public Intent putExtra(String k, int v) { extras.put(k, v); return this; }
    public Intent putExtra(String k, boolean v) { extras.put(k, v); return this; }
    public Intent putExtra(String k, String v) { extras.put(k, v); return this; }
    public boolean hasExtra(String k) { return extras.containsKey(k); }
    public int getIntExtra(String k, int d) { Object v=extras.get(k); return v instanceof Integer ? (Integer)v : d; }
    public String getStringExtra(String k) { Object v=extras.get(k); return v instanceof String ? (String)v : null; }
}
""",
    "android/content/BroadcastReceiver.java": """
package android.content;
public abstract class BroadcastReceiver { public abstract void onReceive(Context c, Intent i); }
""",
    "android/content/IntentFilter.java": """
package android.content;
public class IntentFilter { public IntentFilter(String action) {} }
""",
    "android/os/Handler.java": """
package android.os;
public class Handler {
    public Handler(Looper l) {}
    public boolean post(Runnable r) { return true; }
    public boolean postDelayed(Runnable r, long ms) { return true; }
    public void removeCallbacks(Runnable r) {}
}
""",
    "android/os/Looper.java": """
package android.os;
public class Looper {
    private static final Looper MAIN = new Looper();
    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return MAIN; }
}
""",
    "android/os/SystemClock.java": """
package android.os;
public class SystemClock { public static long now; public static long uptimeMillis() { return now; } }
""",
    "android/util/Log.java": """
package android.util;
public class Log {
    public static final java.util.List<String> messages = new java.util.ArrayList<String>();
    public static int i(String t, String m) { messages.add(t + ": " + m); return 0; }
    public static int w(String t, String m) { return i(t,m); }
    public static int w(String t, String m, Throwable e) { return i(t,m + " " + e); }
    public static int e(String t, String m) { return i(t,m); }
    public static int e(String t, String m, Throwable e) { return i(t,m + " " + e); }
}
""",
    "com/shilapi/xcertplay/hud/BydHudRouteState.java": """
package com.shilapi.xcertplay.hud;
public class BydHudRouteState {
    public boolean routeActive;
    public long remainingMeters = -1, remainingSeconds = -1, distanceMeters = -1;
    public int activeIndex = -1;
    public String currentRoad = "";
    public Long emptyListSinceNs;
    public java.util.Map<Integer,Object> maneuvers = new java.util.HashMap<Integer,Object>();
    public Object accept(int message, byte[] payload) { routeActive = true; return "CHANGED"; }
    public Object currentApple() { return null; }
    public void clear() { routeActive = false; }
}
""",
}


def main():
    assert (ROOT / "build").is_dir(), "build parent must exist"
    with tempfile.TemporaryDirectory(prefix="cruise-probe-tests-", dir=ROOT / "build") as tmp:
        work = Path(tmp)
        generated = []
        for name, content in DOUBLES.items():
            path = work / "doubles" / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
            generated.append(path)
        classes = work / "classes"
        classes.mkdir()
        production = sorted((ROOT / "java").rglob("*.java")) + sorted((ROOT / "java-bridge").rglob("*.java"))
        stubs = sorted((ROOT / "stubs").rglob("*.java"))
        tests = [ROOT / "tests/H52CruiseModeTest.java"]
        suites = ["H52RouteEndPolicyTest", "H52ManeuverIconsTest", "H52NextRoadTest", "H52GuidanceEchoTest"]
        tests.extend(ROOT / "tests" / (s + ".java") for s in suites)
        cmd = [JAVAC, "-source", "7", "-target", "7", "-nowarn", "-encoding", "UTF-8",
               "-classpath", ANDROID, "-d", classes] + generated + production + stubs + tests
        subprocess.run([str(x) for x in cmd], check=True)
        cp = str(classes) + ";" + str(ANDROID)
        for suite in suites + ["H52CruiseModeTest"]:
            print("== " + suite, flush=True)
            subprocess.run([str(JAVA), "-cp", cp, suite], check=True)
        print("PASS: four existing suites plus production cruise/bridge/diagnostic checks", flush=True)


if __name__ == "__main__":
    main()
