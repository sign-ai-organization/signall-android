# El sistema SignAI — referencia de los repositorios hermanos

Este archivo es **referencia**, no reglas. Las reglas están en `AGENTS.md`. Se carga
cuando la tarea toca el pipeline, el WebSocket, el STT, el catálogo o los documentos de
la tesis.

Esta app es **una de las piezas** del sistema SignAI (tesis, Ingeniería de Sistemas,
PUJ). Las otras viven en carpetas hermanas bajo `../`, todas en la organización
`sign-ai-organization` de GitHub.

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

**El contrato entre ellos son frames de WebSocket, no imports.** Un cambio acá rompe allá
sin que el compilador avise. Ese es el motivo de que este archivo exista.

---

## `../sign-ai-backend`

**Leé `CLAUDE.md` (147 líneas) antes de tocarlo.** Tiene las reglas que no están
acá: código y comentarios **en inglés sin excepción**, los nombres de clases tienen que
coincidir con los del SDD, y **nunca hacer `git commit` ni `git push`** — se sugiere el
mensaje y lo manda el owner, por PR contra `develop`.

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

## `../sign-ai-stt`

Sin AGENTS.md ni CLAUDE.md: su documentación es el `README.md` (235 líneas), muy
completo. Mismo criterio de git que el backend: **preguntarle al owner, PR contra
`develop`**.

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

## `../sign-ai-docs`

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
- Los documentos en **Overleaf** son **una de las fuentes de verdad** de la tesis, y el
  owner los considera **más importante** que este repo. Ver § Overleaf en `AGENTS.md`.

### El Overleaf de la tesis

Cuenta `signai.tesis@gmail.com` (dedicada a la tesis, 10 proyectos, 5 sin archivar).
Se lee con el MCP `@netique/overleaf-mcp` v0.3.6, registrado **global** en
`~/.config/opencode/opencode.json` con `OL_BROWSER=/usr/bin/chromium`. La cookie de
sesión vive en `~/.config/overleaf-mcp/cookie.json` (modo 600), dura ~5 días y se
renueva sola: la próxima llamada abre una ventana de Chromium con perfil aislado para
que el owner se loguee, y se cierra sola.

| Proyecto | ID | Última edición cuando se conectó |
|---|---|---|
| SRS | `6a122a19bb9a5dd9d390ba3d` | 2026-10-17 18:45 |
| Memoria | `6a7c98a30e11208333279f4a` | 2026-09-17 |
| SDD - Documento de Diseño | `6a85c9a632974414503d9b82` | 2026-08-30 |
| PMP | `6a11da204de1f4e676e9e59a` | 2026-05-26 |
| PPP | `6a11f38ec7df3c45f6caee2d` | 2026-05-24 |

Overleaf no tiene API pública: este MCP habla los endpoints internos que usa el editor
web, con la cookie de sesión. En plan gratis es el único camino — el puente de Git es
premium— así que **no hay MCP alternativo si este se rompe**: se actualiza el paquete
o se baja el ZIP a mano.

## `../signai-avatar`

**No es la fuente de los GLB de esta app.** Es la demo anterior en Flask que servía un
avatar desde landmarks de LSC50 (`HandCorrector`, `PoseFilter`, script WiLoR para
manos) y tiene su propio `openspec/` con `specs/avatar|dataset|pipeline`. Está
**inactiva desde 2026-09-19**. Los assets de acá salen de otro pipeline: exportaciones
de Cascadeur en `GLB Senas/` → `tools/split_signs.py`. No mezclar los dos pipelines ni
copiar scripts entre ellos.

## Carpetas hermanas que **no** son repositorios

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

## El contrato entre repos (lo que se rompe en silencio)

Un único WebSocket, de la app **solo al backend**: `:8080/ws/translate`. La app nunca
habla con el STT ni con Neon.

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