// SignAI — capa de marcadores no manuales y de movimiento humano.
//
// Los clips traen manos y brazos. Esta capa pone **encima** lo que el clip no
// trae, sin tocar un solo keyframe:
//
//   1. Lo que hizo el intérprete con la cabeza, la boca, las cejas, los ojos y
//      el tronco en cada seña (`nmm.json`, medido por `tools/extract_nmm.py`
//      sobre los videos de LSC50).
//   2. La prosodia del tipo de oración: el movimiento de cabeza y de tronco que
//      acompaña a una negación, una pregunta o una orden. La cara de cada tipo
//      ya viene en el clip; acá va lo que la cara sola no puede dar.
//   3. La mirada: los ojos se quedan en quien lee aunque la cabeza gire, y a
//      veces acompañan a la mano.
//   4. Vida: respiración, balanceo del peso, parpadeo, y un tronco que sigue a
//      las manos con retraso en vez de quedarse clavado.
//
// Cómo convive con el mixer de three.js. El mixer **no reescribe** una
// propiedad si su valor no cambió entre un fotograma y el siguiente, así que no
// se puede sumar sobre lo que haya: se acumularía. Cada fotograma se hace
// `restore()` (deshace lo que esta capa escribió, solo si nadie más escribió
// después), corre el mixer, y `apply()` vuelve a sumar. Con `level` en 0 esta
// capa no escribe nada y el avatar es exactamente el de antes.
//
// Después de pintar se hace otro `restore()`: fuera del fotograma el esqueleto
// queda sin la capa. Cuando una action arranca, el mixer guarda la pose que
// encuentra como su «estado original» y vuelve a ella al terminar o al fundir;
// si la encuentra con la capa puesta, la capa queda horneada en el reposo.

import * as THREE from "three";

const DEG = Math.PI / 180;
const FPS_DEFAULT = 24;

// ── Ajustes ──────────────────────────────────────────────────────────────────
// Un solo lugar para todas las ganancias. Los ángulos están en grados.
export const TUNING = Object.freeze({
  // Cuánto de lo medido en el intérprete se aplica.
  headGain: 0.85,
  torsoGain: 1.0,
  faceGain: 0.9,
  // Con una marca gramatical en la cara, las cejas de la seña ceden ante ella.
  lexicalBrowUnderMarker: 0.35,
  // La cabeza no se aparta del clip cuando una mano está sobre la cara: ahí el
  // clip se hizo contra una cabeza quieta y moverla rompería el contacto.
  contactNear: 0.11,
  contactFar: 0.24,
  // Reparto del giro de cabeza entre cuello y cabeza.
  neckShare: 0.4,
  // Apertura de la mandíbula (hueso) con `jawOpen` en 1.
  jawBoneDeg: 14,
  // Respiración.
  breathHz: 0.23,
  breathDeg: 0.55,
  // Balanceo del peso en reposo; se reduce mientras representa.
  swayDeg: 0.9,
  swayMeters: 0.006,
  swayWhileSigning: 0.45,
  // El tronco sigue a las manos: grados por metro, con tope.
  followYawPerMeter: 28,
  followYawMax: 5,
  followLeanPerMeter: 14,
  followLeanMax: 3,
  followSeconds: 0.28,
  // Mirada.
  eyeMaxDeg: 24,
  // En reposo los ojos del avatar quedan unos 5° bajos (la pose de descanso del GLB deja
  // la pupila en la mitad de abajo del ojo: mirada al suelo). Los clips de seña los
  // suben ellos mismos, así que esto solo actúa cuando no hay una seña sonando.
  restEyeLiftDeg: 5,
  // Mirar a la mano más lejana de vez en cuando. APAGADO: en el celular los ojos se
  // disparan al tope (±24° de lado, 17° de pitch) con un parpadeo, justo antes de cada
  // cambio de palabra, y se ve como un tic. La lógica queda por si se afina.
  glances: false,
  glanceMinGap: 2.2,
  glanceSeconds: [0.35, 0.7],
  // Parpadeo espontáneo, en segundos entre uno y otro.
  // Cuánto del parpadeo medido en el intérprete (`nmm.json`) se aplica. 0 = solo el
  // espontáneo. Lo medido deja los párpados a medio cerrar (0,2–0,5) medio segundo al
  // principio y al final de cada seña: se ve como cerrar los ojos entre palabras.
  nmmBlinkGain: 0,
  blinkGap: [2.2, 5.5],
  blinkSeconds: 0.24,
});

// Prosodia por tipo de oración. Es una **hipótesis lingüística** apoyada en lo
// que pide PR-AVT-05 (movimiento lateral de cabeza en la negación, expresión
// firme en la orden): la tiene que validar alguien que sepa LSC.
//   pitch > 0 = mentón hacia abajo; yaw = giro; roll = ladeo; lean = tronco al frente.
export const PROSODY = Object.freeze({
  AFFIRMATIVE:      { pitch: 0, roll: 0, lean: 0,   nodLast: 3.0 },
  YES_NO_QUESTION:  { pitch: 5, roll: 0, lean: 2.2, holdLast: 2.0 },
  CONTENT_QUESTION: { pitch: 2, roll: 4, lean: 1.4, holdLast: 1.0 },
  NEGATIVE:         { pitch: 1, roll: 0, lean: 0,   shakeDeg: 6.5, shakeHz: 1.7 },
  IMPERATIVE:       { pitch: 1, roll: 0, lean: 1.6, nodEach: 4.0 },
});

// Canal de `nmm.json` → morph targets que mueve, con su factor.
const FACE_MAP = {
  jawOpen:       [["jawOpen", 1]],
  mouthSmile_L:  [["mouthSmile_L", 1]],
  mouthSmile_R:  [["mouthSmile_R", 1]],
  mouthFrown:    [["mouthFrown_L", 1], ["mouthFrown_R", 1], ["mouthShrugLower", 0.4]],
  mouthPucker:   [["mouthPucker", 1]],
  mouthFunnel:   [["mouthFunnel", 1]],
  mouthStretch:  [["mouthStretch_L", 1], ["mouthStretch_R", 1]],
  browInnerUp:   [["browInnerUp", 1]],
  browOuterUp_L: [["browOuterUp_L", 1]],
  browOuterUp_R: [["browOuterUp_R", 1]],
  browDown:      [["browDown_L", 1], ["browDown_R", 1], ["eyeSquint_L", 0.5],
                  ["eyeSquint_R", 0.5], ["noseSneer_L", 0.35], ["noseSneer_R", 0.35]],
  eyeBlink:      [["eyeBlink_L", 1], ["eyeBlink_R", 1]],
  eyeWide:       [["eyeWide_L", 1], ["eyeWide_R", 1]],
  cheekSquint:   [["cheekSquint_L", 1], ["cheekSquint_R", 1]],
};
const BROW_CHANNELS = new Set(["browInnerUp", "browOuterUp_L", "browOuterUp_R", "browDown", "eyeWide"]);
const ANGLE_CHANNELS = ["headPitch", "headYaw", "headRoll", "torsoRoll"];

const smoothstep = (a, b, x) => {
  const t = Math.max(0, Math.min(1, (x - a) / (b - a)));
  return t * t * (3 - 2 * t);
};
const clamp = (x, lo, hi) => Math.max(lo, Math.min(hi, x));
/** Ruido suave en [-1, 1]: tres senos de frecuencias que no se repiten juntas. */
const drift = (t, seed) =>
  (Math.sin(t * 0.61 + seed) + Math.sin(t * 1.13 + seed * 2.3) * 0.6 +
   Math.sin(t * 0.29 + seed * 4.1) * 0.8) / 2.4;
/** Acerca `current` a `target` con constante de tiempo `tau` segundos. */
const approach = (current, target, dt, tau) =>
  current + (target - current) * (1 - Math.exp(-dt / Math.max(1e-4, tau)));
const rand = (lo, hi) => lo + Math.random() * (hi - lo);

const _q = new THREE.Quaternion();
const _parent = new THREE.Quaternion();
const _delta = new THREE.Quaternion();
const _euler = new THREE.Euler();
const _v1 = new THREE.Vector3();
const _v2 = new THREE.Vector3();
const _v3 = new THREE.Vector3();
const _g1 = new THREE.Vector3();
const _g2 = new THREE.Vector3();
const _g3 = new THREE.Vector3();
const FORWARD = new THREE.Vector3(0, 0, 1);

export class Humanizer {
  /**
   * @param root   la escena del GLB (donde está el esqueleto)
   * @param pivot  el pivote normalizado que la contiene (para la escala)
   */
  constructor(root, pivot) {
    this.level = 1;
    this.reduceMotion = false;
    this.data = null;
    this.pivot = pivot;
    this.clock = Math.random() * 100;
    this.bones = {};
    for (const name of ["spine", "spine.001", "spine.002", "spine.003", "neck", "head",
      "jaw", "eyeball.L", "eyeball.R", "hand.L", "hand.R"]) {
      const bone = findBone(root, name);
      if (bone) this.bones[name] = bone;
    }
    // Cada hueso que esta capa toca recuerda qué había antes y qué dejó escrito.
    this.touched = new Map();
    // Morph targets por nombre, en todas las mallas que lo tengan.
    this.morphs = new Map();
    root.traverse(obj => {
      if (!obj.isMesh || !obj.morphTargetDictionary) return;
      for (const [name, index] of Object.entries(obj.morphTargetDictionary)) {
        if (!this.morphs.has(name)) this.morphs.set(name, []);
        this.morphs.get(name).push({ mesh: obj, index, before: 0, written: NaN });
      }
    });

    // La cabeza en reposo, para saber cuánto se apartó y hacia dónde miran los ojos.
    root.updateWorldMatrix(true, true);
    this.headRest = new THREE.Quaternion();
    this.bones.head?.getWorldQuaternion(this.headRest);
    this.headRestInv = this.headRest.clone().invert();

    // Estado suavizado.
    this.s = {
      headPitch: 0, headYaw: 0, headRoll: 0, torsoRoll: 0,
      followYaw: 0, followLean: 0, guard: 1,
      prosodyPitch: 0, prosodyRoll: 0, prosodyLean: 0, shake: 0,
      signing: 0, eyeYaw: 0, eyePitch: 0,
    };
    this.face = {};
    this.shakePhase = 0;
    this.blink = { next: rand(1, 3), t: -1 };
    this.glance = { until: 0, cooldown: rand(1.5, 3), side: null };
    this.lastType = "";
    this.lastClip = "";
    this.diag = { clip: "-", frame: 0, head: [0, 0, 0], guard: 1 };
  }

  setData(data) {
    this.data = data && data.signs ? data : null;
  }

  /** Deshace lo escrito en el fotograma anterior, si nadie escribió después. */
  restore() {
    for (const [bone, memo] of this.touched) {
      if (memo.hasQ && bone.quaternion.equals(memo.wroteQ)) bone.quaternion.copy(memo.beforeQ);
      if (memo.hasP && bone.position.equals(memo.wroteP)) bone.position.copy(memo.beforeP);
      memo.hasQ = memo.hasP = false;
    }
    for (const list of this.morphs.values()) {
      for (const m of list) {
        if (m.written === m.written && m.mesh.morphTargetInfluences[m.index] === m.written) {
          m.mesh.morphTargetInfluences[m.index] = m.before;
        }
        m.written = NaN;
      }
    }
  }

  /**
   * Suma la capa sobre la pose que dejó el mixer.
   *
   * `ctx.layers` son los clips vivos (`{name, time, weight}`: el que suena y el
   * que se va en la fundición), `ctx.type` el tipo de oración, `ctx.last` si es
   * la última seña de la oración y `ctx.camera` la cámara de la escena.
   */
  apply(dt, ctx) {
    if (this.level <= 0) return;
    dt = Math.min(dt, 0.1);   // una pausa larga no debe disparar los resortes
    this.clock += dt;
    const t = this.clock;
    const s = this.s;
    const live = !this.reduceMotion;   // movimiento que no es lingüístico
    const L = this.level;

    const signingNow = ctx.layers.reduce((sum, l) => sum + l.weight, 0) > 0.01 ? 1 : 0;
    s.signing = approach(s.signing, signingNow, dt, 0.25);

    // 1 ── Lo medido en el intérprete, mezclado entre los clips vivos.
    const sample = this.sampleLayers(ctx.layers);
    const marked = ctx.type && ctx.type !== "AFFIRMATIVE";

    // 2 ── ¿Hay una mano sobre la cara? Entonces la cabeza no se aparta del clip.
    s.guard = approach(s.guard, this.contactGuard(), dt, 0.08);

    // 3 ── Prosodia del tipo de oración.
    const pros = (signingNow && PROSODY[ctx.type]) || null;
    const clipU = ctx.layers.length ? ctx.layers[0].progress ?? 0 : 0;
    let pPitch = 0, pRoll = 0, pLean = 0, shakeAmp = 0;
    if (pros) {
      pPitch = pros.pitch; pRoll = pros.roll; pLean = pros.lean;
      if (ctx.last && pros.holdLast) pPitch += pros.holdLast * smoothstep(0.15, 0.6, clipU);
      // Un cabeceo: sube y baja dentro de la seña, con el pico en su mitad.
      const nod = Math.sin(Math.PI * smoothstep(0.25, 0.8, clipU)) ** 2;
      if (pros.nodEach) pPitch += pros.nodEach * nod;
      if (ctx.last && pros.nodLast) pPitch += pros.nodLast * nod;
      if (pros.shakeDeg) shakeAmp = pros.shakeDeg;
    }
    s.prosodyPitch = approach(s.prosodyPitch, pPitch, dt, 0.16);
    s.prosodyRoll = approach(s.prosodyRoll, pRoll, dt, 0.22);
    s.prosodyLean = approach(s.prosodyLean, pLean, dt, 0.3);
    s.shake = approach(s.shake, shakeAmp, dt, 0.18);
    if (s.shake > 0.05) this.shakePhase += dt * 2 * Math.PI * (PROSODY.NEGATIVE.shakeHz);
    else this.shakePhase = 0;
    const shake = s.shake * Math.sin(this.shakePhase);

    // 4 ── Cabeza: lo medido + prosodia + un mínimo de vida propia.
    s.headPitch = approach(s.headPitch, sample.headPitch * TUNING.headGain, dt, 0.05);
    s.headYaw = approach(s.headYaw, sample.headYaw * TUNING.headGain, dt, 0.05);
    s.headRoll = approach(s.headRoll, sample.headRoll * TUNING.headGain, dt, 0.05);
    const idle = live ? 1 : 0;
    const headPitch = (s.headPitch + s.prosodyPitch + idle * 0.5 * drift(t * 0.9, 1.7)) * s.guard;
    const headYaw = (s.headYaw + shake + idle * 0.8 * drift(t * 0.7, 5.3)) * s.guard;
    const headRoll = (s.headRoll + s.prosodyRoll + idle * 0.4 * drift(t * 0.8, 9.1)) * s.guard;

    // 5 ── Tronco: inclinación medida, seguir a las manos, respirar y balancear.
    s.torsoRoll = approach(s.torsoRoll, sample.torsoRoll * TUNING.torsoGain, dt, 0.09);
    const follow = live ? this.handFollow() : { yaw: 0, lean: 0 };
    s.followYaw = approach(s.followYaw, follow.yaw, dt, TUNING.followSeconds);
    s.followLean = approach(s.followLean, follow.lean, dt, TUNING.followSeconds);
    const swayScale = idle * (1 - (1 - TUNING.swayWhileSigning) * s.signing);
    const swayRoll = TUNING.swayDeg * swayScale * drift(t * 0.55, 2.2);
    const swayYaw = TUNING.swayDeg * 1.2 * swayScale * drift(t * 0.43, 7.7);
    const breath = idle * TUNING.breathDeg * Math.sin(t * 2 * Math.PI * TUNING.breathHz);

    const torsoYaw = s.followYaw + swayYaw + headYaw * 0.12;
    const torsoRoll = s.torsoRoll + swayRoll;
    const torsoPitch = s.prosodyLean + s.followLean;

    // La cadera se mece; la columna lo compensa a medias para que no parezca un poste.
    this.rotate("spine", torsoPitch * 0.2 * L, swayYaw * 0.5 * L, swayRoll * 0.6 * L);
    if (swayScale > 0) {
      this.shift("spine", TUNING.swayMeters * swayScale * drift(t * 0.55, 2.2) * L, 0, 0);
    }
    this.rotate("spine.001", torsoPitch * 0.4 * L, torsoYaw * 0.45 * L, torsoRoll * 0.5 * L);
    this.rotate("spine.002", (torsoPitch * 0.4 - breath) * L, torsoYaw * 0.55 * L,
      (torsoRoll * 0.5 - swayRoll * 0.6) * L);
    this.rotate("spine.003", -breath * 0.6 * L, 0, 0);
    // El cuello devuelve la respiración: el pecho se mueve, la cabeza no cabecea con él.
    this.rotate("neck", (headPitch * TUNING.neckShare + breath * 1.2) * L,
      headYaw * TUNING.neckShare * L, headRoll * TUNING.neckShare * L);
    this.rotate("head", headPitch * (1 - TUNING.neckShare) * L,
      headYaw * (1 - TUNING.neckShare) * L, headRoll * (1 - TUNING.neckShare) * L);

    // 6 ── Cara.
    this.applyFace(sample, dt, marked, t, live);

    // 7 ── Ojos.
    this.applyGaze(dt, ctx, live);

    this.diag.head = [headPitch, headYaw, headRoll];
    this.diag.guard = s.guard;
    if (ctx.type !== this.lastType) this.lastType = ctx.type;
  }

  // ── Datos ──────────────────────────────────────────────────────────────────

  /** Valor de cada canal en este instante, ponderado por el peso de cada clip. */
  sampleLayers(layers) {
    const out = { headPitch: 0, headYaw: 0, headRoll: 0, torsoRoll: 0 };
    for (const key of Object.keys(FACE_MAP)) out[key] = 0;
    if (!this.data) return out;
    const fps = this.data.fps || FPS_DEFAULT;
    for (const layer of layers) {
      const sign = this.data.signs[layer.name.slice(0, 4)];
      if (!sign || layer.weight <= 0) continue;
      const at = clamp(layer.time * fps, 0, sign.n - 1);
      const i0 = Math.floor(at), i1 = Math.min(sign.n - 1, i0 + 1), f = at - i0;
      for (const key in sign.c) {
        const series = sign.c[key];
        const scale = ANGLE_CHANNELS.includes(key) ? 0.1 : 0.01;
        out[key] += (series[i0] + (series[i1] - series[i0]) * f) * scale * layer.weight;
      }
      this.diag.clip = layer.name;
      this.diag.frame = Math.round(at);
    }
    return out;
  }

  /** 1 = cabeza libre; 0 = hay una mano pegada a la cara. */
  contactGuard() {
    const head = this.bones.head;
    if (!head) return 1;
    const scale = this.pivot?.scale.x || 1;
    head.getWorldPosition(_v1);
    _v1.y += 0.08 * scale;   // el hueso nace en la nuca; la cara está más arriba
    let nearest = Infinity;
    for (const name of ["hand.L", "hand.R"]) {
      const hand = this.bones[name];
      if (!hand) continue;
      // La palma queda unos centímetros más allá de la muñeca.
      _v2.set(0, 0.08, 0).applyMatrix4(hand.matrixWorld);
      nearest = Math.min(nearest, _v2.distanceTo(_v1) / scale);
    }
    return smoothstep(TUNING.contactNear, TUNING.contactFar, nearest);
  }

  /** Hacia dónde tiran las manos al tronco: giro y avance, en grados. */
  handFollow() {
    const chest = this.bones["spine.003"];
    if (!chest) return { yaw: 0, lean: 0 };
    const scale = this.pivot?.scale.x || 1;
    chest.getWorldPosition(_v1);
    let x = 0, z = 0, weight = 0;
    for (const name of ["hand.L", "hand.R"]) {
      const hand = this.bones[name];
      if (!hand) continue;
      hand.getWorldPosition(_v2).sub(_v1).divideScalar(scale);
      // Solo cuenta una mano que está en el espacio de señas, no colgando.
      const raised = smoothstep(-0.32, -0.12, _v2.y);
      x += _v2.x * raised; z += _v2.z * raised; weight += raised;
    }
    if (weight < 0.05) return { yaw: 0, lean: 0 };
    const spread = Math.min(1, weight);
    return {
      yaw: clamp(TUNING.followYawPerMeter * (x / weight), -TUNING.followYawMax, TUNING.followYawMax) * spread,
      lean: clamp(TUNING.followLeanPerMeter * (z / weight - 0.18), 0, TUNING.followLeanMax) * spread,
    };
  }

  // ── Cara ───────────────────────────────────────────────────────────────────

  applyFace(sample, dt, marked, t, live) {
    const L = this.level;
    // Parpadeo espontáneo.
    const blink = this.blink;
    blink.next -= dt;
    if (blink.next <= 0 && blink.t < 0) {
      blink.t = 0;
      blink.next = rand(...TUNING.blinkGap);
    }
    let lids = 0;
    if (blink.t >= 0) {
      blink.t += dt;
      const u = blink.t / TUNING.blinkSeconds;
      // Cierra rápido (un tercio) y abre más lento.
      lids = u < 0.33 ? smoothstep(0, 0.33, u) : 1 - smoothstep(0.33, 1, u);
      if (u >= 1) blink.t = -1;
    }

    for (const channel of Object.keys(FACE_MAP)) {
      let value = sample[channel] * TUNING.faceGain;
      if (marked && BROW_CHANNELS.has(channel)) value *= TUNING.lexicalBrowUnderMarker;
      if (channel === "eyeBlink") value = Math.max(sample[channel] * TUNING.nmmBlinkGain, lids);
      // Una pizca de vida en la boca en reposo, para que no sea una máscara.
      if (live && (channel === "mouthSmile_L" || channel === "mouthSmile_R")) {
        value += 0.04 * (1 + drift(t * 0.5, channel.length));
      }
      // Los párpados van sin filtro (un parpadeo dura 240 ms); el resto se suaviza.
      const tau = channel === "eyeBlink" ? 0.02 : 0.06;
      const smoothed = this.face[channel] = approach(this.face[channel] ?? 0, value, dt, tau);
      if (smoothed < 0.004) continue;
      const additive = !BROW_CHANNELS.has(channel) && channel !== "eyeBlink";
      for (const [morph, factor] of FACE_MAP[channel]) {
        this.morph(morph, smoothed * factor * L, additive);
      }
    }
    // Los dientes de abajo van con el hueso, no con el morph.
    const jaw = (this.face.jawOpen ?? 0) * TUNING.jawBoneDeg * L;
    if (jaw > 0.05) this.rotate("jaw", jaw, 0, 0);
  }

  // ── Mirada ─────────────────────────────────────────────────────────────────

  applyGaze(dt, ctx, live) {
    const head = this.bones.head;
    const eyeL = this.bones["eyeball.L"], eyeR = this.bones["eyeball.R"];
    if (!head || !eyeL || !eyeR || !ctx.camera) return;
    const s = this.s;
    const scale = this.pivot?.scale.x || 1;

    // Hacia dónde apunta hoy la cara, y dónde está lo que hay que mirar.
    head.getWorldQuaternion(_q);
    _delta.copy(_q).multiply(this.headRestInv);          // cuánto se apartó del reposo
    const facing = _g1.copy(FORWARD).applyQuaternion(_delta);
    eyeL.getWorldPosition(_g2).add(eyeR.getWorldPosition(_g3)).multiplyScalar(0.5);
    const eyes = _g2;

    // De vez en cuando la mirada acompaña a la mano que está más lejos del cuerpo.
    const glance = this.glance;
    glance.cooldown -= dt;
    let target = null;
    if (TUNING.glances && live && this.clock < glance.until && glance.side) {
      target = this.bones[glance.side].getWorldPosition(_g3);
    } else if (TUNING.glances && live && glance.cooldown <= 0 && ctx.layers.length) {
      const side = this.farHand(scale);
      if (side) {
        glance.side = side;
        glance.until = this.clock + rand(...TUNING.glanceSeconds);
        target = this.bones[side].getWorldPosition(_g3);
      }
      glance.cooldown = rand(TUNING.glanceMinGap, TUNING.glanceMinGap * 2.2);
    }
    if (!target) target = _g3.copy(ctx.camera.position);

    const want = target.sub(eyes).normalize();
    // Ángulos de la mirada pedida respecto de donde apunta la cara.
    const yawWant = Math.atan2(want.x, want.z) - Math.atan2(facing.x, facing.z);
    const pitchWant = Math.asin(clamp(facing.y, -1, 1)) - Math.asin(clamp(want.y, -1, 1));
    const max = TUNING.eyeMaxDeg * DEG;
    // Microsacadas: el ojo nunca está del todo quieto.
    const jitter = live ? 0.6 * DEG : 0;
    const targetYaw = clamp(yawWant, -max, max) + jitter * drift(this.clock * 3.1, 3.3);
    const restLift = TUNING.restEyeLiftDeg * DEG * (1 - s.signing);
    const targetPitch = clamp(pitchWant, -max * 0.7, max * 0.7) - restLift + jitter * drift(this.clock * 2.7, 8.8);
    // Un salto grande de mirada va con parpadeo, como en una persona.
    if (Math.abs(targetYaw - s.eyeYaw) > 10 * DEG && this.blink.t < 0) this.blink.t = 0;
    s.eyeYaw = approach(s.eyeYaw, targetYaw, dt, 0.035);
    s.eyePitch = approach(s.eyePitch, targetPitch, dt, 0.035);

    const L = this.level;
    this.rotate("eyeball.L", s.eyePitch / DEG * L, s.eyeYaw / DEG * L, 0);
    this.rotate("eyeball.R", s.eyePitch / DEG * L, s.eyeYaw / DEG * L, 0);
  }

  /** La mano levantada y apartada del cuerpo, si hay una clara. */
  farHand(scale) {
    const chest = this.bones["spine.003"];
    if (!chest) return null;
    chest.getWorldPosition(_v1);
    let best = null, bestReach = 0.34;
    for (const name of ["hand.L", "hand.R"]) {
      const hand = this.bones[name];
      if (!hand) continue;
      hand.getWorldPosition(_v2).sub(_v1).divideScalar(scale);
      if (_v2.y < -0.2) continue;
      const reach = Math.hypot(_v2.x, _v2.z);
      if (reach > bestReach) { bestReach = reach; best = name; }
    }
    return best;
  }

  // ── Escritura ──────────────────────────────────────────────────────────────

  /**
   * Gira un hueso en **ejes del avatar** (x = cabeceo con el mentón hacia abajo,
   * y = giro, z = ladeo), sin importar cómo esté orientado el hueso en el rig.
   */
  rotate(name, pitchDeg, yawDeg, rollDeg) {
    const bone = this.bones[name];
    if (!bone) return;
    if (Math.abs(pitchDeg) + Math.abs(yawDeg) + Math.abs(rollDeg) < 1e-4) return;
    const memo = this.memo(bone);
    if (!memo.hasQ) { memo.beforeQ.copy(bone.quaternion); memo.hasQ = true; }
    _euler.set(pitchDeg * DEG, yawDeg * DEG, rollDeg * DEG, "YXZ");
    _delta.setFromEuler(_euler);
    // local' = P⁻¹ · D · P · local, con P la orientación de mundo del padre.
    if (bone.parent) bone.parent.getWorldQuaternion(_parent); else _parent.identity();
    _q.copy(_parent).invert().multiply(_delta).multiply(_parent).multiply(bone.quaternion);
    bone.quaternion.copy(_q);
    memo.wroteQ.copy(bone.quaternion);
  }

  /** Desplaza un hueso en metros del avatar (antes de la escala del pivote). */
  shift(name, x, y, z) {
    const bone = this.bones[name];
    if (!bone) return;
    const memo = this.memo(bone);
    if (!memo.hasP) { memo.beforeP.copy(bone.position); memo.hasP = true; }
    bone.position.x += x; bone.position.y += y; bone.position.z += z;
    memo.wroteP.copy(bone.position);
  }

  memo(bone) {
    let memo = this.touched.get(bone);
    if (!memo) {
      memo = {
        hasQ: false, hasP: false,
        beforeQ: new THREE.Quaternion(), wroteQ: new THREE.Quaternion(),
        beforeP: new THREE.Vector3(), wroteP: new THREE.Vector3(),
      };
      this.touched.set(bone, memo);
    }
    return memo;
  }

  /** Sube un morph: sumando a lo que trae el clip, o quedándose con el mayor. */
  morph(name, value, additive) {
    const list = this.morphs.get(name);
    if (!list) return;
    for (const m of list) {
      const influences = m.mesh.morphTargetInfluences;
      const current = influences[m.index];
      if (m.written !== m.written) m.before = current;   // primera escritura del fotograma
      const next = additive ? Math.min(1, current + value) : Math.max(current, value);
      influences[m.index] = next;
      m.written = next;
    }
  }

  /** Líneas para el overlay de diagnóstico. */
  debugLines() {
    const [p, y, r] = this.diag.head;
    return [
      `humano: ${this.level.toFixed(2)}${this.reduceMotion ? " (mov. reducido)" : ""}` +
        `  datos: ${this.data ? Object.keys(this.data.signs).length + " señas" : "sin nmm.json"}`,
      `cabeza +${p.toFixed(1)}° / ${y.toFixed(1)}° / ${r.toFixed(1)}°  contacto: ${this.diag.guard.toFixed(2)}` +
        `  nmm: ${this.diag.clip}#${this.diag.frame}`,
    ];
  }
}

function findBone(root, name) {
  // GLTFLoader sanea los nombres: `spine.001` llega como `spine001`.
  const clean = THREE.PropertyBinding.sanitizeNodeName(name);
  let found = null;
  root.traverse(obj => {
    if (!found && obj.isBone && (obj.name === name || obj.name === clean)) found = obj;
  });
  return found;
}
