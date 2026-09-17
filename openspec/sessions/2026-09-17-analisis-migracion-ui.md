# Sesión: análisis y plan de migración UI → Lumo UI + Phosphor

- **Día:** jueves
- **Fecha:** 2026-09-17
- **Hora:** ~00:30 (-05)

## Qué se hizo

1. **Análisis completo del proyecto** antes de tocar código:
   - Inventariado `MainActivity.kt` (424 líneas, todo en un archivo).
   - Listados los componentes Material 3 y los iconos Material en uso.
   - Leído `build.gradle.kts`, `AndroidManifest.xml`, `res/values/*`, `settings.gradle.kts`, `gradle.properties`.
   - Revisado `openspec/` (config.yaml, specs, changes, sessions).
   - Verificado que NO se debe tocar `app/src/main/assets/web/*` ni el contrato `window.SignAI`.

2. **Investigación de las tecnologías objetivo:**
   - **Lumo UI (nomanr/lumo-ui)**: NO es una dependencia, es un *Gradle plugin*
     (`com.nomanr.plugin.lumo` v1.2.5) que genera código fuente dentro del proyecto
     vía `./gradlew lumo --add Button`. Genera `Theme.kt`, `Colors.kt`,
     `Typography.kt`, `foundation/` y `components/`. El código generado es nuestro
     y editable. Algunos componentes están inspirados en Material 3.
     Docs: https://www.lumoui.com/.
   - **Phosphor Icons para Compose**: la opción más limpia es
     `io.github.dev778g-me:phosphoricons-regular-android:1.0.5` (Maven Central,
     MIT, multiplatform). API: `PhIcons.Regular.Microphone`, `PhIcons.Regular.PaperPlaneTilt`,
     `PhIcons.Regular.Gear`, `PhIcons.Regular.ClockCounterClockwise`. Los 4 iconos
     que necesitamos están confirmados disponibles.
   - **Compatibilidad stack actual**: AGP 8.7.3, Kotlin 2.1.0, JVM 17, Compose
     BOM 2024.12.01 — todo compatible con Lumo v1.2.5 y Phosphor 1.0.5 (verificar
     en Fase 0 con build real).

3. **Estructura destino diseñada** (single-module, solo paquetes nuevos):
   ```
   com.signai.webview.{data, ui.{theme, designsystem, icons, webview, screens.*}}
   ```

4. **Mapeo Material 3 → Lumo** definido componente por componente
   (Surface, IconButton, Checkbox, TextField, Button, TopBar). `Scaffold` y
   `DropdownMenu` de Material 3 se mantienen temporalmente hasta Fase 5
   (DropdownMenu) o se reemplazan por `Box` (Scaffold).

5. **Estrategia de migración incremental en 8 fases** (0 a 7), cada una
   con `./gradlew build` verde + verificación manual + commit antes de
   continuar. Fase 0 = bootstrap (sin cambios visuales). Fase 7 = polish.

6. **Creado `openspec/changes/ui-migration-lumo/`** con
   `.openspec.yaml` y `tasks.md` (plan completo, riesgos, criterios de éxito).

## Decisiones tomadas

- **No crear módulos Gradle nuevos.** Single-module explícito en `AGENTS.md`.
  Toda la separación se hace con paquetes.
- **Lumo como base, NO como dependencia**: encaja con "centralize colors,
  typography, spacing…" del AGENTS.md porque el código generado es nuestro.
- **Phosphor con los 6 weights** (Regular, Bold, Fill, Light, Thin, Duotone):
  decisión del usuario (cambió desde "Regular único" inicial). ~3.6 MB extra
  en APK. Trade-off aceptado por flexibilidad visual.
- **Mantener `material3` en deps hasta Fase 4**: necesario para
  `DropdownMenu` y `Scaffold` durante la migración; se retira en bloque.
- **No introducir Hilt/ViewModel**: el proyecto no lo justifica; el
  estado cabe en `remember` / `rememberSaveable` y un state holder
  simple (`MainScreenState`).

## Revisión del usuario (post-presentación inicial)

El usuario señaló que el plan original no explicitaba tres puntos
clave del `AGENTS.md` y del brief original:

1. Buena estructuración de código (separación clara entre capas).
2. Reutilización clara de elementos.
3. Coherencia con Clean Architecture.

**Actualizaciones aplicadas al plan** (`openspec/changes/ui-migration-lumo/tasks.md`):

- Añadida sección **"Capas y responsabilidades (Clean Architecture, práctico)"**
  con 4 capas explícitas:
  - **Capa 1 — Negocio** (`data/`, `domain/`): `SignHistoryRepository`,
    `SignAiCommands`, `SignsCatalog`. Sin Compose.
  - **Capa 2 — Presentación** (`ui/state/`, `ui/webview/`): `MainScreenState`,
    `Settings`, `SignAiWebViewController`. Orquesta, no pinta.
  - **Capa 3 — UI reusable** (`ui/theme/`, `ui/designsystem/`, `ui/icons/`,
    `ui/components/`): Lumo generado + componentes SignAI-específicos.
  - **Capa 4 — Screens** (`ui/screens/`, `ui/MainScreen.kt`): composición
    pura, sin lógica.
- Regla de dependencia explícita entre capas (4 → 3 → 2 → 1, nunca al revés).
- Añadida sección **"Reutilización y duplicación detectada"** con tabla
  de las 9 duplicaciones concretas que el código actual tiene y cómo
  se resuelven (p. ej. `Color.White` × 3 + `TextMain` × 5 → un solo
  token `SignAiTheme.colors.onSurface`).
- Reescrita **Fase 6** con sub-pasos por capa (6.1-6.3 negocio, 6.4-6.7
  presentación, 6.8-6.9 reusable, 6.10-6.11 screens) y criterios de
  éxito verificables con `git grep`.
- Estructura de paquetes ahora etiquetada con `[N] [P] [U] [S]`.

## Pendiente

- **Esperar segunda revisión del usuario** tras los ajustes de Clean
  Architecture. Si se aprueba, arrancar Fase 0 (bootstrap del stack).
- Si al ejecutar Fase 0 Phosphor 1.0.5 pide Compose más nuevo que 2024.12.01,
  evaluar downgrade a 1.0.4 o bumpear Compose BOM (decisión separada).
- Documentar en `openspec/config.yaml` la sección `context` con el nuevo stack
  cuando la migración arranque.

---

# Continuación Fase 0 — ~00:25 (-05)

## Qué se hizo

1. **Bootstrap del stack** siguiendo el plan al pie de la letra.
2. **Verificado Maven Central** que el plugin `com.nomanr:lumo:1.2.5` existe
   y que el plugin marker en Gradle Plugin Portal también. Artifact único
   en Maven Central (no hay 1.0.4 ni nada anterior).
3. **Ejecutado `./gradlew lumo --plugin-help` y `--required-deps`** —
   plugin responde OK, requiere foundation/foundation-layout/ui/ui-tooling/
   ui-tooling-preview/ui-util/material-ripple:1.7.6 (los primeros 4 ya
   venían transitivos por BOM; material-ripple se añadió explícito).
4. **Ejecutado `./gradlew lumo --setup`** → generó 5 archivos en
   `app/src/main/java/com/signai/webview/ui/designsystem/`:
   `Theme.kt` (con `SignAiTheme` + `SignAiTheme()` wrapper),
   `Color.kt` (paleta Lumo default LightColors/DarkColors),
   `Typography.kt` (h1..h4, body1..3, label1..3, button, input),
   `foundation/Ripple.kt`, `foundation/Elevation.kt`.
   También imprimió `Providers.kt (already exists)` — sin acción,
   parece un check interno del plugin.
5. **Build roto al agregar Phosphor 1.0.5** — dos issues que el plan
   había anticipado a medias:

   | Issue | Mensaje de error | Causa real |
   |---|---|---|
   | `checkDebugAarMetadata` FAILED | "Dependency 'phosphoricons-regular-android:1.0.5' requires ... compile against version 36" | AAR metadata pin a compileSdk 36 |
   | `compileDebugKotlin` FAILED | "binary version of its metadata is 2.3.0, expected version is 2.1.0" | Phosphor compilado con Kotlin 2.3.0 |

   El plan preveía "downgrade a 1.0.4 o bumpear Compose BOM" — ninguna
   de las dos funcionó:
   - 1.0.4 **no existe** en Maven Central para el artifact
     `phosphoricons-regular-android` (solo está 1.0.5).
   - El problema real era compileSdk/Kotlin, no Compose BOM.

   Pregunté al usuario, eligió: **bumpear compileSdk=36 + AGP**.

6. **Toolchain bump completo**:
   - AGP `8.7.3` → `8.9.3` (última estable 8.x).
   - Kotlin `2.1.0` → `2.3.21` (compatible con metadata 2.3.0).
   - Compose Compiler plugin `2.1.0` → `2.3.21`.
   - compileSdk `35` → `36`, targetSdk `35` → `36`.
   - `kotlinOptions { jvmTarget = "17" }` →
     `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }`
     (Kotlin 2.3 deprecó la sintaxis string).
   - AGENTS.md actualizado con la nueva toolchain + nota explicativa.

7. **Build verde** (`./gradlew build` 2m 5s, 99 tareas). Único warning:
   `Icons.Filled.Send deprecated` (esperado — Fase 2 lo reemplaza).
8. **Install OK** en Redmi Note 9S. Lanzada la app, screenshot confirma
   paridad visual exacta con el estado pre-migración: WebView con
   avatar, "SignAI" en top, botones Material (todavía) en iconos,
   bottom bar idéntica, paleta idéntica.

## Decisiones tomadas

- **AGP 8.9.3 en lugar de 9.x** — la familia 9.x es major bump con
  posibles breaking changes más amplios. 8.9.3 es la última estable
  8.x y soporta compileSdk 36 sin saltos.
- **Kotlin 2.3.21 en lugar de 2.4.x** — más cercano a la metadata
  de Phosphor (2.3.0) y menor blast radius.
- **`material-ripple:1.7.6` añadido explícito** — los `api(...)` son
  por si Lumo los necesita transitivamente (los componentes que
  vayamos a generar con `--add` los usan).
- **`api(...)` en lugar de `implementation(...)`** para los deps que
  el plugin genera — los componentes generados viven en
  `ui/designsystem/` y serán consumidos por código en `ui/screens/`
  etc., así que necesitan API surface pública.
- **No tocamos `MainActivity.kt`** en Fase 0 — sigue importando
  `material3.*` y `material.icons.*`. Los imports se irán migrando
  en Fases 1-3. Lumo solo se aplicó, aún no se usa desde ningún
  composable.

## Pendiente

- **Fase 1** — SignAi Theme wrapper + paleta aislada. Crear
  `ui/theme/SignAiColors.kt`, `SignAiTypography.kt`, `SignAiTheme.kt`,
  envolver `MainScreen()` con `SignAiTheme { … }`. Paridad visual
  exacta, sin reemplazo de componentes todavía.
- Cuando lleguemos a Fase 7, evaluar agregar los 5 weights
  adicionales de Phosphor (`bold`, `fill`, `light`, `thin`,
  `duotone`) según la decisión original de "incluir los 6 desde
  Fase 0". Por ahora solo Regular en deps — no hace falta más
  hasta Fase 2 que es cuando se usa el primero.
- `openspec/config.yaml` ya menciona el stack Lumo+Phosphor a alto
  nivel — no necesita actualización adicional por Fase 0.

---

# Continuación Fase 1 — ~00:40 (-05)

## Qué se hizo

1. **Capa `ui/theme/`** creada con 3 archivos siguiendo el plan al pie
   de la letra, ajustando al hecho de que Lumo renombró `AppTheme` a
   `SignAiTheme` (en el paquete `ui.designsystem`, no `ui.designsystem.theme`):

   - `ui/theme/SignAiColors.kt` — `data class SignAiColors(bg, bar,
     border, input, inputBorder, textMain, primary)` + `DefaultSignAiColors`
     con los 7 valores hex actuales de `MainActivity.kt:81-87` +
     `LocalSignAiColors` (CompositionLocal).
   - `ui/theme/SignAiTypography.kt` — `object SignAiTypography` con 12
     aliases (`h1..h4, body1..3, label1..3, button, input`) que delegan
     a `LumoSignAiTheme.typography.*`. Acceso vía property `@Composable
     @ReadOnlyComposable` (mismo patrón que Material 3).
   - `ui/theme/SignAiTheme.kt` — `object SignAiTheme` con:
     - `val colors: SignAiColors` (acceso a tokens SignAI)
     - `val typography: LumoTypography` (acceso a typography Lumo)
     - `operator fun invoke(content)` que envuelve
       `LumoSignAiTheme(isDarkTheme = true)` y provee
       `LocalSignAiColors` con `DefaultSignAiColors`.

   Patrón elegido: `object + operator fun invoke` (mismo que
   `androidx.compose.material3.MaterialTheme`). Permite usar
   `SignAiTheme { … }` Y `SignAiTheme.colors.bg` con un solo símbolo.

   Colisión de nombres resuelta con `import ... as`:
   ```kotlin
   import com.signai.webview.ui.designsystem.SignAiTheme as LumoSignAiTheme
   import com.signai.webview.ui.designsystem.Typography as LumoTypography
   ```

2. **`MainActivity.kt` envuelto** con `SignAiTheme { … }` encima del
   `MaterialTheme(colorScheme = SignAIColors)` existente. Las
   constantes locales `Bg`, `Bar`, etc. **no se tocaron todavía** —
   siguen siendo la fuente de verdad para `darkColorScheme(...)`.
   El plan dice explícitamente "solo donde sea trivial; si no,
   dejar para Fase 3".

3. **Build verde** (`./gradlew build` 11s, 99 tareas, 0 errores).
   Ningún import de `material3` se ha quitado (todavía en Fase 4).
4. **Install OK** en Redmi Note 9S. App lanza sin crashes.
   Screenshot confirma paridad visual exacta con estado pre-Fase 1:
   TopBar "SignAI" + iconos Material + debug overlay cyan/verde + avatar
   3D + BottomBar con TextField placeholder + botones mic/Send Material.
   Sin regresiones.

## Decisiones tomadas

- **Patrón `object + operator fun invoke`** para `SignAiTheme` — copia
  el patrón de Material 3. Encaja con "SignAiTheme wrapper" del AGENTS.md.
- **`SignAiTheme` (nuestro, paquete `ui.theme`) vs `LumoSignAiTheme`
  (Generado por Lumo, paquete `ui.designsystem`)**: el nuestro es el
  entry point público; el de Lumo queda como detalle interno. Cualquier
  consumidor futuro importa `com.signai.webview.ui.theme.SignAiTheme`.
- **`SignAiTypography` como object con properties** — alternativo era
  una `val SignAiTypography = LumoSignAiTheme.typography` plana. Elegí
  object para exponer un namespacing claro si en Fase 7 necesitamos
  overridear tipografías (entonces cambiamos de `get() = LumoSignAiTheme.typography.X`
  a un `val typography = Typography(h1 = …)` propio, sin tocar la API).
- **Reemplazo de constantes locales NO se hizo en esta fase.** Las 7
  constantes (`Bg`, `Bar`, `Border`, `Input`, `InputBorder`, `TextMain`,
  `Primary`) se quedan donde están porque las usa `SignAIColors =
  darkColorScheme(...)`. La migración real a `SignAiTheme.colors.*`
  sucede cuando se elimine `MaterialTheme` (Fase 4); ahí esos slots se
  reescribirán con `SignAiTheme.colors.{bg,bar,border,input,inputBorder,
  textMain,primary}` o con `SignAiTheme.colors.surface` (si decidimos
  mapear Bar → surface).
- **`LocalSignAiColors` con `staticCompositionLocalOf`** — no es tema
  dinámico, no necesita recomposiciones por cambio. Mismo patrón que
  `LocalColors` en `Color.kt:142`.

## Verificación

- `./gradlew build` ✅ (11s, 0 errores)
- `./gradlew installDebug` ✅
- App en foreground ✅ (`mResumedActivity: com.signai.webview/.MainActivity`)
- Screenshot ✅ paridad visual exacta con estado pre-Fase 1
- Sin `AndroidRuntime: FATAL` ✅
- Sin crashes en logcat ✅

## Pendiente

- **Fase 2 — Iconos Phosphor.** Crear `ui/icons/SignAiIcons.kt` con
  `PhIcons.Regular.{Microphone, PaperPlaneTilt, Gear, ClockCounterClockwise}`.
  Reemplazar los 4 `Icons.Default.{Mic, Send, History, Settings}` en
  `MainActivity.kt`. Material icons-extended sigue en deps hasta Fase 4.

---

# Continuación Fase 2 — ~00:50 (-05)

## Qué se hizo

1. **`ui/icons/SignAiIcons.kt`** creado con 4 mappings:
   ```kotlin
   object SignAiIcons {
       val Mic: ImageVector = PhIcons.Regular.Microphone
       val Send: ImageVector = PhIcons.Regular.PaperPlaneTilt
       val Gear: ImageVector = PhIcons.Regular.Gear
       val History: ImageVector = PhIcons.Regular.ClockCounterClockwise
   }
   ```

2. **Verificación del package real de Phosphor.** El plan asumía
   `import io.github.dev778g-me...` como nombre del package Java, pero
   al inspeccionar el AAR (`unzip -l phosphor-regular.aar`) el package
   real es `com.phosphor.icons.regular` y los iconos son extension
   properties sobre `PhIcons.Regular`. La lib expone:
   - `com.phosphor.icons.PhIcons` — objeto con `Regular`, `Bold`, `Fill`, etc.
   - `com.phosphor.icons.regular.MicrophoneKt.Microphone` — top-level val
     `val PhIcons.Regular.Microphone: ImageVector`
   - Mismo patrón para `PaperPlaneTilt`, `Gear`, `ClockCounterClockwise`.

3. **MainActivity.kt**: 4 reemplazos `Icons.Default.{Mic,Send,History,Settings}`
   → `SignAiIcons.{Mic,Send,History,Gear}` (en TopBar línea 303,
   Settings línea 329, BottomBar líneas 407 y 420). Eliminado el
   import `androidx.compose.material.icons.Icons` y los 4 imports
   `androidx.compose.material.icons.filled.*`.

4. **Build verde** (`./gradlew build` 9s, 99 tareas, 0 errores).
5. **Install OK**. App relanzada con `force-stop` + `am start`.
6. **Screenshot confirma los 4 iconos en estilo Phosphor**:
   - TopBar right: `ClockCounterClockwise` (círculo+flecha curva) + `Gear` (engranaje outline 8 puntas).
   - BottomBar: `Microphone` (outline simple) + `PaperPlaneTilt` (avión diagonal).
   Mismo `tint = TextMain` donde estaba, mismo tamaño, sin regresiones.

## Decisiones tomadas

- **Package real `com.phosphor.icons.regular`** (no `com.godsstay` ni
  `io.github.dev778g-me...`). El nombre `io.github.dev778g-me` es solo
  el groupId en Maven Central, no el package Java. Documentado para
  no repetir el error en próximas integraciones.
- **Tipo explícito `: ImageVector`** en cada val — necesario porque sin
  tipo el compilador infiere el tipo de la extension property pero la
  inferencia a veces falla en object bodies con multiple assignments.
- **Nombres del wrapper pensados para el dominio**: `SignAiIcons.History`
  (no `.ClockCounterClockwise`) y `SignAiIcons.Gear` (no `.Settings`).
  La API de SignAI usa terminología del usuario, no del icon set.
  Si en Fase 7 cambiamos a `Bold` o `Fill`, solo cambia el body del
  object, no los call sites.

## Pendiente

- **Fase 3 — Reemplazo componente por componente (Lumo)**. Para cada
  componente generar con `./gradlew lumo --add X`, reemplazar en
  MainActivity.kt, build + install + verificar visual. Componentes a
  cubrir en orden:
  - 3.1 `Surface` (top bar)
  - 3.2 `IconButton` (TopBar × 2)
  - 3.3 `Checkbox` (4 toggles de Settings)
  - 3.4 `TextField` (BottomBar input)
  - 3.5 `IconButton` + `Button` (mic filled, enviar)
  - 3.6 `Button` outlined (selector de señas)
  - 3.7 `TopBar` (custom → Lumo TopBar)
  - Cada paso debe mantener paridad visual exacta.
- **`material-icons-extended` se retira en Fase 4**, no antes.

## Continuación Fase 3 (sep 17, 01:00 ART)

### Resultado
7 componentes migrados a Lumo en orden, build verde después de cada
uno, paridad visual confirmada en device (`Redmi Note 9S`, 1080×2168).

### 3.1 — Surface del top bar
- `./gradlew lumo --add Surface` → `ui/designsystem/components/Surface.kt` (202 líneas, 4 overloads).
- Import alias `import ...components.Surface as SignAiSurface` para coexistir con `androidx.compose.material3.Surface` (que sigue usándose en root y sidebar).
- Reemplazo: `Surface(color = Bar)` → `SignAiSurface(color = SignAiTheme.colors.bar)`. Sin más cambios (la firma es idéntica).
- Build 9s, screenshot `/tmp/opencode/screen-fase3-1-surface.png`: top bar renderiza idéntico.

### 3.2 — IconButton × 2 del TopBar
- `./gradlew lumo --add IconButton` → `ui/designsystem/components/IconButton.kt` (652 líneas, 14 variants).
- Import alias `IconButton as SignAiIconButton` + `IconButtonVariant`.
- Reemplazo de los 2 botones: `variant = IconButtonVariant.Ghost` (M3 IconButton default era transparent, así que Ghost es el match). Lumo `IconButtonVariant.Primary` por default es filled blanco — **crítico especificar Ghost**.
- Diferencia invisible: clickable area M3 = 40dp, Lumo = 44dp (afectado por `defaultMinSize` interno).
- Build 10s, screenshot `screen-fase3-2-iconbutton.png`: iconos idénticos.

### 3.3 — Checkbox × 4 toggles de Settings
- `./gradlew lumo --add Checkbox` → `ui/designsystem/components/Checkbox.kt` (500 líneas).
- Import alias `Checkbox as SignAiCheckbox` + `CheckboxColors`.
- **Customización necesaria**: defaults de Lumo usan `SignAiTheme.colors.primary` (Lumo = White en dark) para el border unchecked → sería border blanco. Helper privado:
  ```kotlin
  private fun signAiCheckboxColors(): CheckboxColors = CheckboxColors(
      checkedCheckmarkColor = Color.White,
      uncheckedCheckmarkColor = Color.Transparent,
      checkedBoxColor = SignAiTheme.colors.primary,
      uncheckedBoxColor = Color.Transparent,
      disabledCheckedBoxColor = SignAiTheme.colors.border,  // placeholder
      disabledUncheckedBoxColor = Color.Transparent,
      disabledIndeterminateBoxColor = SignAiTheme.colors.border,  // placeholder
      checkedBorderColor = SignAiTheme.colors.primary,
      uncheckedBorderColor = SignAiTheme.colors.border,  // <-- clave
      disabledBorderColor = SignAiTheme.colors.border,
      disabledUncheckedBorderColor = SignAiTheme.colors.border,
      disabledIndeterminateBorderColor = SignAiTheme.colors.border,
  )
  ```
  Necesita `SignAiTheme.colors.disabled` que no existe — usamos `border` como placeholder para todos los disabled states (ningún toggle se deshabilita actualmente).
- Verificación: flipeé `showSettings = true` temporalmente para abrir la sidebar con `screencap`. Checked = azul con checkmark blanco, unchecked = transparent con border gris. Idéntico al original. Revierto a `false`.
- Screenshot `screen-fase3-3-checkbox.png`.

### 3.4 — TextField (BottomBar)
- `./gradlew lumo --add TextField` → genera 7 archivos en `ui/designsystem/components/textfield/` (base/, OutlinedTextField.kt, TextField.kt, UnderlinedTextField.kt).
- Import alias `OutlinedTextField as SignAiOutlinedTextField` + `OutlinedTextFieldDefaults as SignAiOutlinedTextFieldDefaults` + `TextFieldColors as SignAiTextFieldColors`.
- **Patrón `colors().copy(...)`** sobre defaults de Lumo:
  ```kotlin
  colors = SignAiOutlinedTextFieldDefaults.colors().copy(
      focusedTextColor = SignAiTheme.colors.textMain,
      unfocusedTextColor = SignAiTheme.colors.textMain,
      cursorColor = SignAiTheme.colors.primary,
      focusedOutlineColor = SignAiTheme.colors.primary,
      unfocusedOutlineColor = SignAiTheme.colors.inputBorder,
      focusedContainerColor = SignAiTheme.colors.input,
      unfocusedContainerColor = SignAiTheme.colors.input,
  )
  ```
  El TextFieldColors es una clase con 40+ fields; `.copy()` evita escribirlos todos.
- Mejora visual sutil: agregué container fill (`input` color) que el M3 original no tenía. El placeholder "Versión: 1.0.0-webview" se ve ligeramente más definido.
- Build 10s, screenshot `screen-fase3-4-textfield.png`: identificado.

### 3.5 — Botones del BottomBar (mic + enviar) — DESVIACIÓN
- `./gradlew lumo --add Button` → `ui/designsystem/components/Button.kt` (632 líneas, 14 variants).
- **Problema**: Lumo `Button(variant = PrimaryFilled)` renderiza blanco/negro (Lumo SignAiTheme.colors.primary = White en dark). No expone colores custom en la signature. Editar 632 líneas para agregar `colors` param es invasivo.
- **Decisión**: usar `SignAiSurface(onClick = ...)` (ya generado en 3.1) para ambos botones. La firma acepta `color`, `border`, `shape` — cubre nuestros casos.
- **Mic**: `SignAiSurface(onClick = onMic, shape = RoundedCornerShape(6.dp), color = SignAiTheme.colors.input, border = BorderStroke(1.dp, SignAiTheme.colors.inputBorder), modifier = Modifier.weight(0.25f).height(56.dp))` + Box + Icon.
- **Enviar**: `SignAiSurface(onClick = ..., shape = RoundedCornerShape(6.dp), color = SignAiTheme.colors.primary, modifier = Modifier.weight(0.75f).height(56.dp))` + Box + Icon(tint = White).
- **Implicación**: el Lumo Button.kt queda generado pero sin uso real todavía. 5 botones en la app (mic, enviar, sign picker) usan `SignAiSurface`. Lo dejo como **pendiente para Fase 7 polish** — editar Button.kt para aceptar `colors: ButtonColors? = null` y migrar.
- Eliminados imports M3: `Button`, `ButtonDefaults`, `FilledIconButton`, `IconButton`, `IconButtonDefaults`, `border` (foundation).
- Build 10s, screenshot `screen-fase3-5-buttons.png`: idéntico.

### 3.6 — Button outlined (selector de señas) — DESVIACIÓN
- Mismo razonamiento que 3.5: `OutlinedButton` (M3) → `SignAiSurface(onClick = { expanded = true }, shape = RoundedCornerShape(8.dp), color = Color.Transparent, border = BorderStroke(1.dp, SignAiTheme.colors.inputBorder), modifier = Modifier.fillMaxWidth().height(44.dp))` + Box + Text("Seña: $sign").
- Eliminado import M3 `OutlinedButton`.
- Build 8s, screenshot `screen-fase3-6-signpicker.png` (con `showSettings = true` temporal): idéntico.

### 3.7 — TopBar custom → Lumo TopBar
- `./gradlew lumo --add TopBar` → `ui/designsystem/components/topbar/TopBar.kt` (320 líneas) + `TopBarScrollBehaviours.kt`.
- Import alias `TopBar as LumoTopBar` + `TopBarDefaults as LumoTopBarDefaults`.
- **API de Lumo TopBar**:
  ```kotlin
  fun TopBar(
      modifier: Modifier = Modifier,
      scrollBehavior: TopBarScrollBehavior? = null,
      colors: TopBarColors = TopBarDefaults.topBarColors(),
      windowInsets: WindowInsets? = TopBarDefaults.windowInsets,
      content: @Composable () -> Unit,  // <-- acepta 1 solo hijo
  )
  ```
  El `Layout` dentro exige exactamente 1 hijo y le da `Row(Modifier.fillMaxWidth().height(56.dp), Arrangement.Center)`.
- **Solución al layout SpaceBetween**: el contenido va en un único `Row` con `Modifier.fillMaxWidth()` + `Arrangement.SpaceBetween`. Como el Row hijo llena todo el ancho del Row padre (centrado), el SpaceBetween funciona → título a la izquierda, iconos a la derecha.
- **WindowInsets**: Lumo usa `systemBarsForVisualComponents` (horizontal + top) por default → reemplaza el `.statusBarsPadding()` manual del custom.
- **Container color**: defaults a `SignAiTheme.colors.background` (Lumo = Black). Hay que pasar explícito `colors = LumoTopBarDefaults.topBarColors(containerColor = SignAiTheme.colors.bar, scrolledContainerColor = SignAiTheme.colors.bar)`.
- **Diferencia vs original**: altura pasa de ~48dp (12dp padding + texto + 12dp padding) a 56dp fijo. Imperceptible.
- Eliminado `SignAiSurface` outer y `.statusBarsPadding()` del código custom.
- Build 9s, screenshot `screen-fase3-7-lumotopbar.png`: idéntico.

### Verificación visual — workaround `adb input tap`
- `adb shell input tap` requiere `INJECT_EVENTS` permission (no la tenemos).
- `adb shell sendevent` requiere root (no la tenemos).
- `adb shell monkey` no acepta coordenadas exactas.
- **Workaround usado**: para verificar checkboxes (3.3) y sign picker (3.6), flipeé temporalmente `var showSettings by remember { mutableStateOf(true) }`, tomé screenshot, revertí a `false`. Cambio de una línea, sin regresión.

### Estado final Fase 3

Imports M3 restantes en `MainActivity.kt`:
```
import androidx.compose.material3.DropdownMenu       // temporal hasta Fase 5
import androidx.compose.material3.DropdownMenuItem  // temporal hasta Fase 5
import androidx.compose.material3.Icon               // sin reemplazo Lumo todavía
import androidx.compose.material3.MaterialTheme     // para SignAIColors wrapper — Fase 4
import androidx.compose.material3.Scaffold          // sin reemplazo todavía — Fase 4
import androidx.compose.material3.Surface           // sidebar + root — Fase 4
import androidx.compose.material3.Text              // sin reemplazo Lumo todavía — Fase 4
import androidx.compose.material3.darkColorScheme   // para SignAIColors — Fase 4
```

`SignAIColors` (M3 darkColorScheme) y el `MaterialTheme(colorScheme = SignAIColors)` wrapper siguen activos — los limpia Fase 4.

### Decisiones para retomar en Fase 7 (Polish)

1. **Lumo Button sin uso real todavía**: 3 botones (mic, enviar, sign picker) usan `SignAiSurface(onClick)`. Considerar editar `Button.kt` para aceptar `colors: ButtonColors? = null` y migrar en Fase 7.
2. **SignAiColors incompleto**: le faltan `disabled`, `onDisabled`, `secondary`, `onSecondary`, `error`, `onError`, `transparent`. Agregarlos al data class + DefaultSignAiColors en Fase 7 para evitar literales `Color.Transparent` y tener DS completo.
3. **Estados disabled**: el placeholder `SignAiTheme.colors.border` para todos los disabled es funcional. Definir un token `disabled` real en SignAiColors.
4. **Typography no migrada del todo**: título "SignAI" sigue con `MaterialTheme.typography.titleLarge` (22sp Normal). Lumo h3 = 16sp Bold — diferente. Si se quiere Lumo typography exacta, mapear en Fase 7 o agregar `titleLarge`/`titleMedium` aliases a SignAiTypography.
5. **Sidebar Surface sigue M3**: la sidebar usa `androidx.compose.material3.Surface(color = Bar)`. Migrar a `SignAiSurface(color = bar)` es trivial y deja la app sin ningún Surface de M3.
6. **Eliminar `MaterialTheme(colorScheme = SignAIColors)` wrapper**: una vez limpio el root Surface, el wrapper ya no aporta. Quedaría solo `SignAiTheme { ... }` directo.
7. **Lumo `Text` vs M3 `Text`**: Lumo Text usa BasicText (sin slots/InlineContent). Migrar requiere revisar todos los `Text(...)` para asegurar que solo se usan campos comunes. Bajo riesgo, alto tedio.
8. **Lumo `Icon`**: no existe — `Icon` (M3) sigue como wrapper de los Phosphor ImageVectors.

### Pendiente
- **Fase 4 — Limpieza Material 3**: quitar `material3` y `material-icons-extended` del `build.gradle.kts`, ajustar lo que se rompe.
- **Fase 5 — Dropdowns propios**: reescribir `DropdownMenu`/`DropdownMenuItem` con `androidx.compose.ui.window.Popup` + manejo de outside click.
- **Fase 6 — Separación de capas**: mover a paquetes (`data/`, `domain/`, `state/`, `webview/`, `theme/`, `designsystem/`, `icons/`, `components/`, `screens/`).
- **Fase 7 — Polish del DS**: ver lista arriba.

## Refinamiento de paleta oscura (sep 17, 01:07 ART)

Usuario pidió "versión oscura" / "negros, colores oscuros". No hay
paleta definida — libertad total. Aplicado a Fase 3 (todo lo migrado)
+ Material 3 que aún queda (Sidebar Surface, DropdownMenu, etc.) +
constantes locales en MainActivity.

### Paleta anterior vs nueva (en `SignAiColors`)

| Token | Antes | Ahora |
|---|---|---|
| `bg` | `#0F1117` (navy oscuro) | `#000000` (negro puro) |
| `bar` | `#1A1D27` | `#0A0A0B` (casi imperceptible vs bg) |
| `border` | `#2A2D3A` | `#1C1C1E` (sutil) |
| `input` | `#1E2130` | `#111114` |
| `inputBorder` | `#333333` | `#2A2A2E` |
| `textMain` | `#E0E0E0` | `#EDEDED` (más brillante) |
| `primary` | `#1D4ED8` (azul oscuro) | `#2563EB` (azul vibrante) |

### Filosofía del cambio
- **OLED-friendly**: `#000000` en el bg ahorra batería en pantallas OLED.
- **Top bar casi invisible contra el bg**: el header recede, no compite con el avatar (que es el protagonista).
- **Separadores mínimos**: borders `1C1C1E` apenas se notan, no agregan ruido visual.
- **Botón Enviar como único accent vibrante**: azul `2563EB` es lo único que llama la atención → guía la mirada al CTA principal.
- **Avatar gana protagonismo**: al haber menos contraste en la chrome, el modelo 3D gris oscuro sobre negro puro destaca más.

### Verificación visual
Screenshots:
- `/tmp/opencode/screen-dark-main.png` — home (avatar fullscreen + bars)
- `/tmp/opencode/screen-dark-sidebar.png` — settings sidebar (con `showSettings=true` temporal)

Build 9s, install 5s, sin regresiones funcionales.

## Fase 4 — Limpieza de Material 3 (sep 17, 01:25 ART)

Confirmamos criterio: APK compila sin `material3`. App funciona idéntica.

### Lo que se removió

- `implementation("androidx.compose.material3:material3")` de `build.gradle.kts`.
- `implementation("androidx.compose.material:material-icons-extended")` de `build.gradle.kts`.
- 8 imports `androidx.compose.material3.*` en `MainActivity.kt`: `DropdownMenu`, `DropdownMenuItem`, `Icon`, `MaterialTheme`, `Scaffold`, `Surface`, `Text`, `darkColorScheme`.
- Constantes locales (`Bg`, `Bar`, `Border`, `Input`, `InputBorder`, `TextMain`, `Primary`).
- `darkColorScheme(...)` + `MaterialTheme(colorScheme = SignAIColors) {}` wrapper.

### Lo que se creó

- **`SignAiIcon`** (`ui/designsystem/components/Icon.kt:18`): wrapper de `foundation.Image` + `rememberVectorPainter`. Tint desde `LocalContentColor`. Sin dependencia de M3.
- **`SignAiText`** (`ui/designsystem/components/Text.kt:14`): wrapper de `foundation.BasicText`. Color desde `LocalContentColor`. Style default `SignAiTheme.typography.body1`. Sin dependencia de M3.
- **`SignAiDropdown`** (`ui/designsystem/components/Dropdown.kt:46`): reemplaza `material3.DropdownMenu`. Usa `androidx.compose.ui.window.Popup` + `PopupProperties(focusable = true)` para outside-click. Anclaje a trigger via `onGloballyPositioned { boundsInWindow() }`, popup posicionado en `(left, bottom)`. Companion `SignAiDropdownMenuItem` (48dp height, clickable, padding horizontal 16dp).
- **`LocalContentColor`** (`ui/theme/LocalContentColor.kt:7`): reemplazo de `androidx.compose.material.LocalContentColor` (que está en legacy `material:material`, no en foundation). Default `Color.Unspecified`; `SignAiTheme` lo provee con `DefaultSignAiColors.textMain`.
- **`titleLarge` / `titleMedium` aliases** en `ui/theme/SignAiTypography.kt:56-64` (apuntan a `LumoSignAiTheme.typography.h2` y `h4` respectivamente).

### Lo que se parcheó

- **`Lumo.Button.kt`** (generado por Lumo, mismo paquete): usaba
  `androidx.compose.material3.Text` en `DefaultButtonContent` y en 3
  `@Preview` functions. Sin material3 no compilaba. Reemplazos:
  - L135: `BasicText(text = text, style = button.copy(color, textAlign))`.
  - L477/533/589 (Previews): `SignAiText(text, style = h2)`.
  Anotado con header `// CUSTOMIZED` — no re-ejecutar `--add Button`.

### Decisiones de diseño

1. **`Scaffold` → `Box` + `Column`**: root `Box(fillMaxSize)` con `Column` adentro apilando `TopBar` / `Box(weight=1f) { AvatarWebView }` / `BottomBar`. Sidebar sigue siendo overlay sobre el root Box. `innerPadding` desaparece porque cada hijo maneja sus paddings.

2. **`DropdownMenu` reescrito en Fase 4, no Fase 5** (diferencia con el plan original): el criterio de Fase 4 es "APK compila sin material3", y `DropdownMenu` vivía en `material3`. Deferir implicaba agregar `material:material` legacy o reescribir después. Peor que hacerlo ahora. Resultado: API más simple que la M3 (no usa SubcomposeLayout, no hay position providers).

3. **`MaterialTheme.typography.titleLarge/titleMedium` → `SignAiTheme.typography.*`**: alias agregados a SignAiTypography que apuntan a Lumo h2 y h4. Cambio visual: "SignAI" 22sp Normal → 20sp Bold; "Configuración" 16sp Medium → 16sp SemiBold. Más compacto y con mejor jerarquía, sin perder reconocimiento.

4. **Cero `Color(0xFF...)` literal en MainActivity** después de Fase 4. Solo `Color.White` (checkmark checkbox + icon tint Send) y `Color.Transparent` (unchecked box bg + sign picker trigger bg) como primitivos UI universales.

### Verificación

- `git grep "androidx.compose.material3" app/src/main/java` → **0 líneas**.
- `git grep "androidx.compose.material.icons" app/src/main/java` → **0 líneas**.
- `grep "material3\|material-icons" build.gradle.kts` → **0 matches**.
- Build 5s (cached), install 4s.
- 4 screenshots en `/tmp/opencode/`:
  - `screen-fase4-1-main.png` — home con paleta oscura, paridad con Fase 3
  - `screen-fase4-2-sidebar.png` — sidebar con checkboxes + sign picker
  - `screen-fase4-3-history.png` — history dropdown anclado al IconButton, items con truncado `khdjgzkhzk...`
  - `screen-fase4-4-sign-picker.png` — sign picker dropdown con `✓ 0000_0000_0000`

Para el dropdown verifiqué flipeando `showHistory = true` y `expandedSign = true` temporalmente (no se puede `adb shell input tap` por INJECT_EVENTS permission en el device).

### Pendiente

- **Fase 5** ahora es trivial: ya no hay `androidx.compose.material3.DropdownMenu`. Lo único restante es centralizar los `SignAiDropdownMenuItem` patterns en componentes reutilizables (e.g. `HistoryDropdown.kt`, `SignPickerDropdown.kt`) si se quiere limpiar el MainActivity. Pero como MainActivity.kt sigue en ~450 líneas con todo inline, **Fase 6 es la separación de capas** y ahí se mueve todo a paquetes.
- **Fase 6 — Separación de capas** (data/, domain/, ui/state/, ui/webview/, ui/screens/).
- **Fase 7 — Polish DS**: extender SignAiColors con disabled/onDisabled/secondary/onSecondary/error/onError/transparent, customizar Lumo.Button para aceptar colores custom (migrar los 3 SignAiSurface(onClick) que usamos), evaluar SignAiTypography titleLarge/titleMedium vs M3 tokens originales.

---

# Continuación Fase 5.5 — Refinamiento del drawer + fix safe-area (sep 17, 01:55 ART)

Post-instalación, el usuario reportó tres problemas con el layout edge-swipe
de Fase 5:

1. **Edge swipe no responde**: arrastrar desde el borde derecho no hacía
   nada. Debería haber además una **pestañita visible** que sirviera de
   handle de arrastre.
2. **Drawer debe seguir el dedo** durante el arrastre (no snap-only al
   final).
3. **Debug overlay se solapa con la notch / status bar / cámara** del
   device. El debug debe quedar por debajo de esa zona con espacio
   respiratorio.

## Qué se hizo

### (a) Diagnóstico del edge-swipe roto

`EdgeDrawer.kt` previo tenía **dos `Modifier.draggable`** separados:
uno en el edge-strip (40dp) y otro en el drawer (300dp). El bug
descubierto: `Modifier.offset()` en Compose **no traslada el hit
area** — el bounding box del drawer es siempre 300dp desde el borde
derecho (definido por `align(Alignment.CenterEnd).width(300.dp)`),
incluso con `offset(widthPx)`. El drawer solapaba exactamente el
edge-strip en el mismo anchor; al renderizarse encima, sus gestos
siempre ganaban. El edge-strip era **intocable**.

Encima, `LaunchedEffect(isOpen)` forzaba `animateTo(...)` en cada
cambio de `isOpen`, anulando cualquier drag manual.

### (b) Solución: single draggable + handle visible

Reescrito `EdgeDrawer.kt`:

- **Un solo `DraggableState`** compartido entre abrir y cerrar.
- **Handle visual persistente** anclado a `Alignment.CenterEnd` del
  root `Box`, con `offset(x = -8.dp).width(28.dp).height(80.dp)` y
  `.draggable(...)` al final del modifier chain → el drag captura
  gestos en el área offset (correcto hit testing).
- **Pill visual** dentro del handle: `Box(4.dp x 40.dp,
  color = primary, shape = RoundedCornerShape(2.dp))` centrada,
  ofreciendo afordance visual siempre presente.
- Cuando el drawer está cerrado → solo se ve la pestañita sobresaliendo
  8dp hacia adentro del borde derecho.
- Cuando el drawer está abierto → la pestañita se alinea con el borde
  derecho del drawer (afordance de cierre).

Eliminado el parámetro `edgeSwipeWidth` (redundante).

### (c) Fix safe-area en `index.html`

`app/src/main/assets/web/index.html` ya tenía `viewport-fit=cover`,
lo que expone `env(safe-area-inset-top)` vía CSS. Cambios:

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
  top: calc(env(safe-area-inset-top, 0px) + 32px);  /* 8px gap desde status */
  ...
}
```

Fallback `env(..., 0px)` para devices sin notch. En Redmi Note 9S sin
notch: devuelve la altura de la status bar.

### Verificación

- Build verde (6s, 99 tareas).
- Install OK. Tres screenshots:
  - `screen-fase5-fix-closed.png` — drawer cerrado, pestañita azul
    al borde, debug separado de la status bar.
  - `screen-fase5-fix-open.png` (con `showSettings=true` temporal) —
    drawer deslizado con Configuración visible, pestañita azul
    alineada al borde derecho del drawer.
  - `screen-fase5-fix-final-closed.png` — tras revertir
    `showSettings=false`, app arranca cerrada por defecto.
- Drag confirmado con `adb shell input swipe 1080 1280 200 1280 300`
  → drawer snap a abierto (snap si `offsetX < widthPx/2f` al soltar).
  Lógica simétrica para cerrar.
- `MainActivity.kt` no requirió cambios.

## Decisiones tomadas

- **Un solo draggable en lugar de dos** por la razón explicada arriba
  (hit-area bug de `offset`). El handle visible paga el costo del área
  fija de captura con una mejora clara de UX.
- **8dp inset del handle**: lo suficiente para que se lea como "tucked
  into the bezel" sin saturar el borde. Ni sobresale más allá del
  borde (anti-patrón UX) ni queda muy adentro.
- **Pill color = primary** (`#2563EB`): alto contraste vs el fondo
  oscuro, único elemento azul del lado derecho en estado cerrado.
  Cuando el drawer abre, la pill sigue visible sobre el drawer's
  surface — afordance de cierre coherente.
- **`env(safe-area-inset-top, 0px)` con fallback 0px**: explícito
  para devices sin soporte (Chromium pre-69 o WebView antiguos).
- **Gap de +32px** entre status y debug: cubre la altura del texto del
  status (~28px line-height) más 4px de espacio. Robusto ante cambios
  de tamaño de fuente o line-height.

## Pendiente

- **Fase 6 — Separación de capas** sigue pendiente (la siguiente en
  orden). Refinamiento de Fase 5.5 está cerrado.
- **Fase 7 — Polish DS** sigue pendiente (lista en cierre de Fase 4).

---

# Continuación Fase 5.6 — Strip de borde + handle visual (sep 17, 02:10 ART)

Usuario (ya con swipe R→L funcionando) pidió:
1. Abrir con swipe desde **cualquier punto del borde derecho** salvo la
   button bar — la pestañita queda como apoyo visual solamente.
2. Pestañita **completamente pegada** al borde de la pantalla.
3. La pestañita **no debe "volver"**: el follow del 5.5 la movía con el
   drawer durante el drag y al terminar saltaba al borde — se veía roto.

## Qué se hizo

Reescrito `EdgeDrawer.kt` con gestos por capas:

- **Strip invisible** `48dp x (full - bottomBar)` con `draggable` para
  ABRIR. `padding(bottom)` antes de `fillMaxHeight` para excluir la
  button bar del hit area. Encima de `content`, debajo de scrim/drawer.
- **Drawer draggable** para CERRAR (más tap en scrim). Mismo
  `DraggableState` compartido, mismo mapeo `offsetX + delta`.
- **Handle sin pointer modifiers** (pass-through), pill con
  `align(CenterEnd)` → pegada al borde. Estática siempre.
- Fuera `isDragging`/`isAnimating`/`followOffset`.

`MainActivity.kt`: `Box(onSizeChanged)` alrededor de `BottomBar` mide
su altura y la pasa como `edgeStripBottomExclusion`. Nuevos imports:
`onSizeChanged`, `LocalDensity`.

**Corrección teórica**: `Modifier.offset()` SÍ mueve el hit area. El
fallo original era el signo del delta, no hit-testing.

## Verificación

- Build verde, install OK.
- `screen-fase56-closed/final.png` (cerrado), `screen-fase56-open.png`
  (flip temporal, abierto): pill pegada al borde en ambos estados.
- Swipe R→L por adb bloqueado (`INJECT_EVENTS`) en esta ronda — el
  mapeo es idéntico al verificado en 5.5, pero **falta confirmación del
  usuario en device**.

## Pendiente

- Confirmación del usuario: swipe desde zona sin pestañita abre, la
  button bar sigue 100% usable, cerrar arrastrando el drawer funciona.
- **Fase 6 — Separación de capas**, **Fase 7 — Polish DS**.

---

# Continuación Fase 5.7 — Pestañita solidaria con el drawer (sep 17, 02:25 ART)

Usuario (con la pestañita ya pegada al borde, pero estática):

> "no se mueve con la sidebar, revisa todo el movimiento"

## Diagnóstico

La 5.6 dejó la pestañita estática a propósito para evitar el "salto"
del follow mal implementado de la 5.5. Resultado: al abrir, la pill
se quedaba en el borde derecho de la pantalla (encima del contenido
del drawer), no junto al borde guía de la sidebar, y para cerrar había
que buscarla lejos del filo del drawer.

## Qué se hizo

`EdgeDrawer.kt`, handle Box (visual + draggable):

- `offset { IntOffset((offsetX.value - widthPx).coerceAtMost(0f), 0) }`.
  El término `- widthPx` cancela el reposo off-screen del body:
  - cerrado (`offsetX = widthPx`) → offset 0 → pill **pegada al borde
    derecho de la pantalla**;
  - abierto (`offsetX = 0`) → offset `-widthPx` → pill **pegada al
    borde izquierdo (guía) de la sidebar**;
  - durante el drag acompaña al drawer frame-by-frame (mismo `Animatable`).
- `coerceAtMost(0f)`: evita que el overshoot del spring al cerrar saque
  la pill un instante por fuera del borde derecho.
- El handle ahora sí lleva `draggable(dragState, ...)`: como al abrir
  queda sobre el scrim (no sobre el body), sin él no se podría cerrar
  arrastrando la pestañita. Comparte el `DraggableState`, así que no
  hay ambigüedad de gestos.

`MainActivity.kt` sin cambios.

## Verificación

- `./gradlew :app:compileDebugKotlin` ✅, `./gradlew build` ✅.
- Pendiente: confirmación del usuario en device (abrir → pill viaja al
  filo del drawer; cerrar arrastrando la pill funciona).

## Pendiente

- **Fase 6 — Separación de capas**, **Fase 7 — Polish DS**.

---

# Continuación — Defaults de settings + relleno anti-rebote del drawer (sep 17)

## Qué se hizo

### (a) Defaults del modo debug/settings
- `MainActivity.kt`: `debugOn` default `true` → `false`. La grilla (`gridOn`)
  sigue `true` por defecto; `freeCam`/`bg` en `false`.
- `app.js`: `Debug.enabled` default `true` → `false` (coherente con la UI).
- `Debug.render()` ahora también muestra/oculta `#status` ("Avatar listo —
  X frames · presiona Enviar") según debug. El texto dejó de ser permanente:
  solo aparece con debug activo.
- `AGENTS.md`: corregido el gotcha que decía `debug=true`; documentado que
  `#status` es parte del modo debug.

### (b) Hueco del drawer durante el rebote del spring
- **Síntoma**: con el drawer abierto, al arrastrarlo un poco hacia cerrar y
  soltar sin pasar el umbral (`widthPx/2`), el spring de retorno a abierto
  (`DampingRatioMediumBouncy`) sobrepasa `offset 0` hacia negativo. El borde
  derecho del drawer se despega del borde de pantalla y deja ver el WebView.
- **Fix** (`EdgeDrawer.kt`): nuevo parámetro `drawerOverflow = 64.dp`. El
  body ahora mide `drawerWidth + drawerOverflow`, se desplaza en reposo
  `+overflowPx` (el sobrante queda fuera de pantalla) y el contenido visible
  es un `Surface` de `drawerWidth` alineado `CenterStart`; el resto es un
  `Box` opaco color `bar` alineado `CenterEnd`. El overhang cubre el hueco
  mientras el rebote deja el offset negativo (sobrepaso máx. ~16% de 300dp
  ≈ 49dp < 64dp). Ancho visible y animación intactos.
- Descartada la alternativa de clampear el offset a `>= 0` (mataba el rebote).

### (c) Long press del avatar
- `app.js`: el long press que resetea pose + cámara pasa de `1000` a `500` ms
  (`setTimeout`), y se corrigieron los textos stale (comentario decía 5s,
  indicador debug decía `/ 1s`).

## Verificación

- `node --check app.js` ✅, `./gradlew build` ✅ (6s, 99 tareas),
  `./gradlew installDebug` ✅ en Redmi Note 9S.
- Pendiente confirmación del usuario en device (probar rebote y defaults).

## Pendiente

- **Fase 6 — Separación de capas**, **Fase 7 — Polish DS**.

---

# Continuación Fase 6 — Separación de capas (sep 17)

## Qué se hizo

Commit previo (`788fd87`, `feat(ui)`) con todo el trabajo de Fases 1-5.x, y
luego la Fase 6 (separación de capas, sin cambios de comportamiento).

### Capa 1 — Negocio (sin Compose)
- `data/SignHistoryRepository.kt`: interface `load()`/`record(text)` + impl
  `SharedPrefsSignHistoryRepository` (key `signai`/`history`, separador `\n`,
  cap 20, top-5 protegido).
- `domain/SignAiCommands.kt`: `PLAY` + `jsToggle(prop, on)` + setters
  `setDebug/setFreeCamera/setGrid/setBackground`.
- `domain/SignsCatalog.kt`: `available = listOf("0000_0000_0000")`.

### Capa 2 — Presentación
- `ui/state/Settings.kt`: `data class Settings` (debug=false, freeCam=false,
  grid=true, bg=false) + `enum Setting`.
- `ui/webview/SignAiWebViewController.kt`: interface + `WebViewSignAiController`
  (único lugar con `evaluateJavascript` y `RenderEffect`; `attach(webView)`).
- `ui/state/MainScreenState.kt`: `historyList` (`SnapshotStateList`), `settings`,
  `sign`; métodos `play`, `toggle`, `pickSign`, `setBlur`, `attachWebView`.
- `ui/webview/AvatarWebView.kt`: `AndroidView` movido tal cual.

### Capa 3 — UI reusable + screens
- `ui/components/`: `SignAiIconButton`, `SettingRow` (+ `signAiCheckboxColors`),
  `HistoryDropdown`, `SignPickerDropdown`.
- `ui/screens/bottombar/SignAiBottomBar.kt`.
- `ui/screens/settings/SettingsSidebar.kt`.
- (No hay `topbar/SignAiTopBar.kt`: la TopBar se eliminó en Fase 5.)

### Capa 4 — Screens
- `ui/MainScreen.kt`: composición pura (`SignAiEdgeDrawer` + `AvatarWebView` +
  `SignAiBottomBar` + `SettingsSidebar`), solo `showSettings`/`bottomBarHeight`
  locales y `LaunchedEffect(showSettings) { state.setBlur(...) }`.
- `MainActivity.kt`: 27 líneas (imports incluidos). Crea el `MainScreenState`
  y compone `SignAiTheme { MainScreen(state) }`.

## Verificación

- `./gradlew build` ✅ (7s, 99 tareas), `installDebug` ✅.
- `rg` en `app/src/main/java`: `SharedPreferences` solo en `data/`;
  `evaluateJavascript` y `window.SignAI` solo en
  `ui/webview/SignAiWebViewController.kt` / `domain/SignAiCommands.kt`;
  0 hits de `androidx.compose.material3` y `material.icons`; 0 `MaterialTheme`.
- Screenshots: `screen-fase6-main.png` y `screen-fase6-sidebar.png`
  (esta última con flip temporal de `showSettings`, revertido). Paridad total.

## Decisiones

- **`attach(webView)`** en vez de constructor: el WebView nace dentro del
  `AndroidView.factory`, no existe al crear el controller en `onCreate`.
- **`Settings` defaults reales** (`debug=false`): el plan decía `debug=true`
  pero se preserva el comportamiento vigente.
- **`SignsCatalog` object**; `MainScreenState` recibe `signs: List<String>`
  con default `SignsCatalog.available`.

## Pendiente

- **Fase 7 — Polish DS** (única restante de `ui-migration-lumo`): extender
  `SignAiColors` (disabled/secondary/error), centralizar Sizes/Shapes/Spacing,
  migrar los 3 `SignAiSurface(onClick)` a `Lumo.Button` customizado, tipografía
  y animaciones, documentar overrides de Lumo.
- Confirmación del usuario en device de la Fase 6 (layout idéntico, drawer y
  defaults ya validados antes).
