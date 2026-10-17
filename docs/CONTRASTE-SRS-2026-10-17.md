# SRS (Overleaf) ↔ App — revisión punto por punto

**Fecha:** 2026-10-17
**App:** `sign-ai-android`, rama `flujo-oraciones`, commit `0b3c7f8`
**Documento revisado:** Overleaf, proyecto **SRS** (`6a122a19bb9a5dd9d390ba3d`),
archivo `main.tex`, 1369 líneas, última edición **2026-10-17 18:45**
**Este es el informe de la revisión, no el documento de trabajo.** Para saber qué
hay que hacer hoy, ver [`CONTRASTE-APPvsDOCUMENTOS.md`](CONTRASTE-APPvsDOCUMENTOS.md),
que absorbió este contenido.
**Contraste anterior:** `CONTRASTE-DOCUMENTOS.md` (2026-10-16), renombrado el 2026-10-17.
(2026-10-16, app `6e93987`)

Este documento revisa, **punto por punto**, qué hizo el SRS con cada una de las
24 diferencias que dejó el contraste del 2026-10-16. No se escribió nada en
Overleaf: es una revisión de solo lectura.

Alcance de la revisión: se leyó el `.tex` completo. Los dos diagramas que el
proyecto tiene en `images/` son PNG y **no** se pudieron leer; los `.drawio`
siguen viviendo en `../sign-ai-docs`.

**Tres valores de la tabla de estados, para no confundirlos:** el **documento
7.4** es el diagrama de `../sign-ai-docs` (6 estados), el **SRS** define 7
(inactivo, grabando, texto listo, procesando, reproduciendo, error,
desconectado) y la **app** tiene 9.

---

## 1. Resumen

### 1.1 Resultado por punto

| ID | Tema | ¿El SRS siguió lo del 10-16? | Qué hay que hacer |
|---|---|---|---|
| A1 | Tercer estado del micrófono | **Sí, y de más** | App: mostrarlo visualmente |
| A2 | Cuándo se pide el micrófono | **Sí, textual** | App: pedirlo al abrir |
| A3 | Cifrado del enlace | **Sí, textual** | App: WSS con bypass de desarrollo |
| A4 | Conexión que se cae traduciendo | **No** — quedó a medias | **Decisión del owner** |
| A5 | "Seña no disponible" | **Sí, textual** | App: avisar glosa por glosa |
| A6 | Marcadores no manuales | **Sí, pero el 10-16 los dejó en pausa** | **Decisión del owner** |
| A7 | Estados de la interfaz | **Parcial** — cambió el objetivo | App: de 9 a 7 estados |
| A8 | Motor 3D | **Sí, corregido** | Nada en la app |
| A9 | Micrófono en en vivo | **Sí, coincide con la app** | Nada |
| A10 | Tope de 8 s | **Sí, mudado al servidor** | Nada en la app |
| A11 | Inventario de pantallas | **Sí, documenta lo que hay** | Nada |
| A12 | Accesibilidad | **Sí, ahora es requisito** | Nada (ya está) |
| A13 | Latencia de punta a punta | **Sí, como medida de interfaz** | App: armar el total |
| A14 | Instrumentación en el device | No lo menciona | Nada |
| A15 | Canal HTTP | **Sí, cerrado a `/health`** | Nada (o borrar código muerto) |
| B1 | Confirmación antes de enviar | **Sí, coincide** | Nada |
| B2 | Conservación del texto | **Sí, coincide** | Nada |
| B3 | Edición de la transcripción | **Sí, coincide exacto** | Nada |
| B4 | Tamaño del catálogo | **Discrepa: dice 50** | **Decisión del owner** |
| B5 | Asociación glosa ↔ animación | **Sí, documentada** | Solo las 18 que faltan |
| B6 | Transición entre señas | **Sí, coincide** | Nada |
| B7 | Ubicación de las animaciones | **Sí, pero con 2 errores** | Dueño completa el `.tex` |
| B8 | Selector de modo | **Sí, coincide (sin selector)** | Nada |
| B9 | El nombre del sistema | **No** — sigue diciendo SignAI | Dueño corrige el `.tex` |

### 1.2 Los cuatro puntos donde el SRS no llegó

Ninguno de estos se arregla tocando la app:

1. **La sección 3 del SRS está vacía.** §3 "Requerimientos Específicos" tiene
   los subtítulos de la plantilla IEEE 830 y **ni una línea de contenido**:
   "Funcionalidad 1: Jugar", "Funcionalidad 2: Registro y Autenticación",
   "Funcionalidad 3: Administración", "Funcionalidad 4: Consultas". Con eso
   **desaparecieron los identificadores `RF-*` y `RNF-*`** que usan
   `v0.15` y el plan de pruebas: hoy no hay forma de decir "este SRS
   cumple el RF-UI-01". La "Apreciación Global" (línea 546) promete que "cada
   requerimiento se documenta con su identificador, descripción, razón, casos
   de uso asociados, criterio de aceptación y prioridad", y eso no existe.
   También vacíos: *Historial de Cambios*, §4 *Proceso Ingeniería de
   Requerimientos*, §5 *Proceso Verificación*, §6 *Anexos*, y en §2:
   *Características del Usuario*, *Restricciones*, *Modelo del Dominio*,
   *Suposiciones y Dependencias*, *Distribución de Requerimientos*.
2. **El SRS dice 50 animaciones y hay 32.** En tres lugares (§Alcance,
   §Restricciones de Memoria ×2). Es el mismo bloqueo de B4/B5.
3. **Faltaron dos marcadores de trabajo.** Líneas 931 y 1154:
   `[PENDIENTE: tamaño del APK con todos los archivos GLB.]`.
4. **El nombre.** "SignALL" no aparece ni una vez; "SignAI", 25 veces.

---

## 2. Detalle

### A1 — El tercer estado del micrófono

**Decisión del 10-16.** Se adapta la app: el tercer estado vuelve a existir.

**Qué dice el SRS ahora.** Sí, y en más lugares de los que pedía el diagrama.
En §Alcance lista los siete estados, incluido *texto listo*. Y en la tabla
`tab:estados-ui` hay una fila propia:

> **Texto listo** — El texto dictado o escrito manualmente está en la barra,
> puede editarse y espera a que el usuario pulse Traducir.

La fila *Grabando* además dice que "en el modo dictado, cuando el sistema
detecta una pausa o el usuario apaga el micrófono, la frase se cierra y el
indicador pasa a *Texto listo*".

**Qué hace la app.** El estado existe (`dictationReadyToSend`) pero **no se ve**:
el ícono del micrófono no cambia y lo único que cambia es la descripción
accesible y un aviso de 3 s en el hint del escenario.

**Veredicto.** El documento sí recogió el cambio. La app sigue sin hacerlo.

**Trabajo en la app.** Ícono o indicador que cambie en el tercer estado, no solo
el `stateDescription`.

---

### A2 — El permiso de micrófono se pide tarde

**Decisión del 10-16.** Se adapta la app: pedirlo al abrir.

**Qué dice el SRS ahora.** Textual, línea ~829:

> La primera vez que se abre la aplicación, esta solicita el permiso de
> micrófono. Si el usuario niega el permiso del micrófono la aplicación solo
> trabaja con el texto ingresado manualmente por el usuario.

**Qué hace la app.** `RECORD_AUDIO` se pide la primera vez que se toca cualquiera
de los dos interruptores, guardando la acción para ejecutarla si se concede
(`ui/MainScreen.kt:89-100`).

**Veredicto.** El documento sí recogió el cambio. La app no.

**Trabajo en la app.** Pedir el permiso al abrir la pantalla principal, una
sola vez, conservando el aviso de 3 s cuando se deniega.

---

### A3 — El enlace con el backend va sin cifrar

**Decisión del 10-16.** Se adapta la app, con bypass para desarrollo.

**Qué dice el SRS ahora.** Más fuerte que antes, en §Interfaces de Comunicación:

> Toda comunicación que sale de un equipo viaja cifrada mediante TLS, y los
> mensajes de control y de resultados se intercambian en formato JSON.

> Entre la aplicación y el servidor se mantiene una conexión WebSocket segura
> (WSS), bidireccional y persistente.

**Qué hace la app.** `ws://10.0.2.2:8080/ws/translate`
(`data/SignAiBackendConfig.kt:35`), con cleartext permitido globalmente en el
manifiesto. El código ya sabe derivar `https://` de una URL `wss://`, así que la
mitad está hecha.

**Veredicto.** El documento sí recogió el cambio. La app no.

**Trabajo en la app.** Default a `wss://`, con el `ws://` de desarrollo como
bypass por build config, y el `network_security_config.xml` alineado.

---

### A4 — Si la conexión se cae traduciendo, la app queda trabada

**Decisión del 10-16.** Se adapta la app.

**Qué dice el SRS ahora.** **A medias.** La tabla `tab:estados-ui` sí tiene el
estado *Desconectado*, y dice:

> Se perdió la conexión con el servidor, lo que puede ocurrir en cualquier
> estado. El avatar vuelve a reposo, el indicador de estados lo indica y el
> sistema intenta reconectarse. El texto pendiente no se pierde.

Eso cubre dos de las tres cosas: el avatar vuelve a reposo y el texto se
conserva.

**Lo que falta.** Busqué en las 1369 líneas y no aparece ninguno de estos tres
puntos, que eran el fondo del problema:

- que la app **quede lista para recibir una frase nueva** (hoy queda en
  *Traduciendo* para siempre y rechaza todo),
- el **límite de intentos** de reconexión,
- el **reinicio del contador** al reconectar.

Cero coincidencias de "intento", "espera creciente" y "lista para".

**Qué hace la app.** Igual que el 10-16: `BackendEvent.Disconnected -> Unit`, la
espera crece sin tope (1 s, 2 s, 3 s, 4 s, 5 s, y 5 s para siempre) y `attempt`
nunca se reinicia.

**Veredicto.** El SRS **no cerró este punto**. Y el bug de la app sigue igual.
Es el único punto donde el documento quedó corto y la app también está mal: no
alcanza con adaptar la app, hace falta que el documento precise el comportamiento.

**Decisión del owner.** Cuál es el comportamiento, antes de tocar la app.

---

### A5 — "Seña no disponible" no se avisa glosa por glosa

**Decisión del 10-16.** Se adapta la app.

**Qué dice el SRS ahora.** Dos veces. §Alcance:

> Cuando una glosa no existe en el catálogo, el sistema informa que la seña no
> está disponible en lugar de enviar una instrucción inválida al avatar.

Y en el estado *Reproduciendo* de la tabla: "Si alguna palabra no está en el
catálogo, la pantalla muestra el mensaje 'Seña no disponible'".

**Qué hace la app.** Cada glosa sin animación se salta en silencio y solo se
cuenta en la métrica `sin_seña`. El texto aparece únicamente cuando una oración
entera no produjo nada.

**Veredicto.** El documento sí recogió el cambio. La app no.

**Nota para el trabajo.** El texto del SRS es "si alguna palabra no está en el
catálogo, la pantalla muestra el mensaje". Eso es por glosa. Hay que decidir si
el aviso va en un hint de 3 s (como el actual) o en otro lado, porque con varias
glosas faltantes en la misma oración puede ser repetitivo.

**Trabajo en la app.** Avisar por glosa.

---

### A6 — Los marcadores no manuales no se animan

**Decisión del 10-16.** **En pausa, no se toca por ahora** — porque el rig sí
tiene huesos faciales (28: ceja, párpado, mandíbula, boca, mirada) y la decisión
sobre qué hacer quedaba abierta.

**Qué dice el SRS ahora.** El documento **sí lo pide**, y como funcionalidad, en
tres lugares:

- §Alcance: "También aplica marcadores no manuales, como expresiones faciales,
  según el tipo de frase."
- §Hardware: "para ejecutar las animaciones esqueléticas y las expresiones
  faciales con fluidez."
- §Comunicaciones: el tipo de frase viaja en cada segmento.

**Qué hace la app.** El tipo de frase llega, viaja en cada ítem de la cola y se
guarda en la escena, pero **nadie lo usa**: lo único que lo consume es el texto
de depuración. La expresión facial viene horneada dentro de cada clip.

**Veredicto. Esto cambió de decisión.** El 10-16 lo dejó en pausa *porque el
documento no lo pedía*. Ahora el documento sí lo pide. Eso deja de ser un
"queda en pausa" y pasa a ser una funcionalidad pedida que no está.

**Decisión del owner.** Hay que elegir una de estas dos, y son excluyentes:

- **Hacerlo en la app.** El esqueleto ya tiene los huesos; falta el motor que
  mueva la ceja según el tipo de frase.
- **Sacarlo del SRS.** Si se decide que la expresión horneada en el clip es
  suficiente, la corrección va en el `.tex` y la hace el owner.

**Yo no lo doy por hecho en ninguna de las dos.**

---

### A7 — Los estados de la interfaz

**Decisión del 10-16.** Se adapta la app a los **seis** estados del diagrama 7.4.

**Qué dice el SRS ahora.** **Son siete**, no seis. La tabla `tab:estados-ui`
define: Inactivo, Grabando, **Texto listo**, Procesando, Reproduciendo, Error,
Desconectado.

Los seis del diagrama están todos, más *Texto listo* — que es el tercer estado
de A1. O sea que el SRS **no copió el diagrama**: lo amplió.

**Qué hace la app.** Nueve (`ui/state/PlaybackState.kt`): Listo para traducir,
Dictando, Escuchando, Procesando, Traduciendo, Reproduciendo señas, Reproducción
completada, Conectando con el servidor, Sin conexión con el servidor.

Diferencias de fondo, no de rótulo:

- **No hay estado de error** en la app. Los errores son un mensaje transitorio de
  3 s, no un estado de la máquina.
- **"Traduciendo" y "Conectando" no existen en el SRS**, y son donde la app pasa
  casi todo el tiempo.
- El SRS no distingue dictate/TR en la lista, pero sí dentro de *Grabando*.

**Veredicto. El objetivo cambió.** Ya no es "copiar los seis del diagrama": es
"quedar en los siete del SRS". El diagrama 7.4 de `../sign-ai-docs` quedó atrás
respecto del SRS, al revés de lo que decía el 10-16.

**Decisión del owner.** Siete estados exactos, o siete más distinguir el
dictado del TR como hoy.

**Trabajo en la app.** De 9 a 7. Nota: el estado *Error* y el *Desconectado* son
el mismo trabajo que A4.

---

### A8 — El diagrama C4-2 decía "Filament"

**Decisión del 10-16.** El diagrama está desactualizado.

**Qué dice el SRS ahora.** Corregido. En §Dispositivos: "El avatar se dibuja con
la biblioteca Three.js dentro de un componente WebView incrustado en la
pantalla." Y en §Software: Three.js 0.169.0, cargado desde CDN.

**Veredicto.** Cerrado. Sin trabajo en la app.

**Lo que queda.** El `.drawio` de C4-2 que sigue diciendo Filament está en
`../sign-ai-docs`, no en Overleaf. Overleaf tiene dos PNG en `images/`
(`sdd-6.1-vista-logica-componentes` y `sdd-c4-1-contexto`), que no se pudieron
leer. Los `.drawio` los edita el owner.

---

### A9 — El micrófono no se apaga en modo en vivo

**Decisión del 10-16.** El documento está desactualizado (opción A de la
auditoría §7).

**Qué dice el SRS ahora.** Actualizado, y coincide con la app. En §Modos de
envío: "Modo de traducción en tiempo real (TR): se activa con el botón TR. **El
micrófono permanece encendido** y cada frase se envía a traducir apenas el
sistema detecta una pausa, sin ninguna acción del usuario." Y en el botón TR:
"Con el modo activo, el micrófono permanece encendido".

**Veredicto.** Cerrado. Sin trabajo.

---

### A10 — El tope de 8 segundos de habla

**Decisión del 10-16.** Se deja el comportamiento de la app (no hay temporizador
en el cliente).

**Qué dice el SRS ahora.** Coincide con la app, pero **mudado de lado**: en
§Restricciones de Memoria, como búfer del servidor:

> El servidor conserva como máximo 8 segundos de audio por frase antes de
> transcribirla; en formato PCM de 16 bits a 16 kHz, esto equivale a unos 250 KB
> por sesión activa. Las frases más largas se cortan en una pausa y se
> transcriben por partes.

**Veredicto.** Cerrado en la app: el tope no es del cliente. Lo que hay que
confirmar es que el STT lo cumpla, y eso se mide allá
(`../sign-ai-stt`), no acá.

---

### A11 — El inventario de pantallas

**Decisión del 10-16.** La interfaz queda como está, ya aprobada.

**Qué dice el SRS ahora.** Documenta todo lo que hay: pantalla principal,
indicador de estados, barra de transcripción, botón de micrófono, botón TR,
botón Traducir, botón de historial, hoja de historial (las últimas 20 frases),
panel izquierdo de conversación, panel derecho de ajustes y pantalla Acerca de.

La tabla `tab:ajustes` además nombra ajustes que la app **sí tiene**: velocidad
de señas (control deslizante de 10 a 100, `domain/Settings.kt:15`), modo
oscuro/claro (`Settings.kt:26`), las cinco opciones de accesibilidad, y el bloque
Avanzado de depuración (modo debug, cámara libre, cuadrícula, fondo de bosque,
seña de prueba y selector de seña).

**Veredicto.** Cerrado. Sin trabajo.

---

### A12 — Los ajustes de accesibilidad no tienen requisito

**Decisión del 10-16.** No hay requisito; se dejan en la app.

**Qué dice el SRS ahora.** **Ahora sí es requisito.** En §Alcance: "La
aplicación incluye además opciones de accesibilidad, como ajustar el tamaño de
la interfaz, aumentar el contraste y reducir las animaciones." Y en
`tab:ajustes`, con las cinco, incluida la nota de que "reducir animaciones" hace
que "el avatar pase de una seña a la siguiente con un corte directo, en lugar de
una transición suave" — que es exactamente lo que ya hace `app.js:809-812`.

**Veredicto.** El documento se selló con la app. Las cinco opciones ya están
implementadas. Sin trabajo.

---

### A13 — La latencia de punta a punta no existe como número

**Decisión del 10-16.** Se adapta la app: que exista el total.

**Qué dice el SRS ahora.** Documentado, como §Aspectos de medición de la
interfaz. Tres medidas:

1. **Tiempo de respuesta de la transcripción:** desde que el usuario empieza a
   hablar hasta que aparece texto en la barra.
2. **Tiempo hasta la primera seña:** "tiempo desde que el texto se envía a
   traducir, al pulsar Traducir o, en el modo TR, al cerrarse la frase, hasta que
   el avatar ejecuta la primera seña". **Esta es la de RNF-DES-01, punto por
   punto.**
3. **Legibilidad del avatar:** "grado en que los usuarios de LSC comprenden las
   señas ejecutadas, evaluado con los criterios de efectividad, eficiencia y
   satisfacción de la norma ISO 9241-11".

**Qué hace la app.** Mide tres cosas por logcat, ninguna es el total
(`ui/state/SignAiMetrics.kt`): `onTurn` (frase → `done` del backend),
`onSignStart` (llega el `segment` → primer movimiento) y `onPlayback` (duración
de la representación). El reloj del turno que necesita el total ya existe
(`MainScreenState.kt:329`) y el `signStart` que reporta la escena también
(`MainScreenState.kt:188-195`): **las dos piezas existen, el total no está armado.**

**Veredicto.** El documento sí recogió el cambio. La app no.

**Trabajo en la app.** Armar el total. También falta la medida 1 (transcripción),
que no es código de la métrica de representación sino del STT: se mide allá.

**Lo que no es código.** La medida 3, legibilidad con ISO 9241-11, es un estudio
con usuarios del LSC. No se resuelve tocando la app.

---

### A14 — La app instrumenta sola

**Decisión del 10-16.** Sin problema: que instrumente sola.

**Qué dice el SRS ahora.** No lo menciona. No hay sección de observabilidad, de
registro ni de trazas, y no aparece ninguno de los tags (`SignAiMetrics`,
`SignAiNet`, `SignAIWeb`, `SignAiState`).

**Veredicto.** Sigue sin definirse, igual que el 10-16. No molesta: la app
instrumenta sola y el documento no lo prohíbe. Sin trabajo.

---

### A15 — El canal HTTP tiene más de una operación

**Decisión del 10-16.** Queda pendiente, depende de si habrá assets remotos.

**Qué dice el SRS ahora.** **Cerrado a un endpoint.** En §Comunicaciones §Consulta
de estado: "la aplicación realiza peticiones HTTPS al punto de acceso `/health`
del servidor, con respuesta en formato JSON, para comprobar su disponibilidad y
leer su configuración." Y en §Interfaces con el Sistema lo lista como el único
punto HTTP del servidor.

Y el SRS aclara de dónde sale la animación, que es lo que cerraba la duda:
"por cada glosa su disponibilidad en el catálogo y la referencia a su animación,
**que la aplicación usa para ubicar el archivo ya incluido en la aplicación**".

**Veredicto.** El documento dice que los assets son locales. `SignAssetRepository`
es código que nunca se ejecuta con el catálogo actual.

**Trabajo en la app.** Ninguno obligatorio. La decisión pendiente es si ese
código muerto se borra o se deja por si más adelante hay assets remotos. Pregunta
al owner.

---

### B1 — El modo manual: la app está bien

**Decisión del 10-16.** La app está bien.

**Qué dice el SRS ahora.** Coincide. §Modos de envío, modo dictado: "el texto
queda en la barra de transcripción para que el usuario lo revise, lo corrija si
es necesario y lo envíe con el botón Traducir".

**Veredicto.** Cerrado. Sin trabajo.

---

### B2 — La conservación del texto

**Decisión del 10-16.** El documento quedó atrás.

**Qué dice el SRS ahora.** Coincide, y en tres lugares: estado *Desconectado*,
"El texto pendiente no se pierde"; estado *Error*, "el texto se conserva"; y en
§Alcance, que sin internet "la pantalla indica que la aplicación no puede
funcionar sin él".

**Veredicto.** Cerrado. Sin trabajo.

---

### B3 — La edición de la transcripción

**Decisión del 10-16.** El documento quedó atrás.

**Qué dice el SRS ahora.** Coincide **exacto** con lo que ya hace la app. En
§Interfaz con el Usuario: "Mientras el micrófono está activo, la barra solo
muestra el texto; al apagarse, el texto puede editarse." Y en el estado *Texto
listo*: "puede editarse y espera a que el usuario pulse Traducir".

**Veredicto.** Cerrado. Sin trabajo.

---

### B4 — El tamaño del catálogo

**Decisión del 10-16.** El documento quedó atrás. En la app no hay nada que
cambiar.

**Qué dice el SRS ahora.** Dice **50**, en tres lugares: §Alcance ("catálogo
cerrado de 50 señas"), §Restricciones de Memoria ("las 50 entradas del catálogo
de señas se cargan en memoria") y §Hardware ("las animaciones de las 50 señas se
incluyen dentro del paquete de instalación").

**Cuál es el estado real.** La tabla `catalog` de Neon tiene las 50 glosas de
LSC50, sembradas el 2026-10-14. De esas 50, **32 tienen animación exportada** y
están en el APK; las otras 18 (`0032`–`0049`) llegan con `available: true` y una
ruta que no existe en el dispositivo.

**Veredicto. Discrepancia nueva.** El SRS afirma que las 50 animaciones están en
el APK. Hoy no es así. O se exportan las 18 que faltan, o el SRS dice 32. Es
decisión del owner y es el mismo bloqueo que B5.

---

### B5 — La asociación entre glosa y animación

**Decisión del 10-16.** Hay que resolverlo; el funcionamiento de hoy tiene
sentido. Quedaba atado a si se exportan las 18 que faltan.

**Qué dice el SRS ahora.** **Documentada.** En §Alcance: "cada glosa generada se
verifica contra un catálogo cerrado de 50 señas para obtener la ruta de su
animación". Y en §Comunicaciones: "por cada glosa su disponibilidad en el
catálogo y la referencia a su animación, que la aplicación usa para ubicar el
archivo ya incluido en la aplicación".

**Qué hace la app.** La asociación existe y es `same-origin`: el backend manda
`glb_animation_path` con la forma `signs/<id>.glb` y
`SignsCatalog.localAssetOrNull()` lo valida contra las 32 señas realmente
exportadas. Verificado en el Redmi: una cola de 3 señas con cero peticiones HTTP.

**Veredicto.** El requisito está resuelto y la app lo cumple. Lo que sigue
abierto es **solo** la parte de las 18 glosas sin animación, igual que el 10-16:
llegan con ruta propia que no existe y caen a la seña por defecto o se saltan,
según el ajuste de seña de prueba.

---

### B6 — La transición entre señas

**Decisión del 10-16.** El documento quedó atrás. La fundición se mantiene.

**Qué dice el SRS ahora.** Coincide con la app, en cuatro lugares. §Alcance:
"con transiciones fluidas entre señas mediante interpolación de movimiento", y
en la delimitación: "El avatar reproduce señas grabadas y las concatena mediante
interpolación". §Interfaz: "con sus expresiones faciales y las interpolaciones
entre una seña y la siguiente". Y el ajuste *Reducir animaciones* documenta el
corte directo.

**Veredicto.** Cerrado. La fundición de 100 ms (`crossFadeTo`, `app.js:502-510`)
se mantiene. Sin trabajo.

---

### B7 — La ubicación de las animaciones

**Decisión del 10-16.** El documento quedó atrás.

**Qué dice el SRS ahora.** Documentada, con dos problemas. §Hardware y
§Restricciones de Memoria: "el modelo del avatar y las animaciones de las 50
señas se incluyen dentro del paquete de instalación de la aplicación (APK), por
lo que el tamaño determina el espacio que ocupa la aplicación instalada."

1. Dice **50** y hay 32 (igual que B4).
2. Dejó el marcador literal `[PENDIENTE: tamaño del APK con todos los archivos
   GLB.]` en las líneas 931 y 1154.

**Veredicto.** El requisito de espacio está cubierto. Sin trabajo en la app.
Completar el tamaño del APK y corregir el 50 → 32 lo hace el owner en el `.tex`.

---

### B8 — No hay selector de modo

**Decisión del 10-16.** Por ahora son dos interruptores fijos. El documento quedó
atrás.

**Qué dice el SRS ahora.** Coincide con la app, sin selector. §Operaciones: "el
usuario oyente elige entre dos modos de envío del texto: el modo dictado y el
modo TR". §Interfaz: "Botón de micrófono (dictado)" y "Botón TR" como dos
elementos distintos, con que "nunca están activos al mismo tiempo". §Modos de
envío: "se activa con el botón TR... El modo se mantiene hasta que el usuario
vuelve a pulsar el botón TR".

**Veredicto.** Cerrado. Sin trabajo.

---

### B9 — El nombre del sistema

**Decisión del 10-16.** El documento quedó atrás. Ahora es SignALL.

**Qué dice el SRS ahora.** No se actualizó. **"SignAI" aparece 25 veces** y
**"SignALL" no aparece ni una vez**, ni siquiera en §Alcance, donde va en
\textit{}.

**Veredicto.** El SRS quedó atrás del rebrand del 2026-10-16. Lo corrige el owner
en el `.tex`; la sesión no escribe en Overleaf.

---

## 3. Lo que hay que tocar en la app

### 3.1 Con el documento ya cerrado: se adapta la app

| ID | Qué hay que hacer | Dónde | Requisito que lo pide |
|---|---|---|---|
| A1 | Que el tercer estado se vea, no solo se describa | `ui/screens/bottombar/SignAiBottomBar.kt`, `ui/state/MainScreenState.kt` | SRS, tabla `tab:estados-ui`, fila *Texto listo* |
| A2 | Pedir el permiso de micrófono al abrir la app | `ui/MainScreen.kt:78-103` | SRS §Interfaz con el Usuario, párrafo de permisos |
| A3 | `wss://` por default, con bypass de desarrollo | `data/SignAiBackendConfig.kt:35`, `res/xml/network_security_config.xml`, `app/build.gradle.kts:15` | SRS §Interfaces de Comunicación |
| A5 | Avisar "Seña no disponible" glosa por glosa | `ui/state/MainScreenState.kt:604-648`, `:1069` | SRS §Alcance + tabla, estado *Reproduciendo* |
| A7 | De 9 estados a 7 | `ui/state/PlaybackState.kt`, `ui/state/MainScreenState.kt` | SRS, tabla `tab:estados-ui` |
| A13 | Armar la métrica de latencia de punta a punta | `ui/state/SignAiMetrics.kt`, `MainScreenState.kt:329` y `:188-195` | SRS §Aspectos de medición |

A7 y A4 son el mismo trabajo: el estado *Error* y el *Desconectado* de la tabla
son los dos que la app no tiene como estados de la máquina.

### 3.2 Lo que necesita una decisión del owner antes de tocar código

| ID | La decisión | Por qué no se puede asumir |
|---|---|---|
| A4 | Qué pasa con el turno en vuelo cuando cae la conexión; límite de intentos; reinicio del contador | El SRS no lo dice. Adaptar la app sin que el documento lo precise es inventar el requisito |
| A6 | Si los marcadores no manuales se hacen en la app o se sacan del SRS | El 10-16 los dejó en pausa; el SRS los pide. Cambió la premisa |
| A7 | Siete estados exactos, o siete más distinguir dictado/TR | El SRS no lista la distinción, pero su texto sí la usa |
| B4/B5 | Si se exportan las 18 señas que faltan o el SRS dice 32 | Es trabajo de las señas, no de la app |
| A15 | Si `SignAssetRepository` se borra o se deja | El SRS cerró a `/health`; borrar código no es lo mismo que cumplir el requisito |

### 3.3 Lo que no hay que tocar en la app

A8, A9, A10, A11, A12, A14, B1, B2, B3, B6, B7, B8.

En todos estos el SRS quedó a favor de lo que ya hace la app, o ya no hay
diferencia.

---

## 4. Lo pendiente en Overleaf

**Lo corrige el owner.** Esta sesión no escribe en Overleaf.

1. §3 del SRS vacía, con los subtítulos de gaming de otra plantilla
   ("Jugar", "Registro y Autenticación", "Administración", "Consultas"), y sin
   los identificadores `RF-*` / `RNF-*` que usan `v0.15` y el plan de pruebas.
2. El nombre: 25 "SignAI", cero "SignALL".
3. "50 animaciones dentro del APK" → son 32.
4. Los dos `[PENDIENTE: tamaño del APK...]` de las líneas 931 y 1154.
5. Precisar A4: qué pasa con el turno en vuelo al caer la conexión.
6. A6: pedir los marcadores no manuales o sacarlos del texto.

---

## 5. Método y límites

- Se leyó el `main.tex` completo del proyecto Overleaf **SRS**, el 2026-10-17,
  con las herramientas de solo lectura. **No se escribió nada.**
- Los dos diagramas del proyecto (`images/*.png`) no son texto y no se pudieron
  revisar. Los `.drawio` de referencia viven en `../sign-ai-docs`, no en Overleaf.
- La revisión de los repos hermanos (`../sign-ai-backend`, `../sign-ai-stt`) no
  se hizo: este documento es SRS ↔ app. Lo que el SRS afirma del lado servidor
  (Whisper *medium* en `float16`, 2–3 GB de VRAM, búfer de 8 s, CUDA 12, Three.js
  0.169.0, Compose 2024.12.01, Neon PostgreSQL 16) está sin contrastar.
- Un punto sin cerrar: el SRS **no dice si las 50 animaciones son las 50 de
  LSC50 o si el corpus se recortó**. Si el recorte es real, el número del
  documento está bien y lo que hay que corregir es el seed de Neon, no el `.tex`.