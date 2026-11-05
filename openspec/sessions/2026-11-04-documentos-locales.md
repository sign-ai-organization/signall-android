# Los documentos de la tesis pasan a vivir en el repositorio

**Fecha:** 2026-11-04, noche · **Rama:** `main`

## Qué llegó

Decisión del owner: **Overleaf dejó de ser fuente confiable**. «Ya no sirve de nada, ya no es
una fuente confiable de la información; ahora todo se hace de forma local.» Los cinco
documentos formales —SRS, SDD, memoria, plan de pruebas y plan de administración— pasan a
vivir en `../sign-ai-docs`, se editan ahí y se compilan ahí. Pedido concreto: revisar y pulir
el repo de documentos, instalar LaTeX en la máquina, arreglar `AGENTS.md`, apagar el MCP de
Overleaf y dejar una forma fácil de ver los documentos mientras se editan.

## Qué se hizo

**`../sign-ai-docs` (el original, ya no una copia):**

- `Makefile` nuevo: `make` compila los cinco, `make <documento>` uno, `make watch DOC=<doc>`
  recompila al guardar, `make open DOC=<doc>` abre el PDF, `make clean` / `make limpiar-aux`
  borran auxiliares. `make verificar` imprime páginas y hora de cada PDF.
- `.gitignore` nuevo con los auxiliares de LaTeX y `main.pdf`: **el PDF y los auxiliares salen
  del índice de git** (49 archivos + los 3 PDF), siguen en disco y se regeneran. Sin eso,
  cada compilación ensuciaba el `git status`.
- `.vscode/settings.json` y `.vscode/extensions.json`: LaTeX Workshop con receta `latexmk`,
  `autoBuild.run: onSave`, visor en pestaña, SyncTeX y limpieza automática. El PDF queda al
  lado del `.tex` y se recompila con `Ctrl+S`.
- `README.md` y `CLAUDE.md` reescritos: `latex/` es **el original** y se edita; ya no hay
  `Menu → Download → Source` ni «no se edita un `.tex` en el repositorio».
- `ESTADO.md` reescrito con lo que hay de verdad: los cinco documentos están en el repo
  (antes decía que administración y plan de pruebas no), páginas, última modificación y una
  tabla de **deuda conocida**.
- Limpieza: fuera un PNG duplicado de una descarga (`sdd-6.1-vista-logica-componentes.drawio
  (1).png`, sin uso) y dos `.gitkeep` en carpetas que ya tienen contenido.

**`sign-ai-android` (este repo):**

- `AGENTS.md`: «Las tres fuentes» → **«Las dos fuentes»** (código y `../sign-ai-docs`). Fuera
  toda la sección de Overleaf —lectura libre, escritura con control de cambios, `policies`,
  cookie, IDs de proyecto— reemplazada por «Los documentos de la tesis: locales y
  compilables»: cómo se compila, que el control de cambios es `git diff`, y cómo se miran.
- `docs/SISTEMA.md`: la sección de `sign-ai-docs` describe la estructura real (`latex/`,
  `borradores/`, `cambios/`, `diagramas/`), cómo se compila y los dos puntos que confunden
  (el nombre `SignAI`/`SignAll` y la copia congelada). Fuera la tabla de IDs de Overleaf.
- Skills: `overleaf-lectura` → **`documentos-tesis`** (dónde viven, cómo se editan, cómo se
  compilan, qué se reporta); `tres-fuentes-de-verdad` → **`fuentes-de-verdad`** (dos fuentes,
  qué hacer cuando dos documentos se contradicen).
- Agentes `@contraste` y `@pipeline`: sin Overleaf; apuntan a `latex/srs/main.tex` y al skill
  nuevo. `openspec/config.yaml` ídem.
- `docs/plan-pruebas/README.md` y `generar-plantilla.py`: el plan formal es
  `../sign-ai-docs/latex/plan-pruebas/main.tex`, no Overleaf.
- `openspec/sessions/README.md`: nota de cabecera que marca las sesiones viejas que hablan
  de Overleaf como historia.

**Configuración global:**

- `~/.config/opencode/opencode.json`: se quitó el servidor MCP `overleaf` y las cinco
  `policies` que bloqueaban aceptar/rechazar cambios. Respaldo en
  `opencode.json.bak-2026-11-04-pre-quitar-overleaf`. Quedan `dart-mcp`, `marionette` y
  `mobile-mcp`.

## Qué se decidió

- **El owner retiró Overleaf** (2026-11-04). No es una fuente más atenuada: **si algo existe
  solo allá, no cuenta como entregado**. Por eso las dos fuentes, no tres.
- **Los documentos se editan en `latex/`** y un cambio no está terminado hasta que compila.
- **El control de cambios es git.** `git diff` y `git log` reemplazan el `track changes` de
  Overleaf; lo que se toca se reporta igual.
- **El PDF no se versiona**: se regenera con `make`. Si el owner prefiere que quede en el
  repo para leerlo desde GitHub, es una línea de `.gitignore`.

## Qué se midió

- `latex/plan-pruebas/main.tex`: **72** IDs `PR-*` distintos. Los borradores tienen **73**
  (con `PR-REN-09`), y el libro `plantilla-resultados.xlsx` tiene 74 filas.
- `latex/sdd/main.tex`: **9** apariciones de `SignAI`, **0** de `SignAll`.
- `latex/srs/main.tex`: **34** `SignAll` y **3** `SignAI`; `srs/main.tex:2047` todavía tiene el
  `TODO` de `RF-UI-06`, que la app ya cumple desde el 2026-11-02.
- Imágenes: `latex/memoria/img/` tiene **41 archivos** y solo **4** se usan en las secciones
  (6,2 MB heredados de otra plantilla). No se borraron.

## Qué quedó pendiente

- **La copia local estaba congelada el 2026-11-04 y Overleaf tenía correcciones posteriores**
  que no llegaron al repositorio: SDD `SignAI`→`SignAll`, `TODO` de `RF-UI-06`, redacciones de
  AVANZADO y velocidad, §7.3 del plan de pruebas y **`PR-PER-06` «Flujo completo por voz», que
  existía solo en Overleaf**. **Resuelto en la segunda parte de esta sesión.**
- **`SignAI` contra `SignAll`**: los documentos usaban `SignAll` (34 en el SRS) y el código y
  los repos `SignAI`. **El owner decidió `SignAll` para los documentos** en la segunda parte.
- **La instalación de TeX Live** la corre el owner (necesita sudo):
  `sudo pacman -S --needed texlive-langspanish texlive-latexextra texlive-pictures
  texlive-bibtexextra texlive-fontsrecommended texlive-binextra biber`.
- **`../sign-ai-backend/docs/CONTRASTE-BACKENDvsDOCUMENTOS.md` y `docs/sdd-changes.md`**
  siguen citando a Overleaf como fuente. No se tocaron: ese repo no se edita sin que el owner
  lo pida.
- **Los conteos de los borradores del plan de pruebas** no cuadraban entre sí (72, 73 y 74 en
  `00-bases/resumen.md` y `secciones/09-casos-de-prueba.md`). **Resuelto en la segunda parte
  de esta sesión.**

## Segunda parte: las correcciones que se habían hecho en Overleaf

A pedido del owner, se aplicaron todas las correcciones que se habían hecho en Overleaf y no
habían llegado al repositorio, más las que dependían de decisiones abiertas.

**Decisión del owner:** en los documentos el sistema se llama **`SignAll`**.

- **SRS** (`latex/srs/main.tex`): las 3 `SignAI` que quedaban pasaron a `SignAll` (ahora 37);
  se quitó el `TODO` de `RF-UI-06` (la app ya impide cambiar de modo mientras graba desde el
  2026-11-02); `RF-AVT-08` y la tabla de Ajustes describen la velocidad como **botones −/+ con
  el valor visible, paso 10 y rango 10–100**; y el bloque **Avanzado** pasa a ser de
  desarrollo —solo existe si la compilación trae las herramientas activadas y no va en la
  versión final—, sin el «modo de depuración».
- **SDD** (`latex/sdd/main.tex`): las 9 `SignAI` pasaron a `SignAll`.
- **Plan de pruebas** (`latex/plan-pruebas/main.tex`):
  - Se reescribió la **sección 7.3** sin el `TODO` rojo, con una tabla de instrumentación y
    los nombres verificados contra el código: backend en el 8080 (`/metrics` y
    `/metrics.json`, sin autenticación) con `rt_first_sign_ms`, `rt_phrase_total_ms`,
    `rt_sentence_delay_ms`, los contadores `rt_*` y las sesiones; la traza por frase con
    `DEBUG_TRACE_ENABLED` y los frames `trace` y `sentence_trace`; el STT en el 8000 con sus
    `stt_*`; el celular con `SignAiMetrics`, `SignAiNet` y `SignAIWeb` (este último solo con
    `-Psignai.devTools=true`); y las herramientas concretas de seguridad y resiliencia.
  - Entró **`PR-PER-06` «Flujo completo por voz»**, recuperada de
    `docs/plan-pruebas/plantilla-resultados.xlsx` (la única copia que había quedado), con su
    ficha, su fila en la tabla del bloque, la trazabilidad de RF-STT-01, RF-STT-02, RF-NLP-01
    y RF-AVT-02, y su formato de registro.
  - Entró **`PR-REN-09` «Tasa de procesamiento»** desde el borrador, con la fila del bloque y
    `RNF-DES-09` en el inventario de requerimientos.
  - El plan pasa de **72 a 74 pruebas** y de **85 a 86 requerimientos** (75 por prueba y 11
    por inspección, contados sobre la tabla del inventario).
- **Borradores** (`borradores/plan-pruebas/`): `resumen.md` quedó con 74 pruebas, 35
  obligatorias, 28 recomendadas y 11 complementarias, y la ficha de PER-06 se agregó a
  `01-diseno/personas/pruebas.md`; se corrigieron los conteos de `secciones/09-casos-de-prueba.md`
  y de `02-datos/catalogo.md`.
- **`cambios/requerimientos.md`**: cuatro filas nuevas en la sección 9 con la fecha, el
  requisito y de dónde sale cada cambio.

**No se pudo compilar**: TeX Live está incompleto en la máquina (faltan babel-español, biber,
latexmk, tikz y biblatex) y la instalación necesita `sudo`, que lo corre el owner. La
verificación estructural que sí se hizo: los entornos `\begin`/`\end` y las llaves quedaron
balanceados en los tres `.tex`, y el plan tiene 74 fichas y 74 IDs distintos.

**Lo que queda**: correr `make` cuando TeX esté instalado y revisar los PDF.

## Tercera parte: la copia local estaba 20 commits atrás

Al revisar el estado de git, `../sign-ai-docs` estaba **3 adelante y 20 detrás** de
`origin/main`. El equipo había seguido trabajando en `develop` y mergeado por PR (#6, #7 y
#8): SRS con las secciones 4 y 5 (+584 líneas), la memoria reescrita, el SPMP revisado, los
formularios del plan de pruebas y el `.gitignore` de auxiliares. La copia local era de antes
de todo eso, así que **nuestras correcciones se habían hecho sobre una base vieja**.

Lo que ya estaba resuelto del otro lado y no hacía falta repetir:

| Qué | Dónde estaba ya |
|---|---|
| Los auxiliares de LaTeX y los PDF fuera del índice, con `.gitignore` | commit `c024b32` del equipo |
| El `TODO` de `RF-UI-06` del SRS | commit `18178cd`, alineación del 2/11 |
| El nombre `SignAll` en el SRS (39 apariciones) | commit `6a12042` |

**Lo que se hizo:** `git merge origin/main` con dos ramas de respaldo
(`respaldo/local-2026-11-04-pre-merge` y `respaldo/main-2026-11-04-pre-merge`, más las
etiquetas `respaldo-local-2026-11-04` y `respaldo-remoto-2026-11-04`). Siete conflictos, todos
resueltos conservando lo de las dos partes:

| Archivo | Cómo quedó |
|---|---|
| `.gitignore` | La unión: el nuestro ya traía `main.pdf` y `*.xdv` |
| `ESTADO.md` | El reescrito, actualizado con lo que trajo el merge |
| `latex/memoria/sections/disenoSolucion.tex` | La versión del equipo, que ya usa `SignAll` |
| `latex/plan-pruebas/main.tex` y su borrador de la sección 9 | La fila de `PR-PER-04` del equipo («facilidad para entender») + nuestra fila de `PR-PER-06` |
| `cambios/requerimientos.md` | Nuestras reescrituras (RF-CAT-03, tabla de pendientes, TODO de `RF-UI-06` cerrado) con la **terminología del SRS** (`modo dictado` / `modo TR`), no la de «manual / en vivo» |
| `latex/administracion/main.tex` | La versión del equipo; se le volvieron a cambiar las 3 `SignAI` nuevas a `SignAll` |
| `main.pdf` y `main.synctex.gz` que volvieron con el merge | Fuera del índice otra vez (están en `.gitignore`) |

**Estado verificado después del merge**: 74 fichas y 74 IDs en el plan; 86 requerimientos,
75 por prueba y 11 por inspección; `SignAI` ya no aparece en ningún `.tex` (salvo el tag de
logcat `SignAIWeb`, que se queda); entornos y llaves balanceados en los cinco `.tex`; el SRS
conserva los botones −/+ y Avanzado como herramienta de desarrollo.

**Commits**: `89ff970` (merge) sobre `c86811d` y `8526c30`. **Sin push.** El repo de
documentos trabaja con `develop` y PR contra `main`, así que hay que decidir si esto va
directo a `main` o por una rama con PR.

**Hallazgo que queda abierto**: `cambios/requerimientos.md` venía renombrando los modos a
«manual» y «en vivo» (sesión del 2026-11-03), pero el SRS dice **«modo dictado»** y **«modo
TR»** en todo el texto (39 y 35 apariciones). Se volvió a la terminología del SRS y la
decisión de renombrar en el SRS queda para el owner.

## Cuarta parte: TeX instalado y los cinco documentos compilan

El owner instaló TeX Live (2026-11-05) y `make` compila los cinco sin errores:

| Documento | Páginas |
|---|---|
| SRS | 126 |
| SDD | 22 |
| Memoria | 58 |
| Plan de pruebas | 144 |
| Plan de administración | 43 |

Faltaba un paquete que no estaba en la lista original: **`texlive-publishers`**, que trae
`IEEEtran.bst`. Sin él el SRS compilaba pero se quedaba sin bibliografía y con dos tablas sin
resolver; con él, el `.bbl` sale con las 28 entradas y cero referencias indefinidas. El
comando de instalación del `README.md` ya lo incluye.

El `make` del repo también sirve de verificación: falla con el primer documento que no
compila e imprime las primeras líneas del `main.log`.

**Queda abierto**: la terminología de los modos no coincide entre documentos. El SRS dice
«modo dictado» (39 veces) y «modo TR» (35); el plan de pruebas dice «modo manual» y «modo en
vivo» (14). Es una contradicción entre dos documentos, así que la decide el owner.
