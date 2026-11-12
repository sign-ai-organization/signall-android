# Entradas 01: 0000–0009

**2026-11-11. Estado:** diez entradas aprobadas, integración pendiente. Comparación de entradas con
regresos aprobados de grupos 01/02/03 combinados y mismo acomodo v3 en ambos lados.
Izquierda: entradas anteriores; derecha: propuestas. Sin integración en la app.

| Seña | Entrada propuesta | Protección de muestras | Revisión |
|---|---|---|---|
| `0000` | Unión a 8/24 = 0,3333 s | Aprobadas desde 0,500 s; ver excepción abajo | Aprobada |
| `0001` | Añadir 9/24 = 0,375 s de preparación | Todas las originales, desplazadas 0,375 s | Aprobada |
| `0002` | Unión a 5/24 = 0,2083 s | Exactas desde unión inclusive | Aprobada |
| `0003` | Unión a 6/24 = 0,250 s | Exactas desde unión inclusive | Aprobada |
| `0004` | Unión a 6/24 = 0,250 s | Exactas desde unión inclusive | Aprobada |
| `0005` | Unión a 6/24 = 0,250 s | Exactas desde unión inclusive | Aprobada |
| `0006` | Añadir 6/24 = 0,250 s de preparación | Todas las originales, desplazadas 0,250 s | Aprobada |
| `0007` | Unión a 6/24 = 0,250 s | Exactas desde unión inclusive | Aprobada |
| `0008` | Unión a 6/24 = 0,250 s | Exactas desde unión inclusive | Aprobada |
| `0009` | Unión a 6/24 = 0,250 s | Exactas desde unión inclusive | Aprobada |

El owner respondió **«hermoso todo aprobado TODO, siguientes»**: diez entradas
aprobadas sin correcciones, incluidas las preparaciones añadidas. No se atribuye
revisión desglosada por avatar/variante ni validación lingüística formal.

Uniones provisionales tras inspección de primeras muestras de codos de Juan,
afirmativa, **no fronteras lingüísticas certificadas**. `0000` prepara más tarde;
`0002` flexiona desde segunda muestra; las preparaciones cortas se unen antes de
sus máximos de flexión. Revisar gesto, contacto y rapidez de la preparación.
No se aplica automáticamente la ventana de `0028`, que sigue como referencia.

## Casos que requieren distinguir datos y duración

`0001` ya empieza con gesto activo y `0006` con flexión/movimiento temprano,
incluido cambio de dirección del codo derecho. Para no borrarlo se añaden muestras
antes del clip: seis rotaciones preparan desde reposo nuevo a su primera postura
original (slerp/peso quíntico); otros canales mantienen su primer valor. Todos los
valores originales se conservan y todos los tiempos se desplazan, incluidos pesos
faciales. **Duran 0,375/0,250 s más y no van sincronizadas con la izquierda.**
Regresos con valores/tiempos relativos exactos, pero ocurren después por el prefijo.

`0000` sustituye la mezcla parcial v1 de su primer 20%: preparación directa hasta
0,3333 s y gesto del asset fuente hasta 0,500 s, en vez de aquella mezcla. Desde
0,500 s todas las muestras son exactas a la copia aprobada anterior. No se afirma
que lo sean desde la unión al compararlas con v1. Regreso íntegro.

Las otras ocho entradas solo cambian seis rotaciones (`upper_arm`, `forearm`,
`hand`, ambos lados), sin cambiar tiempos. Otras 160 animaciones por avatar,
nodos y geometría intactos. En las dos con prefijo, todas las muestras originales
de dedos, cara y otros canales se conservan, con tiempos desplazados.

## Evidencia y reproducción

Visor temporal: **http://127.0.0.1:8766/entries-01/**.
Generar antes `group-01/02/03` como carpetas hermanas, con sus `despues.glb`;
copiar scripts/HTML juntos, ejecutar `prepare.py` y `check.py` desde raíz del repo
y servir por HTTP. Three.js necesita jsDelivr.

- `verification.json`: hashes de app/fuentes aprobadas, uniones, prefijos y
  detalle por clip. Se combinan solo curvas aprobadas, sin recalcular regresos.
- `check.py` / `checks.json`: archivos serializados, 40 clips por avatar;
  seis rotaciones iniciales en reposo, muestras protegidas/regresos exactos,
  conteos input/output válidos y desplazamientos temporales correctos.
- `browser-checks.json`: 80 selecciones/render en Chromium headless (diez señas,
  cuatro variantes, dos avatares). Capturas afirmativas a 0,05 s local (visor 0,75 s).
  No certifican movimiento, rendimiento ni significado LSC.
- Procedencia de originales intactos: `../signai-avatar`, commit
  `fc6f372c8412010d85925fe342fdcf0c073fbdf1`, según inventario de empaquetado.

App y copias aprobadas anteriores intactas. Diez entradas aprobadas, integración pendiente; sin build
ni instalación por esta prueba externa. Encadenamiento todavía no comprobado.
