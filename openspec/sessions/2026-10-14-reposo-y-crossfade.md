# Pose de descanso + crossfade — 2026-10-14

Trabajo de la sesión: dejar al muñeco **con los brazos abajo en reposo** y que
**las señas se encadenen sin saltos**. El owner lo formuló así: "una posición
como base, pero que se vea normal, como un ser humano en descanso", y un
crossfade entre animaciones.

Plan completo en [`../changes/reposo-y-crossfade/tasks.md`](../changes/reposo-y-crossfade/tasks.md);
medidas y metodología en
[`investigacion.md`](../changes/reposo-y-crossfade/investigacion.md).

**Estado: implementado y verificado en el dispositivo.**

---

## 1. Sí se puede sacar de los `.glb`

La pregunta era si la pose de descanso se podía extraer de los assets que ya
hay, sin volver a Blender. **Se puede**, y de un frame de seña:

- Los 32 archivos de `signs/` tienen 2424 keyframes. **851 tienen los dos brazos
  colgando** y 188 de esos además están quietos.
- El elegido es el **primer keyframe de `0029_0000_0000.glb` (JUICIOSO), t =
  0.0417 s**: manos a la altura de la cadera, simétrico a 1.2 cm, y **la cara no
  se toca** (ningún hueso de expresión se mueve en ese frame).
- Se descartó el promedio de los 46 inicios de clip (asimétrico, cabeza girada)
  y su versión simetrizada: **rompe la malla**, se ve el desgarro en el hombro.
- No hay Blender ni three.js headless en la máquina, así que se escribió un
  renderizador offline (skinning + el pivot y la cámara de la app) para verlo:
  `changes/reposo-y-crossfade/img/`.

Medido en el dispositivo: las manos pasaron de **52 cm del eje a 21/23 cm**, a la
altura de la cadera, y el BIN chunk del `.glb` quedó **byte a byte igual** (solo
cambian 46 números del JSON).

## 2. El crossfade no es un lujo: los clips ya son continuos

El hallazgo que reencuadra el trabajo:

- El final de una seña y el inicio de la siguiente están a **5.9 cm de
  mediana**; el **79%** de los pares ya es continuo.
- **Antes `app.js` los desarmaba**: `TRANSITION_FRAMES = 6` metía ~100 ms de pose
  congelada entre cada par, y cada transición pagaba dos saltos de ~46 cm en
  lugar de uno de 5.9 cm.

Las siete decisiones del plan se respetaron. La única que necesitó una variación
está abajo (§4).

## 3. Decisiones que quedaron tomadas

- Fundición de **100 ms** (`CROSSFADE_MS`), dos actions vivas y el clip
  siguiente arrancando `dur − 100 ms`.
- La pose se hornea como **paso del pipeline** (`tools/rest_pose.py` invocado
  desde `split_signs.py`), nunca editando `avatar.glb` a mano.
- Sin assets nuevos: se sobrescriben 46 rotaciones que ya estaban en el archivo.

## 4. Tres cosas que la implementación cambió sobre el plan

**(a) `rt_turn_total_ms` no mide lo que la investigación decía.** Es del primer
texto al último fragmento: STT y backend. El crossfade no lo mueve —ni un
milisegundo—, porque el avatar no está en ese camino. La "~8% menos" de §4.5 de
la investigación es real, pero es sobre la **animación**. Así que se agregó la
métrica que sí la mide: `SignAiMetrics.onPlayback` (`firmado: N señas en Xms`),
un reloj que arranca con la primera seña y para con `onPlaybackDone`. La
documentación que decía "remedir `rt_turn_total_ms`" está corregida en
`AGENTS.md` y en `tools/TIEMPO-REAL.md`.

Con ella, el A/B en el Redmi (misma frase "hola gracias", misma build salvo
`app.js`, tres corridas cada una):

| | mediana | |
|---|---|---|
| antes (`TRANSITION_FRAMES = 6`) | 4708 ms | 6 ticks de pose congelada + cola entera |
| crossfade 100 ms | **4522 ms** | −186 ms (−4.0%), contra −200 ms predichos |
| corte seco (reducir animaciones) | 4634 ms | sin congelado, sin solapar |

El −4% es de una frase de **dos** señas, o sea de una sola frontera: −186 ms por
frontera. Con más señas por frase la proportionally es mayor, y el número
absoluto es el que importa.

**(b) La misma seña dos veces seguidas no se puede fundir.** Three.js cachea una
`action` por clip, así que "hola hola" devuelve la misma action y no hay dos que
mezclar: es corte seco. No es una degradación — los clips son continuos consigo
mismos y el salto es el de cualquier par — pero conviene saberlo antes de
prometer una fundición que no está. En el logcat del modo debug sale como
`corte seco`.

**(c) La primera seña entra con `fadeIn`, la de `reduceMotion` no.** Una
fundición de duración 0 no es "sin fundición": el interpolant de peso arranca y
termina en el mismo instante y three.js **deshabilita la action** (`_updateWeight`
pone `enabled = false` cuando el valor interpolado es 0). Por eso el 0 del ajuste
de accesibilidad toma una rama aparte, sin llamar a `fadeIn`/`fadeOut`.

## 5. La trampa que costó un avatar invisible

`rest_pose` escribía las rotaciones como **string** con un decimal corto
(`"0.16770568"` en vez de `0.1677056849002838`): el `.glb` quedaba 1228 bytes
más chico y `GLTFLoader` **no coerciona** — `Quaternion.fromArray()` copia el
string tal cual, la matriz del hueso queda con `NaN` y el personaje no se ve. Sin
un error en la consola; el log decía "avatar listo · nodos: 117".

Queda anotado en el docstring de `rest_pose.compact()` porque la versión corta
*parece* la correcta y es el tipo de coisa que un agente reintroduce. Los floats
van como float: `json.dumps` los escribe con la representación más corta que
vuelve a dar el mismo float32, que es exactamente como los grabó Blender, así que
el archivo queda del mismo tamaño (+4 bytes).

## 6. Herramientas

Tres nuevas, para que las cifras de la investigación sean reproducibles:

- **`tools/glb_pose.py`** — GLB, FK, muestreo de clips y reconstrucción de la
  pose de bind desde las IBM. Sin dependencias. El lector de GLB del repo vive
  acá ahora (el de `split_signs.py` se mudó, no se duplicó).
- **`tools/rest_pose.py`** — hornea la pose. Idempotente, y con `check_bind()`:
  si el export no es la pose de bind, frena en vez de deformar en silencio.
- **`tools/preview_pose.py`** — render offline (skinning + el pivot y la cámara
  que `app.js` declara, leídos del archivo). Regenera las dos imágenes de
  `changes/reposo-y-crossfade/img/`, incluida la del espejado que rompe la malla.

## 7. Verificado

| | |
|---|---|
| `./gradlew build` + `installDebug` | ok |
| Reposo en el dispositivo | al abrir, después de firmar y con el micro prendido |
| Fundición entre dos señas distintas | `fundición -> 0000.001` en logcat, `actions vivas: 2` en el panel |
| Frontera | el clip entra a los 2.03–2.07 s de uno de 2.17 s = `dur − 100 ms` |
| Long press en mitad de una seña | corte seco al reposo, **sin** `onPlaybackDone` (un reset no es una reproducción terminada) |
| `reduceMotion` | `fundición: off`, los dos pasos por `corte seco`, y 74 ms más rápido que el código viejo |
| `rt_first_sign_ms` | **p50 911 ms / p95 1335 ms** (línea base 913 / 1336) sobre los 28 fixtures |
| `tools/verificar-tiempo-real.sh` | 15/15 checks |
| Jank | 2.5% (3/120 fotogramas), mediana 13.1 ms — el GPU del WebView sigue siendo el costo, como antes |

Una corrida dio p95 1690 ms y se descartó: la máquina estaba ocupada con las
pruebas del dispositivo en paralelo y el STT estaba claramente más lento ese día
(`stt_inference_ms{small}` p95 361 ms contra 172 ms de la línea base). La segunda
corrida, sola, clavó la línea base.

## 8. Pendiente

- **Verlo con voz humana.** El paso de una seña a otra se comprobó por logcat,
  por la métrica y por capturas, pero la fundición es un efecto de 100 ms: la
  confirmación de que "se ve bien" es del owner, mirando y firmando.
- Las 18 señas que faltan (`0032`–`0049`) entran por el mismo pipeline; la pose
  de descanso no se toca, salvo que Samuel exports otra cosa.

---

**Por qué los números son creíbles**: todo sale de parsear los `.glb` y hacer
FK con las `inverseBindMatrices` del export, sin dependencias. Dos trampas que
costaron tiempo y están anotadas en la metodología: los accessors enteros
(`JOINTS_0`) hay que leerlos como `uint16` y las `MAT4` son column-major (sin
transponerlas la malla explota).
