package kr.prism.nowflix

import androidx.activity.ComponentActivity

/**
 * A plain host activity locked to landscape (see src/debug/AndroidManifest.xml), used by the
 * Compose screenshot and flow tests. It lives in the **debug** source set — not androidTest — so
 * it belongs to the app package (`kr.prism.nowflix`), the instrumentation's target process;
 * declared in the test package it fails to launch ("Intent resolved to different process").
 *
 * The real kiosk runs landscape (`MainActivity` is `sensorLandscape`), but a bare
 * `ComponentActivity` follows the emulator's rotation — which on the landscape-natural tablet AVD
 * came up portrait, so captures were 1800×2400. Forcing landscape here makes the captured window
 * match the field regardless of how the emulator booted.
 */
class LandscapeActivity : ComponentActivity()
