# SignAI WebView — Agent Instructions

Thesis: Spanish speech → LSC via 3D avatar. This repo holds **only the WebView app code**; the rest of the system lives in sibling repos — see § Mapa del sistema below before touching anything in the pipeline.
Old approaches (Flask server, Kotlin+Filament native, Unity) are dead — ignore any docs referencing them.

## Mapa del sistema: los repositorios hermanos

Esta app es **una de las piezas** del sistema SignAI (tesis, Ingeniería de Sistemas,
PUJ). Las otras viven en carpetas hermanas bajo `../`, todas en la organización
`sign-ai-organization` de GitHub. **Cualquier sesión que toque el pipeline tiene que
mirar los hermanos antes de decidir**: el contrato entre ellos son frames de
WebSocket, no imports, así que un cambio acá rompe allá sin que el compilador avise.

```
../sign-ai-stt ──PCM16/WS──▶ ../sign-ai-backend ──WS/JSON──▶ ../sign-ai-android (esta app)
                                      │
                                      └──▶ Neon (Postgres 16 + pgvector): catalog, semantic_cache

../sign-ai-docs  ← requerimientos, diagramas del SDD, plan de pruebas (nada corre)
../signai-avatar ← demo vieja en Flask del pipeline de landmarks (dormida)
```

| Repo | Qué es | Rama | Puerto | Se levanta con |
|---|---|---|---|---|
| `../sign-ai-backend` | Monolito modular FastAPI: WS con el cliente, NLP de glosas LSC, validación contra catálogo, caché semántico, Neon | `develop` | 8080 | `docker compose up --build` (levanta **también** el STT) |
| `../sign-ai-stt` | faster-whisper + WebRTC VAD: audio → texto en tiempo real | `develop` | 8000 | incluido en el compose del backend, o solo desde su repo |
| `../sign-ai-docs` | Requerimientos consolidados, diagramas del SDD (drawio + png), plan de pruebas, cambios pendientes | `main` | — | no corre nada |
| `../signai-avatar` | Demo Flask del pipeline de landmarks de LSC50 (`HandCorrector`, `PoseFilter`, WiLoR) | `main` | — | inactiva desde 2026-09-19 |

### `../sign-ai-backend`

**Leé `CLAUDE.md` (147 líneas) antes de tocarlo.** Tiene las reglas que no están
acá: código y comentarios **en inglés sin excepción**, los nombres de clases tienen que
coincidir con los del SDD, y **nunca hacer `git commit` ni `git push`** — se sugiere
el mensaje y lo manda el owner, por PR contra `develop`.

- **Contrato con la app**: `docs/android-integration.md` (461 líneas) es la fuente de
  verdad del WebSocket `/ws/translate`: qué campos manda cada frame, qué espera la
  app, y el ejemplo completo de una frase. **Es lo primero que hay que leer si se toca
  el protocolo**, y hay que actualizarlo en el mismo cambio.
- Arquitectura: `Controller` → `Orchestrator` → `NLPService`/`ValidatorService` → Neon.
  `CatalogRepository.load_all()` deja el catálogo entero **en memoria al arrancar**:
  después de un seed hay que reiniciar el backend, no hay invalidación.
- **`semantic_cache` es la trampa**: cambiar el prompt o el modelo **no la invalida**,
  y las glosas viejas siguen saliendo por `HIT`, así que el arreglo parece no haber
  servido. `DELETE FROM semantic_cache;` (es caché derivado, se reconstruye solo con
  la primera frase de cada una).
- `.env` (gitignored) tiene `DATABASE_URL` (Neon), `LLM_*` (`LLM_BASE_URL` vacío =
  OpenAI; apuntarlo a uno local con `host.docker.internal`) y
  `STT_WS_URL=ws://stt:8000/ws/transcribe` — dentro de Docker el host es `stt`, no
  `localhost`. **`REALTIME_ENABLED` ya no existe** (2026-10-15): no hay dos modos de
  backend, hay dos modos de cliente.
- `scripts/verify_*.py` son las pruebas: se corren **como módulo** desde la raíz del
  repo (`python -m scripts.verify_client_frames`), sin pytest. `verify_client_frames.py`
  cubre el contrato del cliente (frase mandada como `text`, `end` y que **la voz no se
  traduzca**) y es el que fija que no haya eco: `read_exactly(ws, 3)` con
  `THREE_MESSAGES = ["segment", "segment", "done"]`.

### `../sign-ai-stt`

Sin AGENTS.md ni CLAUDE.md: su documentación es el `README.md` (235 líneas), muy
completo. Mismo criterio de git que el backend: **preguntarle al owner, PR contra
`develop`**.

- **Protocolo**: el cliente manda frames **binarios** PCM16LE mono 16 kHz sin cabecera
  WAV (el servidor los reagrupa en 30 ms para el VAD) y el STT responde con **JSON de
  texto**. Un frame de texto plano `"end"` fuerza el cierre del segmento.
- `words` y `audio_ms` son **aditivos** (2026-10-14): el backend los usa para decidir
  qué seña firmar y cuándo, pero el STT no depende de que alguien los lea.
- Perfil de tiempo real: **dos modelos** (`PARTIAL_MODEL_SIZE=small` para los
  parciales, `MODEL_SIZE=medium` para los finales) + `InferenceScheduler` +
  `SILENCE_TIMEOUT_MS=600`. `REALTIME=false` devuelve los defaults previos.
- **Los 28 fixtures de `fixtures/audio/*.wav` no están en git** (los binarios no; los
  `.json` sí). Se generan con `python -m scripts.make_audio_fixtures.py --all` en un
  venv aparte con Piper. Los números de latencia solo son comparables entre corridas
  porque el script produce siempre el mismo WAV para el mismo texto.
- De `fixtures/` saca el audio `tools/verificar-tiempo-real.sh` para las pruebas end-to-end.

### `../sign-ai-docs`

Documentación de la tesis: `documentos/requerimientos/SignAI_Requerimientos_Consolidados_v0.15.md`
(el documento formal), `diagramas/` (los `.drawio` editables y los `.png` del SDD:
lógica, despliegue, procesos, clases, secuencia, persistencia y **estados de la UI**) y
`plan-pruebas/` (el plan de pruebas del proyecto, con su `trazabilidad.md`).

- **`modificaciones/requerimientos.md` es una bandeja de cambios pendientes**, con
  estados **Propuesto / Acordado / Hecho**: ahí caen las cosas que la app ya hace y el
  documento todavía no dice (modo en vivo, entrada de texto, historial, apagado del mic…).
  Si se implementa algo sin requisito, se anota ahí.
- Los `.drawio` y el `.md` de requerimientos los edita el owner: los cambios grandes
  se proponen, no se aplican en silencio.

### `../signai-avatar`

**No es la fuente de los GLB de esta app.** Es la demo anterior en Flask que servía un
avatar desde landmarks de LSC50 (`HandCorrector`, `PoseFilter`, script WiLoR para
manos) y tiene su propio `openspec/` con `specs/avatar|dataset|pipeline`. Está
**inactiva desde 2026-09-19**. Los assets de acá salen de otro pipeline: exportaciones
de Cascadeur en `GLB Senas/` → `tools/split_signs.py`. No mezclar los dos pipelines ni
copiar scripts entre ellos.

### Carpetas hermanas que **no** son repositorios

Sin git y sin reglas: material compartido o restos.

- `../openspec/` — specs de `avatar`/`dataset`/`pipeline` y `changes/` con trabajo de
  avatar. Es el OpenSpec **compartido, distinto** del `openspec/` de esta app (que además
  tiene `specs/ui` y `sessions/`); las copias de `avatar`/`dataset` divergen entre sí, así
  que saber cuál manda requiere preguntar.
- `../data/` — `LSC50.pdf` y `LANDMARKS/`: el dataset de origen (las 50 señas).
- `../Resources/` — scripts viejos y docs (ADR, documento maestro, backlog). Incluye
  `split_avatar_for_filament.py` y `retarget_blender.py`, que son del **approach
  abandonado**: no sirven de referencia para nada de acá.
- `../signai_env/` — un venv de Python, no un repo.

### El contrato entre repos (lo que se rompe en silencio)

Un único WebSocket, de la app **solo al backend**: `:8080/ws/translate`. La app nunca
habla con el STT ni con Neon. De ahí salen los acoples que hay que tocar de los dos lados:

- **Solo se traduce lo que el cliente manda como `text`.** La voz entra por el mismo
  socket pero vuelve como `transcript`, y esa transcripción **no se traduce**. Si el
  backend hiciera eco de la frase escrita, la app lo tomaría por una frase de voz y la
  reenviaría: ese bucle existió y quemó el proceso (una frase repetida ~2.900 veces, 44 %
  de CPU). `handle_text` no manda ningún `transcript` y `verify_client_frames.py` lo fija.
- **`segment` es una oración, con `index` y `total` reales desde el primero.** Ya no
  existen `stream`, `provisional` ni `source_text`, y la app no encadena: arma la cola
  entera cuando llega el último. El backend manda las oraciones **en orden** aunque las
  traduzca en paralelo.
- **El `final` siempre llega**, incluso vacío: el STT responde a todo `end`
  (`flush()`). Un `final` sin texto es "no se escuchó nada", no un error.
- **Los mismos nombres de métrica en los dos lados** (`rt_*` en el backend,
  `SignAiMetrics` en el device): un número que no se instrumentó en las dos partes no se
  puede comparar con nada. `rt_first_sign_ms` conserva el nombre aunque cambiara lo que
  mide, para que se pueda comparar contra la línea base — aunque **no** sea comparable:
  la línea base es del camino por palabras.

Y tres herramientas de este repo que **dependen del código de los hermanos**:

- `tools/verificar-tiempo-real.sh` levanta el compose de `../sign-ai-backend` (que incluye
  `../sign-ai-stt`) y usa sus fixtures.
- `tools/mock_backend.py` importa `../sign-ai-backend/app/nlp/llm_client.py` y replica su
  `catalog_repository`: renombrar o mover esos módulos rompe el mock.
- `tools/local_stt.py` arranca el STT desde `../sign-ai-stt`.

### Reglas para que las sesiones se entiendan entre repos

1. **Un contrato se lee en los dos lados antes de cambiarlo.** Frames del WS, nombres de
   glosas, columnas del catálogo, nombres de métricas: primero el repo hermano (con su
   `CLAUDE.md`/`README.md`), después el cambio acá.
2. **Un cambio que cruza el contrato no está terminado hasta que el otro lado está
   actualizado**, y hay que decirlo explícitamente al owner: qué se cambió, en qué repos y
   qué falta del otro lado. Pendiente de otro lado es deuda visible, no un detalle.
3. **Cada sesión deja rastro donde corresponde**: `openspec/sessions/YYYY-MM-DD-<slug>.md`
   acá y en el repo hermano que se tocó. Cuando el dato venga de afuera, dejar **de qué
   repo y de qué commit** salió: sin eso la próxima sesión no puede reproducirlo.
4. **Nada de commits en los hermanos sin que el owner lo pida** (PR contra `develop`). Acá
   sí se commitea directo. Un cambio de contrato commiteado en un solo lado es un cambio a medias.
5. **Referenciá los hermanos por ruta relativa** (`../sign-ai-backend`), nunca con rutas
   absolutas de esta máquina: las sesiones se corren en directorios distintos.
6. **Los números se miden donde están.** Latencias del device acá (`adb logcat -s
   SignAiMetrics`), del STT y del backend allá. Un p50 del avatar mezclado con un p50 del
   STT no es un p50 de nada.
7. **Cuando algo no se pueda resolver leyendo, preguntar.** Las decisiones abiertas están
   marcadas como tales en los `CLAUDE.md`/`docs/` de cada repo; no rellenarlas por inferencia.

## Modo en vivo (rama `flujo-oraciones`, 2026-10-15)

The app signs **while the user speaks**, and the unit is the **sentence**. The pipeline is:
mic → PCM16/16 kHz → WebSocket → backend → STT → the phrase is split into sentences →
sentences are translated → the avatar signs them in order.

El cambio grande de esta rama (auditoría `../sign-ai-docs/auditoria-flujo-2026-10-15.md`
§6): antes el backend mandaba fragmentos (`stream: true, total: 0`) y el avatar firmaba
**a mitad de frase**. Ahora solo hay oraciones, con `total` conocido desde el primero. El
efecto es que el avatar **no** empieza a firmar hasta que termina la primera oración, y
`rt_first_sign_ms` sube por construcción. Es el costo consciente de la opción A (§7 de la
auditoría): la línea base publicada —913 ms p50 / 1336 ms p95— es del camino por palabras
y **no es comparable**. Falta remedir.

**Leé `../sign-ai-docs/auditoria-flujo-2026-10-15.md` §6.3 antes de tocar esta área**, y
después `tools/TIEMPO-REAL.md`.

```bash
tools/verificar-tiempo-real.sh        # todo: servicios, tests, 28 fixtures, métricas, device
tools/verificar-tiempo-real.sh incremental
tools/medir-retraso.py                # ¿hace falta el adelanto automático? (ver abajo)
```

### How it works

- **El backend solo traduce `{"type":"text"}`.** El cliente manda la frase una sola vez
  cuando decide mandarla, y recibe un `segment` por oración con `index`/`total` reales.
- `MainScreenState.pendingSegments` es un `sortedMap` por `index` y la cola se arma en
  `flushSegments()` cuando `size >= total`. **No hay `streamMutex`** y no hace falta: el
  backend manda en orden.
- **`REALTIME_ENABLED` ya no existe.** No hay dos modos de backend: hay dos modos de
  **cliente**. El client manda `text` y el backend responde; el client manda voz y el
  backend transcribe. Un backend viejo que mande `stream`/`provisional` no rompe el parser,
  que es tolerante.

### El micrófono son dos interruptores, y apagado no es mandar

| | Botón | Qué hace al tocarlo | Qué firma |
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
  prendido y el muñeco firmando, el estado es `Signing`.
- **Solo `Live` es continuo.** En `Dictation` el `final` del STT apaga la captura, y por eso
  `dictationBuffer` ya no acumula entre frases: el STT corta a los 1,5 s de silencio.
- **Los dos interruptores se deshabilitan por el otro, en los dos sentidos, y ninguno se
  deshabilita mientras está prendido.** Prendido, cada uno es el único que puede cortar su
  modo. **Un control deshabilitado se marca apagando el icono y la etiqueta a `muted`, nunca
  bajándole el alfa a la superficie** (ver `SignAiBottomBar`).
- **Apagar el TR deja el campo limpio** (`stopCapture`), no retenido: para entonces lo que
  decía el campo ya se firmó. Lo único que sobrevive es el texto de un dictado sin mandar.
  **Apagar el TR tampoco manda nada**: el en vivo manda cada frase al cerrarse, no al
  apagarse.
- **Lo ya enviado no se puede volver a mandar.** El campo conserva la frase en `readOnly`
  con Traducir deshabilitado durante `SentTextHoldMs` = **1500 ms** (`sentText`), y después
  se borra y el campo y Traducir vuelven **en la misma asignación**. Son **tres** los
  caminos que firman sin que nadie toque Traducir (en vivo, Traducir a mano y el Repetir);
  el dictado ya no es uno, porque su texto es del usuario. La retención se prende en
  `requestTranslation`, el punto donde se sabe que la frase salió de verdad. Hay log de los
  dos momentos: la ventana es más corta que el ida y vuelta de `mobile-mcp`, así que el
  logcat es la prueba y el árbol de accesibilidad la confirmación.
- **El tercer estado de RF-UI-01 se descartó** (decisión del owner el 2026-10-15, después
  de probarlo en el Redmi): el micrófono **no** cambia de icono y sigue siendo la única
  forma de dictar de nuevo. El aviso va en el **hint del escenario** ("Texto listo. Toca
  Traducir para firmarlo."), no en el campo, porque con la frase escrita arriba un
  placeholder no se ve nunca. Tocar 🎤 con texto esperando **lo descarta**: es cancelación
  explícita, lo único que RNF-REL-04 permite como pérdida. **Esto es una desviación del
  documento de requisitos** — RF-UI-01 sigue diciendo tres iconos — y hay que decirlo.
- **La ruta del `final` la decide el cliente, no un flag.** El `dictation: true` que usaba
  el backend ya no existe: con el disparador del envío en la app, `micMode == Dictation ||
  dictationAwaitingFinal` alcanza. Un `final` vacío va a `onEmptyFinal` y no rompe nada.
- **Con el mic prendido, Traducir e Historial están deshabilitados y el input es
  `readOnly`.** Texto y voz van por el mismo socket y el backend no puede saber de quién
  es cada fragmento. El input sigue mostrando la transcripción en vivo, que es lo que el
  usuario quiere ver: por eso `readOnly` y no `enabled`.
- **En vivo, una frase que llega con otra en vuelo espera en `livePhraseBacklog`** — un
  único lugar, a propósito. Con más de uno sería una cola que hay que vaciar, y una cola
  que hay que vaciar es una cola que se pierde en algún `fail()`.

### Gotchas

- **El adelanto automático se queda, pero hay que saber por qué**
  (`tools/medir-retraso.py`, 2026-10-15). Las 32 señas duran 2,15 s de media y cada palabra
  es una seña: el avatar no termina antes de que el usuario termine de hablar en **17 de
  28** frases de prueba, y con el adelanto (`CATCHUP_BACKLOG = 2`, `CATCHUP_FACTOR = 1.6`)
  todavía en 7. Firma/habla da 1,26× de mediana, que 1,6× cruza. **Lo que cambió es la
  forma de la cola**: está vacía mientras el usuario habla y se llena de golpe al cerrar
  cada frase, así que el adelanto ya no está prendido todo el tiempo. Como el umbral se
  mide después de que arranque la seña en curso, hace falta una frase de 3+ señas para
  dispararlo. **No prometer que el avatar firme al ritmo de la voz**: harían falta ~5×,
  que es un fast-forward. `setSpeed` guarda `baseSpeed` aparte porque el adelanto
  multiplica sobre el ajuste del usuario, no lo reemplaza.
- **`turnStartedAt` arranca cuando se manda la frase**, no al abrir el micrófono. La
  unidad es la frase completa, así que el tiempo que el usuario tarda en hablarla no es del
  turno de traducción. Lo que sí incluye es la espera por la frase anterior en vuelo.
  `turnStartedAt` mide **el pipeline**, no la escena: el número que mide el avatar es
  `firmado:` (`SignAiMetrics.onPlayback`).
- **Release the mic without speaking**: returns to Idle immediately with "No se escuchó
  nada." Waiting the full 15 s transcription timeout and then reporting a failure that did
  not happen is the old behaviour. `heardAnythingThisSession` separates "user said nothing"
  from "we lost the audio".
- **`pendingSegments`/`pendingTotal` son estado de turno**, los limpia `resetTurn()` en
  cada frase nueva y en `fail()`. Un turno que murió a mitad no puede filtrar oraciones al
  siguiente.
- **`fail()` limpia `dictationAwaitingFinal` pero no el texto**: un fallo de envío no es
  motivo para perder lo que el usuario dijo.

## What it is

Single-module Gradle project (`SignAI-WebView`, `:app` only). Compose shell + WebView that renders a local Three.js avatar. No backend, no network API.

- Kotlin UI: layered packages under `app/src/main/java/com/signai/webview/` — `data/` + `domain/` (business), `ui/state/` + `ui/webview/` (presentation), `ui/{theme,designsystem,icons,components}/` (reusable UI), `ui/screens/` + `ui/MainScreen.kt` (screens). `MainActivity.kt` is a ~27-line entry point.
- Web scene: `app/src/main/assets/web/` — `index.html`, `app.js`, `bosque.jpg`, `manifest.json` + `models/avatar.glb` (the character) + `signs/<id>.glb` (one per sign, animation only)
- Package `com.signai.webview`, portrait-locked (`AndroidManifest.xml`)

## Layers (Clean Architecture, practical)

Dependency rule: screens → components → state/webview → data/domain, **never backwards**. No Hilt/ViewModel: `MainScreenState` (holds `SnapshotStateList` history + `Settings` + `sign` + playback state) is built in `MainActivity` and passed down; `SharedPrefsSignHistoryRepository` and `SharedPrefsSettingsRepository` own SharedPreferences; `WebViewSignAiController` is the only place with `evaluateJavascript`/`RenderEffect`; `domain/SignAiCommands`, `domain/SignsCatalog`, `domain/Gloss` and `domain/Settings` are pure Kotlin.

## Build

```bash
./gradlew build          # verify before committing
./gradlew installDebug   # needs device/emulator via adb
```

**MANDATORY after every change**: compile/verify the code and then run `./gradlew installDebug` on the connected device/emulator so the user can test immediately — never end a work item without installing. If no device is present, say so explicitly.

**Physical device over USB**: the default `BACKEND_WS_URL` is `ws://10.0.2.2:8080/ws/translate`, which is the *emulator* loopback alias. On a real phone use `adb reverse tcp:8080 tcp:8080` and build with `-Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate`. Without it the app shows "Sin conexión con el servidor" and it looks like a backend problem.

**`RECORD_AUDIO` is a runtime permission and is not pre-granted.** Tapping the mic with it missing says "No se pudo abrir el micrófono", which reads like an STT failure but is not. `adb shell pm grant com.signai.webview android.permission.RECORD_AUDIO`.

**El teclado se cierra sacándole el foco al campo, no solo escondiéndolo.** `rememberKeyboardDismissAction()` (`ui/designsystem/foundation/`) hace `focusManager.clearFocus(force = true)` y después `keyboard?.hide()`. Con solo `hide()` el teclado se iba un segundo y **volvía**, porque el `TextField` seguía con el foco y la siguiente recomposición lo mostraba de nuevo; el `force = true` es porque un campo `readOnly` (mic prendido, o el texto retenido) ignora el cierre normal. Regla general: **tocar un control cierra el teclado, no manda la frase** — el texto se queda en el campo. Y el `onDone` del teclado cierra **siempre**, aunque no haya nada que mandar: si dependiera de `sendEnabled`, con el mic prendido el botón de aceptar no haría nada y el usuario no tendría salida.

No tests, no CI, no lint. Toolchain: AGP 8.9.3, Kotlin 2.3.21, compileSdk/target 36, minSdk 26, JVM 17, Compose BOM 2024.12.01. Bumped from 8.7.3/2.1.0/35 → 8.9.3/2.3.21/36 in `ui-migration-lumo` Fase 0 (Phosphor 1.0.5 requires compileSdk 36 + Kotlin 2.3+ metadata).

## Observability

Every service in the stack exposes Prometheus metrics at `/metrics` and JSON at
`/metrics.json`; the device logs its own under logcat. When changing the
pipeline, **add a metric before adding the feature** — a number you did not
instrument is a number you cannot claim improved.

| Where | Tag / endpoint | What |
|---|---|---|
| STT | `:8000/metrics` | inference ms per model, GPU queue wait, audio lag, drops by reason |
| Backend | `:8080/metrics` | `rt_first_sign_ms`, `rt_phrase_total_ms`, `rt_sentence_delay_ms`, `rt_phrases_total`, `rt_sentences_total`, `rt_glosses_total`, `rt_phrase_sentences`, `audio_bytes_total` |
| Device | `adb logcat -s SignAiMetrics` | sentences per phrase, gloss count, **signing duration** (`firmado:`), p50/p95/max, dictated phrases and words |
| Device | `adb logcat -s SignAiNet` | every `segment` the backend sent, with `index`/`total` |
| Scene | `adb logcat -s SignAIWeb` | JS console (needs debug mode on in Settings → AVANZADO). Con depurar prendido también imprime la cola y el adelanto: `cola N/M, adelanto on/off` |

`rt_first_sign_ms` is the one that decides whether this feels real time. `rt_phrase_total_ms`
and `rt_sentence_delay_ms` explain it when it does not. In the STT, `stt_partial_queue_ms` is
the regression guard for the old 4–8 s GPU contention spikes, and
`stt_dropped_total{reason="logprob"}` catches the filter eating audio that Whisper transcribed
correctly.

**Ojo con `rt_first_sign_ms`**: el nombre se conservó aunque cambiara lo que mide, para poder
compararlo contra la línea base, pero **la línea base es del camino por palabras**. Con
oraciones la primera seña sale al terminar la primera oración, así que la cifra sube por
construcción. Antes de citarla hay que remedirla con las 28 frases
(`tools/verificar-tiempo-real.sh`).


## Git workflow

**Este repo es del owner: commit y push directo a `main`, sin PR ni rama de feature** (decidido 2026-09-30). No abrir PRs acá ni pedir confirmación para commitear. Los repos hermanos (`../sign-ai-backend`, `../sign-ai-stt`) sí usan PR contra `develop`, y el `CLAUDE.md` de ese repo manda no commitear sin que el usuario lo pida explícitamente. Ver § Mapa del sistema para las reglas de git de cada hermano.

**Ya no existe la rama `tiempo-real`** (2026-10-14): el trabajo de tiempo real quedó mergeado en **`develop`** en los tres repos — acá, en `sign-ai-backend` y en `sign-ai-stt` — y `main` es un commit adelante con la nota al respecto. Si una sesión encuentra referencias a `tiempo-real` en documentación o mensajes viejos, están desactualizadas: la rama actual es `develop`.

**Hay trabajo en vuelo en `flujo-oraciones`** (los tres repos, desde el 2026-10-15, **sin mergear**): el flujo por oraciones de la auditoría `../sign-ai-docs/auditoria-flujo-2026-10-15.md`. Si una sesión arranca en esa rama, esa es la que manda sobre `develop` hasta que el owner mergee, y hay que leer `docs/CAMBIOS.md` / `docs/CAMBIOSBACKEND.md` / `docs/CAMBIOSSTT.md` antes de tocar el pipeline.

## Neon

**Org: `org-orange-surf-39007829` (`sign-ai`).** Ese es el proyecto de la tesis. La cuenta tiene una segunda organización, `org-steep-paper-48927149` (`Diego`), que es personal — no tocar. Por eso el MCP de Neon **exige pasar `org_id`**: sin él devuelve *"Multiple organizations found"* y no hace nada.

Credenciales en `.envJulian` (`DATABASE_URL`, gitignored) — si `psql` dice `parse error near '&'` no es el archivo: es que la línea tiene `&` sin comillas. Usar `export $(grep -o '^DATABASE_URL=.*' .envJulian | head -1 | xargs)`.

La app **no habla con Neon**: la base es del backend. Neon se usa para leer/seedear el `catalog` (ver `openspec/specs/ui/spec.md`).

`catalog` **ya está sembrada** con las 50 glosas de LSC50 (`tools/seed_catalog.py --apply`, 2026-10-14). Reejecutarla es seguro: es idempotente. Tres cosas que no están a la vista:

- Las glosas van **sin tilde y sin `¿`/`?`**, pero **la `Ñ` se conserva** (`NIÑO`, no `NINO`). El backend pliega los dos lados de la búsqueda con `normalize_gloss()`, que usa una tabla explícita y no `unicodedata`, justamente para no separar la `Ñ` en `N` + tilde combinante.
- El catálogo anuncia **las 50**, pero solo **32** tienen animación exportada. Las otras 18 (`0032`–`0049`) llegan con `available: true` y un path que no existe: es lo que dispara `GLS-10` / `SignsCatalog.isOwnAssetPath()`. **No "arreglar" eso en la app** pidiéndolas por HTTP: son un 404 garantizado.
- `glb_animation_path` es **siempre** un path same-origin dentro del APK (`signs/<id>.glb`), nunca una URL. Es lo que permite que la app resuelva sin red.

El backend carga `catalog` **una sola vez al arrancar** (`load_all()`): después de un seed hay que reiniciarlo, no hay invalidación.

## UI direction

- Jetpack Compose is the UI framework. New components use **Lumo UI by Noman R** (https://github.com/nomanr/lumo-ui, docs https://www.lumoui.com/) — NOT Vaadin's Lumo Design System. It is Compose-specific and shadcn-like: adopt its components as a base, then customize them into our own SignAI Design System. Consult its current docs before making Lumo decisions; don't assume it works like a conventional component library.
- Migrate existing Material 3 away progressively, never add new Material 3 components.
- Icons: Phosphor Icons for anything new; avoid `material-icons-extended` in new code.
- Build a SignAI Design System on top of Lumo UI; centralize colors, typography, spacing, sizes, shapes, states, and animations there.
- Palette (single source of truth): **Lumo palettes**, edited in `ui/designsystem/Color.kt` (`// CUSTOMIZED`). **Dark is the default** (`Settings.darkTheme = true`).
  - **Dark = the SignALL brand** (owner, 2026-10-16, replacing the prototype's dark palette): `background #0C142C`, `surface #141D3A` (derived), `outline #263463` (derived, 1.52:1), `text #FFFFFF`, `muted #A7B0C7` (derived), `soft #1E2A4D` (derived), `strong`/`primary` = accent `#1C4CFC`. Derived shades are documented in a table in the file. **The accent is only 2.3–2.8:1 as a thin mark on those surfaces**, so small/selected marks use `colors.text`, not `strong` (that's why the character picker's check is white). The 3D canvas follows: `app.js`/`index.html` background `#0c142c`, grid `#1e2a4d`.
  - **Light = the Vercel prototype tokens verbatim** (`PrototipoFrontend/app/globals.css`), **unchanged**: `background #F4F4F2`, `text #171717`, `surface #FEFEFC` (opaque composite of the prototype's `rgba(255,255,253,.92)` + `blur(14px)` — Compose has no cheap region blur), `outline #DEDED9`, `muted #777773`, `strong #171717`, `soft #E8E8E3`. `primary` follows `strong` (the prototype has no blue accent).
  - Tokens `strong`/`onStrong`/`soft` and `muted` (alias of `textSecondary`) live next to them. `ui/theme/SignAiTheme` delegates to Lumo and exposes `colors: Colors` — `SignAiColors` no longer exists. `LocalIsDarkTheme` (`ui/designsystem/Theme.kt`) is what lets a component pick between the navy and white wordmarks; the canvas background switch goes to JS over the `setTheme` bridge.
  - Do NOT re-run `lumo --setup` (overwrites).
- Tokens live in `ui/designsystem/foundation/`: `SignAiSizes`, `SignAiShapes`, `SignAiSpacing`, `SignAiMotion` (durations/easings + `LocalReduceMotion`) and `pressScale`. Use them instead of inline `.dp`/literals.
- Theming: `SignAiTheme(darkTheme, fontScale, uiScale, bold, highContrast, reduceMotion)` applies `Settings`. `interfaceSize` scales `LocalDensity`; `large`/`bold` transform the typography; `contrast` overrides `outline`; light mode also switches the 3D canvas background via the `setTheme` bridge command.
- Font: the prototype uses `Arial, Helvetica, sans-serif`; Android ships neither, so **Liberation Sans** (metric-compatible with Arial) is bundled in `res/font/` and wired as `SignAiFontFamily` in `ui/designsystem/Typography.kt`. This keeps layout and text weight identical to the prototype.
- Spinner/loader: `SignAiSplash` (`ui/components/SignAiSplash.kt`) is shown until `AvatarWebView`'s `onLoaded`, with real progress from `onProgressChanged` and an 8 s timeout fallback; content fades in (500 ms).
- Screens: `ui/screens/stage/AvatarStage.kt` (avatar + hint + caption + Repetir), `ui/screens/bottombar/SignAiBottomBar.kt`, `ui/components/HistoryBottomSheet.kt`, `ui/screens/conversation/ConversationPanel.kt` (left drawer), `ui/screens/settings/SettingsSidebar.kt` (right drawer, sections AVATAR/ACCESIBILIDAD/APARIENCIA/AVANZADO), `ui/screens/about/AboutScreen.kt`. Branding ("SignAI", may become "Seña") is centralized in `ui/SignAiBranding.kt`.
- Look: minimalist, modern, dark mode base with good contrast and legibility. The 3D avatar stays the visual protagonist.
- Transparencies, subtle surfaces, blur, and animations are allowed only when subtle, functional, lightweight, and in moderation.
- Keep SignAI components reusable and separate from screens. Keep the UI lightweight — never degrade WebView 3D performance with unnecessary libraries, effects, or animations.

## Kotlin ↔ JS bridge

WebView loads via `WebViewAssetLoader` at `https://appassets.androidplatform.net/assets/web/index.html`. JS console forwards to Logcat tag `SignAIWeb`.

Contract (`window.SignAI` in `app.js`), **backward compatible** — keep the `?.` guard and these exact names:
- Original 5: `play()`, `setDebug(bool)`, `setFreeCamera(bool)`, `setGrid(bool)`, `setBackground(bool)`.
- Added (additive): `setSpeed(factor 0..1)` (0.5 ≈ normal), `setTheme(dark)` (canvas background follows the app theme), `setReduceMotion(bool)` (0 = corte seco entre señas en vez de fundición).
Kotlin calls them with `evaluateJavascript("window.SignAI?.<method>(...)")`.

JS → Kotlin: `window.SignAIBridge.postMessage("onPlaybackDone")` when the queue finishes, and `postMessage("avatarReady")` once the character is parsed and in the scene (that second one is what hides the splash — `onPageFinished` fires while the 10 MB GLB is still downloading). Kotlin registers the listener with `WebViewCompat.addWebMessageListener` (feature-guarded) and drives the playback state machine; when the feature is missing, `onPageFinished` falls back to hiding the splash and `SignAiSplash`'s 8 s timeout covers the rest.

## Web asset rules

- `index.html` loads Three.js r169 from jsDelivr CDN via importmap → **needs INTERNET permission + connectivity at runtime**, even though avatar/data are local.
- **The character is loaded once** from `models/avatar.glb`; every sign is an animation-only GLB in `signs/<SSSS_VVVV_RRRR>.glb` replayed with `THREE.AnimationMixer`. All files load via **relative paths** — keep the `models/` + `signs/` layout.
- **Do not put the normalization (scale/offset) on the loaded scene root.** The Cascadeur exports animate the armature root (`HG_Branden001`) in position, rotation *and* scale, so the mixer overwrites it every frame. Put the scale/offset on a parent pivot object instead, or the character renders unscaled and off-centre with no error in the console.
- `app.js` regenerates with `python3 tools/split_signs.py`, which reads the model's raw exports from `GLB Senas/` (gitignored, ~315 MB), writes `models/avatar.glb` + `signs/*.glb`, writes `manifest.json`, and rewrites `domain/SignsCatalog.kt` so the list and the disk cannot drift. **Adding a sign = drop the new `.glb` in `GLB Senas/` and rerun that script** — never hand-edit `SignsCatalog.kt`.
- **The rest pose is baked into `models/avatar.glb` and comes from a sign clip**, not from Blender: the first keyframe of `signs/0029_0000_0000.glb` (variant 0, t = 0.0417 s), rotations of the 46 arm-chain bones only. `split_signs.py` calls `tools/rest_pose.py` **last**, because the reference frame is a file it just wrote. It works because the node TRS *is* the bind pose (`bind_pose_error` ≈ 6e-6 over the 102 bones, re-checked every run): changing those rotations re-poses the character, the BIN chunk stays byte-identical and the pivot/bbox don't move. **Never hand-edit `avatar.glb`** — regenerate it. `rest_pose.check_bind()` stops the run if a new export is not bind-shaped, because a deformed mesh is invisible otherwise.
- **Between signs there is nothing: no frozen pose, no gap.** The 46 clips already chain (median direct jump 5.9 cm; 79% of pairs are continuous), so `app.js` starts the next clip `CROSSFADE_MS = 100` before the current one ends and lets three.js `crossFadeTo` the two live actions. Things that bite: `fadeOut` returns to the rest pose for free (`PropertyMixer.apply` fills in the node TRS when the weight hits 0); `_scheduleFading` runs on `mixer.time`, so the fade follows catch-up speed by itself; **`fadeIn(0)` is not "no fade"** — the weight interpolant starts and ends at the same instant and three.js disables the action, which is why `reduceMotion` takes a separate branch; and the *same* sign twice in a row cannot be faded (three.js caches one action per clip) so it is a hard cut, which is fine.
- `shouldInterceptRequest` forces `Cache-Control: no-store` so edits to `app.js` show on reload; don't remove it.
- `renderer.setPixelRatio` is **capped at 1.75** on purpose. Uncapped, the Redmi Note 9S (dpr 2.75) renders 19.6 MP/frame and the GPU sits at ~37 ms/frame — 99% jank. Measured with `tools/rendimiento/gfxinfo.py`. Do not also force `DoubleSide` on the meshes: it is pure overdraw on closed meshes and cost another 4% of jank for zero visual change.

## Gotchas

- All Compose state lives in `ui/state/MainScreenState.kt`; there is no ViewModel/DI/navigation. History is `SharedPreferences "signai"/"history"` (max 20, top-5 never reorder) behind `data/SignHistoryRepository.kt`; settings user choices persist in the same file behind `data/SettingsRepository.kt`.
- Playback state machine (`ui/state/PlaybackState.kt`): `idle → translating → signing → done`; `signing → done` is driven by the real `onPlaybackDone` bridge event, not a timer. The mic is **real**, not a placeholder: `data/AudioCapture.kt` does `AudioRecord` on `VOICE_RECOGNITION` (not `MIC`, so the STT hears the voice instead of the AGC pumping) and streams 30 ms PCM16/16 kHz chunks. **Spec `CTL-6`, `STA-9` and `OM-4` in `openspec/specs/ui/spec.md` still describe the old placeholder and are wrong** — see the real-time session log.
- The two edge drawers are one component (`ui/designsystem/components/EdgeDrawer.kt`, `side = Start/End`) with tap + drag + drag-preview; Conversation is `Start`, Settings is `End`, and opening one closes the other.
- **Drawer layering is fragile — read the `ORO` comment at the top of `EdgeDrawer.kt` before touching it** (spec `EDG-22`/`EDG-23`). Three rules: (1) a `draggable`/`clickable` with `enabled = false` STILL registers a Compose input node and stays the topmost hit route, so an inactive full-screen drawer silently swallows the active drawer's taps — never attach the gesture at all when it may not act; (2) the `AndroidView` WebView is a real child View and takes every touch inside its bounds, so it must be the bottom child with the drawers composed above it, and its `pointerInteropFilter` returns true while a drawer is open; (3) the backdrop is a full-screen node composed BEFORE the panel and the handle. The two drawers are siblings (not nested) and an inactive one composes no interactive overlay at all.
- One drawer at a time is enforced twice: reactively by `otherDrawerOpen`, and synchronously by `MainScreenState.claimDrawerGesture` (an `AtomicReference`, deliberately not Compose state) claimed on the first pointer event, so two fingers cannot open both panels in the same frame (`EDG-19`).
- Settings sidebar sends `setDebug/setFreeCamera/setGrid/setBackground/setTheme/setReduceMotion` toggles; blur overlay uses `RenderEffect` (Android 12+ only, guarded) and only while a drawer is open. The `#status` overlay text is part of debug mode and only shows when debug is on.
- Known device quirk: on MIUI the status-bar icon appearance does not follow the light theme (`isAppearanceLightStatusBars` + legacy flag ignored).
- **MIUI "smart dark" (force-dark) MUST stay off** (`android:forceDarkAllowed=false` in `res/values/themes.xml`). With it on, MIUI inverts content drawn inside hardware layers — text inside `verticalScroll` containers and drawers renders **white** while solid boxes stay correct (bug found 2026-09-23). Do not remove that item.
- Bone naming that matters for `app.js`: the Cascadeur exports use **dots** in the fingers and palm (`thumb.01.L`, `f_index.01.L`, `palm.04.L`), **no prefix** on the arm chain (`shoulder.L`, `upper_arm.L`, `forearm.L`, `hand.L`), and `DEF-` camelCase on the long bones and the face (`DEF-browTL`, `DEF-lipTL`, `DEF-lidTL`, `DEF-jaw_master`). The armature root `HG_Branden.001` is animated too but is **not** a skin joint — counting only bones makes 138 tracks look unresolved when they are not. Right brow `DEF-browTR` has ~4 weighted verts vs ~32 left → right brow barely moves (rig issue, not JS).

## Context engineering (OpenSpec)

`openspec/` is the project's living context — keep it updated with every non-trivial change:

- `specs/` (avatar, dataset, pipeline, ui): source of truth. `specs/ui/spec.md` is the **complete feature catalog** of the app (all features come from the `PrototipoFrontend/` Vercel prototype); `avatar`/`dataset`/`pipeline` cover rig/animation behavior. Update the relevant spec when fixing such behavior.
- `sessions/`: work log, one file per session (`YYYY-MM-DD-<slug>.md`, header with title/day/date/time). Append what was done, decided, and left pending at the end of each work item. Convention in `openspec/sessions/README.md`.

## Tooling del agente (mobile-next)

Stack de mobile-next instalado en este equipo para que un agente (Claude,
opencode) opere el emulador/dispositivo sin pedir coordenadas a ciegas. Mismo
setup que el repo `AppMovil`/`trabajoVM`, **adaptado a Android nativo**: allá es
Flutter y tiene `dart-mcp` + `marionette`; acá no aplica nada de eso.

**Un agente que vaya a operar el device debería cargar el skill
`controlar-app-movil`** (tool `skill`, en `.opencode/skills/controlar-app-movil/`).
Ese skill tiene la guía de cuál herramienta usar, los workflows y los errores ya
pisados. Esta sección es solo el índice.

- **`tools/dev.sh`** — wrapper sobre `mobilecli`. Subcomandos: `devices`,
  `shot`, `tap`, `swipe`, `text`, `key`, `back`, `log`, `clearlog`, `tree`,
  `install`, `install-debug` (`./gradlew installDebug` + relanzar la app),
  `launch`, `terminate`, `apps`, `fg`, `logcat-tag`, `doctor`.
  **Sin escala**: mobilecli trabaja en coords reales del device.
- **Vía primaria del agente**: MCP **`mobile-mcp`**
  (`mobile_list_available_devices`, `mobile_take_screenshot`,
  `mobile_list_elements_on_screen` — preferir refs sobre coordenadas—,
  `mobile_click_on_screen_at_coordinates`, `mobile_type_keys`,
  `mobile_install_app`, `mobile_launch_app`, `mobile_get_device_logs`,
  `mobile_batch_commands`). Configurado en `opencode.json` con
  `MOBILEMCP_DISABLE_TELEMETRY=1`. Antes de tocar, leer el árbol con
  `mobile_list_elements_on_screen`. Si el MCP no está disponible, fallback a
  `tools/dev.sh`.
- **`tools/maestro/`** — flows YAML (`smoke-avatar`, `drawers`, `historial`,
  `ejemplo-flow`). Los selectors salen de los `contentDescription` del
  `.semantics {}` de Compose. **No anda en el Redmi (MIUI)**: su driver de
  Android revienta al arrancar con `Cannot call disconnect() while connecting
  UiAutomation`. Usarlo en un emulador con imagen AOSP.
- **`tools/rendimiento/`** — `gfxinfo.py` (reparto por fotograma, latencia de
  toque), `sf-medir.sh` (fotogramas que llegaron a pantalla), `frames.py`
  (duración de la animación, repintado en reposo), `perfetto.sh` (trace para
  atribuir). Midiendo en build release, nunca en debug.
- **`tools/pinch.sh`** — pinch de dos dedos por `sendevent`. Solo emulador o
  root; en un físico sin root da `Permission denied`.

### Dos limitaciones que hay que saber antes de intentar

1. **El avatar 3D es invisible para toda esta instrumentación.** En el árbol sale
   como `android.webkit.WebView` **sin ningún hijo**: el canvas de Three.js no
   publica nada a accesibilidad. No se puede tocar ni leer con `mobile-mcp`,
   Maestro ni `uiautomator`. Para el estado de la escena: el tag `SignAIWeb` de
   logcat, o el modo debug de la app (Ajustes → AVATAR → depurar).
2. **El WebView se come los toques.** Es un `AndroidView`, o sea un View real:
   toma cada toque dentro de sus bounds. Si un drawer no abre, el problema es el
   layering de `EdgeDrawer.kt` (§ Gotchas), no el gesto.

Ver `tools/README.md` para el índice y `tools/HERRAMIENTAS.md` para la
referencia completa (tools de `mobile-mcp`, tabla de `contentDescription`,
selectores, y las trampas ya pisadas).
