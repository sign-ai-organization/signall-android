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




