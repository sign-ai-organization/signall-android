---
name: overleaf-lectura
description: Leer los documentos de la tesis en Overleaf (SRS, SDD, Memoria, PMP, PPP, plan de pruebas) y proponer cambios con control de cambios, pidiendo permiso antes de escribir. Usar cuando haya que consultar el Overleaf, contrastar una decisión contra el SRS o el SDD, o corregir un .tex con revisión del owner.
---

# Overleaf: leer libre, escribir con permiso y con control de cambios

**Leer es libre y no se explica. Escribir se pide, se registra y se reporta.**

El id de este skill quedó como `overleaf-lectura` por historia (lo citan `AGENTS.md`,
`tres-fuentes-de-verdad` y varias bitácoras); el contenido ya no es solo de lectura.

## Leer no necesita permiso

Nueve herramientas de lectura y listado: `ping` · `list_projects` · `open_project` ·
`list_files` · `read_file` · `read_log` · `list_comments` · `read_comment_thread` ·
`list_tracked_changes`.

Un aviso sí es útil, porque es lo único que molesta sin que nadie lo pida: **el MCP abre una
ventana de Chromium con perfil aislado cuando la cookie venció** (duran ~5 días), y hay que
avisar antes de arrancar la revisión para que no aparezca a mitad de una implementación.

## Escribir: qué está habilitado y qué no

Desde el **2026-11-02** el owner levantó tres permisos en la config global y dejó el resto
denegado a propósito:

| Permitido | Denegado (no se puede tocar lo que el owner audita) |
|---|---|
| `edit_file` | `accept_changes` |
| `find_and_replace` | `reject_changes` |
| `compile` | `reply_comment` · `resolve_comment` · `reopen_comment` |

**La sesión propone; el owner acepta o rechaza.** Aceptar un cambio —propio o ajeno— borra el
rastro de la revisión, y por eso está bloqueado. `compile` está permitido pero cuesta tiempo:
no se corre por reflejo, solo cuando hay que comprobar que el `.tex` sigue compilando.

## Las tres condiciones de cada tanda

1. **Permiso antes de escribir.** Que esté habilitado no es permiso permanente: el owner lo da
   en un mensaje y cubre esa tanda. La tanda siguiente se vuelve a preguntar. Un "hacelo" de
   ayer no alcanza para hoy.
2. **Todo cambio tiene que quedar registrado.** El control de cambios (`track changes`) va
   **por documento** y **por cuenta**, y en la cuenta de la tesis es **premium hasta el
   2026-11-09**. Antes de la primera edición se comprueba que el documento lo tenga prendido;
   si no, se para y se avisa.
3. **Reporte en tabla, siempre el mismo formato:**

   | # | Qué cambié | Línea aprox. | Texto que puse |
   |---|---|---|---|
   | 1 | … | **766** | `…` |

   Y encima de la tabla, el documento con su id y su versión. Sin esto el owner no puede
   auditar.

## Cómo se comprueba que el cambio quedó registrado

- La respuesta de `find_and_replace` / `edit_file` trae **`tracked: true`** y
  `track_mode: "on"`, además de `version_before` y `version_after`. **Si trae
  `tracked: false`, la edición entró directo**: se avisa en el momento.
- `list_tracked_changes` muestra las marcas pendientes con autor y hora. Es la prueba que se
  le enseña al owner.
- **No confiar en `track_changes_on_for_me`**: el 2026-11-02 devolvió `false` mientras las
  escrituras sí quedaban registradas. Ese campo del `open_project` es engañoso; la verdad está
  en `tracked` y en `list_tracked_changes`.

## Cuidados al editar

- **`find_and_replace` calcula un diff mínimo**, así que un reemplazo puede aparecer como una
  inserción más una eliminación chiquita en vez de una sustitución limpia. No es un error,
  pero hay que avisarlo para que el owner no lo lea como dos cambios.
- Una edición por lugar, con el `old_string` exacto (copiado de `read_file`, con su
  indentación). Si hay más de una coincidencia, se acota el texto.
- Releer después de escribir: `read_file` devuelve el texto con los cambios aplicados, y las
  líneas se corren. Los números de línea del reporte son **después** de la tanda.

## Cuando el documento y el código no coinciden

Sigue mandando la regla de las tres fuentes: **cuál de los dos está mal lo decide el owner**,
no la sesión. Lo que cambió es lo que se puede hacer con eso:

- Corregir una redacción o completar un dato medido: se pide permiso y se deja como cambio
  registrado.
- Resolver una divergencia (el documento pide algo que la app decidió no hacer, o al revés):
  **primero se pregunta**. Después de la decisión, el texto se propone igual que cualquier
  otro cambio.
- Lo que queda pendiente se anota en `../sign-ai-docs/modificaciones/requerimientos.md` o en
  el log de sesión.

## Tocar la configuración global

Vive en `~/.config/opencode/opencode.json`. Editarla **es posible**, nunca por iniciativa
propia.

| Cambio | Lo que hace falta |
|---|---|
| Editar la config global por otra razón | Confirmación explícita del owner, en un mensaje |
| Quitar, editar o mover un `deny` de Overleaf | Confirmación **que hable de Overleaf** |
| Agregar un `allow` que tape un `deny` | Confirmación **que hable de Overleaf** |
| Levantar los `deny` desde el `opencode.json` de un repo | No sirve: las `policies` son globales |

Un "arreglá lo que necesites en la config" **no** habilita tocar un `deny` de Overleaf.

Agregar un MCP, un plugin o un script que escriba en Overleaf por otra vía está prohibido en
cualquier nivel: no es una solución, es escribir por la puerta de atrás.

### El estado actual, y cómo se revierte

El 2026-11-02 se levantaron `edit_file`, `find_and_replace` y `compile`; quedan cinco `deny`.
Antes de tocar el archivo se dejó un respaldo en
`~/.config/opencode/opencode.json.bak-2026-11-02`. **El 2026-11-09 vence el premium del
control de cambios**: si no se renueva, las escrituras dejarían de quedar registradas y hay
que volver al camino de propuestas en texto (o reponer los `deny` desde el respaldo).

## Dos detalles del mecanismo

Solo si alguien está por tocar la config, y no como tarea:

1. **Tiene que estar en `policies`, no en `permissions`.** Cada agente de la config global
   termina con `{"action": "*", "resource": "*", "effect": "allow"}`, y las reglas de agente
   se aplican **después** de las globales, ganando la última que coincide: un `deny` en
   `permissions` lo pierde ese comodín en silencio.
2. **La cookie tiene permiso de escritura igual.** El bloqueo lo impone opencode, no la
   credencial. Por eso la regla se sostiene en el permiso del owner y en el control de
   cambios, no solo en que la herramienta esté denegada.

## Cómo se renueva la sesión

- La cookie vive en `~/.config/overleaf-mcp/cookie.json` (modo 600), dura **~5 días**.
- MCP: `@netique/overleaf-mcp` (v0.3.7 al 2026-11-02), registrado **global** con
  `OL_BROWSER=/usr/bin/chromium`. Overleaf no tiene API pública — este MCP habla los
  endpoints internos del editor web — así que si se rompe: se actualiza el paquete o se baja
  el ZIP a mano.

Los IDs de los proyectos y las fechas de su última edición están en
[`docs/SISTEMA.md`](../../../docs/SISTEMA.md).
