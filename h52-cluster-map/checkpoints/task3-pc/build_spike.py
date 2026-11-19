"""Build the throw-away Task 3 spike APK (com.diplay.spike) with the real GeelyClusterMap + libdiplay_vfb."""
import shutil
import subprocess
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
TOOLS = Path(r"C:\Users\Hasee\android-build-tools")
JAVA, JAVAC = TOOLS / "jdk17/bin/java.exe", TOOLS / "jdk17/bin/javac.exe"
BT = TOOLS / "build-tools-34"
ANDROID_JAR = TOOLS / "platform-18/android.jar"
SAMPLES = Path(r"C:\Users\Hasee\Desktop\Car\DiPlay-Geely-Android43-main\mobile\src\debug\assets\compat-probe")
OUT = HERE / "out"


def run(*cmd):
    subprocess.run([str(c) for c in cmd], check=True)


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    (OUT / "assets").mkdir(parents=True)
    for name in ("video-smoke.avcc", "video-smoke.frames"):
        shutil.copy2(SAMPLES / name, OUT / "assets" / name)
    run(BT / "aapt.exe", "package", "-f", "-M", HERE / "AndroidManifest.xml", "-A", OUT / "assets",
        "-I", ANDROID_JAR, "-F", OUT / "resources.apk")

    sources = sorted((HERE / "src").rglob("*.java")) + sorted((ROOT / "java").rglob("*.java"))
    classes = OUT / "classes"
    run(JAVAC, "-source", "7", "-target", "7", "-encoding", "UTF-8", "-nowarn",
        "-bootclasspath", ANDROID_JAR, "-d", classes, *sources)
    jar = OUT / "classes.jar"
    with zipfile.ZipFile(jar, "w") as z:
        for f in classes.rglob("*.class"):
            z.write(f, f.relative_to(classes).as_posix())
    run(JAVA, "-cp", BT / "lib/d8.jar", "com.android.tools.r8.D8", "--min-api", "18",
        "--lib", ANDROID_JAR, "--output", OUT, jar)

    unsigned = OUT / "unsigned.apk"
    shutil.copy2(OUT / "resources.apk", unsigned)
    with zipfile.ZipFile(unsigned, "a") as z:
        z.write(OUT / "classes.dex", "classes.dex")
        z.write(ROOT / "native/out/libdiplay_vfb.so", "lib/armeabi-v7a/libdiplay_vfb.so")
    aligned = OUT / "aligned.apk"
    run(BT / "zipalign.exe", "-f", "4", unsigned, aligned)
    apk = OUT / "diplay-cluster-spike.apk"
    run(JAVA, "-jar", BT / "lib/apksigner.jar", "sign", "--ks", TOOLS / "diplay-local.keystore",
        "--ks-key-alias", "diplay", "--ks-pass", "pass:diplaylocal", "--key-pass", "pass:diplaylocal",
        "--min-sdk-version", "18", "--out", apk, aligned)
    run(JAVA, "-jar", BT / "lib/apksigner.jar", "verify", "--min-sdk-version", "18", apk)
    print("built", apk)


if __name__ == "__main__":
    main()
