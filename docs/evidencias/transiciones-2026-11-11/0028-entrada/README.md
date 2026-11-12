# 0028: preparación desde el reposo nuevo

**2026-11-11. Estado:** entrada aprobada visualmente, integración pendiente.
El owner aprobó quitar el salto inicial («Bien, excelente. Muy muy muy buena»),
pero señaló que la seña todavía pasa por el reposo viejo antes del gesto.
Autorizó esta prueba: «Si. No lo habia notado tanto en el resto de señas, pero
en esta sí. Vamos». No autoriza extender esta entrada al resto del corpus.

Después de ver la propuesta, respondió **«hermoso, perfecto. documentamos y
seguimos como antes»**. Entrada aprobada sin correcciones; no se atribuye revisión
desglosada por avatar/variante ni validación lingüística formal.

## Cambio propuesto

Partimos de la copia `0028-start` sin salto aprobada. Solo se cambian seis
rotaciones por variante de `0028`: `upper_arm`, `forearm`, `hand`, ambos lados.

- Desde el inicio hasta **10/24 = 0,4167 s**, brazos/manos en el reposo nuevo.
- De ahí a **18/24 = 0,750 s**, preparación directa hasta la muestra original
  de esa unión, con slerp y peso quíntico, sin mezclar con el recorrido viejo.
- Desde la muestra de **0,750 s inclusive**, todos los valores del gesto y el
  regreso son exactos a la copia aprobada sin salto. No cambia la duración.
- Dedos, cara, torso, piernas, otras transformaciones, nodos y tiempos intactos.
  También se conserva la corrección de primera postura en el resto del cuerpo.

Es una ventana **provisional para revisión visual**, no una frontera lingüística
demostrada. La inspección previa de antebrazos muestra reposo hasta aproximadamente
0,542 s y elevación después; se propone preparar antes y unir a 0,750 s. El owner
debe revisar que esa preparación no elimine un componente del gesto.

Ambos lados usan el mismo acomodo final v3. Izquierda: copia sin salto pero con
reposo inicial anterior. Derecha: entrada desde reposo nuevo. No cambia ninguno
de los regresos aprobados ni las otras propuestas del grupo 07.

## Comprobaciones y reproducción

- `prepare.py` incluye comprobaciones de estructura, tiempos, muestras protegidas,
  reposo inicial exacto, normalización y fuentes intactas.
- `verification.json`: hashes de entrada, ventanas y resultados; **24 canales
  por avatar**, seis rotaciones × cuatro variantes. Otras 196 animaciones intactas.
- `browser-checks.json`: ocho selecciones/render en Chromium headless (cuatro
  variantes × dos avatares). Capturas a 0,05 s local (visor 0,75 s). No certifican
  movimiento, rendimiento ni validez LSC.
- Datos de partida: `0028-start`, cuyos hashes están en el informe; procedencia
  histórica de fuentes intactas `../signai-avatar`, commit
  `fc6f372c8412010d85925fe342fdcf0c073fbdf1`.

Visor temporal: **http://127.0.0.1:8766/0028-entry/**.
Para reproducir aparte, generar primero grupo 07 y `0028-start`; colocar esta
última como hermana del script, ejecutar `prepare.py` desde la raíz y servir
la carpeta por HTTP. Three.js requiere jsDelivr.

App y originales intactos; no hubo build/instalación por esta prueba externa.
Entrada aprobada; pendiente integración en app junto al salto inicial y regreso
ya aprobados. Continúa la revisión por grupos de cinco sin generalizar esta entrada.
