# Contraste App ↔ Documentos — qué hay que cambiar en la app

**Fecha:** 2026-10-17 · **App:** `sign-ai-android`, rama `flujo-oraciones`, commit
`1daa832` (el último de la lista de §1)
**Fuente de verdad:** el **SRS de Overleaf**, proyecto `6a122a19bb9a5dd9d390ba3d`,
archivo `main.tex`, **1370 líneas**, versión 426, editado el **2026-10-17** (releído
después de esa edición).
**Segundo documento:** `../signall-docs` (`v0.16` y `cambios/requerimientos.md`),
usado de apoyo cuando el SRS no habla de algo.
**Contraste del backend:** vive en el repo del backend, no acá
(`../signall-backend/docs/historia/CONTRASTE-BACKENDvsDOCUMENTOS.md`).

> **La fuente de verdad es el SRS.** Cuando el SRS y `../signall-docs` no
> coincidan, manda el SRS y el desajuste se reporta al owner. Los números de
> línea de abajo son del `main.tex` de la versión 426: si el SRS se vuelve a editar,
> verificá que sigan apuntando a lo mismo.

Su único propósito es que otra sesión pueda agarrar un ítem de la tabla y hacerlo sin
tener que releer los tres documentos.

**Trazabilidad: este documento absorbió dos informes que ya no existen.** El 2026-10-17
se borraron `CONTRASTE-DOCUMENTOS.md` (2026-10-16) y `CONTRASTE-SRS-2026-10-17.md` (la
revisión punto por punto del SRS, 651 líneas). Del segundo quedó acá todo lo que no
estaba ya: el sello de qué revisión del SRS se leyó —**versión 426, 1370 líneas, editado el
2026-10-17**—, del que dependen todos los `main.tex:NNN` de abajo, y el punto *B9* (el SRS
dice "SignAI" y el nombre del sistema es SignALL), que está en §6 #2. Los 24 ítems que
tenía ese informe se pueden encontrar acá por su número: los **8 de §1** son los que
faltaban, los **12 de §4** los que el SRS ya dejó cerrados, y los otros cuatro (A6, A15,
B4/B5) están en §3 y §7.

---

## 1. Lo que hay que hacer en la app

Ocho ítems. En todos, **el SRS ya está escrito y la app es la que está atrás.**
Ninguno requiere inventar el requisito.

| ID | Qué hay que cambiar | Dónde en la app | Qué lo pide el SRS | Estado |
|---|---|---|---|---|
| **A1** | Que el tercer estado del micrófono **se vea**, no solo se describa | `ui/screens/bottombar/SignAiBottomBar.kt`, `ui/state/MainScreenState.kt` | Tabla de estados, filas *Texto listo* y *Grabando* (`main.tex:732-741`) | ✅ Hecho con A7 |
| **A2** | Pedir el permiso de micrófono **al abrir la app**, no al tocar el micrófono | `ui/MainScreen.kt:78-103` | §Interfaz con el Usuario (`main.tex:830`) | ✅ Hecho (`f7900e2`) |
| **A3** | **`wss://` por default**, con el `ws://` de desarrollo como bypass por build config | `data/SignAiBackendConfig.kt`, `isEncrypted`, `res/xml/network_security_config.xml`, `app/build.gradle.kts`, el bypass `-Psignai.backendWsUrl` y el build `demo` con `src/demo/res/xml/network_security_config.xml` | §Interfaces de Comunicación (`main.tex:1088`, `:1093`) | ✅ Hecho · **el 2026-10-27 el `demo` demostró el `wss://` con TLS de laboratorio**; para `release` sigue faltando el dominio (§9 #6) |
| **A4** | Destrabar la frase que quedó a medio traducir; reconexión con espera creciente y contador que se reinicia | `ui/state/MainScreenState.kt`, `:695`, `:909-915` | Tabla de estados, fila *Desconectado* (`main.tex:754-765`) | ✅ Hecho · **el SRS necesita un ajuste** (§9 #9) |
| **A5** | Avisar **"Seña no disponible" glosa por glosa**, hoy se salta en silencio | `ui/state/MainScreenState.kt`, `queueItemsFor` y `onSignFailed` | §Alcance (`main.tex:200-202`) y tabla, fila *Reproduciendo* (`main.tex:747`) | ✅ Hecho |
| **A7** | De **9 estados a 7** | `ui/state/PlaybackState.kt`, `ui/state/MainScreenState.kt` | Tabla de estados, completa (`main.tex:717-766`) | ✅ Hecho |
| **A13** | Armar la **métrica de latencia de punta a punta** | `ui/state/SignAiMetrics.kt`, y en `MainScreenState.kt` `onSignStart` y `onPlaybackDone` | §Aspectos de medición (`main.tex:836-849`) | ✅ Hecho el instrumento. **La medición no va en este documento**: es del plan de pruebas de `../signall-docs` (decisión del owner, 2026-10-17) |
| **A14** | Que la app **no se pueda quedar congelada**: cuando nadie contesta, tiene que haber un reloj que la saque | `ui/state/MainScreenState.kt`, `data/SignAiWebSocketClient.kt`, `ui/webview/AvatarWebView.kt`, `assets/web/app.js` | **Ningún documento**: el SRS dice *qué* señal mostrar, nunca *cuánto* esperar (§6 #5) | ✅ Hecho y verificado en el device (6 cambios + 1 encontrado al verificar) |


**Orden sugerido.** A2 es el más chico (un cambio de comportamiento) y sirve para
verificar el ciclo completo compilar-instalar-probar. A13 es el más mecánico
puro y no toca interfaz. **A4 y A7 son el mismo trabajo**: los dos estados que le
faltan a la app (*Error* y *Desconectado*) son los dos que el SRS define.

**Orden en que se están haciendo** (de menor a mayor complejidad, 2026-10-17):
A2 → A13 → A3 → A5 → **A4 + A7 + A1** → A14. El registro de cada uno, con lo que
se midió y lo que quedó sin poder probar, está en **§8**.

---

## 2. Detalle de cada ítem

**Las referencias al código de abajo son por función, no por número de línea.**
`MainScreenState.kt` tiene casi 2.500 líneas y creció 469 en un solo día; una línea
apuntando a lo que no es manda a la sesión al lugar equivocado con toda seguridad. Los
pocos números de línea que quedan son de archivos chicos y estables, y se pueden
verificar con un grep. Si agregás una referencia, que sea al **nombre de la función**.

### A1 — El tercer estado del micrófono no se ve

**Qué dice el SRS.** La tabla de estados define *Texto listo* como estado propio
(`main.tex:740`): "El texto dictado o escrito manualmente está en la barra, puede
editarse y espera a que el usuario pulse Traducir." Y la fila *Grabando*
(`main.tex:735`) dice que en modo dictado, al cerrarse la frase, "el indicador
pasa a *Texto listo*".

**Qué hace la app.** El estado existe (`dictationReadyToSend`) pero no se ve: el
ícono del micrófono no cambia. Lo único que cambia es la descripción accesible
y un aviso de 3 s en el hint del escenario ("Texto listo. Toca Traducir para
enviarlo."). El envío lo dispara el botón Traducir.

**Qué hacer.** Que el ícono o el indicador de estados cambie visualmente en ese
tercer estado. El estado y su descripción ya están; falta la señal visible.

---

### A2 — El permiso de micrófono se pide tarde

**Qué dice el SRS** (`main.tex:830`):

> La primera vez que se abre la aplicación, esta solicita el permiso de
> micrófono. Si el usuario niega el permiso del micrófono la aplicación solo
> trabaja con el texto ingresado manualmente por el usuario.

**Qué hace la app.** `RECORD_AUDIO` se pide la primera vez que se toca cualquiera
de los dos interruptores, guardando la acción para ejecutarla si se concede
(`ui/MainScreen.kt:89-100`). `INTERNET` no se pide nunca porque no es permiso de
runtime.

**Qué hacer.** Pedir el permiso al abrir la pantalla principal, una sola vez,
conservando el aviso de 3 s cuando se deniega (`MainScreenState.onMicPermissionDenied`) y
el comportamiento de "seguir funcionando solo con texto".

---

### A3 — El enlace con el backend va sin cifrar — resuelto el 2026-10-27 con el build `demo`

**Qué dice el SRS.** Doble: "Toda comunicación que sale de un equipo viaja
cifrada mediante TLS" (`main.tex:1088`) y "Entre la aplicación y el servidor se
mantiene una conexión **WebSocket segura (WSS)**, bidireccional y persistente"
(`main.tex:1093`).

**Qué hacía la app el 2026-10-17.** El WebSocket abría en
`ws://10.0.2.2:8080/ws/translate` (`data/SignAiBackendConfig.kt`, `isEncrypted`) y el
manifiesto permitía tráfico en claro de forma global. La URL se inyecta por build config
(`app/build.gradle.kts`, `defaultConfig` y el bypass `-Psignai.backendWsUrl`) y el código ya
sabía derivar `https://` de una URL `wss://` (`SignAiBackendConfig.kt:29`).

**Qué hace hoy.** `debug` no cambió: sigue abriendo `ws://` y sigue permitiendo el texto en
claro, porque es el build de desarrollo y ahí se mide la latencia sin el handshake. Se agregó
un cuarto build, **`demo`**, que es `release` en todo lo que hace al enlace:

| Pieza | Qué es |
|---|---|
| `app/build.gradle.kts` | `create("demo") { initWith(release) }`, firmado con la clave de `debug`, `isDebuggable = true`, y `BACKEND_WS_URL` en `wss://127.0.0.1:8443/ws/translate`. La property `-Psignai.backendWsUrl` solo se acepta si empieza por `wss://`, igual que en `release` |
| `app/src/demo/res/xml/network_security_config.xml` | `cleartextTrafficPermitted="false"` y `trust-anchors` con `system` **y** `user`. Es lo único que hace confiable a la CA de laboratorio: desde la API 24 Android no confía en las CA del usuario si el build no lo declara |
| `tools/verificar-tiempo-real.sh` | además del chequeo del 8080, comprueba `https://127.0.0.1:8443/health` con la CA del laboratorio |

El 8443 del lado del teléfono no es capricho: `adb reverse tcp:443 tcp:443` falla con
`cannot bind listener: Permission denied`, así que el túnel es `adb reverse tcp:8443 tcp:443`
y el certificado no depende del puerto.

**Cómo se verifica.** `./gradlew installDemo`, `adb reverse tcp:8443 tcp:443`, la CA del
laboratorio instalada en el teléfono y `adb logcat -s SignAiNet`: la sesión abre con
`sesión abierta: wss://… (TLS)`. El mismo build, apuntado al 8080 en claro, falla con
`CLEARTEXT communication … not permitted`. Medido el 2026-10-27 con el teléfono por USB.

**Lo que queda.** `release` sigue apuntando a `wss://backend.invalid/ws/translate` y no
sirve para nada real sin un certificado de una CA pública; y el build `demo` reemplaza a
`debug` en el teléfono porque comparte `applicationId`, así que después de la demo hay que
reinstalar `debug`.

---

### A4 — Si la conexión se cae traduciendo, la app queda trabada

**Qué dice el SRS** (`main.tex:754-765`, fila *Desconectado*, texto cerrado por
el owner el 2026-10-17):

> Se perdió la conexión con el servidor, lo que puede ocurrir en cualquier estado.
> El avatar vuelve a reposo, el indicador de estados lo indica y el sistema
> intenta reconectarse. La reconexión espera de forma creciente hasta un máximo de
> cinco segundos entre intentos y no tiene límite de intentos: se mantiene hasta
> que la conexión se restablezca, momento en el que el contador se reinicia. El
> texto pendiente no se pierde. Si la conexión se pierde mientras una frase se
> está traduciendo y el avatar todavía no ha ejecutado ninguna seña de ella, esa
> frase se cancela y su texto vuelve a la barra de transcripción, editable, para
> que el usuario pueda mandarla de nuevo. Si la pérdida ocurre cuando el avatar
> ya está representando, la representación se interrumpe y el indicador vuelve a
> *Inactivo*.

**Qué hace la app.** Tres defectos:

1. `BackendEvent.Disconnected -> Unit` (`:695`): la desconexión no hace nada.
2. El bucle de reconexión no pone *Sin conexión* si el estado es `Translating` o
   `Signing` (`:538-540`), así que **el indicador nunca avisa**, en contra de lo
   que pide el SRS.
3. La espera crece pero `attempt` **nunca se reinicia** al reconectar (`:530-545`):
   después de unas horas de app abierta, la primera caída espera 5 s en vez de 1 s.

Y el síntoma: con el estado clavado en `Translating`, el botón Traducir queda
deshabilitado y el envío manual se rechaza (`:909-915`). No hay error, no hay
mensaje, no hay salida: **hay que cerrar la app a la fuerza**.

**Qué hacer.** La función que deshace todo esto **ya existe**: `fail()`
(`:985-1013`) limpia el turno, suelta la retención del texto, apaga la cola, baja
el estado a `Idle` y deja el campo editable. Hoy solo se llama cuando el backend
manda un `Failure`. El trabajo es que `Disconnected`, con turno en vuelo, haga lo
mismo — y cubrir los dos casos que el SRS separa: en *Traduciendo* cancelar y
devolver el texto, en *Reproduciendo* interrumpir y volver a *Inactivo*.

**Detalle a cuidar.** El límite de intentos **no se implementa**: la espera se
topa en 5 s y se reintenta indefinidamente. Y en modo TR el micrófono está
prendido a propósito; `fail()` lo apaga, así que hay que decidir si la
desconexión debe respectar el modo TR o cortarlo como cualquier error.

---

### A5 — "Seña no disponible" no se avisa glosa por glosa

**Qué dice el SRS.** En §Alcance (`main.tex:200-202`): "Cuando una glosa no existe en
el catálogo, el sistema informa que la seña no está disponible en lugar de enviar
una instrucción inválida al avatar." Y en la tabla de estados, dentro de
*Reproduciendo* (`main.tex:747`): "Si alguna palabra no está en el catálogo, la
pantalla muestra el mensaje 'Seña no disponible'".

**Qué hace la app.** Con la seña de prueba apagada (default), cada glosa sin
animación se salta en silencio y solo se cuenta en la métrica `sin_seña`. El
texto aparece únicamente como caption cuando una oración entera no produjo nada
representable.

**Qué hacer.** Avisar por glosa. Con la seña de prueba encendida la glosa se
representa con la seña por defecto, y ahí el aviso no aplica.

---

### A7 — Los estados de la interfaz no son los del SRS

**Qué dice el SRS.** Siete estados, en la tabla `tab:estados-ui`
(`main.tex:717-766`): **Inactivo, Grabando, Texto listo, Procesando,
Reproduciendo, Error, Desconectado**.

**Qué hace la app.** Nueve (`ui/state/PlaybackState.kt`): Listo para traducir,
Dictando, Escuchando, Procesando, Traduciendo, Reproduciendo señas, Reproducción
completada, Conectando con el servidor, Sin conexión con el servidor.

Diferencias de fondo, no de rótulo:

- **No hay estado de error.** Los errores son un mensaje transitorio de 3 s en el
  hint, no un estado de la máquina.
- **"Traduciendo" y "Conectando" no existen en el SRS**, y son donde la app pasa
  casi todo el tiempo esperando.
- El SRS no distingue dictado de TR en la lista, pero sí dentro de *Grabando*.

**Qué hacer.** De 9 a 7. El estado *Error* y el *Desconectado* son el mismo
trabajo que A4.

#### El mapa, ya decidido (owner, 2026-10-17)

| SRS (7) | App (9) | Qué hacer |
|---|---|---|
| **Inactivo** | `Idle` | Se queda. La etiqueta en pantalla sigue siendo "Listo para traducir" |
| **Grabando** | `Dictating` + `Listening` | **Fusionar** en uno |
| **Texto listo** | `dictationReadyToSend` (hoy es un flag suelto) | **Convertir en estado** |
| **Procesando** | `Processing` + `Translating` | **Fusionar** en uno |
| **Reproduciendo** | `Signing` | Se queda |
| **Error** | — (hoy es un mensaje de 3 s) | **Crear** |
| **Desconectado** | `Offline` + `Connecting` | **Fusionar** en uno |

**Cuatro reglas para implementarlo:**

1. **Los nombres del enum son los del SRS, en español y con mayúscula inicial:**
   `Inactivo`, `Grabando`, `TextoListo`, `Procesando`, `Reproduciendo`, `Error`,
   `Desconectado`. Exactamente los siete de `main.tex:717-766`.
2. **La distinción entre dictado y TR vive en la etiqueta, no en el estado.** Un
   solo estado `Grabando` cuya etiqueta sea "Dictando..." o "Escuchando..."
   según el modo. Así el enum tiene siete exactos —que es lo que pide el SRS— y
   el usuario sigue viendo en qué modo está. Lo mismo con `Procesando`: para el
   usuario, esperar la transcripción y esperar las glosas son la misma espera.
3. **`Done` no se borra, se mueve.** Hoy ese estado no solo decora:
   `doneVisible` lo usa para mostrar el aviso de reproducción completada
   (`MainScreen.kt`, el `if (granted) action?.invoke() else state.onMicPermissionDenied()`). Si se elimina, se pierde ese aviso. Hay que pasarlo a
   flag propio, igual que `dictationReadyToSend` — el patrón ya existe dos veces
   en el archivo.
4. **`Connecting` y `Offline` se funden en `Desconectado`, como dice el SRS.**
   **Consecuencia conocida y aceptada:** cada arranque de la app muestra
   "Desconectado" durante el instante que tarda el socket. Hoy se ve "Conectando
   con el servidor", que para ese momento era más honesto. Se acepta porque el
   SRS no distingue los dos momentos, y el estado `Desconectado` ya dice "el
   sistema intenta reconectarse".

---

### A13 — La latencia de punta a punta no existe como número

**Qué dice el SRS.** §Aspectos de medición (`main.tex:836-849`) define tres
medidas. La que importa acá es la segunda:

> **Tiempo hasta la primera seña:** tiempo desde que el texto se envía a
> traducir, al pulsar Traducir o, en el modo TR, al cerrarse la frase, hasta que el
> avatar ejecuta la primera seña.

**Qué hace la app.** Mide tres cosas por logcat (`ui/state/SignAiMetrics.kt`) y
ninguna es ese total:

| Métrica | Qué mide |
|---|---|
| `onTurn` (`frase #N`) | desde que se manda la frase hasta el `done` del backend |
| `onSignStart` (`oración→avatar:`) | desde que llega un `segment` hasta el primer movimiento |
| `onPlayback` (`representado:`) | duración de la representación |

**Qué hacer.** Armar el total. **Las dos piezas que lo necesitan ya existen**: el
reloj del turno que arranca al mandar la frase (`MainScreenState.turnSentAt`) y el
`signStart` que reporta la escena (`onSignStart`). Solo faltaba restarlos en el
momento correcto y emitirlo con el mismo formato que las otras: **hecho**.

**Lo que queda no es código y no es de acá.** La **medición** de ese número —con
cuántas frases y en qué máquina— la maneja el **plan de pruebas** de
`../signall-docs`. Este contraste solo deja constancia de que el instrumento está.

**Lo que no es código.** La tercera medida del SRS, *legibilidad del avatar*
evaluada con ISO 9241-11 (`main.tex:848`), es un estudio con usuarios. No se
resuelve tocando la app.

---

## 3. Ítems que necesitan una decisión antes de tocar código

| ID | La decisión | Por qué no se puede asumir |
|---|---|---|
| **A6** | ¿Los marcadores no manuales se hacen en la app o se sacan del SRS? | El SRS los pide (`main.tex:205`, y §Hardware). La app recibe el tipo de frase y no lo usa. El 2026-10-16 estaba "en pausa" **porque el documento no lo pedía**; ahora sí lo pide, así que la premisa cambió.<br>**Lo que se averiguó el 2026-10-17, y se corrigió contra el GLB el 2026-10-26: NO es un trabajo chico de la app.** El rig **sí** tiene huesos de cara —**31, y todos son articulaciones de skin**: `brow_*`, `jaw*`, `mouth_*`, `eye_blink_open_*`, `eye_squint_*`, `eyeball*`—, tiene **7 morph targets** (ninguno de expresión facial) y **50 clips** desde el 2026-10-22. Pero los clips dejan los huesos de cara **casi quietos** (el mayor rango de un componente del cuaternión de rotación es **0,0012**, contra **1,996** del mayor hueso del cuerpo, medido el 2026-10-26), y la escena no maneja ni esos huesos ni los morphs. O sea: la cara se puede mover, pero hay que **crear los clips de expresión en Cascadeur y exportarlos**, y además la escena tiene que saber cuándo reproducirlos mientras hay una seña en curso. Es trabajo de rig y de assets, no de la app |
| **A15** | ¿`SignAssetRepository` se borra o se deja? | El SRS cerró el canal HTTP a `/health` (`main.tex:1121`) y dijo que las animaciones van dentro del APK (`:1114`). Con el catálogo actual su descarga **no se ejecuta nunca**, porque toda glosa del catálogo trae una ruta `signs/*.glb` —nuestro propio layout— y eso se saltea antes de llegar a la red. **Pero el archivo no se puede borrar entero**: sus dos constantes `WEB_ORIGIN` y `SIGNS_PATH_PREFIX` son las que usa `AvatarWebView` para el dominio del `WebViewAssetLoader` y para el manejador que sirve `/signs/` desde la caché. Sin ellas el WebView no carga la escena. Decisión del owner 2026-10-17: **se deja**. Lo que está muerto es solo la rama de descarga, y esa rama es justo el comportamiento que describe el SDD ("el cliente Android descarga los archivos de animación que falten"), así que borrarla haría que la app se alejara del documento |
| **B4/B5** | ¿Quién exporta las 18 señas que faltan? | **Resuelto:** las 50 señas de LSC50 están en el archivo único desde el 2026-10-22. Lo que queda no es exportarlas sino **validar el contenido de `0032`–`0049`** con el owner y el director. Ver §5 |

---

## 4. Cosas que no hay que tocar en la app

El SRS quedó a favor de lo que ya hace la app, o ya no hay diferencia. No
abrirlas de nuevo.

| ID | Por qué está cerrado |
|---|---|
| **A8** | El SRS ya dice Three.js 0.169.0 en WebView, no Filament. El `.drawio` de C4-2 que decía Filament está en `../signall-docs` y lo edita el owner |
| **A9** | El SRS dice que en modo TR el micrófono **permanece encendido** (`main.tex:737`). La app hace eso |
| **A10** | El tope de 8 s pasó a ser búfer del **servidor** (`main.tex:1177`). La app no tiene temporizador y no debe tener |
| **A11** | El SRS documenta la interfaz real: paneles, hoja de historial, Acerca de, velocidad de señas (`main.tex:803`), modo oscuro/claro, los cinco ajustes de accesibilidad y el bloque Avanzado. Todo existe |
| **A12** | Las cinco opciones de accesibilidad ya están, incluida la nota de que "reducir animaciones" hace el corte seco entre señas. `domain/Settings.kt`, el bloque de accesibilidad (`ACC-*`) |
| **A14** | El SRS no habla de instrumentación. No molesta ni prohíbe: la app mide sola con `SignAiMetrics`, `SignAiNet`, `SignAIWeb`, `SignAiState` |
| **B1** | El SRS dice que en dictado el texto queda en la barra para revisarlo y se manda con Traducir. La app hace eso |
| **B2** | El SRS dice "el texto pendiente no se pierde". La app lo conserva |
| **B3** | El SRS dice "Mientras el micrófono está activo, la barra solo muestra el texto; al apagarse, el texto puede editarse". La app hace exactamente eso |
| **B6** | El SRS pide "transiciones fluidas mediante interpolación" y documenta el corte seco con "reducir animaciones". La fundición de 100 ms se mantiene |
| **B7** | Las animaciones van dentro del APK. El SRS dice 50 y el objetivo es 50 (ver §5) |
| **B8** | El SRS describe dos botones fijos (micrófono y TR), nunca simultáneos, sin selector. La app hace eso |

**Sobre los IDs.** Los de §1 son los que la app usa hoy. Los de esta tabla vienen del
contraste del 2026-10-16 y son los que tenía el informe punto por punto. **Se pisan en
un número: `A14`.** En §1 es *que la app no se pueda quedar congelada* (agregado el
2026-10-17, entrada nueva); en esta tabla es *la app instrumenta sola*, que no es un
ítem sino una constancia de que el SRS no habla de instrumentación y a la app le da
igual. Cuando se cite un `A14` sin decir la tabla, es el de §1.

---

## 5. Sobre el "50" del SRS

El SRS dice que las **50** animaciones van dentro del APK (`main.tex:930`,
`:1185`, y §Alcance). **Hoy hay 50** desde el 2026-10-22, cuando
`tools/pack_signall.py` armó el archivo único con los 50 clips `0000`..`0049`
(12,98 MB). Desde el 2026-10-26 hay **dos** archivos —`models/juan.glb`, 34,28 MB, y
`models/pauline.glb`, 29,46 MB—, cada uno con las 50 señas base y sus cinco variantes
faciales; la app reproduce solo el clip base.

**Decisión del owner (2026-10-17): el SRS no estaba equivocado.** El documento
describe lo que va a haber; el objetivo era llegar a 50 y se llegó. Lo que queda
de las 18 que faltaban (`0032`–`0049`) no es exportarlas: es **validar su
contenido** con el owner y el director.

Consecuencia para la app: hoy **ninguna** glosa del catálogo llega con una ruta
que no exista, así que el caso no se da. `SignsCatalog.isOwnAssetPath()` se
conserva para una glosa futura anunciada sin clip: evita pedirla por red (un 404
garantizado) y, según `demoSignFallback`, la representa con la seña de reserva o
la saltea avisando "Seña no disponible" por glosa.

---

## 6. Pendiente en los documentos (no es trabajo de la app)

Reportado al owner el 2026-10-17. **Nada de esto se toca desde acá.**

| # | Qué | Dónde |
|---|---|---|
| 1 | La §3 del SRS está vacía y conserva los subtítulos de una plantilla de juegos ("Funcionalidad 1: Jugar", "Registro y Autenticación", "Administración", "Consultas"). Con eso desaparecieron los IDs `RF-*` y `RNF-*` que usan `v0.16` y el plan de pruebas | Overleaf, `main.tex` §3 |
| 2 | El SRS dice "SignAI" 25 veces y "SignALL" ninguna. Delegado al compañero | Overleaf, todo el documento |
| 3 | Dos marcadores de trabajo sin completar: `[PENDIENTE: tamaño del APK con todos los archivos GLB.]` | Overleaf, `main.tex:932` y `:1155` |
| 4 | `../signall-docs/cambios/requerimientos.md` punto 19 pide "límite de intentos" de reconexión. El owner decidió **sin** límite: espera creciente hasta 5 s, indefinida. Hay que corregir ese texto | `../signall-docs` |
| 5 | **Ningún documento dice cuánto esperar antes de decidir que algo salió mal.** El SRS dice *qué* señal mostrar ante un error o una desconexión, y RNF-REL-02 dice que el avatar vuelva a reposo, pero en ningún lado hay un plazo. La app lo inventó: **15 s de silencio**. Sin ese número, una espera larga es indistinguible de un cuelgue | Overleaf, tabla de estados, filas *Procesando* y *Error* (`main.tex:717-766`) |
| 6 | **Apagar el micrófono con una frase en vuelo.** `main.tex:737` dice que en modo TR el micrófono permanece encendido, y nada dice de qué modo se lo apaga a mitad de una traducción. La decisión del owner: apagarlo **cancela la frase** y su texto vuelve al campo editable | Overleaf, `main.tex:737` y la fila *Grabando* |
| 7 | **Seña que existe pero no se puede descargar.** `main.tex:200-202` cubre "cuando una glosa no existe en el catálogo"; no cubre el caso de que la glosa existe y el archivo no baja. La decisión del owner: se saltea esa seña, se siguen representando las demás y el indicador muestra "Seña no disponible" | Overleaf, `main.tex:200-202` y la fila *Reproduciendo* (`main.tex:747`) |
| 8 | **Cómo se detecta que se cayó la conexión.** La fila *Desconectado* describe la reconexión (sin límite, espera creciente hasta 5 s, contador que se reinicia) pero no dice cómo se nota la caída. Sin latido, una caída que no cierra el socket no se ve: la app cree que está conectada | Overleaf, fila *Desconectado* (`main.tex:754-765`) |
| 9 | **Un `ping` en el WebSocket.** El SRS describe el canal (`RF-INT-COM-01`: *"el sistema detectará una desconexión del canal y se reconectará automáticamente"*) pero no dice con qué mecanismo. Hoy no hay ninguno, y la detección depende de que el otro lado cierre la conexión | Overleaf, §Interfaces de Comunicación |

> Desde el 2026-11-02 la sesión puede dejar estos textos como sugerencias con control de
> cambios, con permiso pedido en cada tanda; la aprobación sigue siendo del owner. Ver el
> skill `overleaf-lectura`.

**Texto propuesto para pegar en el SRS** (propuesto por la sesión, **lo aplica el owner**;
no se escribe nada en Overleaf desde acá):


- Final de la fila *Procesando* de `tab:estados-ui` (`main.tex:744`, después de
  *"está trabajando."* y antes del `\\ ddlinespace`):
  *"Si no llega ninguna respuesta del servidor durante 15~segundos, se considera que la
  traducción falló y el indicador pasa a Error. El plazo se cuenta desde la última
  respuesta recibida, de modo que una traducción larga que sigue devolviendo oraciones
  no se interrumpe."*
  **Solo la fila *Procesando*:** la fila *Error* (`main.tex:750-753`) ya dice que se
  muestra la señal de error, que el texto se conserva y que el sistema vuelve a
  *Inactivo*, así que no hay que tocarla.
- Final de la fila *Grabando* de `tab:estados-ui` (`main.tex:738`, después de
  *"captando la frase siguiente."* y antes del `\\ ddlinespace`):
  *"Si el usuario apaga el micrófono mientras una frase enviada se está traduciendo, esa
  frase se cancela y su texto vuelve a la barra de transcripción, editable, para que el
  usuario pueda mandarla de nuevo. Si el avatar ya había ejecutado alguna seña de esa
  frase, la representación se interrumpe, el indicador vuelve a Inactivo y el texto no
  vuelve a la barra, porque ya se mostró parte de ella."*
  **La segunda mitad no es opcional:** es lo que hace la app, y es el mismo criterio de
  la fila *Desconectado* (`main.tex:761-765`). Dejarla afuera haría que el documento
  prometa algo que la app no cumple.
- **Punto 3, dos lugares a propósito.** El SRS afirma lo de las señas que no se pueden
  representar en los dos sitios, así que tocar uno solo deja al otro diciendo que ese
  caso no existe.
  - Final del ítem *Validación contra el catálogo de señas*, en §Alcance
    (`main.tex:202`, después de *"en lugar de enviar una instrucción inválida al
    avatar."*):
    *"Si la glosa existe en el catálogo pero su animación no puede cargarse, el sistema
    omite esa seña y continúa representando las demás."*
  - Fila *Reproduciendo* de `tab:estados-ui` (`main.tex:747`, después de
    *"``Seña no disponible''."* y antes de *"Al terminar,"*):
    *"Cuando la animación de una glosa no puede cargarse, esa seña se omite, el resto de
    la frase se sigue representando y la pantalla muestra el mismo mensaje."*
- **Punto 4, dos lugares: el mecanismo y su referencia.** El mecanismo va donde se
  describe el canal; en la tabla de estados alcanza con señalar que existe, porque esa
  fila ya explica qué se hace después de la caída.
  - §Interfaces de Comunicación, §Canal de traducción (`main.tex:1095`, después de
    *"aplicación está en uso."* y antes de *"Se eligió este protocolo"*):
    *"El cliente mantiene la sesión con un latido: envía una solicitud de comprobación
    cada 20~segundos y, si no recibe respuesta del servidor dentro de los 20~segundos
    siguientes, da la sesión por perdida. Así se detecta una caída que no cierra la
    conexión, que de otro modo pasaría inadvertida."*
  - Fila *Desconectado* de `tab:estados-ui` (`main.tex:756`, después de *"el indicador
    de estados lo indica"* y antes de *"y el sistema intenta reconectarse"*):
    *"La caída se detecta mediante el latido que el cliente mantiene con el servidor."*

---

## 7. Decisiones tomadas el 2026-10-17

Registro de lo que se resolvió en esta sesión, para que otra sesión no lo vuelva
a abrir.

- **A4, reconexión.** Sin límite de intentos: espera creciente hasta un máximo de
  5 s entre intentos, indefinida, con el contador reiniciado al restablecerse. Se
  descartó el límite duro porque dejaría de reconectar y obligaría a reiniciar la
  app, que es justo lo que A4 viene a arreglar.
- **A4, la frase en vuelo.** Si se cae la conexión en *Traduciendo* y el avatar
  no ejecutó ninguna seña, la frase se cancela y su texto vuelve al campo
  editable. Si se cae en *Reproduciendo*, la representación se interrumpe y el
  estado vuelve a *Inactivo*. El texto no vuelve al campo en ese segundo caso,
  porque ya salió del campo y el usuario ya lo vio.
- **A4, el micrófono.** **Resuelto el 2026-10-17:** la desconexión **lo apaga**, con
  el aviso "Se perdió la conexión con el servidor. Se está reconectando." Cortar es
  más honesto que dejar el micrófono grabando hacia un socket muerto, y le dice al
  usuario de una vez que tiene que volver a prenderlo. **Consecuencia: el SRS hay que
  ajustarlo** —`main.tex:737` dice que en modo TR el micrófono permanece encendido, y
  la fila *Desconectado* no menciona el micrófono. El texto exacto para pegar está en
  `openspec/sessions/2026-10-17-estados-y-desconexion.md` (§9 #9).
- **A7, los nombres de los estados.** El enum queda con **los siete nombres del
  SRS, en español**: `Inactivo`, `Grabando`, `TextoListo`, `Procesando`,
  `Reproduciendo`, `Error`, `Desconectado`. La distinción entre dictado y TR
  vive en la etiqueta, no en el estado. `Done` pasa a flag. `Connecting` se funde
  en `Desconectado`, aceptando que cada arranque muestre "Desconectado" mientras
  el socket abre. Mapa completo en §2.
- **B4/B5, el número de señas.** El SRS dice 50 y hay 50 desde el 2026-10-22. Lo
  que queda es validar el contenido de `0032`–`0049` con el owner y el director, no
  exportarlas.
- **§6, los huecos del SRS.** Fuera del alcance de la app: los arregla el owner o
  el compañero.
- **A14, el reloj de silencio: 15 s y estado *Error*.** Cuando una frase sale y no
  llega **nada** —ni una oración, ni el fin, ni un error— en 15 segundos, la app lo
  toma como fallo: el texto vuelve al campo, el micrófono se corta, el indicador
  muestra *Error* y a los 3 s vuelve a *Inactivo*. Son 15 s porque es el mismo plazo
  que ya usa el reloj de la transcripción, y 5 s por encima del tope que el backend
  le pone al LLM (10 s). **El plazo se rearma con cada frame que llega**, así que es
  un reloj de *silencio* y no de duración: una frase larga con muchas oraciones no se
  mate a mitad. Ningún documento prescribe un plazo (§6 #5): es decisión de la app y
  el texto para el SRS está propuesto en §6.
  **Riesgo conocido y aceptado por el owner:** esos 15 s pueden declarar fallida una
  frase que sí iba a funcionar, porque **la consulta a la base de datos no tiene
  tiempo máximo**. El dueño de ese problema es el backend, y la propuesta está en su
  contraste (`../signall-backend/docs/historia/CONTRASTE-BACKENDvsDOCUMENTOS.md`, hallazgo 1).
  Acá queda solo la marca: **esto se revisa del lado del backend**.
- **A14, apagar el micrófono con una frase en vuelo: cancela.** Si hay una frase
  esperando respuesta y el usuario apaga el micrófono o el TR, esa frase se cancela
  y su texto vuelve al campo editable, con el estado en *Inactivo*. No se manda sola.
  Antes el apagado no sacaba a la app de *Procesando*: el botón de parar no paraba.
- **A14, una seña que no se puede descargar: se saltea y se sigue.** Si la glosa
  existe en el catálogo pero el archivo no baja, se omite esa seña, se sigue
  representando el resto de la frase y el indicador muestra "Seña no disponible" —
  el único texto propio que la tabla permite. Antes se cortaba la frase entera en
  silencio: el usuario veía terminar la representación y no se enteraba de que
  faltaban señas.

---

## 8. Registro de avance (2026-10-17)

Una entrada por ítem, en el orden en que se está haciendo: de menor a mayor
complejidad. Cada una dice **qué se midió** y **qué quedó sin poder probar**, que
es lo que otra sesión necesita para no dar algo por verificado sin serlo.

### ✅ A2 — Permiso de micrófono al abrir la app

Commit `f7900e2`, instalado y verificado en el Redmi Note 9S (Android 11, MIUI).
Cuatro casos: el diálogo aparece al abrir; al denegar sale el aviso de 3 s y el
campo y Traducir siguen disponibles; el segundo arranque no pregunta ni avisa; con
el permiso concedido no hay diálogo. Bitácora completa en
`openspec/sessions/2026-10-17-a2-permiso-microfono-al-abrir.md`.

**No se pudo probar:** el camino del toque del micrófono, porque sin backend el
control queda deshabilitado (`micAvailable = state.backendConnected`). Ese camino
no cambió de comportamiento, solo se le extrajo el chequeo a un helper.

### ✅ A13 — Métrica de latencia de punta a punta

`SignAiMetrics.onFirstSign()`, una muestra por frase, y el reloj `turnSentAt` en
`MainScreenState` que arranca en los dos únicos lugares donde la app manda una
frase (`requestTranslation` y `sendLivePhrase`).

**Instrumento verificado en el device** contra un backend falso local
(`/tmp/opencode/fake-backend.py`, fuera del repo): una frase de dos oraciones dio

```
oración→avatar: 130 ms          (el tramo del teléfono)
envío→primera seña: 184 ms      (el total del SRS)
frase #1: 313 ms, 2 oraciones, 3 glosas
```

**El número no es una medición de nada todavía**: el backend falso responde al
instante y mete 150 ms entre oraciones, así que 184 ms es el funcionarial del
instrumento, no la latencia del sistema. **La línea base real sigue pendiente**, y
no es solo por el backend falso: la publicada (913 ms p50 / 1336 ms p95) es **del
camino por palabras** y con oraciones la cifra sube por construcción, así que no
es comparable. Hay que remedir con las 28 frases y el backend de verdad.

### ✅ A3 — `wss://` por default, `ws://` solo como bypass de desarrollo

El cifrado pasó a ser cosa del **build type**, que es lo único que puede
distinguir "estoy mostrando la tesis" de "esto va a producción":

| | URL | Texto en claro |
|---|---|---|
| `debug` | `-Psignai.backendWsUrl=…`, o `ws://10.0.2.2:8080/ws/translate` | Permitido |
| `release` | Solo `wss://`. Una property que no sea `wss://` se **ignora con aviso** | **Prohibido** |
| `demo` (2026-10-27) | Solo `wss://`. Default `wss://127.0.0.1:8443/ws/translate` | **Prohibido**, y confía en las CA del usuario |

Tres archivos: `app/build.gradle.kts` (la property pasa a leerse por build type y
release la ignora si no es `wss://`), `res/xml/network_security_config.xml`
(prohíbe texto en claro) y su gemelo en `src/debug/res/xml/` (lo permite). El
host del bypass no se puede enumerar —`10.0.2.2` en el emulador, la IP de la red
local en un teléfono, `127.0.0.1` con `adb reverse`—, por eso la excepción es
por build type y no por dominio.

**Medido:**

```
$ ./gradlew :app:assembleRelease -Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate
SignAI: release ignora -Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate porque no es
  wss://; usa wss://backend.invalid/ws/translate hasta que se pase una URL wss://.
BUILD SUCCESSFUL

# BuildConfig generado, uno por variante:
debug   → BACKEND_WS_URL = "ws://10.0.2.2:8080/ws/translate"
release → BACKEND_WS_URL = "wss://backend.invalid/ws/translate"

# recurso empaquetado:
debug   → cleartextTrafficPermitted="true"
release → cleartextTrafficPermitted="false"
```

Y en el device, con el bypass: `SignAiNet: sesión abierta:
ws://127.0.0.1:8080/ws/translate (SIN CIFRAR)`. Esa línea es el hook de
verificación de RF-INT-COM-03, cuyo criterio de aceptación es "verificable
mediante inspección del tráfico de red": la app dice con qué ethernetos abrió, y
en un build de release no puede decir otra cosa.

**El 2026-10-27 se cerró el otro extremo:** `demo` hereda `release`, se firma con la clave
de `debug` y trae su propio `src/demo/res/xml/network_security_config.xml` con
`cleartextTrafficPermitted="false"` y anclas `system` + `user`. Es lo único que puede, a la
vez, prohibir el texto en claro y confiar en la CA de laboratorio que instala `mkcert`.

```
$ ./gradlew :app:assembleDemo
$ adb reverse tcp:8443 tcp:443          # el 443 no se puede mapear: Permission denied
$ adb install -r app/build/outputs/apk/demo/app-demo.apk
$ adb logcat -s SignAiNet
SignAiNet: sesión abierta: wss://127.0.0.1:8443/ws/translate (TLS)
```

Y contra el 8080 en claro, el mismo APK no abre: el sistema rechaza la conexión con
`CLEARTEXT communication … not permitted by network security policy`.

El certificado del proxy lo emite `mkcert` para `127.0.0.1`, `localhost` y `192.168.80.49`, y
el plan completo está en `sign-ai-backend/docs/plan-proxy-tls.md`. `demo` comparte
`applicationId` con `debug`, así que lo reemplaza en el teléfono: para volver a medir
latencia hay que reinstalar `debug`.

### ✅ A5 — "Seña no disponible", glosa por glosa

`queueItemsFor` ahora devuelve **qué glosas quedaron afuera** además de la cola
(`ResolvedSentence`), y `appendQueue` avisa cuando esa lista no está vacía. Antes
solo se contaban en `sin_seña` y el texto aparecía únicamente cuando una oración
**entera** no produjo nada representable, así que una frase con dos glosas de cinco
sin animación se saltaba en silencio lo que le faltaba.

El aviso es **exactamente el texto del SRS**, sin la lista de glosas: lo pidió el
owner el 2026-10-17 ("corto; si quieres más detalle, en el modo debug"), porque el
hint es de una línea y se lee de reojo mientras el avatar representa. Cuáles
fueron queda en el log (`SignAiState`), que es donde se mira al depurar:

```
SignAiState: glosas sin animación, avisadas: glosa-sin-seña
```

**Medido en el Redmi Note 9S** contra el backend falso, con la seña de prueba
apagada (default) y encendida:

| Seña de prueba | Resultado |
|---|---|
| Apagada | Hint **"Seña no disponible"**, `sin_seña=1`, el avatar no se mueve |
| Encendida | **Ningún aviso**, la glosa se representa con la seña por defecto, turno completo |

Las dos ramas son excluyentes por construcción, pero se comprobaron las dos. Al
terminar se dejó el interruptor como estaba (apagado, el default).

### ✅ A7 + A4 + A1 — Los siete estados del SRS, y que la app no se trabe

Las tres juntas porque no se separan: *Texto listo* **es** el estado que hace visible
A1, y *Error* y *Desconectado* son los dos estados que le faltaban a la máquina y los
dos que explican lo que le pasa al usuario cuando se cae la red.

**A7.** El enum tiene los siete nombres del SRS y nada más: `Inactivo`, `Grabando`,
`TextoListo`, `Procesando`, `Reproduciendo`, `Error`, `Desconectado`. Tres fusiones y
dos conversiones:

| SRS (7) | Antes (9) | Cómo quedó |
|---|---|---|
| Inactivo | `Idle` | Se queda. La etiqueta sigue diciendo "Listo para traducir" |
| Grabando | `Dictating` + `Listening` | Uno solo; la etiqueta dice "Dictando..." o "Escuchando..." según el modo (`stateLabel`) |
| Texto listo | flag `dictationReadyToSend` | **Se deduce** en `stateForUi`, no se asigna (§9 #10) |
| Procesando | `Processing` + `Translating` | Uno solo |
| Reproduciendo | `Signing` | Se queda |
| Error | — (mensaje de 3 s) | **Creado**: `fail()` lo pone y vuelve a `Inactivo` a los 3 s |
| Desconectado | `Offline` + `Connecting` | Uno solo, y con reconexión sin límite |

`Done` pasó a ser el flag `doneVisible` (mismo patrón que `dictationReadyToSend`), así
que el aviso de "Reproducción completada" no se perdió.

**A4.** `BackendEvent.Disconnected` era `Unit`; ahora es `onDisconnected()`, con los
dos casos que el SRS separa:

| Cuándo se cae | Qué hace |
|---|---|
| **Procesando**, sin ninguna seña ejecutada | Cancela la frase, su texto vuelve al campo **editable** y Traducir vuelve a estar disponible: el usuario nunca la vio representada |
| **Reproduciendo** | Interrumpe de verdad: `SignAI.stop()` corta el mixer y vacía la cola en el JS. El texto **no** vuelve al campo, porque ya salió de ahí y ya se mandó |
| El micrófono, en los dos casos | **Se apaga**, con el aviso "Se perdió la conexión con el servidor. Se está reconectando." Decisión del owner (2026-10-17) |

Además: el indicador se pone `Desconectado` **sin condición** (antes solo si el estado
no era Traduciendo ni Reproduciendo, y por eso una caída a mitad de frase dejaba la
app trabada sin salida); la espera crece de a 1 s hasta un tope de 5 s y **no tiene
límite de intentos**; y el contador se reinicia en `BackendEvent.Connected`.

**Un comando nuevo en el puente**, aditivo como todos (`BRG-8`): `SignAICommands.STOP`
→ `window.SignAI.stop()`, que corta lo que esté sonando y vacía la cola. Vaciar la
cola además de parar es a propósito: si quedara, el próximo `appendQueue` arrancaría
en `queueIdx + 1` y saltaría los ítems de en medio.

**Medido en el Redmi Note 9S**, con el backend falso cerrando la sesión a propósito:

| Escenario | Resultado |
|---|---|
| Cae la conexión con la frase en vuelo | `sesión perdida: interrumpiendo=false cancelando=true`. El texto vuelve al campo **editable** y **Traducir queda habilitado**: la app ya no queda trabada |
| Cae la conexión con el avatar representando | `interrumpiendo=true`, y en el JS `stop(): cola cortada (sonando=true)`. Avatar a reposo, estado `Inactivo` |
| Reconexión | Vuelve sola, con la espera creciente, y el estado sale de `Desconectado` |
| Backend inalcanzable desde el arranque | `Desconectado` con el aviso, sin bloquear nada |
| Dictado → frase cerrada | Etiqueta **"Texto listo"**, persistente, con el texto editable esperando Traducir |
| Se borra el texto | Vuelve a "Listo para traducir" |
| TR prendido | "Escuchando..." |
| TR apagado con la frase representada | "Listo para traducir" + Repetir, **no** "Texto listo" |
| `error` del backend | El mensaje sale y Traducir vuelve a estar disponible; a los 3 s el estado es `Inactivo` |

### ✅ El indicador: el nombre del estado, el color, y un 25 % más grande

Cambio pedido por el owner el 2026-10-17, después de ver los textos que la app se
invaba. Tres cosas, y las tres están medidas en el Redmi Note 9S:

**1. En pantalla va el nombre del estado, y nada más.** Los siete:

| Estado | Texto en pantalla | Color del punto |
|---|---|---|
| Inactivo | "Inactivo" | Azul |
| Grabando | "Grabando" | Ámbar |
| Texto listo | "Texto listo" | Azul |
| Procesando | "Procesando" | Ámbar |
| Reproduciendo | "Reproduciendo" | Azul |
| Error | "Error" | Rojo |
| Desconectado | "Desconectado" | Rojo |

**2. El color del punto dice en qué estado está la app: tres colores, no siete.**
Azul (`strong`, el de marca) para el camino normal; ámbar (`live`) para lo que hay
que esperar; rojo (`error`) para lo que falló. Los tres son tokens que ya estaban
en `ui/designsystem/Color.kt`: no se inventó ningún color.

**3. Un 25 % más grande**, punto y texto: de 7 dp a 8,75 dp, y de 12/16 sp a 15/20 sp.

Lo que **salió** del indicador: las glosas que se mostraban mientras el avatar
representaba ("glosa-uno · glosa-dos"), y todos los avisos de 3 s ("Ya está
representado.", "No se escuchó nada.", "Sin conexión: no puedo escuchar."). Todos
siguen existiendo, pero en el **log** (`SignAiState`) y, si la depuración de Ajustes
→ Avanzado está prendida, también en pantalla. El único texto propio que queda en
el indicador es **"Seña no disponible"**, que es el único que la tabla prescribe
(fila *Reproduciendo*) y que va como texto **alternativo** de *Reproduciendo*.

**Lo que no se pudo medir:** la reconexión en sí con la espera de 1 s / 2 s / 3 s… se
vio que reconecta y que el contador se reinicia, pero no se cronometró la serie
completa: para eso hay que dejar el backend caído y volverlo a levantar, y con
`adb reverse` la caída no se propaga (el túnel deja la conexión medio abierta y OkHttp,
que no tiene timeout de lectura, no se entera). Por eso las pruebas de caída se
hicieron **pidiéndole al backend que cierre la sesión**, no matándolo.

---

### ✅ A14 — Que la app no se pueda quedar congelada

**Decidido el 2026-10-17, hecho y verificado en el Redmi Note 9S.** Esta sesión no llegó a tocar código:
el owner pidió primero la investigación, y lo que salió fue una auditoría de los
veinte casos en que la app espera algo.

**Cómo se encontró.** El owner reportó que la app se quedaba en *Procesando* sin
cambiar. Resultó ser real y reproducible en el Redmi Note 9S, y **no** era el bug que
A5/A7 ya habían arreglado (una frase sin ninguna seña representable). Este es otro: la
app no tiene **ninguna** red de seguridad para una frase que mandó y que nadie
contestó. Con el micrófono apagado, el modo en vivo y el "Repetir" los tres casos se
reprodujeron; en el peor de ellos quedaron **los cinco controles del panel deshabilitados**
y la app no se movió ni reiniciando el backend, ni apagando y prendiendo el Wi-Fi.

**Los seis cambios, en orden:**

| # | Cambio | Dónde | Cierra |
|---|---|---|---|
| 1 | Reloj de silencio para la frase enviada, rearmado con cada frame | `ui/state/MainScreenState.kt` | El congelamiento sin respuesta; el "Repetir" sin salida |
| 2 | `pingInterval` en el WebSocket, para que la caída silenciosa se vuelva *Desconectado* | `data/SignAiWebSocketClient.kt` | La caída que no cierra el socket |
| 3 | Apagar el micrófono o el TR cancela la frase en vuelo | `MainScreenState.stopCapture` / `stopDictation` | El botón de parar que no para |
| 4 | Una seña que no carga se saltea en vez de cortar la frase | `assets/web/app.js` (`loadQueueItem`) + un mensaje aditivo al puente | La frase cortada en silencio |
| 5 | `onReceivedError` y `onRenderProcessGone` en el WebView | `ui/webview/AvatarWebView.kt` | El estado *Reproduciendo* con el muñeco quieto |
| 6 | El `done` espera al trabajo de cola en vuelo | `MainScreenState.finishTurn` | La carrera entre el fin y la cola |

1 y 2 son el mismo arreglo y van juntos.

**Cómo se verificaron.** Todos contra el backend falso de `/tmp/opencode/fake-backend.py`,
que se descifra con una palabra clave en la frase mandada. Cada caso, lo medido:

| # | Qué se probó | Cómo | Resultado medido |
|---|---|---|---|
| 1 | El congelamiento sin respuesta | Frase `SIN-RESPUESTA`: el backend no manda ni una oración ni un `done` | El reloj disparó a los **15,001 ms** exactos (`22:22:37,934` → `22:22:52,935`), salió *Error* con el punto rojo y a los 3 s *Inactivo* con el panel libre. La frase normal que sale antes no se rompió: 2 oraciones, 3 glosas, `envío→primera seña` 155 ms |
| 2 | La caída silenciosa | Backend falso congelado con `SIGSTOP`: el socket queda abierto y nadie responde | Detectada a los ~40 s —un intervalo de ping más la espera del pong— con `sent ping but didn't receive pong within 20000ms`, y la app pasó a *Desconectado* sola. Antes de este cambio eso no se detectaba nunca |
| 3 | Apagar el TR con frase en vuelo | TR prendido, el `final` simulado manda la frase, el backend no contesta, TR apagado a los 5 s | `frase cancelada por el apagado del micrófono (seña en curso=false)`: el estado pasó de *Procesando* a **Texto listo** con la frase de vuelta en el campo editable y Traducir habilitado. A los 18 s seguía en *Texto listo*, sin falso *Error* |
| 4 | Una seña que no carga | El backend sirve un `.glb` que es basura en `prueba/rota.glb`, dentro de una frase de tres | `No se pudo cargar la seña /signs/GLOSA_ROTA_b5245e.glb` → `seña 1 ... se saltea` → **la seña 2 se representó igual** (`signStart ... seña 2`), y en pantalla salió "Seña no disponible". El turno cerró con las 3 oraciones |
| 6 | El `done` antes que la cola | El backend sirve un `.glb` **bueno** con 2 s de retraso en la primera oración, y manda el `done` enseguida | `el fin de la frase llegó con la cola todavía armándose: se espera`, y el turno se cerró 1,8 s después, cuando la cola ya estaba entregada. Antes decía "esa frase no produjo ninguna seña" sobre una frase que sí se iba a representar |
| 5 | El WebView avisa si muere | **Solo leído, no provocado.** Hace falta que el navegador mate su proceso de render y no hay forma de pedirlo por `adb` | El código está y compila; el comportamiento en pantalla no se comprobó. Lo que sí se decidió: si el proceso de render muere, la app **se cierra** y el launcher la vuelve a levantar (`onRenderProcessGone` devuelve `false`), porque un WebView sin proceso de render no vuelve a renderizar nunca más |

**Un defecto que encontró la propia verificación del caso 6** y que no estaba en la
lista: dos oraciones del mismo turno que llegan juntas salían las dos como "la
primera" de la cola, y la segunda **pisaba** la que la primera ya estaba
representando; con la corrección de orden, la entrega quedaba en orden de descarga
y no de oración. Las dos cosas van en el mismo arreglo (`STA-29`).

**Un defecto propio que encontró la verificación del caso 3**: el reloj de silencio
declaraba un fallo 15 s después de una cancelación —"El servidor no respondió" para
una frase que el usuario había cancelado—. Ahora, cuando no hay frase en vuelo y lo
que se esperaba era el `done` de una cancelada, lo que hace es **olvidar el descarte**
sin mostrar nada. El caso 4 (una seña que no carga) necesita que el backend
mande una glosa cuya animación no baja; el 5 necesita que el navegador mate el proceso
del WebView; el 6 necesita que la resolución de una oración tarde más que el `done`.

---

## 9. Diferencias y problemas que aparecieron

No son ítems de la tabla: son cosas que se encontraron trabajando en ella. Se
anotan acá para que no se pierdan, y se marcan las que necesitan una decisión.

**Lo que NO va acá** (decisión del owner, 2026-10-17): las **mediciones**. Los
números de la corrida con el backend real son del **plan de pruebas** de
`../signall-docs`, no de este contraste. Y las **18 señas que alguna vez faltaron**
ya están en el archivo único desde el 2026-10-22: lo que queda es validar su
contenido con el owner y el director, no exportarlas. La app entra sola con
cualquier seña nueva que exista.

| # | Qué | Dónde | Qué hay que hacer |
|---|---|---|---|
| 1 | `shouldShowRequestPermissionRationale()` **no sirve** para saber si ya se preguntó: devuelve `false` tanto si nunca se preguntó como si ya no se va a preguntar. En MIUI **una sola negativa** deja el permiso en `USER_FIXED`, así que la app nunca avisaba que el micrófono quedó bloqueado — justo lo que `RF-UI-04` pide | `data/MicPermissionPrompt.kt` | **Resuelto en A2** con un flag propio. Se documentó en `openspec/specs/ui/spec.md` `CTL-14` |
| 2 | El código tenía `MaxReconnectAttempts = 5`, que contradecía la decisión de §7 (reconexión **sin** límite) | `MainScreenState.kt`, la espera de reconexión | **Resuelto en A4**: ya no existe ese tope. Hoy la espera crece un segundo por intento y se detiene en 5 s, indefinidamente, y el contador que muestra la interfaz se reinicia en cada conexión nueva |
| 3 | El reloj del turno (`turnStartedAt`) **se reinicia** en `resumeListeningIfMicOn`, y el `done` puede llegar **antes** de que la escena se mueva: una frase habría medido desde el reloj de la siguiente, o no habría medido | `MainScreenState.kt` | **Resuelto en A13** con `turnSentAt`, que solo se pone al mandar una frase |
| 4 | Hay **dos** casos de "glosa sin seña" y el SRS habla de uno solo: la glosa que **no está en el catálogo** (`main.tex:200-202` "cuando una glosa no existe en el catálogo") y la glosa que el catálogo anuncia pero **cuyo clip no está en el APK** (era el caso de `0032`–`0049` antes del 2026-10-22; **hoy no hay ninguna**, §5 de este documento). Con la seña de prueba apagada las dos se saltaban en silencio | `MainScreenState.kt` | **Resuelto en A5**: el aviso sale en los dos casos, porque las dos terminan en la misma rama (`sin seña ni fallback`). Decirlo solo para la primera habría sido mentir en silencio al revés: el catálogo **sí** anuncia la glosa, lo que no hay es el clip. Hoy no queda ninguna en ese caso; el aviso sigue para una glosa futura |
| 5 | La clave `duracion_firmado` del `snapshot()` de `SignAiMetrics` seguía usando la palabra que `AGENTS.md` retiró el 2026-10-16 | `SignAiMetrics.kt` | **Resuelto en A13**: ahora es `duracion_representada`. Era una cadena de logcat, no un nombre de código |
| 6 | **No hay dominio del backend.** Ni el SRS (`main.tex:1088`, `:1100`) ni `../signall-docs` nombran uno, así que no había con qué armar el `wss://` | `app/build.gradle.kts` | En A3 el default de release quedó en `wss://backend.invalid/ws/translate`: un host que por construcción no resuelve (`.invalid`, RFC 2606). Es deliberado: un APK sin configurar habla con nadie en vez de con el host de otra persona, y el fallo es ruidoso. **Decisión del owner (2026-10-17): no hay dominio y no se va a tener, porque es un prototipo.** Queda así a propósito y no se vuelve a abrir |
| 7 | **A1 no es separable de A7.** El SRS solo pide que "el indicador pase a *Texto listo*" (`main.tex:735`), y el indicador de la app es la etiqueta de estado del escenario. Convertir `dictationReadyToSend` en el estado `TextoListo` —que es justamente lo que hace A7— **deja A1 hecho**: la etiqueta pasa a decir "Texto listo" mientras el texto espera, en vez de un aviso de 3 s | `SignAiBottomBar.kt`, `MainScreenState.kt` | Hacer A1 con un mecanismo visual aparte (un ícono, un anillo) sería inventar un requisito que el SRS no pide, y además **chocaría con una decisión ya tomada**: el bloque de comentario de `SignAiBottomBar.kt` (el del ícono del micrófono) documenta que el ícono **no** cambia en el tercer estado porque se probó en el dispositivo y el botón dejaba de leerse como micrófono. **Resuelto con A7**: se conservó esa decisión y la señal del tercer estado pasó a ser el nombre del estado (*Texto listo*), que es lo que pedía el SRS |
| 8 | El comentario de `SignAiBottomBar.kt` (el del ícono del micrófono) decía que la señal del tercer estado es el **placeholder del campo**. No es así: el placeholder solo se ve cuando el campo está vacío, y en el tercer estado el campo **tiene** la frase. El propio estado lo dice en otro comentario (`MainScreenState.stateForUi`: "con la frase escrita arriba, un placeholder no se ve nunca, que es justo cuando hace falta"). O sea: la app creía que tenía una señal persistente y no la tenía | `SignAiBottomBar.kt` | **Resuelto en A7**: la señal es el estado `TextoListo`, y el comentario que daba el placeholder por bueno está corregido |
| 9 | **El SRS hay que ajustarlo en dos puntos**, por decisiones que el owner tomó el 2026-10-17. No es un error del documento: son requisitos que la app cumple distinto y el documento tiene que reflejarlo | Overleaf, `main.tex` | **(a)** `main.tex:737` dice que en modo TR el micrófono **permanece encendido**; con la caída de conexión la app lo **apaga** y avisa. **(b)** La fila *Desconectado* (`main.tex:754-765`) no dice qué pasa con el micrófono. El texto exacto que hay que pegar está en `openspec/sessions/2026-10-17-estados-y-desconexion.md` |
| 10 | `TextoListo` se **deduce** en vez de asignarse, y por qué | `MainScreenState.stateForUi` | `dictationReadyToSend` depende de tres cosas a la vez (micrófono apagado, campo con texto, nada retenido) y cualquiera la deshace. Si fuera un estado asignado habría que acordarse de volver a ponerlo en cada transición, y cuando se olvidaba tapaba `Desconectado`. Como estado asignado se dejaba pegado en "Texto listo" con la conexión caída, que es un aviso peor que ninguno |
| 11 | Dictar "X", pasar al TR y decir "X" otra vez dejaba el mismo texto **en los dos lados**: el avatar lo representaba y, al apagar el TR, `stopCapture` lo devolvía al campo como pendiente. Con A1 eso se veía como "Texto listo" sobre una frase que ya se había representado, con Traducir pidiendo mandarla otra vez | `MainScreenState.sendLivePhrase` | **Resuelto**: si lo que sale en vivo es justamente lo que estaba esperando confirmación, deja de estar pendiente. El pendiente sobrevive cuando es **otro** texto, que ese sí es del usuario y no se mandó |
| 12 | La tabla `tab:estados-ui` **no prescribe textos literales**: su columna se llama "qué muestra la pantalla" y describe. El único mensaje literal de toda la tabla es ``Seña no disponible`` (fila *Reproduciendo*) | Overleaf, `main.tex:747` | **Resuelto el 2026-10-17 por decisión del owner**: en el indicador va el **nombre del estado** y nada más —Inactivo, Grabando, Texto listo, Procesando, Reproduciendo, Error, Desconectado—. Los avisos de 3 s y las glosas que antes ocupaban el indicador **salen solo con la depuración prendida** (Ajustes → Avanzado) y en el resto del tiempo quedan en el log (`SignAiState`). El punto del indicador es ahora la señal de color (rojo para lo que falla, ámbar para lo que espera, azul para lo normal) |
| 13 | Una frase **sin ninguna seña representable** dejaba la app en "Procesando" para siempre, con el Traducir deshabilitado | `MainScreenState.appendQueue`, `finishTurn` | **Resuelto**: `queueStarted` solo se prende cuando la escena recibió la cola de verdad, y el `done` del backend cierra el turno. De paso se arregló el caso peor: la primera oración vacía y la segunda con seña entraban por `appendQueue` sin que nadie hubiera llamado `setQueue` ni `play()`, y la frase no se representaba |
| 14 | La app decía "Listo para traducir", "Procesando...", "Reproduciendo señas..." y "Sin conexión con el servidor", que **no están en el SRS** | `PlaybackState` | Son palabras de la app, de antes de esta sesión. Con la decisión del owner del 2026-10-17 el indicador dice el nombre del estado. Las textos viejos quedan en el historial de git |
| 15 | **La app no tenía ninguna red de seguridad para una frase que mandó.** Con el micrófono apagado, en modo en vivo o con el "Repetir", si nadie contestaba el indicador se quedaba en *Procesando* para siempre; en el "Repetir" además quedaban los cinco controles del panel deshabilitados, sin salida. La red que sí existía (15 s) cubría solo la transcripción | `MainScreenState.armTranscriptionTimeout`, `stopCapture`, `finishTurn` | **Resuelto por decisión del owner** (§7, A14): reloj de silencio de 15 s a *Error*, apagar el micrófono cancela la frase en vuelo, y el `ping` para que la caída silenciosa sea *Desconectado*. Los tres textos para el SRS están propuestos en §6 #5, #6 y #8 |
| 16 | **Caída silenciosa de la conexión.** Sin `readTimeout` ni `ping`, una conexión que muere sin cerrar el socket (túnel `adb reverse`, NAT del operador, cambio de red) no se detectaba: la app seguía creyendo conectada y una frase se quedaba esperando para siempre | `data/SignAiWebSocketClient.kt`, el `OkHttpClient.Builder` | **Resuelto por A14**: `pingInterval(20 s)`. Es lo que convierte el congelamiento en un estado visible. Sin ese cambio, el reloj de 15 s al menos sacaría a la app, pero mostraría *Error* donde lo honesto es *Desconectado* |
| 17 | **Una seña que no se puede cargar cortaba la frase entera en silencio.** El `catch` de `loadQueueItem` paraba todo y avisaba "terminado", así que las demás señas de la frase nunca se representaban y nada lo decía | `assets/web/app.js`, `loadQueueItem` | **Resuelto por decisión del owner** (§7, A14): se saltea esa seña, se sigue con las demás y el indicador muestra "Seña no disponible". Texto propuesto para el SRS en §6 #7 |
| 18 | **El WebView no avisaba si moría.** Sin `onReceivedError` ni `onRenderProcessGone`, si el navegador mataba el proceso del WebView el estado se quedaba en *Reproduciendo* con el avatar quieto | `ui/webview/AvatarWebView.kt` | **Resuelto por A14**, y con los dos caminos decididos por separado (2026-10-17): si la **página** no cargó, aviso y la app sigue; si el **proceso de render** murió, se devuelve `false` y el sistema reinicia la app, porque un WebView sin proceso de render no renderiza nunca más y dejarla viva mostraría un canvas negro hasta que el usuario la cerrara a mano. **Y no se va a probar** (decisión del owner, 2026-10-17): no hay forma de pedirle al navegador que mate su proceso, y forzarlo a mano no probaría el caso real |
| 19 | **El `done` podía cerrar el turno antes de que la cola estuviera lista.** `finishTurn` cerraba el turno mientras `appendQueue` todavía resolvía los assets, con dos efectos: la interfaz decía *Inactivo* un instante antes de *Reproduciendo*, y en modo en vivo la frase de reserva podía salir antes de que la anterior entregara su cola | `MainScreenState.finishTurn`, `appendQueue` | **Resuelto por A14**: el `done` espera al trabajo pendiente, y el arreglo de la fila 20 vino con él. **Medido en el device**: con una seña que tarda 2 s en bajarse, el log da `el fin de la frase llegó con la cola todavía armándose: se espera` y el turno cierra 1,8 s después, cuando la cola ya está entregada |
| 20 | **Dos oraciones del mismo turno salían las dos como "la primera" de la cola.** `appendQueue` decidía `first = !queueStarted` **antes** de resolver los assets, así que si una oración tardaba más en resolver que la siguiente, las dos Deliver por `setQueue` + `play()` y **la segunda pisaba la cola** que la primera ya estaba representando: la frase se cortaba a mitad, en silencio, y el turno se cerraba sobre una cola que ya no era la de la frase | `ui/state/MainScreenState.kt`, `appendQueue` | **Resuelto por A14** (`STA-29`): la entrega se serializa con `queueDeliveryGate` y `first` se lee después de resolver. Medido en el device: con una seña que tarda 2 s en bajarse, la frase se representaba al revés o se cortaba; después, en orden |
| 21 | **El `done` no lleva con qué identificar a qué frase pertenece.** Cancelar una frase es local —el protocolo no tiene un frame para cancelarla— así que la app tiene que **tragarse** el `done` de la frase cancelada a ciegas. Si en el medio salió otra frase y el `done` viejo se perdió, se descarta el correcto y el turno nuevo se queda esperando hasta el reloj de 15 s | `ui/state/MainScreenState.kt`, `StaleTurn` | **Deuda de protocolo, no de la app.** Lo que la app hace está anotado en `openspec/specs/ui/spec.md` `STA-25` y el cambio que lo arreglaría de verdad —un id de turno en `segment`/`done`, o un frame `cancel`— está propuesto en el repo del backend: `../signall-backend/docs/historia/CONTRASTE-BACKENDvsDOCUMENTOS.md`. **Requiere tocar el backend, así que no se toca sin que el owner lo pida** |