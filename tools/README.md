# `tools/` — ayudas para desarrollar y probar

> Esta carpeta **no es parte de la app**: es el kit de herramientas con el
> que opera el desarrollador (humano o agente) mientras prueba en el
> emulador/dispositivo.
>
> **Si sos un agente**, cargá primero el skill `controlar-app-movil`
> (`.opencode/skills/controlar-app-movil/`): trae la decisión de qué usar, los
> workflows y los errores ya pisados, en menos tokens. Este README es el índice.

## Contenido

- **`verificar-tiempo-real.sh`** — **empezá por acá** si tocás el pipeline de
  voz. Un comando que corre la verificación completa: salud de los dos
  servicios, el divisor de oraciones y el protocolo del WebSocket, los 28
  fixtures de audio de punta a punta, las métricas y los chequeos del teléfono.
  Falla si la primera seña del pipeline se pasa del presupuesto de 1200 ms p95.
  Bloques: `contrato`, `fixtures`, `flujo`, `latencia`, `metricas`,
  `dispositivo`, `todo`.
- **`verificar-fases1-3.py`** — el escenario de las fases 1 y 3 del plan de
  implementación 2, en 18 comprobaciones y un comando. Consulta Neon con `psql`
  (unicidad de la glosa, glosa 29 `JUICIOSO`, 50 filas, canónico), el backend
  (`/health`, `/metrics.json` con el modelo de embeddings) y el WebSocket de
  punta a punta (preguntas de contenido sin `?`, relativos que no son preguntas,
  y que la frase repetida salga del caché semántico). Necesita el backend
  levantado y `DATABASE_URL` en `.envJulian`. Sale con código 1 si algo falla:
  `python3 tools/verificar-fases1-3.py`.
- **`medir-glosado.py`** — cuántas frases de referencia glosa bien el sistema, de punta a
  punta. Lee las frases y sus glosas de `../sign-ai-docs/borradores/plan-pruebas/02-datos/`
  (no hay una segunda copia) y las manda por el WebSocket: `fr01` son las 30 de PR-INT-02 y
  `fr02` las 90 opciones, que sirven de conjunto de validación porque no están en el prompt.
  Cada línea dice si la frase salió de caché o del modelo, y el resumen avisa si la caché
  estaba sucia. **Hay que vaciarla antes de medir**: `psql "$DATABASE_URL" -c "DELETE FROM
  semantic_cache;"` y reiniciar el backend. Ojo con FR-02: sus tres variantes por frase se
  confunden entre sí en el caché semántico (5 aciertos falsos de 90, medido el 2026-11-06).
- **`TIEMPO-REAL.md`** — la guía de pruebas del modo en vivo: qué se midió y
  cómo repetirlo, qué frases decir, cuáles de las 50 señas tienen animación,
  qué significa cada métrica, los problemas ya pisados y **qué no se verificó y
  por qué**.
- **`medir-primera-sena.py`** — de dónde sale la primera seña. Reproduce los 28
  fixtures por el socket, espera el `final`, manda la frase como `text` (lo que
  hace la app) y mide dos tramos: del primer sample de audio a la seña, y de la
  transcripción final a la seña — este último es `rt_first_sign_ms`. **Para
  medir en frío hay que reiniciar el backend**: `SEMANTIC_CACHE_PRELOAD` deja la
  caché en memoria y `DELETE FROM semantic_cache;` solo no alcanza.
- **`medir-retraso.py`** — ¿el avatar alcanza a representar mientras el usuario habla?
  Compara, con las duraciones reales del `manifest.json`, cuánto tarda el usuario
  en decir cada frase contra cuánto tarda el avatar en representarla. Es lo que
  sostiene (o no) el adelanto automático de `app.js`.
- **`dev.sh`** — wrapper sobre `mobilecli` (mobile-next) para manejar el
  emulador Android. Lista, captura, toca, escribe, instala el APK,
  lanza/termina la app, lee logs, dump del UI tree.
- **`HERRAMIENTAS.md`** — referencia completa del stack de control: las tools
  de `mobile-mcp`, cómo elegir selector, los `contentDescription` que existen
  en la app, y por qué Maestro no anda en el Redmi. Es largo: no lo cargues de
  entrada.
- **`pinch.sh`** — pinch de dos dedos por `sendevent`, para probar el zoom en el
  emulador. No hay otra forma: `adb input` y `mobile-mcp` son de un solo
  puntero.
- **`maestro/`** — flows YAML repetibles: arranque, drawers, traducción +
  historial, y una plantilla.
- **`rendimiento/`** — medir de verdad si la app va lenta. Su propio
  [`README.md`](rendimiento/README.md) tiene las recetas y las trampas.
- **`pack_avatars.py`** — **el pipeline de assets 3D de la app**. Toma los dos
  originales de `../signai-avatar/models/` (`Juan.glb`, 34,27 MB, y `Pauline.glb`,
  33,88 MB; personaje + cara + 200 clips cada uno, rama `dev`), verifica
  skins, articulaciones y nombres de clip, hornea la pose de descanso, **poda los
  morph targets que ninguna variante anima** y compacta el buffer. Escribe
  `assets/web/models/juan.glb` (28,10 MB), `pauline.glb` (27,97 MB; los dos
  conservan además 11 morph targets de la boca para `humanize.js`),
  `models/avatars.json` (inventario con hashes), `manifest.json` y
  `SignsCatalog.kt`. Cada seña trae cuatro marcas faciales (`_afirmativo`,
  `_pregunta`, `_negacion`, `_orden`) y no hay clip base. La escena resuelve
  `signs/<id>.glb` por nombre contra los clips del avatar elegido, sin descargar
  nada. Sin dependencias externas (no necesita Blender ni Node). Uso:
  `python3 tools/pack_avatars.py [--dry-run]`.
- **`extract_nmm.py`** — saca **los marcadores no manuales de cada seña** del
  dataset LSC50 y escribe `assets/web/nmm.json` (104 KB). Lee los 468 puntos de la
  cara (cámara de cara, 50 fps) y los 33 del cuerpo de la toma `0000_0000`, alinea
  cada clip con su video por la altura de las muñecas y guarda, a 24 fps, el giro de
  la cabeza, la inclinación del tronco y los pesos de boca, cejas, ojos y mejillas.
  No toca los `.glb`. `--dry-run` imprime la tabla de alineación de las 50 señas.
  Necesita `numpy` y la carpeta `../Prototipo Tesis/data/LANDMARKS` (o `--landmarks`).
- **`test_humanize.mjs`** — comprueba que la capa `humanize.js` no acumula ni altera
  la pose de los clips. Necesita `three` instalado aparte; el encabezado dice cómo.
- **`pack_signall.py`** — el pipeline **anterior**, de un solo avatar: empaquetaba
  `../material-3d/GLB SignALL/` en `models/avatar.glb` (12,98 MB, borrado el
  2026-10-26). Ya no se usa; sigue en el repo porque `pack_avatars.py` le importa
  `repack` y `morphs_to_keep`.
- **`split_signs.py`** — el pipeline **más viejo**: separa los 40 exports por seña
  (`../material-3d/GLB Senas/`, con el muñeco adentro en cada uno) en `models/avatar.glb` +
  `signs/<id>.glb`. Queda para rearmar el layout viejo o comparar; el camino
  vigente es `pack_avatars.py`. **Los 32 `.glb` que escribía en
  `assets/web/signs/` se borraron el 2026-10-22** (5,7 MB en el APK que ya nadie
  pedía: todas las señas salen del clip del archivo del avatar). El script sigue
  porque `pack_avatars.py` le importa `write_catalog`, y porque es la única forma
  de regenerar el layout viejo si hace falta.
- **`glb_pose.py`** — leer un `.glb`, recorrer el esqueleto (FK), samplear un
  clip y reconstruir la pose de bind desde las `inverseBindMatrices`. Es la
  base de las dos herramientas siguientes y el **único lector de GLB del repo**
  (el de `split_signs.py` se mudó acá). Sin dependencias.
- **`rest_pose.py`** — deja el avatar con los **brazos abajo** en reposo,
  copiando las rotaciones de la cadena de brazos del primer keyframe de
  `0029` (`signs/0029_0000_0000.glb` o el clip del mismo nombre dentro del
  avatar empaquetado). Idempotente, y frena la corrida si el export no es la
  pose de bind (ahí deformaría la malla en silencio).
- **`generar-marca.py`** — genera **los assets de la marca** desde los dos PNG
  maestros de `docs/marca/` (`icono-signall.png`, `logo-signall.png`). Escribe el
  adaptive icon (`ic_launcher_foreground.xml` + `ic_launcher_monochrome.xml` +
  `ic_launcher{,_legacy,_round}.xml`), `drawable-xxhdpi/ic_signall.png`, las dos
  variantes del wordmark (`logo_signall_on_light/on_dark.png`), y parchea
  `ic_launcher_background` en `values/colors.xml` y `app_name` en
  `values/strings.xml`. **Correrlo solo si cambian los maestros**: ni
  `ic_launcher_foreground.xml` ni los PNG se editan a mano. Necesita **potrace**
  y **Pillow** (los vectoriza); verifica solo que la curva trazada caiga dentro
  de la máscara y que la mano entre en el recuadro 18..90 del viewport.
  ⚠️ potrace escribe **polybezier**: un path con muchas groups, y **cada group es
  relativo a su propio arranque**, no encadenado con el anterior, en un espacio
  `y` para arriba (`translate(0,H) scale(1,-1)`). Interpretarlo mal da una mano
  plausible y equivocada — por eso están los asserts del bbox.

  ```bash
  python3 tools/rest_pose.py --dry-run   # el informe, sin escribir
  ```

- **`preview_pose.py`** — render offline de una pose (skinning lineal + el mismo
  `pivot` y la misma cámara que `app.js`, que además lee del archivo): mirar una
  pose sin instalar la app. Genera las comparaciones de
  `openspec/changes/archive/2026-10-17-reposo-y-crossfade/img/`. Es el único de los tres que pide
  `numpy` y `PIL`; el pipeline de assets no.
- **`measure_face_markers.py`** — cuánto mueve la cara cada variante facial.
  Lee el alcance geométrico de cada morph target (mm a peso 1) y las curvas de
  pesos de los canales `weights`, y compara cada variante contra `_afirmativo`
  —la cara que la app usa por defecto— en la misma seña y en tiempo normalizado.
  Sirve para decidir o revisar el mapeo tipo gramatical → variante sin
  renderizar. Salida: tabla Markdown y JSON con el detalle por seña y por avatar.
  La medición del 2026-11-05 (cuatro caras, layout de 200 clips) está en
  `docs/mediciones/marcadores-2026-11-05.md`; la del 2026-11-04 es historia del
  layout anterior. Sin dependencias.
- **`local_stt.py`** — transcripción local con `whisper.cpp`, para probar el
  micrófono sin backend (`--stt` en `mock_backend.py`).

- **`mock_backend.py`** — backend falso para probar sin Neon. Resuelve **glosa → seña contra la misma tabla LSC50**
  que `seed_catalog.py` siembra en Neon, así que se parece a producción en vez
  de inventar rutas: una glosa de las 50 siempre trae su `signs/<id>.glb`
  —incluso si la animación todavía no está exportada— y una que no está en
  LSC50 viene `available: false`. Antes mapeaba las glosas a los `.glb` que había
  en disco, lo que hacía **imposible reproducir el caso de las 18 sin
  animación**, y por eso el 404 garantizado de esas rutas nunca se vio en el
  dispositivo (ver `GLS-10` en el spec).

  ```bash
  # glosas fijas para un caso puntual, sin LLM
  python3 -u tools/mock_backend.py --glosses "POR FAVOR,MUCHO,¿CÓMO ESTÁS?"

  # tipo de oración forzado, para ver el marcador facial que le toca
  python3 -u tools/mock_backend.py --type NEGATIVE

  # asset remoto en vez del path dentro del APK (ejercita la descarga)
  python3 -u tools/mock_backend.py --assets-remote
  ```

  `--type` acepta los cinco del backend (`AFFIRMATIVE`, `YES_NO_QUESTION`,
  `CONTENT_QUESTION`, `NEGATIVE`, `IMPERATIVE`) y es la forma de probar los
  marcadores faciales sin adivinar el tipo de una frase. En modo local no
  necesita ningún archivo en disco: `signs/<id>.glb` es el identificador del
  contrato y el clip sale del avatar empaquetado.

  El `-u` no es opcional cuando redirigís la salida a un archivo: sin él Python
  bloquea el stdout y el log aparece vacío aunque el backend esté sirviendo.
  Y loguea los `GET` de `.glb` que no sirve, que es la evidencia de si la app
  está pidiendo animaciones que no existen.

### Pipeline viejo de landmarks (muerto)

Armaba el formato anterior —50 JSON de landmarks de MediaPipe que `app.js`
reproducía con un motor procedural de retargeting. Desde el 2026-10-13 la app
carga **GLB con la pose horneada** y no los usa.

**Lo que quedaba de ese pipeline se borró**: `build_signs.py`,
`patch_webview_glue.py`, `splice_movement.py` y los CSVs de muestra de
`data/LANDMARKS/` el 2026-10-22; `pose_filter.py` y `hand_corrector.py` el
2026-10-23, cuando quedaron sin su único importador y sin datos. El formato de
los CSVs sigue documentado en `openspec/specs/dataset/spec.md`, que es donde
sirve de referencia.

## `dev.sh` rápido

```bash
tools/dev.sh devices                  # lista dispositivos
tools/dev.sh shot antes               # captura a /tmp/shots/antes.png
tools/dev.sh tap 540 1200             # tap sin escala, coords reales del device
tools/dev.sh text "hola"              # escribir
tools/dev.sh back                     # atrás
tools/dev.sh tree                     # UI tree con refs @eN
tools/dev.sh install-debug            # ./gradlew installDebug + relanzar
tools/dev.sh logcat-tag SignAIWeb 80  # últimas 80 líneas del bridge JS→Kotlin
tools/dev.sh log 40                   # últimas líneas del device log
tools/dev.sh doctor                   # valida entorno (mobilewright doctor)
```

El package por defecto es `com.signai.webview` (este repo). Se puede
sobrescribir con `$APP_PKG` y `$APP_ACTIVITY`. El device se fija con
`$MOBILECLI_DEVICE`.

**Sin escala**: `mobilecli` trabaja en píxeles reales del device (1080×2400),
las capturas y las coords de tap/swipe están en la misma unidad. Si algo no
responde, **no** es un factor de escala — es que el árbol cambió o el elemento
no estaba donde pensabas. Usá `tools/dev.sh tree` para ver qué hay.

## Operar desde un agente

Cuando el que opera el dispositivo es un agente (Claude, opencode), la
vía primaria es el **MCP `mobile-mcp`** de mobile-next
([mobile-next/mobile-mcp](https://github.com/mobile-next/mobile-mcp)),
configurado en `opencode.json` de este repo. Expone tools como:

- `mobile_list_available_devices` — lista emuladores/dispositivos.
- `mobile_take_screenshot` — devuelve PNG.
- `mobile_list_elements_on_screen` — UI tree con refs `@eN` y bounding
  boxes. **Usar siempre antes de tocar**, en lugar de adivinar
  coordenadas.
- `mobile_click_on_screen_at_coordinates`, `mobile_type_keys`,
  `mobile_press_button`, `mobile_swipe_on_screen` — input básico.
- `mobile_install_app`, `mobile_launch_app`, `mobile_terminate_app` —
  ciclo de vida de la app (instalar APK, lanzar activity, terminar).
- `mobile_get_device_logs` — logs del device (filtrar por package).
- `mobile_batch_commands` — agrupa una secuencia de taps y tipeos en un
  round-trip. Cada llamada es un viaje al device: agrupá.

`mobile-mcp` está construido sobre `mobilecli`, así que las dos capas ven
el mismo device y comparten el daemon en `~/.mobilecli/`.

### La limitación que hay que tener presente

**El avatar 3D no existe para estas herramientas.** En el árbol sale como
`android.webkit.WebView` **sin ningún hijo**: el canvas de Three.js no publica
nada a accesibilidad. No se puede tocar ni inspeccionar el avatar con
`mobile-mcp`, Maestro ni `uiautomator`.

Para el estado de la escena:

- `tools/dev.sh logcat-tag SignAIWeb 80` — el bridge JS→Kotlin.
- El **modo debug** de la app (Ajustes → AVATAR → depurar), que enciende el
  overlay `#status` con el FPS en pantalla.

Y como el WebView es un `AndroidView`, es un View real: **se come cada toque
dentro de sus bounds**. Si un drawer no abre, el problema es el layering de
`EdgeDrawer.kt` (ver `AGENTS.md` § Gotchas), no el gesto.

## El flujo de "verificar un cambio"

El `AGENTS.md` § Build dice que es **obligatorio** instalar la app en el
device/emulador después de cada cambio. Con este tooling:

```bash
# humano o agente: un solo comando
tools/dev.sh install-debug
tools/dev.sh shot post-build
tools/dev.sh logcat-tag SignAIWeb 40   # ver mensajes del bridge JS↔Kotlin
```

Con MCP, la versión agente es `mobile_install_app` + `mobile_launch_app`
+ `mobile_take_screenshot` + `mobile_get_device_logs`.

## Maestro

```bash
export PATH="$PATH:$HOME/.maestro/bin"
maestro test tools/maestro/smoke-avatar.yaml
```

Cuatro flows en `tools/maestro/`: `smoke-avatar.yaml`, `drawers.yaml`,
`historial.yaml` y `ejemplo-flow.yaml` (plantilla).

Los selectors salen de los `contentDescription` del `.semantics {}` de Compose.
La tabla de los que ya existen está en
[`HERRAMIENTAS.md`](HERRAMIENTAS.md).

## Rendimiento

```bash
# la principal: reparto por fotograma y latencia de toque
tools/rendimiento/gfxinfo.py reproducir --sec 8 \
    --run "adb -s 1ed6699f shell 'input tap 540 2000; sleep 5'"

# el mismo gesto desde el punto de vista del ojo
tools/rendimiento/sf-medir.sh reproducir 8 -- \
    "adb -s 1ed6699f shell 'input tap 540 2000; sleep 5'"
```

`tools/rendimiento/README.md` tiene el resto: qué mide cada script, las recetas,
y las trampas ya pisadas.

## Stack del tooling del agente

- **`mobilecli`** — CLI unificado para dispositivos (reemplaza `adb`).
  Instalado global vía `npm install -g mobilecli`.
- **`mobile-mcp`** — servidor MCP que envuelve mobilecli como tools para
  agentes. Configurado en este repo en `opencode.json`. Telemetry off vía
  `MOBILEMCP_DISABLE_TELEMETRY=1`.
- **Maestro** `2.11.0` — flows YAML. `~/.maestro/bin`, ya en el `.zshrc`.
  **No anda en el Redmi (MIUI)**: su driver de Android revienta al arrancar con
  `Cannot call disconnect() while connecting UiAutomation`. Ver
  `HERRAMIENTAS.md`.
- **`mobilewright`** — instalado pero **no se usa** (no escribimos tests
  E2E). Su CLI queda disponible para `mobilewright doctor`/`inspect`/
  `screenshot`/`codegen` si alguna vez hace falta.
- **`mobilecli daemon`** — cachea devices entre invocaciones. Expira a
  los 30 min de inactividad. `mobilecli daemon status|stop`.
- **`tools/dev.sh`** — wrapper sobre `mobilecli`. Convive con
  `mock_backend.py`. Si algo se rompe, `adb` puro sigue disponible como
  fallback.

### Instalado y descartado — no lo propongas

- **`agent-device`** (Callstack) — competidor directo de `mobile-mcp`. `doctor`
  pasa, pero `snapshot` falla siempre con `UiAutomationService already
  registered`. Se reproduce en el emulador limpio, así que no es MIUI.
- **`e2e`** (`e2e.tester.army`) — su motor móvil depende de agent-device.