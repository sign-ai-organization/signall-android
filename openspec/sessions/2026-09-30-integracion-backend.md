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
- Este repo: rama `feat/integracion-backend` con el plan y la bitácora.

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

## Pendiente

- **Paso 0.1 sigue bloqueando todo**: sembrar `catalog` en Neon. Sin las 50
  glosas, el 100% vuelve `available:false` y no hay nada que reproducir.
- Mergear el PR #7 a mano.
- **Ojo con la sesión de solo texto:** `router.py:44` conecta al STT antes de
  atender la sesión, así que sin STT manda `error` y cierra aunque el cliente solo
  mande texto. Con el STT falso se resuelve. Hacer la conexión perezosa es un
  cambio de diseño aparte, propuesto en el PR pero **no aplicado**.
- `turn_id` cliente→servidor: no hace falta hasta el paso 5 (con reconexión
  automática un resultado puede llegar de una sesión anterior). El guard actual de
  `MainScreenState.play()` basta mientras tanto.
- El paso 4 (assets) es el único con riesgo real de arquitectura: la red la debe
  hacer Kotlin y nunca el JS, porque el WebView corre en origen `https://` y un
  `fetch` a `http://` es mixed content. Hay que hacerlo bien desde el principio.
- `RF-AVT-04` (interpolación real entre señas) queda en crossfade corto contra el
  rest pose, y hay que decirlo: no es la interpolación del requerimiento.
- Seguridad (auth/TLS, `RF-INT-COM-03` / DEC-14) explícitamente fuera de alcance.
