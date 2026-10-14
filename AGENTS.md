# SignAI WebView — Agent Instructions

Thesis: Spanish speech → LSC via 3D avatar. This repo is **only** the WebView app.
Old approaches (Flask server, Kotlin+Filament native, Unity) are dead — ignore any docs referencing them.

## Real-time mode (branch `tiempo-real`, 2026-10-14)

The app signs **while the user speaks**, not after they stop. The pipeline is:
mic → PCM16/16 kHz → WebSocket → backend → STT → fragments of glosses → appended
to the avatar's queue as they arrive.

Measured on a Redmi Note 9S with the real backend and real Whisper, over 28 audio
fixtures replayed through the socket at 1x: **first sign p50 913 ms / p95 1336 ms**
from the first audio sample. Before: the avatar waited ~2.4–4.8 s after the user
stopped talking. Full detail, phrase list, sign list and troubleshooting in
[`tools/TIEMPO-REAL.md`](tools/TIEMPO-REAL.md).

**Read that file before changing anything in this area.** Then:

```bash
tools/verificar-tiempo-real.sh        # everything: services, tests, 28 fixtures, metrics, device
tools/verificar-tiempo-real.sh incremental   # no services needed; runs in the backend container
```

### How streaming works

- The backend sends each fragment with `stream: true, total: 0`. `total: 0` means
  "not known yet" — in an utterance still open, nobody knows how many fragments
  there will be. A backend without the flag lands on the whole-phrase path, which
  is the safe default.
- `MainScreenState.onStreamedSegment` appends to the queue; the first fragment uses
  `setQueue` + `playFrom(0)`. `play()` could not be reused: it assumes the queue is
  complete and jumps to index 0.
- Fragments pass through a `Mutex`. Resolving a gloss may suspend (remote asset
  download) and two racing fragments would append out of order.
- `REALTIME_ENABLED=false` on the backend restores the old whole-phrase behaviour
  exactly. Both modes are covered in the backend's `verify_client_frames.py`.

### El micrófono son dos interruptores, no un pulsador

Desde el 2026-10-14 el mic **no se apaga solo**, y hay **dos** interruptores
encima de la misma captura:

| | Botón | Qué hace | Qué firma |
|---|---|---|---|
| 🎤 | microfono (fila 1) | **dictado**: escribe tu voz como texto en el campo | nada, hasta que toques Traducir |
| ◢◢ | TR (fila 2) | **tiempo real**: traduce y firma mientras hablás | cada fragmento, en el acto |

Los dos se tocan una vez y quedan prendidos; ninguno se mantiene apretado. **No
pueden estar prendidos a la vez**: comparten el `AudioRecord` y el WebSocket.

Lo que hay que saber antes de tocar esto:

- **El estado del mic es `MainScreenState.micMode`**, un enum de tres valores
  (`Off`/`Dictation`/`Realtime`), y no un booleano. Dos flags dejarían
  "los dos prendidos" como un estado representable, y en ese estado la app se
  rompe. Todo cambio pasa por `changeMicMode()`, que tiene los cuatro casos
  (prender, apagar, cambiar de modo, ya estaba en ese modo).
- **`playbackState` ya no dice si el mic está prendido.** Antes un solo enum
  decía dos cosas —"¿está el mic prendido?" y "¿qué hace el muñeco?"— y eso
  solo funcionaba porque el mic se apagaba en cada frase. Ahora se superponen de
  verdad. Un método nuevo que apague o encienda el mic tiene que tocar **los dos**.
- **Nunca uses `playbackState == Listening` como "el mic está prendido".** Con el
  mic prendido y el muñeco firmando, el estado es `Signing`.
- **Los dos interruptores se deshabilitan por el otro, en los dos sentidos, y
  ninguno se deshabilita mientras está prendido.** Antes solo se cumplía en un
  sentido (el mic se apagaba con TR prendido, pero TR seguía disponible con el
  mic prendido) y el otro no se veía: `soft` al 35 % de alfa sobre `surface` son
  los dos casi blancos. **Un control deshabilitado se marca apagando el icono y
  la etiqueta a `muted`, nunca bajándole el alfa a la superficie** (ver
  `SignAiBottomBar`). Prendido, cada uno es el único que puede cortar su modo, así
  que nunca se bloquea.
- **Lo que la voz ya mandó no se puede volver a mandar.** Al apagar el TR (y al
  autoenviar el dictado) el campo conserva la frase en `readOnly` con Traducir
  deshabilitado durante `VoiceTextHoldMs` = **1500 ms** (`sentVoiceText` en
  `MainScreenState`), y después se borra y el campo y Traducir vuelven **en la
  misma asignación**: no hay estado con el campo vacío y el botón muerto. Sin eso
  el botón invitaba a reenviar lo ya firmado. Hay log de los dos momentos
  (`texto ya firmado` / `texto retenido borrado`): la ventana es más corta que el
  ida y vuelta de `mobile-mcp`, así que el logcat es la prueba y el árbol de
  accesibilidad la confirmación.
- **El `end` va antes que el `mode`.** El cliente manda `end` y después el modo
  nuevo; el backend termina el utterance anterior con el modo con el que empezó.
  Invertir el orden rompe las dos direcciones.
- **El backend manda `dictation: true` en las transcripciones de dictado, y el
  cliente rutea por ese flag, no por su propio `micMode`.** El `final` de lo
  dictado llega después de que el usuario apagó el dictado: sin el flag, la app
  lo tomaría por tiempo real y quedaría en "Traduciendo..." para siempre.
- **Con el mic prendido, Traducir e Historial están deshabilitados y el input es
  `readOnly`.** Texto y voz van por el mismo socket y el backend no puede saber
  de quién es cada fragmento; mezclados, el avatar firma la frase al revés. El
  input sigue mostrando la transcripción en vivo, que es lo que el usuario
  quiere ver: por eso `readOnly` y no `enabled` (un campo deshabilitado se
  atenúa y el texto se vuelve ilegible).
- **Lo dictado se acumula en `dictationBuffer`**, no se reemplaza. El STT cierra
  cada frase por silencio y alguien que dicta hace pausas.
- `resumeListeningIfMicOn()` es lo que devuelve la interfaz a "escuchando" o
  "dictando" al cerrar una frase o una seña. Sin eso el botón se vería apagado
  mientras captura.

### Gotchas specific to real-time

- **The 32 exported signs average 2.15 s each** (2.08–2.75 s, from `manifest.json`).
  Spanish runs at 2–3 words/second and each word is a sign, so at normal speed the
  avatar is two signs behind before the first phrase ends and never catches up.
  That is why `app.js` has auto catch-up (`CATCHUP_BACKLOG = 2`, `CATCHUP_FACTOR =
  1.6`) and why `setSpeed` stores `baseSpeed` separately — catch-up multiplies on
  top of the user's setting, it does not replace it.
- **`turnStartedAt` starts when the mic opens**, not at the first fragment, so
  `rt_turn_total_ms` includes the silence before the first word. That is the number
  a user perceives.
- **Release the mic without speaking**: returns to Idle immediately with "No se
  escuchó nada." Waiting the full 15 s transcription timeout and then reporting a
  failure that did not happen is the old behaviour. `heardAnythingThisTurn`
  separates "user said nothing" from "we lost the audio".
- **`streamedSegments` is per-turn state in `MainScreenState`**, cleared by
  `resetStreamingTurn()` on every new phrase and on `fail()`. A turn that died
  halfway must not leak fragments into the next one.

## What it is

Single-module Gradle project (`SignAI-WebView`, `:app` only). Compose shell + WebView that renders a local Three.js avatar. No backend, no network API.

- Kotlin UI: layered packages under `app/src/main/java/com/signai/webview/` — `data/` + `domain/` (business), `ui/state/` + `ui/webview/` (presentation), `ui/{theme,designsystem,icons,components}/` (reusable UI), `ui/screens/` + `ui/MainScreen.kt` (screens). `MainActivity.kt` is a ~27-line entry point.
- Web scene: `app/src/main/assets/web/` — `index.html`, `app.js`, `bosque.jpg`, `manifest.json` + `models/avatar.glb` (the character) + `signs/<id>.glb` (one per sign, animation only)
- Package `com.signai.webview`, portrait-locked (`AndroidManifest.xml`)

## Layers (Clean Architecture, practical)

Dependency rule: screens → components → state/webview → data/domain, **never backwards**. No Hilt/ViewModel: `MainScreenState` (holds `SnapshotStateList` history + `Settings` + `sign` + playback state) is built in `MainActivity` and passed down; `SharedPrefsSignHistoryRepository` and `SharedPrefsSettingsRepository` own SharedPreferences; `WebViewSignAiController` is the only place with `evaluateJavascript`/`RenderEffect`; `domain/SignAiCommands`, `domain/SignsCatalog`, `domain/Gloss` and `domain/Settings` are pure Kotlin.

## Build

```bash
./gradlew build          # verify before committing
./gradlew installDebug   # needs device/emulator via adb
```

**MANDATORY after every change**: compile/verify the code and then run `./gradlew installDebug` on the connected device/emulator so the user can test immediately — never end a work item without installing. If no device is present, say so explicitly.

**Physical device over USB**: the default `BACKEND_WS_URL` is `ws://10.0.2.2:8080/ws/translate`, which is the *emulator* loopback alias. On a real phone use `adb reverse tcp:8080 tcp:8080` and build with `-Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate`. Without it the app shows "Sin conexión con el servidor" and it looks like a backend problem.

**`RECORD_AUDIO` is a runtime permission and is not pre-granted.** Tapping the mic with it missing says "No se pudo abrir el micrófono", which reads like an STT failure but is not. `adb shell pm grant com.signai.webview android.permission.RECORD_AUDIO`.

No tests, no CI, no lint. Toolchain: AGP 8.9.3, Kotlin 2.3.21, compileSdk/target 36, minSdk 26, JVM 17, Compose BOM 2024.12.01. Bumped from 8.7.3/2.1.0/35 → 8.9.3/2.3.21/36 in `ui-migration-lumo` Fase 0 (Phosphor 1.0.5 requires compileSdk 36 + Kotlin 2.3+ metadata).

## Observability

Every service in the stack exposes Prometheus metrics at `/metrics` and JSON at
`/metrics.json`; the device logs its own under logcat. When changing the
pipeline, **add a metric before adding the feature** — a number you did not
instrument is a number you cannot claim improved.

| Where | Tag / endpoint | What |
|---|---|---|
| STT | `:8000/metrics` | inference ms per model, GPU queue wait, audio lag, drops by reason |
| Backend | `:8080/metrics` | `rt_first_sign_ms`, `rt_utterance_ms`, `rt_chunk_delay_ms`, `audio_bytes_total` |
| Device | `adb logcat -s SignAiMetrics` | fragment→queue ms, turn duration, p50/p95/max, dictated phrases and words |
| Backend | `:8080/metrics` | `dictation_utterances_total`, `capture_mode_*_total`, `session_capture_dictation` |
| Device | `adb logcat -s SignAiNet` | every segment the backend sent, with `stream`/`provisional` |
| Scene | `adb logcat -s SignAIWeb` | JS console (needs debug mode on in Settings → AVATAR) |

`rt_first_sign_ms` is the one that decides whether this feels real time. `rt_utterance_ms` and `rt_chunk_delay_ms` explain it when it does not. In the STT, `stt_partial_queue_ms` is the regression guard for the old 4–8 s GPU contention spikes, and `stt_dropped_total{reason="logprob"}` catches the filter eating audio that Whisper transcribed correctly.

## Git workflow

**Este repo es del owner: commit y push directo a `main`, sin PR ni rama de feature** (decidido 2026-09-30). No abrir PRs acá ni pedir confirmación para commitear. Los repos hermanos (`../sign-ai-backend`, `../sign-ai-stt`) sí usan PR contra `develop`, y el `CLAUDE.md` de ese repo manda no commitear sin que el usuario lo pida explícitamente.

**Branch `tiempo-real`**: the real-time work is in this branch and in the matching `tiempo-real` branch of `sign-ai-backend` and `sign-ai-stt` (both cut from `develop`). The user authorised commit and push on those three branches.

## Neon

**Org: `org-orange-surf-39007829` (`sign-ai`).** Ese es el proyecto de la tesis. La cuenta tiene una segunda organización, `org-steep-paper-48927149` (`Diego`), que es personal — no tocar. Por eso el MCP de Neon **exige pasar `org_id`**: sin él devuelve *"Multiple organizations found"* y no hace nada.

Credenciales en `.envJulian` (`DATABASE_URL`, gitignored) — si `psql` dice `parse error near '&'` no es el archivo: es que la línea tiene `&` sin comillas. Usar `export $(grep -o '^DATABASE_URL=.*' .envJulian | head -1 | xargs)`.

La app **no habla con Neon**: la base es del backend. Neon se usa para leer/seedear el `catalog` (ver `openspec/specs/ui/spec.md`).

`catalog` **ya está sembrada** con las 50 glosas de LSC50 (`tools/seed_catalog.py --apply`, 2026-10-14). Reejecutarla es seguro: es idempotente. Tres cosas que no están a la vista:

- Las glosas van **sin tilde y sin `¿`/`?`**, pero **la `Ñ` se conserva** (`NIÑO`, no `NINO`). El backend pliega los dos lados de la búsqueda con `normalize_gloss()`, que usa una tabla explícita y no `unicodedata`, justamente para no separar la `Ñ` en `N` + tilde combinante.
- El catálogo anuncia **las 50**, pero solo **32** tienen animación exportada. Las otras 18 (`0032`–`0049`) llegan con `available: true` y un path que no existe: es lo que dispara `GLS-10` / `SignsCatalog.isOwnAssetPath()`. **No "arreglar" eso en la app** pidiéndolas por HTTP: son un 404 garantizado.
- `glb_animation_path` es **siempre** un path same-origin dentro del APK (`signs/<id>.glb`), nunca una URL. Es lo que permite que la app resuelva sin red.

El backend carga `catalog` **una sola vez al arrancar** (`load_all()`): después de un seed hay que reiniciarlo, no hay invalidación.

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

JS → Kotlin: `window.SignAIBridge.postMessage("onPlaybackDone")` when the queue finishes, and `postMessage("avatarReady")` once the character is parsed and in the scene (that second one is what hides the splash — `onPageFinished` fires while the 10 MB GLB is still downloading). Kotlin registers the listener with `WebViewCompat.addWebMessageListener` (feature-guarded) and drives the playback state machine; when the feature is missing, `onPageFinished` falls back to hiding the splash and `SignAiSplash`'s 8 s timeout covers the rest.

## Web asset rules

- `index.html` loads Three.js r169 from jsDelivr CDN via importmap → **needs INTERNET permission + connectivity at runtime**, even though avatar/data are local.
- **The character is loaded once** from `models/avatar.glb`; every sign is an animation-only GLB in `signs/<SSSS_VVVV_RRRR>.glb` replayed with `THREE.AnimationMixer`. All files load via **relative paths** — keep the `models/` + `signs/` layout.
- **Do not put the normalization (scale/offset) on the loaded scene root.** The Cascadeur exports animate the armature root (`HG_Branden001`) in position, rotation *and* scale, so the mixer overwrites it every frame. Put the scale/offset on a parent pivot object instead, or the character renders unscaled and off-centre with no error in the console.
- `app.js` regenerates with `python3 tools/split_signs.py`, which reads the model's raw exports from `GLB Senas/` (gitignored, ~315 MB), writes `models/avatar.glb` + `signs/*.glb`, writes `manifest.json`, and rewrites `domain/SignsCatalog.kt` so the list and the disk cannot drift. **Adding a sign = drop the new `.glb` in `GLB Senas/` and rerun that script** — never hand-edit `SignsCatalog.kt`.
- `shouldInterceptRequest` forces `Cache-Control: no-store` so edits to `app.js` show on reload; don't remove it.
- `renderer.setPixelRatio` is **capped at 1.75** on purpose. Uncapped, the Redmi Note 9S (dpr 2.75) renders 19.6 MP/frame and the GPU sits at ~37 ms/frame — 99% jank. Measured with `tools/rendimiento/gfxinfo.py`. Do not also force `DoubleSide` on the meshes: it is pure overdraw on closed meshes and cost another 4% of jank for zero visual change.

## Gotchas

- All Compose state lives in `ui/state/MainScreenState.kt`; there is no ViewModel/DI/navigation. History is `SharedPreferences "signai"/"history"` (max 20, top-5 never reorder) behind `data/SignHistoryRepository.kt`; settings user choices persist in the same file behind `data/SettingsRepository.kt`.
- Playback state machine (`ui/state/PlaybackState.kt`): `idle → translating → signing → done`; `signing → done` is driven by the real `onPlaybackDone` bridge event, not a timer. The mic is **real**, not a placeholder: `data/AudioCapture.kt` does `AudioRecord` on `VOICE_RECOGNITION` (not `MIC`, so the STT hears the voice instead of the AGC pumping) and streams 30 ms PCM16/16 kHz chunks. **Spec `CTL-6`, `STA-9` and `OM-4` in `openspec/specs/ui/spec.md` still describe the old placeholder and are wrong** — see the real-time session log.
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

## Tooling del agente (mobile-next)

Stack de mobile-next instalado en este equipo para que un agente (Claude,
opencode) opere el emulador/dispositivo sin pedir coordenadas a ciegas. Mismo
setup que el repo `AppMovil`/`trabajoVM`, **adaptado a Android nativo**: allá es
Flutter y tiene `dart-mcp` + `marionette`; acá no aplica nada de eso.

**Un agente que vaya a operar el device debería cargar el skill
`controlar-app-movil`** (tool `skill`, en `.opencode/skills/controlar-app-movil/`).
Ese skill tiene la guía de cuál herramienta usar, los workflows y los errores ya
pisados. Esta sección es solo el índice.

- **`tools/dev.sh`** — wrapper sobre `mobilecli`. Subcomandos: `devices`,
  `shot`, `tap`, `swipe`, `text`, `key`, `back`, `log`, `clearlog`, `tree`,
  `install`, `install-debug` (`./gradlew installDebug` + relanzar la app),
  `launch`, `terminate`, `apps`, `fg`, `logcat-tag`, `doctor`.
  **Sin escala**: mobilecli trabaja en coords reales del device.
- **Vía primaria del agente**: MCP **`mobile-mcp`**
  (`mobile_list_available_devices`, `mobile_take_screenshot`,
  `mobile_list_elements_on_screen` — preferir refs sobre coordenadas—,
  `mobile_click_on_screen_at_coordinates`, `mobile_type_keys`,
  `mobile_install_app`, `mobile_launch_app`, `mobile_get_device_logs`,
  `mobile_batch_commands`). Configurado en `opencode.json` con
  `MOBILEMCP_DISABLE_TELEMETRY=1`. Antes de tocar, leer el árbol con
  `mobile_list_elements_on_screen`. Si el MCP no está disponible, fallback a
  `tools/dev.sh`.
- **`tools/maestro/`** — flows YAML (`smoke-avatar`, `drawers`, `historial`,
  `ejemplo-flow`). Los selectors salen de los `contentDescription` del
  `.semantics {}` de Compose. **No anda en el Redmi (MIUI)**: su driver de
  Android revienta al arrancar con `Cannot call disconnect() while connecting
  UiAutomation`. Usarlo en un emulador con imagen AOSP.
- **`tools/rendimiento/`** — `gfxinfo.py` (reparto por fotograma, latencia de
  toque), `sf-medir.sh` (fotogramas que llegaron a pantalla), `frames.py`
  (duración de la animación, repintado en reposo), `perfetto.sh` (trace para
  atribuir). Midiendo en build release, nunca en debug.
- **`tools/pinch.sh`** — pinch de dos dedos por `sendevent`. Solo emulador o
  root; en un físico sin root da `Permission denied`.

### Dos limitaciones que hay que saber antes de intentar

1. **El avatar 3D es invisible para toda esta instrumentación.** En el árbol sale
   como `android.webkit.WebView` **sin ningún hijo**: el canvas de Three.js no
   publica nada a accesibilidad. No se puede tocar ni leer con `mobile-mcp`,
   Maestro ni `uiautomator`. Para el estado de la escena: el tag `SignAIWeb` de
   logcat, o el modo debug de la app (Ajustes → AVATAR → depurar).
2. **El WebView se come los toques.** Es un `AndroidView`, o sea un View real:
   toma cada toque dentro de sus bounds. Si un drawer no abre, el problema es el
   layering de `EdgeDrawer.kt` (§ Gotchas), no el gesto.

Ver `tools/README.md` para el índice y `tools/HERRAMIENTAS.md` para la
referencia completa (tools de `mobile-mcp`, tabla de `contentDescription`,
selectores, y las trampas ya pisadas).
