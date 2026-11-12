# 0020 v3: manos, dedos y cuerpo con el mismo criterio temporal

**2026-11-11. Estado:** v3 elegida para conservar, integración pendiente. El owner
precisó «son las manos, creo que los dedos, y el cuerpo» y autorizó («si»)
preparar otra copia del conjunto, sin filtrar la seña completa.

Decisión posterior: **«bien, ya sigamos con el resto. esa ya dejemosla asi»**.
Se conserva v3 y se cierra la ronda de ajustes de `0020` por decisión del owner,
sin declararla perfecta ni validar LSC formalmente. `accepted.json` registra
decisión y hashes. Para integrar `0020` usar esta selección, no entradas 03 anterior.

## Alcance y comparación

Ventana **1,500–2,125 s**, igual a v2; kernel temporal de cinco muestras con pesos
1,2,3,2,1, alineación/normalización de cuaterniones y mezcla quíntica de entrada/
salida de 0,125 s. Se extiende el mismo criterio de v2 a las rotaciones de brazos,
antebrazos, manos, palmas y dedos. No se seleccionan saltos solo por magnitud.

- Para calcular se usa **v1**, igual que al generar v2, evitando volver a filtrar
  sobre curvas corporales ya filtradas. El generador comprueba que el cuerpo
  recalculado coincide exactamente con v2 y no lo reescribe.
- Izquierda: v2 (cuerpo suavizado, manos/dedos intactos). Derecha: v3 (mismo cuerpo,
  con rotaciones de brazos/manos/dedos suavizadas en la misma ventana).
- Juan: **144 canales** adicionales cambiados en cuatro variantes (36 por clip).
- Pauline: **164 canales** adicionales cambiados (41 por clip). Se respeta la
  estructura de cada export, sin añadir canales de huesos ausentes.
- Entrada, cara/pesos, escalas, traslaciones de manos/dedos, nodos, geometría y
  tiempos intactos. Otras 196 animaciones por avatar idénticas a v2.
- Todas las muestras fuera de la ventana y sus anclas son exactas. Desde
  2,125 s continúa la salida de v2 sin cambios nuevos.

**Esta versión sí cambia las curvas locales de brazos/manos/dedos dentro del
tramo**, incluido el principio del regreso que se solapa con él. No se repite
la afirmación de v1/v2 de que esas curvas estén intactas. Las configuraciones
intermedias y contactos de manos pueden variar: requiere revisión visual, no
se certifica significado LSC por un máximo angular menor. Se conservan anclas y
temporización, pero no se garantiza forma exacta dentro del tramo filtrado.

## Comprobaciones

`prepare.py`: fuentes/estructura preservadas y cuerpo exacto a v2, sin doble
filtrado. `check.py`: GLB serializados, canales no objetivo exactos, muestras
fuera de ventana exactas, tiempos/nodos idénticos y cuaterniones normalizados.
Todas las rotaciones modificadas cumplen máximo angular/frame no mayor que v2.

Ejemplo de Juan afirmativo **en esta ventana**, calculado del GLB: meñique
izquierdo 53,29°→12,21° de máximo por frame; muñeca derecha 8,09°→6,29°.
No es medición de fluidez, personas o rendimiento ni validación lingüística.
`verification.json` registra hashes de v1/v2, parámetros y canales; `checks.json`
guarda resultados. Ambas propuestas previas siguen sin aprobación visual.

`browser-checks.json`: ocho selecciones/render en Chromium headless (cuatro
variantes × dos avatares), capturas afirmativas a 1,75 s local (visor 2,45 s).
No bastan para evaluar continuidad ni configuraciones de manos.

## Revisar y reproducir

Visor temporal: **http://127.0.0.1:8766/0020-smooth-v3/**.
Mismo final suave en ambos lados, velocidad normal, pausa, controles de tiempo
y cámara lenta; modo «solo clip» para aislar los datos de la mezcla final.
Revisar especialmente manos, contactos y coordinación corporal alrededor de 1,8 s.

Generar antes `0020-smooth` y `0020-smooth-v2` como carpetas hermanas; ejecutar
`prepare.py` y `check.py` desde raíz del repo y servir esta carpeta por HTTP.
Three.js necesita jsDelivr. Procedencia histórica de originales intactos:
`../signai-avatar`, commit `fc6f372c8412010d85925fe342fdcf0c073fbdf1`, según inventario.

Versión seleccionada; integración pendiente. App/fuentes/copias anteriores intactas;
sin build ni instalación por esta prueba externa. Se retoman grupos de entradas.
