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

- **La copia local está congelada el 2026-11-04 y Overleaf tenía correcciones posteriores**
  que no llegaron al repositorio. Están listadas en `../sign-ai-docs/ESTADO.md`: SDD
  `SignAI`→`SignAll`, `TODO` de `RF-UI-06`, redacciones de AVANZADO y velocidad, §7.3 del
  plan de pruebas y **`PR-PER-06` «Flujo completo por voz», que existía solo en Overleaf**.
  La ficha de PER-06 se conserva en `docs/plan-pruebas/plantilla-resultados.xlsx` de este
  repo, pero no como texto del plan.
- **`SignAI` contra `SignAll`**: los documentos usan `SignAll` (34 en el SRS) y el código y
  los repos usan `SignAI`. **No se unificó**: decide el owner.
- **La instalación de TeX Live** la corre el owner (necesita sudo):
  `sudo pacman -S --needed texlive-langspanish texlive-latexextra texlive-pictures
  texlive-bibtexextra texlive-fontsrecommended texlive-binextra biber`.
- **`../sign-ai-backend/docs/CONTRASTE-BACKENDvsDOCUMENTOS.md` y `docs/sdd-changes.md`**
  siguen citando a Overleaf como fuente. No se tocaron: ese repo no se edita sin que el owner
  lo pida.
- **Los conteos de los borradores del plan de pruebas** no cuadran entre sí (72, 73 y 74 en
  `00-bases/resumen.md` y `secciones/09-casos-de-prueba.md`). Queda anotado, no corregido.
