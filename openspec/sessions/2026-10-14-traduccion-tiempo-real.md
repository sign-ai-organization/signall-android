# Traducción en tiempo real

**Día**: martes 2026-10-14
**Rama**: `tiempo-real` en los tres repos (`sign-ai-android` desde `main`, `sign-ai-backend` y `sign-ai-stt` desde `develop`)
**Autorizado por el owner**: commit y push directo a esas tres ramas.

---

## Qué se pidió

Que la app traduzca **en tiempo real**: decís "hola", el micrófono lo escucha, y
el avatar firma HOLA mientras todavía estás hablando. Con la menor latencia
posible. El objetivo no era "más rápido que antes" sino que se sintiera en vivo.

## Qué estaba ahí

El cableado ya existía entero: `AudioCapture` con `AudioRecord`, el WebSocket de
`/ws/translate`, el reenvío al STT, `SignsCatalog`. Lo verificado el 2026-10-14
en la sesión anterior. Lo que faltaba era la **cadencia**: todo esperaba al
`final`.

La línea de tiempo era: hablar → 1500 ms de hangover de silencio → 350–800 ms de
Whisper → ~200 ms de caché en Neon o 300–1900 ms de LLM → el avatar firma.
Entre 2.4 y 4.8 segundos después de que dejabas de hablar.

## Los cuatro cuellos de botella que había en el camino

Cada uno estaba medido, no supuesto. Los números están en
`../sign-ai-backend/docs/performance.md` y `../sign-ai-stt/docs/performance.md`.

1. **El bucle que leía el STT traducía adentro.** Con el LLM en vuelo no sacaba
   el siguiente mensaje del socket, así que un `final` podía quedarse esperando
   mientras el avatar esperaba por él. La traducción fue a una tarea aparte con
   cola.

2. **El caché semántico consultaba Neon en cada lookup.** ~200 ms con acquire y
   release, contra 270–440 ms de la llamada al LLM que estaba evitando: el caché
   era más lento que lo que ahorraba. Ahora la tabla se carga entera en memoria
   al arrancar (46 filas, 662 ms) y la búsqueda es distancia coseno con numpy,
   con un camino de texto exacto que ni siquiera calcula el embedding.

3. **Los picos de 4 a 8 segundos.** Whisper serializa igual por abajo
   (CTranslate2 con `num_workers=1`), así que sin un orden explícito el orden de
   llegada era el de ejecución: un parcial despachado antes que un final corría
   primero y el usuario esperaba los dos. `InferenceScheduler` pone los finales
   delante. Espera por la GPU ahora: p95 0.8 ms.

4. **El filtro de confianza se comía audio real.** `AVG_LOGPROB_THRESHOLD=-0.8`
   descartaba frases que Whisper había transcrito bien: "cómo estás" y "estoy
   triste" salían como transcripción **vacía**, y un final vacío es indistinguible
   de un micrófono roto. Volvió a `-1.0`, el default de Whisper. Y
   `transcribe_buffer` ahora devuelve `drop_reason` para que un vacío se pueda
   explicar.

## Decisiones de diseño

**Qué se firma, y cuándo.** El STT manda una hipótesis que crece:
`"hola"` → `"hola como"` → `"hola como estas"`. Traducir cada una entera produce
trabajo duplicado y una salida incoherente: el avatar firmaría HOLA, después
HOLA COMO, después HOLA COMO ESTAS. Así que se traduce **solo la cola nueva**.

La regla de "qué es cola nueva" son los **timestamps por palabra** que ahora manda
el STT (`words`, con inicio y fin de cada una). Una palabra está firme cuando
terminó al menos 180 ms antes del final del audio capturado. Eso responde una
pregunta que el texto solo no puede responder: ¿esta persona ya terminó de decir
"estas", o todavía la está formando? Sin timestamps queda un plan B más grueso
("todo menos la última palabra"), que es peor y por eso los timestamps son el
camino principal.

**Append-only.** Una palabra firmada no se vuelve a mirar, ni aunque el STT la
corrija después. Des-firmar una seña que ya se está animando en pantalla no es
posible, y volver a firmarla sería peor que esperar un instante con el texto
viejo. Se registra en log cuando pasa.

**Contexto de solo lectura.** Sin contexto, el modelo responde un fragmento como
si fuera la frase entera: "estudiante" solo dio NIÑO, una seña equivocada que el
avatar firmaba con total confianza. Con "me llamo ana" de contexto, da
ESTUDIANTE. El primer fragmento de un utterance no lleva contexto porque es el
único que con suerte es una frase, y ese sí entra por el camino cacheable.

**Solo gramática no se firma.** "me" a secas dio YO y "en" a secas dio DONDE. La
lista de palabras funcionales es **corta a propósito** y el filtro es de todo-o-
nada: solo descarta un fragmento cuando *todas* sus palabras son funcionales.
"como", "donde", "cuando", "que" y "mas" son glosas del catálogo o parte de una
frasal, ySACAR la "o" de "más o menos" es exactamente la regresión que el prompt
de glosas frasales vino a arreglar.

**Dos modelos en el STT.** Un parcial se tira a la basura en cuanto llega el
final, así que no necesita la precisión de `medium`. `small` cuesta p50 92 ms
contra 249 ms. Cae a un solo modelo si los tamaños coinciden o si la VRAM no
alcanza.

**Adelanto automático en el avatar.** Las 32 señas exportadas duran 2.08–2.75 s
(promedio 2.15 s, del `manifest.json`). El español corre a 2–3 palabras por
segundo y cada palabra es una seña: a velocidad normal el avatar va dos señas
atrasado antes de terminar la primera frase y nunca alcanza. La traducción es
rápida; la animación es el cuello de botella. Con dos o más señas en cola la
escena firma a 1.6× y vuelve atrás en cuanto se vacía.

## Tres bugs de corrección que solo se ven cuando corre

Los tres producían una seña **equivocada**, no una ausente, que es el modo de
falla que más importa cuando el producto es un intérprete de señas.

1. **El índice en memoria comparaba vectores sin normalizar con producto punto.**
   El `<=>` de pgvector normaliza internamente y `SentenceTransformer.encode` no,
   así que un fragmento corto daba distancia negativa y *todo* era HIT. Cada
   fragmento respondía HOLA.

2. **El matching por similitud cruzaba fragmentos distintos.** El umbral de 0.05
   está calibrado para frases completas, donde "cómo estás" y "¿cómo estás?" sí
   son la misma seña. Aplicado a fragmentos de una o dos palabras dejó de ser
   equivalencia y pasó a ser coincidencia: `"me llamo ana vivo en || bogota
   soy"` caía a menos de 0.05 de `"me llamo ana vivo en || vivo en"` y devolvía
   VIVIR,DONDE. Los fragmentos ahora usan coincidencia literal, y del mismo modo
   "me llamo bob" habría recuperado NOMBRE,NIÑA de "me llamo ana".

3. **Un `settled_count` contado en un espacio y usado en otro.** Contaba sobre
   `words` (que incluye los rellenos filtrados) e indexaba `tokens` (que no los
   tiene), así que firmaba media palabra de más. `_tokenize` y `_settled_count`
   aplican ahora el mismo filtro.

## Qué se midió

Sobre los **28 fixtures de audio** de `../sign-ai-stt/fixtures/audio/`,
reproducidos por el WebSocket real a velocidad 1x, con GPU libre:

| | antes | después |
|---|---|---|
| Primera seña (p50 / p95) | — | **913 ms / 1336 ms** |
| Dejar de hablar → final | 1813 – 2099 ms | **839 ms / 958 ms** (p50 / p95) |
| Inferencia de un parcial | 350 – 630 ms | **92 ms / 172 ms** |
| Inferencia de un final | 246 – 332 ms | 249 – 338 ms |
| Espera por la GPU | picos de 4 – 8 s | **p95 0.8 ms** |

28/28 fixtures produjeron al menos una seña. El reloj arranca en el primer sample
de audio, que es lo que hace el micrófono; la primera palabra de cada clip
termina alrededor de los 400–500 ms, así que la latencia **por palabra** es
menor que el número de la tabla.

Detalle completo, lista de frases y de señas, y troubleshooting:
[`tools/TIEMPO-REAL.md`](../../tools/TIEMPO-REAL.md).

## Verificación en el dispositivo (Redmi Note 9S)

- Frase escrita contra el backend real: llega `stream=true total=0`, se encolan
  3 señas en **5 ms**, el avatar firma HAMBRE → POR FAVOR → DONDE y
  `onPlaybackDone` deja la app en "Reproducción completada". Sin errores JS.
- Captura de micrófono: 157 chunks × 960 bytes llegan al STT a 32 000 B/s
  exactos, que es lo que implica un stream PCM16 a 16 kHz.
- Soltar el micrófono sin hablar: vuelve a `idle` al instante con "No se escuchó
  nada.". Antes esperaba el timeout completo de 15 s y después reportaba un fallo
  que no había pasado.

- **Voz real (owner, 2026-10-14 tarde).** Un turno de **22 s con 4 fragmentos**
  y el micrófono **nunca apagado**: `está probando -> PROBAR`, `¿cordo? -> CORDO`,
  `acuerdo? -> ACUERDO`, y `anotaste -> ANOTAR` en el turno siguiente. Los
  fragmentos llegan `provisional=true` mientras la frase está abierta y `false`
  en el último, que es el contrato. Esto cerró el último hueco de "qué no se
  verificó": el tramo "hablo y me transcriben bien" ya no está pendiente.

## Micrófono continuo (2026-10-14, pedido del owner)

El mic era pulsador-hablar sin quererlo: se apagaba solo en cada `final`, así que
había que volver a tocar después de cada frase. Para una conversación eso es al
revés de lo que hace falta.

El cambio no fue "no apagar el audio" sino **separar dos estados que estaban
fusionados en uno**. `playbackState` decía a la vez "¿está el mic prendido?" y
"¿qué hace el muñeco?", y eso solo funcionaba *porque* el mic se apagaba en cada
frase. Con el mic prendido el avatar firma mientras el mic está caliente, así que
los dos se superponen de verdad y necesitan banderas propias: `micActive` pasó a
ser la fuente de verdad del mic y `toggleMic()` decide por ahí.

Lo que hubo que tocar por consecuencia:

- `onPlaybackDone` y el cierre de cada frase vuelven a `Listening`, no a `Done`:
  el botón tiene que verse prendido mientras captura, o el usuario no sabe si
  tocarlo.
- El botón de mic nunca se deshabilita mientras esté prendido. Si la única forma
  de pararlo quedara bloqueada durante una seña, no habría forma de pararlo.
- Con el mic prendido, **Traducir e Historial quedan deshabilitados** y el campo
  de texto pasa a `readOnly` (sigue mostrando la transcripción en vivo, que es
  justo lo que el usuario quiere ver). Texto y voz van por el mismo socket y el
  backend no puede saber de quién es cada fragmento; mezclados, el `index` de voz
  deja de corresponderse con el orden de llegada y el avatar firmaría la frase al
  revés. Son dos formas de mandar, no dos a la vez.
- El botón pasó a `Role.Switch` con `stateDescription`, que es lo que lee
  TalkBack. El `contentDescription` quedó igual porque es el selector de las
  pruebas de Maestro.

## La métrica que mentía

Justo en esa prueba con voz real apareció `stt_partial_lag_ms` con p95 = 13091 ms
y max = 27332 ms, con la espera por la GPU en 0.4 ms. Los dos no pueden ser
ciertos a la vez, y el culpable era la métrica: medía *tiempo real desde el
primer frame menos audio recibido*, o sea que contaba como saturación los
segundos en que el micrófono estaba apagado y no llegaba nada que procesar.
Reproducir un fixture la hacía salir negativa.

Ahora es *audio recibido menos el que estaba en el buffer cuando arrancó la
inferencia*. Con los 28 fixtures: p50 360 ms, p95 390 ms, max 630 ms, que es
literalmente lo que dura una inferencia.

Vale la pena anotarlo porque es la misma clase de error que ya apareció dos veces
en este trabajo (vectores sin normalizar, umbral de similitud cruzando
fragmentos): **un número mal definido es peor que ningún número**, porque después
se cita como si fuera verdad.

## Archivos nuevos

| Dónde | Qué |
|---|---|
| `sign-ai-android/tools/verificar-tiempo-real.sh` | un comando para toda la verificación; **falla** si el p95 se pasa del presupuesto |
| `sign-ai-android/tools/TIEMPO-REAL.md` | guía de pruebas: qué medir, qué frases, qué señas, troubleshooting |
| `sign-ai-android/.../SignAiMetrics.kt` | latencias que solo el teléfono puede medir |
| `sign-ai-backend/app/realtime/incremental.py` | la máquina de estados que decide qué seña se firma |
| `sign-ai-backend/app/metrics.py`, `sign-ai-stt/app/metrics.py` | métricas Prometheus sin dependencias |
| `sign-ai-backend/scripts/verify_incremental.py` | 31 aserciones sobre la lógica de fragmentado, sin servicios |
| `sign-ai-backend/scripts/verify_realtime_latency.py` | cronómetro del tramo cliente→cliente |
| `sign-ai-stt/scripts/make_audio_fixtures.py` | genera los 28 WAV de prueba con Piper |

`verify_client_frames.py` (existente) se actualizó y ahora cubre **los dos
modos**: frase entera y fragmentado.

## Cosas deliberadamente no hechas

- **Cancelar un parcial en vuelo cuando llega un final.** Cancelar el `await`
  descarta el resultado, pero el hilo de la GPU sigue hasta que CTranslate2
  termine. Se hace igual porque lo que se gana es no entregar texto viejo, no
  throughput. Está anotado como tal en `session.py`.
- **TLS y autenticación** en el WebSocket (`RF-INT-COM-M-03`/`DEC-14` del
  backend). Sigue siendo `cleartext` y sin auth.
- **La cola bloquea el panel.** Ya no: `STA-18`. Sigue siendo cierto que mandar
  una frase con el avatar firmando **reemplaza** la cola, así que una
  conversación larga pierde el resto de la cola si el usuario manda otra cosa.
  Es el mismo comportamiento que ya tenía el camino de frases escritas.
- **Las 18 señas sin animación** (`0032`–`0049`). Sigue siendo el gap más grande
  de calidad: el catálogo las anuncia con `available: true` y no hay `.glb`.

---

# Los dos modos del micrófono (2026-10-14, tarde)

## Qué se pidió

El mic es un interruptor, y ya lo era. Lo que faltaba era el otro: que el
**dictado** fuera una cosa y el **tiempo real** otra, con dos botones distintos y
no uno que hacía las dos cosas.

- 🎤 (fila 1) = **dictado**. Se toca, se habla, se toca de nuevo. Solo
  transcribe: el texto se acumula en el campo y el muñeco no se mueve hasta que
  toques Traducir.
- ◢◢ TR (fila 2) = **tiempo real**. Se toca, firma mientras hablás, se toca de
  nuevo para apagar.

Los dos son interruptores, ninguno se mantiene apretado. Y **no pueden estar
prendidos a la vez**: hay un `AudioRecord` y un WebSocket.

## El problema que no es obvio: el cambio de modo

Los dos modos mandan el mismo audio por el mismo socket, así que el único modo de
separarlos es pedirle algo distinto al backend: `{"type":"mode","mode":...}`.

Y ahí aparece la trampa. El cliente manda `end` (para cerrar la frase que estaba
abierta) y **después** el modo nuevo. El `final` de esa frase llega cuando la
sesión ya está en el modo que se acaba de prender. Sin más, al prender TR se
firmaba lo que el usuario solo quería dictar; y si el orden fuera el inverso, la
última frase de TR quedaba sin firmar.

La regla que lo resuelve: **el modo es por utterance**. El backend lo fija con la
primera transcripción de la frase y dura hasta su `final`. Un utterance es de un
solo modo de punta a punta.

De ahí sale el segundo problema, que apareció en el dispositivo: el `final` de lo
dictado llega **después** de que el usuario apagó el dictado, así que la app ya
se cree con el micro apagado. Lo tomaba por una frase de tiempo real, esperaba
fragmentos que no iban a llegar y quedaba en **"Traduciendo..." para siempre**.
Por eso el `final` de dictado viaja con `dictation: true` y el cliente rutea por
ese flag, no por su propio estado. Aditivo: un cliente viejo lee los mismos tres
campos de siempre.

Y una tercera cosa, más chica: `handle_text` **no** mira el modo. Si lo mirara,
Traducir no funcionaría después de dictar, que es justo cuando más se usa.

## Lo que se cambió

**Backend** (`cc0156b`):

- `CAPTURE_REALTIME`/`CAPTURE_DICTATION` + `Controller.set_mode`, con
  `_utterance_mode` como latch por utterance.
- `send_transcript(..., dictation=)` y `handle_text` por fuera del camino de voz.
- Frame `{"type":"mode"}` en el router, con `MODE_FRAME` y validación.
- Métricas: `dictation_utterances_total`, `dictation_utterance_words`,
  `capture_mode_{dictation,realtime}_total`, `session_capture_dictation`,
  `capture_mode_switch_open_utterance_total`.

**Android**:

- `MicMode` (`Off`/`Dictation`/`Realtime`). Enum y no dos flags: "los dos prendidos"
  es un estado que la app no puede representar.
- `toggleDictation()` / `toggleRealtime()` sobre un `changeMicMode()` único. Los
  cuatro casos (prender, apagar, cambiar, ya estaba) en un solo lugar.
- Cambiar de modo **no reinicia la captura**: `AudioRecord` tarda cientos de ms y
  el usuario ya estaba hablando.
- `onDictationTranscript` acumula en `dictationBuffer`. El STT cierra cada frase
  por silencio y alguien que dicta hace pausas: conservar solo la última perdería
  todo lo anterior.
- `PlaybackState.Dictating` y `resumeListeningIfMicOn()` eligen la etiqueta del
  modo activo.
- Al apagar TR, si hubo dictado, el input vuelve a mostrar lo dictado.
- `AudioCapture.stop()` distingue "paré de grabar" de "no había nada grabado": con
  el log viejo, un `stop()` de a poco se veía igual que un corte real.
- `fail()` ahora corta la captura, no solo la bandera. Antes dejaba el
  `AudioRecord` corriendo con el botón apagado.

## Verificación

**Determinista, sin hablar** — `verify_voice_modes.py` (nuevo, en el backend)
reproduce los fixtures por el socket contra el Whisper de verdad, en los dos modos
y en el cambio de uno al otro. 48 checks sobre 3 clips. La línea que resume el
contrato, del escenario del cambio:

```
 1002ms  transcript  final=0 dictation=1  'Me llamo Ana.'
 1330ms  transcript  final=1 dictation=1  'Me llamo Ana'      <- NO se firmó
 1792ms  transcript  final=0 dictation=0  '¡Viva Hamburger!'  <- ya en TR
 2889ms  transcript  final=0 dictation=0  'Vivo en Bogotá, soy estudiante.'
 3851ms  transcript  final=1 dictation=0  'Vivo en Bogotá, soy estudiante.'
segment=3 done=1, primero a los 1793ms (el corte fue a los 1092ms)
```

`verify_client_frames.py` pasó de 33 a 57 checks, todo con dobles de prueba.

**Con voz humana, en el Redmi** (owner, 2026-10-14):

- *Dictado*: 4 frases en 30 s (`dictado #1..#4`, 53 palabras), **cero** `Sending
  segment` en el backend, y al apagar el micro las 4 frases quedaron concatenadas
  en el campo. Traducir después firmó `BUENOS DIAS · NUNCA · TRABAJAR ·
  TRABAJAR`.
- *Tiempo real*: 5 turnos en una sola sesión con el micro siempre prendido;
  primeras señas a 239 ms, 241 ms, 268 ms, 476 ms.
- El bug de "Traduciendo..." para siempre se vio **en el dispositivo** antes de
  arreglarse, no en un test: por eso el `dictation: true`.

## Dos cosas que costaron tiempo y conviene no repetir

- **`mobile-mcp` con coordenadas de esquina.** El botón del micro empieza en
  (729, 2019) y termina en (867, 2157): un tap en (729, 2019) es el borde exacto
  y a veces no registra. Usar el **centro**.
- **Los fixtures traen segundos de silencio al final.** `conversacion_2` dura
  5.7 s pero su última palabra termina en los 2.2 s, así que "la mitad del
  archivo" cae después de toda la voz y un test de cambio de modo no tiene nada
  que firmar del otro lado. El corte va en el medio de la **voz**
  (`words[-1].end_ms`), no del archivo.

## Segunda vuelta del owner: el dictado manda solo y la interfaz se libera

Lo que faltaba después de la primera entrega de los dos modos, reportado en el
dispositivo:

1. **Dictado: apagar el interruptor tiene que mandar solo.** El diseño anterior
   dejaba el texto en el campo y esperaba un toque en Traducir. Con un
   interruptor, apagar *es* mandar; pedir un segundo toque después de haber
   hablado es la fricción que el modo vino a sacar.
2. **Apagar TR dejaba el panel entero deshabilitado**, a veces por segundos.

### El segundo bug era más interesante

El panel se quedaba bloqueado con "Procesando..." porque `stopCapture` ponía ese
estado esperando una transcripción que ya no podía llegar: la captura se cortó y
el `end` cerró el utterance. Se estaba esperando algo que no existía.

Y el motivo de fondo era peor: **"el `done` del backend ya llegó" y "el avatar ya
terminó de firmar" son cosas distintas**. En modo tiempo real la cola se llena más
rápido de lo que el avatar la consume; en una prueba de 30 s había **38
fragmentos**, casi 40 s de señas por delante. Cuando el `done` cerraba el turno,
`playbackState` volvía a `Listening` mientras el muñeco seguía firmando, así que
al apagar el micrófono la app leía "no hay nada en curso" y se ponía a esperar.

La corrección tiene dos partes, y las dos importan:

- `queuePlaying` es la fuente de verdad de "el avatar está firmando", y la
  alimenta el puente `onPlaybackDone`. Apagar el mic deja el estado en `signing`
  si queda cola, o en `done` si no. Nunca más en `processing` por un turno de voz.
- **`signing` no bloquea el panel.** Lo que no se puede tocar es lo que está *en
  vuelo* (`translating`, `processing`). Con la cola bloqueando, una conversación
  de 30 s dejaba al usuario mirando un panel muerto medio minuto; ahora puede
  seguir escribiendo mientras el avatar firma, y mandar otra frase reemplaza la
  cola, que es lo que ya hacía la escena. `play()` y `replayTurn()` dejaron de
  rechazar en `signing` también: un botón habilitado que hace nada es peor que
  uno deshabilitado.

El timeout de 15 s quedó solo para el único caso en que de verdad hay algo
esperando: el dictado con la frase del STT todavía abierta.

### Verificación en el dispositivo

- Dictado: `dictado → envío: 25 caracteres, 4 palabras`, y 520 ms después firmó
  `HOLA · COMO ESTAS` sin que nadie tocara Traducir.
- TR apagado: `Reproducción completada`, Repetir visible, campo editable, mic,
  Historial y Traducir habilitados, todo en el instante.
- Con el avatar firmando una frase larga y el hint mostrando las glosas, **nada
  del panel estaba deshabilitado** — leído del árbol de accesibilidad en ese
  momento, no después.

## Pendiente

1. ~~**Probarlo con voz humana.**~~ **Hecho**: tiempo real y dictado, los dos, en
   el Redmi. Ver "Verificación en el dispositivo".
2. **Dos clientes a la vez.** El código lo contempla (un `IncrementalTranslator`
   por sesión, contadores por sesión) pero no se probó con dos conexiones
   simultáneas.
3. **Calidad de glosas en fragmentos de dos palabras sin contenido semántico.**
   El contexto ya arregla el caso obvio, pero un fragmento suelto todavía puede
   dar una glosa rara. Es una limitación del prompt, no del transporte.
4. **Abrir PR contra `develop`.** Los tres repos están pusheados a `tiempo-real`
   pero no hay PR. La política del repo de Android es commit directo a `main`;
   los otros dos usan PR contra `develop`, así que **este trabajo no debería
   mergearse a `main` ni a `develop` sin que el usuario lo revise**. Hay cambios
   de comportamiento en el protocolo del WebSocket.