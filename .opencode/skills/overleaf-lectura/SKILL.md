---
name: overleaf-lectura
description: Revisar los documentos de la tesis en Overleaf (SRS, SDD, Memoria, PMP, PPP) sin escribir nunca, y entender cómo el bloqueo de solo lectura está puesto y cómo verificar que sigue puesto. Usar cuando haya que consultar el Overleaf de la tesis, cuando una decisión técnica tenga que contrastarse contra el SDD o el SRS, o cuando haya que confirmar que el MCP de Overleaf sigue bloqueado para escritura.
---

# Overleaf: revisión y solo lectura

**La prohibición es la regla, y no se negocia: de Overleaf se lee, nunca se escribe.**
Un `.tex`, un comentario, una sugerencia, un cambio aceptado o rechazado: nada, salvo
que el owner lo diga **explícitamente en un mensaje**. No alcanza con que la tarea
parezca necesitarlo, ni con que el cambio sea "solo corregir una tilde".

Si al final de tu trabajo queda pendiente corregir un `.tex`, **eso se le dice al
owner, no se hace**.

## Verificar el bloqueo antes de confiar en él

El bloqueo está en `policies` de `~/.config/opencode/opencode.json`, no en este repo.
Son **ocho** denegaciones, sobre 17 herramientas en total:

```
overleaf_edit_file          overleaf_reply_comment
overleaf_find_and_replace   overleaf_resolve_comment
overleaf_accept_changes     overleaf_reopen_comment
overleaf_reject_changes     overleaf_compile
```

`compile` **también** está denegado (decisión del owner el 2026-10-17): no se compila
desde acá, ni aunque no se escriba sobre el `.tex`. El error de compilación se lee en el
Overleaf del owner. Quedan 9 herramientas usables y **todas son de lectura o listado**:

`ping` · `list_projects` · `open_project` · `list_files` · `read_file` · `read_log` ·
`list_comments` · `read_comment_thread` · `list_tracked_changes`

**Comprobación rápida**: si `overleaf_edit_file` aparece entre las herramientas
disponibles, algo cambió la config global y hay que avisarle al owner antes de seguir.

## Dos trampas al tocar esa config

1. **Tiene que ser en `policies`, no en `permissions`.** Cada agente de la config global
   termina con `{"action": "*", "resource": "*", "effect": "allow"}`, y las reglas de
   agente se aplican **después** de las globales, ganando la última que coincide: un
   `deny` en `permissions` lo pierde ese comodín en silencio.
2. **`policies` global no lo puede levantar el `opencode.json` de ningún repo.** Si
   aparece `overleaf_edit_file` entre las herramientas, algo cambió la config.

## El límite honesto

**El bloqueo lo impone opencode, no la credencial.** La cookie de sesión sigue teniendo
permiso de escritura sobre toda la cuenta, y Overleaf en plan gratis no da tokens con
alcance limitado. Dentro de opencode no hay forma de escribir; **fuera de él, sí**. Por
eso la regla se sostiene en que el owner la confirme, no solo en que la herramienta
esté denegada.

## Cómo se renueva la sesión

- La cookie vive en `~/.config/overleaf-mcp/cookie.json` (modo 600), dura **~5 días**.
- Cuando venció, la próxima llamada **abre una ventana de Chromium con perfil aislado**
  para que el owner se loguee, y se cierra sola. Ese perfil **no** comparte datos del
  navegador principal ni el gestor de contraseñas: el owner tiene que escribir sus
  credenciales a mano.
- **Avisá antes de Triggerar una revisión de Overleaf**: esa ventana puede aparecer y
  rompe el flujo. No hay que hacerlo por sorpresa a mitad de una implementación.
- MCP: `@netique/overleaf-mcp` v0.3.6, registrado **global** con
  `OL_BROWSER=/usr/bin/chromium`. Overleaf no tiene API pública — este MCP habla los
  endpoints internos del editor web — así que en plan gratis **no hay alternativa** si se
  rompe: se actualiza el paquete o se baja el ZIP a mano.

Los IDs de los cinco proyectos y las fechas de su última edición están en
[`docs/SISTEMA.md`](../../../docs/SISTEMA.md).

## Qué hacer cuando el documento de Overleaf está mal

**Lo corrige el owner, no la sesión.** Si el código y el Overleaf no coinciden, el
`.tex` no se toca. Lo que sí puede hacer una sesión es:

1. Anotar la divergencia en `../sign-ai-docs/modificaciones/requerimientos.md`, o
2. Dejarla escrita y con fecha en el log de sesión (`openspec/sessions/`).

Y decirlo en el mensaje, con las dos versiones y sus fechas.