# Sesión 2026-10-16 (tarde) — Ejecución de las siete decisiones de latencia

**Fecha:** 2026-10-16, tarde · **Rama:** `flujo-oraciones` (los tres repos) ·
**Alcance:** ejecutar el plan de `2026-10-16-latencia-y-decisiones.md`. Todas las
decisiones están tomadas; esto es lo que se implementó, se midió y quedó
pendiente.

## Lo que se implementó, en el orden del plan

### Fase 1 — Que los números existan

| Métrica | Dónde | Qué |
|---|---|---|
| `llm_ms{model}` | backend | La llamada al modelo de lenguaje. Antes solo existía como paso de `PipelineTrace`, que necesita `DEBUG_TRACE_ENABLED` y un log para leerse. |
| `cache_lookup_ms` | backend | La consulta a la caché semántica. Va con la anterior porque separada de ella se presta a confusión: con la caché caliente no hay `llm_ms` en absoluto. |
| `rt_read_loop_busy_ms` | backend | Trabajo por frame del bucle de lectura del socket, sin contar la espera. Es la que convierte D-2 en algo afirmable. |
| `signStart:<i>:<jsMs>` | app → JS | La primera seña del turno moviéndose, con su reloj propio (`BRG-16`). |
| `oracion_a_avatar` | `SignAiMetrics` | Del `segment` recibido al avatar moviéndose. El tramo que no lo medía nadie. |
| `stt_silence_timeout_ms` | backend `/health` | Copiado del `/health` del STT, para que la app lo muestre (D-6). |

Medido con `qwen3:4b-instruct` en Ollama local: `llm_ms` p50 442 ms / max
881 ms; `cache_lookup_ms` p50 48 ms. **Nuevo hallazgo que nadie estaba mirando:**
la consulta a la caché cuesta ~48 ms por oración, no 1 ms. Es más de lo que
cuesta un `HIT` de la base y es comparable a un `MISS` barato. Queda anotado
para cuando se mire el caché en serio.

### Fase 2 — D-2 y D-3, en el mismo archivo y el mismo rato

`app/controller/router.py`. Los dos con su prueba de regresión:

| | antes | después |
|---|---|---|
| retraso del audio durante la traducción (`tools/verificar-bloqueo-lectura.py`) | 991 ms (8438 ms en la primera corrida) | **0 ms** |
| `rt_read_loop_busy_ms` p50 / max | 0,155 / **931** ms | 0,151 / **0,595** ms |
| conexiones al STT colgando después de 10 sesiones (`tools/verificar-conexiones-stt.py`) | +18 | **0** |

`tools/verificar-conexiones-stt.py` es nuevo y vive en el backend, aunque el
problema se detecte desde la app: cuenta los sockets establecidos con el puerto
del STT **desde el host**, no desde adentro del contenedor (el primer intento usó
`docker exec` y era innecesario: `network_mode: host` lo deja todo visible).

**La medición necesitó tres versiones para ser confiable**, y esto vale para
cualquier herramienta que mida conexiones: una lectura puntual de `ss` no sirve,
porque un socket que se acaba de cerrar sigue apareciendo un rato y uno que se
acaba de abrir todavía no. La versión final toma una línea de partida y después
espera a que el número **vuelva** a ella (20 s), en vez de comparar dos
instantes. Con eso no dio falsos fallos en 6 corridas seguidas, y funciona
también con la app del teléfono abierta: avisa que la línea de partida no es
cero y mide contra esa, no contra cero.

### Fase 3 — D-1, D-5 y D-6 en la app

**D-1**: cada oración se representa apenas llega. `flushSegments()` entrega lo
pendiente en vez de esperar a `pendingTotal`; `flushedThrough` la hace
idempotente. `setQueue` + `play()` con la primera, `appendQueue` con cada
siguiente (`BRG-14`, que estaba en el puente sin que nadie lo llamara).

Medido en el Redmi con una frase de tres oraciones:

- **oración → avatar: 95–114 ms** (antes: ~569 ms parado con la primera seña ya
  en la mano).
- El caso difícil también: con una tercera oración que tardó 7,6 s, la cola se
  vació, llegó `onPlaybackDone`, y la oración tardía **reanudó en el índice
  correcto** y el segundo tramo se midió (`representado: 3 señas en 2151ms`).

Dos bugs que solo aparecieron en el dispositivo y que hay que documentar:

1. `appendQueue` arrancaba el ítem que ya estaba cargando → la misma seña
   arrancaba dos veces y su `signStart` salía duplicado. Ahora `playFrom` corta
   también con `state.loading`.
2. El reinicio usaba `queueIdx` a secas y **repetía la última seña**. `advanceQueue`
   deja `queueIdx` en el último índice que llegó a sonar, así que reanudar es
   `queueIdx + 1` si la cola ya arrancó, y `0` si nunca arrancó. Medido: el
   primer arreglo reproducía `seña 1` dos veces.

**D-5**: se quitó el adelanto automático. `CATCHUP_BACKLOG`, `CATCHUP_FACTOR`,
`state.catchUp` y la rama de `tickPlayback` ya no existen en `app.js`; `BRG-15`
queda marcada como eliminada y `queueState()` dejó de devolver `catchUp`.
`tools/medir-retraso.py` dejó de calcular las columnas "con el adelanto" y ahora
responde otra pregunta: cuánto se sigue representando después de que el hablante
terminó.

**D-6**: el overlay de depurar muestra `silencio STT: 1500ms`. El valor llega por
`/health` del backend → `BackendHealth` → `setSilenceTimeoutMs`. **No es un
ajuste**: solo se muestra, y sin conexión dice "?", que es la verdad.

### Fase 4 — Las primeras pruebas del STT

`sign-ai-stt/scripts/verify_session.py`, 10 pruebas, todas en verde. Cubren la
ventana de inicio, el cierre por `SILENCE_TIMEOUT_MS`, que una pausa de 300 ms
**no** parte la frase, el recorte del silencio final, que `flush()` responde
siempre, que el corte del tope del buffer cae en la pausa, la prioridad del
parcial y `audio_ms`.

Corren sin GPU, sin micrófono y sin los fixtures de audio (que no están en git):
el VAD y el modelo son dobles, y lo que se prueba es la lógica del repo. Se
ejecutan en el contenedor del servicio con `scripts/` montado desde el host
(comando en el README). `scripts/` sigue fuera de la imagen a propósito: ahí hay
utilidades de escritorio que necesitan Piper.

### Fase 5 — El script de verificación

`tools/verificar-tiempo-real.sh` daba por buena una corrida hecha con la caché
semántica llena: el p95 salía de 5 ms, pasaba cualquier presupuesto y no medía
nada. Ahora esa corrida se declara **no medible**, con la instrucción para medir
en frío. Es la diferencia entre un control que mide y uno que aprueba siempre.

## Dos cosas que se rompieron al cambiar el cierre de sesión (y no se ven en la app)

1. **`verify_client_frames.py` se colgó.** El doble del cliente del STT devolvía
   `None` de `receive_transcript` cuando no había transcripción en cola. Con el
   `gather` viejo eso no importaba —la otra mitad de la sesión seguía leyendo—, pero
   con `FIRST_COMPLETED` significa "el STT se fue" y **cortaba la sesión antes de que
   el cliente mandara nada**. El doble ahora bloquea como el cliente real, que es
   lo que hace `recv()`. Y de paso el script fijó dos regresiones nuevas de D-3: que
   cerrar el cliente cierre el socket con el STT **una vez**, y que si el STT se cae la
   sesión también termine.
2. **`verify_orchestrator.py` hablaba con la API de OpenAI de verdad.** Construía el
   `LLMClient` sin `settings.llm_base_url`, así que con `LLM_MODEL=qwen3:4b-instruct`
   le preguntaba a OpenAI y recibía 404 `model not found`. El script parecía que
   andaba y lo que hacía era pegarle a la API de otro con un modelo que no existe ahí.
   Es anterior a este trabajo (el parámetro se agregó cuando `Settings` exponía
   `llm_base_url`); ahora pasa el `base_url` y el vocabulario del catálogo.
## Métricas del STT que mentían

- **`stt_end_to_final_ms`** usaba `primer chunk + audio recibido / 1000` como
  reloj de "dejaré de hablar", lo que supone que el audio llega a velocidad real
  y sin pausas. Con el micrófono apagado 3 s entre dos frases de la misma
  conexión, la segunda daba **4805 ms** en vez de 1733 ms. Ahora se marca el reloj
  de pared en el último frame que el VAD llamó voz.
- **`stt_sessions_active` y `stt_inference_active`** leían su propio valor con
  `counter_value()`, que mira el mapa de contadores y siempre devuelve 0, así que
  el gauge era `0 + 1`. Ahora existe `gauge_value()`. El backend tenía el mismo
  error en `sessions_active`, y lo tuvo el primer tiempo después de arreglarlo:
  el `finally` nuevo usaba `counter_value()` porque era lo que estaba a mano.

## Archivos tocados

- **android**: `app.js`, `MainScreenState.kt`, `SignAiMetrics.kt`,
  `SignAiWebViewController.kt`, `SignAiCommands.kt`, `AvatarWebView.kt`,
  `MainActivity.kt`, `MainScreen.kt`, `BackendHealth.kt` (nuevo),
  `openspec/specs/ui/spec.md` (GLS-1, GLS-13, STA-15, STA-18, BRG-14, BRG-15,
  BRG-16, BRG-17, OBS-7), `docs/CAMBIOS.md`,
  `tools/medir-retraso.py`, `tools/medir-primera-sena.py`,
  `tools/verificar-tiempo-real.sh`.
- **backend**: `app/controller/router.py`, `app/controller/controller.py` (solo
  el docstring, que mentía), `app/metrics.py`, `app/main.py`,
  `app/config/settings.py`, `app/nlp/llm_client.py`,
  `app/nlp/nlp_service_impl.py`, `docs/sdd-changes.md`, `docs/CAMBIOSBACKEND.md`,
  `tools/verificar-conexiones-stt.py` (nuevo).
- **stt**: `app/session.py`, `app/metrics.py`, `app/main.py`, `Dockerfile`
  (comentario), `README.md`, `docs/CAMBIOSSTT.md`,
  `scripts/verify_session.py` (nuevo).

## Cosas que se encontraron y NO se tocaron

1. **`setQueue` resetea `queueIdx` a 0 aunque haya algo sonando.** Si una frase
   entra mientras la anterior se representa, el índice 0 de la nueva no llega a
   reproducirse. Es anterior a este trabajo; `appendQueue` lo hace más visible
   porque ahora la cola crece durante la reproducción. Anotado en el pendiente
   de `docs/CAMBIOS.md`.
2. **La app no se enteró de que el backend había reiniciado.** Después de un
   `docker compose restart backend`, la app siguió con `isConnected == true` y
   aceptó mandar una frase que no salió. Es del `SignAiWebSocketClient` (OkHttp no
   había detectado el cierre todavía). No se tocó; conviene revisarlo.
3. **`cache_lookup_ms` p50 48 ms por oración.** Con 3 oraciones eso es ~150 ms
   del total, y el medidor dice que un `HIT` "es rápido". No es un bug: es que
   `SentenceTransformer.encode` corre en `asyncio.to_thread` por oración. Queda
   anotado.
4. **Sin pruebas de regresión para la app.** Kotlin no tiene suite y sigue
   teniendo cero. Lo que se hizo fue probar en el dispositivo y leer logcat,
   que es más lento pero agarró los dos bugs de `appendQueue`.

## Terminología: "firmar" que sobrevivió al cambio de terminología

El 16 por la mañana se reemplazó `firmar` por `representar` en 211 lugares de 38
archivos. Quedaron cinco que el paso no agarró, todos por la misma razón (el
verbo en otra persona del indicativo):

- `sign-ai-backend/scripts/verify_client_frames.py:220` — `"nada se firmo solo"`
- `sign-ai-stt/README.md:134` — "el camino que firmaba por fragmentos"
- `sign-ai-stt/docker-compose.yml:29` — ídem
- `sign-ai-stt/docs/CAMBIOSSTT.md:28` — "el avatar firmaba a mitad de camino"
- `sign-ai-stt/app/transcriber.py:92` — ídem

**Sin corregir, a propósito:** el nombre de la métrica `duracion_firmado` en el
`resumen` de `SignAiMetrics` y las dos tablas de `docs/CAMBIOS.md` y
`tools/TIEMPO-REAL.md` que citan `Firma/habla`. Son el nombre de una métrica que
el plan de pruebas y el script de verificación leen por string, y renombrarlos es
cambio de contrato de salida, no de vocabulario. **Decisión del owner.**

## Lo que NO se hizo

- **No se commiteó nada**, en ninguno de los tres repos. Hay cambios sin
  commitear de la mañana (terminología + este archivo + la sonda) y los de esta
  tarde, y `flujo-oraciones` no se mergea todavía.
- **No se tocó `sign-ai-docs`.** Lo pendiente del lado del owner sigue igual:
  el envío oración-por-oración de *Propuesto* a *Acordado* en
  `modificaciones/requerimientos.md`, el tiempo de silencio en la configuración
  obligatoria de cada corrida (`plan-pruebas/00-bases/marco.md:176-179`), la
  salvedad de RF-INT-SW-04 en `trazabilidad.md:151`, y "reproducir" → "representar"
  en la tesis.
- **No se remedieron las 28 frases.** La línea base publicada sigue siendo del
  camino por palabras y hay que repetirla con la pila completa y la caché vacía
  antes de comparar nada. Es el pendiente 4 de `docs/CAMBIOS.md`.
- **P-1 (la espera de 1,5 s) sigue igual**: el valor se queda en 1500 y el
  experimento queda preparado, no ejecutado.

## Para retomar

```bash
# regresiones de D-2 y D-3
python3 tools/verificar-bloqueo-lectura.py          # en sign-ai-android
python3 tools/verificar-conexiones-stt.py 10        # en sign-ai-backend

# regresiones de la lógica del STT
docker compose -f ../signall-backend/docker-compose.yml run --rm --no-deps \
    -v "$PWD/scripts:/app/scripts" stt python -m scripts.verify_session

# la medición grande, con la caché vacía (si no, no mide nada)
#   DELETE FROM semantic_cache;  y  docker compose restart backend
python3 tools/medir-primera-sena.py
python3 tools/medir-retraso.py
```