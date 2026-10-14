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

1. Abrí la app, tocá el **micrófono**.
2. Decí **"hola"** y hacé una pausa de un segundo antes de seguir.
3. Seguí con **"cómo estás"**.
4. Soltá el micrófono.

Deberías ver al muñeco firmar HOLA mientras todavía estás hablando, no después.
Mientras tanto:

```bash
adb logcat -s SignAiMetrics      # latencias del teléfono
adb logcat -s SignAiNet          # cada segmento que llega del backend
adb logcat -s SignAiWeb          # consola del JS (modo depuración)
```

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
| `stt_partial_queue_ms` | **espera por la GPU**. Antes daba picos de 4–8 s. | p50 0.4 ms / p95 0.8 ms |
| `stt_partial_lag_ms` | audio recibido sin procesar. Sube = saturación. | p50 −21 ms / p95 13 ms |
| `stt_dropped_total{reason=…}` | transiciones descartadas: `rms`, `no_speech`, `logprob` | ver abajo |

`stt_dropped_total` merece atención: `reason="logprob"` cuenta veces que **el
modelo transcribió bien y el filtro lo tiró igual**. Antes pasaba con
`AVG_LOGPROB_THRESHOLD=-0.8`; dos frases de los fixtures ("cómo estás", "estoy
triste") salían como transcripción vacía. Volvió a `-1.0`, el default de
Whisper. Si este número sube mucho, el filtro se está comiendo audio real.

### Teléfono (logcat)

```bash
adb logcat -s SignAiMetrics
```

```
fragmento #1: 2 señas en 5ms (p50=5ms p95=5ms max=5ms n=1)
turno #1: 822ms, 1 fragmentos, 3 glosas (p50=822ms ...)
```

---

## 6. Tests sueltos

```bash
# ¿qué seña se firma y cuál no? 31 aserciones, sin servicios ni red
docker compose -f ../sign-ai-backend/docker-compose.yml exec backend \
    python scripts/verify_incremental.py

# ¿el protocolo sigue igual? frase entera Y fragmentado
docker compose -f ../sign-ai-backend/docker-compose.yml exec backend \
    python scripts/verify_client_frames.py

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

---

## 8. Qué NO se verificó, y por qué

- **El micrófono real.** Un agente no tiene voz. Se verificó que el audio sale
  del teléfono a 32 000 B/s exactos por el WebSocket hasta el STT, y que el
  camino del segmento hacia el avatar funciona con un `stream: true` real. Lo
  que falta es el tramo "hablo y me transcriben bien", que depende del micrófono
  y la voz de una persona. Por eso la sección 2 tiene los pasos exactos.
- **Varios clientes a la vez.** El código lo contempla (un `IncrementalTranslator`
  por sesión, un contador por sesión) pero no se probó con dos conexiones
  simultáneas.
- **Traducción de fragmentos de dos palabras con contexto.** El contexto ya está
  en el prompt y arregla el caso obvio (`estudiante` → ESTUDIANTE en vez de
  NIÑO), pero un fragmento de dos palabras sin contenido semántico todavía puede
  producir una glosa raro. Es una limitación conocida del prompt, no del
  transporte.

---

## 9. Dónde tocar para mover cada número

| Querés… | Tocá |
|---|---|
| que la primera seña llegue antes | `SILENCE_TIMEOUT_MS` y `PARTIAL_INTERVAL_MS` (STT), `REALTIME_SETTLE_MARGIN_MS` (backend) |
| que el avatar no se atrase | `CATCHUP_FACTOR` y `CATCHUP_BACKLOG` en `app.js`, o el ajuste de velocidad en Ajustes |
| menos llamadas al LLM | `SEMANTIC_CACHE_SIMILARITY_THRESHOLD`, `REALTIME_BATCH_WORDS` |
| Traducción más precisa a costa de latencia | `REALTIME_SETTLE_MARGIN_MS` más alto, `PARTIAL_MODEL_SIZE=medium` |
| volver al comportamiento viejo | `REALTIME_ENABLED=false` en el `.env` del backend |