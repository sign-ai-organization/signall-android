Plan de implementación · SignAI

# De la propuesta al commit

Basado en revisión del código real de `sign-ai-backend`, `sign-ai-stt` y `sign-ai-android` · 05 nov 2026

[Cómo leer esto](#como-leer) [Mapa](#mapa) [Fase 1](#fase1) [Fase 2](#fase2) [Fase 3](#fase3) [Fase 4](#fase4) [Fase 5](#fase5) [Riesgos](#riesgos)

## 00 Cómo leer esto

Cada arreglo del artifact de diagnóstico se aterrizó contra el código real de los tres repos — no es la propuesta genérica, es dónde exactamente se toca. Las fases están ordenadas por **riesgo y dependencia**, no por impacto: la Fase 1 es aislada y paralelizable; la Fase 2 es el núcleo que sube el acierto; las fases 3-5 son refinamiento y housekeeping.

### Dos cosas que el código ya deja claras

- **Decisión D-7 (owner, 2026-10-16) sigue vigente:** el catálogo *no* va en el prompt del LLM, por diseño — lo confirma el comentario de cabecera en `app/nlp/llm_client.py`. Todo arreglo de glosado va en `_repair_glosses`, nunca en el prompt.
- **La capa de reparación ya existe**, no se construye desde cero: `_merge_fragments` y `_split_into_glosses` ya resuelven glosas frasales partidas. La capa de vocabulario (Fase 2) extiende este mismo mecanismo, no agrega uno paralelo.

## 01 Mapa de los 10 arreglos

| Fase | Repo | Arreglo | Archivo principal |
| --- | --- | --- | --- |
| 1 | backend | Índice único en catálogo | migrations/005\_\*.sql |
| 1 | stt | Umbral de filtro no-voz | app/config.py |
| 1 | backend | Normalizar números/fechas | app/nlp/text_normalizer.py |
| 1 | backend | `JUCIOSO`→`JUICIOSO` decidido | tools/seed_catalog.py + 17 refs |
| 2 | backend | Flexión + pronombre (TU/YO) decidido | app/nlp/gloss_lexicon.py *(nuevo)* |
| 2 | backend | Sinónimos como tabla en BD decidido | migrations/006_catalog_synonyms.sql *(nuevo)* |
| 3 | backend | Preguntas sin "?" | app/nlp/gloss_analyzer.py |
| 3 | backend | Embeddings parametrizables + umbral de sinónimos | app/nlp/embedding_model.py |
| 4 | android | Cola de turnos del avatar decisión | ui/state/MainScreenState.kt |
| 4 | android | Reactivar micrófono sin relanzar | domain/BackendEvent.kt |
| 5 | backend | Invalidar caché al editar por SQL | app/nlp/semantic_cache.py |
| 5 | backend | Interfaz para Orchestrator | app/orchestrator/orchestrator.py |

FASE 1

## 02 Aislados, bajo riesgo, en paralelo

No dependen de nada más y no tocan el pipeline de glosado. Se pueden hacer primero, en cualquier orden, cada uno por su cuenta.

#### Índice único sobre `gloss`

backendVAL-02

app/persistence/migrations/001_create_catalog.sql:10 → nueva 005_catalog_unique_gloss.sql

Confirmado

`idx_catalog_gloss` es un índice normal, no `UNIQUE`. Se verificó: insertar `HAMBRE` con un `id_sign` nuevo lo acepta sin error.

Cambio

`ALTER TABLE catalog ADD CONSTRAINT uq_catalog_gloss UNIQUE (gloss);` en una migración nueva. `CatalogRepository.load_all()` no cambia: ya construye el mapa en memoria por `normalize_gloss`, la restricción solo cierra la puerta en la base.

Verificar

Repetir el paso de `PR-VAL-02`: insertar la misma glosa con `id_sign` distinto, debe fallar por `unique constraint`.

#### Filtro de audio sin voz

sttSTT-03

app/transcriber.py:263-266 — thresholds en app/config.py:66,73

Confirmado

Un segmento se descarta solo si `no_speech_prob >= NO_SPEECH_THRESHOLD` (0.6) o `avg_logprob < AVG_LOGPROB_THRESHOLD` (-1.0). Whisper transcribe tos/carraspeo con confianza suficiente para pasar ambos umbrales.

Cambio

Endurecer `AVG_LOGPROB_THRESHOLD` (p.ej. -1.0 → -0.5) en un experimento controlado, midiendo en paralelo contra los 60 audios de voz real de `AU-01` — el riesgo es descartar habla válida de baja confianza, no solo ruido. No cambiar el código: son variables de entorno, el experimento es de calibración.

Verificar

Reproducir `PR-STT-03` (los 8 audios de `AU-03`) y `PR-STT-01` (WER de `AU-01`) en la misma corrida — un umbral que baja los falsos positivos de ruido pero sube el WER no sirve.

#### Corregir `JUCIOSO` → `JUICIOSO`

backendmás que código

sign-ai-android/tools/seed_catalog.py:71 — y 17 referencias más fuera de código

Confirmado

La fuente real de la glosa 29 es `seed_catalog.py:71`: `(29, "JUCIOSO", "adjetivo")`. El catálogo en Neon se siembra desde ahí, así que es el único lugar de código que hay que tocar para que el sistema en vivo diga `JUICIOSO`.

Pero no es solo eso

Busqué `JUCIOSO` en los tres repos y en `sign-ai-docs`: aparece **17 veces más** fuera de código — en datos del plan de pruebas (`FR-01`, `FR-02`, `CO-03`, `CT-03`), en formularios de evaluación con personas (`PER-01/02/03`) y en el anexo de datos del documento LaTeX de la tesis (`anexo-c-datos.tex`).

Pasos

1. Cambiar `seed_catalog.py:71` a `"JUICIOSO"` y volver a correr el seed (idempotente, según `AGENTS.md` del Android).
2. Reiniciar el backend — el catálogo se carga una sola vez al arrancar, un reseed sin reiniciar no se ve.
3. Actualizar las referencias en `sign-ai-docs/borradores/plan-pruebas/` y `anexo-c-datos.tex` — pero esos archivos **los edita el owner**, no una sesión de agente (regla explícita del repo Android), así que esta parte queda para ti o se propone como diff, no se aplica solo.

#### Normalizar números y fechas antes del LLM

backendLLM-04

app/nlp/text_normalizer.py (nueva función) → app/nlp/nlp_service_impl.py

Confirmado

`text_normalizer.py` hoy solo colapsa espacios y pliega acentos para comparar — no hay ninguna regla de números ni fechas en el repo.

Cambio

Agregar una función de normalización de entidades (regex para fechas/teléfonos/cifras) en el mismo módulo, llamada desde `NLPServiceImpl` antes de mandar la oración al `LLMClient`. Convención a decidir: deletreo dígito por dígito si el catálogo tiene glosas de número, o una seña genérica + valor en subtítulo si no las tiene.

Verificar

Reproducir `PR-LLM-04` con las 10 frases de `FR-06` que tienen números — hoy 4/10 producen glosas fuera de catálogo.

FASE 2

## 03 La capa de vocabulario — el núcleo

Esta es la que sube el 57 % de `PR-INT-02`. Depende de entender `_repair_glosses` tal como está hoy, así que primero lo que ya hace y después lo que se le agrega.

#### Lo que ya existe (no se reescribe)

app/nlp/llm_client.py:289-380

Flujo actual

`_repair_glosses` recibe la lista de glosas crudas del LLM, llama a `_merge_fragments` (rejuntar fragmentos partidos de una glosa frasal) y después, por cada glosa, busca `self.vocabulary.get(normalize_gloss(gloss))`. Si no hay match, intenta `_split_into_glosses`; si tampoco, la descarta.

Dónde entra lo nuevo

Justo donde hoy dice *"si no hay match, se descarta"* — ahí va la cadena de resolución nueva, **antes** de descartar, no reemplazando lo que ya funciona.

### Las 4 decisiones ya están tomadas

- **1. Pronombre implícito → sí, agregarlo.** El sistema sí debe insertar `TU` o `YO` según corresponda, aunque la persona no lo haya dicho en voz alta.
- **2. Ambigüedad usted/él → se habla directo a la persona.** Cuando la frase es ambigua entre "tú/usted" y "él/ella" (p.ej. «¿Se siente mal?»), el sistema asume que le habla a la persona directamente: `TU`, no la tercera persona.
- **3. `JUCIOSO` → se corrige a `JUICIOSO`.** Se respeta la ortografía correcta, no la errata del dataset original.
- **4. La tabla de sinónimos vive en la base de datos**, como tabla/columna nueva del catálogo — no como constante en el código.

#### Módulo nuevo: `gloss_lexicon.py` — flexión y pronombre

Impacto altoINT-02

app/nlp/gloss_lexicon.py (nuevo) · app/nlp/llm_client.py (cambio de firma)

Regla 1

Resolución de flexión: si `normalize_gloss(gloss)` no está en el vocabulario, probar singular (quitar "S" final), femenino↔masculino (terminación "A"↔"O") y variante con/sin Ñ, antes de rendirse. Arregla `NINOS→NIÑO`, `BIENVENIDOS→BIENVENIDO`.

Regla 2

Inserción de pronombre, ya resuelta por el owner: si el verbo de la oración original está en **segunda persona** (estás, tienes, comes) o hay un posesivo de segunda persona (tu hermano) y la lista de glosas no trae `TU`, se agrega `TU`. Si está en **primera persona** (estoy, siento, tengo) y no trae `YO`, se agrega `YO`. Cuando la persona gramatical es ambigua (ni primera ni segunda explícita, como «¿Se siente mal?»), la regla por defecto agrega `TU` — es la decisión 2 de arriba, habla directo a la persona en vez de asumir un tercero.

Cambio de firma

La Regla 2 necesita el texto original de la oración, que `_repair_glosses` hoy no recibe (solo la lista de glosas). Hay que pasarlo desde `transform(self, text)` hacia abajo — es el único cambio de interfaz de toda la fase.

No tocar

`normalize_gloss` en `catalog_repository.py` se queda igual: es la llave que usan el catálogo y la caché, agregarle flexión ahí fusionaría glosas que hoy son filas distintas.

#### Sinónimos: tabla nueva en la base de datos, no en código

Impacto altoLLM-02, CAC-03

migrations/006_catalog_synonyms.sql (nuevo) · app/validation/catalog_repository.py · app/nlp/semantic_cache.py

Diseño (decisión 4)

Tabla nueva `catalog_synonym (id serial, synonym varchar unique, id_sign int references catalog(id_sign))`: cada fila dice "este sinónimo significa la glosa de este `id_sign`". Una sola glosa canónica puede tener varios sinónimos (`MUCHO` ← `MUY`; `PERMISO` ← `CON PERMISO, DAME PASAR`).

Dónde se conecta — sin módulo nuevo para esto

`CatalogRepository.load_all()` ya construye `self.cache` con las 50 glosas; se agrega una segunda consulta que suma los sinónimos al mismo diccionario, apuntando a la misma `CatalogEntry` del canónico. Como `_repair_glosses` en `llm_client.py` ya busca en `self.vocabulary.get(normalize_gloss(gloss))`, un sinónimo se resuelve **automáticamente**, sin tocar `llm_client.py` para esta regla.

Para la caché (`CAC-03`)

`semantic_cache._lookup` normaliza el *texto de la frase*, no una glosa suelta, así que necesita su propio paso: antes de `normalize(text)`, sustituir las palabras/frases que estén en `catalog_synonym` por su forma canónica. Mismo dato, dos puntos de uso distintos.

Respeta D-7

Esta tabla nunca se le manda al LLM — solo se consulta después de que el LLM ya respondió (en `_repair_glosses`) o antes de buscar en caché. Es exactamente la misma relación que el catálogo ya tiene con el LLM hoy.

Verificación de toda la fase: reproducir las 30 frases de `PR-INT-02` completas — no solo las 13 que fallan hoy — y contar regresiones. Aritmética esperada: 17 → 26-28 de 30 (87-93 %), más lo que suma la regla de persona ambigua ya resuelta. Reusar `sonda-glosado.py` si ya existe en el repo, o el mismo patrón: `docker exec` contra el contenedor del backend, sin tocar la caché productiva.

FASE 3

## 04 Afinar huella gramatical y caché

Mejoras acotadas, cada una en su propio archivo, sin depender de la Fase 2.

#### Preguntas de contenido sin signo "?"

HUE-03

app/nlp/gloss_analyzer.py:86-90

Confirmado leyendo el código

Las dos ramas de pregunta están condicionadas a `has_question_mark`: sin "?", ni `CONTENT_QUESTION` ni `YES_NO_QUESTION` se disparan nunca, aunque haya una palabra interrogativa. Esto explica por qué `PR-HUE-03` mide 0 % en preguntas sí/no sin puntuación — es así por construcción, no es un caso de borde raro.

Cambio viable

Para `CONTENT_QUESTION`: disparar también cuando `has_interrogative_word` es verdadero, sin exigir `has_question_mark` — la palabra interrogativa ya es una señal léxica fuerte en español.

Límite real, no un fix rápido

`YES_NO_QUESTION` no tiene ningún marcador léxico en español — depende de la entonación. Sin una señal nueva (p.ej. algo que el STT pudiera exponer), este caso queda como limitación documentada, no como bug a cerrar en esta fase.

#### Embeddings parametrizables + umbral de sinónimos

CAC-01, CAC-02

app/nlp/embedding_model.py:1-11 · app/main.py:64 · app/config/settings.py

Confirmado

`EmbeddingModel()` se instancia sin argumentos en `main.py:64`; el nombre del modelo es la constante `DEFAULT_MODEL_NAME`, no hay variable de entorno para cambiarlo.

Cambio

Agregar `EMBEDDING_MODEL_NAME` a `Settings` y pasarlo a `EmbeddingModel(model_name=...)` en `main.py`. Esto no mejora nada por sí solo — destraba poder *probar* `bge-m3` o `multilingual-e5-large` sin editar código, que es lo que `PR-CAC-01` pide y hoy no se puede.

Riesgo a vigilar

Cualquier cambio de umbral de similitud se mide contra `PR-CAC-02` (calibración) **y** `PR-CAC-04` (falsos aciertos) juntos: subir sinónimos sin volver a abrir el caso de la negación que `PR-CAC-04` ya cerró.

FASE 4

## 05 Android — una decisión y un fix

#### El avatar corta la frase anterior al llegar una nueva

androidAVT-03

ui/state/MainScreenState.kt:1260 (sendLivePhrase) y :1434/:1504 (requestTranslation)

Confirmado leyendo el código

`clearTurnState()` se llama **sin condición** cada vez que se manda una frase nueva, con el comentario explícito "Un turno anterior no puede filtrarse a este". Esto vacía `turnQueueItems` del turno en vuelo — es la causa exacta del corte que mide `PR-AVT-03`, y es una decisión de diseño deliberada, no un descuido.

Esto NO es un bug a arreglar solo — es una decisión

Hay dos comportamientos válidos y la elección le pertenece al owner, en el mismo estilo que `D-7`/`D-8`:

- **(a) Mantener el corte** — una frase nueva siempre interrumpe la que suena, útil en Modo en Vivo para no acumular atraso. Entonces `PR-AVT-03` está probando el comportamiento equivocado y la ficha se corrige, no el código.
- **(b) Cola real entre turnos** — bufferear `turnQueueItems` de un turno nuevo sin tocar el que está sonando, y encadenarlo cuando termine. Esto cambia el comportamiento de Modo en Vivo también, no solo el de texto, y `docs/MODO-EN-VIVO.md` hay que revisarlo en consecuencia.

Si se elige (b)

El punto de cambio es `sendLivePhrase` y la ruta de texto equivalente: en vez de llamar `clearTurnState()` de inmediato, encolar la frase entrante y dejar que el \`done\`/\`onPlaybackDone\` del turno actual dispare el envío real.

#### El micrófono no se reactiva solo cuando el STT vuelve

androidRES-03

domain/BackendEvent.kt:22-65 · data/SignAiWebSocketClient.kt

Confirmado leyendo el código

`BackendEvent` es una `sealed interface` con 6 casos fijos (`Connected`, `Transcript`, `Segment`, `PhraseDone`, `Failure`, `Disconnected`) y su propio comentario dice que "frames con un `type` que este cliente no conoce son descartados por el parser". No existe ningún caso de capacidad de audio — el frame que `PR-RES-03` vio en el log del backend (`capability{audio:false}`) llega al cliente y el parser lo tira, por diseño actual, no por un bug de parseo.

Cambio — aditivo, respeta la regla del repo

`AGENTS.md` de este repo pide explícitamente que un comando/evento nuevo sea aditivo, nunca un reemplazo. Agregar `data class Capability(val audio: Boolean) : BackendEvent`, el caso en el parser de `SignAiWebSocketClient.kt`, y en `MainScreenState` un handler que refresque la disponibilidad del micrófono sin relanzar la app.

Contrato cruzado

Este cambio toca el WebSocket entre repos: hay que confirmar primero en `sign-ai-backend` el nombre exacto y el momento en que ya manda este frame (se vio en `PR-RES-03`, pero no está en `docs/android-integration.md` citado por `BackendEvent.kt`). Es justo el caso que pide el skill `contrato-websocket` de este repo: leer el contrato en los dos lados antes de tocarlo.

FASE 5

## 06 Housekeeping — no mueve el acierto

#### Invalidar la caché en memoria al editar por SQL

backendCAC-05

app/nlp/semantic_cache.py:123 (load_all) — no hay ningún endpoint de recarga hoy

Confirmado

`load_all()` corre una sola vez al arrancar y llena `self._indexes`; nada vuelve a llamarlo. Un `UPDATE` manual en Neon no se refleja hasta reiniciar el contenedor.

Cambio

Endpoint de administración (`POST /admin/cache/reload`) que vuelva a correr `load_all()` contra el pool existente, o `LISTEN/NOTIFY` de Postgres si se quiere automático. El endpoint es más simple y suficiente para el caso de uso medido (edición manual ocasional).

#### Interfaz para `Orchestrator`

backendTRA-01

app/orchestrator/orchestrator.py:10 · app/controller/controller.py

Confirmado

`Orchestrator` es una clase concreta sin ABC, a diferencia de `NLPService`/`ValidatorService` que sí la tienen. `controller.py` importa la clase concreta porque no hay otra opción.

Cambio

Extraer `OrchestratorService(ABC)` con `translate`/`translate_stream`, siguiendo el mismo patrón ya usado en `validation/validator_service.py`. Cambio mecánico, sin riesgo funcional.

## 07 Lo que no hay que romper

- **D-7 sigue vigente:** ningún arreglo de esta lista mete el catálogo en el prompt del LLM. Toda corrección de vocabulario vive después de la respuesta del modelo.
- **No cambiar `normalize_gloss`:** es la llave compartida entre catálogo y caché; la flexión/sinónimos van en una capa aparte (`gloss_lexicon.py`).
- **No reabrir `PR-CAC-04`:** cualquier ajuste de umbral de similitud se corre junto con el caso de la negación que ya se cerró, no solo con el de sinónimos.
- **En Android, todo evento nuevo es aditivo** (regla explícita del repo): nunca renombrar o quitar un caso de `BackendEvent` existente.
- **Cambiar el prompt o el modelo no invalida `semantic_cache` sola** — hay que vaciarla a mano (`DELETE FROM semantic_cache;`) o las glosas viejas van a seguir saliendo por caché y van a parecer que el arreglo no funcionó.

Archivos leídos para este plan: `sign-ai-backend/app/{nlp,validation,persistence,orchestrator}/*`, `sign-ai-stt/app/{transcriber,config}.py`, `sign-ai-android/.../{MainScreenState,BackendEvent}.kt`. Compañero del artifact de diagnóstico de resultados-consolidados.xlsx.