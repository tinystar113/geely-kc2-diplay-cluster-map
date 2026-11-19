#!/system/bin/sh
# U-disk auto-installer: installs only the explicitly named USB installer.
# Triggered by firmware /system/bin/update.sh via d4ad0e79...sh (trigger script).

TRIGGER_NAME="b832bc61472727635baffcf25dd28e9f239273e2"
TS="$(date +%s)"
LOG="$USB/outputInstall_${TS}_$$.txt"

echo "================================" >> "$LOG"
echo "INSTALL START" >> "$LOG"
echo "PID=$$" >> "$LOG"
echo "TIME=$(date)" >> "$LOG"
echo "USB=$USB" >> "$LOG"
echo "================================" >> "$LOG"

if [ "$ES_LOCK_HELD" != "1" ]; then
    echo "ERROR: done.sh called without trigger lock" >> "$LOG"
    exit 10
fi

# Wake the head-unit config page (best-effort; failure is non-fatal).
# WPC_SETTINGS action does not resolve on this KC-2 build, so it is dropped.
# gkuiconfig MainActivity does resolve (MAIN_EXIT=0) and is kept.
am start -n com.ecarx.gkuiconfig/.MainActivity >> "$LOG" 2>&1
echo "MAIN_EXIT=$?" >> "$LOG"

install_one() {
    SRC_APK="$1"
    # Android 4.3's minimal shell does not include basename.
    LABEL="${SRC_APK##*/}"
    STAGE="/data/local/tmp/es_usb_stage_$$.apk"

    echo "--- SOURCE $LABEL ---" >> "$LOG"
    echo "SOURCE=$SRC_APK" >> "$LOG"
    if [ ! -f "$SRC_APK" ]; then
        echo "ERROR: $LABEL missing" >> "$LOG"
        return 20
    fi
    ls -l "$SRC_APK" >> "$LOG" 2>&1

    rm -f "$STAGE"
    cp "$SRC_APK" "$STAGE"
    COPY_RC=$?
    echo "${LABEL}_COPY_RC=$COPY_RC" >> "$LOG"
    if [ "$COPY_RC" -ne 0 ]; then
        echo "ERROR: copy $LABEL to stage failed" >> "$LOG"
        rm -f "$STAGE"
        return 21
    fi

    chmod 644 "$STAGE"

    echo "--- PM INSTALL $LABEL ---" >> "$LOG"
    # Android 4.3's pm may return shell status 0 even for Failure [...].
    # Capture and require PackageManager's explicit success response.
    PM_OUTPUT="$(pm install -rtdg "$STAGE" 2>&1)"
    PM_RC=$?
    echo "$PM_OUTPUT" >> "$LOG"
    echo "${LABEL}_PM_EXIT=$PM_RC" >> "$LOG"
    rm -f "$STAGE"

    if [ "$PM_RC" -ne 0 ] || ! echo "$PM_OUTPUT" | grep -q '^Success$'; then
        echo "${LABEL}_INSTALL FAILED" >> "$LOG"
        return 30
    fi

    echo "${LABEL}_INSTALL OK" >> "$LOG"
    return 0
}

# Strict allowlist: ignore ES.apk and every other APK in the U-disk root.
TARGET_APK="USBInstaller.apk"
TARGET_PKG="com.example.usbinstaller"
APK="$USB/$TARGET_APK"
if [ ! -f "$APK" ]; then
    echo "ERROR: required $TARGET_APK missing" >> "$LOG"
    exit 20
fi

install_one "$APK"
RC=$?
if [ "$RC" -ne 0 ]; then
    echo "INSTALL ABORT at $APK rc=$RC" >> "$LOG"
    exit "$RC"
fi

echo "INSTALLED 1/1 APK: $TARGET_APK" >> "$LOG"

verify_target() {
    LABEL="$1"
    echo "--- VERIFY $LABEL $TARGET_PKG ---" >> "$LOG"
    if pm path "$TARGET_PKG" 2>/dev/null | grep -q '^package:'; then
        echo "VERIFY_${LABEL}_OK $TARGET_PKG" >> "$LOG"
        return 0
    fi
    echo "VERIFY_${LABEL}_FAILED $TARGET_PKG" >> "$LOG"
    return 1
}

verify_target "0s" || exit 31
sleep 5
verify_target "5s" || exit 32
sleep 10
verify_target "15s" || exit 33

echo "INSTALL VERIFIED" >> "$LOG"

# Keep the exact trigger directory.  update.sh is started at boot and waits
# for this path, so converting it to _done would disable future U-disk runs.
echo "TRIGGER RETAINED=$USB/$TRIGGER_NAME" >> "$LOG"
echo "FINISH=$(date)" >> "$LOG"
exit 0
