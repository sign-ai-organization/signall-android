# `changes/` archivados

Trabajo que **se terminó** o que quedó atrás. Nada acá es trabajo pendiente: si algo de
esto hay que retomarlo, es porque el owner lo pide explícitamente.

Un plan activo vive en `openspec/changes/<nombre>/`. Cuando se termina, se mueve acá con
el prefijo de la fecha de cierre.

## Qué se archivó y por qué

| Carpeta | Estado al archivar | Por qué |
|---|---|---|
| `2026-10-17-prototipo-ui-vercel` | 33/33 tareas tildadas | Las 7 fases (A–G) están implementadas y verificadas en device. La rama `feat/prototipo-ui-vercel` ya está mergeada. |
| `2026-10-17-reposo-y-crossfade` | 31/31 tareas tildadas | La pose de descanso horneada y la fundición de 100 ms entre señas están en `specs/avatar/spec.md`, que es donde vive el resultado. La investigación quedó en `investigacion.md`. |
| `2026-10-17-ui-migration-lumo` | Completada; las cajas de Fase 0 quedaron sin tildar | La migración a Lumo está hecha: `grep -rl material3 app/src/main/java` da **cero**. Dos tareas quedan genuinamente abiertas y están anotadas como tales en el archivo. |
| `2026-10-17-integracion-backend` | Completada; dos tareas mentían sobre el estado | La 0.1 decía que el catálogo estaba vacío y que "era lo único que bloquea": se sembró el 2026-10-14. La de cerrar la frase citaba una rama del backend que ya no existe. Ambas corregidas antes de archivar. |
| `2026-10-17-avatar-fixes-y-mejoras` | **Obsoleta** | Todas sus tareas apuntan al enfoque abandonado de septiembre: Blender, `blender/PruebaBlender2.blend`, y verificación en Flutter/Filament. Es historia, no plan. |
| `2026-10-17-mejoras-animacion-manos` | **Obsoleta** | Todas sus tareas apuntan al motor procedural que ya no existe (`mpToThree`, `server.py` de Flask, `state.palmNormalL/R`). De septiembre. Es historia, no plan. |

## Por qué se archivaron las dos obsoletas y no se borraron

Los dos cambios de septiembre (`avatar-fixes-y-mejoras` y `mejoras-animacion-manos`) apuntan
al pipeline de landmarks que fue reemplazado por los GLB el 2026-10-13. Borrados, se pierde
el registro de que ese trabajo existió y de por qué se abandonó. Archivados con la fecha y
esta nota, un agente que los encuentre sabe que son historia en una línea.

## La trampa que estas carpetas eran

Un agente que las leyera como trabajo activo Cometía dos errores:

- **`integracion-backend` decía que faltaba el catálogo** y que "era lo único que
  bloqueaba" — cuando el catálogo ya tenía las 50 glosas desde el 2026-10-14.
- **Las dos obsoletas proponían tocar código que no existe** (`signai-avatar/app/static`,
  `blender/`, el motor procedural de `app.js`).

Por eso también hay una advertencia en `AGENTS.md` § Contexto del proyecto: no todos los
specs están al día.