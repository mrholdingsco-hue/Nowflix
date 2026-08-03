#!/usr/bin/env bash
# Runs INSIDE the android-emulator-runner (adb + a booted emulator on PATH). Captures the seven
# kiosk screens and pulls them off the device. Safe to invoke twice (retry) — it overwrites outputs.
#
# Why not `gradle connectedAndroidTest`? AGP UNINSTALLS the app right after that task, which wipes
# /data/data/<pkg> (and Android/data) before we can pull — so every capture "passed" yet produced
# zero pullable files. Here we install the APKs, drive the ScreenshotTest with `am instrument`
# (which leaves the app installed), pull the screenshots, and only then clean up.
set -x
set +e

echo "emulator script started $(date -u +%H:%M:%S)" | tee booted.txt

# Root adb so we can read the app's private files dir (see ScreenshotTest.capture()).
adb root && adb wait-for-device && sleep 3

# Tablet lobby orientation: force landscape so the captured screens match the field.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1

PKG=kr.prism.nowflix
RUNNER="$PKG.test/androidx.test.runner.AndroidJUnitRunner"
APP_APK=app/build/outputs/apk/debug/app-debug.apk
TEST_APK=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

# Build both APKs (app + androidTest).
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace 2>&1 | tee build-apks.log

# testOnly=true app → -t required. Install app + instrumentation, keep them installed.
adb install -r -t "$APP_APK"
adb install -r -t "$TEST_APK"

# 1) Drive ONLY the screenshot test via am instrument (does NOT uninstall afterwards).
adb shell am instrument -w -r -e class "$PKG.ScreenshotTest" "$RUNNER" 2>&1 | tee screenshot-capture.log

# 2) App is still installed → pull its private screenshots dir (root; run-as fallback).
APP_FILES=/data/data/$PKG/files
adb shell ls -la "$APP_FILES/screenshots" > device-screenshots-ls.txt 2>&1 || true
echo "---- device screenshots dir ----"; cat device-screenshots-ls.txt
mkdir -p screenshots
adb pull "$APP_FILES/screenshots" ./ 2>/dev/null || true
if ! ls screenshots/*.png >/dev/null 2>&1; then
  echo "root pull empty; trying run-as tar"
  adb exec-out run-as "$PKG" tar c -C files screenshots 2>/dev/null | tar x 2>/dev/null || true
fi
if ! ls screenshots/*.png >/dev/null 2>&1; then
  echo "still empty; searching the device for the PNGs"
  adb shell 'find /data /sdcard /storage -type f -name "0*_*.png" 2>/dev/null; find /data /sdcard /storage -type f -name "07_admin.png" 2>/dev/null' > device-find.txt 2>&1 || true
  cat device-find.txt
fi
ls -al screenshots || echo "no screenshots dir"
adb logcat -d > instrumented-logcat.txt 2>/dev/null || true

# 3) Full behavioural suite (best-effort; capped). This reinstalls/uninstalls the app itself, which
#    is fine — the screenshots were already pulled above.
timeout 1500 ./gradlew connectedDebugAndroidTest --stacktrace 2>&1 | tee connected.log
BEHAVIOR_EXIT=${PIPESTATUS[0]}

# Clean up the manually-installed app.
adb uninstall "$PKG.test" >/dev/null 2>&1 || true
adb uninstall "$PKG" >/dev/null 2>&1 || true

echo "BEHAVIOR_EXIT=$BEHAVIOR_EXIT"
# Fail the step only if no screenshots were produced, so the retry attempt kicks in.
ls screenshots/*.png >/dev/null 2>&1
