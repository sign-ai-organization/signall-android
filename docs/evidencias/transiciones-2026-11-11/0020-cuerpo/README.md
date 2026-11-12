# 0020: propuesta localizada para el tirón corporal

**2026-11-11. Estado:** preparada, pendiente de aprobación. El owner confirmó el
tirón hacia 2,2 s y autorizó una copia («Si»; luego «sigue»). No se interpreta
esa autorización como aprobación visual del resultado.

## Alcance

Fuente: `entries-03/despues.glb` aprobado, con entrada y regreso corregidos.
Se sustituye únicamente el interior del intervalo **49/24 = 2,0417 s a
54/24 = 2,250 s** en canales corporales seleccionados: columna, cuello/cabeza,
hombros, muslos, pantorrillas, pies y un dedo del pie izquierdo. Rotaciones por
slerp y traslaciones lineales, ambas con peso quíntico entre las muestras ancla.
No se filtra el clip completo ni se cambia el gesto previo al intervalo.

El intervalo termina en la última muestra disponible de esos canales corporales
(2,250 s); no se presupone que tengan tantas muestras como el canal más largo.
Muestras ancla y todos los valores fuera del intervalo, tiempos y nodos exactos.

- Juan: **18 canales por variante**, 72 en las cuatro variantes.
- Pauline: **16 canales por variante**, 64 en las cuatro variantes: no exporta
  traslaciones de hombros, así que no se añaden canales nuevos ni se alteran sus
  offsets fijos para simularlas.
- Se conservan exactamente las seis curvas locales de brazos/antebrazos/manos,
  los dedos de las manos y la cara. La entrada permanece intacta.
- Suavizar hombros/torso modifica ligeramente la trayectoria **global** de los
  brazos durante el intervalo, aunque sus curvas locales sean idénticas. No se
  afirma lo contrario. Requiere aprobación visual del conjunto.
- La sacudida de dedos hacia 2,000 s no se corrige: el owner confirmó el tirón
  corporal posterior como objeto de esta prueba. No se suaviza un posible gesto
  significativo de dedos por inercia.
- Geometría, bind, escalas y las otras 196 animaciones por avatar intactos.

## Comprobación, no validación visual

`prepare.py` y `check.py` verifican límites/estructura, archivos serializados,
curvas no objetivo exactas, muestras fuera de la ventana exactas y normalización.
`verification.json` registra hashes y canales; `checks.json` contiene resultados.

En el GLB de Juan, afirmativa, el máximo cambio angular por fotograma dentro de
la ventana pasa de **16,59° a 6,64°** en muslo izquierdo, **14,22° a 6,52°** en
derecho, **11,63° a 4,61°** en hombro izquierdo y **10,73° a 4,60°** en derecho.
Calculado con cuaterniones normalizados. Todas las rotaciones seleccionadas
cumplen máximo por frame no mayor que antes; no es una medición de rendimiento,
prueba con personas ni garantía de que el movimiento ya sea natural.

`browser-checks.json`: **24 selecciones/tiempos** verificados en Chromium headless
(cuatro variantes × dos avatares × tres casos: con mezcla final y dos tiempos
de clip sin mezcla). Se verificaron pausa, reloj y opción de mezcla. Capturas
afirmativas a reproducción local 2,300 s con mezcla y a 52/24 y 53/24 s sin mezcla.
Son poses de comprobación, no evidencia suficiente de fluidez.

## Revisar/reproducir

Visor temporal: **http://127.0.0.1:8766/0020-smooth/**.
Izquierda: versión aprobada con el tirón. Derecha: propuesta suavizada.
Ambos lados usan el mismo final v3 por defecto y velocidad normal; controles
de mitad/cuarto de velocidad, pausa y desplazamiento temporal. La casilla «solo
clip» aísla los datos sin mezcla final y mantiene última muestra al acabar.
Con mezcla, reloj/slider son tiempo de reproducción local, no tiempo muestreado.

Generar antes `entries-03` como carpeta hermana; ejecutar `prepare.py` y `check.py`
desde raíz del repo y servir esta carpeta por HTTP. Three.js necesita jsDelivr.
Procedencia histórica de fuentes originales intactas: `../signai-avatar`, commit
`fc6f372c8412010d85925fe342fdcf0c073fbdf1`, según inventario del empaquetado.

Pendiente aprobación visual. App, originales y copias aprobadas intactos; sin
integración, build ni instalación por esta prueba externa. No se continúa al
siguiente grupo de entradas hasta cerrar esta revisión.

## Primera devolución: movimiento anterior, alrededor de 1,8 s

El owner indicó «como en el 1.8, sigue habiendo un movimiento raro». La propuesta
no se considera aprobada ni resuelta. Ese tramo es anterior a la ventana de
corrección corporal y quedó intacto.

Inspección de la propuesta afirmativa en ambos avatares, 1,65–1,95 s: salto del
meñique izquierdo de unos 47,65° entre 1,6667 y 1,7083 s y 45,91° entre 1,875 y
1,9167 s; muñeca derecha cambia unos 8,09° entre 1,6667 y 1,7083 s. En ese último
intervalo la pantorrilla izquierda cambia unos 3,37°. Datos del GLB, con ángulos
normalizados; `early-motion-audit.json` guarda los eventos de ambos avatares.

Estos cambios pueden corresponder a otro movimiento percibido, pero no se declara
la causa del reporte sin confirmar localización con el owner. No se filtran dedos
ni se amplía a ciegas la ventana: esos canales pueden contener significado LSC.
Pendiente precisar si lo observa en manos/muñecas o en el cuerpo completo y preparar
otro ajuste solo cuando se delimite. Copias y app sin nuevas modificaciones.

El owner aclaró después que lo percibe **en todo el cuerpo**, no específicamente
en manos. Se preparó otra prueba corporal en `../0020-cuerpo-v2/`, visor
`/0020-smooth-v2/`: filtro local de torso/hombros/piernas en 1,500–2,125 s sobre
esta copia, sin filtrar dedos ni curvas locales de brazos. Pendiente aprobación;
la presente v1 conserva su evidencia y no se considera aprobada ni resuelta.
