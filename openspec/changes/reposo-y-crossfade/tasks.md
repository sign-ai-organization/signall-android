# Pose de descanso + crossfade entre señas

Plan de trabajo. La investigación que lo respalda está en
[`investigacion.md`](investigacion.md) con todos los números; acá está solo
qué hay que hacer y en qué orden.

Estado: **implementada y verificada en el dispositivo** (2026-10-14).

Tres cosas se apartaron del plan durante la implementación y están anotadas en
§4 de la bitácora (`openspec/sessions/2026-10-14-reposo-y-crossfade.md`): la
métrica que había que remedir (`rt_turn_total_ms`) no mide la animación y se
agregó `firmado:`; la misma seña repetida no se puede fundir (three.js cachea
una action por clip) y va por corte seco; y `fadeIn(0)` no es "sin fundición"
sino "action deshabilitada", así que `reduceMotion` toma una rama aparte.

---

## Decisiones tomadas en la investigación

| # | Decisión | Valor |
|---|---|---|
| D1 | Duración de la fundición | **100 ms** (`CROSSFADE_MS`), una constante en `app.js` |
| D2 | Clip siguiente cargado | precargar al arrancar el clip actual; si no llega, corte seco |
| D3 | `onPlaybackDone` | cuando termine la fundición de salida (ya calendarizada para acabar con el clip, no agrega latencia) |
| D4 | `playFrom` / Repetir en mitad de una fundición | matar la action que sale antes de arrancar |
| D5 | Long press (reset) | corte seco — es un reset, no una transición |
| D6 | `reduceMotion` | la fundición se desactiva si está el ajuste prendido (hay que pasarle el flag, ver 4.4) |
| D7 | Pose de descanso | rotaciones de `signs/0029_0000_0000.glb`, variante 0, keyframe 0 (`t = 0.0417 s`), solo la cadena de brazos |

Descartados con evidencia, no volver a abrir: el promedio de los 46 inicios de
clip (asimétrico, cabeza girada) y su versión espejada (**rompe la malla**, se ve
en `img/reposo-comparacion.png`).

---

## 0. Herramientas de análisis → `tools/`

- [x] 0.1 `tools/glb_pose.py` — lectura de GLB + FK + sampling de clips, self-contained
      (lo que hoy es `/tmp/opencode/pose/lib.py`). Sin esto las cifras de
      `investigacion.md` no son reproducibles.
- [x] 0.2 `tools/preview_pose.py` — renderizador offline (LBS + el pivot y la
      cámara de `app.js`) para ver una pose sin instalar la app. Las dos
      imágenes de `img/` se generan con esto.
- [x] 0.3 Correr 0.2 sobre las 4 poses y dejar los PNG regenerables.

## 1. Pose de descanso

- [x] 1.1 `tools/rest_pose.py`: copia las rotaciones de la cadena de brazos del
      clip de referencia a los nodos de `models/avatar.glb`
      (`shoulder`, `upper_arm`, `forearm`, `hand`, `palm.*`, `f_*`, `thumb.*` —
      46 huesos, L y R). Idempotente: no parte de los valores actuales del avatar.
- [x] 1.2 Integrarlo en `tools/split_signs.py` al final (después de
      `write_glb(models / "avatar.glb", ...)`) para que regenerar no pierda la
      pose. **Nunca editar `avatar.glb` a mano**: se regenera.
- [x] 1.3 Verificar que el archivo no cambia de tamaño (solo se sobrescriben
      floats que ya estaban) y que `split_signs.py` sigue reportando el mismo
      `alto`/`ancho` (el bbox sale de los accessors `POSITION`, no del TRS).
- [x] 1.4 Confirmar con 0.2 que la poseIdle es la imagen B de `img/`.
- [x] 1.5 Instalar y mirar en el dispositivo: reposo al abrir la app, después de
      una seña, y con el micrófono prendido esperando.

## 2. Crossfade en `app.js`

- [x] 2.1 Constante `CROSSFADE_MS = 100` y helpers de peso.
- [x] 2.2 Dos actions vivas, no una: la que suena y la que se va. Cambiar
      `state.action` por algo que pueda sostener las dos.
- [x] 2.3 `startClip`: si hay una action saliendo, `crossFadeTo` en vez de
      `stopAllAction() + play()`. Si no hay, `fadeIn(dur)` desde el reposo.
- [x] 2.4 **Borrar `TRANSITION_FRAMES`** y el `state.transition`: hoy congelan el
      mixer ~100 ms entre señas y son la causa directa de los saltos (§4.1 de la
      investigación). El mixer tiene que seguir avanzando durante la fundición.
- [x] 2.5 El avance de cola pasa a `dur - CROSSFADE_MS`, no a `dur`.
- [x] 2.6 Precarga del ítem siguiente (D2): `loadSignClips` ya cachea por URL, así
      que es cargarlo y nada más.
- [x] 2.7 Al terminar la cola: `fadeOut` de la última action hacia el reposo.
- [x] 2.8 `notifyPlaybackDone` en el momento correcto (D3), sin extender el turno.

## 3. Caminos que se pueden interrumpir

- [x] 3.1 `playFrom` / Repetir con una fundición en curso (D4).
- [x] 3.2 Long press → `stopPlayback()` (D5): matar todas las actions.
- [x] 3.3 `dt` grande al volver del background: no debe teletransportar una
      fundición a mitad de camino (hoy está contemplado para el clip, hay que
      cubrir también la mezcla).
- [x] 3.4 Falla de carga de un ítem: `stopPlayback()` tiene que dejar al muñeco
      en reposo, no en pose a medio fundir.

## 4. Accesibilidad y telemetría

- [x] 4.1 Pasar `reduceMotion` a la escena (hoy solo existe del lado Compose).
- [x] 4.2 Con el ajuste prendido la fundición es 0 y se conserva el corte seco.
- [x] 4.3 El panel de debug muestra las dos actions, no una.

## 5. Verificación

- [x] 5.1 `./gradlew build` + `./gradlew installDebug` en el dispositivo.
- [x] 5.2 Mirar las 4 conversaciones de `tools/TIEMPO-REAL.md` §3 y las frases de
      una y dos señas: el cambio tiene que verse en el paso de una a otra.
- [x] 5.3 `tools/verificar-tiempo-real.sh` completo: el pipeline no debe
      cambiar (`rt_first_sign_ms` p50 913 ms / p95 1336 ms).
- [x] 5.4 Remedir `rt_turn_total_ms`: se espera **~8% menos** (§4.5 de la
      investigación) y el adelanto automático debería dispararse menos. Actualizar
      las cifras de `tools/TIEMPO-REAL.md` si se mueven.
- [x] 5.5 No debe haber regresión de jank: el mixer con dos actions sobre el mismo
      esqueleto no cambia el costo de render (el AGENTS.md lo mide con
      `tools/rendimiento/gfxinfo.py`).

## 6. Contexto

- [x] 6.1 Actualizar `openspec/specs/avatar/spec.md`: hoy describe el pipeline
      muerto (landmarks, `DEF-` bones, `sign.json`) que ya no existe.
- [x] 6.2 Documentar la pose de descanso y el crossfade en `AGENTS.md`
      (sección "Kotlin ↔ JS bridge" / "Web asset rules").
- [x] 6.3 Sesión en `openspec/sessions/`.

---

## Riesgos

| riesgo | por qué importa | cómo se cubre |
|---|---|---|
| El clip siguiente no está cargado en la frontera | sin fundición cae al corte seco, que es el comportamiento actual: no es peor, solo no mejora | precarga (2.6) |
| La fundición tapa parte de la seña | a 100 ms se tapan ~3 cm de gesto; a 200 ms, ~18 cm | constante (D1) + el barrido medido está en `investigacion.md` §4.4 |
| Romper el ritmo en tiempo real | dos actions vivas durante la fundición | el advancement es `dur - CROSSFADE_MS`: el turno se acorta, no se alarga |
| Que la pose horneada se pierda al regenerar | `split_signs.py` reescribe `avatar.glb` | 1.2: es un paso del pipeline, no una edición |
| El `pivot` de `app.js` cambia | el encuadre se mueve | no puede: sale de los accessors `POSITION`, que no se tocan (verificado, 1.3) |