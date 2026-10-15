# Flujo por oraciones — fase 3 (Android)

**Día:** miércoles 2026-10-15 · **Rama:** `flujo-oraciones` (sin mergear)

La tercera y última fase de `../sign-ai-docs/auditoria-flujo-2026-10-15.md` §6.3:
sacar de la app el camino por palabras y dejar dos modos que tienen reglas
distintas — el dictado **espera** y el en vivo **manda solo** — con la oración como
unidad de entrega.

Fases 1 y 2 (backend y STT) están en sus repos; acá está el resumen de las tres
y el detalle de esta.

---

## Lo que cambió en la app

### El camino por palabras, afuera

`onStreamedSegment`, `streamMutex`, `streamedSegments`, `streamingGlosses`,
`streamingTurn`, `finishStreamedTurn`, `resetStreamingTurn` (que pasó a ser
`resetTurn`), `sendDictation`, `dictationAutoSend`, `dictationOpen`, `sendMode()`, y
los campos `stream` / `provisional` / `sourceText` de `BackendEvent.Segment`, más
`dictation` en `Transcript`. `MicMode.Realtime` pasó a **`Live`** ("en vivo", el
vocabulario de `trazabilidad.md`).

El parser quedó **tolerante**: un backend viejo que mande esos campos no rompe
nada, y el frame es el mismo que usaba el camino de frase completa.

### Apagar no es mandar (RF-STT-03)

Esto es lo central. Antes `stopDictation` llamaba `sendDictation()` → `play()` →
`requestTranslation()`: **apagar el micrófono mandaba la frase**. Ahora:

- El `final` del STT **apaga la captura** (`stopDictation`), salvo que TR esté
  prendido.
- El texto queda a la espera de que el usuario toque Traducir.
- Aparecen `dictationAwaitingFinal`, `dictationPending`, `onEmptyFinal` y
  `stopDictation(endIsRedundant)`.
- El aviso va al **hint del escenario** ("Texto listo. Toca Traducir para firmarlo."),
  no al campo.

`RNF-REL-04` también quedó cubierto: una caída de red con texto esperando no lo
borra, y lo único que lo descarta es que el usuario toque el micrófono (cancelación
explícita).

### En vivo

`queueLivePhrase` / `sendLivePhrase` / `livePhraseBacklog` / `releaseLiveBacklog`.
Una frase en vivo sale sola al cerrarse; si hay otra en vuelo espera en **un único**
lugar (`livePhraseBacklog`) — con más de uno sería una cola que hay que vaciar, y
una cola que hay que vaciar es una cola que se pierde en algún `fail()`.

---

## Lo que decidió el owner probando en el Redmi

Dos cosas que **no** eran lo que el plan decía, y que están registradas acá porque
la próxima sesión las va a encontrar en el código y no en el documento:

### 1. Se descartó el tercer estado de RF-UI-01 — **desviación del requisito**

RF-UI-01 (v0.2) pide tres iconos en el botón de micrófono y que el tercero mande
la frase. Se implementó así y se probó en el dispositivo: **con un icono distinto
el botón deja de leerse como "micrófono"**, y la primera reacción de quien lo usa
es querer dictar otra vez, no confirmar.

Quedó: el micrófono **no** cambia de icono y sigue siendo la única forma de dictar
de cero. El aviso de que hay texto listo va en el hint del escenario. El envío es
Traducir.

> **Esto hay que propagarlo al documento de requerimientos del owner.** RF-UI-01
> sigue diciendo tres iconos, y su criterio de aceptación ("al presionarlo en el
> último estado el texto se envía al NLP") **no** está implementado, a propósito.

### 2. El aviso no va en el placeholder

La primera versión lo puso en el campo, con el texto "Tocá Traducir para firmar.".
Es **invisible**: el placeholder solo se ve cuando el campo está vacío, y con la
frase dictada arriba el campo nunca está vacío. Es exactamente el caso para el que
el aviso existe. Va al hint del escenario, que es donde ya viven los mensajes
transitorios ("Ya está firmado.").

### 3. Apagar el TR deja el campo limpio

Pedido del owner a mitad de la sesión. `stopCapture(Live)` ya no hace
`holdSentText()`: para cuando se apaga el interruptor lo que decía el campo ya se
mandó y se firmó, así que no hay nada que impedir reenviar — y con Traducir de
vuelta a estar disponible, dejarlo puesto era una invitación a firmarlo otra vez.
Lo único que sobrevive es el texto de un dictado sin mandar.

---

## Un bug de contrato que solo apareció en el dispositivo

`handle_text` en el backend hacía eco de la frase recibida como un
`transcript` con `final: true`. Venía de `main` y era inofensivo cuando **el
backend** decidía qué traducir. Al mover el disparador a la app, `onLiveTranscript`
tomaba ese eco por una frase de voz y la reenviaba:

```
frase #2875 y subiendo · 44 % de CPU · rt_phrases_total 3731
```

Arreglado quitando el `await self.send_transcript(text, True)` de `handle_text`, y
fijado con `verify_client_frames.py` (`THREE_MESSAGES = ["segment","segment","done"]`
y `read_exactly(ws, 3)`: exactamente tres mensajes, en ese orden, en una lectura).

**Vale la pena una prueba de regresión en la app**: el auto-envío en vivo no
debe poder dispararse por un frame que no venga del STT.

---

## Lo que se midió (§6.3 punto 5 y §7 de la auditoría)

### El adelanto automático: **se queda**

`tools/medir-retraso.py` (nuevo) manda los 28 fixtures por el socket y compara
cuánto tarda el usuario en decir cada frase con cuánto tarda el avatar en firmarla,
usando las duraciones reales del `manifest.json`.

| | |
|---|---|
| Avatar no termina antes de que el usuario termine de hablar | **17 de 28** |
| …ni con el adelanto 1,6× puesto | **7 de 28** |
| Firma/habla | mediana **1,26×**, máx. 3,72× |
| Con el adelanto | mediana 0,79× |
| Desfase absoluto | mediana **+0,4 s** |

**El desfase no lo causaba el modo de entrega**, así que el adelanto no se borra: lo
causan los 2,15 s de animación contra la velocidad del habla. Lo que **sí** cambió
es la forma de la cola: con oraciones está **vacía mientras el usuario habla y se
llena de golpe** al cerrar la frase. Como el umbral se mide después de que arranque
la seña en curso, hace falta una frase de 3+ señas para dispararlo, y con las 28
frases de prueba —cortas, mediana 2 palabras y 1 seña— solo una lo dispara.

Los números (`CATCHUP_BACKLOG = 2`, `CATCHUP_FACTOR = 1.6`) **no se tocaron**.

### La primera seña: **remedida** (`tools/medir-primera-sena.py`, nuevo)

28 fixtures, backend y STT reales, `qwen3:4b-instruct` en Ollama local, caché
semántica vacía:

| | p50 | p95 |
|---|---|---|
| **`rt_first_sign_ms`** (transcripción final → primera oración) | **273 ms** | **422 ms** |
| Ídem con la caché caliente | 1 ms | 4 ms |
| Primer sample de audio → primera seña (**lo que percibe el usuario**) | **4010 ms** | — |
| …de los cuales el STT | 3709 ms | — |

**El pipeline no es el cuello de botella: el STT lo es.** De los 4,0 s, 3,7 s son el
STT esperando el cierre por los 1,5 s de silencio y transcribiendo. Traducir cuesta
273 ms.

**Esto no se puede comparar** con la línea base publicada (p50 913 ms / p95
1336 ms): esa es del camino por palabras. El nombre `rt_first_sign_ms` se conservó
para que la comparación sea visible, pero no vale.

---

## La base de las ramas: decisión del owner, no un accidente

**`sign-ai-backend` y `sign-ai-stt` salen de `origin/main`, y `main` de esos repos
es anterior a todo el trabajo de tiempo real.**

```
sign-ai-backend:  develop tiene 8 commits que main no tiene
sign-ai-stt:      develop tiene 4 commits que main no tiene
```

Eso se vio a mitad de esta sesión y en un primer momento quedó registrado acá como
"hallazgo pendiente de decisión". **No lo es: está decidido y escrito.** Ver
`Revisión de develop.md` (nota del owner en la raíz de este repo, 2026-10-15):

> **Base recomendada.** `origin/main` del backend ya contiene lo útil
> (normalización del catálogo y frames `text`/`end`), sin nada de tiempo real.
> En STT, el commit `e1da961`.

La decisión es volver a `main` y **dejar atrás** el trabajo de tiempo real, no
portarlo. Eso es lo que hace que esta rama sea mucho más chica de lo que parecía:
el camino por palabras nunca se mezcló con el flujo por oraciones y no hay nada
que sacar, porque en `main` no estaba.

**Que nadie lo "arregle"**: rebasar estas ramas sobre `develop` sin preguntar
reintroduce el perfil de tiempo real del STT, `app/realtime/` en el backend y las
métricas de fragmento — que es justo lo que esta rama vino a desarmar.

Efectos colaterales que sí hubo que arreglar, y que son bugs y no decisiones:

1. `tools/verificar-tiempo-real.sh` de este repo llamaba a
   `verify_incremental.py`, `verify_voice_modes.py` y `verify_realtime_latency.py`,
   que **solo existen en `develop`**: el script fallaba siempre y no verificaba
   nada. Ya corre `verify_sentence_segmenter.py` y `verify_client_frames.py`, que
   existen en las dos líneas, más las dos mediciones nuevas. Los bloques
   `incremental` y `voz` dicen por qué ya no existen.
2. El `Dockerfile` del backend ahora copia `scripts/`: los `verify_*.py` corren
   **dentro** del contenedor porque necesitan `spacy` y `httpx2`, y sin el `COPY`
   no estaban en la imagen.
3. Los `verify_*.py` se corren **como módulo** (`python -m scripts.verify_client_frames`),
   no como `python scripts/...`. Corridos así, `sys.path[0]` es `/app/scripts` y
   `import app` no resuelve. Esto ya estaba mal en el script viejo.
4. El `p95` del presupuesto se extraía con `grep -oE '[0-9]+'` sobre `p95 5`, que
   devuelve **dos** números (`95` del nombre de la métrica y `5` del valor) y el
   `(( ))` lo leía como `955`. Ahora es un `sed`.

---

## Verificado en el dispositivo físico (Redmi Note 9S, `1ed6699f`)

- **Dictado con voz**: el texto va al campo, el mic se apaga solo en el silencio,
  el muñeco **no** se mueve y `rt_phrases_total` **no se mueve** en el backend.
  RF-STT-03 funcionando.
- **En vivo**: la frase sale sola al cerrarse y firma.
- **`final` vacío** (el que devuelve el `end`): manejado en los dos modos, sin
  colgarse.
- **Aviso "Texto listo. Toca Traducir para firmarlo."** en el hint del escenario.

Las pruebas de voz necesitan una persona: la app usa `AudioSource.VOICE_RECOGNITION`,
que **cancela el eco del propio altavoz**, así que un bucle de retorno acústico es
imposible. Ya está pisado y documentado en `tools/TIEMPO-REAL.md` §8.

---

## Documentación que se actualizó

- `openspec/specs/ui/spec.md` — los 14 IDs del §6.3 punto 6 (`STA-9`, `STA-15` a
  `STA-20`, `GLS-11`, `GLS-12`, `BRG-14`, `BRG-15`, `OBS-1` a `OBS-3`). De paso se
  escaparon dos pipes sin escapar que rompían la tabla de markdown en `EDG-23` y
  `OBS-6` (preexistente, no de este trabajo).
- `AGENTS.md` — la sección de modo en vivo entera, los bullets del contrato entre
  repos, la tabla de observabilidad y la nota de rama.
- `tools/TIEMPO-REAL.md` — reescrito entero.
- `tools/README.md` y `tools/HERRAMIENTAS.md` — falta agregar las dos herramientas
  nuevas.

## Pendiente

- [ ] Decidir **cuándo** se mergea `flujo-oraciones` a `main` en los tres repos.
      La base ya está decidida (ver arriba); lo que falta es el momento.
- [ ] **Propagar la desviación de RF-UI-01** al documento de requerimientos
      (`../sign-ai-docs/documentos/requerimientos/`) y a `trazabilidad.md`: RF-UI-01
      va de "Parcial" a un criterio distinto, y RF-STT-03 y RNF-REL-04 de "No
      implementado" a implementado.
- [ ] Repetir `medir-primera-sena.py` con el LLM de producción y no con Ollama
      local: la cifra de 273 ms es de `qwen3:4b-instruct`, no de OpenAI.
- [ ] Probar en el dispositivo una frase larga en en vivo, para ver el adelanto
      automático prenderse y apagarse dentro de una misma conversación (necesita voz
      o escribirla en el campo, que tampoco da 3+ señas).