package kr.prism.nowflix

import androidx.activity.ComponentActivity

/**
 * A plain host activity locked to landscape (see androidTest/AndroidManifest.xml), used by the
 * screenshot and flow tests. The real kiosk runs landscape (`MainActivity` is `sensorLandscape`),
 * but a bare `ComponentActivity` follows the emulator's rotation — which on the landscape-natural
 * tablet AVD came up portrait, so captures were 1800×2400. Forcing landscape here makes the
 * captured window match the field regardless of how the emulator booted.
 */
class LandscapeActivity : ComponentActivity()
