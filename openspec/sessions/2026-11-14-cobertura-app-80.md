# Cobertura de pruebas de la app al 80 %

**Fecha:** 2026-11-14, noche · **Rama:** `main`

## Qué llegó

La sesión de calidad había dejado la infraestructura de pruebas (`junit`, Robolectric,
Mockito, JaCoCo, ktlint) y 44 pruebas, pero la cobertura del código propio de la app era
**40 líneas de 3 361 (1,2 %)**. El pedido fue subirla al **80 %** sin tocar el design
system de Lumo, que queda fuera del denominador.

## Qué se hizo

**1. `MainScreenState` — 0 % → 89,9 %.** Dos archivos:

- `app/src/test/java/com/signall/app/ui/state/MainScreenStateTest.kt` (el que ya existía,
  ampliado con el sondeo de voz).
- `app/src/test/java/com/signall/app/ui/state/MainScreenStateFlowTest.kt` (nuevo, 53
  pruebas): el turno completo de punta a punta con reloj virtual. Dictado, modo en vivo,
  oraciones que se representan al llegar, glosas sin clip, `done`, `error`, desconexión con
  y sin seña en curso, frase cancelada (`StaleTurn`) con su `done` tragado, relojes de 15 s
  de transcripción y de silencio, recuperación del servicio de voz, cambio de avatar con
  `avatarLoaded`/`avatarFailed`, Repetir y Repetir desde el drawer, los dos drawers y el
  gesto que se reclama una sola vez.

**2. Pruebas de composables con `createComposeRule` + Robolectric.** Una por pantalla:
`SignAllBottomBarTest`, `ConversationPanelTest`, `SettingsSidebarTest`, `AvatarStageTest`,
`AboutScreenTest`, `MainScreenTest` (con un `MainScreenState` real y dobles),
`ComponentsTest` (stepper, selector de personaje y de seña, hoja de historial, marca,
splash, `SettingRow`) y `SignAllThemeTest`.

**3. Lo que no es UI.**

- `SignAllWebSocketClientTest`: cliente real contra `MockWebServer`, con frames de
  `capability`/`transcript`/`segment`/`done`/`error`, el arreglo legado sin sobre, basura que
  se descarta, `sendText`/`sendAudio`/`sendEnd` y `probeVoice`.
- `HttpRepositoriesTest`: `SignAssetRepository` (descarga, caché por huella del path, 404,
  origen inválido) y `BackendHealth` contra `MockWebServer`.
- `WebViewSignAllControllerTest` y `AvatarWebViewTest`: los comandos del puente, la URL de
  la escena, el progreso, `onPageFinished` y el `no-store` del asset loader.
- `SignAllCommandsTest`, `SignAllMetricsTest`, `AudioCaptureTest`, `MicPermissionPrompt`,
  `MainActivityTest`.

**4. Tres arreglos de la infraestructura de pruebas** (ver `app/build.gradle.kts` y
`settings.gradle.kts`):

- **JaCoCo no medía nada de Robolectric.** Las clases que Robolectric define en su
  *sandbox* no tienen `codeSource` y el agente las saltea por defecto: el informe decía
  1,2 % aunque las pruebas corrieran. Se agregó `isIncludeNoLocationClasses = true` y la
  exclusión de `jdk.internal.*`.
- **`phosphoricons-regular-android:1.0.5` viene en class-file 65 (Java 21)** y el JVM 17 del
  proyecto no puede cargarlo: la primera prueba que dibujaba un icono moría con
  `UnsupportedClassVersionError`. El JVM que **ejecuta** las pruebas pasa a ser el 21
  (toolchain + resolvedor de Foojay; el build sigue compilando con `jvmTarget = 17`).
- **Memoria:** la suite entera con Robolectric no entraba en el heap por defecto de 512 MB
  (el síntoma era un OOM del agente de JaCoCo). `maxHeapSize = "2g"`.

Además, el `AudioRecord` que simula Robolectric entregaba datos a un ritmo que hacía
girar el bucle de captura y agotaba la memoria del proceso de pruebas: se reemplaza por una
fuente lenta a propósito (`app/src/test/java/com/signall/app/testing/ThrottledAudioRecord.kt`).

**5. ktlint.** `ktlintCheck` ya fallaba en `HEAD` por `MainScreenStateTest.kt`
(`standard:blank-line-before-declaration` en los `override fun … = Unit` de los dobles).
Se corrigió con `ktlintFormat`, que solo agregó líneas en blanco.

## Qué se midió

- Antes: **40 / 3 361 líneas = 1,19 %** (sin `ui/designsystem`).
- Después: **3 099 / 3 361 líneas = 92,20 %** (`./gradlew jacocoTestReport`, XML en
  `app/build/reports/jacoco/test/jacocoTestReport.xml`, contador `LINE` por `<package>`,
  descontando los paquetes con `ui/designsystem`).
- **221 pruebas**, todas en verde (`./gradlew testDebugUnitTest --no-daemon`).
- `./gradlew ktlintCheck --no-daemon` en verde.

## Qué se decidió

- **Los toques de las pruebas de composables van por la acción de semántica
  (`performSemanticsAction(OnClick)`) y no por el puntero.** Con un `BasicTextField` en la
  misma composición —el del panel de control— el `performClick()` sobre un nodo hermano se
  pierde en Robolectric aunque el nodo tenga límites y `OnClick`. Se verificó con una
  composición mínima: sin campo de texto el toque por puntero funciona. Lo que se comprueba
  igual es el `onClick` real del componente, que es lo que importa.
- **`ui/designsystem` queda fuera** del denominador, como pidió el owner: es el design
  system de Lumo adaptado, no lógica de la app.
- **No se tocó código de producción.** Las pruebas destaparon comportamientos que no son
  bugs y se ajustaron para afirmarlos: en modo en vivo, `onPlaybackDone` deja el estado en
  *Grabando* (el micrófono sigue prendido) y la frase enviada queda retenida 1,5 s en el
  campo, así que el selector de avatar se habilita recién cuando la retención se suelta.

## Qué quedó pendiente

- **`AvatarWebViewKt` en 53,6 %** y `MainScreenState$finishTurnWhenQueueSettles` en 0 %:
  lo primero necesita el listener de mensajes del WebView (`WEB_MESSAGE_LISTENER`), que
  Robolectric no soporta, y el `onReceivedError`, que pide un `WebResourceErrorCompat` que
  no se puede construir; lo segundo exige que el `done` llegue con un `appendQueue`
  suspendido, y eso depende de un asset que resuelva con demora.
- **`SettingsSidebar`: el bloque AVANZADO no se ejecuta** con `DEV_TOOLS = false`, que es el
  default de `debug` (decisión del owner). Son 24 líneas inalcanzables en esta compilación.
- **El CI compila con Java 17.** Las pruebas de composables necesitan JDK 21; el toolchain
  lo descarga solo (Foojay), pero si se quiere fijar la versión, alcanza con
  `java-version: "21"` en `.github/workflows/ci.yml`.
- **La verificación con `jacocoTestReport` después de una corrida filtrada** (`--tests`)
  puede dejar el `.exec` a medio escribir y el reporte falla con "Unable to read execution
  data file": se corre la suite completa (`testDebugUnitTest`) antes del reporte.
