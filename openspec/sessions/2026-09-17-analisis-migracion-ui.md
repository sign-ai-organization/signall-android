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
