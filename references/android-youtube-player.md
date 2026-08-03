# android-youtube-player (PierfrancescoSoffritti)

Source: https://github.com/PierfrancescoSoffritti/android-youtube-player (README @ master),
Maven Central `com.pierfrancescosoffritti.androidyoutubeplayer/core`. Checked 2026-08.

## Version
- Latest stable: **13.0.0**
- Dependency: `implementation 'com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0'`
- On Maven Central (settings.gradle already has mavenCentral()).

## v13 API notes
- `IFramePlayerOptions.Builder(context)` — **v13 requires a Context arg** (changed from older versions).
  - `.controls(0)` hide web UI controls, `.rel(0)` related from same channel only,
    `.fullscreen(0)` no fullscreen button, `.ccLoadPolicy(0)` captions off, `.autoplay(1)` autoplay.
- Manual init (no web UI):
  ```kotlin
  view.setEnableAutomaticInitialization(false)
  view.initialize(listener, options)   // listener = AbstractYouTubePlayerListener
  ```
- `fullscreen(1)` REQUIRES a FullscreenListener; with `fullscreen(0)` none is needed.
- Player methods: `loadVideo(id, startSec)` (autoplays), `cueVideo(id, startSec)` (no autoplay),
  `seekTo(sec)`, `play()`, `pause()`.
- `AbstractYouTubePlayerListener`: `onReady(player)`, `onStateChange(player, state)`,
  `onCurrentSecond(player, sec)`, `onVideoDuration(player, dur)`.
- `PlayerConstants.PlayerState`: UNKNOWN, UNSTARTED, ENDED, PLAYING, PAUSED, BUFFERING, VIDEO_CUED.
- Lifecycle: add `youTubePlayerView` as a LifecycleObserver (or call `release()` on dispose).

## Kiosk lock-down findings (verified against library source)
- `WebViewYouTubePlayer` extends `WebView`. It sets **no WebViewClient** (only a WebChromeClient
  for fullscreen). The iframe is loaded via `loadDataWithBaseURL(origin, html, ...)` and playback is
  driven through a JS bridge (addJavascriptInterface) — NOT through WebViewClient callbacks.
  => We can safely attach OUR OWN WebViewClient + WebChromeClient without breaking the player.
- The internal WebView is not exposed publicly; reach it by recursively scanning the
  YouTubePlayerView's child views for the first `WebView` instance.
- Blocking approach used in NOWFLIX (two independent layers):
  1. Full-surface transparent Compose overlay that consumes every pointer event, so no touch ever
     reaches the iframe (YouTube logo / video title / "Watch on YouTube" become physically
     un-tappable). Our custom controls sit above this shield. This is the PRIMARY, library-agnostic block.
  2. On the discovered WebView: WebViewClient.shouldOverrideUrlLoading returns true for any URL that
     isn't the embed itself (blocks navigation), WebChromeClient.onCreateWindow returns false
     (blocks target=_blank popups), long-click disabled. Defense-in-depth for programmatic escapes.
- End screen: on `PlayerState.ENDED` load the next video immediately (before recommendations render);
  last video -> return to the list screen.
