# Pose de descanso + crossfade — 2026-10-14

Trabajo de la sesión: dejar al muñeco **con los brazos abajo en reposo** y que
**las señas se encadenen sin saltos**. El owner lo Formule así: "una posición
como base, pero que se vea normal, como un ser humano en descanso", y un
crossfade entre animaciones.

Plan completo en [`../changes/reposo-y-crossfade/tasks.md`](../changes/reposo-y-crossfade/tasks.md);
medidas y metodología en
[`investigacion.md`](../changes/reposo-y-crossfade/investigacion.md).

---

## 1. Sí se puede sacar de los `.glb`

La pregunta era si la pose de descanso se podía extraer de los assets que ya
hay, sin volver a Blender. **Se puede**, y de un frame de seña:

- Los 32 archivos de `signs/` tienen 2424 keyframes. **851 tienen los dos brazos
  colgando** y 188 de esos además están quietos (no en mitad de un gesto).
- El elegido es el **primer keyframe de `0029_0000_0000.glb` (JUICIOSO), t =
  0.0417 s**: manos a la altura de la cadera, brazos a 28° de la vertical,
  simétrico a 1.2 cm, y **la cara no se toca** (ningún hueso de expresión se
  mueve). Hoy el reposo tiene las manos a 54 cm del eje y 35° bajo la
  horizontal.
- Se descartó el promedio de los 46 inicios de clip (asimétrico, cabeza girada)
  y su versión simetrizada: **rompe la malla**, se ve el desgarro en el hombro.
- No hay Blender ni three.js headless en la máquina, así que se escribió un
  renderizador offline (skinning + el pivot y la cámara de la app) para verlo:
  `changes/reposo-y-crossfade/img/`.

## 2. El crossfade no es un lujo: los clips ya son continuos

El hallazgo que reencuadra el trabajo:

- El final de una seña y el inicio de la siguiente están a **5.9 cm de
  mediana**; el **79%** de los pares ya es continuo. Los 46 clips arrancan y
  terminan en el mismo cluster (desvío de 2.5–6 cm).
- **Hoy `app.js` los desarma**: `TRANSITION_FRAMES = 6` mete ~100 ms de pose
  congelada entre cada par, y cada transición paga dos saltos de ~46 cm en
  lugar de uno de 5.9 cm.

Simulación de 10 frases reales a 60 fps, métrica = salto máximo de la mano entre
fotogramas:

| | cm/frame |
|---|---|
| hoy | **94.5** |
| solo la pose de descanso nueva | 25.6 |
| pose nueva + crossfade 100 ms | **12.1** |
| crossfade 150 ms | 8.8 (piso = velocidad real de una seña) |

O sea: **la pose de descanso sola no arregla los saltos**, y el crossfade es lo
que los arregla. Los dos juntos.

Además el crossfade **acorta** el turno: el clip siguiente arranca 100 ms antes
del final del anterior, así que una frase de 3 señas dura 600 ms menos (~8%).

## 3. Decisiones que quedaron tomadas

- Fundición de **100 ms** (constante, se cambia en una línea). Elimina el 87% del
  salto solapando solo ~3 cm de gesto de cada lado.
- La pose se hornea como **paso del pipeline** (`tools/rest_pose.py` invocado
  desde `split_signs.py`), nunca editando `avatar.glb` a mano.
- Sin assets nuevos: se sobrescriben 46 rotaciones que ya están en el archivo.

## 4. Pendiente

Implementación completa (tareas 0–6 del plan). Lo que hay que decidir antes de
escribir código está en §5 de la investigación; las propuestas ya están, pero
`reduceMotion` (D6) implica un comando de puente nuevo.

También salió deuda de contexto: **`openspec/specs/avatar/spec.md` describe el
pipeline muerto** (landmarks de MediaPipe, huesos `DEF-`, `sign.json`) que ya no
existe desde la migración a GLB. Tarea 6.1.

---

**Por qué los números son creíbles**: todo sale de parsear los `.glb` y hacer
FK con las `inverseBindMatrices` del export, sin dependencias. Dos trampas que
costaron tiempo y están anotadas en la metodología: los accessors enteros
(`JOINTS_0`) hay que leerlos como `uint16` y las `MAT4` son column-major (sin
transponerlas la malla explota).