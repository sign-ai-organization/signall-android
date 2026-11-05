// SignAI — escena 3D del avatar.
//
// El personaje se carga UNA vez desde `models/juan.glb` o `models/pauline.glb`
// (elección de Ajustes → Personaje). No hay retargeting ni interpretación de
// landmarks: los clips traen la pose ya horneada por hueso, y acá solo se
// reproduce. Eso deja el `app.js` en ~300 líneas y saca de en medio el motor
// procedural que antes tenía que adivinar la pose desde coordenadas
// normalizadas de imagen.
//
// Dónde viene cada cosa, desde el 2026-10-26:
//   models/<avatar>.glb → el muñeco con su esqueleto (102 huesos, HG_*) **y las
//                        50 señas como clips** `0000_afirmativo`..`0049_orden`,
//                        una por marca facial (200 clips por archivo; desde el
//                        2026-11-04 no hay clip base ni caras emocionales)
//   signs/*.glb         → el layout viejo: solo animación, un clip por variante,
//                        mismo esqueleto. Se sigue soportando como descarga, pero
//                        con las señas adentro del avatar no se pide ninguno.
//
// Los clips indexados del avatar se resuelven por nombre (`embeddedClipsFor`), así
// que `signs/<id>.glb` es un identificador: el pedido por red solo ocurre si el
// clip no vino en el archivo.
//
// Contrato con Android (`window.SignAI`) — no cambiar sin tocar el lado Kotlin:
//   setQueue, appendQueue, playFrom, queuePending, queueState, play, setDebug,
//   setFreeCamera, setGrid, setBackground, setReduceMotion, setAvatar,
//   setSpeed, setTheme, setDefaultSign, setSilenceTimeoutMs
// Hacia Android: `window.SignAIBridge.postMessage("onPlaybackDone")`,
// `postMessage("avatarReady")` (primer avatar), `postMessage("avatarLoaded:<id>")`
// (cambios posteriores), `postMessage("avatarFailed:<id>")` y
// `postMessage("signStart:<idx>:<jsMs>")` por cada seña.
//
// `signStart` es la primera seña del turno moviéndose en pantalla. Sin él, el
// tramo desde que el backend manda una oración hasta que el muñeco se mueve no
// tenía reloj en ninguno de los dos lados: el backend cronometraba hasta mandar
// el `segment` y el device cronometraba desde `play()`, que es antes de que el
// `.glb` termine de bajar. El número que falta es el que percibe el usuario.

import * as THREE from "three";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";

// ── Estado global ─────────────────────────────────────────────────────────────
const state = {
  mixer: null,
  action: null,          // clip en curso
  outgoing: null,        // clip que se va (peso 1 → 0) durante la fundición
  finishing: false,      // la fundición de salida está calendarizada
  playing: false,
  speed: 1,
  loading: false,
  epoch: 0,              // sube en cada stopPlayback(); una carga vieja no revive
  reduceMotion: false,   // ACC-*: sin fundición, corte seco
  nodeCount: 0,
  nodeNames: new Set(),
  // Cola que envía Android con setQueue()/appendQueue(); cada ítem es
  // {gloss, type, url, available} y `url` es siempre same-origin.
  queue: [],
  queueIdx: 0,
  // Si esta cola llegó a arrancar. `setQueue` lo baja y `play`/`playFrom` lo
  // suben: es lo que le dice a `appendQueue` si hay que arrancar desde el
  // principio o seguir después de la última seña que sonó.
  queueStarted: false,
  currentType: "",
  // Variante facial que se está representando (`afirmativo`, `pregunta`, …).
  // La elige `pickVariant` con la tabla por tipo; solo se lee en el diagnóstico.
  currentVariant: "",
  // Velocidad pedida por el usuario (setSpeed) y velocidad aplicada.
  baseSpeed: 1,
  speed: 1,
  // `SILENCE_TIMEOUT_MS` del STT, que Android copia desde `/health`. `null` = no
  // se sabe todavía; el overlay de depuración lo muestra igual, con "?".
  silenceTimeoutMs: null,
};

const AVATAR_SOURCES = {
  juan: "models/juan.glb",
  pauline: "models/pauline.glb",
};
const DEFAULT_AVATAR = "juan";

/**
 * Catálogo local de avatares. La URL nunca se arma con un valor crudo: el nombre
 * que llega por `setAvatar` o por `?avatar=` tiene que ser una clave de este
 * objeto, así que no hay forma de pedir algo fuera de `models/`.
 */
function avatarFromQuery() {
  const requested = new URLSearchParams(window.location.search).get("avatar");
  return Object.prototype.hasOwnProperty.call(AVATAR_SOURCES, requested)
    ? requested
    : DEFAULT_AVATAR;
}

let avatarId = avatarFromQuery();
/** La escena ya tiene este avatar indexado y en pantalla. */
let loadedAvatarId = null;
/** Sube con cada carga; una carga vieja no debe pisar a la nueva. */
let avatarEpoch = 0;
/** Pivote del avatar en escena, para poder limpiarlo al cambiar. */
let avatarPivot = null;

const DEFAULT_SIGN_URL = "signs/0000_0000_0000.glb";
let defaultSignUrl = DEFAULT_SIGN_URL;

// ── Velocidad ────────────────────────────────────────────────────────────────
//
// La velocidad es **un parámetro, no un resultado**: la fija el usuario en
// Ajustes y `setSpeed` la aplica tal cual. Antes este archivo multiplicaba 1,6×
// por su cuenta cuando había dos o más señas en la fila (`CATCHUP_BACKLOG` y
// `CATCHUP_FACTOR`), y esa función se quitó el 2026-10-16: decisión D-5 de
// `openspec/sessions/2026-10-16-latencia-y-decisiones.md`.
//
// Los tres porqués de quitarla, en orden de peso:
//
//   1. **Alteraba la variable que había que medir.** La prueba obligatoria
//      PR-PER-04 (`../sign-ai-docs/plan-pruebas/01-diseno/personas/pruebas.md`)
//      pregunta a usuarios con LSC si la velocidad es adecuada, en una escala de
//      1 a 5. Con el adelanto prendido la misma respuesta mezcla dos cosas —la
//      velocidad que eligió la persona y un factor que nadie le dijo— y la
//      prueba deja de poder separarlas.
//   2. **La carrera que venía a empatar no existe.** Quien lee el avatar es una
//      persona sorda que no está mirando a quien habla. Que el muñeco termine
//      después de que el hablante termine no le hace nada a quien lee.
//   3. **No estaba en ningún documento de la tesis.** No aparece en el documento
//      de requerimientos, ni en el plan de pruebas, ni en los doce diagramas del
//      SDD; solo en este archivo y en la auditoría del 15 de octubre. Y dos
//      números mágicos sin criterio escrito (`2` y `1.6`) contradicen la regla
//      del backend de que ningún umbral numérico va hardcodeado en la lógica
//      funcional (`../sign-ai-backend/CLAUDE.md`).
//
// Lo que sí era cierto y sigue siendo cierto: las 32 señas duran 2,15 s de
// media y una frase larga tarda más en representarse de lo que se dice. Eso ya
// no es una falla del sistema, es la consecuencia de que el lector no compita
// con el hablante.

/** Aplica la velocidad pedida. Sin factores ocultos: lo que se pide es lo que corre. */
function applySpeed() {
  state.speed = Math.max(0.1, Math.min(3, state.baseSpeed));
}

/** Cuántas señas quedaron por detrás de la que está sonando. */
function queueBacklog() {
  return Math.max(0, state.queue.length - state.queueIdx - 1);
}

// ── Fundición entre señas ─────────────────────────────────────────────────────
//
// Las variantes de clip arrancan y terminan en el mismo lugar (medido en
// `openspec/changes/reposo-y-crossfade/investigacion.md` §4.1: el salto directo
// del final de una seña al inicio de la siguiente es de **5.9 cm de mediana** y
// el 79% de los pares ya es continuo). Este archivo antes metía ~100 ms de pose
// **congelada** de reposo entre cada par (`TRANSITION_FRAMES`), y eso
// desarmaba lo que los clips ya traían bien: en vez de un salto de 5.9 cm cada
// transición pagaba dos de ~46 cm, uno al salir al reposo y otro al volver.
//
// Con la fundición el clip siguiente arranca `CROSSFADE_MS` antes de que termine
// el actual, y las dos actions viven juntas sobre el mismo esqueleto mientras
// three.js las mezcla. Con 100 ms desaparece el 87% del salto (94.5 → 12.1
// cm/frame entre fotogramas) solapando solo ~3 cm de gesto de cada lado; más
// allá de 150 ms no se mejora nada (el piso de 8.8 cm/frame es la velocidad real
// de una seña, no un salto).
//
// Sale **más corto**, no más largo: la cola adelanta 100 ms por seña, y además
// desaparecen los ~100 ms de pose congelada que había entre cada par. Medido en
// el Redmi con dos señas: 4708 ms → 4522 ms (−186 ms, contra los −200 ms que
// predice la cuenta; el resto es redondeo de fotograma).
const CROSSFADE_MS = 100;

/**
 * Segundos de fundición. Con `reduceMotion` es 0 y la transición es un corte
 * seco.
 *
 * Ojo con el 0: `fadeIn(0)` no es "sin fundición" sino lo contrario — el
 * interpolant de peso arranca y termina en el mismo instante, así que three.js
 * lo da por terminado de una y **deshabilita la action**. Por eso el 0 se
 * maneja como un caso aparte, sin llamar a `fadeIn`/`fadeOut`.
 */
function crossfade() {
  return state.reduceMotion ? 0 : CROSSFADE_MS / 1000;
}

let pressTimer = null;
let pressStart = null;
let pressStartTime = 0;
let activePointers = 0;

// ── Three.js setup ────────────────────────────────────────────────────────────
const canvas   = document.getElementById("avatar-canvas");
// El canvas del WebView es del tamaño de la pantalla, así que a
// `devicePixelRatio` completo el Redmi Note 9S (dpr 2.75) renderiza 1080×2400
// escalados a ~2970×6600: 19.6 megapíxeles por fotograma. Medido, eso deja el
// GPU en 37 ms y la app al 99% en jank. Tope a 1.75 → 6.5 MP, que para un
// avatar de torso y manos es indistinguible en pantalla.
const MAX_DPR = 1.75;
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, MAX_DPR));
renderer.outputColorSpace = THREE.SRGBColorSpace;
// El material del muñeco es PBR (texturas de Blender). Sin ACES los tonos
// claros se queman; con ACES el rango se comprime como se exportó.
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.0;

const scene = new THREE.Scene();
// Fondo del tema oscuro = la marca (SignALL) en grafito: el mismo #121316 que
// `DarkColors.background` en ui/designsystem/Color.kt. El canvas es la
// superficie mas grande de la app, asi que si queda en el color viejo la
// paleta nueva se ve solo en los controles.
scene.background = new THREE.Color(0x121316);

// Encuadre por defecto. La cámara y el punto de mira se suben **juntos**, por
// el mismo `FRAME_LIFT`: eso deja el ángulo con el que se mira al muñeco
// exactamente igual (es una traslación rígida de la cámara) y sólo corre el
// encuadre hacia abajo, que es lo pedido — "subamos la cámara, como ponernos más
// altas" — sin angostar ni torcer la vista. Antes (1.35 / 0.95) la cabeza del
// muñeco rozaba el borde superior y el aviso de estado le caía encima.
const FRAME_LIFT = 0.15;
const FRAME_CAMERA = [0, 1.35 + FRAME_LIFT, 2.3];
const FRAME_TARGET = [0, 0.95 + FRAME_LIFT, 0];

const camera = new THREE.PerspectiveCamera(45, 1, 0.01, 100);

const controls = new OrbitControls(camera, canvas);
/** Vuelve al encuadre por defecto (arranque y reset por long-press). */
function frameCamera() {
  camera.position.set(...FRAME_CAMERA);
  controls.target.set(...FRAME_TARGET);
  controls.update();
}
controls.enableDamping = true;
controls.dampingFactor = 0.08;
controls.enableRotate = false;  // solo zoom: sin orbitar ni pan
controls.enablePan = false;
controls.minDistance = 1.4;
controls.maxDistance = 3.8;
// Las señas son de torso y manos: se encuadra de la cadera para arriba y no el
// cuerpo entero, que en un teléfono en vertical sería una figura diminuta.
frameCamera();

scene.add(new THREE.HemisphereLight(0xffffff, 0x6b6b66, 1.0));
const key = new THREE.DirectionalLight(0xffffff, 2.2);
key.position.set(1.5, 2.2, 2.4);
scene.add(key);
const fill = new THREE.DirectionalLight(0xffffff, 0.6);
fill.position.set(-2, 1, 1.5);
scene.add(fill);
// Grilla del suelo: `soft` de la misma paleta (#25272c), que sobre el fondo
// queda tan sutil como lo era antes sobre su propio fondo.
const grid = new THREE.GridHelper(4, 8, 0x25272c, 0x25272c);
grid.visible = false; // por defecto sin grilla (suelo)
scene.add(grid);

// Fondo opcional (bosque): se carga una vez, se activa vía SignAI.setBackground(true)
// El mismo fondo que arriba; `setTheme` alterna entre este y el claro del prototipo.
const BG_COLOR = new THREE.Color(0x121316);
const BG_LIGHT = new THREE.Color(0xf4f4f2);
let bgTexture = null;
let showBg = false;
let darkTheme = true;
new THREE.TextureLoader().load("bosque.jpg", (t) => { bgTexture = t; });

function refreshBackground() {
  scene.background = (showBg && bgTexture) ? bgTexture : (darkTheme ? BG_COLOR : BG_LIGHT);
}

function setStatus(msg) {
  document.getElementById("status").textContent = msg;
}

/**
 * Progreso real de la escena para la barra del splash, `progress:<0..100>`.
 *
 * El `onProgressChanged` del WebView no sirve para esto: dice 100 % cuando la
 * página terminó de cargar el HTML, y ahí todavía falta lo caro (bajar, parsear
 * y compilar 13 MB de avatar). El tramo 45–100 es de la escena; el 0–45 lo pone
 * Kotlin con el progreso de la página. Con la escena vieja, que no manda nada,
 * la barra se comporta como antes.
 */
function reportProgress(pct) {
  try { window.SignAIBridge?.postMessage(`progress:${Math.round(pct)}`); } catch (e) { /* no bridge */ }
}

// ── DEBUG ─────────────────────────────────────────────────────────────────────
// Centro único de opciones debug. Para agregar una opción: agregar una función
// a `flags` que devuelva líneas de texto. Toggle: window.SignAI.setDebug(bool).
const Debug = {
  enabled: false,
  el: null,
  statusEl: null,
  flags: {
    playback: () => [
      `play: ${state.playing}  clip: ${state.action?.getClip().name ?? "-"}`,
      `t: ${(state.action?.time ?? 0).toFixed(2)} / ` +
        `${(state.action?.getClip().duration ?? 0).toFixed(2)}s  speed: ${state.speed.toFixed(2)}`,
      `cola: ${state.queueIdx + 1}/${state.queue.length}  tipo: ${state.currentType || "-"}` +
        `  cara: ${state.currentVariant || "-"}`,
      // `inUse` es la prueba de que hay dos actions vivas: durante la fundición
      // son 2 (la que entra y la que sale) y el resto del tiempo 1.
      `fundición: ${state.finishing ? "salida" : state.outgoing ? "saliendo" : "-"}` +
        `  ${CROSSFADE_MS}ms  actions vivas: ${state.mixer?.stats.actions.inUse ?? 0}`,
    ],
    // El tiempo de silencio vive en el STT, no en el backend ni en la app, y es
    // el segundo bloque más grande de la latencia de punta a punta: con 1500 ms
    // son 1,5 s de espera entre que la persona deja de hablar y que la frase
    // exista. Está acá para que cada corrida de pruebas lo lea de la pantalla en
    // vez de acordarse de anotarlo (decisión D-6): lo manda Android con
    // `setSilenceTimeoutMs` y sin conexión sale "?", que es la verdad.
    stt: () => [
      `silencio STT: ${state.silenceTimeoutMs != null ? state.silenceTimeoutMs + "ms" : "?"}`,
    ],
    avatar: () => [
      `nodos: ${state.nodeCount}`,
      `canvas: ${canvas.clientWidth}x${canvas.clientHeight}`,
    ],
    camera: () => {
      const p = camera.position, t = controls.target;
      return [
        `cam: (${p.x.toFixed(2)}, ${p.y.toFixed(2)}, ${p.z.toFixed(2)})  target: (${t.x.toFixed(2)}, ${t.y.toFixed(2)}, ${t.z.toFixed(2)})`,
        `fov: ${camera.fov}°  dpr: ${window.devicePixelRatio}`,
      ];
    },
    render: () => {
      const i = renderer.info.render;
      const d = renderer.getDrawingBufferSize(new THREE.Vector2());
      return [
        `draws: ${i.calls}  tris: ${i.triangles}  frame: ${i.frame}`,
        `buffer: ${d.x}x${d.y} (${(d.x * d.y / 1e6).toFixed(1)} MP)  dpr: ${renderer.getPixelRatio().toFixed(2)}`,
      ];
    },
    taps: () => [
      pressTimer
        ? `long_press: ${((performance.now() - pressStartTime) / 1000).toFixed(1)}s / 0.5s`
        : `long_press: - (${Debug.lastTap ?? "inactivo"})`,
    ],
  },
  render() {
    if (!this.el) this.el = document.getElementById("debug");
    if (!this.statusEl) this.statusEl = document.getElementById("status");
    if (!this.enabled) {
      this.el.style.display = "none";
      if (this.statusEl) this.statusEl.style.display = "none";
      return;
    }
    this.el.style.display = "";
    if (this.statusEl) this.statusEl.style.display = "";
    this.el.textContent = Object.values(this.flags).flatMap(f => f()).join("\n");
  },
};

// ── Carga ─────────────────────────────────────────────────────────────────────
// Las URLs son siempre same-origin: las de `signs/` salen de los assets
// empaquetados y las descargadas del backend pasan por /signs/ (PathHandler en
// AvatarWebView). El JS no puede pedir http:// al backend: sería mixed content
// y Android lo bloquea. Por eso las descargas las hace Kotlin, no esta escena.
const loader = new GLTFLoader();
const signCache = new Map();

/**
 * Clips que ya vinieron **dentro** del avatar, por nombre de clip.
 *
 * `models/<avatar>.glb` (Juan o Pauline) puede traer las señas empaquetadas como
 * animaciones (`0000`..`0049`) en vez de un `.glb` por seña. Cuando las trae,
 * pedir el `signs/<id>.glb` es un viaje de red (o un 404, si el archivo no se
 * empaquetó porque la seña ya está acá) para un dato que ya está en memoria: se
 * resuelve por nombre y no se descarga nada.
 *
 * Se llena al cargar el avatar. Si el archivo no trae animaciones queda vacío y
 * todo sigue por el camino de siempre (un `.glb` por seña), así que las dos
 * variantes de asset conviven sin ramas en el resto del archivo.
 */
const embeddedClips = new Map();

/**
 * Variantes faciales que trae cada avatar, por sufijo de clip. Ya no hay clip
 * base: las cuatro son la misma seña con una marca facial distinta, y solo
 * mueven los morph targets (los canales de hueso son idénticos).
 */
const FACE_MARKER_SUFFIXES = new Set([
  "afirmativo", "pregunta", "negacion", "orden",
]);

/** La cara que se usa cuando el tipo no viene o no está en la tabla. */
const DEFAULT_FACE_VARIANT = "afirmativo";

/**
 * Tipo de oración del backend → variante facial. El export del 2026-11-04 trae
 * exactamente las cuatro marcas gramaticales, así que ya no hay filas abiertas:
 * pregunta sí/no y pregunta de contenido comparten `pregunta`, y la orden usa
 * `orden`. Cambiar una fila acá no toca los assets ni el backend: el tipo ya
 * viaja en cada `segment` y `app.js` lo recibe en `item.type`.
 */
const FACE_VARIANT_BY_TYPE = Object.freeze({
  AFFIRMATIVE: "afirmativo",
  YES_NO_QUESTION: "pregunta",
  CONTENT_QUESTION: "pregunta",
  NEGATIVE: "negacion",
  IMPERATIVE: "orden",
});

/**
 * Los clips de `url` si vienen dentro del avatar, o `null` si hay que cargarlos.
 *
 * La URL es la misma que manda Android para las dos variantes —`signs/0000_0000_0000.glb`—
 * así que el id de la seña es lo que identifica al clip (`0000`). Se devuelven
 * **separadas** dos cosas:
 *
 *   - `clips`: las tomas de la seña (`0000`, `0000.001`, …), que son la misma
 *     animación exportada varias veces. `pickVariant` elige una al azar, como
 *     siempre. En el export del 2026-11-04 no hay tomas ni clip base: si no
 *     aparece ninguna, `clips` queda con la variante por defecto para que la
 *     seña se pueda representar igual.
 *   - `variants`: un `Map` sufijo → clip con la marca facial
 *     (`0000_afirmativo`, `0000_pregunta`, `0000_negacion`, `0000_orden`). Se
 *     eligen por tipo de oración, no al azar.
 *
 * No mezclarlas es lo que evita que `pickVariant` devuelva una cara al azar:
 * los dos grupos se parecen en el nombre pero no en el significado.
 */
function embeddedClipsFor(url) {
  if (!embeddedClips.size) return null;
  const match = /^signs\/([0-9]{4})(?:_[0-9]{4}){2}\.glb$/.exec(url);
  if (!match) return null;
  const id = match[1];
  const clips = [];
  const variants = new Map();
  for (const [name, clip] of embeddedClips) {
    if (name === id || name.startsWith(id + ".")) {
      clips.push(clip);
      continue;
    }
    const suffix = faceMarkerSuffix(name, id);
    if (suffix) variants.set(suffix, clip);
  }
  if (!clips.length) {
    // Sin clip base ni tomas: la seña igual tiene que sonar con alguna cara.
    const fallback = variants.get(DEFAULT_FACE_VARIANT) ??
      variants.values().next().value;
    if (fallback) clips.push(fallback);
  }
  if (!clips.length) return null;
  clips.sort((a, b) => a.name.localeCompare(b.name));
  return { clips, variants };
}

/** Sufijo facial de `name` (`0000_afirmativo` → `afirmativo`), o `null`. */
function faceMarkerSuffix(name, id) {
  const prefix = id + "_";
  if (!name.startsWith(prefix)) return null;
  const suffix = name.slice(prefix.length);
  return FACE_MARKER_SUFFIXES.has(suffix) ? suffix : null;
}

/** Devuelve {clips, variants} de un sign GLB, cacheado por URL. */
async function loadSignClips(url) {
  if (signCache.has(url)) return signCache.get(url);
  const embedded = embeddedClipsFor(url);
  if (embedded) {
    signCache.set(url, embedded);
    return embedded;
  }
  const gltf = await loader.loadAsync(url);
  if (!gltf.animations.length) throw new Error(url + " -> sin clips");
  const entry = { clips: gltf.animations, variants: new Map() };
  signCache.set(url, entry);
  return entry;
}

/**
 * Los tracks de un clip nombran nodos que tienen que existir en la escena del
 * avatar. Three.js descarta en silencio los que no resuelve, así que una seña
 * puede cargar "bien" y quedarse en pose de reposo sin decir nada — el único
 * modo de falla invisible del pipeline. Se cuenta y se avisa por consola.
 *
 * El set es de *nodos*, no de huesos: los exports de Blender animan también la
 * raíz del armature (`HG_Branden001`), que existe en la escena pero no es un
 * bone del skeleton. Contar solo huesos marca 138 tracks "sin resolver" que en
 * realidad sí se aplican.
 */
function unresolvedTracks(clip, nodeNames) {
  let missing = 0;
  for (const track of clip.tracks) {
    const node = THREE.PropertyBinding.sanitizeNodeName(track.name.split(".")[0]);
    if (!nodeNames.has(node)) missing++;
  }
  return missing;
}

/** Avisa a Android que el cambio de avatar terminó (`avatarLoaded:<id>`). */
function notifyAvatarLoaded(id) {
  try { window.SignAIBridge?.postMessage(`avatarLoaded:${id}`); } catch (e) { /* no bridge */ }
  console.log(`[SignAI] avatar cargado: ${id} · ${Math.round(performance.now())} ms`);
}

/** Avisa que el avatar pedido no se pudo cargar (`avatarFailed:<id>`). */
function notifyAvatarFailed(id) {
  try { window.SignAIBridge?.postMessage(`avatarFailed:${id}`); } catch (e) { /* no bridge */ }
}

/** Libera geometrías, materiales y texturas de un árbol que se va de escena. */
function disposeTree(root) {
  const geometries = new Set();
  const materials = new Set();
  const textures = new Set();
  root.traverse(obj => {
    if (obj.geometry) geometries.add(obj.geometry);
    const list = Array.isArray(obj.material)
      ? obj.material
      : (obj.material ? [obj.material] : []);
    for (const material of list) {
      materials.add(material);
      // Las texturas cuelgan de propiedades con nombres distintos (map,
      // normalMap, roughnessMap, …): se recorren todos los valores.
      for (const value of Object.values(material)) {
        if (value && value.isTexture) textures.add(value);
      }
    }
  });
  for (const texture of textures) texture.dispose();
  for (const material of materials) material.dispose?.();
  for (const geometry of geometries) geometry.dispose();
}

/**
 * Saca el avatar actual de la escena y suelta lo que tenía en GPU.
 *
 * Se llama **después** de que el avatar nuevo terminó de parsearse, no antes:
 * si la carga falla, el usuario se queda con el avatar que ya veía. El pico de
 * memoria es el parseo del nuevo, que todavía no está en la escena.
 */
function releaseAvatar() {
  state.mixer?.stopAllAction();
  state.mixer = null;
  state.action = null;
  state.outgoing = null;
  state.playing = false;
  state.finishing = false;
  state.nodeNames = new Set();
  state.nodeCount = 0;
  if (!avatarPivot) return;
  scene.remove(avatarPivot);
  disposeTree(avatarPivot);
  avatarPivot = null;
}

/** Construye el pivote normalizado, el índice de nodos y el mixer de un GLB. */
function prepareAvatar(gltf) {
  const root = gltf.scene;
  root.traverse(obj => {
    if (obj.isMesh) {
      obj.frustumCulled = false;
      // NO se fuerza DoubleSide: las mallas del export son cerradas y se ven
      // bien en una sola cara. Forzarlo duplica el trabajo de rasterizado sin
      // cambiar un solo píxel (medido: mismo aspecto, menos GPU).
    }
  });

  // Normalizar: el muñeco mide ~1.83 de alto en el export. Se escala a 1.7 y se
  // apoya en el piso (y=0) para que el encuadre y la grilla signifiquen algo.
  //
  // La transformación va en un `pivot` PADRE, no sobre `root`: los clips de
  // Cascadeur animan la raíz del armature en posición, rotación y escala, así
  // que el mixer sobrescribe `root` frame a frame. Si la normalización estuviera
  // ahí, la perdería el primer frame y el muñeco aparecería descolocado y sin
  // escalar — sin ningún error, solo feo.
  const pivot = new THREE.Object3D();
  pivot.name = "SignAiPivot";
  pivot.add(root);
  const box = new THREE.Box3();
  root.updateWorldMatrix(true, true);
  root.traverse(obj => { if (obj.isMesh) box.expandByObject(obj); });
  if (!box.isEmpty()) {
    const h = box.getSize(new THREE.Vector3()).y;
    const s = 1.7 / h;
    const c = box.getCenter(new THREE.Vector3());
    pivot.scale.setScalar(s);
    pivot.position.set(-c.x * s, -box.min.y * s, -c.z * s);
  }

  // Todo nodo del avatar, no solo los huesos: los tracks de los exports de
  // Blender incluyen la raíz del armature además de los 102 bones.
  const nodeNames = new Set();
  root.traverse(obj => { nodeNames.add(obj.name); });
  root.traverse(obj => {
    if (obj.isSkinnedMesh && obj.skeleton) {
      for (const bone of obj.skeleton.bones) nodeNames.add(bone.name);
    }
  });

  return {
    pivot,
    nodeNames,
    nodeCount: nodeNames.size,
    // El mixer se ata al `root`, NO al `pivot`: el pivot es nuestro andamiaje y
    // ningún track debe tocarlo. Todos los clips del avatar se reproducen sobre
    // este mismo root, así que un solo mixer alcanza.
    mixer: new THREE.AnimationMixer(root),
    animations: gltf.animations,
  };
}

/**
 * Carga un avatar y lo deja listo para representar.
 *
 * [initial] distingue el arranque (manda `avatarReady`, el aviso que destapa el
 * splash) de un cambio en caliente (manda `avatarLoaded:<id>`). El avatar
 * anterior se libera recién cuando el nuevo parseó: un fallo no deja la escena
 * vacía.
 */
function loadAvatar(id, initial = false) {
  const url = AVATAR_SOURCES[id];
  if (!url) {
    console.error("loadAvatar: avatar desconocido", id);
    notifyAvatarFailed(id);
    return false;
  }

  const epoch = ++avatarEpoch;
  const started = performance.now();
  // Cortar lo que estuviera sonando sin tocar el modelo: la escena solo cambia
  // de muñeco cuando el nuevo está listo.
  stopPlayback();
  // Una cola vieja pertenece al turno anterior y no debe reanudarse sola.
  state.queue = [];
  state.queueIdx = 0;
  state.queueStarted = false;
  state.loading = true;
  setStatus(`Cargando avatar ${id}...`);
  if (initial) reportProgress(45);

  loader.load(url, (gltf) => {
    if (epoch !== avatarEpoch) {
      // Llegó una carga que ya no interesa: liberar y no tocar la escena.
      disposeTree(gltf.scene);
      return;
    }

    const parsedAt = Math.round(performance.now() - started);
    const prepared = prepareAvatar(gltf);
    console.log(`[SignAI] ${id} parseado · nodos:`, prepared.nodeCount,
      "·", parsedAt, "ms");

    // Recién acá se reemplaza: el anterior se va con sus texturas y geometrías.
    releaseAvatar();
    avatarPivot = prepared.pivot;
    scene.add(avatarPivot);
    avatarPivot.updateWorldMatrix(true, true);
    state.mixer = prepared.mixer;
    state.nodeNames = prepared.nodeNames;
    state.nodeCount = prepared.nodeCount;

    // Los clips pertenecen al modelo que se acaba de cargar. Se reindexan
    // enteros (y se vacía la caché) para que una seña no se reproduzca con los
    // tracks del avatar anterior.
    embeddedClips.clear();
    signCache.clear();
    for (const clip of prepared.animations) embeddedClips.set(clip.name, clip);
    loadedAvatarId = id;
    if (initial) reportProgress(85);
    console.log(`[SignAI] ${id}: ${embeddedClips.size} clips indexados`);

    // Seña de arranque, para que la app abra mostrando algo aunque el backend
    // esté caído.
    const defaultSign = loadSignClips(defaultSignUrl).then(({ clips }) => {
      const missing = unresolvedTracks(clips[0], state.nodeNames);
      setStatus(
        `Avatar listo — ${clips.length} toma(s)` +
        (missing ? ` · ${missing} tracks sin resolver` : "") +
        " · presiona Enviar",
      );
      if (missing) console.warn("[SignAI] tracks sin hueso:", missing, clips[0].name);
    }).catch(err => {
      setStatus("Error cargando la seña por defecto");
      console.error(err);
    });

    // El splash de Compose se esconde con `avatarReady`, así que ese aviso se
    // manda cuando la escena está **entera**, no cuando el GLB terminó de
    // parsear: los clips ya indexados, la seña de arranque ya resuelta y un
    // frame dibujado con el avatar adentro. Los dos `requestAnimationFrame` son
    // el orden del navegador: el primero corre antes de pintar, el segundo ya
    // corre después de que el frame salió.
    defaultSign
      .catch(() => {})  // el error ya se muestra en la barra; no debe colgar el splash
      .then(() => renderer.compileAsync(scene, camera).catch(() => {}))
      .then(() => { if (initial) reportProgress(96); })  // shaders ya compilados
      .then(() => requestAnimationFrame(() => requestAnimationFrame(() => {
        if (epoch !== avatarEpoch) return;
        state.loading = false;
        const totalMs = Math.round(performance.now() - started);
        if (initial) {
          // Este es el número que ve el owner: el splash se va justo después.
          console.log(`[SignAI] escena lista (${id}) · ${totalMs} ms`);
          reportProgress(100);
          try { window.SignAIBridge?.postMessage("avatarReady"); } catch (e) { /* no bridge */ }
        } else {
          console.log(`[SignAI] cambio de avatar a ${id} listo · ${totalMs} ms`);
          notifyAvatarLoaded(id);
        }
      })));
  }, (xhr) => {
    if (!initial) return;  // el progreso solo tiene sentido para el splash
    // El splash ya no lo ve nadie (está tapado por la barra), pero el estado de
    // debug sí: es la única señal de cuánto falta mientras baja el archivo.
    const pct = xhr.total ? xhr.loaded / xhr.total : 0;
    setStatus(`Cargando avatar ${id}... ${Math.round(pct * 100)}%`);
    reportProgress(45 + 35 * pct);  // 45 → 80: la descarga
  }, (err) => {
    if (epoch !== avatarEpoch) return;
    state.loading = false;
    setStatus(`Error cargando ${url}`);
    console.error(err);
    if (initial) {
      reportProgress(100);  // que el splash no se quede esperando lo que ya falló
    } else {
      notifyAvatarFailed(id);
    }
  });
  return true;
}

loadAvatar(avatarId, true);

// ── Reproducción ──────────────────────────────────────────────────────────────
function notifyPlaybackDone() {
  try { window.SignAIBridge?.postMessage("onPlaybackDone"); } catch (e) { /* no bridge */ }
}

/**
 * Avisa que el muñeco se está moviendo de verdad, con su reloj.
 *
 * `signStart:<idx de la cola>:<performance.now()>`. Va desde `loadQueueItem`
 * —no desde `startClip`— porque entre una cosa y la otra está la carga del
 * `.glb`, y esa carga es justamente parte de lo que hay que medir: la oración
 * puede estar lista hace un segundo y el avatar no moverse hasta que bajan tres
 * megabytes.
 *
 * El reloj propio viaja en el mensaje para poder leer los tres tramos por
 * separado: cuánto tardó Android en entregar la cola (`performance.now()` menos
 * el `setQueue`), cuánto tardó la escena en arrancar el clip, y cuánto tardó
 * Kotlin en enterarse. Los dos relojes tienen épocas distintas y no se restan
 * entre sí, así que el número que se compara es el que calcula cada lado.
 */
function notifySignStart(index) {
  const msg = `signStart:${index}:${Math.round(performance.now())}`;
  try { window.SignAIBridge?.postMessage(msg); } catch (e) { /* no bridge */ }
  if (Debug.enabled) console.log(`[SignAI] signStart ${msg}`);
}

/**
 * Detiene lo que esté sonando y deja al muñeco en pose de reposo.
 * `stopAllAction()` llama a `restoreOriginalDirection()` sobre cada binding,
 * así que los huesos vuelven a los quaterniones con los que se cargó el avatar:
 * la pose de descanso horneada en `models/<avatar>.glb` por
 * `tools/pack_avatars.py` (la misma regla de `rest_pose.py`).
 *
 * Es también el camino del long press (reset) y del corte seco con
 * `reduceMotion`: no es una transición, es cortar.
 */
function stopPlayback() {
  state.playing = false;
  state.action = null;
  state.outgoing = null;
  state.finishing = false;
  state.loading = false;
  // Una carga que ya estaba en vuelo no debe resucitar la reproducción después
  // de un reset (o el long press "no resetea" y la seña arranca sola).
  state.epoch++;
  state.mixer?.stopAllAction();
}

/**
 * Arranca un clip sobre el mixer. `clip` ya debe venir de `loadSignClips`.
 *
 * Tres casos:
 *
 *   - **hay algo sonando y es otro clip** → las dos actions quedan vivas y
 *     `crossFadeTo` encadena la que entra sobre la que sale.
 *   - **no hay nada sonando** → `fadeIn` desde la pose de reposo, para que el
 *     turno no empiece con un corte.
 *   - **el clip es el mismo que está sonando** → corte seco. Pasa con una
 *     frase repetida ("hola hola"): three.js cachea una `action` por clip, así
 *     que no hay dos actions que fundir. Los clips son continuos consigo
 *     mismos (los 46 arrancan y terminan en el mismo cluster), así que el salto
 *     es el mismo de cualquier par, sin la pausa de antes.
 *
 * Con `reduceMotion` los tres son corte seco: una fundición de duración 0 no es
 * "sin fundición" sino "peso 0 y action deshabilitada" (ver [crossfade]).
 */
function startClip(clip) {
  const mixer = state.mixer;
  if (!mixer) return false;
  const missing = unresolvedTracks(clip, state.nodeNames);
  if (missing) console.warn("[SignAI] seña con", missing, "tracks sin hueso:", clip.name);

  const previous = state.action;
  const action = mixer.clipAction(clip);
  // La misma seña dos veces seguidas devuelve la misma action (three.js cachea
  // una por clip): no hay dos actions que fundir.
  const fade = previous && previous !== action ? crossfade() : 0;

  state.finishing = false;
  state.playing = true;
  // RF-AVT-08: si el usuario cambió la velocidad mientras la seña anterior
  // estaba saliendo, el valor nuevo se aplica acá, al arrancar esta. La seña
  // que ya estaba en curso termina con la velocidad con la que empezó.
  applySpeed();

  if (Debug.enabled) {
    const how = fade > 0 ? "fundición" : !previous && crossfade() > 0
      ? "fundición desde el reposo" : "corte seco";
    console.log(`[SignAI] ${how} -> ${action.getClip().name || "(clip sin nombre)"}`);
  }

  if (fade > 0) {
    // Lo que todavía se está yendo no se puede volver a fundir: se mata antes
    // de encadenar otra (un `play()` en mitad de una fundición, un `playFrom`).
    if (state.outgoing) state.outgoing.stop();
    state.outgoing = null;
    action.reset().setEffectiveWeight(1).play();
    previous.crossFadeTo(action, fade, false);
    state.outgoing = previous;
    state.action = action;
  } else {
    mixer.stopAllAction();
    state.outgoing = null;
    state.action = action.reset().setEffectiveWeight(1).play();
    if (!previous && crossfade() > 0) {
      // Primera seña del turno: entra desde el reposo, como sale la última.
      state.action.fadeIn(crossfade());
    } else {
      // `update(0)` aplica la pose en este mismo frame: sin esto queda un
      // fotograma en reposo entre dos señas, que es justo el salto que se viene a
      // quitar. No avanza el reloj.
      mixer.update(0);
    }
  }

  preloadNext();
  return true;
}

/**
 * Pide el clip del ítem siguiente para que esté en caché cuando llegue la
 * frontera. Sin esto la fundición depende de que la carga del `.glb` termine
 * justo a tiempo, y si no termina se cae al corte seco: no es peor que antes,
 * pero tampoco mejora nada. `signCache` ya cachea por URL.
 */
function preloadNext() {
  const next = state.queue[state.queueIdx + 1];
  if (!next || signCache.has(next.url)) return;
  loadSignClips(next.url).catch(err =>
    console.warn("[SignAI] no se pudo precargar", next.url, err));
}

/**
 * Elige el clip de la seña: la variante facial del tipo de oración.
 *
 * Las cuatro variantes no entran al azar: son caras distintas de la misma seña
 * y elegirlas sorteadas mostraría una negación en una afirmación. Si el tipo no
 * viene o no está en la tabla se usa la variante por defecto. Solo se sortea
 * dentro de las tomas, que son la misma animación repetida.
 * Deja en `state.currentVariant` lo que se eligió, para el diagnóstico.
 */
function pickVariant(clips, variants, type) {
  const wanted = FACE_VARIANT_BY_TYPE[type] || DEFAULT_FACE_VARIANT;
  const chosen = variants?.has(wanted) ? wanted
    : variants?.has(DEFAULT_FACE_VARIANT) ? DEFAULT_FACE_VARIANT : null;
  state.currentVariant = chosen || "base";
  if (chosen) return variants.get(chosen);
  return clips.length > 1 ? clips[(Math.random() * clips.length) | 0] : clips[0];
}

/**
 * Avisa que una seña de la cola no se pudo cargar y se está saltando.
 *
 * `signFailed:<índice>`. Distinto de `signStart`, que es "el muñeco ya se está
 * moviendo": acá el muñeco **no** se va a mover por este ítem, y Kotlin lo usa para
 * poner "Seña no disponible", que es el único texto propio que la tabla de estados
 * del SRS permite sobre el indicador (`main.tex:753`).
 */
function notifySignFailed(index, item) {
  const gloss = (item && (item.gloss || item.url)) || "(sin nombre)";
  try { window.SignAIBridge?.postMessage(`signFailed:${index}`); } catch (e) { /* no bridge */ }
  console.warn(`[SignAI] seña ${index} (${gloss}) no se pudo cargar: se saltea`);
}

/**
 * Carga el ítem [index] de la cola y lo deja listo para reproducir. Mientras
 * carga, `state.loading` congela el loop: sin eso, una carga lenta dejaría
 * sonando el clip anterior.
 */
async function loadQueueItem(index) {
  const item = state.queue[index];
  if (!item) return false;
  const epoch = state.epoch;
  state.loading = true;
  state.currentType = item.type || "";
  try {
    const { clips, variants } = await loadSignClips(item.url);
    if (index !== state.queueIdx || epoch !== state.epoch) return false;
    state.loading = false;
    startClip(pickVariant(clips, variants, state.currentType));
    notifySignStart(index);
    return true;
  } catch (err) {
    console.error("No se pudo cargar la seña", item.url, err);
    state.loading = false;
    // Un ítem que no carga **no se come el resto de la frase**. Antes se paraba
    // todo (`stopPlayback`) y se avisaba que el turno había terminado: el usuario
    // veía al muñeco detenerse y las demás señas de esa frase no se representaban
    // nunca, sin que nada lo dijera — la frase "!terminaba" en silencio, a mitad.
    // Ahora se saltea esta seña y se sigue con la siguiente: lo que se pierde es
    // una seña, no la frase.
    //
    // `advanceQueue` es el que sabe si la cola se acabó, así que el "terminó"
    // sigue siendo suyo y no de este `catch`. Y es el mismo camino que el normal:
    // si el ítem que falló era el primero, acá no hay nada sonando y nadie va a
    // empujar la cola, así que hay que llamarlo igual.
    notifySignFailed(index, item);
    advanceQueue();
    return false;
  }
}

/** Pasa al ítem siguiente de la cola, o cierra el turno si no hay más. */
function advanceQueue() {
  const next = state.queueIdx + 1;
  const fade = crossfade();

  if (next >= state.queue.length) {
    // `state.action` puede no existir: si **todos** los ítems fallaron al cargar,
    // el muñeco nunca llegó a moverse y no hay action de la cual salir con
    // fundición. Sin esta guarda esto reventaba con "cannot read property of null".
    if (fade > 0 && state.action) {
      // Salida al reposo. No hace falta escribir ningún keyframe: cuando el
      // peso de una action llega a 0, `PropertyMixer.apply()` completa con el
      // valor guardado cuando se creó el binding — el TRS del nodo, o sea la
      // pose de descanso horneada. Como la fundición se calendariza para
      // acabar junto con el clip, esperar a que termine no alarga el turno.
      state.action.fadeOut(fade);
      state.finishing = true;
      return;
    }
    stopPlayback();
    notifyPlaybackDone();
    return;
  }

  state.queueIdx = next;
  loadQueueItem(next);
}

/** Avanza el mixer un frame y resuelve la frontera entre clips. */
function tickPlayback(dt) {
  if (!state.playing) return;

  if (state.loading) return;           // el mixer espera al clip
  const action = state.action;
  if (!action) return;

  const clip = action.getClip();
  const step = dt * state.speed;
  // El clip siguiente entra `fade` antes del final de este, así que la
  // fundición termina justo cuando termina el clip.
  const boundary = clip.duration - crossfade();

  if (state.finishing) {
    if (action.time + step >= clip.duration) {
      state.mixer.update(Math.max(0, clip.duration - action.time));
      stopPlayback();
      notifyPlaybackDone();
      return;
    }
    state.mixer.update(step);
    return;
  }

  if (action.time + step >= boundary) {
    // Un dt grande (la app volvió del background, p.ej.) no debe teletransportar
    // la mezcla a mitad de camino: se avanza solo hasta la frontera.
    state.mixer.update(Math.max(0, boundary - action.time));
    advanceQueue();
    return;
  }
  state.mixer.update(step);
}

// ── Render loop ───────────────────────────────────────────────────────────────
const clock = new THREE.Clock();
function renderLoop() {
  requestAnimationFrame(renderLoop);
  const dt = clock.getDelta();

  const w = canvas.clientWidth  || window.innerWidth;
  const h = canvas.clientHeight || window.innerHeight;
  if (w && h && (renderer.domElement.width !== w || renderer.domElement.height !== h)) {
    renderer.setSize(w, h);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  }

  tickPlayback(dt);
  controls.update();
  renderer.render(scene, camera);
  Debug.render();
}
renderLoop();

// Long press (1 solo dedo, casi sin mover) 0.5s → reset pose + cámara al default
canvas.addEventListener("pointerdown", (e) => {
  activePointers++;
  if (activePointers > 1) { cancelPress(); return; } // pinch/zoom: no cuenta
  pressStart = { x: e.clientX, y: e.clientY };
  pressStartTime = performance.now();
  pressTimer = setTimeout(() => {
    pressTimer = null;
    stopPlayback();
    frameCamera();
    Debug.lastTap = "reset (long press)";
  }, 500);
});
const cancelPress = () => { if (pressTimer) { clearTimeout(pressTimer); pressTimer = null; } };
canvas.addEventListener("pointermove", (e) => {
  if (pressTimer && pressStart &&
      Math.hypot(e.clientX - pressStart.x, e.clientY - pressStart.y) > 8) {
    cancelPress(); // el dedo se movió: es orbitar, no long press
  }
});
canvas.addEventListener("pointerup", () => { activePointers = Math.max(0, activePointers - 1); cancelPress(); });
canvas.addEventListener("pointercancel", () => { activePointers = 0; cancelPress(); });

// ── API para Android ──────────────────────────────────────────────────────────
window.SignAI = {
  /**
   * Secuencia a reproducir. `json` llega como string (Kotlin lo entrecomilla) y
   * se parsea acá para no interpolar datos crudos en el script.
   * Aditivo: los comandos originales y el guard `?.` no cambian (BRG-8).
   */
  setQueue(json) {
    try {
      const items = JSON.parse(json);
      if (!Array.isArray(items) || items.length === 0) return;
      state.queue = items;
      state.queueIdx = 0;
      // Cola nueva: si más adelante hay que reanudarla, se arranca desde 0 y no
      // desde donde terminó la anterior.
      state.queueStarted = false;
    } catch (e) {
      console.error("setQueue: payload inválido", e);
    }
  },

  /**
   * Agrega ítems al final de la cola sin tocar lo que ya se está representando.
   *
   * Es la mitad en tiempo real de `setQueue`. Con la frase por oraciones el
   * backend manda una oración apenas está traducida —no espera a tenerlas
   * todas—, así que esto es lo que encadena cada oración detrás de la anterior
   * mientras el usuario sigue hablando. `setQueue` sigue existiendo y sigue
   * reemplazando, porque una frase nueva arranca su propia cola.
   *
   * Si la cola ya se había vaciado mientras estas oraciones se traducían, la
   * vuelve a arrancar desde donde quedó. Sin eso, la oración que llegó tarde se
   * quedaría encolada para siempre esperando a un `play()` que nadie vuelve a
   * mandar. La decisión va **dentro** de este método y no en Kotlin a propósito:
   * acá no hay dos hilos que puedan intercalarse entre "la cola está vacía" y
   * "arranco", que es la forma más barata de que esa oración quede sin cubrir.
   *
   * El índice de reanudación depende de en cuál de los dos estados esté la cola:
   *
   *   - **nunca arrancó** (`state.queueStarted` falso): se representa desde 0.
   *   - **ya se vació**: se sigue en el ítem siguiente al último que sonó, o sea
   *     `queueIdx + 1`. `advanceQueue` deja `queueIdx` en el último índice que
   *     llegó a sonar, así que `queueIdx` a secas haría repetir esa seña.
   *
   * Aditivo: no cambia `setQueue` ni el guard `?.` (BRG-8).
   */
  appendQueue(json) {
    try {
      const items = JSON.parse(json);
      if (!Array.isArray(items) || items.length === 0) return 0;
      state.queue.push(...items);
      if (state.playing || state.loading) return state.queue.length;
      this.playFrom(state.queueStarted ? state.queueIdx + 1 : 0);
      return state.queue.length;
    } catch (e) {
      console.error("appendQueue: payload inválido", e);
      return 0;
    }
  },

  /**
   * Empieza a representar sin reiniciar la cola.
   *
   * `play()` es correcto solo cuando la cola está completa: salta al índice 0.
   * En streaming se llama cuando llega el primer fragmento y la cola puede
   * seguir creciendo, así que tiene que arrancar desde donde esté y no volver
   * al principio. Si ya está sonando no hace nada, que es justo lo que quiere
   * un fragmento que llega mientras el anterior se reproduce.
   *
   * `state.loading` también corta: una carga en vuelo significa que ese mismo
   * ítem ya está en camino, y arrancarlo otra vez lo reinicia — el `mixer`
   * vuelve a aplicar el clip desde el primer keyframe y el `signStart` sale dos
   * veces para la misma seña.
   */
  playFrom(startIndex) {
    if (state.playing || state.loading || !state.mixer) return false;
    const from = Number(startIndex) || 0;
    if (from < 0 || from >= state.queue.length) return false;
    state.queueStarted = true;
    state.queueIdx = from;
    // Sin fundición (D4): si algo se estuviera yendo, `startClip` lo mata antes
    // de arrancar la nueva. El corte de `stopPlayback` no hace falta porque acá
    // no hay nada sonando.
    loadQueueItem(from);
    return true;
  },

  /** Cola pendiente de representar; lo usa la app para mostrar el texto en vivo. */
  queuePending() {
    if (!state.playing) return state.queue.length;
    return queueBacklog();
  },

  /** Estado de la cola, para diagnóstico desde el modo debug. */
  queueState() {
    return {
      idx: state.queueIdx,
      length: state.queue.length,
      backlog: queueBacklog(),
      speed: state.speed,
      baseSpeed: state.baseSpeed,
    };
  },

  play() {
    if (state.playing || state.loading || !state.mixer) return;
    if (state.queue.length) {
      state.queueStarted = true;
      // El asset puede no estar en caché todavía (recién descargado del
      // backend), así que la carga es asíncrona y el loop arranca al resolver.
      loadQueueItem(0);
      return;
    }
    // Sin cola: se repite la seña de arranque.
    loadSignClips(defaultSignUrl).then(({ clips }) => startClip(pickVariant(clips)));
  },

  /**
   * Corta lo que esté sonando y vacía la cola.
   *
   * Lo llama Android cuando se pierde la conexión y el turno ya está
   * representándose a medias (estado *Desconectado* del SRS). Sin esto la interfaz
   * volvía a reposo mientras el canvas seguía con la seña adelante.
   *
   * Vaciar la cola además de parar es a propósito: si quedara, un `appendQueue`
   * posterior arrancaría en `queueStarted ? queueIdx + 1 : 0` y saltaría los ítems
   * que quedaron entre medio. Con la cola vacía, el próximo `setQueue` de una frase
   * nueva arranca en 0 como debe.
   *
   * Aditivo: no cambia `play`, `setQueue` ni el guard `?.` (BRG-8).
   */
  stop() {
    const estabaSonando = state.playing;
    stopPlayback();
    state.queue = [];
    state.queueIdx = 0;
    state.queueStarted = false;
    console.log(`stop(): cola cortada (sonando=${estabaSonando})`);
    return true;
  },

  setDebug(on) { Debug.enabled = !!on; Debug.render(); },

  /**
   * Cambia el avatar completo que está en escena (`juan` / `pauline`).
   *
   * Solo en reposo: si hay una seña sonando, una cola viva o una carga en
   * curso, se rechaza y devuelve `false`. La app también bloquea el selector,
   * pero la guarda vive acá porque el comando es parte del puente y puede
   * llamarse desde cualquier momento.
   *
   * El identificador se resuelve contra [AVATAR_SOURCES]; un valor que no esté
   * en el catálogo no arma ninguna URL ni pide nada por red.
   *
   * Aditivo: no cambia ningún comando existente (BRG-8). El resultado se avisa
   * con `avatarLoaded:<id>` o `avatarFailed:<id>`.
   */
  setAvatar(id) {
    const name = String(id || "").toLowerCase();
    if (!Object.prototype.hasOwnProperty.call(AVATAR_SOURCES, name)) {
      console.error("setAvatar: avatar desconocido", id);
      return false;
    }
    if (name === loadedAvatarId && !state.loading) return true;
    if (state.playing || state.loading || state.finishing) {
      console.warn("setAvatar: la escena no está en reposo");
      return false;
    }
    return loadAvatar(name, false);
  },

  /**
   * Accesibilidad: con el ajuste prendido no hay fundición entre señas y cada
   * transición es un corte seco (spec `ACC-*`).
   *
   * Lo que se reduce es el movimiento de la transición, que es parte de cómo se
   * lee la seña y no un efecto. Prenderse a mitad de una fundición no la
   * interrumpe — corta el turno que viene — así que no hay nada que reconciliar.
   */
  setReduceMotion(on) {
    state.reduceMotion = !!on;
    if (Debug.enabled) console.log("[SignAI] fundición:", crossfade() > 0 ? "on" : "off");
  },

  setSpeed(v) {
    // v en 0..1 (0.5 = velocidad normal) → factor 0.1..3
    state.baseSpeed = Math.max(0.1, Math.min(3, (Number(v) || 0.5) * 2));
    // RF-AVT-08: un cambio a mitad de frase **no** corta ni acelera la seña que
    // está saliendo; se aplica desde la siguiente (`startClip` llama a
    // `applySpeed`). Si no hay nada sonando —al abrir, o entre turnos— no hay a
    // quién esperar y se aplica en el acto.
    if (!state.playing || !state.action) applySpeed();
    if (Debug.enabled) Debug.render();
  },

  /**
   * El `SILENCE_TIMEOUT_MS` que está usando el STT, para el overlay de depurar.
   *
   * No es un ajuste de la app: la app nunca habla con el STT, y el valor decide
   * en qué momento se cierra una frase. Se muestra para que quien corre una
   * prueba lo lea, no para cambiarlo. `null` o un valor no numérico dejan el
   * overlay con "?".
   */
  setSilenceTimeoutMs(ms) {
    const value = Number(ms);
    state.silenceTimeoutMs = Number.isFinite(value) && value > 0 ? value : null;
    if (Debug.enabled) Debug.render();
    return state.silenceTimeoutMs;
  },

  setFreeCamera(on) {
    controls.enableRotate = !!on;
    controls.enablePan = false; // nunca mover el punto de vista, solo orbitar/zoom
    controls.minDistance = on ? 0.2 : 1.4;
    controls.maxDistance = on ? 20 : 3.8;
  },

  setGrid(on) { grid.visible = !!on; },
  setBackground(on) { showBg = !!on; refreshBackground(); },

  /**
   * Cambia la seña con la que abre la app (menú de ajustes). `url` es un path
   * same-origin dentro de assets/web/, p.ej. "signs/0018_0000_0000.glb".
   *
   * Cargar una seña son ~200 KB, así que no se hace acá: se guarda la URL y el
   * próximo play() la carga. Devuelve `false` si la URL no es de `signs/`, para
   * que un valor raro no termine pidiendo algo fuera de los assets.
   */
  setDefaultSign(url) {
    const clean = String(url || "").trim();
    if (!clean.startsWith("signs/") || clean.includes("..") || clean.includes("//")) {
      console.error("setDefaultSign: url no permitida", url);
      return false;
    }
    defaultSignUrl = clean;
    return true;
  },

  // Tema del canvas: el fondo del render sigue al tema.
  // El <html> lleva la clase para que el CSS ajuste el color del texto
  // de debug/estado (ver index.html).
  setTheme(on) {
    darkTheme = !!on;
    document.documentElement.classList.toggle("light", !darkTheme);
    refreshBackground();
  },
};