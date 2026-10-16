plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.nomanr.plugin.lumo")
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Where the backend lives. Override per device without touching code, e.g.
//   ./gradlew installDebug -Psignai.backendWsUrl=ws://192.168.1.50:8080/ws/translate
// Default is the Android emulator's alias for the host machine. A real device
// needs the host's LAN address, never 127.0.0.1.
val backendWsUrl: String =
    (project.findProperty("signai.backendWsUrl") as String?) ?: "ws://10.0.2.2:8080/ws/translate"

android {
    namespace = "com.signai.webview"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.signai.webview"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "BACKEND_WS_URL", "\"$backendWsUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Backend translation session (ws://…/ws/translate).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.foundation:foundation-layout")
    api("androidx.compose.ui:ui-util")
    api("androidx.compose.ui:ui-tooling")
    api("androidx.compose.ui:ui-tooling-preview")
    api("androidx.compose.material:material-ripple:1.7.6")

    implementation("io.github.dev778g-me:phosphoricons-regular-android:1.0.5")

    implementation("androidx.webkit:webkit:1.12.1")
}
