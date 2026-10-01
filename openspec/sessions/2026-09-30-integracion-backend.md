# Sesión: análisis del backend y plan de integración

- **Día:** miércoles
- **Fecha:** 2026-09-30
- **Hora:** ~ (local)

## Qué se hizo

Análisis de `../sign-ai-backend` (repo FastAPI, 29 archivos Python) para definir
cómo se integra con el front WebView. **No se escribió código de app**: solo
análisis + plan.

### Estado del backend (verificado en código, no en docs)

- Fase 11 (Controller + WebSocket) **completa**. HEAD `ccb778e`.
- Contrato real: `ws://<host>:8080/ws/translate` (`router.py:36`). El cliente
  manda frames binarios PCM16LE 16 kHz; el backend manda
  `{"type":"transcript",…}` (`controller.py:43`), `{"type":"error",…}` (línea 50)
  y **un array pelado** de `ValidatedSegment` como resultado (línea 47, snake_case
  porque viene de `model_dump()`).
- `GET /health` existe. El backend nunca habla directo con el STT (DEC 9.4): reenvía
  internamente a `ws://stt:8000/ws/transcribe`.

### Bloqueos encontrados

1. **`catalog` está vacía.** No hay ningún `INSERT` en el repo, solo la migración
   `001_create_catalog.sql`. Sin las 50 glosas en Neon, todo vuelve
   `available:false`. Hay que verificar si se sembró a mano.
2. **No existe ningún asset de animación por seña** en toda la máquina (solo
   `avatar.glb`), y `app/static_glb/` está vacío → Fase 12 sin empezar.
3. **`sign-ai-stt` no está clonado**, y `docker-compose.yml` hace
   `build: ../sign-ai-stt` → falla hoy.
4. **El backend descarta los frames de texto en silencio** (`router.py:21` solo
   mira `message["bytes"]`). Sin un cambio ahí, una frase escrita no se puede
   traducir (bloquea `CTL-1`/`CTL-2` y `RF-UI-02`).
5. **El WebView no puede llamar al backend**: corre en
   `https://appassets.androidplatform.net`, así que un `fetch("http://…")` es
   mixed content, y Android 9+ bloquea cleartext por defecto. Además el proyecto
   no tiene `OkHttp` ni `RECORD_AUDIO`.

## Qué se decidió

Con el usuario, 2026-09-30:

1. **Formato de animación: híbrido.** El vertical slice reproduce los frames de
   landmarks horneados (el pipeline que ya funciona) servidos por HTTP; los clips
   GLB por seña se difieren a la etapa del corpus de 50. No se cambia el schema:
   `catalog.glb_animation_path` guardará la ruta del asset sea cual sea. Queda
   pendiente registrar el desvío de ADR-012 en `docs/sdd-changes.md` del backend
   cuando se implemente la Fase 12.
2. **Texto primero**, sin `AudioRecord`: valida el contrato completo (WS + glosas +
   playback) sin permiso de micrófono ni depender del STT. El audio real es la
   Fase D.

**Solución de arquitectura clave:** la red la posee **Kotlin, nunca el JS**. Los
assets descargados se le sirven al WebView como *same-origin* con un `PathHandler`
extra en el `WebViewAssetLoader` que ya existe (`/signs/` →
`cacheDir/signs/`), evitando CORS y mixed content sin tocar el CDN de Three.js.

## Los tres repos

```
🎙️ Micrófono → 📱 App móvil → 🧠 Backend → 📱 App móvil → 🧑‍🦱 Avatar 3D
                        └── por dentro llama al 👂 STT (audio → texto)
```

El usuario clonó `../sign-ai-stt` (el oído, faster-whisper + WebRTC VAD). Con eso
el `build: ../sign-ai-stt` del compose del backend **deja de apuntar a la nada**:
`docker compose up --build` desde `sign-ai-backend` levanta los dos servicios.

El camino de texto **nunca pasa por el oído** (va directo a
`Controller.handle_text`), así que para el paso 2 alcanza el STT falso
(`scripts/fixtures/fake_stt_server`): solo tiene que aceptar la conexión.

**No hace falta PR en `sign-ai-stt`** para este camino. Lo que sí quedó anotado
para más adelante: `SILENCE_TIMEOUT_MS` tiene default 600 en `app/config.py`
pero el proyecto necesita 1500 (el compose sí pone 1500; solo afecta si se corre
fuera de Docker), y la decisión de Whisper `medium` se tomó midiendo en una
RTX 3050 mientras esta máquina tiene una **RTX 4050 de 6 GB**.

## Qué quedó hecho

- `openspec/changes/integracion-backend/tasks.md` — **reescrito en lenguaje simple**
  como documento de trabajo: los tres repos en una línea, mapa de PRs, paso 0 de
  precondiciones, un control previo con la cadena probada desde la laptop, y los
  pasos 1-5 con "qué se toca" y "cómo se prueba".
- `openspec/config.yaml` y `openspec/specs/pipeline/spec.md`: la sección
  "Integraciones futuras" pasa de "cero integración" a apuntar al plan + las dos
  decisiones.
- **Pasos 0.2 y 0.4 hechos en el repo hermano** (autorizado explícitamente por el
  usuario, aunque el `CLAUDE.md` de ese repo dice no commitear nunca). Rama
  `feat/android-client-support` desde `origin/develop`, **un solo PR**:
  [PR #7](https://github.com/sign-ai-organization/sign-ai-backend/pull/7) contra
  `develop`, **marcado listo para revisión** (ya no es borrador). **No mergeado**:
  lo mergea el usuario.
  - `1a5d975` — frame `{"type":"text","text":…}` → `Controller.handle_text` →
    `handle_transcript(text, is_final=True)`. El camino de audio no cambia y los
    frames no-usables se ignoran en silencio. `scripts/verify_text_frames.py`
    nuevo (8 casos de parsing + routing con socket falso). **Verificado.**
  - `bab1cd8` — `LLM_BASE_URL` threaded `settings.py` → `main.py` → `LLMClient`.
    Vacío = comportamiento actual intacto. `scripts/verify_llm_client.py` ahora
    respeta la variable. **Verificado** con el `LLMClient` real contra Ollama.
  - Docs: entrada en `docs/decisions.md` (Ollama como proveedor de develop) y en
    `docs/sdd-changes.md` (el canal también lleva texto escrito).
  - El PR #6 (solo texto, rama `feat/text-frames`) se cerró al consolidar: la
    rama se renombró y el trabajo quedó en `1a5d975`.
- Este repo: 4 commits en `main` (sin PR, directo, por decisión del owner):
  `77aa092` sesión con el backend, `76ff5d9` frase escrita + cola, `61ecd51` cola
  y MNM en `app.js`, `4c0d399` features en el spec. Más `tools/mock_backend.py`.

## Implementado y verificado (2026-09-30, tarde)

Con el usuario autorizando "conectar todo lo posible, mockeando lo que falte":

- **Datos**: `SignAiWebSocketClient` (OkHttp, flow frío por sesión, parser
  tolerante: resultado = array pelado, transcript/error = objetos),
  `SignAiBackendConfig` (BuildConfig + `-Psignai.backendWsUrl`),
  `SignAssetRepository` (descarga nativa → URL same-origin),
  `network_security_config.xml` para cleartext con la nota de por qué.
- **Estado**: `play()` manda `{"type":"text",…}` y espera el resultado; se
  borraron el delay falso de 850 ms y `domain/Gloss.kt`. Estados
  `Connecting`/`Offline` con reconexión y backoff, hint con las glosas durante
  `signing`, mensaje de error 3 s.
- **app.js**: `setQueue(json)` (string, `JSON.parse` en JS), caché de assets por
  URL, transición corta entre señas, `loading` para que un fetch lento no
  repita la seña anterior, y MNM de ceja por `PhraseType` **compuesto sobre la
  pose actual** (porque `FACE_ALPHA = 1.0` reemplaza la rotación).
- **`/signs/` PathHandler** en el `WebViewAssetLoader` existente.
- **Ajuste "Seña de prueba si falta en el catálogo"** (activo por defecto): con
  el catálogo vacío toda glosa llega desconocida, y esto hace que use la seña
  horneada del APK para que la cola se vea.
- **`tools/mock_backend.py`**: mock del backend con el mismo protocolo en un
  solo puerto (WS + `/health` + `/static/*.json` sirviendo el `sign.json` del
  repo). Permite probar la app sin Neon, Whisper ni Ollama, y ejercita el camino
  de descarga que más riesgo tenía.

### Bugs encontrados y corregidos probando en el emulador

Probando con el MCP de mobile-next (elAGENTS.md lo marca como vía primaria;
yo venía usando `adb shell input` a ciegas y por eso diagnosing cosas que no
eran) aparecieron dos bugs reales:

1. **Crash al descargar el asset** — `4bf0699`. `httpOrigin` concatenaba
   esquema y host sin separador: `ws://10.0.2.2:8080/ws/translate` se
   volvía `http10.0.2.2:8080/ws`, OkHttp lo rechazaba y la app moría con
   `Expected URL scheme 'http' or 'https' but was 'http10.0.2.2'` justo
   cuando llegaba la primera glosa con animación. Además, la request se
   construía *fuera* del `runCatching`, así que una URL mal puesta mataba
   la app; ahora degrada a "asset no disponible".
2. **El WebView robaba el foco** — `be653ec`. Un WebView es focusable en
   touch mode por defecto: lo tomaba al arrancar y lo mantenía, así que
   **todo lo que se escribía iba a la escena 3D en vez de al campo**, el
   botón Traducir quedaba deshabilitado y la app parecía muerta.
   `mobile_list_elements_on_screen` lo dejó claro: WebView y EditText
   ambos `[focused]`. La escena no tiene input de texto ni necesita el
   IME, así que `isFocusableInTouchMode = false` lo resuelve.

**Corrección a lo que reporté antes:** sospeché un bug de layering
preexistente entre el WebView y la barra de control. **Era falso.** Mis
coordenadas estaban mal (leí y≈1735 de una captura escalada; la barra
está en y≈2017) y el problema real era el foco. El layering estaba bien
desde el principio.

### Verificado end-to-end en emulador (VMApp_API36)

Todo el camino, con `tools/mock_backend.py`:

1. Escribo "quiero ir a la universidad" y le doy Traducir.
2. El mock registra la frase recibida.
3. La app descarga los 3 assets al `cache/` (438 KB cada uno).
4. El hint del stage muestra **`YO · UNIVERSIDAD · IR`** (las glosas del
   backend, `STA-13`).
5. La cola se reproduce completa y el estado llega a
   **"Reproducción completada"** con el botón Repetir visible → el evento
   `onPlaybackDone` del bridge funciona (`BRG-7`/`STA-8`).
6. **Repetir** reenvía la frase y reproduce de nuevo, y **no vuelve a
   descargar** (mtime intacto): la caché de assets funciona.
7. El drawer de Conversación muestra los 3 turnos con sus glosas.

Sin crashes nuevos. `cache/signs/{YO,UNIVERSIDAD,IR}.json` en disco.

## Pendiente

- **Paso 0.1 sigue bloqueando la prueba real**: sembrar `catalog` en Neon.
- Mergear el PR #7 del backend a mano.
- Probar la frase escrita en el device real (ver arriba).
- `turn_id` cliente→servidor: no hace falta hasta el paso 5.
- `RF-AVT-04` (interpolación real) queda en crossfade corto; está anotado.
- Seguridad (auth/TLS, `RF-INT-COM-03` / DEC-14) fuera de alcance.

### Ollama: lo que se midió

En esta máquina: Ollama instalado y corriendo (11434), con `qwen3:4b-instruct`
(2.5 GB) y `qwen3:4b` descargados. Con el `SYSTEM_PROMPT` real del repo:

| Frase | Respuesta | Tiempo |
|---|---|---|
| ¿Cuál es su nombre? | `NOMBRE,TU,QUE` | 4.1 s (primera) |
| Quiero ir a la universidad. | `YO,IR,UNIVERSIDAD` | 0.2 s |
| Firme aquí, por favor. | `FAVOR,AQUI,FIRMAR` | 0.2 s |

Idénticas a los ejemplos del prompt. `gpt-4o-mini` tarda 1-3 s.

**Ojo — Parakeet no sirve para esto.** `parakeet-tdt-0.6b-v3` y `Qwen3-ASR-0.6B`
están en `~/.cache/opencode-voice/models/` (los usa el plugin de voz de
opencode): son modelos de **transcripción** (audio → texto), que corresponden al
repo `sign-ai-stt` (hoy Whisper), no al LLM de glosado. Para glosado el modelo
correcto es el de texto, `qwen3:4b-instruct`.

**Dos trampas con Ollama, ya documentadas:** la primera llamada tras arrancar
tarda ~1 min (carga el modelo) y excede `LLM_TIMEOUT_MS=10s` → hay que calentar
con `ollama run qwen3:4b-instruct`. Y usar `qwen3:4b-instruct`, no `qwen3:4b`:
esta última tiene *thinking* y rompe el formato de salida (solo glosas por
comas).

