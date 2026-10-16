# Modo en vivo — cómo probarlo, qué mirar y qué esperar

Rama `flujo-oraciones` en los tres repos (2026-10-15, **sin mergear**). Este
documento es la guía de pruebas del modo: qué se midió, cómo repetirlo, qué
frases usar, qué señas hay y qué significa cada número.

> **Si llegaste desde `develop`:** este archivo cambió entero con el flujo por
> oraciones. Lo que aquí dice "antes" es el camino por palabras, que ya no
> existe. El plan del cambio está en
> `../sign-ai-docs/auditoria-flujo-2026-10-15.md` §6, y el resumen de qué
> cambió en cada repo está en `docs/CAMBIOS.md`, `../sign-ai-backend/docs/CAMBIOSBACKEND.md`
> y `../sign-ai-stt/docs/CAMBIOSSTT.md`.

---

## 1. Qué cambió, en una frase

Antes el avatar representaba **a mitad de frase**: el STT iba diciendo qué palabras ya
terminó de pronunciar, el backend traducía esa cola nueva y la app la encadenaba
detrás de lo que ya sonaba. Ahora la unidad es la **oración**: el STT cierra la
frase, el backend la divide en oraciones, las traduce y el avatar representa oración
por oración.

```
ANTES   hablar ─┬─ "hola"     ─▶ HOLA        (el avatar representaba a mitad de frase)
                ├─ "cómo"     ─▶ COMO
                └─ "estás"    ─▶ ESTAS      (el `final` solo confirmaba)

AHORA  hablar ─────── 1,5s de silencio ── Whisper ── divide en oraciones ──▶ avatar
        (aquí no se representa nada)                                                  (recién acá)
```

**La pérdida es la primera seña, y es deliberada** (opción A, §7 de la
auditoría): representar a mitad de frase obligaría a partir los clips de dos palabras
en glosas sueltas y a mantener el `||` de dos barras en la caché semántica, que
es justamente el camino que la auditoría desarma. Lo que se gana es que **el
avatar nunca representa media oración** y que la clave de caché es una oración
limpia, la misma en los dos modos.

### Lo que se midió (28 fixtures, backend + STT reales, 2026-10-15)

Las 28 frases de `../sign-ai-stt/fixtures/audio` reproducidas por el WebSocket a
velocidad 1x, con `qwen3:4b-instruct` en Ollama local (`127.0.0.1:11434`) y la
caché semántica vacía:

| | p50 | p95 |
|---|---|---|
| **`rt_first_sign_ms`** (transcripción final → primera oración enviada) | **273 ms** | **422 ms** |
| Ídem con la caché caliente | 1 ms | 4 ms |
| Del primer sample de audio a la primera seña (**lo que percibe el usuario**) | **4010 ms** | — |
| …de los cuales el STT | 3709 ms | — |

**El pipeline no es el cuello de botella: el STT lo es.** De los 4,0 s que pasan
desde que el usuario empieza a hablar hasta que aparece la primera seña, 3,7 s
son el STT esperando a que la frase se cierre por los 1,5 s de silencio y
transcribiéndola. Traducir cuesta 273 ms.

Esto **no se puede comparar** con la línea base publicada (p50 913 ms / p95
1336 ms): esa era del camino por palabras y medía otra cosa. El nombre
`rt_first_sign_ms` se conservó a propósito para que la comparación sea visible,
pero la comparación no vale.

Las 28 frases son cortas —mediana de 2 palabras, 1 seña y 1 oración— así que
**no dicen nada de una conversación real**. Para eso están las cuatro
`conversacion_*.wav`, que tienen 5–8 palabras.

### Repetir la medición

```bash
tools/medir-primera-sena.py     # primera seña, frío y caliente
tools/medir-retraso.py           # ¿el avatar alcanza a representar mientras hablás?
```

`medir-primera-sena.py` reproduce los fixtures, espera el `final`, manda la frase
como `text` —que es lo que hace la app— y mide dos tiempos: del primer sample de
audio a la primera seña, y de la transcripción final a la primera seña.

**Para medir en frío hay que reiniciar el backend.** `SEMANTIC_CACHE_PRELOAD=true`
precarga la caché en memoria al arrancar, así que `DELETE FROM semantic_cache;`
no alcanza: el proceso sigue con la copia en memoria y la segunda pasada sigue
saliendo a 1 ms.

```bash
cd ../sign-ai-backend
export $(grep -o '^DATABASE_URL=.*' .env | head -1 | xargs)
psql "$DATABASE_URL" -c "DELETE FROM semantic_cache;"
docker compose restart backend        # <- sin esto no es una medición en frío
```

---

## 2. Correr todo

```bash
# un comando: servicios, tests, fixtures, métricas y dispositivo
tools/verificar-tiempo-real.sh

# o por partes
tools/verificar-tiempo-real.sh incremental    # divisor de oraciones + protocolo
tools/verificar-tiempo-real.sh metricas       # /metrics de los dos servicios
tools/verificar-tiempo-real.sh dispositivo    # teléfono: permiso, adb reverse, relanzar
tools/medir-primera-sena.py                   # primera seña, extremo a extremo
tools/medir-retraso.py                        # cuánto se sigue representando tras hablar
```

### Requisitos previos

```bash
cd ../sign-ai-backend && docker compose up -d --build   # backend + STT
curl -s 127.0.0.1:8080/health   # {"status":"ok","cache_loaded":true,"cache_rows":0}
curl -s 127.0.0.1:8000/health   # {...}
```

**No hay bandera de tiempo real.** En `main` del backend no existen
`REALTIME_ENABLED`, ni el modo por fragmentos, ni el perfil de dos modelos del
STT: el flujo por oraciones nunca se mezcló con el camino viejo, y en `develop`
quedó la otra línea. Lo que hay son **dos modos de cliente**: el cliente manda
`{"type":"text"}` y el backend traduce; el cliente manda audio y el backend
transcribe.

### Probar con tu propia voz (esto no lo puede hacer un agente)

```bash
./gradlew installDebug -Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate
adb reverse tcp:8080 tcp:8080
adb shell pm grant com.signai.webview android.permission.RECORD_AUDIO
adb logcat -c
adb logcat -s SignAiState SignAiMetrics SignAiNet SignAIWeb
```

Los logs:

```bash
adb logcat -s SignAiMetrics      # latencias del teléfono
adb logcat -s SignAiNet          # cada oración que llega del backend
adb logcat -s SignAiState        # en qué modo está el micrófono y por qué
adb logcat -s SignAIWeb          # consola del JS (modo depuración prendido)
```

> **Un agente no puede hablarle al micrófono.** La app usa
> `AudioSource.VOICE_RECOGNITION`, que es la fuente que Android afina para
> reconocimiento y que **cancela el eco del propio altavoz** — la elección
> correcta, porque con `MIC` el STT escucha la AGC bombeando en vez de la voz, pero
> que hace imposible un bucle de retorno acústico. Todo lo que se pruebe con voz
> necesita una persona. Para el resto están los fixtures.

---

## 3. Qué probar en el teléfono

### A. Dictado — la voz se vuelve texto y **espera**

1. Tocá el **micrófono** (se prende solo).
2. Hablá una frase y hacé una pausa de un segundo y medio. **No toques nada más.**
3. El micrófono **se apaga solo** cuando el STT cierra la frase.
4. **El muñeco no se mueve.** El texto queda en el campo, editable.
5. Arriba del avatar aparece el aviso *"Texto listo. Toca Traducir para representarlo."*
6. Recién ahí tocá **Traducir**, y ahí sí representa.

El punto de la prueba es el paso 4: **apagar no es mandar**. Antes sí lo era
(`sendDictation`), y por eso `trazabilidad.md` §2.1 tenía RF-STT-03 y RNF-REL-04
en "No implementado".

Dos cosas más que hay que comprobar:

- **El icono del micrófono no cambia.** Hay texto esperando y el botón sigue
  siendo el micrófono, porque volver a tocarlo **descarta lo que está escrito y
  dicta de cero**. Es cancelación explícita, que es lo único que RNF-REL-04
  permite como pérdida.
- **El backend no recibe nada.** Mientras está esperando, `rt_phrases_total` en
  `curl -s 127.0.0.1:8080/metrics` **no se mueve**. Es la prueba más directa de
  que la app no mandó nada.

En los logs:

```
SignAiState: micrófono prendido en modo Dictation
SignAiNet: recording 16000Hz mono, chunks of 30ms
SignAiMetrics: dictado #1: 6 palabras, 35 caracteres
SignAiNet: recording stopped                                   <- el mic se apagó solo
SignAiState: dictado cerrado: 35 caracteres a la espera de confirmación
```

Y si tocás el micrófono **sin** haber esperados el silencio:

```
SignAiState: micrófono apagado (venía de Dictation)
SignAiState: dictado detenido: esperando el final de la frase   <- el `end` abrió el cierre
```

Ese es el otro camino del `end`: la captura se cortó antes de que la pausa del
STT llegara, así que hay que esperar el `final` igual. Cuando llega con texto, se
suma; cuando llega vacío (ruido de fondo), es `final vacío: el utterance se cerró
sin palabras` y no rompe nada.

### B. En vivo — representa mientras hablás

1. Tocá **TR**.
2. Decí **"hola"** y hacé una pausa de un segundo antes de seguir.
3. Seguí con **"cómo estás"**.
4. **Seguí hablando todo lo que quieras** — el micrófono **no se apaga solo**.
5. Cuando te boredes, tocá TR de nuevo para apagarlo.

Cada vez que el STT cierra una frase por silencio, esa frase **sale sola**. El
interruptor solo apaga el micrófono; no es el que manda.

Y al apagarlo:

| | al apagar TR |
|---|---|
| Micrófono, Historial, TR | habilitados al instante |
| El campo | **vacío** |
| Traducir | habilitado |

**El campo queda limpio a propósito.** Lo que había ahí era la transcripción en
vivo de lo que el micrófono estaba oyendo, y para cuando se apaga el interruptor
eso ya se mandó y se representó. Dejarlo puesto tapaba el campo con una frase que
nadie puede volver a mandar, y con Traducir de vuelta a estar disponible el
botón invitaba a reenviar lo que el avatar acababa de representar. Lo único que
sobrevive es el texto de un dictado sin mandar, que es del usuario.

Mientras tanto, aunque al avatar todavía le falte cola, el resto del panel no se
bloquea: una oración larga son ~19 s de animación contra los ~2 s que tarda en
traducirse, y dejar todo deshabilitado mientras termina esconde al usuario una
app muerta sin explicación.

**Con cualquiera de los dos prendido el resto se apaga:** el campo de texto queda
de solo lectura (muestra lo que se está transcribiendo en vivo), y **Traducir** e
**Historial** quedan deshabilitados. Es a propósito: texto y voz van por el mismo
socket y el backend no puede saber de quién es cada fragmento; mezclados, el
avatar representaría la frase al revés.

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
2. Cambia de modo y sigue: lo que ya dictaste **no** se representa, lo que digas
   después sí.

Y al revés: si dictaste algo y después probaste TR, al apagar TR el campo vuelve
a mostrar lo que habías dictado, con su aviso de "esperando confirmación".
Perder tres frases porque quisiste probar el otro modo sería la peor forma de
que esto salga mal.

Esto se puede verificar **sin hablar**, con los fixtures de audio reales contra
el Whisper de verdad y el Ollama local:

```bash
python3 tools/medir-retraso.py        # manda los 28 fixtures por el socket
```

### D. Traducir y Repetir — la misma retención

1. Escribí una frase larga (5 o 6 señas, para tener ventana) y tocá **Traducir**.
2. La frase queda en el campo y **Traducir se deshabilita 1,5 s**; a los 1,5 s el
   texto se borra solo. En el log son `texto ya enviado` y `texto retenido borrado`
   (la segunda línea, ~1500 ms después de la primera).
3. Cuando termine de representar, tocá **Repetir** (el botón del escenario, abajo al
   centro). Tiene que pasar lo mismo.
4. **Durante toda la seña —no solo mientras traduce— el panel entero está
   deshabilitado**: micrófono, Historial y TR se ven en `muted` y no responden.

El punto 4 es el que se ve mejor mirando los tres iconos: pasan de `text` a
`muted` y vuelven. Si quedaran prendidos mientras el muñeco representa, es que
`replayPending` se soltó antes de `onPlaybackDone`.

---

## 4. Qué frases usar

### Para ver una sola oración

| Decí | Esperado |
|---|---|
| "hola" | HOLA |
| "gracias" | GRACIAS |
| "cómo estás" | COMO ESTAS |
| "buenos días" | BUENOS DIAS |
| "bien" | BIEN |
| "nunca" | NUNCA |

### Para ver varias oraciones

Acá es donde se nota el cambio: la pausa de 1,5 s es lo que parte la frase, y cada
oración se traduce por separado.

| Decí | Esperado |
|---|---|
| "me llamo Ana" | NOMBRE, NIÑA |
| "vivo en Bogotá" | VIVIR |
| "tengo hambre" | HAMBRE |
| "estoy contenta" | CONTENTO |
| "estoy triste" | SENTIR, TRISTE |
| "mi familia" | FAMILIA |
| "dónde vives" | DONDE, VIVIR |
| "quién es ella" | QUIEN, MUJER |
| "hola, buenos días, cómo estás" | HOLA · BUENOS DIAS · COMO ESTAS (3 oraciones) |

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
# reproducir un WAV por el WebSocket, a velocidad real, y ver la línea de tiempo
python3 tools/medir-primera-sena.py
adb logcat -s SignAiNet
```

### Qué mirar además del ritmo

- **El reposo**: al abrir la app, después de que termine una frase y con el
  micrófono prendido esperando, el muñeco tiene que estar **con los brazos abajo,
  al lado de los muslos**.
- **El paso de una seña a otra**: las 46 variantes de clip ya se encadenan solas;
  la app no mete nada en el medio y las funde durante 100 ms. Se nota sobre todo
  en el par de señas de una frase corta ("hola gracias"), que es donde el salto
  directo es más visible.
- **El tiempo de silencio del STT**: el panel de depurar lo muestra
  (`silencio STT: 1500ms`). Es el segundo bloque más grande de la latencia de
  punta a punta, así que cada corrida de pruebas debería anotarlo de la pantalla
  en vez de acordarse (decisión D-6). Sale de `/health` del backend, que lo copia
  del STT; si no se pudo leer, dice `?`.
- **Para verlo en números**: el panel de depurar muestra la fundición, cuántas
  actions están vivas (`2` mientras se funde) y la cola. En logcat sale una línea
  por seña: `fundición -> 0000.001` o `corte seco -> …`, más
  `signStart: oración→avatar Nms` por cada seña que arranca.

---

## 5. Qué señas hay

**32 con animación exportada** (las que el avatar puede representar de verdad). La
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

**Promedio 2.15 s por seña.** Es el número que explica por qué una frase larga
se sigue representando después de que el hablante terminó de hablar.

### El adelanto automático: se quitó (2026-10-16, D-5)

`app.js` multiplicaba la velocidad por 1,6 cuando había 2 o más señas en la fila
(`CATCHUP_BACKLOG = 2`, `CATCHUP_FACTOR = 1.6`). Ya no existe, y `BRG-15` en
`openspec/specs/ui/spec.md` queda marcada como eliminada.

Por qué, en orden de peso:

1. **Alteraba la variable que había que medir.** La prueba obligatoria **PR-PER-04**
   (`../sign-ai-docs/plan-pruebas/01-diseno/personas/pruebas.md`) pregunta a
   usuarios con LSC si la velocidad es adecuada, de 1 a 5. Con el adelanto prendido
   la respuesta mezclaba la velocidad que eligió la persona con un factor que
   nadie le dijo, y la prueba no podía separarlas.
2. **La carrera que venía a empatar no existe.** Quien lee el avatar es una persona
   sorda que no está mirando a quien habla.
3. **No estaba en ningún documento de la tesis.** Ni en requerimientos, ni en el
   plan de pruebas, ni en los doce diagramas del SDD. Y dos números mágicos sin
   criterio escrito contradicen la regla del backend de que ningún umbral numérico
   va hardcodeado en la lógica funcional.

Las cifras que Justificaban mantenerlo siguen siendo ciertas y `medir-retraso.py`
sigue midiéndolas (ya sin las columnas "con el adelanto"): el avatar no termina de
representar antes de que el usuario termine de hablar en **17 de las 28** frases,
representación/habla da **1,26× de mediana** y el desfase absoluto es de **+0,4 s
de mediana**, no de segundos.

**Eso ya no lo corrige el sistema.** Si molesta, la respuesta es PR-PER-04 y no
un factor escondido en la escena.

### Palabras que el catálogo no cubre

`estudiante`, `médica`, `restaurante`, `aqui`, `trabajo` (como sustantivo) y
cualquier nombre propio no tienen glosa. La respuesta correcta es que **no se
representen** y queden en el caption, no que el avatar improvise una seña. Si pedís
"soy estudiante" y el muñeco representa NIÑO, eso es un error de traducción, no del
avatar.

---

## 6. Métricas

Los tres servicios exponen lo mismo: Prometheus en `/metrics` y JSON en
`/metrics.json`.

```bash
curl -s 127.0.0.1:8080/metrics | less      # STT igual, en :8000
```

### Backend (`:8080`) — lo que el usuario percibe

| Métrica | Qué es |
|---|---|
| `rt_first_sign_ms` | frase recibida → **primera oración enviada**. La latencia que siente el usuario. Ver §1 para los valores y para por qué no se compara con la línea base. |
| `rt_phrase_total_ms` | frase recibida → última oración enviada |
| `rt_sentence_delay_ms` | por oración: cuándo salió cada una respecto de la frase |
| `rt_phrases_total` / `rt_sentences_total` / `rt_glosses_total` | cuántas frases, oraciones y glosas salieron |
| `rt_phrase_sentences` | histograma de cuántas oraciones tiene cada frase |
| `audio_bytes_total` / `audio_chunks_total` | audio reenviado al STT |
| `session_audio_rms` | nivel del audio entrante. Bytes que crecen con RMS en el piso = micrófono captando silencio. |

`rt_first_sign_ms` es la que decide si el producto se siente en tiempo real;
`rt_phrase_total_ms` y `rt_sentence_delay_ms` la explican cuando no.

En el STT (`:8000`) están los tiempos de inferencia, la espera por la GPU, el
atraso del audio y los descartes por motivo: `stt_dropped_total{reason=...}`.
`stt_partial_queue_ms` es el guard de regresión de los picos viejos de 4–8 s de
contención de GPU, y `stt_dropped_total{reason="logprob"}` caza al filtro
comiéndose audio que Whisper transcribió bien.

### Teléfono

```bash
adb logcat -s SignAiMetrics
```

El resumen (`resumen:`) trae `frases`, `oraciones`, `glosas`, `sin_seña`,
`dictado_frases`, `dictado_palabras`, `duracion_frase` y `duracion_firmado`.
`duracion_firmado` (`representado:`) es lo único que mide la escena; lo demás mide el
pipeline.

**`representado:` y `duracion_frase` son dos nombres para cosas distintas y no se
mezclan.** El primero es el tiempo que el avatar estuvo representando; el segundo, el
del turno de traducción. Con dos nombres para una sola cantidad, el segundo
habría parecido roto.

---

## 7. Tests sueltos

```bash
# ¿el contrato del WebSocket sigue igual? qué se traduce, qué no, y el `end`
docker compose -f ../sign-ai-backend/docker-compose.yml exec backend \
    python scripts/verify_client_frames.py

# ¿el divisor de oraciones parte bien?
docker compose -f ../sign-ai-backend/docker-compose.yml exec backend \
    python scripts/verify_sentence_segmenter.py

# extremo a punta, sin hablar
tools/medir-primera-sena.py
tools/medir-retraso.py
```

Los de Python se corren **dentro del contenedor**: en el host faltarían las
dependencias del servicio (httpx2 para el `TestClient` de Starlette), y un check
que solo pasa en un lado no verifica nada.

### Generar los fixtures de audio otra vez

Los `.wav` **no están en el repo** (son 1,5 MB de binario); sí están los `.json`.
Se generan con Piper:

```bash
cd ../sign-ai-stt
./venv/bin/python scripts/make_audio_fixtures.py --all
```

---

## 8. Problemas ya pisados

| Síntoma | Causa | Qué hacer |
|---|---|---|
| "No se pudo abrir el micrófono" | falta el permiso en runtime | `adb shell pm grant com.signai.webview android.permission.RECORD_AUDIO` |
| "Sin conexión con el servidor" en el teléfono | el `10.0.2.2` del build es para emulador | `adb reverse tcp:8080 tcp:8080` y compilar con `-Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate` |
| Transcripción vacía | filtro de confianza de Whisper | mirá `stt_dropped_total{reason="logprob"}` |
| La frase se parte en dos | dos oraciones separadas por 1,5 s de silencio | normal: es el divisor de oraciones haciendo su trabajo |
| El avatar representa una seña y después otra rara | glosa fuera de LSC50 | encendé **Ajustes → AVANZADO → depurar** y mirá `SignAiNet` |
| La medición sale a 1 ms y no es creíble | la caché semántica está caliente | `DELETE FROM semantic_cache;` **y reiniciar el backend** (§1) |
| `session failed: null` en logcat, en bucle | el backend se reinició y una sesión quedó colgada | reiniciá la app: `adb shell am force-stop com.signai.webview` |
| Al apagar TR queda todo deshabilitado y dice "Procesando..." | se confundió "el `done` ya llegó" con "el avatar ya terminó": una oración larga deja cola | la fuente de verdad es `queuePlaying`, no `playbackState` (`STA-18`) |
| El otro interruptor no se ve deshabilitado | el relleno `soft` atenuado al 35 % sobre `surface` da dos colores casi iguales | se apaga el icono y la etiqueta a `muted`, nunca la superficie (`STA-17`) |
| Traducir manda dos veces lo mismo | el campo tiene la frase que la voz ya representó | 1,5 s de retención: el texto se ve, Traducir no, y después se borra solo (`STA-20`) |
| **La misma frase se representa una y otra vez, el backend al 40 % de CPU** | el backend hacía eco de la frase escrita como un `transcript` y la app lo tomaba por voz | **ya está corregido**: `handle_text` no manda `transcript`. Si vuelve a pasar, mirá `rt_phrases_total` subiendo solo |
| Queda en "Dictando..." para siempre con el micrófono prendido | el ambiente tiene ruido por debajo del umbral de energía y el VAD nunca abre frase | apagá el micrófono a mano: el `end` siempre produce un `final` |
| Tocar un botón no hace nada | el tap cayó en el borde de la superficie | `mobile-mcp` hay que usarlo con el **centro** del elemento, no con la esquina |
| Un test falla con `httpx2` faltante | ese test necesita el entorno del servicio | corré los tests **dentro** del contenedor |

### El micrófono no se puede probar con el audio del propio teléfono

Ya está arriba y conviene no volver a intentarlo: la app usa
`AudioSource.VOICE_RECOGNITION`, que **cancela el eco del propio altavoz**. Es la
elección correcta —con `MIC` el STT escucha la AGC bombeando en vez de la voz—,
pero significa que un bucle de retorno acústico es imposible.

### Cómo se ve que el micrófono está capturing silencio

```bash
# en una habitación silenciosa, con la app grabando
curl -s 127.0.0.1:8080/metrics.json | python3 -c "
import json,sys; d=json.load(sys.stdin)
print('rms de sesión:', d['gauges']['session_audio_rms'])
"
```

`audio_bytes_total` subiendo con `session_audio_rms` en el piso es el micrófono
funcionando y el umbral de energía filtrando todo: el VAD nunca abre frase, no
hay `final` por silencio y el dictado queda esperando. Es el comportamiento
correcto, no un bug — pero es la razón por la que el micrófono solo se prueba
hablando.

### Dónde tocar cuando algo no cierra

| Síntoma | Variable |
|---|---|
| Que la frase se cierre más rápido o más tarde | `SILENCE_TIMEOUT_MS` (STT) |
| Que una frase larga se parta en más oraciones | el divisor de oraciones del backend, `verify_sentence_segmenter.py` |
| Qué es un acierto de caché | `SEMANTIC_CACHE_SIMILARITY_THRESHOLD` (backend) |
| Que el STT use más o menos GPU | `MODEL_SIZE` (STT; en este flujo hay **un solo** modelo) |