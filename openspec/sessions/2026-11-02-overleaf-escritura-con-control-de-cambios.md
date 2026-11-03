# Overleaf: de solo lectura a escritura con control de cambios

**Fecha:** 2026-11-02, noche · **Rama:** `main`

## Qué llegó

El owner pidió poder auditar cambios en Overleaf antes de que existieran. La respuesta
fue habilitar la escritura **con control de cambios** (`track changes`), que en su cuenta
es una función premium **hasta el 2026-11-09**, y dejar por escrito cómo se usa.

## Qué se hizo

**Configuración global** (`~/.config/opencode/opencode.json`, respaldo en
`opencode.json.bak-2026-11-02`):

| Herramienta | Estado |
|---|---|
| `edit_file`, `find_and_replace`, `compile` | habilitadas |
| `accept_changes`, `reject_changes`, `reply_comment`, `resolve_comment`, `reopen_comment` | siguen denegadas |

Las cinco que quedan bloqueadas son justamente las que borran el rastro de la revisión:
**la sesión propone, el owner acepta o rechaza.**

**Se comprobó que el registro funciona.** La primera comprobación dio `false`, y la razón no
era el MCP: el interruptor estaba prendido en el **SDD** y todavía no en el **SRS**, porque va
por documento. Después de prenderlo en los dos, `track_changes_on_for_me` pasó a `true` en
ambos. La lección: el campo puede quedar viejo si se prende con el proyecto ya abierto, pero
un `false` significa "no escribas todavía"; el registro real se confirma con `tracked: true` y
con `list_tracked_changes`.

**Contextos actualizados:**

- `AGENTS.md`: la sección final ya no dice "de solo lectura". Ahora dice que se escribe con
  permiso, con control de cambios y con reporte obligatorio en tabla (documento, qué cambió,
  línea aproximada, texto puesto).
- `.opencode/skills/overleaf-lectura/SKILL.md`: reescrito. El id se mantiene por historia
  (lo citan `AGENTS.md`, `tres-fuentes-de-verdad` y varias bitácoras), pero el título y la
  descripción ya cubren leer **y** escribir.
- `docs/SISTEMA.md`: la sección de Overleaf dice cómo se escribe y hasta cuándo dura el
  premium; actualizados el MCP (v0.3.7) y las fechas de última edición.

## Primeras ediciones en el SRS

Proyecto `6a122a19bb9a5dd9d390ba3d`, `main.tex`, versión **632 → 637**. Cinco sugerencias,
**las cinco aceptadas por el owner el mismo día**:

| # | Qué | Línea aprox. |
|---|---|---|
| 1 | Borrado el `TODO: completar las animaciones que faltan` de `RF-INT-HW-04` (vencido: las 50 señas están completas desde el 2026-10-22) | 2237 |
| 2 | Agregado el **botón Repetir** a la lista de componentes de la pantalla principal | 766 |
| 3 | Agregado el repetir por turno al panel de conversación | 847 |
| 4 | Reemplazado el `TODO` del tamaño del APK en §2.1.3 por la cifra medida | 1008 |
| 5 | Lo mismo en §2.5, restricciones de memoria | 1251 |

La cifra sale de un `assembleRelease` del 2026-11-02: **35.529.589 bytes** (35,5 MB
decimales, 33,9 MiB), con `juan.glb` y `pauline.glb` verificados dentro del ZIP.

## Lo que queda

- Las cinco sugerencias del SRS quedaron aceptadas; no hay ninguna pendiente en el proyecto.
- **§2.7, línea ~1498**: falta la misma cifra del APK ("espacio suficiente para instalar el
  paquete"). Preguntado; sin respuesta todavía.
- **2026-11-09**: vence el premium del control de cambios. Si no se renueva, no se escribe
  directo: se deja el texto propuesto en un archivo y lo pega el owner (o se reponen los
  `deny` desde el respaldo).
- Los ocho pendientes del SRS que necesitan decisión del owner están listados en el mensaje
  de la sesión: §3.2–3.7 vacías, `RF-UI-06` (modo mientras se graba), expresiones faciales,
  Three.js por CDN, requerimiento del avatar y del Repetir, y §3.3 de desempeño.

## Nota para la próxima sesión

Cada documento tiene su propio interruptor y se prende en el panel **Review** del editor.
Antes de la primera edición de una tanda hay que **preguntar**, aunque el permiso de la
tanda anterior haya sido amplio.
