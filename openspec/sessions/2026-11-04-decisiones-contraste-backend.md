# Implementación de las decisiones del contraste del backend

**Fecha:** 2026-11-04, madrugada · **Rama:** `main`

## Qué llegó

El owner aprobó las 14 decisiones de la sección 6 de
[`docs/backend/contraste-backend-2026-11-03.html`](../../docs/backend/contraste-backend-2026-11-03.html) y
pidió implementarlas, de la más fácil a la más difícil, con el Redmi Note 9S conectado. Los
puntos 2, 7 y 14 son de documentos y quedaron para la sesión aparte.

## Qué se hizo

| Punto | Archivo | Qué cambió |
|---|---|---|
| 1 | `../signall-backend/app/controller/controller.py` y `router.py` | `handle_audio_chunk` rechaza un fragmento vacío, uno impar o uno mayor a `AUDIO_MAX_CHUNK_BYTES` (32 KiB) y devuelve `False`; el bucle de lectura manda el frame `error`, cierra con código **1008** y corta la sesión. Contador `audio_chunks_rejected_total{reason}` |
| 1 | `../signall-backend/scripts/verify_audio_validation.py` | **Nuevo.** Los tres rechazos y el caso válido, sin STT ni base |
| 3 | `.env`, `.env.local`, `.env.local.example`, `.gitignore`, `docker-compose.yml` | `.env` queda con `gpt-4o-mini`, `LLM_BASE_URL` vacío, 5000 ms y 1 reintento. Qwen pasa a `.env.local`, que no se versiona y compose monta si existe (`required: false`) |
| 3 | `scripts/verify_official_config.py` | **Nuevo.** Verifica las dos configuraciones y que `.env.local` no entre al repo |
| 4 | `docs/sdd.md` | Sincronizado en §7.1 (`handleText(text, turn)`, `Gloss.semanticCategory`, `CatalogEntry.semanticCategory`, `SemanticCache.lookup/store` con glosas), §7.2 (`done` también en el error) y §7.5 (etiqueta `source`, dos tramos, direcciones enmascaradas y `/health`) |
| 4 | `docs/sdd-changes.md` | Las tres entradas del 2026-10-21 y la de métricas quedan marcadas como **aplicadas** |
| 5 | `data/SignAiWebSocketClient.kt` | `sendText` devuelve el turno y lo manda en el frame; el contador arranca en 1 en cada conexión. `Segment`, `PhraseDone` y `Failure` llevan `turn: Int?` |
| 5 | `ui/state/MainScreenState.kt` | `currentTurn` compara el turno de cada frame y descarta segmentos, `done` y `error` de turnos viejos. `StaleTurn` queda como respaldo cuando el backend no manda el campo |
| 6 | `../signall-backend/app/main.py` | `_redact_url` deja esquema y ruta y oculta host y puerto en `/metrics.json` |
| 6 | `scripts/verify_endpoints.py` | **Nuevo.** Verifica el enmascarado y el estado del catálogo en `/health` |
| 10 | `app/main.py` | `/health` publica `catalog_loaded` y `catalog_glosses`; con el catálogo vacío responde `degraded` |
| 11 | `app/config/settings.py`, `app/persistence/database.py`, `app/nlp/semantic_cache.py`, `app/main.py` | `DB_CONNECT_TIMEOUT_SECONDS` (5 s) y `DB_ACQUIRE_TIMEOUT_SECONDS` (5 s); el segundo se pasa a cada `pool.acquire` |
| 11 | `scripts/verify_database_resilience.py` | Se agregó un servidor TCP que acepta y no contesta: el pool se rinde en **0,50 s** |
| 12 | `build.gradle.kts`, `app/build.gradle.kts` | **ktlint** `org.jlleitschuh.gradle.ktlint` 14.2.0, `ktlintCheck` dentro de `build`. Las reglas de formato que chocaban con el estilo del repo se apagan en `additionalEditorconfig`; las de corrección quedan y el árbol pasa limpio |
| 13 | `scripts/measure_cache_lookup.py` | **Nuevo.** Mide el acierto en memoria contra la consulta a Neon sobre la misma frase |

## Qué se decidió

- No se abrió ninguna decisión nueva. Las 14 siguen como las aprobó el owner.
- **El run local de esta máquina sigue usando Qwen** porque `.env.local` existe. Es lo que
  el owner pidió para desarrollo; el archivo oficial del repo es `.env`, y borrar
  `.env.local` deja el backend contra `gpt-4o-mini`. Queda dicho para que nadie lea
  `/metrics.json` y crea que el modelo oficial es Qwen.
- Los puntos 2, 7 y 14 no se tocaron: son documentos, sesión aparte, o los aplica el owner.

## Qué se midió

- **Caché (punto 13)**, `scripts/measure_cache_lookup.py`, 20 rondas por camino, misma
  frase (`Bien más o menos.`), 109 filas cargadas, dentro del contenedor del backend:
  **p50 10 µs en memoria** y **p50 254 ms contra Neon** (p95 348 ms). Neon suma 254 ms al
  p50. La memoria se queda.
- **Conexión a la base (punto 11)**, `verify_database_resilience.py`, contra un servidor
  TCP local que acepta y no contesta: **0,50 s** de espera con tope de 0,5 s, en vez del
  tiempo del sistema operativo. La espera por una conexión libre pide 5 s.
- **Turno (punto 5)**, Redmi Note 9S, backend real en `127.0.0.1:8080` con `adb reverse` y
  STT local: `SignAiNet` muestra `segment 0/1 turn=1 -> HOLA,COMO ESTAS` y después
  `segment 0/1 turn=2 -> BUENOS DIAS`. La frase 1 se resolvió por caché en 102 ms y la 2 en
  69 ms.
- **Descarte por turno (punto 5)**, en el mismo device: una frase que no estaba en caché
  agotó los 10 s del modelo local y el backend mandó `error` y `done` del turno 1. La app
  registró `frame 'done' del turno 1 descartado (turno en vuelo=null)`: el `fail` ya había
  cerrado el turno y el `done` viejo no lo reabrió. Es el mismo guard que descarta una frase
  cancelada.
- **El modelo local no es alcanzable desde la red del contenedor** (`host.docker.internal:11434`
  con `ufw` activo, la trampa documentada en `AGENTS.md`): las frases que no están en caché
  fallan a los 10 s. No es una regresión de este cambio; para probar el modelo local hay que
  usar `docker-compose.host.yml`.
- **`/health` y `/metrics.json` (puntos 6 y 10)**, backend en marcha: `status: ok`,
  `catalog_loaded: true`, 50 glosas; `llm_base_url: http://***/v1` y
  `stt_ws_url: ws://***/ws/transcribe`.

## Qué quedó pendiente

- **Punto 2:** reescribir RF-CAT-03 en `../signall-docs` y Overleaf. Sesión de documentos.
- **Punto 7:** la tanda de documentos y Overleaf, con control de cambios. Sesión aparte.
- **Punto 8:** quitar o enmascarar el texto de las frases en los logs **antes** de una demo
  con personas reales. Hoy se queda, como se decidió.
- **Punto 14:** el owner cambia las menciones a **SignAll** y limpia la portada del SDD.
- **Backend:** los cambios están sin commit, como manda su `CLAUDE.md`. Los cinco scripts
  nuevos (`verify_audio_validation`, `verify_endpoints`, `verify_official_config`,
  `measure_cache_lookup` y el cambio en `verify_database_resilience`) también.
- **El camino de descarte por turno** se vio con el `error` + `done` de una frase fallida
  (turno 1), no con una cancelación: cancelar exige una frase en vuelo con el micrófono
  prendido (modo en vivo). El guard es el mismo (`turn != currentTurn`), y el flujo normal
  con `turn=1`…`turn=4` sí quedó verificado en el dispositivo.
