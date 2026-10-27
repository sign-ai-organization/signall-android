# SignAI — instrucciones para el agente

Tesis: español hablado → Lengua de Señas Colombiana (LSC) con un avatar 3D. Este repo
tiene **solo el código de la app**. El resto del sistema vive en repos hermanos, y el
contrato entre ellos son frames de WebSocket, no imports.

Los enfoques viejos (servidor Flask, Kotlin+Filament nativo, Unity) están **muertos**:
ignorá cualquier documento que los mencione.

**Este archivo son reglas que aplican a todo.** La referencia profunda está en otros
archivos y se carga **solo cuando la tarea lo necesita**: la lista está en
§ Índice: qué cargar y cuándo, al final. Si tocás un área, leé su archivo antes de
decidir.

---

## Cómo hablarle al owner

El owner es la persona autora de la tesis. Cuando le expliques algo —un hallazgo, una
decisión, un plan— tenés que poder hacerlo **en español neutro, como si se lo
explicaras a alguien que nunca vio el proyecto**: frases cortas, sin jerga sin
explicar, y el detalle técnico después de la idea, no antes.

- **Nada de voseo.** "Toca", no "tocá".
- **El término de la tesis es `representar`.** El avatar *representa* las señas: no
  "firma" y no "signa". Y si el texto habla de mandar una frase, es `enviar`, no
  `representar`. Desde el 2026-10-16 no aparece más `firmar` en este repositorio:
  la palabra se inventó en documentos internos y confundía más de lo que aclaraba.
  Los identificadores en inglés (`PlaybackState.Signing`, `signing`) **se quedan**:
  `signing` es la palabra correcta en inglés para lengua de señas.
- **Los números van con su origen.** Una cifra sin decir de dónde salió no se puede
  discutir, y una cifra de fixture no es una cifra de persona. Decí siempre
  "medido con 28 frases reproducidas por el socket", no "medido".
- **No inventes terminología.** Si una palabra no está en
  `../sign-ai-docs/documentos/requerimientos/`, es palabra mía hasta que el owner la
  avale. Preguntá antes de escribir una en un documento.
- **Una decisión, una pregunta.** Cuando algo sea decisión del owner, presentá las
  opciones con lo que se gana y lo que se pierde, y pará ahí. No advanced por las
  opciones difíciles: preguntá.
- **Separá lo medido de lo supuesto.** Si algo es una hipótesis, decí que lo es.

---

## Las tres fuentes de verdad

El owner decidió (2026-10-17) que hay **tres** fuentes que hay que revisar antes de
decidir o implementar, y que ninguna se resuelve por cuenta propia:

1. **Este repo** (el código).
2. **`../sign-ai-docs`** (requerimientos, diagramas del SDD, plan de pruebas).
3. **El Overleaf de la tesis** (SRS, Memoria, SDD, PMP, PPP).

El flujo que pidió el owner:

1. **Antes de decidir o de implementar**, leer qué dicen los documentos de las tres.
2. **Al implementar o al revisar**, comprobar que lo que hace el código está **alineado**
   con esos documentos.
3. **Si no están alineados, no seguir por cuenta propia.** Primero determinar cuál de los
   dos está mal —el **documento** o el **código**— y ahí preguntarle al owner.

Un detalle práctico: la revisión de Overleaf tiene un costo en tiempo, porque el MCP puede
abrir una ventana de Chromium cuando la cookie venció (~5 días). **Avisá que vas a leer, no
lo hagas por sorpresa** a mitad de una implementación.

De Overleaf se usa la fuente para **revisar**, y solo eso: está bloqueado a nivel de
configuración —ocho `policies` en `~/.config/opencode/opencode.json`—, así que las nueve
herramientas que quedan son todas de lectura. La regla completa, y por qué el bloqueo está
en `policies` y no en `permissions`, están al final de este archivo y en el skill
**`overleaf-lectura`**.

### No hay desempate entre las dos fuentes de documento

Cuando `../sign-ai-docs` y Overleaf se contradigan **entre ellos** —no cuando el código se
aparta de los dos— **no hay ganador automático**: se reporta la contradicción y se pregunta.
No se elige una fuente, no se promedian las dos y no se deja que ninguna gane por inercia.
Las dos versiones se citan **con su fecha**: un "el SRS dice X" sin fecha no se puede
discutir.

Cuando el documento que está mal es uno de Overleaf, **lo corrige el owner y no la
sesión**. Anotarlo en `../sign-ai-docs/modificaciones/requerimientos.md` o en el log de
sesión es lo que puede hacer una sesión; el `.tex` no.

---

## Mapa del sistema

Cualquier sesión que toque el pipeline tiene que mirar los hermanos antes de decidir.

| Repo | Qué es | Rama | Puerto |
|---|---|---|---|
| `../sign-ai-backend` | FastAPI: WS con el cliente, NLP de glosas, catálogo, caché semántico, Neon | `develop` | 8080 |
| `../sign-ai-stt` | faster-whisper + WebRTC VAD: audio → texto en tiempo real | `develop` | 8000 |
| `../sign-ai-docs` | Requerimientos, diagramas del SDD, plan de pruebas (nada corre) | `main` | — |
| `../signai-avatar` | Producción de las animaciones (Cascadeur): versiona los **GLB finales** de Juan y Pauline, 300 clips cada uno. La demo Flask de landmarks que también vive ahí está congelada | `dev` | — |

Reglas por repo:

- **Acá se commitea directo a `main`**, sin PR ni rama de feature. No pidas confirmación.
- **En los hermanos, nada de commits sin que el owner lo pida** (PR contra `develop`).
  Cada uno tiene su documentación: el backend tiene `CLAUDE.md` (leelo antes de
  tocarlo: código en inglés sin excepción, nombres de clases iguales a los del SDD, y
  nunca commitear), el STT tiene `README.md`.
- **Los `.drawio` y el `.md` de requerimientos los edita el owner**: los cambios grandes
  se proponen, no se aplican en silencio.
- **`../signai-avatar` es la fuente de los GLB de esta app.** En su rama `dev` versiona
  `models/Juan.glb` y `models/Pauline.glb` (300 clips cada uno; desde el 2026-10-26 con la
  cadera centrada). De ahí los lee `tools/pack_avatars.py`; no se copian ni se editan a
  mano, y sus commits no los hace esta sesión sin que el owner lo pida. La demo Flask de
  landmarks que también vive en ese repo está **congelada desde el 2026-09-19**: no se le
  copian scripts a esta app.

Detalle de cada hermano, Carpetas hermanas que no son repositorios, y las IDs de los
cinco proyectos de Overleaf: **[`docs/SISTEMA.md`](docs/SISTEMA.md)**.

## El contrato entre repos (lo que se rompe en silencio)

Un único WebSocket, de la app **solo al backend**: `:8080/ws/translate`. La app nunca
habla con el STT ni con Neon. Los cuatro invariantes que hay que no romper:

- **Solo se traduce lo que el cliente manda como `text`.** La voz entra por el mismo
  socket pero vuelve como `transcript`, y esa transcripción **no se traduce**. Si el
  backend hiciera eco de la frase escrita, la app lo tomaría por una frase de voz y la
  reenviaría: ese bucle existió y quemó el proceso (una frase repetida ~2.900 veces, 44 %
  de CPU).
- **`segment` es una oración, con `index` y `total` reales desde el primero.** Ya no
  existen `stream`, `provisional` ni `source_text`.
- **El `final` siempre llega**, incluso vacío: el STT responde a todo `end` (`flush()`).
  Un `final` sin texto es "no se escuchó nada", no un error.
- **Los mismos nombres de métrica en los dos lados** (`rt_*` en el backend,
  `SignAiMetrics` en el device): un número que no se instrumentó en las dos partes no se
  puede comparar con nada.

Para cambiar un frame o un nombre de métrica: cargá el skill **`contrato-websocket`**.

## Reglas para que las sesiones se entiendan entre repos

1. **Un contrato se lee en los dos lados antes de cambiarlo.** Frames del WS, nombres de
   glosas, columnas del catálogo, nombres de métricas: primero el repo hermano, después
   el cambio acá.
2. **Un cambio que cruza el contrato no está terminado hasta que el otro lado está
   actualizado**, y hay que decirlo explícitamente al owner: qué se cambió, en qué repos y
   qué falta del otro lado. Pendiente de otro lado es deuda visible, no un detalle.
3. **Cada sesión deja rastro donde corresponde**: `openspec/sessions/YYYY-MM-DD-<slug>.md`
   acá y en el repo hermano que se tocó. Cuando el dato venga de afuera, dejar **de qué
   repo y de qué commit** salió: sin eso la próxima sesión no puede reproducirlo.
4. **Referenciá los hermanos por ruta relativa** (`../sign-ai-backend`), nunca con rutas
   absolutas de esta máquina: las sesiones se corren en directorios distintos.
5. **Los números se miden donde están.** Latencias del device acá (`adb logcat -s
   SignAiMetrics`), del STT y del backend allá. Un p50 del avatar mezclado con un p50 del
   STT no es un p50 de nada.
6. **Cuando algo no se pueda resolver leyendo, preguntar.** Las decisiones abiertas están
   marcadas como tales en los `CLAUDE.md`/`docs/` de cada repo; no rellenarlas por inferencia.

---

## Build y verificación

```bash
./gradlew build          # verificar antes de commitear
./gradlew installDebug   # necesita device/emulator por adb
```

**Obligatorio después de cada cambio**: compilar y después correr `./gradlew installDebug`
en el device conectado, para que el owner pueda probar de inmediato. **Nunca cierres un
trabajo sin instalar.** Si no hay device, decilo explícitamente.

Trampas que hacen perder tiempo:

- **Device físico por USB**: el default `BACKEND_WS_URL` es `ws://10.0.2.2:8080/ws/translate`,
  que es el loopback del **emulador**. En un teléfono real usá
  `adb reverse tcp:8080 tcp:8080` y compilá con
  `-Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate`. Sin eso la app muestra "Sin
  conexión con el servidor" y parece un problema del backend.
- **La demo del cifrado usa el build `demo`, no `debug`.** `demo` hereda `release` (solo
  `wss://`), se firma con la clave de `debug` y trae su `network_security_config` con las CA
  del **usuario** como anclas; apunta a `wss://127.0.0.1:8443/ws/translate`. El túnel es
  `adb reverse tcp:8443 tcp:443`, porque el `443` no se puede mapear en el teléfono
  (`cannot bind listener: Permission denied`). El proxy y el certificado los levanta el
  backend (`../sign-ai-backend/deploy/`, `docs/plan-proxy-tls.md`); si falta la CA de
  laboratorio en el teléfono, el handshake falla con `Trust anchor for certification path
  not found` y eso **no** es un problema de la app. `demo` comparte `applicationId` con
  `debug`, así que lo reemplaza: después de la demo, `./gradlew installDebug`.
- **`RECORD_AUDIO` es permiso de runtime y no está pre-otorgado.** Tocar el mic sin él dice
  "No se pudo abrir el micrófono", que se lee como un fallo del STT y no lo es:
  `adb shell pm grant com.signai.webview android.permission.RECORD_AUDIO`.
- **El teclado se cierra sacándole el foco al campo, no solo escondiéndolo.** Con solo
  `hide()` se iba un segundo y volvía, porque el `TextField` seguía con el foco y la
  siguiente recomposición lo mostraba de nuevo. Regla general: **tocar un control cierra el
  teclado, no manda la frase** — el texto se queda en el campo.

No hay tests, no hay CI, no hay lint. Toolchain: AGP 8.9.3, Kotlin 2.3.21,
compileSdk/target 36, minSdk 26, JVM 17, Compose BOM 2024.12.01.

## Observabilidad

| Dónde | Tag / endpoint | Qué |
|---|---|---|
| STT | `:8000/metrics` | ms de inferencia por modelo, espera de GPU, lag de audio, drops por razón |
| Backend | `:8080/metrics` | `rt_first_sign_ms`, `rt_phrase_total_ms`, `rt_sentence_delay_ms`, `rt_phrases_total`, `rt_sentences_total` |
| Device | `adb logcat -s SignAiMetrics` | oraciones por frase, glosas, **duración de la representación**, p50/p95/max |
| Device | `adb logcat -s SignAiNet` | cada `segment` que mandó el backend, con `index`/`total` |
| Escena | `adb logcat -s SignAIWeb` | consola JS (necesita depurar prendido en Ajustes → AVANZADO) |

**Regla: agregar la métrica antes de agregar la feature.** Un número que no se
instrumentó no se puede afirmar que mejoró. Y al citar un número, decí de dónde salió.

`rt_first_sign_ms` es el que decide si se siente en tiempo real. **Ojo**: la línea base
publicada (913 ms p50 / 1336 ms p95) es **del camino por palabras**; con oraciones la
cifra sube por construcción y **no es comparable**. Falta remedir con las 28 frases.

## Git

Acá: commit y push directo a `main`, sin PR ni rama de feature. **No abrir PRs ni pedir
confirmación para commitear.** Los hermanos usan PR contra `develop` y no se commitean sin
que el owner lo pida.

**`develop` existe acá desde 2026-10-14**, creado como copia exacta de `main` (`4221303`),
para dejar los tres repos con la misma rama de integración. Por ahora **no cambia el
flujo**: este repo sigue por `main` directo sin PR. Nadie la mergea ni la borra sin que lo
pidas.

**El flujo por oraciones ya está en `main`** (2026-10-21). La rama `flujo-oraciones` fue el
nuevo `main`: un fast-forward (`587ed04` → `2d8c757`) con el merge de `origin/main` que ya
traía. Se trabaja sobre `main`. `develop` quedó como copia exacta de `main`. El punto de
retorno es `respaldo/main-2026-10-21`, que apunta al `main` viejo y está en el remoto.

La rama `tiempo-real` **ya no existe**: el trabajo de tiempo real de los tres repos entró
por fast-forward a `main` acá y a `develop` en los hermanos, y la rama se borró local y
remoto. Donde quede escrita en una bitácora vieja o en un documento es un dato histórico
de dónde se hizo el trabajo, no una rama a la que volver.

## UI

- **Lumo UI by Noman R** (https://www.lumoui.com/) para componentes nuevos — **NO** el
  Lumo Design System de Vaadin. Es Compose y shadcn-like: se adopta como base y después
  se personaliza en el Design System de SignAI. Consultá su documentación actual antes
  de tomar una decisión de Lumo; no asumas que funciona como una librería común.
- **Migrar Material 3 progressivement, nunca agregar Material 3 nuevo.** Hoy el repo tiene
  **cero** imports de `material3`.
- **Iconos**: Phosphor para cualquier cosa nueva; evitá `material-icons-extended`.
- **Tokens**: viven en `ui/designsystem/foundation/` (`SignAiSizes`, `SignAiShapes`,
  `SignAiSpacing`, `SignAiMotion`, `pressScale`). Usalos en vez de `.dp` sueltos.
- **Paleta**: fuente única en `ui/designsystem/Color.kt` (`// CUSTOMIZED`).
  `docs/manual-identidad-visual.md` es la referencia de marca y hay que aplicarlo.
  Reglas que muerden: **dark = grafito** y es el **default**; el **azul de marca
  `#1C4CFC` vive solo en la acción principal y los controles**; **`live`/`onLive` =
  ámbar `#F2B84B` es lo único que significa "escuchando ahora"** y nunca es la única señal
  (el control también cambia etiqueta o `stateDescription`). Sobre oscuro, los iconos y
  líneas finas usan `colors.text`, no `strong`: `strong` da 2,86:1 sobre `surface`, por
  debajo de 3:1.
- **Look**: minimalista, moderno, modo oscuro base, buen contraste. El avatar 3D es el
  protagonista visual. Transparencia, blur y animación solo si son sutiles, funcionales y
  moderados. **Nunca degradar el performance del WebView 3D.**
- **Teclas cada una vez y quedan prendidas; ninguna se mantiene apretada.** Es la regla
  de todo el control de la app.

## El puente Kotlin ↔ JS

El WebView carga por `WebViewAssetLoader` en
`https://appassets.androidplatform.net/assets/web/index.html`. JS→Kotlin va por
`window.SignAIBridge.postMessage("onPlaybackDone")` al terminar la cola,
`postMessage("avatarReady")` cuando el personaje ya está en escena (no en
`onPageFinished`, que dispara mientras baja el GLB de 13 MB), `progress:<0..100>` con el
avance real de la escena para la barra del splash y `signStart:<idx>:<jsMs>` por cada seña.

**`avatarReady` es "todo listo", no "el archivo llegó":** se manda después de indexar los
300 clips del avatar, resolver la seña de arranque, compilar los shaders
(`renderer.compileAsync`) y pintar un frame con el avatar adentro. El splash de Compose se
esconde con ese aviso, así que si se manda antes se ve el canvas vacío y la primera seña
paga el arranque del WebGL.
El progreso de la página (`WebChromeClient.onProgressChanged`) **no** sirve para la barra:
dice 100 % cuando terminó el HTML.

**El WebView no se crea en el primer frame.** `MainScreen` monta el `AvatarWebView` dos
`withFrameNanos` después, para que el splash ya esté en pantalla cuando el WebView se crea
y empieza a cargar: si se crea en la primera composición, ese frame se paga antes de que
exista el splash y el usuario ve la ventana vacía durante segundos (medido en el Redmi
Note 9S, 2026-10-22). No lo "simplifiques" creando el WebView directo.

**La fuente de verdad del contrato es `domain/SignAiCommands.kt`**: los nombres exactos de
cada comando están ahí, no en este archivo. Kotlin los llama con
`evaluateJavascript("window.SignAI?.<método>(...)")`.

- **Compatible hacia atrás, siempre**: mantené el guard `?.` y los nombres existentes. Un
  método nuevo es **aditivo**. Borrar o renombrar uno rompe en silencio.
- El listener es `WebViewCompat.addWebMessageListener` (con feature-guard); si la
  feature falta, `onPageFinished` tapa el splash y el timeout de 8 s de `SignAiSplash`
  cubre el resto.

## Gotchas

- Todo el estado de Compose vive en `ui/state/MainScreenState.kt`. No hay ViewModel, ni
  DI, ni navegación. Historial y ajustes en `SharedPreferences "signai"/"history"` detrás
  de `data/SignHistoryRepository.kt` y `data/SettingsRepository.kt`.
- **Los dos drawers son un solo componente** (`ui/designsystem/components/EdgeDrawer.kt`,
  `side = Start/End`) con tap + drag; Conversation es `Start`, Settings es `End`, y abrir
  uno cierra el otro. Solo uno abierto a la vez, validado dos veces: reactivamente por
  `otherDrawerOpen` y sincrónicamente por `claimDrawerGesture` (un `AtomicReference`, a
  propósito no estado de Compose), tomado en el primer evento de puntero, así que dos
  dedos no pueden abrir los dos paneles en el mismo frame.
- **El layering de los drawers es frágil — leé el comentario `ORO` al principio de
  `EdgeDrawer.kt` antes de tocarlo.** Tres reglas: (1) un `clickable`/`draggable` con
  `enabled = false` **igual** registra un nodo de entrada y queda como la ruta de toque
  más alta, así que un drawer inactivo de pantalla completa se traga los toques del
  activo — nunca enganches el gesto cuando puede no actuar; (2) el `AndroidView` del
  WebView es un View real y toma cada toque dentro de sus bounds, así que tiene que ser
  el hijo de abajo con los drawers compuestos arriba, y su `pointerInteropFilter` devuelve
  true mientras haya un drawer abierto; (3) el backdrop es un nodo de pantalla completa
  compuesto **antes** del panel y del tirador. Los dos drawers son hermanos (no anidados)
  y el inactivo no compone overlay interactivo alguno.
- **MIUI "smart dark" (force-dark) tiene que seguir apagado**
  (`android:forceDarkAllowed=false` en `res/values/themes.xml`). Con él prendido, MIUI
  invierte el contenido dibujado dentro de hardware layers: el texto dentro de
  `verticalScroll` y de los drawers se ve **blanco** mientras las cajas sólidas quedan
  bien (bug del 2026-09-23). No lo saques.
- En MIUI la apariencia de los iconos de la barra de estado no sigue al tema claro
  (`isAppearanceLightStatusBars` y el flag legacy se ignoran).
- **`windowSoftInputMode` va en `<activity>`, no en `<application>`.** Puesto en la
  aplicación el sistema lo ignora y la ventana queda en `adjustPan`: al abrir el teclado
  el sistema **panea** toda la ventana hacia arriba y encima el panel también reserva el
  inset del teclado, así que se suman los dos desplazamientos y queda un hueco de ~200 dp
  entre el teclado y los botones (bug del 2026-10-22, arreglado con `adjustResize` en la
  activity). Y en el panel, el inset de abajo es **uno solo**: `safeDrawing` bottom (el
  mayor entre barra de navegación y teclado), nunca `navigationBarsPadding().imePadding()`
  sumados — el teclado ya se dibuja encima de la barra de navegación.
- **Con navegación por gestos, ~20 dp de cada borde son del sistema, y MIUI ignora la
  exclusión.** `Modifier.systemGestureExclusion()` (en la pestaña) y
  `View.setSystemGestureExclusionRects` (en `MainActivity`) están puestos, pero medido en
  el Redmi Note 9S: un arrastre que arranca en x=2/26/45 px se lo queda el gesto de
  "atrás" y la app va al launcher; desde x=60 px sí llega y abre el panel. Por eso las
  zonas táctiles de la pestaña son más grandes que el dibujo. Si alguien "limpia" la
  exclusión, en un Pixel el arrastre desde el borde se pierde.
- El blur de la sidebar usa `RenderEffect` (solo Android 12+, con guard) y solo mientras
  hay un drawer abierto.
- **El avatar 3D es invisible para toda la instrumentación de accesibilidad**: sale como
  `android.webkit.WebView` sin ningún hijo, porque el canvas de Three.js no publica nada.
  No se puede tocar ni leer con `mobile-mcp`, Maestro ni `uiautomator`. Para el estado de
  la escena: el tag `SignAIWeb` de logcat, o el modo debug de la app.
- **Los cues del pipeline (`micMode`, cola, estados de turno) están documentados en
  [`docs/MODO-EN-VIVO.md`](docs/MODO-EN-VIVO.md).** Si la tarea toca el micrófono, el
  WebSocket, la cola o la latencia, leelo antes.

## Assets web

- `index.html` carga Three.js r169 de jsDelivr por importmap → **necesita permiso
  INTERNET y conectividad en runtime**, aunque el avatar y los datos sean locales.
- **Nunca pongas la normalización (escala/offset) en la raíz de la escena cargada.** Las
  exportaciones de Cascadeur animan la raíz del armature en posición, rotación *y* escala,
  así que el mixer la sobrescribe cada frame. Va en un objeto pivote padre.
- **Dos `.glb` completos, uno por avatar.** `models/juan.glb` (34,28 MB) y
  `models/pauline.glb` (29,46 MB) traen el personaje, la cara y las **50 señas como
  clips** `0000`..`0049`, cada una con sus variantes faciales (`_alegria`, `_tristeza`,
  `_sorpresa`, `_pregunta`, `_negacion`): **300 clips por archivo**. La escena indexa
  solo el avatar elegido (`embeddedClipsFor` en `app.js`) y resuelve `signs/<id>.glb`
  **por nombre, sin pedir nada por red**. Hoy reproduce únicamente el clip base de cada
  seña: los marcadores faciales están fuera de alcance hasta que se apruebe el mapeo.
- **`models/avatares.json`** es el inventario reproducible: hash y tamaño de cada fuente
  original, del archivo empaquetado, clips, articulaciones, variantes y la advertencia de
  pose de bind de Pauline. Los originales `Juan.glb` y `Pauline.glb` viven versionados en
  `../signai-avatar/models/` (rama `dev`) y no se editan. `../material-3d/Avatares/`
  conserva la versión anterior (`*_final.glb`, 2026-10-24), que ya **no** es la fuente.
- **`assets/web/signs/` ya no existe** (borrado el 2026-10-22: 32 `.glb`, 5,7 MB en el
  APK). Era el layout viejo y nadie los pedía porque toda glosa del catálogo resuelve su
  clip adentro del archivo único. La ruta `signs/<id>.glb` **sigue siendo el
  identificador** que manda el backend y `app.js` la sigue parseando para sacar el `<id>`;
  lo que ya no hay es un archivo detrás. No la "limpies" del código: es el contrato.
- **Nunca edites `models/juan.glb` ni `models/pauline.glb` a mano**: se regeneran con
  `tools/pack_avatars.py` desde `../signai-avatar/models/Juan.glb` y
  `.../Pauline.glb`, que también reescriben `models/avatares.json`, `manifest.json` y
  `domain/SignsCatalog.kt`. El script hornea la pose de descanso y poda los morph targets
  que ninguna de las 300 variantes mueve. `tools/pack_signall.py` es el pipeline viejo de
  un solo avatar: no procesa estos dos archivos.
- **Arranque medido el 2026-10-26 en el Redmi Note 9S**: Juan, **1,81–3,42 s** según
  caché de página (3,42 s en frío, 1,95–2,50 s en caliente); Pauline, **1,95 s**. Un
  cambio de avatar en caliente tarda **1,90–2,57 s** (parseo 1,18–1,95 s) y se mide en
  `SignAiState` (`cambio de avatar`, `avatar listo`) y `SignAIWeb`. El splash espera a
  `avatarReady`, así que eso es lo que se ve antes de la escena.
- `renderer.setPixelRatio` está **capado en 1.75** a propósito. Sin cap, el Redmi Note 9S
  renderiza 19,6 MP/frame y el GPU queda en ~37 ms/frame, con 99 % de jank.
- `shouldInterceptRequest` fuerza `Cache-Control: no-store` para que los cambios de
  `app.js` se vean al recargar. No lo saques.
- **No fuerces `DoubleSide` en las mallas**: es overdraw puro en mallas cerradas y costó
  4 % más de jank para un cambio visual cero.

## Neon

**Org: `org-orange-surf-39007829` (`sign-ai`).** Es el proyecto de la tesis. La cuenta
tiene una segunda organización, `org-steep-paper-48927149` (`Diego`), que es personal —
**no tocar**. Por eso el MCP de Neon **exige pasar `org_id`**.

Credenciales en `.envJulian` (gitignored). Si `psql` dice `parse error near '&'` no es el
archivo: es que la línea tiene `&` sin comillas.

La app **no habla con Neon**: la base es del backend. `catalog` está sembrada con las 50
glosas de LSC50 y reejecutar el seed es idempotente. **Las 50 tienen animación desde el
2026-10-22**: el archivo único trae las 18 que faltaban (`0032`–`0049`), así que `GLS-10`
("anunciada pero no exportada") ya no alcanza a ninguna glosa. **No las pidas por HTTP**:
salen de los clips que vienen dentro del avatar. El backend carga el catálogo **una sola
vez al arrancar**: después de un seed hay que reiniciarlo.

## Contexto del proyecto (OpenSpec)

`openspec/` es el contexto vivo del proyecto — mantenelo actualizado con cada cambio que no
sea trivial:

- **`specs/`**: fuente de verdad. `specs/ui/spec.md` es el **catálogo completo de features**
  de la app (todo viene del prototipo `PrototipoFrontend/`); `avatar`/`dataset`/`pipeline`
  cubren el comportamiento del rig y la animación. Actualizá el spec correspondiente cuando
  arreglás ese comportamiento.
- **`sessions/`**: bitácora de trabajo, un archivo por sesión. Convención y plantilla en el
  skill **`sesion-openspec`**.
- **Si un spec o `openspec/config.yaml` contradicen al código, gana el código** y la
  diferencia se reporta; no se elige por cuenta propia. El pipeline de landmarks con
  `sign.json` ya no está vigente: donde aparezca, es historia. `specs/pipeline/spec.md` y
  `openspec/config.yaml` se auditaron contra el código el 2026-10-24.

---

## Índice: qué cargar y cuándo

Este archivo se lee entero en cada mensaje. **Lo de abajo no**: se carga cuando la tarea lo
pide. La regla es leer la fila antes de decidir, no después.

| Si la tarea toca… | Cargá |
|---|---|
| **Un frame del WebSocket, un nombre de métrica, el catálogo, o cualquier cosa que cruce a otro repo** | skill **`contrato-websocket`** — el contrato se lee en los dos lados antes de cambiarse, y el otro lado tiene que quedar actualizado o la deuda se dice en voz alta |
| **Si el código y los documentos no coinciden, o hay que decidir si manda el código o el documento** | skill **`tres-fuentes-de-verdad`** — las tres fuentes, el flujo de revisión y qué hacer cuando no alinean |
| **Consultar el Overleaf de la tesis** | skill **`overleaf-lectura`** — cómo leer, la cookie, y qué hacer cuando un documento está mal |
| **El hermano, el STT, el catálogo, o una decisión de tesis** | [`docs/SISTEMA.md`](docs/SISTEMA.md) — mapa, `CLAUDE.md` del backend, protocolo del STT, IDs de Overleaf |
| **Saber qué se decidió ya entre el código y los documentos, o qué hay que cambiar en la app** | **`ls docs/CONTRASTE-*.md`**. Los contrastes son documentos de trabajo de una sesión: se crean, se actualizan y a veces se borran, así que no hay una lista fija — se buscan por nombre. Cada repo tiene el suyo en su `docs/` (el del backend está en `../sign-ai-backend/docs/`) |
| **El micrófono, el modo en vivo, la cola de la escena o la latencia** | [`docs/MODO-EN-VIVO.md`](docs/MODO-EN-VIVO.md) + `tools/TIEMPO-REAL.md` |
| **Probar o verificar en el device, o "va lento"** | skill **`controlar-app-movil`** — qué herramienta usar, y los errores ya pisados |
| **Compose, Lumo, accesibilidad o performance** | skill **`compose-best-practices`** (Lumo/Phosphor, cero Material 3) + `ui/designsystem/Color.kt` |
| **Tocar el device, medir, o elegir herramienta del `mobile-mcp`** | [`tools/HERRAMIENTAS.md`](tools/HERRAMIENTAS.md) — índice en [`tools/README.md`](tools/README.md) |
| **Colores, marca, contraste** | [`docs/manual-identidad-visual.md`](docs/manual-identidad-visual.md) (el owner) y `ui/designsystem/Color.kt` (los valores) |
| **Un cambio no trivial, o cerrar una sesión** | skill **`sesion-openspec`** — la bitácora y su plantilla |

### Agentes disponibles

`@pipeline` para cambios que cruzan el contrato · `@ui` para cambios de interfaz en
Compose · `@contraste` para comparar código contra documentos (solo lectura, no escribe).
Se invocan con `@nombre`.

---

# El Overleaf es de solo lectura

De Overleaf **se lee libremente**, sin pedir permiso: `ping`, `list_projects`,
`open_project`, `list_files`, `read_file`, `read_log`, `list_comments`,
`read_comment_thread`, `list_tracked_changes`. Las nueve de lectura.

**No se escribe.** Ni un `.tex`, ni un comentario, ni una sugerencia, ni un cambio
aceptado o rechazado, ni `compile`. Solo si el owner lo pide explícitamente en un mensaje, y
no cuenta como que lo pidió que la tarea lo requiera, que sea obviamente correcto, que sea
"una tilde", o que se haya hecho antes. Si queda pendiente corregir algo en Overleaf, **se lo
decís al owner y lo hace él.**

Editar `~/.config/opencode/opencode.json` requiere su confirmación explícita en un mensaje.
Para tocar un `deny` de Overleaf hace falta que el mensaje **hable de Overleaf**: un "arreglá
la config" no alcanza. Sin confirmación, se propone el cambio exacto y se espera.

Lo único que hay que avisar antes de leer: el MCP **abre una ventana de Chromium con perfil
aislado cuando la cookie venció** (duran ~5 días), y hay que avisar porque rompe el flujo si
aparece a mitad de una implementación.

Leer no necesita más explicación. Cómo está puesto el bloqueo y qué hacer cuando un
documento de Overleaf está mal, en el skill **`overleaf-lectura`**.
