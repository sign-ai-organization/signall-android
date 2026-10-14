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

## Los cuatroostos que había en el camino

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
- **Las 18 señas sin animación** (`0032`–`0049`). Sigue siendo el gap más grande
  de calidad: el catálogo las anuncia con `available: true` y no hay `.glb`.

## Pendiente

1. **Probarlo con voz humana.** Un agente no tiene voz. Lo verificado es que el
   audio sale del teléfono a la tasa correcta y que el camino del segmento hacia
   el avatar funciona con un `stream: true` real. Los pasos exactos están en
   §2 de `tools/TIEMPO-REAL.md`.
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