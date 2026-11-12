# Grupo 05: 0018–0022

**2026-11-11. Estado:** cinco propuestas aprobadas visualmente, integración pendiente. Grupo 04 aprobado íntegro,
incluida `0016`; no se modifican sus ventanas.

| Seña | Ancla del regreso (tiempo de clip) | Llegada al reposo | Revisión | Integración |
|---|---:|---:|---|---|
| `0018` | 47/24 = 1,9583 s | 51/24 = 2,125 s | Aprobada, incluida revisión especial | Pendiente |
| `0019` | 46/24 = 1,9167 s | 51/24 = 2,125 s | Aprobada | Pendiente |
| `0020` | 47/24 = 1,9583 s | 56/24 = 2,3333 s | Aprobada | Pendiente |
| `0021` | 38/24 = 1,5833 s | 49/24 = 2,0417 s | Aprobada | Pendiente |
| `0022` | 53/24 = 2,2083 s | 66/24 = 2,750 s | Aprobada | Pendiente |

El owner respondió **«hermoso todo aprobado TODO, siguientes»** el 2026-11-11:
las cinco propuestas, incluida `0018`, aprobadas sin correcciones. No especificó
revisión desglosada por avatar/variante; no es validación lingüística formal.

Ventanas propuestas tras inspeccionar las curvas de antebrazos de Juan,
afirmativa; **no son límites lingüísticos certificados**:

- `0018`: sostiene flexión hasta muy cerca del final y el original acaba con
  ambos codos flexionados. Se propone completar el regreso desde un ancla tardía.
  La revisión especial debe comprobar el gesto y que la bajada sea natural.
- `0019`: conserva flexión hasta aproximadamente 1,917 s y baja después;
  se mantiene el tramo sostenido y se sustituye la salida tardía.
- `0020`: postura sostenida hasta 1,917 s y después una bajada; ancla posterior
  a esa zona para no acortar la postura.
- `0021`: conserva su zona sostenida hasta aproximadamente 1,542 s y baja después.
- `0022`: dura más; sostiene flexión hasta la zona de 2,167 s. No se le aplica
  un ancla de las señas cortas que recortaría esa parte de la animación.

Todas las entradas y muestras hasta el ancla permanecen exactas en los GLB de
prueba. Solo se sustituyen seis rotaciones por clip con regreso directo al reposo
aprobado (slerp/peso quíntico). Cara, dedos, otros canales, nodos, geometría y
tiempos sin cambios; las otras 180 animaciones por avatar intactas.

El visor aplica además el acomodo v3 (700 ms) al cuerpo completo de la propuesta,
con desaceleración de los últimos 250 ms de clip. En `0018` y `0019` comienza
**antes del ancla de regreso**: que sus muestras estén intactas no significa que
el visor las reproduzca a velocidad/peso originales durante ese acomodo. Se debe
revisar el conjunto; estos regresos cortos en tiempo de clip se ralentizan en
pantalla. No se afirma que el original esté incompleto ni incorrecto.

## Evidencia y reproducción

Visor temporal: **http://127.0.0.1:8766/group-05/**.
Actual a la izquierda, propuesta a la derecha, con selectores de seña/avatar y
variante. Sin encadenamiento ni integración en la app.

- `prepare.py` y `check.py`: ejecutar desde la raíz del repo, generan/verifican
  copias al lado del script. Servir esa carpeta por HTTP y abrir `index.html`.
- `verification.json`: ventanas y hashes de entrada de ambos assets.
- `checks.json`: 120 canales por avatar; entradas/muestras protegidas exactas,
  cuaterniones normalizados, regreso angular monótono y extremo en reposo.
- `browser-checks.json`: 40 selecciones/render en Chromium headless, cinco señas
  × cuatro variantes × dos avatares. Comprobador reutilizable en `../grupo-04/`.
- Capturas afirmativas por avatar/seña a tiempo local 1,75 s (visor 2,45 s);
  no evalúan por sí solas los regresos tardíos ni la continuidad del movimiento.
- Procedencia de fuentes originales intactas: `../signai-avatar`, commit
  `fc6f372c8412010d85925fe342fdcf0c073fbdf1`, según inventario del empaquetado.

Pendientes integración de las cinco aprobadas y prueba en el controlador real.
No se modificó la app ni hubo build/instalación por esta prueba externa.
