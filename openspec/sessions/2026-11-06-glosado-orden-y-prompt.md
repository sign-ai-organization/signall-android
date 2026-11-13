# Glosado: Fase 5, el prompt medido y el orden de las glosas

**Fecha:** 2026-11-06, mañana · **Rama:** `fix/fases1-3` en los tres repos

## Qué llegó

El owner pidió dos cosas en la misma ronda: cerrar la **Fase 5** del plan de
implementación 2 y probar un **prompt nuevo** para el glosado. Con dos condiciones:
mostrarle el prompt viejo y el nuevo, con qué mejora cada cambio, antes de aplicarlo; y
medir con las 30 frases de FR-01 con la caché vacía, validando después en un conjunto que
no estuviera en el prompt (aprobó FR-02).

## Qué se hizo

| Repo | Qué | Commit |
|---|---|---|
| backend | `POST /admin/cache/reload` con `X-Admin-Token` y `OrchestratorService(ABC)` (Fase 5) | `d95bfde` |
| backend | La flexión resuelve NIÑO/NIÑA sin depender del hash y el `TU` va después de la pregunta | `0748898` |
| backend | La bitácora del experimento del prompt | `e982cf4` |
| backend | `normalize_order`: el verbo cierra la pregunta y `TODOS` va antes del verbo | `ca85480` |
| STT | Nada: la calibración de `AVG_LOGPROB_THRESHOLD` ya estaba cerrada (`9787e49`) | — |
| Android | `tools/medir-glosado.py` y esta entrada | este commit |

El detalle de cada cosa está en `../signall-backend/docs/sessions/`:
`2026-11-06-fase5-backend.md`, `2026-11-06-prompt-experimento.md` y
`2026-11-06-orden-glosas.md`.

## Qué se decidió

- **La Fase 5 es un endpoint explícito y no `LISTEN/NOTIFY`.** El caso medido es la
  edición manual ocasional de `semantic_cache`; el token no es autenticación de usuario
  —DEC-14 sigue abierta—, es lo mínimo para que la ruta no quede abierta a quien alcance
  el puerto. Con `ADMIN_TOKEN` vacío la ruta responde 404.
- **El prompt no se cambió.** La versión que daba 26/30 en FR-01 bajaba FR-02 de 70-71 a
  67-69: sobre las 120 frases el acierto quedaba igual, solo cambiaba de conjunto. El
  owner eligió dejarlo como estaba y no reportar un número sobreajustado.
- **El orden sí se corrige, y después del modelo.** El corpus cumple dos convenciones en
  todas sus frases de referencia: en una pregunta con palabra interrogativa el verbo va al
  final (8 de 8), y `TODOS`/`TODAS` va pegado al sustantivo antes del verbo (11 de 11). Se
  aplica solo si el catálogo dice qué glosa es verbo.
- **Las afirmaciones no se reordenan.** El corpus no es consistente ahí, y mover un
  sustantivo de delante del verbo a detrás exige saber si es sujeto o complemento.
- **El pronombre ambiguo no se toca.** «Se siente mal» → `TU` es decisión del owner, y es
  la causa de la mayoría de los fallos que quedan.

## Qué se midió

Todo con `gpt-4o-mini`, temperatura 0, y `DELETE FROM semantic_cache;` + reinicio del
backend antes de cada corrida.

| Conjunto | Con el arreglo de vocabulario | Con el orden de las glosas |
|---|---|---|
| FR-01 (30 frases, por el socket) | 23 y 22 | **25** y **25** |
| FR-02 (90 frases, sin caché) | 70-71 | **75** y **75** |

- **FR-01 25/30 y 113/112 glosas**, medido hoy con `tools/medir-glosado.py` contra el
  backend ya con el orden aplicado. Las cinco que fallan son 006 (`CON GUSTO`), 010 y 024
  (pronombre ambiguo), 013 (`NOMBRE`) y 029 (`SENTIR` de más).
- **FR-02 75/90 sin caché**, dos corridas, con el arnés directo sobre el cliente del LLM.
- **Por el socket, FR-02 da 70/90**, y la diferencia tiene nombre: **5 de los 20 fallos
  fueron aciertos falsos del caché semántico**. Las 90 frases de FR-02 son tres variantes
  de cada frase de FR-01 y se diferencian por un sustantivo; el caché devolvió las glosas
  de la variante vecina (pedía `HERMANO`, salió `TIO`; pedía `CONTENTO`, salió `FELIZ`).
  Es el mismo problema que la ronda del 2026-11-04 midió en el umbral (0,042 contra 0,05),
  ahora con nombre y con 5 casos concretos.
- **En el Redmi Note 9S**, con el backend local por `adb reverse`:
  «Quién come con ustedes» → `QUIEN,USTEDES,COMER`, primera seña en 1013 ms; «Hola, como
  estas» → `HOLA,COMO ESTAS`, primera seña en 963 ms. `SignAiMetrics`, 2026-11-06.

## Qué quedó pendiente

- **El caché semántico da falsos aciertos entre frases que se diferencian por una palabra**
  (backend). Cinco casos medidos en FR-02. No se tocó el umbral en esta ronda: cambiarlo
  mueve `PR-CAC-*` y merece su propia medición.
- **006, 013, 029** son del modelo: omite `CON GUSTO`, responde `LLAMAR` donde el corpus
  pide `NOMBRE`, y agrega `SENTIR`. Solo se arreglan con prompt, y el prompt medido cuesta
  más de lo que da.
- **La Fase 5 no recarga el catálogo**: después de un seed sigue habiendo que reiniciar el
  contenedor. Está dicho en el docstring del endpoint y en `.env.example`.
- **`develop` queda atrás de `main`** en los tres repos: el merge de esta ronda fue a
  `main`, que es lo que pidió el owner.
