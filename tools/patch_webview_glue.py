#!/usr/bin/env python3
"""
Aplica al `app.js` del WebView los cambios que el splice NO puede hacer.

El movimiento viene entero del prototipo (`tools/splice_movement.py`), pero
hay cuatro cosas que son del WebView y el splice no puede saber:

  1. `state` gana los campos de la bisagra de dedos (`boneFlexAxis`,
     `fingerAlpha`, los dos deadzones) y pierde `palmNormal{L,R}`, que la
     versión nueva de `applyHandOrientation` ya no publica.
  2. La carga del GLB registra huesos con el fallback por
     `skeleton.bones` (en AutoRigPro `isBone` puede ser false) y llama a
     `measureFingerHinges()`.
  3. La seña por defecto sale de `signs/`, no del `sign.json` único.
  4. Se borra el `applyFace`/`applyMnm`: este rig no tiene huesos de cara.

Es idempotente: si algo ya está aplicado, no lo vuelve a aplicar. Corre
primero y después el splice; no forma parte del build.

    python3 tools/patch_webview_glue.py
"""

from __future__ import annotations

import pathlib

TARGET = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/web/app.js"

EDITS: list[tuple[str, str, str]] = [
    (
        "state: boneFlexAxis",
        """  boneRestDir:   new Map(),
  frames: [],""",
        """  boneRestDir:   new Map(),
  boneFlexAxis:  new Map(),   // nombre → eje bisagra en frame local (falanges)
  frames: [],""",
    ),
    (
        "state: alphas de dedo, sin palmNormal",
        """  loading: false,
  smoothAlpha: 0.12,
  palmNormalL: new THREE.Vector3(0, 0, 1),
  palmNormalR: new THREE.Vector3(0, 0, 1),
  armRest: {""",
        """  loading: false,
  // Fracción [0,1] que avanza hacia el quaternion objetivo cada tick de rAF.
  smoothAlpha: 0.12,
  // Suavizado de las falanges. La bisagra de 1 eje + clamp es mucho más estable
  // que el retargeting libre, así que admite un alpha más alto sin temblor.
  fingerAlpha: 0.18,
  // Deadzone adaptativo: se ignora la rotación de una falange cuando su vector
  // de segmento es más corto que fingerDeadzone × (largo de palma wrist→MCP).
  fingerDeadzone: 0.12,
  // En CONTACT el corrector ya congeló la pose (cero jitter por construcción),
  // así que un segmento corto es un dedo realmente flexionado, no ruido: el
  // piso baja para que un puño llegue a cerrar.
  fingerDeadzoneContact: 0.02,
  armRest: {""",
    ),
    (
        "seña por defecto desde signs/",
        """// bloquea. Por eso las descargas las hace Kotlin, no esta escena.
const signCache = new Map();""",
        """// bloquea. Por eso las descargas las hace Kotlin, no esta escena.

// Seña con la que abre la app, de las horneadas en assets/web/signs/. Android
// puede cambiarla con SignAI.setDefaultSign() desde el menú de ajustes.
const DEFAULT_SIGN_URL = "signs/0000_0000_0000.json";
let defaultSignUrl = DEFAULT_SIGN_URL;

const signCache = new Map();""",
    ),
    (
        "registro de huesos + measureFingerHinges",
        """  gltf.scene.traverse(obj => {
    if (!obj.isBone) return;
    state.bones.set(obj.name, obj);
    state.boneRestLocalQ.set(obj.name, obj.quaternion.clone());
    const wq = new THREE.Quaternion();
    obj.getWorldQuaternion(wq);
    state.boneRestWorldQ.set(obj.name, wq.clone());
    const dir = new THREE.Vector3(0, 1, 0).applyQuaternion(wq).normalize();
    state.boneRestDir.set(obj.name, dir);
  });

  measureArmRest();""",
        """  function registerBone(obj) {
    if (state.bones.has(obj.name)) return;
    state.bones.set(obj.name, obj);
    state.boneRestLocalQ.set(obj.name, obj.quaternion.clone());
    const wq = new THREE.Quaternion();
    obj.getWorldQuaternion(wq);
    state.boneRestWorldQ.set(obj.name, wq.clone());
    const dir = new THREE.Vector3(0, 1, 0).applyQuaternion(wq).normalize();
    state.boneRestDir.set(obj.name, dir);
  }

  // Método 1: traverse estándar. Método 2 (abajo): fallback por skeleton.bones,
  // necesario en rigs donde `isBone` puede ser false (AutoRigPro entre otros).
  gltf.scene.traverse(obj => {
    if (obj.isBone) registerBone(obj);
  });
  gltf.scene.traverse(obj => {
    if (obj.isSkinnedMesh && obj.skeleton) {
      for (const bone of obj.skeleton.bones) registerBone(bone);
    }
  });
  console.log("[SignAI] Huesos encontrados:", state.bones.size);

  // IK de brazos y bisagra de dedos miden el rig al cargar: ambos necesitan
  // sus datos antes del primer frame.
  measureArmRest();
  measureFingerHinges();""",
    ),
    (
        "cargar la seña por defecto",
        """  loadSignData("sign.json").then(sign => {""",
        """  loadSignData(defaultSignUrl).then(sign => {""",
    ),
    (
        "mensaje de error de la seña por defecto",
        '''    setStatus("Error cargando sign.json");''',
        '''    setStatus("Error cargando la seña por defecto");''',
    ),
]


def main() -> None:
    text = TARGET.read_text()
    for name, old, new in EDITS:
        if new in text:
            print(f"  ya aplicado: {name}")
            continue
        if old not in text:
            raise SystemExit(f"No encontré el texto de «{name}». "
                             f"¿El app.js cambió? Revisar EDITS.")
        text = text.replace(old, new, 1)
        print(f"  aplicado: {name}")
    TARGET.write_text(text)
    print(f"listo: {TARGET}")


if __name__ == "__main__":
    main()