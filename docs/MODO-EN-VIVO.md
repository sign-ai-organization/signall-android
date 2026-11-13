# Modo en vivo (rama `flujo-oraciones`) — referencia

Referencia, no reglas. Las reglas que aplican siempre están en `AGENTS.md`. Esto se
carga cuando la tarea toque la captura de micrófono, el WebSocket de traducción, la
cola de la escena o las métricas de latencia.

## Qué hace

The app signs **while the user speaks**, and the unit is the **sentence**. The pipeline is:
mic → PCM16/16 kHz → WebSocket → backend → STT → the phrase is split into sentences →
sentences are translated → the avatar signs them in order.

El cambio grande de esta rama (el plan del cambio está en
`../signall-backend/docs/CONTRASTE-BACKENDvsDOCUMENTOS.md` §10): antes el backend mandaba fragmentos (`stream: true, total: 0`) y el avatar representaba
**a mitad de frase**. Ahora solo hay oraciones, con `total` conocido desde el primero. El
efecto es que el avatar **no** empieza a representar hasta que termina la primera oración, y
`rt_first_sign_ms` sube por construcción. Es el costo consciente de la opción A (que se
explica en `tools/TIEMPO-REAL.md`): la línea base publicada —913 ms p50 / 1336 ms p95— es del camino por palabras
y **no es comparable**. Falta remedir.

**Leé `docs/CAMBIOS.md` antes de tocar esta área**, y
después `tools/TIEMPO-REAL.md`.

```bash
tools/verificar-tiempo-real.sh        # todo: servicios, tests, 28 fixtures, métricas, device
tools/verificar-tiempo-real.sh incremental
tools/medir-retraso.py                # ¿hace falta el adelanto automático? (ver abajo)
```

## How it works

- **El backend solo traduce `{"type":"text"}`.** El cliente manda la frase una sola vez
  cuando decide mandarla, y recibe un `segment` por oración con `index`/`total` reales.
- `MainScreenState.pendingSegments` es un `sortedMap` por `index` y la cola se arma en
  `flushSegments()` cuando `size >= total`. **No hay `streamMutex`** y no hace falta: el
  backend manda en orden.
- **`REALTIME_ENABLED` ya no existe.** No hay dos modos de backend: hay dos modos de
  **cliente**. El client manda `text` y el backend responde; el client manda voz y el
  backend transcribe. Un backend viejo que mande `stream`/`provisional` no rompe el parser,
  que es tolerante.

## El micrófono son dos interruptores, y apagado no es mandar

| | Botón | Qué hace al tocarlo | Qué representa |
|---|---|---|---|
| 🎤 | microfono (fila 1) | **dictado**: dicta, el silencio cierra la frase, el mic se apaga solo y **el texto espera** | nada, hasta que toques Traducir |
| ◢◢ | TR (fila 2) | **en vivo**: el interruptor continuo, el mic queda prendido | cada frase, en el acto |

Los dos se tocan una vez y quedan prendidos; ninguno se mantiene apretado. **No pueden
estar prendidos a la vez**: comparten el `AudioRecord` y el WebSocket.

Lo que hay que saber antes de tocar esto:

- **El estado del mic es `MainScreenState.micMode`**, un enum de tres valores
  (`Off`/`Dictation`/`Live` — `Live`, no `Realtime`: es el vocabulario de `trazabilidad.md`),
  y no un booleano. Dos flags dejarían "los dos prendidos" como un estado representable, y
  en ese estado la app se rompe. Todo cambio pasa por `changeMicMode()`.
- **`playbackState` ya no dice si el mic está prendido.** Un método nuevo que apague o
  encienda el mic tiene que tocar **los dos**.
- **Nunca uses `playbackState == Listening` como "el mic está prendido".** Con el mic
  prendido y el muñeco representando, el estado es `Signing`.
- **Solo `Live` es continuo.** En `Dictation` el `final` del STT apaga la captura, y por eso
  `dictationBuffer` ya no acumula entre frases: el STT corta a los 1,5 s de silencio.
- **Los dos interruptores se deshabilitan por el otro, en los dos sentidos, y ninguno se
  deshabilita mientras está prendido.** Prendido, cada uno es el único que puede cortar su
  modo. **Un control deshabilitado se marca apagando el icono y la etiqueta a `muted`, nunca
  bajándole el alfa a la superficie** (ver `SignAllBottomBar`).
- **Apagar el TR deja el campo limpio** (`stopCapture`), no retenido: para entonces lo que
  decía el campo ya se representó. Lo único que sobrevive es el texto de un dictado sin mandar.
  **Apagar el TR tampoco manda nada**: el en vivo manda cada frase al cerrarse, no al
  apagarse.
- **Lo ya enviado no se puede volver a mandar.** El campo conserva la frase en `readOnly`
  con Traducir deshabilitado durante `SentTextHoldMs` = **1500 ms** (`sentText`), y después
  se borra y el campo y Traducir vuelven **en la misma asignación**. Son **tres** los
  caminos que representan sin que nadie toque Traducir (en vivo, Traducir a mano y el Repetir);
  el dictado ya no es uno, porque su texto es del usuario. La retención se prende en
  `requestTranslation`, el punto donde se sabe que la frase salió de verdad. Hay log de los
  dos momentos: la ventana es más corta que el ida y vuelta de `mobile-mcp`, así que el
  logcat es la prueba y el árbol de accesibilidad la confirmación.
- **El tercer estado de RF-UI-01 se descartó** (decisión del owner el 2026-10-15, después de
  probarlo en el Redmi): el micrófono **no** cambia de icono y sigue siendo la única forma de
  dictar de nuevo. El aviso va en el **hint del escenario** ("Texto listo. Toca Traducir para
  representarlo."), no en el campo, porque con la frase escrita arriba un placeholder no se ve
  nunca. Tocar 🎤 con texto esperando **lo descarta**: es cancelación explícita, lo único que
  RNF-REL-04 permite como pérdida. **Esto es una desviación del documento de requisitos** —
  RF-UI-01 sigue diciendo tres iconos — y hay que decirlo.
- **La ruta del `final` la decide el cliente, no un flag.** El `dictation: true` que usaba
  el backend ya no existe: con el disparador del envío en la app, `micMode == Dictation ||
  dictationAwaitingFinal` alcanza. Un `final` vacío va a `onEmptyFinal` y no rompe nada.
- **Con el mic prendido, Traducir e Historial están deshabilitados y el input es
  `readOnly`.** Texto y voz van por el mismo socket y el backend no puede saber de quién
  es cada fragmento. El input sigue mostrando la transcripción en vivo, que es lo que el
  usuario quiere ver: por eso `readOnly` y no `enabled`.
- **En vivo, una frase que llega con otra en vuelo espera en `livePhraseBacklog`** — un
  único lugar, a propósito. Con más de uno sería una cola que hay que vaciar, y una cola que
  hay que vaciar es una cola que se pierde en algún `fail()`.

## Gotchas

- **El adelanto automático ya no existe** (2026-10-16, decisión D-5). `CATCHUP_BACKLOG`,
  `CATCHUP_FACTOR` y `state.catchUp` se borraron de `app.js`: la velocidad es un parámetro
  que fija el usuario y `setSpeed` la aplica tal cual. **Por qué**: (1) la prueba obligatoria
  **PR-PER-04** pregunta a usuarios con LSC si la velocidad es adecuada, y con el adelanto esa
  respuesta mezclaba la velocidad que eligió la persona con un factor que nadie le dijo;
  (2) quien lee el avatar es una persona sorda que no está mirando a quien habla, así que la
  carrera "terminar antes que el hablante" no la corre nadie; (3) no estaba en ningún documento
  de la tesis. `BRG-15` queda marcada como eliminada. Lo que sí es cierto y se sigue midiendo
  (`tools/medir-retraso.py`): las 32 señas duran 2,15 s de media, así que una frase larga se
  sigue representando después de que el hablante terminó. Eso ya no lo corrige el sistema.
- **La app representa cada oración apenas llega** (D-1). `flushSegments()` entrega lo pendiente
  en vez de esperar a `pendingTotal`, y `flushedThrough` la hace idempotente. La escena recibe
  `setQueue` + `play()` con la primera oración y `appendQueue` con cada una de las siguientes.
  Dos trampas que solo aparecen en el dispositivo: `appendQueue` **no** debe arrancar un ítem
  que ya se está cargando (`playFrom` corta con `state.loading`; si no, la misma seña arranca
  dos veces y su `signStart` sale duplicado), y **el índice de reanudación es `queueIdx + 1`**,
  no `queueIdx`: `advanceQueue` deja `queueIdx` en el último índice que llegó a sonar, así que
  reanudar en `queueIdx` repite la última seña. El `done` ya no arma la cola: cierra el turno,
  mide `metrics.onTurn` y suelta `livePhraseBacklog`. Medido en el Redmi: **95–114 ms** del
  `segment` al primer movimiento, contra ~569 ms parado.
- **`turnStartedAt` arranca cuando se manda la frase**, no al abrir el micrófono. La
  unidad es la frase completa, así que el tiempo que el usuario tarda en hablarla no es del
  turno de traducción. Lo que sí incluye es la espera por la frase anterior en vuelo.
  `turnStartedAt` mide **el pipeline**, no la escena: los números que miden la escena son
  `oracion→avatar:` (`SignAllMetrics.onSignStart`, el `segment` hasta que el muñeco se mueve) y
  `representado:` (`SignAllMetrics.onPlayback`).

  **Desde el 2026-11-11 `representado:` incluye el acomodo final**: los últimos 250 ms
  del clip se reparten en 700 ms y `onPlaybackDone` se manda cuando el peso del clip
  llega a cero, o sea **~450 ms después del final nominal** (a velocidad normal;
  medido en el Redmi: 2,574 s para `0028`, 2,125 s de clip). Las cifras anteriores de
  ese campo no son comparables con las nuevas sin descontarlo; con `reduceMotion` no
  se aplica y la salida termina con el clip.
- **Release the mic without speaking**: returns to Idle immediately with "No se escuchó
  nada." Waiting the full 15 s transcription timeout and then reporting a failure that did
  not happen is the old behaviour. `heardAnythingThisSession` separates "user said nothing"
  from "we lost the audio".
- **`pendingSegments`/`pendingTotal` son estado de turno**, los limpia `resetTurn()` en
  cada frase nueva y en `fail()`. Un turno que murió a mitad no puede filtrar oraciones al
  siguiente.
- **`fail()` limpia `dictationAwaitingFinal` pero no el texto**: un fallo de envío no es
  motivo para perder lo que el usuario dijo.