"""Host-only regression tests. Android doubles are generated in an isolated test folder.
They never enter the production source path or APK. No adb, native calls or network.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
JDK = Path("C:/Users/Hasee/android-build-tools/jdk17/bin")
STUBS = {
    "android/content/Context.java": """package android.content;
public abstract class Context {
 public static final int MODE_PRIVATE=0;
 public Context getApplicationContext(){return this;}
 public abstract void sendBroadcast(Intent i);
 public Intent registerReceiver(BroadcastReceiver r, IntentFilter f){return null;}
 public void unregisterReceiver(BroadcastReceiver r){}
 public SharedPreferences getSharedPreferences(String name,int mode){throw new UnsupportedOperationException();}
}""",
    "android/content/Intent.java": """package android.content;
public class Intent {
 private final String action; private String pkg;
 private final java.util.Map<String,Object> extras=new java.util.HashMap<String,Object>();
 public Intent(String a){action=a;}
 public String getAction(){return action;}
 public Intent setPackage(String p){pkg=p;return this;}
 public String getPackage(){return pkg;}
 public Intent putExtra(String k,int v){extras.put(k,v);return this;}
 public Intent putExtra(String k,boolean v){extras.put(k,v);return this;}
 public Intent putExtra(String k,String v){extras.put(k,v);return this;}
 public int getIntExtra(String k,int d){Object v=extras.get(k);return v instanceof Integer?(Integer)v:d;}
 public String getStringExtra(String k){return (String)extras.get(k);}
 public boolean getBooleanExtra(String k,boolean d){Object v=extras.get(k);return v instanceof Boolean?(Boolean)v:d;}
 public boolean hasExtra(String k){return extras.containsKey(k);}
}""",
    "android/content/IntentFilter.java": "package android.content; public class IntentFilter {public IntentFilter(String a){}}",
    "android/content/BroadcastReceiver.java": "package android.content; public abstract class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);}",
    "android/content/SharedPreferences.java": """package android.content; public interface SharedPreferences {
 boolean getBoolean(String k,boolean d); Editor edit();
 interface Editor {Editor putBoolean(String k,boolean v);void apply();}
}""",
    "android/app/Activity.java": """package android.app; public class Activity extends android.content.Context {
 public void sendBroadcast(android.content.Intent i){throw new UnsupportedOperationException();}
 public void runOnUiThread(Runnable r){r.run();}
}""",
    "android/os/Looper.java": """package android.os; public class Looper {
 private static final Looper MAIN=new Looper();
 public static Looper getMainLooper(){return MAIN;} public static Looper myLooper(){return MAIN;}
}""",
    "android/os/Handler.java": """package android.os; public class Handler {
 public Handler(Looper l){} public boolean post(Runnable r){return true;}
 public boolean postDelayed(Runnable r,long ms){return true;} public void removeCallbacks(Runnable r){}
}""",
    "android/os/HandlerThread.java": """package android.os; public class HandlerThread {
 public HandlerThread(String s){} public void start(){} public Looper getLooper(){return Looper.getMainLooper();}
}""",
    "android/os/SystemClock.java": "package android.os; public class SystemClock {public static long now;public static long uptimeMillis(){return now;}}",
    "android/util/Log.java": """package android.util; public class Log {
 public static final java.util.List<String> messages=new java.util.ArrayList<String>();
 public static int i(String t,String m){messages.add(t+": "+m);return 0;}
 public static int w(String t,String m){return i(t,m);}
 public static int w(String t,String m,Throwable e){return i(t,m+" "+e);}
 public static int e(String t,String m){return i(t,m);}
 public static int e(String t,String m,Throwable e){return i(t,m+" "+e);}
}""",
    "android/graphics/SurfaceTexture.java": """package android.graphics; public class SurfaceTexture {
 public SurfaceTexture(int t){} public void release(){} public void updateTexImage(){}
 public void getTransformMatrix(float[] m){} public void setOnFrameAvailableListener(OnFrameAvailableListener l){}
 public interface OnFrameAvailableListener {void onFrameAvailable(SurfaceTexture s);}
}""",
    "android/view/Surface.java": "package android.view; public class Surface {public Surface(android.graphics.SurfaceTexture s){} public void release(){}}",
    "com/shilapi/xcertplay/ui/H52Diagnostics.java": """package com.shilapi.xcertplay.ui;
public class H52Diagnostics {
 public static final java.util.List<String> messages=new java.util.ArrayList<String>();
 public static void record(android.content.Context c,String message){messages.add(message);}
}""",
    "org/json/JSONObject.java": """package org.json; public class JSONObject {
 public JSONObject put(String k,Object v){return this;} public String toString(){return "{}";}
}""",
    "org/json/JSONArray.java": "package org.json; public class JSONArray {public JSONArray put(Object o){return this;}}",
    "com/shilapi/xcertplay/hud/BydHudRouteState.java": """package com.shilapi.xcertplay.hud;
public class BydHudRouteState {
 public boolean routeActive; public long remainingMeters=-1,remainingSeconds=-1,distanceMeters=-1;
 public String currentRoad=""; public int activeIndex=-1; public Long emptyListSinceNs;
 public java.util.Map<Integer,Object> maneuvers=new java.util.HashMap<Integer,Object>();
 private Object maneuver;
 public Object accept(int message,byte[] payload){
  int mode=payload[0];
  if(mode==3){clear();return "CLEAR";}
  routeActive=mode!=2 && mode!=5;
  maneuver=(mode==2 || mode==4 || mode==6)?new Maneuver(mode==6):null;
  if(mode==6){
   currentRoad="Test Current Road"; activeIndex=0;
   remainingMeters=3300; remainingSeconds=660; distanceMeters=114;
   maneuvers.put(0,maneuver);
  }
  return "UPDATE";
 }
 public Object currentApple(){return maneuver;}
 public void clear(){
  routeActive=false; maneuver=null; currentRoad=""; activeIndex=-1;
  remainingMeters=-1; remainingSeconds=-1; distanceMeters=-1; maneuvers.clear();
 }
 public static class Maneuver {
  private final boolean full;
  public Maneuver(boolean full){this.full=full;}
  public int getType(){return full?2:3;}
  public String getAfterRoad(){return full?"Test Next Road":"";}
  public long getDistanceMeters(){return full?114:-1;}
  public long getRemainingMeters(){return full?3300:-1;}
  public long getRemainingSeconds(){return full?660:-1;}
 }
}""",
}


def run(*args):
    subprocess.run([str(a) for a in args], check=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", action="store_true", help="run the four existing pure-Java suites only")
    args = parser.parse_args()
    if not args.baseline:
        hooks = ROOT / "android_tree/classes5/com/shilapi/xcertplay/media"
        for suffix, method in (("1", "stopForReplacement"), ("2", "stopSession")):
            code = (hooks / ("H52SessionIntegration$" + suffix + ".smali")).read_text(encoding="utf-8")
            assert "H52NavigationBridge;->" + method + "()V" in code
            assert "H52NavigationBridge;->stop()V" not in code
        print("PASS session replacement/disconnect smali call-site contract", flush=True)
    with tempfile.TemporaryDirectory(prefix="host-test-", dir=ROOT / "tests") as temp:
        work = Path(temp)
        classes = work / "classes"
        classes.mkdir()
        media = ROOT / "java-bridge/com/shilapi/xcertplay/media"
        if args.baseline:
            names = ["H52RouteEndPolicy", "H52ManeuverIcons", "H52NextRoad", "H52GuidanceEcho"]
            sources = [media / (n + ".java") for n in names]
            sources += [ROOT / "tests" / (n + "Test.java") for n in names]
            tests = [n + "Test" for n in names]
        else:
            sources = []
            for name, code in STUBS.items():
                path = work / "doubles" / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(code, encoding="utf-8")
                sources.append(path)
            sources += sorted(media.glob("*.java"))
            sources += sorted((ROOT / "java").rglob("*.java"))
            sources += [p for p in sorted((ROOT / "stubs").rglob("*.java"))
                        if p.relative_to(ROOT / "stubs").as_posix() not in STUBS]
            sources.append(ROOT / "tests/H52CruiseModeTest.java")
            tests = ["H52CruiseModeTest"]
        run(JDK / "javac.exe", "-encoding", "UTF-8", "-d", classes, *sources)
        for test in tests:
            run(JDK / "java.exe", "-cp", classes, test)


if __name__ == "__main__":
    main()
