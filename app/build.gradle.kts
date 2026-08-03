import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Secrets read from local.properties (gitignored) at build time and injected via
// BuildConfig. Never checked into source, parts.json, or commits. Blank when absent:
// the app still builds; the Supabase fetch just fails silently into cache/asset fallback.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val youtubeApiKey: String = localProps.getProperty("YOUTUBE_API_KEY").orEmpty().trim()
val supabaseUrl: String = localProps.getProperty("SUPABASE_URL").orEmpty().trim()
val supabaseAnonKey: String = localProps.getProperty("SUPABASE_ANON_KEY").orEmpty().trim()

android {
    namespace = "kr.prism.nowflix"
    // 35 is required by the YouTube player library's transitive Compose/lifecycle deps.
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.prism.nowflix"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "YOUTUBE_API_KEY", "\"$youtubeApiKey\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        // Admin web address the operator scans from the tablet's admin screen. Not a secret.
        buildConfigField("String", "ADMIN_WEB_URL", "\"https://nowflix-admin.vercel.app\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Run each instrumented test in its own process. A Compose LazyLayout prefetch callback
        // (Choreographer-based) from a UI test was leaking across the shared instrumentation
        // process and crashing a later pure-data test (ConfigNetworkTest) with "must have a
        // looper!". Per-test isolation contains it; it does not change any assertion.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.youtube.player)
    implementation(libs.zxing.core)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests (STEP 9): drive the real Compose screens on an emulator, feed them
    // fixed fixtures over a local MockWebServer, capture screenshots, and verify the flows.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    // Provides the empty host Activity that createComposeRule() launches.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // Per-test process isolation for connectedAndroidTest (see testOptions above).
    androidTestUtil("androidx.test:orchestrator:1.5.1")
}
