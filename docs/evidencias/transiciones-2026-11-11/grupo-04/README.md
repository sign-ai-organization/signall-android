# Grupo 04: 0013–0017

**2026-11-11. Estado:** cinco propuestas aprobadas visualmente, integración pendiente. Grupo 03 aprobado
por el owner, sin correcciones; no se modifican las propuestas anteriores.

| Seña | Inicio de regreso | Llegada al reposo | Revisión | Integración |
|---|---:|---:|---|---|
| `0013` | 40/24 = 1,6667 s | 51/24 = 2,125 s | Aprobada | Pendiente |
| `0014` | 35/24 = 1,4583 s | 48/24 = 2,000 s | Aprobada | Pendiente |
| `0015` | 35/24 = 1,4583 s | 49/24 = 2,0417 s | Aprobada | Pendiente |
| `0016` | 44/24 = 1,8333 s | 51/24 = 2,125 s | Aprobada, incluida revisión especial | Pendiente |
| `0017` | 44/24 = 1,8333 s | 51/24 = 2,125 s | Aprobada | Pendiente |

El owner respondió **«hermoso todo aprobado TODO, siguientes»** el 2026-11-11:
las cinco, incluida `0016`, quedan aprobadas sin correcciones. No especificó
revisión desglosada por avatar/variante ni se atribuye validación LSC formal.

Ventanas provisionales, elegidas tras inspección de curvas de antebrazos de Juan,
variante afirmativa, **no fronteras lingüísticas demostradas**:

- `0013`: el codo izquierdo sigue aumentando su flexión hasta aproximadamente
  1,542 s y desciende después de la zona de 1,667 s; ancla tardía.
- `0014` y `0015`: se conserva la zona de máxima flexión y postura sostenida
  hasta 1,417 s; se propone regresar a partir de la siguiente muestra.
- `0016`: conserva flexión alta hasta 1,792 s y **no llega cerca del reposo al
  acabar el original**. Se mantiene el movimiento hasta 1,8333 s y se propone
  completar el regreso. No se afirma que el original esté incorrecto o incompleto.
  Esta propuesta requiere especial revisión: no basta con extrapolar los casos
  anteriores. La cola v3 ralentiza buena parte de este regreso tardío.
- `0017`: ambos codos permanecen flexionados hasta la zona de 1,792 s; se propone
  un regreso tardío también, conservando íntegra esa parte del gesto.

Solo se sustituyen seis rotaciones por clip (`upper_arm`, `forearm`, `hand`,
ambos lados), mediante slerp con peso quíntico hacia el reposo aprobado. Todas las
entradas y muestras hasta el ancla permanecen exactas; resto de canales, cara,
dedos, nodos, geometría y tiempos intactos. Las otras 180 animaciones por avatar
no cambian. Son copias externas: ni app ni fuentes originales se editan.

Visor: actual a la izquierda, propuesta a la derecha con acomodo final v3 de
700 ms. Ese acomodo es del visor, no parte de los GLB. No prueba encadenamiento.

## Comprobaciones y reproducción

- `verification.json`: hashes de entrada y ventanas, ambos avatares.
- `check.py` / `checks.json`: 120 canales por avatar; normalización, entrada y
  muestras protegidas exactas, regreso angular monótono y reposo exacto final.
- `browser-checks.json`: 40 selecciones/render en Chromium headless (cinco señas,
  cuatro variantes, dos avatares). Capturas afirmativas a tiempo local 1,75 s
  (visor 2,45 s). No son una revisión del movimiento ni validación LSC.
- `browser_check.py` conserva el comprobador CDP reutilizable; se ejecuta con
  carpeta del grupo y puerto de Chromium, ya abierto en el visor con depuración.
- Fuentes originales intactas: `../signai-avatar`, commit
  `fc6f372c8412010d85925fe342fdcf0c073fbdf1`, según procedencia del empaquetado.

Visor temporal: **http://127.0.0.1:8766/group-04/**.
Para reproducir aparte: copiar scripts y HTML juntos; ejecutar `prepare.py` y
`check.py` desde la raíz del repo, servir la carpeta por HTTP. El enlace al grupo
anterior necesita esa carpeta hermana. Three.js requiere jsDelivr.

No hubo build/instalación nuevos por esta prueba externa. Pendientes revisión
en el controlador real e integración de las cinco propuestas ya aprobadas.
