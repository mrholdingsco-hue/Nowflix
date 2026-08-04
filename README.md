# NOWFLIX Kiosk

병원 대기실 태블릿용 **넷플릭스 스타일 키오스크 앱**. 유튜브 재생목록을 파트별로
보여주고, 태블릿을 앱 하나에 잠근다(device owner 기반 하드 락다운). 파트·재생목록·
설정은 별도의 **관리자 웹**에서 바꾸며, 태블릿은 이를 주기적으로 받아와 반영한다.

- 앱: Android (Kotlin, Jetpack Compose), `applicationId = kr.prism.nowflix`, minSdk 29 / targetSdk 34
- 관리자 웹: Next.js (App Router) + Supabase, Vercel(서울 리전) 배포
- 데이터: Supabase(파트/설정/포스터) + YouTube Data API v3(영상 목록)

> 비개발자용 문서는 [`docs/`](docs/) 에 있습니다 →
> [설치가이드](docs/설치가이드.md) · [운영가이드](docs/운영가이드.md) · [인계문서](docs/인계문서.md)

---

## 저장소 구조

```
nowflix-kiosk/
├─ app/                    # 안드로이드 태블릿 앱 (키오스크)
│  └─ src/main/java/kr/prism/nowflix/
│     ├─ MainActivity.kt   # 앱 셸 + 화면 전환 배선 + 원격 설정 로딩
│     ├─ ui/               # 홈/상세/플레이어/관리자 Compose 화면
│     ├─ data/             # YouTube·Supabase 연동, 캐시, DTO/매퍼
│     ├─ player/           # 재생 큐 · 진행바 · 유휴 복귀 타이머(순수 로직)
│     └─ kiosk/            # device owner 잠금, PIN 게이트, 부팅 리시버
├─ admin-web/              # 관리자 웹 (Next.js) — Vercel: nowflix-admin
├─ supabase/migrations/    # DB 스키마 + 시드 (parts / settings / web_auth)
├─ scripts/                # 설치·해제·CI 스크립트
│  ├─ install.sh           # 태블릿 1대 끝까지 세팅 (연결→설치→잠금→확인)
│  └─ uninstall.sh         # 잠금 해제 + 앱 삭제
├─ docs/                   # 인계 문서 (한국어, 비개발자용)
├─ dist/                   # 릴리즈 산출물 (nowflix-1.0.0.apk)
├─ references/             # 조사 결과 캐시 (재조사 금지)
├─ ci-viewer/              # (임시) CI 스크린샷 뷰어 — 납품 후 삭제 대상
└─ .github/workflows/      # CI (계측/스크린샷/키오스크 잠금 검증)
```

---

## 빌드 방법

### 사전 준비 (`local.properties`, gitignored)
저장소에는 키·서명 정보가 들어 있지 않다. 루트에 `local.properties` 를 만들고 채운다:

```properties
sdk.dir=/path/to/android-sdk
YOUTUBE_API_KEY=<YouTube Data API v3 키>
SUPABASE_URL=<https://xxxx.supabase.co>
SUPABASE_ANON_KEY=<Supabase anon 키>

# 릴리즈 서명 (릴리즈 빌드에만 필요)
RELEASE_STORE_FILE=nowflix-release.jks
RELEASE_STORE_PASSWORD=<keystore 비밀번호>
RELEASE_KEY_ALIAS=nowflix
RELEASE_KEY_PASSWORD=<key 비밀번호>
```

키 중 하나라도 없으면 그 기능만 조용히 비활성화되고 빌드는 계속된다(디버그 개발 편의).
`nowflix-release.jks` 가 없으면 릴리즈 APK는 **미서명**으로 나온다.

### 디버그 빌드
```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # 순수 단위 테스트
```

### 릴리즈 빌드 (서명)
```bash
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
# 서명 검증:
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs \
    app/build/outputs/apk/release/app-release.apk
```
- `versionCode 1` / `versionName "1.0.0"`
- 난독화·리소스 축소 **끔**(`minifyEnabled=false`, `shrinkResources=false`) — WebView 탐색/직렬화 보호
- `debuggable false`
- `android:testOnly="true"` **유지** → 잠금 사고 시 공장초기화 없이 ADB로 복구 가능.
  대신 설치·업데이트는 항상 **`adb install -t`** 필요(스크립트가 처리).

### 관리자 웹
```bash
cd admin-web && npm install && npm run dev     # 로컬
# 프로덕션: Vercel 프로젝트 nowflix-admin (환경변수 5종은 Vercel에 저장)
```

---

## 태블릿에 설치

```bash
./scripts/install.sh        # 연결 확인 → 기존 앱 정리 → 설치 → device owner → 실행 → 결과 확인
./scripts/uninstall.sh      # 잠금 해제 + 앱 삭제
```
비개발자용 단계별 설명은 [`docs/설치가이드.md`](docs/설치가이드.md).
device owner 지정은 **계정이 하나도 없는 태블릿**에서만 성공한다(설정 → 계정 전부 삭제).

---

## 문서

| 문서 | 내용 |
|---|---|
| [docs/설치가이드.md](docs/설치가이드.md) | adb 설치부터 태블릿 5대 세팅까지 (윈도우 기준, 비개발자용) |
| [docs/운영가이드.md](docs/운영가이드.md) | 관리자 웹으로 재생목록·파트·복귀시간·PIN 변경 |
| [docs/인계문서.md](docs/인계문서.md) | 시스템 구성, 계정, 기본 비번/PIN, keystore, 문제 해결 순서, 유지보수 |
| [docs/kiosk-recovery.md](docs/kiosk-recovery.md) | 잠금 해제/복구 ADB 명령 모음 |
| [docs/onsite-checklist.md](docs/onsite-checklist.md) | 설치 현장 점검표 |

진행 이력은 [`PROGRESS.md`](PROGRESS.md) (STEP 1~10).
