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
| EDG-23 | El drawer inactivo no compone ningún gesto | `[N]` | Un drawer cerrado cuyo contrario está abierto no tiene **ningún** nodo interactivo: ni franja de borde, ni pestaña, ni `draggable` en el panel (el panel sólo se arrastra cuando está abierto o en movimiento, `isOpen || fraction > 0.001`). Esto cierra el último resquicio: deslizar desde el borde extremo ya no puede abrir el panel opuesto por encima de uno abierto. |

---

## 4. Avatar stage

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| STG-1 | Avatar 3D | `[A]` | `avatar.glb` renderizado con Three.js en el WebView (protagonista visual). |
| STG-2 | **Hint de estado** | `[P]` | Arriba a la izquierda, dot "live" + texto del estado (`STA-*`). |
| STG-3 | **Caption** | `[P]` | Abajo al centro: `LSC · Avatar 3D` (dot separador). |
| STG-4 | Botón **Repetir** | `[P]` | Visible solo en estado `done`; repite la última frase. |
| STG-5 | Escena con glow/sombra de piso | `[P]` | Glow radial detrás + elipse de piso (en el prototipo; el 3D real los reemplaza). |
| STG-6 | Animación "is-signing" | `[P]` | Feedback visual mientras reproduce (en la app: el propio avatar animándose). |
| STG-7 | Área del avatar respeta el layout | `[P]` | Ocupa el alto disponible entre safe-area superior y el control panel. |
| STG-8 | Long-press resetea pose/cámara | `[A]` | Long press (500 ms) en el canvas resetea pose + cámara (`app.js`). |

---

## 5. Control panel (barra inferior)

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| CTL-1 | Campo de texto | `[P][A]` | Entrada de frase en español, placeholder "Escribe una frase...". |
| CTL-2 | Enviar con **Enter** | `[P]` | Enter dispara `send()`, con guard de composición IME (`isComposing`/`keyCode 229`). |
| CTL-3 | Botón **Historial** | `[P][A]` | Ícono a la derecha del input; abre el bottom sheet (o dropdown). |
| CTL-4 | Botón **Traducir** | `[P][A]` | Ícono + label; ancho 75%. Deshabilitado si input vacío o estado translating/signing. |
| CTL-5 | Botón **Micrófono** | `[P][A]` | Ancho 25%. Alterna estado `listening`; visual "activo" mientras escucha. |
| CTL-6 | Mic es placeholder | `[A]` | Hoy `play("mic")`; sin reconocimiento de voz real. |
| CTL-7 | Estado **activo** del mic | `[P]` | Fondo strong + ring cuando `listening`. |
| CTL-8 | Estado **disabled** de Traducir | `[P]` | Opacidad reducida cuando no se puede enviar. |
| CTL-9 | Foco del input | `[P]` | Borde strong + ring al enfocar. |
| CTL-10 | Home indicator | `[P]` | Barrita inferior 112×4 (`strong` al 70%). **Se conserva** por fidelidad con el prototipo. |
| CTL-11 | Safe area / IME | `[A]` | Respeta navigation bar y teclado (`imePadding`). |
| CTL-12 | Controles bloqueados durante un replay | `[N]` | Desde "Repetir" (stage o drawer) hasta que la seña termina, todos los controles (mic, Recientes, input, Traducir) quedan deshabilitados y atenuados al 35% (`replayPending`, se libera con `onPlaybackDone`). |

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
| STA-9 | Flujo del mic | `[P]` | `listening`: el prototipo auto-rellena una frase y procesa. En la app: placeholder. |
| STA-10 | Estado visible en stage + panel | `[P]` | El estado alimenta el hint del stage y el estado del avatar en Conversación. |

---

## 12. Gloss (traducción español → LSC)

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| GLS-1 | Función de gloss | `[P]` | `glossFor(text)` pura, en `domain/`. |
| GLS-2 | Regla "universidad" | `[P]` | Contiene "universidad" → "YO UNIVERSIDAD IR QUERER". |
| GLS-3 | Regla "gracias" | `[P]` | Contiene "gracias" → "GRACIAS MUCHO". |
| GLS-4 | Regla "ayuda" | `[P]` | Contiene "ayuda" → "YO AYUDA NECESITAR". |
| GLS-5 | Regla "cómo" | `[P]` | Contiene "cómo" → "TÚ CÓMO ESTAR". |
| GLS-6 | Fallback | `[P]` | Mayúsculas sin `¿?¡!`; vacío → "ESPERANDO MENSAJE". |
| GLS-7 | Placeholder honesto | `[N]` | Es aproximado; el motor de gloss real está fuera de alcance. |

---

## 13. Puente Kotlin ↔ JS (WebView)

| ID | Feature | Origen | Descripción / criterio de aceptación |
|---|---|---|---|
| BRG-1 | `window.SignAI.play()` | `[A]` | Reproduce el sign.json. Guard `?.`. |
| BRG-2 | `setDebug(bool)` | `[A]` | Activa overlay de debug + `#status`. El texto del overlay usa colores oscuros en tema claro (clase `light` en `<html>`) y coloca `#debug` arriba y `#status` abajo para no solaparse. |
| BRG-3 | `setFreeCamera(bool)` | `[A]` | Cámara libre (orbitar/zoom sin límite). |
| BRG-4 | `setGrid(bool)` | `[A]` | Grilla/suelo. |
| BRG-5 | `setBackground(bool)` | `[A]` | Fondo bosque. |
| BRG-6 | `setSpeed(0..1)` | `[N]` | Velocidad de reproducción de señas (slider `SET-4`). Aditivo. |
| BRG-7 | `onPlaybackDone` (JS→Kotlin) | `[N]` | Evento al terminar `play()`; alimenta `STA-8`. Vía `WebMessageListener` (`SignAIBridge`). |
| BRG-8 | Contrato retrocompatible | `[A]` | Los 5 comandos actuales y el guard `?.` no cambian. |
| BRG-9 | `setTheme(dark)` | `[N]` | Aditivo. Hace que el fondo del canvas 3D siga el tema claro/oscuro (mitiga el riesgo de light mode) y alterna la clase `light` en `<html>` para contrastar el texto de debug/estado. |

---

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
| OM-4 | Auto-relleno del mic | `[P]` | **Placeholder** | Sin reconocimiento de voz real todavía (`CTL-6`). |
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
