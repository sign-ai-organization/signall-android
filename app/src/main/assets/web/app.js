// SignAI — escena 3D del avatar.
//
// El personaje se carga UNA vez desde `models/avatar.glb` y cada seña es un
// clip de animación suelto en `signs/<id>.glb`. No hay retargeting ni
// interpretación de landmarks: los `.glb` traen la pose ya horneada por hueso, y
// acá solo se reproduce. Eso deja el `app.js` en ~300 líneas y saca de en medio
// el motor procedural que antes tenía que adivinar la pose desde coordenadas
// normalizadas de imagen.
//
// Dónde viene cada cosa:
//   models/avatar.glb  → el muñeco con su esqueleto (102 huesos, HG_*)
//   signs/*.glb        → solo animación, un clip por variante, mismo esqueleto
//
// Contrato con Android (`window.SignAI`) — no cambiar sin tocar el lado Kotlin:
//   setQueue, appendQueue, playFrom, queuePending, queueState, play, setDebug,
//   setFreeCamera, setGrid, setBackground, setReduceMotion,
//   setSpeed, setTheme, setDefaultSign
// Hacia Android: `window.SignAIBridge.postMessage("onPlaybackDone")` y
// `postMessage("avatarReady")`.

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
  // Cola que envía Android con setQueue(); cada ítem es
  // {gloss, type, url, available} y `url` es siempre same-origin.
  queue: [],
  queueIdx: 0,
  currentType: "",
  // Velocidad pedida por el usuario (setSpeed) y velocidad realmente aplicada.
  // Se separan porque el adelanto automático multiplica sobre la primera y
  // tiene que poder deshacerse sin perder el ajuste del usuario.
  baseSpeed: 1,
  catchUp: false,
};

const AVATAR_URL = "models/avatar.glb";
const DEFAULT_SIGN_URL = "signs/0000_0000_0000.glb";
let defaultSignUrl = DEFAULT_SIGN_URL;

// ── Adelanto automático ──────────────────────────────────────────────────────
//
// Las 32 señas que hay exportadas duran 2.08-2.75 s cada una (promedio 2.15 s,
// medido contra el manifest.json) y cada palabra se traduce en una seña. La
// traducción es rápida; la animación es el cuello de botella: si el muñeco
// firmara siempre a velocidad normal, terminaría la frase después de que el
// usuario terminó de decirla. Así que cuando la cola se acumula, el muñeco firma
// más rápido. Dos señas de cola es el umbral: con una sola todavía se está al
// día, y acelerar antes solo quitaría claridad a los movimientos.
//
// **Remedido con el flujo por oraciones** (2026-10-15, `tools/medir-retraso.py`,
// las 28 frases de `../sign-ai-stt/fixtures/audio`): el adelanto **se queda**,
// pero por un motivo distinto al que tenía este comentario.
//
// Lo que no cambió es el origen del desfase, que no es el modo de entrega sino
// 2,15 s de animación contra la velocidad del habla. Medido: el avatar no
// termina de firmar antes de que el usuario termine de hablar en 17 de las 28
// frases, y con el adelanto puesto todavía en 7. Es la misma cuenta que antes,
// porque cada palabra sigue siendo una seña.
//
// Lo que sí cambió es la forma de la cola. Con entrega por fragmento las señas
// llegaban de a una durante el habla y la cola crecía sin parar; con entrega por
// oración **la cola está vacía mientras el usuario habla y se llena de golpe al
// cerrar la frase**. De ahí dos cosas:
//
//   - El adelanto ya no está prendido todo el tiempo, y está bien que no lo
//     esté: acelerar un clip que ya va solo no aporta nada. Prende al caer el
//     primer lote y se apaga cuando la cola baja de 2.
//   - El umbral de 2 se mide **después** de que arranque la seña en curso
//     (`queue.length - queueIdx - 1`), así que hace falta una frase de 3 señas o
//     más para dispararlo. Con las 28 frases de prueba solo una lo dispara:
//     son 28 frases cortas, de mediana 2 palabras y 1 seña.
//
// La conclusión es que **no hay que cambiar los números**: 1,26x de mediana está
// justo en el borde y 1,6x lo cruza (mediana 0,79x con adelanto). Lo que no se
// puede es prometer que el avatar firme al ritmo de la voz, y eso no lo arregla
// este archivo: harían falta ~5x, que es un fast-forward, no una seña.
const CATCHUP_BACKLOG = 2;
const CATCHUP_FACTOR = 1.6;

/** Aplica el factor de adelanto sobre la velocidad pedida. */
function applySpeed() {
  state.speed = Math.max(0.1, Math.min(3, state.baseSpeed * (state.catchUp ? CATCHUP_FACTOR : 1)));
}

/** Cuántas señas quedaron por detrás de la que está sonando. */
function queueBacklog() {
  return Math.max(0, state.queue.length - state.queueIdx - 1);
}

// ── Fundición entre señas ─────────────────────────────────────────────────────
//
// Las 46 variantes de clip arrancan y terminan en el mismo lugar (medido en
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
// Fondo del tema oscuro = la marca (SignALL): el mismo #0c142c que
// `DarkColors.background` en ui/designsystem/Color.kt. El canvas es la
// superficie mas grande de la app, asi que si queda en el color viejo la
// paleta nueva se ve solo en los controles.
scene.background = new THREE.Color(0x0c142c);

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
// Grilla del suelo: `soft` de la misma paleta (#1e2a4d), que sobre el fondo
// queda tan sutil como la #1e2130 lo era sobre el fondo anterior.
const grid = new THREE.GridHelper(4, 8, 0x1e2a4d, 0x1e2a4d);
grid.visible = false; // por defecto sin grilla (suelo)
scene.add(grid);

// Fondo opcional (bosque): se carga una vez, se activa vía SignAI.setBackground(true)
// El mismo fondo que arriba; `setTheme` alterna entre este y el claro del prototipo.
const BG_COLOR = new THREE.Color(0x0c142c);
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
      `cola: ${state.queueIdx + 1}/${state.queue.length}  tipo: ${state.currentType || "-"}`,
      // `inUse` es la prueba de que hay dos actions vivas: durante la fundición
      // son 2 (la que entra y la que sale) y el resto del tiempo 1.
      `fundición: ${state.finishing ? "salida" : state.outgoing ? "saliendo" : "-"}` +
        `  ${CROSSFADE_MS}ms  actions vivas: ${state.mixer?.stats.actions.inUse ?? 0}`,
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

/** Devuelve {clips} de un sign GLB, cacheado por URL. */
async function loadSignClips(url) {
  if (signCache.has(url)) return signCache.get(url);
  const gltf = await loader.loadAsync(url);
  if (!gltf.animations.length) throw new Error(url + " -> sin clips");
  const entry = { clips: gltf.animations };
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

loader.load(AVATAR_URL, (gltf) => {
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
  // apoya en el piso (y=0) para que el encuadre y la grilla significen algo.
  //
  // La transformación va en un `pivot` PADRE, no sobre `root`: los clips de
  // Cascadeur animan la raíz del armature (`HG_Branden001`) en posición,
  // rotación y escala, así que el mixer sobrescribe `root` frame a frame. Si la
  // normalización estuviera ahí, la perdería el primer frame y el muñeco
  // aparecería descolocado y sin escalar — sin ningún error, solo feo.
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

  scene.add(pivot);
  pivot.updateWorldMatrix(true, true);

  // Todo nodo del avatar, no solo los huesos: los tracks de los exports de
  // Blender incluyen la raíz del armature además de los 102 bones.
  const nodeNames = new Set();
  root.traverse(obj => { nodeNames.add(obj.name); });
  root.traverse(obj => {
    if (obj.isSkinnedMesh && obj.skeleton) {
      for (const bone of obj.skeleton.bones) nodeNames.add(bone.name);
    }
  });
  state.nodeCount = nodeNames.size;
  state.nodeNames = nodeNames;
  // El mixer se ata al root con el que se construye. El root del avatar no
  // cambia nunca (todos los clips de seña se reproducen sobre el mismo
  // muñeco), así que un solo mixer alcanza — y es lo que permite cambiar de
  // seña sin recargar los 10 MB. Se ata a `root`, NO a `pivot`: el pivot es
  // nuestro andamiaje y ningún track debe tocarlo.
  state.mixer = new THREE.AnimationMixer(root);
  console.log("[SignAI] avatar listo · nodos:", state.nodeCount);

  // Seña de arranque, para que la app abra mostrando algo aunque el backend
  // esté caído.
  loadSignClips(defaultSignUrl).then(({ clips }) => {
    const missing = unresolvedTracks(clips[0], nodeNames);
    setStatus(
      `Avatar listo — ${clips.length} variante(s)` +
      (missing ? ` · ${missing} tracks sin resolver` : "") +
      " · presiona Enviar",
    );
    if (missing) console.warn("[SignAI] tracks sin hueso:", missing, clips[0].name);
  }).catch(err => {
    setStatus("Error cargando la seña por defecto");
    console.error(err);
  });

  // La splash de Compose se esconde cuando la página termina de cargar, pero
  // el avatar son 10 MB: sin este aviso el splash se va y se ve un canvas
  // negro mientras descarga.
  try { window.SignAIBridge?.postMessage("avatarReady"); } catch (e) { /* no bridge */ }

}, (xhr) => {
  setStatus(`Cargando avatar... ${xhr.total ? Math.round(xhr.loaded / xhr.total * 100) : 0}%`);
}, (err) => {
  setStatus("Error cargando models/avatar.glb");
  console.error(err);
});

// ── Reproducción ──────────────────────────────────────────────────────────────
function notifyPlaybackDone() {
  try { window.SignAIBridge?.postMessage("onPlaybackDone"); } catch (e) { /* no bridge */ }
}

/**
 * Detiene lo que esté sonando y deja al muñeco en pose de reposo.
 * `stopAllAction()` llama a `restoreOriginalDirection()` sobre cada binding,
 * así que los huesos vuelven a los quaterniones con los que se cargó el avatar:
 * la pose de descanso horneada en `models/avatar.glb` por `tools/rest_pose.py`.
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

/** Elige una variante al azar: 10 de las 32 señas traen 2–4 tomas distintas. */
function pickVariant(clips) {
  return clips.length > 1 ? clips[(Math.random() * clips.length) | 0] : clips[0];
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
    const { clips } = await loadSignClips(item.url);
    if (index !== state.queueIdx || epoch !== state.epoch) return false;
    state.loading = false;
    startClip(pickVariant(clips));
    return true;
  } catch (err) {
    console.error("No se pudo cargar la seña", item.url, err);
    state.loading = false;
    // Un ítem que no carga no puede dejar el turno colgado en "firmando": el
    // muñeco vuelve al reposo y se avisa que terminó, que es lo que espera el
    // lado Kotlin para liberar el panel y el micrófono.
    stopPlayback();
    notifyPlaybackDone();
    return false;
  }
}

/** Pasa al ítem siguiente de la cola, o cierra el turno si no hay más. */
function advanceQueue() {
  const next = state.queueIdx + 1;
  const fade = crossfade();

  if (next >= state.queue.length) {
    if (fade > 0) {
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

  // El adelanto se decide acá y no cuando llega un fragmento: la cola crece
  // por dos vías (la app y el propio avance) y el dato bueno es "cuántas me
  // faltan", que solo existe en este punto.
  const backlog = queueBacklog();
  const shouldCatchUp = backlog >= CATCHUP_BACKLOG;
  if (shouldCatchUp !== state.catchUp) {
    state.catchUp = shouldCatchUp;
    applySpeed();
    if (Debug.enabled) {
      console.log(`[SignAI] cola ${state.queueIdx + 1}/${state.queue.length}, ` +
                  `adelanto ${state.catchUp ? "on" : "off"} (velocidad ${state.speed.toFixed(2)})`);
    }
  }

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
    } catch (e) {
      console.error("setQueue: payload inválido", e);
    }
  },

  /**
   * Agrega ítems al final de la cola sin tocar lo que ya se está firmando.
   *
   * Es la mitad en tiempo real de `setQueue`. El backend ya no espera a tener
   * la frase entera: manda un fragmento, la app lo agrega a la cola y el
   * muñeco lo firma mientras la persona sigue hablando. `setQueue` sigue
   * existiendo y sigue reemplazando, porque una frase escrita o un "Repetir"
   * llegan completos de una.
   *
   * Aditivo: no cambia `setQueue` ni el guard `?.` (BRG-8).
   */
  appendQueue(json) {
    try {
      const items = JSON.parse(json);
      if (!Array.isArray(items) || items.length === 0) return 0;
      state.queue.push(...items);
      return state.queue.length;
    } catch (e) {
      console.error("appendQueue: payload inválido", e);
      return 0;
    }
  },

  /**
   * Empieza a firmar sin reiniciar la cola.
   *
   * `play()` es correcto solo cuando la cola está completa: salta al índice 0.
   * En streaming se llama cuando llega el primer fragmento y la cola puede
   * seguir creciendo, así que tiene que arrancar desde donde esté y no volver
   * al principio. Si ya está sonando no hace nada, que es justo lo que quiere
   * un fragmento que llega mientras el anterior se reproduce.
   */
  playFrom(startIndex) {
    if (state.playing || !state.mixer) return false;
    const from = Number(startIndex) || 0;
    if (from < 0 || from >= state.queue.length) return false;
    state.queueIdx = from;
    // Sin fundición (D4): si algo se estuviera yendo, `startClip` lo mata antes
    // de arrancar la nueva. El corte de `stopPlayback` no hace falta porque acá
    // no hay nada sonando.
    loadQueueItem(from);
    return true;
  },

  /** Cola pendiente de firmar; lo usa la app para mostrar el texto en vivo. */
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
      catchUp: state.catchUp,
      speed: state.speed,
      baseSpeed: state.baseSpeed,
    };
  },

  play() {
    if (state.playing || !state.mixer) return;
    if (state.queue.length) {
      // El asset puede no estar en caché todavía (recién descargado del
      // backend), así que la carga es asíncrona y el loop arranca al resolver.
      loadQueueItem(0);
      return;
    }
    // Sin cola: se repite la seña de arranque.
    loadSignClips(defaultSignUrl).then(({ clips }) => startClip(pickVariant(clips)));
  },

  setDebug(on) { Debug.enabled = !!on; Debug.render(); },

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
    applySpeed();
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