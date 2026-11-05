# Avatar 3D — Especificación técnica del rig y la animación

## Qué es hoy

Un `THREE.AnimationMixer` sobre un export de Cascadeur. No hay retargeting, ni
IK, ni landmarks, ni interpretación de nada: los `.glb` traen la pose ya
horneada por hueso y la escena solo la reproduce.

| | |
|---|---|
| Personaje | `models/juan.glb` (34,28 MB) **o** `models/pauline.glb` (29,46 MB), 102 articulaciones de skin cada uno; el que se carga lo elige el ajuste «Personaje» |
| Señas | **200 clips adentro de cada archivo**: las 50 señas (`0000`..`0049`) por cuatro marcas faciales (`_afirmativo`, `_pregunta`, `_negacion`, `_orden`); no hay clip base |
| Escena | `app.js` + `index.html`, three r169 por CDN |
| Catálogo | `domain/SignsCatalog.kt`, generado |

**Cambio del 2026-10-26**: hay **dos archivos completos, uno por avatar**. El
`models/avatar.glb` de 12,98 MB se borró; `tools/pack_avatars.py` arma los dos
desde `../signai-avatar/models/`, el repo hermano que versiona los GLB finales en su rama
`dev`. La escena indexa solo el avatar elegido y resuelve `signs/<id>.glb` por nombre
contra sus clips (`embeddedClipsFor` en `app.js`).

**Cambio del 2026-10-27**: se regeneraron con la versión del 2026-10-26 de esos GLB, que
trae la **cadera centrada** en las 50 señas. Mismos 300 clips, mismos nombres y mismas
duraciones; solo cambian `spine` (traslación y giro) y `neck`/`head`.

**Cambio del 2026-11-04**: Samuel reemplazó los GLB por una versión con **cuatro marcas
faciales gramaticales** —`_afirmativo`, `_pregunta`, `_negacion`, `_orden`— y sin clip
base. Cada archivo pasa a tener **200 clips** (50 señas × 4), los mismos 102 huesos y los
mismos canales de hueso entre variantes; solo cambian los pesos de la cara. `app.js` ya
reproduce las cuatro por tipo de oración.

**Cambio del 2026-10-22**: el asset pasó de `models/avatar.glb` + `signs/<id>.glb` a
**un solo archivo** con las 50 señas adentro. `tools/pack_signall.py` lo armaba desde
`../material-3d/GLB SignALL/`. Los `signs/*.glb` que quedan en el repo no se piden en runtime: la
escena resuelve `signs/<id>.glb` por nombre contra los clips del avatar
(`embeddedClipsFor` en `app.js`). Por qué el cambio y con qué números, en
`openspec/sessions/2026-10-22-signall-un-glb-50-senas.md`.

Esto reemplazó (2026-10-13) al motor procedural que venía del prototipo web:
landmarks de MediaPipe, `BONE_MAP`, `solveIKElbow`, `applyHandOrientation` y
`sign.json`. **Nada de eso existe ya.** Si un documento o una sesión lo
menciona, es historia: está en `openspec/sessions/2026-10-13-glb-animation-mixer.md`.

## Dos archivos, uno por avatar

Cada export de Cascadeur trae el personaje entero adentro, y cada uno de los dos
avatares es un archivo cerrado. El primer pipeline (`tools/split_signs.py`,
2026-10-13) separaba eso en `models/avatar.glb` + `signs/<id>.glb` para no
versionar 32 copias del muñeco. Desde el 2026-10-22 el archivo único trae **el
personaje + las 50 animaciones**, y desde el 2026-10-26 son **dos** archivos con
**200 clips cada uno**: el mixer resuelve los tracks por nombre de nodo contra el
root, y cargar el clip desde el mismo `gltf.animations` que el avatar es lo mismo
que cargarlo desde un `.glb` aparte, sin pedido de red.

Las **cuatro variantes faciales** existen en los dos archivos y desde el **2026-11-04 se
reproducen por tipo de oración** (`FACE_VARIANT_BY_TYPE` en `app.js`): afirmación usa
`_afirmativo`, pregunta sí/no y pregunta de contenido usan `_pregunta`, negación usa
`_negacion` y orden usa `_orden`. No hay clip base: todas las frases llevan una de las
cuatro caras. Las variantes se devuelven **separadas** de las tomas en `embeddedClipsFor`
(`clips` vs. `variants`), así que el sorteo de tomas nunca elige una cara. Los morph
targets que alguna de las 200 variantes anima se conservan en el archivo (59 bloques en
Juan y 58 en Pauline después del empaquetado del 2026-11-05).

Cambiar de avatar **no requiere regenerar nada**: los dos archivos están en el APK
y la escena cambia de uno al otro en caliente. Regenerarlos sí: los escribe
`tools/pack_avatars.py`.

## El cambio de avatar en caliente

- **Quién decide**: el ajuste «Personaje» de Ajustes, que persiste en
  `SharedPreferences`. Los valores son `juan` y `pauline`; Juan es el
  predeterminado y cualquier valor viejo (`Julián`, `Samuel`, `Ximena`) se
  normaliza a Juan al leerlo, porque esas opciones nunca cambiaron el modelo.
- **Solo en reposo.** El selector se habilita en `Inactivo`, `Desconectado` y
  `Error`, y se niega con captura, frase lista o enviada, traducción, cola o
  repetición en curso. Mientras el avatar carga, la barra de abajo y el botón de
  reproducir quedan bloqueados.
- **Cómo viaja**: el avatar inicial va en la URL de la página (`?avatar=<id>`) y
  los cambios usan `window.SignAI.setAvatar(id)`, método aditivo declarado en
  `domain/SignAiCommands.kt`. La escena responde `avatarLoaded:<id>` o
  `avatarFailed:<id>`; el splash espera ese aviso, no `onPageFinished`.
- **El orden importa**: el modelo nuevo se parsea **antes** de liberar el
  anterior, así un cambio que falla deja el avatar viejo en pantalla. Al liberar
  se llama `dispose()` sobre geometrías, materiales y texturas.
- **Costo medido** (Redmi Note 9S, 2026-10-26): un cambio en caliente tarda
  1,90–2,57 s, de los cuales 1,18–1,95 s son el parseo del GLB. El arranque de la
  app tarda 1,81–2,50 s con caché de página caliente y 3,42 s en frío.

## La pose de descanso está horneada en los dos `.glb`

El reposo **no** es una T-pose ni un default de three.js: es el TRS de los nodos
del `.glb`, que es la pose que Cascadeur dejó en el export — brazos a 35° bajo
la horizontal, manos a 52 cm del eje, de maniquí.

La que hay ahora sale de la seña `0029` (JUICIOSO), primer keyframe
(`t = 0.0417 s`) —en el layout viejo `signs/0029_0000_0000.glb`, variante 0; en
los archivos vigentes, el clip `0029` del propio avatar—, y son las rotaciones de **46
huesos**: la
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

`tools/pack_avatars.py` es el pipeline vigente: reusa las funciones de
`tools/rest_pose.py` y saca el frame de referencia del **primer keyframe del clip
`0029` del propio archivo** que está armando, así que no necesita un
`signs/0029_0000_0000.glb` en disco. Verifica 1 skin, 102 articulaciones y los 300
nombres de clip antes de escribir, y frena si la pose de bind no cierra.
`tools/split_signs.py` es el layout viejo —separaba `signs/` y después horneaba la
pose desde ahí— y sigue en el repo porque el pipeline le importa `write_catalog`.
**`models/juan.glb` y `models/pauline.glb` no se editan a mano**: se regeneran, y
la pose viene sola.

```bash
python3 tools/pack_avatars.py --dry-run   # informe de los dos, sin escribir
python3 tools/pack_avatars.py             # los dos, desde ../signai-avatar/models
```

`tools/pack_signall.py` es el pipeline viejo de un solo avatar: no procesa estos dos
archivos.

Ojo: `python3 tools/rest_pose.py --dry-run` y `python3 tools/preview_pose.py --sheet`
**ya no corren solos** desde el 2026-10-22: los dos esperan el layout viejo
(`assets/web/signs/`), que se borró. El informe de la pose lo da el `--dry-run` de
`pack_avatars.py`.

`preview_pose.py` renderiza una pose sin three.js ni instalar la app (skinning
lineal con las IBM del export, el mismo `pivot` y la misma cámara que `app.js`,
que lee del propio archivo). Necesita `numpy` y `PIL`; el pipeline de assets no.

## Nombres de huesos

**Los export de Cascadeur usan puntos en la cadena del brazo, en los dedos y en la
palma, y no usan ningún prefijo en los huesos de la cara.** Los nombres que existen
(verificados contra el `.glb`):

Verificado el 2026-10-17 contra el `avatar.glb` de entonces **y** contra el export original de
Cascadeur (`../material-3d/GLB Senas/0000 - GRACIAS.glb`), y reverificado el 2026-10-24
y el 2026-10-26 contra los dos archivos vigentes (`models/juan.glb` y
`models/pauline.glb`). **102 articulaciones de skin en todos ellos.**

```
shoulder.L, upper_arm.L, forearm.L, hand.L        (+ lados R)
palm.01.L … palm.04.L   f_index.01.L … f_pinky.03.L   thumb.01.L … thumb.03.L
HG_Branden                     ← raíz del armature (NO es un joint, y no está animada)
```

`HG_Branden.001` era el nombre de la raíz en el export viejo (`../material-3d/GLB
Senas/`); en el archivo vigente la raíz es `HG_Branden` y **ningún canal de los 50
clips la toca**.

**No existe ningún hueso `DEF-` en este rig.** Verificado: cero coincidencias, tanto en el
GLB que va en el APK como en el export original. Cualquier documento que liste
`DEF-upper_armL`, `DEF-browTL`, `DEF-lidTL`, `DEF-lipTL` o `DEF-jaw_master` como huesos
**está equivocado** — ver la corrección del 2026-10-17 más abajo.

### La cara no la mueve la app

Los nodos de cara que existen en el archivo usan nombres al estilo ARKit y **sí son
articulaciones de skin**: los 31 están entre los 102 joints.

```
brow_dwn_L / brow_dwn_R        brow_outer_up_L / brow_outer_up_R   brow_inner_up
eye_blink_open_L / eye_blink_open_R    eye_squint_L / eye_squint_R
eyeball.L, eyeball.lookat.L, eyeball_lookat_master
jaw, jaw_upper, jaw_dwn_mouth_clsd, jaw_open_lt_rt_frwd
mouth_dimple_*, mouth_lower_down_*, mouth_smile_frown_*, mouth_stretch_*,
mouth_upper_up_*, mouth_lt_rt_funnel, mouth_roll_lower, mouth_shrug_roll_upper
```

Desde el **2026-11-04** la app también elige una **variante facial** según el tipo de
oración que manda el backend (`FACE_VARIANT_BY_TYPE` y `pickVariant(clips, variants,
type)`): afirmación → `_afirmativo`, pregunta sí/no y pregunta de contenido → `_pregunta`,
negación → `_negacion`, orden → `_orden`. El export del 2026-11-04 trae exactamente esas
cuatro marcas, así que no quedan filas abiertas. La medición de las variantes viejas
(`_alegria`, `_tristeza`, `_sorpresa`) está en `docs/mediciones/marcadores-2026-11-04.md`
como historia: esas caras ya no existen en el asset. En las cuatro actuales los huesos son
idénticos entre sí y solo cambian los morph targets. Los dos archivos vigentes conservan
los morph targets que alguna de las 200 variantes anima —59 bloques en Juan, 58 en
Pauline, contados el 2026-11-05—.

Las mediciones de «cara quieta» que siguen son del archivo único anterior, tomadas
sobre sus 50 clips (`avatar.glb`, borrado el 2026-10-26): el mayor rango de un
componente del cuaternión de rotación de un hueso de cara era **0,0012** (`jaw`, clip
`0015`), contra **1,996** del mayor hueso del cuerpo (`thigh.L`, clip `0034`), y sus
5 canales de pesos por clip valían 1,0 constante. **No se remidió sobre los dos
archivos nuevos**: sirve como referencia de los clips base, no como afirmación sobre
las variantes. Es el punto **A6** de `docs/CONTRASTE-APPvsDOCUMENTOS.md`: el SRS pide
marcadores por tipo de frase y desde el 2026-11-04 hay una primera implementación con
tres filas medidas y dos pendientes de validación lingüística.

Dos detalles que no son obvios y ya costaron tiempo:

- **La raíz del armature no se anima, y no es un joint.** `app.js` cuenta nodos
  resueltos y no huesos porque los canales de pesos de cada clip apuntan a mallas
  (por ejemplo `HG_Body_facial`), que no son huesos del skeleton: contar solo huesos
  marcaría esos tracks como "sin resolver" en cada clip.
- **La normalización va en un `pivot` padre, nunca sobre la raíz.** El mixer
  sobrescribe la raíz frame a frame: la escala se perdería en el primer frame y
  el muñeco aparecería descolocado y sin escalar, sin ningún error.

## Animación entre señas

Los clips base **arrancan y terminan en el mismo cluster** (medido el 2026-10-26
sobre los 50 clips del archivo único anterior, hoy borrado: mano izquierda en
`(+31,5, +91,0,
+17,6) cm`, desviación estándar 3,2–4,3 cm; derecha en `(−12,1, +91,8, +26,3) cm`,
2,4–6,0 cm): el salto directo del final de una seña al inicio de cualquier otra
—distancia de `hand.L`, sobre los 2450 pares ordenados— es de **5,6 cm de mediana**
y el **81 % de los pares queda por debajo de 10 cm**. Los exports de Cascadeur están
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
> **Lo que sí es cierto:** la app no anima la cara de ninguna manera, así que la
> asimetría no puede venir del runtime. Y los clips actuales dejan los huesos de cara
> **casi quietos** (rango máximo de 0,0012 en un componente del cuaternión, medido el
> 2026-10-26 sobre los 50 clips), así que ni siquiera viene de una diferencia entre
> clips: hay que **medirla en el export**, con los nombres que sí existen. Para
> corregirla hay que tocar el `.blend` de origen en Blender y re-exportar; no se
> arregla en `app.js`.
>
> Para volver a ocuparse de esto hace falta medir los pesos reales en el export, con los
> nombres que sí existen en el archivo.

### La normalización sobre la raíz (arriba)

Va en el `pivot` padre. Si alguna vez aparece el muñeco descolocado y sin
escalar después de un cambio en `app.js`, es esto.

## Lo que NO hace la escena

- No hay IK, ni cinemática inversa, ni reintentos de pose: los `.glb` traen la
  pose.
- Hay una sola cara por tipo de oración, no una por seña: la variante elegida es la
  misma en las 50 señas (medido el 2026-11-04). No hay emociones: el backend no
  manda esa señal y no se clasifican.
- No hay movimiento de cabeza en las variantes: los huesos son idénticos entre las cuatro.
  La negación solo cambia la cara.
- La escena **sí maneja los morph targets** desde el 2026-11-04, pero solo a través
  del clip de la variante elegida: no los escribe a mano ni los mezcla entre
  variantes.
- El peso por vértice lo pone Cascadeur, no la app.
