# Pipeline SignAI (WebView) — flujo de datos

> **Actualización del 2026-10-22:** el flujo de assets cambió. Ya no hay
> `models/avatar.glb` + `signs/<id>.glb`: hay **un solo** `models/avatar.glb` con el
> personaje y las 50 señas como clips `0000`..`0049`, armado por `tools/pack_signall.py`
> desde `../material-3d/GLB SignALL/`. El flujo de runtime no cambió. Detalle en
> `openspec/sessions/2026-10-22-signall-un-glb-50-senas.md`.
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
../material-3d/GLB SignALL/*.glb                  el .glb único de Cascadeur: personaje + 50 clips
      │                            (fuente, gitignored; 27,16 MB)
      ▼
tools/pack_signall.py              hornea la pose de descanso, poda morph targets
      │                            que nadie anima y compacta el buffer
      │  · models/avatar.glb        ← 12,98 MB: personaje + cara + clips 0000..0049
      │  · manifest.json            ← inventario de los 50 clips
      │  · domain/SignsCatalog.kt   ← reescrito, para que el asset y el código no divergan
      ▼
app/src/main/assets/web/           dentro del APK
```

Todo se resuelve con **rutas relativas**, sin red. La escena indexa los clips al cargar
el avatar y resuelve `signs/<id>.glb` **por nombre** contra ese índice
(`embeddedClipsFor` en `app.js`): el path es el identificador de la seña, no un archivo
que exista en disco.

El layout viejo (`tools/split_signs.py` → `models/` + `signs/`) quedó en el repo para
rearmar los assets por seña, pero **el vigente es el archivo único**.

**Nunca se edita `models/avatar.glb` a mano.** Se regenera. **Nunca se edita
`SignsCatalog.kt` a mano** tampoco: lo reescribe el generador, justamente para que la
lista de código y el asset no puedan divergir.

**Agregar o cambiar una seña** = regenerar el `.glb` único en `../material-3d/GLB SignALL/` y correr
`pack_signall.py`. No es un paso más: es el único procedimiento.

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
                     │
                     ▼
              ../sign-ai-stt  (faster-whisper + VAD)
                     │  transcript (muestra en vivo, NO se traduce)
                     │  final     (siempre llega, incluso vacío)
                     ▼
              el backend parte la frase en oraciones y traduce cada una
                     │
                     ▼
              segment {index, total, ...}   uno por oración, EN ORDEN
                     │
                     ▼
              MainScreenState.pendingSegments  (sortedMap por index)
                     │  flushSegments() cuando size >= total
                     ▼
              escena: setQueue + play()  ·  appendQueue por cada oración siguiente
                     │
                     ▼
              THREE.AnimationMixer reproduce signs/<id>.glb con crossfade de 100 ms
                     │
                     ▼
              window.SignAIBridge.postMessage("onPlaybackDone")
```

**El contrato son frames de WebSocket, no imports.** Un cambio aquí rompe en el hermano sin
que el compilador avise. El detalle está en [`docs/SISTEMA.md`](../../../docs/SISTEMA.md) y
en el skill `contrato-websocket`.

### Lo que la app **no** hace

- **No habla con el STT.** El audio va al backend y el backend lo reenvía.
- **No habla con Neon.** La base es del backend.
- **No traduce la voz.** La voz entra por el mismo socket pero vuelve como `transcript`, y
  esa transcripción no se traduce: solo lo que el cliente manda como `text`.
- **No encadena.** Arma la cola entera cuando llega el último `segment`.
- **No pide un `segment` a la vez para la primera oración.** Recibe todas.

## Formato de `manifest.json`

Lo escribe el generador de assets: `pack_signall.py` con el archivo único (vigente desde
el 2026-10-22), `split_signs.py` con el layout por seña. Es el inventario de lo que hay en
el APK:

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

Con el archivo único, `bytes` y `sha256` son los **del archivo entero**, no los de un clip:
no existe un asset por seña. `gloss` va en `null` porque la glosa la pone el catálogo del
backend, no el asset. El `sha256` es lo que permite cruzar el APK contra el catálogo sin
abrir los binarios.

## Nombres de los huesos — crítico

**Las exportaciones de Cascadeur usan PUNTOS.** Este es el dato que más se ha equivocado,
y se equivocó en las dos direcciones.

Verificado el **2026-10-17** contra `models/avatar.glb` **y** contra el export original de
Cascadeur (`../material-3d/GLB Senas/0000 - GRACIAS.glb`): **102 articulaciones de skin en los dos
archivos**.

| Zona | Cómo es |
|---|---|
| Cadena del brazo | `shoulder.L`, `upper_arm.L`, `forearm.L`, `hand.L` — con punto, **sin prefijo** |
| Dedos y palma | `thumb.01.L`, `f_index.01.L`, `palm.01.L` … `palm.04.L` |
| Raíz del armature | `HG_Branden.001` |

La raíz `HG_Branden.001` también está animada, pero **no es una articulación de skin**:
contar solo huesos hace que 138 tracks parezcan sin resolver cuando no lo están.

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

### La cara no la anima la app

Los nodos de cara que existen usan nombres al estilo ARKit y **no son articulaciones de
skin**:

```
brow_dwn_L / brow_dwn_R   brow_outer_up_L / brow_outer_up_R   brow_inner_up
eye_blink_open_L / eye_blink_open_R   eye_squint_L / eye_squint_R
eyeball.L, eyeball.lookat.L, eyeball_lookat_master
jaw, jaw_upper, jaw_dwn_mouth_clsd, jaw_open_lt_rt_frwd
```

Y **nada los mueve**: el GLB que va en el APK tiene **cero morph targets**, y `app.js` no
menciona morph targets ni ninguno de esos nombres. La expresión facial **viene horneada en
el clip**.

Es el punto **A6** de `docs/CONTRASTE-APPvsDOCUMENTOS.md`: el SRS pide un marcador facial
según el tipo de frase y no hay implementación. **Decisión del owner pendiente** (2026-10-17).
Antes estaba "en pausa" porque el documento no lo pedía; el SRS de Overleaf ahora sí lo pide,
así que hay que decidir si se implementa o se saca del documento. Si algún día se
implementa, se toca acá y en `app.js` — no hay atajo con los huesos de cara, porque no
existen.

## Constantes de la escena en `app.js` (valores reales)

Verificadas en `app/src/main/assets/web/app.js`:

| Variable | Valor | Para qué |
|---|---|---|
| `CROSSFADE_MS` | `100` | Fundición entre dos señas. Entre señas **no hay pose congelada ni hueco** |
| `MAX_DPR` | `1.75` | Tope de `setPixelRatio`. **A propósito** |
| `AVATAR_URL` | `models/avatar.glb` | El personaje |
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

**La fuente de verdad es `domain/SignAiCommands.kt`.** No esta lista.

### Kotlin → JS: `window.SignAI.<método>(…)`

Siempre con el guard `?.`, y siempre **aditivo**: borrar o renombrar uno rompe en silencio.

| Comando | Qué hace |
|---|---|
| `play()` | Arranca la cola desde el ítem 0 |
| `playFrom(i)` | Arranca desde `i` |
| `setQueue(json)` | **Reemplaza** la cola y la arranca |
| `appendQueue(json)` | **Agrega** al final sin reiniciar lo que está sonando |
| `setSpeed(f)` | Velocidad, `0..1`. La aplica tal cual: no hay adelanto automático |
| `setReduceMotion(on)` | Corte seco entre señas en vez de fundición (accesibilidad) |
| `setTheme(dark)` | El fondo del canvas sigue al tema de la app |
| `setDefaultSign(url)` | Seña de apertura y de reserva |
| `setDebug(on)` | Overlay `#status` |
| `setFreeCamera(on)` · `setGrid(on)` · `setBackground(on)` | Ajustes de escena |
| `setSilenceTimeoutMs(ms)` | Solo para el overlay de depurar; el valor llega desde `/health` del backend |

### JS → Kotlin: `window.SignAIBridge.postMessage(…)`

| Mensaje | Cuándo |
|---|---|
| `avatarReady` | El personaje ya está parseado y en escena |
| `onPlaybackDone` | La cola terminó |
| `signStart:<idx>:<jsMs>` | El muñeco se empezó a mover en la seña `<idx>` |

`avatarReady` es lo que **tapa el splash** — no `onPageFinished`, que dispara mientras el
GLB de 10 MB todavía se está bajando. `SignAiSplash` tiene además un timeout de 8 s.

## El catálogo de Neon

`catalog` lo maneja el backend, sembrada con las **50 glosas de LSC50**
(`tools/seed_catalog.py`, idempotente). `glb_animation_path` es **siempre** un path
same-origin dentro del APK (`signs/<id>.glb`), nunca una URL: eso es lo que permite que la
app resuelva sin red.

**Las 50 tienen animación desde el 2026-10-22.** El archivo único trae las 18 que
faltaban (`0032`–`0049`), así que ya ninguna glosa llega con un path que no exista y
`GLS-10` no alcanza a ninguna. `SignsCatalog.isOwnAssetPath()` se queda igual: distingue
"no es un path nuestro" de "es nuestro pero todavía no lo exportamos", que es el caso de
una glosa futura sin clip — y en ese caso la manda al sign de reserva **sin tocar la red**,
porque pedirla por HTTP sería un 404 garantizado en cada frase que la mencione.

El backend carga el catálogo **una sola vez al arrancar**. Después de un seed hay que
reiniciarlo: no hay invalidación.

## Datos crudos de LSC50 (histórico)

El dataset original son los CSVs de landmarks (BODY 33, HANDS 21×2, FACE 468 landmarks de
MediaPipe). **El pipeline de captura ya no alimenta la app** —la fuente de las animaciones
es Cascadeur— pero el formato de los CSVs y el esquema de IDs `SSSS_VVVV_RRRR` se mantienen
como referencia del dataset, y eso es lo que documenta `specs/dataset/spec.md`. El paper
del dataset está en `docs/lsc50-dataset.pdf`; los CSVs de muestra que había en
`data/LANDMARKS/` se borraron del repo el 2026-10-22.

Los scripts de captura de ese pipeline quedaron en `../signai-avatar`, **inactiva desde el
2026-09-19**. No se copian scripts entre los dos pipelines.

## Cómo se verifica un cambio de acá

```bash
tools/verificar-tiempo-real.sh    # servicios, tests, 28 fixtures, métricas, device
tools/preview_pose.py <pose>      # ver una pose sin installDebug
./gradlew build                   # el APK sigue compilando
```

Un número sin su origen no se puede afirmar: ver `AGENTS.md` § Observabilidad. Y
`rt_first_sign_ms` de la línea base es **del camino por palabras** — con oraciones no es
comparable.