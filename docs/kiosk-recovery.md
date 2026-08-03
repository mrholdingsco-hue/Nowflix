# 키오스크 복구 안내 (병원 인계용)

NOWFLIX 키오스크 앱(`kr.prism.nowflix`)이 잠긴 상태에서 빠져나오는 방법.
**순서대로 시도**한다. 대부분은 1번(PIN)으로 해결된다. ADB(3번)는 최후 수단이다.

관리자 PIN 기본값: **000000** (원격/캐시에 설정된 PIN이 있으면 그 값이 우선).

---

## 1. PIN으로 해제 (가장 먼저)
1. 화면 **우상단 모서리를 3초간 길게** 누른다. 어느 화면에서든 동작한다.
2. PIN 입력창에 관리자 PIN을 입력한다(기본 `000000`).
3. 5회 연속 틀리면 30초간 입력이 잠긴다(영구 잠금 아님, 30초 뒤 자동 해제).
4. 통과하면 **관리자 화면**으로 들어간다.

## 2. 관리자 화면에서 완전 해제
관리자 화면의 **"키오스크 모드 완전 해제"** 버튼을 누르고 확인한다.
- 화면 고정 해제 → 상태바/잠금화면 복구 → 홈 런처 기본 설정 해제 →
  device owner 권한 해제 → 앱 종료 순으로 실행된다.
- 실행 후 태블릿은 **공장초기화 없이 평범한 안드로이드 상태**로 돌아간다. (병원 인계 안전장치)
- 단순히 앱만 닫으려면 **"앱 종료"** 버튼을 쓴다(권한은 유지).

---

## 3. ADB 최후 수단 (PIN도 앱도 못 쓸 때)
PC에 USB 디버깅으로 태블릿을 연결하고 아래 명령을 실행한다.
이 앱은 `android:testOnly="true"`로 빌드되어 있어 **공장초기화 없이** ADB로 device owner를 제거할 수 있다.

```bash
# (a) 현재 소유자 확인 — 컴포넌트 이름 오타 방지
adb shell dpm list-owners

# (b) device owner / device admin 권한 해제
adb shell dpm remove-active-admin kr.prism.nowflix/.kiosk.KioskAdminReceiver

# (c) 앱 강제 종료
adb shell am force-stop kr.prism.nowflix

# (d) 앱 삭제 (반드시 (b)로 device owner를 먼저 해제한 뒤)
adb uninstall kr.prism.nowflix
```

메모
- (b)가 "not allowed"로 거부되면 앱이 재부팅 시 소유권을 재주장했을 수 있다.
  `adb uninstall kr.prism.nowflix` → `adb shell dpm remove-active-admin ...` 순으로 다시 시도한다.
- 앱을 다시 설치할 때는 testOnly 앱이므로 **`adb install -t app-debug.apk`** 로 설치한다.
- device owner 재지정: `adb shell dpm set-device-owner kr.prism.nowflix/.kiosk.KioskAdminReceiver`
  (기기에 계정이 없어야 성공).

### 제약 (정직하게)
- 만약 이 앱이 testOnly가 **아니었다면**, device owner는 ADB로 제거할 수 없고 **공장초기화**만 남는다.
  이번 빌드는 이 위험을 없애기 위해 의도적으로 testOnly로 만들었다.
- 근거 명령/출처는 `references/adb-device-owner.md` 참조.
