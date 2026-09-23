# Sesión: investigación del prototipo Vercel y plan de adaptación UI

- **Día:** miércoles
- **Fecha:** 2026-09-23
- **Hora:** ~ (local)

## Qué se hizo

1. **Investigación completa de `PrototipoFrontend/`** (Next.js 16 + React 19 +
   Tailwind 4 + shadcn, v0.app):
   - `app/page.tsx` (240 líneas): toda la UI y el estado en un solo
     componente — splash, main, about, bottom sheet de historial, drawer
     izquierdo (Conversación) y drawer derecho (Ajustes), máquina de estados
     `idle/listening/processing/translating/signing/done`, `glossFor()`,
     personajes, accesibilidad, theming.
   - `app/globals.css` (55 líneas): CSS variables de theming, clases
     `theme-dark` / `font-bold` / `font-large` / `high-contrast`, animaciones
     (`rise`, `slide`, `dropdown-in`) y `prefers-reduced-motion`.
   - `components/ui/button.tsx`, `lib/utils.ts`, `public/*` — scaffolding
     shadcn/v0, no relevante (nuestra base es Lumo).
2. **Inventario de la app actual** (`app/src/main/java/...`): shell Compose +
   `AvatarWebView` (GLB real), `SignAiBottomBar` (input + historial dropdown +
   mic/send), `SignAiEdgeDrawer` (solo derecha) + `SettingsSidebar` (4 toggles
   debug + sign picker), `SignAiSplash` (spinner), paleta Lumo dark con
   `primary = Blue900 #276EF1`.
3. **Verificación de iconos Phosphor 1.0.5**: listados los 1513 iconos del
   AAR `phosphor-regular`; **todos** los del mapeo lucide→Phosphor existen
   (ArrowLeft, Check, CaretDown/Right, Clock, ClockCounterClockwise, CircleHalf,
   Info, Minus, Microphone, Moon, Play, Plus, ArrowCounterClockwise,
   PaperPlaneTilt, SlidersHorizontal, Sparkle, Sun, TextAa, SpeakerHigh, X,
   Gear, Translate).
4. **Se levantó el prototipo** (`pnpm install` + `pnpm dev`, Next en
   `localhost:3000`) para tenerlo como referencia. No se pudo capturar
   pantallazos: **no hay navegador de escritorio conectado a la sesión**
   (`browser.tabs.open` → `browser.disconnected`). Server detenido al cierre.
5. **Plan escrito** en `openspec/changes/prototipo-ui-vercel/tasks.md`:
   enfoque (reconstruir nativo, no embeber), inventario feature-by-feature,
   capacidades nuevas, qué NO copiar, 7 fases (A–G), riesgos y decisiones
   abiertas.

6. **Catálogo completo de features** en `openspec/specs/ui/spec.md` (nuevo):
   todas las features de la app con IDs (`NAV`, `SPL`, `EDG`, `STG`, `CTL`,
   `HIS`, `CNV`, `SET`, `PER`, `ACC`, `APP`, `STA`, `GLS`, `BRG`, `ANI`, `SEM`,
   `RSP`, `BRD`, `OM`), criterios de aceptación, paleta del prototipo, mapa de
   iconos y features omitidas con su razón. Es la **fuente de verdad**; el plan
   mapea cada fase a los IDs.

## Decisiones tomadas

- **Reconstruir la UI en Compose nativo**, NO portar la app web ni embeberla.
  El prototipo es spec visual/funcional; el WebView sigue siendo solo el canvas
  3D. Alineado con `AGENTS.md` y con el rendimiento 3D.
- **Extender el contrato `window.SignAI` de forma aditiva** (`setSpeed`,
  `setCharacter`, evento `onPlaybackDone`) para la máquina de estados y la
  velocidad; los 5 comandos actuales y el guard `?.` se mantienen.
- **Gloss LSC como función pura** en `domain/Gloss.kt` (placeholder honesto;
  el motor real está fuera de alcance).
- **Omitir el "home indicator"** del prototipo (duplica la navegación por
  gestos de Android).

## Decisiones resueltas (2026-09-23)

1. **Branding — ABIERTO**: se mantiene "SignAI" por ahora, pero seguramente
   cambie; el wordmark se centraliza para cambiarlo en un solo lugar.
2. **Personajes — UI-only**: picker completo a nivel visual, sin efecto sobre
   el GLB (queda listo para futuros avatares).
3. **Light mode — switch real** dark/light con `LightColors`/`DarkColors` de Lumo.
4. **Settings de debug — sección "Avanzado"** al final del drawer de Ajustes.
5. **Ritmo — fase por fase con `installDebug`**; no arrancar sin OK explícito.

## Pendiente

- **OK del usuario para arrancar la Fase A** (design system extendido). El plan
  está completo y con las decisiones registradas en `tasks.md`.
- El usuario pidió **ver el planning antes de que se ejecute nada** — no se ha
  tocado código de la app (solo se crearon documentos de openspec).

---

## 2026-09-23 — Ejecución Fase A (design system)

### Git

- `main`: commit `chore: consolida prototipo Vercel, dataset y contexto openspec`
  (prototipo, `data/` 6.4 MB, `opencode.json`, todo `openspec/` y cambios Kotlin)
  y **push a origin**. Verificado que `PrototipoFrontend/node_modules` y
  `PrototipoFrontend/.next` están ignorados (`git check-ignore`).
- Rama de trabajo creada: **`feat/prototipo-ui-vercel`** (todo lo de aquí en
  adelante va en esta rama; `main` no se toca).

### Fase A — Design system extendido

- **A.1 `Color.kt`**: `Colors` ahora expone `strong`, `onStrong` y `soft`
  (valores del prototipo: light `#171717`/`#E8E8E3`, dark `#F5F5F1`/`#292926`)
  y `muted` como alias de `textSecondary`. No se tocaron los valores Lumo
  existentes (paridad visual). `contentColorFor` mapea `strong → onStrong`.
- **A.2 `SignAiMotion.kt`** (nuevo): duraciones `Fast 160`, `Short 180`,
  `Medium 250`, `Slow 500`; easings `EaseOut` (LinearOutSlowIn) y `EaseInOut`
  (FastOutSlowIn); helper `motionTween()` que colapsa a 0 ms y
  `LocalReduceMotion` para ACC-5/ANI-7.
- **A.3 tokens**: `SignAiSizes` (+input 50, actionButton 50, panel
  `min(88%,350dp)`, sheet/tile/switch/etc.), `SignAiShapes` (+corner 12/13/16/
  24/28, pill, edge tabs) y `SignAiSpacing` (+xxs/smLg/mdLg/xxl). Se conservaron
  los tokens viejos para no alterar pantallas actuales.
- **A.4 tipografía**: `Typography` + wrapper público exponen `eyebrow`,
  `caption`, `quote`, `gloss`, `label` con las métricas del prototipo.
- **A.5 iconos**: `SignAiIcons` con los 23 del mapeo (todos verificados en
  `phosphoricons-regular-android:1.0.5` contra el AAR), conservando `Mic`,
  `Send`, `Gear`, `History`.

### Decisión

- Se mantienen **intactos los valores Lumo** de `background/surface/outline/...`
  en Fase A para cumplir "paridad: sin cambios visuales"; los nuevos tokens
  `strong`/`soft` son los que llevan los valores del prototipo y se usarán en
  Fases B–F. Se revisará el ajuste fino de la paleta en su fase.

### Verificación

- `./gradlew build` → **BUILD SUCCESSFUL**.
- `./gradlew installDebug` → **OK** en Redmi Note 9S (`Redmi Note 9S - 11`,
  1 device). Sin cambios visuales esperados (Fase A solo agrega tokens).

---

## 2026-09-23 — Ejecución Fase B (chrome principal)

Device disponible: **Redmi Note 9S (Android 11)**.

### Qué se hizo

- **B.1 `AvatarStage.kt`** (nuevo, `ui/screens/stage/`): escena con el WebView
  como contenido + `stage-hint` (dot `strong` + `PlaybackState.label`,
  top-left con status bar insets), `stage-caption` (`LSC · Avatar 3D`,
  bottom-center) y botón **Repetir** (pill, solo en `Done`).
- **B.2 `SignAiBottomBar.kt`**: panel con borde superior de 1dp, esquinas
  superiores 26, input (`Escribe una frase...`, Enter→enviar con `ImeAction.Done`),
  botón historial 50dp, y fila de acciones `Traducir` (flex, `strong` + label) +
  `Mic` (50dp, activo con `listening`). `HistoryDropdown` ahora recibe un slot
  `trigger` para estilizar el botón.
- **B.3 `SignAiSplash.kt`**: logo tile `S` (68dp, 23 radio, `strong`),
  eyebrow del branding, barra de progreso real y `%`. `AvatarWebView` expone
  `onProgress: (Int) -> Unit` vía `WebChromeClient.onProgressChanged`.
- **`PlaybackState.kt`** (nuevo) y `MainScreenState`: `playbackState`, `lastText`,
  `replay()` y `toggleMic()`.

### Decisiones

- **`PlaybackState` se adelantó de E.1 a B**: el stage y el estado activo del
  mic necesitan el enum ya. E conserva la orquestación y el callback real.
- **Fila de acciones fiel al prototipo**: `Traducir` a la izquierda (flex, con
  label) y `Mic` a la derecha 50dp (el plan decía "Mic | Traducir 25/75").
- **Mic ya no hace `play("mic")`** (que metía "mic" al historial): ahora solo
  alterna `listening`. El mock con auto-relleno llega en E (`OM-4`).
- **Branding centralizado** en `ui/SignAiBranding.kt` (`Name`, `LogoLetter`,
  `Eyebrow`) — el splash ya lo consume (`APP-6`).
- **Repetir** usa `lastText` (nuevo en `MainScreenState`).

### Verificación

- `./gradlew build` → **BUILD SUCCESSFUL**.
- `./gradlew installDebug` → **OK** en Redmi Note 9S.
- Capturas: main (hint + caption + panel nuevo) y splash con progreso real al
  **80%**. `SPL-5` verificado en device.

### Pendiente

- Fase C (sidebars) — no arrancar hasta OK del usuario.

---

## 2026-09-23 — Ejecución Fase C (sidebars)

### Qué se hizo

- **C.1 `EdgeDrawer.kt` generalizado** a `DrawerSide.Start/End` (`EDG-13`):
  mismo modelo de gesto para ambos lados (offset firmado), pestaña visible
  (grip) tappable y arrastrable (`EDG-3`/`EDG-8`/`EDG-11`), `EDG-6`
  **drag-preview** translúcido con label, scrim (`EDG-10`) y exclusión de la
  bottom bar (`EDG-12`).
- **C.2 `ConversationPanel.kt`** (`CNV-1..8`): CONTEXTO/Conversación, TÚ,
  TRADUCCIÓN (gloss), estado del avatar, repetir y nota de swipe. Con insets de
  status/navigation bar.
- **C.3 `SettingsSidebar.kt`** (`SET-1/2/5/6/9/10`): header con logo, secciones
  AVATAR (Personaje + Velocidad), ACCESIBILIDAD (tamaño/negrita/contraste/
  grande/reducir), APARIENCIA (dark/light) y **AVANZADO** (`SET-8`) con los
  toggles debug y el selector de Seña. Link "Acerca de" añadido (cierra el
  drawer por ahora; D lo cablea a la pantalla).
- **Componentes nuevos**: `CharacterPicker` (`PER-1..3/5`), `SignAiSlider`
  (sin Material), `SettingRow` genérico (`SET-9`), `SignAiSmallIconButton`.
- **C.4** blur del canvas solo con drawer abierto (`EDG-14`), ya guardado a
  Android 12+.
- `MainScreen` con **dos drawers anidados** (Start=Conversación, End=Ajustes),
  exclusión mutua al abrir, y ancho de panel `min(88%, 350dp)`.

### Decisiones / adelantos

- **`domain/Gloss.kt` (era E.2)** se adelantó: el panel Conversación necesita el
  gloss real. **`Settings` (era F.1)** también: los controles del sidebar
  necesitan los campos. La **aplicación real** de accesibilidad/tema/velocidad y
  la **persistencia** siguen en F.
- El link **Acerca de** existe ya pero cierra el drawer sin navegar; D crea la
  pantalla y cablea `NAV-3/7`.
- **Switches en azul `primary`** (Lumo), no `strong` como el prototipo; se
  revisará en F/polish si molesta la fidelidad.

### Verificación

- `./gradlew build` → **BUILD SUCCESSFUL**; `installDebug` → **OK**.
- Capturas con Ajustes abierto y con Conversación abierto (blur + panel
  correctos, sin overlap de status bar tras el fix).
- **Límite:** `adb shell input` está bloqueado en MIUI (`INJECT_EVENTS`), así que
  los **gestos** (tap de pestaña, drag, edge-swipe) no se pudieron automatizar.
  Verificación manual del usuario pendiente.

### Pendiente

- Fase D (historial bottom sheet + About) — no arrancar hasta OK del usuario.

---

## 2026-09-23 — Ejecución Fase D (historial + About)

### Qué se hizo

- **D.1 `HistoryBottomSheet.kt`** (`HIS-1..8`): sheet modal con scrim, handle,
  eyebrow `TUS FRASES`, título "Historial reciente", items con `Reutilizar →`,
  estado vacío y scroll. Entrada `rise` con `slideInVertically`; cierre por
  scrim o X (`HIS-8`). Reemplaza `HistoryDropdown` (eliminado).
  - El **input** del control panel se elevó a `MainScreenState.inputText` para
    que `Reutilizar` cargue la frase y la envíe (`HIS-3`).
- **D.2 `AboutScreen.kt`** (`NAV-3`, `BRD-2/3/4`): volver, logo, eyebrow,
  nombre centralizado, copy y meta con **versión dinámica**
  (`BuildConfig.VERSION_NAME` = `1.0.0-webview`).
- **D.3 `MainScreen`**: `showHistory`/`showAbout`; About exclusiva (`NAV-8`) y
  `onOpenAbout` del sidebar cierra el drawer y navega (`NAV-7`).

### Verificación

- `./gradlew build` → **BUILD SUCCESSFUL**; `installDebug` → **OK**.
- Capturas: sheet con historial real (incluye entradas viejas persistidas) y
  About con versión dinámica.
- **Límite:** igual que en C, `adb input` bloqueado → los taps reales
  (abrir historial, Reutilizar, Acerca de, volver) quedan para verificación
  manual.

### Pendiente

- Fase E (estado de reproducción + bridge `setSpeed`/`onPlaybackDone`) — no
  arrancar hasta OK del usuario.

---

## 2026-09-23 — Ejecución Fase E (estado + bridge)

### Qué se hizo

- **E.3 `AvatarWebView`**: `WebViewCompat.addWebMessageListener(this,
  "SignAIBridge", setOf("https://appassets.androidplatform.net"), …)` con
  `WebViewFeature.isFeatureSupported(WEB_MESSAGE_LISTENER)`. Expone
  `onPlaybackDone: () -> Unit`; `MainScreen` lo cablea a
  `state::onPlaybackDone`.
- **E.4 `SignAiCommands.setSpeed(factor)`**: `window.SignAI?.setSpeed(...)`
  (aditivo, guard `?.` intacto). `SignAiWebViewController.setSpeed`.
- **E.5 `app.js`**: nuevo `state.speed` (multiplica el avance de frames),
  `notifyPlaybackDone()` → `window.SignAIBridge?.postMessage("onPlaybackDone")`
  al terminar `animTick`, y `setSpeed(v)` (v 0..1 → factor 0.1..3, 0.5 = 1×).
  Los 5 comandos existentes y `index.html` no cambian. `node --check` OK.
- **E.6 `MainScreenState`**: `play()` ahora `Translating` → (850 ms) `Signing`
  → `Done` por callback real; `onPlaybackDone()`; mock del mic
  (`Listening` → 1.5 s auto-rellena "Quiero ir a la universidad" →
  `Processing` → 0.8 s `Idle`); `setSpeed` aplica al WebView y `attachWebView`
  siembra la velocidad actual.

### Decisiones

- **Bridge vía `addWebMessageListener`** (recomendado en el plan) en vez de
  `@JavascriptInterface`. Si el feature no está soportado, no hay evento (la UI
  se queda en `signing`); no rompe nada.
- El evento se propaga directo `AvatarWebView → MainScreenState` en vez de pasar
  por el controller (el controller sigue siendo el único con `evaluateJavascript`).
- Se implementó el **mock del mic** del prototipo (`OM-4`) porque era barato y
  mejora la fidelidad; sigue sin reconocimiento de voz real.

### Verificación

- `./gradlew build` → **BUILD SUCCESSFUL**; `installDebug` → **OK**;
  `node --check app.js` → **OK**.
- Captura en estado **Done** ("Reproducción completada" + botón **Repetir** +
  input con la frase) alcanzado por el **callback real** ⇒ `BRG-7`/`STA-8`
  funcionando en device. Sin errores en logcat.
- **Límite:** `adb input` bloqueado → la interacción manual (escribir y pulsar
  Traducir, slider de velocidad, mic) queda para verificación del usuario.

### Pendiente

- Fase F (ajustes reales + theming + persistencia) — no arrancar hasta OK.

---

## 2026-09-23 — Ejecución Fase F (ajustes + theming)

### Qué se hizo

- **F.1 modelos**: `Settings`/`Setting`/`Character` se movieron de `ui/state` a
  **`domain`** (carácter guarda `colorArgb` en vez de Compose `Color`) para que
  `data` pueda persistirlos sin depender de `ui`.
- **F.2 accesibilidad real**:
  - `large` → `provideTypography(scale)` (fontSize/lineHeight ×1.15).
  - `bold` → `provideTypography(bold)` sube pesos (Normal→SemiBold, …).
  - `contrast` → `outline = #777777` en el tema.
  - `reduceMotion` → `LocalReduceMotion`; drawer y sheet saltan la animación y
    el fade del contenido/splash se colapsa (`SignAiMotion.motionTween`).
  - `interfaceSize` → escala de `LocalDensity` (0.9×–1.1×) en el tema.
- **F.3 theming**: `SignAiTheme` acepta `darkTheme` y pasa a
  `LightColors`/`DarkColors`; `MainActivity` provee el tema desde `Settings` y
  fija la apariencia de las barras del sistema. `enableEdgeToEdge` sigue.
  - **Canvas 3D theme-aware (extra):** nuevo comando aditivo `setTheme(dark)` en
    `app.js` (no toca los 5 originales) que cambia el fondo del render entre
    `#111110` y `#f4f4f2`. Se aplica en `onLoaded` (`syncWebSettings`) y al
    cambiar el switch.
- **F.4 persistencia**: `data/SettingsRepository.kt` +
  `SharedPrefsSettingsRepository` (mismo archivo `signai`). `MainScreenState`
  persiste en cada cambio y re-aplica al bridge en attach/load.
- **F.5** build + install + capturas dark / light / accesibilidad.

### Decisiones

- **`Settings` en `domain`** (no en `data` ni `ui.state`) para respetar la
  regla de dependencias con la persistencia en `data`.
- **Tercer comando aditivo `setTheme`** (además de `setSpeed` y el evento
  `onPlaybackDone`), porque en light el canvas oscuro chocaba con el chrome
  claro (riesgo previsto en el plan). El contrato original sigue intacto.
- **`darkTheme` default = true** (se mantiene dark como base, como pidió el
  AGENTS).

### Verificación

- `./gradlew build`/`installDebug` → **OK**; `node --check app.js` → OK.
- Capturas: dark normal; light con bold+large+contrast (chrome y canvas claros,
  texto más grande/negrita); dark final tras revertir defaults.
- **Limitación conocida:** en **MIUI** (Redmi Note 9S) los iconos de la status
  bar no cambian a oscuro en light pese a `isAppearanceLightStatusBars` +
  flag legacy. Pendiente probar en otro ROM / decidir si se deja.

### Pendiente

- Fase G (polish + docs) — no arrancar hasta OK.
- Verificación manual de interacción (taps/switches) por el bloqueo de
  `adb input` en MIUI.

---

## 2026-09-23 — Ejecución Fase G (polish + docs) — CIERRE

### Qué se hizo

- **G.1 polish**:
  - `dropdown-in` en `SignAiDropdown` (fade + scale 160 ms), respetando
    reduce-motion.
  - `Modifier.pressScale` (`foundation/PressScale.kt`) aplicado a los botones
    Repetir del stage y de Conversación (0.94, `ANI-6`).
  - Eliminado el `SignAiIconButton` ya muerto.
- **G.2 documentación**:
  - `AGENTS.md`: contrato JS actualizado (5 + `setSpeed`/`setTheme` +
    `onPlaybackDone`), theming/accesibilidad, screens, gotchas (MIUI).
  - `openspec/specs/ui/spec.md`: estado = implementado; nuevo `BRG-9`
    (`setTheme`).
  - `openspec/config.yaml`: puente JS y estado UI actualizados.
- **G.3**: capturas revisadas en device (main, splash con progreso, Ajustes,
  Conversación, historial, About, translating/signing/done, light/dark/a11y).

### Estado final

Las **7 fases (A–G)** quedan implementadas, con `build` + `installDebug` verdes
en cada una y commit por fase en `feat/prototipo-ui-vercel`. Pendiente global:
- **Verificación manual de gestos/taps** por el bloqueo de `adb input` en MIUI
  (pestañas de borde, drag-preview, edge-swipe, Reutilizar, switches, Acerca de,
  volver).
- **Merge a `main`** cuando el usuario apruebe.
- Decidir sobre el **branding** ("SignAI" ↔ "Seña") y sobre el icono de status
  bar en light en MIUI.









---

## Ajustes de UI posteriores (device)

Iteración sobre la UI ya implementada, verificada con capturas en device.

### Cámara del avatar (default + reset por long-press)
- Valores finales en `app.js` (init y reset): `camera.position.set(0, 1.5, 2.15)`
  con `controls.target.set(0, 1.2, 0)`. Cámara algo alta mirando hacia adelante/
  abajo para compensar el contrapicado de una altura baja, encuadre hasta cadera.
- Aprendizaje: bajar **solo** la `y` de la cámara con el target fijo inclina la
  mirada; para bajar sin inclinar hay que bajar cámara y target juntos.

### Stage
- Eliminado el caption "LSC · AVATAR 3D" (`STG-3`) y su `CaptionText`.
- Botón **Repetir** movido a `BottomCenter` justo sobre la barra y agrandado ~25%.
- **Esquinas negras** de la bottom bar: se resolvió con WebView a pantalla completa
  y la barra superpuesta (`Box` + `BottomCenter`) en vez de `Column`, así las
  esquinas redondeadas muestran la escena y no el fondo raíz.

### Pestañas de agarre de los drawers (EDG-8/EDG-11)
- Reemplazada la barra azul (`primary`) por el diseño del prototipo `.edge-tab`:
  tab de `surface` 20×62 dp, borde hairline `outline`, sombra suave, redondeo 18 dp
  solo en el lado que sobresale (`SignAiShapes.cornerEdgeTabLeft/Right`) y grip de
  **dos** líneas `muted` (4×28 dp, opacidad 0.65, separación 3 dp).
- Se reutilizan los tokens `SignAiShapes.cornerEdgeTabLeft/Right` que ya existían.
- Verificado en device: cerrado (pestaña en el borde de pantalla) y abierto
  (pestaña pegada al borde del panel), ambos con su grip.

### Fix: hueco entre el drawer abierto y su agarre
- Síntoma: al terminar la animación de apertura, el drawer se "contraía" y la
  pestaña quedaba flotando, separada del panel.
- Causa: el cuerpo del drawer se medía con `width(drawerWidth + drawerOverflow)`
  = 1128px en un `Box` de 1080px; Compose lo **centraba** en vez de alinearlo al
  borde (natural left = -24px en lugar de -48px). Con el `offset` del overflow,
  el borde de entrada del panel quedaba ~23px adentro → hueco de ~23px respecto
  a la pestaña (que sí estaba en la posición correcta). Confirmado con logs
  `positionInRoot()` y prueba pintando el `Surface` de rojo.
- Fix: se elimina el cuerpo sobredimensionado. Ahora el `Surface` mide
  exactamente `drawerWidth` (tamaño natural, sin desbordar el padre) y un `Box`
  de desborde aparte (`drawerOverflow`) sigue cubriendo el borde de pantalla
  durante el rebote del spring. Verificado en device: borde del drawer en 130px
  = borde derecho de la pestaña.

---

## 2026-09-23 (cont.) — Fidelidad de la paleta del prototipo + fix crítico de MIUI force-dark

### Paleta del prototipo aplicada verbatim
- `Color.kt`: `LightColors` y `DarkColors` ahora usan los tokens del prototipo
  (`PrototipoFrontend/app/globals.css`). Claro (default): `background #F4F4F2`,
  `text #171717`, `outline #DEDED9`, `muted #777773`, `strong #171717`,
  `onStrong #F4F4F2`, `soft #E8E8E3`, `scrim` negro .28, `elevation` negro.
  Oscuro: `#111110 / #F5F5F1 / #32322F / #969690 / #292926`.
- `surface` pasa de translúcido (`rgba(255,255,253,.92)`) a **opaco `#FEFEFC`**:
  el prototipo lo atenúa con `backdrop-filter: blur(14px)` (que Compose no tiene
  barato) y además recorta el avatar arriba del panel, así que el resultado
  opaco es lo que se ve. Sin esto el avatar "sangraba" por la barra inferior.
- `primary` sigue a `strong` (el prototipo no tiene acento azul).
- **Claro es el default**: `Settings.darkTheme = false`; `MainActivity` y
  `syncWebSettings` respetan `settings.darkTheme` en vez de forzar claro.

### Tipografía
- El prototipo usa `Arial, Helvetica, sans-serif`; Android no trae ninguno.
  Se empaqueta **Liberation Sans** (métricamente compatible con Arial) en
  `res/font/` y se usa como `SignAiFontFamily`.

### Fidelidad por componente
- Bottom bar: borde del input 1dp, switch 43×25, anillo `soft` 5dp en el mic
  activo, home indicator 112×4 al 70%, "Traducir" en `body1` bold.
- Stage: caption `LSC · AVATAR 3D` restaurado, Repetir a `BottomEnd` (pill,
  13/9, icono 14).
- Drawers: pestaña al 42% de alto (era centro), grip de 2 líneas, **esquinas
  interiores 27dp** (`cornerDrawerLeft/Right`), scrim con `background` negro
  (antes era un `Box` transparente → no oscurecía).
- `CharacterPicker`: fondo `soft` + `cornerMenu` en el ítem seleccionado y
  anillo `background` de 3px en el punto del personaje.
- Splash/About/Ajustes/Historial: paddings, hairlines y tamaños alineados.

### Fix crítico: MIUI "smart dark" invertía el texto
- **Síntoma**: en el drawer de Ajustes y en Conversación, todo el texto con
  `color = colors.text` (#171717) se veía **blanco**; #333333 se aclaraba;
  #777773 salía gris; el rojo y los rectángulos de color salían correctos.
  Diagnóstico: `SignAiText` recibía el color correcto (logcat), el mismo
  `BasicText` con el mismo color se veía bien en la pantalla principal pero
  mal dentro de `Modifier.verticalScroll`/drawer. Un test controlado
  (columna fija vs. columna con scroll) lo confirmó: **el texto dentro de una
  capa hardware (scroll/drawer) se invertía**.
- **Causa**: el dispositivo tiene modo oscuro + **`smart_dark_enable=1`** de
  MIUI; la app declara tema claro (`Theme.Material.Light.NoActionBar`), así que
  MIUI le aplica force-dark e invierte el contenido de los `RenderNode`.
- **Fix**: `android:forceDarkAllowed=false` en `res/values/themes.xml`.
  Verificado en device: drawer, conversación, historial y about con texto
  oscuro correcto. **No quitar ese item** (ver `AGENTS.md` → Gotchas).

### Pendiente
- Revisar con el usuario: rejilla del piso (`Settings.grid` default `true`; el
  prototipo no tiene rejilla), anillo de foco del input (3px `soft` del
  prototipo, no implementado), sombra superior del panel (`.control-panel`).
- Modo oscuro: no se tocó (queda para después), pero ambas paletas ya están
  alineadas al prototipo.

## Ajustes finales de UI (segunda pasada)

- **Handles de las sidebars**: se quitó el ripple/indication del `clickable`
  (dejaba una zona rectangular al mantener presionado). Ahora solo abre/cierra
  sin feedback visual; el drag sigue igual.
- **Repetir**: pasa de `BottomEnd` (right 23 / bottom 58) a `BottomCenter`,
  subido a `bottom 76`, y ~25% más grande (padding 16/11, icono 18).
- **Stage**: eliminado el caption `LSC · AVATAR 3D` que quedaba frente al avatar.
- Commit `3249c97` con todo el pulido previo + estos ajustes, pusheado a
  `origin/feat/prototipo-ui-vercel`.
