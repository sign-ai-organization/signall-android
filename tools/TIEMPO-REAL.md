# Modo tiempo real — cómo probarlo, qué mirar y qué esperar

Branch `tiempo-real` en los tres repos. Este documento es la guía de pruebas
del trabajo: qué se midió, cómo repetirlo, qué frases usar, qué señas hay y
qué significa cada número.

---

## 1. Qué cambió, en una frase

Antes, el avatar firmaba **después** de que terminaras de hablar. Ahora firma
**mientras** hablas: el STT va diciendo qué palabras ya terminó de pronunciar, el
backend traduce solo esa cola nueva y la app la encadena detrás de lo que ya
está sonando.

```
ANTES   hablar ────── silencio ────── 1.5s ── Whisper ── LLM ──▶ avatar firma
                                       ≈ 2.4 – 4.8 s de espera total

AHORA  hablar ─┬─ "hola"     ─▶ HOLA        (el avatar ya empezó)
               ├─ "cómo"     ─▶ COMO
               └─ "estás"    ─▶ ESTAS      (el `final` solo confirma)
```

Medido sobre los 28 fixtures de audio reproducidos por el WebSocket real a
velocidad 1x, con GPU libre:

| | antes | después |
|---|---|---|
| Primera seña (p50) | — | **913 ms** |
| Primera seña (p95) | — | **1336 ms** |
| Dejar de hablar → transcripción final | 1813 – 2099 ms | **p50 839 ms / p95 958 ms** |
| Inferencia de un parcial | 350 – 630 ms | **p50 92 ms / p95 172 ms** |
| Espera por la GPU | picos de 4 – 8 s | **p95 0.8 ms** |

El reloj arranca en el primer sample de audio, que es lo que hace el micrófono.
La primera palabra de cada clip termina alrededor de los 400 – 500 ms, así que
la latencia **por palabra** es bastante menor que el número de la tabla.

---

## 2. Correr todo

```bash
# un comando: servicios, tests, los 28 fixtures, métricas y dispositivo
tools/verificar-tiempo-real.sh

# o por partes
tools/verificar-tiempo-real.sh incremental    # lógica de fragmentado + protocolo
tools/verificar-tiempo-real.sh latencia       # los 28 fixtures de punta a punta
tools/verificar-tiempo-real.sh metricas       # /metrics de los dos servicios
tools/verificar-tiempo-real.sh dispositivo    # teléfono: permiso, adb reverse, relanzar
```

El script **falla solo** si algo se rompe, y en el paso de latencia falla si la
primera seña se pasa del presupuesto (2500 ms p95 por default, ajustable con
`SIGNAI_P95_MAX_MS`).

### Requisitos previos

```bash
cd ../sign-ai-backend && docker compose up -d --build   # backend + STT
curl -s 127.0.0.1:8080/health   # {"status":"ok","realtime":true,...}
curl -s 127.0.0.1:8000/health   # {...,"partial_model":"small","realtime":true}
```

### Probar con tu propia voz (esto no lo puede hacer un agente)

```bash
./gradlew installDebug -Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate
adb reverse tcp:8080 tcp:8080        # hace que el teléfono llegue al backend por USB
adb shell pm grant com.signai.webview android.permission.RECORD_AUDIO
adb logcat -c
```

El micrófono tiene **dos interruptores** y los dos son de tocar una vez, no de
mantener apretado. Mismo micrófono, misma captura, distinto resultado.

| | Qué toca | Qué hace | Qué firma |
|---|---|---|---|
| 🎤 **Dictado** (fila 1) | el micrófono | escribe tu voz como texto en el campo | **nada** hasta que toques Traducir |
| ◢◢ **TR** (fila 2) | el botón TR | traduce y firma mientras hablás | cada fragmento, en el acto |

No pueden estar prendidos a la vez: comparten el `AudioRecord` y el WebSocket.
Por eso el micrófono se ve deshabilitado mientras TR está prendido — y TR nunca
se deshabilita, porque es el único botón que puede cortar el modo continuo.

### A. Dictado — probar que la voz se vuelve texto y se manda sola

1. Tocá el **micrófono** (se prende solo).
2. Hablá con una pausa en el medio: *"hola"* … *(1 s)* … *"¿cómo estás?"*.
3. Tocá el micrófono de nuevo para dejar de dictar.
4. **Firma solo, sin tocar Traducir.** El texto queda en el campo y el muñeco
   firma `HOLA · COMO ESTAS` en medio segundo.
5. Al mandar, Traducir queda **deshabilitado 1,5 s** con el texto a la vista:
   no se puede reenviar lo que ya se firmó. Pasados 1,5 s el texto se borra solo
   y el campo y Traducir vuelven a quedar utilizables. En los logs son las dos
   líneas `texto ya enviado` y `texto retenido borrado`.
6. Cuando termine de firmar, Traducir, Historial, el micrófono y el campo quedan
   **habilitados**.

Las pausas son el punto de la prueba: el STT cierra cada frase por silencio, y
dictar son varias frases, no una. Si solo apareciera la última, el buffer no se
está acumulando.

En los logs:

```
SignAiState: micrófono prendido en modo Dictation
SignAiMetrics: dictado #1: 1 palabras, 6 caracteres
SignAiMetrics: dictado #2: 3 palabras, 18 caracteres      <- el STT cerró por silencio
SignAiState: micrófono apagado (venía de Dictation)
SignAiMetrics: dictado #3: 4 palabras, 25 caracteres      <- el `end` forzó el final
SignAiState: dictado → envío: 25 caracteres, 4 palabras   <- y sale solo
SignAiNet: segment 0 ... de=hola ¿cómo estás? -> HOLA,COMO ESTAS
```

Y en el backend, `capture mode -> dictation` y `dictated: '...'`, **sin** ningún
`Sending segment` antes del envío: esa es la prueba de que no firmó mientras
dictabas.

### B. Tiempo real — probar que firma mientras hablás

1. Tocá **TR**.
2. Decí **"hola"** y hacé una pausa de un segundo antes de seguir.
3. Seguí con **"cómo estás"**.
4. **Seguí hablando todo lo que quieras** — el micrófono **no se apaga solo**.
5. Cuando te boredes, tocá TR de nuevo para apagarlo.

Deberías ver al muñeco firmar HOLA mientras todavía estás hablando, no después.

Y al apagarlo hay que mirar **qué pasa en los 1,5 s siguientes**, porque no todo
se libera en el acto:

| | al apagar TR | 1,5 s después |
|---|---|---|
| Micrófono, Historial, TR | habilitados al instante | habilitados |
| El campo | **solo lectura**, con lo último que firmaba el muñeco | vacío y escribible |
| Traducir | **deshabilitado** | habilitado |

El campo no se libera enseguida a propósito: muestra lo que la voz acaba de
mandar y ya se firmó, y si Traducir estuviera disponible sería un botón para
reenviarlo. Por eso el texto se va solo, y **el texto que se borra, el campo y
el botón vuelven en el mismo instante** — nunca se ve el campo vacío con Traducir
muerto, que es lo que hace que la app parezca trabada.

Mientras tanto, aunque al avatar todavía le falte cola, el resto del panel no se
bloquea: en tiempo real la cola se llena más rápido de lo que el muñeco la
consume, y dejar todo deshabilitado mientras termina esconde al usuario una app
muerta sin explicación.

Mientras tanto:

```bash
adb logcat -s SignAiMetrics      # latencias del teléfono
adb logcat -s SignAiNet          # cada segmento que llega del backend
adb logcat -s SignAiState        # en qué modo está el micrófono y por qué
adb logcat -s SignAiWeb          # consola del JS (modo depuración)
```

**Con cualquiera de los dos prendido el resto se apaga:** el campo de texto
queda de solo lectura (muestra lo que se está transcribiendo en vivo), y
**Traducir** e **Historial** quedan deshabilitados. Es a propósito: texto y voz
van por el mismo socket y el backend no puede saber de quién es cada fragmento;
mezclados, el avatar firmaría la frase al revés.

**Y el otro interruptor también se apaga, en los dos sentidos:** con el
micrófono prendido, TR queda deshabilitado, y con TR prendido, el micrófono. No
pueden estar prendidos a la vez porque comparten el `AudioRecord` y el socket. Lo
que **no** se deshabilita nunca es el interruptor que está prendido: es el botón
de parar. Un control apagado se reconoce por su icono y su etiqueta en gris
(`muted`), no por un relleno más claro — el relleno de estos dos botones es casi
del mismo color que la barra, así que un relleno atenuado no se distingue de uno
habilitado.

### C. Cambiar de uno al otro sin soltar nada

1. Dictá algo y **sin soltar el micrófono** tocá TR.
2. Cambia de modo y sigue: lo que ya dictaste **no** se firma, lo que digas
   después sí.

Y al revés: si dictaste algo y después probaste TR, al apagar TR el campo vuelve
a mostrar lo que habías dictado. Perder tres frases porque quisiste probar el
modo continuo sería la peor forma de que esto salga mal.

Esto se puede verificar sin hablar, con los fixtures de audio reales contra el
Whisper de verdad:

```bash
python scripts/verify_voice_modes.py --clips 3     # en sign-ai-backend
tools/verificar-tiempo-real.sh voz                  # lo mismo + el bloque de servicios
```

### D. Traducir y Repetir — la misma retención que el micrófono

Esto se verifica **sin hablar**, y es la mitad de lo que el dueño報告aba: un
Traducir a mano y el Repetir dejaban la frase en el campo con Traducir
habilitado, así que un toque más firmaba la misma frase dos veces.

1. Escribí una frase larga (5 o 6 señas, para tener ventana) y tocá **Traducir**.
2. La frase queda en el campo y **Traducir se deshabilita 1,5 s**; a los 1,5 s el
   texto se borra solo. En el log son `texto ya enviado` y `texto retenido borrado`
   (la segunda línea, 1501 ms después de la primera).
3. Cuando termine de firmar, tocá **Repetir** (el botón del escenario, abajo al
   centro).tiene que pasar lo mismo: la frase a la vista, Traducir muerto 1,5 s,
   y el texto que se va solo.
4. **Durante toda la seña —no solo mientras traduce— el panel entero está
   deshabilitado**: micrófono, Historial y TR se ven en `muted` y no responden.
   Antes se liberaban a los ~50 ms del toque, cuando la seña recién arrancaba.
   Cuando el muñeco termina, el panel vuelve solo.

El punto 4 es el que se ve mejor mirando los tres iconos: pasan de `text`
(244 en el gris del pixel más claro del glifo) a `muted` (148) y vuelven. Si
quedaran prendidos mientras el muñeco firma, es que `replayPending` se soltó antes
de `onPlaybackDone`.

Repetir también baja 32 dp (a `AvatarStage.RepeatGap`): el botón quedó más
cerca de la barra, sin pegarse.

**La pausa entre palabras importa.** Un hangover de 600 ms parte la frase en dos
si hablás corrido. Está deliberado: las señas se pueden encadenar, y partir más
agresivamente rompe palabras.

---

## 3. Qué frases usar

### Para ver el modo tiempo real (una palabra a la vez)

Estas están pensadas para que el avatar empiece a firmar antes de que terminés:

| Decí | Esperado |
|---|---|
| "hola" | HOLA |
| "gracias" | GRACIAS |
| "cómo estás" | COMO ESTAS |
| "buenos días" | BUENOS DIAS |
| "bien" | BIEN |
| "nunca" | NUNCA |

### Para ver el flujo completo (encadenadas)

| Decí | Esperado |
|---|---|
| "me llamo Ana" | NOMBRE, NIÑA |
| "vivo en Bogotá" | VIVIR |
| "tengo hambre" | HAMBRE |
| "estoy contenta" | CONTENTO |
| "estoy triste" | SENTIR, TRISTE |
| "mi familia" | FAMILIA |
| "mi hermano" | HERMANO |
| "buenas tardes" | BUENAS TARDES |
| "por favor" | POR FAVOR |
| "bien más o menos" | BIEN, MAS O MENOS |
| "cómo estás, bien" | COMO ESTAS, BIEN |
| "dónde vives" | DONDE, VIVIR |
| "quién es ella" | QUIEN, MUJER |
| "cuánto ganas" | CUANTO, TRABAJAR |
| "hola, buenos días, cómo estás" | HOLA, BUENOS DIAS, COMO ESTAS |

### Conversaciones completas (lo que de verdad prueba el modo)

Las cuatro que están en `sign-ai-stt/fixtures/audio/conversacion_*.wav`:

```
conversacion_1  hola, buenos dias, como estas
conversacion_2  me llamo ana, vivo en bogota, soy estudiante
conversacion_3  tengo hambre, por favor, donde esta el restaurante
conversacion_4  bien, muchas gracias, hasta manana
```

### Probar sin hablar

```bash
# frase escrita: recorre el mismo camino del backend
../sign-ai-backend/scripts/verify_realtime_latency.py --phrase "hola" --phrase "cómo estás"

# reproducir un WAV por el WebSocket, a velocidad real
../sign-ai-backend/scripts/verify_realtime_latency.py \
    --replay-audio ../sign-ai-stt/fixtures/audio/conversacion_2.wav

# ver la línea de tiempo de un fragmento
adb logcat -s SignAiNet
```

### Qué mirar además del ritmo

- **El reposo**: al abrir la app, después de que termine una frase y con el
  micrófono prendido esperando, el muñeco tiene que estar **con los brazos abajo,
  al lado de los muslos**. Antes tenía los brazos abiertos como de maniquí.
- **El paso de una seña a otra**: es lo que cambió. Las 46 variantes de clip ya
  se encadenan solas; la app no mete nada en el medio y las funde durante 100 ms.
  Se nota sobre todo en el par de señas de una frase corta ("hola gracias"), que
  es donde el salto directo es más visible.
- **Para verlo en números**: Ajustes → AVANZADO → Modo depurar, y el panel
  muestra la fundición y cuántas actions están vivas (`2` mientras se funde).
  En logcat sale una línea por seña: `fundición -> 0000.001` o `corte seco -> …`
  (el corte seco es correcto para la primera seña, para una seña repetida y con
  "reducir animaciones" prendido).

---

## 4. Qué señas hay

**32 con animación exportada** (las que el avatar puede firmar de verdad). La
columna `id` es el índice de LSC50 que empareja con `signs/<id>.glb`.

| glosa | id | duración | glosa | id | duración |
|---|---|---|---|---|---|
| GRACIAS | 0000 | 2.42 s | NIÑO | 0014 | 2.08 s |
| BUENOS DÍAS | 0001 | 2.08 s | NIÑA | 0015 | 2.08 s |
| BUENAS TARDES | 0002 | 2.08 s | ABUELO | 0016 | 2.08 s |
| BUENAS NOCHES | 0003 | 2.08 s | TÍO | 0017 | 2.08 s |
| SEÑA | 0004 | 2.08 s | HERMANO | 0018 | 2.08 s |
| NOMBRE | 0005 | 2.08 s | HAMBRE | 0019 | 2.08 s |
| TRABAJAR | 0006 | 2.08 s | FELIZ | 0020 | 2.21 s |
| COMER | 0007 | 2.08 s | CONTENTO | 0021 | 2.08 s |
| VIVIR | 0008 | 2.08 s | TRISTE | 0022 | 2.75 s |
| POCO | 0009 | 2.12 s | ABURRIDO | 0023 | 2.33 s |
| FAMILIA | 0010 | 2.08 s | BIEN | 0024 | 2.42 s |
| PERSONAS | 0011 | 2.08 s | MAL | 0025 | 2.25 s |
| MUJER | 0012 | 2.08 s | CÓMO ESTÁS | 0026 | 2.08 s |
| HOMBRE | 0013 | 2.08 s | MÁS O MENOS | 0027 | 2.17 s |

Y las últimas ocho:

| glosa | id | duración | glosa | id | duración |
|---|---|---|---|---|---|
| SENTIR | 0028 | 2.08 s | ADIÓS | 0031 | 2.08 s |
| JUCIOSO | 0029 | 2.17 s | HOLA | 0030 | 2.12 s |
| POR FAVOR | 0032 | ⚠️ sin exportar | NUNCA | 0037 | ⚠️ sin exportar |
| CON GUSTO | 0033 | ⚠️ sin exportar | YO | 0038 | ⚠️ sin exportar |
| BIENVENIDO | 0034 | ⚠️ sin exportar | TU | 0039 | ⚠️ sin exportar |
| PERDÓN | 0035 | ⚠️ sin exportar | USTEDES | 0040 | ⚠️ sin exportar |
| PERMISO | 0036 | ⚠️ sin exportar | QUE · CUANDO · DONDE | 0041–0043 | ⚠️ sin exportar |

Las 18 marcadas **no tienen animación**: el catálogo de Neon las anuncia con
`available: true` pero el `.glb` no existe en el APK. La app no pide el archivo
por HTTP (sería un 404 garantizado, `GLS-10`) y cae a la seña de prueba si el
ajuste está prendido, o las reporta en el caption si está apagado.

**Promedio 2.15 s por seña.** Es el número que explica por qué el avatar se
atrasa al hablar rápido, y por qué el modo tiempo real suma *adelanto
automático* (firmar 1.6× mientras haya dos o más señas en cola).

### Palabras que el catálogo no cubre

`estudiante`, `médica`, `restaurante`, `aqui`, `trabajo` (como sustantivo) y
cualquier nombre propio no tienen glosa. La respuesta correcta es que **no se
firmen** y queden en el caption, no que el avatar improvise una seña. Si pedís
"soy estudiante" y el muñeco firma NIÑO, eso es un error de traducción, no del
avatar.

---

## 5. Métricas

Los tres servicios exponen lo mismo: Prometheus en `/metrics` y JSON en
`/metrics.json`.

```bash
curl -s 127.0.0.1:8080/metrics | less      # STT igual, en :8000
```

### Backend (`:8080`) — lo que el usuario percibe

| Métrica | Qué es | Medido |
|---|---|---|
| `rt_first_sign_ms` | primer texto del STT → **primera seña enviada**. La latencia que siente el usuario. | p50 0.4 ms / p95 394 ms |
| `rt_utterance_ms` | primer texto → última seña | p50 960 ms / p95 2918 ms |
| `rt_chunk_delay_ms` | cada fragmento por separado | p50 0.4 ms / p95 367 ms |
| `audio_bytes_total` | audio reenviado al STT | 32 000 B/s mientras se escucha |
| `dictation_utterances_total` | frases dictadas que el STT cerró por silencio o por el `end` | crece solo en dictado |
| `capture_mode_dictation_total` | veces que el cliente prendió el dictado | 1 por sesión de dictado |
| `session_capture_dictation` | 1 si la sesión está en dictado, 0 si está en TR o sin micro | cambia con el interruptor |
| `capture_mode_switch_open_utterance_total` | cambios de modo con una frase todavía abierta | crece normal: cambiar de dictado a TR mientras se habla es justamente ese caso, y es lo que el latch por utterance resuelve |
| `session_audio_rms` | nivel del audio entrante. Bytes que crecen con RMS en el piso = micrófono capturing silencio. | 0.002 en una habitación en silencio |

`rt_first_sign_ms` da **0.4 ms** porque el fragmento que llega primero es casi
siempre una palabra suelta del catálogo o un acierto de caché, y las dos rutas son
un lookup en memoria. Los ~900 ms de la tabla de arriba son el camino completo
audio → Whisper → traducción; el trabajo del backend una vez que tiene el texto
es prácticamente gratis.

### STT (`:8000`)

| Métrica | Qué es | Medido |
|---|---|---|
| `stt_end_to_final_ms` | dejar de hablar → transcripción final | p50 839 ms / p95 958 ms |
| `stt_inference_ms{model="small"}` | inferencia de un parcial | p50 92 ms / p95 172 ms |
| `stt_inference_ms{model="medium"}` | inferencia de un final | p50 249 ms / p95 338 ms |
| `stt_partial_lag_ms` | **atraso real**: audio que llegó mientras se transcribía y todavía no está transcrito. Sube = el avatar va detrás de la voz. | p50 360 ms / p95 390 ms |
| `stt_partial_queue_ms` | **espera por la GPU**. Antes daba picos de 4–8 s. | p50 0.4 ms / p95 0.6 ms |
| `stt_dropped_total{reason=…}` | transiciones descartadas: `rms`, `no_speech`, `logprob` | ver abajo |

`stt_dropped_total` merece atención: `reason="logprob"` cuenta veces que **el
modelo transcribió bien y el filtro lo tiró igual**. Antes pasaba con
`AVG_LOGPROB_THRESHOLD=-0.8`; dos frases de los fixtures ("cómo estás", "estoy
triste") salían como transcripción vacía. Volvió a `-1.0`, el default de
Whisper. Si este número sube mucho, el filtro se está comiendo audio real.

### `stt_partial_lag_ms` no es lo que sugiere el nombre

Ojo con esta: se llama "lag" pero **no** es "reloj de sesión menos audio
recibido". Se define como *audio recibido menos el audio que ya estaba en el
buffer cuando arrancó la inferencia*, o sea cuánto se acumuló mientras Whisper
trabajaba.

La diferencia no es cosmética. Con la definición anterior, 20 s de micrófono
apagado daban `lag = 20000 ms` y se leían como "GPU saturada" cuando en realidad
no estaba llegando nada que procesar. En la primera prueba con voz real eso era
justo lo que más números había: p95 = 13091 ms, max = 27332 ms, todos segundos
en que el micro estaba apagado. Ahora está acotado y mide algo: p50 360 ms,
p95 390 ms, max 630 ms.

Si sube de verdad, el avatar va visible detrás de la voz, y ahí sí hay que mirar
`stt_inference_ms` y `stt_partial_queue_ms` para saber si el culpable es el
modelo o la cola.

### Teléfono (logcat)

```bash
adb logcat -s SignAiMetrics
```

```
fragmento #1: 2 señas en 5ms (p50=5ms p95=5ms max=5ms n=1)
turno #1: 822ms, 1 fragmentos, 3 glosas (p50=822ms ...)
firmado: 3 señas en 6841ms (p50=6841ms p95=6841ms max=6841ms n=1)
dictado #1: 24 palabras, 136 caracteres
```

`fragmento`/`turno` son de tiempo real; `dictado` es del otro interruptor. Si
`dictado` crece mientras TR está prendido, o `fragmento` crece mientras se
dicta, los dos modos se mezclaron.

**`turno` y `firmado` son dos cosas distintas y conviene no confundirlas.**
`turno` (`rt_turn_total_ms`) corre del primer texto al último fragmento: mide el
STT y el backend, y el avatar no aparece. `firmado` mide **la animación** — de
la primera seña a la última — y es el número que se mueve cuando cambia la
escena. Con el crossfade de 100 ms, dos señas pasaron de 4708 ms a 4544 ms
medido en el Redmi, que es lo que predice el diseño (menos los 100 ms de cola
del clip anterior, más los 100 ms de pose congelada que ya no hay).

---

## 6. Tests sueltos

```bash
# ¿qué seña se firma y cuál no? 31 aserciones, sin servicios ni red
docker compose -f ../sign-ai-backend/docker-compose.yml exec backend \
    python scripts/verify_incremental.py

# ¿el protocolo sigue igual? frase entera, fragmentado y los dos modos
# del micrófono (57 checks, sin servicios de por medio)
docker compose -f ../sign-ai-backend/docker-compose.yml exec backend \
    python scripts/verify_client_frames.py

# ¿dictado no firma y tiempo real sí, contra el Whisper de verdad?
# Reproduce los fixtures por el socket en los dos modos y en el cambio de uno
# al otro. Es lo único que verifica el contrato entero sin hablar.
../sign-ai-backend/scripts/verify_voice_modes.py --clips 3

# latencia de punta a punta
../sign-ai-backend/scripts/verify_realtime_latency.py --all-fixtures
../sign-ai-backend/scripts/verify_realtime_latency.py --phrase "hola" -n 5

# ¿Whisper transcribe bien los fixtures? (sirve para separar un problema de
# audio de un problema de traducción)
../sign-ai-stt/venv/bin/python ../sign-ai-stt/scripts/make_audio_fixtures.py --all
```

### Generar los fixtures de audio otra vez

Los `.wav` **no están en el repo** (son 1.5 MB de binario); sí están los `.json`
con el texto, la duración y los timestamps por palabra. Para reconstruirlos:

```bash
python3 -m venv .venv-tts && .venv-tts/bin/pip install piper-tts numpy
.venv-tts/bin/python ../sign-ai-stt/scripts/make_audio_fixtures.py --all
```

La voz se descarga sola la primera vez (~75 MB de `sharvard/medium`). El mismo
texto produce el mismo WAV, así que las cifras son comparables entre corridas.

---

## 7. Problemas ya pisados

| Síntoma | Causa | Qué hacer |
|---|---|---|
| "No se pudo abrir el micrófono" | falta el permiso en runtime | `adb shell pm grant com.signai.webview android.permission.RECORD_AUDIO` |
| "Sin conexión con el servidor" en el teléfono | el `10.0.2.2` del build es para emulador | `adb reverse tcp:8080 tcp:8080` y compilar con `-Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate` |
| Transcripción vacía | filtro de confianza de Whisper | mirá `stt_dropped_total{reason="logprob"}` |
| La frase se parte en dos | hangover de 600 ms | normal si hablás corrido; hacé pausas |
| Todos los fragmentos salen iguales | caché semántica con coincidencia por similitud | ya corregido: los fragmentos usan coincidencia literal |
| El avatar firma una seña y después otra rara | glosa fuera de LSC50 | encendé **Ajustes → AVATAR → depurar** y mirá `SignAiNet` |
| El primer `check_latency` tarda el doble | caché semántica vacía | es normal: la primera pasada paga el LLM. Corré dos veces. |
| Un test falla con `httpx2` faltante | ese test necesita el entorno del servicio | corré los tests **dentro** del contenedor |
| `session failed: null` en logcat, en bucle | el backend se reinició y una sesión quedó colgada | reiniciá la app: `adb shell am force-stop com.signai.webview` |
| Queda en "Traduciendo..." para siempre después de dictar | el cliente tomó el `final` de lo dictado por una frase de tiempo real | el `final` de dictado tiene que llegar con `dictation: true`; si falta, actualizá el backend |
| Al dictar, el muñeco firma igual | el cliente no mandó `{"type":"mode","mode":"dictation"}` | mirá `capture mode -> dictation` en los logs del backend |
| Al prender TR no pasa nada hasta la pausa | el cambio de modo no cerró el utterance anterior | el `end` tiene que ir **antes** del `mode`; el backend lo lacha por utterance |
| Solo aparece la última frase dictada | el STT cierra por silencio y el app no acumuló | es el buffer (`dictationBuffer`), no el STT: fijate si hay varias líneas `dictado #` |
| Tocar un botón no hace nada | el tap cayó en el borde de la superficie | `mobile-mcp` hay que usarlo con el **centro** del elemento, no con la esquina |
| Al apagar TR queda todo deshabilitado y dice "Procesando..." | se confundió "el `done` ya llegó" con "el avatar ya terminó": en tiempo real la cola va 30-40 s por detrás | la fuente de verdad es `queuePlaying`, no `playbackState` (`STA-18`) |
| El otro interruptor no se ve deshabilitado | el relleno `soft` atenuado al 35 % sobre `surface` da dos colores casi iguales | se apaga el icono y la etiqueta a `muted`, nunca la superficie (`STA-17`) |
| Traducir manda dos veces lo mismo | el campo tiene la frase que la voz ya firmó | 1,5 s de retención: el texto se ve, Traducir no, y después se borra solo (`STA-20`) |
| Al dictar hay que apretar Traducir a mano | versión anterior a que apagar el interruptor mandara solo | el envío automático es `sendDictation()` |

### El micrófono no se puede probar con el audio del propio teléfono

Se intentó: push de un WAV al teléfono, reproducción con el Reproductor
Multimedia y grabación con el micrófono de la app al mismo tiempo. **No
funciona, y no por un bug.**

La app usa `AudioSource.VOICE_RECOGNITION`, que es la fuente que Android afina
para reconocimiento de voz y que **cancela el eco del propio altavoz**. Es
justamente la elección correcta (con `MIC` el STT escucha la AGC bombeando en vez
de la voz), pero significa que un bucle de retorno acústico es imposible.

Se puede ver en un `curl`:

```bash
# en una habitación silenciosa, con la app grabando
curl -s 127.0.0.1:8080/metrics.json | python3 -c "
import json,sys; d=json.load(sys.stdin)
print('bytes:', d['counters']['audio_bytes_total'])
print('rms:  ', d['histograms']['audio_rms']['p50'])"
# bytes: 395520   <- el audio llega entero
# rms:   0.002    <- pero es el piso de ruido, no voz
```

Esos dos números juntos son el diagnóstico: bytes que crecen con RMS en el piso
significa "el micrófono está capturing silencio", que es distinto de "el STT
está caído". Para probar con voz hace falta una persona.

---

## 8. Qué NO se verificó, y por qué

### Verificado con voz humana (2026-10-14, con el owner)

- **Micrófono continuo.** Un turno de **22 s con 4 fragmentos** y el micrófono
  **nunca apagado**: `luta → LUTA`, `está probando → PROBAR`, `¿cordo? → CORDO`,
  `acuerdo? → ACUERDO`, y después `anotaste → ANOTAR` en otro turno. Los
  fragmentos llegan con `provisional=true` mientras la frase está abierta y
  `false` en el último, que es exactamente el contrato.
- **Traducción y avatar** sobre audio real, no fixtures.
- **El micro sobrevive a los `final`.** Antes se apagaba solo en cada frase;
  ahora queda prendido y el STT abre el utterance siguiente solo.

### Lo que sigue sin verificarse

- **Varios clientes a la vez.** El código lo contempla (un `IncrementalTranslator`
  por sesión, un contador por sesión) pero no se probó con dos conexiones
  simultáneas.
- **Traducción de fragmentos de dos palabras sin contenido semántico.** El
  contexto ya arregla el caso obvio (`estudiante` → ESTUDIANTE en vez de NIÑO),
  pero un fragmento suelto todavía puede dar una glosa rara. Es una limitación
  del prompt, no del transporte.
- **Bucle acústico en el teléfono.** No se puede: `VOICE_RECOGNITION` cancela el
  eco del altavoz (ver §7). Con voz humana no hace falta.

---

## 9. Dónde tocar para mover cada número

| Querés… | Tocá |
|---|---|
| que la primera seña llegue antes | `SILENCE_TIMEOUT_MS` y `PARTIAL_INTERVAL_MS` (STT), `REALTIME_SETTLE_MARGIN_MS` (backend) |
| que el avatar no se atrase | `CATCHUP_FACTOR` y `CATCHUP_BACKLOG` en `app.js`, o el ajuste de velocidad en Ajustes |
| menos llamadas al LLM | `SEMANTIC_CACHE_SIMILARITY_THRESHOLD`, `REALTIME_BATCH_WORDS` |
| Traducción más precisa a costa de latencia | `REALTIME_SETTLE_MARGIN_MS` más alto, `PARTIAL_MODEL_SIZE=medium` |
| volver al comportamiento viejo | `REALTIME_ENABLED=false` en el `.env` del backend |