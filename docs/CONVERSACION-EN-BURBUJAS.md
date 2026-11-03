# Panel de Conversación en burbujas — qué se hizo y por qué

**Fecha:** 2026-11-03 · **Rama:** `main` · **Commit:** `44264e5`
**Archivos tocados:** `ui/screens/conversation/ConversationPanel.kt`,
`ui/designsystem/foundation/SignAiShapes.kt`, `ui/designsystem/foundation/SignAiSizes.kt`,
`openspec/specs/ui/spec.md`, `openspec/sessions/2026-11-03-conversacion-en-burbujas.md`
y el índice de sesiones.

Este documento cuenta la sesión completa: el pedido, cómo estaba el panel, las tres
decisiones de diseño que se tomaron y con qué criterio, cómo quedó implementado, el
problema del desplazamiento automático que apareció en el camino, qué se verificó en el
dispositivo y qué quedó abierto.

---

## 1. El pedido

Una sola cosa, y explícitamente una sola: que el drawer izquierdo de **Conversación** se
lea como una conversación con burbujas, en vez de dos bloques apilados separados por una
línea. La frase de la persona a un lado y su traducción a glosas al otro. Sin perder:

- el botón **Repetir** de cada turno (`CNV-5`);
- el **desplazamiento automático** al último turno (`CNV-4`);
- el **estado vacío** (`CNV-7`) y la **nota de gesto** (`CNV-6`).

Con dos restricciones de marca y de stack:

- el azul `#1C4CFC` vive **solo** en la acción principal y los controles, así que una
  burbuja rellena de azul no entra;
- hay que usar `surface` y `soft` con borde, los tokens de `ui/designsystem/foundation`, y
  Phosphor para cualquier ícono nuevo. Nada de Material 3.

El pedido incluía decidir tres cosas de diseño (etiquetas, lados y colores, ancho máximo).
La sesión empezó preguntándolas y el owner pidió tomarlas, así que se tomaron y quedaron
documentadas con su porqué en la sección 3.

---

## 2. Punto de partida

`ConversationPanel.kt` tenía dos composables:

- `ConversationBlock(label, content)`: una columna a ancho completo con una etiqueta
  (`TÚ` / `TRADUCCIÓN`), 9 dp, el contenido y una **línea de 1 dp dibujada con
  `drawBehind`** al pie.
- `ConversationTurnItem`: dos `ConversationBlock` seguidos, el segundo con la glosa y el
  botón Repetir a su derecha.

Es decir: dos bloques de pantalla completa por turno, con la etiqueta como único elemento
que decía quién hablaba, y una línea divisoria entre el texto de la persona y su
traducción. Funcionaba, pero se leía como una ficha, no como una conversación.

---

## 3. Las tres decisiones de diseño

### 3.1 Las etiquetas «TÚ» y «TRADUCCIÓN» se conservan

Se quedan, como **subtítulo chico arriba de cada burbuja**: `typography.label` (10 sp, bold,
interletrado 1,6) en color `muted`, separadas del texto por 4 dp.

Por qué:

- Si se van, la única señal que queda es el lado y el relleno. Eso se pierde en el árbol de
  accesibilidad (TalkBack lee el texto sin decir de qué lado está) y para quien no distingue
  bien los grises del modo oscuro.
- Son las que nombran `CNV-2` y `CNV-3`; sacarlas obligaba a reescribir el significado de
  esos criterios, no solo su redacción.
- Van en 10 sp y en `muted` para no competir con el contenido: el texto de la frase (20 sp)
  y la glosa (15 sp, bold) siguen siendo lo que se lee primero.

### 3.2 Frase a la derecha con `soft`; traducción a la izquierda sobre `surface`

La lógica es la de un chat: **lo que escribe la persona sale de su lado, lo que devuelve el
sistema entra**.

- **Frase («TÚ»)**: pegada al borde **derecho**, fondo `soft`, borde de 1 dp `outline`,
  tipografía `quote` (20 sp).
- **Traducción («TRADUCCIÓN»)**: pegada al borde **izquierdo**, fondo `surface`, borde de
  1 dp `outline`, tipografía `gloss` (15 sp bold).

Por qué ese reparto de rellenos y no el inverso: el panel del drawer ya es `surface`, así
que una burbuja `surface` sola se confunde con el fondo y solo se ve por el borde. Con el
reparto elegido, la frase —el contenido de la persona, el que manda— es la que tiene
relleno visible, y la traducción queda delineada: presente pero secundaria. En modo oscuro
el efecto es el mismo: `soft` (`#25272C`) se levanta sobre el `surface` (`#1A1B1F`) del
panel, y en claro `soft` (`#E8E8E3`) es el gris que se despega del `surface` (`#FEFEFC`).

**Cero azul de marca.** Los dos lados se distinguen por lado, relleno y etiqueta; el color
no es la única señal en ningún caso.

### 3.3 Ancho máximo: 88 % del panel, en fracción

`SignAiSizes.conversationBubbleMaxWidthFraction = 0.88f`.

Por qué una fracción y no un dp: el panel mide `min(88 % del ancho de pantalla, 350 dp)`,
así que un techo fijo en dp no sirve en un teléfono angosto. Con 0,88 quedan unos **269 dp
de texto y ~37 dp de aire** del lado contrario en el Redmi (panel de 350 dp menos 44 dp de
padding = 306 dp de contenido).

Y lo importante: **una burbuja corta no se estira hasta el techo**. Mide lo que mide su
texto; el 88 % es un máximo, no un ancho. Eso se consigue con
`fillMaxWidth(fraction) + wrapContentWidth(Alignment.End)` en la frase, y con
`weight(1f, fill = false)` en la glosa, donde ese peso es el **máximo** que puede ocupar
para dejarle lugar al botón Repetir (46 dp + 12 dp de separación ≈ 248 dp útiles de glosa).

---

## 4. Cómo quedó implementado

### 4.1 Estructura por turno

```
ConversationTurnItem
├── Column(horizontalAlignment = End)            ← la frase
│   └── ConversationBubble("TÚ", soft, cornerBubbleEnd,
│                          fillMaxWidth(0.88f).wrapContentWidth(End))
│       └── SignAiText(turn.text, quote)
├── Spacer(8 dp)
└── Row(verticalAlignment = CenterVertically)    ← la traducción + Repetir
    ├── ConversationBubble("TRADUCCIÓN", surface, cornerBubbleStart,
    │                      weight(1f, fill = false))
    │   └── SignAiText(turn.gloss, gloss)
    ├── Spacer(12 dp)
    └── TurnRepeatButton(phrase, onRepeat)       ← CNV-5, igual que antes
```

- **`ConversationBubble`** es un composable nuevo y privado: `Surface` con `shape` y
  `color` por parámetro, borde de 1 dp `outline`, padding 16 dp horizontal y 12 dp vertical,
  etiqueta + 4 dp + contenido. Recibe `color` y `shape` para que cada lado conserve su
  identidad sin duplicar el bloque.
- **`ConversationBlock` se eliminó**, y con él el `drawBehind` de la línea divisoria y los
  imports `drawBehind`/`Offset`. El aire entre turnos lo da el `padding(vertical = 12.dp)`
  del ítem (24 dp entre turnos) contra los 8 dp internos: el ojo agrupa las dos burbujas.
- **`TurnRepeatButton` no se tocó**: mismo tamaño (`repeatButtonSize`), misma forma
  (`cornerRepeat`), mismo `contentDescription` («Repetir la seña de \<frase\>») y mismo
  `pressScale`.

### 4.2 Tokens nuevos (foundation)

`SignAiShapes`:

```kotlin
val cornerBubbleStart = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp)
val cornerBubbleEnd   = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp)
```

18 dp como las pestañas de los drawers, con **la esquina que apunta al hablante bajada a
6 dp**, que es lo que hace que se lean como burbujas y no como tarjetas.

`SignAiSizes`: `conversationBubbleMaxWidthFraction = 0.88f` (constante, con el comentario
del cálculo de 306 dp → 269 dp).

Todo lo demás son tokens existentes: `SignAiSpacing` (`sm`, `smLg`, `md`, `xs`) en vez de
dp sueltos, `typography.quote` / `gloss` / `label`, y colores de `SignAiTheme.colors`.

---

## 5. `CNV-4`: el desplazamiento automático al último turno

Acá apareció lo que el pedido no anticipaba, y es la parte más delicada de la sesión.

### 5.1 El problema

El código hacía:

```kotlin
LaunchedEffect(turns.size) {
    if (turns.isNotEmpty()) listState.animateScrollToItem(turns.lastIndex)
}
```

`animateScrollToItem` alinea el **borde de arriba** del ítem con el borde de arriba de la
lista. Con los bloques viejos el último ítem entraba casi entero, pero un turno en burbujas
es más alto (frase + 8 dp + glosa de varias líneas): con una traducción de cuatro líneas, el
final de la glosa —justo lo que el usuario quiere leer— quedaba **por debajo del pliegue**.
Se veía cortado.

### 5.2 Los intentos que no sirvieron (y por qué)

1. **Medir el sobrante inmediatamente después del scroll y desplazarlo.** Falló: la medición
   se tomaba mientras `animateItem()` todavía estaba acomodando los turnos, así que
   devolvía una posición que la lista ya no tenía un frame después.
2. **`reverseLayout = true`** (anclar el contenido al fondo, como un chat). Ancló bien el
   fondo cuando la lista estaba corta, pero **llenaba la lista desde abajo** (con un solo
   turno, el par de burbujas aparecía pegado a la nota de gesto, lejos de la cabecera) y,
   con la lista larga, el último turno seguía cortado. Se descartó.
3. **Esperar a que el layout se asiente y recién ahí medir.** Casi: el sobrante medido era
   cero, pero la burbuja seguía cortada. La causa real apareció al imprimir los tamaños.

### 5.3 La causa real

El log mostró que los turnos viejos medían 577 px y el recién agregado 412 px:

```
items=0@-336+577, 1@241+577, 2@818+577, 3@1395+412
```

**El turno crece después de aparecer.** `MainScreenState` crea el `ConversationTurn` con la
glosa de la **primera oración** y después lo **completa** con las demás:

```kotlin
conversation.add(ConversationTurn(id, text, gloss = formatGlosses(pendingSegments.values.toList())))
// …y más tarde:
conversation[i] = turn.copy(gloss = formatGlosses(pendingSegments.values.toList()))
```

Es decir: el scroll se hacía sobre un turno de una línea de glosa y medio segundo después
ese turno ya tenía cuatro. Cualquier medición única hecha en el momento del alta queda
vieja por construcción.

### 5.4 La solución

Dos efectos con responsabilidades distintas sobre un helper común:

```kotlin
// Un turno nuevo: ir a él y después sentarse en su final.
LaunchedEffect(turns.size, reduceMotion) {
    if (turns.isEmpty()) return@LaunchedEffect
    if (reduceMotion) listState.scrollToItem(turns.lastIndex)
    else listState.animateScrollToItem(turns.lastIndex)
    listState.sitOnNewestTurn(animated = !reduceMotion)
}

// El turno crece (glosa que llega oración por oración) o cambia la tipografía (ACC-2):
// volver a sentarse en el final. No se vuelve a hacer scroll al ítem, para no deshacer
// el anclaje de arriba y producir un sube y baja.
val newestGloss = turns.lastOrNull()?.gloss
val phraseTextSize = SignAiTheme.typography.quote.fontSize
LaunchedEffect(newestGloss, phraseTextSize, reduceMotion) {
    if (turns.isEmpty()) return@LaunchedEffect
    listState.sitOnNewestTurn(animated = !reduceMotion)
}
```

Y el helper:

```kotlin
private suspend fun LazyListState.sitOnNewestTurn(animated: Boolean) {
    var previous = Int.MIN_VALUE
    repeat(120) {
        withFrameNanos { }
        val info = layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return
        val overflow = lastVisible.offset + lastVisible.size - info.viewportEndOffset
        if (overflow == previous) {          // el layout dejó de moverse
            if (overflow <= 0) return       // ya se ve entero
            if (animated) animateScrollBy(overflow.toFloat()) else scrollBy(overflow.toFloat())
            return
        }
        previous = overflow
    }
}
```

- Se mide **cuando el layout deja de moverse** (dos frames con el mismo sobrante), porque
  `animateItem` sigue corriendo después de que termina la animación de scroll.
- La repetición está **capada a 120 frames** (~2 s a 60 fps) para que un layout que nunca
  se asienta no deje el efecto vivo.
- Con **«Reducir animaciones»** prendido no corre `animateItem()` (ya era así) y el
  desplazamiento usa `scrollToItem`/`scrollBy` instantáneos, sin animar nada.
- La tipografía se mira a propósito: cambiar «Tamaño de interfaz» agranda todas las
  burbujas sin que cambie la lista, y sin esa clave el último turno volvía a quedar cortado
  (se reprodujo con el tamaño en 80).

---

## 6. Qué se verificó (y dónde)

Todo en el **Redmi Note 9S físico** (1080 × 2400, 440 dpi), build `debug` con
`-Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate` y `adb reverse tcp:8080 tcp:8080`.

El backend real no estaba levantado y el que había corriendo en el 8080 (un falso de una
sesión anterior) devuelve siempre tres glosas cortas, así que para probar glosas **largas**
se levantó uno propio en `/tmp/opencode/fake_backend_conversacion.py`, en el puerto 8090,
con `adb reverse tcp:8080 tcp:8090`: tres oraciones con tres glosas cada una (nueve
palabras por turno). Después de las pruebas se apagó y el reverse volvió al 8080.

| Qué se probó | Resultado |
|---|---|
| Frase corta («Hola todo bien») | Burbuja del ancho de su texto, alineada a la derecha; la glosa corta («YO CASA GRACIAS») también. |
| Frase larga (83 caracteres) | Envuelve en cuatro líneas y respeta el 88 %; no invade el aire del otro lado. |
| Glosa de nueve palabras | Envuelve en cuatro líneas y **el botón Repetir sigue visible**, a su derecha. |
| Muchas frases (nueve turnos) | El panel abre anclado al último turno, con el **final** de la traducción visible. |
| Estado vacío | «Aún no hay frases en esta conversación.», sin burbujas ni placeholders (`CNV-7`). |
| Nota de gesto | «Desliza desde el borde para volver», igual que antes (`CNV-6`). |
| Modo oscuro (por defecto) | Frase en `soft` con borde, glosa delineada sobre `surface`. |
| Modo claro | Mismo reparto de rellenos; las dos burbujas se distinguen. |
| «Reducir animaciones» prendido | El panel abre ya anclado, sin `animateItem` ni animación de scroll. |
| «Tamaño de interfaz» en 80 | El último turno queda entero después de re-anclar. |
| Botón Repetir de un turno | Cierra el drawer y reproduce: `signStart` a **30 ms** del toque en el log (`CNV-9`). |
| Árbol de accesibilidad | Los botones se publican como «Repetir la seña de \<frase\>». |

`./gradlew build` limpio antes de commitear, y `./gradlew installDebug` con el override de
`ws://127.0.0.1:8080` para que el owner pueda probar de inmediato.

---

## 7. Documentación que se actualizó

- **`openspec/specs/ui/spec.md`**: reescritas `CNV-2`, `CNV-3` y `CNV-4` contra el código.
  `CNV-2`/`CNV-3` ahora describen las burbujas, sus colores, el techo de ancho y que el
  azul de marca no entra. `CNV-4` agrega que la vista queda anclada al final del último
  turno, que el anclaje se repite cuando el turno crece o cambia la tipografía, y que con
  «Reducir animaciones» no corre `animateItem` y el scroll es instantáneo.
  `CNV-1`, `CNV-5` … `CNV-9` no cambiaron.
- **`openspec/sessions/2026-11-03-conversacion-en-burbujas.md`**: la bitácora de la sesión
  (qué llegó, qué se hizo, qué se decidió, qué se midió, qué quedó pendiente), más su línea
  en `openspec/sessions/README.md`.
- **Este documento**: `docs/CONVERSACION-EN-BURBUJAS.md`.

---

## 8. Lo que no se tocó

- El contrato con los repos hermanos: **ningún frame nuevo**, ninguna métrica nueva, ningún
  cambio de glosa. El panel consume `ConversationTurn` como antes.
- El estado: sigue todo en `MainScreenState`, el panel es stateless (recibe `turns`, emite
  `onRepeat` y `onClose`).
- `TurnRepeatButton`, la cabecera, la nota de gesto, el estado vacío, el cierre y el
  `horizontalDragBlocked()` que devuelve el arrastre horizontal al drawer (`EDG-9`).
- Nada de Material 3 (el repo sigue con cero imports de `material3`), y el ícono Repetir
  sigue siendo el mismo de Phosphor.
- Ningún archivo de los hermanos.

---

## 9. Pendientes y decisiones abiertas

1. **Probar con TalkBack puesto.** El orden de lectura esperado es: etiqueta + texto de la
   frase, etiqueta + texto de la traducción, botón Repetir. Se verificó que los botones se
   publican bien, no el recorrido completo con el lector encendido.
2. **Contraste de la etiqueta de 10 sp en modo claro.** Sobre `soft` da **3,65:1**, por
   debajo de AA para texto chico. El «CONTEXTO» de la cabecera, con el mismo estilo sobre el
   fondo del panel, ya estaba en **4,07:1**: es una limitación que la app ya traía, no algo
   que haya introducido este cambio. Si se quiere AA estricto hay que subir el color de
   `muted` o el tamaño de la etiqueta, y eso toca la paleta, no este panel. **Es decisión
   del owner.**
3. **El backend falso de `/tmp`** (`fake_backend_conversacion.py`) queda fuera del repo, en
   `/tmp/opencode/`, solo como herramienta de prueba con glosas largas.
