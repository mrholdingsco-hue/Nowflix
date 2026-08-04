#!/usr/bin/env bash
#
# NOWFLIX 키오스크 — 잠금 해제 + 앱 삭제 스크립트 (STEP 10)
#
# 태블릿을 평범한 안드로이드 상태로 되돌린다:
#   1) ADB 연결 확인 (여러 대면 선택)
#   2) device owner / 화면 고정 해제
#   3) 앱 삭제
#
# 이 앱은 testOnly 라 device owner 를 ADB 로 해제할 수 있어 공장초기화가 필요 없다.
# 각 단계 오류는 한국어로 원인과 다음 행동을 알려준다.

set -u

PKG="kr.prism.nowflix"
COMP="kr.prism.nowflix/.kiosk.KioskAdminReceiver"

BOLD=$(tput bold 2>/dev/null || true); RESET=$(tput sgr0 2>/dev/null || true)
line() { echo "────────────────────────────────────────────────────────"; }
step() { echo; line; echo "${BOLD}$*${RESET}"; line; }
ok()   { echo "  ✅ $*"; }
warn() { echo "  ⚠️  $*"; }
fail() { echo; echo "  ❌ $*"; echo; exit 1; }

command -v adb >/dev/null 2>&1 || fail "PC에 adb 가 설치되어 있지 않습니다. (docs/설치가이드.md 참고)"

step "1) 태블릿 연결 확인"
adb start-server >/dev/null 2>&1
mapfile -t DEVICES < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
if [ "${#DEVICES[@]}" -eq 0 ]; then
  fail "연결된 태블릿이 없습니다. USB 연결과 USB 디버깅을 확인하세요."
elif [ "${#DEVICES[@]}" -eq 1 ]; then
  SERIAL="${DEVICES[0]}"; ok "태블릿 연결됨: $SERIAL"
else
  echo "  태블릿이 여러 대 연결됐습니다. 해제할 태블릿을 고르세요:"
  i=1; for d in "${DEVICES[@]}"; do
    MODEL=$(adb -s "$d" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    echo "    $i) $d  ($MODEL)"; i=$((i+1))
  done
  printf "  번호 입력 > "; read -r CHOICE
  if ! [[ "$CHOICE" =~ ^[0-9]+$ ]] || [ "$CHOICE" -lt 1 ] || [ "$CHOICE" -gt "${#DEVICES[@]}" ]; then
    fail "잘못된 번호입니다. 1~${#DEVICES[@]} 중에서 다시 선택하세요."
  fi
  SERIAL="${DEVICES[$((CHOICE-1))]}"; ok "선택된 태블릿: $SERIAL"
fi
ADB="adb -s $SERIAL"

if ! $ADB shell pm list packages 2>/dev/null | tr -d '\r' | grep -qx "package:$PKG"; then
  ok "이 태블릿에는 NOWFLIX 앱이 설치돼 있지 않습니다. 할 일이 없습니다."
  exit 0
fi

step "2) 잠금(device owner / 화면 고정) 해제"
$ADB shell am force-stop "$PKG" >/dev/null 2>&1 || true
$ADB shell dpm remove-active-admin "$COMP" >/dev/null 2>&1 || true
$ADB shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
sleep 1
OWNERS=$($ADB shell dpm list-owners 2>/dev/null | tr -d '\r')
if echo "$OWNERS" | grep -q "$PKG"; then
  warn "device owner 가 아직 남아 있습니다. 재부팅 시 재주장했을 수 있습니다."
  echo "     앱을 먼저 삭제한 뒤 다시 해제를 시도합니다."
else
  ok "device owner / 화면 고정 해제됨"
fi

step "3) 앱 삭제"
if $ADB uninstall "$PKG" >/dev/null 2>&1; then
  ok "앱 삭제 완료"
else
  warn "1차 삭제 실패 — device owner 재해제 후 재시도합니다."
  $ADB shell dpm remove-active-admin "$COMP" >/dev/null 2>&1 || true
  if $ADB uninstall "$PKG" >/dev/null 2>&1; then
    ok "앱 삭제 완료 (재시도)"
  else
    fail "앱 삭제에 실패했습니다.
     태블릿을 재부팅한 뒤 이 스크립트를 다시 실행하거나,
     docs/설치가이드.md '실패했을 때 확인할 것' 을 참고하세요."
  fi
fi

# 최종 확인
OWNERS2=$($ADB shell dpm list-owners 2>/dev/null | tr -d '\r')
echo
if echo "$OWNERS2" | grep -q "$PKG"; then
  warn "device owner 흔적이 남아 있습니다. 태블릿을 재부팅한 뒤 다시 실행하면 깨끗해집니다."
  exit 1
fi
line
echo "  ✅ 완료. 태블릿이 평범한 안드로이드 상태로 돌아갔습니다. (공장초기화 불필요)"
line
