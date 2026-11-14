# Plantilla de resultados — Plan de Pruebas

> Las evidencias de las rondas ya ejecutadas **no están en el repositorio**: viven en
> `../signall-docs/evidencias/plan-pruebas/`. El índice y la equivalencia con las rutas viejas
> están en [`INDICE-EVIDENCIAS.md`](INDICE-EVIDENCIAS.md). Los reportes de las rondas,
> en [`../historia/plan-pruebas/`](../historia/plan-pruebas/).

Instrumento para registrar los resultados de las pruebas del plan. Un libro de Excel,
una fila por prueba, con la ficha ya copiada: lo único que se agrega es lo que se
obtuvo al ejecutar.

- **Archivo:** `plantilla-resultados.xlsx`
- **Pruebas:** 74, en los 14 bloques del plan
- **Generado:** 4 de noviembre de 2026, con `openpyxl`

## De dónde sale

| Parte | Origen |
|---|---|
| Las 74 fichas (ID, nombre, tipo, objetivo, trazabilidad, entorno, datos, procedimiento, métrica, criterio y evidencia) | `../signall-docs/latex/plan-pruebas/main.tex`, copiadas literalmente: nadie las retipea. |
| PR-REN-09 «Tasa de procesamiento» | También está ya en el plan formal desde el 2026-11-04. |
| Los cuatro estados y los doce campos de la ficha | Sección 9.1 del plan (tablas `tab:estados` y `tab:ficha`). |

**El plan formal vive en el repositorio desde el 2026-11-04** y tiene **74 pruebas**:
`PR-PER-06`, que existía solo en Overleaf, se recuperó de este libro y se volvió a escribir
en el plan.

## Las hojas

| Hoja | Qué tiene |
|---|---|
| **Portada** | Los cuatro estados de resultado, los doce campos de la ficha y cómo se llena. |
| **Resumen** | Totales por bloque y por ronda, con fórmulas. Se recalcula solo; no se escribe a mano. |
| **STT, SEG, HUE, CAC, LLM, VAL, INT, AND, AVT, REN, RES, SEC, TRA, PER** | Una fila por prueba, en el mismo orden que las secciones 9 y 10 y que el Anexo A. |

Cada hoja de bloque tiene el encabezado congelado, filtros en la fila de encabezado,
texto ajustado y la columna «Estado» con lista desplegable y color.

## Las diecisiete columnas

Las once primeras vienen del plan y **no se tocan**. Las seis últimas se llenan al
ejecutar.

| # | Columna | Qué va | Origen |
|---|---|---|---|
| 1 | **ID** | `PR-BLOQUE-NN` | Ficha |
| 2 | **Nombre** | Título corto de la prueba | Ficha |
| 3 | **Tipo** | Uno de los siete tipos de la sección 6.1 | Ficha |
| 4 | **Objetivo** | Qué se quiere saber con la prueba | Ficha |
| 5 | **Trazabilidad** | Requerimientos y objetivo específico que cubre | Ficha |
| 6 | **Entorno y precondiciones** | Dispositivo, configuración, modo de envío y estado inicial | Ficha |
| 7 | **Datos de entrada** | Conjuntos de la sección 8, citados por su código (`AU-01`, `FR-01`…) | Ficha |
| 8 | **Procedimiento** | Pasos resumidos | Ficha |
| 9 | **Métrica** | Qué se mide y cómo se calcula | Ficha |
| 10 | **Criterio de aceptación** | Cuándo pasa; en las de caracterización dice «se reporta» | Ficha |
| 11 | **Evidencia prevista** | Qué se guarda. Es el campo «Evidencia» de la ficha, renombrado para no confundirlo con lo ejecutado | Ficha |
| 12 | **Estado** | `Pasa`, `Falla`, `Se reporta` o `Bloqueada`. Lista desplegable; la celda cambia de color | Ejecución |
| 13 | **Resultado obtenido** | El número o el texto que salió, sin juzgarlo. El juicio está en «Estado» | Ejecución |
| 14 | **Evidencia ejecutada** | Nombre del archivo guardado, con la convención de abajo | Ejecución |
| 15 | **Fecha** | Día de la ejecución, `dd/mm/aaaa` | Ejecución |
| 16 | **Ejecutor** | Quién corrió la prueba | Ejecución |
| 17 | **Ronda** | `Ronda diagnóstica` o `Ronda de evaluación` (sección 6.2). Lista desplegable | Ejecución |

La columna 17 no está en la ficha: existe porque el resumen pide totales **por ronda** y
sin ella no hay forma de calcularlos.

## Los cuatro estados

Sección 9.1 del plan. Una prueba **bloqueada** no es una prueba fallida.

| Estado | Significado | Color |
|---|---|---|
| **Pasa** | Cumplió el criterio de aceptación. | Verde |
| **Falla** | No cumplió el criterio de aceptación. | Rojo |
| **Se reporta** | Es una prueba de caracterización: el resultado se registra sin juzgarlo. | Ámbar |
| **Bloqueada** | No se pudo ejecutar porque falta algo de lo que depende, como una funcionalidad que todavía no existe. | Gris |

Una fila sin estado es una prueba **sin ejecutar**. Eso es distinto de «Bloqueada», que se
elige cuando se intentó y no se pudo.

## Cómo se nombran las evidencias

```
PR-<BLOQUE>-<NN>_<R>_<AAAAMMDD>_<nn>.<ext>
```

| Parte | Valor |
|---|---|
| `<BLOQUE>` | Código de tres letras: `STT`, `SEG`, `HUE`, `CAC`, `LLM`, `VAL`, `INT`, `AND`, `AVT`, `REN`, `RES`, `SEC`, `TRA`, `PER` |
| `<NN>` | Número de la prueba, dos dígitos |
| `<R>` | `D` si es la ronda diagnóstica, `E` si es la de evaluación |
| `<AAAAMMDD>` | Fecha de la ejecución |
| `<nn>` | Consecutivo `01`–`99`, para las varias piezas de una misma prueba el mismo día |
| `<ext>` | `txt` registro crudo · `csv` tabla de resultados · `json` salida de `/metrics.json` · `png` captura · `mp4` grabación · `pdf` formulario o cuestionario |

Ejemplos:

```
PR-REN-02_E_20261110_01.json      salida de /metrics.json de la corrida
PR-STT-01_D_20261108_01.txt       adb logcat -s SignAllMetrics, crudo
PR-AVT-02_E_20261112_03.mp4       video de las animaciones
PR-PER-05_E_20261115_01.pdf       cuestionario SUS respondido
```

Reglas:

1. Una carpeta por bloque, `03-resultados/evidencias/<BLOQUE>/`, en el mismo orden del
   Anexo A del plan.
2. Nunca se sobrescribe un archivo: si hay una segunda corrida el mismo día, sube el
   consecutivo.
3. El `.txt` de `adb logcat` se guarda **crudo**, con la línea de resumen
   (`SignAiMetrics.snapshot()`) al final, tal como salió.
4. En «Evidencia ejecutada» van los nombres separados por coma, sin la ruta.

## Cómo se llena

1. **Una copia del libro por ronda.** El campo «Ronda» dice a cuál pertenece cada fila.
2. Se completan las columnas 12 a 17. Nada más.
3. La hoja «Resumen» no se toca: cuenta lo que haya en las hojas de bloque.
4. Si una prueba no se pudo correr, se marca **Bloqueada** y se escribe en «Resultado
   obtenido» qué faltó. No se deja vacía.
5. Antes de cerrar una ronda se revisa que el Resumen no tenga pruebas sin ejecutar sin
   justificar.

## Lo que falta

- **Nada del contenido:** el plan formal tiene 74 pruebas y este libro, 74 filas. `PR-REN-09`
  y `PR-PER-06` ya están en `../signall-docs/latex/plan-pruebas/main.tex`.
- **Los conteos de los `.md`** se unificaron el 2026-11-04 en `00-bases/resumen.md`,
  `secciones/09-casos-de-prueba.md` y `02-datos/catalogo.md`.
- **El plan todavía no se compiló en esta máquina**, porque falta instalar TeX Live. El primer
  `make plan-pruebas` lo verifica.

## Regenerar

El libro se genera con `generar-plantilla.py`, que lee
`../signall-docs/latex/plan-pruebas/main.tex` y la ficha de PR-REN-09 del borrador. Sin
argumentos usa las rutas del repositorio:

```bash
python3 generar-plantilla.py
# o, con el plan y el borrador en otro lado:
python3 generar-plantilla.py <main.tex> <rendimiento/pruebas.md>
```

Requiere `openpyxl`. Si cambia una ficha en el plan, se vuelve a correr y se regenera el
libro; los resultados ya cargados no se conservan, así que se regenera antes de empezar
una ronda, no en el medio.
