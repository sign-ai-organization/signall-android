# El sistema SignAll — referencia de los repositorios hermanos

Este archivo es **referencia**, no reglas. Las reglas están en `AGENTS.md`. Se carga
cuando la tarea toca el pipeline, el WebSocket, el STT, el catálogo o los documentos de
la tesis.

Esta app es **una de las piezas** del sistema SignAll (tesis, Ingeniería de Sistemas,
PUJ). Las otras viven en carpetas hermanas bajo `../`, todas en la organización
`sign-ai-organization` de GitHub.

```
../signall-stt ──PCM16/WS──▶ ../signall-backend ──WS/JSON──▶ ../signall-android (esta app)
                                      │
                                      └──▶ Neon (Postgres 16 + pgvector): catalog, semantic_cache

../signall-docs  ← documentos de la tesis en LaTeX (se compilan con make)
../signall-avatar ← animaciones (Cascadeur): GLB finales de Juan y Pauline (rama dev)
```

| Repo | Qué es | Rama | Puerto | Se levanta con |
|---|---|---|---|---|
| `../signall-backend` | Monolito modular FastAPI: WS con el cliente, NLP de glosas LSC, validación contra catálogo, caché semántico, Neon | `main` | 8080 | `docker compose up --build` (levanta **también** el STT) |
| `../signall-stt` | faster-whisper + WebRTC VAD: audio → texto en tiempo real | `main` | 8000 | incluido en el compose del backend, o solo desde su repo |
| `../signall-docs` | **Los documentos de la tesis**: los formales en LaTeX (SRS, SDD, memoria, plan de pruebas, SPMP), los borradores en Markdown y los diagramas | `main` | — | `make` (compila los PDF) |
| `../signall-avatar` | Producción de las animaciones con Cascadeur; versiona en `models/` los GLB finales de Juan y Pauline (200 clips cada uno: 50 señas × 4 marcas faciales). La demo Flask de landmarks que también vive ahí está congelada desde 2026-09-19 | `dev` | — | — |

**El contrato entre ellos son frames de WebSocket, no imports.** Un cambio acá rompe allá
sin que el compilador avise. Ese es el motivo de que este archivo exista.

---

## `../signall-backend`

**Leé `CLAUDE.md` (147 líneas) antes de tocarlo.** Tiene las reglas que no están
acá: código y comentarios **en inglés sin excepción**, los nombres de clases tienen que
coincidir con los del SDD, y **nunca hacer `git commit` ni `git push`** — se sugiere el
mensaje y lo manda el owner, por PR contra `main`.

- **Contrato con la app**: `docs/android-integration.md` (423 líneas) es la fuente de
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

## `../signall-stt`

Sin AGENTS.md ni CLAUDE.md: su documentación es el `README.md` (235 líneas), muy
completo. Mismo criterio de git que el backend: **preguntarle al owner, PR contra
`main`**.

- **Protocolo**: el cliente manda frames **binarios** PCM16LE mono 16 kHz sin cabecera
  WAV (el servidor los reagrupa en 30 ms para el VAD) y el STT responde con **JSON de
  texto**. Un frame de texto plano `"end"` fuerza el cierre del segmento.
- `words` y `audio_ms` son **aditivos** (2026-10-14): el backend los usa para decidir
  qué seña representar y cuándo, pero el STT no depende de que alguien los lea.
- Perfil de tiempo real: **dos modelos** (`PARTIAL_MODEL_SIZE=small` para los
  parciales, `MODEL_SIZE=medium` para los finales) + `InferenceScheduler` +
  `SILENCE_TIMEOUT_MS=600`. `REALTIME=false` devuelve los defaults previos.
- **Los 28 fixtures de `fixtures/audio/*.wav` no están en git** (los binarios no; los
  `.json` sí). Se generan con `python -m scripts.make_audio_fixtures.py --all` en un
  venv aparte con Piper. Los números de latencia solo son comparables entre corridas
  porque el script produce siempre el mismo WAV para el mismo texto.
- De `fixtures/` saca el audio `tools/verificar-tiempo-real.sh` para las pruebas end-to-end.

## `../signall-docs`

**Los documentos de la tesis.** Desde el **2026-11-04** este repositorio es la **única fuente
de verdad**: Overleaf se retiró y su MCP se quitó de la configuración global. No hay copia que
sincronizar ni nada que se edite en dos lugares.

```
latex/<documento>/main.tex   ← el original, se edita acá
  srs/ · sdd/ · memoria/ · plan-pruebas/ · administracion/
borradores/                  ← Markdown de trabajo (requerimientos, casos de uso, plan de pruebas)
cambios/requerimientos.md    ← bandeja de cambios: Propuesto / Acordado / Hecho
diagramas/draw.io + png/     ← los .drawio editables y su exportación
versiones-pasadas/           ← historia, no se edita
```

- **Se compila con `make`**: `make` (los cinco), `make srs` (uno), `make watch DOC=srs`
  (recompila al guardar). Los PDF y auxiliares están en `.gitignore` y se regeneran. Un
  cambio en un documento **no está terminado hasta que compila**.
- **Ese repo trabaja con `main`**: los commits llegan a `main` por *pull request*, y desde
  el 2026-11-11 no hay rama `develop` — todo el trabajo va a `main`. Un commit directo en
  `main` local queda por detrás en cuanto el equipo mergea, así que **antes de empezar hay
  que hacer `git fetch`**: el 2026-11-04 la copia local estaba 20 commits atrás.
- **El control de cambios es git**: `git diff` dice qué se tocó y `git log` cuándo.
- **`cambios/requerimientos.md` es la bandeja de cambios pendientes**, con estados
  **Propuesto / Acordado / Hecho**: ahí caen las cosas que la app ya hace y el documento
  todavía no dice (modo en vivo, entrada de texto, historial, apagado del mic…). Si se
  implementa algo sin requisito, se anota ahí.
- **`ESTADO.md` lista la deuda conocida** de cada documento: qué está atrasado, qué falta y
  con qué evidencia. Conviene leerlo antes de «corregir» algo que ya está anotado.
- **Los `.drawio` y los borradores de requerimientos los edita el owner**: los cambios
  grandes se proponen, no se aplican en silencio.

### Cómo se entra en calor

1. `../signall-docs/README.md` — mapa de carpetas y cómo compilar.
2. `../signall-docs/ESTADO.md` — estado y pendientes de cada documento.
3. `../signall-docs/latex/<documento>/main.tex` — el documento.
4. El skill **`documentos-tesis`** — el flujo de trabajo completo.

### Dos cosas que confunden

- **El nombre del sistema**: **los documentos dicen `SignAll` y el código dice `SignAll`**, a
  propósito. El owner decidió el 2026-11-04 que en la tesis el nombre es `SignAll`; los
  repositorios de GitHub y los documentos internos de los repos no se renombraron.
- **La copia quedó congelada el 2026-11-04** y las correcciones que se habían hecho en
  Overleaf después se volvieron a aplicar el mismo día. Lo que se corrigió y lo que sigue
  abierto está en `ESTADO.md`.

## `../signall-avatar`

**Es la fuente de los GLB de esta app.** Es el repo de producción de las animaciones:
las señas se hacen en Cascadeur sobre los avatares HumGen3D (Juan y Pauline) y el
resultado final se versiona en su carpeta `models/`, en la rama **`dev`**:

- `models/Juan.glb` — 34,27 MB, 200 clips, 102 articulaciones.
- `models/Pauline.glb` — 33,88 MB, ídem.

Desde el commit `fc6f372` (2026-11-04) esos archivos traen **cuatro marcas faciales**
por seña (`_afirmativo`, `_pregunta`, `_negacion`, `_orden`) y ningún clip base. La
versión anterior (`cf014ef`, 2026-10-26) traía la **cadera centrada** en las 50 señas:
antes el `spine` venía corrido y girado, y el avatar se iba de costado al representar.
`tools/pack_avatars.py` los lee de ahí, hornea la pose de descanso, poda los
morph targets que nadie anima y escribe los dos `.glb` de la app. El proceso completo está
en su `openspec/specs/animacion-cascadeur/spec.md`; los cambios de expresión facial, en
`openspec/changes/avatar-expresiones-faciales/`.

La **demo Flask vieja** (`HandCorrector`, `PoseFilter`, WiLoR, con su `openspec/` de
`specs/avatar|dataset|pipeline`) sigue en el mismo repo pero está **congelada desde el
2026-09-19**: es el pipeline de landmarks, no el vigente, y no se le copian scripts a esta
app. El layout viejo de `../material-3d/GLB Senas/` + `tools/split_signs.py` sigue en el
repo para rearmar los assets por seña, pero no es el vigente —y sus 32 `.glb` ya no se
empaquetan: `assets/web/signs/` se borró el 2026-10-22—. `../material-3d/GLB SignALL/` y
`tools/pack_signall.py` son el pipeline intermedio de un solo avatar, retirado el
2026-10-26. No mezclar los pipelines ni copiar scripts entre ellos.

**Regla de git**: como todo hermano, no se commitea ahí sin que el owner lo pida (PR contra
`main`). Esta app solo lee `models/`.

## Carpetas hermanas que **no** son repositorios

Sin git y sin reglas: material compartido o restos.

- `../openspec/` — specs de `avatar`/`dataset`/`pipeline` y `changes/` con trabajo de
  avatar. Es el OpenSpec **compartido, distinto** del `openspec/` de esta app (que además
  tiene `specs/ui` y `sessions/`); las copias de `avatar`/`dataset` divergen entre sí, así
  que saber cuál manda requiere preguntar.
- `../data/` — el dataset de origen (las 50 señas). Ya **no** tiene el `LSC50.pdf` ni
  los `LANDMARKS/`: el paper se movió a `docs/lsc50-dataset.pdf` y los CSVs de muestra
  se borraron el 2026-10-22.
- `../material-3d/` — **el material crudo de los assets**, movido acá el 2026-10-23 para
  sacarlo de la base del repo (1 GB y git lo ignoraba):
  - `Avatares/` — `Juan_final.glb` (37,87 MB) y `Pauline_final.glb` (35,27 MB), la
    **versión anterior** (2026-10-24) de los dos exports de Cascadeur. Ya no es la fuente:
    la vigente está versionada en `../signall-avatar/models/`. **Entrada inmutable**: no se
    edita; se conserva para comparar.
  - `GLB SignALL/` — el `.glb` único (27 MB) del pipeline intermedio, retirado el
    2026-10-26.
  - `GLB Senas/` — los 32 exports por seña del pipeline viejo (315 MB).
  - `Para Exportar/` — los `.blend` + `.fbx` de Cascadeur (660 MB).
  Ningún script escribe ahí: `pack_signall.py` y `split_signs.py` solo leen.
  `pack_avatars.py` ya no lee de acá: lee de `../signall-avatar/models/`.
- `../Resources/` — scripts viejos y docs (ADR, documento maestro, backlog). Incluye
  `split_avatar_for_filament.py` y `retarget_blender.py`, que son del **approach
  abandonado**: no sirven de referencia para nada de acá.
- `../signai_env/` — un venv de Python, no un repo.

## El contrato entre repos (lo que se rompe en silencio)

Un único WebSocket, de la app **solo al backend**: `:8080/ws/translate`. La app nunca
habla con el STT ni con Neon.

**En la demo el canal va cifrado, y el backend no lo sabe.** La app habla `wss://` contra
Caddy, que corre en el host del backend escuchando en el 443, termina el TLS con un
certificado de laboratorio y reenvía al backend en claro por `127.0.0.1:8080`. El backend
sigue escuchando donde escuchaba y no tiene ninguna configuración de TLS. El build que lo
demuestra es `demo` (`wss://127.0.0.1:8443/ws/translate` con `adb reverse tcp:8443 tcp:443`
y la CA de laboratorio instalada en el teléfono); `debug` sigue en `ws://`, porque es el
build con el que se mide latencia y el handshake no debe entrar en la comparación. El plan
completo está en `../signall-backend/docs/plan-proxy-tls.md`.

- **Solo se traduce lo que el cliente manda como `text`.** La voz entra por el mismo
  socket pero vuelve como `transcript`, y esa transcripción **no se traduce**. Si el
  backend hiciera eco de la frase escrita, la app lo tomaría por una frase de voz y la
  reenviaría: ese bucle existió y quemó el proceso (una frase repetida ~2.900 veces, 44 %
  de CPU). `handle_text` no manda ningún `transcript` y `verify_client_frames.py` lo fija.
- **`segment` es una oración, con `index` y `total` reales desde el primero.** Ya no
  existen `stream`, `provisional` ni `source_text`. El backend manda las oraciones **en
  orden** aunque las traduzca en paralelo.
- **El `final` siempre llega**, incluso vacío: el STT responde a todo `end`
  (`flush()`). Un `final` sin texto es "no se escuchó nada", no un error.
- **Los mismos nombres de métrica en los dos lados** (`rt_*` en el backend,
  `SignAllMetrics` en el device): un número que no se instrumentó en las dos partes no se
  puede comparar con nada. `rt_first_sign_ms` conserva el nombre aunque cambiara lo que
  mide, para que se pueda comparar contra la línea base — aunque **no** sea comparable:
  la línea base es del camino por palabras.

Y tres herramientas de este repo que **dependen del código de los hermanos**:

- `tools/verificar-tiempo-real.sh` levanta el compose de `../signall-backend` (que incluye
  `../signall-stt`) y usa sus fixtures.
- `tools/mock_backend.py` importa `../signall-backend/app/nlp/llm_client.py` y replica su
  `catalog_repository`: renombrar o mover esos módulos rompe el mock.
- `tools/local_stt.py` arranca el STT desde `../signall-stt`.