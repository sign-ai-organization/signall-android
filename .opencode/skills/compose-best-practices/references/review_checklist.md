# Revisión de UI — lista antes de darla por terminada

## Prohibiciones

- [ ] **Ni un import de `androidx.compose.material3.*` o `androidx.compose.material.*`.**
      `grep -rl material3 app/src/main/java` tiene que dar cero.
- [ ] **Ni un `Color(0xFF...)` ni un `.dp` suelto** en un composable.
- [ ] **Ningún `material-icons-extended`.** Los iconos son Phosphor.
- [ ] **Ninguna referencia a `MaterialTheme.colorScheme`.** El color sale de `colors`.

## Componente

- [ ] ¿Es un componente Lumo, o algo que ya existía en `ui/designsystem/`? Si es nuevo y
      no salió de Lumo, está bien **si se construyó con `foundation` y los tokens**.
- [ ] ¿Es stateless, con el estado en `MainScreenState`?
- [ ] ¿Las listas `Lazy` tienen **llave estable**?
- [ ] ¿Hay asignaciones en un camino caliente de recomposición?
- [ ] ¿Hay `suspend` en el cuerpo de un composable?
- [ ] El componente es **reutilizable**, no está atado a una pantalla. Los que son de una
      pantalla van en `ui/screens/`, no en `ui/components/`.

## Teclado y foco

- [ ] ¿Usás `rememberKeyboardDismissAction()` y no `keyboard?.hide()` pelado?
- [ ] ¿**Tocar un control cierra el teclado y no manda la frase**? El texto se queda en el
      campo.
- [ ] El `onDone` del teclado cierra **siempre**, aunque no haya nada que mandar.

## Drawers y WebView

Si tocaste algo del layering:

- [ ] ¿Leíste el comentario `ORO` de `EdgeDrawer.kt`?
- [ ] ¿El WebView sigue siendo **el hijo de abajo**, con los drawers compuestos arriba?
- [ ] ¿El `pointerInteropFilter` del WebView devuelve `true` mientras hay un drawer
      abierto?
- [ ] ¿El drawer inactivo **no compone ningún overlay interactivo**? Un
      `clickable(enabled = false)` se traga los toques del otro.
- [ ] ¿El backdrop se compone **antes** del panel y del tirador?

## Color y contraste

- [ ] ¿El azul `#1C4CFC` quedó **solo en la acción principal y los controles**?
- [ ] ¿El ámbar `live` se usa solo para "escuchando ahora"?
- [ ] Si un color comunica "escuchando", ¿el control **también** cambia de etiqueta o de
      `stateDescription`? **El color nunca es la única señal.**
- [ ] ¿Los iconos y líneas finas sobre oscuro usan `colors.text`, no `colors.strong`?
- [ ] ¿El tema claro también usa el azul de marca para la acción principal?

## Accesibilidad

- [ ] `contentDescription` en todo interactivo que no sea texto; `null` en lo decorativo.
- [ ] Objetivo táctil ≥ 48 × 48 dp.
- [ ] `stateDescription` en los controles custom.
- [ ] Orden de lectura de TalkBack = orden visual.
- [ ] **`sp` en texto**, nunca `dp`.

## Verificado en el device, no en el papel

Un cambio de UI **no está terminado hasta que se ve en el device**:

- [ ] `./gradlew installDebug` corrido y app relanzada.
- [ ] Los dos modos, claro y oscuro.
- [ ] Con TalkBack: el elemento se anuncia y se puede tocar.
- [ ] Con el tamaño de interfaz y la negrita más grandes (Ajustes → ACCESIBILIDAD).
- [ ] Con **reducir animaciones** prendido: la transición tiene que tener un corte seco
      (`setReduceMotion`).

> Recordá: el avatar 3D **no** aparece en el árbol de accesibilidad. Para el estado de la
> escena, el tag `SignAllWeb` de logcat o el modo debug de Ajustes → AVANZADO.