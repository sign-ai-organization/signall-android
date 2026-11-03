# Los dos drawers abren y cierran sin rebote, y el gesto decide por velocidad

**Fecha:** 2026-11-03, tarde · **Rama:** `main`

## Qué llegó

El owner pidió quitar el rebote de los dos paneles laterales: que abran y se suelten **sin
sobrepasarse**, que se asienten y queden quietos, con la misma sensación de rapidez. Después
aclaró el criterio con el que hay que comparar el comportamiento: **cómo se abre una sidebar
en las apps de todos los días** —Gmail, X/Twitter—, donde "con que arrastres un poquito ya
te la abre completa", tocar afuera la cierra sin rebote, y la animación es sobria.

## Qué se hizo

Todo el cambio está en `ui/designsystem/components/EdgeDrawer.kt`, que es el único
componente de los dos paneles (`side = Start/End`).

1. **Sin rebote** (`EdgeDrawer.kt:195` y `:275`): el spring de apertura/cierre por tap y el
   del soltar el arrastre pasan de `Spring.DampingRatioMediumBouncy` (ζ = 0,5) a
   `Spring.DampingRatioNoBouncy` (ζ = 1). Las rigideces quedan como estaban (`StiffnessMedium`
   al abrir/cerrar por tap, `StiffnessHigh` al soltar el arrastre).
2. **Fuera la franja `drawerOverflow`** (`:511`): se elimina el `Box` de 64 dp de `surface`
   colgando fuera de cuadro, el parámetro `drawerOverflow` y el `dragDirectionSign`, que
   existían solo para tapar el hueco del sobrepaso.
3. **El gesto decide por velocidad** (`:239` `snapAfterDrag`): antes se miraba únicamente la
   mitad del recorrido. Ahora, si el soltar trae `|v| ≥ 125 dp/s` (constante
   `DrawerFlingVelocity`, `:105`), manda el sentido del gesto; si no, manda la distancia (la
   mitad del panel). La velocidad se arrastra al resorte con `initialVelocity`.
   **Solo si empuja hacia el destino**: en contra, un resorte crítico podría pasarse del
   borde, que es justo lo que se está quitando.
4. **Spec**: `EDG-5` reescrito (`[P]` → `[N]`, umbral de 90 px → velocidad + mitad) y
   `EDG-25` nuevo (sin rebote, con las mediciones).

## Qué se decidió

- **Spring sin rebote, no un tween**, y **borrar la franja**: las dos las eligió el owner
  entre opciones con costo y beneficio. Se descartó `tween(250, EaseOut)` —el token que
  `SignAiMotion` documenta para "slide del drawer"— porque su duración es fija: soltar el
  arrastre al 99 % abierto igual esperaría 250 ms con el backdrop a medio desvanecer.
- **Umbral de 125 dp/s**: lo resolvió la sesión, no es una decisión de diseño nueva. Es el
  `velocityThreshold` del drawer modal de Material 3 (`SwipeableDefaults`) y está en una
  constante con nombre para poder moverlo después de probarlo.
- **No se toca el ancho de 300/350 dp ni la exclusión de gestos**: `panelWidth =
  min(maxWidth * 0.88, 350.dp)` ya es responsive y en el Redmi unos 20 dp de cada borde son
  del sistema (spec `EDG-24`).

## Qué se midió

Todo en el **Redmi Note 9S físico (MIUI, Android 11)**, build `debug` con
`-Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate` (sin backend levantado: los drawers
no lo necesitan), 2026-11-03.

- **Sin sobrepaso**, `screenrecord` a 60 fps en tema claro con el panel de Ajustes (345 dp =
  948 px), sacando el borde del panel frame por frame: recorre **1080 → 131 px en ~10 frames
  (~167 ms)** y se queda en 131 durante los ~270 frames siguientes. **Ningún frame por debajo
  de la posición abierta** y ningún retroceso. Con ζ = 0,5 el sobrepaso máximo teórico era
  `e^(-πζ/√(1-ζ²))` = 16,3 % → ~49 dp, o sea ~135 px de más sobre este panel.
- **Sin hueco en el borde**: en el primer frame con movimiento, el píxel del borde de la
  pantalla (`x = 1076`, `y = 1400`) ya es `(253,253,250)`, el color del panel, y no cambia
  en ningún frame posterior.
- **Velocidad** (`adb shell input swipe`, registro temporal en el propio componente):
  abrir con 250 px en 120 ms (~750 dp/s) **abre**; cerrar con 200 px en 120 ms **cierra**
  (las dos por debajo de la mitad del panel, así que antes se habrían devuelto);
  arrastrar 150 px en 900 ms (~47 dp/s) **se queda cerrado**.
- **Tres anchos**, cambiando `wm density`: 392 dp de pantalla → panel de 345 dp; 540 dp →
  panel de 350 dp (el tope); 308 dp → panel de 271 dp. En los tres, el borde derecho queda
  tapado y el panel abre igual.
- **Tema claro y oscuro**: tap para abrir, tap en el backdrop para cerrar, arrastre para
  abrir y flick para cerrar, en los dos lados.
- `./gradlew build` limpio.

Nota de método: con `adb shell input swipe` el `VelocityTracker` de Compose a veces informa
`v = 0` (pasó en 1 de 4 intentos): es un artefacto de los eventos sintéticos, no del gesto,
y cae en el respaldo por distancia. Y los primeros toques después de instalar se pierden
mientras el WebView parsea el GLB de 34 MB: hay que esperar ~10 s antes de probar gestos.

## Qué quedó pendiente

- **Los iconos de la barra de estado al abrir el panel** (owner, misma sesión): en Gmail
  desaparecen y el panel corre por debajo de la barra; acá el drawer arranca **debajo** de la
  barra de estado (spec `EDG-18`) y los iconos quedan. Es un cambio de layering y de insets,
  así que **queda para que lo decida el owner**; no se tocó.
- **`EDG-24`** sigue abierta: si la pestaña se dibuja 12–24 dp adentro del borde para que el
  dedo caiga fuera de la franja del sistema.

## Segunda parte (misma fecha): las dos cosas que quedaron anotadas

El owner pidió arreglar en un commit aparte lo que la sesión había dejado anotado como
"encontrado y no tocado".

1. **Preview de arrastre duplicado** (`EdgeDrawer.kt`): había dos bloques `if` idénticos. Se
   borró uno. **No cambia nada visible**: midiendo con capturas a mitad de gesto, el preview
   se compone antes del panel real y mide lo mismo, así que el panel opaco lo tapa siempre;
   lo que se ve al arrastrar es el panel de verdad con su contenido y el label translúcido
   nunca aparece. Queda anotado en `EDG-6` y la decisión (quitarlo o dibujarlo encima) es
   del owner.
2. **Ajustes apretado en pantallas angostas** (`SettingRow.kt`, `SettingsSidebar.kt`,
   `SignAiSizes.kt`): con el panel de una pantalla de 308 dp, "Personaje" se partía en tres
   líneas porque el selector fijo de 167 dp se llevaba todo el ancho. `SettingRow` ahora
   mide su ancho (`BoxWithConstraints`) y, si baja de `settingRowStackBelow` (270 dp), las
   filas que piden `stackBelow` —solo las dos con control ancho: Personaje y Tamaño de
   interfaz— **se apilan**: etiqueta arriba, control abajo a la derecha. Las de switch no se
   apilan, porque su control de 43 dp nunca deja a la etiqueta sin lugar.

   Verificado con `wm density` (el activity está fijo en retrato, así que el ancho se simula
   con la densidad): **392 dp** (panel 345, tu teléfono) → idéntico a antes; **360 dp**
   (panel 317) → fila única, "Personaje" y "Tamaño de interfaz" en una línea; **308 dp**
   (panel 271) → apiladas las dos filas anchas y el resto igual.

   No cambia ninguna interacción ni el funcionamiento de los drawers: es solo layout de esa
   pantalla, y `EDG-25`/`EDG-5` de esta misma sesión siguen como quedaron.

Estado final de la sesión: `./gradlew build` limpio, app instalada en el Redmi Note 9S,
commit `fix(drawers)` + este `fix(settings)`, los dos en `main`.
