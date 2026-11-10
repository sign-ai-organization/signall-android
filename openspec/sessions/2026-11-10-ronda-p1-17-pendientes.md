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
- **El libro `resultados-consolidados.xlsx` quedó actualizado** (segunda parte de la sesión):
  se agregaron las 17 filas como tercera ronda, **«Ronda P1 10/11»**, con
  `docs/plan-pruebas/agregar-ronda.py` y los resultados de
  `docs/plan-pruebas/resultados-p1-2026-11-10/`. El libro pasa de 148 a 165 filas y el
  `Resumen` gana una fila en «Por ronda» (17 pruebas: 1 Pasa, 2 Falla, 14 Se reporta).
  Las filas de las dos rondas anteriores no se tocaron.

## Decisiones del owner en esta sesión

- Alcance: las 17 pendientes del carril P1, no solo las 2 fallas.
- Usar el `.env` nuevo de `~/Descargas/envtesis` en vez de correr con el modelo local.
- Cargar las 17 filas al libro y dejar una copia en `~/Descargas/`.

## Cómo se reproduce

Está en `docs/plan-pruebas/RESULTADOS-P1-2026-11-10.md` §8 y en cada script de
`docs/plan-pruebas/arnes/`.
