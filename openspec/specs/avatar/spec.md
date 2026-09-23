# Avatar 3D — Especificación técnica del rig y animación

## Origen (prototipo web → WebView)

El avatar y la lógica de animación vienen del prototipo web Flask
(`../app/static/` — referencia, proyecto muerto, no desarrollar ahí):

- `../app/static/avatar.glb` → copiado a `app/src/main/assets/web/avatar.glb`
- `../app/static/app.js` (634 líneas: video + selector de señas vía `/api/*`)
  → portado a `app/src/main/assets/web/app.js` (581 líneas)

Diferencias del port (WebView): sin `<video>`, sin selector de señas, sin
fetch a `/api/*`. Los frames vienen de `sign.json` local (fetch relativo) y
la reproducción la dispara Kotlin vía `window.SignAI.play()`.

## Archivos en este repo

- GLB: `app/src/main/assets/web/avatar.glb` (~1.4 MB)
- Escena: `app/src/main/assets/web/{index.html, app.js, sign.json, bosque.jpg}`
  (mismo directorio, paths relativos — no separar)

## Estado actual del GLB

| Aspecto            | Estado                                                      |
|--------------------|-------------------------------------------------------------|
| Meshes             | female_v006lowresUV (cuerpo), male_v008Eyes ×2 (globos)    |
| Vértices           | 12010 cuerpo + 2×2176 ojos = 16362 total                    |
| Material/Textura   | **NINGUNO** — se ve como maniquí gris                        |
| Skins              | rig (918 joints DEF-*) + metarig (174 joints)               |
| Morph targets      | Ninguno exportado                                           |
| Rig tipo           | Rigify completo (vertex weights asignados automáticamente)  |

**Prioridad**: Reemplazar mesh por uno con textura de piel. Recomendación: mantener el rig
Rigify actual y solo cambiar el mesh visual (opción más segura y rápida).

Al cargar, `app.js` oculta `WGT-*` y `metarig` con `obj.visible = false`
(nunca `removeFromParent()`).

## Nombres de huesos — regla de oro (verificado en `app.js` + GLB)

**NINGÚN hueso usa puntos.** Dedos sin prefijo `DEF-`, cara en camelCase sin
separadores. Lo que usa `app.js` es lo correcto:

```
thumb01L, f_index01L, f_middle01L, f_ring01L, f_pinky01L   (+ lados R)
DEF-upper_armL, DEF-upper_armL001, DEF-forearmL, DEF-forearmL001, DEF-handL
DEF-browTL / DEF-browTR        ← cejas superiores
DEF-lipTL  / DEF-lipTR         ← labio superior
DEF-lidTL  / DEF-lidTR         ← párpado superior (etc. con L/R/001…)
DEF-jaw_master                 ← mandíbula (con guión bajo)
DEF-eyeL / DEF-eyeR, DEF-eye_irisL / DEF-eye_irisR
```

⚠️ El spec anterior (pre-WebView) documentaba nombres con puntos
(`DEF-brow.T.L`) — **incorrectos, no existen en el GLB**. No reintroducirlos.

## Bugs conocidos

### Bug 1 — Nombres con puntos (spec viejo, NO es bug del código)
El spec antiguo llamaba "bug" a los nombres sin puntos de `app.js`. Al revés:
los correctos son sin puntos (`DEF-browTL`), verificados en ambos `app.js`
(prototipo y WebView) y en el GLB.

### Bug 2 — Ceja derecha casi estática (vigente, rig)
- `DEF-browTL`: 32 vértices pesados (correcto)
- `DEF-browTR`: solo ~4 vértices (pesos bajos ~0.12) → movimiento casi invisible
- Fix: Weight Paint en Blender sobre el `.blend` original + re-exportar el GLB

### Face/body sync — resuelto en tiempo de horneado
Face venía a 50fps y body/hands a 24fps (ratio ~2.9×). En WebView no hay
remuestreo en runtime: `sign.json` ya trae la cara sincronizada a 24fps
(59 frames). Si se re-hornea `sign.json`, aplicar
`face_idx = round(i * len(face_frames) / len(body_frames))` al generarlo.

## Estado de animación por parte del cuerpo

| Parte               | Estado      | Implementación                        |
|---------------------|-------------|---------------------------------------|
| Dedos (30 huesos)   | ✅ Funciona  | BONE_MAP + rotateBone()               |
| Muñeca roll (palma) | ✅ Funciona  | applyHandOrientation()                |
| Muñeca flex/ext     | ⚠️ Parcial   | Solo vía IK, no hueso independiente   |
| Codo                | ✅ Funciona  | IK 2-huesos (solveIKElbow)            |
| Hombro              | ✅ Funciona  | IK 2-huesos                           |
| Mandíbula (boca)    | ✅ Funciona  | DEF-jaw_master, FACE_ALPHA=1.0        |
| Cejas               | ✅ Nombres OK| DEF-browTL/TR (pendiente Bug 2: pesos)|
| Párpados            | ❌ No impl.  | Huesos existen, datos disponibles     |
| Labios (comisuras)  | ❌ No impl.  | DEF-lipTL/TR existen, datos listos   |
| Cabeza (inclinación)| ❌ No impl.  | Pendiente (lm1/168 de FaceMesh)       |

## Expresiones faciales prioritarias

| Expresión  | Huesos principales              | Landmark fuente       |
|-----------|----------------------------------|------------------------|
| Duda       | DEF-browTL (asimétrico)          | lm70/107/55, lm300/336 |
| Felicidad  | DEF-browTL/TR + DEF-lipTL/TR     | lm70/300 + lm78/308    |
| Pregunta   | DEF-browTL/TR + DEF-jaw_master   | lm70/300 + lm13/14     |

Todos los huesos faciales deben usar `FACE_ALPHA = 1.0` (sin slerp).
`sign.json` actual trae 16 landmarks de cara:
`1, 13, 14, 55, 70, 78, 107, 145, 159, 168, 285, 300, 308, 336, 374, 386`.

## Retargeting — fórmula correcta

```js
// rotateBone: alinea el eje Y del hueso a targetDir
// 1. deltaQ: rotación mínima desde restDir hasta targetDir
_dQ.setFromUnitVectors(restDir, targetDir.normalized());
// 2. targetWorldQ: aplicar delta sobre rest world quaternion
_tWQ.multiplyQuaternions(_dQ, restWorldQ);
// 3. localQ: convertir de world a espacio local del padre
bone.parent.getWorldQuaternion(_pWQ);
_tLQ.multiplyQuaternions(_pWQ.invert(), _tWQ);
bone.quaternion.slerp(_tLQ, alpha);
```

NUNCA hacer `quaternion.identity()` para resetear — rompe el avatar. Usar `boneRestLocalQ`.

## Conversión de ejes landmark → Three.js

```js
// Body landmarks (imagen normalizada, y=0 arriba):
lmDir(a, b) = Vector3(b.x-a.x, -(b.y-a.y), -(b.z-a.z))

// Hand landmarks, relativo a muñeca (port WebView: z = 0):
mpToThree(lm, wrist) = Vector3(lm.x-wrist.x, -(lm.y-wrist.y), 0)
```

## Escala de coordenadas de cuerpo

- `bodyScale()`: usa separación entre hombros (lm11-lm12) como ancla
- `Z_SCALE = 0.40`: factor empírico para profundidad del brazo (eje Z de imagen normalizada)
- Polo del IK: `pole.y -= (L1+L2)*0.35` (bias hacia abajo) + `pole.z += (L1+L2)*0.10` (frontal)
