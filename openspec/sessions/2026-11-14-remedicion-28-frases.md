# Remedición de `rt_first_sign_ms` con las 28 frases

**Días:** 13 y 14 de noviembre de 2026 · **Rama:** `main` · **Commit:** ver la bitácora
de `reestructuracion/00-general.md`

La cifra vieja (p50 273 ms) era de `qwen3:4b-instruct` en Ollama local y estaba
publicada desde el 2026-10-15. El LLM de producción es `gpt-4o-mini`, así que la
cifra no se podía citar. Esta sesión la reemplaza por una medición con la pila que
se publica, y cierra el pendiente que arrastraban el SRS, el spec de UI y `AGENTS.md`.

## Cómo se midió

- Instrumento: `tools/medir-primera-sena.py`, que reproduce cada `.wav` de
  `../signall-stt/fixtures/audio` por el WebSocket a 1x (frames de 30 ms), espera
  el `final` del STT, manda la transcripción como `text` —lo que hace la app— y
  cronometra hasta el primer `segment`.
- Pila: backend y STT en Docker (`sign-ai-backend-*`), Whisper `medium` en CUDA,
  `gpt-4o-mini` con `LLM_TEMPERATURE=0.0`.
- Caché: `DELETE FROM semantic_cache;` **más** reinicio del contenedor, porque
  `SEMANTIC_CACHE_PRELOAD` deja la copia en memoria y sin el reinicio la segunda
  pasada sale a 1 ms sin ser una medición en frío.
- Dos pasadas en frío el mismo día y una en caliente, para que la cifra no dependa
  de una sola corrida.

## Qué dio

Cifras de `/metrics` del backend (`rt_first_sign_ms`, que es donde vive la métrica):

| Corrida | Frases | p50 | p95 | min | max |
|---|---|---|---|---|---|
| Frío, pasada 1 | 28 (28 *miss*) | 675 ms | 1015 ms | 556 ms | 1165 ms |
| Frío, pasada 2 | 28 (28 *miss*) | 632 ms | 890 ms | 525 ms | 1132 ms |
| Caliente | 28 (27 aciertos) | 0,5 ms | 3,1 ms | — | — |

Y lo que percibe el usuario, que incluye el STT:

| | p50 | min | max |
|---|---|---|---|
| Audio → primera seña | **4366 ms** | 3192 ms | 8529 ms |
| …de los cuales el STT | 3724 ms | — | — |

**El cuello de botella sigue siendo el STT, no el pipeline.** Traducir cuesta
0,63 s con el LLM en frío y 0,5 ms con la caché caliente; transcribir y esperar el
cierre por 1,5 s de silencio cuesta 3,7 s. Las cuatro frases de conversación
(5–8 palabras) fueron las más lentas: 6,2–8,5 s de audio a primera seña.

Las 28 frases son cortas (mediana de 2 palabras, 1 oración), así que no describen
una conversación real. Sirven para comparar versiones del pipeline, no para
afirmar cómo se siente el producto hablando.

## Qué se actualizó

- `tools/TIEMPO-REAL.md`: la tabla vigente pasa a ser esta; la de Ollama queda
  abajo, marcada como historia y con la advertencia de que no es el LLM de producción.
- `AGENTS.md` (§ Observabilidad): la cifra y la advertencia de que no es comparable
  con la línea base por palabras.
- `openspec/specs/ui/spec.md` (OBS-1) y `docs/CAMBIOS.md` (§ pendientes, ítem 4):
  el pendiente queda cerrado con los números.
- `reestructuracion/02-android.md` y `03-contrato.md`: los dos decían que faltaba.

## Qué quedó pendiente

1. **La deuda de `SignAI` en `signall-docs`.** Una auditoría de solo lectura
   encontró que los cinco documentos formales ya dicen `SignAll`; lo que quedó
   atrás son tres líneas del plan de pruebas (`SignAiMetrics`, `SignAiNet`,
   `SignAIWeb`, `-Psignai.devTools`), ~51 referencias técnicas en borradores y
   algunas notas de estado que ya son falsas. El título y el nombre en los
   borradores que alimentan el SRS son decisión del owner. No se tocó el repo.
2. **Publicar los números en el SRS y la memoria.** Ahora que existen, el documento
   que cite latencia tiene que citar esta corrida con su origen, no la de Ollama.
