# Entradas 03: 0020–0029

**2026-11-11. Estado:** nueve entradas nuevas aprobadas y referencia `0028`
ya aprobada. Entradas 02 aprobadas sin correcciones, copias anteriores intactas.

| Seña | Reposo nuevo hasta | Unión al gesto | Revisión |
|---|---:|---:|---|
| `0020` | 10/24 = 0,4167 s | 16/24 = 0,6667 s | Entrada aprobada; conservar corrección v3 aparte |
| `0021` | 2/24 = 0,0833 s | 8/24 = 0,3333 s | Aprobada |
| `0022` | 14/24 = 0,5833 s | 19/24 = 0,7917 s | Aprobada |
| `0023` | 14/24 = 0,5833 s | 19/24 = 0,7917 s | Aprobada |
| `0024` | 16/24 = 0,6667 s | 21/24 = 0,8750 s | Aprobada |
| `0025` | 10/24 = 0,4167 s | 15/24 = 0,6250 s | Aprobada |
| `0026` | 6/24 = 0,2500 s | 15/24 = 0,6250 s | Aprobada |
| `0027` | 14/24 = 0,5833 s | 19/24 = 0,7917 s | Aprobada |
| `0028` | 10/24 = 0,4167 s | 18/24 = 0,7500 s | Aprobada antes; referencia intacta |
| `0029` | 10/24 = 0,4167 s | 17/24 = 0,7083 s | Aprobada |

El owner respondió «perfecto, todo. hermoso», pero señaló aparte una sacudida
corporal de `0020` después del gesto; considera que lo modificado está bien.
Se registran entradas aprobadas sin correcciones y esa incidencia independiente.
Diagnóstico en `../0020-diagnostico/`; no se presume aprobación de ningún arreglo
de la sacudida ni revisión desglosada por variante ni validación LSC formal.

Ventanas provisionales tras inspección de primeras muestras de codos de Juan,
afirmativa, **no límites lingüísticos certificados**. `0021` prepara temprano;
`0020/25/29` tienen espera intermedia; `0022/23/27` comienzan su preparación más
tarde; `0024` aún más tarde. `0026` tiene preparación temprana del codo derecho y
se propone una espera menor y enlace más largo para conservar la unión posterior.
Revisar gesto, contactos y rapidez de preparación por seña.

Nueve propuestas: solo seis rotaciones, reposo nuevo durante la espera y enlace
directo por slerp/peso quíntico hasta la muestra ancla. Desde la unión inclusive,
muestras exactas al aprobado anterior. Otros canales, cara, dedos, nodos,
geometría y tiempos intactos. **Ninguna añade duración en este grupo.**

## Referencia 0028

Se incorporan **todos los canales** de `0028-entry` aprobado, no solo brazos:
esto conserva corrección del salto de primera postura, entrada nueva y regreso.
Sus cuatro clips son idénticos entre izquierda/derecha y no se vuelven a corregir.
Se muestra para comparación con las nueve propuestas, no como nueva pendiente.

## Evidencia y reproducción

Visor temporal: **http://127.0.0.1:8766/entries-03/**. Izquierda: entradas anteriores,
derecha: propuestas; ambos lados con regresos aprobados y acomodo final v3.
No se prueba encadenamiento.

Generar `group-05/06/07` y `0028-entry` como carpetas hermanas con sus `despues.glb`;
ejecutar `prepare.py` y `check.py` desde raíz del repo y servir esta carpeta por
HTTP. Three.js necesita jsDelivr.

- `verification.json`: hashes de app y fuentes aprobadas, uniones, esperas y
  referencia. Los regresos no se recalculan: se combinan curvas aprobadas.
- `checks.json`: GLB serializados, 40 clips por avatar (36 nuevos + cuatro de
  referencia), muestras protegidas/regresos exactos, esperas en reposo y conteos
  input/output válidos. Otras 160 animaciones por avatar idénticas.
- `browser-checks.json`: 80 selecciones/render en Chromium headless (diez señas,
  cuatro variantes, dos avatares). Capturas afirmativas a 0,05 s local; no son
  una validación de fluidez, rendimiento ni significado LSC.
- Fuentes originales intactas `../signai-avatar`, commit
  `fc6f372c8412010d85925fe342fdcf0c073fbdf1`, según inventario del empaquetado.

Entradas aprobadas; el owner eligió conservar v3 de `0020` y continuar. Selección
en `../0020-conjunto-v3/accepted.json`; estos archivos no se sobrescriben.
Integración pendiente. App/fuentes aprobadas intactas; sin integración,
build ni instalación nuevos por esta prueba externa.
