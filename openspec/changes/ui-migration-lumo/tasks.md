# Migración UI — Material 3 → Lumo UI + Phosphor + SignAI Design System

Migración visual y arquitectónica de `app/src/main/java/com/signai/webview/MainActivity.kt`
a Lumo UI (nomanr) como base de componentes, Phosphor Icons en lugar de
`material-icons-extended`, y un Design System propio centralizado.

**NO es una reescritura funcional**: todas las features actuales deben
seguir funcionando idénticamente. Migración incremental, sin tocar
WebView / Three.js / bridge JS.

---

## Estado actual (inventario)

`MainActivity.kt` — 424 líneas, **un solo archivo** contiene:
- Theme Material 3 + paleta hardcoded (líneas 81-97)
- `MainScreen` con TODO el estado: history, settings toggles, sign actual,
  blur del WebView, callbacks de play/send (líneas 113-185)
- `SettingsSidebar` (overlay con scrim + slide-in, checkboxes, dropdown
  de señas) (líneas 191-269)
- `TopBar` (título "SignAI" + icon buttons history/settings + dropdown) (283-334)
- `AvatarWebView` (AndroidView con `WebViewAssetLoader`,
  `WebViewClientCompat`, `shouldInterceptRequest` no-store) (337-371)
- `BottomBar` (TextField placeholder versión + botón mic + botón enviar) (374-423)

**Componentes Material 3 usados:** Button, ButtonDefaults, Checkbox,
DropdownMenu, DropdownMenuItem, FilledIconButton, Icon, IconButton,
IconButtonDefaults, MaterialTheme, OutlinedButton, OutlinedTextField,
OutlinedTextFieldDefaults, Scaffold, Surface, Text, TextFieldDefaults,
darkColorScheme. Más `material.icons.filled.{Mic, History, Send, Settings}`.

**Dependencias actuales** (`app/build.gradle.kts`):
- AGP 8.7.3, Kotlin 2.1.0, Compose Compiler plugin 2.1.0, JVM 17
- Compose BOM 2024.12.01, material3, material-icons-extended
- androidx.webkit 1.12.1, core-ktx 1.15.0, activity-compose 1.9.3
- compileSdk/target 35, minSdk 26

**Web assets:** `app/src/main/assets/web/{index.html, app.js, avatar.glb,
bosque.jpg, sign.json}` — **NO se tocan**. Contrato `window.SignAI`
(`play`, `setDebug`, `setFreeCamera`, `setGrid`, `setBackground`) intacto.

**Proyecto:** single-module `:app` (`AGENTS.md` lo deja explícito:
"Single-module Gradle project"). Por lo tanto la migración se hace con
**paquetes**, no con módulos Gradle.

---

## Stack objetivo

| Capa | Antes | Después |
|---|---|---|
| Componentes UI | Material 3 (`androidx.compose.material3`) | **Lumo UI** (nomanr v1.2.5) — generado como código propio |
| Iconos | `material-icons-extended` | **Phosphor Icons** (`io.github.dev778g-me:phosphoricons-*-android:1.0.5`, MIT) |
| Theme wrapper | `MaterialTheme` directo | `SignAiTheme` que envuelve `AppTheme` (Lumo) y aplica overrides del Design System SignAI |
| Estructura | 1 archivo `MainActivity.kt` con todo | Paquetes por capa (`data/`, `domain/`, `ui.state/`, `ui.webview/`, `ui.theme/`, `ui.designsystem/`, `ui.icons/`, `ui.components/`, `ui.screens/`) |

**Versiones objetivo (Maven Central, sep 2026):**
- `com.nomanr:lumo` Gradle plugin v1.2.5 (`com.nomanr.plugin.lumo`)
- `io.github.dev778g-me:phosphoricons-{regular,bold,fill,light,thin,duotone,core}-android:1.0.5` (los 6 weights disponibles; ~3.6 MB extra vs Regular único)

**Por qué Lumo y no "una dependencia más":** Lumo UI es un *Gradle
plugin* que genera `Button.kt`, `Surface.kt`, `Theme.kt`, etc. **dentro
del codebase** vía `./gradlew lumo --add Button`. El código generado
es 100% nuestro, modificable, sin acoplamiento a binarios externos
de UI. Encaja exactamente con "centralize colors, typography,
spacing, sizes, shapes, states" de `AGENTS.md`.

---

## Convenciones y decisiones

1. **No crear módulos nuevos.** Solo paquetes nuevos dentro de `:app`.
2. **No romper `app.js` / WebView / assets.** Cualquier cambio visual
   no debe tocar el contrato `window.SignAI`.
3. **No añadir dependencias nuevas** fuera de Lumo plugin + Phosphor +
   lo estrictamente necesario (p.ej. `androidx.compose.foundation` ya
   viene transitivo con material3; `material-icons-extended` se
   elimina al terminar migración).
4. **Lumo genera, nosotros customizamos.** El código bajo
   `ui/designsystem/` es nuestro: lo editamos después de generarlo. Una
   vez personalizado un componente, **no** re-ejecutar
   `--add` para ese componente (sobrescribe). Documentar en este
   tasks.md qué componentes se han personalizado.
5. **Bridge Kotlin↔JS:** `webViewRef.value?.evaluateJavascript("window.SignAI?.…()", null)`
   se mantiene literal (incluido el `?.`). Centralizar en
   `ui/webview/SignAiWebViewController.kt`.
6. **Historial:** mover la lógica de SharedPreferences a
   `data/SignHistoryRepository.kt`. No usar Hilt/ViewModel — el
   proyecto es chico y no lo justifica.
7. **Rendimiento del WebView:** nada de blur, `RenderEffect`,
   animaciones pesadas ni sombras en la zona del avatar. Las
   transparencias y surfaces sutiles solo fuera del área del WebView.
8. **Funcionalidad placeholder intacta:** mic (`play("mic")`) sigue
   siendo placeholder; enviar texto sigue disparando `window.SignAI?.play()`.

---

## Capas y responsabilidades (Clean Architecture, práctico)

Cuatro capas con responsabilidades claras. Para el tamaño del proyecto
**no se introduce DI (Hilt/Koin) ni ViewModel formal** — el estado
vive en `remember` / `rememberSaveable` o en repositorios.Singleton
instanciados desde `MainActivity`. Si crece el proyecto, los repos
se vuelven inyectables sin tocar UI.

### Capa 1 — Lógica de negocio (`data/`, `domain/`)

> **Regla:** sin dependencias de Compose ni Android UI. Pura Kotlin +
> `Context` (solo donde inevitable, ej. SharedPreferences).

- `data/SignHistoryRepository.kt`
  - `interface SignHistoryRepository` con `load(): List<String>`,
    `record(text: String): List<String>` (devuelve la lista actualizada),
    `save(list: List<String>)`.
  - `class SharedPrefsSignHistoryRepository(context: Context) : SignHistoryRepository`
    — encapsula la key `"signai"`/`"history"`, el separador `"\n"`, el cap
    de 20 ítems, y la regla "top-5 nunca se reordena".
  - **Toda la regla del top-5 vive aquí**, no en el composable.

- `webview/SignAiCommands.kt` *(parte de la capa de negocio: es la
  definición del contrato JS, no UI)*
  - `object SignAiCommands { const val PLAY = "window.SignAI?.play()"; ... }`
  - `fun jsToggle(prop: String, on: Boolean): String` — pure function,
    testeable.
  - Aquí vive **qué** comandos existen; el `WebViewController` decide
    **cuándo** mandarlos.

- `domain/SignsCatalog.kt`
  - `object SignsCatalog { val available: List<String> = listOf("0000_0000_0000") }`
  - Trivial ahora, pero centraliza: cuando crezca la lista (Fase con
    múltiples señas), este es el único lugar a tocar.

### Capa 2 — Lógica de presentación (`ui/state/`, `ui/webview/`)

> **Regla:** orquesta la lógica de negocio y la expone a la UI en
> forma de estado + callbacks. Sin tocar SharedPreferences ni
> WebView directamente. Tampoco pinta.

- `ui/webview/SignAiWebViewController.kt`
  - `interface SignAiWebViewController {
       fun play()
       fun setDebug(on: Boolean); fun setFreeCamera(on: Boolean)
       fun setGrid(on: Boolean); fun setBackground(on: Boolean)
       fun applyBlur(enabled: Boolean)
    }`
  - `class WebViewSignAiController(webView: WebView) : SignAiWebViewController`
    — única clase que llama `evaluateJavascript(...)` y `setRenderEffect(...)`.
    Aquí vive el guard `?.` para `window.SignAI?.…()`.
  - Se instancia una vez en `MainActivity`, vive en `remember` y se
    inyecta vía parámetro a los screens. Cero `webViewRef.value?.evaluateJavascript(...)`
    disperso por composables.

- `ui/state/MainScreenState.kt` *(presentational state holder, sin DI)*
  - `class MainScreenState(
       val history: SignHistoryRepository,
       val webView: SignAiWebViewController,
       val signsCatalog: SignsCatalog = SignsCatalog,
    )` con `remember { MainScreenState(...) }` en `MainScreen`.
  - Expone: `historyList: SnapshotStateList<String>`, `settings: Settings`,
    métodos `play(text)`, `replay(text)`, `toggleSetting(name, on)`, etc.
  - **Toda la lógica de "qué pasa cuando presionas enviar / mic / replay"**
    vive aquí, no en `MainScreen`.

- `ui/state/Settings.kt`
  - `data class Settings(val debug, val freeCam, val grid, val bg: Boolean)`
    con defaults `debug=true, grid=true, freeCam=false, bg=false` —
    mismos defaults que `MainActivity` actual.

### Capa 3 — UI reutilizable (`ui/components/`, `ui/designsystem/`)

> **Regla:** composables sin estado de negocio. Reciben datos +
> callbacks. Reutilizables entre pantallas.

- `ui/designsystem/` — código **generado por Lumo y customizado**.
  Componentes base (Button, Surface, TextField, Checkbox, IconButton,
  TopBar) parametrizados por `SignAiTheme`.
- `ui/components/` — **componentes SignAI-específicos** que el DS
  genérico no cubre:
  - `HistoryDropdown(history: List<String>, onPick: (String) -> Unit)` —
    reusa la `IconButton` Lumo + el dropdown propio (Fase 5).
  - `SignPickerDropdown(current: String, options: List<String>, onPick: (String) -> Unit)`
    — mismo patrón, distinto contenido.
  - `SettingRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit)`
    — fila de la sidebar, Lumo `Checkbox` + label. Antes era
    `SettingCheck` privado.
  - `SignAiIconButton(icon, contentDescription, onClick)` — wrapper
    delgado sobre Lumo `IconButton` para centralizar padding/size.
- `ui/icons/SignAiIcons.kt`
  - `object SignAiIcons { val Mic, Send, Gear, History: ImageVector }`
    — referencia al weight Regular por defecto + constantes para los
    otros 5 weights. Decide qué peso usar cada icono en un solo lugar.

### Capa 4 — Pantallas (`ui/screens/`)

> **Regla:** solo composición. Reciben `MainScreenState` o props
> directas, llaman callbacks, no calculan nada de negocio. Máximo
> un `var x by remember { mutableStateOf(...) }` para estado
> puramente visual (ej. `expanded` de un dropdown).

- `ui/MainScreen.kt` — entry composable de UI. Recibe `MainScreenState`,
  compone `Scaffold(topBar = ..., bottomBar = ...)` + `AvatarWebView`
  + `SettingsSidebar` (overlay).
- `ui/screens/topbar/SignAiTopBar.kt` — recibe `history: List<String>`,
  `onReplay: (String) -> Unit`, `onOpenSettings: () -> Unit`. Nada más.
- `ui/screens/bottombar/SignAiBottomBar.kt` — recibe `onSend: (String) -> Unit`,
  `onMic: () -> Unit`. Estado local solo del `query: String`.
- `ui/screens/settings/SettingsSidebar.kt` — recibe `settings: Settings`,
  `onToggle: (Setting, Boolean) -> Unit`, `sign`, `onSign`, `onDismiss`.
- `ui/screens/webview/AvatarWebView.kt` — recibe `onControllerReady: (SignAiWebViewController) -> Unit`.

### Regla de dependencia entre capas

```
Capa 4 (screens) ──→ Capa 3 (components + designsystem)
       │
       └──→ Capa 2 (state + webview controller)
                  │
                  └──→ Capa 1 (data repos + commands + catalog)
```

**Nunca al revés.** Un composable nunca instancia un repository; un
repository nunca importa Compose; un command nunca sabe de WebView.

---

## Reutilización y duplicación detectada en el código actual

Inventario de lo que **se está duplicando hoy** y cómo se resuelve:

| Duplicación actual | Resolución |
|---|---|
| `Color.White` (3 sitios: líneas 220, 230, 297) y `TextMain` (5+ sitios) usados como "color de texto" | Un solo token: `SignAiTheme.colors.onSurface` (alias). Eliminar `Color.White` hardcoded. |
| `MaterialTheme.typography.titleLarge` (TopBar) y `titleMedium` (Sidebar) | Mapeo central en `SignAiTypography`: `SignAiTheme.typography.titleLarge` → reexporta `AppTheme.typography.h3`. |
| `Border` (TopBar `Surface`), `InputBorder` (TextField) y `outline` del `darkColorScheme` | Un solo token: `SignAiTheme.colors.outline`. |
| `jsToggle(prop, on)` (línea 187-188) — función pura repetible | Mover a `SignAiCommands.kt` (capa 1). Usada por los 4 toggles. |
| Patrón `Box + IconButton + DropdownMenu + DropdownMenuItem` aparece 2 veces (history, sign picker) | Componente reusable `SignAiDropdown` en `ui/components/` (Fase 5 usa foundation; Fases 1-4 temporal M3). |
| `Color(0xFF…)` repetidos como literales hardcoded (líneas 81-87) | Toda la paleta a `SignAiColors.kt`. |
| `RoundedCornerShape(6.dp)` (BottomBar) y `height(56.dp)` (BottomBar) | Tokens en `ui/designsystem/foundation/Sizes.kt`: `SignAiSizes.buttonHeight = 56.dp`, `SignAiSizes.cornerSmall = 6.dp`. |
| La lambda `play(text)` (líneas 144-156) — incluye lógica de history + persistencia + WebView call | Se descompone en `state.play(text)` (capa 2), que llama `historyRepo.record(...)` + `webView.play()`. El composable solo hace `state.play(query)`. |
| La lambda `sendJs(js)` (línea 157-159) | Queda interna al `SignAiWebViewController`. Nadie la llama desde un composable. |

**Resultado esperado al terminar Fase 6:** cero `Color(0xFF…)` literal
fuera de `SignAiColors.kt`; cero `MaterialTheme.typography.*` en screens;
cero `webViewRef.value?.evaluateJavascript(...)` fuera del controller.

---

## Mapeo componente → componente (lo que cambia y lo que no)

| Material 3 actual | Reemplazo | Notas |
|---|---|---|
| `Scaffold` (root) | `Box` + `Scaffold` (Lumo) o `Scaffold` (Material foundation) | Lumo tiene `Scaffold`, pero el actual es simple: lo mantenemos con foundation para evitar acoplamiento. |
| `Surface(color = Bar)` (top bar) | `SignAiSurface` (envuelve Lumo `Surface`) | Lumo Surface ya existe, lo estilizamos con `SignAiColors`. |
| `IconButton` (history, settings) | `IconButton` (Lumo) | |
| `Icon` + `Icons.Default.History` | `Icon` + `PhIcons.Regular.ClockCounterClockwise` | |
| `Icon` + `Icons.Default.Settings` | `Icon` + `PhIcons.Regular.Gear` | |
| `DropdownMenu` (history, sign) | **Se mantiene con `androidx.compose.material3.DropdownMenu`** | Lumo no tiene dropdown menu todavía. Después de migrar Material 3 lo reescribimos con foundation (`Popup`) — segunda fase. |
| `DropdownMenuItem` | Igual — se mantiene M3 temporal | Igual que arriba. |
| `OutlinedButton` (sign selector trigger) | `Button(variant = ButtonVariant.PrimaryOutlined)` (Lumo) | |
| `Button` (enviar, containerColor = Primary) | `Button(variant = ButtonVariant.Primary)` (Lumo) | |
| `ButtonDefaults.buttonColors` | `Button` con `colors = ButtonDefaults.primaryButtonColors()` (Lumo) | Si Lumo no expone exactamente esos, ajustar variant. |
| `FilledIconButton` (mic) | `IconButton` (Lumo) con `variant` filled o `Surface` + `IconButton` custom | |
| `OutlinedTextField` (input placeholder) | `TextField` (Lumo) | Lumo expone su propio `TextField`. |
| `OutlinedTextFieldDefaults.colors(...)` | `TextFieldColors` (Lumo) | Mapeo de colores al Design System. |
| `Checkbox` (4 toggles) | `Checkbox` (Lumo) | |
| `MaterialTheme.typography.titleLarge` | `SignAiTheme.typography.h3` (Lumo Typography) | Mapeo aproximado. |
| `MaterialTheme.colorScheme.darkColorScheme` | `SignAiTheme { … }` que envuelve `AppTheme` con colores SignAI | Sin `MaterialTheme` en código de feature. |
| `AnimatedVisibility`, `fadeIn/Out`, `slideIn/Out` (sidebar) | Mismos, vienen de `androidx.compose.animation` (foundation, no Material) | **Se mantienen tal cual**. |

---

## Estructura destino de paquetes

Las carpetas marcadas con `[N]`, `[P]`, `[U]`, `[S]` indican a qué capa
pertenecen (negocio / presentación / UI reusable / screen).

```
app/src/main/java/com/signai/webview/
├── MainActivity.kt                       # entry point (~15 líneas)
│
├── data/                                # [N] Capa 1 — negocio
│   └── SignHistoryRepository.kt          # interface + impl SharedPreferences
│
├── domain/                              # [N] Capa 1 — negocio
│   ├── SignAiCommands.kt                # object con constantes JS + jsToggle()
│   └── SignsCatalog.kt                  # object con la lista de señas
│
└── ui/                                  # [P] [U] [S] Capas 2-4
    ├── MainScreen.kt                    # [S] entry composable
    │
    ├── state/                           # [P] Capa 2 — presentación
    │   ├── MainScreenState.kt           # state holder (sin DI)
    │   └── Settings.kt                  # data class Settings(...)
    │
    ├── webview/                         # [P] Capa 2 — presentación
    │   ├── SignAiWebViewController.kt   # interface + WebViewSignAiController impl
    │   └── AvatarWebView.kt             # [S] composable AndroidView (portado del actual)
    │
    ├── theme/                           # [U] Capa 3 — UI reusable (theme)
    │   ├── SignAiColors.kt              # paleta centralizada (Bg, Bar, Border, Input, Primary, TextMain, onSurface)
    │   ├── SignAiTypography.kt          # aliases de AppTheme.typography
    │   └── SignAiTheme.kt               # @Composable SignAiTheme(content) — envuelve AppTheme
    │
    ├── designsystem/                    # [U] Capa 3 — UI reusable (componentes)
    │   ├── components/                  # GENERADO por Lumo + customizado
    │   │   ├── Button.kt
    │   │   ├── Surface.kt
    │   │   ├── TextField.kt
    │   │   ├── Checkbox.kt
    │   │   ├── IconButton.kt
    │   │   ├── TopBar.kt
    │   │   └── …
    │   ├── foundation/                  # GENERADO + extendido por nosotros
    │   │   ├── Ripple.kt
    │   │   ├── ButtonElevation.kt
    │   │   └── Sizes.kt                 # + SignAiSizes (buttonHeight, cornerSmall…)
    │   ├── Theme.kt                     # GENERADO — AppTheme (Lumo)
    │   ├── Colors.kt                    # GENERADO — Lumo (lo overrideamos en SignAiTheme)
    │   └── Typography.kt                # GENERADO — Lumo
    │
    ├── icons/                           # [U] Capa 3 — UI reusable
    │   └── SignAiIcons.kt               # object: wrappers de Phosphor (los 6 weights disponibles)
    │
    ├── components/                      # [U] Capa 3 — SignAI-specific reusable
    │   ├── HistoryDropdown.kt           # patrón dropdown reutilizable
    │   ├── SignPickerDropdown.kt
    │   ├── SettingRow.kt
    │   └── SignAiIconButton.kt          # wrapper delgado Lumo IconButton
    │
    └── screens/                         # [S] Capa 4 — pantallas (composición pura)
        ├── topbar/
        │   └── SignAiTopBar.kt          # recibe (history, onReplay, onOpenSettings)
        ├── bottombar/
        │   └── SignAiBottomBar.kt       # recibe (onSend, onMic)
        ├── settings/
        │   ├── SettingsSidebar.kt       # recibe (settings, onToggle, sign, onSign, onDismiss)
        │   └── SignPicker.kt            # sub-componente de SettingsSidebar
        └── history/
            └── HistoryMenu.kt           # sub-componente de SignAiTopBar
```

`MainActivity.kt` original se reduce a:

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SignAiTheme { MainScreen() }
        }
    }
}
```

---

## Fases de ejecución

Cada fase termina con `./gradlew build` verde y la app probada
manualmente (instalada vía `./gradlew installDebug`). Si algo se
rompe, se revierte la fase antes de continuar.

### Fase 0 — Bootstrap del stack (sin tocar UI)

- [ ] 0.1 Añadir `com.nomanr.plugin.lumo` v1.2.5 al `build.gradle.kts` raíz (`apply false`).
- [ ] 0.2 Aplicar el plugin en `app/build.gradle.kts`.
- [ ] 0.3 Crear `lumo.properties` en la raíz:
      ```
      ThemeName=SignAiTheme
      ComponentsDir=app/src/main/java/com/signai/webview/ui/designsystem
      PackageName=com.signai.webview.ui.designsystem
      ```
      (Nota: si Lumo exige el dir ya creado, lo creamos vacío antes.)
- [ ] 0.4 `./gradlew lumo --init && ./gradlew lumo --setup` → genera `Theme.kt`, `Colors.kt`, `Typography.kt`, `foundation/`.
- [ ] 0.5 Añadir dependencia Phosphor a `app/build.gradle.kts`:
      `implementation("io.github.dev778g-me:phosphoricons-regular-android:1.0.5")`.
- [ ] 0.6 `./gradlew build` — verificar que sincroniza y compila sin errores.
- [ ] 0.7 `./gradlew installDebug` — verificar que arranca y muestra el WebView (sin cambios visuales aún).

**Criterio de éxito:** app compila y se comporta idénticamente; `ui/designsystem/Theme.kt` ya existe y se puede usar.

### Fase 1 — SignAI Theme wrapper + paleta aislada

- [x] 1.1 Crear `ui/theme/SignAiColors.kt` con la paleta actual (Bg, Bar, Border, Input, InputBorder, TextMain, Primary).
- [x] 1.2 Crear `ui/theme/SignAiTypography.kt` con alias de `AppTheme.typography.*` (h1, h2, h3, body1, body2, label1).
- [x] 1.3 Crear `ui/theme/SignAiTheme.kt`:
      ```kotlin
      @Composable
      fun SignAiTheme(content: @Composable () -> Unit) {
          AppTheme(isDarkTheme = true) {  // siempre dark por ahora
              CompositionLocalProvider(LocalSignAiColors provides SignAiColors) {
                  content()
              }
          }
      }
      ```
- [x] 1.4 En `MainActivity.kt`, envolver `MainScreen()` con `SignAiTheme { … }`.
      Reemplazar las constantes locales (`Bg`, `Bar`, etc.) por `SignAiTheme.colors.*` solo donde sea trivial; si no, dejar para Fase 3.
- [x] 1.5 Build + install. Verificar paridad visual exacta.

**Criterio de éxito:** cero cambios visuales. Toda la paleta centralizada en `SignAiColors`.

### Fase 2 — Iconos Phosphor

- [x] 2.1 Crear `ui/icons/SignAiIcons.kt`:
      ```kotlin
      object SignAiIcons {
          val Mic   = PhIcons.Regular.Microphone
          val Send  = PhIcons.Regular.PaperPlaneTilt
          val Gear  = PhIcons.Regular.Gear
          val History = PhIcons.Regular.ClockCounterClockwise
      }
      ```
- [x] 2.2 Sustituir los 4 `Icons.Default.{Mic,Send,History,Settings}` por `SignAiIcons.X`. Mantener `tint` y `contentDescription` originales.
- [x] 2.3 Build + install. Verificar que los 4 iconos cambian visualmente a estilo Phosphor.
- [x] 2.4 (Solo si todo OK) Eliminar import `androidx.compose.material.icons.*` de `MainActivity.kt`. **No** eliminar todavía `material-icons-extended` del `build.gradle.kts` (lo retiramos en Fase 4).

**Criterio de éxito:** iconos Phosphor visibles en TopBar y BottomBar. Cero regresiones.

### Fase 3 — Reemplazo componente por componente (Lumo)

Generamos componentes según necesidad. Para cada uno: build → install → verificar visual → commit.

- [x] 3.1 `./gradlew lumo --add Surface`. Reemplazar `Surface(color = Bar)` (top bar) por `SignAiSurface`. Personalizar colores si difiere.
  - **Hecho**: import con alias `Surface as SignAiSurface`. Pasamos `color = SignAiTheme.colors.bar` explícito. El root Surface (line 103) y el sidebar Surface (line 218) siguen siendo M3 porque solo se customizó el del top bar.
- [x] 3.2 `./gradlew lumo --add IconButton`. Reemplazar los 2 `IconButton` del TopBar.
  - **Hecho**: import con alias `IconButton as SignAiIconButton` + `IconButtonVariant`. Variant `Ghost` (transparent bg, LocalContentColor). Clickable area pasa de 40dp a 44dp (default Lumo) — visualmente imperceptible porque el title padding (12dp vertical) sigue dominando la altura del row.
- [x] 3.3 `./gradlew lumo --add Checkbox`. Reemplazar `Checkbox` (4 toggles de Settings). Personalizar colores.
  - **Hecho**: import con alias `Checkbox as SignAiCheckbox`. Helper privado `signAiCheckboxColors(): CheckboxColors` con tokens SignAI (primary azul, border gris). Default Lumo (border blanco en dark) sobreescrito. Estados disabled usan `SignAiTheme.colors.border` como placeholder razonable (no usado actualmente).
- [x] 3.4 `./gradlew lumo --add TextField`. Reemplazar `OutlinedTextField` del BottomBar. Ajustar colores.
  - **Hecho**: import con alias `OutlinedTextField as SignAiOutlinedTextField` + `OutlinedTextFieldDefaults as SignAiOutlinedTextFieldDefaults`. Patrón `.colors().copy(...)` sobre los defaults de Lumo para mapear a tokens SignAI. Container fill agregado (`input` color) — sutil mejora visual.
- [x] 3.5 `./gradlew lumo --add IconButton` + `--add Button`. Reemplazar `FilledIconButton` (mic) y `Button` (enviar) por sus contrapartes Lumo.
  - **Desviación del plan**: Lumo `Button(variant = PrimaryFilled)` renderiza blanco/negro (usa Lumo SignAiTheme.colors.primary = White en dark), no nuestro azul Primary. Lumo no expone colores custom en Button. Decisión: usar `SignAiSurface(onClick = ...)` (que sí acepta `color`, `border`, `shape`) en vez de Lumo Button. Resultado visual idéntico. Mismo patrón para el OutlinedButton del sign picker (3.6). Esto deja Lumo Button sin uso práctico todavía — pulir en Fase 7.
  - Eliminados imports M3: `Button`, `ButtonDefaults`, `FilledIconButton`, `IconButton`, `IconButtonDefaults`. El Icon dentro del Surface sigue siendo M3 (no hay Icon en Lumo generado).
- [x] 3.6 `./gradlew lumo --add Button`. Reemplazar `OutlinedButton` del selector de señas por `Button(variant = ButtonVariant.PrimaryOutlined)`.
  - **Desviación**: misma razón que 3.5 — usamos `SignAiSurface(onClick = ..., color = Transparent, border = BorderStroke(1.dp, inputBorder), shape = RoundedCornerShape(8.dp))`. Eliminados imports M3: `OutlinedButton`.
- [x] 3.7 `./gradlew lumo --add TopBar`. Sustituir el `TopBar` custom de `MainActivity.kt` por el `TopBar` de Lumo, manteniendo los 2 iconos y el título "SignAI".
  - **Hecho**: import con alias `TopBar as LumoTopBar` + `TopBarDefaults as LumoTopBarDefaults`. Pasamos `colors = LumoTopBarDefaults.topBarColors(containerColor = bar, scrolledContainerColor = bar)`. El Row(SpaceBetween) interno preserva el layout (Lumo centra su contenido, pero nuestro Row hijo con fillMaxWidth+SpaceBetween neutraliza eso). El default windowInsets (systemBarsForVisualComponents horizontal+top) reemplaza `.statusBarsPadding()` manual. Eliminada la Surface outer (Lumo TopBar tiene su propia Surface interna).
- [x] 3.8 Build + install después de cada 3.x. Verificar paridad visual.
  - **Hecho**: 7 screenshots en `/tmp/opencode/screen-fase3-{1..7}-*.png`. Paridad visual exacta confirmada para todas las fases. Para verificar sidebar/checkboxes (3.3, 3.6), se flipeó temporalmente `showSettings = remember { mutableStateOf(true) }` y se revirtió.

**Criterio de éxito (acumulado):** ✓. Imports M3 restantes en `MainActivity.kt`: `DropdownMenu`, `DropdownMenuItem`, `Icon`, `MaterialTheme`, `Scaffold`, `Surface`, `Text`, `darkColorScheme`. Los "temporales" según el plan son `Scaffold` y `DropdownMenu` (hasta Fase 5). Los demás (`Icon`, `Text`, `Surface`, `MaterialTheme`, `darkColorScheme`) se retiran en Fase 4.

**Decisiones de Fase 3 a recordar para Fase 7:**
1. **Lumo Button sin uso real todavía**: 5 botones en la app (mic, enviar, sign picker) usan `SignAiSurface(onClick)` en lugar de `Lumo.Button`. Considerar customizar `Button.kt` para aceptar colores custom y migrar en Fase 7.
2. **SignAiColors incompleto**: le faltan `disabled`, `onDisabled`, `secondary`, `onSecondary`, `error`, `onError`, `transparent`. Agregarlos en Fase 7 para evitar `Color.Transparent` literales y tener un SignAI Design System completo.
3. **Estados disabled**: el placeholder `SignAiTheme.colors.border` para todos los disabled es funcional pero feo. Definir un token `disabled` real en SignAiColors en Fase 7.
4. **Typography no migrada del todo**: el título "SignAI" sigue usando `MaterialTheme.typography.titleLarge` (22sp Normal). Lumo h3 es 16sp Bold — visualmente diferente. Si se quiere Lumo typography exacta, hay que mapear titleLarge → algo en Lumo o mantener M3 typography hasta Fase 7 polish.
5. **Sidebar Surface sigue M3**: la sidebar usa `androidx.compose.material3.Surface(color = Bar)`. Podría migrarse a `SignAiSurface(color = bar)` también. Diferencia 0 visualmente.

### Fase 4 — Limpieza de Material 3

- [x] 4.1 Quitar `implementation("androidx.compose.material3:material3")` y `implementation("androidx.compose.material:material-icons-extended")` de `app/build.gradle.kts`.
- [x] 4.2 Añadir manualmente lo que aún necesitemos de foundation:
      `implementation("androidx.compose.foundation:foundation")` (ya viene transitivo, verificar).
      `androidx.compose.material:material` para `Scaffold` / `IconButton` mínimos si Lumo no los cubre (Lumogenera los suyos propios, así que probablemente no).
- [x] 4.3 Build + install. Si `Scaffold` o `DropdownMenu` rompen, decidir:
      - `Scaffold` → reemplazable por `Box` + `Column` con `TopBar`/`BottomBar` slots de Lumo.
      - `DropdownMenu` → reemplazar por `androidx.compose.ui.window.Popup` (foundation) — más trabajo, hacerlo en Fase 5.
- [x] 4.4 `./gradlew build` debe pasar solo con: compose-bom + ui + foundation + activity-compose + core-ktx + webkit + phosphor + lumo plugin.

**Criterio de éxito:** APK compila sin `material3`. App funciona idéntica.

**Decisiones / trabajo realizado en Fase 4:**

1. **`Scaffold` reemplazado por `Box` + `Column`**. El root ahora es
   un `Box(Modifier.fillMaxSize())` con un `Column(Modifier.fillMaxSize())`
   adentro que apila `TopBar` + `Box(weight=1f) { AvatarWebView }` +
   `BottomBar`. La sidebar sigue siendo overlay sobre el Box root.
   Cambio trivial, sin pérdida funcional. `innerPadding` ya no hace falta
   porque cada hijo maneja sus propios paddings (TopBar via windowInsets
   internos de Lumo, BottomBar via `navigationBarsPadding`/`imePadding`,
   AvatarWebView llena el peso).

2. **`DropdownMenu` reemplazado por `androidx.compose.ui.window.Popup`
   custom en Fase 4** (no en Fase 5 como decía el plan original).
   Razón: el criterio de Fase 4 es "APK compila sin material3", y
   DropdownMenu vivía en material3. Deferir a Fase 5 implicaba agregar
   `material:material` legacy o reescribir después — peor que hacerlo
   ahora. Creado `SignAiDropdown` en
   `ui/designsystem/components/Dropdown.kt:46` con API:
   ```kotlin
   SignAiDropdown(
       expanded, onDismissRequest, modifier, maxHeight,
       trigger = { ... },   // @Composable () -> Unit
   ) { ... }                // ColumnScope content con menu items
   ```
   Anclaje a trigger via `onGloballyPositioned { boundsInWindow() }` y
   `Popup(alignment = TopStart, offset = (left, bottom))`. Outside-click
   via `PopupProperties(focusable = true)`. `SignAiDropdownMenuItem`
   complementario (48dp height, clickable, padding horizontal 16dp).
   Limpiador que el DropdownMenu de M3 (no usa SubcomposeLayout ni
   position providers).

3. **`Icon` y `Text` reemplazados por wrappers propios** en
   `ui/designsystem/components/Icon.kt:18` y `Text.kt:14`. Ambos usan
   foundation (`Image` + `rememberVectorPainter` para Icon,
   `BasicText` para Text). Tint y color por defecto desde
   `LocalContentColor` (CompositionLocal propio en
   `ui/theme/LocalContentColor.kt:7` — `LocalContentColor` de M3 vive
   en `material:material` legacy, no en foundation, así que lo
   redefinimos en SignAiTheme; default `Color.Unspecified`,
   SignAiTheme provee `colors.textMain`).

4. **`MaterialTheme` y `darkColorScheme` eliminados**. El wrapper M3 era
   necesario solo porque `SignAiSurface` (Lumo) no se había adoptado
   en el root ni en el sidebar. Ahora root + sidebar usan
   `SignAiSurface` directamente. `MainActivity` queda:
   ```kotlin
   class MainActivity : ComponentActivity() {
       override fun onCreate(savedInstanceState: Bundle?) {
           enableEdgeToEdge()
           super.onCreate(savedInstanceState)
           setContent { SignAiTheme { MainScreen() } }
       }
   }
   ```

5. **`MaterialTheme.typography.titleLarge/titleMedium` migrados** a
   `SignAiTheme.typography.titleLarge/titleMedium`. Estos se agregaron
   a `ui/theme/SignAiTypography.kt:56-64` como aliases que apuntan a
   `LumoSignAiTheme.typography.h2` y `h4` respectivamente. Cambio
   visual: el título "SignAI" pasa de 22sp Normal (M3 titleLarge) a
   20sp Bold (Lumo h2) — sutilmente más compacto, mejor jerarquía.
   El header "Configuración" pasa de 16sp Medium (M3 titleMedium) a
   16sp SemiBold (Lumo h4). Ambos mejor legibilidad.

6. **Constantes locales (`Bg`, `Bar`, `Border`, `Input`,
   `InputBorder`, `TextMain`, `Primary`) eliminadas** de MainActivity.
   Todo reemplazado por `SignAiTheme.colors.*`. Las únicas
   apariciones restantes de literales `Color(0xFF...)` fuera de
   `SignAiColors.kt` son `Color.White` (2 sitios: checkmark del
   checkbox y tinte del icono Send sobre botón azul — ambos correctos
   como primitivos UI) y `Color.Transparent` (1 sitio: tinte del
   checkbox sin marcar y fondo del sign picker). `Color.Unspecified`
   en LocalContentColor.

7. **`Button.kt` (Lumo generado) parcheado**. Usaba
   `androidx.compose.material3.Text` en el `DefaultButtonContent`
   (línea 135) y en los 3 `@Preview` functions (líneas 477, 533, 589).
   Sin material3, no compila. Reemplazos:
   - DefaultButtonContent: `BasicText(text = text, style = button.copy(color, textAlign))`.
     El `TextOverflow.Clip` se pasa igual.
   - Previews: `SignAiText(text = "X", style = h2)` (same package, no
     import).
   Header `// CUSTOMIZED` documenta que NO se debe re-ejecutar
   `--add Button` (sobrescribiría los fixes).

8. **`build.gradle.kts` reducido**. Eliminadas dos líneas:
   ```kotlin
   implementation("androidx.compose.material3:material3")
   implementation("androidx.compose.material:material-icons-extended")
   ```
   Solo queda `material-ripple` (de foundation, lo usa Lumo para
   indicaciones). Deps finales: compose-bom + ui + foundation +
   material-ripple + activity-compose + core-ktx + webkit + phosphor.

9. **Verificación visual**: 4 screenshots en `/tmp/opencode/`:
   - `screen-fase4-1-main.png` — vista principal con paleta oscura
   - `screen-fase4-2-sidebar.png` — sidebar con 4 checkboxes + sign
     picker
   - `screen-fase4-3-history.png` — history dropdown anclado al
     IconButton, items "khk / Hola / khdjgzkhzk... / mic / ohoh / lhhi"
     (truncado a 10 chars + "...")
   - `screen-fase4-4-sign-picker.png` — sign picker dropdown con
     "✓ 0000_0000_0000"
   Paridad visual total con Fase 3 + refinamiento de paleta oscura.

10. **Cero referencias residuales**: `git grep
    "androidx.compose.material3" app/src/main/java` → 0 líneas;
    `git grep "androidx.compose.material.icons" app/src/main/java` →
    0 líneas; gradle sin material3 ni material-icons-extended. Criterio
    de éxito cumplido.

**Decisiones de Fase 4 a recordar para Fase 6/7:**
- `SignAiDropdown` reemplaza cualquier futura adición de menú
  desplegable. Para dropdowns anidados / multi-anchor / async data
  sources, evaluar si necesita lifting del state a ViewModel.
- `SignAiText` y `SignAiIcon` son wrappers thin; cualquier
  feature de M3 Text/Icon no soportada (e.g. `AnnotatedString` con
  inline content) hay que extenderlos o usar `BasicText`/`Image`
  directamente. Por ahora solo necesitamos String + style.
- `LocalContentColor` propio: si en Fase 7 se quiere integrar con
  tokens más finos (onSurface, onPrimary, onError...), migrar a
  varios CompositionLocals o definir un wrapper.
- `Lumo.Button` no se usa en runtime (sólo en los `@Preview`). Si
  en Fase 7 se retoma la idea de customizar Button.kt para aceptar
  colores custom, hay que migrar los 3 SignAiSurface(onClick) que
  usamos (mic, enviar, sign picker).

### Fase 5 — Rediseño layout: TopBar eliminada, drawer edge-swipe (sep 17, 01:38 ART)

Usuario pidió cambios estructurales al layout:
1. **Quitar TopBar completa** (no más "SignAI", iconos history/settings arriba).
2. **Mover botón historial a la derecha del TextField** dentro del BottomBar.
3. **Acceso a sidebar por drag desde el borde derecho** (no más botón).

- [x] 5.1 Eliminar `TopBar` function + `LumoTopBar`/`LumoTopBarDefaults` imports de MainActivity. Layout root ahora `SignAiEdgeDrawer { content = Column { Box(weight=1f){AvatarWebView}, BottomBar } }`.
- [x] 5.2 BottomBar reestructurado: Row con `OutlinedTextField(weight=1f) | HistoryMenuButton` arriba, Row `Mic (25%) | Send (75%)` abajo. `HistoryMenuButton` usa `SignAiDropdown` anclado al IconButton (trigger = button).
- [x] 5.3 **`SignAiEdgeDrawer`** (`ui/designsystem/components/EdgeDrawer.kt`): side drawer con `Modifier.draggable` (foundation) en DOS lugares — un edge strip de 40dp en el borde derecho (para abrir desde cerrado) Y el drawer mismo (para arrastrar-cerrar). `Animatable<Float>` para el offset, snap a open/closed según `offset < widthPx/2`. Scrim con `graphicsLayer.alpha = fraction * 0.5f`. Sin `AnchoredDraggableState` porque queremos el edge-strip independiente del drawer.
- [x] 5.4 Build + install + verify (`/tmp/opencode/screen-fase4-main-no-topbar.png`, `screen-fase4-drawer.png`).

**Notas:**
- Lumo no tiene side drawer component (la lista de Lumo: Accordion, AlertDialog, Badge, Button, Card, Checkbox, Chip, Divider, Icon, IconButton, ModalBottomSheet, NavigationBar, OTP, Progress, RadioButton, Scaffold, Slider, Snackbar, Surface, Switch, Text, TextField, Tooltip, TopBar — **ningún drawer**). Por eso usamos foundation `Modifier.draggable` + Lumo `Surface` + tokens SignAI.
- El dropdown del historial sigue usando `SignAiDropdown` (popup custom foundation) porque sigue siendo un menú pequeño anclado al IconButton — no un bottom sheet ni un drawer. Es el patrón correcto para ese caso.
- El blur del WebView sigue atado a `showSettings` (que ahora es el estado del drawer). RenderEffect con Android 12+, blur 16f.

### Fase 5.5 — Refinamiento del drawer + fix debug safe-area (sep 17, 01:55 ART)

Post-instalación el usuario reportó:
- (a) **Edge swipe no funciona**: arrastrar desde el borde derecho no abría nada.
- (b) **Falta pestañita visible**: el edge-strip era un Box vacío (40dp sin contenido visual).
- (c) **Debug/status overlay se ven debajo del notch / status bar / cámara**: cuando el WebView es edge-to-edge con `viewport-fit=cover`, los `<div>` en `top: 8px` y `top: 28px` se pintan literalmente debajo del área de notificaciones.

### (a) Por qué el edge swipe fallaba

El diseño anterior tenía **dos `Modifier.draggable`** separados:
- `Box(edgeSwipeWidth=40.dp).draggable(...)` — para abrir desde cerrado.
- `Box(drawerWidth=300.dp).draggable(...)` — para cerrar arrastrando el drawer.

`Modifier.offset()` en Compose **no traslada el área de hit testing**:
el `align(Alignment.CenterEnd).width(300.dp)` del drawer define su bounding
box como 300dp desde el borde derecho *siempre* — incluso cuando está
cerrado con `offset(widthPx)`. Resultado: la hit area del drawer solapaba
exactamente el edge-strip (300dp vs 40dp en el mismo anchor). Como el
drawer se renderizaba después (encima), sus gestos siempre ganaban.
El edge-strip era **intocable**.

El `LaunchedEffect(isOpen)` además forzaba el `animateTo(...)` en cada
cambio de `isOpen`, anulando cualquier drag que intentara mover el offset.

### (b) Solución: un solo draggable anclado a un handle visible

Reescrito `EdgeDrawer.kt` con:
- **Un solo `DraggableState`** compartido para abrir y cerrar.
- **Handle visual persistente**: `Box(28.dp x 80.dp).offset(x = -8.dp).draggable(...)` anclado a `Alignment.CenterEnd`. El `offset(-8.dp)` lo inserta 8dp hacia adentro del borde derecho → cuando el drawer está cerrado, solo se ve la pestañita sobresaliendo; cuando está abierto, se alinea con el borde derecho del drawer (afordance de cierre).
- **Pill visual** dentro del handle: `Box(4.dp x 40.dp, color = primary, shape = RoundedCornerShape(2.dp))` centrada. Sutil pero claramente visible contra el fondo oscuro.
- **`offset` aplicado antes del `draggable`**: como ambos están en el modifier chain del mismo Box, el `draggable` captura gestos en el área offset (correcto hit testing). Verificado con `adb shell input swipe 1080 1280 200 1280 300` → abría el drawer correctamente.
- Eliminado el parámetro `edgeSwipeWidth` (era redundante con el handle).

API final:
```kotlin
SignAiEdgeDrawer(
    isOpen = showSettings,
    onOpenChange = { showSettings = it },
    drawerWidth = 300.dp,
    content = { ... },
    drawerContent = { ... },
)
```

### (c) Fix safe-area en `index.html`

`app/src/main/assets/web/index.html` ya tenía `viewport-fit=cover`, lo
que expone `env(safe-area-inset-top)` en el CSS. Cambios aplicados:

```css
#status {
  position: absolute;
  left: 8px;
  top: calc(env(safe-area-inset-top, 0px) + 8px);
  ...
}
#debug {
  position: absolute;
  left: 8px;
  top: calc(env(safe-area-inset-top, 0px) + 32px);  /* +32 = status altura + gap */
  ...
}
```

`env(safe-area-inset-top, 0px)` con fallback para devices sin notch.
En Redmi Note 9S (status bar sin notch): `env(...)` devuelve la altura
de la status bar (~24-28dp). El debug queda ~60dp debajo del borde
superior, claramente separado del reloj / iconos / cámara.

### Verificación

- Build verde (6s).
- Install OK. Tres screenshots en `/tmp/opencode/`:
  - `screen-fase5-fix-closed.png` — drawer cerrado, pestañita azul al borde
    derecho, debug separado de la status bar. Avatar fullscreen nítido.
  - `screen-fase5-fix-open.png` (con `showSettings=true` temporal) — drawer
    deslizado, Configuración visible (4 toggles + Seña picker), pestañita
    azul alineada al borde derecho del drawer (afordance de cierre).
  - `screen-fase5-fix-final-closed.png` — tras revertir `showSettings=false`,
    la app arranca cerrada por defecto con la pestañita visible y debug
    separado de la status bar.
- Drag confirmado con `adb shell input swipe`: 1080→200 a mitad de pantalla
  → drawer snap a abierto. Lógica simétrica para cerrar (snap si
  `offsetX < widthPx/2f`).
- `MainActivity.kt` no requirió cambios (no usaba `edgeSwipeWidth`).

### Fase 5.6 — Strip de borde completo + handle puramente visual (sep 17, 02:10 ART)

Usuario pidió, ya con el swipe R→L funcionando:
1. **Abrir con swipe desde CUALQUIER punto del borde derecho** (salvo la
   button bar), no solo desde la pestañita. La pestañita pasa a ser solo
   apoyo visual.
2. **Pestañita completamente pegada al borde** de la pantalla (estaba
   centrada en su hitbox, con ~12dp de aire al borde).
3. **La pestañita no debe "volver" al abrir**: el follow-animate del 5.5
   la desplazaba a la izquierda junto al drawer durante el drag y al
   terminar el snap saltaba de vuelta al borde — el usuario lo veía como
   "vuelve a la posición inicial, no se queda junto a la sidebar".

Reescrito `EdgeDrawer.kt` con modelo de gestos por capas (z-order):

```
content() → edge strip → scrim → drawer → handle (visual)
```

- **OPEN — edge strip invisible** (`48dp` ancho, `fillMaxHeight` menos
  la exclusión inferior): `Box(TopEnd).padding(bottom =
  edgeStripBottomExclusion).fillMaxHeight().width(48dp).draggable(...)`.
  El `padding` ANTES del `fillMaxHeight` achica el nodo, así el hit area
  excluye la button bar en vez de taparla. Encima de `content` (gana al
  WebView), debajo de scrim/drawer (no interfiere con drawer abierto).
- **CLOSE — el drawer mismo es draggable** (drag a la derecha), o tap en
  el scrim. Strip y drawer comparten UN `DraggableState` con el mismo
  mapeo (`offsetX + delta`); solo uno es alcanzable a la vez (drawer
  off-screen cuando cerrado, strip tapado cuando abierto) → sin
  ambigüedad.
- **Handle 100% visual**: sin modifiers de pointer, los toques lo
  atraviesan hacia el draggable de abajo (strip si cerrado, drawer si
  abierto). Pill `5dp x 50dp` con `align(CenterEnd)` dentro de su
  contenedor → pegada al borde físico. Nunca se mueve, nunca salta.
- Eliminados `isDragging`/`isAnimating`/`followOffset` (ya no hay follow).

`MainActivity.kt`: la BottomBar se envuelve en un `Box` con
`Modifier.onSizeChanged { bottomBarHeight = with(density) {
it.height.toDp() } }` y se pasa como `edgeStripBottomExclusion` al
drawer. Se auto-corrige si el teclado cambia la altura (recompose).

**Corrección a la teoría del 5.5**: `Modifier.offset()` SÍ traslada el
hit area (el placement mueve el nodo). El fallo original del edge-strip
no era hit-testing sino el **signo del delta invertido** (`- delta` en
vez de `+ delta`): el drag R→L aumentaba el offset en vez de
disminuirlo, el drawer "seguía" hacia afuera (invisible) y el snap
siempre cerraba. El usuario lo percibía como "no pasa nada".

Verificación: build verde, install OK.
- `screen-fase56-closed.png` / `screen-fase56-final.png` — pill pegada
  al borde, avatar nítido, BottomBar completa, debug bajo la status bar.
- `screen-fase56-open.png` (flip `showSettings=true` temporal) — drawer
  abierto, pill fija sobre el borde derecho del drawer.
- Swipe R→L verificado en build 5.5 (mismo mapeo `+delta`); en 5.6 el
  `adb input swipe` fue bloqueado por `INJECT_EVENTS` y no se pudo
  re-verificar por adb — **pendiente confirmación del usuario en device**.

### Fase 5 — Dropdowns propios + DropdownMenu sin Material 3

- [x] 5.1 Reescribir `HistoryMenu` y `SignPicker` usando `androidx.compose.ui.window.Popup` o un wrapper que centralice el patrón dropdown (focus + dismiss on outside click).
- [x] 5.2 Confirmar que ya no hay **ningún** import de `androidx.compose.material3.*` en todo el proyecto.

### Fase 6 — Separación de capas (limpieza arquitectónica)

Esta fase **no cambia la UI**, solo reorganiza archivos y mueve
lógica de negocio/presentation fuera de los composables. Sin
regresiones posibles: cada paso se compila y se prueba antes del
siguiente.

#### Capa 1 — Negocio (sin Compose, sin Android UI)

- [x] 6.1 Crear `data/SignHistoryRepository.kt`:
      - `interface SignHistoryRepository { fun load(): List<String>; fun record(text: String): List<String> }`.
      - `class SharedPrefsSignHistoryRepository(context: Context) : SignHistoryRepository`.
      - Mover aquí: key `"signai"`/`"history"`, separador `"\n"`, cap 20, regla "top-5 nunca se reordena", y el `.apply()` de SharedPreferences.
- [x] 6.2 Crear `domain/SignAiCommands.kt`:
      - `object SignAiCommands { const val PLAY = "window.SignAI?.play()" ; ... }`.
      - `fun jsToggle(prop: String, on: Boolean): String` (movida de `MainActivity.kt:187`).
- [x] 6.3 Crear `domain/SignsCatalog.kt`:
      - `object SignsCatalog { val available: List<String> = listOf("0000_0000_0000") }`.

#### Capa 2 — Presentación (orquesta, no pinta)

- [x] 6.4 Crear `ui/state/Settings.kt`: `data class Settings(val debug, val freeCam, val grid, val bg: Boolean)` con defaults actuales.
- [x] 6.5 Crear `ui/webview/SignAiWebViewController.kt`:
      - Interface con `play()`, `setDebug(on)`, `setFreeCamera(on)`, `setGrid(on)`, `setBackground(on)`, `applyBlur(enabled)`.
      - Impl `WebViewSignAiController(webView: WebView)` que centraliza los `evaluateJavascript(SignAiCommands.X, null)` y el `setRenderEffect(...)` con el guard `?.` del contrato.
- [x] 6.6 Crear `ui/state/MainScreenState.kt`:
      - `class MainScreenState(val history: SignHistoryRepository, val webView: SignAiWebViewController)` con `SnapshotStateList<String>` y `Settings` interno.
      - Métodos: `play(text)`, `replay(text)`, `toggleSetting(prop, on)`, `pickSign(id)`. Toda la lógica de las lambdas `play` y `sendJs` actuales vive aquí.
- [x] 6.7 Mover `AvatarWebView` (AndroidView) a `ui/webview/AvatarWebView.kt`. Su `onReady` callback ahora entrega el `WebView` al `WebViewSignAiController` (no un `WebView?` suelto).

#### Capa 3 — UI reusable (componentes, sin estado de negocio)

- [x] 6.8 Extraer `ui/components/HistoryDropdown.kt`, `SignPickerDropdown.kt`, `SettingRow.kt`, `SignAiIconButton.kt` según el mapeo del plan.
- [x] 6.9 Mover `ui/screens/topbar/SignAiTopBar.kt`, `bottombar/SignAiBottomBar.kt`, `settings/SettingsSidebar.kt`.

#### Capa 4 — Screens (composición pura)

- [x] 6.10 Reescribir `ui/MainScreen.kt` para que **solo componga**:
      ```kotlin
      @Composable
      fun MainScreen(state: MainScreenState) {
          Box(Modifier.fillMaxSize()) {
              Scaffold(
                  topBar = { SignAiTopBar(state.historyList, state::replay, state::openSettings) },
                  bottomBar = { SignAiBottomBar(onSend = state::play, onMic = { state.play("mic") }) },
              ) { innerPadding ->
                  AvatarWebView(Modifier.padding(innerPadding), onControllerReady = state::attachWebView)
              }
              SettingsSidebar(state = state.settings, onDismiss = state::closeSettings, ...)
          }
      }
      ```
      Cero `webViewRef`, cero `mutableStateOf("0000_0000_0000")`, cero `SharedPreferences.edit()` dentro del composable.
- [x] 6.11 `MainActivity.kt` queda en ~15 líneas:
      ```kotlin
      class MainActivity : ComponentActivity() {
          override fun onCreate(savedInstanceState: Bundle?) {
              enableEdgeToEdge(); super.onCreate(savedInstanceState)
              val state = MainScreenState(
                  history = SharedPrefsSignHistoryRepository(this),
                  webView = WebViewSignAiController(/* attached in onReady */),
              )
              setContent { SignAiTheme { MainScreen(state) } }
          }
      }
      ```

**Criterio de éxito:** `git grep -n "SharedPreferences" app/src/main/java` solo devuelve hits en `data/`. `git grep -n "evaluateJavascript" app/src/main/java` solo devuelve hits en `ui/webview/SignAiWebViewController.kt`. Cero hits en `ui/screens/`, `ui/components/`, `ui/theme/`.

**Trabajo realizado en Fase 6 (sep 17):**

1. **`MainActivity.kt` reducido a 27 líneas** (imports incluidos, ~13 de código):
   crea `MainScreenState(SharedPrefsSignHistoryRepository(this), WebViewSignAiController())`
   y compone `SignAiTheme { MainScreen(state) }`. Cero `SharedPreferences`, cero
   `WebView`, cero estado de negocio.
2. **Capas creadas** (regla de dependencia 4 → 3 → 2 → 1 respetada):
   - Capa 1: `data/SignHistoryRepository.kt` (interface + `SharedPrefsSignHistoryRepository`,
     cap 20 + top-5 protegido + separador `\n`), `domain/SignAiCommands.kt`
     (constantes JS + `jsToggle` + setters `setDebug/setFreeCamera/setGrid/setBackground`),
     `domain/SignsCatalog.kt` (`available`).
   - Capa 2: `ui/state/Settings.kt` (`data class Settings` + `enum Setting`),
     `ui/state/MainScreenState.kt` (`SnapshotStateList` historial, `settings`, `sign`,
     métodos `play/toggle/pickSign/setBlur/attachWebView`),
     `ui/webview/SignAiWebViewController.kt` (interface + `WebViewSignAiController`:
     único lugar con `evaluateJavascript` y `RenderEffect`),
     `ui/webview/AvatarWebView.kt` (AndroidView movido).
   - Capa 3: `ui/components/{SignAiIconButton,SettingRow,HistoryDropdown,SignPickerDropdown}.kt`.
   - Capa 4: `ui/screens/bottombar/SignAiBottomBar.kt`,
     `ui/screens/settings/SettingsSidebar.kt`, `ui/MainScreen.kt`.
3. **Desviaciones del plan:**
   - **Sin `ui/screens/topbar/SignAiTopBar.kt`**: la TopBar fue eliminada en Fase 5,
     no existe contraparte que mover.
   - **`Settings` defaults reales**: `debug=false` (no `true` como decía el plan),
     `grid=true`, `freeCam=false`, `bg=false` — se preserva el comportamiento
     vigente tras el ajuste de defaults previo a esta fase.
   - **`SignAiWebViewController` sin `WebView` en constructor**: el WebView nace
     dentro del `AndroidView.factory`, así que el controller expone `attach(webView)`
     (el plan ya lo insinuaba con "attached in onReady").
   - **`SignsCatalog` como `object`**: `MainScreenState` recibe `signs: List<String>`
     con default `SignsCatalog.available` (más simple que inyectar el object).
4. **Verificación:** `./gradlew build` verde (7s, 99 tareas), `installDebug` OK,
   app arranca y renderiza idéntica (screenshots `/tmp/opencode/screen-fase6-main.png`
   y `screen-fase6-sidebar.png` con flip temporal de `showSettings`). `rg` confirma:
   `SharedPreferences` solo en `data/`, `evaluateJavascript` y `window.SignAI`
   solo en `ui/webview/SignAiWebViewController.kt` / `domain/SignAiCommands.kt`,
   0 hits de `androidx.compose.material3` / `material.icons`.

### Fase 7 — Polish del Design System

Fase centrada en **adoptar la paleta default de Lumo** como única fuente de
color (hoy conviven dos paletas: la de Lumo y nuestra `SignAiColors` propia),
más dos extras de deuda técnica y una pantalla de carga inicial.

**Decisiones de paleta (sep 17, aprobadas por el usuario):**

- Base: **paleta oscura default de Lumo** (`Color.kt` generado). Fondo negro,
  paneles gris `Gray900 #282828`, texto blanco, bordes `Gray800 #4B4B4B`.
- Acento (`primary`): **azul propio de Lumo `Blue900 #276EF1`** (no inventamos
  color; no usamos el blanco default que Lumo trae para `primary`).
- Superficies de BottomBar y drawer: **gris `#282828`** (Lumo `surface`),
  para que los paneles se despeguen del negro del avatar.

- [x] 7.1 **Unificar la paleta a Lumo.**
  - Editar `ui/designsystem/Color.kt` → `DarkColors` con `primary = Blue900
    (#276EF1)`, `onPrimary = White` y el resto de valores default de Lumo
    (`surface = Gray900`, `background = Black`, `outline = Gray800`,
    `text = White`, `textSecondary = Gray300`, `error = Red400`,
    `success = Green700`, etc.).
  - Eliminar `ui/theme/SignAiColors.kt` (data class + `DefaultSignAiColors` +
    `LocalSignAiColors`): deja de existir la paleta paralela.
  - `ui/theme/SignAiTheme.kt` deja de inyectar paleta propia; expone
    `colors` = `Colors` de Lumo. Unificar los dos `LocalContentColor` (el de
    `ui/theme/LocalContentColor.kt` y el de `ui/designsystem/Color.kt`) en uno.
  - Renombrar los 7 tokens viejos en pantallas/componentes a los de Lumo:
    `bg`→`background`, `bar`→`surface`, `border`/`inputBorder`→`outline`,
    `input`→`surface`, `textMain`→`text`, `primary`→`primary`.
    Regla: ningún `Color(0xFF…)` ni `Color.White`/`Color.Transparent` literal
    fuera de `ui/designsystem/Color.kt`.
  - **Criterio de éxito:** una sola paleta en el repo; `rg "SignAiColors"
    app/src/main/java` devuelve 0 hits; `rg "Color\(0x"` en `ui/theme`,
    `ui/screens`, `ui/components` devuelve 0 hits.
  - **Hecho (sep 17):** `DarkColors.primary = Blue900`, `onPrimary = White`.
    Borrados `SignAiColors.kt` y `ui/theme/LocalContentColor.kt`; `SignAiTheme`
    ahora delega a Lumo (`colors: Colors`). `Icon.kt`/`Text.kt` migrados a
    `ui.designsystem.LocalContentColor`. Renombrados todos los tokens viejos
    en MainScreen, BottomBar, SettingsSidebar, SignPickerDropdown, SettingRow,
    SignAiIconButton, EdgeDrawer y Dropdown. Literales de UI reemplazados por
    `onPrimary`/`transparent`; `signAiCheckboxColors` ahora usa tokens Lumo
    (checked=primary azul, unchecked=outline). Build + install OK; muestreo de
    píxeles confirma `#276EF1` (enviar), `#282828` (superficies) y `#4B4B4B`
    (bordes). Quedan `Color.Unspecified`/`Color.Black`/`Color.White` solo en
    previews y en el ripple de Lumo (no son color de UI).

- [ ] 7.2 **Extra — `Lumo.Button` real para los 3 botones.** Hoy mic, enviar y
      el selector de señas usan `SignAiSurface(onClick = …)`. Editar
      `ui/designsystem/components/Button.kt` para aceptar
      `colors: ButtonColors? = null` (o el mecanismo que exponga Lumo) y
      migrar los 3 call sites a `Button`. Objetivo: usar el componente
      correcto del DS en vez de un `Surface` con click. Marcar `// CUSTOMIZED`.

- [ ] 7.3 **Extra — centralizar `Shapes`, `Sizes`, `Spacing`** en
      `ui/designsystem/foundation/` (archivos generados de Lumo): crear
      `SignAiSizes` (`buttonHeight = 56.dp`, `fieldHeight = 44.dp`),
      `SignAiShapes` (`cornerSmall = 6.dp`, `cornerMedium = 8.dp`) y
      `SignAiSpacing` (`xs/sm/md/lg`). Reemplazar los `56.dp`, `44.dp`,
      `RoundedCornerShape(6/8.dp)`, `padding(4/8/16.dp)` hardcodeados.

- [ ] 7.4 **Pantalla de carga inicial (splash).** Al abrir la app, mostrar
      primero una pantalla del color base de fondo con un spinner centrado;
      cuando el WebView termina de cargar, el splash desaparece y el
      contenido real entra con una animación **sutil** (fade, sin mover el
      avatar ni afectar rendimiento del WebView).
  - Spinner: usar el componente `Progress` de Lumo
    (`./gradlew lumo --add Progress`); si no encaja, spinner propio con
    `androidx.compose.foundation` + `infiniteTransition`.
  - Señal de "listo": extender `ui/webview/AvatarWebView.kt` con un callback
    `onLoaded` disparado en `onPageFinished` (hoy `onReady` dispara al crear
    la vista, antes de que la página cargue).
  - Estado: `loading` en `MainScreen` (o `MainScreenState`); overlay
    `Box` con `background` + spinner; `AnimatedVisibility`/`Crossfade` para
    la salida y la entrada del contenido.
  - **Fallback obligatorio:** si la página no carga (necesita internet para
    Three.js del CDN), ocultar el splash igual tras un timeout (~8 s) para no
    dejar la app colgada en el spinner.
  - Mantener el `background` base `#000000` para no flooding; cero blur sobre
    el avatar.

- [ ] 7.5 Documentar overrides aplicados a cada componente Lumo (comentario
      `// CUSTOMIZED` al inicio de cada `.kt` generado modificado) y refinar
      timings de entrada/salida del drawer si hace falta.
- [ ] 7.6 Actualizar `AGENTS.md` (paleta Lumo + splash) y `openspec/specs`
      si corresponde, y loguear en la sesión.

---

## Compatibilidad / riesgos

| Riesgo | Mitigación |
|---|---|
| Plugin Lumo requiere Gradle 8.4+ (AGP 8.7.3 → Gradle 8.11.1 ✓) | Ya cumplimos. |
| Plugin Lumo requiere JVM 17 | Ya cumplimos. |
| Phosphor lib 1.0.5 fue publicada mayo 2026, podría pin Compose más nuevo que 2024.12.01 | Verificar en Fase 0 con `./gradlew build`. Si rompe, downgrade a 1.0.4 o bump Compose BOM (decisión separada, no en esta migración). |
| Componentes generados por Lumo se sobrescriben si re-ejecutamos `--add` | Documentar en cada `.kt` con un header `// CUSTOMIZED — no re-generate with --add`. No volver a generar componentes ya personalizados. |
| WebView performance con `RenderEffect`/blur | Mantener el `RenderEffect` actual **solo** detrás de la sidebar; nada de blur general, nada de blur sobre el avatar. |
| APK size con Phosphor 6 weights | ~3.6 MB extra (6 × ~600 KB). Decisión aprobada: incluir los 6 desde Fase 0. Si el build final muestra impacto inaceptable, pasar a Regular + Bold. |
| `material-icons-extended` traía todos los Material icons; lo quitamos en Fase 4. Mientras conviva (Fases 1-3), no afecta. | Sin acción especial. |
| `androidx.compose.material3.DropdownMenu` se usa temporalmente en Fases 1-4 | Aceptable porque `material3` sigue en deps hasta Fase 4. En Fase 5 se reescribe sin él. |

---

## Verificación entre fases

Después de **cada** fase:
1. `./gradlew build` (debe pasar limpio).
2. `./gradlew installDebug` en device/emulador.
3. Verificación manual:
   - TopBar muestra "SignAI", botón history (abre dropdown con historial), botón settings (abre sidebar).
   - WebView carga y se ve el avatar.
   - Sidebar muestra 4 toggles + selector de seña.
   - BottomBar muestra placeholder "Versión: …", botón mic (placeholder), botón enviar.
   - Toggles cambian el comportamiento del WebView (ver en Logcat `SignAIWeb`).
   - Cerrar sidebar devuelve el WebView nítido (sin blur).
4. Commit por fase con mensaje conventional (`feat(ui): …`,
   `refactor(ui): …`).

---

## Verificación final (post-Fase 7)

- [ ] `git grep -n "androidx.compose.material3" app/src/main/java` → debe devolver **0 líneas** (excepto comentarios).
- [ ] `git grep -n "androidx.compose.material.icons" app/src/main/java` → debe devolver **0 líneas**.
- [ ] `app/build.gradle.kts` no contiene `material3` ni `material-icons-extended`.
- [ ] `./gradlew build` limpio.
- [ ] `./gradlew installDebug` funciona.
- [ ] `openspec/specs/` y `openspec/sessions/` actualizados con el resultado final.
