---
name: overleaf-lectura
description: Revisar los documentos de la tesis en Overleaf (SRS, SDD, Memoria, PMP, PPP) sin escribir nunca, y saber qué hacer cuando un documento está mal. Usar cuando haya que consultar el Overleaf de la tesis, o cuando una decisión técnica tenga que contrastarse contra el SDD o el SRS.
---

# Overleaf: revisión y solo lectura

**De Overleaf se lee, nunca se escribe.** Un `.tex`, un comentario, una sugerencia, un cambio
aceptado o rechazado, y también `compile`: nada, salvo que el owner lo diga **explícitamente
en un mensaje**. No alcanza con que la tarea parezca necesitarlo, ni con que el cambio sea
"solo corregir una tilde".

Si al final del trabajo queda pendiente corregir un `.tex`, **eso se le dice al owner, no se
hace**.

## Leer no necesita permiso ni explicación

Las nueve herramientas usables, todas de lectura o listado:

`ping` · `list_projects` · `open_project` · `list_files` · `read_file` · `read_log` ·
`list_comments` · `read_comment_thread` · `list_tracked_changes`

Las otras ocho —`edit_file`, `find_and_replace`, `accept_changes`, `reject_changes`,
`reply_comment`, `resolve_comment`, `reopen_comment`, `compile`— están denegadas en
`~/.config/opencode/opencode.json`. **No es algo que haya que verificar**: el bloqueo está
puesto y no cambia la forma de trabajar. Ir a leer la tesis es una operación normal, y no
tiene que ir acompañada de ningún aviso sobre el estado del bloqueo.

Un aviso sí es útil, porque es lo único que molesta sin que nadie lo pida: **el MCP abre una
ventana de Chromium con perfil aislado cuando la cookie venció** (duran ~5 días), y hay que
avisar antes de arrancar la revisión para que no aparezca a mitad de una implementación.

## Tocar la configuración global

Vive en `~/.config/opencode/opencode.json`. Editarla **es posible**, nunca por iniciativa
propia.

| Cambio | Lo que hace falta |
|---|---|
| Editar la config global por otra razón | Confirmación explícita del owner, en un mensaje |
| Quitar, editar o mover un `deny` de Overleaf | Confirmación **que hable de Overleaf** |
| Agregar un `allow` que tape un `deny` | Confirmación **que hable de Overleaf** |
| Levantar los `deny` desde el `opencode.json` de un repo | No sirve: las `policies` son globales |

Un "arreglá lo que necesites en la config" **no** habilita tocar un `deny` de Overleaf. Y al
revés: **que te autoricen a tocar el bloqueo no te autoriza a escribir.** Son dos permisos
distintos. Sin confirmación, **se propone el cambio exacto y se espera.**

Agregar un MCP, un plugin o un script que escriba en Overleaf por otra vía está prohibido en
cualquier nivel: no es una solución, es escribir por la puerta de atrás.

## Dos detalles del mecanismo

Solo si alguien está por tocar la config, y no como tarea:

1. **Tiene que estar en `policies`, no en `permissions`.** Cada agente de la config global
   termina con `{"action": "*", "resource": "*", "effect": "allow"}`, y las reglas de agente
   se aplican **después** de las globales, ganando la última que coincide: un `deny` en
   `permissions` lo pierde ese comodín en silencio.
2. **La cookie tiene permiso de escritura igual.** El bloqueo lo impone opencode, no la
   credencial. Dentro de opencode no hay forma de escribir; fuera de él, sí. Por eso la regla
   se sostiene en que el owner la confirme, y no solo en que la herramienta esté denegada.

## Cómo se renueva la sesión

- La cookie vive en `~/.config/overleaf-mcp/cookie.json` (modo 600), dura **~5 días**.
- MCP: `@netique/overleaf-mcp` v0.3.6, registrado **global** con
  `OL_BROWSER=/usr/bin/chromium`. Overleaf no tiene API pública — este MCP habla los
  endpoints internos del editor web — así que en plan gratis **no hay alternativa** si se
  rompe: se actualiza el paquete o se baja el ZIP a mano.

Los IDs de los cinco proyectos y las fechas de su última edición están en
[`docs/SISTEMA.md`](../../../docs/SISTEMA.md).

## Qué hacer cuando el documento de Overleaf está mal

**Lo corrige el owner, no la sesión.** Si el código y el Overleaf no coinciden, el `.tex` no
se toca. Lo que sí puede hacer una sesión es:

1. Anotar la divergencia en `../sign-ai-docs/modificaciones/requerimientos.md`, o
2. Dejarla escrita y con fecha en el log de sesión (`openspec/sessions/`).

Y decirlo en el mensaje, con las dos versiones y sus fechas.
