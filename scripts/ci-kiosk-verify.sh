#!/usr/bin/env bash
# Runs INSIDE the android-emulator-runner (adb + a booted emulator on PATH). Provisions the app as
# device owner and verifies the kiosk lock-down + release, writing PASS/FAIL for each check to
# kiosk-summary.txt (published to the ci-screenshots branch). Safe to run twice: it re-installs and
# truncates the summary each time, so a second boot attempt overwrites a partial first attempt.
#
# The debug APK must already be built (assembleDebug step) before this runs. This is the kiosk
# lock-down job's most important check — the emulator here uses the SAME API 30 google_apis config
# that boots reliably for the screenshot job (API 34 was flaky and left the summary empty).
set -x

APK=app/build/outputs/apk/debug/app-debug.apk
COMP=kr.prism.nowflix/.kiosk.KioskAdminReceiver
PKG=kr.prism.nowflix
PASS=0; FAIL=0
SUMMARY="${GITHUB_WORKSPACE:-$PWD}/kiosk-summary.txt"; : > "$SUMMARY"

check() { # check "<label>" <0-if-pass>
  if [ "$2" -eq 0 ]; then L="PASS  $1"; PASS=$((PASS+1));
  else L="FAIL  $1"; FAIL=$((FAIL+1)); fi
  echo "$L"; echo "$L" >> "$SUMMARY"
}
focus() { adb shell dumpsys window 2>/dev/null | grep -m1 -i mCurrentFocus; }

echo "::group::Install + device owner"
adb install -r -t "$APK"
# Emulator (google_apis, no Play) has no accounts, so device-owner provisioning succeeds.
adb shell dpm set-device-owner "$COMP"
adb shell dpm list-owners || true
echo "::endgroup::"

echo "::group::Launch + settle"
adb shell am start -n "$PKG/.MainActivity"
sleep 8
adb logcat -d -s Nowflix:* KioskController:* | tail -n 40
echo "::endgroup::"

echo "==================  KIOSK LOCK CHECKS  =================="

# (1) App entered FULL_LOCK mode.
adb logcat -d -s Nowflix:I | grep -q "enterKioskLock: mode=FULL_LOCK"
check "(1) FULL_LOCK mode entered" $?

# (2) Lock task is actually active (state != NONE).
LTS=$(adb shell dumpsys activity activities | grep -m1 -i mLockTaskModeState || true)
echo "    lockTaskModeState: $LTS"
echo "$LTS" | grep -qiE "LOCKED|PINNED"
check "(2) lock task active" $?

# Under a LOCKED lock task the app physically cannot be left, so the app must stay the focused
# window after HOME/RECENTS/shade. Poll rather than read once: mCurrentFocus is briefly null
# during the emulator's GC pauses, which made a single read flaky (passed 6/6 one run, 4/6 the
# next with identical code). Polling for the kiosk to hold focus is the same contract, not a
# weaker one — if it had truly escaped it would never come back while still LOCKED.
still_locked_on_kiosk() { # 0 if the app holds focus within ~8s AND lock task is still LOCKED
  for _ in $(seq 1 16); do
    if focus | grep -q "$PKG"; then
      adb shell dumpsys activity activities | grep -m1 -i mLockTaskModeState | grep -qiE "LOCKED|PINNED" && return 0
    fi
    sleep 0.5
  done
  return 1
}

# (3) HOME and RECENTS key events are swallowed (focus stays on our app, lock still LOCKED).
BEFORE=$(focus); echo "    focus before: $BEFORE"
adb shell input keyevent KEYCODE_HOME
adb shell input keyevent KEYCODE_APP_SWITCH
still_locked_on_kiosk
RES3=$?
echo "    focus after HOME+RECENTS: $(focus)"
check "(3) HOME + RECENTS ignored (still focused on kiosk)" $RES3

# (4) Status bar / notification shade is inaccessible under the lock.
adb shell cmd statusbar expand-notifications >/dev/null 2>&1 || true
still_locked_on_kiosk
RES4=$?
echo "    focus after shade attempt: $(focus)"
check "(4) status bar / shade blocked" $RES4

echo "==================  RELEASE (hospital hand-off safety net)  =================="
# The app is testOnly=true, so device ownership can always be relinquished with adb —
# the documented recovery (docs/kiosk-recovery.md). This is the exact "완전 해제" outcome:
# owner removed and the device usable again.
adb shell dpm remove-active-admin "$COMP" || true
sleep 2
OWNERS=$(adb shell dpm list-owners 2>/dev/null || true)
echo "    owners after release: $OWNERS"
echo "$OWNERS" | grep -q "$PKG" && REL_OWNER=1 || REL_OWNER=0
check "(5) device owner released" $REL_OWNER

# Device usable again: after release + HOME, lock task drops back to NONE.
adb shell input keyevent KEYCODE_HOME
sleep 2
LTS2=$(adb shell dumpsys activity activities | grep -m1 -i mLockTaskModeState || true)
echo "    lockTaskModeState after release: $LTS2"
echo "$LTS2" | grep -qi "NONE"
check "(6) lock task released, device usable again" $?

echo "========================================================"
echo "RESULT: $PASS passed, $FAIL failed" | tee -a "$SUMMARY"

adb logcat -d > kiosk-logcat.txt || true
[ "$FAIL" -eq 0 ]
