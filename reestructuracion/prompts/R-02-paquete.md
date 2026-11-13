# R-02 · Renombrar el paquete de la app

Prompt para la sesión que ejecuta este bloque. Se copia y se pega tal cual en un chat
nuevo, dentro de `sign-ai-android`.

---

Trabajamos en `sign-ai-android`, la app Android de la tesis (español hablado → Lengua de
Señas Colombiana con avatar 3D). Antes de tocar nada, lee `AGENTS.md` completo: ahí están
las reglas de build, de UI y las trampas del device.

## Contexto

Estamos en una reestructuración para que el proyecto se llame **SignAll** y no SignAI.
El plan completo y las decisiones están en `reestructuracion/00-general.md`; léelo.
Esta sesión ejecuta **solo el bloque R-02**: el cambio de paquete. Los bloques R-03
(identificadores internos), R-04 (métricas que cruzan el socket), R-05 (documentos),
R-06 (READMEs), R-07 (calidad) y R-08 (renombrar los repos en GitHub) van en sesiones
aparte. **No los adelantes.**

Estado de partida: `main` alineado con `origin/main`. La primera fase (R-01, leaks) ya se
hizo y encontró dos credenciales reales en evidencias de `PR-CAC-05`; el owner las tiene
que rotar. Eso no bloquea este trabajo y no se toca acá.

## Objetivo

Que la app deje de llamarse `com.signai.webview` y pase a `com.signall.app`, sin cambiar
nada de lo que hace.

**Este bloque es un cimiento**: mientras dure, nadie más toca `app/`. Si alguien tiene que
trabajar, va a backend, a STT o a la tesis. Avisar antes de empezar y avisar al terminar.

## Lo que hay hoy (medido, no lo vuelvas a contar desde cero)

- `namespace` y `applicationId` en `app/build.gradle.kts`: los dos `com.signai.webview`.
- Paquete Kotlin en `app/src/main/java/com/signai/webview/`, **75 archivos `.kt`**.
- **151 archivos** mencionan `com.signai`: `app/` 76, `docs/` 47, `tools/` 14,
  `openspec/` 10, más `AGENTS.md`, `README.md` y `lumo.properties`.
- `app_name` en `res/values/strings.xml` **ya dice `SignALL`**: no se toca.
- `AndroidManifest.xml` no tiene atributo `package`, así que el rename es de Gradle y de
  carpetas, no del manifest.
- `settings.gradle.kts` tiene `rootProject.name = "SignAI-WebView"`.
- `lumo.properties` tiene `ThemeName=SignAiTheme`, y `ComponentsDir`/`PackageName` con la
  ruta y el paquete viejos.

## Pasos

1. Cambiar `namespace` y `applicationId` a `com.signall.app`.
2. Mover el paquete Kotlin: `app/src/main/java/com/signai/webview/` →
   `app/src/main/java/com/signall/app/`, y actualizar los `package`/`import` de los 75
   archivos. `git mv` para que la historia no se pierda y el diff se lea.
3. Actualizar las referencias que **rompen si quedan viejas**:
   - `tools/verificar-tiempo-real.sh` (variable `PKG`), `tools/maestro/*.yaml` (`appId`),
     `tools/split_signs.py` (genera código con el `package` adentro), `tools/README.md` y
     `tools/TIEMPO-REAL.md` (comandos `pm grant`, `am force-stop`, `run-as`).
   - `AGENTS.md` y `README.md` en lo que sea instrucción vigente.
   - `openspec/specs/` y `openspec/config.yaml` solo donde describen el comportamiento
     actual.
   - `lumo.properties` y `settings.gradle.kts`.
   - `sonar-project.properties` de la raíz, si menciona rutas del paquete.
4. **No reescribas los informes históricos** de `docs/plan-pruebas/` ni las evidencias:
   dicen lo que se midió ese día con el paquete que existía ese día. Cambiarles el nombre
   sería falsificar la medición. Igual con `docs/evidencias/`.

## Verificación obligatoria

1. `./gradlew build` (corre ktlint; no puede fallar por violaciones nuevas).
2. `./gradlew installDebug` en el device conectado. Si es un teléfono físico:
   `adb reverse tcp:8080 tcp:8080` y
   `-Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate`.
3. `adb shell pm grant com.signall.app android.permission.RECORD_AUDIO`; sin eso el mic
   dice "No se pudo abrir el micrófono" y parece un fallo del STT.
4. Abrir la app, esperar el avatar (el splash se va con `avatarReady`), escribir una frase
   y comprobar que se representa y que el historial queda en
   `shared_prefs/signai.xml` bajo la instalación nueva.
5. `./reestructuracion/herramientas/escanear/sonar.sh sign-ai-android` con el Sonar local
   levantado (el proyecto en Sonar se llama `signall-android`), y confirmar que el
   análisis no empeoró respecto de la línea base (13.806 ncloc, 63 code smells, 1 bug).

## Consecuencia que hay que decirle al owner

El `applicationId` nuevo es **otra app**: no actualiza la instalada, convive con ella y
hay que desinstalar la vieja. `RECORD_AUDIO` se vuelve a conceder y el historial y los
ajustes de la instalación anterior no pasan a la nueva (de todos modos R-03 renombra las
`SharedPreferences`, así que no tiene sentido migrarlos). El build `demo` comparte
`applicationId` con `debug`, así que lo sigue reemplazando.

## Regla que está por encima de todo

**Nada de `push`.** Se commitea local y se para ahí. El push lo ordena el owner cuando lo
decida; esto vale para los cinco repos y también para cualquier rama.

## Al cerrar

- Escribe `reestructuracion/02-android.md` con lo que se cambió, lo que quedó pendiente y
  los números de verificación.
- Actualiza la fila de R-02 en `reestructuracion/00-general.md` y agrega la línea de
  bitácora con la fecha y el commit.
- Commit local en `main`, sin PR y **sin push**. No commitees archivos ajenos al bloque.
- Si algo del plan no cierra o una decisión no está tomada, **pregunta**; no la resuelvas
  por tu cuenta.
