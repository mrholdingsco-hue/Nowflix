#!/usr/bin/env bash
# Runs INSIDE the android-emulator-runner (adb + a booted emulator on PATH). Captures the seven
# kiosk screens, pulls them off the device, and leaves diagnostics for the token-less watcher.
# Called by the screenshot job; safe to invoke twice (retry) — it just overwrites its outputs.
set -x
set +e

echo "emulator script started $(date -u +%H:%M:%S)" | tee booted.txt

# Root adb so we can read the app's Android/data (scoped storage blocks the shell user on API 30+,
# which is why earlier runs pulled zero screenshots). google_apis images are userdebug.
adb root && adb wait-for-device && sleep 3

# Tablet lobby orientation: force landscape so the captured screens match the field.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1

# 1) DEDICATED screenshot capture + pull immediately, so nothing downstream can block or wipe it.
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=kr.prism.nowflix.ScreenshotTest \
  --stacktrace 2>&1 | tee screenshot-capture.log
SHOT_EXIT=${PIPESTATUS[0]}

# The screenshots are written to the app's INTERNAL files dir (see ScreenshotTest.capture()).
# Pull it as root; fall back to run-as (debuggable app); then locate them anywhere as a last resort.
APP_FILES=/data/data/kr.prism.nowflix/files
adb shell ls -la "$APP_FILES/screenshots" > device-screenshots-ls.txt 2>&1 || true
echo "---- device screenshots dir ----"; cat device-screenshots-ls.txt
adb pull "$APP_FILES/screenshots" ./ 2>/dev/null || true
if ! ls screenshots/*.png >/dev/null 2>&1; then
  echo "root pull empty; trying run-as tar"
  mkdir -p screenshots
  adb exec-out run-as kr.prism.nowflix tar c -C files screenshots 2>/dev/null | tar x 2>/dev/null || true
fi
if ! ls screenshots/*.png >/dev/null 2>&1; then
  echo "still empty; searching the device for the PNGs"
  adb shell 'find /data /sdcard /storage -type f -name "0*_*.png" 2>/dev/null; find /data /sdcard /storage -type f -name "07_admin.png" 2>/dev/null' > device-find.txt 2>&1 || true
  cat device-find.txt
fi
ls -al screenshots || echo "no screenshots dir"
adb logcat -d > instrumented-logcat.txt 2>/dev/null || true

# 2) Full behavioural suite (best-effort; capped so it can't eat the whole job budget). Its report
#    is uploaded for inspection, but a failure here never affects the screenshots pulled above.
timeout 1500 ./gradlew connectedDebugAndroidTest --stacktrace 2>&1 | tee connected.log
BEHAVIOR_EXIT=${PIPESTATUS[0]}

echo "SHOT_EXIT=$SHOT_EXIT BEHAVIOR_EXIT=$BEHAVIOR_EXIT"
# Fail the step only if no screenshots were produced, so the retry attempt kicks in.
ls screenshots/*.png >/dev/null 2>&1
