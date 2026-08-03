# NOWFLIX Kiosk — Progress

## STEP 1 (2026-08-03) — env + scaffold — DONE (repo push pending)
- Env: OpenJDK 17, Android SDK at /opt/android-sdk (platform-tools, android-34, build-tools 34.0.0), Gradle 8.11.1. ANDROID_HOME/PATH in ~/.bashrc.
- Scaffold ~/nowflix-kiosk: Gradle KDSL + version catalog (AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01). appId kr.prism.nowflix, minSdk 29, target/compileSdk 34. Single MainActivity: sensorLandscape + fullscreen, black/red-band/"STEP 1 OK" placeholder.
- Build: `./gradlew assembleDebug` OK (~19m, slow VM JVM). APK: app/build/outputs/apk/debug/app-debug.apk (24 MB).
- Git: committed on main (d86a9e0). remote=git@gh-cla14:mrholdingsco-hue/nowflix-kiosk.git.
- BLOCKED: GitHub repo not created — no gh CLI / no API token; SSH can push but not create repo. Create private repo `mrholdingsco-hue/nowflix-kiosk` then `git push -u origin main`.

## STEP 2 (2026-08-03) — main screen (Netflix-style home) — DONE (assets + push pending)
- Data-driven UI: `app/src/main/assets/parts.json` (6 parts, id/title/thumbnail/playlistId). Home renders from this list — adding a 7th/8th part is data-only, no code change. TOP N label + card count both derive from `parts.size`.
- Compose (`MainActivity.kt`): 18%-height red (#E50914) top band = NOWFLIX wordmark (left) + QR placeholder (right); body #0B0B0C; red "TOP N" chip + "오늘 대한민국의 TOP N 콘텐츠"; LazyRow of cards. Card width = `CardMetrics.cardWidthDp` (6 fill row, 7+ scroll). Press: 1.06x scale + #E50914 border, tween 160ms, 4dp corners, single-line title. Tap logs only (`Log.d "Nowflix"`) — no nav yet (STEP 3).
- Split for testability: `Part`/`PartsRepository.parse` (kotlinx-serialization), `CardMetrics.cardWidthDp` (plain Float dp, no Android). JVM tests: 6 pass (CardMetrics 4 + PartsRepository 2), covers 6- and 9-item cases.
- Build tuning (gradle.properties): +daemon +parallel; Gradle -Xmx1536m + Kotlin daemon -Xmx1024m (was -Xmx2048m, swap-thrashing on 3.8GB VM). Times: cold assembleDebug 9m (VM is I/O-bound, CPU ~idle), warm no-op 11s (config-cache). Deps added: kotlinx-serialization-json 1.7.3, compose foundation, junit.
- APK: app/build/outputs/apk/debug/app-debug.apk (24.8 MB). Committed on main.
- BLOCKED (user-side): (1) all asset URLs in the STEP 2 brief were literal `[URL]` placeholders — main-screen AI, 6 thumbnails, font not downloaded; logo/QR/thumbnails are Compose placeholders, swap in by dropping `part_0X_*.png` into res/drawable-nodpi + logo/QR PNGs. (2) GitHub repo `mrholdingsco-hue/nowflix-kiosk` still doesn't exist (push = "Repository not found"); no gh CLI/token to create it. Create the private repo, then `git push -u origin main`.
