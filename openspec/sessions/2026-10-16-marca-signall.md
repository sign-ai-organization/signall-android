# Sesión 2026-10-16 — Marca SignALL, paleta y arreglo de la sidebar

**Fecha:** 2026-10-16 · **Rama:** `flujo-oraciones` · **Alcance:** rebranding
completo (nombre, icono, wordmark, paleta oscura) + los fixes de front-end que
el owner encontró probando en el Redmi Note 9S físico.

## Qué pidió el owner

1. Rebrand de **"SignAI" → "SignALL"** con los PNG de icono y logo que entregó.
   Icono → lanzador. Logo → splash. Icono → sidebar derecha y sección Acerca de.
2. **Paleta oscura nueva** (`#1c4cfc` acento, `#0c142c` fondo, texto blanco).
   **Solo en dark**: el tema claro se queda exactamente como estaba.
3. Que no aparezca más la palabra **"WebView"** en nada que el usuario lea.
4. Arreglos en la sidebar derecha (abajo, en orden):
   - el desplegable de **Personaje**, ~5% más angosto,Flush right;
   - las opciones **debajo** del botón, no flotando;
   - el desplegable de **Seña** scrolleable (32 opciones);
   - "Seña de prueba si falta en el catálogo" → **apagado** por default, rótulo
     corto y switch alineado;
   - la opción de personaje que decía **"Gris"** no corresponde a nada;
   - el slider de **Tamaño de interfaz** → botones **− / +**;
   - el número entre los botones, **centrado y en negrita**;
   - el desplegable de Personaje otra vez, **15% más chico**.
5. Al volver de **Acerca de** aparece un canvas vacío durante segundos → que salga
   la pantalla de carga.
6. **Hitbox** de las pestañas de los drawers más grande, **sin tocar el diseño**.

## Decisiones

### D-1 — El dark arranca activo

`Settings.darkTheme = true`. Seidata al cambiar la paleta: el dark es el tema de
la marca ahora y el claro quedó como el modo alterno.

### D-2 — Los derivados de la paleta se aprueban con números

El owner dio los tres colores base. Los derivados (`surface #141D3A`,
`outline #263463`, `muted #A7B0C7`, `soft #1E2A4D`) se derivaron de ahí y se
pidieron **aprobaron con su ratio**: `outline` da 1.52:1, que es lo que ya daba
el `#32322F` del prototipo, así que no perdemos contraste de borde.

**Consecuencia que hubo que corregir:** `#1C4CFC` como marca fina sobre `#141D3A`
da **2.8:1**, y sobre `#0C142C` **2.3:1** — por debajo de 3:1. Por eso el check
del personaje seleccionado va en `colors.text` (blanco) y no en `strong`. La
tabla de derivados está documentada en `Color.kt`.

### D-3 — "Gris" sale del enum, Julián pasa a ser el default

`Character` **nunca llega a la escena 3D**: el `AvatarWebView` siempre carga
`models/avatar.glb`. Las opciones son solo el nombre y el punto de color del
desplegable. Se ofrecía un "Gris" que no correspondía a nada y además era el
**default**, así que la primera pantalla anunciaba un muñeco que no se veía. Se
quitó; el default es Julián, que es el avatar que realmente se muestra. Una
instalación que tuviera `"Gris"` guardado cae al default por el `firstOrNull` del
repositorio, no rompe.

### D-4 — El splash vuelve a levantarse al salir de Acerca de

Acerca de es exclusivo (`NAV-8`): el `AvatarWebView` está dentro del `else` del
`if (showAbout)`, así que **sale de la composición y se destruye**. Al volver se
crea uno nuevo y hay que volver a descargar y parsear el GLB de 10 MB. Se veía el
canvas vacío durante segundos.

El splash ya existía y ya espera al `avatarReady` real, así que la solución es
`loading = true` en el `onBack` y nada más. **Pero el timeout de seguridad tenía
`LaunchedEffect(Unit)`**: solo cubría el primer arranque. Con la recarga, un
fallo dejaba el splash puesto para siempre. Pasó a `LaunchedEffect(loading)`.

### D-5 — La hitbox del tab crece, el dibujo no

MIUI se come los primeros ~15 dp de cada lado con sus gestos de sistema, y el
tab se dibuja de **20 dp pegados al borde**: del ancho útil quedaban muy poco y
en el Redmi no se acertaba. Se agregó `handleHitPadding = 28.dp` que suma ancho
**solo al nodo que recibe el toque**. Como `contentAlignment` sigue siendo
`CenterEnd`/`CenterStart`, el tab se dibuja pegado al borde y el extra queda
invisible hacia adentro. Zona táctil: 76 dp cerrado, 100 dp con el panel abierto.

La franja invisible del borde (`edgeStripWidth`, 48 dp) **no** se tocó: es solo
`draggable` horizontal y ensancharla se comería más gestos de órbita del canvas.

### D-6 — El `Popup` de Compose ignora `alignment`/`offset` en Android 11

El menú se abría descuadrado. En este dispositivo la ventana del `Popup` no
respeta ni `alignment` ni `offset`: hay que estirarla a `fillMaxSize()` y
colocar el menú con un `offset` normal de Compose, medido contra
`view.rootView.getLocationOnScreen`. Con eso el menú sale **debajo** del trigger
y con su ancho exacto; si no cabe, **sube** (que es lo que hace el selector de
Seña, que está al final de la lista). El interno lleva `verticalScroll`, sin lo
cual no se llegaba ni a la opción 27.

## Lo que se cambió

| Área | Cambio |
|---|---|
| `SignAiBranding.kt` | `Name = "SignALL"`, `Eyebrow`, `Icon`, `logo(darkTheme)` |
| `SignAiBrandTile.kt`, `SignAiWordmark.kt` | Componentes nuevos de marca |
| `SignAiSplash.kt` | Wordmark (se quitó el eyebrow), progreso |
| `SettingsSidebar.kt`, `AboutScreen.kt` | Tile del icono, rótulo corto, stepper |
| `Color.kt` | `DarkColors` de marca; `LightColors` intacta |
| `Theme.kt` | `LocalIsDarkTheme` |
| `Dropdown.kt` | Reescrito: ventana full-screen + `offset` + `verticalScroll` + flip |
| `SignAiSizes.kt` | `characterTriggerWidth` 196 → 167 dp, alto 42 → 36 dp |
| `SignAiStepper.kt` | Nuevo: `−` `n` `+` con paso 5 |
| `SignAiSmallIconButton.kt` | Nuevo parámetro `enabled` |
| `SettingRow.kt` | Label con `weight(1f)`: el rótulo envuelve, el control no se pega |
| `EdgeDrawer.kt` | `handleHitPadding` |
| `MainScreen.kt` | Splash al volver de About + timeout rearmado |
| `Settings.kt` | `darkTheme = true`, `demoSignFallback = false`, sin `Gris` |
| `app.js`, `index.html` | Canvas `#0c142c`, grilla `#1e2a4d` |
| `app/build.gradle.kts` | `versionName` `1.0.0-webview` → `1.0.0` |
| `values*/themes.xml`, manifest | `Theme.SignAIWebView` → `Theme.SignALL` |
| `tools/generar-marca.py` | **Nuevo**: genera los assets de marca desde los PNG maestros |

## Decisión que el owner todavía no tomó

**Renombrar el paquete `com.signall.app`.** No es visible para el usuario y
renombrarlo cambia la identidad de la app instalada (habría que reinstalar) y
toca ~30 archivos más `AGENTS.md` y los flows de `tools/maestro`. Se dejó como
está y se anotaron las consecuencias en `BRD-1`/`BRD-6` del spec. **Si el owner
lo pide, es un cambio mecánico de una sentada.**

## Gotchas de esta sesión

- **potrace escribe polybezier**, no path: un letter con muchos grupos, y **cada
  grupo es relativo a su propio arranque**, no encadenado con el anterior, en un
  espacio `y` para arriba (`translate(0,H) scale(1,-1)`). Interpretarlo mal da una
  mano plausible y equivocada. `generar-marca.py` asserta el bbox de la curva
  contra la máscara justamente por eso.
- Geometría del adaptive icon: el maestro de 528 px mapea al visible 72/108, así
  que el margen es `(108-72)/2 = 18`; el radio máximo de la mano es 0.401 del
  tile, o sea entra en el círculo seguro.
- Las capturas que se leen en la conversación vienen escaladas ~0.833× (900 px de
  ancho mostrado contra 1080 reales). Para coordenadas reales, leer el árbol de
  accesibilidad con `mobile-mcp`, no la imagen.

## Verificado en el dispositivo (Redmi Note 9S, Android 11)

- Adaptive icon y nombre "SignALL" en "Info de la aplicación".
- Los dos desplegables abren debajo del trigger y con su ancho; el de Seña sube y
  scrollea hasta la 27+.
- Stepper: 50 → 75 con cinco toques, número centrado y en negrita, `−` deshabilitado
  en el mínimo.
- "Seña de prueba si falta" apagada por default y con el switch alineado.
- Personaje: Julián / Samuel / Ximena.
- Acerca de: tile del icono, "Versión 1.0.0", sin "webview".
- Volver de Acerca de muestra el splash y la escena vuelve detrás.
- Tocar el tab en **x = 920 px** abre Ajustes (quedaba fuera de la zona vieja de
  948–1080) y en **x = 160 px** abre Conversación.