# Contrastes App/Docs

**Fecha:** 2026-10-16
**App:** `sign-ai-android`, rama `flujo-oraciones`, commit `6e93987`
**Documentos:** `../sign-ai-docs`, rama `main`, commit `18457be`

Este documento contrasta lo que hace la aplicación con lo que dicen los
documentos del repositorio hermano. Se anotan **solo las diferencias**: lo que
coincide no se lista. Cada diferencia trae la referencia exacta de dónde está
lo que pide el documento y de dónde sale lo que hace la app, más la decisión
que se tomó el 2026-10-16.

**Archivos de los documentos citados**

| Sigla | Archivo |
|---|---|
| **v0.15** | `../sign-ai-docs/documentos/requerimientos/SignAI_Requerimientos_Consolidados_v0.15.md` |
| **modif.** | `../sign-ai-docs/modificaciones/requerimientos.md` |
| **alcance** | `../sign-ai-docs/plan-pruebas/00-bases/alcance.md` |
| **traz.** | `../sign-ai-docs/plan-pruebas/00-bases/trazabilidad.md` |
| **auditoría** | `../sign-ai-docs/auditoria-flujo-2026-10-15.md` |
| **diagramas** | `../sign-ai-docs/diagramas/draw.io/` (12 archivos `.drawio`) |

---

## 1. Resumen

| ID | Tema | Qué dice el documento | Qué hace la app | Decisión del 2026-10-16 |
|---|---|---|---|---|
| A1 | Tercer estado del micrófono | RF-UI-01: tres estados visuales, el tercero manda | El ícono no cambia; el aviso va al hint del escenario | **Se adapta la app al documento** |
| A2 | Cuándo se pide el micrófono | RF-UI-04: al abrir la app por primera vez | Se pide al tocar el interruptor por primera vez | **Se adapta la app al documento** |
| A3 | Cifrado del enlace | RF-INT-COM-03: todo cifrado, diagramas `[Android, WSS]` | `ws://` en claro | **Se adapta la app al documento**, sin dañar el funcionamiento (bypass) |
| A4 | Conexión que se cae traduciendo | RNF-REL-02 + modif. #19: queda lista para otra frase; reconexión con tope de intentos y contador que se reinicia | El turno queda en vuelo para siempre; el contador nunca se reinicia | **Se adapta la app al documento** |
| A5 | "Seña no disponible" | RF-CAT-03: mensaje por cada glosa ausente | La glosa se salta en silencio; el mensaje solo si la oración entera no produjo nada | **Se adapta la app al documento** |
| A6 | Marcadores no manuales | RF-AVT-03: expresión facial según el tipo de frase | Nada se anima en código; la cara viene horneada en el clip | **En pausa**, no se toca por ahora |
| A7 | Estados de la interfaz | Diagrama 7.4: seis estados, incluido "Error" | Nueve etiquetas y ningún estado de error | **Se adapta la app al documento**: seis estados |
| A8 | Motor 3D | Diagrama C4-2: "avatar 3D con Filament" | WebView con Three.js | El diagrama está desactualizado |
| A9 | Microéfono en en vivo | traz.: "en ambos modos el micrófono se apaga" | En en vivo el micrófono queda prendido | El documento está desactualizado (opción A de la auditoría §7) |
| A10 | Tope de 8 s de habla | traz. §2.3 y stt/general.md: la frase cierra a los 8 s | No hay tope en la app | **Se deja el comportamiento de la app** |
| A11 | Inventario de pantallas | alcance §1.1: pantalla, botón de micrófono, texto e historial | Hay paneles de conversación y de ajustes, hoja de historial, Acerca de, Repetir, hint y splash | **La interfaz queda como está**, ya aprobada |
| A12 | Ajustes de accesibilidad | No existen como requisito | Tamaño de interfaz, negrita, contraste, texto más grande, reducir animaciones | **Se dejan en la app** |
| A13 | Latencia de punta a punta | RNF-DES-01: un tiempo total desde la confirmación del usuario | Se miden tres trozos sueltos, sin total | **Se adapta la app al documento**: que exista el total. Los datos extra no sobran |
| A14 | Instrumentación en el device | El plan mide en el backend y el STT | La app instrumenta sola (`SignAiMetrics`, `SignAiNet`, `SignAIWeb`, `SignAiState`) | Sin problema: que instrumente sola |
| A15 | Canal HTTP | RF-INT-COM-02 + modif. #15: solo `/health` | Además existe `SignAssetRepository` para assets remotos | **Queda pendiente**, depende de si habrá assets remotos (trabajo de Samuel con las señas) |
| B1 | Confirmación antes de enviar | RF-STT-03: no se manda sin que el usuario confirme | En dictado el texto espera y lo manda Traducir | **La app está bien** |
| B2 | Conservación del texto | RNF-REL-04: no implementado | Un fallo de envío conserva el texto | El documento quedó atrás |
| B3 | Edición de la transcripción | RF-UI-02: parcial, el campo se bloquea | El campo solo se bloquea con el micrófono prendido | El documento quedó atrás |
| B4 | Tamaño del catálogo | traz. §2.1: "el catálogo tiene 3 de las 50 señas" | 50 glosas en la base de datos | El documento quedó atrás (ver detalle) |
| B5 | Asociación glosa ↔ animación | RF-AVT-02: parcial, "no existe la asociación" | La asociación existe y resuelve dentro del APK | **Hay que resolverlo**; el funcionamiento de hoy tiene sentido |
| B6 | Transición entre señas | RF-AVT-04: parcial, pausa en reposo sin mezcla | Fundición real de 100 ms entre señas | El documento quedó atrás |
| B7 | Ubicación de las animaciones | RF-INT-HW-04: pendiente, sin definir | Definida: dentro del APK | El documento quedó atrás |
| B8 | Selector de modo | modif. punto 2: selector manual / en vivo por construir | No hay selector: dos interruptores fijos | El documento quedó atrás |
| B9 | Nombre del sistema | "SignAI" en toda la documentación | **SignALL** en la app | El documento quedó atrás |

---

## 2. Detalle

### A1 — El micrófono no tiene el tercer estado (RF-UI-01)

**Qué dice el documento.** v0.15 §3.5, RF-UI-01: un botón de control de voz
con tres estados visuales —inactivo, grabando, y "texto listo para enviar"— y
"al presionarlo en el último estado el texto se envía al NLP".

**Qué hace la app.** El ícono del micrófono es siempre el mismo y sigue siendo
la única forma de dictar otra vez. Lo que existe es una descripción de estado
accesible que cambia (`MainScreenState.kt:403-413`) y un aviso de 3 s en el
hint del escenario (`MainScreenState.kt:850-851`): "Texto listo. Toca Traducir
para enviarlo." El envío lo dispara el botón Traducir.

- Contrato de la interfaz: `ui/screens/bottombar/SignAiBottomBar.kt:241-258`
- Estado y su descripción: `ui/state/MainScreenState.kt:429-430` (`dictationReadyToSend`)
- Motivo documentado: `openspec/specs/ui/spec.md`, fila `STA-17`
- El documento de documentos ya sabe que el tercer estado falta: modif. punto 8

**Decisión.** Se adapta la app a lo que pide el documento: el tercer estado
vuelve a existir.

---

### A2 — El permiso de micrófono se pide tarde (RF-UI-04)

**Qué dice el documento.** v0.15 §3.5, RF-UI-04: "al iniciar la aplicación por
primera vez" se piden micrófono e internet, y si el usuario deniega se muestra
un mensaje.

**Qué dice también.** modif. punto 17 ya había detectado esto: "La app pide el
micrófono al tocarlo por primera vez. Decidir: ajustar el requisito o la app."

**Qué hace la app.** `RECORD_AUDIO` se pide la primera vez que se toca
cualquiera de los dos interruptores de micrófono, guardando la acción para
ejecutarla si se concede (`ui/MainScreen.kt:78-103`). `INTERNET` no se pide
nunca porque no es permiso de runtime. Si se deniega, el aviso
"Sin permiso de micrófono la app no puede traducir voz." dura 3 s
(`ui/state/MainScreenState.kt:1422-1426`).

- Declaraciones: `app/src/main/AndroidManifest.xml:4-5`

**Decisión.** Se adapta la app a lo que pide el documento.

---

### A3 — El enlace con el backend va sin cifrar (RF-INT-COM-03)

**Qué dice el documento.** v0.15 §2.4, RF-INT-COM-03: "Toda comunicación entre
la app, el backend y los servicios externos viajará cifrada", y los diagramas
C4-1 y C4-2 etiquetan el enlace de la app como `[Android, WSS]`. traz. §2.1 lo
marca como "No implementado".

**Qué hace la app.** El WebSocket abre en `ws://10.0.2.2:8080/ws/translate`
(`data/SignAiBackendConfig.kt:35`) y el manifiesto permite tráfico en claro de
forma global (`res/xml/network_security_config.xml:13`,
`AndroidManifest.xml:17`). La URL se puede inyectar en el build
(`app/build.gradle.kts:14-15`), así que por el mismo código ya se puede apuntar
a un `wss://`.

**Decisión.** Se adapta la app a lo que pide el documento, sin dañar el
funcionamiento: tiene que poder seguir conectándose al backend de desarrollo
en claro, con un bypass para el caso de desarrollo.

---

### A4 — Si la conexión se cae traduciendo, la app queda trabada (RNF-REL-02)

**Qué dice el documento.** v0.15 §6.1, RNF-REL-02 pide que la app detecte la
desconexión y lleve el avatar a reposo. modif. punto 19 amplía el criterio con
dos cosas: (a) "si la conexión cae mientras se traduce, la app debe quedar
lista para recibir una frase nueva; hoy queda en 'Traduciendo...' y rechaza
frases hasta reiniciarla"; (b) describir la reconexión automática con "espera
creciente, límite de intentos y reinicio del contador al reconectar".

**Qué hace la app.**

- El evento de desconexión no hace nada: `BackendEvent.Disconnected -> Unit`
  (`ui/state/MainScreenState.kt:695`).
- El bucle de reconexión no pone el estado "Sin conexión" si la frase está en
  `Translating` o `Signing` (`MainScreenState.kt:538-540`), así que el turno
  queda en vuelo sin camino de salida: no hay timeout y ningún mensaje.
- La espera sí crece, pero sin tope: `delay(ReconnectBaseMs * attempt.coerceAtMost(MaxReconnectAttempts))`
  (`MainScreenState.kt:544`), o sea 1 s, 2 s, 3 s, 4 s, 5 s y después 5 s para
  siempre, y `attempt` **nunca se reinicia** al reconectar
  (`MainScreenState.kt:530-545`).
- Mientras el estado sea `Translating`, Traducir queda deshabilitado
  (`ui/screens/bottombar/SignAiBottomBar.kt:101-137`) y el envío a mano
  también se rechaza (`MainScreenState.kt:909-915`).

**Decisión.** Se adapta la app a lo que pide el documento.

---

### A5 — "Seña no disponible" no se avisa glosa por glosa (RF-CAT-03)

**Qué dice el documento.** v0.15 §3.4, RF-CAT-03: "Cuando una glosa no exista
en el catálogo, el sistema responderá con el mensaje 'Seña no disponible' en
lugar de enviar una instrucción de animación al avatar."

**Qué hace la app.** Con la seña de prueba apagada (es el default desde el
2026-10-16, `domain/Settings.kt`), cada glosa sin animación se salta en
silencio y solo se cuenta en la métrica `sin_seña`
(`ui/state/MainScreenState.kt:640-644`). El texto "Seña no disponible"
únicamente aparece como caption cuando una oración entera no produjo ninguna
seña representable (`MainScreenState.kt:1069`), y el aviso "Ninguna seña de
esta frase está en el catálogo todavía" cuando una oración llega sin ítems
(`MainScreenState.kt:1071-1077`).

- Cómo se decide qué glosa tiene animación: `MainScreenState.kt:604-648`
- Con la seña de prueba encendida, la glosa se representa con la seña por
  defecto y tampoco se avisa (`:633-638`).

**Decisión.** Se adapta la app a lo que pide el documento.

---

### A6 — Los marcadores no manuales no se animan (RF-AVT-03, RF-NLP-03)

**Qué dice el documento.** v0.15 §3.3, RF-AVT-03: "El sistema aplicará
expresiones faciales durante la reproducción de la secuencia, según el tipo de
frase indicado por el NLP." Y RF-NLP-03 pide que el tipo de frase se identifique
y se entregue junto con las glosas. traz. §2.1 dice "Solo se anima la ceja".

**Qué hace la app.** El tipo de frase llega y está disponible
(`domain/Translation.kt:14-26`, `PhraseType`), viaja en cada ítem de la cola
(`ui/webview/SignAiWebViewController.kt:85-96`) y se guarda en la escena
(`app/src/main/assets/web/app.js:558`), pero **nadie lo usa**: lo único que lo
consume es el texto de depuración de la escena (`app.js:241`). El motor
procedural que animaba la ceja se eliminó el 2026-10-13
(`openspec/specs/ui/spec.md`, fila `BRG-11`) y la expresión facial viene
horneada dentro de cada clip de seña (`openspec/specs/avatar/spec.md:159-165`).

Esto es lo que queda **sin tocar por ahora**: no es lo mismo a lo que dice el
documento, pero el rig sí tiene huesos faciales (28: ceja, párpado, mandíbula,
boca, mirada), así que la decisión sobre qué hacer queda abierta.

---

### A7 — Los estados de la interfaz no son los del diagrama 7.4

**Qué dice el documento.** El diagrama
`../sign-ai-docs/diagramas/draw.io/sdd-7.4-diagrama-estados-ui.drawio` define
seis estados: **Inactivo, Grabando, Procesando, Reproduciendo, Error,
Desconectado**, con estas transiciones: activar micrófono → Grabando;
detiene grabación o silencio detectado → Procesando; primer segmento recibido →
Reproduciendo; termina la animación del último segmento → Inactivo; falla
NLP/LLM o falla del STT → Error; pierde WebSocket → Desconectado → reconecta.

**Qué hace la app.** El enum `PlaybackState` tiene nueve valores
(`ui/state/PlaybackState.kt:11-21`): Listo para traducir, Dictando,
Escuchando, Procesando, Traduciendo, Reproduciendo señas, Reproducción
completada, Conectando con el servidor, Sin conexión con el servidor.

Diferencias de fondo, no solo de rótulo:

- **No hay estado de error.** Los errores son un mensaje transitorio de 3 s en
  el hint del escenario (`MainScreenState.kt:1015-1022`), no un estado de la
  máquina.
- **"Traduciendo" no existe en el diagrama**, pero es el estado en el que la
  app pasa la mayor parte del tiempo esperando al backend.
- El diagrama no distingue entre dictado y en vivo, ni tiene un estado de
  "texto listo para enviar".

**Decisión.** Se adapta la app al documento: los seis estados del diagrama 7.4,
en vez de las etiquetas nuevas.

---

### A8 — El diagrama C4-2 dice "Filament"

**Qué dice el documento.**
`../sign-ai-docs/diagramas/draw.io/sdd-c4-2-contenedores.drawio`: el contenedor
"Android Client" "renderiza el avatar 3D con **Filament**, con las animaciones
GLB incluidas en la app".

**Qué hace la app.** Es un `WebView` con Three.js r169 cargado por CDN
(`ui/webview/AvatarWebView.kt:188`, `app/src/main/assets/web/index.html`).
Filament y Unity son los enfoques que se abandonaron (`AGENTS.md`: "Old
approaches... are dead"). La parte del diagrama que sí sigue siendo cierta es
"las animaciones GLB incluidas en la app".

**Decisión.** El diagrama está desactualizado.

---

### A9 — El micrófono no se apaga en modo en vivo

**Qué dice el documento.** traz., sección "Modos de envío": "En ambos modos el
micrófono se apaga al terminar cada frase y hay que volver a activarlo."
modif. punto 5 refuerza que eso "documenta el límite que separa este modo del
uso continuo en una clase".

**Qué hace la app.** En **dictado** (🎤) el micrófono se apaga al llegar la
frase y hay que volver a tocarlo. En **en vivo** (◢◢ TR) el micrófono **queda
prendido** y cada frase sale sola cuando se cierra por silencio
(`ui/state/MainScreenState.kt:1186`, `changeMicMode` en `:1197-1211`).

**Decisión.** El documento está desactualizado. Es la opción A de la auditoría
§7, ya decidida el 2026-10-15: TR queda, el micrófono queda prendido, y la
unidad sigue siendo la oración.

---

### A10 — El tope de 8 segundos de habla

**Qué dicen los documentos.** traz. §2.3: la frase en vivo "se envía sola tras
1,5 segundos de silencio o a los 8 segundos de habla seguida".
`plan-pruebas/01-diseno/stt/general.md:25-26` repite el criterio: la frase
"empieza tras 300 ms de voz y se cierra tras 1,5 segundos de silencio, a los 8
segundos de habla seguida, o al recibir el mensaje de fin".

**Qué hace la app.** En la app **no hay ningún temporizador** de captura. El
corte por silencio y el máximo los impone el STT; el cliente solo manda
`{"type":"end"}` cuando el usuario apaga el micrófono
(`ui/state/MainScreenState.kt:1370-1395`) y espera el `final`. El límite de 8 s
ya no existe en el cliente (era del camino por palabras, que se desarmó).

**Decisión.** No estamos de acuerdo con esa lectura. Se deja el
comportamiento de la app.

---

### A11 — El inventario de pantallas del alcance

**Qué dice el documento.** alcance §1.1 describe la interfaz de la app como
"Pantalla principal, botón de micrófono, entrada de texto e historial de la
conversación".

**Qué hace la app.** Todo eso existe, y además: panel lateral de conversación
con los turnos TÚ / TRADUCCIÓN y repetición por turno, panel lateral de
ajustes, hoja modal de historial, pantalla Acerca de, botón Repetir en el
escenario, hint de estado sobre el avatar y splash con progreso de carga real.

- `ui/MainScreen.kt:196-357` (composición y orden de capas)
- `ui/screens/bottombar/SignAiBottomBar.kt`, `ui/screens/stage/AvatarStage.kt`,
  `ui/screens/conversation/ConversationPanel.kt`,
  `ui/screens/settings/SettingsSidebar.kt`, `ui/components/HistoryBottomSheet.kt`,
  `ui/screens/about/AboutScreen.kt`

**Decisión.** La interfaz de la app está bien y fue aprobada. Se deja como
está.

---

### A12 — Los ajustes de accesibilidad no tienen requisito

**Qué dicen los documentos.** Ninguno. No hay un requisito de accesibilidad
en v0.15 ni una mención en el plan de pruebas.

**Qué hace la app.** Tamaño de interfaz, texto en negrita, contraste
aumentado, texto más grande y reducir animaciones
(`ui/screens/settings/SettingsSidebar.kt:94-114`), con efecto en la
composición (`MainActivity.kt:43-44`, `ui/designsystem/Theme.kt:42-76`) y en la
escena 3D, donde reducir animaciones convierte la mezcla entre señas en un
corte seco (`app.js:809-812`).

**Decisión.** Se deja en la app: la accesibilidad está bien como está.

---

### A13 — La latencia de punta a punta no existe como número

**Qué dice el documento.** v0.15 §4, RNF-DES-01: "El tiempo desde que el
usuario confirma el envío del texto hasta que el avatar ejecuta la primera
seña se mantendrá dentro de un rango estimado", con un rango para caché y otro
para llamada al LLM. modif. punto 22 lo tiene bloqueado hasta la ronda
diagnóstica.

**Qué hace la app.** El device mide tres cosas por logcat, y ninguna es el
total (`ui/state/SignAiMetrics.kt`):

| Métrica | Qué mide |
|---|---|
| `frase #N` | desde que se manda la frase hasta el `done` del backend |
| `oración→avatar:` | desde que llega un `segment` hasta el primer movimiento |
| `representado:` | duración de la representación |

El total de RNF-DES-01 (envío confirmado → primera seña en movimiento) no está
armado en ninguna métrica: las dos que lo necesitan existen por separado, con
el reloj del turno que arranca al mandar la frase (`MainScreenState.kt:329`) y
el `signStart` que reporta la escena (`MainScreenState.kt:188-195`).

**Decisión.** Se adapta la app a lo que pide el documento: que exista el
total. Los datos extra que ya se miden no sobran, se dejan.

---

### A14 — La app instrumenta sola

**Qué dicen los documentos.** El plan de pruebas mide los tiempos en el backend
(`rt_*`) y en el STT. El device no aparece como fuente de métricas.

**Qué hace la app.** Emite sus propias medidas por logcat: `SignAiMetrics`
(latencias y contadores), `SignAiNet` (los `segment` que llegan), `SignAIWeb`
(consola de JS) y `SignAiState` (transiciones de estado). Ninguna se ve en
pantalla: son solo logs (`SignAiMetrics.kt:16-17`).

**Decisión.** No está definido del todo, pero no hay problema con que la app
instrumente sola.

---

### A15 — El canal HTTP tiene más de una operación

**Qué dicen los documentos.** RF-INT-COM-02 pide un canal transaccional HTTP
para operaciones puntuales. modif. punto 15 dice: "Hoy solo existe una
consulta de estado del servicio (`/health`). Limitar el requisito a eso o
quitarlo."

**Qué hace la app.** Dos usos de HTTP:

1. `GET /health`, para leer `silence_timeout_ms` y mostrarlo en el overlay de
   depuración (`data/BackendHealth.kt:29-48`, `MainScreenState.pushSilenceTimeout()`
   en `:206-212`).
2. `SignAssetRepository`, que baja un asset cuando la glosa trae una ruta que
   **no** es del layout propio `signs/*.glb` (`data/SignAssetRepository.kt`). Con
   el catálogo actual esto no se usa nunca, porque `glb_animation_path` siempre
   es una ruta dentro del APK (`domain/SignsCatalog.kt:59-83`), pero el camino
   existe y sirve en el WebView.

**Decisión.** Queda pendiente. No está definido si los assets van a servirse
remotos o no: depende del trabajo de Samuel con las señas.

---

### B1 — El modo manual (RF-STT-03) — la app está bien

**Qué dice el documento.** RF-STT-03: "El sistema detectará cuando el usuario
deja de hablar... El sistema no enviará este texto al módulo de NLP de forma
automática, el envío requiere una acción de confirmación del usuario."
traz. §2.1 lo marca como "No implementado", y la auditoría §6.3 punto 3 lo
trataba como el trabajo pendiente.

**Qué hace la app.** El micrófono de la fila 1 es dictado: se toca, se habla, el
texto va apareciendo en el campo mientras se escucha, y cuando la frase se
cierra **el texto se queda ahí sin mandarse**. El micrófono se apaga solo y lo
que dispara el envío es el botón Traducir
(`ui/state/MainScreenState.kt:821-859`, `stopDictation` en `:1370-1395`).

**Decisión.** La app está bien. El dictado es la simple conversión de voz a
texto: el texto se ve en el campo para poder editarlo si se transcribió mal, y
después solo hay que presionar Traducir.

---

### B2 — La conservación del texto (RNF-REL-04)

**Qué dice el documento.** traz. §2.1: "No implementado".

**Qué hace la app.** `fail()` conserva el texto en el campo
(`ui/state/MainScreenState.kt:985-1013`). Lo único que lo descarta es tocar el
micrófono con texto esperando, que es cancelación explícita.

**Decisión.** El documento quedó atrás.

---

### B3 — La edición de la transcripción (RF-UI-02)

**Qué dice el documento.** traz. §2.1: "Parcial. La barra muestra el texto y se
puede escribir en ella, pero no se puede corregir antes de que se envíe: el
campo se bloquea apenas llega la frase final."

**Qué hace la app.** El campo es `readOnly` solo mientras el micrófono está
prendido (para que la transcripción se vea en vivo sin que se pueda escribir
encima) y es editable en cuanto el micrófono se apaga
(`ui/screens/bottombar/SignAiBottomBar.kt:199-200`). modif. punto 9 ya lo
anticipaba: "La edición del texto antes de enviar aplica solo al modo manual.
Hoy el campo se bloquea apenas llega la frase final."

**Decisión.** El documento quedó atrás.

---

### B4 — El tamaño del catálogo

Aclaración, porque el punto quedó confuso: **el catálogo es la tabla `catalog`
de la base de datos (Neon), no algo de la app.** La app no guarda el catálogo:
solo resuelve la ruta que el backend le manda en cada glosa.

**Qué dice el documento.** traz. §2.1, fila RF-CAT-01: "El catálogo tiene 3 de
las 50 señas."

**Cuál es el estado real.** La tabla `catalog` tiene las **50 glosas de LSC50**
con su `glb_animation_path`, sembradas el 2026-10-14
(`openspec/sessions/2026-10-14-seed-catalog-neon.md`). De las 50, **32 tienen
animación exportada** y están en el APK; las otras 18 están declaradas con
`available: true` y una ruta que no existe en el dispositivo, que es lo que
detecta `SignsCatalog.isOwnAssetPath()` para no pedir por red un 404
garantizado (`domain/SignsCatalog.kt:79-83`).

**Decisión.** El documento quedó atrás. En la app no hay nada que cambiar en
este punto.

---

### B5 — La asociación entre glosa y animación (RF-AVT-02)

**Qué dice el documento.** traz. §2.1: "No existe la asociación entre glosa y
animación."

**Qué hace la app.** La asociación existe y es `same-origin`: el backend manda
`glb_animation_path` con la forma `signs/<id>.glb`, y `SignsCatalog.localAssetOrNull()`
lo reconoce, lo valida contra las 32 señas realmente exportadas y lo devuelve
tal cual para que la escena lo cargue sin red (`domain/SignsCatalog.kt:59-65`,
`ui/state/MainScreenState.kt:604-648`). Verificado en el Redmi: una cola de 3
señas reproducida con cero peticiones HTTP.

Lo que queda abierto es la parte de las 18 glosas sin animación: llegan con
ruta propia que no existe y caen a la seña por defecto o se saltan, según el
ajuste de seña de prueba.

**Decisión.** Hay que resolverlo, pero el funcionamiento de hoy tiene sentido.
Queda atado a si se exportan o no las 18 señas que faltan (trabajo de Samuel).

---

### B6 — La transición entre señas (RF-AVT-04, RNF-DES-06)

**Qué dice el documento.** traz. §2.1: "Entre señas hay una pausa en reposo de
unos 100 ms, sin mezcla de movimientos."

**Qué hace la app.** Entre señas hay una **fundición de 100 ms con dos actions
vivas** (`crossFadeTo`, `app.js:502-510`), que es exactamente lo que describe
el requisito RF-AVT-04 ("no se perciben cortes ni saltos visibles") y lo que
pide RNF-DES-06 como duración máxima de transición. Con "reducir animaciones"
activado el paso es un corte seco, que es el comportamiento accesible.

**Decisión.** El documento quedó atrás. La fundición se mantiene porque es lo
que hace que la transición entre animaciones sea suave.

---

### B7 — La ubicación de las animaciones (RF-INT-HW-04)

**Qué dice el documento.** traz. §2.1: "La ubicación de las animaciones no está
definida."

**Cuál es el estado real.** Está definida y es dentro de la app:
`assets/web/models/avatar.glb` (10,33 MB) y `assets/web/signs/*.glb` (32
archivos, 5,7 MB en total). El requisito pide además que haya espacio
suficiente antes de empezar una sesión, que es lo que hace el APK.

**Decisión.** El documento quedó atrás.

---

### B8 — No hay selector de modo

**Qué dicen los documentos.** modif. punto 2: "Selector de modo de envío
(manual o en vivo). Se decidió dejar ambos modos y el selector no existe."
traz. §2.1 lo lista como "por construir".

**Qué hace la app.** Los dos modos existen como dos interruptores fijos: 🎤 en
la fila 1 (dictado) y ◢◢ TR en la fila 2 (en vivo), uno por fila y nunca
prendidos al mismo tiempo (`ui/state/MicMode.kt`,
`ui/screens/bottombar/SignAiBottomBar.kt:101-137`).

**Decisión.** Por ahora son dos interruptores fijos. El documento quedó atrás.

---

### B9 — El nombre del sistema

**Qué dicen los documentos.** "SignAI" en los 66 requisitos, en el plan de
pruebas, en los 12 diagramas y en el nombre de los repos.

**Qué hace la app.** Se rebrandeó a **SignALL** el 2026-10-16: nombre de la
aplicación, splash, pantalla Acerca de y panel de ajustes
(`ui/SignAiBranding.kt:18-20`, `app/src/main/res/values/strings.xml`).
`../sign-ai-docs` no menciona "SignALL" en ningún archivo.

**Decisión.** El documento quedó atrás. Ahora es SignALL.

---

## 3. Las diferencias en las que hay que tocar la app

| ID | Qué hay que hacer | Dónde |
|---|---|---|
| A1 | Recuperar el tercer estado visual del micrófono | `ui/screens/bottombar/SignAiBottomBar.kt:241-258`, `ui/state/MainScreenState.kt:403-413` |
| A2 | Pedir el permiso de micrófono al abrir la app | `ui/MainScreen.kt:78-103`, `MainActivity.kt` |
| A3 | Cifrado, con bypass para el desarrollo | `data/SignAiBackendConfig.kt:35`, `res/xml/network_security_config.xml`, `app/build.gradle.kts:14-15` |
| A4 | Destrabar el turno cuando cae la conexión traduciendo; reconexión con tope de intentos y contador que se reinicia | `ui/state/MainScreenState.kt:527-547`, `:695`, `:909-915` |
| A5 | Avisar "Seña no disponible" por glosa | `ui/state/MainScreenState.kt:604-648`, `:1069` |
| A7 | Volver a los seis estados del diagrama 7.4 | `ui/state/PlaybackState.kt:11-21` y `ui/state/MainScreenState.kt` |
| A13 | Armar la métrica de latencia de punta a punta (envío confirmado → primera seña) | `ui/state/SignAiMetrics.kt`, `ui/state/MainScreenState.kt:329`, `:188-195` |

Y las dos que quedan abiertas esperando el trabajo de las señas: **A15** (si los
assets van a servirse remotos o no) y **B5** (las 18 glosas sin animación
exportada).
