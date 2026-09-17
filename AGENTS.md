# SignAI WebView — Agent Instructions

Thesis: Spanish speech → LSC via 3D avatar. This repo is **only** the WebView app.
Old approaches (Flask server, Kotlin+Filament native, Unity) are dead — ignore any docs referencing them.

## What it is

Single-module Gradle project (`SignAI-WebView`, `:app` only). Compose shell + WebView that renders a local Three.js avatar. No backend, no network API.

- Kotlin UI: layered packages under `app/src/main/java/com/signai/webview/` — `data/` + `domain/` (business), `ui/state/` + `ui/webview/` (presentation), `ui/{theme,designsystem,icons,components}/` (reusable UI), `ui/screens/` + `ui/MainScreen.kt` (screens). `MainActivity.kt` is a ~27-line entry point.
- Web scene: `app/src/main/assets/web/` — `index.html`, `app.js`, `avatar.glb`, `sign.json`, `bosque.jpg`
- Package `com.signai.webview`, portrait-locked (`AndroidManifest.xml`)

## Layers (Clean Architecture, practical)

Dependency rule: screens → components → state/webview → data/domain, **never backwards**. No Hilt/ViewModel: `MainScreenState` (holds `SnapshotStateList` history + `Settings` + `sign`) is built in `MainActivity` and passed down; `SharedPrefsSignHistoryRepository` owns SharedPreferences; `WebViewSignAiController` is the only place with `evaluateJavascript`/`RenderEffect`; `domain/SignAiCommands` and `domain/SignsCatalog` are pure Kotlin.

## Build

```bash
./gradlew build          # verify before committing
./gradlew installDebug   # needs device/emulator via adb
```

**MANDATORY after every change**: compile/verify the code and then run `./gradlew installDebug` on the connected device/emulator so the user can test immediately — never end a work item without installing. If no device is present, say so explicitly.

No tests, no CI, no lint. Toolchain: AGP 8.9.3, Kotlin 2.3.21, compileSdk/target 36, minSdk 26, JVM 17, Compose BOM 2024.12.01. Bumped from 8.7.3/2.1.0/35 → 8.9.3/2.3.21/36 in `ui-migration-lumo` Fase 0 (Phosphor 1.0.5 requires compileSdk 36 + Kotlin 2.3+ metadata).

## UI direction

- Jetpack Compose is the UI framework. New components use **Lumo UI by Noman R** (https://github.com/nomanr/lumo-ui, docs https://www.lumoui.com/) — NOT Vaadin's Lumo Design System. It is Compose-specific and shadcn-like: adopt its components as a base, then customize them into our own SignAI Design System. Consult its current docs before making Lumo decisions; don't assume it works like a conventional component library.
- Migrate existing Material 3 away progressively, never add new Material 3 components.
- Icons: Phosphor Icons for anything new; avoid `material-icons-extended` in new code.
- Build a SignAI Design System on top of Lumo UI; centralize colors, typography, spacing, sizes, shapes, states, and animations there.
- Palette (single source of truth): **Lumo's default dark palette**, edited in `ui/designsystem/Color.kt` (`// CUSTOMIZED`). Black background, gray surfaces `Gray900 #282828`, white text, outline `Gray800 #4B4B4B`; accent `primary` overridden to Lumo's `Blue900 #276EF1`. Do NOT re-run `lumo --setup` (overwrites). `ui/theme/SignAiTheme` delegates to Lumo and exposes `colors: Colors` — `SignAiColors` no longer exists.
- Tokens live in `ui/designsystem/foundation/`: `SignAiSizes` (buttonHeight 56, fieldHeight 44, drawerWidth 300…), `SignAiShapes` (cornerSmall 6, cornerMedium 8), `SignAiSpacing` (xs 4, sm 8, md 16…). Use them instead of inline `.dp`.
- Spinner/loader: Lumo `ProgressIndicators` (`ui/designsystem/components/progressindicators/`). The app shows a `SignAiSplash` overlay (`ui/components/SignAiSplash.kt`) until `AvatarWebView`'s `onLoaded` (`onPageFinished`) fires, with an 8 s timeout fallback; content fades in (500 ms).
- Look: minimalist, modern, dark mode base with good contrast and legibility. The 3D avatar stays the visual protagonist.
- Transparencies, subtle surfaces, blur, and animations are allowed only when subtle, functional, lightweight, and in moderation.
- Keep SignAI components reusable and separate from screens. Keep the UI lightweight — never degrade WebView 3D performance with unnecessary libraries, effects, or animations.

## Kotlin ↔ JS bridge

WebView loads via `WebViewAssetLoader` at `https://appassets.androidplatform.net/assets/web/index.html`. JS console forwards to Logcat tag `SignAIWeb`.

Contract (`window.SignAI` in `app.js`): `play()`, `setDebug(bool)`, `setFreeCamera(bool)`, `setGrid(bool)`, `setBackground(bool)`. Kotlin calls them with `evaluateJavascript("window.SignAI?.<method>(...)")` — keep the `?.` guard and these exact names.

## Web asset rules

- `index.html` loads Three.js r169 from jsDelivr CDN via importmap → **needs INTERNET permission + connectivity at runtime**, even though avatar/data are local.
- `app.js` is self-contained (port of old Flask `app/static/app.js`, no video, no sign picker). `avatar.glb`, `sign.json`, `bosque.jpg` load via **relative paths** — keep all five files in the same `assets/web/` dir.
- `shouldInterceptRequest` forces `Cache-Control: no-store` so edits to `app.js`/`sign.json` show on reload; don't remove it.
- `sign.json` is a single baked sign (`stem 0000_0000_0000`, 59 frames @24fps). Adding a sign = regenerating/replacing this file (source CSVs in `data/LANDMARKS/`: BODY/FACE/HANDS) **and** extending `domain/SignsCatalog.kt` (`available`).

## Gotchas

- All Compose state lives in `ui/state/MainScreenState.kt`; there is no ViewModel/DI/navigation. History is `SharedPreferences "signai"/"history"` (max 20, top-5 never reorder) behind `data/SignHistoryRepository.kt`.
- Mic button is a placeholder (`play("mic")`); bottom-bar Send just replays the baked sign.
- Settings sidebar sends `setDebug/setFreeCamera/setGrid/setBackground` toggles with defaults debug=false, grid=true, freeCam=false, bg=false; blur overlay uses `RenderEffect` (Android 12+ only, guarded). The `#status` overlay text ("Avatar listo…") is part of debug mode and only shows when debug is on.
- Bone naming that matters for `app.js`: fingers have no `DEF-` prefix (`thumb01L`, `f_index01L`…), arms use `DEF-` (`DEF-upper_armL`…), face is camelCase without dots (`DEF-browTL`, `DEF-lipTL`, `DEF-lidTL`, `DEF-jaw_master`). Right brow `DEF-browTR` has ~4 weighted verts vs ~32 left → right brow barely moves (rig issue, not JS).

## Context engineering (OpenSpec)

`openspec/` is the project's living context — keep it updated with every non-trivial change:

- `specs/` (avatar, dataset, pipeline): source of truth for rig/animation behavior; update the relevant spec when fixing such behavior.
- `sessions/`: work log, one file per session (`YYYY-MM-DD-<slug>.md`, header with title/day/date/time). Append what was done, decided, and left pending at the end of each work item. Convention in `openspec/sessions/README.md`.
