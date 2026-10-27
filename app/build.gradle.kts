plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.nomanr.plugin.lumo")
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// ── Dónde vive el backend ──────────────────────────────────────────────────
//
// El SRS pide que todo lo que sale del dispositivo vaya cifrado (`main.tex:1095`)
// y que el enlace con el backend sea WSS (`:1100`), así que **un build de
// release solo puede apuntar a `wss://`**. Para desarrollar hace falta `ws://`
// contra la máquina que corre el backend, y ese es el bypass: la propiedad
// `-Psignai.backendWsUrl`, que **solo** lee el build type `debug`.
//
//   ./gradlew installDebug -Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate
//
val backendWsUrlOverride: String? = project.findProperty("signai.backendWsUrl") as String?

/**
 * Default de debug: el alias del emulador a la máquina de desarrollo, para que
 * `./gradlew installDebug` funcione sin pasar nada. Un teléfono físico necesita
 * la dirección de la red local o `adb reverse`, nunca `127.0.0.1` a secas.
 */
val debugBackendWsUrl = "ws://10.0.2.2:8080/ws/translate"

/**
 * Default de release: un host que **no puede resolver** (`.invalid`, RFC 2606).
 *
 * Todavía no hay dominio deployed, y inventar uno sería peor que no tener ninguno:
 * un APK de release sin configurar habla con nadie en vez de hablar con el host
 * de otra persona, y el fallo es ruidoso. Cuando el backend esté desplegado se
 * pasa `-Psignai.backendWsUrl=wss://…` y deja de hacer falta.
 */
val releaseBackendWsUrl = "wss://backend.invalid/ws/translate"

/**
 * Default de `demo`: el camino **USB**, por `adb reverse tcp:8443 tcp:443`.
 *
 * El 8443 no es capricho: en el teléfono `adb reverse tcp:443 tcp:443` falla con
 * `cannot bind listener: Permission denied` (verificado), así que el puerto del
 * lado del teléfono tiene que ser uno no privilegiado. El certificado no depende
 * del puerto: cubre la identidad `127.0.0.1`.
 *
 * Se elige USB y no Wi-Fi como default porque es determinista: no depende de que
 * la IP por DHCP siga siendo la misma ni de una regla del cortafuegos. El camino
 * por Wi-Fi se pasa explícitamente, con la IP de la máquina:
 *
 *   ./gradlew installDemo -Psignai.backendWsUrl=wss://192.168.80.49/ws/translate
 */
val demoBackendWsUrl = "wss://127.0.0.1:8443/ws/translate"

if (backendWsUrlOverride != null && !backendWsUrlOverride.startsWith("wss://")) {
    logger.lifecycle(
        "SignAI: release y demo ignoran -Psignai.backendWsUrl=$backendWsUrlOverride porque no " +
            "es wss://; release usa $releaseBackendWsUrl y demo usa $demoBackendWsUrl.",
    )
}

android {
    namespace = "com.signai.webview"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.signai.webview"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "BACKEND_WS_URL",
                "\"${backendWsUrlOverride ?: debugBackendWsUrl}\"",
            )
        }

        release {
            isMinifyEnabled = false
            // El override de desarrollo **no** se honra acá a propósito: es la
            // forma más fácil de que texto en claro termine en un APK firmado.
            buildConfigField(
                "String",
                "BACKEND_WS_URL",
                "\"${backendWsUrlOverride?.takeIf { it.startsWith("wss://") } ?: releaseBackendWsUrl}\"",
            )
        }

        // Build de demostración del enlace cifrado (`docs/plan-proxy-tls.md`).
        //
        // Es `release` en todo lo que hace al enlace —solo `wss://`, y el
        // `network_security_config` que prohíbe el texto en claro— pero firmado
        // con la clave de debug para poder instalarlo y depurarlo. Además declara
        // `app/src/demo/res/xml/network_security_config.xml`, que confía en las
        // CA del **usuario**: sin eso el teléfono no acepta la CA de laboratorio
        // de `mkcert`, porque desde la API 24 Android dejó de confiar en ellas
        // por defecto.
        //
        // Tiene el mismo `applicationId` que `debug`, así que lo reemplaza en el
        // teléfono. Para volver atrás se reinstala `debug`.
        create("demo") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
            // Mismo criterio que `release`: la propiedad de build nunca puede
            // degradar el enlace a `ws://`.
            buildConfigField(
                "String",
                "BACKEND_WS_URL",
                "\"${backendWsUrlOverride?.takeIf { it.startsWith("wss://") } ?: demoBackendWsUrl}\"",
            )
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
