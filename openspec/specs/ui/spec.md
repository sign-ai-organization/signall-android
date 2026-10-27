# UI / Features — Especificación de la app (fuente de verdad)

Catálogo **completo** de features de la app, derivado del prototipo
`PrototipoFrontend/` (Next.js/React — prototipo de referencia) y del estado
actual de la app nativa (`Compose shell + WebView 3D`).

> **Regla:** estas son **todas** las features que va a tener la app. Toda
> implementación debe cubrir los IDs de este spec. Si una feature del prototipo
> no se implementa, debe quedar marcada explícitamente como *Omitida* con su
> razón. Si aparece una feature nueva, se agrega aquí primero.

- **Prototipo de referencia:** `PrototipoFrontend/app/page.tsx` + `app/globals.css`.
- **Plan de ejecución:** `openspec/changes/archive/2026-10-17-prototipo-ui-vercel/tasks.md`.
- **Implementación:** Compose nativo (Lumo + Phosphor). El WebView es solo el
  canvas 3D.
- **Estado (2026-10-17):** implementado y **verificado en device**. Las fases A–G de
  `prototipo-ui-vercel` y la Fase 0 de `ui-migration-lumo` están **terminadas**; ambas
  están en `openspec/changes/archive/`. `grep -rl material3 app/src/main/java` da **cero**.
- **Tema:** el **default es oscuro** (`Settings.darkTheme = true`). La paleta oscura es
  **grafito**, no el navy del prototipo; el azul de marca `#1C4CFC` vive solo en la acción
  principal y los controles. Valores en `ui/designsystem/Color.kt`, referencia de marca en
  `docs/manual-identidad-visual.md`.
- **Marca:** **SignALL** (rebranding del 2026-10-16), centralizada en
  `ui/SignAiBranding.kt`.
- **MIUI force-dark:** la app **debe** declarar `android:forceDarkAllowed=false`
  (`res/values/themes.xml`). Si no, MIUI "smart dark" invierte el contenido de
  las capas hardware y el texto dentro de `verticalScroll`/drawers se ve blanco.

**Leyenda de origen** (verificada el 2026-10-17, contra el código):

- `[P]` — viene del prototipo `PrototipoFrontend/` (solo referencia visual).
- `[A]` — ya existía en la app antes de este spec.
- `[N]` — nueva o derivada, no está en el prototipo.

> Cuando un ID cambie de estado, la fila se actualiza con la **fecha** y el motivo, como
> se hizo con `CTL-6`, `STA-9` y `OM-4`. Una fila que dice "pendiente de merge" cuando el
> merge ya está hecho hace que un agente intente rehacer trabajo terminado.

---

## 1. Navegación y vistas

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| NAV-1 | Vista **Splash** | `[P]` | Primera vista; se muestra hasta que el avatar 3D termina de cargar. |
| NAV-2 | Vista **Main** | `[P][A]` | Avatar + control panel + pestañas de borde. Vista principal. |
| NAV-3 | Vista **About** | `[P]` | Pantalla "Acerca de" con botón volver. |
| NAV-4 | **Bottom sheet Historial** | `[P]` | Modal sobre Main. |
| NAV-5 | **Drawer izquierdo — Conversación** | `[P]` | Panel lateral izquierdo. |
| NAV-6 | **Drawer derecho — Ajustes** | `[P][A]` | Panel lateral derecho. |
| NAV-7 | Transición a About cierra el drawer | `[P]` | Abrir About desde Ajustes cierra el drawer antes de navegar. |
| NAV-8 | About es excluyente | `[P]` | Con About abierto no se muestra Main ni drawers. |

**Estados de vista (prototipo):** `loaded=false → Splash`; `about=true → About`;
resto → Main. `side` (left/right/null) y `historyOpen` controlan overlays sobre
Main.

---

## 2. Splash

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| SPL-1 | Wordmark "SignALL" | `[N]` | Logotipo **real** de la marca (`logo_signall_on_{light,dark}.png`), no una tile con la letra "S". Dos variantes: azul marino para tema claro, blanca para oscuro, elegidas por `LocalIsDarkTheme`. Ver `BRD-1`/`BRD-5`. |
| SPL-2 | Eyebrow "SEÑA"*¹ | `[N]` | **Quitado** del splash (owner, 2026-10-16): el wordmark ya dice el nombre y el eyebrow competía con él. Sigue en About (`BRD-2`). |
| SPL-3 | Barra de progreso | `[P]` | Track + valor que avanza de 0% a 100%. |
| SPL-4 | Porcentaje numérico | `[P]` | `{n}%` debajo de la barra. |
| SPL-5 | Progreso real | `[N]` | Dos tramos, no fake. El **0–45 %** sale de `WebChromeClient.onProgressChanged` (la página). El **45–100 %** lo manda la escena con `postMessage("progress:<n>")`: 45 al empezar a cargar el avatar, 45→80 con la descarga, 85 con el GLB parseado, 90 con los 300 clips indexados, 96 con los shaders compilados y 100 antes de `avatarReady`. Antes se ataba solo a la página, que dice 100 % cuando terminó el HTML —con 34 MB de avatar todavía en camino— y el splash se quedaba clavado en 100 % (owner, 2026-10-22). Sin la escena nueva (que no manda nada) la barra se comporta como antes. |
| SPL-6 | Timeout de seguridad | `[A]` | Si el WebView no carga (sin internet para el CDN), salir del splash a los 8 s. El timeout se **rearma en cada arranque del splash**, no solo en el primero: volver de Acerca de vuelve a poner `loading = true` y su propio timeout tiene que cubrir esa recarga (`SPL-8`). |
| SPL-7 | Fade de salida | `[A]` | El splash se desvanece (**800 ms**) al terminar; el contenido entra con fade (**1000 ms**), solapados. Era 400/500 y el avatar aparecía de golpe; el owner pidió el doble de lento (2026-10-22). |
| SPL-8 | Splash también al volver de About | `[A]` | Acerca de saca el `AvatarWebView` de la composición (es exclusivo, `NAV-8`), así que al volver se crea uno nuevo y hay que volver a descargar y parsear el GLB del avatar (34,21 MB con Juan, 29,46 MB con Pauline) con sus 300 clips: se veía el canvas vacío durante segundos. Al volver de About se levanta el splash otra vez y se espera al `avatarReady` real (owner, 2026-10-16). | **Qué significa "cargado"** (2026-10-22): `avatarReady` se manda después de indexar los clips, resolver la seña de arranque, compilar los shaders y pintar un frame con el avatar adentro — dos `requestAnimationFrame`—, así que el splash ya no se va entre "el avatar está en la escena" y "el avatar se ve". |

| SPL-9 | El splash se dibuja antes que el WebView | `[N]` | El `AvatarWebView` **no existe en el primer frame**: se monta dos frames después (`withFrameNanos` ×2 en `MainScreen`), así el splash ya está en pantalla cuando el WebView se crea y empieza a cargar. Antes el primer frame de la app era el que creaba el WebView, y el sistema mostraba una ventana vacía del color de fondo durante 3,4 s sin logo ni barra (medido con `screenrecord` en el Redmi Note 9S, arranque en frío, 2026-10-22); ahora lo primero que se ve es el splash. Cuesta ~2,2 s de arranque total (el avatar llega a los ~5,8 s en vez de ~3,6 s) y lo paga el splash, no una pantalla vacía. `sceneMounted` vive dentro de la rama principal, así que volver de Acerca de vuelve a diferir en vez de crear el WebView junto con el splash. |

\*¹ El texto del branding ya no está abierto: es **"SignALL"** (ver `BRD-1`). El texto de la fila es lo que el prototipo traía y lo que el owner quitó del splash (`SPL-2`).

---

## 3. Edge tabs, gestos y drag (drawers)

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| EDG-1 | Pestaña borde **izquierdo** | `[P]` | Abre Conversación; visible con drawer cerrado. |
| EDG-2 | Pestaña borde **derecho** | `[P][A]` | Abre Ajustes; visible con drawer cerrado. |
| EDG-3 | Click en pestaña abre drawer | `[P]` | Tap directo (sin arrastrar) abre el drawer correspondiente. |
| EDG-3b | Hitbox de la pestaña ≠ dibujo | `[A]` | El tab **se dibuja** de 20 dp pegado al borde (el diseño no cambia), pero el nodo que recibe el toque es mucho más ancho: +28 dp hacia adentro del ancho del strip (`handleHitPadding`), o sea 76 dp cerrado y 100 dp con el panel abierto. Motivo: MIUI se come los primeros ~15 dp de cada lado con sus gestos de sistema y 48 dp de zona útil no se acertaban en el Redmi Note 9S (owner, 2026-10-16). El `contentAlignment` mantiene el tab pegado al borde, así que el extra queda invisible. |
| EDG-4 | **Drag** desde pestaña | `[P]` | Arrastrar la pestaña sigue el dedo; offset acotado 0–300. |
| EDG-5 | Umbral de apertura por drag | `[P]` | Soltar con offset `> 90` abre; si no, vuelve. |
| EDG-6 | **Drag preview** | `[P]` | Panel translúcido que sigue el dedo con label "Conversación"/"Ajustes". |
| EDG-7 | **Edge swipe** desde borde | `[P]` | Swipe desde el borde izquierdo (inicio `< 54px`, delta `> 58`) abre Conversación; desde el derecho (`> width-54`, delta `< -58`) abre Ajustes. |
| EDG-8 | Pestaña visible + grip | `[A]` | Pestaña de agarre = `.edge-tab` del prototipo: tab de `surface` 20×62 dp pegado al borde, borde hairline `outline`, sombra suave y grip de **dos** líneas `muted` (4×28 dp, opacidad 0.65, separación 3 dp), redondeado solo en el lado que sobresale. |
| EDG-9 | Cerrar arrastrando el panel | `[A]` | El drawer abierto se cierra arrastrando desde cualquier zona de gesto: la pestaña, la franja del borde (activa también con el panel abierto) o el cuerpo deslizando sobre zonas que no scrollean. Se cierra también con tap en su backdrop. |
| EDG-10 | Tap en scrim cierra | `[P]` | Overlay oscurecido clicable para cerrar en **todo** lo que queda fuera del panel. El backdrop es un nodo a pantalla completa compuesto **antes** del panel y la pestaña, de modo que éstos conservan sus propios toques y cualquier otro punto (franja expuesta, hueco de la pestaña opuesta, esquina vacía) cierra el drawer. |
| EDG-11 | Pestaña del panel abierto | `[P]` | Handle lateral en el borde del panel que también cierra (click). |
| EDG-12 | Exclusión de la bottom bar | `[A]` | El área de gesto no tapa la barra de controles. |
| EDG-13 | Drawer bidireccional generalizado | `[N]` | Un solo componente `SignAiEdgeDrawer(side = Start/End)` reutilizado por ambos lados. |
| EDG-14 | Blur del canvas 3D | `[A]` | El WebView se difumina mientras un drawer está abierto (Android 12+, guardado). |
| EDG-15 | Drawer abierto es estado de la app | `[A]` | `MainScreenState.openDrawer` (`Start`/`End`/`null`) es la única fuente de verdad. |
| EDG-16 | Back cierra el drawer | `[A]` | Atrás con un drawer (o el sheet de historial) abierto lo cierra en vez de salir de la app. |
| EDG-17 | Backdrop en degradado, anclado al drawer | `[A]` | El overlay no es oscuro uniforme: es un degradado con peso sobre el borde del drawer abierto y transparente hacia el lado opuesto (como `.drawer-backdrop` del prototipo), dibujado sobre un nodo a pantalla completa cuyo gradiente se ancla al borde del panel (`edgeX = widthPx + offsetX` en Start, `offsetX` en End). |
| EDG-18 | Drawer por debajo de la barra de estado | `[A]` | El drawer y su pestaña arrancan bajo la status bar (`systemBars` top inset): su borde superior queda visible en vez de correr bajo los íconos del sistema. |
| EDG-19 | Una sola sidebar a la vez | `[N]` | Con un drawer abierto, la pestaña y la franja del otro lado quedan **totalmente inertes** (no sólo atenuadas): ningún toque ni arrastre abre nada. Además hay un lock síncrono compartido (`MainScreenState.claimDrawerGesture`) que se reclama en el **primer** evento de puntero, así que dos dedos simultáneos en ambas pestañas no pueden abrir los dos paneles: el segundo gesto se rechaza en el mismo frame, antes de recomponer. Para cambiar de panel primero hay que cerrar el que está abierto (tap en su backdrop o su pestaña, drag hacia su borde, o atrás). |
| EDG-20 | Pestaña atenuada, no oculta | `[N]` | La pestaña del lado que se abre se desvanece de forma **continua y sincronizada** con la fracción de apertura del drawer contrario (no de golpe al terminar): arranca con el propio gesto o con la animación del tap. Sigue visible mientras dura, sin desaparecer abruptamente. |
| EDG-21 | Zona de arrastre amplia | `[N]` | La pestaña del drawer abierto tiene una zona de arrastre de **90 dp** (48 → 72 dp en 2026-10-16, 72 → 90 dp el 2026-10-22) y la franja de borde del panel abierto está activa, así que cerrar por drag ya no exige empezar justo sobre la pestaña. |
| EDG-24 | Gestos del sistema vs. arrastre desde el borde | `[N]` | Con navegación por gestos, el gesto de "atrás" de Android se queda con el primer toque de cada borde y el arrastre nunca llega a la app: la pestaña se podía tocar pero no arrastrar (owner, 2026-10-22). Se pide la exclusión con `Modifier.systemGestureExclusion()` en la pestaña (200 dp de alto, que es el tope por borde) y además `View.setSystemGestureExclusionRects` a nivel de la ventana en `MainActivity`. **En el Redmi Note 9S (MIUI, Android 11) el sistema la ignora**: medido con `adb shell input swipe` a la altura de la pestaña, arrancando en x=2/26/45 px el gesto se lo queda el sistema (la app va al launcher) y desde x=60 px llega a la app y abre el panel. O sea: ~20 dp de cada borde son del sistema en este teléfono. Mitigación: las zonas táctiles crecieron 25 % (franja 48 → 60 dp, pestaña 76 → 95 dp de ancho, 160 → 200 dp de alto), así que el arrastre funciona si el dedo arranca un poco adentro de la pestaña. Queda pendiente decidir si la pestaña se dibuja 12-24 dp adentro del borde para que el dedo caiga fuera de la franja del sistema. |
| EDG-22 | Orden de capas y hit-testing (causa raíz) | `[N]` | Regla de oro, documentada en `EdgeDrawer.kt`: (1) Un `draggable`/`clickable` con `enabled = false` **sigue registrando** un nodo de input en Compose y **sigue siendo la ruta de hit-test más alta**, por lo que el drawer inactivo apilado a pantalla completa se comía todos los toques del backdrop del activo. Solución: no adjuntar el gesto en absoluto cuando no puede actuar (`.then(if (enabled) Modifier.draggable(...) else Modifier)`); un nodo de layout sin input es ignorado por el hit-test y puede seguir dibujándose (para el fade). (2) Un `AndroidView`/WebView es una View hija real: se lleva todo toque dentro de sus límites y ningún hermano Compose lo pinta encima, así que debe ser el **primer** hijo (abajo), los drawers van por encima y su `pointerInteropFilter` devuelve `true` mientras un drawer está abierto. (3) El backdrop debe ir **antes** del panel y la pestaña, y a pantalla completa. |
| EDG-23 | El drawer inactivo no compone ningún gesto | `[N]` | Un drawer cerrado cuyo contrario está abierto no tiene **ningún** nodo interactivo: ni franja de borde, ni pestaña, ni `draggable` en el panel (el panel sólo se arrastra cuando está abierto o en movimiento, `isOpen \|\| fraction > 0.001`). Esto cierra el último resquicio: deslizar desde el borde extremo ya no puede abrir el panel opuesto por encima de uno abierto. |

---

## 4. Avatar stage

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| STG-1 | Avatar 3D | `[A]` | `models/juan.glb` o `models/pauline.glb` renderizado con Three.js en el WebView (protagonista visual). El elegido en Ajustes se carga una sola vez y **trae adentro las 50 señas como clips base** `0000`..`0049` más sus variantes faciales (300 clips, desde el 2026-10-26), reproducidas con `THREE.AnimationMixer` sobre el mismo esqueleto; solo se usa el clip base. La normalización de escala/offset va en un pivot **padre**, porque el clip anima la raíz del armature. |
| STG-2 | **Hint de estado** | `[P]` | Arriba a la izquierda, dot "live" + texto del estado (`STA-*`). |
| STG-3 | **Caption** | `[P]` | Abajo al centro: `LSC · Avatar 3D` (dot separador). |
| STG-4 | Botón **Repetir** | `[P][N]` | Visible solo en estado `done`; repite la última frase. **2026-10-14:** el hueco con la barra pasó de 64 dp a `AvatarStage.RepeatGap` = **32 dp**, a pedido del owner ("reducimos la separación a la mitad, pero no lo peguemos"). El inset sigue siendo la altura medida de la barra (`bottomInset`), así que el botón nunca la pisa. |
| STG-5 | Escena con glow/sombra de piso | `[P]` | Glow radial detrás + elipse de piso (en el prototipo; el 3D real los reemplaza). |
| STG-6 | Animación "is-signing" | `[P]` | Feedback visual mientras reproduce (en la app: el propio avatar animándose). |
| STG-7 | Área del avatar respeta el layout | `[P]` | Ocupa el alto disponible entre safe-area superior y el control panel. |
| STG-8 | Long-press resetea pose/cámara | `[A]` | Long press (500 ms) en el canvas resetea pose + cámara (`app.js`). El reset usa la misma función que el arranque (`frameCamera()`), así que el encuadre por defecto está escrito **una sola vez** (`STG-9`): antes eran los mismos números mágicos duplicados en los dos lados y cambiarlos en uno dejaba el otro en el viejo. |
| STG-9 | Encuadre por defecto | `[N]` | **2026-10-14.** Cámara en `(0, 1.50, 2.30)` y punto de mira en `(0, 1.10, 0.00)`, fov 45°. Los dos se suben **juntos** por el mismo `FRAME_LIFT` = **0.15** sobre los valores anteriores (1.35 / 0.95): una traslación rígida de la cámara deja **el ángulo con el que se mira al muñeco idéntico** y sólo corre el encuadre hacia abajo, que es lo que pidió el owner ("subamos un poco la cámara, como ponernos más altas… sin alterar el ángulo"). Antes la cabeza rozaba el borde superior y el aviso de estado le caía encima; ahora queda ~215 px de aire arriba de la cabeza. Medido: el pelo arranca en y≈320 px en vez de y≈150. |

---

## 5. Control panel (barra inferior)

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| CTL-1 | Campo de texto | `[P][A]` | Entrada de frase en español, placeholder "Escribe una frase...". |
| CTL-2 | Enviar con **Enter** | `[P]` | El botón de acción del teclado (`ImeAction.Done`) **siempre cierra el teclado**, y además manda `send()` si la frase se puede mandar. Son dos cosas separadas a propósito: si el `onDone` dependiera de `sendEnabled`, con el micrófono prendido —donde el campo es de solo lectura y no hay nada que mandar— el botón no cerraba nada y el usuario se quedaba sin salida. Guard de composición IME (`isComposing`/`keyCode 229`). |
| CTL-3 | Botón **Historial** | `[P][A]` | Ícono a la derecha del input; abre el bottom sheet (o dropdown). |
| CTL-4 | Botón **Traducir** | `[P][A]` | Ícono + label; ancho 75%. Deshabilitado si input vacío o estado `Procesando` (`STA-3`). |
| CTL-5 | Botón **Micrófono** | `[P][A]` | Ancho 25%. El estado de grabación es `Grabando` (`STA-2`); visual "activo" mientras escucha. |
| CTL-6 | Mic real | `[A]` | **Corregido 2026-10-14.** `data/AudioCapture.kt` captura con `AudioRecord` sobre `AudioSource.VOICE_RECOGNITION` y manda chunks de 30 ms de PCM16 mono a 16 kHz por el mismo WebSocket de traducción. `VOICE_RECOGNITION` y no `MIC` a propósito: es la fuente afinada para reconocimiento, así el STT escucha la voz en vez del AGC bombeando. El audio crudo va al backend, que es el único que habla con el STT. |
| CTL-7 | Estado **activo** del mic | `[P]` | Fondo strong + ring cuando `Grabando` y el modo es dictado. |
| CTL-8 | Estado **disabled** de Traducir | `[P]` | Opacidad reducida cuando no se puede enviar. |
| CTL-9 | Foco del input | `[P]` | Borde strong + ring al enfocar. |
| CTL-10 | Home indicator | `[P]` | Barrita inferior 112×4 (`strong` al 70%). **Se conserva** por fidelidad con el prototipo. |
| CTL-11 | Safe area / IME | `[N]` | Respeta la barra de navegación y el teclado con **un solo** inset de fondo: `WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)` (el mayor entre barra de navegación y teclado), aplicado después del `background` para que el panel siga llegando al borde de abajo. Antes era `navigationBarsPadding().imePadding()` sumados —el teclado ya se dibuja encima de la barra de navegación— y encima el manifest tenía `windowSoftInputMode` en `<application>`, donde el sistema lo ignora y queda `adjustPan`: el sistema paneaba la ventana y el inset la volvía a subir. Resultado: ~535 px de hueco entre el teclado y los botones (medido el 2026-10-22 en el Redmi; después del arreglo son 33 px, el padding de 12 dp del panel). |
| CTL-12 | Controles bloqueados durante un replay | `[N]` | Desde "Repetir" (stage o drawer) hasta que la seña termina, todos los controles (mic, Recientes, input, Traducir) quedan deshabilitados y atenuados al 35% (`replayPending`, se libera con `onPlaybackDone`). | **Corregido 2026-10-14:** el flag lo prendía `repeatTurnFromDrawer` —el único de los dos Repetir— y lo soltaba **el primer fragmento**, que es justo cuando la seña empieza a representarse: el panel volvía a quedar habilitado a los ~50 ms del toque, con la seña todavía corriendo. Ahora lo prende `requestTranslation` cuando el replay es real (stage o drawer) y lo sueltan `onPlaybackDone`, `fail()` y el caso "la frase no produjo ninguna seña". Se prende **después** de los cuatro descartes tempranos de `requestTranslation` a propósito: desde cualquiera de ellos el panel quedaría muerto para siempre, sin Repetir que lo vuelva a prenda
| CTL-13 | Tocar un control cierra el teclado | `[N]` | **2026-10-14.** Micrófono, TR, Traducir, Historial y los tiradores de los drawers cierran el teclado **y no vuelve**: `rememberKeyboardDismissAction()` hace `focusManager.clearFocus(force = true)` y después `keyboard?.hide()`. **Con solo `keyboard?.hide()` el teclado se iba un segundo y volvía**, porque el `TextField` seguía con el foco y la siguiente recomposición lo volvía a mostrar; el `force = true` es porque un campo `readOnly` ignora el cierre normal. Cerrar el teclado **no manda la frase**: el texto se queda en el campo. |
| CTL-14 | Permiso de micrófono **al abrir** | `[N]` | **2026-10-17 (A2).** `RECORD_AUDIO` se pide en un `LaunchedEffect(Unit)` de `MainScreen`, al abrir la pantalla principal, y **no** en el primer toque del micrófono. Lo piden el SRS (`main.tex:837`, "La primera vez que se abre la aplicación, esta solicita el permiso de micrófono") y `RF-UI-04`. Antes el diálogo aparecía con el dedo del usuario ya encima del control, que es la peor forma de pedir un permiso. Concederlo no prende el micrófono —eso sigue siendo del toque del interruptor—, y denegarlo **no rompe nada**: la app trabaja solo con el texto, con el aviso de 3 s de `onMicPermissionDenied()`. El camino del toque del interruptor **se conserva como red de seguridad** (`withMicPermission`): si al abrir no se concedió, volver a tocar el micrófono reintenta, y ahí el aviso sale siempre, porque sin él el control no hace nada y el usuario no sabe por qué.<br>· **La pregunta sale una sola vez, y para acordarse de eso hace falta un flag propio** (`data/MicPermissionPrompt.kt`, clave `mic_prompted` del archivo de preferencias `signai`). **No sirve `shouldShowRequestPermissionRationale()`**: devuelve `false` tanto si nunca se preguntó como si ya no se va a preguntar nunca, y en MIUI —el teléfono donde se prueba— **una sola negativa deja el permiso en `USER_FIXED`**: el diálogo no vuelve a aparecer y el sistema responde `false` en el acto. Con esa señal como criterio, la app nunca avisaba que el micrófono quedó bloqueado, que es justo lo que `RF-UI-04` pide. Medido en el Redmi Note 9S el 2026-10-17. |

---

## 6. Historial

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| HIS-1 | Bottom sheet | `[P]` | Modal con handle, título "Historial reciente" y eyebrow "TUS FRASES". |
| HIS-2 | Lista de frases | `[P][A]` | Una fila por frase reciente. |
| HIS-3 | Acción **Reutilizar →** | `[P]` | Tocar una frase la carga en el input, cierra el sheet y la envía. |
| HIS-4 | Persistencia | `[A]` | Guardado en SharedPreferences (`signai`/`history`). |
| HIS-5 | Cap de items | `[A]` | Cap real 20; top-5 nunca se reordena. (El prototipo usa 5.) |
| HIS-6 | Dedupe | `[P]` | Sin duplicados case-insensitive; el más reciente primero. |
| HIS-7 | Estado vacío | `[N]` | Sin historial → placeholder en vez de lista. |
| HIS-8 | Cerrar | `[P]` | Tap en scrim o botón X. |

---

## 7. Drawer izquierdo — Conversación

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| CNV-1 | Header | `[P]` | Eyebrow "CONTEXTO" + título "Conversación" + cerrar. |
| CNV-2 | Turno **TÚ** | `[P]` | Por cada frase enviada: label "TÚ" + la frase del usuario. |
| CNV-3 | Turno **TRADUCCIÓN** | `[P]` | Por cada turno: label "TRADUCCIÓN" + gloss LSC (`GLS-*`). |
| CNV-4 | Registro de sesión | `[N]` | La lista crece con cada frase enviada; cada turno entra con animación y la vista se desplaza al último. Vive solo mientras el proceso está abierto (`ConversationTurn`, en memoria; no se persiste). |
| CNV-5 | Botón **Repetir** por turno | `[N]` | A la derecha de la traducción de cada turno; repite la seña de ese turno sin crear uno nuevo. El botón del stage (`STG-4`) sigue repitiendo la última frase. |
| CNV-6 | Nota de gesto | `[P]` | "Desliza desde el borde para volver". |
| CNV-7 | Estado vacío | `[N]` | Sin turnos: "Aún no hay frases en esta conversación." (no hay bloques estáticos). |
| CNV-8 | Sin estado "Esperando" | `[N]` | Se elimina el bloque de estado del avatar; ninguna sección dice "Esperando traducción". |
| CNV-9 | Repetir cierra el drawer antes de reproducir | `[N]` | Repetir en un turno cierra el panel izquierdo, espera 500 ms (animación de cierre) y recién ahí reproduce la seña. |

---

## 8. Drawer derecho — Ajustes

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| SET-1 | Header con logo | `[N]` | Tile con el **icono real de la marca** (`ic_signall.png`) + cerrar. Ver `BRD-5`. |
| SET-2 | Sección **AVATAR** | `[P]` | Título de sección. |
| SET-3 | **Personaje** (picker) | `[P]` | Dropdown con Juan y Pauline + dots de color + check del seleccionado. Ver `PER-*`. |
| SET-4 | **Velocidad de señas** | `[P]` | Slider 10–100 (ícono SpeakerHigh). |
| SET-5 | Sección **ACCESIBILIDAD** | `[P]` | Ver `ACC-*`. |
| SET-6 | Sección **APARIENCIA** | `[P]` | Ver `APP-*`. |
| SET-7 | Link **Acerca de** | `[P]` | Ícono Info + "Acerca de" + chevron; abre About. |
| SET-8 | Sección **AVANZADO** | `[A][N]` | Al final: Modo debug, Cámara libre, Suelo (grilla), Fondo bosque, selector de Seña. |
| SET-9 | Fila de setting reutilizable | `[N]` | Ícono + label + control (switch/slider), min-height 50. |
| SET-10 | Dropdown de personaje | `[P]` | Trigger con chevron que rota al abrir; menú anclado con animación `dropdown-in`. |
| SET-11 | Menú **debajo** del trigger | `[A]` | Las opciones se abren **debajo** del botón y con su mismo ancho, no flotando descuadrado. El `Popup` de Compose ignora `alignment`/`offset` en Android 11: la ventana se estira a `fillMaxSize()` y el menú seUbica con un `offset` normal medido contra `view.rootView.getLocationOnScreen`. Si no cabe abajo, **sube**. |
| SET-12 | Menú largo scrolleable | `[A]` | El selector de Seña tiene 32 opciones y no entraban todas: la lista interna lleva `verticalScroll`. Es el mismo `Dropdown` del picker de Personaje (`SET-11`), que por eso no podía llegar a la opción 27+. |

### 8.1 Personajes (selector funcional, dos avatares)

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| PER-1 | 2 personajes | `[N]` | **Juan** y **Pauline**; default **Juan** (2026-10-26). Reemplaza a Julián, Samuel y Ximena, que nunca llegaron a la escena: `Character` no cambiaba el modelo y el avatar era siempre el mismo archivo (owner, 2026-10-16). Las preferencias guardadas con los nombres viejos se normalizan a Juan al leerlas. |
| PER-2 | Dots de color | `[P]` | Cada personaje con su color (Juan `#765044`, Pauline `#a46b54`), en el orden de la lista. |
| PER-3 | Check del seleccionado | `[P]` | Ícono check en la opción activa. En **dark** el check va en `colors.text` (blanco) y no en `strong`: el acento `#1c4cfc` sobre la superficie oscura da 2.8:1, por debajo de 3:1. |
| PER-4 | **Cambia el avatar de verdad** | `[N]` | La selección se persiste y la escena carga `models/juan.glb` o `models/pauline.glb` en caliente (2026-10-26). El modelo nuevo se parsea antes de liberar el anterior: si falla, queda el viejo en pantalla y se avisa. |
| PER-4b | **Solo en reposo** | `[N]` | El selector se habilita en `Inactivo`, `Desconectado` y `Error`, y se deshabilita con captura, frase lista o enviada, traducción, cola o repetición en curso. Mientras el avatar carga, la barra de abajo y el botón de reproducir quedan bloqueados. Así se cumple la regla del owner: el avatar solo cambia cuando no hay operación activa. |
| PER-5 | Semántica listbox | `[P]` | Roles listbox/option, `aria-expanded`, `aria-selected`. |

---

## 9. Accesibilidad

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| ACC-1 | **Tamaño de interfaz** (stepper) | `[P]` | Escala de la UI (`interfaceSize` 0–100 → `uiScale` 0.9–1.1). El owner cambió la barra por **botones − / +** el 2026-10-16: en un drawer angosto y con la fuente escalada apuntar a un valor exacto es difícil, y no había forma de saber en qué punto se estaba. Pasa de a 5 (0.01 de densidad) con el valor actual en negrita **centrado entre los dos botones**; `−` deshabilitado en 0 y `+` en 100, con `contentDescription` propio en cada botón. |
| ACC-2 | **Texto en negrita** | `[P]` | Aumenta el peso tipográfico global. |
| ACC-3 | **Contraste aumentado** | `[P]` | Sube el contraste de líneas/bordes (`--line`). |
| ACC-4 | **Texto más grande** | `[P]` | Aumenta el tamaño base de fuente. |
| ACC-5 | **Reducir animaciones** | `[P]` | Desactiva/limita animaciones. |
| ACC-6 | `prefers-reduced-motion` | `[P]` | En la app: respetar también el ajuste del sistema. |
| ACC-7 | Switches con estado | `[P]` | Switch accesible (rol switch, estado anunciado). |

---

## 10. Apariencia / Theming

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| APP-1 | **Modo claro** | `[P]` | Tema claro con su paleta. |
| APP-2 | **Modo oscuro** | `[P][A]` | Tema oscuro con **la paleta de marca SignALL en grafito** (`#121316` fondo, `#1a1b1f` superficie, `#25272c` suave, `#33363d` borde, `#f5f4f0` texto, `#a5a7ae` secundario, `#1c4cfc` azul de acción), no la del prototipo. Reemplaza la paleta navy que había quedado "demasiado azul" esa misma mañana (Samuel Osorio, 2026-10-16). **El default de la app es oscuro** (`Settings.darkTheme = true`, owner 2026-10-16). La referencia de marca es `docs/manual-identidad-visual.md`. |
| APP-2b | **Ámbar de captura** | `[A]` | Los tokens `live`/`onLive` (`#f2b84b`/`#1c1503`) marcan **solo** cuando la app está escuchando: micrófono y TR encendidos. No es decoración ni se usa en botones que no capturan audio, y nunca es la única señal (el control también cambia de etiqueta o descripción para lectores de pantalla). |
| APP-3 | **Switch de tema** | `[P]` | Alterna; el ícono/label cambia (Moon ↔ Sun, "Modo oscuro" ↔ "Modo claro"). |
| APP-4 | Tokens de color temables | `[P]` | `background`, `foreground/text`, `muted`, `line/outline`, `surface`, `strong`, `soft`, `primary`, `live`/`onLive` (`APP-2b`). |
| APP-5 | Transición de tema | `[P]` | Cambio de color suave (250 ms). |
| APP-6 | Branding centralizado | `[N]` | Nombre, iconos y wordmark en **un solo lugar** (`SignAiBranding`): `Name`, `Eyebrow`, `Icon`, `logo(darkTheme)`. Ver `BRD-1`. |
| APP-7 | Persistencia del tema | `[N]` | Recordar la elección entre sesiones. |

### 10.1 Paleta del prototipo (referencia)

| Token | Light | Dark |
|---|---|---|
| `--background` | `#f4f4f2` | `#111110` |
| `--foreground` | `#171717` | `#f5f5f1` |
| `--muted` | `#777773` | `#969690` |
| `--line` | `#deded9` | `#32322f` |
| `--surface` | `rgba(255,255,253,.92)` | `rgba(30,30,28,.94)` |
| `--strong` | `#171717` | `#f5f5f1` |
| `--soft` | `#e8e8e3` | `#292926` |
| `--radius` | `24px` | `24px` |

Modos: `.theme-dark`, `.font-bold` (w650), `.font-large` (17px),
`.high-contrast` (`--line #777`).

---

## 11. Máquina de estados de reproducción

Los siete estados son **los del SRS** (`tab:estados-ui`, `main.tex:726-773`), con sus
nombres: `Inactivo`, `Grabando`, `TextoListo`, `Procesando`, `Reproduciendo`, `Error`,
`Desconectado`. El enum `PlaybackState` los tiene exactamente, en español y con
mayúscula inicial.

Antes eran nueve, con nombres que la app se había hecho sola
(`docs/CONTRASTE-APPvsDOCUMENTOS.md` §2, ítem A7). Tres de los nueve —"Traduciendo",
"Conectando" y "Reproducción completada"— no existen en el SRS y eran justo donde la
app pasa casi todo el tiempo esperando; y faltaban dos de los siete, *Error* y
*Desconectado*, que son los que explican lo que le pasa al usuario.

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| STA-1 | Estado **Inactivo** | `[P][N]` | Texto en pantalla **"Inactivo"**: el nombre del estado, tal cual. Decidido con el owner el 2026-10-17 — antes decía "Listo para traducir" y ese texto no está en el SRS. La tabla `tab:estados-ui` no prescribe textos literales (su columna es "qué muestra la pantalla"), así que el nombre del estado es lo que se lee. |
| STA-2 | Estado **Grabando** | `[P][N]` | Texto en pantalla **"Grabando"**. Un solo estado para los dos modos: el SRS tiene uno solo, así que el enum no puede decir si se está dictando o escuchando. El modo sigue en `micMode`, que es lo que muestran el ícono del micrófono, su `stateDescription` y el log. Antes la etiqueta decía "Dictando..." o "Escuchando..." según el modo; ya no: el indicador no se inventa textos propios. |
| STA-3 | Estado **Procesando** | `[P][N]` | Texto en pantalla **"Procesando"**. Fusiona los antiguos *processing* (esperar la transcripción) y *translating* (esperar las glosas): para quien espera es la misma espera, y el SRS tiene un solo estado. El que sí bloquea el panel es este. |
| STA-4 | Estado **Reproduciendo** | `[P]` | Texto en pantalla **"Reproduciendo"**, o **"Seña no disponible"** cuando alguna palabra de la frase no tiene seña: es el texto alternativo de este estado y el único texto propio que la tabla permite (`main.tex:753`, fila *Reproduciendo*). **No bloquea el panel**: lo que no se puede tocar es lo que está en vuelo (ver `STA-18`). |
| STA-5 | Estado **Texto listo** | `[N]` | Texto "Texto listo". Es el tercer estado de RF-UI-01 y lo define el SRS (`main.tex:746`): el texto está en la barra, se puede editar y espera a que el usuario pulse Traducir.<br>· **Se deduce, no se asigna**: `MainScreenState.stateForUi` devuelve `TextoListo` solo cuando la máquina está `Inactivo` y se cumple `dictationReadyToSend` (micrófono apagado, campo con texto, nada retenido). Se deduce porque depende de tres cosas a la vez y cualquiera la deshace; si fuera un estado asignado habría que acordarse de volver a ponerlo, y cuando se olvidaba tapaba *Desconectado*.<br>· Con el TR prendido **no** sale, aunque haya texto pendiente: el campo está mostrando la transcripción en vivo, no lo que quedó esperando, y marcarlo sería señalar algo que el usuario no está viendo.<br>· Sustituye al aviso de 3 s "Texto listo. Toca Traducir para enviarlo.", que se perdía a los 3 s. Ese aviso **sigue** saliendo, pero ya no es la única señal ni la persistente. |
| STA-6 | Estado **Error** | `[N]` | Texto "Error". Antes los fallos eran un mensaje transitorio de 3 s **encima** de la etiqueta de otro estado, así que el indicador decía "Listo para traducir" mientras la app acababa de fallar. Ahora `fail()` pone el estado y el mensaje va encima (`STA-14`). El estado vuelve a `Inactivo` a los 3 s, que es lo que dura el mensaje, y solo si en el medio no pasó nada más: si se mandó una frase o reconectó, lo que hay que mostrar es eso. |
| STA-7 | Flujo de envío | `[P]` | `send()`: `Inactivo → Procesando → Reproduciendo`. El prototipo usa 850 ms/2700 ms fake. |
| STA-8 | **Fin real de reproducción** | `[N]` | La vuelta a reposo usa un callback JS→Kotlin (`onPlaybackDone`), no un timer. Lo que antes era el estado `Done` es ahora el flag `doneVisible` (`STA-21`). |
| STA-11 | Estado **Desconectado** | `[N]` | Texto en pantalla **"Desconectado"**. Fusiona *connecting* y *offline*: el SRS no distingue los dos momentos. **No hay mensaje encima y no parpadea**: se queda quieto en los reintentos, sin alternar con un aviso. Antes ponía "Inactivo" + un aviso de 3 s y, como el reintento es cada 5 s, los dos textos se turnaban sin parar. |
| STA-12 | Reconexión sin límite | `[N]` | Espera creciente de a 1 s **hasta un máximo de 5 s**, indefinida, con el contador **reiniciado** cuando la conexión vuelve (`MainScreenState.reconnectAttempts`, que pone cero en `BackendEvent.Connected` y no al perderse la sesión). Antes el contador subía para siempre: después de unas horas de app abierta la primera caída esperaba 5 s en vez de 1 s. Y el estado se ponía `Desconectado` **sin condición**: antes solo si el estado no era Traduciendo ni Reproduciendo, así que una caída a mitad de una frase dejaba el indicador clavado, con Traducir deshabilitado y el envío manual rechazado. |
| STA-21 | Pérdida de conexión con un turno en vuelo | `[N]` | `MainScreenState.onDisconnected()`, que antes era `Unit`. El SRS separa dos casos (`main.tex:761-772`) y se tratan distinto:<br>· **Procesando, sin ninguna seña ejecutada** → la frase se cancela y su texto vuelve al campo **editable**: se suelta la retención y Traducir vuelve a estar disponible en cuanto reconecte. El usuario nunca la vio representada.<br>· **Reproduciendo** → la representación **se interrumpe de verdad**: `SignAI.stop()` corta el mixer y vacía la cola en el JS. Sin eso la interfaz volvía a reposo mientras el canvas seguía con la seña adelante. El texto **no** vuelve al campo, porque ya salió de ahí y ya se mandó.<br>· **El micrófono se apaga** en los dos casos, con el aviso "Se perdió la conexión con el servidor. Se está reconectando." Decisión del owner (2026-10-17). Cortar es más honesto que dejar el micrófono grabando hacia un socket muerto, y le dice al usuario de una vez que tiene que volver a prenderlo. **El SRS dice que en modo TR el micrófono permanece encendido (`main.tex:709`), así que este punto hay que documentarlo allá** — está anotado en `docs/CONTRASTE-APPvsDOCUMENTOS.md` §9.<br>· Lo que se dijo en vivo y no llegó a salir vuelve al campo, porque "el texto pendiente no se pierde". |
| STA-9 | Flujo del mic | `[A]` | **2026-10-15, reescrito por el flujo por oraciones.** El auto-relleno del prototipo (eliminado el 2026-10-14) sigue eliminado: hacía que la app *pareciera* estar escuchando sin capturar nada. Ahora hay **tres** caminos de entrada y los dos de voz terminan en el mismo punto —una frase mandada como `{"type":"text"}`—, que es lo único que el backend traduce (`GLS-11`).<br>• **Dictado** (🎤): `changeMicMode(Dictation)` → `audio.start()` → se mandan chunks PCM16 → el backend devuelve `transcript` y el campo muestra la transcripción en vivo → el STT cierra la frase a los 1,5 s de silencio → llega el `final` → **la captura se apaga sola** y el texto queda a la espera de confirmación (`STA-17`). En este modo no se manda nada.<br>• **En vivo** (◢◢ TR): mismo arranque, pero el `final` **no** apaga el micrófono: la frase sale sola y el interruptor sigue prendido (`STA-16`).<br>• **Texto escrito**: el campo y Traducir, que es lo que ya existía.<br>· Apagar el micrófono manda `{"type":"end"}`, que cierra el utterance del STT. Si en toda la sesión no volvió nada, se vuelve a `Inactivo` de inmediato con "No se escuchó nada." en vez de esperar el timeout y reportar un fallo que no ocurrió (`STA-19`).<br>· **Una frase sin ninguna seña representable ya no deja la app trabada.** Es un caso real de una glosa que el catálogo anuncia pero cuyo clip no está en el APK —hoy no hay ninguna: las 50 glosas de LSC50 tienen animación desde el 2026-10-22—, y una frase puede caer entera en ese hueco. Antes la app se quedaba en *Procesando* para siempre, con el Traducir deshabilitado, porque la escena nunca recibía la cola, nunca sonaba y nunca avisaba que terminó, así que el `done` del backend veía la cola "arrancada" y no cerraba el turno. Ahora: `queueStarted` solo se prende cuando la escena recibió la cola de verdad, y el `done` cierra el turno. El bug de al lado —la primera oración vacía y la segunda con seña, que entraban por `appendQueue` sin que nadie hubiera llamado `setQueue` ni `play()`, y la frase no se representaba— quedó arreglado con el mismo cambio.<br>· **El `final` puede estar vacío y hay que tolerarlo**: el STT responde a **todo** `end`, incluso al que no tiene audio detrás (`flush()` responde siempre). Un `final` sin texto en dictado es "no se escuchó nada" (`onEmptyFinal`); no es un error, y no es motivo para tocar la interfaz más de lo que ya se tocó. |
| STA-16 | Micrófono continuo (interruptor) | `[N]` | **2026-10-15.** El estado del mic es un **enum de tres valores** (`MainScreenState.micMode`: `Off`/`Dictation`/`Live`) y es **independiente** de `playbackState`, que ya no dice si el mic está prendido. Un enum y no dos flags porque los dos modos comparten un `AudioRecord` y un WebSocket: "los dos prendidos" sería un estado que la app no puede representar. Ver `STA-17`.<br>· **Solo `Live` es continuo.** En `Dictation` el `final` del STT **apaga la captura**: la frase se terminó y no hay nada más que escuchar hasta que el usuario vuelva a tocar. Por eso `dictationBuffer` ya no acumula entre frases — con 1,5 s de silencio el STT corta solo, y lo que hay entre una frase y la siguiente lo decide el usuario con una pausa.<br>· Un `final` en `Live` **no** apaga el mic: solo dice "esta frase se cerró". El STT abre el siguiente utterance en cuanto escucha voz de nuevo. **Nunca uses `playbackState == Grabando` como "el mic está prendido"**: con el mic prendido y el muñeco representando, el estado es `Reproduciendo`. Para saber si el mic está prendido está `micActive`.<br>· Al terminar una frase o una seña el estado vuelve a `Grabando` (con la etiqueta del modo activo), no a "reproducción completada": el botón tiene que verse prendido mientras captura, o el usuario no sabe si tocarlo (`resumeListeningIfMicOn`).<br>· El botón de mic nunca se deshabilita mientras esté prendido: prendido, cada uno es el único que puede cortar su modo, así que bloquearlo dejaría al usuario sin salida.<br>· Con el mic prendido **Traducir y el Historial quedan deshabilitados** y el input es `readOnly`: texto y voz van por el mismo socket y el backend no puede saber de quién es cada fragmento; mezclados, el avatar representaría la frase al revés. Son dos formas de mandar, no dos a la vez. El input sigue mostrando la transcripción en vivo, que es lo que el usuario quiere ver: por eso `readOnly` y no `enabled`, que lo atenuaría hasta volverlo ilegible.<br>· Las métricas (`SignAiMetrics`) se acumulan por sesión y se reinician al prender el mic. |
| STA-17 | Dos modos de voz sobre un solo micrófono | `[N]` | **2026-10-15.** 🎤 (fila 1) es **dictado**: se toca, se habla, el silencio cierra la frase, la captura se apaga sola y **el texto espera**. ◢◢ TR (fila 2) es **en vivo**: se toca, el mic queda prendido y cada frase sale sola al cerrarse. Los dos son interruptores —ninguno se mantiene apretado— y **no pueden estar prendidos a la vez**: comparten la captura y el socket. Por eso **cada uno deshabilita al otro, en los dos sentidos** (`STA-16`).<br>· **Regla que gobierna los dos: apagado no es mandar.** Solo el modo en vivo manda sin que nadie toque Traducir. En dictado, apagar deja el texto a la espera (`RF-STT-03`). Antes era al revés (`stopDictation` llamaba `sendDictation`), y por eso `trazabilidad.md` §2.1 tenía RF-STT-03 y RNF-REL-04 en "No implementado".<br>· **El tercer estado de RF-UI-01 no es un ícono, es un estado.** La v0.2 pedía tres iconos del micrófono y que el tercero mandara la frase. La parte del ícono se implementó y se probó en el Redmi: con un ícono distinto el botón deja de leerse como "micrófono", y la primera reacción de quien lo usa es querer dictar otra vez, no confirmar. **El micrófono no cambia de icono** y sigue siendo la única forma de dictar de nuevo; la señal persistente es el estado `TextoListo` de la etiqueta del escenario (`STA-5`), que además es lo que pide el SRS. Decidido con el owner el 2026-10-17, que es cuando la SRS definía *Texto listo* como estado de la tabla. Desviación de RF-UI-01 en la **forma**, no en el fondo: los tres estados existen, el envío es Traducir.<br>· Tocar 🎤 con texto esperando **lo descarta y vuelve a dictar de cero**: es una cancelación explícita, que es lo único que RNF-REL-04 permite como pérdida. El texto de una sesión en vivo, en cambio, **no** se descarta: ese es del micrófono, no del usuario.<br>· **Un control deshabilitado se marca apagando el icono y la etiqueta a `muted`, nunca bajándole el alfa a la superficie.** La trampa: estos dos botones se rellenan con `soft` sobre una barra `surface`, y `soft` al 35 % de alfa da `#F8F8F5` sobre `#FEFEFC` — dos colores que a simple vista son el mismo. El botón parecía habilitado y no lo estaba, y el owner lo reportó como "el otro no se deshabilita". Traducir sí se notaba, porque su relleno es `strong`: atenuado queda gris medio.<br>· Cambiar de un modo al otro **no reinicia la captura** (`AudioRecord` tarda cientos de ms en abrir y el usuario ya estaba hablando): se manda `end` para que el STT cierre el utterance del modo que se deja. **Ya no se manda `mode`** (`sendMode` se eliminó): el backend no decide qué traducir, así que no tiene nada que decidir con eso.<br>· **No hay `streamMutex` ni carrera que ordenar**: el backend manda las oraciones **en orden** y la app las encadena por `index` (`GLS-12`).<br>· Traducir funciona siempre: `handle_text` no mira de dónde vino el texto, porque una frase escrita *es* la orden de traducirla. |
| STA-18 | La cola manda sobre el enum | `[N]` | **2026-10-15.** `MainScreenState.queuePlaying` sigue siendo la fuente de verdad de "el avatar está representando", y no `playbackState`: el `done` de la última oración llega mucho antes de que el avatar termine, porque una oración de 9 glosas son ~19 s de animación contra los ~2 s que tarda en traducirse. Confundirlas hacía que apagar el micrófono pareciera "no hay nada en curso" y dejaba la interfaz en "Procesando..." para siempre, con el panel entero deshabilitado.<br>· Lo que cambió es **por qué** la cola se adelanta. Con el camino por palabras crecía de a una durante el habla —38 fragmentos, casi 40 s de señas por delante en una prueba de 30 s—; con el flujo por oraciones la cola está **vacía mientras el usuario habla y se llena de golpe** al cerrar cada frase. El desfase de fondo es el mismo (2,15 s de animación por seña), pero ahora llega a lotes. Lo que antes se corregía multiplicando la velocidad ya no se corrige: la velocidad es un ajuste del usuario (`BRG-15`, eliminada).<br>· Por eso **`Reproduciendo` no bloquea el panel**: lo que no se puede tocar es lo que está **en vuelo** (`Procesando`). Mandar otra frase con el avatar representando la anterior reemplaza la cola, que es lo que ya hacía la escena. Un botón habilitado que hace nada es peor que uno deshabilitado, así que `play()` y `replayTurn()` tampoco rechazan en `Reproduciendo`.<br>· El puente `onPlaybackDone` es quien cierra la cola: con el mic apagado vuelve a `Inactivo` y prende `doneVisible` (y aparece "Repetir"), y con el mic prendido vuelve a `Grabando`, con la etiqueta del modo. |
| STA-20 | Lo ya enviado no se vuelve a mandar | `[N]` | **2026-10-15.** El campo conserva la frase que el avatar está representando — pero en `readOnly` y con **Traducir deshabilitado**, durante `SentTextHoldMs` = **1500 ms** (`MainScreenState.sentText`). Sin eso el botón ofrecía reenviar lo que ya se envió, y el avatar representaría la frase dos veces seguidas por un solo toque.<br>· **Son tres los caminos que mandan una frase sin que nadie toque Traducir**, y por eso los tres la retienen: el **en vivo** mientras se hablaba, un **Traducir** a mano (botón o Enter) y el **Repetir**. Ya no son cuatro: el dictado **no** manda solo (`STA-17`), así que su texto es del usuario y no se retiene nunca. El owner pidió explícitamente que el Repetir y el Traducir compartieran la retención ("es la misma situación que lo que sucedía con el botón de tiempo real").<br>· La retención se prende **en `requestTranslation`**, el único lugar por donde pasan las dos últimas, y no en cada llamador: es donde se sabe que la frase **salió de verdad**; si se retuviera en el llamador, un envío rechazado (micrófono prendido, frase en vuelo, sin conexión) dejaría el campo bloqueado sin nada que representar. El en vivo tiene su propio camino (`sendLivePhrase`) y **no** retiene: con el mic prendido Traducir ya está deshabilitado por `micActive`, y la retención solo cambiaría el texto del campo por el de la frase anterior mientras el usuario sigue hablando.<br>· Al cumplirse, **el texto se borra y el campo y Traducir vuelven en la misma asignación**: no existe el estado intermedio de "campo vacío con el botón muerto", que es el que hace que la app parezca trabada.<br>· 1,5 s es lo que pidió el owner, y con 5 s se quejó de que era mucho rato. La frase no se pierde: ya está en el historial y en el panel de conversación.<br>· El aviso "Ya está representado." **no** se muestra mientras el estado es `Procesando`: durante la traducción el campo ya está bloqueado por `busy`, que se explica solo, y pisar el estado con "ya representado" antes de que la frase ni se haya traducido era una contradicción a la vista.<br>· **Apagar el TR deja el campo limpio** en vez de retener (`stopCapture`). Para entonces lo que decía el campo ya se mandó y se representó, así que no hay nada que impedir reenviar — con Traducir de vuelta a estar disponible, dejarlo puesto era una invitación a representarlo otra vez. Lo único que sobrevive es el texto de un dictado sin mandar, que es del usuario y vuelve al campo con su estado de "esperando confirmación" intacto.<br>· Se suelta antes si al campo llega otro texto, si arranca una captura nueva, si arranca un Repetir (`setInput` lo ignora mientras `replayPending`), o si el turno falla (`releaseSentText`): en los cuatro casos el texto del campo ya no es la frase que se mandó.<br>· Los dos momentos tienen log (`texto ya enviado` / `texto retenido borrado`) **a propósito**: 1,5 s es más corto que el ida y vuelta de `mobile-mcp`, así que el árbol de accesibilidad no alcanza a ver la ventana y el logcat es la prueba. |
| STA-19 | Apagar el micrófono | `[N]` | **2026-10-15, cambiado: ahora sí queda algo esperando, y a propósito.** En **dictado**, apagar manda `end` y **espera el `final`** (`dictationAwaitingFinal`): la captura se cortó antes de que la pausa del STT llegara, así que lo que se dijo todavía no está transcrito. Hasta que el `final` llega, el campo muestra la hipótesis que se está formando y el texto todavía puede cambiar. Ese es el único caso en que de verdad hay algo esperando, y es lo que hace que el timeout siga teniendo sentido.<br>· Si la pausa del STT ya había cerrado la frase, no hay utterance abierto y el `end` sería un viaje de ida y vuelta y un `final` vacío de ruido: por eso `stopDictation` recibe `endIsRedundant` y a veces no lo manda.<br>· En **en vivo**, apagar manda `end` y devuelve la interfaz enseguida: `Reproduciendo` si al avatar le queda cola, `doneVisible` si no. No se queda en `Procesando` esperando una transcripción que ya no va a llegar.<br>· En los dos, si no volvió nada en toda la sesión, se vuelve a `Inactivo` de inmediato con "No se escuchó nada." |
| STA-15 | Turno por oración | `[N]` | **2026-10-15.** Ya no hay nada "fragmentado": `streamedSegments`, `streamingGlosses`, `streamingTurn` y `streamMutex` se eliminaron con el camino por palabras. Lo que queda son `pendingSegments` (un `sortedMap` por `index`) y `pendingTotal`, que se limpian con `resetTurn()` al empezar cada frase y en `fail()`: un turno que murió a mitad no puede filtrar oraciones al siguiente.<br>· **2026-10-16 (D-1):** la cola ya no espera a `pendingTotal`. Cada oración que llega entra por `flushSegments`, y la primera del turno usa `setQueue` + `play()` mientras las siguientes usan `appendQueue`. `pendingSegments` y `flushedThrough` quedan igual, pero por otro motivo: el caption, la entrada de conversación y `metrics.onTurn` son de la frase entera. `turnStartedAt` sigue arrancando al mandar la frase.<br>· **2026-10-17 (A13):** el reloj del "tiempo hasta la primera seña" del SRS (`main.tex:848-852`) es `turnSentAt`, que va **aparte** de `turnStartedAt`. Ese lo reinicia `resumeListeningIfMicOn` al terminar cada frase en modo en vivo, y el `done` puede llegar antes de que la escena se mueva: con un solo reloj, esa frase habría medido desde el reloj de la siguiente, o no habría medido. `turnSentAt` solo se pone en los dos lugares donde la app manda una frase (`requestTranslation` y `sendLivePhrase`) y lo borra `clearTurnState`, así que también sirve de cerrojo: un `signStart` de una cola muerta no inventa una muestra. |
| STA-10 | Estado visible en stage + panel | `[P]` | El estado alimenta el hint del stage y el estado del avatar en Conversación. Lo que se lee es `stateLabel`, que sale de `stateForUi.label`: **el nombre del estado**, sin variantes. |
| STA-13 | Hint con glosas | `[N]` | **2026-10-17: las glosas ya no salen en el indicador.** Mientras `Reproduciendo` el hint mostraba las glosas de la secuencia ("glosa-uno · glosa-dos") en vez del estado; ahora el indicador dice el nombre del estado y nada más. Con la depuración de Ajustes → Avanzado prendida, el hint sí muestra `Reproduciendo · <glosas>`. |
| STA-14 | Mensaje transitorio | `[N]` | **2026-10-17: los avisos de 3 s ya no van al indicador.** "Ya está representado.", "No se escuchó nada.", "Sin conexión: no puedo escuchar." y el resto de los mensajes de 3 s no están en la tabla `tab:estados-ui` y el owner pidió que el indicador mostrara solo los estados. Esos textos siguen existiendo y ahora se leen **en el log** (`SignAiState`), donde quedan a la vista del que depura. El único que sí sale en pantalla es "Seña no disponible", que la tabla sí prescribe. El estado `Error` es lo que informa del fallo (fila *Error*: "el indicador de estados muestra la señal de error"), y el texto se conserva y vuelve a `Inactivo` solo (`STA-6`). |
| STA-22 | El indicador: tamaño y color | `[N]` | **2026-10-17.** El indicador de estados (el punto y el texto de la esquina superior izquierda del escenario) es la **única señal de estado de la app**, así que el owner lo agrandó un **25 %**: el punto de 7 dp a 8,75 dp y el texto de `body3` (12/16 sp) a 15/20 sp, escalando también el `letterSpacing` para que la línea no se corte.<br>El **color del punto** dice en qué estado está la app, con **tres colores para los siete estados** —un semáforo, no un color por estado— y los tres son tokens que ya existen en `ui/designsystem/Color.kt`, no se inventó ninguno:<br>· **Azul** (`strong`, el de marca): el camino normal — `Inactivo`, `TextoListo`, `Reproduciendo`.<br>· **Ámbar** (`live`): hay que esperar — `Grabando`, `Procesando`.<br>· **Rojo** (`error`): falló o no hay a quién preguntar — `Error`, `Desconectado`.<br>El ámbar era de `live`/`onLive`, que por manual significa "escuchando ahora" (`docs/manual-identidad-visual.md`); se ensancha a las dos esperas, que es el mismo sentido, y **no** se usa para nada que no esté pasando en ese momento, que es lo que el manual protege. La fila *Error* dice "el indicador de estados muestra la señal de error" y esa señal es el punto rojo; la fila *Desconectado* dice "el indicador de estados lo indica" y también es rojo, porque la conexión caída es un fallo aunque la app se reconecte sola. El texto del punto se acompaña del nombre del estado, así que el color nunca es el único canal. |

### 11.1 La app no se puede quedar congelada

**2026-10-17, decided con el owner.** Estas seis nacieron de una auditoría de los veinte
casos en que la app espera algo, y de un congelamiento real: una frase mandada que
nadie contesta dejaba el estado en *Procesando* para siempre, con los cinco controles
del panel deshabilitados y **sin ninguna salida** — ni apagar el micrófono, ni
reiniciar el backend, ni esperar. El SRS no dice cuánto se espera antes de declarar
un fallo, así que el número de acá es decisión de la app y está anotada en
`docs/CONTRASTE-APPvsDOCUMENTOS.md` §6 #5 con el texto propuesto para el SRS.

La regla que gobierna las seis: **no puede haber bucles**. Todo estado en el que la
app espera algo tiene que tener una salida, y esa salida tiene que dejar la interfaz
en un estado del SRS.

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| STA-23 | **Reloj de silencio de la frase mandada** | `[N]` | `MainScreenState.armTurnSilence()`: si en `TurnSilenceMs` = **15 000 ms** no llega **nada** —ni una oración, ni un `done`, ni una transcripción, ni un error— la frase se declara fallida con `fail("El servidor no respondió.")`. Es el hermano de `armTranscriptionTimeout()`, que protege la transcripción; este protege la traducción.<br>· Es un reloj de **silencio**, no de duración: se rearma con cada frame que vuelve (en `handleEvent` para `transcript` y en `onWholePhraseSegment`), así que una frase larga con muchas oraciones no se mata a mitad. Los 15 s son el mismo plazo de la transcripción y 5 s por encima del tope que el backend le pone al LLM (`llm_timeout_ms = 10000`), así que una frase válida siempre tiene tiempo.<br>· Lo que se pierde es la traducción, **no el texto**: `fail()` suelta la retención, el campo queda editable y la frase se puede mandar otra vez. Es también lo que cierra el "Repetir" sin respuesta, que dejaba el panel entero muerto con `replayPending`. |
| STA-24 | **Latido del WebSocket** | `[N]` | `OkHttpClient.Builder().pingInterval(20 s)` en `SignAiWebSocketClient`. Es lo que hace falta porque el `readTimeout` está en **0** —una sesión es larga—: sin latido, una conexión que muere **sin cerrar el socket** (un túnel `adb reverse`, un NAT que tira el mapeo, un cambio de red) no se detectaba nunca, la app seguía creyendo conectada y una frase se quedaba esperando para siempre sin dejar un solo log.<br>· Es un **frame de control** del protocolo WebSocket, no un mensaje de aplicación: el backend no lo lee, el contrato no cambia y ninguna métrica se mueve. Cuando el pong no vuelve, OkHttp falla el socket y llega `BackendEvent.Disconnected`, que la app ya sabía manejar (`STA-11`, `STA-12`, `STA-21`).<br>· **Medido en el Redmi Note 9S** con el backend falso congelado con `SIGSTOP` (socket abierto, nadie responde): la app detectó la sesión a los ~40 s —un intervalo de ping más el tiempo de espera del pong— y pasó a *Desconectado* sola, con `sent ping but didn't receive pong within 20000ms` en el log. |
| STA-25 | **Apagar el micrófono o el TR cancela la frase en vuelo** | `[N]` | Decisión del owner: el interruptor es también el botón de parar. Antes, apagar el TR con una frase esperando **no sacaba a la app de *Procesando*** —el apagado solo apagaba el micrófono— y el botón de parar no paraba.<br>· `cancelPhraseByMicOff()` trata los dos finales igual que `onDisconnected` (`STA-21`) y por el mismo motivo: si el avatar **ya estaba representando**, el usuario vio parte de la frase y el texto no vuelve al campo, porque ofrecer el reenvío de algo ya representado es el problema que `sentText` existe para evitar. Si **no había ni una seña en pantalla**, la frase no se vio nunca y vuelve al campo editable como *Texto listo*, con `dictationPending`.<br>· En **dictado** el texto **no** vuelve al campo: ahí el campo es del `final` del STT que está por llegar, que es más nuevo que la frase cancelada. La cancelada queda en `lastText`, o sea que el Repetir del escenario la puede mandar.<br>· Solo cancela cuando el apagado lo pidió el **usuario**: los dos caminos donde lo que cierra el dictado es el silencio pasan `cancelInFlight = false`, porque ahí la traducción en vuelo se sigue esperando.<br>· **La cancelación es local** (`MainScreenState.StaleTurn`): el protocolo no tiene un frame para cancelar una frase, así que el backend sigue mandando lo que le falta. Las oraciones de la frase cancelada se descartan y su `done` se traga; si en el medio salió otra frase, de sus oraciones se aceptan (puede colarse alguna de la cancelada) pero el `done` viejo se traga igual, porque si no cerraría el turno nuevo. El `done` no lleva con qué identificar a qué frase pertenece, y esa es una deuda de protocolo anotada en `../sign-ai-backend/docs/CONTRASTE-BACKENDvsDOCUMENTOS.md`. |
| STA-26 | **Una seña que no carga se saltea** | `[N]` | El `catch` de `loadQueueItem` (`app.js`) **no corta la frase**: saltea ese ítem y sigue con el siguiente. Antes llamaba `stopPlayback()` + `notifyPlaybackDone()`, así que el usuario veía al muñeco detenerse y **las demás señas de la frase no se representaban nunca, sin que nada lo dijera**.<br>· Quién decide que el turno terminó sigue siendo `advanceQueue`, que es el único que sabe si la cola se acabó: si **todas** las señas fallan, ahí se da el `onPlaybackDone` y la app vuelve a `Inactivo` como siempre.<br>· El salto se avisa al puente con `signFailed:<índice>` (`BRG-18`) y en pantalla sale **"Seña no disponible"**, el mismo texto que ya usaba una glosa ausente del catálogo. Ahora hay dos motivos distintos con el mismo texto: la glosa que el catálogo no tiene (lo detecta Kotlin antes de entregar la cola) y la que el catálogo tiene pero cuyo `.glb` no bajó (solo se puede descubrir adentro del WebView). |
| STA-27 | **El WebView avisa si muere** | `[N]` | `AvatarWebView` tiene `onReceivedError` (solo marco principal) y `onRenderProcessGone`, y los dos llaman `MainScreenState.onSceneFailed()`, que es un `fail()` y **no vuelve a mentir**: `sceneDead` impide entregar colas nuevas, medir un `signStart` o volver a decir que terminó de representar.<br>· Sin esto el síntoma era el peor de la lista: estado *Reproduciendo* con el muñeco quieto y sin salida.<br>· Los dos caminos **no se tratan igual**, por decisión del owner (2026-10-17):<br>· **`onReceivedError`** — la página no cargó pero el WebView vive: el aviso, se limpia el estado y la app sigue.<br>· **`onRenderProcessGone` devuelve `false`**: el sistema **mata la app** y el launcher la levanta con todo funcionando. Es lo único que sirve, porque un WebView sin proceso de render **no renderiza nunca más**: con `true` la app sobreviviría con un canvas negro hasta que el usuario la cerrara a mano. Perder lo que había en el campo es menos malo que un avatar muerto. El aviso se manda igual, para que quede en el log por qué pasó. |
| STA-28 | **El `done` espera a la cola** | `[N]` | `appendQueue` resuelve los assets en una coroutine —puede suspender bajando un `.glb`—, así que el `done` del backend llegaba a veces antes de que la escena tuviera la frase. `finishTurn` decidía el destino del turno mirando `queueStarted`, que en ese momento todavía era `false`: el resultado era **"esa frase no produjo ninguna seña" sobre una frase que sí se iba a representar**, con la interfaz diciendo *Inactivo* un instante antes de *Reproduciendo*.<br>· `appendsInFlight` cuenta los `appendQueue` vivos y `finishTurn` espera a que se armen (`finishTurnWhenQueueSettles`, techo `TurnSilenceMs` y poll de 16 ms). Es idempotente: la segunda entrada ya ve la cola lista. |
| STA-29 | **La cola se entrega en orden de oración** | `[N]` | **Hallazgo de la propia verificación de `STA-28`, 2026-10-17.** `appendQueue` decidía "esta es la primera tanda" (`!queueStarted`) **antes** de resolver los assets. Con dos oraciones del mismo turno que llegan juntas y resuelven en distinto orden —una cuya seña tarda 2 s en bajarse y otra que resuelve al instante— **las dos salían como la primera**: las dos hacían `setQueue` + `play()` y la segunda **pisaba** la cola que la primera ya estaba representando. La frase se cortaba a mitad, en silencio, y el turno se cerraba sobre una cola que ya no era la de la frase.<br>· Con solo mover la lectura de `queueStarted` adentro de la coroutine el pisamiento desaparece, pero aparece el problema siguiente: la entrega quedaba en **orden de descarga**, no de oración, y el avatar representaba la frase al revés.<br>· La solución es separar las dos cosas: **los assets se resuelven en paralelo** —que es lo que hace que una frase larga no espere una descarga detrás de otra— y **la entrega se serializa** con `queueDeliveryGate`, una cadena de `CompletableDeferred` que se reinicia por turno en `clearTurnState`. Entre resolver y entregar no hay punto de suspensión, así que solo una tanda puede ser la primera.<br>· **Medido en el Redmi Note 9S** con una seña servida con 2 s de retraso: antes, la segunda oración se representaba primero y la primera la pisaba; después, `signStart` llegó en orden (seña 0 a los 2186 ms, seña 1 a los 4105 ms) y el `done` esperó a que la cola estuviera entregada. |

---

## 12. Gloss (traducción español → LSC)

Las glosas ya **no** se inventan en el cliente: las produce `../sign-ai-backend`
(paso 2 del plan en `openspec/changes/archive/2026-10-17-integracion-backend/tasks.md`). El
placeholder local se eliminó.

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| GLS-1 | Glosas del backend | `[N]` | `TranslationSegment`/`TranslationGloss` espejan los modelos Python. La frase viaja por el WebSocket como `{"type":"text","text":…}` y la respuesta es **un `segment` por oración** (`{"type":"segment","index":…,"total":…,"segment":…}`) seguido de un `done`. La app **representa cada oración apenas llega** (2026-10-16, decisión D-1): la primera entra a la cola sin esperar a las demás. Antes sí esperaba a la frase completa, "para no ejecutar media frase" — y ese motivo venía del camino por fragmentos, donde el backend mandaba palabras sueltas a mitad de frase. Con oraciones un `segment` **es** una oración completa, así que esperar solo dejaba al avatar parado: medido, 569 ms con la primera seña ya disponible en una frase fría de tres oraciones. Ver `GLS-13`. |
| GLS-11 | Oraciones, no fragmentos | `[N]` | **2026-10-15.** El backend tiene **un** modo de entrega. Cada `segment` es **una oración** con `index` y `total` reales desde el primero, y la app encadena cada oración apenas llega, sin esperar a la última (`GLS-13`). Ya no existe `stream`, ni `provisional`, ni `source_text`: el frame es exactamente el mismo que usaba el camino de frase completa, y el parser queda tolerante para que un backend viejo que mande los campos no rompa nada.<br>· `total` siempre conocido **no** es un detalle: es lo que permite no representar media oración. Con el camino por palabras, `total: 0` decía "en un utterance abierto nadie sabe cuántas son todavía" y la app encadenaba igual.<br>· El backend **traduce solo lo que el cliente manda como `text`**. La voz entra por el mismo socket pero vuelve como `transcript`, y esa transcripción **no se traduce**: si se tradujera, la app la tomaría por una frase de voz y la reenviaría, y la misma frase saldría otra vez. Ese bucle existió y quemó el backend —una frase repetida ~2.900 veces, 44 % de CPU— hasta que se le quitó el eco a `handle_text`; `verify_client_frames.py` lo fija con `THREE_MESSAGES = ["segment", "segment", "done"]`, una sola lectura de tres mensajes. |
| GLS-13 | Cada oración, cuando llega | `[N]` | **2026-10-16 (D-1).** El avatar representa cada oración apenas llega, sin
esperar a las demás. Tres cosas cambian juntas porque son el mismo cambio:<br>· `flushSegments()`
entrega **lo pendiente** en vez de esperar a `pendingTotal`, y `flushedThrough` es lo que la hace
idempotente: la primera llamada se lleva lo que había y sube el índice, y las siguientes encuentran
la lista vacía. Llegar tarde no representa dos veces.<br>· La escena recibe `setQueue` + `play()`
con la primera oración y `appendQueue` con cada una de las siguientes (`BRG-14`).<br>· El `done` ya no
arma la cola: cierra el turno, y ahí es donde se mide `metrics.onTurn` y donde se suelta
`livePhraseBacklog`. Soltarlo con la primera oración dejaría pasar una segunda frase mientras la
anterior todavía se traduce, y dos `index` de oraciones intercalados hacen que el avatar represente
la frase al revés.<br>· Una oración que no produce ninguna seña **no cierra el turno**: las
siguientes pueden sí, y el "no produjo ninguna seña" se dice una sola vez, en el `done`.<br>·
Medido en el Redmi con una frase de tres oraciones: la primera seña se movió **95-114 ms** después
de que llegó su `segment`, contra los ~569 ms que costaba esperar a que llegaran las otras dos. |
| GLS-12 | El orden lo garantiza el backend | `[N]` | **2026-10-15.** Ya no hay `streamMutex` ni una carrera que ordenar. El backend divide el texto en oraciones y las traduce **en paralelo** — por eso una frase larga sale más rápido en conjunto que si fuera una sola — pero las **manda en orden**, y `verify_client_frames.py` lo verifica con tres hipótesis y `THREE_MESSAGES`.<br>· En la app el orden es por construcción: `pendingSegments` es un `sortedMap` por `index` y `flushSegments()` entrega lo pendiente en ese orden, sin esperar a `total` (`GLS-13`). Si llegaran desordenadas, el índice las ordena igual; no hace falta un mutex porque no hay dos. Insertar una oración puede suspender (hay que bajar un asset remoto), así que lo que evita que dos oraciones en vuelo se anexen al revés es el `index`, no un cerrojo.<br>· Lo que sí quedó del camino viejo es una consecuencia útil: con el `Mutex` nadie se pregunta ya por el orden **entre frases**, porque solo puede haber una en vuelo (`livePhraseBacklog`, un único lugar de espera, y sin él una frase en vivo se perdería). |
| GLS-2 | Tipo de frase | `[N]` | `PhraseType` (los 5 valores del backend) + `UNKNOWN` para no crashear ante un valor nuevo. Alimenta los marcadores no manuales. |
| GLS-3 | Glosa no disponible | `[N]` | `available == false` → la glosa no se reproduce y se reporta en el caption (RF-CAT-03). |
| GLS-4 | Transcripción del backend | `[N]` | El evento `transcript` se refleja en el input, para poder corregirlo antes de reenviar (RF-UI-02). |
| GLS-5 | Fallback de seña de prueba | `[N]` | Con el catálogo vacío toda glosa llega desconocida. El ajuste "Seña de prueba si falta en el catálogo" hace que use la seña horneada del APK, para poder ver la cola. **Apagado por defecto** (`Settings.demoSignFallback = false`) desde el 2026-10-16: con el catálogo sembrado el fallback llegó a alcanzar a las 18 señas que entonces no tenían animación (`0032`–`0049`), y el owner lo pidió así de corto — "Seña de prueba si falta", sin "en el catálogo" (la fila se partía en dos líneas y el switch se encima). **Hoy no alcanza a ninguna glosa**: las 50 tienen clip desde el 2026-10-22. El ajuste queda para una glosa futura sin clip: encendido la representa con la seña horneada; apagado la saltea y avisa "Seña no disponible" (`GLS-10`). En los dos casos no se toca la red. |
| GLS-9 | Resolución de la ruta del asset | `[N]` | `glb_animation_path` del backend es un path **same-origin dentro del APK** (`signs/<id>.glb`), porque las animaciones se empaquetan y no se sirven por HTTP. `SignsCatalog.localAssetOrNull()` lo reconoce —validándolo contra `available`— y devuelve el path tal cual; la escena **extrae `<id>` del path y busca ese clip entre las animaciones del avatar que ya cargó** (`embeddedClipsFor`), así que el path es un identificador y no un archivo. Cualquier otra cosa cae al camino de descarga de `SignAssetRepository`, que es donde viviría un asset futuro alojado en el backend. Sin esto el backend devolvería una ruta que su propio servidor no tiene, la descarga daría 404 y **todas** las glosas caerían al sign por defecto. Verificado en el Redmi el 2026-10-22: cola de 4 señas (`YO`, `NUNCA`, `TODOS`, `MUCHO`) reproducida con **cero** peticiones a `signs/`, y `oración→avatar` en 61 ms. |
| GLS-10 | Anunciada pero no exportada | `[N]` | **Hoy no alcanza a ninguna glosa**: desde el 2026-10-22 el archivo único trae las 50 animaciones, así que no queda ninguna `0032`–`0049` sin exportar. El requisito se conserva porque protege el caso de una glosa nueva del catálogo sin clip. `SignsCatalog.isOwnAssetPath()` distingue "no es un path nuestro" de "es nuestro pero todavía no lo exportamos". Una glosa así llega con `available == true` y un `signs/<id>.glb` que no tenemos: es nuestro layout, así que **nadie** lo va a servir por HTTP, y pedirlo es un 404 garantizado en cada frase que la mencione. Con `demoSignFallback` prendido se representa con la seña de reserva; apagado se saltea y se avisa "Seña no disponible" por glosa (`GLS-5`). En los dos casos no se toca la red. Solo un path fuera del layout `signs/*.glb` baja por `SignAssetRepository`. Medido en el Redmi contra el mock el 2026-10-17, con las 18 glosas que entonces no tenían clip: **2 peticiones y 2 × 404 sin este requisito, 0 con él**, sobre la misma frase y las mismas glosas. |
| GLS-6 | Placeholder eliminado | `[N]` | `domain/Gloss.kt` (`glossFor`) se borró: era una aproximación que ya no se usa. |
| GLS-7 | Frames desconocidos | `[N]` | Cualquier `type` que la app no conozca se ignora en silencio, sin tumbar la sesión. Así el backend puede añadir `trace`/`sentence_trace` u otros sin romper clientes ya instalados. |
| GLS-8 | Cierre del micrófono | `[N]` | Al soltar el micrófono la app manda `{"type":"end"}`; el backend lo reenvía al STT como un frame `end`. Sin esto, soltar a mitad de una frase la pierde, porque el silencio que cierra el segmento nunca llega. |

---

## 13. Puente Kotlin ↔ JS (WebView)

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| BRG-1 | `window.SignAI.play()` | `[A]` | Reproduce la seña actual (clip GLB). Guard `?.`. Sin cola, repite `signs/<default>.glb`. |
| BRG-2 | `setDebug(bool)` | `[A]` | Activa overlay de debug + `#status`. El texto del overlay usa colores oscuros en tema claro (clase `light` en `<html>`) y coloca `#debug` arriba y `#status` abajo para no solaparse. |
| BRG-3 | `setFreeCamera(bool)` | `[A]` | Cámara libre (orbitar/zoom sin límite). |
| BRG-4 | `setGrid(bool)` | `[A]` | Grilla/suelo. |
| BRG-5 | `setBackground(bool)` | `[A]` | Fondo bosque. |
| BRG-6 | `setSpeed(0..1)` | `[N]` | Velocidad de reproducción de señas (slider `SET-4`). Aditivo. |
| BRG-7 | `onPlaybackDone` (JS→Kotlin) | `[N]` | Evento al terminar `play()`; alimenta `STA-8`. Vía `WebMessageListener` (`SignAIBridge`). |
| BRG-8 | Contrato retrocompatible | `[A]` | Los 5 comandos actuales y el guard `?.` no cambian. |
| BRG-9 | `setTheme(dark)` | `[N]` | Aditivo. Hace que el fondo del canvas 3D siga el tema claro/oscuro (mitiga el riesgo de light mode) y alterna la clase `light` en `<html>` para contrastar el texto de debug/estado. |
| BRG-10 | `setQueue(json)` | `[N]` | Aditivo. Recibe la secuencia de glosas como **string** (Kotlin lo entrecomilla con `JSONObject.quote` y el JS hace `JSON.parse`), nunca datos crudos en el script. `play()` recorre la cola y `onPlaybackDone` salta al terminar la última. |
| BRG-11 | Marcadores no manuales | `[N]` | Reemplazado el 2026-10-13: el motor procedural que aplicaba ceja por `PhraseType` ya no existe. Los cues vienen horneados en el clip. El rig nuevo sí tiene huesos faciales (**31, todos articulaciones de skin**, contados el 2026-10-26: ceja, párpado, mandíbula, boca y mirada), pero los clips actuales los dejan casi quietos y la escena no los maneja, así que la expresión facial y MNM **vuelven a ser posibles** solo con trabajo de rig y decisión del owner — es el pendiente que abre esto (**A6**). |
| BRG-12 | `avatarReady` (JS→Kotlin) | `[N]` | La escena avisa cuando está **todo listo**, no cuando el archivo llegó: los 300 clips indexados, la seña de arranque resuelta, los shaders compilados (`renderer.compileAsync`) y un frame pintado con el avatar adentro. `onPageFinished` dispara mientras el GLB de 34,21 MB (Juan) o 29,46 MB (Pauline) todavía baja, así que usarlo para esconder el splash dejaba ver un canvas negro y la primera seña pagaba el arranque del WebGL. Si el `WebMessageListener` no está soportado, se vuelve al fallback por `onPageFinished` y el timeout de 8 s de `SignAiSplash`. |
| BRG-13 | Variantes de seña | `[N]` | **Sin efecto desde el 2026-10-22.** El layout viejo traía 2–4 tomas en 10 de las 32 señas y `pickVariant` elegía una al azar; el archivo único trae **una toma por seña**, así que `pickVariant` siempre devuelve la misma. Recuperar las variantes es una decisión abierta (se pueden fusionar las tomas viejas al archivo único: el esqueleto es el mismo). |
| BRG-14 | `appendQueue(json)` / `playFrom(i)` | `[N]` | **2026-10-16: en uso** (decisión D-1). Antes estaban en el puente sin que nadie los llamara; ahora son lo que hace posible representar oración por oración. `setQueue` + `play()` arrancan la cola del turno y cada oración siguiente se encadena con `appendQueue`, que en JS **no reinicia** lo que está sonando.<br>· `appendQueue` además **se reanuda solo** si la cola ya se había vaciado mientras esa oración se traducía: llama a `playFrom` con `queueIdx + 1` si la cola ya arrancó, o con 0 si nunca arrancó. La decisión va dentro de JS y no en Kotlin a propósito: ahí no hay dos hilos que puedan intercalarse entre "la cola está vacía" y "arranco". Con índice 0 a secas se repetiría la última seña, que es lo que pasaba antes de corregirlo (medido en el Redmi: una frase de tres oraciones cuya tercera tardó 7,6 s repitió la segunda).<br>· `playFrom` corta también con `state.loading`: sin eso, una carga en vuelo y un `appendQueue` que llega después arrancaban el mismo ítem dos veces y el `signStart` salía duplicado.<br>· `queueState()` sigue sin llamarlo Kotlin — sirve desde la consola del WebView (Ajustes → AVANZADO → depurar) para ver `backlog` y `speed`. |
| BRG-15 | Adelanto automático | `[X]` | **ELIMINADA el 2026-10-16 (decisión D-5).** Multiplicaba la velocidad por 1,6 cuando había 2 o más señas en la fila (`CATCHUP_BACKLOG`/`CATCHUP_FACTOR`, `applySpeed`, `state.catchUp` y la rama de `tickPlayback`): ya no existe ninguno de esos cuatro en `app.js`, y `queueState()` dejó de devolver `catchUp`.<br>· **Por qué se quitó, en orden de peso:** (1) alteraba la variable que había que medir —la prueba obligatoria **PR-PER-04** pregunta a usuarios con LSC si la velocidad es adecuada, y con el adelanto la respuesta mezclaba la velocidad que eligió la persona con un factor que nadie le dijo—; (2) la carrera que venía a empatar no existe, porque quien lee el avatar es una persona sorda que no está mirando a quien habla; (3) no estaba en ningún documento de la tesis: no aparece en el documento de requerimientos, ni en el plan de pruebas, ni en los doce diagramas del SDD, y `CATCHUP_BACKLOG = 2` y `CATCHUP_FACTOR = 1.6` son dos números mágicos sin criterio escrito, que contradicen la regla del backend de que ningún umbral numérico va hardcodeado en la lógica funcional.<br>· Lo que sí era cierto y queda: las 32 señas exportadas duran 2,08–2,75 s (promedio 2,15 s) y una frase larga tarda más en representarse de lo que se dice. Eso ya no es una falla del sistema. `setSpeed` sigue siendo el único ajuste de velocidad, y ahora se aplica tal cual. |
| BRG-16 | `signStart` (JS→Kotlin) | `[N]` | **2026-10-16.** La escena avisa `signStart:<índice de cola>:<performance.now()>` desde `notifySignStart`, en `loadQueueItem`, cuando el clip ya arrancó —no desde `startClip`, porque entre una cosa y otra está la carga del `.glb`, y esa carga es parte justo de lo que hay que medir—. Los dos relojes tienen épocas distintas y no se restan entre sí: Android compara contra su propio `SystemClock` desde que llegó la oración, y el valor propio de JS se loguea para poder separar los tres tramos (Kotlin→escena, escena→primer fotograma, escena→Kotlin). Se llama una vez por seña; `MainScreenState` solo cuenta el de la primera del turno (`OBS-7`). |
| BRG-17 | `setSilenceTimeoutMs(ms)` | `[N]` | **2026-10-16 (D-6).** El `SILENCE_TIMEOUT_MS` que está usando el STT, para que el overlay de depurar lo muestre. No es un ajuste: la app nunca habla con el STT, así que el valor llega desde `/health` del backend —que lo copia del `/health` del STT— y solo se **muestra**. `null` deja el overlay diciendo "?", que es lo que hay que ver cuando no se sabe. Con eso cada corrida de pruebas puede anotar el número sin depender de que alguien se acuerde. |
| BRG-18 | `signFailed` (JS→Kotlin) | `[N]` | **2026-10-17.** `signFailed:<índice de cola>`, desde `notifySignFailed` en el `catch` de `loadQueueItem`. Es el aviso de que **esa** seña no se pudo descargar y la escena siguió con la frase (`STA-26`). Distinto de `signStart` (`BRG-16`), que es "el muñeco ya se está moviendo": acá no se va a mover por ese ítem. Kotlin lo usa para el texto "Seña no disponible".<br>· **Aditivo**: el prefijo vive en `AvatarWebView.SignFailedPrefix`, al lado de `SignStartPrefix`, y no se toca ni un nombre existente (`BRG-8`). Una escena vieja no manda el mensaje y una app vieja no lo escucha: en los dos casos el comportamiento es el viejo, sin romperse nada.<br>· Por qué el motivo del fallo no viaja en el mensaje: el nombre de la seña ya está en el log de `app.js` (`SignAIWeb`) y en el de `SignAiState`. El puente lleva lo mínimo. |

---

## 13bis. Observabilidad (modo en vivo)

Sin instrumentación no hay forma de afirmar que algo mejoró, así que estos
requisitos son parte del trabajo y no un extra. Los tres servicios exponen
`/metrics` (formato de exposición de Prometheus) y `/metrics.json` (lo mismo,
estructurado, que es lo que leen los checks automáticos porque parsear texto con
expresiones regulares es frágil).

| ID | Requisito | Dónde | Criterio |
|---|---|---|---|
| OBS-1 | Latencia de la primera seña | backend `/metrics` | `rt_first_sign_ms` = de la **frase recibida** a la **primera oración enviada**. Es **la** métrica que decide si el producto se siente en tiempo real. El nombre se conservó a propósito aunque cambiara lo que mide: la línea base publicada (913 ms p50 / 1336 ms p95) es del camino por palabras y **no es comparable** con esta. Con el flujo por oraciones la primera seña sale al terminar la primera oración, no a mitad de camino, así que la cifra sube por construcción — es el costo conocido de la opción A (que se explica en [`docs/CAMBIOS.md`](../../../docs/CAMBIOS.md)). Falta remedir con las 28 frases. |
| OBS-2 | Latencia de la frase | backend `/metrics` | `rt_phrase_total_ms` = de la frase recibida a la última oración enviada (antes `rt_utterance_ms`). `rt_sentence_delay_ms` por oración, para ver si se entregan espaciadas o de golpe al final — que con el flujo por oraciones es justamente la forma **esperada**: la cola se llena al cerrar cada frase, no durante. `rt_phrase_sentences` es el histograma de cuántas oraciones tiene cada frase, que es el número que decide cuánto vale la entrega por oración. |
| OBS-3 | El audio está fluyendo | backend `/metrics` | `audio_bytes_total` y `audio_chunks_total`. Es el único tramo del sistema que no produce salida: si el micrófono del teléfono se rompe, el STT se queda mudo y no llega ningún `transcript`. Cuenta bytes además de fragmentos, así distingue "no manda nada" de "manda el doble". Del lado del teléfono, `SignAiMetrics` cuenta `dictado_frases` y `dictado_palabras`, que son los que dicen si el dictado está transcribiendo de verdad y no solo captando ruido. |
| OBS-4 | Contención de la GPU | STT `/metrics` | `stt_partial_queue_ms`. Antes de este trabajo un parcial podía retrasar un final 4–8 s; el guard de regresión es que este número no vuelva a subir. |
| OBS-5 | Saturación de audio | STT `/metrics` | `stt_partial_lag_ms`: audio recibido menos audio procesado. Sube = la GPU no da abasto y el avatar va a quedar atrás respecto a la voz. |
| OBS-6 | Descartes con motivo | STT `/metrics` | `stt_dropped_total{reason="rms"\|"no_speech"\|"logprob"}`. `reason="logprob"` merece atención porque cuenta audio que Whisper transcribió **bien** y el filtro tiró igual: con `-0.8` se comía "cómo estás" y "estoy triste". Si sube, el filtro está comiéndose audio real. |
| OBS-7 | Latencias del teléfono | logcat `SignAiMetrics` | Fragmento→cola y duración de turno, p50/p95/max, en un anillo de 256 muestras. Es lo único que el dispositivo puede medir: los otros dos servicios no ven qué pasa después de que el mensaje llega al teléfono.<br>· **2026-10-16: `oracion_a_avatar`** (mensaje de puente `signStart`, `BRG-16`). Mide el tramo que no tenía reloj en ningún lado: de la oración recibida a **el avatar moviéndose en pantalla**. Antes `play()` se llamaba desde Kotlin antes de que el `.glb` terminara de bajar, así que el reloj de "representando" arrancaba con milisegundos de carga que no son del avatar, y el backend cronometraba hasta mandar el `segment` y nada más. Medido en el Redmi con una frase de tres oraciones: **95–114 ms** del primer `segment` al primer movimiento. |
| OBS-8 | Traza de segmentos | logcat `SignAiNet` | Cada `segment` recibido con `stream`, `provisional`, `source_text` y sus glosas. Con eso se separa "el backend mandó otra cosa" de "el avatar no la representó". |
| OBS-9 | Motivo de descarte del STT | log + `TranscriptionResult` | `transcribe_buffer` devuelve `drop_reason`. Un final vacío sin explicación es indistinguible de un micrófono roto. |

Regla de trabajo: **agregar la métrica antes que la feature**. Un número que no
se instrumentó es un número que no se puede afirmar que mejoró.

## 14. Animaciones y motion

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| ANI-1 | Rise del bottom sheet | `[P]` | Entrada desde abajo (~250 ms). |
| ANI-2 | Slide del drawer | `[P]` | Entrada lateral (~250 ms), dirección según lado. |
| ANI-3 | Dropdown-in del menú | `[P]` | Menú de personaje (~160 ms, fade + scale). |
| ANI-4 | Transición de tema | `[P]` | 250 ms. |
| ANI-5 | Progress del splash | `[P]` | Avance lineal suave. |
| ANI-6 | Feedback de press | `[P]` | Scale en botones (p. ej. repetir 0.94). |
| ANI-7 | Respeto de reduce-motion | `[P]` | Si `ACC-5`/sistema → mínimas animaciones. |
| ANI-8 | Motion tokens | `[N]` | Duraciones centralizadas (fast/medium/slow) en el design system. |

---

## 15. Semántica / accesibilidad (TalkBack, ARIA→Compose)

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| SEM-1 | Labels de botones | `[P]` | Pestañas, mic, historial, cerrar, repetir, volver. |
| SEM-2 | Labels de vistas | `[P]` | Splash "Cargando…", stage "Área del avatar", panel "Controles de traducción", sheet "Historial reciente". |
| SEM-3 | Roles | `[P]` | listbox/option, switch, button. |
| SEM-4 | Estados anunciados | `[P]` | `aria-expanded`, `aria-selected`, `aria-pressed` → equivalentes Compose (`stateDescription`, `toggleable`, `selectable`). |
| SEM-5 | Contraste y legibilidad | `[P]` | Base dark con buen contraste; `APP-3`/`ACC-3` refuerzan. |

---

## 16. Responsive / layout

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| RSP-1 | Shell máx. 430 px | `[P]` | En pantallas anchas, la shell se centra (equivalente: layout de columna). |
| RSP-2 | Portrait-locked | `[A]` | App fija en vertical (`AndroidManifest`). |
| RSP-3 | Edge-to-edge | `[A]` | Contenido detrás de status/navigation bars con insets. |
| RSP-4 | Alturas relativas | `[P]` | El stage usa alto disponible; el panel inferior es fijo. |
| RSP-5 | Framing en pantallas grandes | `[P]` | Borde/sombra de marco en ≥431 px (solo referencia web; no aplica en Android). |

---

## 17. Branding y contenido

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| BRD-1 | Nombre de la app | `[N]` | **"SignALL"**, decidido por el owner el 2026-10-16 (cierra el "ABIERTO" que decía "SignAI … seguramente cambiará"). Vive en **un solo lugar**: `SignAiBranding.Name` dentro de la app y `strings.xml/app_name` para el lanzador, que no puede leer Kotlin. El paquete `com.signai.webview` **no** se renombró: no es visible para el usuario y renombrarlo cambia la identidad de la app instalada. |
| BRD-2 | Pantalla About | `[P]` | Tile con el icono de la marca, eyebrow, título y copy "Una herramienta que transforma tus frases en español a Lengua de Señas Colombiana mediante un avatar 3D." |
| BRD-3 | Meta del About | `[P]` | "Proyecto de tesis" · "Pontificia Universidad Javeriana" · "Versión 1.0.0". |
| BRD-4 | Versión dinámica | `[N]` | "Versión 1.0.0" lee de `BuildConfig.VERSION_NAME`, no hardcodeado. `versionName` pasó de `1.0.0-webview` a `1.0.0` con el rename: ese sufijo era la última aparición de "WebView" que el usuario podía leer. |
| BRD-5 | Icono de lanzador propio | `[N]` | Icono de marca real (mano blanca sobre azul `#1c4cfc`), generado por `tools/generar-marca.py` desde el PNG maestro. Adaptive icon con `<foreground>` (malla vectorizada con potrace, viewport 108 con la mano dentro del 18..90) y `<monochrome>` para el tema monocromático de Android 13+, `ic_launcher_background = #1C4CFC`, y fallback plano para API < 26 (`drawable-xxhdpi/ic_signall.png` → `drawable/ic_launcher.xml`). El **mismo** PNG es la tile del header de Ajustes y del About. |
| BRD-6 | Sin la palabra "WebView" | `[N]` | El owner pidió no volver a verla (2026-10-16): se fue de `versionName`, del tema (`Theme.SignAIWebView` → `Theme.SignALL`) y de la documentación visible. Queda solo en el paquete/`applicationId`, que el usuario no ve (ver `BRD-1`). |

---

## 18. Features omitidas / a decisión

| ID | Feature | Origen | Estado | Razón |
|---|---|---|---|---|
| OM-1 | Home indicator | `[P]` | **Conservada** | Se implementó (112×4) por fidelidad con el prototipo. |
| OM-2 | Avatar CSS del prototipo | `[P]` | **Reemplazada** | Se usa el GLB 3D real. |
| OM-3 | Starter history mock | `[P]` | **Reemplazada** | Se usa historial persistido real. |
| OM-4 | Auto-relleno del mic | `[P]` | **Eliminado** | 2026-10-14. Existía porque no había reconocimiento de voz real; ahora sí lo hay (`CTL-6`) y el auto-relleno solo hacía que la app pareciera escuchar mientras no capturaba nada. |
| OM-5 | Assets de v0 / shadcn / Tailwind | `[P]` | **No se usan** | Base nativa Lumo + Phosphor. |
| OM-6 | "Tamaño de interfaz" sin cablear | `[P]` | **Se cablea** | En el prototipo era un slider sin estado; `ACC-1` lo hace real. |

---

## 19. Inventario de iconos (lucide → Phosphor Regular)

Todos verificados disponibles en `phosphoricons-regular-android:1.0.5`.

| Prototipo | Phosphor | Feature |
|---|---|---|
| ArrowLeft | `ArrowLeft` | `NAV-3` |
| Check | `Check` | `PER-3` |
| ChevronDown | `CaretDown` | `SET-10` |
| ChevronRight | `CaretRight` | `HIS-3`, `SET-7` |
| Clock3 / History | `Clock` / `ClockCounterClockwise` | `CTL-3` |
| Contrast | `CircleHalf` | `ACC-3` |
| Info | `Info` | `SET-7` |
| Minus | `Minus` | `ACC-5` |
| Mic | `Microphone` | `CTL-5` |
| Moon / Sun | `Moon` / `Sun` | `APP-3` |
| Plus | `Plus` | `ACC-4` |
| RotateCcw | `ArrowCounterClockwise` | `STG-4`, `CNV-5` |
| Send | `PaperPlaneTilt` | `CTL-4` |
| Settings2 | `SlidersHorizontal` | `NAV-6` |
| Sparkles | `Sparkle` | `SET-3` |
| Type | `TextAa` | `ACC-1/2` |
| Volume2 | `SpeakerHigh` | `SET-4` |
| X | `X` | cierres |
| Play | `Play` | (disponible) |
