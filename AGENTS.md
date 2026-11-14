# SignAll — app Android

Aplicación Android del proyecto **SignAll**: español hablado → Lengua de Señas Colombiana
(LSC) representada por un avatar 3D. Es la última pieza de la cadena: captura el audio y las
frases escritas, las envía al backend y representa las señas que el backend devuelve.

Trabajo de grado de Ingeniería de Sistemas, Pontificia Universidad Javeriana (Colombia), con
un corpus fijo de 50 señas (LSC50) para el caso de uso "Registro Básico de Datos".

Este archivo son las reglas que aplican a todo, para una persona o para un agente. La
referencia profunda vive en otros archivos y se carga **solo cuando la tarea lo necesita**: la
lista está en § Índice: qué cargar y cuándo, al final. Si se toca un área, se lee su archivo
antes de decidir.

Los enfoques viejos (servidor Flask, Kotlin+Filament nativo, Unity) están **muertos**: donde
aparezcan, es historia.

---

## El sistema: cuatro repositorios y el avatar

Este repositorio es **una pieza**. El sistema completo, con todo lo que hace falta para
entenderlo:

| Repositorio | Qué es | Puerto |
|---|---|---|
| [signall-android](https://github.com/sign-ai-organization/signall-android) | esta app: captura el micrófono, envía por WebSocket y representa las señas | — |
| [signall-backend](https://github.com/sign-ai-organization/signall-backend) | FastAPI: WebSocket con la app, NLP de glosas, catálogo, caché semántico y Neon | 8080 |
| [signall-stt](https://github.com/sign-ai-organization/signall-stt) | faster-whisper + WebRTC VAD: audio → texto en tiempo real | 8000 |
| [signall-docs](https://github.com/sign-ai-organization/signall-docs) | los documentos de la tesis: SRS, SDD, memoria, plan de pruebas y plan de administración | — |
| [signall-avatar](https://github.com/sign-ai-organization/signall-avatar) (rama `dev`) | producción de las animaciones con Cascadeur: los GLB finales de Juan y Pauline, 200 clips cada uno | — |

**Los repos hermanos son fuentes de información, no cajas negras.** Antes de cambiar un frame
del WebSocket, una glosa del catálogo o un nombre de métrica hay que leer el otro lado: el
contrato entre ellos son frames, y ningún compilador avisa cuando se rompe. Un cambio que
cruza el contrato no está terminado hasta que el otro lado lo refleja; si queda pendiente, se
dice en voz alta.

`signall-docs` es además el repositorio de documentación de la tesis y **el que manda para lo
escrito**: los documentos formales son suyos y no hay copia en otro lugar. Los otros cuatro se
mantienen actualizados entre sí, y cuando el código y un documento no coinciden no se elige
por cuenta propia (ver § Las dos fuentes de verdad).

Los comandos de este archivo suponen los clones uno al lado del otro (`../signall-backend`),
que es como se trabaja normalmente en la máquina de la tesis. Si están en otro lugar, los
enlaces de arriba dicen qué es cada repositorio; nada acá depende de una ruta absoluta.

---

## Cómo se escribe y cómo se le habla al owner

El owner es la persona autora de la tesis. Cuando se le explica algo —un hallazgo, una
decisión, un plan— hay que poder hacerlo **en español neutro, como si se lo explicara a
alguien que nunca vio el proyecto**: frases cortas, sin jerga sin explicar, y el detalle
técnico después de la idea, no antes.

- **Nada de voseo.** "Toca", no "tocá".
- **El término de la tesis es `representar`.** El avatar *representa* las señas: no "firma" y
  no "signa". Y si el texto habla de mandar una frase, es `enviar`, no `representar`. Desde el
  2026-10-16 no aparece más `firmar` en este repositorio: la palabra se inventó en documentos
  internos y confundía más de lo que aclaraba. Los identificadores en inglés
  (`PlaybackState.Signing`, `signing`) **se quedan**: `signing` es la palabra correcta en
  inglés para lengua de señas.
- **Los números van con su origen.** Una cifra sin decir de dónde salió no se puede discutir, y
  una cifra de fixture no es una cifra de persona. Se dice "medido con 28 frases reproducidas
  por el socket", no "medido".
- **No se inventa terminología.** Si una palabra no está en `signall-docs/latex/` ni en
  `signall-docs/borradores/`, es palabra del owner hasta que la avale. Se pregunta antes de
  escribirla en un documento.
- **Una decisión, una pregunta.** Cuando algo sea decisión del owner, se presentan las opciones
  con lo que se gana y lo que se pierde, y se para ahí. No se avanza por las opciones difíciles:
  se pregunta.
- **Se separa lo medido de lo supuesto.** Si algo es una hipótesis, se dice que lo es.

---

## Cada cambio se documenta en `signall-docs`

> **Cada cambio no trivial se documenta en `signall-docs`**: en `cambios/` (qué cambió y por
> qué) y, si toca un documento formal, se actualiza el `.tex` y se compila con `make`. Una
> sesión que termina sin dejar rastro ahí no está terminada.

Es un **estándar**, no una sugerencia. Aplica igual a los cuatro repositorios y a esta app.

- En `signall-docs/cambios/` va la entrada: qué se cambió, por qué, y qué quedó pendiente.
- Si el cambio toca un documento formal (`latex/srs`, `latex/sdd`, `latex/memoria`,
  `latex/plan-pruebas`, `latex/administracion`, `latex/vfp`), se edita el `.tex` y se compila
  con `make <documento>`: un documento que no compila no es un cambio terminado.
- En este repositorio el rastro de trabajo es además
  `openspec/sessions/YYYY-MM-DD-<slug>.md`, con la plantilla del skill `sesion-openspec`.
- Cuando el dato venga de otro repositorio, se deja **de qué repo y de qué commit** salió: sin
  eso la próxima sesión no puede reproducirlo.
- Las evidencias de las pruebas también viven en `signall-docs/evidencias/`: se agregan, no se
  corrigen.

---

## Las dos fuentes de verdad

El owner decidió (2026-10-17) que ninguna fuente se resuelve por cuenta propia. Hasta el
2026-11-04 eran tres, con el Overleaf de la tesis como tercera. Ese día **Overleaf se retiró**
("ya no es una fuente confiable"): los documentos formales pasaron a vivir en el repositorio y
ahora son **dos**:

1. **Este repositorio** — el código.
2. **[signall-docs](https://github.com/sign-ai-organization/signall-docs)** — los documentos:
   los formales en `latex/` (SRS, SDD, memoria, plan de pruebas, plan de administración) y los
   borradores en `borradores/`, con los cambios en `cambios/` y las evidencias en
   `evidencias/`.

El flujo que pidió el owner:

1. **Antes de decidir o de implementar**, leer qué dicen los documentos.
2. **Al implementar o al revisar**, comprobar que lo que hace el código está **alineado** con
   esos documentos.
3. **Si no están alineados, no seguir por cuenta propia.** Primero determinar cuál de los dos
   está mal —el **documento** o el **código**— y ahí preguntarle al owner.

Un detalle práctico: los documentos son `.tex` que se compilan con `make` desde el clon de
`signall-docs`. **Un cambio en un documento no está terminado hasta que compila**
(`cd ../signall-docs && make srs`), y lo que se tocó se ve en `git diff`: desde que Overleaf se
retiró, el control de cambios es el historial de git, no una herramienta aparte.

### No hay desempate entre dos documentos

Cuando dos documentos se contradigan **entre ellos** —por ejemplo el SRS contra los
requerimientos consolidados, o el plan de pruebas contra el SRS— y no cuando el código se
aparta de los dos, **no hay ganador automático**: se reporta la contradicción y se pregunta. No
se elige una fuente, no se promedian las dos y no se deja que ninguna gane por inercia. Las dos
versiones se citan **con su fecha**: un "el SRS dice X" sin fecha no se puede discutir.

**Cuál de los dos está mal lo decide el owner.** La sesión puede proponer el texto y dejarlo en
el `.tex`, pero una divergencia de contenido no se resuelve escribiendo por cuenta propia: se
anota en `signall-docs/cambios/` o en el log de sesión y se pregunta.

---

## El contrato entre repos (lo que se rompe en silencio)

Un único WebSocket, de la app **solo al backend**: `:8080/ws/translate`. La app nunca habla con
el STT ni con Neon. Los cuatro invariantes que no se rompen:

- **Solo se traduce lo que el cliente manda como `text`.** La voz entra por el mismo socket
  pero vuelve como `transcript`, y esa transcripción **no se traduce**. Si el backend hiciera
  eco de la frase escrita, la app lo tomaría por una frase de voz y la reenviaría: ese bucle
  existió y quemó el proceso (una frase repetida ~2.900 veces, 44 % de CPU).
- **`segment` es una oración, con `index` y `total` reales desde el primero.** Ya no existen
  `stream`, `provisional` ni `source_text`.
- **El `final` siempre llega**, incluso vacío: el STT responde a todo `end` (`flush()`). Un
  `final` sin texto es "no se escuchó nada", no un error.
- **Los mismos nombres de métrica en los dos lados** (`rt_*` en el backend, `SignAllMetrics` en
  el dispositivo): un número que no se instrumentó en las dos partes no se puede comparar con
  nada.

Para cambiar un frame o un nombre de métrica: se carga el skill **`contrato-websocket`**.

## Reglas para que las sesiones se entiendan entre repos

1. **Un contrato se lee en los dos lados antes de cambiarlo.** Frames del WebSocket, nombres de
   glosas, columnas del catálogo, nombres de métricas: primero el repositorio hermano, después
   el cambio acá.
2. **Un cambio que cruza el contrato no está terminado hasta que el otro lado está
   actualizado**, y hay que decirlo explícitamente al owner: qué se cambió, en qué repositorios
   y qué falta del otro lado. Pendiente del otro lado es deuda visible, no un detalle.
3. **Cada sesión deja rastro donde corresponde**: `openspec/sessions/YYYY-MM-DD-<slug>.md` acá y
   la entrada en `signall-docs/cambios/`.
4. **Los repositorios se nombran por nombre y URL** (`signall-backend`), nunca por ruta absoluta
   de una máquina: las sesiones se corren en directorios distintos.
5. **Los números se miden donde están.** Latencias del dispositivo acá (`adb logcat -s
   SignAllMetrics`), del STT y del backend allá. Un p50 del avatar mezclado con un p50 del STT
   no es un p50 de nada.
6. **Cuando algo no se pueda resolver leyendo, preguntar.** Las decisiones abiertas están
   marcadas como tales en los `CLAUDE.md`/`docs/` de cada repositorio; no se rellenan por
   inferencia.

---

## Build y verificación

```bash
./gradlew build          # verificar antes de commitear
./gradlew ktlintCheck    # solo el análisis estático de Kotlin
./gradlew installDebug   # necesita device/emulador por adb
```

**Obligatorio después de cada cambio**: compilar y después correr `./gradlew installDebug` en
el dispositivo conectado, para que el owner pueda probar de inmediato. **Nunca se cierra un
trabajo sin instalar.** Si no hay dispositivo, se dice explícitamente.

Trampas que hacen perder tiempo:

- **Dispositivo físico por USB**: el `BACKEND_WS_URL` por defecto es
  `ws://10.0.2.2:8080/ws/translate`, que es el loopback del **emulador**. En un teléfono real
  hay que usar `adb reverse tcp:8080 tcp:8080` y compilar con
  `-Psignall.backendWsUrl=ws://127.0.0.1:8080/ws/translate`. Sin eso la app muestra "Sin
  conexión con el servidor" y parece un problema del backend.
- **La demo del cifrado usa el build `demo`, no `debug`.** `demo` hereda `release` (solo
  `wss://`), se firma con la clave de `debug` y trae su `network_security_config` con las CA
  del **usuario** como anclas; apunta a `wss://127.0.0.1:8443/ws/translate`. El túnel es
  `adb reverse tcp:8443 tcp:443`, porque el `443` no se puede mapear en el teléfono
  (`cannot bind listener: Permission denied`). El proxy y el certificado los levanta el
  backend: el sistema completo se arranca con
  `docker compose -f docker-compose.yml -f docker-compose.host.yml up -d` desde el clon del
  backend, y el detalle está en su `deploy/README.md`. Si falta la CA de laboratorio en el
  teléfono, el handshake falla con `Trust anchor for certification path not found` y eso **no**
  es un problema de la app. `demo` comparte `applicationId` con `debug`, así que lo reemplaza:
  después de la demo, `./gradlew installDebug`.
- **La consola de la escena y el bloque AVANZADO no son un ajuste del teléfono.** No hay
  interruptor en Ajustes: existen solo si la compilación los pide, con
  `./gradlew installDebug -Psignall.devTools=true` o
  `SIGNALL_DEV_TOOLS=1 ./gradlew installDebug`. Están **apagados por defecto, incluso en
  `debug`**, y `release`/`demo` los ignoran siempre. Si no aparecen, no es un bug: es el build.
  **La grilla del suelo también arranca apagada** (`Settings.grid = false`, 2026-11-05) y sin
  `DEV_TOOLS` no se dibuja aunque una instalación vieja tenga la preferencia en `true`.
- **`RECORD_AUDIO` es permiso de runtime y no está pre-otorgado.** Tocar el micrófono sin él
  dice "No se pudo abrir el micrófono", que se lee como un fallo del STT y no lo es:
  `adb shell pm grant com.signall.app android.permission.RECORD_AUDIO`.
- **El teclado se cierra sacándole el foco al campo, no solo escondiéndolo.** Con solo `hide()`
  se iba un segundo y volvía, porque el `TextField` seguía con el foco y la siguiente
  recomposición lo mostraba de nuevo. Regla general: **tocar un control cierra el teclado, no
  manda la frase** — el texto se queda en el campo.

No hay tests, no hay CI. **ktlint se agregó el 2026-11-03** (`org.jlleitschuh.gradle.ktlint`
14.2.0): `./gradlew ktlintCheck` corre en `build` y falla con los avisos nuevos. La deuda de
estilo anterior está congelada en `app/config/ktlint/baseline.xml` (ninguna violación era un
error real, solo reglas de formato); un archivo nuevo que entre con avisos sí falla. Toolchain:
AGP 8.9.3, Kotlin 2.3.21, compileSdk/target 36, minSdk 26, JVM 17, Compose BOM 2024.12.01.

## Observabilidad

| Dónde | Tag / endpoint | Qué |
|---|---|---|
| STT | `:8000/metrics` | ms de inferencia por modelo, espera de GPU, lag de audio, drops por razón |
| Backend | `:8080/metrics` | `rt_first_sign_ms`, `rt_phrase_total_ms`, `rt_sentence_delay_ms`, `rt_phrases_total`, `rt_sentences_total` |
| Dispositivo | `adb logcat -s SignAllMetrics` | oraciones por frase, glosas, **duración de la representación**, p50/p95/max |
| Dispositivo | `adb logcat -s SignAllNet` | cada `segment` que mandó el backend, con `index`/`total` |
| Escena | `adb logcat -s SignAllWeb` | consola JS (solo en el build con `DEV_TOOLS`) |

**Regla: agregar la métrica antes de agregar la feature.** Un número que no se instrumentó no
se puede afirmar que mejoró. Y al citar un número, se dice de dónde salió.

`rt_first_sign_ms` es el que decide si se siente en tiempo real. **Ojo**: la línea base
publicada (913 ms p50 / 1336 ms p95) es **del camino por palabras** y **no es comparable**. Lo
remedido con el flujo por oraciones y la pila de producción (2026-11-13, 28 frases, dos pasadas
en frío, `/metrics` del backend) es **p50 632–675 ms / p95 890–1015 ms** con el LLM en frío y
**p50 0,5 ms** con la caché caliente. El número que manda para la experiencia es otro:
**audio→primera seña, p50 4366 ms**, de los cuales el STT se lleva 3724 ms. El detalle y cómo
repetirlo, en [`tools/TIEMPO-REAL.md`](tools/TIEMPO-REAL.md).

## Git

Acá: commit y push directo a `main`, sin PR ni rama de feature. **No se abren PRs ni se pide
confirmación para commitear.** Los hermanos tampoco se commitean sin que el owner lo pida.

**Todo el trabajo va a `main` en los cuatro repositorios.** La rama `develop` se abandonó el
2026-11-11: **no existe más, no se usa y no se vuelve a crear**. Las ramas remotas
`origin/develop` quedaron atrás y no se borraron: son historia, y borrarlas en el remoto es
cosa del owner.

`signall-avatar` queda aparte: su rama de trabajo es `dev` y ahí **no se toca nada** por
decisión del owner.

La historia de ramas y de la reescritura del historial del 2026-11-14, con el mapeo de los
hashes viejos, está en [`docs/historia/ramas-y-historial-2026-11.md`](docs/historia/ramas-y-historial-2026-11.md).
No hace falta para trabajar: se consulta solo cuando un documento viejo cita un commit que ya
no existe con ese hash.

## UI

- **Lumo UI by Noman R** (https://www.lumoui.com/) para componentes nuevos — **NO** el Lumo
  Design System de Vaadin. Es Compose y shadcn-like: se adopta como base y después se
  personaliza en el Design System de SignAll. Hay que consultar su documentación actual antes
  de tomar una decisión de Lumo; no se asume que funciona como una librería común.
- **Migrar Material 3 progresivamente, nunca agregar Material 3 nuevo.** Hoy el repositorio
  tiene **cero** imports de `material3`.
- **Iconos**: Phosphor para cualquier cosa nueva; `material-icons-extended` se evita.
- **Tokens**: viven en `ui/designsystem/foundation/` (`SignAllSizes`, `SignAllShapes`,
  `SignAllSpacing`, `SignAllMotion`, `pressScale`). Se usan en vez de `.dp` sueltos.
- **Paleta**: fuente única en `ui/designsystem/Color.kt` (`// CUSTOMIZED`).
  `docs/manual-identidad-visual.md` es la referencia de marca y hay que aplicarla. Reglas que
  muerden: **dark = grafito** y es el **default**; el **azul de marca `#1C4CFC` vive solo en la
  acción principal y los controles**; **`live`/`onLive` = ámbar `#F2B84B` es lo único que
  significa "escuchando ahora"** y nunca es la única señal (el control también cambia etiqueta
  o `stateDescription`). Sobre oscuro, los iconos y las líneas finas usan `colors.text`, no
  `strong`: `strong` da 2,86:1 sobre `surface`, por debajo de 3:1.
- **Look**: minimalista, moderno, modo oscuro base, buen contraste. El avatar 3D es el
  protagonista visual. Transparencia, blur y animación solo si son sutiles, funcionales y
  moderados. **Nunca degradar el rendimiento del WebView 3D.**
- **Teclas cada una vez y quedan prendidas; ninguna se mantiene apretada.** Es la regla de todo
  el control de la app.

## El puente Kotlin ↔ JS

El WebView carga por `WebViewAssetLoader` en
`https://appassets.androidplatform.net/assets/web/index.html`. JS→Kotlin va por
`window.SignAllBridge.postMessage("onPlaybackDone")` al terminar la cola,
`postMessage("avatarReady")` cuando el personaje ya está en escena (no en `onPageFinished`, que
dispara mientras baja el GLB de 13 MB), `progress:<0..100>` con el avance real de la escena para
la barra del splash y `signStart:<idx>:<jsMs>` por cada seña.

**`avatarReady` es "todo listo", no "el archivo llegó":** se manda después de indexar los 200
clips del avatar, resolver la seña de arranque, compilar los shaders (`renderer.compileAsync`) y
pintar un frame con el avatar adentro. El splash de Compose se esconde con ese aviso, así que si
se manda antes se ve el canvas vacío y la primera seña paga el arranque del WebGL. El progreso
de la página (`WebChromeClient.onProgressChanged`) **no** sirve para la barra: dice 100 % cuando
terminó el HTML.

**El WebView no se crea en el primer frame.** `MainScreen` monta el `AvatarWebView` dos
`withFrameNanos` después, para que el splash ya esté en pantalla cuando el WebView se crea y
empieza a cargar: si se crea en la primera composición, ese frame se paga antes de que exista el
splash y el usuario ve la ventana vacía durante segundos (medido en el Redmi Note 9S,
2026-10-22). No se "simplifica" creando el WebView directo.

**La fuente de verdad del contrato es `domain/SignAllCommands.kt`**: los nombres exactos de cada
comando están ahí, no en este archivo. Kotlin los llama con
`evaluateJavascript("window.SignAll?.<método>(...)")`.

- **Compatible hacia atrás, siempre**: se mantiene el guard `?.` y los nombres existentes. Un
  método nuevo es **aditivo**. Borrar o renombrar uno rompe en silencio.
- El listener es `WebViewCompat.addWebMessageListener` (con feature-guard); si la feature falta,
  `onPageFinished` tapa el splash y el timeout de 8 s de `SignAllSplash` cubre el resto.

## Gotchas

- Todo el estado de Compose vive en `ui/state/MainScreenState.kt`. No hay ViewModel, ni DI, ni
  navegación. Historial y ajustes en `SharedPreferences "signall"/"history"` detrás de
  `data/SignHistoryRepository.kt` y `data/SettingsRepository.kt`.
- **Los dos drawers son un solo componente** (`ui/designsystem/components/EdgeDrawer.kt`,
  `side = Start/End`) con tap + drag; Conversation es `Start`, Settings es `End`, y abrir uno
  cierra el otro. Solo uno abierto a la vez, validado dos veces: reactivamente por
  `otherDrawerOpen` y sincrónicamente por `claimDrawerGesture` (un `AtomicReference`, a
  propósito no estado de Compose), tomado en el primer evento de puntero, así que dos dedos no
  pueden abrir los dos paneles en el mismo frame.
- **El layering de los drawers es frágil — se lee el comentario `ORO` al principio de
  `EdgeDrawer.kt` antes de tocarlo.** Tres reglas: (1) un `clickable`/`draggable` con
  `enabled = false` **igual** registra un nodo de entrada y queda como la ruta de toque más
  alta, así que un drawer inactivo de pantalla completa se traga los toques del activo — nunca
  se engancha el gesto cuando puede no actuar; (2) el `AndroidView` del WebView es un View real
  y toma cada toque dentro de sus bounds, así que tiene que ser el hijo de abajo con los
  drawers compuestos arriba, y su `pointerInteropFilter` devuelve true mientras haya un drawer
  abierto; (3) el backdrop es un nodo de pantalla completa compuesto **antes** del panel y del
  tirador. Los dos drawers son hermanos (no anidados) y el inactivo no compone overlay
  interactivo alguno.
- **MIUI "smart dark" (force-dark) tiene que seguir apagado**
  (`android:forceDarkAllowed=false` en `res/values/themes.xml`). Con él prendido, MIUI invierte
  el contenido dibujado dentro de hardware layers: el texto dentro de `verticalScroll` y de los
  drawers se ve **blanco** mientras las cajas sólidas quedan bien (bug del 2026-09-23).
- En MIUI la apariencia de los iconos de la barra de estado no sigue al tema claro
  (`isAppearanceLightStatusBars` y el flag legacy se ignoran).
- **`windowSoftInputMode` va en `<activity>`, no en `<application>`.** Puesto en la aplicación
  el sistema lo ignora y la ventana queda en `adjustPan`: al abrir el teclado el sistema
  **panea** toda la ventana hacia arriba y encima el panel también reserva el inset del teclado,
  así que se suman los dos desplazamientos y queda un hueco de ~200 dp entre el teclado y los
  botones (bug del 2026-10-22, arreglado con `adjustResize` en la activity). Y en el panel, el
  inset de abajo es **uno solo**: `safeDrawing` bottom (el mayor entre barra de navegación y
  teclado), nunca `navigationBarsPadding().imePadding()` sumados — el teclado ya se dibuja
  encima de la barra de navegación.
- **Con navegación por gestos, ~20 dp de cada borde son del sistema, y MIUI ignora la
  exclusión.** `Modifier.systemGestureExclusion()` (en la pestaña) y
  `View.setSystemGestureExclusionRects` (en `MainActivity`) están puestos, pero medido en el
  Redmi Note 9S: un arrastre que arranca en x=2/26/45 px se lo queda el gesto de "atrás" y la
  app va al launcher; desde x=60 px sí llega y abre el panel. Por eso las zonas táctiles de la
  pestaña son más grandes que el dibujo. Si alguien "limpia" la exclusión, en un Pixel el
  arrastre desde el borde se pierde.
- El blur de la sidebar usa `RenderEffect` (solo Android 12+, con guard) y solo mientras hay un
  drawer abierto.
- **El avatar 3D es invisible para toda la instrumentación de accesibilidad**: sale como
  `android.webkit.WebView` sin ningún hijo, porque el canvas de Three.js no publica nada. No se
  puede tocar ni leer con `mobile-mcp`, Maestro ni `uiautomator`. Para el estado de la escena:
  el tag `SignAllWeb` de logcat o el overlay de diagnóstico, los dos solo en el build con
  `DEV_TOOLS`.
- **Los cues del pipeline (`micMode`, cola, estados de turno) están documentados en
  [`docs/MODO-EN-VIVO.md`](docs/MODO-EN-VIVO.md).** Si la tarea toca el micrófono, el WebSocket,
  la cola o la latencia, se lee antes.

## Assets web

- `index.html` carga Three.js r169 de jsDelivr por importmap → **necesita permiso INTERNET y
  conectividad en runtime**, aunque el avatar y los datos sean locales.
- **Nunca poner la normalización (escala/offset) en la raíz de la escena cargada.** Las
  exportaciones de Cascadeur animan la raíz del armature en posición, rotación *y* escala, así
  que el mixer la sobrescribe cada frame. Va en un objeto pivote padre.
- **Dos `.glb` completos, uno por avatar.** `models/juan.glb` (30,32 MB) y `models/pauline.glb`
  (27,85 MB) traen el personaje, la cara y las **50 señas como clips**
  `0000_afirmativo`..`0049_orden`: **200 clips por archivo**, cuatro marcas faciales por seña
  (`_afirmativo`, `_pregunta`, `_negacion`, `_orden`) y **ningún clip base**. La escena indexa
  solo el avatar elegido (`embeddedClipsFor` en `app.js`) y resuelve `signs/<id>.glb` **por
  nombre, sin pedir nada por red**. Desde el 2026-11-04 reproduce la cara que corresponde al
  tipo de oración (`FACE_VARIANT_BY_TYPE`): las cuatro variantes se distinguen solo por los
  pesos de la cara, los huesos son idénticos.
- **`humanize.js` + `nmm.json` son la capa de marcadores no manuales** (2026-11-12): suman sobre
  el clip la cabeza, la cara y el tronco del intérprete, la prosodia del tipo de oración, la
  mirada, el parpadeo y la respiración. No tocan los keyframes ni el reloj. `nmm.json` se
  regenera con `tools/extract_nmm.py`; no se edita a mano. El orden `human.restore()` →
  `tickPlayback()` → `human.apply()` del render loop **no se cambia**: sin el `restore` la capa
  se acumula fotograma a fotograma. `SignAll.setHumanize(0)` la apaga. Detalle en
  `openspec/specs/avatar/spec.md`.
- **`models/avatars.json`** es el inventario reproducible: hash y tamaño de cada fuente
  original, del archivo empaquetado, clips, articulaciones, variantes y la advertencia de pose de
  bind de Pauline. Los originales `Juan.glb` y `Pauline.glb` viven versionados en
  [`signall-avatar`](https://github.com/sign-ai-organization/signall-avatar) (`models/`, rama
  `dev`) y no se editan. `../material-3d/Avatares/` conserva la versión anterior
  (`*_final.glb`, 2026-10-24), que ya **no** es la fuente.
- **`assets/web/signs/` ya no existe** (borrado el 2026-10-22: 32 `.glb`, 5,7 MB en el APK). Era
  el layout viejo y nadie los pedía porque toda glosa del catálogo resuelve su clip adentro del
  archivo único. La ruta `signs/<id>.glb` **sigue siendo el identificador** que manda el backend
  y `app.js` la sigue parseando para sacar el `<id>`; lo que ya no hay es un archivo detrás. No
  se "limpia" del código: es el contrato.
- **Nunca editar `models/juan.glb` ni `models/pauline.glb` a mano**: se regeneran con
  `tools/pack_avatars.py` desde los originales de `signall-avatar`, que también reescriben
  `models/avatars.json`, `manifest.json` y `domain/SignsCatalog.kt`. El script hornea la pose de
  descanso y poda los morph targets que ninguna de las 200 variantes mueve.
  `tools/pack_signall.py` es el pipeline viejo de un solo avatar: no procesa estos dos archivos.
- **Arranque medido el 2026-10-26 en el Redmi Note 9S**: Juan, **1,81–3,42 s** según caché de
  página (3,42 s en frío, 1,95–2,50 s en caliente); Pauline, **1,95 s**. Un cambio de avatar en
  caliente tarda **1,90–2,57 s** (parseo 1,18–1,95 s) y se mide en `SignAllState` (`cambio de
  avatar`, `avatar listo`) y `SignAllWeb`. El splash espera a `avatarReady`, así que eso es lo
  que se ve antes de la escena.
- `renderer.setPixelRatio` está **capado en 1.75** a propósito. Sin cap, el Redmi Note 9S
  renderiza 19,6 MP/frame y el GPU queda en ~37 ms/frame, con 99 % de jank.
- `shouldInterceptRequest` fuerza `Cache-Control: no-store` para que los cambios de `app.js` se
  vean al recargar. No se saca.
- **No forzar `DoubleSide` en las mallas**: es overdraw puro en mallas cerradas y costó 4 % más
  de jank para un cambio visual cero.

## Neon

**Org: `org-orange-surf-39007829` (`sign-ai`).** Es el proyecto de la tesis. La cuenta tiene una
segunda organización, `org-steep-paper-48927149` (`Diego`), que es personal — **no tocar**. Por
eso el MCP de Neon **exige pasar `org_id`**.

Credenciales en `.envJulian` (gitignored). Si `psql` dice `parse error near '&'` no es el
archivo: es que la línea tiene `&` sin comillas.

La app **no habla con Neon**: la base es del backend. `catalog` está sembrada con las 50 glosas
de LSC50 y reejecutar el seed es idempotente. **Las 50 tienen animación desde el 2026-10-22**: el
archivo único trae las 18 que faltaban (`0032`–`0049`), así que `GLS-10` ("anunciada pero no
exportada") ya no alcanza a ninguna glosa. **No se piden por HTTP**: salen de los clips que
vienen dentro del avatar. El backend carga el catálogo **una sola vez al arrancar**: después de
un seed hay que reiniciarlo.

## Contexto del proyecto (OpenSpec)

`openspec/` es el contexto vivo del proyecto y se mantiene actualizado con cada cambio que no
sea trivial:

- **`specs/`**: fuente de verdad del comportamiento. `specs/ui/spec.md` es el **catálogo
  completo de features** de la app (todo viene del prototipo `PrototipoFrontend/`);
  `avatar`/`dataset`/`pipeline` cubren el comportamiento del rig y la animación. Se actualiza el
  spec correspondiente cuando se arregla ese comportamiento.
- **`sessions/`**: bitácora de trabajo, un archivo por sesión. Convención y plantilla en el
  skill **`sesion-openspec`**.
- **Si un spec o `openspec/config.yaml` contradicen al código, gana el código** y la diferencia
  se reporta; no se elige por cuenta propia. El pipeline de landmarks con `sign.json` ya no está
  vigente: donde aparezca, es historia. `specs/pipeline/spec.md` y `openspec/config.yaml` se
  auditaron contra el código el 2026-10-24.

---

## Índice: qué cargar y cuándo

Este archivo se lee entero en cada mensaje. **Lo de abajo no**: se carga cuando la tarea lo pide.
La regla es leer la fila antes de decidir, no después.

| Si la tarea toca… | Cargar |
|---|---|
| **Un frame del WebSocket, un nombre de métrica, el catálogo, o cualquier cosa que cruce a otro repositorio** | skill **`contrato-websocket`** — el contrato se lee en los dos lados antes de cambiarse, y el otro lado tiene que quedar actualizado o la deuda se dice en voz alta |
| **Si el código y los documentos no coinciden, o hay que decidir si manda el código o el documento** | skill **`fuentes-de-verdad`** — las dos fuentes, el flujo de revisión y qué hacer cuando no alinean |
| **Leer, corregir o compilar los documentos de la tesis** | skill **`documentos-tesis`** — dónde viven, cómo se editan, cómo se compilan con `make` y qué se hace con una divergencia |
| **El hermano, el STT, el catálogo, o una decisión de tesis** | [`docs/SISTEMA.md`](docs/SISTEMA.md) — mapa, `CLAUDE.md` del backend, protocolo del STT, estructura de `signall-docs` |
| **Saber qué se decidió ya entre el código y los documentos, o qué hay que cambiar en la app** | `ls docs/historia/CONTRASTE-*.md`. Los contrastes son documentos de trabajo de una sesión: se crean, se actualizan y a veces se borran, así que no hay una lista fija — se buscan por nombre. Cada repositorio tiene el suyo en su `docs/` |
| **El micrófono, el modo en vivo, la cola de la escena o la latencia** | [`docs/MODO-EN-VIVO.md`](docs/MODO-EN-VIVO.md) + `tools/TIEMPO-REAL.md` |
| **Probar o verificar en el dispositivo, o "va lento"** | skill **`controlar-app-movil`** — qué herramienta usar, y los errores ya pisados |
| **Compose, Lumo, accesibilidad o performance** | skill **`compose-best-practices`** (Lumo/Phosphor, cero Material 3) + `ui/designsystem/Color.kt` |
| **Tocar el dispositivo, medir, o elegir herramienta del `mobile-mcp`** | [`tools/HERRAMIENTAS.md`](tools/HERRAMIENTAS.md) — índice en [`tools/README.md`](tools/README.md) |
| **Colores, marca, contraste** | [`docs/manual-identidad-visual.md`](docs/manual-identidad-visual.md) (el owner) y `ui/designsystem/Color.kt` (los valores) |
| **Un cambio no trivial, o cerrar una sesión** | skill **`sesion-openspec`** — la bitácora y su plantilla, más la entrada en `signall-docs/cambios/` |

### Agentes disponibles

`@pipeline` para cambios que cruzan el contrato · `@ui` para cambios de interfaz en Compose ·
`@contraste` para comparar código contra documentos (solo lectura, no escribe). Se invocan con
`@nombre`.

---

## Los documentos de la tesis: locales y compilables

Desde el **2026-11-04** los documentos formales —SRS, SDD, memoria, plan de pruebas y plan de
administración— viven en [`signall-docs`](https://github.com/sign-ai-organization/signall-docs),
en `latex/`, uno por carpeta, y **ese repositorio es la fuente de verdad**. Overleaf se retiró
ese día y su MCP se quitó de la configuración global: no hay copia que sincronizar ni nada que
se edite en dos lugares.

- **Se edita el `.tex` y se compila**: `cd ../signall-docs && make srs`. Un documento que no
  compila no es un cambio terminado, y el error se lee; no se reintenta hasta que pase.
- **El control de cambios es git.** `git diff` dice qué se tocó y `git log` cuándo. Los cambios
  de fondo se anotan en `signall-docs/cambios/`.
- **Una corrección de redacción se hace; una divergencia de contenido se pregunta primero** (ver
  § Las dos fuentes de verdad).
- **Los diagramas** se editan en `diagramas/draw.io/` y se exportan a `diagramas/png/`; los PNG
  que están dentro de `latex/` existen para que el documento compile.
- **`evidencias/`** guarda las evidencias de las pruebas ejecutadas (videos, audios, libros de
  resultados y capturas): se agregan, no se corrigieron las viejas.
- **`versiones-pasadas/` es historia**: no se edita ni se cita como si fuera vigente.

Los `.drawio` y los borradores de requerimientos los edita el owner: los cambios grandes se
proponen, no se aplican en silencio.

Para mirar un documento mientras se edita: `make open DOC=srs` abre el PDF, `make watch
DOC=srs` lo recompila al guardar, y con VS Code más la extensión LaTeX Workshop el PDF queda al
lado del `.tex` y se recompila con `Ctrl+S`. El detalle está en el skill **`documentos-tesis`**.
