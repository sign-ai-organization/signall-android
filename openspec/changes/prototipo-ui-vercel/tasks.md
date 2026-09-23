# Prototipo UI "Seña" (Vercel/Next) → App WebView

Llevar a la app nativa (`Compose shell + WebView 3D`) el look & feel, las
features, las sidebars, los iconos y el theming del prototipo
`PrototipoFrontend/` (Next.js 16 + React 19 + Tailwind 4 + shadcn, generado en
v0.app), manteniendo el **avatar 3D real como protagonista** y sin romper el
contrato `window.SignAI`.

## Enfoque: reconstruir nativo, NO embeber el web

El prototipo es una app web separada. **No se porta su código**; se usa como
spec visual/funcional y se reconstruye con Compose + Lumo + Phosphor.

Razones (alineadas con `AGENTS.md` y `openspec/config.yaml`):

1. Compose es el framework de UI del proyecto; el WebView es **solo** el
   canvas 3D del avatar.
2. El "avatar" del prototipo es un muñeco CSS placeholder; el real es
   `avatar.glb` (Three.js). No hay nada que reutilizar de esa escena.
3. Theming (light/dark), accesibilidad (bold/large/contrast/reduce-motion),
   edge-swipe drawers y bottom sheets son más robustos y performantes en
   Compose nativo que en un WebView.
4. Embeber la UI web degradaría el rendimiento 3D (prohibido por AGENTS.md)
   y duplicaría estado (Kotlin ↔ web).

**Regla transversal:** cualquier feature nueva del prototipo se implementa en
Compose. El único código web que se toca es `assets/web/app.js` para
**extender** el bridge (`window.SignAI`), de forma aditiva.

> **Catálogo completo de features (fuente de verdad):**
> [`openspec/specs/ui/spec.md`](../../specs/ui/spec.md). Ese spec lista **todas**
> las features que va a tener la app, con IDs (`NAV`, `SPL`, `EDG`, `STG`,
> `CTL`, `HIS`, `CNV`, `SET`, `PER`, `ACC`, `APP`, `STA`, `GLS`, `BRG`, `ANI`,
> `SEM`, `RSP`, `BRD`, `OM`). Toda implementación debe cubrir esos IDs.

### Mapa fase → features del spec

| Fase | Features cubiertas |
|---|---|
| A — Design system | `APP-4/6`, `ACC-2/3/4`, `ANI-8`, tokens de `APP-*`/`RSP-*`, iconos (§19) |
| B — Chrome principal | `SPL-1..7`, `STG-1..7`, `CTL-1..9/11` |
| C — Sidebars | `EDG-1..14`, `NAV-5/6`, `CNV-1..8`, `SET-1/2/5/6/9/10`, `PER-1..5` |
| D — Historial + About | `NAV-3/4/7/8`, `HIS-1..8`, `BRD-2/3/4` |
| E — Estado + bridge | `STA-1..10`, `GLS-1..7`, `BRG-1..8`, `STG-4` |
| F — Ajustes + theming | `SET-3/4/7/8`, `PER-4`, `ACC-1..7`, `APP-1/2/3/5/7` |
| G — Polish + docs | `ANI-1..7`, `SEM-1..5`, `RSP-1..5`, `BRD-1`, `OM-*` |

---

## Inventario del prototipo (qué hay que replicar)

Archivos fuente de referencia:

- `PrototipoFrontend/app/page.tsx` (240 líneas) — toda la UI y el estado.
- `PrototipoFrontend/app/globals.css` (55 líneas densas) — todo el estilo,
  las CSS variables de theming y las animaciones.
- `PrototipoFrontend/components/ui/button.tsx`, `lib/utils.ts` — scaffolding
  shadcn (no relevante; nuestra base es Lumo).
- `PrototipoFrontend/public/*` — iconos/placeholders de v0 (no usar).

### Pantallas / vistas

| # | Vista prototipo | Estado actual en la app | Acción |
|---|---|---|---|
| 1 | **Splash** (logo `S`, eyebrow `SEÑA`, barra de progreso, `%`) | `SignAiSplash` = spinner circular centrado | Rediseñar (Fase B) |
| 2 | **Main** (avatar stage + control panel + pestañas de borde) | Avatar WebView + `SignAiBottomBar` + drawer derecho | Rediseñar (Fases B/C) |
| 3 | **About** (logo, copy, meta Javeriana/versión) | No existe | Crear (Fase D) |
| 4 | **History bottom sheet** (handle, `TUS FRASES`, `Reutilizar →`) | `HistoryDropdown` (popup anclado) | Reemplazar (Fase D) |
| 5 | **Left drawer — Conversación** (`TÚ`, `TRADUCCIÓN`, estado avatar, repetir) | No existe | Crear (Fase C) |
| 6 | **Right drawer — Ajustes** (AVATAR / ACCESIBILIDAD / APARIENCIA / About) | `SettingsSidebar` (4 toggles debug + sign picker) | Rediseñar (Fases C/F) |

### Main — elementos

| Elemento prototipo | Detalle | Estado actual | Acción |
|---|---|---|---|
| Pestaña borde **izquierdo** | Abre Conversación; click o drag | No existe | Fase C |
| Pestaña borde **derecho** | Abre Ajustes; click o drag | Existe (`SignAiEdgeDrawer`, solo derecha) | Generalizar |
| Drag preview | Panel translúcido que sigue el dedo | No existe | Fase C |
| **stage-hint** | `● Listo para traducir` (top-left) | No existe (solo `#status` dentro del WebView, y es de debug) | Fase B |
| Avatar 3D | Real (`avatar.glb`) | ✅ Existe | Conservar |
| **stage-caption** | `LSC · Avatar 3D` (bottom-center) | No existe | Fase B |
| **Botón Repetir** | Aparece en estado `done` | No existe | Fase B |
| Input | Placeholder `Escribe una frase...` | Placeholder `Versión: X` | Fase B |
| Botón Historial | Ícono a la derecha del input | ✅ Existe (dropdown) | Mover a bottom sheet |
| Botón **Traducir** | Ícono + label, ancho 75% | Ícono solo, 75% | Fase B |
| Botón **Mic** | Ícono, ancho 25% | ✅ Existe | Conservar (placeholder) |
| Home indicator | Barrita tipo iOS | No existe | **Omitir** (Android ya tiene gestos) |

### Left drawer — Conversación

| Elemento | Detalle | Acción |
|---|---|---|
| Header | `CONTEXTO` / `Conversación` + cerrar | Fase C |
| Bloque `TÚ` | Texto del usuario o "Aún no has escrito una frase." | Fase C |
| Bloque `TRADUCCIÓN` | Gloss LSC (`glossFor`) | Fase C + E (gloss real) |
| Estado avatar | Mini-tile + `AVATAR` + estado | Fase C |
| Botón repetir | Ícono cuadrado | Fase C |
| Swipe note | "Desliza desde el borde para volver" | Fase C |

### Right drawer — Ajustes

| Sección | Control | Estado actual | Acción |
|---|---|---|---|
| AVATAR | **Personaje** (Julián/Samuel/Ximena/Gris) con dots + check | No existe | Fase C/F |
| AVATAR | **Velocidad de señas** (slider 10–100) | No existe | Fase C/F |
| ACCESIBILIDAD | Tamaño de interfaz (slider) | No existe | Fase F |
| ACCESIBILIDAD | Texto en negrita (switch) | No existe | Fase F |
| ACCESIBILIDAD | Contraste aumentado (switch) | No existe | Fase F |
| ACCESIBILIDAD | Texto más grande (switch) | No existe | Fase F |
| ACCESIBILIDAD | Reducir animaciones (switch) | No existe | Fase F |
| APARIENCIA | Modo oscuro/claro (switch) | Siempre dark | Fase F |
| — | Acerca de (link) | No existe | Fase D |
| — | Modo debug / Cámara libre / Suelo / Fondo (actuales) | ✅ Existen | Reubicar en sección "Avanzado" (¿? decisión) |
| — | Selector de seña (actual) | ✅ Existe | Conservar (sección "Avanzado") |

### Theming / tokens del prototipo

CSS variables en `globals.css` (línea 7 y `.theme-dark` línea 23):

| Token prototipo | Light | Dark | Mapeo propuesto (Lumo `Colors`) |
|---|---|---|---|
| `--background` | `#f4f4f2` | `#111110` | `background` |
| `--foreground` | `#171717` | `#f5f5f1` | `text` |
| `--muted` | `#777773` | `#969690` | `textSecondary` |
| `--line` | `#deded9` | `#32322f` | `outline` |
| `--surface` | `rgba(255,255,253,.92)` | `rgba(30,30,28,.94)` | `surface` (+alpha) |
| `--strong` | `#171717` | `#f5f5f1` | `primary` en light / `text` en dark |
| `--soft` | `#e8e8e3` | `#292926` | `surfaceVariant` (token nuevo) |
| `--radius` | `24px` | `24px` | `SignAiShapes.cornerXl` (nuevo) |

Clases de modo: `.theme-dark`, `.font-bold` (w650), `.font-large` (17px),
`.high-contrast` (`--line #777`), `prefers-reduced-motion`.

### Lógica del prototipo

| Lógica | Detalle | Cómo se resuelve |
|---|---|---|
| Splash progreso | `progress 0→100` fake, luego `loaded` | `WebChromeClient.onProgressChanged` real (Fase B) |
| Máquina de estados | `idle/listening/processing/translating/signing/done` | `PlaybackState` en `MainScreenState` (Fase E) |
| `send()` | translating → signing (850ms) → done (2700ms) | Con callback real de fin de reproducción (Fase E) |
| `glossFor(text)` | 4 reglas hardcodeadas + fallback uppercase | `domain/Gloss.kt` función pura (Fase E) |
| Historial | starter 5, cap 5, dedupe | Ya existe persistido (cap 20, top-5 protegido); adaptar presentación |
| Mic | Auto-rellena frase y "procesa" | Placeholder: mantener `play("mic")` (Fase E) |
| Personajes | Recolor CSS (pelo/cuerpo) | Tinte del GLB vía `setCharacter` o UI-only (decisión abierta) |
| Velocidad | Slider sin efecto | `setSpeed` en `app.js` (Fase E) |

### Iconos (lucide → Phosphor Regular)

Todos verificados como existentes en `phosphoricons-regular-android:1.0.5`:

| Prototipo (lucide) | Phosphor propuesto | Uso |
|---|---|---|
| `ArrowLeft` | `ArrowLeft` | Volver (About) |
| `Check` | `Check` | Personaje seleccionado |
| `ChevronDown` | `CaretDown` | Trigger de dropdown |
| `ChevronRight` | `CaretRight` | `Reutilizar →`, About |
| `Clock3` / `History` | `Clock` / `ClockCounterClockwise` | Historial |
| `Contrast` | `CircleHalf` | Contraste |
| `Info` | `Info` | About |
| `Minus` | `Minus` | Reducir |
| `Mic` | `Microphone` | ✅ ya |
| `Moon` / `Sun` | `Moon` / `Sun` | Tema |
| `Plus` | `Plus` | Aumentar |
| `RotateCcw` | `ArrowCounterClockwise` | Repetir |
| `Send` | `PaperPlaneTilt` | ✅ ya |
| `Settings2` | `SlidersHorizontal` | Ajustes |
| `Sparkles` | `Sparkle` | Avatar status |
| `Type` | `TextAa` | Tamaño de texto |
| `Volume2` | `SpeakerHigh` | Velocidad |
| `X` | `X` | Cerrar |
| `Play` | `Play` | (no usado aún) |

---

## Capacidades nuevas necesarias

1. **Drawer bidireccional.** `SignAiEdgeDrawer` hoy es solo derecha y
   monolítico (`EdgeDrawer.kt`, 217 líneas). Generalizar a `side: Start|End`
   con pestaña visible + drag + drag-preview, reutilizado por ambos lados.
2. **Bottom sheet de historial.** `ModalBottomSheet` de Lumo ya está generado
   en `ui/designsystem/components/` (verificar); si no, usar `Popup`/`Dialog`
   con animación `rise`.
3. **Callback JS→Kotlin de fin de reproducción.** `app.js` no avisa cuando
   termina `play()`. Opciones:
   - `WebViewCompat.addWebMessageListener` (androidx.webkit) — recomendado,
     seguro y moderno; requiere feature check.
   - `@JavascriptInterface` — simple pero menos seguro.
   - `onConsoleMessage` parsing — descartado (frágil).
4. **Comando JS nuevo (aditivo):** `setSpeed(0..1)` en `window.SignAI`;
   mantener `?.` y los existentes. (Personajes = UI-only, sin comando.)
5. **Gloss LSC.** Función pura en `domain/Gloss.kt` (port de `glossFor`).
   Nota: es un placeholder honesto; el motor de gloss real está fuera de
   alcance (`config.yaml`).
6. **Design system extendido.** Light colors, `surfaceVariant`/`muted`/`strong`,
   motion tokens, escala tipográfica (eyebrow, caption, quote, gloss), y
   shapes/sizes/spacing alineados al prototipo.
7. **Settings reales** con persistencia: personaje (UI-only), velocidad, bold,
   large, contrast, reduceMotion, darkTheme.

---

## Qué NO copiar / adaptar

- **Home indicator**: duplica la barra de navegación por gestos de Android.
  Se omite.
- **Avatar CSS**: se usa el GLB real.
- **`starterHistory` y auto-fill del mic**: mocks del prototipo. Se mantiene
  el historial real persistido; el mic sigue placeholder (`play("mic")`).
- **Assets de v0** (`public/*.png/svg`, `apple-icon`, etc.): no se usan.
- **Branding**: el prototipo se llama "Seña"; el proyecto "SignAI". Se mantiene
  "SignAI" **por ahora** (decisión abierta — ver abajo); el wordmark se
  centraliza para que sea trivial de cambiar.
- **`components/ui/button.tsx` / Tailwind / shadcn**: nuestra base es Lumo.

---

## Fases de ejecución

Cada fase termina con `./gradlew build` verde + `./gradlew installDebug` en el
device (regla de `AGENTS.md`) + verificación visual.

### Fase A — Design system extendido (sin cambiar pantallas)

- [x] A.1 `ui/designsystem/Color.kt`: dejar utilizable `LightColors`; agregar
      tokens que faltan (`muted`, `surfaceVariant`/`soft`, `strong`) o mapear a
      los existentes Lumo. Mantener dark Lumo actual como default.
- [x] A.2 `ui/designsystem/foundation/SignAiMotion.kt` (nuevo): duraciones
      (`fast 160`, `short 180`, `medium 250`, `slow 500`) y easings; flag
      reduce-motion. (El plan decía `fast 200`; se usó **160** por fidelidad al
      prototipo/`ANI-3`, dropdown-in .16s.)
- [x] A.3 `ui/designsystem/foundation/SignAiSizes.kt` / `SignAiShapes.kt` /
      `SignAiSpacing.kt`: alinear al prototipo (radio 24/16/13/12, panel
      `min(88%,350dp)`, botón 50/56, field 50).
- [x] A.4 `ui/designsystem/Typography.kt` + `ui/theme/SignAiTypography.kt`:
      agregar estilos `eyebrow`, `caption`, `quote`, `gloss`, `label`.
- [x] A.5 `ui/icons/SignAiIcons.kt`: agregar todos los iconos del mapeo.
- [x] A.6 Build + install (paridad: sin cambios visuales).
      `./gradlew build` verde + `installDebug` OK en Redmi Note 9S (Android 11).

### Fase B — Chrome principal (stage + control panel + splash)

- [x] B.1 `ui/screens/stage/AvatarStage.kt` (nuevo): envuelve `AvatarWebView`
      + `stage-hint` (live-dot + estado) + `stage-caption` + botón Repetir.
      **Nota:** requirió adelantar `PlaybackState` (era E.1) para el texto de
      estado; la orquestación real sigue en E.
- [x] B.2 `ui/screens/bottombar/SignAiBottomBar.kt`: input con placeholder
      real, botón historial, fila `Traducir (flex) | Mic (50dp)` con label.
      **Decisión:** se siguió el **prototipo** (Traducir a la izquierda con
      label, Mic a la derecha 50dp), no el "Mic | Traducir 25/75" del plan.
- [x] B.3 `ui/components/SignAiSplash.kt`: rediseño (logo tile, eyebrow,
      progreso real vía `onProgressChanged`, `%`). `AvatarWebView` expone
      `onProgress: (Int) -> Unit`.
- [x] B.4 Build + install + verificación (capturas: main + splash 80%).

### Fase C — Sidebars (drawers)

- [x] C.1 Generalizar `SignAiEdgeDrawer` a `side: DrawerSide` (Start/End),
      con pestaña + drag + drag-preview reutilizables.
- [x] C.2 `ui/screens/conversation/ConversationPanel.kt` (nuevo) para el
      drawer izquierdo.
- [x] C.3 `ui/screens/settings/SettingsSidebar.kt`: rediseño con secciones
      AVATAR / ACCESIBILIDAD / APARIENCIA / About, más una sección
      **"Avanzado"** al final con los ajustes debug actuales (Modo debug,
      Cámara libre, Suelo, Fondo, selector de Seña).
- [x] C.4 Blur/scrim: mantener `RenderEffect` guardado a Android 12+ y solo
      mientras un drawer está abierto (rendimiento).
- [x] C.5 Build + install + verificación (capturas con cada drawer abierto;
      `adb input` bloqueado en MIUI, así que la verificación de *gestos* queda
      para prueba manual del usuario).
      **Adelantos:** `domain/Gloss.kt` (era E.2), campos de `Settings`
      (era F.1) y `SignAiSlider`. La verificación funcional de esos ajustes
      sigue en F.

### Fase D — Historial bottom sheet + About

- [x] D.1 `ui/components/HistoryBottomSheet.kt` (nuevo): handle, `TUS FRASES`,
      items con `Reutilizar →`; reemplaza `HistoryDropdown` en el BottomBar
      (eliminado). El input se elevó a `MainScreenState.inputText`.
- [x] D.2 `ui/screens/about/AboutScreen.kt` (nuevo): logo, copy, meta con
      versión dinámica (`BuildConfig.VERSION_NAME`). `NAV-7/8`: About es
      excluyente y abrirla desde Ajustes cierra el drawer.
- [x] D.3 Build + install + verificación (capturas de sheet y About).

### Fase E — Estado de reproducción + bridge

- [x] E.1 `ui/state/PlaybackState.kt` (nuevo): `Idle, Listening, Processing,
      Translating, Signing, Done` + texto de estado. **(adelantado a la Fase B)**
- [x] E.2 `domain/Gloss.kt` (nuevo): `glossFor(text): String` pura.
      **(adelantado a la Fase C)**
- [x] E.3 `ui/webview/AvatarWebView.kt`: `WebViewCompat.addWebMessageListener`
      (`SignAIBridge`, feature check) para `onPlaybackDone`; se propaga a
      `MainScreenState.onPlaybackDone()`.
- [x] E.4 `domain/SignAiCommands.kt`: `setSpeed(0..1)` (aditivo).
- [x] E.5 `assets/web/app.js`: emite `SignAIBridge.postMessage("onPlaybackDone")`
      al terminar `play()`; implementa `setSpeed` (factor 0.1..3×).
      `index.html` sin cambios de contrato. `node --check` OK.
- [x] E.6 `MainScreenState`: orquesta `play()` → `Translating` → (850 ms)
      `Signing` → `Done` con el callback real; mock del mic (`Listening`→
      auto-relleno→`Processing`→`Idle`).
- [x] E.7 Build + install + verificación (`node --check app.js` incluido;
      captura en `Done` con botón Repetir ⇒ el bridge funciona).

### Fase F — Ajustes funcionales + theming

- [ ] F.1 `ui/state/Settings.kt`: agregar `character` (UI-only), `speed`,
      `bold`, `large`, `contrast`, `reduceMotion`, `darkTheme`.
- [ ] F.2 Aplicar accesibilidad real: escala tipográfica, weight, tokens de
      contraste, motion off.
- [ ] F.3 Dark/light switch: `SignAiTheme` deja de ser siempre dark; usar
      `LightColors`/`DarkColors` de Lumo + `enableEdgeToEdge` coherente.
- [ ] F.4 Persistencia de settings en SharedPreferences (extender
      `data/SignHistoryRepository` o crear `data/SettingsRepository`).
- [ ] F.5 Build + install + verificación.

### Fase G — Polish + documentación

- [ ] G.1 Animaciones sutiles (fade contenido, rise del sheet, slide drawer)
      respetando reduce-motion.
- [ ] G.2 Actualizar `openspec/specs/`, sesión y `AGENTS.md` (nuevas
      capacidades y contrato JS extendido).
- [ ] G.3 Capturas comparativas vs prototipo.

---

## Riesgos

| Riesgo | Mitigación |
|---|---|
| Performance 3D del WebView | Nada de blur/animaciones pesadas sobre el canvas; blur solo con drawer abierto y Android 12+ |
| Light mode con avatar 3D gris | El GLB puede verse mal sobre fondo claro; probar y, si no convence, dejar dark-only o ajustar fondo del canvas |
| `WebMessageListener` | Requiere `WebViewFeature.isFeatureSupported` + fallback |
| "Personajes" con GLB único | Tinte puede no ser convincente; opción UI-only (decisión abierta) |
| Scope grande (7 fases) | Entregar fase por fase, cada una instalable y verificable |
| Cambiar el contrato JS | Todo aditivo; los 5 comandos existentes y el `?.` se mantienen |
| Historial cap 5 (prototipo) vs cap 20 (app) | Mantener el cap real 20; la UI muestra los primeros N |

---

## Decisiones resueltas (2026-09-23)

1. **Branding — ABIERTO.** Se mantiene **"SignAI"** por ahora, pero el nombre
   **seguramente cambiará** (el prototipo usa "Seña"). El wordmark debe quedar
   **fácil de cambiar**: una sola constante / `string resource` (logo `S`,
   eyebrow y título leen de ahí), sin literales dispersos.
2. **Personajes — UI-only.** El picker (Julián/Samuel/Ximena/Gris) se
   implementa completo a nivel visual (dots, check, selección, persistencia)
   pero **sin efecto sobre el GLB** todavía. Queda listo para futuros avatares.
3. **Light mode — switch real.** Se implementa dark/light con
   `LightColors`/`DarkColors` de Lumo. El avatar 3D se prueba sobre fondo claro
   y, si no convence, se ajusta el fondo del canvas (no se descarta el switch).
4. **Settings de debug — sección "Avanzado".** Modo debug, Cámara libre,
   Suelo, Fondo y selector de Seña se conservan al final del drawer de Ajustes,
   separados de los ajustes de usuario.
5. **Ritmo — fase por fase con install.** Cada fase termina con
   `./gradlew build` + `./gradlew installDebug` para aprobación antes de la
   siguiente. **No arrancar sin OK explícito del usuario.**

## Referencia

- Prototipo: `PrototipoFrontend/` (`app/page.tsx`, `app/globals.css`).
- Para verlo: `cd PrototipoFrontend && pnpm install && pnpm dev` → `localhost:3000`.
- Estado inicial del plan: pendiente de OK para arrancar **Fase A**.
