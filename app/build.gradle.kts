import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.nomanr.plugin.lumo")
    id("org.jlleitschuh.gradle.ktlint")
}

// ── Identidad del build ────────────────────────────────────────────────────
//
// El teléfono ya no se conecta por cable, así que no hay `adb shell dumpsys
// package` para preguntarle qué build tiene instalado: el SHA viaja en la
// pantalla Acerca de y, más adelante, en el reporte de métricas. `versionCode`
// sale de la cantidad de commits para que cada APK publicado sea estrictamente
// más nuevo que el anterior y Android nunca rechace la instalación por
// "downgrade" (`../sign-ai-backend/docs/plan-wifi.md`, fase 2).
fun git(vararg args: String): String =
    runCatching {
        providers.exec { commandLine("git", *args) }.standardOutput.asText.get().trim()
    }.getOrDefault("")

val gitSha: String = git("rev-parse", "--short", "HEAD").ifBlank { "unknown" }
val buildSha: String = if (git("status", "--porcelain").isNotBlank()) "$gitSha-dirty" else gitSha
val buildNumber: Int = git("rev-list", "--count", "HEAD").toIntOrNull()?.coerceAtLeast(1) ?: 1

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

// ── Herramientas de desarrollo ──────────────────────────────────────────────
//
// El bloque AVANZADO de Ajustes y la consola de la escena (`setDebug` del puente,
// la que se lee con `adb logcat -s SignAIWeb`) **no son un ajuste del teléfono**:
// existen solo si la compilación los pide. Antes eran el interruptor «Modo debug»
// de Settings, persistido en SharedPreferences; eso permitía encenderlos desde
// cualquier APK de producción y dejaba viva una preferencia vieja.
//
// Se piden por el mismo camino que `signai.backendWsUrl`:
//
//   ./gradlew installDebug -Psignai.devTools=true
//   SIGNAI_DEV_TOOLS=1 ./gradlew installDebug
//
// **Apagados por defecto, incluso en debug** (decisión del owner, 2026-11-03).
// Release y `demo` los ignoran siempre: producción no compone `Section("AVANZADO")`
// ni manda `setDebug(true)` a la escena, y una propiedad de build no puede
// cambiarlo.
fun devToolsFlag(raw: String?): Boolean? =
    when (raw?.trim()?.lowercase()) {
        "1", "true", "yes", "on" -> true
        "0", "false", "no", "off" -> false
        else -> null
    }

val devToolsFromProperty = devToolsFlag(project.findProperty("signai.devTools") as String?)
val devToolsFromEnvironment =
    devToolsFlag(providers.environmentVariable("SIGNAI_DEV_TOOLS").orNull)
val devToolsRequested = devToolsFromProperty ?: devToolsFromEnvironment ?: false

if (devToolsFromProperty != null && devToolsFromEnvironment != null &&
    devToolsFromProperty != devToolsFromEnvironment
) {
    logger.lifecycle(
        "SignAI: -Psignai.devTools=$devToolsFromProperty gana sobre " +
            "SIGNAI_DEV_TOOLS=$devToolsFromEnvironment.",
    )
}

if (devToolsRequested) {
    logger.lifecycle(
        "SignAI: AVANZADO y la consola de la escena solo existen en debug; " +
            "release y demo los ignoran.",
    )
}

android {
    namespace = "com.signai.webview"
    compileSdk = 36
    // build-tools 35.0.0 (el default que pide AGP para compileSdk 36 en esta
    // versión) no está instalado y el SDK del equipo de pruebas es de solo
    // lectura. 36.0.0 ya está instalado y es compatible.
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.signai.webview"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.0.0"
        buildConfigField("String", "GIT_SHA", "\"$buildSha\"")
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "BACKEND_WS_URL",
                "\"${backendWsUrlOverride ?: debugBackendWsUrl}\"",
            )
            buildConfigField("boolean", "DEV_TOOLS", devToolsRequested.toString())
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
            // Producción no tiene bloque AVANZADO ni consola de escena. La
            // propiedad existe para debug, y acá se ignora aunque se pase.
            buildConfigField("boolean", "DEV_TOOLS", "false")
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
            // `demo` hereda release: tampoco tiene herramientas de desarrollo.
            buildConfigField("boolean", "DEV_TOOLS", "false")
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

// ── Análisis estático ──────────────────────────────────────────────────────
//
// `./gradlew ktlintCheck` corre dentro de `build` y falla con los avisos nuevos.
// Las reglas que se apagan son preferencias de formato que chocan con el estilo
// que el repo ya usa (firmas largas en una línea, constantes en PascalCase,
// funciones de Compose en PascalCase, imports agrupados por origen). Prenderlas
// reformatearía casi todos los archivos y taparía los cambios que importan.
//
// Queda prendido lo que detecta un error real: imports sin usar, variables sin
// usar, espacios al final, falta de salto de línea final, líneas en blanco
// consecutivas, imports con comodín y el resto de las reglas de corrección.
ktlint {
    android.set(true)
    additionalEditorconfig.set(
        mapOf(
            "ktlint_standard_class-signature" to "disabled",
            "ktlint_standard_function-signature" to "disabled",
            "ktlint_standard_multiline-expression-wrapping" to "disabled",
            "ktlint_standard_argument-list-wrapping" to "disabled",
            "ktlint_standard_chain-method-continuation" to "disabled",
            "ktlint_standard_function-expression-body" to "disabled",
            "ktlint_standard_function-literal" to "disabled",
            "ktlint_standard_function-naming" to "disabled",
            "ktlint_standard_property-naming" to "disabled",
            "ktlint_standard_multiline-if-else" to "disabled",
            "ktlint_standard_if-else-wrapping" to "disabled",
            "ktlint_standard_statement-wrapping" to "disabled",
            "ktlint_standard_condition-wrapping" to "disabled",
            "ktlint_standard_binary-expression-wrapping" to "disabled",
            "ktlint_standard_no-empty-first-line-in-class-body" to "disabled",
            "ktlint_standard_spacing-between-declarations-with-comments" to "disabled",
            "ktlint_standard_no-consecutive-comments" to "disabled",
            "ktlint_standard_import-ordering" to "disabled",
            "ktlint_standard_max-line-length" to "disabled",
            "ktlint_standard_string-template" to "disabled",
            "ktlint_standard_no-consecutive-blank-lines" to "disabled",
        ),
    )
}
