// Prueba de `humanize.js`: la capa no acumula ni altera la pose del mixer.
//
// Corre dos esqueletos iguales con la misma secuencia de órdenes (arrancar,
// fundir, cortar a mitad, pausar, parar): uno sin la capa y otro con ella. Después
// de cada `restore()` los dos tienen que ser idénticos, y con `level = 0` también.
//
// Necesita `three`, que la app carga por CDN y el repo no instala:
//   mkdir /tmp/h && cd /tmp/h && npm init -y && npm i three@0.169.0
//   mkdir -p tools app/src/main/assets/web
//   cp <repo>/tools/test_humanize.mjs tools/
//   cp <repo>/app/src/main/assets/web/{humanize.js,nmm.json} app/src/main/assets/web/
//   echo '{"type":"module"}' > app/src/main/assets/web/package.json
//   node tools/test_humanize.mjs      # → mismatches: 0, finalIdentical: true, outsideFrameMismatches: 0
import * as THREE from "three";
import { Humanizer } from "../app/src/main/assets/web/humanize.js";
import fs from "node:fs";
const nmm = JSON.parse(fs.readFileSync(new URL("../app/src/main/assets/web/nmm.json", import.meta.url)));
function build() {
  const root = new THREE.Object3D(); const pivot = new THREE.Object3D(); pivot.add(root);
  const names = ["spine", "spine001", "spine002", "spine003", "neck", "head"]; let parent = root; const bones = {};
  for (const n of names) { const b = new THREE.Bone(); b.name = n; b.position.set(0, 0.2, 0); b.quaternion.setFromEuler(new THREE.Euler(0.1, 0.05, 0)); parent.add(b); bones[n] = b; parent = b; }
  for (const n of ["jaw", "eyeballL", "eyeballR"]) { const b = new THREE.Bone(); b.name = n; b.position.set(n.endsWith("L") ? 0.03 : -0.03, 0.05, 0.08); bones.head.add(b); bones[n] = b; }
  for (const n of ["handL", "handR"]) { const b = new THREE.Bone(); b.name = n; b.position.set(n.endsWith("L") ? 0.3 : -0.3, -0.3, 0.1); bones.spine003.add(b); bones[n] = b; }
  const mesh = new THREE.Mesh(new THREE.BufferGeometry()); mesh.name = "face";
  const morphs = ["jawOpen", "mouthSmile_L", "mouthSmile_R", "browDown_L", "browDown_R", "browInnerUp", "eyeBlink_L", "eyeBlink_R", "mouthPucker"];
  mesh.morphTargetDictionary = Object.fromEntries(morphs.map((m, i) => [m, i])); mesh.morphTargetInfluences = morphs.map(() => 0); root.add(mesh);
  const t = [0, 0.5, 1, 1.5, 2];
  const q = a => t.flatMap(x => new THREE.Quaternion().setFromEuler(new THREE.Euler(0.1 + a * Math.sin(x * 3), 0.05, 0)).toArray());
  const clip = name => new THREE.AnimationClip(name, 2, [
    new THREE.QuaternionKeyframeTrack("head.quaternion", t, q(0.2)),
    new THREE.QuaternionKeyframeTrack("neck.quaternion", [0, 2], [...bones.neck.quaternion.toArray(), ...bones.neck.quaternion.toArray()]),  // constante: el mixer no la reescribe
    new THREE.QuaternionKeyframeTrack("handL.quaternion", t, q(0.9)),
    new THREE.NumberKeyframeTrack("face.morphTargetInfluences", [0, 2], [...morphs.map((m, i) => i === 3 ? 0.45 : 0), ...morphs.map((m, i) => i === 3 ? 0.45 : 0)]),
  ]);
  return { root, pivot, bones, mesh, mixer: new THREE.AnimationMixer(root), clips: [clip("0041_negacion"), clip("0020_negacion")] };
}
const snap = s => JSON.stringify([...Object.values(s.bones).flatMap(b => [...b.quaternion.toArray(), ...b.position.toArray()]), ...s.mesh.morphTargetInfluences].map(x => +x.toFixed(6)));
const A = build(), B = build();
const h = new Humanizer(B.root, B.pivot); h.setData(nmm);
const cam = { position: new THREE.Vector3(0, 1.5, 2.3) };
let maxHead = 0, maxMorph = 0, mismatches = 0, frames = 0;
function script(s, step) {  // misma secuencia de órdenes en los dos
  if (step === 10) s.act = s.mixer.clipAction(s.clips[0]).reset().play();
  if (step === 70) { const n = s.mixer.clipAction(s.clips[1]).reset().play(); s.act.crossFadeTo(n, 0.1, false); s.out = s.act; s.act = n; }
  if (step === 130) { s.mixer.stopAllAction(); s.act = s.out = null; }      // stop() a mitad
  if (step === 160) s.act = s.mixer.clipAction(s.clips[0]).reset().play();
  if (step === 200) s.act.paused = true;                                     // salida muestreada a mano
  if (step === 230) { s.mixer.stopAllAction(); s.act = s.out = null; }
}
for (let step = 0; step < 400; step++) {
  const dt = 1 / 60;
  h.restore();
  // después de restore, B tiene que ser idéntico a A antes de este fotograma
  if (snap(A) !== snap(B)) mismatches++;
  script(A, step); script(B, step);
  A.mixer.update(dt); B.mixer.update(dt);
  if (snap(A) !== snap(B)) mismatches++;
  const layers = [B.act, B.out].filter(Boolean).map(a => ({ name: a.getClip().name, time: a.time, weight: a.getEffectiveWeight(), progress: a.time / 2 })).filter(l => l.weight > 0);
  if (step === 300) h.level = 0;
  if (step === 330) h.level = 1;
  if (step === 360) h.reduceMotion = true;
  B.root.updateWorldMatrix(true, true);
  h.apply(dt, { layers, type: layers.length ? "NEGATIVE" : "", last: true, camera: cam });
  maxHead = Math.max(maxHead, 2 * Math.acos(Math.min(1, Math.abs(A.bones.head.quaternion.dot(B.bones.head.quaternion)))) * 180 / Math.PI);
  maxMorph = Math.max(maxMorph, ...B.mesh.morphTargetInfluences);
  if (step > 300 && step < 330 && snap(A) !== snap(B)) mismatches++;       // nivel 0 = sin capa
  if (B.mesh.morphTargetInfluences.some(x => x < 0 || x > 1 || Number.isNaN(x))) mismatches++;
  frames++;
}
h.restore();

// Segunda parte: las órdenes llegan **fuera** del fotograma, como en la app
// (`startClip` corre después de un `await`). El loop es el de `app.js`: restore,
// mixer, apply, pintar, restore. Sin el segundo `restore` el mixer guarda la capa
// como pose de reposo y los ojos se van acumulando turno a turno.
const C = build(), D = build();
const h2 = new Humanizer(D.root, D.pivot); h2.setData(nmm);
let outside = 0;
for (let step = 0; step < 1200; step++) {
  const dt = 1 / 60;
  h2.restore();
  C.mixer.update(dt); D.mixer.update(dt);
  const layers = D.act ? [{ name: D.act.getClip().name, time: D.act.time, weight: D.act.getEffectiveWeight(), progress: D.act.time / 2 }] : [];
  D.root.updateWorldMatrix(true, true);
  h2.apply(dt, { layers, type: layers.length ? "AFFIRMATIVE" : "", last: true, camera: cam });
  h2.restore();                                   // el de después de pintar
  if (snap(C) !== snap(D)) outside++;
  const phase = step % 200;                       // seis turnos: arrancar y parar
  if (phase === 80) h2.blink.t = 0.05;            // la orden cae a mitad de un parpadeo
  for (const s of [C, D]) {
    if (phase === 80) s.act = s.mixer.clipAction(s.clips[0]).reset().play().fadeIn(0.1);
    if (phase === 180) { s.mixer.stopAllAction(); s.act = null; }
  }
}
console.log(JSON.stringify({ frames, mismatches, finalIdentical: snap(A) === snap(B), maxHeadOffsetDeg: +maxHead.toFixed(1), maxMorph: +maxMorph.toFixed(2), outsideFrameMismatches: outside }));
