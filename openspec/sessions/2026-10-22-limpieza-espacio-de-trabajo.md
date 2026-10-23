# Limpieza del espacio de trabajo: duplicados, restos del pipeline muerto y `docs/`

**Fecha:** 2026-10-22, noche · **Rama:** `main`

## Qué llegó

El owner pidió **organizar el repo** —carpetas, archivos y documentos— y dejar limpio el
espacio de trabajo, borrando o moviendo lo que ya estuviera en otro lado. Antes de mirar
nada se pulleó `../sign-ai-docs` (30 archivos nuevos, sobre todo `plan-pruebas/01-diseno/`).

## Qué se hizo

### Duplicados reales (borrados)

- **`Icono-SingALL.png` y `Logo-SingALL.png` de la raíz**: mismo md5 que
  `docs/marca/icono-signall.png` y `docs/marca/logo-signall.png`
  (`6ca7544fb39876feb5d629b53f41b525` y `5835415cfab4ba1356d558106d6af10a`). No eran
  basura: `tools/generar-marca.py` los leía desde la raíz. Se dejó **una sola copia** en
  `docs/marca/` y se repuntaron `MASTER_ICON`/`MASTER_LOGO` (`tools/generar-marca.py:46`)
  más el comentario de `ui/SignAiBranding.kt` y la línea de `tools/README.md`.
- **`android-integration.md` de la raíz** (352 líneas, del 2026-10-13): copia vieja del
  contrato. La fuente de verdad es `../sign-ai-backend/docs/android-integration.md`
  (423 líneas, del 2026-10-21) y todas las referencias vivas ya apuntaban allá. Se corrigió
  la única que apuntaba al archivo pelado (`.env.example:13`) y se actualizó el número de
  líneas en `docs/SISTEMA.md:39` y en el skill `contrato-websocket`, que decían 461.

### Movidos

- `Revisión de develop.md` (raíz) → **`docs/REVISION-DEVELOP.md`**. Se actualizó la
  referencia en `openspec/sessions/2026-10-15-flujo-oraciones-android.md:172`.
- `data/LSC50.pdf` (1,8 MB) → **`docs/lsc50-dataset.pdf`**. `data/` desapareció como
  carpeta.

### Restos del pipeline de landmarks (borrados)

El motor procedural de landmarks se abandonó el 2026-10-13 y sus herramientas seguían en
el repo. Se borraron, porque **ningún archivo las importa ni las corre**:

- `tools/build_signs.py`, `tools/patch_webview_glue.py`, `tools/splice_movement.py`.
- `data/LANDMARKS/` (4 CSVs de muestra, 4,7 MB).
- `tools/backup/` (`avatar-rigify-918huesos.glb` + `sign-rigify.json`, 1,4 MB, cero
  referencias). Era el pendiente **#5** de la sesión `2026-10-17-stack-de-ia-del-repo.md`.

Actualizados: `tools/README.md` (§ «Pipeline viejo»), `openspec/config.yaml:155-160`,
`openspec/specs/pipeline/spec.md` y `openspec/specs/dataset/spec.md` (decían que los CSVs
estaban en `data/LANDMARKS/`), `docs/SISTEMA.md` (§ Carpetas hermanas) y el comentario de
`.gitignore` que todavía mandaba a `split_signs.py` como pipeline vigente (hoy es
`pack_signall.py`).

### Artefactos de disco (no versionados)

- `PrototipoFrontend/node_modules` (540 MB) y `.next` (55 MB), regenerables con
  `pnpm install`.
- `tools/__pycache__/` (296 KB).

El repo pasó de ~2 GB a 1,4 GB en disco. Lo versionado bajó 8 MB.

## Qué se decidió

- **Quién decide qué se borra.** Los duplicados, los movimientos y los artefactos de disco
  los resolvió la sesión; los restos del pipeline de landmarks los eligió el owner en una
  pregunta con opciones (borrar los tres scripts + `data/LANDMARKS`, borrar
  `tools/backup/`, y mover el PDF a `docs/`). Todo estaba en git, así que es recuperable.
- **`docs/` sigue siendo plano.** `CAMBIOS.md`, `CONTRASTE-APPvsDOCUMENTOS.md`,
  `PLAN-MEDICION.md`, `MODO-EN-VIVO.md`, `SISTEMA.md` y el manual de marca son documentos
  de trabajo vivos y están referenciados desde `AGENTS.md`, los skills y los agentes;
  subcarpetearlos costaba más enlaces rotos que claridad ganada.
- **`PrototipoFrontend/` se queda en la raíz.** Son 23 archivos versionados y lo referencian
  siete archivos de Kotlin (los tokens de `ui/designsystem/foundation/` y `Color.kt`) más
  el spec de UI y `config.yaml`. Borrarle `node_modules` ya resuelve los 595 MB.
- **No se borró ningún documento por «estar duplicado en `../sign-ai-docs`»**: se comparó
  archivo por archivo y no hay ninguno. El único duplicado cruzado era el del backend.

## Qué se midió

Nada de comportamiento: no cambió ni una línea ejecutable. Solo un comentario de Kotlin.
Se verificó igual, como pide `AGENTS.md`: `./gradlew build` sin errores e `installDebug` en
el Redmi Note 9S (`1ed6699f`, `lastUpdateTime=2026-10-22 21:28:50`) con `adb reverse
tcp:8080` y `-Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate`.

## Qué quedó pendiente

- **Dos huérfanos del mismo pipeline**: `tools/pose_filter.py` y `tools/hand_corrector.py`
  los importaba únicamente `build_signs.py` y leían los CSVs que se borraron. Quedan
  porque el owner eligió borrar tres scripts y no cinco; **decisión abierta**: borrarlos
  también o dejarlos como herramienta de dataset.
- **`docs/CAMBIOS.md` §7 (`:167`, `:186`) sigue contradiciendo la decisión D-5** (dice que
  el adelanto automático «se queda», y D-5 lo eliminó). Es el pendiente #4 de
  `2026-10-17-stack-de-ia-del-repo.md` y no se tocó acá: es contenido, no limpieza.
- **`openspec/specs/pipeline/spec.md` sigue describiendo el pipeline viejo** más allá de
  las dos líneas corregidas. Arreglarlo es reescribir el spec, no mover archivos.
- **`../sign-ai-docs` no tiene README** (pendiente #7, del otro repo). No se tocó: en los
  hermanos no se commitea sin que lo pida el owner.
