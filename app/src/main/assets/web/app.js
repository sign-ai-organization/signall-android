// Puerto de app/static/app.js: setup + GLB + retargeting completo (IK brazos,
// cara, manos). Sin video ni selector de señas: los frames vienen de sign.json
// (asset) y se reproducen vía window.SignAI.play() desde Android.

import * as THREE from "three";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";

// ── Mapeo landmarks MediaPipe → huesos DEF de Rigify ────────────────────────
const BONE_MAP = [
  ["thumb01L",    1, 2],  ["thumb02L",    2, 3],  ["thumb03L",    3, 4],
  ["f_index01L",  5, 6],  ["f_index02L",  6, 7],  ["f_index03L",  7, 8],
  ["f_middle01L", 9,10],  ["f_middle02L",10,11],  ["f_middle03L",11,12],
  ["f_ring01L",  13,14],  ["f_ring02L",  14,15],  ["f_ring03L",  15,16],
  ["f_pinky01L", 17,18],  ["f_pinky02L", 18,19],  ["f_pinky03L", 19,20],
  ["thumb01R",    1, 2],  ["thumb02R",    2, 3],  ["thumb03R",    3, 4],
  ["f_index01R",  5, 6],  ["f_index02R",  6, 7],  ["f_index03R",  7, 8],
  ["f_middle01R", 9,10],  ["f_middle02R",10,11],  ["f_middle03R",11,12],
  ["f_ring01R",  13,14],  ["f_ring02R",  14,15],  ["f_ring03R",  15,16],
  ["f_pinky01R", 17,18],  ["f_pinky02R", 18,19],  ["f_pinky03R", 19,20],
];

function lmDir(a, b) {
  return new THREE.Vector3(b.x - a.x, -(b.y - a.y), -(b.z - a.z));
}

function mpToThree(lm, wrist) {
  return new THREE.Vector3(
     (lm.x - wrist.x),
    -(lm.y - wrist.y),
    0,
  );
}

// ── Estado global ─────────────────────────────────────────────────────────────
const state = {
  bones:         new Map(),
  boneRestLocalQ: new Map(),
  boneRestWorldQ: new Map(),
  boneRestDir:   new Map(),
  frames: [],
  fps: 24,
  frameIdx: 0,
  playing: false,
  lastTime: null,
  animHandle: null,
  smoothAlpha: 0.12,
  palmNormalL: new THREE.Vector3(0, 0, 1),
  palmNormalR: new THREE.Vector3(0, 0, 1),
  armRest: {
    shoulderL: new THREE.Vector3(), shoulderR: new THREE.Vector3(),
    L_upperL: 0, L_foreL: 0,
    L_upperR: 0, L_foreR: 0,
  },
};

// ── DEBUG ─────────────────────────────────────────────────────────────────────
// Centro único de opciones debug. Para agregar una opción:
//   1. Agregarla a `flags` con una función que devuelva líneas de texto.
//   2. Llamarla donde corresponda (o usar el refresh del render loop).
// Toggle global: Debug.enabled = false, o window.SignAI.setDebug(false) desde Android.
const Debug = {
  enabled: false,
  el: null,
  statusEl: null,
  flags: {
    playback: () => [
      `play: ${state.playing}  frame: ${Math.floor(state.frameIdx)}/${state.frames.length}  fps_datos: ${state.fps}`,
      `alpha_smooth: ${state.smoothAlpha}`,
      `palmL: (${state.palmNormalL.x.toFixed(2)}, ${state.palmNormalL.y.toFixed(2)}, ${state.palmNormalL.z.toFixed(2)})`,
      `palmR: (${state.palmNormalR.x.toFixed(2)}, ${state.palmNormalR.y.toFixed(2)}, ${state.palmNormalR.z.toFixed(2)})`,
    ],
    camera: () => {
      const p = camera.position, t = controls.target;
      const dist = camera.position.distanceTo(controls.target);
      return [
        `cam: (${p.x.toFixed(2)}, ${p.y.toFixed(2)}, ${p.z.toFixed(2)})  target: (${t.x.toFixed(2)}, ${t.y.toFixed(2)}, ${t.z.toFixed(2)})`,
        `dist: ${dist.toFixed(2)}  fov: ${camera.fov}°  dpr: ${window.devicePixelRatio}`,
      ];
    },
    avatar: () => [
      `huesos: ${state.bones.size}  brazoL: ${state.armRest.L_upperL.toFixed(3)}+${state.armRest.L_foreL.toFixed(3)}`,
      `canvas: ${canvas.clientWidth}x${canvas.clientHeight}`,
    ],
    render: () => {
      const i = renderer.info.render;
      return [`draws: ${i.calls}  tris: ${i.triangles}  frame: ${i.frame}`];
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

// Estado del long press (declarado temprano: el debug panel lo lee cada frame)
let pressTimer = null;
let pressStart = null;
let pressStartTime = 0;
let activePointers = 0;

// ── Three.js setup ────────────────────────────────────────────────────────────
const canvas   = document.getElementById("avatar-canvas");
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
renderer.setPixelRatio(window.devicePixelRatio);
renderer.outputColorSpace = THREE.SRGBColorSpace;

const scene  = new THREE.Scene();
scene.background = new THREE.Color(0x0d0f1a);

const camera = new THREE.PerspectiveCamera(45, 1, 0.01, 100);
// De frente, a la altura del cuello (~1.45 con avatar de 1.7): encuadre centrado en cara/cuello
camera.position.set(0, 1.45, 2);

const controls = new OrbitControls(camera, canvas);
controls.target.set(0, 1.45, 0);
controls.enableDamping = true;
controls.dampingFactor = 0.08;
controls.enableRotate = false;  // solo zoom: sin orbitar ni pan
controls.enablePan = false;
controls.minDistance = 1.4;
controls.maxDistance = 3.8;

scene.add(new THREE.AmbientLight(0xffffff, 0.7));
const key = new THREE.DirectionalLight(0xffffff, 1.2);
key.position.set(2, 4, 3);
scene.add(key);
const fill = new THREE.DirectionalLight(0x8090ff, 0.4);
fill.position.set(-2, 2, -2);
scene.add(fill);
const grid = new THREE.GridHelper(4, 8, 0x1e2130, 0x1e2130);
scene.add(grid);

// Fondo opcional (bosque): se carga una vez, se activa vía SignAI.setBackground(true)
const BG_COLOR = new THREE.Color(0x0d0f1a);
let bgTexture = null;
new THREE.TextureLoader().load("bosque.jpg", (t) => { bgTexture = t; });

function resizeRenderer() {
  const w = canvas.clientWidth  || window.innerWidth;
  const h = canvas.clientHeight || window.innerHeight;
  if (w && h && (renderer.domElement.width !== w || renderer.domElement.height !== h)) {
    renderer.setSize(w, h);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  }
}
function renderLoop() {
  requestAnimationFrame(renderLoop);
  resizeRenderer();
  controls.update();
  renderer.render(scene, camera);
  Debug.render();
}
renderLoop();

function setStatus(msg) {
  document.getElementById("status").textContent = msg;
}

// ── Carga del avatar GLB ─────────────────────────────────────────────────────
new GLTFLoader().load("avatar.glb", (gltf) => {
  gltf.scene.traverse(obj => {
    if (obj.name.startsWith("WGT-") || obj.name === "metarig") obj.visible = false;
    if (obj.isMesh) obj.frustumCulled = false;
  });

  scene.add(gltf.scene);

  const meshBox = new THREE.Box3();
  gltf.scene.traverse(obj => { if (obj.isMesh && obj.visible) meshBox.expandByObject(obj); });
  if (!meshBox.isEmpty()) {
    const h = meshBox.getSize(new THREE.Vector3()).y;
    const s = 1.7 / h;
    gltf.scene.scale.setScalar(s);
    const c = meshBox.getCenter(new THREE.Vector3());
    gltf.scene.position.set(-c.x * s, -meshBox.min.y * s, -c.z * s);
  }

  gltf.scene.updateWorldMatrix(true, true);

  gltf.scene.traverse(obj => {
    if (!obj.isBone) return;
    state.bones.set(obj.name, obj);
    state.boneRestLocalQ.set(obj.name, obj.quaternion.clone());
    const wq = new THREE.Quaternion();
    obj.getWorldQuaternion(wq);
    state.boneRestWorldQ.set(obj.name, wq.clone());
    const dir = new THREE.Vector3(0, 1, 0).applyQuaternion(wq).normalize();
    state.boneRestDir.set(obj.name, dir);
  });

  measureArmRest();

  // Cargar frames de la seña demo
  fetch("sign.json").then(r => r.json()).then(data => {
    state.frames   = data.frames ?? [];
    state.fps      = data.fps ?? 24;
    setStatus(`Avatar listo — ${data.total_frames} frames · presiona Enviar`);
  }).catch(err => {
    setStatus("Error cargando sign.json");
    console.error(err);
  });

}, (xhr) => {
  setStatus(`Cargando... ${Math.round(xhr.loaded / xhr.total * 100)}%`);
}, (err) => {
  setStatus("Error cargando avatar.glb");
  console.error(err);
});

// ── IK de brazos ─────────────────────────────────────────────────────────────

function measureArmRest() {
  const g  = state.armRest;
  const bSL = state.bones.get("DEF-upper_armL"), bEL = state.bones.get("DEF-forearmL"), bWL = state.bones.get("DEF-handL");
  const bSR = state.bones.get("DEF-upper_armR"), bER = state.bones.get("DEF-forearmR"), bWR = state.bones.get("DEF-handR");
  if (!bSL || !bEL || !bWL || !bSR || !bER || !bWR) return;
  const eL = new THREE.Vector3(), wL = new THREE.Vector3();
  const eR = new THREE.Vector3(), wR = new THREE.Vector3();
  bSL.getWorldPosition(g.shoulderL); bEL.getWorldPosition(eL); bWL.getWorldPosition(wL);
  bSR.getWorldPosition(g.shoulderR); bER.getWorldPosition(eR); bWR.getWorldPosition(wR);
  g.L_upperL = g.shoulderL.distanceTo(eL); g.L_foreL = eL.distanceTo(wL);
  g.L_upperR = g.shoulderR.distanceTo(eR); g.L_foreR = eR.distanceTo(wR);
}

const _ikWrist = new THREE.Vector3();
const _ikEHint = new THREE.Vector3();
const _ikElbow = new THREE.Vector3();
const _ikTT    = new THREE.Vector3();
const _ikPole  = new THREE.Vector3();

function lmWorldOffset(lm, lmRef, scale, worldRef, out) {
  const Z_SCALE = 0.40;
  return out.set(
    (lm.x - lmRef.x) * scale,
    -(lm.y - lmRef.y) * scale,
    -(lm.z - lmRef.z) * scale * Z_SCALE,
  ).add(worldRef);
}

function bodyScale(body) {
  const a = body[11], b = body[12];
  const lmSep = Math.sqrt((a.x-b.x)**2 + (a.y-b.y)**2 + (a.z-b.z)**2);
  if (lmSep < 1e-6) return 1;
  return state.armRest.shoulderL.distanceTo(state.armRest.shoulderR) / lmSep;
}

function solveIKElbow(shoulder, target, pole, L1, L2) {
  _ikTT.subVectors(target, shoulder);
  const D = Math.min(_ikTT.length(), L1 + L2 - 1e-4);
  _ikTT.normalize();

  const cosA = THREE.MathUtils.clamp((L1*L1 + D*D - L2*L2) / (2*L1*D), -1, 1);
  const sinA  = Math.sqrt(1 - cosA*cosA);

  pole.addScaledVector(_ikTT, -pole.dot(_ikTT));
  if (pole.lengthSq() < 1e-8) {
    pole.set(0, 1, 0).addScaledVector(_ikTT, -_ikTT.y).normalize();
  } else {
    pole.normalize();
  }

  _ikElbow.copy(shoulder)
    .addScaledVector(_ikTT, cosA * L1)
    .addScaledVector(pole, sinA * L1);
}

function applyArmIK(body) {
  if (!state.armRest.L_upperL) return;
  const scale = bodyScale(body);
  const g = state.armRest;

  _applyOneArm(body, 11, 13, 15,
    "DEF-upper_armL", "DEF-upper_armL001", "DEF-forearmL", "DEF-forearmL001",
    g.shoulderL, g.L_upperL, g.L_foreL, scale);

  _applyOneArm(body, 12, 14, 16,
    "DEF-upper_armR", "DEF-upper_armR001", "DEF-forearmR", "DEF-forearmR001",
    g.shoulderR, g.L_upperR, g.L_foreR, scale);
}

function _applyOneArm(body, iS, iE, iW,
    nUA, nUA1, nFA, nFA1, shoulderWorld, L1, L2, scale) {
  if (L1 < 1e-6 || L2 < 1e-6) return;

  lmWorldOffset(body[iW], body[iS], scale, shoulderWorld, _ikWrist);
  lmWorldOffset(body[iE], body[iS], scale, shoulderWorld, _ikEHint);

  _ikPole.subVectors(_ikEHint, shoulderWorld);
  _ikPole.y -= (L1 + L2) * 0.35;
  _ikPole.z += (L1 + L2) * 0.10;

  solveIKElbow(shoulderWorld, _ikWrist, _ikPole, L1, L2);

  const dirUA = _ikTT.subVectors(_ikElbow, shoulderWorld);
  const dirFA = _ikEHint.subVectors(_ikWrist, _ikElbow);

  const bUA  = state.bones.get(nUA),  bUA1 = state.bones.get(nUA1);
  const bFA  = state.bones.get(nFA),  bFA1 = state.bones.get(nFA1);

  if (bUA)  rotateBone(bUA,  dirUA);
  if (bUA1) rotateBone(bUA1, dirUA);
  if (bFA)  rotateBone(bFA,  dirFA);
  if (bFA1) rotateBone(bFA1, dirFA);
}

// ── Retargeting ───────────────────────────────────────────────────────────────

const _dQ      = new THREE.Quaternion();
const _tWQ     = new THREE.Quaternion();
const _pWQ     = new THREE.Quaternion();
const _tLQ     = new THREE.Quaternion();
const _dir     = new THREE.Vector3();
const _xAxis   = new THREE.Vector3(1, 0, 0);
const _faceDir = new THREE.Vector3();

const _hUp   = new THREE.Vector3();
const _hIdxV = new THREE.Vector3();
const _hPnkV = new THREE.Vector3();
const _hNorm = new THREE.Vector3();
const _hSide = new THREE.Vector3();
const _hMat  = new THREE.Matrix4();
const _hQ    = new THREE.Quaternion();

function rotateBone(bone, targetDir, alpha = state.smoothAlpha) {
  const restDir    = state.boneRestDir.get(bone.name);
  const restWorldQ = state.boneRestWorldQ.get(bone.name);
  if (!restDir || !restWorldQ) return;

  _dir.copy(targetDir);
  if (_dir.lengthSq() < 1e-6) return;
  _dir.normalize();

  _dQ.setFromUnitVectors(restDir, _dir);
  _tWQ.multiplyQuaternions(_dQ, restWorldQ);

  if (bone.parent) {
    bone.parent.getWorldQuaternion(_pWQ);
    _tLQ.multiplyQuaternions(_pWQ.invert(), _tWQ);
  } else {
    _tLQ.copy(_tWQ);
  }
  bone.quaternion.slerp(_tLQ, alpha);
  bone.updateMatrixWorld(true);
}

function applyHandOrientation(bone, rawLms, normalSign) {
  const w = rawLms[0];

  _hUp.set(rawLms[9].x - w.x, -(rawLms[9].y - w.y), -(rawLms[9].z - w.z));
  if (_hUp.lengthSq() < 1e-8) return;
  _hUp.normalize();
  rotateBone(bone, _hUp);

  _hIdxV.set(rawLms[5].x  - w.x, -(rawLms[5].y  - w.y), -(rawLms[5].z  - w.z));
  _hPnkV.set(rawLms[17].x - w.x, -(rawLms[17].y - w.y), -(rawLms[17].z - w.z));
  _hNorm.crossVectors(_hIdxV, _hPnkV).multiplyScalar(normalSign);
  if (_hNorm.lengthSq() < 1e-8) return;
  _hNorm.normalize();
  _hNorm.addScaledVector(_hUp, -_hNorm.dot(_hUp)).normalize();
  if (_hNorm.lengthSq() < 1e-8) return;

  bone.getWorldQuaternion(_hQ);

  _hSide.set(0, 0, 1).applyQuaternion(_hQ);
  _hSide.addScaledVector(_hUp, -_hSide.dot(_hUp)).normalize();
  if (_hSide.lengthSq() < 1e-8) return;

  _dQ.setFromUnitVectors(_hSide, _hNorm);

  _tWQ.multiplyQuaternions(_dQ, _hQ);

  if (bone.parent) {
    bone.parent.getWorldQuaternion(_pWQ);
    _tLQ.multiplyQuaternions(_pWQ.invert(), _tWQ);
  } else {
    _tLQ.copy(_tWQ);
  }
  bone.quaternion.slerp(_tLQ, state.smoothAlpha);
  bone.updateMatrixWorld(true);

  if (bone.name === "DEF-handL") state.palmNormalL.copy(_hNorm);
  else                           state.palmNormalR.copy(_hNorm);
}

// ── Animación facial ─────────────────────────────────────────────────────────
const FACE_REF_BROW   = 0.47;
const FACE_BROW_SCALE = 6.0;
const FACE_JAW_MAX    = 1.2;
const FACE_LIP_SCALE  = 4.0;
const FACE_LIP_MAX    = 0.5;
const FACE_ALPHA = 1.0;

function applyFace(face) {
  const lipT = face["13"], lipB = face["14"];
  const cL   = face["78"], cR   = face["308"];
  if (lipT && lipB && cL && cR) {
    const mouthW  = Math.abs(cR.x - cL.x);
    const jawFactor = mouthW > 1e-4
      ? THREE.MathUtils.clamp((lipB.y - lipT.y) / mouthW, 0, 0.6)
      : 0;
    const bJaw = state.bones.get("DEF-jaw_master");
    if (bJaw) {
      const rd = state.boneRestDir.get("DEF-jaw_master");
      if (rd) {
        _faceDir.copy(rd).applyAxisAngle(_xAxis, jawFactor * FACE_JAW_MAX);
        rotateBone(bJaw, _faceDir, FACE_ALPHA);
      }
    }
  }

  const lidTL = face["159"], lidTR = face["386"];
  const browL  = face["107"], browR  = face["336"];
  if (lidTL && lidTR && browL && browR) {
    const iod = Math.abs(lidTR.x - lidTL.x);
    if (iod > 0.01) {
      const browHtL = (lidTL.y - browL.y) / iod;
      const browHtR = (lidTR.y - browR.y) / iod;
      const raiseL  = THREE.MathUtils.clamp((browHtL - FACE_REF_BROW) * FACE_BROW_SCALE, -0.6, 0.6);
      const raiseR  = THREE.MathUtils.clamp((browHtR - FACE_REF_BROW) * FACE_BROW_SCALE, -0.6, 0.6);

      const bBL = state.bones.get("DEF-browTL");
      if (bBL) {
        const rd = state.boneRestDir.get("DEF-browTL");
        if (rd) { _faceDir.copy(rd).applyAxisAngle(_xAxis, -raiseL); rotateBone(bBL, _faceDir, FACE_ALPHA); }
      }
      const bBR = state.bones.get("DEF-browTR");
      if (bBR) {
        const rd = state.boneRestDir.get("DEF-browTR");
        if (rd) { _faceDir.copy(rd).applyAxisAngle(_xAxis, -raiseR); rotateBone(bBR, _faceDir, FACE_ALPHA); }
      }
    }
  }

  if (lipT && lipB && cL && cR) {
    const mouthW  = Math.abs(cR.x - cL.x);
    if (mouthW > 1e-4) {
      const mouthCY = (lipT.y + lipB.y) * 0.5;
      const raiseL  = THREE.MathUtils.clamp((mouthCY - cL.y) / mouthW * FACE_LIP_SCALE, -FACE_LIP_MAX, FACE_LIP_MAX);
      const raiseR  = THREE.MathUtils.clamp((mouthCY - cR.y) / mouthW * FACE_LIP_SCALE, -FACE_LIP_MAX, FACE_LIP_MAX);

      const bLL = state.bones.get("DEF-lipTL");
      if (bLL) {
        const rd = state.boneRestDir.get("DEF-lipTL");
        if (rd) { _faceDir.copy(rd).applyAxisAngle(_xAxis, -raiseL); rotateBone(bLL, _faceDir, FACE_ALPHA); }
      }
      const bLR = state.bones.get("DEF-lipTR");
      if (bLR) {
        const rd = state.boneRestDir.get("DEF-lipTR");
        if (rd) { _faceDir.copy(rd).applyAxisAngle(_xAxis, -raiseR); rotateBone(bLR, _faceDir, FACE_ALPHA); }
      }
    }
  }
}

function applyFrame(frameData) {
  if (frameData.body) {
    applyArmIK(frameData.body);
  }
  if (frameData.face) {
    applyFace(frameData.face);
  }

  if (!frameData.hands?.length) return;
  const handsMap = {};
  for (const h of frameData.hands) handsMap[h.hand] = h.landmarks;

  const bHL = state.bones.get("DEF-handL");
  const bHR = state.bones.get("DEF-handR");
  if (bHL && handsMap["Left"])  applyHandOrientation(bHL, handsMap["Left"],   1);
  if (bHR && handsMap["Right"]) applyHandOrientation(bHR, handsMap["Right"], -1);

  for (const [boneName, lmStart, lmEnd] of BONE_MAP) {
    const bone = state.bones.get(boneName);
    if (!bone) continue;

    const side      = boneName.endsWith("L") ? "Left" : "Right";
    const rawLms    = handsMap[side];
    if (!rawLms) continue;

    const palmNorm  = side === "Left" ? state.palmNormalL : state.palmNormalR;
    const wrist     = rawLms[0];
    const dir = mpToThree(rawLms[lmEnd], wrist).sub(mpToThree(rawLms[lmStart], wrist));

    dir.addScaledVector(palmNorm, -dir.dot(palmNorm));

    rotateBone(bone, dir);
  }
}

function resetPose() {
  state.bones.forEach((bone, name) => {
    const restQ = state.boneRestLocalQ.get(name);
    if (restQ) bone.quaternion.copy(restQ);
    bone.updateMatrixWorld(true);
  });
}

// ── Loop de reproducción (sin video) ─────────────────────────────────────────
function animTick(now) {
  if (!state.playing) return;
  state.animHandle = requestAnimationFrame(animTick);
  if (state.lastTime === null) { state.lastTime = now; return; }

  state.frameIdx += (now - state.lastTime) / 1000 * state.fps;
  state.lastTime  = now;

  const idx = Math.floor(state.frameIdx);
  if (idx >= state.frames.length) {
    // Fin de la seña: detener en rest pose
    state.playing = false;
    state.frameIdx = 0;
    state.lastTime = null;
    cancelAnimationFrame(state.animHandle);
    resetPose();
    return;
  }
  try {
    applyFrame(state.frames[idx]);
  } catch (e) {
    state.playing = false;
    cancelAnimationFrame(state.animHandle);
    setStatus("Error en frame " + idx);
    console.error(e.stack || e);
  }
}

// Long press (1 solo dedo, casi sin mover) 0.5s → reset pose + cámara al default
canvas.addEventListener("pointerdown", (e) => {
  activePointers++;
  if (activePointers > 1) { cancelPress(); return; } // pinch/zoom: no cuenta
  pressStart = { x: e.clientX, y: e.clientY };
  pressStartTime = performance.now();
  pressTimer = setTimeout(() => {
    pressTimer = null;
    state.playing = false;
    cancelAnimationFrame(state.animHandle);
    resetPose();
    camera.position.set(0, 1.45, 2);
    controls.target.set(0, 1.45, 0);
    controls.update();
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

// API para Android: SignAI.play(), setDebug, setFreeCamera, setGrid, setBackground
window.SignAI = {
  play() {
    if (state.playing || !state.frames.length) return;
    resetPose();
    state.playing = true;
    state.frameIdx = 0;
    state.lastTime = null;
    state.animHandle = requestAnimationFrame(animTick);
  },
  setDebug(on) { Debug.enabled = !!on; Debug.render(); },
  setFreeCamera(on) {
    controls.enableRotate = !!on;
    controls.enablePan = false; // nunca mover el punto de vista, solo orbitar/zoom
    controls.minDistance = on ? 0.2 : 1.4;
    controls.maxDistance = on ? 20 : 3.8;
  },
  setGrid(on) { grid.visible = !!on; },
  setBackground(on) { scene.background = (on && bgTexture) ? bgTexture : BG_COLOR; },
};
