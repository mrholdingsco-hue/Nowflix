#!/usr/bin/env bash
#
# NOWFLIX 키오스크 — 태블릿 1대 설치 스크립트 (STEP 10)
#
# 태블릿 한 대를 처음부터 끝까지 세팅한다:
#   1) ADB 연결 확인 (여러 대면 선택)
#   2) 기존 앱이 있으면 device owner 해제 후 삭제
#   3) adb install -t 로 설치 (이 앱은 testOnly 라 -t 필수)
#   4) dpm set-device-owner 로 device owner 지정
#   5) 앱 실행
#   6) lock task 상태 + device owner 상태를 조회해 성공 여부를 한국어로 출력
#
# 모든 오류 메시지는 한국어로 원인과 다음 행동을 알려준다.
#
# 사용법:  ./scripts/install.sh [APK경로]
#   APK 경로를 생략하면 dist/ 안의 nowflix-*.apk 중 가장 최신 버전 → 없으면 release APK 를 찾는다.

set -u

PKG="kr.prism.nowflix"
COMP="kr.prism.nowflix/.kiosk.KioskAdminReceiver"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# 색/구분선 (터미널이 지원 안 하면 그냥 빈 값)
BOLD=$(tput bold 2>/dev/null || true); RESET=$(tput sgr0 2>/dev/null || true)
line() { echo "────────────────────────────────────────────────────────"; }
step() { echo; line; echo "${BOLD}$*${RESET}"; line; }
ok()   { echo "  ✅ $*"; }
warn() { echo "  ⚠️  $*"; }
fail() { echo; echo "  ❌ $*"; echo; exit 1; }

# ── APK 경로 결정 ─────────────────────────────────────────────
APK="${1:-}"
if [ -z "$APK" ]; then
  # 버전이 올라가도 스크립트를 고칠 필요가 없도록, dist/ 안의 nowflix-*.apk 중 버전이 가장 높은 것을 쓴다.
  APK=$(ls "$HERE"/dist/nowflix-*.apk 2>/dev/null | sort -V | tail -1)
  if [ -z "$APK" ] && [ -f "$HERE/app/build/outputs/apk/release/app-release.apk" ]; then
    APK="$HERE/app/build/outputs/apk/release/app-release.apk"
  fi
fi
[ -n "$APK" ] && [ -f "$APK" ] || fail "설치할 APK 파일을 찾지 못했습니다.
     dist/ 폴더에 nowflix-1.1.0.apk 가 있는지 확인하거나, 경로를 직접 넘겨주세요:
       ./scripts/install.sh /경로/nowflix-1.1.0.apk"

# ── adb 존재 확인 ─────────────────────────────────────────────
command -v adb >/dev/null 2>&1 || fail "PC에 adb(안드로이드 플랫폼툴)가 설치되어 있지 않습니다.
     docs/설치가이드.md 의 'adb 설치' 부분을 먼저 진행하세요."

step "1) 태블릿 연결 확인"
adb start-server >/dev/null 2>&1
# device 로 잡힌 시리얼만 추출 (unauthorized/offline 제외)
mapfile -t DEVICES < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
UNAUTH=$(adb devices | awk 'NR>1 && $2=="unauthorized" {print $1}')

if [ "${#DEVICES[@]}" -eq 0 ]; then
  if [ -n "$UNAUTH" ]; then
    fail "태블릿이 연결됐지만 'USB 디버깅 허용'을 하지 않았습니다.
     태블릿 화면에 뜬 '이 컴퓨터에서 USB 디버깅을 허용하시겠습니까?' 창에서
     '항상 허용'을 체크하고 확인을 누른 뒤 다시 실행하세요."
  fi
  fail "연결된 태블릿이 없습니다.
     • USB 케이블이 데이터 전송용인지 (충전 전용 케이블 아님)
     • 태블릿에서 개발자 옵션 → USB 디버깅이 켜져 있는지
     확인 후 다시 실행하세요. (docs/설치가이드.md 참고)"
fi

if [ "${#DEVICES[@]}" -eq 1 ]; then
  SERIAL="${DEVICES[0]}"
  ok "태블릿 1대 연결됨: $SERIAL"
else
  echo "  태블릿이 여러 대 연결됐습니다. 설치할 태블릿을 고르세요:"
  i=1; for d in "${DEVICES[@]}"; do
    MODEL=$(adb -s "$d" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    echo "    $i) $d  ($MODEL)"; i=$((i+1))
  done
  printf "  번호 입력 > "; read -r CHOICE
  if ! [[ "$CHOICE" =~ ^[0-9]+$ ]] || [ "$CHOICE" -lt 1 ] || [ "$CHOICE" -gt "${#DEVICES[@]}" ]; then
    fail "잘못된 번호입니다. 1~${#DEVICES[@]} 중에서 다시 선택하세요."
  fi
  SERIAL="${DEVICES[$((CHOICE-1))]}"
  ok "선택된 태블릿: $SERIAL"
fi

ADB="adb -s $SERIAL"
MODEL=$($ADB shell getprop ro.product.model 2>/dev/null | tr -d '\r')
ANDROID=$($ADB shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
echo "     모델: ${MODEL:-알수없음} / 안드로이드 ${ANDROID:-?}"

step "2) 기존 앱 정리"
if $ADB shell pm list packages 2>/dev/null | tr -d '\r' | grep -qx "package:$PKG"; then
  warn "이미 설치된 NOWFLIX 앱이 있습니다. device owner 해제 후 삭제합니다."
  # device owner 였다면 먼저 해제해야 삭제가 가능하다 (testOnly 라 성공)
  $ADB shell dpm remove-active-admin "$COMP" >/dev/null 2>&1 || true
  $ADB shell am force-stop "$PKG" >/dev/null 2>&1 || true
  if $ADB uninstall "$PKG" >/dev/null 2>&1; then
    ok "기존 앱 삭제 완료"
  else
    fail "기존 앱을 삭제하지 못했습니다.
     태블릿을 재부팅한 뒤 다시 실행하거나,
     docs/설치가이드.md '실패했을 때 확인할 것' 을 참고하세요."
  fi
else
  ok "기존 앱 없음 (새 설치)"
fi

step "3) 앱 설치"
echo "     APK: $APK"
INSTALL_OUT=$($ADB install -r -t "$APK" 2>&1)
if echo "$INSTALL_OUT" | grep -q "Success"; then
  ok "설치 완료"
else
  echo "     (adb 출력) $INSTALL_OUT"
  fail "앱 설치에 실패했습니다.
     • 저장공간이 부족하지 않은지
     • APK 파일이 손상되지 않았는지
     확인 후 다시 실행하세요."
fi

step "4) device owner 지정"
DO_OUT=$($ADB shell dpm set-device-owner "$COMP" 2>&1 | tr -d '\r')
if echo "$DO_OUT" | grep -qi "Success"; then
  ok "device owner 지정 완료"
else
  echo "     (dpm 출력) $DO_OUT"
  # 가장 흔한 실패 원인 = 계정이 등록돼 있음
  if echo "$DO_OUT" | grep -qiE "account|already|not allowed|provision"; then
    fail "device owner 지정에 실패했습니다. (가장 흔한 원인: 태블릿에 계정이 등록돼 있음)
     태블릿 설정 → 계정에서 구글 등 모든 계정을 삭제한 뒤,
     이 스크립트를 다시 실행하세요.
     ※ device owner 는 '계정이 하나도 없는' 태블릿에서만 지정됩니다."
  fi
  fail "device owner 지정에 실패했습니다.
     설정 → 계정에서 모든 계정을 삭제하고, 태블릿을 재부팅한 뒤 다시 실행하세요.
     (docs/설치가이드.md 참고)"
fi

step "5) 앱 실행"
$ADB shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
sleep 5
ok "앱을 실행했습니다. 태블릿 화면에 NOWFLIX 메인이 떠야 합니다."

step "6) 설치 결과 확인"
# device owner 상태
OWNERS=$($ADB shell dpm list-owners 2>/dev/null | tr -d '\r')
if echo "$OWNERS" | grep -q "$PKG"; then OWNER_OK=1; else OWNER_OK=0; fi
# lock task 상태
LTS=$($ADB shell dumpsys activity activities 2>/dev/null | grep -m1 -i mLockTaskModeState | tr -d '\r')
if echo "$LTS" | grep -qiE "LOCKED|PINNED"; then LOCK_OK=1; else LOCK_OK=0; fi

echo
if [ "$OWNER_OK" -eq 1 ]; then echo "  ✅ device owner: 지정됨"; else echo "  ❌ device owner: 지정 안 됨"; fi
if [ "$LOCK_OK"  -eq 1 ]; then echo "  ✅ 화면 고정(lock task): 켜짐 (${LTS##*=})";
  else echo "  ⚠️  화면 고정(lock task): 아직 확인 안 됨 ($LTS)"; fi
echo

if [ "$OWNER_OK" -eq 1 ] && [ "$LOCK_OK" -eq 1 ]; then
  line
  echo "  🎉 설치 성공!  이 태블릿은 이제 NOWFLIX 키오스크로 잠겼습니다."
  echo "     • 홈/최근앱 버튼과 상태바가 막혀 앱을 벗어날 수 없습니다."
  echo "     • 관리자 진입: 화면 오른쪽 위 모서리를 3초간 길게 누른 뒤 PIN 입력"
  echo "     • 기본 PIN: 739104  (원격 설정값 / 설정 미수신 시에만 000000)"
  echo "       → 운영가이드에서 반드시 변경하세요."
  line
  exit 0
else
  warn "설치는 됐지만 잠금 상태가 완전하지 않습니다."
  echo "     확인할 것:"
  echo "       1) 태블릿에 계정이 남아 있지 않은지 (설정 → 계정)"
  echo "       2) 화면에 NOWFLIX 앱이 실제로 떠 있는지"
  echo "       3) 태블릿을 재부팅한 뒤 이 스크립트를 다시 실행"
  echo "     그래도 안 되면 docs/설치가이드.md 를 참고하세요."
  exit 1
fi
