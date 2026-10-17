---
name: compose-best-practices
description: Escribir UI en Compose para la app SignAI — Lumo UI y Phosphor, cero Material 3, los tokens de ui/designsystem/, el estado en MainScreenState y las trampas de layout del WebView y los drawers. Usar al crear o cambiar un composable, al elegir un componente o un icono, al tocar tema, tipografía, espaciado, animación o accesibilidad, o en una code review de UI.
---

# Compose en SignAI

Este proyecto **no usa Material 3**. `grep -rl material3 app/src/main/java` da **cero**.
La UI es **Lumo UI** (Noman R) + **Phosphor Icons**, sobre el Design System de SignAI.

> ⚠️ Este skill reemplazó a un skill genérico que decía "only androidx.compose.material3.*
> allowed" y "use MaterialTheme.colorScheme". **Eso está mal acá** y ya fue corregido. Si
> alguna referencia antigua de este pack sigue diciendo Material 3, está obsoleta.

## Reglas duras

1. **Nunca agregar Material 3.** Ni `androidx.compose.material3.*` ni
   `androidx.compose.material.*`. Si un componente no existe en Lumo, **se construye** con
   `foundation` y los tokens — no se importa de Material.
2. **Componente nuevo = Lumo primero.** Lumo es Compose y shadcn-like: se **adopta como
   base** y después se personaliza en el Design System de SignAI. Consultá
   <https://www.lumoui.com/> antes de decidir; no asumas que funciona como una librería
   convencional.
3. **Icono nuevo = Phosphor.** Nunca `material-icons-extended`.
4. **Ningún valor de estilo escrito a mano.** Nada de `.dp` sueltos ni de
   `Color(0xFF...)` en un composable.
   - Espaciado, tamaños, formas, motion → `ui/designsystem/foundation/` (`SignAiSpacing`,
     `SignAiSizes`, `SignAiShapes`, `SignAiMotion`, `pressScale`).
   - Color → `ui/designsystem/Color.kt`. Se consume con `colors` de `SignAiTheme`, **no**
     con `MaterialTheme.colorScheme`.
5. **Todo el estado de Compose vive en `ui/state/MainScreenState.kt`.** No hay ViewModel,
   ni Hilt, ni navegación. Un componente nuevo recibe su estado por parámetro y emite
   callbacks; no busca un singleton.
6. **La regla de la tecla es "cada una se toca una vez y queda prendida"**, no "se mantiene
   apretada". Ningún control de esta app es de mantener.
7. **Nunca uses `playbackState == Listening` para saber si el micrófono está prendido.**
   Con el mic prendido y el muñeco representando, el estado es `Signing`. Si un método
   nuevo toca el mic, tiene que tocar **los dos** estados.

## Ingeniería que sigue valiendo

- **Composable sin estado**: recibe datos, emite eventos. El estado vive arriba.
- **Llave estable en listas `Lazy`**: `items(items, key = { it.id })`. Sin llave, un
  `SnapshotStateList` reordena mal y el recycler-compose se equivoca.
- **Sin asignaciones en el camino caliente de recomposición.** En una lista lazy o una
  animación, cada allocate por frame es jank.
- **Sin `suspend` en el cuerpo de un composable.** Se usa `LaunchedEffect` con una clave
  correcta; una clave mal puesta re-suscribe en cada recomposición.
- **Strong skipping** es el default (Compose Compiler 2.0+): reduce la necesidad de
  `@Stable`/`@Immutable` a mano. No los pongas "por las dudas" — solo si el compilador lo
  pide.
- `rememberKeyboardDismissAction()` (`ui/designsystem/foundation/`) es **la** forma de
  cerrar el teclado: `focusManager.clearFocus(force = true)` y después `keyboard?.hide()`.
  Con solo `hide()` el teclado vuelve, porque el `TextField` sigue con el foco y la
  siguiente recomposición lo muestra de nuevo. **Tocar un control cierra el teclado, no
  manda la frase.**

## Color y contraste

El manual de marca es `docs/manual-identidad-visual.md` (lo escribió Samuel Osorio) y los
valores están en `ui/designsystem/Color.kt`. Reglas que se rompen fácil:

- **Dark = grafito y es el default.** Light usa los tokens del prototipo.
- **El azul de marca `#1C4CFC` vive solo en la acción principal y los controles.** No es
  decoración.
- **`live`/`onLive` = ámbar `#F2B84B` es lo único que significa "escuchando ahora"**, y
  **nunca es la única señal**: el control tiene que cambiar etiqueta o
  `stateDescription`.
- **Sobre oscuro, los iconos y las líneas finas usan `colors.text`, no `colors.strong`**:
  `strong` da 2,86:1 sobre `surface`, por debajo del 3:1 que necesitan los elementos no
  textuales.
- El canvas 3D sigue la paleta: `app.js`/`index.html` con fondo `#121316` y grilla
  `#25272c`.

## Las dos trampas de layout de esta app

**1. El `AndroidView` del WebView se come los toques.** Es un View real: toma cada toque
dentro de sus bounds. Por eso tiene que ser el **hijo de abajo** con los drawers
compuestos arriba, y su `pointerInteropFilter` devuelve `true` mientras haya un drawer
abierto. Si un drawer no abre, el problema es el layering, no el gesto.

**2. Los dos drawers son hermanos, no anidados.** Un `clickable`/`draggable` con
`enabled = false` **igual** registra un nodo de entrada y queda como la ruta de toque más
alta: un drawer inactivo de pantalla completa se traga los toques del activo. **Nunca
enganches el gesto cuando puede no actuar.** El backdrop se compone **antes** del panel.
**Leé el comentario `ORO` al principio de `ui/designsystem/components/EdgeDrawer.kt` antes
de tocar nada de esto.**

## Accesibilidad

Este proyecto tiene **audiencia con LSC**, así que la accesibilidad no es decorativa.

- **`contentDescription` en todo elemento interactivo que no sea texto**; `null` en lo
  puramente decorativo.
- **Objetivo táctil mínimo 48 × 48 dp.** El owner ya pidió hitboxes más grandes en las
  pestañas de los drawers: es una decisión, no un descuido.
- **Nunca conveyar información solo por color.** El ámbar de "escuchando" va con cambio de
  etiqueta o de `stateDescription`.
- **`Modifier.semantics { stateDescription = ... }`** en los controles custom. Los dos
  interruptores del mic ya lo hacen.
- **Orden de lectura de TalkBack igual al orden visual**, y `mergeDescendants` para
  agrupar lo que va junto.
- **`sp`, nunca `dp`, en texto**, para que respete el escalado del sistema. La app tiene
  además su propio escalado (`fontScale` en Ajustes) que transforma la tipografía.
- Probá con TalkBack en el device: `tools/dev.sh launch` y el árbol de accesibilidad.

El detalle por área está en el skill `android-accessibility-best-practices`.

## Performance

- **Nunca degradar el WebView 3D.** Es el protagonista y el presupuesto de GPU es el que
  fijó el cap de `setPixelRatio`. Cualquier animación de Compose se mide contra esto.
- **Medir en build release, nunca en debug.** En debug no hay R8 y el número no extrapola.
- Herramientas: `tools/rendimiento/` (`gfxinfo.py`, `sf-medir.sh`, `frames.py`,
  `perfetto.sh`).
- **El splash existe por una razón**: `avatarReady` del bridge, no `onPageFinished`, porque
  el GLB de 10 MB todavía está bajando. No lo reemplaces por un timeout.

El detalle por área está en el skill `android-performance-best-practices`.

## Referencias

- `references/design_tokens.md` — los tokens de este proyecto y cómo se consumen
- `references/review_checklist.md` — la lista para revisar UI antes de darla por terminada
- `references/compose_compiler_stability.md` — strong skipping y cuándo anotar estabilidad
- `references/performance_budgets.md` — dónde vale la pena optimizar
- `references/examples.md` — patrones de Compose que sí se usan acá