# Pipeline SignAll (WebView) — flujo de datos

> **Actualización del 2026-11-05:** los dos `.glb` de origen pasan a la versión del
> 2026-11-04 (`fc6f372`), con **cuatro marcas faciales** (`_afirmativo`, `_pregunta`,
> `_negacion`, `_orden`) y **sin clip base**. Cada archivo tiene **200 clips** (50 × 4),
> 102 articulaciones y los mismos canales de hueso entre variantes; solo cambian los pesos
> de la cara. `app.js` elige la variante por tipo de oración. `../material-3d/Avatares/`
> sigue siendo la versión anterior.
>
> **Actualización del 2026-10-27:** los dos `.glb` de origen pasan a leerse del repo
> hermano `../signall-avatar/models/` (rama `dev`, commit `cf014ef`), que versiona la
> versión con la **cadera centrada** del 2026-10-26. `tools/pack_avatars.py` no cambia
> más que la ruta: el resultado sigue teniendo 300 clips, 102 articulaciones y los mismos
> nombres y duraciones. `../material-3d/Avatares/` queda como versión anterior.
>
> **Actualización del 2026-10-26:** hay **dos archivos completos, uno por avatar**
> (`models/juan.glb`, 34,28 MB, y `models/pauline.glb`, 29,46 MB), armados por
> `tools/pack_avatars.py` desde `../signall-avatar/models/`. Cada uno trae el personaje y
> sus **300 clips** (50 señas × 6 variantes); la escena carga solo el avatar elegido en
> Ajustes. El `models/avatar.glb` de 12,98 MB se borró. El flujo de runtime no cambió.
>
> **Actualización del 2026-10-22:** el flujo de assets cambió. Ya no hay
> `models/avatar.glb` + `signs/<id>.glb`: hay **un solo** `models/avatar.glb` con el
> personaje y las 50 señas como clips `0000`..`0049`, armado por `tools/pack_signall.py`
> desde `../material-3d/GLB SignALL/`. Detalle en
> `openspec/sessions/2026-10-22-signall-un-glb-50-senas.md`.
>
> **Auditoría del 2026-10-24:** se contrastó cada afirmación contra el código y contra el
> GLB del APK. Se corrigieron el nombre de la raíz del armature, el número de morph
> targets, el tamaño del archivo, la ausencia de `stop()` en el puente, el encadenado por
> oración y la descripción de la cara. El detalle, en
> `openspec/sessions/2026-10-24-auditoria-pipeline-spec.md`.
>
> **Este archivo se reescribió entero el 2026-10-17.** La versión anterior describía el
> pipeline de landmarks con `sign.json`, que se reemplazó por clips GLB el 2026-10-13, y
> decía que la app era "100 % local, sin servidor", que es exactamente lo contrario del
> sistema actual. Los cuatro repos hermanos y el WebSocket ya existían. Si algo de acá
> contradice al código, **el código manda** — y se reporta.

## Flujo real

Hay **dos flujos distintos** y conviene no mezclarlos: uno es de assets, el otro es de
runtime.

### Flujo de assets — cómo llega una animación al APK

```
../signall-avatar/models/Juan.glb               los dos exports finales de Cascadeur:
../signall-avatar/models/Pauline.glb            personaje, esqueleto y 200 clips cada uno
      │                            (fuente versionada, rama dev; 34,27 y 33,88 MB)
      ▼
tools/pack_avatars.py              verifica skins, articulaciones y nombres de clip;
      │                            hornea la pose de descanso y poda los morph
      │                            targets que ninguna variante anima
      │  · models/juan.glb          ← 30,32 MB: personaje + cara + 200 clips
      │  · models/pauline.glb       ← 27,85 MB: idem
      │  · models/avatars.json      ← inventario: hashes, clips, articulaciones
      │  · manifest.json            ← inventario de las 50 señas y sus 4 variantes
      │  · domain/SignsCatalog.kt   ← reescrito, para que el asset y el código no divergan
      ▼
app/src/main/assets/web/           dentro del APK
```

Los **assets** se resuelven con rutas relativas, sin red: la escena indexa los clips al
cargar el avatar y resuelve `signs/<id>.glb` **por nombre** contra ese índice
(`embeddedClipsFor` en `app.js`), así que el path es el identificador de la seña y no un
archivo que exista en disco. La página sí carga Three.js r169 desde jsDelivr, así que el
WebView necesita INTERNET aunque el avatar y las señas sean locales.

El layout viejo (`tools/split_signs.py` → `models/` + `signs/`) sigue en el repo
—`pack_signall.py` le importa `write_catalog`— y `app.js` sigue soportando un `.glb` por
seña, pero `assets/web/signs/` se borró el 2026-10-22. El vigente son los **dos archivos
completos**, uno por avatar, desde el 2026-10-26.

**Nunca se editan `models/juan.glb` ni `models/pauline.glb` a mano.** Se regeneran.
**Nunca se edita `SignsCatalog.kt` a mano** tampoco: lo reescribe el generador, justamente
para que la lista de código y el asset no puedan divergir.

**Agregar o cambiar una seña** = regenerar los dos `.glb` desde
`../signall-avatar/models/` y correr `pack_avatars.py`. No es un paso más: es el único
procedimiento. `tools/pack_signall.py` es el pipeline viejo de un solo avatar y no procesa
estos archivos.

### Flujo de runtime — qué pasa cuando alguien habla

```
micrófono
  │  AudioRecord sobre VOICE_RECOGNITION, chunks de 30 ms de PCM16 mono 16 kHz
  ▼
WebSocket  ws://<backend>:8080/ws/translate          ← la app SOLO habla con el backend
  │                                                     nunca con el STT ni con Neon
  ├─ texto escrito  ──► {"type":"text"}  ──► se traduce ──► segment × N ──► done
  │
  └─ voz  ──► frames binarios PCM16 ──► el backend reenvía al STT
                     │  {"type":"end"} al cerrar la captura
                     ▼
              ../signall-stt  (faster-whisper + VAD)
                     │  transcript (muestra en vivo, NO se traduce)
                     │  final     (siempre llega, incluso vacío)
                     ▼
              la app muestra el texto y, en el modo en vivo, lo manda de
              vuelta como {"type":"text"} — la voz no se traduce sola
                     │
                     ▼
              el backend parte la frase en oraciones y traduce cada una
                     │
                     ▼
              segment {index, total, ...}   uno por oración, EN ORDEN
                     │
                     ▼
              MainScreenState.pendingSegments  (sortedMap por index)
                     │  flushSegments() entrega lo que haya, sin esperar a "total"
                     ▼
              escena: setQueue + play() con la primera oración
                      appendQueue con cada oración siguiente
                     │
                     ▼
              THREE.AnimationMixer reproduce el clip <id> embebido en
              el GLB del avatar elegido (resuelto por nombre) con crossfade de 100 ms
                     │
                     ▼
              window.SignAllBridge.postMessage("onPlaybackDone")
```

**El contrato son frames de WebSocket, no imports.** Un cambio aquí rompe en el hermano sin
que el compilador avise. El detalle está en [`docs/SISTEMA.md`](../../../docs/SISTEMA.md) y
en el skill `contrato-websocket`.

### Lo que la app **no** hace

- **No habla con el STT.** El audio va al backend y el backend lo reenvía.
- **No habla con Neon.** La base es del backend.
- **No traduce la voz.** La voz entra por el mismo socket pero vuelve como `transcript`, y
  esa transcripción no se traduce: solo lo que el cliente manda como `text`.
- **No espera a la frase entera.** Encadena cada oración apenas llega: `setQueue` + `play()`
  con la primera y `appendQueue` con las siguientes (D-1, 2026-10-16). No arma la cola al
  final.
- **No pide los `segment` de a uno.** El backend los manda todos, en orden.

## Formato de `manifest.json`

Lo escribe el generador de assets: `pack_avatars.py` con los dos archivos completos
(vigente desde el 2026-10-26), `split_signs.py` con el layout por seña. Es el inventario
de lo que hay en el APK:

```json
[{ "sign_id": "0000",
   "path": "signs/0000_0000_0000.glb",
   "clip": "0000",
   "bundled": "avatar",
   "variants": 1,
   "variant_names": ["0000"],
   "fps": 24, "frames": 59, "duration_s": 2.417, "tracks": 311,
   "faces": true,
   "bytes": 12978476, "sha256": "b5cacff4…" }]
```

Con los archivos completos, `bytes` y `sha256` son los **del archivo entero**, no los de un
clip: no existe un asset por seña. `gloss` va en `null` porque la glosa la pone el catálogo
del backend, no el asset. El `sha256` es lo que permite cruzar el APK contra el catálogo sin
abrir los binarios. Ojo con `faces`: `pack_avatars.py` lo pone en `true` cuando el clip
tiene algún canal de pesos (`weights`), no cuando tiene expresión facial — los canales de
pesos no son de expresión (ver § La cara).

## Nombres de los huesos — crítico

**Las exportaciones de Cascadeur usan PUNTOS.** Este es el dato que más se ha equivocado,
y se equivocó en las dos direcciones.

Verificado el **2026-10-17** contra el `avatar.glb` de entonces y contra el export original
de Cascadeur (`../material-3d/GLB Senas/0000 - GRACIAS.glb`), y **reverificado el 2026-10-24,
el 2026-10-26 y el 2026-10-27** contra los archivos vigentes (`models/juan.glb` y
`models/pauline.glb`): **102 articulaciones de skin** en todos ellos.

| Zona | Cómo es |
|---|---|
| Cadena del brazo | `shoulder.L`, `upper_arm.L`, `forearm.L`, `hand.L` — con punto, **sin prefijo** |
| Dedos y palma | `thumb.01.L`, `f_index.01.L`, `palm.01.L` … `palm.04.L` |
| Cara | `brow_*`, `eye_blink_open_*`, `eye_squint_*`, `eyeball*`, `jaw*`, `mouth_*` — **sí son joints del skin** (ver § La cara) |
| Raíz del armature | `HG_Branden` en el archivo único; `HG_Branden.001` en el export viejo |

La raíz **no es una articulación de skin** (los 102 joints empiezan en `spine`). En el
archivo vigente **no está animada**: ningún canal de los 50 clips la toca. En el export
viejo sí lo estaba, y de ahí viene la confusión. Cada clip actual de Juan tiene **312
canales** (contados en el GLB del APK el 2026-10-27): 306 son TRS de los 102 huesos y 6 son
pesos de morph targets de mallas
(`HG_Body_facial`, `HG_Chinos_Male.002`, `HG_Dress_Shirt_Collar_Male.002`,
`HG_Leather_Dress_Shoe_Male.002`, `HG_Sweater_1_Male.002`, `HG_Haircap_Eyelashes`). Por eso
`app.js` cuenta **nodos** y no huesos: contar solo huesos dejaría esos canales de pesos como
falsos "sin resolver".

> ### Dos trampas, y las dos costaron trabajo
>
> **1. La versión anterior de este spec y `openspec/config.yaml` decían que NINGÚN hueso
> usaba puntos**, y daban ejemplos como `DEF-upper_armL`. **Es falso**: los huesos de la
> cadena del brazo se llaman `upper_arm.L`, con punto. Un agente que confiara en eso
> renombraría huesos y rompería la animación sin ver ningún error.
>
> **2. Y después se inventó el otro extremo**: apareció una lista de huesos de cara
> `DEF-browTL` / `DEF-lidTL` / `DEF-lipTL` / `DEF-jaw_master` como si fueran
> articulaciones del rig. **Esos huesos no existen**: en el archivo hay **cero**
> articulating con prefijo `DEF-`.
>
> **Verificá contra el archivo antes de confiar en cualquier lista de nombres.**

### La cara se anima por morph targets, no por huesos

Los nodos de cara usan nombres al estilo ARKit y **son articulaciones del skin** (están
entre los 102 joints, no son nodos sueltos):

```
brow_dwn_L / brow_dwn_R   brow_outer_up_L / brow_outer_up_R   brow_inner_up
eye_blink_open_L / eye_blink_open_R   eye_squint_L / eye_squint_R
eyeball.L, eyeball.lookat.L, eyeball_lookat_master
jaw, jaw_upper, jaw_dwn_mouth_clsd, jaw_open_lt_rt_frwd
```

En los clips esos huesos **casi no se mueven**: el mayor rango de un componente del
cuaternión de rotación de un nodo de cara en los 50 clips base de un archivo anterior es
**0,0012** (`jaw`, clip `0015`), contra **1,996** del mayor hueso del cuerpo (`thigh.L`,
clip `0034`) —medido el 2026-10-24—. La expresión facial de los exports del 2026-11-04
vive en los **canales `weights`** de los morph targets, no en los huesos.

Desde el 2026-11-04 la escena **sí maneja esa cara**: cada seña tiene cuatro clips
(`_afirmativo`, `_pregunta`, `_negacion`, `_orden`) cuyos canales de hueso son idénticos y
cuyos pesos cambian; `pickVariant` elige uno según el tipo de oración que llega en el
`segment`. Ya no hay clip base: las cuatro variantes son caras gramaticales y la app usa
`afirmativo` como respaldo cuando el tipo no viene o no está en la tabla. Los dos archivos
vigentes conservan los morph targets que alguna de las 200 variantes anima —59 bloques en
Juan y 58 en Pauline, contados el 2026-11-05—.

Es el punto **A6** de `docs/historia/CONTRASTE-APPvsDOCUMENTOS.md`: el SRS pide un marcador facial
según el tipo de frase y desde el 2026-11-04 hay implementación para los cinco tipos del
backend, con cuatro caras (pregunta sí/no y pregunta de contenido comparten `_pregunta`).
Lo que falta es la validación lingüística de LSC, no el código.

## Constantes de la escena en `app.js` (valores reales)

Verificadas en `app/src/main/assets/web/app.js`:

| Variable | Valor | Para qué |
|---|---|---|
| `CROSSFADE_MS` | `100` | Fundición entre dos señas. Entre señas **no hay pose congelada ni hueco** |
| `MAX_DPR` | `1.75` | Tope de `setPixelRatio`. **A propósito** |
| `AVATAR_SOURCES` | `{ juan: "models/juan.glb", pauline: "models/pauline.glb" }` | Los dos personajes; el nombre pedido tiene que ser una clave de este objeto |
| `DEFAULT_SIGN_URL` | `signs/0000_0000_0000.glb` | Seña de apertura y de reserva |
| `BG_COLOR` | `#121316` | Fondo del canvas en oscuro (= `background` del tema) |
| `BG_LIGHT` | `#f4f4f2` | Fondo del canvas en claro |

### Por qué el tope de DPR, y por qué no `DoubleSide`

Sin tope, el Redmi Note 9S (dpr 2.75) renderiza 19,6 MP por fotograma y el GPU queda en
~37 ms/fotograma, con **99 % de jank**. Medido con `tools/rendimiento/gfxinfo.py`. Y no hay
que forzar `DoubleSide` en las mallas: es overdraw puro en mallas cerradas y costó **4 % más
de jank** para un cambio visual cero.

### Lo que ya no existe

Estas constantes pertenecían al **motor procedural** de landmarks, que se eliminó el
2026-10-13. **No aparecen en `app.js`** (verificado: cero coincidencias):

`smoothAlpha` · `FACE_ALPHA` · `Z_SCALE` · `FACE_REF_BROW` · `FACE_BROW_SCALE` ·
`FACE_JAW_MAX`

Si una tarea de avatar necesita ajustar algo así, no existe a qué ajustarlo: lo que se
mueve ahora es el clip horneado en el GLB.

## El puente Kotlin ↔ JS

**La fuente de verdad es `domain/SignAllCommands.kt`.** No esta lista.

### Kotlin → JS: `window.SignAll.<método>(…)`

Siempre con el guard `?.`, y siempre **aditivo**: borrar o renombrar uno rompe en silencio.

| Comando | Qué hace |
|---|---|
| `play()` | Arranca la cola desde el ítem 0; sin cola, repite la seña de arranque |
| `playFrom(i)` | Arranca desde `i` |
| `setQueue(json)` | **Reemplaza** la cola; el arranque lo hace `play()` o `playFrom()` |
| `appendQueue(json)` | **Agrega** al final sin reiniciar lo que está sonando |
| `stop()` | Corta lo que esté sonando y **vacía** la cola (estado *Desconectado* del SRS) |
| `setSpeed(f)` | Velocidad pedida en `0..1` (`0.5` = normal): `app.js` la escala por 2 y la limita a `0.1..3`. No hay adelanto automático. Un `0` cae en `0.5` por el operador lógico del JS, no en `0.1` |
| `setReduceMotion(on)` | Corte seco entre señas en vez de fundición (accesibilidad) |
| `setTheme(dark)` | El fondo del canvas sigue al tema de la app |
| `setDefaultSign(url)` | Seña de apertura y de reserva |
| `setAvatar(id)` | Cambia de avatar en caliente. El `id` tiene que ser `juan` o `pauline`; se ignora cualquier otro. Aditivo desde el 2026-10-26 |
| `setDebug(on)` | Overlay `#status` |
| `setFreeCamera(on)` · `setGrid(on)` · `setBackground(on)` | Ajustes de escena |
| `setSilenceTimeoutMs(ms)` | Solo para el overlay de depurar; el valor llega desde `/health` del backend |

### JS → Kotlin: `window.SignAllBridge.postMessage(…)`

| Mensaje | Cuándo |
|---|---|
| `avatarReady` | La escena está entera: **200 clips indexados**, seña de arranque resuelta, shaders compilados y un frame pintado con el avatar adentro |
| `avatarLoaded:<id>` | Un cambio de avatar en caliente terminó (2026-10-26) |
| `avatarFailed:<id>` | El cambio de avatar falló; el anterior sigue en pantalla (2026-10-26) |
| `onPlaybackDone` | La cola terminó |
| `signStart:<idx>:<jsMs>` | El muñeco se empezó a mover en la seña `<idx>` |
| `signFailed:<idx>` | La seña `<idx>` no se pudo cargar y se saltea |
| `progress:<0..100>` | Avance real de la escena para la barra del splash (45–100) |

`avatarReady` es lo que **tapa el splash** — no `onPageFinished`, que dispara mientras el
GLB de 34,28 MB (Juan) o 29,46 MB (Pauline) todavía se está bajando. `SignAllSplash` tiene
además un timeout de 8 s.

## El catálogo de Neon

`catalog` lo maneja el backend, sembrada con las **50 glosas de LSC50**
(`tools/seed_catalog.py`, idempotente). `glb_animation_path` es **siempre** un path
same-origin dentro del APK (`signs/<id>.glb`), nunca una URL: eso es lo que permite que la
app resuelva sin red.

**Las 50 tienen animación desde el 2026-10-22.** El archivo único trae las 18 que
faltaban (`0032`–`0049`), así que ya ninguna glosa llega con un path que no exista y
`GLS-10` no alcanza a ninguna. `SignsCatalog.isOwnAssetPath()` se queda igual: distingue
"no es un path nuestro" de "es nuestro pero todavía no lo exportamos", que hoy no alcanza
a ninguna glosa pero protege a una glosa futura sin clip. En ese caso **no toca la red**,
porque pedirla por HTTP sería un 404 garantizado: si el ajuste *Seña de prueba si falta*
está prendido la representa con la seña de reserva, y si está apagado —el default— la
saltea y avisa "Seña no disponible" por glosa.

El backend carga el catálogo **una sola vez al arrancar**. Después de un seed hay que
reiniciarlo: no hay invalidación.

## Datos crudos de LSC50 (histórico)

El dataset original son los CSVs de landmarks (BODY 33, HANDS 21×2, FACE 468 landmarks de
MediaPipe). **El pipeline de captura ya no alimenta la app** —la fuente de las animaciones
es Cascadeur— pero el formato de los CSVs y el esquema de IDs `SSSS_VVVV_RRRR` se mantienen
como referencia del dataset, y eso es lo que documenta `specs/dataset/spec.md`. El paper
del dataset está en `docs/lsc50-dataset.pdf`; los CSVs de muestra que había en
`data/LANDMARKS/` se borraron del repo el 2026-10-22.

Los scripts de captura de ese pipeline quedaron en `../signall-avatar`, donde la **demo
Flask de landmarks está congelada desde el 2026-09-19**. El repo siguió activo para otra
cosa: versiona los GLB finales de Cascadeur en `models/`. No se copian scripts entre los
dos pipelines.

## Cómo se verifica un cambio de acá

```bash
tools/verificar-tiempo-real.sh    # servicios, tests, 28 fixtures, métricas, device
tools/preview_pose.py <pose>      # ver una pose sin installDebug
./gradlew build                   # el APK sigue compilando
```

Un número sin su origen no se puede afirmar: ver `AGENTS.md` § Observabilidad. Y
`rt_first_sign_ms` de la línea base es **del camino por palabras** — con oraciones no es
comparable.