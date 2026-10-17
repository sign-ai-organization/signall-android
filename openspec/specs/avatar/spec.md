# Avatar 3D — Especificación técnica del rig y la animación

## Qué es hoy

Un `THREE.AnimationMixer` sobre un export de Cascadeur. No hay retargeting, ni
IK, ni landmarks, ni interpretación de nada: los `.glb` traen la pose ya
horneada por hueso y la escena solo la reproduce.

| | |
|---|---|
| Personaje | `models/avatar.glb` (~10.3 MB), 102 huesos, 11 mallas, texturas PBR |
| Señas | `signs/<id>.glb`, un archivo por glosa, **solo animación** (~180 KB) |
| Escena | `app.js` + `index.html`, three r169 por CDN |
| Catálogo | `domain/SignsCatalog.kt`, generado |

Esto reemplazó (2026-10-13) al motor procedural que venía del prototipo web:
landmarks de MediaPipe, `BONE_MAP`, `solveIKElbow`, `applyHandOrientation` y
`sign.json`. **Nada de eso existe ya.** Si un documento o una sesión lo
menciona, es historia: está en `openspec/sessions/2026-10-13-glb-animation-mixer.md`.

## Los dos archivos y por qué están separados

Cada export de Cascadeur trae el personaje entero adentro, así que sin separar
el muñeco estaría duplicado 32 veces (329.7 MB). `tools/split_signs.py` lo
escribe una vez en `models/` y deja en `signs/` solo la animación, que es todo lo
que el mixer necesita: **resuelve los tracks por nombre de nodo contra el root
que se le pase**, no contra los nodos del propio archivo del clip.

Cambiar el personaje no requiere re-exportar las señas, y cambiar una seña no
requiere tocar el personaje.

## La pose de descanso está horneada en `avatar.glb`

El reposo **no** es una T-pose ni un default de three.js: es el TRS de los nodos
del `.glb`, que es la pose que Cascadeur dejó en el export — brazos a 35° bajo
la horizontal, manos a 52 cm del eje, de maniquí.

La que hay ahora sale de `signs/0029_0000_0000.glb` (JUICIOSO), variante 0,
primer keyframe (`t = 0.0417 s`), y son las rotaciones de **46 huesos**: la
cadena de brazos entera de los dos lados (`shoulder`, `upper_arm`, `forearm`,
`hand`, `palm.*`, `f_*`, `thumb*`). Ni la cara, ni el torso, ni las piernas.

Por qué ese frame y no otro:

- de los 2424 keyframes de las 32 señas, **851 tienen los dos brazos colgando**;
- es una pose por la que el personaje **ya pasó**, así que el skinning, la ropa
  y el pelo son válidos por construcción — no hay que confiar en que un giro de
  hueso a ojo no rompa la malla;
- es simétrico a 1.2 cm y **no toca ningún hueso de expresión**: la cara queda
  neutra;
- es idempotente y repetible: el mismo archivo da el mismo resultado.

Descartados con evidencia, no reabrir: el promedio de los 46 inicios de clip
(asimétrico, cabeza girada) y su versión espejada (**rompe la malla**; en el
rig la rotación local de `.R` es el espejo de la de `.L` solo en la pose de
bind, y promediar una pose de animación con su espejo cae fuera del rango para
el que la malla fue tejida — se ve en
[`img/`](../changes/archive/2026-10-17-reposo-y-crossfade/img/)).

### Por qué re-posa y no deforma

El TRS de los nodos del avatar **es** la pose de bind: se verifica en cada
corrida del pipeline con `glb_pose.bind_pose_error`, y da ~6e-6 sobre los 102
huesos (`max |invers(IBM[k]) − mundo_del_hueso(k)|`). Cambiar esas rotaciones
deja intactas las `inverseBindMatrices` y los accessors `POSITION`, así que:

- el skinning sigue siendo válido (misma pose de bind, distinta pose de reposo);
- el `bbox` que reporta `split_signs.py` no cambia;
- el `pivot` que calcula `app.js` no cambia, así que el encuadre tampoco;
- el BIN chunk del `.glb` queda byte a byte igual: solo cambian 46 números del
  JSON.

Si algún día un export nuevo no cumple la identidad, `rest_pose.check_bind()`
frena la corrida: deformar la malla no da ningún error en pantalla.

### Cómo se regenera

`tools/rest_pose.py`, llamado **al final** de `tools/split_signs.py` (después de
escribir `signs/`, porque el frame de referencia sale de ahí). **`avatar.glb` no
se edita a mano**: se regenera, y la pose viene sola.

```bash
python3 tools/rest_pose.py --dry-run   # solo el informe: manos antes/después
python3 tools/preview_pose.py --sheet  # las 4 poses de la comparación
```

`preview_pose.py` renderiza una pose sin three.js ni instalar la app (skinning
lineal con las IBM del export, el mismo `pivot` y la misma cámara que `app.js`,
que lee del propio archivo). Necesita `numpy` y `PIL`; el pipeline de assets no.

## Nombres de huesos

**Los export de Cascadeur usan puntos en los dedos y en la palma, y `DEF-` en
los huesos largos y de la cara.** Los nombres que existen (verificados contra el
`.glb`):

Verificado el 2026-10-17 contra `models/avatar.glb` **y** contra el export original de
Cascadeur (`GLB Senas/0000 - GRACIAS.glb`). **102 articulaciones de skin en los dos
archivos.**

```
shoulder.L, upper_arm.L, forearm.L, hand.L        (+ lados R)
palm.01.L … palm.04.L   f_index.01.L … f_pinky.03.L   thumb.01.L … thumb.03.L
HG_Branden.001                 ← raíz del armature (NO es un hueso)
```

**No existe ningún hueso `DEF-` en este rig.** Verificado: cero coincidencias, tanto en el
GLB que va en el APK como en el export original. Cualquier documento que liste
`DEF-upper_armL`, `DEF-browTL`, `DEF-lidTL`, `DEF-lipTL` o `DEF-jaw_master` como huesos
**está equivocado** — ver la corrección del 2026-10-17 más abajo.

### La cara no la mueve la app

Los nodos de cara que sí existen en el archivo usan nombres al estilo ARKit y **no son
articulaciones de skin**:

```
brow_dwn_L / brow_dwn_R        brow_outer_up_L / brow_outer_up_R   brow_inner_up
eye_blink_open_L / eye_blink_open_R    eye_squint_L / eye_squint_R
eyeball.L, eyeball.lookat.L, eyeball_lookat_master
jaw, jaw_upper, jaw_dwn_mouth_clsd, jaw_open_lt_rt_frwd
```

Y **no están driven por nada**: el GLB que va en el APK tiene **cero morph targets**, y `app.js`
no menciona morph targets, blendshapes ni ninguno de esos nombres. La expresión facial no
se anima en código — la cara viene horneada en el clip. Es el punto **A6** de
`docs/CONTRASTE-APPvsDOCUMENTOS.md`: el SRS pide marcadores por tipo de frase y no hay
implementación. Antes estaba en pausa; el SRS de Overleaf ahora sí lo pide, así que la
decisión del owner está pendiente (2026-10-17).

Dos detalles que no son obvios y ya costaron tiempo:

- **Los tracks también animan la raíz del armature** (`HG_Branden.001`, en
  posición, rotación y escala). Por eso `app.js` cuenta nodos resueltos y no
  huesos: marcar 138 tracks "sin resolver" sería un falso positivo.
- **La normalización va en un `pivot` padre, nunca sobre la raíz.** El mixer
  sobrescribe la raíz frame a frame: la escala se perdería en el primer frame y
  el muñeco aparecería descolocado y sin escalar, sin ningún error.

## Animación entre señas

Los 46 clips de animación **arrancan y terminan en el mismo cluster** (mano
izquierda en `(+30, +91, +19) cm`, desviación estándar 2.5–6 cm): el salto
directo del final de una seña al inicio de la siguiente es de **5.9 cm de
mediana** y el 79% de los pares ya es continuo. Los exports de Cascadeur están
escritos para encadenarse.

Por eso `app.js` **no** pone nada entre las señas: el clip siguiente entra 100 ms
antes de que termine el actual y las dos `AnimationAction` viven juntas sobre el
mismo esqueleto mientras three.js las mezcla (`crossFadeTo` / `fadeIn` /
`fadeOut`). Lo que hay que saber para no romperlo:

- **`fadeOut` vuelve al reposo sin escribir un keyframe.** Cuando el peso de una
  action llega a 0, `PropertyMixer.apply()` completa con el valor guardado
  cuando se creó el binding, que es el TRS del nodo — la pose horneada.
- **`_scheduleFading` usa `mixer.time` como reloj**, y `app.js` avanza el mixer
  con `dt * speed`: la fundición sigue sola al factor de adelanto automático.
- **Una fundición de duración 0 no es "sin fundición"**: el interpolant de peso
  arranca y termina en el mismo instante y three.js **deshabilita la action**.
  Con `reduceMotion` el 0 se maneja como un caso aparte, sin llamar a
  `fadeIn`/`fadeOut`.
- **La misma seña dos veces seguidas no se puede fundir**: three.js cachea una
  action por clip, así que es la misma y no hay dos que mezclar. Es corte seco,
  y está bien — los clips son continuos consigo mismos.

Detalle, duración y la cuenta de por qué 100 ms: `openspec/changes/archive/2026-10-17-reposo-y-crossfade/investigacion.md`.

## Bugs conocidos

### Ceja derecha casi estática — **sin confirmar, y probablemente mal described**

> **Corregido el 2026-10-17.** Este bug se describía con `DEF-browTL` y `DEF-browTR`, que
> **no son huesos de este rig** (ver § Nombres de huesos). El síntoma se había visto en
> pantalla, pero la causa atribuida a los pesos no se pudo verificar contra el archivo.
>
> **Lo que sí es cierto:** la app no anima la cara de ninguna manera, así que cualquier
> asimetría de cejas viene **horneada en el clip**, no del runtime. Para corregirla hay que
> tocar el `.blend` de origen en Blender y re-exportar; no se arregla en `app.js`.
>
> Para volver a ocuparse de esto hace falta medir los pesos reales en el export, con los
> nombres que sí existen en el archivo.

### La normalización sobre la raíz (arriba)

Va en el `pivot` padre. Si alguna vez aparece el muñeco descolocado y sin
escalar después de un cambio en `app.js`, es esto.

## Lo que NO hace la escena

- No hay IK, ni cinemática inversa, ni reintentos de pose: los `.glb` traen la
  pose.
- No hay sincronización de cara: viene horneada en el mismo clip.
- No hay morph targets ni blendshapes.
- El peso por vértice lo pone Cascadeur, no la app.
