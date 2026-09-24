# SignAI WebView — Agent Instructions

Thesis: Spanish speech → LSC via 3D avatar. This repo is **only** the WebView app.
Old approaches (Flask server, Kotlin+Filament native, Unity) are dead — ignore any docs referencing them.

## What it is

Single-module Gradle project (`SignAI-WebView`, `:app` only). Compose shell + WebView that renders a local Three.js avatar. No backend, no network API.

- Kotlin UI: layered packages under `app/src/main/java/com/signai/webview/` — `data/` + `domain/` (business), `ui/state/` + `ui/webview/` (presentation), `ui/{theme,designsystem,icons,components}/` (reusable UI), `ui/screens/` + `ui/MainScreen.kt` (screens). `MainActivity.kt` is a ~27-line entry point.
- Web scene: `app/src/main/assets/web/` — `index.html`, `app.js`, `avatar.glb`, `sign.json`, `bosque.jpg`
- Package `com.signai.webview`, portrait-locked (`AndroidManifest.xml`)

## Layers (Clean Architecture, practical)

Dependency rule: screens → components → state/webview → data/domain, **never backwards**. No Hilt/ViewModel: `MainScreenState` (holds `SnapshotStateList` history + `Settings` + `sign` + playback state) is built in `MainActivity` and passed down; `SharedPrefsSignHistoryRepository` and `SharedPrefsSettingsRepository` own SharedPreferences; `WebViewSignAiController` is the only place with `evaluateJavascript`/`RenderEffect`; `domain/SignAiCommands`, `domain/SignsCatalog`, `domain/Gloss` and `domain/Settings` are pure Kotlin.

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
- Palette (single source of truth): **Lumo palettes**, edited in `ui/designsystem/Color.kt` (`// CUSTOMIZED`). Both palettes use the **Vercel prototype tokens verbatim** (`PrototipoFrontend/app/globals.css`). **Light is the default** (`Settings.darkTheme = false`): `background #F4F4F2`, `text #171717`, `surface #FEFEFC` (opaque composite of the prototype's `rgba(255,255,253,.92)` + `blur(14px)` — Compose has no cheap region blur), `outline #DEDED9`, `muted #777773`, `strong #171717`, `soft #E8E8E3`. Dark: `#111110 / #F5F5F1 / #1E1E1C / #32322F / #969690 / #292926`. `primary` follows `strong` (the prototype has no blue accent). Tokens `strong`/`onStrong`/`soft` and `muted` (alias of `textSecondary`) live next to them. Do NOT re-run `lumo --setup` (overwrites). `ui/theme/SignAiTheme` delegates to Lumo and exposes `colors: Colors` — `SignAiColors` no longer exists.
- Tokens live in `ui/designsystem/foundation/`: `SignAiSizes`, `SignAiShapes`, `SignAiSpacing`, `SignAiMotion` (durations/easings + `LocalReduceMotion`) and `pressScale`. Use them instead of inline `.dp`/literals.
- Theming: `SignAiTheme(darkTheme, fontScale, uiScale, bold, highContrast, reduceMotion)` applies `Settings`. `interfaceSize` scales `LocalDensity`; `large`/`bold` transform the typography; `contrast` overrides `outline`; light mode also switches the 3D canvas background via the `setTheme` bridge command.
- Font: the prototype uses `Arial, Helvetica, sans-serif`; Android ships neither, so **Liberation Sans** (metric-compatible with Arial) is bundled in `res/font/` and wired as `SignAiFontFamily` in `ui/designsystem/Typography.kt`. This keeps layout and text weight identical to the prototype.
- Spinner/loader: `SignAiSplash` (`ui/components/SignAiSplash.kt`) is shown until `AvatarWebView`'s `onLoaded`, with real progress from `onProgressChanged` and an 8 s timeout fallback; content fades in (500 ms).
- Screens: `ui/screens/stage/AvatarStage.kt` (avatar + hint + caption + Repetir), `ui/screens/bottombar/SignAiBottomBar.kt`, `ui/components/HistoryBottomSheet.kt`, `ui/screens/conversation/ConversationPanel.kt` (left drawer), `ui/screens/settings/SettingsSidebar.kt` (right drawer, sections AVATAR/ACCESIBILIDAD/APARIENCIA/AVANZADO), `ui/screens/about/AboutScreen.kt`. Branding ("SignAI", may become "Seña") is centralized in `ui/SignAiBranding.kt`.
- Look: minimalist, modern, dark mode base with good contrast and legibility. The 3D avatar stays the visual protagonist.
- Transparencies, subtle surfaces, blur, and animations are allowed only when subtle, functional, lightweight, and in moderation.
- Keep SignAI components reusable and separate from screens. Keep the UI lightweight — never degrade WebView 3D performance with unnecessary libraries, effects, or animations.

## Kotlin ↔ JS bridge

WebView loads via `WebViewAssetLoader` at `https://appassets.androidplatform.net/assets/web/index.html`. JS console forwards to Logcat tag `SignAIWeb`.

Contract (`window.SignAI` in `app.js`), **backward compatible** — keep the `?.` guard and these exact names:
- Original 5: `play()`, `setDebug(bool)`, `setFreeCamera(bool)`, `setGrid(bool)`, `setBackground(bool)`.
- Added (additive): `setSpeed(factor 0..1)` (0.5 ≈ normal), `setTheme(dark)` (canvas background follows the app theme).
Kotlin calls them with `evaluateJavascript("window.SignAI?.<method>(...)")`.

JS → Kotlin: `window.SignAIBridge.postMessage("onPlaybackDone")` when `play()` finishes; Kotlin registers it with `WebViewCompat.addWebMessageListener` (feature-guarded) and drives the playback state machine.

## Web asset rules

- `index.html` loads Three.js r169 from jsDelivr CDN via importmap → **needs INTERNET permission + connectivity at runtime**, even though avatar/data are local.
- `app.js` is self-contained (port of old Flask `app/static/app.js`, no video, no sign picker). `avatar.glb`, `sign.json`, `bosque.jpg` load via **relative paths** — keep all five files in the same `assets/web/` dir.
- `shouldInterceptRequest` forces `Cache-Control: no-store` so edits to `app.js`/`sign.json` show on reload; don't remove it.
- `sign.json` is a single baked sign (`stem 0000_0000_0000`, 59 frames @24fps). Adding a sign = regenerating/replacing this file (source CSVs in `data/LANDMARKS/`: BODY/FACE/HANDS) **and** extending `domain/SignsCatalog.kt` (`available`).

## Gotchas

- All Compose state lives in `ui/state/MainScreenState.kt`; there is no ViewModel/DI/navigation. History is `SharedPreferences "signai"/"history"` (max 20, top-5 never reorder) behind `data/SignHistoryRepository.kt`; settings user choices persist in the same file behind `data/SettingsRepository.kt`.
- Playback state machine (`ui/state/PlaybackState.kt`): `idle → translating → signing → done`; `signing → done` is driven by the real `onPlaybackDone` bridge event, not a timer. Mic is a placeholder that toggles `listening` and auto-fills a demo phrase (`OM-4`) — no real speech recognition.
- The two edge drawers are one component (`ui/designsystem/components/EdgeDrawer.kt`, `side = Start/End`) with tap + drag + drag-preview; Conversation is `Start`, Settings is `End`, and opening one closes the other.
- **Drawer layering is fragile — read the `ORO` comment at the top of `EdgeDrawer.kt` before touching it** (spec `EDG-22`/`EDG-23`). Three rules: (1) a `draggable`/`clickable` with `enabled = false` STILL registers a Compose input node and stays the topmost hit route, so an inactive full-screen drawer silently swallows the active drawer's taps — never attach the gesture at all when it may not act; (2) the `AndroidView` WebView is a real child View and takes every touch inside its bounds, so it must be the bottom child with the drawers composed above it, and its `pointerInteropFilter` returns true while a drawer is open; (3) the backdrop is a full-screen node composed BEFORE the panel and the handle. The two drawers are siblings (not nested) and an inactive one composes no interactive overlay at all.
- One drawer at a time is enforced twice: reactively by `otherDrawerOpen`, and synchronously by `MainScreenState.claimDrawerGesture` (an `AtomicReference`, deliberately not Compose state) claimed on the first pointer event, so two fingers cannot open both panels in the same frame (`EDG-19`).
- Settings sidebar sends `setDebug/setFreeCamera/setGrid/setBackground/setTheme` toggles; blur overlay uses `RenderEffect` (Android 12+ only, guarded) and only while a drawer is open. The `#status` overlay text is part of debug mode and only shows when debug is on.
- Known device quirk: on MIUI the status-bar icon appearance does not follow the light theme (`isAppearanceLightStatusBars` + legacy flag ignored).
- **MIUI "smart dark" (force-dark) MUST stay off** (`android:forceDarkAllowed=false` in `res/values/themes.xml`). With it on, MIUI inverts content drawn inside hardware layers — text inside `verticalScroll` containers and drawers renders **white** while solid boxes stay correct (bug found 2026-09-23). Do not remove that item.
- Bone naming that matters for `app.js`: fingers have no `DEF-` prefix (`thumb01L`, `f_index01L`…), arms use `DEF-` (`DEF-upper_armL`…), face is camelCase without dots (`DEF-browTL`, `DEF-lipTL`, `DEF-lidTL`, `DEF-jaw_master`). Right brow `DEF-browTR` has ~4 weighted verts vs ~32 left → right brow barely moves (rig issue, not JS).

## Context engineering (OpenSpec)

`openspec/` is the project's living context — keep it updated with every non-trivial change:

- `specs/` (avatar, dataset, pipeline, ui): source of truth. `specs/ui/spec.md` is the **complete feature catalog** of the app (all features come from the `PrototipoFrontend/` Vercel prototype); `avatar`/`dataset`/`pipeline` cover rig/animation behavior. Update the relevant spec when fixing such behavior.
- `sessions/`: work log, one file per session (`YYYY-MM-DD-<slug>.md`, header with title/day/date/time). Append what was done, decided, and left pending at the end of each work item. Convention in `openspec/sessions/README.md`.
