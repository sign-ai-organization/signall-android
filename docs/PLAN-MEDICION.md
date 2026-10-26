# Plan de medición de latencia de SignAI

**Fecha:** 2026-10-16 · **Rama:** `flujo-oraciones` (los tres repos, sin mergear)
· **Alcance:** qué falta medir, qué hay que agregar para poder medirlo, y qué
tablas salen. **Nada de esto está implementado todavía.**

Este documento reemplaza a la idea de "medir y ya". La regla que gobierna el
orden es la del spec (`openspec/specs/ui/spec.md:325`): **agregar la métrica
antes que la feature**. Sin la Fase 1, las fases siguientes no se pueden
afirmar en la tesis.

---

## 1. Qué se quiere conseguir

1. **Cumplir los documentos.** Los seis requisitos de desempeño (RNF-DES-01 a
   RNF-DES-06) están escritos **sin ninguna cifra**: los valores están
   pendientes de TSK-03/04/05 en
   `../sign-ai-docs/documentos/requerimientos/SignAI_Requerimientos_Consolidados_v0.16.md:439`.
   Estos números son los que los fijan.
2. **Demostrar el pipeline paso a paso.** Cuánto se tarda en cada etapa, con
   el desglose que ya existe como molde en
   `../sign-ai-backend/docs/performance.md:24-48`.
3. **Comparar las dos IAs.** `gpt-4o-mini` (el que está en todos los
   documentos) contra `qwen3:4b-instruct` (el local).

La ruta de referencia: `RNF-DES-01` pide el tiempo **desde que el usuario
confirma el envío** hasta que el avatar ejecuta la primera seña. Ese es el
número que decide si el producto se siente en tiempo real.

---

## 2. Estado actual: qué mide cada parte

### 2.1 Lo que ya existe y se reutiliza

| Dónde | Qué expone | Formato |
|---|---|---|
| Backend `:8080` | `rt_first_sign_ms`, `rt_sentence_delay_ms`, `rt_phrase_total_ms`, `llm_ms{model}`, `cache_lookup_ms`, `rt_read_loop_busy_ms`, `llm_warmup_ms`, `audio_rms` + contadores | `/metrics.json`, parseable |
| STT `:8000` | `stt_inference_ms`, `stt_partial_queue_ms`, `stt_partial_lag_ms`, `stt_final_lag_ms`, `stt_vad_ms`, `stt_warmup_ms`, `stt_silence_timeout_ms`, `stt_dropped_total{reason}` | `/metrics.json`, parseable |
| Teléfono | `frase #N`, `oración→avatar`, `representado:`, `dictado #N`, `resumen:` | `adb logcat -s SignAiMetrics`, **en prosa** |

Herramientas que ya existen y funcionan: `tools/medir-primera-sena.py`,
`tools/medir-retraso.py`, `tools/verificar-tiempo-real.sh`,
`tools/verificar-bloqueo-lectura.py`, `tools/rendimiento/{gfxinfo.py,sf-medir.sh,frames.py,perfetto.sh}`,
y en el backend `tools/verificar-conexiones-stt.py`.

El pipeline trace del backend (`PipelineTrace`) ya mide `fingerprint`, `llm` y
`validation` por oración, pero **solo si `DEBUG_TRACE_ENABLED=true`** y solo
llega al cliente como `sentence_trace`, que la app ignora
(`SignAiWebSocketClient.kt:187`). No hay histograma.

### 2.2 Los huecos, por tramo

| # | Tramo | ¿Medido? | Nota |
|---|---|---|---|
| 0 | Toque del usuario → envío al socket | **No** | `turnStartedAt` se arma en `resetTurn()` (`MainScreenState.kt:962`), que corre **después** de `sendText` (`:788`). El reloj del usuario no existe |
| 1 | Espera de 1,5 s de silencio | Sí (STT) | `stt_silence_timeout_ms` es un valor de configuración, no una medición de cuánto se tardó |
| 2 | Transcripción | Sí (STT) | |
| 3 | Dividir en oraciones | **No** | `Orchestrator.translate_stream` llama `nlp_service.split()` sin cronómetro |
| 4 | Huella gramatical | **No** | Solo `trace_step("fingerprint")`, que necesita `DEBUG_TRACE_ENABLED` |
| 5 | Caché / LLM | Sí (backend) | `cache_lookup_ms` y `llm_ms` |
| 6 | Backend → teléfono → avatar | Sí (teléfono) | `oración→avatar`, medido en 95–114 ms en el Redmi |
| 7 | Baja del `.glb` | **No** | Va **mezclado** dentro del 6 |
| 8 | Representa | Sí (teléfono) | `representado:` |
| — | Cuadros por segundo | **No** | Ver §2.3 |
| — | Transición entre señas | **No** | `CROSSFADE_MS = 100` es una constante, no una duración medida |
| — | Carga de `avatar.glb` (12,98 MB) | **No** | Solo el timeout de 8 s del splash |
| — | Ida y vuelta de la red | **No** | `OkHttpClient` pelado, sin `EventListener` ni `pingInterval` |

### 2.3 Tres problemas concretos que hay que resolver

**a) Los cuadros por segundo no existen.** RNF-DES-05 los pide. El overlay de
depurar muestra `frame: 8123`, que es el contador acumulado de three.js
(`app.js:272`), no una frecuencia. No hay un solo número de FPS en toda la app.
Peor: `tools/README.md:185` y `tools/rendimiento/README.md:132` **afirman** que
el overlay muestra el FPS, y no lo hace. Además `dumpsys gfxinfo` no sirve
para esto (`rendimiento/README.md:128-131`): three.js corre en el hilo de UI y
Android lo reporta como un bloque único.

**b) Ningún reloj cruza entre los tres procesos.** No hay marca de tiempo
absoluta que viaje del teléfono al backend. Lo único que cruza es `jsMillis`
dentro de `signStart:<i>:<jsMs>`, y ese es el reloj de `performance.now()` del
WebView, no el del teléfono. Para descomponer la latencia entre procesos hoy
habría que parsear logcat a mano.

**c) El teléfono es el único eslabón sin salida para una máquina.**
`SignAiMetrics.kt` tiene las tres latencias correctas, pero: el anillo de 256
muestras **se pierde al cerrar la app**, `summarise()` da p50/p95/max/n pero
**no da mínimo ni media**, y `snapshot()` es una frase en español.

---

## 3. Decisiones tomadas por el owner (2026-10-16)

### D-7 — El prompt NO lleva la lista de glosas

**Decisión:** quitar el vocabulario cerrado del prompt. Va en contra de la
documentación de la tesis y no escala.

**Verificado:** hoy sí está. `llm_client.py:72-73`:

```
You must use ONLY the glosses in this closed vocabulary:
{vocabulary}
```

**Por qué el owner tiene razón en las dos razones que da:**

1. Va contra el plan de pruebas: `../sign-ai-docs/plan-pruebas/01-diseno/llm/general.md:32`
   dice literalmente *"No incluye la lista de las 50 glosas del catálogo, así
   que el modelo puede inventar glosas. Las que no existen las marca como no
   disponibles el validador."*
2. No escala, y el validador **sí** revisa:
   `ValidatorServiceImpl._validate_gloss` (`validator_service_impl.py:17-21`)
   hace `catalog_repository.search(gloss)` y devuelve `available=False` si no
   está. Ese es exactamente el mecanismo de RF-CAT-03.

**Qué sale y qué se queda: lo responde la documentación, no hace falta
preguntarlo.**

El vocabulario se usa en **dos** lugares:

| Lugar | Qué hace | Qué decide la documentación |
|---|---|---|
| **El prompt** | Cierra el vocabulario: el modelo no puede inventar | **Sale.** Ver abajo |
| **La reparación** | Re-une fragmentos ("COMO","ESTAS" → "COMO ESTAS") y borra lo que no está en el catálogo | **Se queda.** Ver abajo |

**Por qué sale del prompt.** Cuatro fuentes, todas en el mismo sentido:

1. `plan-pruebas/01-diseno/llm/general.md:32`: *"No incluye la lista de las 50
   glosas del catálogo, así que el modelo puede inventar glosas. Las que no
   existen las marca como no disponibles el validador."*
2. **RF-NLP-02**, criterio de aceptación
   (`SignAI_Requerimientos_Consolidados_v0.16.md:260`): *"el sistema envía esa
   oración al LLM externo y recibe una secuencia de glosas propuesta. **Esta
   salida todavía no ha sido validada contra el catálogo de señas, esa
   verificación ocurre en un módulo distinto.**"*
3. El **historial de versiones** del mismo documento, línea 15 (v0.3 del
   10/08/2026): *"**El NLP no valida contra el catálogo**, esa función queda
   pendiente para la sección 3.4."* La 3.4 es donde después se escribió
   RF-CAT-02.
4. `../sign-ai-backend/docs/performance.md` §9: el costo de la lista
   *"es lineal con el tamaño del corpus y se paga en cada llamada"* — 413
   caracteres hoy, ~41 KB a 5 000 glosas.

**Por qué la reparación se queda.** El mismo §9 es explícito:

> *"**Lo que NO hay que cambiar ahora.** La mitad del diseño es sana y se
> queda: `_repair_glosses` y `_merge_fragments` consultan el vocabulario con
> `normalize_gloss` en un diccionario: es O(1) y **escala sin problema. El
> problema es solo la lista dentro del prompt.**"*

Es decir: la objection del owner (que no escala) aplica al prompt, no a la
reparación. Consultar un diccionario en memoria no escala mal.

**El riesgo que reaparece.** La reparación se escribió el 2026-10-14
(`llm_client.py:12-66`) después de que el prompt viejo partiera "cómo estás"
en `TÚ,ESTAR,CÓMO`. Ninguna de esas tres glosas tiene animación: el avatar
caía a la seña de reserva y la app informaba *"Reproducción completada"* como
si hubiera funcionado. **Pero la reparación es justamente lo que lo cubre**, y
es más fuerte que la instrucción del prompt: actúa después de la respuesta. El
plan lo mide en la Fase 2 para que la consecuencia quede con número, no con
recuerdo.

**Lo que hay que reescribir en el prompt al quitar el vocabulario.** No se vacía:
quedan las reglas de formato y los siete ejemplos. Pero tres reglas mencionan
"the vocabulary" y quedan sin sentido si la lista ya no está
(`llm_client.py:78-81` y `:88-89`):

- *"Never split a gloss from the vocabulary into its separate words. If the
  vocabulary has "COMO ESTAS", never answer "TU,ESTAR,CÓMO"."*
- *"Write each gloss exactly as it appears in the vocabulary above..."*
- *"If no vocabulary gloss covers part of the meaning, leave that part out
  rather than inventing a gloss."*

Hay que reescribirlas sin referring al catálogo. La regla del ejemplo
("COMO ESTAS" es **una** glosa, no dos) sí se puede conservar como ejemplo
concreto, sin decir de dónde sale.

**Una desviación que queda anotada, no resuelta.** La reparación **borra** las
glosas que no están en el catálogo (`llm_client.py:215-218`). Eso es validar
dentro del NLP, que RF-NLP-02 prohíbe, y tiene una consecuencia concreta:
si el NLP borra la glosa, **RF-CAT-03 nunca se dispara** y el usuario nunca ve
el aviso *"Seña no disponible"* que el requisito pide
(`SignAI_Requerimientos_Consolidados_v0.16.md:358`). Hoy eso no se nota
porque el prompt cerrado evita que la glosa llegue a ese punto.

No se corrige en este trabajo. Se mide, se documenta y queda anotado como
desviación entre el código y el requisito. Nota además que
`plan-pruebas/validador-catalogo/general.md:39` tiene esto como **decisión
abierta** ya escrita: *"Dónde se normalizan las glosas (quitar tildes y
puntuación, pasar a mayúsculas): al leer la respuesta del LLM o en el
validador. Se decide con los resultados de PR-LLM-02 y PR-VAL-01."*

### Modelos a comparar

Solo dos, por decisión del owner:

| | Modelo | Cómo se activa |
|---|---|---|
| Nube | `gpt-4o-mini` | `LLM_BASE_URL=` vacío |
| Local | `qwen3:4b-instruct` | `LLM_BASE_URL=http://127.0.0.1:11434/v1` |

Es el que está en `docs/decisions.md:57`, en
`plan-pruebas/01-diseno/llm/general.md:28` y en el diagrama de despliegue
`sdd-6.2-vista-fisica-despliegue.drawio:43`.

**Referencia previa, con su origen:** el 2026-10-14, 10 frases, una sola corrida
(`llm_client.py:58-62`): `gpt-4o-mini` 9/10 frases correctas, 1,09 s de media;
`qwen3:4b-instruct` 9/10, 0,20 s. **No es una línea base**: son 10 frases, una
corrida, con el prompt viejo.

### Pendiente escrito que esta medición cierra

`../sign-ai-backend/docs/CAMBIOSBACKEND.md:186-188`:
*"Falta remedir `rt_first_sign_ms` con el LLM de producción. Los 273 ms son de
`qwen3:4b-instruct` en Ollama local, no de OpenAI. Con OpenAI la cifra es otra y
esa es la que va a la tesis."*

---

## 4. Fase 0 — Bloqueante: el prompt sin vocabulario

Sin esto, las Fases 2 y 3 miden una configuración que va a cambiar.

1. **Quitar `{vocabulary}` del prompt** (`llm_client.py:72-73`) y reescribir
   las tres reglas que lo mencionan (§3). La reparación
   (`_repair_glosses` / `_merge_fragments`) **se queda**: escala y es la que
   cubre el fallo de fragmentación.
2. La decisión del prompt no toca el validador, que ya cumple RF-CAT-02.
3. **Comprobar la cadena completa con una frase que produzca glosas fuera del
   catálogo**, para ver qué hace la app: si avisa "Seña no disponible"
   (RF-CAT-03), si cae en la seña de reserva, o si informa éxito sin avisar
   (GLS-10). Es el escenario del 14 de octubre y hay que verlo con número.
4. Correr `verify_client_frames.py` y `verify_nlp_service.py`.
5. Medir el antes y el después con los 28 fixtures: cuántas glosas salen
   inventadas y cuánto tarda.

**Ojo con la caché.** Cambiar el prompt **no** invalida `semantic_cache`. Sin
`DELETE FROM semantic_cache;` **y** `docker compose restart backend`, la
segunda pasada sale como acierto de caché y no mide nada
(`tools/TIEMPO-REAL.md:78-88` y `../sign-ai-backend/AGENTS.md`).

---

## 5. Fase 1 — Que los números existan

| # | Dónde | Qué | Para qué |
|---|---|---|---|
| 1.1 | `SignAiMetrics.kt` | Añadir **mínimo** y **media** a `summarise()` | Todas las tablas |
| 1.2 | `SignAiMetrics.kt` | Exportar las muestras en **JSON parseable**, no en prosa | Que las tablas del teléfono se/armen solas |
| 1.3 | `MainScreenState.kt` | Reloj del **toque**, no del envío | RNF-DES-01 |
| 1.4 | `MainScreenState.kt` | Reloj en `queueItemsFor()` y en `appendQueue()` | Separar la resolución de assets del tramo 6 |
| 1.5 | `app.js` | Timestamp en `setQueue` / `appendQueue` / `loadSignClips` | Separar la baja del `.glb`. El docstring de `app.js:429-434` ya lo promete y no lo cumple |
| 1.6 | `app.js` | **FPS real** | **RNF-DES-05** |
| 1.7 | `app.js` | Duración real de la transición | **RNF-DES-06** |
| 1.8 | `app.js` | Tiempo de carga de `avatar.glb` (12,98 MB) | Arranque |
| 1.9 | Backend | `split_ms`, `fingerprint_ms`, `validation_ms` como histogramas | Los tres tramos sin número |
| 1.10 | Backend | Reloj absoluto en cada mensaje del socket | Descomponer la latencia entre procesos |
| 1.11 | `embedding_model.py:3` | El modelo está **escrito en el código**; hacerlo configurable por entorno | `marco.md:178` obliga a registrar el modelo de embeddings en cada corrida |

**Restricción del plan de pruebas:** `../sign-ai-docs/plan-pruebas/00-bases/marco.md:176-179`
obliga a registrar la configuración de cada corrida, pero **esa lista no
incluye `SILENCE_TIMEOUT_MS`**, aunque `alcance.md:72` sí lo exige. Es el
pendiente P-3 de la sesión del 2026-10-16 y sigue abierto.

---

## 6. Fase 2 — La prueba principal: texto escrito

Es la que pidió el owner y la que sostiene RNF-DES-01. Un script escribe una
frase por el mismo WebSocket que usa la app, con el **mismo reloj de arranque
que la app** (la confirmación de envío), y mide el viaje entero hasta que
llega el primer `segment`.

Receta, en orden, con el estado de la caché explícito en cada corrida:

| Corrida | Caché | Qué mide |
|---|---|---|
| Frío, IA local | Vacía | El peor caso de la IA local |
| Caliente, IA local | Con esas frases | El mejor caso |
| Frío, `gpt-4o-mini` | Vacía | El peor caso de la nube |
| Caliente, `gpt-4o-mini` | Con esas frases | El mejor caso |

Los 28 fixtures existen como `.wav` en
`../sign-ai-stt/fixtures/audio/` (no están en git; se generan con
`scripts/make_audio_fixtures.py --all`). Más las 4 `conversacion_*.wav`, que
son las que tienen 5–8 palabras y por lo tanto sí parecen una conversación.

**Lo que hay que registrar por frase:** primero-seña, total de la frase, número
de oraciones, número de glosas, y la fuente (caché o LLM). Más el desglose por
paso con el formato de `performance.md:24-48`.

---

## 7. Fase 3 — Las dos IAs

Con el prompt ya fijo (Fase 0), lo mismo de la Fase 2 con las dos
configuraciones.

Además de la latencia, la comparación tiene que medir **calidad**: cuántas
glosas de las 28 frases están dentro del catálogo. Es el criterio que ya se usó
el 14 de octubre ("in-vocabulary 9/10") y el único que justifica decir que
"la calidad es equivalente".

**Trampa de la caché.** La caché semántica guarda `(embedding, glosas)` y **no
sabe qué IA las produjo**. Si se corre la local primero, la de la nube sale
como acierto de caché y no mide nada. Hay que vaciarla entre una y otra.

---

## 8. Fase 4 — Voz real

Los 14 audios de `/sdcard/Music/` (ver §10) cubren lo que con audio generado
por computadora no se puede medir:

- **El micrófono real.** La app usa `AudioSource.VOICE_RECOGNITION`, que
  cancela el eco del altavoz (`TIEMPO-REAL.md:529-532`). Por eso un agente no
  puede hablarle al micrófono: es imposible. Y `AudioCapture.kt` no escribe
  nada en disco: si se grabó con la app, no quedó archivo.
- **La espera de 1,5 s (decisión P-1).** Los 28 fixtures tienen todos
  `pause_ms = 0`: no tienen respiración que medir. Los audios del grupo B sí.
- **Los filtros.** El 2026-10-16, de 224 transcripciones, 92 fueron descartadas.
  Con voz de computadora no se puede distinguir un descarte correcto de uno
  equivocado.

Los tres audios con pausas marcados con puntos en el nombre
(`me llamo.... diego`, `vivo en Bogotá.....y trabajo aqui`,
`dónde está.....el restaurante`) son los que permiten medir cuánto dura de
verdad la pausa dentro de la frase, y por lo tanto dónde está el límite real
de `SILENCE_TIMEOUT_MS = 1500`.

---

## 9. Qué tablas salen

Cada tabla en su propio archivo `.md`, en `docs/mediciones/<fecha>/`, con el
modelo, la configuración, el número de repeticiones y la máquina anotados en
la cabecera.

| # | Tabla | De dónde sale | Requisito |
|---|---|---|---|
| T1 | Descomposición de la latencia por paso | Backend + teléfono | RNF-DES-01, RNF-DES-03 |
| T2 | Efecto de la caché (frío vs caliente) | Backend | RNF-DES-03 |
| T3 | Latencia según longitud de la frase (1, 2, 3 oraciones) | Backend | RNF-DES-01 |
| T4 | Comparación de las dos IAs | Backend | RNF-DES-03 |
| T5 | Calidad por IA (glosas dentro del catálogo) | Backend | RF-NLP-01 |
| T6 | Latencia de transcripción, real y sintética | STT | RNF-DES-02 |
| T7 | Efecto del tiempo de silencio (800/1000/1200/1500) | STT | RNF-DES-02, P-1 |
| T8 | Los cuatro modos de entrada | Teléfono | RNF-DES-01 |
| T9 | Cuadros por segundo durante una representación | Teléfono | **RNF-DES-05** |
| T10 | Transición entre señas | Teléfono | **RNF-DES-06** |
| T11 | Cuánto se sigue representando tras terminar de hablar | Script | Contexto (OE4) |
| T12 | Sesiones simultáneas (1, 2, 3) | Los tres | **RNF-DES-04** |

Los requisitos en negrita **no se pueden calcular hoy**: no hay con qué medirlos.

---

## 10. Material de prueba

### Los 14 audios

Están en `/sdcard/Music/` en el Redmi, grabados con la grabadora del sistema
(el 2026-10-16 20:45). Formato `.aac`; hay que convertirlos a 16 kHz mono
PCM16 con `ffmpeg`, que está instalado.

| Archivo | Grupo | Duración |
|---|---|---|
| `hola.aac` | A, corta | 2,26 s |
| `gracias .aac` | A, corta | 2,11 s |
| `como estas.aac` | A, corta | 4,63 s |
| `buenos dias.aac` | A, corta | 2,97 s |
| `bien.aac` | A, corta | 1,94 s |
| `me llamo.... diego.aac` | B, con pausa | 5,85 s |
| `vivo en Bogotá.....y trabajo aqui.aac` | B, con pausa | 6,83 s |
| `dónde está.....el restaurante .aac` | B, con pausa | 5,91 s |
| `hola, buenos días, como estas.aac` | C, conversación | 6,17 s |
| `me llamo Diego, vivo en Bogotá, soy estudiante.aac` | C, conversación | 6,23 s |
| `tengo hambre, por favor, dónde está el restaur..aac` | C, conversación | 5,31 s |
| `bien bien,muchas gracias,hasta mañana.aac` | C, conversación | 3,20 s |
| `6 segundos silencio.aac` | D, sin voz | 5,38 s |
| `tos, golpes, toques, 6 segundos.aac` | D, sin voz | 6,42 s |

**Falta:** una sola repetición de cada uno. Con una muestra no hay p50 ni p95,
que es justo lo que va a las tablas. El plan puede entregar media y mínimo con
estos, y los percentiles necesitan dos o tres tomas más de cada frase.

Los nombres llevan las pausas marcadas con puntos, lo cual sirve como
metadato de la intención. La duración real de la pausa hay que sacarla midiendo
la energía del audio, no del nombre.

### Los 28 fixtures sintéticos

`../sign-ai-stt/fixtures/audio/*.wav`, presentes en disco. Los `.json` sí están
en git, los `.wav` no.

---

## 11. Trampas ya pisadas

| Trampa | Consecuencia |
|---|---|
| La caché semántica caliente | Un p95 de 5 ms que no mide nada. Hay que `DELETE FROM semantic_cache;` **y** `docker compose restart backend` (`SEMANTIC_CACHE_PRELOAD=true` deja copia en memoria) |
| La caché no sabe qué IA produjo la entrada | La segunda IA sale como acierto de caché |
| Los histogramas guardan 512 muestras | Con más, los percentiles son de las últimas 512. Reiniciar entre bloques |
| Cambiar el prompt no invalida la caché | La Fase 0 tiene que vaciarla |
| `dumpsys gfxinfo` no ve dentro del WebView | Three.js va en el hilo de UI y Android lo reporta como un bloque |
| Los timestamps de `framestats` están en el dominio `Uptime:` | Distinto de `/proc/uptime` y de `date`; anclar con el host da números negativos |
| `dumpsys SurfaceFlinger --latency` devuelve el anillo de 128 | Sin `--latency-clear` se mide la transición de entrada, no el gesto |
| Medir en build debug | La inflación de R8 no está; solo sirve para comparar gestos entre sí |
| `rt_first_sign_ms` con nombre de siempre | La línea base publicada (913 ms p50 / 1336 ms p95) es del **camino por palabras** y no es comparable |

---

## 12. Lo que este documento NO hace

- **No escribe en `../sign-ai-docs`.** Ese repositorio es del owner y no se
  toca sin que lo pida. Los documentos de la tesis se usan **solo como
  referencia** de qué hay que cumplir.
- **No implementa nada.** La Fase 0 y la Fase 1 son el trabajo de la próxima
  sesión.
- **No arregla la discrepancia entre documentos y código** que se encontró al
  revisar: el bloque LLM del plan de pruebas (`llm/general.md:32-33` y `:44`)
  describe un cliente que ya no existe, porque el prompt se reescribió el
  2026-10-14 sin anotarlo en `docs/decisions.md`.