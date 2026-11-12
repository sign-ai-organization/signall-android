# Entradas 02: 0010–0019

**2026-11-11. Estado:** diez entradas aprobadas, integración pendiente. Entradas 01 aprobadas sin
correcciones, incluidas sus preparaciones añadidas. No se modifican esas copias.

| Seña | Reposo nuevo hasta | Unión al gesto / preparación añadida | Revisión |
|---|---:|---|---|
| `0010` | Inicio de preparación | Añadir 6/24 = 0,250 s, conservar todas las muestras | Aprobada |
| `0011` | 1/24 s | Unión a 7/24 = 0,2917 s | Aprobada |
| `0012` | Inicio de preparación | Añadir 8/24 = 0,3333 s, conservar todas las muestras | Aprobada |
| `0013` | 1/24 s | Unión a 8/24 = 0,3333 s | Aprobada |
| `0014` | 12/24 = 0,500 s | Unión a 18/24 = 0,750 s | Aprobada |
| `0015` | 10/24 = 0,4167 s | Unión a 16/24 = 0,6667 s | Aprobada |
| `0016` | 14/24 = 0,5833 s | Unión a 19/24 = 0,7917 s | Aprobada |
| `0017` | 12/24 = 0,500 s | Unión a 18/24 = 0,750 s | Aprobada |
| `0018` | 14/24 = 0,5833 s | Unión a 19/24 = 0,7917 s | Aprobada |
| `0019` | 1/24 s | Unión a 6/24 = 0,250 s | Aprobada |

El owner respondió **«hermoso todo aprobado TODO, siguientes»**: las diez entradas
aprobadas sin correcciones, incluidos prefijos. No se atribuye revisión desglosada
por avatar/variante ni validación lingüística formal.

Ventanas provisionales tras inspección de primeras muestras de codos de Juan,
afirmativa, no fronteras lingüísticas demostradas. `0010` tiene flexión/movimiento
tempranos y `0012` ya empieza flexionada: se añade preparación sin borrar ningún
fotograma. Todos sus timestamps, incluidos cara/regreso, se desplazan por el
prefijo; duración aumenta 0,250/0,3333 s y no van sincronizadas con la izquierda.

`0011/0013/0019` tienen preparación temprana; `0014`–`0018` tienen reposo más largo
antes de prepararse. En estas cinco se sostiene reposo nuevo hasta el tiempo
indicado para no recorrer lentamente una preparación durante toda la espera.
Después se enlaza directamente con la muestra ancla, slerp/peso quíntico.
El owner debe comprobar que no se eliminan contactos/componentes del gesto.

Las ocho entradas locales solo cambian seis rotaciones; todas las muestras desde
la unión inclusive son exactas al aprobado. Las dos con prefijo conservan todas
sus muestras originales, con tiempos desplazados. Dedos/cara y otros canales de
las ocho locales intactos. Nodos, geometría y otras 160 animaciones por avatar
intactos. No se modifica ninguna ventana ni valor de los regresos aprobados.

## Evidencia y reproducción

Visor temporal: **http://127.0.0.1:8766/entries-02/**. Ambos lados usan regresos
aprobados y acomodo final v3; izquierda entradas anteriores, derecha propuestas.
No se prueba encadenamiento. `0028` y entradas 01 mantienen su aprobación aparte.

Generar `group-03/04/05` como carpetas hermanas con sus `despues.glb`; ejecutar
`prepare.py` y `check.py` desde raíz del repo, servir esta carpeta por HTTP.
Three.js necesita jsDelivr.

- `verification.json`: hashes de app y fuentes aprobadas, uniones, esperas y prefijos.
- `checks.json`: verificación de GLB serializados, 40 clips por avatar, seis
  rotaciones iniciales en reposo, esperas exactas, muestras protegidas/regresos
  exactos y conteos input/output válidos.
- `browser-checks.json`: 80 selecciones/render en Chromium headless (diez señas,
  cuatro variantes, dos avatares). Capturas afirmativas a 0,05 s local; no
  certifican movimiento, rendimiento ni significado LSC.
- Fuentes originales intactas `../signai-avatar`, commit
  `fc6f372c8412010d85925fe342fdcf0c073fbdf1`, según procedencia del empaquetado.

Diez entradas aprobadas, integración pendiente. App/fuentes aprobadas intactas; sin integración,
build ni instalación nuevos por esta prueba externa.
