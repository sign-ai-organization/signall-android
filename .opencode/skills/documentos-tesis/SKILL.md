---
name: documentos-tesis
description: Leer, editar y compilar los documentos de la tesis (SRS, SDD, memoria, plan de pruebas, plan de administración) que viven en ../sign-ai-docs/latex/. Usar cuando haya que consultar un documento, corregir un .tex, compilarlo con make, revisar un cambio contra el código, o cuando el owner pida tocar el SRS o el SDD.
---

# Los documentos de la tesis: locales, editables y compilables

Desde el **2026-11-04** todo vive en `../sign-ai-docs`. Overleaf se retiró y su MCP se quitó
de `~/.config/opencode/opencode.json`: **si algo existe solo allá, no cuenta.**

## Dónde está cada cosa

| Qué | Ruta |
|---|---|
| Documentos formales (el original, lo que se edita) | `../sign-ai-docs/latex/<documento>/main.tex` |
| Documentos: `srs` · `sdd` · `memoria` · `plan-pruebas` · `administracion` | `latex/<documento>/` |
| Borradores en Markdown que alimentan los documentos | `../sign-ai-docs/borradores/` |
| Cambios pendientes y su estado | `../sign-ai-docs/cambios/` |
| Diagramas (.drawio y PNG exportado) | `../sign-ai-docs/diagramas/` |
| Historia | `../sign-ai-docs/versiones-pasadas/` (no se edita) |
| Estado de cada documento y pendientes conocidos | `../sign-ai-docs/ESTADO.md` |

La estructura completa está en `../sign-ai-docs/README.md`.

## Compilar: es parte de editar

```bash
cd ../sign-ai-docs
make                 # los cinco documentos y un resumen con páginas y hora
make srs             # uno solo
make watch DOC=srs   # recompila al guardar
make open DOC=srs    # abre el PDF
```

- **Un cambio no está terminado hasta que compila.** Si falla, `make` imprime las primeras
  líneas del `main.log`; el resto está en `latex/<documento>/main.log` con el número de línea.
- **No se reintenta a ciegas** ni se borran los auxiliares como primer reflejo: se lee el
  error. `make limpiar-aux` es el último recurso.
- **Los PDF y auxiliares no se versionan** (están en `.gitignore`): se regeneran.
- En VS Code, guardar recompila y el PDF queda al lado (`Ctrl+Alt+V` para abrirlo,
  `Ctrl+clic` para saltar del PDF a la línea). Está configurado en `../sign-ai-docs/.vscode/`.

## Escribir

- **Se edita el `.tex` directamente**, con la indentación y el estilo del archivo. Nada de
  reescribir secciones enteras cuando alcanza con cambiar una frase.
- **Una corrección de redacción se hace**; una **divergencia de contenido se pregunta
  primero** (skill `fuentes-de-verdad`). Cuál de los dos está mal —documento o código— lo
  decide el owner.
- **El control de cambios es git**: `git diff` muestra qué se tocó, `git log` cuándo. No hay
  que marcar cambios a mano, pero **sí hay que reportarlos** al owner: documento, qué cambió,
  línea aproximada y el texto exacto.
- **Los IDs (`RF-*`, `RNF-*`, `CU-*`, `PR-*`) son el vínculo entre documentos.** Si se agrega,
  se renombra o se quita uno, hay que buscar dónde más se cita antes de terminar.
- **Los `.drawio` y los borradores de requerimientos los edita el owner**: los cambios grandes
  se proponen.
- **No se commitea ni se pushea `../sign-ai-docs` sin que el owner lo pida** (el repo de la
  app sí se commitea directo a `main`).

## Lo que hay que saber antes de creerle a un documento

- **El nombre del sistema está en discusión**: los documentos usan `SignAll` y el código y los
  repos usan `SignAI`. No unificar por cuenta propia.
- **Los documentos formales son una copia de Overleaf congelada el 2026-11-04**, y Overleaf
  tenía correcciones posteriores que no llegaron al repositorio. `ESTADO.md` lista cuáles:
  antes de «corregir» algo, mirar esa tabla, porque puede ser deuda conocida y no un error
  nuevo.
- **`versiones-pasadas/` no es fuente de nada.**

## Verificar que lo escrito es cierto

Una afirmación del documento que hable del comportamiento del sistema **se contrasta con el
código** (`arquivo:línea`) antes de darla por buena. Un requisito que describe algo que la app
no hace es un hallazgo, no un texto.
