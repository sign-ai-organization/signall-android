# UI / Features — Especificación de la app (fuente de verdad)

Catálogo **completo** de features de la app, derivado del prototipo
`PrototipoFrontend/` (Next.js/React — prototipo de referencia) y del estado
actual de la app nativa (`Compose shell + WebView 3D`).

> **Regla:** estas son **todas** las features que va a tener la app. Toda
> implementación debe cubrir los IDs de este spec. Si una feature del prototipo
> no se implementa, debe quedar marcada explícitamente como *Omitida* con su
> razón. Si aparece una feature nueva, se agrega aquí primero.

- **Prototipo de referencia:** `PrototipoFrontend/app/page.tsx` + `app/globals.css`.
- **Plan de ejecución:** `openspec/changes/prototipo-ui-vercel/tasks.md`.
- **Implementación:** Compose nativo (Lumo + Phosphor). El WebView es solo el
  canvas 3D.
- **Estado:** **implementado** en la rama `feat/prototipo-ui-vercel` (fases A–G),
  verificado en device (build + install). Pendiente de merge a `main`.
- **Tema:** el **default es claro** con la paleta del prototipo
  (`Settings.darkTheme = false`). El modo oscuro existe y usa la paleta oscura
  del prototipo.
- **MIUI force-dark:** la app **debe** declarar `android:forceDarkAllowed=false`
  (`res/values/themes.xml`). Si no, MIUI "smart dark" invierte el contenido de
  las capas hardware y el texto dentro de `verticalScroll`/drawers se ve blanco.

Leyenda de origen: `[P]` = del prototipo · `[A]` = ya existe en la app ·
`[N]` = nueva/derivada.

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
| SPL-1 | Logo tile "S" | `[P]` | Cuadrado redondeado oscuro con "S" blanca. |
| SPL-2 | Eyebrow "SEÑA"*¹ | `[P]` | Texto pequeño en mayúsculas, tracking amplio, debajo del logo. |
| SPL-3 | Barra de progreso | `[P]` | Track + valor que avanza de 0% a 100%. |
| SPL-4 | Porcentaje numérico | `[P]` | `{n}%` debajo de la barra. |
| SPL-5 | Progreso real | `[N]` | En la app el progreso se ata a `WebChromeClient.onProgressChanged` (no fake). |
| SPL-6 | Timeout de seguridad | `[A]` | Si el WebView no carga (sin internet para el CDN), salir del splash a los 8 s. |
| SPL-7 | Fade de salida | `[A]` | El splash se desvanece (400 ms) al terminar; el contenido entra con fade (500 ms). |

\*¹ El texto del branding está **abierto** (ver `BRD-1`).

---

## 3. Edge tabs, gestos y drag (drawers)

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| EDG-1 | Pestaña borde **izquierdo** | `[P]` | Abre Conversación; visible con drawer cerrado. |
| EDG-2 | Pestaña borde **derecho** | `[P][A]` | Abre Ajustes; visible con drawer cerrado. |
| EDG-3 | Click en pestaña abre drawer | `[P]` | Tap directo (sin arrastrar) abre el drawer correspondiente. |
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
| EDG-21 | Zona de arrastre amplia | `[N]` | La pestaña del drawer abierto tiene una zona de arrastre de 72 dp (antes 48 dp) y la franja de borde del panel abierto está activa, así que cerrar por drag ya no exige empezar justo sobre la pestaña. |
| EDG-22 | Orden de capas y hit-testing (causa raíz) | `[N]` | Regla de oro, documentada en `EdgeDrawer.kt`: (1) Un `draggable`/`clickable` con `enabled = false` **sigue registrando** un nodo de input en Compose y **sigue siendo la ruta de hit-test más alta**, por lo que el drawer inactivo apilado a pantalla completa se comía todos los toques del backdrop del activo. Solución: no adjuntar el gesto en absoluto cuando no puede actuar (`.then(if (enabled) Modifier.draggable(...) else Modifier)`); un nodo de layout sin input es ignorado por el hit-test y puede seguir dibujándose (para el fade). (2) Un `AndroidView`/WebView es una View hija real: se lleva todo toque dentro de sus límites y ningún hermano Compose lo pinta encima, así que debe ser el **primer** hijo (abajo), los drawers van por encima y su `pointerInteropFilter` devuelve `true` mientras un drawer está abierto. (3) El backdrop debe ir **antes** del panel y la pestaña, y a pantalla completa. |
| EDG-23 | El drawer inactivo no compone ningún gesto | `[N]` | Un drawer cerrado cuyo contrario está abierto no tiene **ningún** nodo interactivo: ni franja de borde, ni pestaña, ni `draggable` en el panel (el panel sólo se arrastra cuando está abierto o en movimiento, `isOpen \|\| fraction > 0.001`). Esto cierra el último resquicio: deslizar desde el borde extremo ya no puede abrir el panel opuesto por encima de uno abierto. |

---

## 4. Avatar stage

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| STG-1 | Avatar 3D | `[A]` | `models/avatar.glb` renderizado con Three.js en el WebView (protagonista visual). Se carga una sola vez; las señas son `signs/<id>.glb` con la animación ya horneada, reproducidas con `THREE.AnimationMixer` sobre el mismo esqueleto. La normalización de escala/offset va en un pivot **padre**, porque el clip anima la raíz del armature. |
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
| CTL-4 | Botón **Traducir** | `[P][A]` | Ícono + label; ancho 75%. Deshabilitado si input vacío o estado translating/signing. |
| CTL-5 | Botón **Micrófono** | `[P][A]` | Ancho 25%. Alterna estado `listening`; visual "activo" mientras escucha. |
| CTL-6 | Mic real | `[A]` | **Corregido 2026-10-14.** `data/AudioCapture.kt` captura con `AudioRecord` sobre `AudioSource.VOICE_RECOGNITION` y manda chunks de 30 ms de PCM16 mono a 16 kHz por el mismo WebSocket de traducción. `VOICE_RECOGNITION` y no `MIC` a propósito: es la fuente afinada para reconocimiento, así el STT escucha la voz en vez del AGC bombeando. El audio crudo va al backend, que es el único que habla con el STT. |
| CTL-7 | Estado **activo** del mic | `[P]` | Fondo strong + ring cuando `listening`. |
| CTL-8 | Estado **disabled** de Traducir | `[P]` | Opacidad reducida cuando no se puede enviar. |
| CTL-9 | Foco del input | `[P]` | Borde strong + ring al enfocar. |
| CTL-10 | Home indicator | `[P]` | Barrita inferior 112×4 (`strong` al 70%). **Se conserva** por fidelidad con el prototipo. |
| CTL-11 | Safe area / IME | `[A]` | Respeta navigation bar y teclado (`imePadding`). |
| CTL-12 | Controles bloqueados durante un replay | `[N]` | Desde "Repetir" (stage o drawer) hasta que la seña termina, todos los controles (mic, Recientes, input, Traducir) quedan deshabilitados y atenuados al 35% (`replayPending`, se libera con `onPlaybackDone`). | **Corregido 2026-10-14:** el flag lo prendía `repeatTurnFromDrawer` —el único de los dos Repetir— y lo soltaba **el primer fragmento**, que es justo cuando la seña empieza a firmarse: el panel volvía a quedar habilitado a los ~50 ms del toque, con la seña todavía corriendo. Ahora lo prende `requestTranslation` cuando el replay es real (stage o drawer) y lo sueltan `onPlaybackDone`, `fail()` y el caso "la frase no produjo ninguna seña". Se prende **después** de los cuatro descartes tempranos de `requestTranslation` a propósito: desde cualquiera de ellos el panel quedaría muerto para siempre, sin Repetir que lo vuelva a prenda
| CTL-13 | Tocar un control cierra el teclado | `[N]` | **2026-10-14.** Micrófono, TR, Traducir, Historial y los tiradores de los drawers cierran el teclado **y no vuelve**: `rememberKeyboardDismissAction()` hace `focusManager.clearFocus(force = true)` y después `keyboard?.hide()`. **Con solo `keyboard?.hide()` el teclado se iba un segundo y volvía**, porque el `TextField` seguía con el foco y la siguiente recomposición lo volvía a mostrar; el `force = true` es porque un campo `readOnly` ignora el cierre normal. Cerrar el teclado **no manda la frase**: el texto se queda en el campo. |

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
| SET-1 | Header con logo | `[P]` | Logo tile "S"*¹ + cerrar. |
| SET-2 | Sección **AVATAR** | `[P]` | Título de sección. |
| SET-3 | **Personaje** (picker) | `[P]` | Dropdown con Julián, Samuel, Ximena, Gris + dots de color + check del seleccionado. Ver `PER-*`. |
| SET-4 | **Velocidad de señas** | `[P]` | Slider 10–100 (ícono SpeakerHigh). |
| SET-5 | Sección **ACCESIBILIDAD** | `[P]` | Ver `ACC-*`. |
| SET-6 | Sección **APARIENCIA** | `[P]` | Ver `APP-*`. |
| SET-7 | Link **Acerca de** | `[P]` | Ícono Info + "Acerca de" + chevron; abre About. |
| SET-8 | Sección **AVANZADO** | `[A][N]` | Al final: Modo debug, Cámara libre, Suelo (grilla), Fondo bosque, selector de Seña. |
| SET-9 | Fila de setting reutilizable | `[N]` | Ícono + label + control (switch/slider), min-height 50. |
| SET-10 | Dropdown de personaje | `[P]` | Trigger con chevron que rota al abrir; menú anclado con animación `dropdown-in`. |

### 8.1 Personajes (picker UI-only)

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| PER-1 | 4 personajes | `[P]` | Julián, Samuel, Ximena, Gris; default **Gris**. |
| PER-2 | Dots de color | `[P]` | Cada personaje con su color (Julián `#765044`, Samuel `#303843`, Ximena `#a46b54`, Gris `#9a9a94`). |
| PER-3 | Check del seleccionado | `[P]` | Ícono check en la opción activa. |
| PER-4 | **UI-only** | `[N]` | La selección **no** cambia el GLB todavía; queda lista para futuros avatares. Persistir la elección. |
| PER-5 | Semántica listbox | `[P]` | Roles listbox/option, `aria-expanded`, `aria-selected`. |

---

## 9. Accesibilidad

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| ACC-1 | **Tamaño de interfaz** (slider) | `[P]` | Escala de la UI. En el prototipo no estaba cableado; en la app debe tener efecto real. |
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
| APP-2 | **Modo oscuro** | `[P][A]` | Tema oscuro con la paleta oscura del prototipo. **El default de la app es claro** (`Settings.darkTheme = false`). |
| APP-3 | **Switch de tema** | `[P]` | Alterna; el ícono/label cambia (Moon ↔ Sun, "Modo oscuro" ↔ "Modo claro"). |
| APP-4 | Tokens de color temables | `[P]` | `background`, `foreground/text`, `muted`, `line/outline`, `surface`, `strong`, `soft`, `primary`. |
| APP-5 | Transición de tema | `[P]` | Cambio de color suave (250 ms). |
| APP-6 | Branding centralizado | `[N]` | Wordmark en un único lugar para cambiar "SignAI" ↔ "Seña" trivialmente (ver `BRD-1`). |
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

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| STA-1 | Estado **idle** | `[P]` | Texto "Listo para traducir". |
| STA-2 | Estado **listening** | `[P]` | Texto "Escuchando...". |
| STA-3 | Estado **processing** | `[P]` | Texto "Procesando...". |
| STA-4 | Estado **translating** | `[P]` | Texto "Traduciendo...". |
| STA-5 | Estado **signing** | `[P]` | Texto "Reproduciendo señas...". |
| STA-6 | Estado **done** | `[P]` | Texto "Reproducción completada". |
| STA-7 | Flujo de envío | `[P]` | `send()`: idle → translating → signing → done. El prototipo usa 850 ms/2700 ms fake. |
| STA-8 | **Fin real de reproducción** | `[N]` | La transición `signing → done` usa un callback JS→Kotlin (`onPlaybackDone`), no un timer. |
| STA-9 | Flujo del mic | `[A]` | **2026-10-15, reescrito por el flujo por oraciones.** El auto-relleno del prototipo (eliminado el 2026-10-14) sigue eliminado: hacía que la app *pareciera* estar escuchando sin capturar nada. Ahora hay **tres** caminos de entrada y los dos de voz terminan en el mismo punto —una frase mandada como `{"type":"text"}`—, que es lo único que el backend traduce (`GLS-11`).<br>• **Dictado** (🎤): `changeMicMode(Dictation)` → `audio.start()` → se mandan chunks PCM16 → el backend devuelve `transcript` y el campo muestra la transcripción en vivo → el STT cierra la frase a los 1,5 s de silencio → llega el `final` → **la captura se apaga sola** y el texto queda a la espera de confirmación (`STA-17`). En este modo no se manda nada.<br>• **En vivo** (◢◢ TR): mismo arranque, pero el `final` **no** apaga el micrófono: la frase sale sola y el interruptor sigue prendido (`STA-16`).<br>• **Texto escrito**: el campo y Traducir, que es lo que ya existía.<br>· Apagar el micrófono manda `{"type":"end"}`, que cierra el utterance del STT. Si en toda la sesión no volvió nada, se vuelve a `idle` de inmediato con "No se escuchó nada." en vez de esperar el timeout y reportar un fallo que no ocurrió (`STA-19`).<br>· **El `final` puede estar vacío y hay que tolerarlo**: el STT responde a **todo** `end`, incluso al que no tiene audio detrás (`flush()` responde siempre). Un `final` sin texto en dictado es "no se escuchó nada" (`onEmptyFinal`); no es un error, y no es motivo para tocar la interfaz más de lo que ya se tocó. |
| STA-16 | Micrófono continuo (interruptor) | `[N]` | **2026-10-15.** El estado del mic es un **enum de tres valores** (`MainScreenState.micMode`: `Off`/`Dictation`/`Live`) y es **independiente** de `playbackState`, que ya no dice si el mic está prendido. Un enum y no dos flags porque los dos modos comparten un `AudioRecord` y un WebSocket: "los dos prendidos" sería un estado que la app no puede representar. Ver `STA-17`.<br>· **Solo `Live` es continuo.** En `Dictation` el `final` del STT **apaga la captura**: la frase se terminó y no hay nada más que escuchar hasta que el usuario vuelva a tocar. Por eso `dictationBuffer` ya no acumula entre frases — con 1,5 s de silencio el STT corta solo, y lo que hay entre una frase y la siguiente lo decide el usuario con una pausa.<br>· Un `final` en `Live` **no** apaga el mic: solo dice "esta frase se cerró". El STT abre el siguiente utterance en cuanto escucha voz de nuevo. **Nunca uses `playbackState == Listening` como "el mic está prendido"**: con el mic prendido y el muñeco firmando, el estado es `Signing`.<br>· Al terminar una frase o una seña el estado vuelve al del modo activo (`Listening` en TR, `Dictating` en dictado), no a `Done`: el botón tiene que verse prendido mientras captura, o el usuario no sabe si tocarlo (`resumeListeningIfMicOn`).<br>· El botón de mic nunca se deshabilita mientras esté prendido: prendido, cada uno es el único que puede cortar su modo, así que bloquearlo dejaría al usuario sin salida.<br>· Con el mic prendido **Traducir y el Historial quedan deshabilitados** y el input es `readOnly`: texto y voz van por el mismo socket y el backend no puede saber de quién es cada fragmento; mezclados, el avatar firmaría la frase al revés. Son dos formas de mandar, no dos a la vez. El input sigue mostrando la transcripción en vivo, que es lo que el usuario quiere ver: por eso `readOnly` y no `enabled`, que lo atenuaría hasta volverlo ilegible.<br>· Las métricas (`SignAiMetrics`) se acumulan por sesión y se reinician al prender el mic. |
| STA-17 | Dos modos de voz sobre un solo micrófono | `[N]` | **2026-10-15.** 🎤 (fila 1) es **dictado**: se toca, se habla, el silencio cierra la frase, la captura se apaga sola y **el texto espera**. ◢◢ TR (fila 2) es **en vivo**: se toca, el mic queda prendido y cada frase sale sola al cerrarse. Los dos son interruptores —ninguno se mantiene apretado— y **no pueden estar prendidos a la vez**: comparten la captura y el socket. Por eso **cada uno deshabilita al otro, en los dos sentidos** (`STA-16`).<br>· **Regla que gobierna los dos: apagado no es mandar.** Solo el modo en vivo manda sin que nadie toque Traducir. En dictado, apagar deja el texto a la espera (`RF-STT-03`). Antes era al revés (`stopDictation` llamaba `sendDictation`), y por eso `trazabilidad.md` §2.1 tenía RF-STT-03 y RNF-REL-04 en "No implementado".<br>· **El tercer estado de RF-UI-01 se descartó, y hay que decirlo porque el documento de requisitos todavía lo pide.** La v0.2 pide tres iconos y que el tercero mande la frase. Se implementó así y se probó en el Redmi: con un icono distinto el botón deja de leerse como "micrófono", y la primera reacción de quien lo usa es querer dictar otra vez, no confirmar. El micrófono **no** cambia de icono y sigue siendo la única forma de dictar de nuevo. Lo que hace falta —un aviso de que hay texto listo— lo da el **hint del escenario** ("Texto listo. Toca Traducir para firmarlo."), que está al lado y no reemplaza ninguna función; en el campo no sirve, porque con la frase escrita arriba un placeholder no se ve nunca. **Desviación de RF-UI-01, decidida por el owner el 2026-10-15 después de probarla en el dispositivo físico.** El envío es Traducir.<br>· Tocar 🎤 con texto esperando **lo descarta y vuelve a dictar de cero**: es una cancelación explícita, que es lo único que RNF-REL-04 permite como pérdida. El texto de una sesión en vivo, en cambio, **no** se descarta: ese es del micrófono, no del usuario.<br>· **Un control deshabilitado se marca apagando el icono y la etiqueta a `muted`, nunca bajándole el alfa a la superficie.** La trampa: estos dos botones se rellenan con `soft` sobre una barra `surface`, y `soft` al 35 % de alfa da `#F8F8F5` sobre `#FEFEFC` — dos colores que a simple vista son el mismo. El botón parecía habilitado y no lo estaba, y el owner lo reportó como "el otro no se deshabilita". Traducir sí se notaba, porque su relleno es `strong`: atenuado queda gris medio.<br>· Cambiar de un modo al otro **no reinicia la captura** (`AudioRecord` tarda cientos de ms en abrir y el usuario ya estaba hablando): se manda `end` para que el STT cierre el utterance del modo que se deja. **Ya no se manda `mode`** (`sendMode` se eliminó): el backend no decide qué traducir, así que no tiene nada que decidir con eso.<br>· **No hay `streamMutex` ni carrera que ordenar**: el backend manda las oraciones **en orden** y la app las encadena por `index` (`GLS-12`).<br>· Traducir funciona siempre: `handle_text` no mira de dónde vino el texto, porque una frase escrita *es* la orden de traducirla. |
| STA-18 | La cola manda sobre el enum | `[N]` | **2026-10-15.** `MainScreenState.queuePlaying` sigue siendo la fuente de verdad de "el avatar está firmando", y no `playbackState`: el `done` de la última oración llega mucho antes de que el avatar termine, porque una oración de 9 glosas son ~19 s de animación contra los ~2 s que tarda en traducirse. Confundirlas hacía que apagar el micrófono pareciera "no hay nada en curso" y dejaba la interfaz en "Procesando..." para siempre, con el panel entero deshabilitado.<br>· Lo que cambió es **por qué** la cola se adelanta. Con el camino por palabras crecía de a una durante el habla —38 fragmentos, casi 40 s de señas por delante en una prueba de 30 s—; con el flujo por oraciones la cola está **vacía mientras el usuario habla y se llena de golpe** al cerrar cada frase. El desfase de fondo es el mismo (`BRG-15`), pero ahora llega a lotes.<br>· Por eso **`signing` no bloquea el panel**: lo que no se puede tocar es lo que está **en vuelo** (`translating`, `processing`). Mandar otra frase con el avatar firmando la anterior reemplaza la cola, que es lo que ya hacía la escena. Un botón habilitado que hace nada es peor que uno deshabilitado, así que `play()` y `replayTurn()` tampoco rechazan en `signing`.<br>· El puente `onPlaybackDone` es quien cierra la cola: con el mic apagado pasa a `done` (y aparece "Repetir"), y con el mic prendido vuelve a la etiqueta del modo. |
| STA-20 | Lo ya enviado no se vuelve a mandar | `[N]` | **2026-10-15.** El campo conserva la frase que el avatar está firmando — pero en `readOnly` y con **Traducir deshabilitado**, durante `SentTextHoldMs` = **1500 ms** (`MainScreenState.sentText`). Sin eso el botón ofrecía reenviar lo que ya se envió, y el avatar firmaría la frase dos veces seguidas por un solo toque.<br>· **Son tres los caminos que mandan una frase sin que nadie toque Traducir**, y por eso los tres la retienen: el **en vivo** mientras se hablaba, un **Traducir** a mano (botón o Enter) y el **Repetir**. Ya no son cuatro: el dictado **no** manda solo (`STA-17`), así que su texto es del usuario y no se retiene nunca. El owner pidió explícitamente que el Repetir y el Traducir compartieran la retención ("es la misma situación que lo que sucedía con el botón de tiempo real").<br>· La retención se prende **en `requestTranslation`**, el único lugar por donde pasan las dos últimas, y no en cada llamador: es donde se sabe que la frase **salió de verdad**; si se retuviera en el llamador, un envío rechazado (micrófono prendido, frase en vuelo, sin conexión) dejaría el campo bloqueado sin nada que firmar. El en vivo tiene su propio camino (`sendLivePhrase`) y **no** retiene: con el mic prendido Traducir ya está deshabilitado por `micActive`, y la retención solo cambiaría el texto del campo por el de la frase anterior mientras el usuario sigue hablando.<br>· Al cumplirse, **el texto se borra y el campo y Traducir vuelven en la misma asignación**: no existe el estado intermedio de "campo vacío con el botón muerto", que es el que hace que la app parezca trabada.<br>· 1,5 s es lo que pidió el owner, y con 5 s se quejó de que era mucho rato. La frase no se pierde: ya está en el historial y en el panel de conversación.<br>· El aviso "Ya está firmado." **no** se muestra mientras el estado es `Translating`: durante la traducción el campo ya está bloqueado por `busy`, que se explica solo, y pisar el estado con "ya firmado" antes de que la frase ni se haya traducido era una contradicción a la vista.<br>· **Apagar el TR deja el campo limpio** en vez de retener (`stopCapture`). Para entonces lo que decía el campo ya se mandó y se firmó, así que no hay nada que impedir reenviar — con Traducir de vuelta a estar disponible, dejarlo puesto era una invitación a firmarlo otra vez. Lo único que sobrevive es el texto de un dictado sin mandar, que es del usuario y vuelve al campo con su estado de "esperando confirmación" intacto.<br>· Se suelta antes si al campo llega otro texto, si arranca una captura nueva, si arranca un Repetir (`setInput` lo ignora mientras `replayPending`), o si el turno falla (`releaseSentText`): en los cuatro casos el texto del campo ya no es la frase que se mandó.<br>· Los dos momentos tienen log (`texto ya enviado` / `texto retenido borrado`) **a propósito**: 1,5 s es más corto que el ida y vuelta de `mobile-mcp`, así que el árbol de accesibilidad no alcanza a ver la ventana y el logcat es la prueba. |
| STA-19 | Apagar el micrófono | `[N]` | **2026-10-15, cambiado: ahora sí queda algo esperando, y a propósito.** En **dictado**, apagar manda `end` y **espera el `final`** (`dictationAwaitingFinal`): la captura se cortó antes de que la pausa del STT llegara, así que lo que se dijo todavía no está transcrito. Hasta que el `final` llega, el campo muestra la hipótesis que se está formando y el texto todavía puede cambiar. Ese es el único caso en que de verdad hay algo esperando, y es lo que hace que el timeout siga teniendo sentido.<br>· Si la pausa del STT ya había cerrado la frase, no hay utterance abierto y el `end` sería un viaje de ida y vuelta y un `final` vacío de ruido: por eso `stopDictation` recibe `endIsRedundant` y a veces no lo manda.<br>· En **en vivo**, apagar manda `end` y devuelve la interfaz enseguida: `signing` si al avatar le queda cola, `done` si no. No se queda en `processing` esperando una transcripción que ya no va a llegar.<br>· En los dos, si no volvió nada en toda la sesión, se vuelve a `idle` de inmediato con "No se escuchó nada." |
| STA-15 | Turno por oración | `[N]` | **2026-10-15.** Ya no hay nada "fragmentado": `streamedSegments`, `streamingGlosses`, `streamingTurn` y `streamMutex` se eliminaron con el camino por palabras. Lo que queda son `pendingSegments` (un `sortedMap` por `index`) y `pendingTotal`, que se limpian con `resetTurn()` al empezar cada frase y en `fail()`: un turno que murió a mitad no puede filtrar oraciones al siguiente.<br>· La cola se arma cuando `pendingSegments.size >= pendingTotal` (`flushSegments`), o sea cuando llegó la última oración — nunca a mitad, que era justo lo que había que evitar.<br>· `turnStartedAt` arranca cuando se **manda** la frase, no al abrir el micrófono. Antes arrancaba al prender el micro para que la latencia incluyera el silencio previo a la primera palabra; con entrega por oraciones la unidad es la frase completa y ese tiempo es del turno de voz, no del de traducción. Lo que sí tiene que incluir el reloj es la espera: si la frase anterior seguía en vuelo cuando llegó esta, esa espera es del turno. |
| STA-10 | Estado visible en stage + panel | `[P]` | El estado alimenta el hint del stage y el estado del avatar en Conversación. |
| STA-11 | Estado **connecting** | `[N]` | "Conectando con el servidor..." mientras abre el WebSocket. |
| STA-12 | Estado **offline** | `[N]` | "Sin conexión con el servidor" + reconexión automática con backoff (RF-INT-COM-01). |
| STA-13 | Hint con glosas | `[N]` | Mientras `signing`, el hint del stage muestra las glosas de la secuencia en vez del label. |
| STA-14 | Mensaje transitorio | `[N]` | Un error del backend se muestra en el hint durante 3 s y vuelve a `idle` (RF-UI-05). |

---

## 12. Gloss (traducción español → LSC)

Las glosas ya **no** se inventan en el cliente: las produce `../sign-ai-backend`
(paso 2 del plan en `openspec/changes/integracion-backend/tasks.md`). El
placeholder local se eliminó.

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| GLS-1 | Glosas del backend | `[N]` | `TranslationSegment`/`TranslationGloss` espejan los modelos Python. La frase viaja por el WebSocket como `{"type":"text","text":…}` y la respuesta es **un `segment` por oración** (`{"type":"segment","index":…,"total":…,"segment":…}`) seguido de un `done`. La app los acumula y solo arma la cola cuando la frase está completa, para no ejecutar media frase. |
| GLS-11 | Oraciones, no fragmentos | `[N]` | **2026-10-15.** El backend tiene **un** modo de entrega. Cada `segment` es **una oración** con `index` y `total` reales desde el primero, y la app arma la cola completa cuando llega el último. Ya no existe `stream`, ni `provisional`, ni `source_text`: el frame es exactamente el mismo que usaba el camino de frase completa, y el parser queda tolerante para que un backend viejo que mande los campos no rompa nada.<br>· `total` siempre conocido **no** es un detalle: es lo que permite no firmar media oración. Con el camino por palabras, `total: 0` decía "en un utterance abierto nadie sabe cuántas son todavía" y la app encadenaba igual.<br>· El backend **traduce solo lo que el cliente manda como `text`**. La voz entra por el mismo socket pero vuelve como `transcript`, y esa transcripción **no se traduce**: si se tradujera, la app la tomaría por una frase de voz y la reenviaría, y la misma frase saldría otra vez. Ese bucle existió y quemó el backend —una frase repetida ~2.900 veces, 44 % de CPU— hasta que se le quitó el eco a `handle_text`; `verify_client_frames.py` lo fija con `THREE_MESSAGES = ["segment", "segment", "done"]`, una sola lectura de tres mensajes. |
| GLS-12 | El orden lo garantiza el backend | `[N]` | **2026-10-15.** Ya no hay `streamMutex` ni una carrera que ordenar. El backend divide el texto en oraciones y las traduce **en paralelo** — por eso una frase larga sale más rápido en conjunto que si fuera una sola — pero las **manda en orden**, y `verify_client_frames.py` lo verifica con tres hipótesis y `THREE_MESSAGES`.<br>· En la app el orden es por construcción: `pendingSegments` es un `sortedMap` por `index` y la cola se arma cuando `size >= total`. Si llegaran desordenadas, el índice las ordena igual; no hace falta un mutex porque no hay dos. Insertar una oración puede suspender (hay que bajar un asset remoto), así que lo que evita que dos oraciones en vuelo se anexen al revés es el `index`, no un cerrojo.<br>· Lo que sí quedó del camino viejo es una consecuencia útil: con el `Mutex` nadie se pregunta ya por el orden **entre frases**, porque solo puede haber una en vuelo (`livePhraseBacklog`, un único lugar de espera, y sin él una frase en vivo se perdería). |
| GLS-2 | Tipo de frase | `[N]` | `PhraseType` (los 5 valores del backend) + `UNKNOWN` para no crashear ante un valor nuevo. Alimenta los marcadores no manuales. |
| GLS-3 | Glosa no disponible | `[N]` | `available == false` → la glosa no se reproduce y se reporta en el caption (RF-CAT-03). |
| GLS-4 | Transcripción del backend | `[N]` | El evento `transcript` se refleja en el input, para poder corregirlo antes de reenviar (RF-UI-02). |
| GLS-5 | Fallback de seña de prueba | `[N]` | Con el catálogo vacío toda glosa llega desconocida. El ajuste "Seña de prueba si falta en el catálogo" hace que use la seña horneada del APK, para poder ver la cola. **Sigue en `true` por defecto aunque el catálogo ya esté sembrado**: es lo que hace que las 18 señas sin animación (`0032`–`0049`) muestren algo en vez de "Seña no disponible". Apagarlo activa `GLS-3` para ellas. |
| GLS-9 | Resolución de la ruta del asset | `[N]` | `glb_animation_path` del backend es un path **same-origin dentro del APK** (`signs/<id>.glb`), porque las animaciones se empaquetan y no se sirven por HTTP. `SignsCatalog.localAssetOrNull()` lo reconoce —validándolo contra `available`— y devuelve el path tal cual para que la escena lo cargue directo; cualquier otra cosa cae al camino de descarga de `SignAssetRepository`, que es donde viviría un asset futuro alojado en el backend. Sin esto el backend devolvería una ruta que su propio servidor no tiene, la descarga daría 404 y **todas** las glosas caerían al sign por defecto. Verificado en el Redmi: cola de 3 señas reproducida con **cero** peticiones HTTP y sin directorio de caché. |
| GLS-10 | Anunciada pero no exportada | `[N]` | `SignsCatalog.isOwnAssetPath()` distingue "no es un path nuestro" de "es nuestro pero todavía no lo exportamos". Una glosa de las 18 sin animación (`0032`–`0049`) llega con `available == true` y un `signs/<id>.glb` que no tenemos: es nuestro layout, así que **nadie** lo va a servir por HTTP, y pedirlo es un 404 garantizado en cada frase que la mencione. Va directo al sign por defecto sin tocar la red. Solo un path fuera del layout `signs/*.glb` baja por `SignAssetRepository`. Medido en el Redmi contra el mock: **2 peticiones y 2 × 404 sin este requisito, 0 con él**, sobre la misma frase y las mismas glosas. |
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
| BRG-11 | Marcadores no manuales | `[N]` | Reemplazado el 2026-10-13: el motor procedural que aplicaba ceja por `PhraseType` ya no existe. Los cues vienen horneados en el clip. El rig nuevo sí tiene huesos faciales (28: ceja, párpado, mandíbula, boca, mirada), así que la expresión facial y MNM **vuelven a ser posibles** — es el pendiente que abre esto. |
| BRG-12 | `avatarReady` (JS→Kotlin) | `[N]` | La escena avisa cuando el personaje está parseado y en escena. `onPageFinished` dispara mientras el GLB de 10 MB todavía baja, así que usarlo para esconder el splash dejaba ver un canvas negro. Si el `WebMessageListener` no está soportado, se vuelve al fallback por `onPageFinished` y el timeout de 8 s de `SignAiSplash`. |
| BRG-13 | Variantes de seña | `[N]` | 10 de las 32 señas traen 2–4 tomas. `play()` elige una al azar, así la misma glosa no se ve idéntica cada vez. |
| BRG-14 | `appendQueue(json)` / `playFrom(i)` | `[N]` | **2026-10-15, sin uso.** Los dos siguen en el puente (`SignAiCommands`, `app.js`) y **no los llama nadie**. Nació para el camino por fragmentos, donde `setQueue` no se podía reusar porque asume la cola completa y salta al índice 0; con entrega por oraciones la app arma la cola entera y usa `setQueue` + `play()`, el mismo camino que el "Repetir".<br>· Se quedan por compat: `BRG-8` declara el contrato retrocompatible y quitarlos lo rompería sin ganar nada. `queueState()` tampoco lo llama Kotlin — sirve desde la consola del WebView (Ajustes → AVANZADO → depurar) para ver `backlog`, `catchUp` y `speed`, que es como se verificó el adelanto automático (`BRG-15`). |
| BRG-15 | Adelanto automático | `[N]` | Las 32 señas exportadas duran 2.08–2.75 s (promedio **2.15 s**, del `manifest.json`) y cada palabra se traduce en una seña: la traducción es rápida, la animación es el cuello de botella. Cuando la cola tiene **2 o más** señas pendientes, la escena firma a **1.6×** y vuelve a la velocidad del usuario en cuanto se vacía. `setSpeed` guarda el valor del usuario en `state.baseSpeed` y el adelanto multiplica sobre él en vez de reemplazarlo. La decisión se toma en `tickPlayback` y no donde llegan las oraciones, porque "cuántas me faltan" solo existe ahí.<br>· **Remedido con el flujo por oraciones** (2026-10-15, `tools/medir-retraso.py`, las 28 frases de `../sign-ai-stt/fixtures/audio`): **los números no se cambian**. El origen del desfase no era el modo de entrega, así que sobrevive: el avatar no termina de firmar antes de que el usuario termine de hablar en **17 de 28** frases, y con el adelanto todavía en **7**. Firma/habla da una mediana de **1,26×** (máx. 3,72×), que 1,6× cruza (mediana 0,79×).<br>· Lo que **sí** cambió es la forma: con entrega por oración la cola está vacía mientras el usuario habla y se llena de golpe al cerrar la frase, así que el adelanto ya no está prendido todo el tiempo — y está bien que no lo esté, porque acelerar un clip que ya va solo no aporta nada. Como el umbral se mide después de que arranque la seña en curso (`length - idx - 1`), hace falta una frase de **3 señas o más** para dispararlo, y con las 28 frases de prueba —cortas, de mediana 2 palabras y 1 seña— solo una lo dispara. Verificado en el dispositivo con la consola del WebView.<br>· La conclusión incómoda es que **el avatar no puede firmar al ritmo de la voz** y eso no lo arregla este archivo: harían falta ~5×, que es un fast-forward, no una seña. Lo que se puede afirmar es otra cosa y es la que se midió: el desfase absoluto es de **+0,4 s** de mediana, no de segundos. |

---

## 13bis. Observabilidad (`tiempo-real`)

Sin instrumentación no hay forma de afirmar que algo mejoró, así que estos
requisitos son parte del trabajo y no un extra. Los tres servicios exponen
`/metrics` (formato de exposición de Prometheus) y `/metrics.json` (lo mismo,
estructurado, que es lo que leen los checks automáticos porque parsear texto con
expresiones regulares es frágil).

| ID | Requisito | Dónde | Criterio |
|---|---|---|---|
| OBS-1 | Latencia de la primera seña | backend `/metrics` | `rt_first_sign_ms` = de la **frase recibida** a la **primera oración enviada**. Es **la** métrica que decide si el producto se siente en tiempo real. El nombre se conservó a propósito aunque cambiara lo que mide: la línea base publicada (913 ms p50 / 1336 ms p95) es del camino por palabras y **no es comparable** con esta. Con el flujo por oraciones la primera seña sale al terminar la primera oración, no a mitad de camino, así que la cifra sube por construcción — es el costo conocido de la opción A (§7 de la auditoría). Falta remedir con las 28 frases. |
| OBS-2 | Latencia de la frase | backend `/metrics` | `rt_phrase_total_ms` = de la frase recibida a la última oración enviada (antes `rt_utterance_ms`). `rt_sentence_delay_ms` por oración, para ver si se entregan espaciadas o de golpe al final — que con el flujo por oraciones es justamente la forma **esperada**: la cola se llena al cerrar cada frase, no durante. `rt_phrase_sentences` es el histograma de cuántas oraciones tiene cada frase, que es el número que decide cuánto vale la entrega por oración. |
| OBS-3 | El audio está fluyendo | backend `/metrics` | `audio_bytes_total` y `audio_chunks_total`. Es el único tramo del sistema que no produce salida: si el micrófono del teléfono se rompe, el STT se queda mudo y no llega ningún `transcript`. Cuenta bytes además de fragmentos, así distingue "no manda nada" de "manda el doble". Del lado del teléfono, `SignAiMetrics` cuenta `dictado_frases` y `dictado_palabras`, que son los que dicen si el dictado está transcribiendo de verdad y no solo captando ruido. |
| OBS-4 | Contención de la GPU | STT `/metrics` | `stt_partial_queue_ms`. Antes de este trabajo un parcial podía retrasar un final 4–8 s; el guard de regresión es que este número no vuelva a subir. |
| OBS-5 | Saturación de audio | STT `/metrics` | `stt_partial_lag_ms`: audio recibido menos audio procesado. Sube = la GPU no da abasto y el avatar va a quedar atrás respecto a la voz. |
| OBS-6 | Descartes con motivo | STT `/metrics` | `stt_dropped_total{reason="rms"\|"no_speech"\|"logprob"}`. `reason="logprob"` merece atención porque cuenta audio que Whisper transcribió **bien** y el filtro tiró igual: con `-0.8` se comía "cómo estás" y "estoy triste". Si sube, el filtro está comiéndose audio real. |
| OBS-7 | Latencias del teléfono | logcat `SignAiMetrics` | Fragmento→cola y duración de turno, p50/p95/max, en un anillo de 256 muestras. Es lo único que el dispositivo puede medir: los otros dos servicios no ven qué pasa después de que el mensaje llega al teléfono. |
| OBS-8 | Traza de segmentos | logcat `SignAiNet` | Cada `segment` recibido con `stream`, `provisional`, `source_text` y sus glosas. Con eso se separa "el backend mandó otra cosa" de "el avatar no la firmó". |
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
| BRD-1 | Nombre de la app — **ABIERTO** | `[P]` | Se mantiene **"SignAI"** por ahora; **seguramente cambiará** (prototipo: "Seña"). Debe ser una **constante/string resource única** (logo, eyebrow, títulos). |
| BRD-2 | Pantalla About | `[P]` | Logo, título, copy "Una herramienta que transforma tus frases en español a Lengua de Señas Colombiana mediante un avatar 3D." |
| BRD-3 | Meta del About | `[P]` | "Proyecto de tesis" · "Pontificia Universidad Javeriana" · "Versión 1.0". |
| BRD-4 | Versión dinámica | `[N]` | "Versión 1.0" debe leer de `BuildConfig.VERSION_NAME`, no hardcodeado. |
| BRD-5 | Icono de lanzador propio | `[N]` | La app tenía el icono por defecto de Android. Ahora usa la "S" de `SignAiBranding.LogoLetter` como trazo geométrico: adaptive icon (`mipmap-anydpi-v26`, tile `#171717` + S `#F5F5F1`) y fallback plano para API < 26 (`drawable/ic_launcher.xml`). Ver `drawable/ic_launcher_foreground.xml`. |

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
