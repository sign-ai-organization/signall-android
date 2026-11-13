# SignALL: un solo GLB con las 50 señas adentro

**Fecha:** 2026-10-22, mañana · **Rama:** `main`

## Qué llegó

El owner trajo `SignALL (GLB por Sena).glb` (27,16 MB), exportado por un compañero: **un solo
archivo con el personaje, la cara y las 50 señas como animaciones separadas** nombradas
`0000`..`0049`, en vez de un `.glb` por seña. La pregunta era si sirve para la app y,
si sirve, probarlo.

El archivo quedó en `GLB SignALL/` (gitignored, igual que `GLB Senas/`), no en la raíz.

## Qué se verificó antes de tocar nada

Leído con `tools/glb_pose.py` y renderizado en Chromium headless con el `app.js` real:

- **Mismo personaje**: 102 articulaciones de skin, en el mismo orden y con los mismos
  nombres que el `models/avatar.glb` que ya estaba; 11 mallas y 12 materiales. La geometría
  viene **decimada** (86 099 → 55 679 vértices) y las texturas en **WebP** (3,31 MB → 0,74
  MB), así que el archivo pide `EXT_texture_webp`; three r169 lo soporta con
  `GLTFTextureWebP`.
- **50 clips reales**: `0000`..`0049`, todos distintos entre sí (hash por accessors) y con
  movimiento de mano entre 19 y 70 cm de recorrido. Los 18 que faltaban en el APK
  (`0032`–`0049`) **existen**. Que cada uno sea la seña correcta es contenido: no lo puede
  validar una sesión, lo tiene que ver el owner o el director.
- **Los clips `0000`–`0031` no son byte a byte los de `signs/`**: 20 de 306 tracks difieren
  (cuello y mandíbula sobre todo, hasta 0,116 en un cuaternión). Es otro export, con cara.
- **La pose de descanso no estaba horneada**: los brazos salen a 44° del reposo que tiene el
  APK (pose de maniquí de Cascadeur). Sin hornearla, el muñeco abre con los brazos abiertos y
  vuelve a esa pose al terminar cada frase.
- **68 morph targets, casi todos muertos**: solo 5 se animan (`Male`, `LIVE_KEY_PERMANENT`,
  `LIVE_KEY_TEMP_aged_young`, `cor_ElbowBend_Rt`, `cor_ShoulderSideRaise_Rt`); ninguno de los
  de cara (`e_Smile`, `jawOpen`, …) tiene un keyframe. Son ~15 MB de datos, más lo que
  `three.js` sube a la GPU para nada.

## Qué se hizo

1. **`app.js`: los clips del avatar se resuelven por nombre.** `embeddedClips` se llena con
   `gltf.animations` al cargar el avatar y `embeddedClipsFor(url)` traduce
   `signs/<id>_0000_0000.glb` a los clips `<id>` / `<id>.001` … sin pedir nada. Si el avatar
   no trae animaciones, todo sigue por el camino de siempre (aditivo, compatible con el
   layout viejo y con los assets que baje el backend).
2. **`tools/pack_signall.py`** (nuevo, sin dependencias): valida que estén los 50 clips, hornea
   la pose de descanso con la regla de `rest_pose.py` pero leyendo el frame de referencia del
   propio archivo (clip `0029`), **poda los morph targets** que no anima nadie ni pesan por
   defecto, **compacta el buffer** copiando cada `bufferView` una sola vez y solo el tramo en
   uso, y escribe `models/avatar.glb` + `manifest.json` + `SignsCatalog.kt` con el generador
   que ya existía (`write_catalog(manifest, packed=True)`).
3. **`SignsCatalog.kt` regenerado con las 50** (antes 32). `signs/<id>.glb` sigue siendo el
   identificador: lo que cambió es que ahora se resuelve contra los clips del avatar.
4. **`app.js` imprime `performance.now()` en "avatar listo"**, para poder medir el costo de
   arranque con `adb logcat -s SignAIWeb`.
5. **Docs alineados**: `AGENTS.md` (§ Assets web, § Neon, mapa), `tools/README.md`,
   `docs/SISTEMA.md` y los specs `avatar`, `pipeline` y `ui` (STG-1, GLS-9, GLS-10, BRG-13).

## Qué se midió

Todo en el **Redmi Note 9S físico** (`1ed6699f`, MIUI, Android 11), build `debug` con
`-Psignai.backendWsUrl=ws://127.0.0.1:8080` y `adb reverse tcp:8080 tcp:8080`, contra el
backend y el STT locales que ya estaban corriendo.

| | Archivo viejo (10,3 MB + 32 señas) | Archivo único (12,98 MB, 50 señas) |
|---|---|---|
| Avatar listo (`performance.now()`) | 1451 / 1540 / 1593 ms | 1845 / 1854 / 1855 ms |
| `oración→avatar` | 54 ms (p50 de n=2) | 61–115 ms |
| Peticiones a `signs/` en una frase | 0 (assets locales) | 0 |
| Frases con señas de `0032`–`0049` | no se pueden representar | **sí** |

- **Tamaño**: 27,16 MB de origen → **12,98 MB** (morphs 14,9 MB → 1,04 MB; animaciones
  2,62 MB; geometría 3,54 MB; texturas 0,74 MB; buffer 7,95 MB). Contra lo que había en el
  APK (10,3 MB de avatar + ~5,8 MB de `signs/` = 16,1 MB) es **~3 MB menos** y 18 señas más.
- **Fidelidad**: el recorrido de mano por clip en el archivo empaquetado es **idéntico** al
  del original en los 50 clips (comparado clip por clip, 40 fotogramas cada uno), y el render
  de `0000` y `0029` en el mismo instante es indistinguible.
- **Extremo a extremo**: `"por favor gracias"` → `POR FAVOR,GRACIAS`, 2 señas representadas en
  4536 ms, sin `signFailed`. `"yo nunca todos mucho"` → `YO,NUNCA,TODOS,MUCHO`, 4 señas en
  8241 ms: las cuatro son clips que antes no existían en el APK.

## Qué se decidió

- **El layout viejo sigue en el repo** (`signs/*.glb`, `split_signs.py`) y **sigue soportado en
  `app.js`**, pero no se pide en runtime. Borrar `signs/` (−5,8 MB en el APK) es decisión del
  owner, no de la sesión.
- **La pose de descanso se hornea en el pipeline**, no se pide al modelador: misma regla de
  `rest_pose.py` (46 huesos de brazos, `0029`, primer keyframe), referencia leída del propio
  archivo.
- **Los morph targets no animados se podan** salvo los que pesan por defecto: sacar un shape
  que pesa 0,3 deforma la malla sin que ningún keyframe lo toque.

## Qué quedó pendiente

- **`signs/`**: 32 archivos (~5,8 MB) que ya no se usan. Si el owner dice que sí, se borran y
  el APK baja otro tanto. Mientras estén, nada se rompe.
- **Variantes**: el layout viejo tenía 46 tomas para 32 señas y `pickVariant` elegía al azar;
  ahora hay una por seña. Se pueden fusionar las 14 tomas viejas al archivo único (mismo
  esqueleto) si el owner quiere conservar la variedad — decisión abierta.
- **Contenido de `0032`–`0049`**: hay que confirmar con el owner/director que cada clip es la
  seña que dice ser. La sesión solo verificó que existen, que son distintos y que se mueven.
- **`catalog` de Neon**: `tools/seed_catalog.py` ya maneja el manifest nuevo y ahora informa
  50/50 con animación, pero **no se re-sembró**. No hace falta para que funcione: los paths no
  cambiaron. Si se re-siembra, hay que reiniciar el backend (carga el catálogo una sola vez).
- **`../signall-docs` y Overleaf**: siguen diciendo que 18 señas no tienen animación. Ese
  contraste se propone, no se aplica desde acá (el `.tex` lo edita el owner).

## Segunda parte: el splash cubre toda la carga

El owner preguntó si el costo de arranque (~300 ms más) era por cada seña o solo al inicio,
y pidió que el splash no se vaya hasta que el avatar y las señas estén cargadas. **Es solo
al inicio**: por seña no hay nada que cargar, porque los clips ya están en memoria. Medido
en el Redmi el mismo día: `oración→avatar` 61–130 ms (la primera frase de una sesión recién
arrancada, 130 ms).

Lo que sí estaba mal era el splash:

1. **`avatarReady` se mandaba antes de que la escena estuviera lista.** Ahora se manda
   después de indexar los clips, resolver la seña de arranque, compilar los shaders
   (`renderer.compileAsync`, ~400–900 ms en este teléfono que antes se pagaban en el primer
   frame visible) y pintar un frame con el avatar adentro (dos `requestAnimationFrame`).
   Medido: `avatar listo` 1818–2084 ms → `escena lista` 2236–2957 ms (reloj JS,
   `adb logcat -s SignAIWeb`). El frame del fade ya tiene el avatar debajo.
2. **La barra decía 100 % mientras faltaba todo.** El progreso salía de
   `WebChromeClient.onProgressChanged`, que llega a 100 cuando terminó el HTML. Ahora la
   página ocupa el 0–45 % y la escena manda `progress:<n>` para el 45–100 % (45 → 80
   descarga, 85 GLB parseado, 90 clips indexados, 96 shaders, 100 antes de `avatarReady`).
   Verificado en video: 0 → 45 → 80 → 90 → 96 → 100.
3. **Arranque en frío: ~2,3 s de ventana del sistema antes del primer píxel de la app**
   (`adb shell am start -W` → `TotalTime: 2287`), con el gris `#181818` de MIUI. Se agregó
   `windowBackground` = grafito + wordmark (`res/drawable/window_launch.xml`), que en MIUI
   **no se ve** porque la ventana de lanzamiento del sistema la tapa; queda para AOSP.

## Tercera parte: el WebView ya no se crea en el primer frame

El video del arranque en frío mostró que el splash **no aparecía**: entre la ventana de MIUI
y el avatar había 3,4 s de gris vacío. La causa es que `AvatarWebView` se creaba en la
primera composición, y crear el WebView y empezar a cargar la página bloquea el hilo de UI
más tiempo que el propio arranque del proceso: el primer frame que la app alcanzaba a
pintar ya era el del avatar, así que el splash nunca llegaba a verse.

**Cambio** (`ui/MainScreen.kt`): `sceneMounted` arranca en `false` y un `LaunchedEffect`
espera dos `withFrameNanos` antes de ponerlo en `true`. `AvatarStage` (que es quien contiene
el `AndroidView` del WebView) solo se compone cuando está en `true`. El estado vive dentro
de la rama principal y no en la raíz de `MainScreen`, así que volver de Acerca de vuelve a
diferir igual que el arranque en frío.

**Medición** (Redmi Note 9S, `screenrecord` a 10 fps, ARRANQUE EN FRÍO, 2026-10-22):

| | Sin diferir | Con diferir |
|---|---|---|
| Primera imagen de la app | ventana del sistema (~3,4 s) y de ahí directo al avatar: **el splash nunca se vio** | ventana del sistema (~3,3 s) y de ahí **el splash** con logo y barra |
| Barra | no llega a verse | 0 → 58 → 98 → 100 |
| Avatar en pantalla | ~3,6 s | ~5,7–5,9 s (el frame del fade ya lo tiene debajo) |

El costo es real: **~2,2 s más** hasta el avatar, porque el WebView empieza a cargar después
del primer frame en vez de durante la primera composición —en la corrida sin diferir la
página se cargaba mientras el primer frame todavía no existía, y llegaba lista justo cuando
la app por fin pintaba—. Se paga con el splash delante, que es lo que el owner pidió
("siempre y cuando sea literalmente al INICIO"). Lo que se elimina es la pantalla vacía sin
logo ni barra, que era el 100 % de lo que se veía.

También se verificó el camino **Ajustes → Acerca de → Volver**: el splash vuelve a
levantarse y la escena se recrea detrás (video: 58 % a los 1,6 s de tocar Volver, avatar
visible al final del fade).

## Cuarta parte: pulido de la salida del splash, gestos, teclado y borrado de `signs/`

Cuatro pedidos del owner en la misma tanda (2026-10-22, tarde), todos en el Redmi Note 9S.

### 1. La salida del splash, el doble de lenta

La animación de salida se sentía como un corte: el avatar aparecía de golpe. En
`ui/MainScreen.kt` el cruce son dos animaciones que corren juntas —el splash se va y el
contenido entra— y las dos se duplicaron:

| | Antes | Ahora |
|---|---|---|
| `SplashFadeMs` (se va el splash) | 400 ms | **800 ms** |
| `ContentFadeMs` (entra el contenido) | 500 ms | **1000 ms** |

Medido con `screencap` en bucle (una captura cada ~450–650 ms, sin reloj confiable en
`screenrecord` porque graba a *variable frame rate*): el cruce dura ~1,0 s entre la primera
captura con las dos capas mezcladas y la primera con el contenido solo, y en el medio se ve
el avatar con el wordmark encima (`shot/s14.png` de la captura). Los valores son constantes
del código, la medición solo confirma que se ven.

### 2. Navegación por gestos: arrastrar desde el borde no llegaba a la app

El owner reportó que con gestos del sistema las pestañas se abren tocando pero no
arrastrando. **No es la app: es el sistema.** Medido con `adb shell input swipe` a la altura
de la pestaña, arrancando en distintos x (`launcher` = el gesto se lo quedó el sistema y la
app se cerró, `app` = el arrastre llegó a la app):

| x inicial | Resultado |
|---|---|
| 2 px | gesto de "atrás" → la app se va al launcher |
| 26 px | gesto de "atrás" → launcher |
| 45 px | gesto de "atrás" → launcher |
| 60 px | llega a la app y abre el panel |
| 70 px | llega a la app y abre el panel |

O sea: **~20 dp de cada borde son del sistema**, y la pestaña visible son 20 dp pegados al
borde, así que el dedo casi siempre cae en la franja del sistema. Se pidió la exclusión por
las dos vías que existen y **MIUI las ignora**:

- `Modifier.systemGestureExclusion()` en la pestaña (`EdgeDrawer.kt`), 200 dp de alto.
- `View.setSystemGestureExclusionRects` a nivel de la ventana en `MainActivity.kt`
  (`excludeDrawerEdgeBands`), con un rect de 24 dp de ancho × 200 dp de alto por borde —
  200 dp es el tope que acepta Android por borde.

Mitigación real, que es lo que pidió el owner como plan B: **todas las zonas táctiles +25 %**.

| Zona | Antes | Ahora |
|---|---|---|
| Franja de arrastre del borde (`edgeStripWidth`) | 48 dp | **60 dp** |
| Zona de arrastre del panel abierto (`handleDragWidth`) | 72 dp | **90 dp** |
| Ancho táctil de la pestaña (`edgeStripWidth + handleHitPadding`) | 76 dp | **95 dp** |
| Alto táctil de la pestaña | 160 dp | **200 dp** |
| Alto del dibujo (la pestaña visible) | 62 dp | 62 dp (no cambió) |

Verificado: tocar el centro de la pestaña abre Ajustes (el nodo mide 261×550 px en el árbol
de accesibilidad, antes 209×440), y un arrastre desde x=60 px abre Conversación.

### 3. Hueco enorme entre el teclado y los botones

Eran **dos** bugs sumados:

1. `android:windowSoftInputMode="adjustResize"` estaba en `<application>`, donde el sistema
   lo **ignora** (es atributo de `<activity>`). La ventana quedaba en `adjustPan` —se ve en
   `dumpsys window`: `sim={adjust=pan …}`— y el sistema paneaba la ventana entera hacia
   arriba al abrir el teclado.
2. `SignAiBottomBar` sumaba `.navigationBarsPadding().imePadding()`: el teclado se dibuja
   **encima** de la barra de navegación, así que su alto ya la incluye y el inset se contaba
   dos veces.

El paneo y el inset daban ~535 px de hueco (medido desde el borde inferior de "Traducir"
hasta el teclado: botones en y=1120, teclado en y=1655, sobre 2400). Ahora:

- `windowSoftInputMode="adjustResize"` en la `<activity>` → `sim={adjust=resize …}`.
- Un solo inset: `WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)` (el mayor entre
  barra de navegación y teclado), aplicado **después** del `background` para que el panel
  siga llegando hasta el borde de abajo y la píldora de navegación quede sobre el panel.

Medido después: "Traducir" termina en y=1622 y el teclado arranca en y=1655 → **33 px
(12,5 dp)**, que es el padding de 12 dp del propio panel. El status "Inactivo" volvió a
y=135 (antes el paneo lo mandaba a y=-367).

### 4. `assets/web/signs/` borrado

32 `.glb`, 5,7 MB en el APK. Nadie los pedía: toda glosa del catálogo resuelve su clip
adentro de `models/avatar.glb` (`embeddedClipsFor` en `app.js`). Verificado en frío después
del borrado: `señas empaquetadas en el avatar: 50`, `escena lista · 2431 ms`, cero pedidos
a `signs/`.

**La ruta `signs/<id>.glb` se queda en el código**: es el identificador que manda el
backend y `app.js` la parsea para sacar el `<id>`. Lo que ya no existe es el archivo.
`tools/split_signs.py` también se queda, porque `pack_signall.py` le importa
`write_catalog`.

## Qué quedó pendiente (cuarta parte)

- **Gesto de arrastre desde el borde en MIUI**: no se puede resolver desde la app. Si el
  owner quiere que el dedo caiga seguro fuera de la franja del sistema, hay que **mover la
  pestaña 12–24 dp hacia adentro** (decisión de diseño, no tomada). Alternativa: dejarla
  pegada al borde y que el arrastre se haga desde la zona táctil invisible, que ahora es
  más grande.
- **Acerca de + botón atrás del sistema cierra la app** en vez de volver a la escena (solo
  el botón "Volver" vuelve). Sin tocar: no estaba en el pedido.


