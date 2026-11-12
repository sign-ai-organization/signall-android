# Grupo 07: 0028–0032

**2026-11-11. Estado:** cinco regresos aprobados; incidencia aparte en el inicio de `0028`. Grupo 06 aprobado completo,
incluida `0025`; se conservan todas las ventanas aprobadas anteriores.

| Seña | Ancla del regreso (tiempo de clip) | Llegada al reposo | Revisión | Integración |
|---|---:|---:|---|---|
| `0028` | 41/24 = 1,7083 s | 50/24 = 2,0833 s | Regreso aprobado; inicio observado aparte | Pendiente |
| `0029` | 47/24 = 1,9583 s | 53/24 = 2,2083 s | Aprobada | Pendiente |
| `0030` | 26/24 = 1,0833 s | 41/24 = 1,7083 s | Aprobada, incluida revisión temprana | Pendiente |
| `0031` | 38/24 = 1,5833 s | 49/24 = 2,0417 s | Aprobada | Pendiente |
| `0032` | 38/24 = 1,5833 s | 49/24 = 2,0417 s | Aprobada | Pendiente |

El owner respondió **«Muy bien, todas bien»**, pero señaló un inicio abrupto en
`0028`, que identificó como ajeno a los cambios. Se aprueban los regresos y se
separa esa incidencia; sin revisión desglosada por avatar/variante ni validación
lingüística formal.

Hipótesis tras inspección de curvas de antebrazos de Juan, afirmativa; no límites
lingüísticos certificados. `0028` sostiene flexión hasta aproximadamente 1,667 s;
se conserva también su inicio flexionado. `0029` mantiene postura hasta cerca
de 1,917 s, incluida la elevación tardía del codo derecho. `0030` empieza a bajar
mucho antes, después de 1,042 s: revisar que su ancla temprana no cambie el gesto.
`0031` y `0032` mantienen flexión hasta la zona de 1,542 s y bajan después.

Solo se sustituyen seis rotaciones por clip: regreso directo slerp/peso quíntico
al reposo aprobado, sin seguir el recorrido viejo. Entradas y muestras hasta el
ancla exactas; cara, dedos, otros canales, geometría, nodos y tiempos intactos.
Otras 180 animaciones por avatar intactas. Copias externas, sin edición de app.

El visor aplica a la derecha el acomodo v3 de 700 ms del cuerpo completo. En
`0029` puede empezar antes del ancla: se debe revisar el conjunto de velocidad y
mezcla, no asumir que muestras exactas implican reproducción intacta en ese tramo.
Izquierda: asset actual. No prueba encadenamiento entre señas.

## Evidencia y reproducción

Visor temporal: **http://127.0.0.1:8766/group-07/**.
Ejecutar `prepare.py` y `check.py` desde la raíz del repo; generan/verifican copias
junto al script. Servir la carpeta por HTTP. Enlace anterior requiere carpeta
hermana; Three.js requiere jsDelivr. Comprobador CDP reutilizable en `../grupo-04/`.

- `verification.json`: hashes de entrada y ventanas, ambos avatares.
- `checks.json`: 120 canales por avatar, entradas/muestras protegidas exactas,
  normalización, regreso angular monótono y extremo en reposo.
- `browser-checks.json`: 40 selecciones/render en Chromium headless (cinco señas,
  cuatro variantes, dos avatares). Capturas afirmativas a tiempo local 1,75 s
  (visor 2,45 s); no certifican el movimiento ni el significado LSC.
- Fuentes originales intactas: `../signai-avatar`, commit
  `fc6f372c8412010d85925fe342fdcf0c073fbdf1`, según procedencia del empaquetado.

Pendientes incidencia inicial de `0028` e integración con prueba en dispositivo.
Sin cambios en la app ni build/instalación nuevos por esta prueba externa.

## Incidencia separada: primer tramo de 0028

Inspección de `0028_afirmativo`, ambos avatares, en las copias antes/después:
todos los canales del primer medio segundo son idénticos. No lo introdujo el
ajuste de regreso. `0028-start-diagnosis.json` guarda la comprobación y muestras.

En el original, la distancia angular del antebrazo al reposo nuevo pasa de
26,558° a 1,305° (L) y de 26,478° a 0,699° (R), entre 0,041667 y 0,083333 s.
Es un cambio grande entre muestras separadas por aproximadamente 42 ms, antes
de la preparación principal. Coincide con la observación, pero no determina
por sí solo qué fotograma se debe corregir ni certifica el significado LSC.

No se corrigió el inicio ni se alteraron las propuestas aprobadas. Pendiente
aprobar una comparación específica de ese arranque sobre otra copia.
El owner autorizó prepararla («Si. Go.»): propuesta y verificaciones en
`../0028-inicio/README.md`, visor temporal `/0028-start/` del puerto 8766.
Sustituye solo las primeras muestras TRS por las segundas, sin alterar el regreso.
El análisis ampliado encontró salto inicial también en brazos, manos y otras
partes del cuerpo. La corrección del salto se aprobó después («Bien, excelente.
Muy muy muy buena»). La entrada desde reposo nuevo también fue aprobada
(«hermoso, perfecto. documentamos y seguimos como antes»), registro en
`../0028-entrada/`. Ambas siguen pendientes de integración junto al regreso.
