# El stack de IA del repo: AGENTS.md corto, skills que de verdad son skills, y tres agentes

**Fecha:** 2026-10-17, tarde · **Rama:** `flujo-oraciones` · **Alcance:** solo este
repo. Los repos hermanos quedan reportados, sin tocar.

## Qué llegó

El pedido fue revisar el stack de IA del proyecto y mejorarlo lo máximo posible:
optimizar el `AGENTS.md`, convertir en skills lo que tiene que ser un skill, definir la
convención de las sesiones de `openspec/`, y revisar los skills y los agentes.

## Qué se encontró

**1. `AGENTS.md` tenía 632 líneas y se cargaba entero en cada mensaje.** Un 70 % eran
reglas que aplican siempre y un 30 % era referencia de un área puntual. Ese 30 % se
pagaba en todos los mensajes.

**2. Cuatro archivos le dicen al agente cosas que son falsas.** Esto es lo que más daño
hace, porque un agente que los lee construye el sistema equivocado:

| Dónde | Qué dice | Qué es verdad |
|---|---|---|
| `openspec/specs/pipeline/spec.md:16` | "No hay servidor: la app es 100 % local" | Todo lo contrario: WebSocket, STT y Neon |
| `openspec/config.yaml` | Los huesos **no** usan puntos (`DEF-upper_armL`) | Usan puntos: `upper_arm.L`, `thumb.01.L` |
| `openspec/config.yaml` | Los datos son `sign.json` | Son 32 GLB, desde el 2026-10-13 |
| `openspec/config.yaml` | El puente tiene 7 métodos | Tiene 14, más 3 eventos de vuelta |

`config.yaml` además cita `../app/`, `../appKotlin/`, `../unity/` y
`../sign-ai-gloss-engine`: **ninguno existe**.

**3. Un skill contradecía al `AGENTS.md` de frente.** `compose-best-practices` decía
"Only androidx.compose.material3.* allowed. Mixing = BLOCKER" y "Use
MaterialTheme.colorScheme", cuando el `AGENTS.md` dice que Material 3 no se agrega nunca
y el sistema es Lumo. Hoy el repo tiene **cero** imports de `material3`.

**4. Un claim del propio `AGENTS.md` era falso.** `AGENTS.md:565` decía que los spec
`CTL-6`, `STA-9` y `OM-4` "siguen describiendo el placeholder y están mal". Los tres ya
estaban corregidos (`spec.md:122`, `:250`, `:407`). Un agente que le creyera "arreglaría"
entradas que ya estaban bien.

**5. No había ni un agente definido**, ni acá ni en los hermanos.

**6. Las 6 carpetas de `openspec/changes/` estaban viejas.** Dos son de la estrategia
muerta de septiembre; y `integracion-backend/tasks.md:76` decía que el catálogo estaba
vacío y que "era lo único que bloquea", cuando se sembró el 2026-10-14.

**7. `docs/CONTRASTE-DOCUMENTOS.md` (548 líneas) estaba huérfano**: nadie lo referenciaba
desde el `AGENTS.md` ni desde el índice de sesiones, y no tenía log de sesión.

## Decisiones del owner

| # | Decisión |
|---|---|
| 1 | Alcance: **solo este repo, estructura**. Los cuatro archivos envenenados y los hermanos quedan reportados para otra sesión. |
| 2 | `compose-best-practices`: **reescribirlo como Lumo**, en vez de dejar de invocarlo o borrarlo. |
| 3 | `openspec/changes/`: **archivarlas** y corregir la línea mentirosa. |
| 4 | Los hermanos: **solo el stack de este repo**. No tocar `../sign-ai-backend`, `../sign-ai-stt` ni `../sign-ai-docs`. |

## Qué se hizo

### `AGENTS.md`: 632 → 386 líneas

La referencia profunda salió a dos archivos que se cargan **solo cuando la tarea los
pide**:

- **`docs/SISTEMA.md`** (168 líneas) — detalle de cada repo hermano, el `CLAUDE.md` del
  backend, el protocolo del STT, las carpetas hermanas que no son repositorios, las IDs
  de Overleaf, y el contrato entre repos desarrollado.
- **`docs/MODO-EN-VIVO.md`** (133 líneas) — el modo en vivo: cómo funciona, los dos
  interruptores del micrófono, y los gotchas con su porqué.

En `AGENTS.md` quedó: la forma de hablarle al owner, las tres fuentes de verdad, la
prohibición de escribir en Overleaf, el mapa, los cuatro invariantes del contrato, las
seis reglas entre repos, build y verificación, observabilidad, git, UI, el puente, los
gotchas, los assets web, Neon, y el contexto de OpenSpec.

Se agregaró al final una tabla nueva —**§ Índice: qué cargar y cuándo**— que es lo que
hace que la carga diferida funcione: sin ella, los archivos nuevos serían texto que
nadie encuentra.

También se corrigió el claim falso del `:565` (punto 4 del hallazgo): los tres spec
están corregidos, así que la advertencia se borró en vez de actualizarse.

### Cuatro skills nuevos

| Skill | Por qué es un skill y no una sección |
|---|---|
| **`contrato-websocket`** | Es un **procedimiento**: leer los dos lados, cambiar de forma aditiva, y cerrar diciendo qué queda pendiente del otro lado. Se dispara por tarea, no siempre. |
| **`tres-fuentes-de-verdad`** | Es el flujo de revisión de las tres fuentes y qué hacer cuando no alinean, más la tabla de señales para determinar cuál de los dos está mal. |
| **`sesion-openspec`** | La convención de la bitácora con su plantilla y la regla de mantener el índice. Es lo que hace que "cada sesión deja rastro" sea algo que se cumple y no una intención. |
| **`overleaf-lectura`** | La prohibición queda siempre en `AGENTS.md`; el **mecanismo** (las ocho `policies`, por qué va en `policies` y no en `permissions`, cómo se renueva la cookie) es una auditoría bajo demanda. |

### `compose-best-practices`: reescrito como Lumo

El `SKILL.md` ahora es el checklist de Compose/Lumo/Phosphor del proyecto: cero Material
3, tokens de `foundation/` y `Color.kt`, estado en `MainScreenState`, las dos trampas de
layout (el WebView se come los toques, los drawers son hermanos), y el cierre obligatorio
de verificar en device en los dos modos.

- `references/design_tokens.md` reescrito con los valores reales y los ratios de contraste.
- `references/review_checklist.md` reescrito: la lista para dar una UI por terminada.
- **Borradas tres referencias** que empujaban en la dirección contraria:
  `material_migration.md` (el proyecto nunca usó Material 3, no hay nada que migrar),
  `adaptive_layouts.md` (manda `androidx.compose.material3.adaptive`; la app es
  portrait-locked de teléfono), y `shared_element_transitions.md` (pide un `NavHost`; la
  app no tiene navegación).
- Se dejaron las cinco que sí aplican: estabilidad del compiler, budgets, ejemplos,
  enterprise mode.

Los skills de accesibilidad y de performance no mandaban Material 3, así que se
conservaron; lo que se cambió fue el frontmatter (`user-invocable` es de Claude Code,
opencode usa `metadata: opencode/slash`) y se les agregó lo específico del proyecto: el
presupuesto de GPU del WebView, el aviso de que el canvas 3D no existe para
accesibilidad, y que el avatar sí está en `app.js`, no en Kotlin.

### Tres agentes nuevos

| Agente | Para qué | Permisos que importan |
|---|---|---|
| **`@pipeline`** | Cambios que cruzan el contrato | `bash` denegado salvo `git`/`grep`; **no commitea en hermanos** |
| **`@ui`** | Cambios de interfaz en Compose | `edit` acotado a `app/src/main/**`; `./gradlew` y `tools/dev.sh` permitidos |
| **`@contraste`** | Auditoría código ↔ documentos | **`edit: deny`** — no escribe nada, el informe es el entregable |

### `openspec/changes/`: las 6 archivadas

Movidas a `openspec/changes/archive/` con prefijo de fecha, más un `README.md` del
archivo que dice **por qué** se archivó cada una y por qué las dos obsoletas no se
borran (para no perder el registro de que ese trabajo existió).

Antes de archivar se corrigieron las dos líneas que mentían en
`integracion-backend/tasks.md`:

- **0.1** decía que el catálogo estaba vacío y que "era lo único que bloqueaba" →
  hecho el 2026-10-14 con `tools/seed_catalog.py --apply`, con el log de sesión.
- **Cerrar la frase al soltar el micrófono** citaba la rama
  `feat/android-client-support-v2` del backend, **que no existe** → hecho y mergeado el
  2026-10-15.

Los 18 enlaces a esas carpetas en 10 archivos se actualizaron al camino nuevo y se
verificaron uno por uno: **ninguno quedó roto**.

## Segunda vuelta: los archivos envenenados, arreglados

El owner pidió arreglar los dos que se habían reportado, "también en el Legend", y agregar
una regla explícita sobre los permisos de Overleaf.

### Los nombres de huesos eran falsos en las dos direcciones

Al verificar en vez de transcribir apareció algo más grande que lo reportado. Se comprobó
contra `models/avatar.glb` **y contra el export original de Cascadeur**
(`GLB Senas/0000 - GRACIAS.glb`), leyendo las articulaciones de skin con `tools/glb_pose.py`:

- **102 articulaciones de skin** en los dos archivos.
- La cadena del brazo usa **punto y sin prefijo**: `shoulder.L`, `upper_arm.L`,
  `forearm.L`, `hand.L`.
- Dedos y palma: `thumb.01.L`, `f_index.01.L`, `palm.01.L` … `palm.04.L`.
- **Cero huesos con prefijo `DEF-`.** Ni en el GLB del APK, ni en el export de origen.

Es decir: la lista `DEF-browTL` / `DEF-lidTL` / `DEF-lipTL` / `DEF-jaw_master` / `DEF-eyeL`
que aparecía en **cuatro** archivos **no existe en este rig**. Estaba en `config.yaml`, en
`specs/pipeline/spec.md` y en `specs/avatar/spec.md`. Los tres están corregidos y ahora
dicen explícitamente que esa lista es falsa, para que nadie la vuelva a copiar.

**Y la cara no la anima la app.** Los nodos de cara que sí existen usan nombres al estilo
ARKit (`brow_dwn_L`, `brow_outer_up_L`, `eye_blink_open_L`, `jaw_open_lt_rt_frwd`,
`eyeball.lookat.L`) pero **no son articulaciones de skin**, el GLB que va en el APK tiene
**cero morph targets**, y `app.js` no menciona morph targets ni ninguno de esos nombres.
La expresión facial viene **horneada en el clip**.

Eso confirma el punto **A6** de `docs/CONTRASTE-DOCUMENTOS.md`, que ya decía "nada se anima
en código" y lo tenía como **en pausa** (RF-AVT-03). Y obliga a matizar un "bug conocido":
la **ceja derecha casi estática** es un síntoma real, pero la causa que se le atribuía —los
pesos de `DEF-browTL` contra `DEF-browTR`— **no se puede verificar**, porque esos huesos no
existen. Ahora está anotado como causa no confirmada en vez de como hecho.

### Qué se reescribió

- **`openspec/specs/pipeline/spec.md`** — entero, desde cero. La versión anterior decía "No
  hay servidor: la app es 100 % local", documentaba el formato de `sign.json` como "formato
  real (verificado del archivo)", y listaba seis constantes del motor procedural que ya no
  existen. Ahora separa **flujo de assets** (Cascadeur → `split_signs.py` → el APK) de
  **flujo de runtime** (mic → WS → backend → STT → oraciones → glosas → cola → escena), con
  los cuatro invariantes del contrato, el puente completo y el caso de las 18 glosas sin
  animación.
- **`openspec/config.yaml`** — el bloque `context` entero y las secciones `rules` y
  `operations`. Se fueron: el nombre de carpeta viejo (`webview/`), las cuatro rutas que no
  existen (`../app/`, `../appKotlin/`, `../unity/`, `../sign-ai-gloss-engine`), `sign.json`,
  el puente con 7 de 14 métodos, "Branding ABIERTO" (es SignALL), y el tema default
  invertido. Las seis constantes procedurales quedan listadas como **inexistentes**, porque
  saber que no están es lo que evita buscarlas.
- **`openspec/specs/avatar/spec.md`** — la lista de huesos y el bug de la ceja.
- **`openspec/specs/ui/spec.md`** — la rama `feat/prototipo-ui-vercel` que figuraba "pendiente
  de merge" cuando ya está mergeada; el default del tema (decía **claro**, y
  `Settings.kt:26` tiene `darkTheme = true`); el encabezado con la rama muerta
  `tiempo-real`; y `GLS-5`, que decía que las 18 glosas "**firman**" — la palabra que la
  propia regla del `AGENTS.md` prohíbe. La leyenda de origen pasó a ser una lista con la
  fecha de verificación y una nota sobre por qué una fila que miente sobre el estado hace
  que un agente rehaga trabajo terminado.

### La regla de Overleaf

Al final del `AGENTS.md`, con encabezado propio y en bloque de cita, va
**§ EL OVERLEAF ES DE SOLO LECTURA. NUNCA SE ESCRIBE EN ÉL.** Está al final a propósito:
es lo último que se lee y lo último que se olvida. Tiene cinco partes:

1. **Nunca escribir**, con la lista de lo que *no* cuenta como autorización (que la tarea
   lo requiera, que sea obviamente correcto, que sea "una tilde", que otro documento diga
   otra cosa, que se haya hecho antes).
2. **Nunca cambiar los permisos que bloquean la escritura.** Esta es la parte que pidió el
   owner. Tabla con lo prohibido: quitar un `deny`, moverlos de `policies` a `permissions`,
   agregar un `allow` que los tape, editar `~/.config/opencode/opencode.json` por
   cualquier motivo, editar el `opencode.json` de un repo, o meter un MCP o script que
   esquive el bloqueo. **Ese archivo no se edita en ninguna circunstancia.**
3. **Si el bloqueo parece roto, se reporta, no se arregla** — y nunca se "prueba" una
   herramienta de escritura para ver si funciona.
4. **Por qué no es opcional**: el bloqueo lo impone opencode, no la credencial. La cookie sí
   puede escribir, así que un agente que pueda editar la config **puede quitarse el bloqueo
   a sí mismo**, y eso está prohibido aunque la escritura esté autorizada.
5. **Lo que sí se puede hacer**: las nueve herramientas de lectura.

La misma prohibición se repitió al final del skill `overleaf-lectura`, porque ese es
justo el archivo que alguien cargaría si quisiera tocar el bloqueo: **cargarlo no autoriza a
cambiar nada.**

**Verificado: `~/.config/opencode/opencode.json` sigue intacto, con las ocho `policies` en
`deny`.** No se tocó en ninguna de las dos vueltas.

## Qué quedó pendiente

Esto es lo que se encontró y **no se tocó**, por la decisión de alcance:

| # | Pendiente | Por qué importa |
|---|---|---|
| **4** | **`docs/CAMBIOS.md` §7 (`:167`, `:186`) contradice su propia sección del 2026-10-16** (`:283`): §7 dice que el adelanto automático "se queda" y que `CATCHUP_BACKLOG = 2` no se tocó, cuando D-5 lo eliminó. Los pendientes #3 y #6 de la lista están duplicados, y el #5 está a medio hacer. | Quien lea §7 después de §D-5 se queda con la respuesta equivocada. |
| **5** | **`tools/` tiene tres scripts sin consumidor**: `build_signs.py`, `patch_webview_glue.py` y `splice_movement.py`, todos del pipeline de landmarks ya muerto. `tools/backup/` tiene un binario y un `sign.json` sin referenciar. | `tools/README.md:111-125` ya los marca como viejos, pero los archivos siguen ahí y el `README` no menciona `backup/`. |
| **6** | **`../sign-ai-stt` no tiene archivo de agente**: solo `README.md`. Un agente que vaya a tocar el protocolo del STT no tiene ni una regla de git. | |
| **7** | **`../sign-ai-docs` no tiene ni README.** Una sesión que entre ahí no sabe qué es cada carpeta ni que los `.drawio` los edita el owner. | |
| **8** | `../sign-ai-backend` tiene `CLAUDE.md` + 2 skills en `.claude/skills/`. opencode lee las dos cosas (CLAUDE.md como fallback y `.claude/skills/`), así que **está bien para opencode** — pero si el owner usa Claude Code, `CLAUDE.md` funciona y las skills también. Nada que hacer. | Sin acción. |

## Verificación

- **No se tocó ni una línea de Kotlin ni de JavaScript.** Los cambios son de `AGENTS.md`,
  skills, agentes, `docs/`, `openspec/` y dos referencias de accesibilidad. Por eso
  **no se corrió `./gradlew installDebug`**: no hay APK que cambiar.
- Los 18 enlaces renombrados se resolvieron uno por uno contra el disco, y **todos** los
  enlaces relativos de `AGENTS.md`, los skills, los agentes y los dos `docs/` nuevos.
- Los ocho skills quedaron con frontmatter válido, `name` coincidente con la carpeta y
  `description` de 222 a 492 caracteres.
- El índice de sesiones quedó verificado contra el disco: **22 entradas, 22 archivos,
  cero diferencia**.