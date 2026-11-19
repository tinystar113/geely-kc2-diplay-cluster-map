"""Rebuild the DiPlay H52 C7A-R2 APK from its smali trees (Windows).

Base: the released APK (DiPlay-H52-Slim-v1.6.20-C7A-R2-BT-native-lease.apk). Only classes2.dex,
classes5.dex and assets/build_info.txt are replaced; an optional native lib is added. Every other
entry is copied byte-for-byte, then the APK is zipaligned and signed with the local key.

Usage:
  python -I build_h52.py --out OUT.apk [--java DIR] [--native DIR] [--verify-roundtrip]
"""
import argparse
import hashlib
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent            # h52-cluster-map


def _repo_root():
    """向上找到包含 h52-cluster-map 的目录，即仓库根。"""
    here = Path(__file__).resolve()
    for cand in (here.parent, *here.parents):
        if (cand / "h52-cluster-map").is_dir():
            return cand
    return here.parent


REPO_ROOT = _repo_root()
TOOLS = Path(os.environ.get("ANDROID_TOOLS") or (Path.home() / "android-build-tools"))
JAVA = TOOLS / "jdk17" / "bin" / "java.exe"
JAVAC = TOOLS / "jdk17" / "bin" / "javac.exe"
BT = TOOLS / "build-tools-34"
ANDROID_JAR = TOOLS / "platform-18" / "android.jar"
SMALI_CP = ";".join(str(p) for p in sorted((TOOLS / "smali-jars").glob("*.jar")))
KEYSTORE = TOOLS / "diplay-local.keystore"
KEY_ALIAS = "diplay"
_KEYSTORE = TOOLS / "diplay-local.keystore"
_KEY_PASS_FILE = _KEYSTORE.with_name(_KEYSTORE.name + ".pass")


def _keystore_pass():
    """从环境变量 DIPLAY_KS_PASS 或密钥库旁的 .pass 文件读取，不写进源码。"""
    pw = os.environ.get("DIPLAY_KS_PASS")
    if pw:
        return pw.strip()
    if _KEY_PASS_FILE.is_file():
        return _KEY_PASS_FILE.read_text(encoding="utf-8").strip()
    sys.exit("缺少签名密码：请设置环境变量 DIPLAY_KS_PASS，"
             f"或把密码写入 {_KEY_PASS_FILE}")


KEY_PASS = _keystore_pass()
BASE_APK = REPO_ROOT / "DiPlay-H52-Slim-v1.6.20-C7A-R2-BT-native-lease.apk"
BASE_SHA256 = "7bb6825032726b693cc995d72091ab7fd208b412491082b80cb34c7369234ddb"
BUILD_INFO = b"v1.6.20+1109-C7A-R2-cluster-map\n"
NATIVE_LIB = "libdiplay_vfb.so"
WORK = ROOT / "build" / "work"


def run(*cmd):
    subprocess.run([str(c) for c in cmd], check=True)


def smali(*args):
    run(JAVA, "-cp", SMALI_CP, "com.android.tools.smali.smali.Main", *args)


def baksmali(*args):
    run(JAVA, "-cp", SMALI_CP, "com.android.tools.smali.baksmali.Main", *args)


def compile_java(src_dirs, stubs_dir, tree5: Path):
    """javac (Java 7) -> d8 (min-api 18) -> baksmali, merged into the classes5 tree.

    stubs_dir holds compile-time stand-ins for classes that already ship in the APK; they are on the
    classpath only and never packaged.
    """
    sources = sorted(f for d in src_dirs for f in d.rglob("*.java"))
    if not sources:
        sys.exit(f"no .java files under {src_dirs}")
    classpath = []
    if stubs_dir:
        stub_classes = WORK / "stubs"
        run(JAVAC, "-source", "7", "-target", "7", "-encoding", "UTF-8", "-nowarn",
            "-bootclasspath", ANDROID_JAR, "-d", stub_classes, *sorted(stubs_dir.rglob("*.java")))
        classpath = ["-classpath", stub_classes]
    classes = WORK / "javac"
    run(JAVAC, "-source", "7", "-target", "7", "-encoding", "UTF-8", "-nowarn",
        "-bootclasspath", ANDROID_JAR, *classpath, "-d", classes, *sources)
    jar = WORK / "patch.jar"
    with zipfile.ZipFile(jar, "w") as z:
        for f in classes.rglob("*.class"):
            z.write(f, f.relative_to(classes).as_posix())
    dex_dir = WORK / "patch-dex"
    dex_dir.mkdir()
    d8_classpath = ["--classpath", WORK / "stubs"] if stubs_dir else []
    run(JAVA, "-cp", BT / "lib" / "d8.jar", "com.android.tools.r8.D8", "--min-api", "18",
        "--lib", ANDROID_JAR, *d8_classpath, "--output", dex_dir, jar)
    patch_smali = WORK / "patch-smali"
    baksmali("d", "--api", "18", dex_dir / "classes.dex", "-o", patch_smali)
    for f in patch_smali.rglob("*.smali"):
        target = tree5 / f.relative_to(patch_smali)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(f, target)
        print("java ->", target.relative_to(tree5).as_posix())


def tree_text(dex: Path, out: Path) -> dict:
    baksmali("d", "--api", "18", dex, "-o", out)
    return {f.relative_to(out).as_posix(): f.read_text(encoding="utf-8") for f in out.rglob("*.smali")}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True, type=Path)
    ap.add_argument("--java", type=Path, action="append", help="Java source dir (repeatable)")
    ap.add_argument("--stubs", type=Path, help="compile-time-only stand-ins (never packaged)")
    ap.add_argument("--native", type=Path)
    ap.add_argument("--verify-roundtrip", action="store_true")
    args = ap.parse_args()

    if hashlib.sha256(BASE_APK.read_bytes()).hexdigest() != BASE_SHA256:
        sys.exit("base APK checksum mismatch")
    if WORK.exists():
        shutil.rmtree(WORK)
    WORK.mkdir(parents=True)

    # Work on a copy so the extracted trees stay pristine.
    tree = WORK / "android_tree"
    shutil.copytree(ROOT / "android_tree", tree)
    if args.java:
        compile_java(args.java, args.stubs, tree / "classes5")

    dex = {}
    for n in ("classes2", "classes5"):
        out = WORK / f"{n}.dex"
        smali("a", "--api", "18", tree / n, "-o", out)
        dex[f"{n}.dex"] = out.read_bytes()

    native = None
    if args.native:
        native = (args.native / NATIVE_LIB).read_bytes()

    unsigned = WORK / "unsigned.apk"
    with zipfile.ZipFile(BASE_APK) as old, zipfile.ZipFile(unsigned, "w") as new:
        for info in old.infolist():
            if info.filename.startswith("META-INF/"):
                continue
            data = old.read(info.filename)
            if info.filename in dex:
                data = dex[info.filename]
            elif info.filename == "assets/build_info.txt":
                data = BUILD_INFO
            new.writestr(info, data)
        if native is not None:
            new.writestr(zipfile.ZipInfo(f"lib/armeabi-v7a/{NATIVE_LIB}", (2026, 11, 9, 0, 0, 0)),
                         native, compress_type=zipfile.ZIP_DEFLATED)

    aligned = WORK / "aligned.apk"
    run(BT / "zipalign.exe", "-f", "4", unsigned, aligned)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    run(JAVA, "-jar", BT / "lib" / "apksigner.jar", "sign", "--ks", KEYSTORE, "--ks-key-alias", KEY_ALIAS,
        "--ks-pass", f"pass:{KEY_PASS}", "--key-pass", f"pass:{KEY_PASS}", "--min-sdk-version", "18",
        "--v1-signing-enabled", "true", "--v2-signing-enabled", "true", "--v3-signing-enabled", "false",
        "--out", args.out, aligned)
    run(BT / "zipalign.exe", "-c", "4", args.out)
    run(JAVA, "-jar", BT / "lib" / "apksigner.jar", "verify", "--min-sdk-version", "18", args.out)

    # Changed-entries contract (plan, Global Constraints).
    allowed = {"classes2.dex", "classes5.dex", "assets/build_info.txt"}
    with zipfile.ZipFile(BASE_APK) as old, zipfile.ZipFile(args.out) as new:
        old_names = {n for n in old.namelist() if not n.startswith("META-INF/")}
        new_names = {n for n in new.namelist() if not n.startswith("META-INF/")}
        added = new_names - old_names
        assert old_names <= new_names, f"entries removed: {sorted(old_names - new_names)}"
        expected_added = {f"lib/armeabi-v7a/{NATIVE_LIB}"} if native is not None else set()
        assert added == expected_added, f"unexpected added entries: {sorted(added)}"
        changed = sorted(n for n in old_names if old.read(n) != new.read(n))
        assert set(changed) <= allowed, f"unexpected changed entries: {changed}"
        print("changed entries:", changed, "added:", sorted(added))

        if args.verify_roundtrip:
            for n in ("classes2.dex", "classes5.dex"):
                (WORK / f"released-{n}").write_bytes(old.read(n))
                (WORK / f"rebuilt-{n}").write_bytes(new.read(n))
                a = tree_text(WORK / f"released-{n}", WORK / f"released-{n}-smali")
                b = tree_text(WORK / f"rebuilt-{n}", WORK / f"rebuilt-{n}-smali")
                assert a.keys() == b.keys(), f"{n}: class set differs: {sorted(a.keys() ^ b.keys())[:10]}"
                diff = [k for k in a if a[k] != b[k]]
                assert not diff, f"{n}: {len(diff)} classes differ, e.g. {diff[:5]}"
                print(f"roundtrip {n}: {len(a)} classes identical")

    print("sha256", hashlib.sha256(args.out.read_bytes()).hexdigest(), args.out)


if __name__ == "__main__":
    main()
