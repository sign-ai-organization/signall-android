# Marcadores faciales: medición de las variantes, recorrido del tipo y propuesta

**Fecha:** 2026-11-04, tarde · **Rama:** `main`

## Qué llegó

Orden 8 de [`docs/historia/PLAN-SEMANA-13.md`](../../docs/historia/PLAN-SEMANA-13.md): pavimentar el terreno
de los marcadores faciales **sin activarlos**. El owner fijó ese día que los marcadores son
**gramaticales, no emocionales**: la clasificación de alegría/tristeza/sorpresa se intentó y
no se logró, y el backend no manda ninguna señal emocional. Queda descartada la opción B y
se trabaja la A (tabla tipo gramatical → variante).

## Qué se hizo

- **Medidor nuevo:** `tools/measure_face_markers.py`. Lee los morph targets de
  `app/src/main/assets/web/models/juan.glb` y `pauline.glb`, calcula el alcance geométrico
  de cada target (mm a peso 1), samplea los canales `weights` de los 300 clips y compara
  cada variante contra el clip base de su seña en tiempo normalizado. Soporta accessors
  `sparse`, que `glb_pose.read_accessor` no lee porque el empaquetado no los necesita.
- **Resultado completo:** [`docs/mediciones/marcadores-2026-11-04.md`](../../docs/mediciones/marcadores-2026-11-04.md)
  con tablas por variante, por par de variantes y por seña para los dos avatares, más el
  JSON con el resumen por seña al lado. No se renderizó: la medición es geométrica.
- **Recorrido del tipo verificado**, sin cambios de contrato:
  `PhraseType` (`../signall-backend/app/domain/models.py:6`) → `analyze(sentence)`
  (`app/nlp/gloss_analyzer.py:78`) → `Segment(type=...)` (`app/nlp/nlp_service_impl.py:87`)
  → frame `segment` con `index`/`total` (`app/controller/controller.py:289`) → parser
  (`data/SignAiWebSocketClient.kt:252`) → `SignQueueItem.type`
  (`ui/state/MainScreenState.kt:949`) → JSON del puente
  (`ui/webview/SignAiWebViewController.kt:110`) → `state.currentType`
  (`app.js:830`), que hoy solo aparece en el diagnóstico.
- **Propuesta de tabla tipo → variante** y **lista exacta de cambios** para activarlos, en
  [`docs/PLAN-AVATARES-Y-MARCADORES.md`](../../docs/PLAN-AVATARES-Y-MARCADORES.md). La tabla
  **no se escribió** en `../signall-docs/.../pruebas.md`: la decisión es del owner.
- El plan se puso al día: DOC-5 y DOC-6 actualizados, DOC-9 nuevo, registro de cambios con
  la fila del 2026-11-04. Se corrigió el nombre del inventario a `models/avatars.json`
  (los documentos decían `avatares.json`; el archivo y el script siempre dijeron
  `avatars.json`).

## Qué se decidió

- **Opción A**, por decisión del owner del 2026-11-04. La B queda descartada. La C se toma
  como corte mínimo dentro de la A: activar solo las filas medidas.
- **No se implementó la función.** No se tocaron `embeddedClipsFor`, `pickVariant` ni
  `loadQueueItem`. Los marcadores siguen apagados.
- **Backend: nada.** El tipo ya viaja por oración; no hace falta frame, campo ni métrica
  nueva para la opción A.
- **No se tocó Overleaf ni `../signall-backend`.**

## Qué se midió

Todo sobre los GLB **empaquetados** de la app (2026-10-27), con
`tools/measure_face_markers.py`, el 2026-11-04. Δ = desplazamiento facial que agrega la
variante sobre el clip base de la misma seña, mediana de las 50 señas (las 50 dieron
idénticas).

- **Δ pico:** `_alegria` 7,28 mm · `_tristeza` 6,24 · `_sorpresa` 7,07 · `_pregunta` 8,31 ·
  `_negacion` 3,66. Con el encuadre por defecto (1 mm ≈ 1,26 px) son 9,2 / 7,9 / 8,9 / 10,5 /
  4,6 px. Ninguna queda por debajo de 1 mm.
- **Cara idéntica en las 50 señas:** la matriz de pesos de `0049_<variante>` es exactamente
  la de `0000_<variante>` para las cinco.
- **Pose fija:** el rango de peso a lo largo del clip es 0,00 mm en las cinco; lo único que
  se mueve durante la seña es el parpadeo, que ya está en el base.
- **Solo cara:** los 250 clips variantes tienen los mismos canales de hueso y los mismos
  valores que su base (tolerancia 0,01° / 0,01 mm). No agregan cabeza, tronco ni manos.
- **Distinción:** el par más parecido es `_sorpresa`–`_pregunta` (2,89 mm de pico, 0,86 mm
  de RMS; comparten la subida de cejas y difieren en la boca). El segundo es
  `_tristeza`–`_negacion` (1,07 mm de RMS). Los demás pares superan 1,6 mm de RMS.
- **Alcance de la malla de ojos:** Juan 9,44 mm, Pauline 7,90 mm; la malla de la cara mide
  15,76 mm en los dos. No cambia las cifras de la tabla.

## Qué quedó pendiente

- **Validación LSC** de las dos filas abiertas: pregunta de contenido y orden. La propuesta
  deja esas dos con el clip base hasta que alguien que sepa LSC defina la cara.
- **Escribir la tabla aprobada en PR-AVT-05** (`../signall-docs/borradores/plan-pruebas/01-diseno/avatar/pruebas.md`),
  que hoy tiene un TODO y pide para la negación un movimiento de cabeza que la variante no
  aporta. Lo hace el owner.
- **Implementar** `embeddedClipsFor` + `pickVariant` en `app.js` cuando la tabla esté
  aprobada. Ojo: hay que separar «toma» (`id.<n>`) de «variante» (`id_<sufijo>`) o el
  resolutor elegiría una cara al azar.
- **`AGENTS.md` y otros documentos siguen diciendo `models/avatares.json`**; el archivo real
  es `models/avatars.json`. No se tocó `AGENTS.md` en esta sesión.
- **Cabeza de la negación:** si el validador LSC la pide, es una tanda nueva en
  `../signall-avatar` (Cascadeur), no un cambio de la app.

## Activación de los marcadores (misma sesión, después)

El owner pidió dejar la **base funcional ya**, usando las variantes que existen, sin esperar
la validación LSC. Se implementó la tabla de la propuesta **tal como está medida**:

| Tipo | Variante |
|---|---|
| `AFFIRMATIVE` | base |
| `YES_NO_QUESTION` | `_pregunta` |
| `NEGATIVE` | `_negacion` |
| `CONTENT_QUESTION` | base (abierto) |
| `IMPERATIVE` | base (abierto) |

Cambios en `app/src/main/assets/web/app.js`:

- `FACE_MARKER_SUFFIXES` y `FACE_VARIANT_BY_TYPE`, la tabla que se cambia cuando LSC defina
  las dos filas abiertas.
- `embeddedClipsFor` devuelve `{ clips, variants }`: las tomas (`0000`, `0000.001`, …) van
  separadas de las caras (`0000_pregunta`, …). Sin esa separación, `pickVariant` sortearía
  una cara.
- `pickVariant(clips, variants, type)` aplica la tabla, cae al base si la variante falta y
  deja `state.currentVariant` para el diagnóstico.
- `loadQueueItem` le pasa `item.type`. No cambió el contrato.
- El overlay muestra `cara:` al lado de `tipo:`.

`tools/mock_backend.py`: se agregó `--type <TIPO>` para probar cada fila sin el backend real.
También se quitó el eco de `text` como `transcript`: el backend real no lo hace y, con el
modo en vivo prendido, el eco disparaba el bucle de reenvío de la misma frase.

### Verificación en el Redmi Note 9S

Con el mock y `adb reverse tcp:8080 tcp:8080`, build `debug` apuntando a
`ws://127.0.0.1:8080/ws/translate`:

- `--type YES_NO_QUESTION` → la escena cargó `0038_pregunta`; overlay `tipo:
  YES_NO_QUESTION  cara: pregunta`.
- `--type NEGATIVE` → `0038_negacion`; overlay `tipo: NEGATIVE  cara: negacion`.
- `AFFIRMATIVE` sigue usando el base.
- La app recibió una sola `segment` por frase, sin bucle.

Pendiente y sin cambios: la validación LSC de pregunta de contenido y orden, la escritura de
la tabla en PR-AVT-05 y el movimiento de cabeza de la negación.

### Verificación con el backend real (misma sesión)

El owner pidió dejar el backend real preparado, sin mock. Se levantaron los dos servicios:

- **Backend**: `docker compose -f docker-compose.yml -f docker-compose.host.yml up -d --build backend`.
  Con la red de compose, Neon daba `TimeoutError` al crear el pool (la resolución devuelve IPv6 y
  el contenedor se queda esperando); con la red del host conecta. `/health` respondió con
  `catalog_glosses: 50` y 109 filas de caché.
- **STT**: `docker compose up -d --build` en `../signall-stt`. La imagen vieja fallaba al
  arrancar con `NameError: REALTIME` (código anterior al commit actual); el rebuild cargó el
  modelo `medium` en CUDA y `/health` respondió `device: cuda`.

Con los dos arriba, frases reales por el WebSocket y por la app (Redmi Note 9S, build `debug`
con `adb reverse tcp:8080 tcp:8080`):

| Frase | Tipo del backend | Glosas | Clip en la escena |
|---|---|---|---|
| `No quiero ir a la universidad.` | `NEGATIVE` | YO, NUNCA | `0038_negacion`, `0037_negacion` |
| `¿Tú comes?` | `YES_NO_QUESTION` | TU, COMER | `0039_pregunta`, `0007_pregunta` |
| `Yo como.` | `AFFIRMATIVE` | YO, COMER | `0038`, `0007` (base) |
| `¿Cómo estás?` | `CONTENT_QUESTION` | COMO ESTAS | base (fila abierta) |

El overlay mostró `tipo: NEGATIVE  cara: negacion`, `tipo: YES_NO_QUESTION  cara: pregunta` y
`tipo: AFFIRMATIVE  cara: base`. El backend real no manda el tipo por ninguna otra vía ni
cambió el contrato.

Ojo al probar: solo se ve una seña si sus glosas están en LSC50. «¿Vienes mañana?» produce
VIENES y MANANA, que no están en el catálogo, y la app las saltea sin error (no hay clip que
representar). Para la cara conviene usar frases con glosas del catálogo, como las de la tabla.
