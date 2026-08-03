# ADB device owner / lock task 복구 명령 (근거)

STEP 7 `docs/kiosk-recovery.md` 작성 근거. 재조사 금지 — 이 파일 참조.

## 확인된 명령 (표준 Android `dpm`)
- device owner 지정(설치 시): `adb shell dpm set-device-owner <pkg>/<admin-component>`
  - 예: `adb shell dpm set-device-owner kr.prism.nowflix/.kiosk.KioskAdminReceiver`
  - 계정이 없는 기기에서만 성공. testOnly 앱은 제약이 완화됨.
- device owner / device admin 해제:
  `adb shell dpm remove-active-admin <pkg>/<admin-component>`
  - 예: `adb shell dpm remove-active-admin kr.prism.nowflix/.kiosk.KioskAdminReceiver`
  - **중요**: 일반(비-testOnly) 앱이 device owner이면 adb 셸(uid 2000)에서 이 명령이 거부됨 →
    공장초기화만 남음. 앱을 `android:testOnly="true"`로 빌드하면 adb로 제거 가능.
- 소유자 확인: `adb shell dpm list-owners`
- 앱 강제 종료: `adb shell am force-stop <pkg>`
- 앱 삭제: `adb uninstall <pkg>` (device owner면 먼저 remove-active-admin 후 삭제)
- testOnly 앱 설치: `adb install -t <apk>` (일반 install 은 testOnly 를 거부)

## 근거 (WebSearch, 2026-08)
- https://www.iditect.com/program-example/adb--how-to-remove-setdeviceowner-in-android-dpm.html
- https://www.codestudy.net/blog/how-to-remove-set-device-owner-in-android-dpm/
- https://techblogs.42gears.com/using-adb-for-speeding-up-the-device-owner-enrollment/
- 핵심 요지: `dpm remove-active-admin <component>`; 컴포넌트는 `dpm list-owners`로 확인;
  일부 앱은 재부팅 시 소유권을 재주장하므로 먼저 uninstall 후 remove 반복. testOnly 조건은
  개발/사이드로드 앱 제거의 전제.
