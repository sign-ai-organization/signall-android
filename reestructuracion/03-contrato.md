# R-04 · Lo que cruza el WebSocket

Verificación del contrato entre la app y el backend, hecha el **2026-11-13** leyendo
los dos lados antes de decidir si algo se renombra.

Estado de partida: `signall-android` en `39d6a82` y `signall-backend` en `301e721`
(este último sin cambios: el bloque no encontró nada que renombrar del lado del
servidor).

## Veredicto

**No hay nada de marca en el cable, así que no se renombra nada.** Ni un frame ni un
nombre de métrica dice `SignAI` ni `sign-ai`. Los `rt_*` son prefijo de "tiempo real",
no de la marca, y son identificadores de medición: renombrarlos invalidaría la
comparación con las cifras ya publicadas en la tesis.

Lo único que cambia de nombre es del lado del device y no cruza el socket:
`SignAiMetrics` → `SignAllMetrics` (R-03, commit `cf9fb5b`). El backend nunca supo cómo
se llama esa clase.

Decisión del owner el 2026-11-13: `rt_*` y los frames se quedan como están.

## Frames, app → backend

| Frame | Forma | Qué significa |
|---|---|---|
| audio | binario, PCM 16 bits LE, mono, 16 kHz, sin cabecera | voz cruda; **no se traduce**, vuelve como `transcript` |
| `text` | `{"type":"text","text":"…","turn":<int>}` | frase escrita o confirmada; **es lo único que se traduce** |
| `end` | `{"type":"end"}` | el usuario soltó el micrófono; el STT hace `flush()` y siempre responde |

`turn` lo pone la app y el backend lo devuelve sin tocarlo.

## Frames, backend → app

| Frame | Forma | Lo que hace la app |
|---|---|---|
| `transcript` | `{"type":"transcript","text":"…","final":bool}` | lo muestra; si `final` viene vacío, es "no se escuchó nada" y no un error |
| `segment` | `{"type":"segment","index":int,"total":int,"segment":{"type":"AFFIRMATIVE\|YES_NO_QUESTION\|CONTENT_QUESTION\|NEGATIVE\|IMPERATIVE","glosses":[{"text":"…","available":bool,"glb_path":"signs/<id>.glb","semantic_category":"…"}]},"turn":int}` | una oración, con `index`/`total` reales desde la primera |
| `done` | `{"type":"done","turn":int}` | cierra la frase; **siempre llega**, también con cero segmentos |
| `error` | `{"type":"error","message":"…","turn":int}` | se muestra como fallo de la frase |
| `capability` | `{"type":"capability","audio":bool,"reason":"…"}` | habilita o deshabilita el micrófono y dice por qué |
| `trace`, `sentence_trace` | `{"type":"trace","steps_ms":{…}}` | solo depuración; la app los descarta por tipo desconocido |

Comprobaciones que salieron bien:

- La app espera literalmente `"capability"` (no `capabilities`); coincide con lo que
  manda `controller.py:315`.
- `glb_path` sigue siendo `signs/<id>.glb`: **identificador del contrato**, no marca.
  No se toca y el clip sale del `.glb` empaquetado, no de la red.
- Un `type` desconocido se descarta sin romper el cliente, que es lo que permite que el
  backend agregue frames.

## Métricas

| Backend (`:8080/metrics`) | Device (`adb logcat -s SignAllMetrics`) | Tramo |
|---|---|---|
| `rt_first_sign_ms` | `envío→primera seña: N ms` | frase enviada → avatar moviéndose |
| `rt_phrase_total_ms` | `frase #N: N ms, X oraciones, Y glosas` | frase enviada → último `segment` |
| `rt_sentence_delay_ms` | — | cada oración por separado |
| `rt_phrases_total`, `rt_sentences_total`, `rt_glosses_total` | contadores del log | volumen |
| — | `representado: N señas en N ms` | solo device: duración de la representación |

Los nombres `rt_*` se quedan en los dos lados. Lo que cambió es el nombre de la clase
que los emite en el teléfono, y eso no se ve desde el backend.

## Qué queda pendiente

- El backend sigue diciendo `SignAI` en su documentación interna y en el arranque del
  log; es su propio bloque de nombres, no el contrato.
- Remedir `rt_first_sign_ms` con las 28 frases ~~sigue pendiente y no depende de esto~~:
  **hecho el 2026-11-13**. Con `gpt-4o-mini` y Whisper `medium` en Docker dio p50
  632–675 ms / p95 890–1015 ms en frío, y p50 0,5 ms con la caché caliente. El
  número que importa para la experiencia es audio→primera seña, p50 4366 ms, de
  los cuales 3724 ms son el STT. Detalle en `tools/TIEMPO-REAL.md`.
