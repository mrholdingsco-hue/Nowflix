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

        buildConfigField("String", "YOUTUBE_API_KEY", "\"$youtubeApiKey\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
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
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
