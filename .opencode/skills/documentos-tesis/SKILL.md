---
name: documentos-tesis
description: Leer, editar y compilar los documentos de la tesis (SRS, SDD, memoria, plan de pruebas, plan de administración) que viven en ../signall-docs/latex/. Usar cuando haya que consultar un documento, corregir un .tex, compilarlo con make, revisar un cambio contra el código, o cuando el owner pida tocar el SRS o el SDD.
---

# Los documentos de la tesis: locales, editables y compilables

Desde el **2026-11-04** todo vive en `../signall-docs`. Overleaf se retiró y su MCP se quitó
de `~/.config/opencode/opencode.json`: **si algo existe solo allá, no cuenta.**

## Dónde está cada cosa

| Qué | Ruta |
|---|---|
| Documentos formales (el original, lo que se edita) | `../signall-docs/latex/<documento>/main.tex` |
| Documentos: `srs` · `sdd` · `memoria` · `plan-pruebas` · `administracion` | `latex/<documento>/` |
| Borradores en Markdown que alimentan los documentos | `../signall-docs/borradores/` |
| Cambios pendientes y su estado | `../signall-docs/cambios/` |
| Diagramas (.drawio y PNG exportado) | `../signall-docs/diagramas/` |
| Historia | `../signall-docs/versiones-pasadas/` (no se edita) |
| Estado de cada documento y pendientes conocidos | `../signall-docs/ESTADO.md` |

La estructura completa está en `../signall-docs/README.md`.

## Compilar: es parte de editar

```bash
cd ../signall-docs
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
  `Ctrl+clic` para saltar del PDF a la línea). Está configurado en `../signall-docs/.vscode/`.

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
- **No se commitea ni se pushea `../signall-docs` sin que el owner lo pida** (el repo de la
  app sí se commitea directo a `main`).

## Lo que hay que saber antes de creerle a un documento

- **El nombre del sistema**: en los documentos es **`SignAll`** (decisión del owner del
  2026-11-04) y en el código, los repos y los documentos internos es `SignAll`. Las dos cosas
  son correctas en su lugar: no unificar.
- **Los documentos formales son una copia de Overleaf congelada el 2026-11-04**, y las
  correcciones que se hicieron allá después se volvieron a aplicar el mismo día. `ESTADO.md`
  lista qué se corrigió y qué sigue abierto: conviene mirarlo antes de «arreglar» algo que ya
  está anotado como deuda.
- **`versiones-pasadas/` no es fuente de nada.**

## Verificar que lo escrito es cierto

Una afirmación del documento que hable del comportamiento del sistema **se contrasta con el
código** (`arquivo:línea`) antes de darla por buena. Un requisito que describe algo que la app
no hace es un hallazgo, no un texto.
