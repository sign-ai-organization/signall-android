# Contraste App ↔ Documentos — qué hay que cambiar en la app

**Fecha:** 2026-10-17
**App:** `sign-ai-android`, rama `flujo-oraciones`, commit `0b3c7f8`
**Fuente de verdad:** el **SRS de Overleaf**, proyecto `6a122a19bb9a5dd9d390ba3d`,
archivo `main.tex`. Revisado el 2026-10-17.
**Segundo documento:** `../sign-ai-docs` (`v0.15` y `modificaciones/requerimientos.md`),
usado de apoyo cuando el SRS no habla de algo.

> **La fuente de verdad es el SRS.** Cuando el SRS y `../sign-ai-docs` no
> coincidan, manda el SRS y el desajuste se reporta al owner. Los números de
> línea de abajo son del `main.tex` del 2026-10-17: si el SRS se vuelve a editar,
> verificá que sigan apuntando a lo mismo.

Este documento reemplaza a `CONTRASTE-DOCUMENTOS.md` (2026-10-16) y absorbió el
detalle de `CONTRASTE-SRS-2026-10-17.md`. Su único propósito es que otra sesión
pueda agarrar un ítem de la tabla y hacerlo sin tener que releer los tres
documentos.

---

## 1. Lo que hay que hacer en la app

Siete ítems. En todos, **el SRS ya está escrito y la app es la que está atrás.**
Ninguno requiere inventar el requisito.

| ID | Qué hay que cambiar | Dónde en la app | Qué lo pide el SRS | Estado |
|---|---|---|---|---|
| **A1** | Que el tercer estado del micrófono **se vea**, no solo se describa | `ui/screens/bottombar/SignAiBottomBar.kt`, `ui/state/MainScreenState.kt` | Tabla de estados, filas *Texto listo* y *Grabando* (`main.tex:736-747`) | ⬜ Pendiente |
| **A2** | Pedir el permiso de micrófono **al abrir la app**, no al tocar el micrófono | `ui/MainScreen.kt:78-103` | §Interfaz con el Usuario (`main.tex:837`) | ✅ Hecho (`1c85ddd`) |
| **A3** | **`wss://` por default**, con el `ws://` de desarrollo como bypass por build config | `data/SignAiBackendConfig.kt:35`, `res/xml/network_security_config.xml`, `app/build.gradle.kts:15` | §Interfaces de Comunicación (`main.tex:1095`, `:1100`) | ✅ Hecho · **falta el dominio** (§9 #6) |
| **A4** | Destrabar la frase que quedó a medio traducir; reconexión con espera creciente y contador que se reinicia | `ui/state/MainScreenState.kt:538-544`, `:695`, `:909-915` | Tabla de estados, fila *Desconectado* (`main.tex:761-772`) | ⬜ Pendiente · **decisión abierta** (§7) |
| **A5** | Avisar **"Seña no disponible" glosa por glosa**, hoy se salta en silencio | `ui/state/MainScreenState.kt:604-648`, `:1069` | §Alcance (`main.tex:201`) y tabla, fila *Reproduciendo* (`main.tex:753`) | ✅ Hecho |
| **A7** | De **9 estados a 7** | `ui/state/PlaybackState.kt`, `ui/state/MainScreenState.kt` | Tabla de estados, completa (`main.tex:726-773`) | ⬜ Pendiente · depende de A4 |
| **A13** | Armar la **métrica de latencia de punta a punta** | `ui/state/SignAiMetrics.kt`, `MainScreenState.kt:329` y `:188-195` | §Aspectos de medición (`main.tex:848-852`) | ✅ Hecho (instrumento; falta medir con el backend real) |

**Orden sugerido.** A2 es el más chico (un cambio de comportamiento) y sirve para
verificar el ciclo completo compilar-instalar-probar. A13 es el más mecánico
puro y no toca interfaz. **A4 y A7 son el mismo trabajo**: los dos estados que le
faltan a la app (*Error* y *Desconectado*) son los dos que el SRS define.

**Orden en que se están haciendo** (de menor a mayor complejidad, 2026-10-17):
A2 → A13 → A3 → A5 → A1 → **A4 + A7**. El registro de cada uno, con lo que se
midió y lo que quedó sin poder probar, está en **§8**.

---

## 2. Detalle de cada ítem

### A1 — El tercer estado del micrófono no se ve

**Qué dice el SRS.** La tabla de estados define *Texto listo* como estado propio
(`main.tex:746`): "El texto dictado o escrito manualmente está en la barra, puede
editarse y espera a que el usuario pulse Traducir." Y la fila *Grabando*
(`main.tex:738`) dice que en modo dictado, al cerrarse la frase, "el indicador
pasa a *Texto listo*".

**Qué hace la app.** El estado existe (`dictationReadyToSend`) pero no se ve: el
ícono del micrófono no cambia. Lo único que cambia es la descripción accesible
y un aviso de 3 s en el hint del escenario ("Texto listo. Toca Traducir para
enviarlo."). El envío lo dispara el botón Traducir.

**Qué hacer.** Que el ícono o el indicador de estados cambie visualmente en ese
tercer estado. El estado y su descripción ya están; falta la señal visible.

---

### A2 — El permiso de micrófono se pide tarde

**Qué dice el SRS** (`main.tex:837`):

> La primera vez que se abre la aplicación, esta solicita el permiso de
> micrófono. Si el usuario niega el permiso del micrófono la aplicación solo
> trabaja con el texto ingresado manualmente por el usuario.

**Qué hace la app.** `RECORD_AUDIO` se pide la primera vez que se toca cualquiera
de los dos interruptores, guardando la acción para ejecutarla si se concede
(`ui/MainScreen.kt:89-100`). `INTERNET` no se pide nunca porque no es permiso de
runtime.

**Qué hacer.** Pedir el permiso al abrir la pantalla principal, una sola vez,
conservando el aviso de 3 s cuando se deniega (`MainScreenState.kt:1422-1426`) y
el comportamiento de "seguir funcionando solo con texto".

---

### A3 — El enlace con el backend va sin cifrar

**Qué dice el SRS.** Doble: "Toda comunicación que sale de un equipo viaja
cifrada mediante TLS" (`main.tex:1095`) y "Entre la aplicación y el servidor se
mantiene una conexión **WebSocket segura (WSS)**, bidireccional y persistente"
(`main.tex:1100`).

**Qué hace la app.** El WebSocket abre en `ws://10.0.2.2:8080/ws/translate`
(`data/SignAiBackendConfig.kt:35`) y el manifiesto permite tráfico en claro de
forma global. La URL se inyecta por build config (`app/build.gradle.kts:15`) y el
código ya sabe derivar `https://` de una URL `wss://` (`SignAiBackendConfig.kt:29`).

**Qué hacer.** Default a `wss://`, con el `ws://` de desarrollo como bypass por
propiedad de build, y `network_security_config.xml` alineado para que el cleartext
quede limitado al caso de desarrollo y no sea global.

---

### A4 — Si la conexión se cae traduciendo, la app queda trabada

**Qué dice el SRS** (`main.tex:761-772`, fila *Desconectado*, texto cerrado por
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

**Qué dice el SRS.** En §Alcance (`main.tex:201`): "Cuando una glosa no existe en
el catálogo, el sistema informa que la seña no está disponible en lugar de enviar
una instrucción inválida al avatar." Y en la tabla de estados, dentro de
*Reproduciendo* (`main.tex:753`): "Si alguna palabra no está en el catálogo, la
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
(`main.tex:726-773`): **Inactivo, Grabando, Texto listo, Procesando,
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
   `Desconectado`. Exactamente los siete de `main.tex:726-773`.
2. **La distinción entre dictado y TR vive en la etiqueta, no en el estado.** Un
   solo estado `Grabando` cuya etiqueta sea "Dictando..." o "Escuchando..."
   según el modo. Así el enum tiene siete exactos —que es lo que pide el SRS— y
   el usuario sigue viendo en qué modo está. Lo mismo con `Procesando`: para el
   usuario, esperar la transcripción y esperar las glosas son la misma espera.
3. **`Done` no se borra, se mueve.** Hoy ese estado no solo decora:
   `doneVisible` lo usa para mostrar el aviso de reproducción completada
   (`MainScreen.kt:199`). Si se elimina, se pierde ese aviso. Hay que pasarlo a
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

**Qué dice el SRS.** §Aspectos de medición (`main.tex:848-852`) define tres
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
reloj del turno que arranca al mandar la frase (`MainScreenState.kt:329`) y el
`signStart` que reporta la escena (`:188-195`). Solo falta restarlos en el momento
correcto y emitirlo con el mismo formato que las otras.

**Lo que no es código.** La tercera medida del SRS, *legibilidad del avatar*
evaluada con ISO 9241-11 (`main.tex:852`), es un estudio con usuarios. No se
resuelve tocando la app.

---

## 3. Ítems que necesitan una decisión antes de tocar código

| ID | La decisión | Por qué no se puede asumir |
|---|---|---|
| **A6** | ¿Los marcadores no manuales se hacen en la app o se sacan del SRS? | El SRS los pide (`main.tex:205`, y §Hardware). La app recibe el tipo de frase y no lo usa. El 2026-10-16 estaba "en pausa" **porque el documento no lo pedía**; ahora sí lo pide, así que la premisa cambió |
| **A15** | ¿`SignAssetRepository` se borra o se deja? | El SRS cerró el canal HTTP a `/health` (`main.tex:1129`) y dijo que las animaciones van dentro del APK (`:1121`). `SignAssetRepository` nunca se ejecuta con el catálogo actual, pero borrar código no es lo mismo que cumplir un requisito |
| **B4/B5** | ¿Quién exporta las 18 señas que faltan? | Ver §5: el SRS dice 50 y el objetivo es 50. No es una corrección de documento, es trabajo de las señas |

---

## 4. Cosas que no hay que tocar en la app

El SRS quedó a favor de lo que ya hace la app, o ya no hay diferencia. No
abrirlas de nuevo.

| ID | Por qué está cerrado |
|---|---|
| **A8** | El SRS ya dice Three.js 0.169.0 en WebView, no Filament. El `.drawio` de C4-2 que decía Filament está en `../sign-ai-docs` y lo edita el owner |
| **A9** | El SRS dice que en modo TR el micrófono **permanece encendido** (`main.tex:709`). La app hace eso |
| **A10** | El tope de 8 s pasó a ser búfer del **servidor** (`main.tex:1184`). La app no tiene temporizador y no debe tener |
| **A11** | El SRS documenta la interfaz real: paneles, hoja de historial, Acerca de, velocidad de señas (`main.tex:810`), modo oscuro/claro, los cinco ajustes de accesibilidad y el bloque Avanzado. Todo existe |
| **A12** | Las cinco opciones de accesibilidad ya están, incluida la nota de que "reducir animaciones" hace el corte seco entre señas. `Settings.kt:17-20`, `Settings.kt:26` |
| **A14** | El SRS no habla de instrumentación. No molesta ni prohíbe: la app mide sola con `SignAiMetrics`, `SignAiNet`, `SignAIWeb`, `SignAiState` |
| **B1** | El SRS dice que en dictado el texto queda en la barra para revisarlo y se manda con Traducir. La app hace eso |
| **B2** | El SRS dice "el texto pendiente no se pierde". La app lo conserva |
| **B3** | El SRS dice "Mientras el micrófono está activo, la barra solo muestra el texto; al apagarse, el texto puede editarse". La app hace exactamente eso |
| **B6** | El SRS pide "transiciones fluidas mediante interpolación" y documenta el corte seco con "reducir animaciones". La fundición de 100 ms se mantiene |
| **B7** | Las animaciones van dentro del APK. El SRS dice 50 y el objetivo es 50 (ver §5) |
| **B8** | El SRS describe dos botones fijos (micrófono y TR), nunca simultáneos, sin selector. La app hace eso |

---

## 5. Sobre el "50" del SRS

El SRS dice que las **50** animaciones van dentro del APK (`main.tex:937`,
`:1188`, y §Alcance). Hoy hay **32**.

**Decisión del owner (2026-10-17): el SRS no está equivocado.** El documento
describe lo que va a haber; el objetivo es llegar a 50. Exportar las 18 que
faltan (`0032`–`0049`) es trabajo de las señas, no una corrección del documento.

Consecuencia para la app: hoy esas 18 glosas llegan con `available: true` y una
ruta que no existe, y `SignsCatalog.isOwnAssetPath()` evita pedirlas por red para
no comerse un 404. Cuando se exporten, la app las resuelve sola, sin cambios.

---

## 6. Pendiente en los documentos (no es trabajo de la app)

Reportado al owner el 2026-10-17. **Nada de esto se toca desde acá.**

| # | Qué | Dónde |
|---|---|---|
| 1 | La §3 del SRS está vacía y conserva los subtítulos de una plantilla de juegos ("Funcionalidad 1: Jugar", "Registro y Autenticación", "Administración", "Consultas"). Con eso desaparecieron los IDs `RF-*` y `RNF-*` que usan `v0.15` y el plan de pruebas | Overleaf, `main.tex` §3 |
| 2 | El SRS dice "SignAI" 25 veces y "SignALL" ninguna. Delegado al compañero | Overleaf, todo el documento |
| 3 | Dos marcadores de trabajo sin completar: `[PENDIENTE: tamaño del APK con todos los archivos GLB.]` | Overleaf, `main.tex:931` y `:1154` |
| 4 | `../sign-ai-docs/modificaciones/requerimientos.md` punto 19 pide "límite de intentos" de reconexión. El owner decidió **sin** límite: espera creciente hasta 5 s, indefinida. Hay que corregir ese texto | `../sign-ai-docs` |

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
- **A4, el micrófono.** **Sin resolver.** `fail()` apaga el micrófono; hay que
  decidir si la desconexión respeta el modo TR o corta como cualquier error.
- **A7, los nombres de los estados.** El enum queda con **los siete nombres del
  SRS, en español**: `Inactivo`, `Grabando`, `TextoListo`, `Procesando`,
  `Reproduciendo`, `Error`, `Desconectado`. La distinción entre dictado y TR
  vive en la etiqueta, no en el estado. `Done` pasa a flag. `Connecting` se funde
  en `Desconectado`, aceptando que cada arranque muestre "Desconectado" mientras
  el socket abre. Mapa completo en §2.
- **B4/B5, el número de señas.** El SRS dice 50 y se queda en 50. Exportar las 18
  que faltan es trabajo de las señas.
- **§6, los huecos del SRS.** Fuera del alcance de la app: los arregla el owner o
  el compañero.

---

## 8. Registro de avance (2026-10-17)

Una entrada por ítem, en el orden en que se está haciendo: de menor a mayor
complejidad. Cada una dice **qué se midió** y **qué quedó sin poder probar**, que
es lo que otra sesión necesita para no dar algo por verificado sin serlo.

### ✅ A2 — Permiso de micrófono al abrir la app

Commit `1c85ddd`, instalado y verificado en el Redmi Note 9S (Android 11, MIUI).
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

---

## 9. Diferencias y problemas que aparecieron

No son ítems de la tabla: son cosas que se encontraron trabajando en ella. Se
anotan acá para que no se pierdan, y se marcan las que necesitan una decisión.

| # | Qué | Dónde | Qué hay que hacer |
|---|---|---|---|
| 1 | `shouldShowRequestPermissionRationale()` **no sirve** para saber si ya se preguntó: devuelve `false` tanto si nunca se preguntó como si ya no se va a preguntar. En MIUI **una sola negativa** deja el permiso en `USER_FIXED`, así que la app nunca avisaba que el micrófono quedó bloqueado — justo lo que `RF-UI-04` pide | `data/MicPermissionPrompt.kt` | **Resuelto en A2** con un flag propio. Se documentó en `openspec/specs/ui/spec.md` `CTL-14` |
| 2 | El código sigue con `MaxReconnectAttempts = 5`, que contradice la decisión de §7 (reconexión **sin** límite) | `MainScreenState.kt:50` | Se corrige en **A4**. No se tocó antes |
| 3 | El reloj del turno (`turnStartedAt`) **se reinicia** en `resumeListeningIfMicOn`, y el `done` puede llegar **antes** de que la escena se mueva: una frase habría medido desde el reloj de la siguiente, o no habría medido | `MainScreenState.kt` | **Resuelto en A13** con `turnSentAt`, que solo se pone al mandar una frase |
| 4 | Hay **dos** casos de "glosa sin seña" y el SRS habla de uno solo: la glosa que **no está en el catálogo** (`main.tex:201` "cuando una glosa no existe en el catálogo") y la glosa que el catálogo anuncia pero **sin animación exportada** (`0032`–`0049`, §5 de este documento). Con la seña de prueba apagada las dos se saltaban en silencio | `MainScreenState.kt` | **Resuelto en A5**: el aviso sale en los dos casos, porque las dos terminan en la misma rama (`sin seña ni fallback`). Decirlo solo para la primera habría sido mentir en silencio al revés: el catálogo **sí** anuncia las 18, lo que no hay es el clip. Cuando se exporten, desaparece solo el aviso de esas |
| 5 | La clave `duracion_firmado` del `snapshot()` de `SignAiMetrics` seguía usando la palabra que `AGENTS.md` retiró el 2026-10-16 | `SignAiMetrics.kt` | **Resuelto en A13**: ahora es `duracion_representada`. Era una cadena de logcat, no un nombre de código |
| 6 | **No hay dominio del backend.** Ni el SRS (`main.tex:1095`, `:1100`) ni `../sign-ai-docs` nombran uno, así que no había con qué armar el `wss://` | `app/build.gradle.kts` | En A3 el default de release quedó en `wss://backend.invalid/ws/translate`: un host que por construcción no resuelve (`.invalid`, RFC 2606). Es deliberado —un APK sin configurar habla con nadie en vez de con el host de otra persona, y el fallo es ruidoso— pero **es una decisión del owner**: hace falta el dominio real para que release sirva para algo |