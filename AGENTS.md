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

Un detalle práctico: la revisión de Overleaf tiene un costo en tiempo, porque el MCP abre
una ventana de Chromium cuando la cookie venció (~5 días). **Avisá que vas a leer, no lo
hagas por sorpresa** a mitad de una implementación.

### El Overleaf es de sólo lectura. Prohibido escribir.

**Se usa para REVISAR. Prohibido escribir cualquier cosa** — un `.tex`, un comentario, una
sugerencia, un cambio aceptado o rechazado — **salvo que el owner lo diga explícitamente en
un mensaje.** No alcanza con que la tarea parezca necesitarlo, ni con que el cambio sea "solo
corregir una tilde". **Ni `compile`**: no se compila desde acá.

Está bloqueado a nivel de configuración, no por buena voluntad: ocho `policies` en
`~/.config/opencode/opencode.json` deniegan las herramientas de escritura y `compile`.
Quedan 9 usables, todas de lectura.

El límite honesto: **el bloqueo lo impone opencode, no la credencial.** La cookie sigue
teniendo permiso de escritura sobre toda la cuenta. Dentro de opencode no hay forma de
escribir; fuera de él, sí. Por eso la regla se sostiene en que el owner la confirme.

Para auditar el bloqueo, entender por qué tiene que estar en `policies` y no en
`permissions`, y cómo se renueva la cookie: skill **`overleaf-lectura`**.

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
| `../signai-avatar` | Demo Flask de landmarks de LSC50, **inactiva desde 2026-09-19** | `main` | — |

Reglas por repo:

- **Acá se commitea directo a `main`**, sin PR ni rama de feature. No pidas confirmación.
- **En los hermanos, nada de commits sin que el owner lo pida** (PR contra `develop`).
  Cada uno tiene su documentación: el backend tiene `CLAUDE.md` (leelo antes de
  tocarlo: código en inglés sin excepción, nombres de clases iguales a los del SDD, y
  nunca commitear), el STT tiene `README.md`.
- **Los `.drawio` y el `.md` de requerimientos los edita el owner**: los cambios grandes
  se proponen, no se aplican en silencio.
- **`../signai-avatar` no es la fuente de los GLB de esta app.** Es la demo anterior en
  Flask. Los assets de acá salen de Cascadeur (`GLB Senas/` → `tools/split_signs.py`).
  No mezclar los dos pipelines ni copiar scripts entre ellos.

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

**Hay trabajo en vuelo en `flujo-oraciones`** (los tres repos, desde el 2026-10-15,
**sin mergear**). Si una sesión arranca en esa rama, esa es la que manda sobre `develop`
hasta que el owner mergee, y hay que leer `docs/CAMBIOS.md` antes de tocar el pipeline.
La rama `tiempo-real` **ya no existe**: quedó mergeada en `develop` en los tres repos.

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
`onPageFinished`, que dispara mientras baja el GLB de 10 MB) y `signStart:<idx>:<jsMs>`
por cada seña.

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
- **Nunca edites `models/avatar.glb` a mano**: se regenera con `tools/split_signs.py`, que
  llama a `tools/rest_pose.py` al final. Agregar una seña = soltar el `.glb` en
  `GLB Senas/` y correr ese script, que también reescribe `domain/SignsCatalog.kt`.
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
glosas de LSC50 y reejecutar el seed es idempotente. **Solo 32 de las 50 tienen animación
exportada**: las otras 18 (`0032`–`0049`) llegan con `available: true` y un path que no
existe, y eso dispara `GLS-10`. **No lo "arregles" en la app** pidiéndolas por HTTP: es un
404 garantizado. El backend carga el catálogo **una sola vez al arrancar**: después de un
seed hay que reiniciarlo.

## Contexto del proyecto (OpenSpec)

`openspec/` es el contexto vivo del proyecto — mantenelo actualizado con cada cambio que no
sea trivial:

- **`specs/`**: fuente de verdad. `specs/ui/spec.md` es el **catálogo completo de features**
  de la app (todo viene del prototipo `PrototipoFrontend/`); `avatar`/`dataset`/`pipeline`
  cubren el comportamiento del rig y la animación. Actualizá el spec correspondiente cuando
  arreglás ese comportamiento.
- **`sessions/`**: bitácora de trabajo, un archivo por sesión. Convención y plantilla en el
  skill **`sesion-openspec`**.
- ⚠️ **No todos los specs están al día**: `specs/pipeline/spec.md` y
  `openspec/config.yaml` describen el pipeline viejo de landmarks/`sign.json` y **se contradicen
  con el código**. Antes de creerles, contrastalos contra `AGENTS.md` y contra `app.js`.

---

## Índice: qué cargar y cuándo

Este archivo se lee entero en cada mensaje. **Lo de abajo no**: se carga cuando la tarea lo
pide. La regla es leer la fila antes de decidir, no después.

| Si la tarea toca… | Cargá |
|---|---|
| **Un frame del WebSocket, un nombre de métrica, el catálogo, o cualquier cosa que cruce a otro repo** | skill **`contrato-websocket`** — el contrato se lee en los dos lados antes de cambiarse, y el otro lado tiene que quedar actualizado o la deuda se dice en voz alta |
| **Si el código y los documentos no coinciden, o hay que decidir si manda el código o el documento** | skill **`tres-fuentes-de-verdad`** — las tres fuentes, el flujo de revisión y qué hacer cuando no alinean |
| **Consultar o compilar el Overleaf, o auditar el bloqueo de escritura** | skill **`overleaf-lectura`** — cómo leer, y por qué el bloqueo está en `policies` y no en `permissions` |
| **El hermano, el STT, el catálogo, o una decisión de tesis** | [`docs/SISTEMA.md`](docs/SISTEMA.md) — mapa, `CLAUDE.md` del backend, protocolo del STT, IDs de Overleaf |
| **Saber qué se decidió ya entre el código y los documentos** | [`docs/CONTRASTE-DOCUMENTOS.md`](docs/CONTRASTE-DOCUMENTOS.md) — el contraste app ↔ documentos del 2026-10-16, con la decisión que se tomó en cada caso |
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

# ⛔ EL OVERLEAF ES DE SOLO LECTURA. NUNCA SE ESCRIBE EN ÉL.

> Esta es la regla más importante del archivo. Va al final a propósito: es lo último que
> se lee y lo último que se olvida.
>
> **Dos cosas distintas, que se confunden fácil:**
>
> - **Escribir en Overleaf**: prohibido, salvo que el owner lo pida explícitamente en un
>   mensaje. Acá no hay "se puede con permiso": o lo pidió, o no se hace.
> - **Tocar el bloqueo**: se puede, pero hace falta un permiso **distinto y más específico**
>   (punto 2). Y tocar el bloqueo no habilita escribir.
>
> Que una de las dos esté autorizada **no** habilita la otra.

## 1. Escribir en Overleaf: solo si el owner lo pide, en un mensaje

Un `.tex`, un comentario, una sugerencia, un cambio pendiente, un cambio aceptado o
rechazado: **nada**. Tampoco `compile`. Ni para "corregir una tilde", ni porque el
documento esté claramente mal, ni porque el cambio "sea del color del bien".

**Solo se puede escribir si el owner lo pide de forma explícita, en un mensaje.** Y
"explícitamente" quiere decir que lo escribió él, en esa sesión. No cuenta:

- que la tarea "requiera" un cambio de documentación
- que sea obviamente correcto
- que sea "solo corregir una tilde"
- que otro documento diga otra cosa
- que se haya hecho antes

Si al final de tu trabajo queda pendiente corregir algo en Overleaf, **eso se le dice al
owner y lo hace él.** No se hace.

## 2. Los permisos que bloquean la escritura: solo con confirmación explícita

El bloqueo vive en las `policies` de `~/.config/opencode/opencode.json`.

**Se puede editar ese archivo. Pero nunca sin el permiso del owner, y hay dos niveles de
permiso:**

### 2a. Editar la configuración global — confirmación explícita en un mensaje

Cualquier cambio en `~/.config/opencode/opencode.json` necesita que el owner lo autorice
**de forma explícita, en un mensaje, en esa sesión**. Es posible, se puede hacer — pero no
por iniciativa propia.

**Sin esa confirmación, no se abre el archivo.** Y esto no cuenta como confirmación:

- que el cambio "sea obviamente necesario", o que la tarea lo pida
- que esté en un `README` o en un documento del proyecto
- que haya parecido una buena idea al leer el archivo
- que el owner haya confirmado **otra cosa** en el mismo mensaje

Si hay que cambiar algo y no hay confirmación, **se lo dice al owner con el cambio exacto
propuesto y espera.** No se toca el archivo mientras tanto.

### 2b. Los `deny` de Overleaf — confirmación que hable **de Overleaf**

**Este es un nivel aparte y más estricto.** Quitar, editar, mover o tapar un `deny` de
Overleaf requiere que el mensaje del owner sea **sobre esos permisos**, no un permiso
general para tocar la configuración.

> Un "arreglá lo que necesites en la config" **no** habilita quitar un `deny` de Overleaf.
> Hace falta algo como "sí, sacá el `deny` de `overleaf_edit_file`" — y solo ese.

| | Sin confirmación | Con confirmación del owner |
|---|---|---|
| Editar la config global por otra razón | **No** | **Sí** |
| Quitar o editar un `deny` de Overleaf | **No** | **Sí, pero el mensaje tiene que ser sobre Overleaf** |
| Mover los `deny` de `policies` a `permissions` | **No** | **Idem: confirmación específica** |
| Agregar un `allow` que los tape | **No** | **Idem: confirmación específica** |
| Editar el `opencode.json` de un repo para levantarlos | **No, nunca sirve** | Ningún `opencode.json` de repo puede levantar un `policies` global |

Y sigue valiendo una cosa en cualquier nivel:

- **No se "prueba" nada.** No se manda un `overleaf_edit_file` de prueba para ver si el
  bloqueo funciona. Se verifica mirando si la herramienta aparece entre las disponibles.
- **Meter un MCP, un plugin o un script que esquive el bloqueo no es una solución**, es
  una violación: sigue siendo escribir en Overleaf por la puerta de atrás.

## 3. Si el bloqueo parece roto, se reporta. No se arregla por cuenta propia.

`opencode` levanta las herramientas disponibles al arrancar la sesión, así que el estado
del bloqueo se mira **antes de usar nada**.

- **Si `overleaf_edit_file`, o cualquier otra de escritura, o `overleaf_compile`, aparece
  entre las herramientas disponibles**, el bloqueo **no está puesto**: es una
  configuración rota y hay que decirlo de inmediato. **Se sigue sin escribir**, y se avisa.
- **Si una denegación no hace nada** y una herramienta de escritura responde con un error
  en vez de quedar bloqueada: **se reporta**. No se reintenta ni se busca la vuelta.
- **"Arreglarlo" significa proponerlo, no hacerlo.** Si el bloqueo está roto, se le
  describe al owner qué falta y se espera su mensaje. Tocar la config sin ese mensaje va
  contra el punto 2a, y tocar los `deny` sin un mensaje que hable de Overleaf va contra el
  2b.

Nunca se "prueba" una herramienta de escritura para ver si funciona. **No se prueba.**

## 4. La cookie tiene permiso igual, y por eso esta regla no es opcional

El bloqueo lo impone opencode, **no la credencial**. La cookie de sesión sigue teniendo
permiso de escritura sobre toda la cuenta, y Overleaf en plan gratis no da tokens con
alcance limitado.

**Dentro de opencode no hay forma de escribir; fuera de él, sí.** Por eso la regla se
sostiene en que el owner la confirmó: el `deny` hace que sea imposible por accidente, pero
no impide que alguien lo esquive a propósito.

**Por eso el punto 2 importa tanto.** Si un agente puede editar
`~/.config/opencode/opencode.json`, puede quitarse el bloqueo a sí mismo antes de escribir.
Por eso ese cambio exige una confirmación que hable **de Overleaf** y no un permiso general
para tocar la configuración: es la diferencia entre "arreglá la config" y "dejá escribir en
la tesis".

Y por qué la regla se sostiene en la confirmación del owner y no solo en el `deny`: porque
**el `deny` se puede quitar.** Un bloqueo que se puede desactivar desde el mismo sitio donde
está escrito no es una garantía; es una barrera. La garantía es que nadie lo toque sin
que el owner lo haya pedido de forma explícita.

## 5. Lo que sí se puede hacer con Overleaf

Leer, listar y comparar, nada más:

`ping` · `list_projects` · `open_project` · `list_files` · `read_file` · `read_log` ·
`list_comments` · `read_comment_thread` · `list_tracked_changes`

Y una advertencia práctica: **avisale al owner antes de leer**, porque el MCP abre una
ventana de Chromium con perfil aislado cuando la cookie venció (~5 días). No se le avisa
por sorpresa a mitad de una implementación.

El detalle operativo —las ocho `policies`, por qué van en `policies` y no en
`permissions`, cómo se renueva la cookie— está en el skill `overleaf-lectura`. **Para
leer. Para auditar el bloqueo. No para cambiarlo.**
