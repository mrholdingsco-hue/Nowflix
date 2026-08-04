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

// Release signing (STEP 10). Read from local.properties — the keystore + passwords are the
// hospital's and are gitignored, never in source. When the store file is missing (CI, a dev
// machine without the keystore) the release build stays unsigned and installable as debug,
// so the project still builds everywhere.
val releaseStoreFile: String = localProps.getProperty("RELEASE_STORE_FILE").orEmpty().trim()
val releaseStorePassword: String = localProps.getProperty("RELEASE_STORE_PASSWORD").orEmpty()
val releaseKeyAlias: String = localProps.getProperty("RELEASE_KEY_ALIAS").orEmpty().trim()
val releaseKeyPassword: String = localProps.getProperty("RELEASE_KEY_PASSWORD").orEmpty()
val hasReleaseSigning: Boolean =
    releaseStoreFile.isNotEmpty() && rootProject.file(releaseStoreFile).exists()

android {
    namespace = "kr.prism.nowflix"
    // 35 is required by the YouTube player library's transitive Compose/lifecycle deps.
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.prism.nowflix"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "YOUTUBE_API_KEY", "\"$youtubeApiKey\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        // Admin web address the operator scans from the tablet's admin screen. Not a secret.
        buildConfigField("String", "ADMIN_WEB_URL", "\"https://nowflix-admin.vercel.app\"")
    }

    signingConfigs {
        // Only registered when the keystore is actually present (see hasReleaseSigning). The
        // hospital's release key + passwords live in gitignored local.properties.
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Obfuscation/shrinking deliberately OFF: no upside for a sideloaded kiosk, and a
            // real risk of breaking the WebView view-tree scan (player exit-lock) or
            // kotlinx.serialization DTOs. debuggable stays false so no debug logging/attach.
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signed with the hospital's release key when available; otherwise left unsigned
            // (the build still succeeds — sign later on a machine that has the keystore).
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    lint {
        // Work around a lint tooling crash: androidx.lifecycle's NonNullableMutableLiveDataDetector
        // throws IncompatibleClassChangeError under this AGP/lint version (a lint/library version
        // mismatch, not our code — the app uses no LiveData). It crashes on class linkage before the
        // issue filter applies, so `disable` can't suppress it; lint-vital must simply not run on the
        // release build. Correctness is covered by unit + instrumented CI, not by lint-vital, so this
        // only drops a broken tooling step. Run `./gradlew lint` manually for a normal lint pass.
        checkReleaseBuilds = false
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
