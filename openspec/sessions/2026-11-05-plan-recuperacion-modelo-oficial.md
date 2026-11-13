# Plan de recuperación con el modelo oficial y ronda de evaluación

**Fecha:** 2026-11-05 · **Rama:** `main` · **Repos tocados:** este (solo
`docs/plan-pruebas/` y esta bitácora), `../signall-backend` y `../signall-docs`.
La app y el STT no se tocaron.

## Qué llegó

La ronda diagnóstica ya estaba cerrada con **74 de 74** filas, pero se había corrido
con **Ollama local `qwen3:4b-instruct`**, no con el modelo oficial `gpt-4o-mini`. El
owner pidió planear —sin ejecutar— la recuperación de las pruebas contaminadas, los
arreglos y la ronda de evaluación, y dejar el modelo local disponible pero apagado por
defecto.

## Qué se hizo

1. Se leyó la ronda completa (`ANALISIS`, `PRUEBAS`, el libro y las 261 evidencias) y se
   reconstruyó el mecanismo de contaminación: `.env.local` gana sobre `.env`, compose lo
   inyecta si existe y `docker-compose.host.yml` vuelve a forzar el Ollama local.
2. Se escribió **`docs/plan-pruebas/PLAN-2026-11-05-recuperacion-y-ronda-oficial.md`**:
   criterio C1–C5, **24 pruebas a repetir**, **34 válidas** con su justificación y **16
   bloqueadas**; fase 1 (recuperación), fase 2 (arreglos P0/P1/P2) y fase 3 (ronda de
   evaluación con cinco carriles de subagentes y reglas contra los loops).
3. Se revisó el diff del backend: **219 líneas en 12 archivos más 6 nuevos**, todas
   alineadas con el SDD. Se commiteó en `8f27bf8` y se pusheó a `main` y `develop`.
4. Se corrigió el SRS (`../signall-docs`, `72070e3`): se quitó la sección
   «Disponibilidad» duplicada —estaba bajo «Requerimientos de Desempeño» y bajo
   «Atributos del Sistema»— y el peso del APK pasó de 78,19 MB a **33,9 MB release /
   39,0 MB debug**, medidos. `make srs` compila y no quedan etiquetas duplicadas.
5. Se verificó el **MCP de Neon** con tres `SELECT`: proyecto `bold-shape-68007559`,
   catálogo **50**, caché **68**, y las nueve filas de FR-11 presentes. No se modificó
   nada.

## Decisiones cerradas

| Tema | Quedó |
|---|---|
| Backend | Commiteado en `main` y `develop` (`8f27bf8`) |
| Modelo grande de `PR-LLM-01` | `gpt-4o` |
| Cola del avatar | Encolar, con tope de 2 frases o ~20 s y aviso al descartar |
| Texto fuera de glosa | Criterio estricto; se arregla con post-proceso, sin catálogo en el prompt |
| Umbral de la caché | 0,03 |
| Barrera de no-voz | En el STT |
| `PR-INT-03` | Cirugía del catálogo aprobada, camino real, con respaldo y Neon |
| `PR-AVT-05` | Fuera de este plan; el GLB nuevo está en `../signall-avatar` |
| `JUCIOSO` | Se corrige en todos lados, previa confirmación del LSC50 |
| `RNF-DES-07` | Se fija después de la fase 1 |
| Consolidado | Dos columnas por bloque, una por ronda |
| Pruebas con personas | Bloqueadas por ahora |

## Lo que queda

- Ejecutar la fase 1: las 24 pruebas con `gpt-4o-mini` y el libro actualizado en sitio.
- Ejecutar los arreglos P0 y la cirugía de `PR-INT-03` al final de la fase 2.
- La sesión aparte del GLB nuevo con las cuatro marcas faciales.
- La ronda de evaluación completa con subagentes.

## Commits

- `sign-ai-android`: `ab65293`, `eb2549f`, `8a4e2a9` (plan, notas y conclusiones).
- `sign-ai-backend`: `8f27bf8` (`main` y `develop`).
- `sign-ai-docs`: `72070e3` (`main`).
