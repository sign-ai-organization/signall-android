# El artifact «Contraste del backend», verificado al 2026-11-03

**Qué es este documento.** El owner exportó un artifact que comparaba el backend
con los documentos de la tesis. Ese artifact se armó contra `origin/main` **`a03bbc9`**
del 21 de octubre a las 15:29, y hoy `origin/main` es **`c8aa516`**. Este documento
toma el artifact como lo que es —una lista de afirmaciones— y comprueba cada una
contra el código, la base de datos y los documentos de hoy. No cambia código ni
documentos: solo deja el estado y las decisiones que faltan.

> **Regla de lectura.** Todo lo que dice «verificado» se comprobó en esta sesión,
> con el archivo y la línea o con el comando que se indica. Lo que dice
> «no verificado» no se comprobó y no se rellena por inferencia. Las dos listas
> están separadas en §8.

---

## 0. Alcance, método y sellos

### 0.1 Qué se leyó y en qué estado

| Fuente | Sello de esta sesión | Cómo se usó |
|---|---|---|
| `sign-ai-backend` | `main` = `develop` = **`c8aa516`**, árbol limpio | Código, migraciones, `docs/`, `deploy/`, `.github/` |
| `sign-ai-stt` | `main` = `develop` = **`7a571c2`** | Solo lo que el artifact toca: búfer de 8 s y `/health` |
| `sign-ai-docs` | **`c778326`** (2/11), con `cambios/requerimientos.md` modificado sin commitear | v0.16, plan de pruebas, trazabilidad, `latex/` |
| `sign-ai-android` | **`2fc1f03`** | Contrato del WebSocket y catálogo de animaciones |
| Artifact | `/tmp/opencode/contraste-backend-artifacto.md` (704 líneas) | Lista de afirmaciones a verificar |
| Overleaf | **No se abrió**, por instrucción del owner | Se usó la copia de `sign-ai-docs/latex/` |
| `.env` del backend | **No se leyó el archivo** | Se instanciaron los `Settings` dentro de la imagen con el repo montado (§0.3) |

### 0.2 La trampa de las fechas

`git log a03bbc9..c8aa516` muestra commits del 14 de octubre mezclados con los del
27. No es un error: el 27 de octubre se mergeó `develop` en `main` (`c9e895a`) y
`develop` traía la línea vieja del camino por palabras. **El árbol neto no volvió
atrás** —el flujo por oraciones sigue entero— pero una lectura ingenua del log
puede hacer creer que `main` retrocedió. Lo que importa es el diff del árbol, que
son 27 archivos.

### 0.3 El `.env`: medido, no leído

El artifact advirtió que «tu `.env` real puede tener `LLM_TIMEOUT_MS=10000`». Para
comprobarlo sin abrir el archivo, se ejecutó la propia aplicación dentro de la
imagen construida, con el repositorio montado:

```bash
docker run --rm --network none -v "$PWD":/app -w /app \
  --entrypoint python sign-ai-backend-backend:latest -c \
  "from app.config.settings import settings; print(settings.llm_model, settings.llm_base_url,
   settings.llm_timeout_ms, settings.llm_max_retries, settings.debug_trace_enabled)"
```

Resultado (valores efectivos, no los de `.env.example`):

| Ajuste | Valor efectivo | Lo que dicen los documentos |
|---|---|---|
| `llm_model` | **`qwen3:4b-instruct`** | `gpt-4o-mini` (SRS `main.tex:1160-1161`, `:1061`; contraste §6.1) |
| `llm_base_url` | **`http://host.docker.internal:11434/v1`** | vacío = OpenAI (contraste §6.1) |
| `llm_timeout_ms` | **`10000`** | 5 000 (RNF-AV-03, SRS `main.tex:2699`) |
| `llm_max_retries` | **`0`** | 1 (RNF-AV-03) |
| `debug_trace_enabled` | **`true`** | apagado para el cliente de producción (`settings.py:13-15`) |

No hay `.env.local`: el único archivo es `.env` (26/10), que sigue apuntando al
modelo local. Esto es una divergencia entre la configuración que corre y la
decisión escrita, y ninguna de las 43 filas del artifact la ve.

### 0.4 Overleaf: qué se pudo y qué no

No se abrió Overleaf. Sí se leyó la copia que el repo de documentos guarda en
`latex/`, que según `ESTADO.md` es «una copia de lo que hay en Overleaf»:

- `latex/srs/main.tex`, 3 029 líneas, mtime **2/11 17:08**.
- `latex/sdd/main.tex`, 549 líneas, mtime **30/10 18:00**.

`ESTADO.md` dice «24/10» para los dos; está desactualizado. Las copias son
posteriores al proxy TLS y a la reescritura del SRS, así que sirven para verificar
casi todo lo que el artifact atribuía a Overleaf. **Lo que no se puede afirmar es
que Overleaf, en este minuto, sea idéntico a esa copia.**

---

## 1. Qué proponía el artifact y qué concluía

El artifact no es un contraste nuevo: es una **revalidación** del contraste que ya
existía en `../signall-backend/docs/historia/CONTRASTE-BACKENDvsDOCUMENTOS.md`, con una
columna añadida —«main hoy»— que miraba `a03bbc9`. Su valor está en tres cosas:
ordenó las 43 filas, separó los cambios de código que el documento no numeraba
(17), listó los nueve puntos de la §10.3 y agregó dos bloques que el documento no
tenía: ocho pendientes y siete advertencias.

**La tesis del artifact** se puede resumir así: el backend se había adelantado a
los documentos. Casi todas las filas se resolvían cambiando el SRS, el SDD o los
requerimientos; el código ya hacía lo que el documento todavía no describía. Las
excepciones eran pocas y estaban localizadas: validar el audio, sacar el texto de
los logs, el límite de solicitudes, la cola de sesiones, el nombre de
`rt_first_sign_ms` y el cifrado. El artifact concluía que los nueve puntos de
código de la §10.3 ya estaban implementados y que lo que quedaba era,
sobre todo, documentación y tres decisiones de fondo.

**Su método** fue leer el código en `a03bbc9` y el documento, fila por fila. No
ejecutó nada: no corrió el CI, no construyó la imagen, no consultó la base y no
abrió Overleaf ni el `.env`. Él mismo lo dice en T5, y eso marca el límite de sus
conclusiones.

**Lo que confirmo del artifact.** La mayor parte del diagnóstico es correcto y
sigue siendo útil: el flujo por oraciones, el `turn`, el `done` tras el error, la
lectura de `semantic_category`, el UPSERT de la caché, los analizadores estáticos
y las dependencias fijadas están implementados y verificados hoy. La lista de
advertencias (T1–T7) sigue siendo la mejor guía de lo que puede fallar en una
demo, con una excepción importante: T1 ya está aplicado en producción y T5 ya se
puede responder.

**Lo que no confirmo.** El artifact dio por buenos varios «según el documento,
agregado» sin abrir el documento. Hoy se puede comprobar que cinco de esos
cambios no están completos: spaCy no está en el SDD (fila 17), `EmbeddingModel`
no está en el texto del SDD (fila 5), `CLAUDE.md` sigue hablando del servidor
estático de GLB (fila 9), el ejemplo `/static/glb/casa.glb` sigue en el SDD y en
el requisito aunque la base ya guarda otra cosa (fila 12), y `docs/sdd.md` —la
copia del SDD que vive en el backend— se quedó con la lista de clases vieja
(filas 5 y 6). Además, el artifact describe un sistema y unos documentos que se
movieron después del 21 de octubre: el SRS se reescribió, la §3 se llenó, el
cifrado se instaló, la red de compose pasó a ser el default y el prompt cambió
la regla de los pronombres.

**Lo que el artifact deduce y hoy es falso.** La fila 33 y su pendiente S1
(«`.env.example` y `settings.py` siguen con `ws://stt:8000`, que no resuelve en
red host») eran ciertas en `a03bbc9`, cuando el backend corría con
`network_mode: host`. Desde el 26 de octubre el default es la red de compose y
`stt` **sí** resuelve; el modo host quedó como override y ahí sí se usa
`127.0.0.1`. El artifact se contradice a sí mismo en esa fila —dice
«Implementado» y a la vez «siguen con `ws://stt:8000`»— porque mezcló dos
estados.

---

## 2. Las 43 filas, hoy

Estados: **✅ resuelto** (código y documento alineados) · **🟡 parcial** ·
**🔴 abierto** (brecha real) · **⚪ ya no aplica** · **❌ cambió después del
artifact** · **❔ no verificable**.

| # | Lo que afirmaba el artifact | Estado hoy | Evidencia |
|---|---|---|---|
| 1 | Las 12 PNG del SDD eran del juego viejo | ✅ | Los 12 `sdd-*.png` son md5 idénticos en `sign-ai-docs/diagramas/png/`, `sign-ai-backend/docs/diagrams/` y `sign-ai-docs/latex/sdd/images/` (copia de Overleaf). Overleaf en vivo no se abrió |
| 2 | `Controller.sendResult(segments)` no existe | ✅ | No existe en el código; el SDD de Overleaf describe `sendTranscript`, `sendSegment` y `sendError` (`latex/sdd/main.tex:310`) y `docs/sdd.md:181` |
| 3 | El SDD dice que `Orchestrator` tiene un solo método | ✅ | `orchestrator.py:15` y `:18`; el SDD describe los dos (`latex/sdd/main.tex:312`) |
| 4 | `NLPService.transform` contra `split` y `transform_sentence` | ✅ | `nlp_service_impl.py:33-41`; el SDD describe los tres |
| 5 | Faltan `SentenceSegmenter` y `EmbeddingModel` en el SDD | 🟡 | El código tiene los dos (`app/nlp/embedding_model.py`, `nlp_service_impl.py:24`, `semantic_cache.py:27`). El texto de Overleaf nombra `SentenceSegmenter` (`latex/sdd/main.tex:323`) pero **no menciona `EmbeddingModel`**; `docs/sdd.md:192-210` sigue con la lista vieja |
| 6 | `lookup` devuelve `List<Segment>` en el SDD | 🟡 | El código usa `List<String>` (`semantic_cache.py:179`). Overleaf ya no tiene la lista de clases contradictoria; **`docs/sdd.md:200-201` sí** (`List<Segment>?`, `store(... segments ...)`) |
| 7 | El `store` aparece síncrono | ✅ | `nlp_service_impl.py:70-79` lo lanza en segundo plano; el SDD lo dice (`latex/sdd/main.tex:325`) |
| 8 | El SDD no dice quién dispara la traducción | ✅ | `controller.py:129-163`: solo `handle_text` traduce; `handle_transcript` solo reenvía (`:202-217`). RF-INT-COM-01 y RF-STT-03 lo dicen (`v0.16:195`, `:268-275`) |
| 9 | «Static GLB Server» en el SDD | 🟡 | Fuera del SDD y de los 12 `.drawio`; **sigue en `CLAUDE.md:18` y `:40`** y en el comentario de `requirements.txt:1` |
| 10 | `modified_at` «permite detectar cambios sin reiniciar» | ✅ | `docs/sdd.md:311` y RNF-MNT-01 (`v0.16:791`) dicen «después de reiniciar»; `catalog_repository.py` no lee la columna |
| 11 | `semantic_category` está en la base y nadie la lee | ✅ | `catalog_repository.py:52` la incluye en el `SELECT`; `validator_service_impl.py:21-25` la pasa al `Gloss`. En Neon: 50 filas con 10 categorías distintas |
| 12 | `glb_animation_path` guarda rutas `/static/glb/…` | 🟡 | **Los datos cambiaron:** Neon tiene `signs/0000_0000_0000.glb` … `signs/0049_0000_0000.glb`, 50 distintas, cero con `/static/glb/`. Pero el TODO de RF-CAT-01 (`v0.16:441`), `docs/sdd.md:326` y `latex/sdd/main.tex:391` siguen diciendo «hoy el catálogo guarda rutas como `/static/glb/casa.glb`» |
| 13 | El 7.4 tiene seis estados; falta «Texto listo» | ✅ | `sdd-7.4-diagrama-estados-ui.drawio` tiene los siete; el PNG coincide en los tres destinos; el texto lo describe (`latex/sdd/main.tex:419`) |
| 14 | El 7.4 rotula la salida de Error «≤2 s» | ✅ | El `.drawio` ya no tiene el número |
| 15 | El C4-2 dice «Filament» | ✅ | No aparece «Filament» en ningún `.drawio`; el C4-2 dice Three.js |
| 16 | El SRS no describe el protocolo y lista «solicitud en espera» | ✅ | RF-INT-COM-01 (`v0.16:195`, SRS `main.tex:2278`) describe los cinco mensajes, los tres de la app, el `turn` y el `done` tras error; «solicitud en espera» ya no está |
| 17 | spaCy no aparece en el SRS ni en el SDD §6.2 | 🟡 | SRS sí (`latex/srs/main.tex:1141`, con los 440 MB). El **SDD no tiene tabla de librerías ni menciona spaCy** |
| 18 | El SRS dice «el NLP no consume memoria» | ✅ | Corregido: «El procesamiento del lenguaje natural sí consume memoria…» (`latex/srs/main.tex:1287-1290`) |
| 19 | El SRS dice «dos puntos de entrada»; son cuatro | ✅ | `main.py:186`, `:199`, `:212` y `router.py:200`; el SRS dice cuatro (`latex/srs/main.tex:712`) |
| 20 | El SRS dice FastAPI 0.115 y LangChain 1.4.6 | ✅ | SRS 0.143.0 / 1.4.5 (`latex/srs/main.tex:1110`, `:1131`); `requirements.txt:2` y `:14`; la imagen del 21/10 tiene exactamente esas versiones |
| 21 | El SRS dice «8 s de audio por frase» del servidor | ✅ | El tope está en el STT (`../signall-stt/app/config.py:51`, `MAX_BUFFER_MS=8000`); el SRS ya lo atribuye al STT (`latex/srs/main.tex:1278`) |
| 22 | RF-INT-SW-01 dice que el STT es «componente interno» | ✅ | `v0.16:145` dice «servicio propio… por WebSocket»; RNF-POR-02 lo pone en contenedor aparte (`v0.16:846`) |
| 23 | RNF-SEC-01 pide un límite por cliente | ✅ | Requisito reescrito a medir sesiones (`v0.16:756-761`, SRS `main.tex:2847`); el código publica `sessions_active` y `sessions_total` (`router.py:216-217`) |
| 24 | RNF-SEC-02 pide validar que lo recibido sea audio | 🔴 | El requisito sigue (`v0.16:765-771`, SRS `main.tex:2856`) y el código **no valida nada**: `controller.py:102-121` reenvía cualquier byte, incluido vacío o impar |
| 25 | RNF-SEC-03: el texto queda en logs y caché | 🟡 | La voz no se guarda y el texto sí está en la caché; **los logs guardan el texto más de dos veces por frase**: `controller.py:159`, `:179`, `:255`, `:315`, `nlp_service_impl.py:59`, `:65`, `sentence_segmenter.py:31`. El requisito no menciona los logs |
| 26 | RNF-REL-01 y RNF-DES-04 piden cola y mínimo de simultáneas | ✅ con la medición pendiente | Requisitos reescritos a tres sesiones y sin cola (`v0.16:687`, `:596`); el código no tiene cola. La medición con tres sesiones **sigue sin hacerse** (`performance.md:721`) y está planificada como PR-REN-06 |
| 27 | RNF-REL-03: reintentos hacia la base | 🟡 | `database.py:26-37`, `:70-104`, `:107-131`; pero el TODO del requisito sigue pidiendo «implementar» (`v0.16:708`) → T6 |
| 28 | RNF-MNT-05: borrar una entrada del caché sin reiniciar | ✅ | Requisito reescrito a «después de reiniciar» (`v0.16:825-828`); el caché vive en memoria (`semantic_cache.py:116-119`) |
| 29 | RNF-BD-03: «cero consultas durante la traducción» | 🟡 | Requisito reescrito a «se evitan las lecturas» (`v0.16:882-888`); si la precarga falla, `_lookup` cae a Neon en cada frase (`semantic_cache.py:228-233`) |
| 30 | RNF-AV-03: avisar en un par de segundos si falla el modelo | 🟡 | Requisito: 5 s + 1 reintento (`v0.16:745-748`); el **código por defecto** lo cumple (`settings.py:42`, `:47`) pero **la configuración efectiva es 10 s + 0 reintentos** (§0.3) |
| 31 | RNF-MNT-03: analizadores estáticos | 🟡 | `pyproject.toml` y `.github/workflows/ci.yml`; el CI de `main` en `c8aa516` está **verde** (run 40617063393). La app móvil no tiene analizadores (ni detekt ni ktlint en `build.gradle.kts`) |
| 32 | RNF-MNT-04: dependencias fijadas | ✅ | `requirements.txt:2-17`; las **9 fijadas con `==` existen en PyPI** (HTTP 200), la rueda de `es_core_news_md-3.8.0` también responde 200, y la imagen del 21/10 tiene esas versiones instaladas (spaCy 3.8.16, el modelo 3.8.0) |
| 33 | RNF-POR-02: `network_mode: host` | ✅ | Default: red de compose y `8080:8080` (`docker-compose.yml`); host como override (`docker-compose.host.yml:21-35`); `stt_ws_url` resuelve (`settings.py:76`). RNF-POR-02 y el SRS ya exigen la red de compose (`v0.16:849`, `latex/srs/main.tex:2937`) |
| 34 | RF-INT-COM-03: TLS, WSS y proxy inverso | ✅ | `deploy/Caddyfile`, servicio `caddy` en `docker-compose.host.yml:48-61`, build `demo` con `wss://127.0.0.1:8443` (`app/build.gradle.kts:70`), tres diagramas y el SRS con el proxy (`latex/srs/main.tex:1170`, `:2150`). Deuda declarada: el tramo loopback va en claro, `release` apunta a `wss://backend.invalid` y el certificado es de laboratorio |
| 35 | RF-NLP-02: al modelo se le entrega «la frase completa» | ✅ | Requisito dice «cada oración» (`v0.16:308`); el código llama al modelo por oración (`nlp_service_impl.py:46-63`) |
| 36 | RF-CAT-03: «Seña no disponible» | 🟡 | El código solo marca `available: false` cuando la glosa no está en el catálogo (`validator_service_impl.py:18-20`); el requisito pide que el backend también detecte «está pero sin animación» (`v0.16:456-459`). **Hoy las 50 señas tienen animación** (300 clips por avatar en `app/src/main/assets/web/models/avatars.json`), así que el segundo caso no tiene ningún caso vivo, pero la brecha lógica sigue si se agrega una fila sin clip |
| 37 | `rt_first_sign_ms` promete la seña y mide el envío | ✅ | `controller.py:235-245`: mide hasta enviar la primera oración. RNF-DES-01 documenta los dos tramos (`v0.16:572`, SRS `main.tex:2615`) y la app mide el segundo (`SignAiMetrics.kt:126`) |
| 38 | El caché guarda duplicados y respuestas vacías | ✅ | `semantic_cache.py:236-241` (no guarda vacías) y `:311-317` (UPSERT); migración `004`; en Neon el índice único **ya existe** y hay 0 duplicados y 0 vacías |
| 39 | Si una frase falla a mitad, sale error y no done | ✅ | `controller.py:170-174` manda `done` tras `error`; el contrato lo dice (`android-integration.md:259-279`) y la app lo tolera (`MainScreenState.kt:1495-1522` limpia el turno y `:1755-1830` no lo convierte en éxito) |
| 40 | Ni el SRS ni el SDD mencionan la instrumentación | 🟡 | `docs/sdd.md:359-363` y `latex/sdd/main.tex:425-431` tienen la subsección. **Quedó incompleta**: no menciona `rt_cache_total`, `rt_turn_queue_ms` ni la etiqueta `source`, que entraron el 22/10 |
| 41 | La §3 del SRS está vacía con subtítulos de plantilla de juegos | ⚪ | La §3 (`\section{Requerimientos Específicos}`) está llena: `latex/srs/main.tex:1952` y siguientes |
| 42 | El SRS dice «SignAI» 25 veces y «SignALL» ninguna | 🟡 | En la copia del 2/11: **«SignAll» 34 veces, «SignAI» 3** (`:1713`, `:2330`, `:2987`), «SignALL» 0. Cambió el problema, no desapareció |
| 43 | Portada del SDD con «[PENDIENTE DE COMPLETAR]» y un solo registro | 🟡 | Sigue `\pendiente` + «Falta hoja de portada acá» + instrucciones azules (`latex/sdd/main.tex:101-110`, `:163`, `:530`); el historial **ya tiene dos filas** (`:131`) |

---

## 3. Los nueve puntos de la §10.3, hoy

| # | Punto | Estado | Evidencia |
|---|---|---|---|
| P1 | D-7: sacar la lista de glosas del prompt | ✅ hecho | `llm_client.py:74-109` (constante, sin vocabulario); `:156-160`; `verify_llm_prompt` pasa |
| P2 | LLM: 5 s y un reintento | 🟡 en código, 🔴 en la configuración | `settings.py:42`, `:47` y `llm_client.py:171-172`; **el `.env` efectivo es 10 s y 0 reintentos** (§0.3) y `verify_llm_timeout` falla por eso |
| P3 | Base de datos: tope, reintento y arranque controlado | ✅ | `database.py:26-37`, `open_pool` devuelve `None` (`:70-104`), `main.py:51-58` envuelve la carga del catálogo en `try` |
| P4 | Identificador de turno `turn` | 🟡 backend sí, app no | `router.py:40-71`, `controller.py:272-282`; **`SignAiWebSocketClient.kt:127` manda solo `type` y `text`** |
| P5 | `done` también cuando hay error | ✅ | `controller.py:170-174`; `verify_done_on_error` pasa |
| P6 | Leer `semantic_category` | ✅ | `catalog_repository.py:52`; `verify_catalog_semantic_category` pasa; 50 filas con categoría en Neon |
| P7 | UPSERT del caché y no guardar vacías | ✅ | `semantic_cache.py:236-241`, `:311-317`; `004` aplicada en Neon; `verify_cache_upsert` pasa |
| P8 | ruff y mypy | ✅ | `.github/workflows/ci.yml`; run **40617063393** en `main` (`c8aa516`), verde en 14 s |
| P9 | Dependencias fijadas | ✅ | `requirements.txt`; las 9 fijadas y la rueda del modelo existen en PyPI; imagen con las versiones exactas; `verify_dependencies_pinned` pasa |

**Nota sobre P2.** El valor por defecto (5 s + 1 reintento) es el de la decisión y
está en el código. Lo que no coincide es el archivo `.env` de esta máquina. El
propio script de verificación lo detecta: `verify_llm_timeout` imprime
«expected: 5000 / got: 10000» y «expected: 1 / got: 0». Eso no es un fallo del
código: es una configuración que pisa la decisión.

---

## 4. Los 17 cambios que el documento no numera (E)

| # | Afirmación | Estado hoy | Evidencia |
|---|---|---|---|
| E1 | Caché semántico en memoria | ✅ implementado, **sin medición publicada** | `semantic_cache.py:121-169` (precarga), `:179-233` (exacto y coseno, umbral 0,05), `:235-283`; el tope de 20 000 está en `settings.py:73`; `performance.md` no mide el antes/después y solo documenta el pool del 1/10 |
| E2 | D-7: la lista sale del prompt | ✅ | Igual que P1 |
| E3 | Reglas del prompt | ❌ **cambió después del artifact** | El artifact decía «regla de no emitir pronombres sueltos»; `c8aa516` la invirtió: «Keep the pronoun (YO, TU, USTEDES)» (`llm_client.py:88-89`, ejemplos `:101-109`). El plan de pruebas se alineó el 28/10 (`plan-pruebas/01-diseno/llm/general.md:40`, `02-datos/FR-01-frases-dominio.md:18`). **No hay medición registrada de la regla nueva** |
| E4 | Reparación de glosas | 🟡 | `_repair_glosses` sigue descartando en silencio lo que no está en el catálogo mientras quede una válida (`llm_client.py:227-246`); el comentario de `:56-61` y `CLAUDE.md:76-79` siguen diciendo que el validador las marca. Con D-7 pesa más → T3 |
| E5 | Modelo oficial y `LLM_BASE_URL` | 🟡 en `.env.example`, 🔴 en la máquina | `.env.example:34` y `:64` dicen `gpt-4o-mini` y vacío; **el `.env` efectivo usa Qwen local** (§0.3). El comentario de `llm_client.py:69-70` todavía dice que «el modelo local es el default en `.env`» |
| E6 | Llamada de calentamiento | ✅ | `main.py:107` y `:127-132`; `LLM_WARMUP_ENABLED=true` en `.env.example:70` y en el `.env` efectivo |
| E7 | Identificador de turno | 🟡 | Backend completo; la app no lo manda → §3, P4 |
| E8 | Router: traducción en su propia tarea y cierre de sesión | ✅ | `router.py:74-124` (`_PhraseTranslator` con candado) y `:237-262` (`asyncio.wait` con `FIRST_COMPLETED`) |
| E9 | Eco de la frase escrita | ✅ | `handle_text` no llama a `send_transcript` (`controller.py:129-184`); `handle_transcript` solo reenvía (`:202-217`) |
| E10 | Migración 003: cuatro columnas en `catalog` | ✅ | `migrations/003_add_catalog_asset_metadata.sql`; en Neon están `fps`, `total_frames`, `asset_bytes`, `asset_sha256` |
| E11 | Normalización del catálogo | ✅ | `catalog_repository.py:13-29` (`normalize_gloss`, conserva la Ñ) |
| E12 | Lectura periódica del `/health` del STT | ✅ | `main.py:157-179` cada 30 s; el STT publica `silence_timeout_ms` (`../signall-stt/app/main.py:35-42`) |
| E13 | RMS sin `audioop` | ✅ | `controller.py:55-73` |
| E14 | Métricas en el código | 🟡 | Métricas sí (`main.py:199-230`); **`/metrics.json` sigue publicando `llm_base_url` y `stt_ws_url` sin enmascarar** (`main.py:219`, `:223`) → S2 |
| E15 | Vaciar el caché antes de remedir | 🟡 | Sigue siendo manual; `CLAUDE.md:90-96` dice `DELETE` sin reiniciar → S3 |
| E16 | `flujo-oraciones` pasó a `main` | ✅ | `respaldo/main-2026-10-21` existe (`979e99d`); hoy `develop` y `main` están en `c8aa516` |
| E17 | Borrado de `docs/requirements.md` | 🟡 | Borrado, y `CLAUDE.md:28` apunta al v0.16; **la ruta que usa ya no existe**: `../signall-docs/documentos/requerimientos/` (hoy es `borradores/requerimientos/`) |

---

## 5. Los ocho pendientes (S) y las siete advertencias (T)

| # | Pendiente | Estado hoy |
|---|---|---|
| S1 | `network_mode: host` y `STT_WS_URL` | ⚪ **Ya no aplica.** El default es compose; `ws://stt:8000` resuelve. El override usa `127.0.0.1` (`docker-compose.host.yml:32`) |
| S2 | Enmascarar direcciones en `/metrics.json` | 🔴 Sigue sin hacerse (`main.py:219`, `:223`) |
| S3 | `CLAUDE.md` sobre vaciar el caché | 🔴 Sigue sin mencionar el reinicio (`CLAUDE.md:90-96`) |
| S4 | El nombre `rt_first_sign_ms` | ⚪ Es una decisión tomada (B, §2.4 del contraste), no un pendiente; el nombre se conserva a propósito (`controller.py:235-239`) |
| S5 | Decisiones abiertas | 🔴 quedan dos reales: validar el audio (fila 24) y el texto en los logs (fila 25). El límite de solicitudes y la cola ya se resolvieron en los requisitos |
| S6 | `docs/sdd.md` sin actualizar | 🔴 **y es peor de lo que decía:** además de `handle_text` sin `turn` y `Gloss` sin `semantic_category`, conserva la lista de clases de §7.1 con `List<Segment>` y tres colaboradores (`docs/sdd.md:192-210`), que Overleaf ya no tiene |
| S7 | Cambios de Android para el contrato nuevo | 🔴 confirmado: la app **no manda `turn`** (`SignAiWebSocketClient.kt:127`). El `done` tras error sí lo tolera (`MainScreenState.kt:1755-1830`) |
| S8 | Requisitos con código pendiente | 🟡 audio (sí), RF-CAT-03 (brecha lógica sin caso vivo), RNF-POR-02 (cerrado), RNF-MNT-03 móvil (sí) |

| # | Advertencia | Estado hoy |
|---|---|---|
| T1 | La migración 004 borra datos y nadie la aplica sola | ✅ **aplicada en Neon**: existe `idx_semantic_cache_phrase`, 109 filas, 0 vacías, 0 duplicadas. Sigue sin ejecutor de migraciones para una base nueva |
| T2 | El arranque sin base no se avisa | 🟡 `/health` sí publica `cache_loaded` y `cache_rows` (`main.py:186-196`), pero no el catálogo y `status` siempre es `ok` |
| T3 | D-7 sin lista agrava la reparación silenciosa | 🔴 sigue igual (`llm_client.py:227-246`) |
| T4 | El tope de 5 s no cubre todo | 🟡 sigue siendo una lectura razonable: `command_timeout` no cubre `connect` ni `pool.acquire`; además `open_pool` reintenta una vez, así que un arranque sin Neon puede esperar 2 × 60 s. No se midió |
| T5 | No verificó versiones ni analizadores | ✅ resuelto: las 9 fijadas y la rueda del modelo existen en PyPI, la imagen del 21/10 las tiene, CI verde |
| T6 | Dos TODO de los requisitos ya están implementados | 🔴 RNF-REL-03 (`v0.16:708`) y RNF-AV-03 (`v0.16:748`, SRS `main.tex:2702`) siguen diciendo «TODO: implementar» |
| T7 | La numeración de requisitos cambió | ✅ confirmado: RF-INT-COM-03 es el STT y RF-INT-COM-06 el cifrado (`v0.16:208`, `:235`). El propio contraste del backend todavía cita «RNF-INT-COM-03» para el proxy (`CONTRASTE…:1328`), que ya no existe |

---

## 6. Lo que cambió desde `a03bbc9` y el artifact no contempla

1. **Las métricas dicen de dónde salió cada respuesta (22/10, `78006a5`).**
   `rt_first_sign_ms`, `rt_sentence_delay_ms` y `rt_phrase_total_ms` llevan la
   etiqueta `source` (`cache`/`llm`), nace `rt_cache_total{source}` y
   `rt_turn_queue_ms`, y `rt_phrase_sentences` pasa de gauge a histograma
   (`controller.py:232-251`, `:300-313`, `semantic_cache.py:191-193`,
   `router.py:112-116`). Esto resuelve la «semilla 4» del contraste —separar el
   escenario de caché del de LLM—, pero **el SDD §7.5 y `performance.md` no las
   mencionan**. `verify_metrics_sources` pasa.

2. **La red de compose es el default (26/10, `3e8ff9c`).** `docker-compose.yml`
   publica `8080:8080` y declara `host.docker.internal`; el modo host queda como
   override. RNF-POR-02 y el SRS se reescribieron para exigir esto. Cierra la
   fila 33 y anula S1.

3. **El proxy TLS está instalado (27/10, `1c8e60e`).** Caddy en el 443, CA de
   laboratorio, build `demo` con `wss://`, tres diagramas y el SRS con el proxy.
   Cierra la fila 34. Deuda declarada: loopback en claro, `release` a
   `wss://backend.invalid`, certificado de una máquina.

4. **El APK se publica por Wi-Fi (27/10, `9c873be` y `13ea8ea`).** `deploy/apk/`
   servido por Caddy y `docs/plan-wifi.md`. No estaba en el artifact; agrega una
   superficie de despliegue que nadie contrastó contra los documentos.

5. **El prompt conserva los pronombres (27/10, `c8aa516`).** La regla y los
   ejemplos cambiaron; el plan de pruebas se actualizó el 28/10. La recomendación
   del artifact («mide la de pronombres») quedó sin objeto: ahora hay que medir
   la regla nueva, no la vieja. No hay medición registrada.

6. **Los valores del catálogo cambiaron.** Neon guarda `signs/NNNN_0000_0000.glb`
   (50 filas, 0 con `/static/glb/`). El documento y el requisito siguen usando
   `/static/glb/casa.glb` como ejemplo de lo que hay hoy. Es la divergencia
   inversa a la que describía la fila 12.

7. **La migración 004 ya está aplicada en Neon.** El índice único existe y no hay
   duplicados ni respuestas vacías. T1 deja de ser un riesgo de despliegue y pasa
   a ser un problema de reproducibilidad (no hay runner).

8. **Las 50 señas tienen animación.** `avatars.json` declara 50 señas y 300 clips
   por avatar. La premisa de la fila 36 («18 con `available: true` y ruta
   inexistente») ya no existe; la brecha del requisito queda como caso hipotético.

9. **El SRS se reescribió (copia del 2/11).** La §3 está llena, el texto usa
   «servidor» en vez de «backend» y «SignAll» en vez de «SignAI» (34 frente a 3),
   y el protocolo con `turn` está en el criterio de RF-INT-COM-01. Las filas 41 y
   42 describen un documento que ya no es ese.

10. **El SDD de Overleaf ya no tiene la lista de clases de §7.1, pero la copia
    del backend sí.** `latex/sdd/main.tex` pasa de la figura a la narrativa sin la
    lista; `docs/sdd.md:192-210` la conserva. La copia del backend lleva dos
    derivas distintas: la de §7.1 y la de `turn`/`semantic_category`.

11. **La configuración efectiva no es la decidida.** `.env` sigue con
    `qwen3:4b-instruct`, URL local, 10 s, 0 reintentos y traza de depuración
    encendida. La decisión de §6.1 (oficial `gpt-4o-mini`, local en `.env.local`)
    no se ejecutó, y no existe `.env.local`.

12. **La ruta de los requerimientos ya no existe.** `../signall-docs/documentos/`
    no está; el consolidado vive en `borradores/requerimientos/`. `CLAUDE.md:28`
    del backend, `AGENTS.md` de la app y las citas del contraste apuntan a la ruta
    vieja.

13. **El CI y las dependencias se pueden verificar.** `main` en `c8aa516` tiene
    el run 40617063393 verde; las 9 dependencias fijadas con `==` y la rueda del modelo existen en
    PyPI; la imagen del 21/10 las tiene instaladas. T5 deja de ser una incógnita.

14. **`performance.md` no documenta la caché en memoria ni las métricas nuevas,
    y sigue midiendo con un solo cliente** (`performance.md:721`). La prueba de
    tres sesiones existe en el plan (PR-REN-06) pero no se ha corrido.

15. **En la organización de Neon hay un segundo proyecto** (`my-first-project`,
    creado el 30/10) además de `sign-ai`. No es de este contraste, pero conviene
    saber que está ahí.

---

## 7. Recomendaciones para lo que sigue abierto

Cada recomendación está marcada **A** (cambiar código o configuración) o **B**
(cambiar documento o requisito). Ninguna se ejecutó. Las que tocan un requisito
necesitan la aprobación del owner; las que tocan Overleaf, además, control de
cambios.

| Tema | Recomiendo | Qué implica | Por qué |
|---|---|---|---|
| **RNF-SEC-02**, validar el audio (fila 24) | **A** | Tres comprobaciones en `handle_audio_chunk`: vacío, longitud impar y máximo; cerrar la conexión. Definir el máximo en `settings.py` | El requisito existe y el costo es bajo. La alternativa B —quitarlo— deja un requisito aprobado sin cumplir |
| **RNF-SEC-03**, el texto en los logs (fila 25) | **B** por ahora; **A** antes de una demo con personas reales | B: escribir en el requisito que, mientras se mide, los logs conservan el texto; A: dejar de escribir el texto y la traza con texto | El owner decidió el 21/10 conservar todos los logs. Lo que falta es que el requisito lo diga, porque hoy no lo cubre |
| **RNF-AV-03** y el `.env` (fila 30, P2) | **A** | Dejar `.env` con `gpt-4o-mini`, `LLM_BASE_URL` vacío, `LLM_TIMEOUT_MS=5000`, `LLM_MAX_RETRIES=1`; mover Qwen a `.env.local` | Es la decisión de §6.1 y la de RNF-AV-03. Hoy la máquina corre lo contrario y el script de verificación falla |
| **Modelo oficial** (E5) | **A** (misma acción que la anterior) | Igual | El SRS dice OpenAI y la máquina usa Qwen. Si la ronda se hace con Qwen, hay que decirlo en la Memoria y cambiar el requisito; no se puede afirmar una cosa y medir otra |
| **RF-CAT-03**, «Seña no disponible» (fila 36) | **B** | Reescribir el requisito: el segundo caso —existe pero sin animación— lo detecta la aplicación; el backend solo marca lo que no está en el catálogo | El backend no tiene el dato y hoy no hay ningún caso vivo (50/50 con clip). A —agregar una columna al catálogo— solo se justifica si el corpus crece |
| **`rt_first_sign_ms`** (fila 37) | **B** | Dejar el nombre y los dos tramos como están; la etiqueta `source` ya separa caché de LLM | La decisión B está tomada y el nombre conserva la comparación con la línea base. Renombrar (A) rompe citas en la app, `performance.md` y el contrato |
| **El ejemplo `/static/glb`** (fila 12) | **B** | Cambiar el ejemplo por `signs/NNNN_0000_0000.glb` en RF-CAT-01, `docs/sdd.md` §7.3 y el SDD de Overleaf | La base ya no guarda rutas de servidor. El documento describe un dato que no existe |
| **spaCy en el SDD** (fila 17) | **B** | Agregar spaCy a la tabla o al texto de §6.2 del SDD, o decir que el SDD no lista librerías | El SRS ya lo tiene; el SDD no |
| **El servidor estático de GLB en `CLAUDE.md`** (fila 9) | **B** | Quitar las dos menciones (`CLAUDE.md:18`, `:40`) y el comentario de `requirements.txt:1` | Es un componente que no existe y confunde a quien lea el repo |
| **`docs/sdd.md` desalineado** (S6) | **B** | Sincronizar la copia con Overleaf: quitar la lista de clases de §7.1, agregar `turn` a `handleText` y `semantic_category` a `Gloss`; o marcarla como copia con fecha | Hoy la copia contradice a Overleaf y al código en tres puntos |
| **`turn` en Android** (S7, P4) | **A** | Enviar `turn` en el frame de texto y usarlo para descartar el `done` de otra frase | El backend ya lo soporta y el contrato está escrito; sin la app, el campo no sirve para nada |
| **Enmascarar `/metrics.json`** (S2) | **A** | Enmascarar `llm_base_url` y `stt_ws_url`, como dice §9.4 | Es una decisión registrada y el endpoint no tiene autenticación |
| **`CLAUDE.md` y el reinicio del caché** (S3) | **B** | Agregar «reiniciar el backend» junto al `DELETE` | Con la caché en memoria, sin reiniciar no surte efecto |
| **T2**, salud del arranque | **A** | Publicar `catalog_loaded` y `catalog_glosses` en `/health`, y que `status` no sea `ok` con el catálogo vacío | Hoy un arranque sin base se ve sano y todas las señas salen no disponibles |
| **T4**, el tope no cubre todo | **A**, después de medir | Poner `timeout` en `pool.acquire` y en la conexión inicial, o trocear la precarga | Es la única forma de que el «peor caso 10 s» sea cierto también al arrancar |
| **T6**, TODO desactualizados | **B** | Quitar «implementar» de RNF-REL-03 y RNF-AV-03 | El código ya lo hace; el requisito se contradice a sí mismo |
| **Caché en memoria** (E1) | **B** hasta medir | Documentar en `performance.md` el antes/después y decidir con el número | Hay dos mediciones que se contradicen y ninguna está publicada |
| **Métricas nuevas en el SDD** (fila 40) | **B** | Agregar `rt_cache_total`, `rt_turn_queue_ms` y la etiqueta `source` a §7.5 y a `performance.md` | La instrumentación se documentó el 20/10 y cambió el 22/10 |
| **RNF-MNT-03 en la app** (fila 31) | **B** o **A** | B: quitar la app del criterio. A: agregar ktlint o detekt | Depende de si la tesis exige análisis estático en el cliente; hoy no hay ninguno |
| **Ruta `documentos/`** (E17) | **B** | Actualizar las citas a `borradores/requerimientos/` | La carpeta no existe; cualquiera que siga la cita se pierde |
| **Portada y nombre** (filas 42 y 43) | **B** | Limpiar `[PENDIENTE DE COMPLETAR]`, las instrucciones azules y decidir «SignAI» o «SignAll» | Es trabajo de entrega; el artifact ya lo atribuía al compañero |

**Lo que no se toca sin tu aprobación.** El modelo del `.env` y la validación del
audio son las dos decisiones con más efecto sobre lo que se va a medir. Si la
ronda diagnóstica se hace antes de resolverlas, los números van a describir un
sistema que no es el que dice el SRS.

---

## 8. Lo que no se pudo verificar

- **Overleaf en vivo.** No se abrió. Todo lo que se atribuye al SRS o al SDD se
  verificó contra `sign-ai-docs/latex/` (copias del 30/10 y 2/11). Si Overleaf
  cambió después, este documento no lo ve.
- **El contenido literal del `.env`.** No se leyó el archivo. Los valores de §0.3
  son los que la aplicación carga, medidos ejecutando `Settings`. No se muestran
  claves ni cadenas de conexión.
- **La aplicación en el dispositivo.** El contrato se verificó leyendo el código
  de Android; no se instaló ni se probó en un teléfono en esta sesión.
- **La escena 3D y el avatar.** Fuera del alcance de este contraste.
- **El tiempo de arranque con Neon caído.** No se provocó la caída ni se midió el
  peor caso de `open_pool`; T4 sigue siendo una lectura de los valores por
  defecto.
- **La migración 004 en una base nueva.** Se verificó que el índice existe en la
  base de producción; no se creó una base desde cero para probar el orden de las
  migraciones.
- **Los 8 s del STT en ejecución.** Se verificó el valor por defecto
  (`MAX_BUFFER_MS=8000`); el contenedor del STT está detenido.
- **La demo TLS de punta a punta.** El contenedor `caddy` está arriba, pero el
  backend y el STT están detenidos (`docker ps -a`); no se repitió la captura de
  tráfico de §10.2 bis.
- **El texto de los documentos que el artifact citaba por número de línea de
  Overleaf.** Los números cambiaron con la reescritura; se usaron las citas
  equivalentes de la copia local.

---

## 9. Decisiones que necesito del owner

Ninguna de estas se resolvió por cuenta propia. Las que tocan requisitos
necesitan una respuesta antes de que alguien escriba en el SRS o en el SDD.

1. **RNF-SEC-02.** ¿Se implementa la validación del audio (A) o se retira el
   requisito (B)?
2. **RF-CAT-03.** ¿Se reescribe para que el segundo caso lo detecte la app (B) o
   se agrega al catálogo la información de qué animaciones existen (A)?
3. **Modelo oficial.** ¿La máquina de medición pasa a `gpt-4o-mini` con 5 s y un
   reintento (A, como dice §6.1), o la ronda se hace con Qwen y se cambia el
   requisito y el SRS (B)?
4. **`docs/sdd.md`.** ¿Se sincroniza con Overleaf (B) o se congela como copia con
   fecha y se deja de tratar como fuente?
5. **`turn` en Android.** ¿Se completa el contrato en la app ahora (A) o queda
   como deuda documentada (B)?
6. **Métricas.** ¿Se enmascaran las dos direcciones en `/metrics.json` (A) o se
   retira esa decisión del documento (B)?
7. **Documentos.** ¿Se corrige el ejemplo `/static/glb`, la ruta `documentos/` y
   las menciones al servidor de GLB en `CLAUDE.md` (B), y con qué permiso —repo
   solamente o también Overleaf con control de cambios?

---

## Anexo: comprobaciones que se pueden repetir

```bash
# Desde ../signall-backend
# Estado de los repos
git -C ../signall-backend log --oneline -1        # c8aa516
git -C ../signall-stt log --oneline -1            # 7a571c2
git -C ../signall-docs log --oneline -1           # c778326

# Los 12 PNG, en los tres destinos
md5sum ../signall-docs/diagramas/png/*.png \
       ../signall-backend/docs/diagrams/*.png \
       ../signall-docs/latex/sdd/images/*.png

# Los scripts del backend, con el código actual y sin red
docker run --rm --network none -v "$PWD":/app -w /app \
  --entrypoint python sign-ai-backend-backend:latest -m scripts.verify_turn_id
# (y los otros ocho: done_on_error, llm_prompt, llm_timeout, dependencies_pinned,
#  metrics_sources, catalog_semantic_category, cache_upsert, static_analysis)

# El CI
gh run list --limit 5
gh run view 40617063393

# La base (solo SELECT)
#   catalog: 50 filas, 50 glosas distintas, 0 rutas /static/glb
#   semantic_cache: 109 filas, 0 vacías, 0 duplicadas, índice único presente
```

**Commits citados.**

| Commit | Repo | Qué hizo |
|---|---|---|
| `a03bbc9` | backend | Punta de `main` que vio el artifact (21/10 15:29) |
| `6e0871a` | backend | D-7: la lista sale del prompt |
| `4806a89` | backend | LLM: 5 s y un reintento |
| `a09ed17` | backend | Base: tope, reintento y arranque controlado |
| `4522dbd` | backend | `turn` y `done` tras error |
| `cdd09fd` | backend | `semantic_category` y UPSERT del caché |
| `8ff4a84` | backend | ruff, mypy y dependencias fijadas |
| `78006a5` | backend | Origen de cada respuesta en las métricas (22/10) |
| `3e8ff9c` | backend | Red de compose por defecto (26/10) |
| `1c8e60e` | backend | Caddy y TLS (27/10) |
| `9c873be` | backend | APK por Wi-Fi (27/10) |
| `c8aa516` | backend | El prompt conserva el pronombre (27/10) |
| `c778326` | docs | Copia del SRS del 2/11 |
| `2fc1f03` | android | Punta de `main` al cerrar esta revisión |
| `7a571c2` | stt | Punta de `main` al cerrar esta revisión |
