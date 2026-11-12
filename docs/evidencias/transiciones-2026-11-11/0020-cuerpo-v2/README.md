# 0020: segunda prueba corporal, incluyendo la zona de 1,8 s

**2026-11-11. Estado:** propuesta pendiente de aprobación, no integración.
El owner aclaró «es en todo el cuerpo siento. es como un movimeinto raro».
No se interpreta como autorización para suavizar los dedos ni como aprobación
de la primera corrección corporal, que no resolvió su observación.

## Qué se propone

Se parte de la primera propuesta `0020-smooth/despues.glb`, que aún no está
aprobada. Se suaviza **solo el cuerpo dentro de 1,500–2,125 s** con una ventana
local de cinco muestras (pesos 1,2,3,2,1), no el clip entero. Traslaciones con
promedio ponderado; rotaciones con media de cuaterniones alineados al mismo
hemisferio, normalización y mezcla esférica con el valor previo.

El efecto entra/sale con peso quíntico durante 0,125 s en cada borde, manteniendo
exactas las muestras ancla y las de fuera. Ventana de cinco muestras a 24 fps:
radio 2/24 s alrededor de cada muestra. Es una prueba, no una frontera lingüística
ni una causa del movimiento confirmada por esta operación.

Canales considerados: columna, cuello/cabeza, hombros, muslos, pantorrillas,
pies, dedos de pies y talones. Solo rotación/traslación con muestreo completo;
canales constantes/compactados, escalas y otros nodos intactos. Diferencias de
canales de los dos exports respetadas, sin inventar traslaciones ausentes.

- Juan: **88 canales** cambiados entre las cuatro variantes de `0020`.
- Pauline: **76 canales** cambiados entre las cuatro variantes.
- Entrada, curvas locales de brazos/manos, dedos de manos y cara exactos a v1.
- Nodos, tiempos, geometría y otras 196 animaciones por avatar intactos.
- La primera corrección corporal se conserva como base; **después de 2,125 s
  sus muestras son exactas**. Hay solapamiento de pruebas en 2,0417–2,125 s,
  no se afirma que toda la ventana de v1 permanezca idéntica.
- Suavizar hombros/torso cambia posiciones globales de brazos aunque sus curvas
  locales no cambien. Suavizar piernas puede afectar apoyos: el visor pide
  revisar también los pies. No hay IK ni garantía de contacto con el suelo.

El diagnóstico ampliado mostró pequeños cambios simultáneos de torso/piernas
en el tramo temprano y cambios mayores después, además de saltos de dedos. El
owner percibe cuerpo completo: no se eligen los saltos de dedos como causa por
inercia ni se filtran posibles configuraciones significativas de la seña.

## Comprobaciones y revisión

`prepare.py` y `check.py` verifican los GLB serializados: canales no objetivo
exactos, muestras fuera del intervalo exactas, tiempos/nodos idénticos y
normalización. Máximo angular/frame no mayor que v1 en las rotaciones filtradas.

Ejemplo de Juan afirmativo, dentro de **esta ventana**, calculado del GLB:
hombro L 6,90°→3,32°, muslo L 10,29°→4,10°. No son métricas comparables a los
máximos de v1 en otra ventana ni una medición de fluidez/personas/rendimiento.
`verification.json` registra fuente/hash/canales; `checks.json` guarda resultados.

`browser-checks.json`: ocho selecciones/render en Chromium headless (cuatro
variantes × dos avatares). Capturas afirmativas a 1,75 s local (visor 2,45 s).
No certifican continuidad, contactos ni validez lingüística.

Visor temporal: **http://127.0.0.1:8766/0020-smooth-v2/**.
Izquierda: primera propuesta, con tramo temprano intacto. Derecha: segunda prueba.
Ambos lados con el mismo final v3, velocidad normal por defecto y controles de
pausa/lentitud/tiempo. Casilla «solo clip» permite aislar la mezcla de reproducción.

Para reproducir, generar antes `0020-smooth` como carpeta hermana y ejecutar
`prepare.py` y `check.py` desde raíz; servir esta carpeta por HTTP. Three.js
requiere jsDelivr. Procedencia de originales intactos `../signai-avatar`, commit
`fc6f372c8412010d85925fe342fdcf0c073fbdf1`, según inventario de empaquetado.

Pendiente revisión visual, especialmente movimiento corporal y apoyos de pies.
App/fuentes/copias anteriores intactas; sin build/instalación por prueba externa.
No se avanza aún al siguiente grupo de entradas.

El owner precisó después que percibe manos/dedos **y** cuerpo, y autorizó una
tercera prueba conjunta. Registro `../0020-conjunto-v3/README.md`, visor
`/0020-smooth-v3/`. V2 no se declara aprobada ni se sobrescribe: sirve como
comparación de cuerpo suavizado sin modificar los dedos.
