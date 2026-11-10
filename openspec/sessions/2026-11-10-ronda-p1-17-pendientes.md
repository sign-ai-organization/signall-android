# Ronda P1 · las 17 pruebas pendientes del carril 1

**Fecha:** 2026-11-10, tarde · **Rama:** `main`

## Qué llegó

El owner trajo todo de los remotos y pidió correr otra vez **solo las pruebas que no habían
quedado como pasadas**, en el carril P1. La división vigente es la del reparto que Sergio
actualizó el 10/11 (`2ad7b1c`): 25 pruebas para P1, 23 para P2 y 26 para P3, con 32 marcadas
«Pasó · no repetir». De las 25 de P1, 8 ya habían pasado el 6/11, así que quedaban 17.

Antes de arrancar se revisó la división: suma 74, no hay pruebas repetidas ni sin dueño, y las
cuatro que Sergio movió (REN-03 y SEC-01 a P3, TRA-03 y REN-04 a P1) quedan en el carril que
tiene el recurso que les falta.

## Qué se hizo

- **Remotos:** `git fetch --all --prune` y `pull --ff-only` en los cinco repos. Entraron
  `a7d4eec` (backend), `4824979` (STT), `ca21dc1` (docs), `dc90b99` (app). `signai-avatar` no
  tenía novedades.
- **Ambiente:** la contraseña de Neon había rotado (el backend arrancaba `degraded` con
  `InvalidPasswordError`) y la clave de OpenAI del `.env` devolvía 401. El owner pasó un
  `.env` nuevo; se reemplazó y el backend arrancó `ok` con 50 glosas y 13 sinónimos.
- **Arnés versionado:** esta vez los scripts quedan en `docs/plan-pruebas/arnes/`
  (`extraer_anexo.py`, `seg01.py`, `hue.py`, `cac.py`, `llm.py`, `qwen01.py`, `tra01.py`,
  `avt06.py`, `ws.py`, `ren04.py`, `ren04.sh`). El del 6/11 se perdió con el `/tmp` y hubo que
  reconstruirlo.
- **Las 17 corridas:** SEG-01/03, HUE-01/02/03, CAC-01/02/03, LLM-01/02/03, AVT-06, REN-04,
  REN-08, REN-09, TRA-01 y TRA-03. Resultado: **1 Pasa, 2 Falla, 14 Se reporta**.
- **Cierre:** 33 archivos de evidencia en `evidencias/<BLOQUE>/PR-*_E_20261110_*`, el sello
  `SELLO-P1_E_20261110_01.json` y el informe `docs/plan-pruebas/RESULTADOS-P1-2026-11-10.md`.

## Hallazgos

1. **El 78,0 % de PR-SEG-01 del 6/11 estaba subestimado por el arnés.** Comparaba la división
   esperada con los corchetes de las muletillas como texto literal. Con la regla del plan
   (la muletilla se puede pegar o descartar) el número es **86,0 %**.
2. **La caché se mide por oración, no por frase.** Con la unidad correcta, mpnet **no** supera
   a MiniLM (18/34 contra 22/34) y los sinónimos siguen en 3/12.
3. **El umbral de la caché pasó de 0,03 a 0,05** en el `.env` del 10/11. A 0,05 aparece el
   primer falso acierto medido (`FR-04-005`, distancia 0,031). PR-CAC-04 está marcada como
   pasada con 0/30 y con este umbral no se puede afirmar que siga pasando: hay que repetirla o
   volver a 0,03.
4. **PR-LLM-02 sigue fallando** en CO-04-008 (frase de cortesía después del punto) y
   **PR-TRA-01 sigue fallando** por `normalize_gloss`. Ninguna tiene arreglo desde el 6/11; las
   dos esperan una decisión o un cambio del backend.
5. **El modelo local se mide desde el host.** Ollama en `127.0.0.1:11434` no es alcanzable desde
   el contenedor; con el mismo prompt y la misma reparación da 20/30, igual que gpt-4o-mini.

## Qué quedó pendiente

- **PR-CAC-04:** repetirla con el umbral nuevo, o volver a 0,03.
- **PR-LLM-02:** arreglo del parser en el backend.
- **PR-TRA-01:** decisión del owner sobre `normalize_gloss` (kernel compartido o módulo neutral).
- **La caché semántica quedó vacía y luego con 3 frases.** Para medir REN-04 con la caché fría
  se borraron las 107 filas de `semantic_cache` (`DELETE` + `POST /admin/cache/reload`, con
  `ADMIN_TOKEN` agregado al `.env`). La caché es derivada: se rehace sola con el uso.
- **El libro `resultados-consolidados.xlsx` quedó con dos rondas**, como pidió el owner: la
  **diagnóstica** es la primera pasada del 06/11 (74 filas) y la **de evaluación** es la
  repetición del 10/11, con **solo las 18 pruebas del carril P1**. La ronda del 05/11 salió del
  libro y queda en el historial de git; las 11 filas que el carril 2 había escrito en la ronda
  de evaluación se sacaron para que cada carril agregue las suyas. Para los tests que P2 había
  pisado, la fila diagnóstica se restauró a su resultado del 06/11 desde el commit `8be173d`.
  Quedan 92 filas.

## Decisiones del owner en esta sesión

- Alcance: las 17 pendientes del carril P1, no solo las 2 fallas.
- Usar el `.env` nuevo de `~/Descargas/envtesis` en vez de correr con el modelo local.
- Cargar las 17 filas al libro y dejar una copia en `~/Descargas/`.

## Cómo se reproduce

Está en `docs/plan-pruebas/RESULTADOS-P1-2026-11-10.md` §8 y en cada script de
`docs/plan-pruebas/arnes/`.

## Segunda parte: los tres arreglos (misma fecha)

El owner pidió, en este orden: arreglar el parser de LLM-02, bajar el umbral de la caché a 0,04
y agrandar la lista de verbos imperativos y la tabla de sinónimos. Se hizo todo y se repitieron
las 10 pruebas afectadas.

- **Backend, `app/nlp/llm_client.py`:** `_parse_glosses` parte también por fin de oración
  después del separador elegido. PR-LLM-02 pasa de Falla a **Pasa**: 0/30 respuestas reales con
  problemas, 10/10 fabricadas, y CO-04-008 devuelve `TIO, TRABAJAR`.
- **Backend, `app/nlp/gloss_analyzer.py`:** siete verbos imperativos más, salto de los
  marcadores de arranque y de la cláusula previa a la coma. HUE-03 pasa de 151/200 (75,5 %) a
  **168/200 (84,0 %)**, con las órdenes de 22/40 a 39/40 y los otros cuatro grupos iguales.
- **Backend, migración `007_catalog_synonyms_fr03.sql`:** 12 sinónimos nuevos (13 → 25 filas).
  CAC-03 pasa de 23/34 a **32/34** y los sinónimos de 3/12 a **12/12**.
- **Backend, `.env`:** umbral de 0,05 a **0,04**. CAC-04 se corre por primera vez con ese umbral
  y **Pasa** con 0/30 trampas; CAC-02 muestra que 0,04 es el punto más alto sin falsos aciertos
  (0,05 ya acepta 2 de 30).
- **Arnés:** `cac.py` ahora escribe la evidencia de PR-CAC-04, que antes no se generaba.
- **Libro:** la ronda de evaluación queda con 29 filas (14 Pasa, 1 Falla, 14 Se reporta). La
  única falla es TRA-01.

**Trampa de ambiente:** `docker compose build backend` falla por falta de espacio en disco
(98 %). Para medir se copiaron los dos `.py` al contenedor en marcha y se reinició. El cambio
está en el repositorio; la imagen hay que reconstruirla cuando haya disco.

**Pendiente de otro lado:** los cambios del backend están en la rama
`fix/glosado-hue-cac-llm-p1` y en el **PR #13 contra `develop`**
(https://github.com/sign-ai-organization/sign-ai-backend/pull/13), esperando revisión. La app no
depende de esos cambios para compilar: no se tocó código Kotlin.
