# Panel de Conversación en burbujas

**Fecha:** 2026-11-03, tarde · **Rama:** `main`

## Qué llegó

El owner pidió **una sola cosa**: que el drawer izquierdo de Conversación se lea como una
conversación en vez de dos bloques apilados con una línea en medio. La frase de la persona
a un lado y su traducción a glosas al otro, sin perder el botón Repetir por turno (`CNV-5`)
ni el desplazamiento automático al último (`CNV-4`), y sin azul de marca en las burbujas:
el azul `#1C4CFC` vive solo en la acción principal y los controles.

Las tres decisiones de diseño eran suyas y pidió tomarlas: quedan abajo, con el porqué.
El resto (estado vacío `CNV-7`, nota de gesto `CNV-6`, cabecera `CNV-1`) no se tocó.

## Qué se hizo

- `ui/screens/conversation/ConversationPanel.kt`: `ConversationTurnItem` pasa de dos
  `ConversationBlock` apilados a dos burbujas. La frase va al **borde derecho** (`soft` +
  borde `outline`, tipografía `quote`) y la traducción al **izquierdo** (`surface` + borde
  `outline`, tipografía `gloss`), con el botón Repetir a la derecha de su burbuja, como
  antes lo pedía `CNV-5`. Cada burbuja conserva su etiqueta chica arriba. Se fue
  `ConversationBlock` (y con él el `drawBehind` de la línea divisoria).
- La burbuja se mide por su texto: `fillMaxWidth(fraction) + wrapContentWidth(End)` en la
  frase y `weight(1f, fill = false)` en la glosa (ese peso es su **máximo**, no su ancho,
  así que siempre queda lugar para el botón).
- `designsystem/foundation/SignAiShapes.kt`: `cornerBubbleStart`/`cornerBubbleEnd`, 18 dp
  con la esquina que apunta al hablante en 6 dp.
- `designsystem/foundation/SignAiSizes.kt`: `conversationBubbleMaxWidthFraction = 0.88f`.
- `CNV-4`: el desplazamiento automático ahora también deja ver el **final** del último
  turno. Antes se hacía `animateScrollToItem(lastIndex)`, que alinea el borde de arriba del
  ítem: con una glosa de cuatro líneas la traducción terminaba por debajo del pliegue. Se
  agrega `sitOnNewestTurn()`, que mide el sobrante y lo desplaza; mide **cuando el layout
  deja de moverse**, porque `animateItem` sigue acomodando los turnos después de que la
  animación de scroll termina.
- El turno **crece después de aparecer** (`ConversationTurn` se crea con la glosa de la
  primera oración y se completa con las demás), así que el anclaje se repite cuando cambia
  la glosa del último turno y cuando cambia el tamaño de la tipografía (`ACC-2`); sin eso
  el turno crecía después del scroll y quedaba cortado igual.
- Con «Reducir animaciones» prendido no corre `animateItem()` (ya era así) y el scroll usa
  `scrollToItem`/`scrollBy` instantáneos en vez de las variantes animadas.

## Qué se decidió

1. **Las etiquetas «TÚ» y «TRADUCCIÓN» se quedan**, como subtítulo chico arriba de cada
   burbuja (`typography.label`, color `muted`). Sacarlas dejaba el lado y el relleno como
   única señal, y eso se pierde con TalkBack y para quien no distingue bien los grises;
   además `CNV-2`/`CNV-3` las nombran. Van en 10 sp para no competir con el contenido.
2. **La frase va a la derecha y la traducción a la izquierda**, como en un chat: lo que
   escribe la persona sale de su lado y lo que devuelve el sistema entra. De relleno, la
   frase toma `soft` (es el contenido de la persona y el que manda) y la traducción queda
   en `surface` con borde, que la hace secundaria sin desaparecer: el panel ya es
   `surface`, así que el relleno no alcanzaba para distinguirlas. Cero azul de marca.
3. **El ancho máximo es 88 % del panel**, en fracción y no en dp, porque el panel mide
   `min(88 % de la pantalla, 350 dp)`: en el Redmi quedan ~269 dp de texto y ~37 dp de aire
   del lado contrario. Una frase corta no se estira hasta el máximo; solo se topa con él.

## Qué se midió

Todo en el **Redmi Note 9S físico** (1080 × 2400, 440 dpi), build `debug` con
`-Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate` y `adb reverse` al 8080, contra un
backend falso en `/tmp/opencode/fake_backend_conversacion.py` que devuelve tres oraciones
con tres glosas cada una (nueve palabras por turno) — el backend real no estaba levantado y
el falso que había en el 8080 devuelve siempre «YO CASA GRACIAS».

- Frases cortas y largas: una frase de 83 caracteres envuelve en cuatro líneas y la burbuja
  respeta el 88 %; las cortas quedan del ancho de su texto.
- Muchas frases: nueve turnos, desplazamiento automático al último y final de la
  traducción visible, sin quedar cortado por el borde inferior.
- Estado vacío, nota de gesto y cabecera: sin cambios, en los dos temas.
- Modo claro y modo oscuro: las dos burbujas se distinguen en los dos.
- «Reducir animaciones» prendido: el panel se abre ya anclado al último turno, sin
  animación de entrada ni de scroll.
- «Tamaño de interfaz» en 80: el turno sigue entero después de re-anclar.
- Botón Repetir de un turno: cierra el drawer y reproduce (30 ms de `signStart`, `CNV-9`).
- Árbol de accesibilidad: los botones se publican como «Repetir la seña de \<frase\>».

## Qué quedó pendiente

- Probar con TalkBack puesto: el orden de lectura tiene que ser etiqueta + texto de la
  frase, después etiqueta + texto de la traducción, después el botón.
- El contraste de la etiqueta de 10 sp en modo claro es 3,65:1 sobre `soft` (y 4,07:1 sobre
  el fondo del panel el `eyebrow` de la cabecera, que ya estaba así). Es la misma
  limitación que ya tenía la app; si el owner quiere AA estricto hay que subir el color de
  `muted` o el tamaño.
- Nada de los repos hermanos se tocó: no hay frame nuevo ni cambio de contrato.
