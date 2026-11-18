# 2026-11-18 — reparto y método de la revisión final de los documentos

**Repositorios:** `signall-docs` (es donde se trabaja), `signall-android` (esta bitácora)
**Cambio cruzado:** no toca el contrato entre repos.
**Esta sesión no editó ningún documento ni creó ramas.** Solo se definió el método.

## Inventario al empezar

Los seis documentos formales viven en `../signall-docs/latex/` y compilan con `make`. Páginas
medidas con `pdfinfo` sobre los PDF que están en disco el 2026-11-18; ojo, el PDF del SRS es del
2026-11-10 y el de la memoria del 2026-11-13, así que pueden estar atrasados respecto del `.tex`.

| Documento | Carpeta | Páginas |
|---|---|---|
| SRS | `latex/srs/` | 124 |
| SDD | `latex/sdd/` | 48 |
| Memoria de grado | `latex/memoria/` | 79 |
| Plan de administración (SPMP) | `latex/administracion/` | 43 |
| Plan de pruebas | `latex/plan-pruebas/` | 104 |
| VFP (propuesta de trabajo de grado) | `latex/vfp/` | 35 |

Dos cosas que el inventario dejó a la vista:

- **El formato de biblioteca no existe** en el repositorio: se buscó `*.bib`, `*biblio*` y
  `*biblioteca*` y solo hay `latex/srs/referencias.bib` y `latex/memoria/referencias.bib`, que no
  son ese entregable.
- El SRS tiene la sección 4 todavía en Markdown (`latex/srs/proceso-ingenieria-requerimientos.md`),
  fuera de la compilación.

## Reparto (lo decidió el owner)

Un solo dueño por documento: el que lo tiene es el único que lo edita.

| Quién | Documento(s) | Páginas | Por qué |
|---|---|---|---|
| Julián | Memoria + VFP | 79 + 35 | La memoria era fija. La VFP va con ella porque cuentan la misma historia en dos momentos y separadas se contradicen. |
| Sergio | SRS | 124 | Viene de backend, STT y documentación. |
| Samuel | Plan de pruebas | 104 | Hizo las pruebas de la app y conoce el avatar, que es lo que se mide. |
| Diego | SDD + Plan de administración | 48 + 43 | El SDD es el diseño y la parte de la app solo la puede verificar contra el código; la administración es la más corta y no pide backend. |

Reparto de trabajo real de cada uno, para cuando haya que preguntar algo: Diego, la app Android;
Samuel, el avatar y las pruebas de la app; Sergio y Julián, documentación, backend y STT.

## Método de trabajo

1. Cada uno abre una rama **`revision-<nombre>`** (`revision-diego`, `revision-julian`,
   `revision-sergio`, `revision-samuel`) **derivada de `main`** en `signall-docs`.
2. Ahí hace su lectura y sus cambios, solo sobre sus documentos.
3. **Si un cambio obliga a tocar otro repositorio**, la misma rama se propaga a ese repositorio,
   también derivada de `main`. Mismo nombre, misma lógica.
4. Al final, cuando los cuatro terminen, se integra.

## Qué se decidió

- **Un dueño por documento y sin cruces.** Un documento no lo trabajan dos personas.
- **La memoria es toda de Julián**, sin excepción.
- **El reparto se hizo por trabajo y por tamaño**, no por gusto: quedó en 114, 124, 104 y 91
  páginas.
- **Las ramas nacen de `main`**, siempre, también las de los repos hermanos.
- **Esta sesión no arranca la revisión.** Solo deja el método escrito.

## Qué quedó pendiente

- **Confirmar si el formato de biblioteca entra en la entrega.** Si entra, hay que conseguirlo: no
  está en ningún repositorio. Propuesto para Sergio, que ya maneja la bibliografía del SRS.
- **Decidir si hay segundo lector** por documento. Recomendado sí, leyendo y reportando sin
  editar; si no, cada uno revisa lo suyo y nadie ve el conjunto.
- **Precisar el destino de la integración final.** El owner lo dijo como «todo va a quedar en mi
  rama»; falta decir si el destino es `main` o una rama de consolidación.
- **Empezar por el plan de administración** (Diego, el más corto) para calibrar cómo se revisa
  antes de meterse en el SRS.
- El SDD incluye backend y STT: Diego lo revisa completo, pero lo del backend se le pregunta a
  Sergio, que no lo edita.
- Plan de pruebas y plan de administración se tocan en las rondas: Samuel y Diego tienen que
  hablarlo, no reescribirlo cada uno por su lado.

## Nota de método de la revisión

La idea acordada es que cada uno lea su documento mientras un agente hace el contraste simultáneo
contra el código y contra los otros documentos, y devuelva hallazgos por dueño. **Nadie edita un
documento sin que su dueño lo decida.**
