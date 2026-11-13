# CAMBIOS — Android (`flujo-oraciones`, 2026-10-15)

Qué cambió en la app para pasar del flujo **por fragmentos** al flujo **por
oraciones**, y qué tiene que hacer el resto del sistema con esto.

**Rama:** `flujo-oraciones`, desde `develop`. **Sin mergear.**

Plan completo: `../signall-backend/docs/CONTRASTE-BACKENDvsDOCUMENTOS.md` §10.
Log de la sesión: `openspec/sessions/2026-10-15-flujo-oraciones-android.md`.
Spec: `openspec/specs/ui/spec.md` (los 14 IDs que reescribió el plan de octubre:
`STA-9`, `STA-15` a `STA-20`, `GLS-11`, `GLS-12`, `BRG-14`, `BRG-15` y `OBS-1`
a `OBS-3`).

---

## 1. La unidad es la oración, no el fragmento

Fuera el camino por palabras:

| salía | qué era |
|---|---|
| `onStreamedSegment` |rutear un `segment` con `stream: true` a la cola, uno por uno |
| `streamMutex` | ordenar dos fragmentos que podían anexarse al revés |
| `streamedSegments`, `streamingGlosses`, `streamingTurn` | estado de turno del camino fragmentado |
| `finishStreamedTurn`, `resetStreamingTurn` | cierre de ese estado (`resetStreamingTurn` → **`resetTurn`**) |
| `sendDictation`, `dictationAutoSend`, `dictationOpen` | **apagar el micrófono mandaba la frase** |
| `sendMode()` | mandar `{"type":"mode"}` al backend |
| `BackendEvent.Segment.stream` / `.provisional` / `.sourceText` | campos del fragmento |
| `BackendEvent.Transcript.dictation` | flag con el que el backend decía de dónde venía el `final` |
| `MicMode.Realtime` | renombrado a **`Live`** ("en vivo", el vocabulario de `trazabilidad.md`) |

**El parser quedó tolerante**: un backend viejo que mande esos campos no rompe
nada, porque el frame es el mismo que usaba el camino de frase completa.

Ahora el turno es:

```
pendingSegments   sortedMap<Int, TranslationSegment>   uno por index
pendingTotal      el total del primer segment que llega
flushSegments()   cuando pendingSegments.size >= pendingTotal
```

**No hay mutex**: el backend traduce las oraciones en paralelo pero las **manda
en orden**, y el `sortedMap` las ordena igual si llegaran desordenadas.

---

## 2. Apagar no es mandar (RF-STT-03)

Esto es el cambio central. Antes, `stopDictation` llamaba `sendDictation()` →
`play()` → `requestTranslation()`: **apagar el micrófono mandaba la frase**. Por
eso `trazabilidad.md` §2.1 tenía RF-STT-03 y RNF-REL-04 en "No implementado".

Ahora:

- El `final` del STT **apaga la captura** — salvo que TR esté prendido.
- El texto queda a la espera de que el usuario toque Traducir.
- `stopDictation(endIsRedundant)` no manda un `end` de más cuando la pausa del STT
  ya cerró la frase.

Estado nuevo:

| | qué es |
|---|---|
| `dictationAwaitingFinal` | se mandó `end` y el `final` todavía no llegó |
| `dictationPending` | hay texto dictado esperando confirmación |
| `dictationReadyToSend` | el campo tiene ese texto, el mic está apagado y no hay retención |
| `onEmptyFinal(final)` | el `final` llegó sin palabras: "no se escuchó nada" |
| `livePhraseBacklog` | **una** frase en vivo esperando hueco (ver §4) |

`RNF-REL-04` quedó cubierto: una caída de red con texto esperando no lo borra.
Lo único que lo descarta es que el usuario toque el micrófono —cancelación
explícita, que es lo único que RNF-REL-04 permite como pérdida.

---

## 3. El en vivo manda solo; el resto espera

```
🎤 dictado   se toca → se habla → el silencio cierra la frase → el mic se apaga
             solo → el texto ESPERA → Traducir manda

◢◢ TR en vivo  se toca → el mic queda prendido → cada frase sale SOLA al cerrarse
               → el interruptor solo apaga el micrófono
```

**Solo el en vivo manda sin que nadie toque Traducir.** Antes los dos lo hacían.

Estado del campo al apagar cada uno:

| | al apagar |
|---|---|
| **Dictado** | el texto queda, editable, con su aviso |
| **En vivo** | el campo queda **limpio** |

El campo limpio del en vivo es pedido del owner: para cuando se apaga el
interruptor lo que decía el campo ya se mandó y se representó, así que no hay nada
que impedir reenviar — y con Traducir de vuelta a estar disponible, dejarlo
puesto era una invitación a representarlo otra vez. Lo único que sobrevive es el texto
de un dictado sin mandar, que es del usuario.

---

## 4. En vivo, una sola frase puede esperar

`livePhraseBacklog` es **un único lugar**, a propósito: si el LLM tarda más que la
pausa de 1,5 s, la frase siguiente espera ahí. Con más de uno sería una cola que
hay que vaciar, y una cola que hay que vaciar es una cola que se pierde en algún
`fail()`.

`releaseLiveBacklog()` la manda cuando la anterior dejó de estar en vuelo. Si
todavía no puede, **no se manda igual**: una frase que sale sin que el usuario lo
decida es exactamente lo que RF-STT-03 prohíbe. Se muestra para que la pueda
mandar a mano.

---

## 5. Desviación de un requisito: RF-UI-01

**Esto hay que propagarlo al documento de requerimientos del owner.**

RF-UI-01 (v0.2) pide **tres iconos** en el botón de micrófono y que en el tercero
la frase se envíe al NLP. Se implementó así y se probó en el Redmi: **con un
icono distinto el botón deja de leerse como "micrófono"**, y la primera reacción de
quien lo usa es querer dictar otra vez, no confirmar.

Quedó:

- El micrófono **no** cambia de icono y sigue siendo la única forma de dictar de
  cero. Tocarlo con texto esperando lo descarta y vuelve a dictar.
- El aviso de que hay texto listo va en el **hint del escenario**:
  *"Texto listo. Toca Traducir para representarlo."* En el campo no sirve —el
  placeholder solo se ve cuando el campo está vacío, que es justo cuando el
  aviso importa—.
- El envío es Traducir.

> RF-UI-01 sigue diciendo tres iconos y su criterio de aceptación ("al presionarlo
> en el último estado el texto se envía al NLP") **no** está implementado, a
> propósito.

---

## 6. Métricas del teléfono

`SignAllMetrics` perdió lo de fragmento:

| antes | ahora |
|---|---|
| `onFragment(...)` | — |
| `onTurn(totalMs, fragmentCount, glossCount)` | **`onTurn(totalMs, sentenceCount, glossCount)`** |
| `fragmento #N` en el resumen | `oraciones=N` |

El resumen (`resumen:` en logcat) queda:

```
frases=2 oraciones=3 glosas=11 sin_seña=0 dictado_frases=0 dictado_palabras=0
duracion_frase=p50=1178ms p95=1178ms max=7802ms n=2 duracion_representada=n/a
```

`duracion_representada` (`representado:`) es lo único que mide la escena; `duracion_frase`
mide el turno de traducción. **Son dos cosas distintas** y no se mezclan.

`turnStartedAt` ahora arranca **cuando se manda la frase**, no al abrir el
micrófono: con la unidad siendo la frase completa, el tiempo que el usuario tarda
en hablarla no es del turno de traducción.

---

## 7. El adelanto automático: se quedó el 15 y se quitó el 16 (decisión D-5)

> **Estado actual: no existe.** El 2026-10-16 la decisión **D-5** borró
> `CATCHUP_BACKLOG`, `CATCHUP_FACTOR`, `state.catchUp` y la rama de `tickPlayback`
> de `app.js`, y `BRG-15` quedó marcada como eliminada. El motivo no fue técnico:
> la prueba **PR-PER-04** le pregunta a usuarios de LSC si la velocidad es
> adecuada, y con el adelanto esa respuesta mezclaba la velocidad que eligió la
> persona con un factor que nadie le dijo. Lo que sigue es **lo que se midió el
> 2026-10-15**, cuando todavía existía: se conserva como registro, no como
> comportamiento.

`tools/medir-retraso.py` mide, con los 28 fixtures reales y las duraciones del
`manifest.json`:

| | |
|---|---|
| El avatar no termina antes de que el usuario termine de hablar | **17 de 28** |
| …ni con el adelanto 1,6× | **7 de 28** |
| Representación / habla | mediana **1,26×** (máx. 3,72×) |
| Con el adelanto | mediana 0,79× |
| Desfase absoluto | mediana **+0,4 s** |

**El desfase no lo causaba el modo de entrega**: lo causan los 2,15 s de animación
contra la velocidad del habla. Por eso el 15 se decidió mantenerlo —la cola está
vacía mientras el usuario habla y se llena de golpe al cerrar la frase, así que el
adelanto prendía y apagaba dentro de una misma conversación—.

Ese argumento técnico no cambió. Lo que cambió fue **qué se le puede preguntar a
una persona**: por eso D-5 lo quitó al día siguiente. Hoy `setSpeed` es el único
ajuste de velocidad y se aplica tal cual.

---

## 8. `app.js`: adelantos de la escena, sin cambios de comportamiento

**Ojo: el 2026-10-16 D-5 eliminó la sección entera** (ver §7). Lo que sigue
describe lo que pasó el 15; hoy el comentario de `app.js` documenta por qué ya no
hay adelanto automático.

`BRG-14` y `BRG-15` de la spec. Lo único que cambió es el comentario de la sección
"Adelanto automático", que afirmaba cosas que la medición desmintió ("2-3 palabras
por segundo", "con la primera frase ya está dos señas de atraso").

`appendQueue` / `playFrom` / `queueState` **siguen en el puente y no los llama
nadie**: nacieron para el camino por fragmentos. Se quedan por compat —
`BRG-8` declara el contrato retrocompatible y quitarlos lo rompería sin ganar nada.

---

## 9. Documentación que se actualizó

| archivo | qué |
|---|---|
| `openspec/specs/ui/spec.md` | los 14 IDs que reescribió el plan de octubre |
| `AGENTS.md` | sección de modo en vivo, contrato entre repos, observabilidad, rama |
| `tools/TIEMPO-REAL.md` | reescrito entero |
| `tools/verificar-tiempo-real.sh` | ver más abajo |
| `openspec/sessions/2026-10-15-flujo-oraciones-android.md` | el log |

### `tools/verificar-tiempo-real.sh` estaba roto

Llamaba a `verify_incremental.py`, `verify_voice_modes.py` y
`verify_realtime_latency.py`, que **solo existen en la rama `develop` del
backend**. El script fallaba siempre y no verificaba nada. Ahora corre
`verify_sentence_segmenter.py` y `verify_client_frames.py` (que existen en las dos
líneas) y las dos mediciones nuevas. Los bloques `incremental` y `voz` dicen por
qué ya no existen.

---

## 10. Herramientas nuevas

```bash
tools/medir-primera-sena.py   # de dónde sale la primera seña (frío y caliente)
tools/medir-retraso.py        # ¿el avatar alcanza a representar mientras hablás?
```

Ambas corren los 28 fixtures de `../signall-stt/fixtures/audio` por el WebSocket
real, sin micrófono.

**La línea base nueva** (28 fixtures, backend y STT reales, `qwen3:4b-instruct` en
Ollama local, caché semántica vacía):

| | p50 | p95 |
|---|---|---|
| `rt_first_sign_ms` (final → primera oración) | 273 ms | 422 ms |
| Ídem con la caché caliente | 1 ms | 4 ms |
| Primer sample de audio → primera seña | 4010 ms | — |
| …de los cuales el STT | 3709 ms | — |

**El pipeline no es el cuello de botella: el STT lo es.**

No se puede comparar con la línea base publicada (p50 913 ms / p95 1336 ms): esa
es del camino por palabras.
---

## 2026-10-16 — Cada oración cuando llega, y sin adelanto automático

Decisiones D-1, D-5 y D-6 de
`openspec/sessions/2026-10-16-latencia-y-decisiones.md`, más la métrica del
tramo que no lo tenía (oración recibida → avatar moviéndose).

### El avatar representa cada oración apenas llega

`flushSegments()` ya no espera a que la frase esté completa: entrega lo que
haya. La escena recibe `setQueue` + `play()` con la primera oración y
`appendQueue` con cada una de las siguientes — los dos comandos ya existían en
el puente y no los llamaba nadie (`BRG-14`) —.

Medido en el Redmi con una frase de tres oraciones: la primera seña se movió
**95-114 ms** después de que llegara su `segment`, contra los ~569 ms que
costaba esperar a las otras dos.

Dos cosas que hubo que arreglar sobre la marcha, y que sólo aparecieron
probando en el dispositivo:

- `appendQueue` arrancaba el ítem que ya estaba cargando, así que la misma
  seña arrancaba dos veces y su `signStart` salía duplicado. Ahora corta
  también con `state.loading`.
- Cuando la cola se había vaciado mientras la oración se traducía, el
  reinicio usaba `queueIdx` a secas y **repetía la última seña** en vez de
  seguir con la siguiente. Ahora reanuda en `queueIdx + 1` si la cola ya
  arrancó, y en 0 si nunca arrancó. Reproducido en el Redmi con una tercera
  oración que tardó 7,6 s.

El `done` ya no arma la cola: cierra el turno, y ahí se mide `metrics.onTurn`
y se suelta la frase en vivo que estuviera esperando. Soltarla con la primera
oración dejaría pasar una segunda frase mientras la anterior todavía se
traduce.

### El adelanto automático se quitó

`CATCHUP_BACKLOG`, `CATCHUP_FACTOR`, `state.catchUp` y la rama de
`tickPlayback` ya no existen. `setSpeed` es el único ajuste de velocidad y se
aplica tal cual. `BRG-15` queda marcada como eliminada.

El motivo decisive no fue técnico: la prueba obligatoria **PR-PER-04** le
pregunta a usuarios con LSC si la velocidad es adecuada, y con el adelanto esa
respuesta mezclaba la velocidad que eligió la persona con un factor que nadie
le dijo. `tools/medir-retraso.py` dejó de calcular las columnas "con el
adelanto" y ahora responde otra pregunta: cuánto se sigue representando
después de que el hablante terminó.

### El tiempo de silencio, a la vista

El overlay de depurar muestra `silencio STT: 1500ms` (`BRG-17`). El valor
viene del `/health` del backend, que lo copia del STT cada 30 s; la app nunca
habla con el STT.

### La métrica que faltaba

`signStart` (JS→Kotlin, `BRG-16`) avisa que el muñeco se está moviendo, con
el reloj propio de JS. Con eso existe por primera vez el tramo de **oración
recibida → avatar moviéndose**, que antes no lo medía nadie: ni el backend
(cronometra hasta mandar el `segment`) ni el teléfono (`play()` se llama antes
de que el `.glb` termine de bajar). Sale en `SignAllMetrics` como
`oracion_a_avatar`.

### Dos herramientas que faltaban

- `tools/verificar-tiempo-real.sh` ya no da por buena una corrida de latencia
  hecha con la caché semántica llena: en ese caso el p95 sale de 5 ms, pasa
  cualquier presupuesto y no midió nada. Ahora esa corrida se declara **no
  medible** con la instrucción para medir en frío.
- `scripts/verify_session.py` del STT cubre la lógica de `session.py`.

---

## Pendiente

> Lista revisada el 2026-10-23: se sacó el **#6**, que era el **#3** repetido casi
> palabra por palabra, y el **#5**, que ya estaba hecho (`tools/README.md` documenta
> las dos herramientas; `tools/HERRAMIENTAS.md` es la referencia del control del
> móvil, así que no es su lugar).

1. **Propagar la desviación de RF-UI-01** al documento de requerimientos y a
   `trazabilidad.md`: RF-UI-01 cambia de criterio, y RF-STT-03 y RNF-REL-04 pasan
   de "No implementado" a implementado.
2. **Repetir `medir-primera-sena.py` con el LLM de producción.** Los 273 ms son
   de `qwen3:4b-instruct` en Ollama local.
3. **Prueba de regresión del eco**: el auto-envío en vivo no debe poder dispararse
   por un frame que no venga del STT. El backend ya está arreglado y con test; en
   la app es una guarda de una línea.
4. **Repetir la medición de las 28 frases** con la pila completa y la caché
   vacía, ahora que el avatar representa oración por oración: la línea base
   publicada (913 ms p50) sigue siendo del camino por palabras y no es
   comparable.
5. `setQueue` resetea `queueIdx` a 0 aunque haya algo sonando: si una frase
   entra mientras la anterior se representa, el índice 0 de la nueva no llega a
   reproducirse. Es anterior a este trabajo y no se tocó, pero `appendQueue` lo
   hace más visible porque ahora la cola crece durante la reproducción. Candidato
   a la próxima sesión.