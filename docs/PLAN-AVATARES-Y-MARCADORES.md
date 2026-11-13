# Plan: avatares Juan y Pauline, y marcadores faciales

> **Documento vivo — obligatorio mantenerlo actualizado.** Toda persona o agente que
> trabaje en esta iniciativa debe actualizar este plan en cada paso que realice: al
> investigar, cambiar código o assets, tomar una decisión, cambiar un documento o detectar
> una diferencia. Debe anotar qué cambió, en qué repo/archivo, con qué evidencia y qué
> queda pendiente. También debe actualizar la matriz de diferencias y el registro de
> cambios de este documento antes de terminar. **No se hacen cambios silenciosos ni se
> deja el plan para “actualizar después”.**

**Estado:** **implementado el 2026-10-26** el selector funcional con los dos avatares y el
empaquetado de los GLB; **regenerados el 2026-10-27** con la versión de los avatares que
tiene la cadera centrada. El 2026-11-04 el owner fijó el enfoque **A** (tabla tipo
gramatical → variante), descartó la clasificación emocional (**B**) y se activaron las filas
medidas. **El 2026-11-05** se reemplazaron los GLB por la versión de Samuel con **cuatro
marcas gramaticales** (`_afirmativo`, `_pregunta`, `_negacion`, `_orden`) y sin clip base:
las cinco categorías del backend ya tienen cara, y pregunta sí/no y pregunta de contenido
comparten `_pregunta`. Ver «Registro de cambios», fila 2026-11-05.
**Fecha:** 2026-10-24 (investigación y plan) · 2026-10-26 (implementación) · 2026-11-04
(medición de variantes e implementación del mapeo) · 2026-11-05 (GLB con cuatro caras)
**Alcance pedido:** usar Juan y Pauline desde el selector existente. Desde el 2026-11-04 se
representan también los marcadores faciales de los tipos validados por medición.

## Resultado de la implementación (2026-10-26)

Se implementó el selector funcional con los dos avatares. **Los marcadores faciales no se
tocaron**: el owner decidió ese día dejar las expresiones para después y seguir
representando solo el clip base de cada seña, «como lo estamos mostrando ahora».

Qué quedó hecho:

- **Empaquetado.** `tools/pack_avatars.py` lee los dos originales de
  `../material-3d/Avatares/`, verifica 1 skin, 102 articulaciones y los 300 nombres de clip
  esperados, hornea la pose de descanso con la regla de `rest_pose.py` y poda los morph
  targets que ninguna variante anima. Escribe `models/juan.glb` (34,21 MB),
  `models/pauline.glb` (29,46 MB), el inventario `models/avatars.json`, `manifest.json` y
  `domain/SignsCatalog.kt`. El `avatar.glb` de 12,98 MB se borró.
- **Selector.** `Character` en `domain/Settings.kt` tiene `Juan` y `Pauline`; Juan es el
  predeterminado y cualquier valor viejo (`Julián`, `Samuel`, `Ximena`) se normaliza a
  Juan al leerlo, porque esas opciones nunca cambiaron el modelo.
- **Puente.** El avatar inicial viaja en `?avatar=<id>` al cargar la página y los cambios en
  caliente usan `window.SignAll.setAvatar(id)`, aditivo sobre `domain/SignAllCommands.kt`. El
  modelo nuevo se parsea **antes** de liberar el anterior, y la escena avisa
  `avatarLoaded:<id>` o `avatarFailed:<id>`.
- **Bloqueo.** El selector está deshabilitado salvo en reposo: se permite en `Inactivo`,
  `Desconectado` y `Error`, y se niega con captura, frase lista o enviada, traducción, cola
  o repetición en curso. Mientras el avatar carga, la barra de abajo y el botón de
  reproducir quedan bloqueados.
- **Escena.** `app.js` carga el GLB del avatar elegido, indexa sus 300 clips, libera la
  geometría, los materiales y las texturas del anterior con `dispose()` y vuelve a resolver
  la seña que estaba en pantalla. Solo resuelve los clips base (`0000`..`0049`): el
  resolutor no reconoce los sufijos faciales a propósito.

Mediciones en el Redmi Note 9S, 2026-10-26, con `SignAllWeb` y `SignAllState` de logcat:

| Qué | Medido |
|---|---|
| Arranque con Juan | 1,81–2,50 s en caliente, 3,42 s en frío (caché de página) |
| Arranque con Pauline | 1,95 s |
| Cambio de avatar en caliente | 1,90–2,57 s, de los cuales 1,18–1,95 s son el parseo del GLB |
| APK debug / release sin firmar | 48 MB / 34 MB |
| PSS de la app con Juan cargado | ~390 MB |

Desvíos respecto del plan, todos deliberados:

- El plan recomendaba habilitar el cambio **solo** en `Inactivo`; se habilitó también en
  `Desconectado` y `Error` porque la lista del owner enumera operaciones activas, y en esos
  dos estados no hay ninguna.
- La correspondencia tipo de oración → variante facial (DOC-5, DOC-6) sigue abierta y sin
  uso: no se reproduce ninguna variante.
- `Pauline_final.glb` no pasa la comprobación de pose de bind (el TRS de 2,00 de los nodos
  que no son brazo no es la pose de bind). Se registró como `bind_warning` en
  `models/avatars.json` y el resultado visual en el dispositivo es correcto; queda como
  advertencia para una revisión del export, no como bloqueo.

Pendiente después de esta implementación:

- Medir lo que pide PR-AVT-06 (DOC-8): tamaño del instalador, memoria y tiempo de carga con
  build, modelo y dispositivo registrados.
- Actualizar en Overleaf el SDD (DOC-2) para que describa dos GLB completos, uno por avatar.
  Lo edita el owner.
- Decidir la tabla facial (DOC-5): la propuesta medida del 2026-11-04 está lista; falta la validación LSC y la aprobación del owner.

## Actualización del 2026-10-27 — GLB con la cadera centrada

El repo hermano `../signall-avatar` empezó a versionar en su rama `dev` los GLB finales del
pipeline de Cascadeur (`models/Juan.glb` y `models/Pauline.glb`, commit `cf014ef`). Esa
versión corrige la cadera: el `spine` venía corrido 5–12 cm y girado 10°–18° en las 50
señas, así que el avatar se iba de costado al representar y volvía al centro en reposo.

- `tools/pack_avatars.py` ahora lee de `../signall-avatar/models/`.
  `../material-3d/Avatares/` conserva la versión anterior y ya no es la fuente.
- Se regeneraron `models/juan.glb` (34,28 MB) y `models/pauline.glb` (29,46 MB),
  `models/avatars.json` y `manifest.json`; `SignsCatalog.kt` salió igual.
- Verificación estática: 300 clips, 102 articulaciones, **mismos nombres y mismas
  duraciones** por clip. El desplazamiento lateral de `spine` en los 50 clips base bajó de
  **8,89 cm (Juan) y 8,92 cm (Pauline)** a **0,00 cm**.
- El diff canal por canal de los 300 clips muestra cambios **solo** en `spine` (traslación
  y giro) y `neck`/`head`; ningún otro canal cambió. El paquete que usa la app (pose de
  descanso horneada y morphs podados) conserva esos mismos cambios y ninguno más.

Documentación actualizada: `AGENTS.md`, `docs/SISTEMA.md`, `openspec/config.yaml`,
`specs/pipeline/spec.md`, `specs/avatar/spec.md`, `tools/README.md` y
`tools/pack_avatars.py`. Los documentos de la tesis no cambian: RF-AVT-02 pide reproducir
el GLB de cada glosa y no distingue versiones del asset.

## Medición y propuesta de marcadores (2026-11-04)

El owner fijó el 2026-11-04 que los marcadores **no son emociones**: dependen del tipo de
oración. La clasificación de alegría, tristeza y sorpresa se intentó y no se logró, y el
backend no manda ninguna señal emocional, así que **la opción B queda descartada**. Se
trabaja la **opción A**: una tabla tipo gramatical → variante con los cinco tipos que el
backend ya calcula. Esta sesión pavimenta el terreno: mide, documenta el recorrido del dato
y propone la tabla y los cambios, **sin activar la función**.

### Cómo se midió

Herramienta nueva: `tools/measure_face_markers.py`. Lee los dos GLB empaquetados y hace
tres cosas, sin renderizar y sin Blender:

1. **Alcance geométrico.** Cada morph target guarda, por vértice, cuánto se mueve ese
   vértice a peso 1. El máximo de esas distancias es el alcance del target en milímetros.
2. **Pesos animados.** Los canales `weights` del clip dan el peso de cada target en cada
   keyframe. Se samplea el clip completo (81 puntos) y se calcula el desplazamiento real
   `peso × alcance`.
3. **Δ contra el clip base.** La variante se compara con el clip base de la misma seña en
   tiempo normalizado, excluyendo el parpadeo (`eyeBlink`), que está en todas. Eso mide lo
   que el usuario **ve cambiar** si se activa el marcador.

Salida completa: [`mediciones/marcadores-2026-11-04.md`](mediciones/marcadores-2026-11-04.md)
(tablas por seña y por avatar) y el JSON con el resumen por seña al lado. La conversión a píxeles usa el
encuadre por defecto de `app.js` (cámara a 2,3 m, fov 45°, canvas de 2400 px): 1 mm ≈
1,26 px. **Es una cuenta, no una captura.** Los umbrales de visibilidad (< 1 mm apenas se
percibe, 1–2 mm marginal, > 3 mm visible) son una **hipótesis geométrica**: no se
validaron con personas ni con una grabación de pantalla.

### Resultado por avatar y variante

El Δ pico es la mediana de las 50 señas; en la medición **las 50 dieron idénticas** (ver
hallazgo 3). Juan y Pauline comparten los mismos pesos y los mismos alcances en la malla de
la cara; solo cambia el alcance máximo de la malla de los ojos (Juan 9,44 mm, Pauline
7,90 mm), que no cambia ninguna de las cifras de esta tabla.

| Variante | Δ pico (mm) | ≈px | Δ RMS (mm) | Cambia durante la seña | Target que más mueve |
|---|---:|---:|---:|---:|---|
| `_alegria` | 7,28 | 9,2 | 2,22 | 0,00 mm | `mouthSmile_L` (peso 0,75) |
| `_tristeza` | 6,24 | 7,9 | 1,49 | 0,00 mm | `browInnerUp` (peso 0,75) |
| `_sorpresa` | 7,07 | 8,9 | 2,35 | 0,00 mm | `browOuterUp_R` (peso 0,85) |
| `_pregunta` | 8,31 | 10,5 | 2,79 | 0,00 mm | `browOuterUp_R` (peso 1,00) |
| `_negacion` | 3,66 | 4,6 | 1,49 | 0,00 mm | `mouthShrugLower` (peso 0,45) |

Qué mueve cada una, en concreto (pesos sostenidos; las cinco mueven más targets que los
que se listan):

| Variante | Cejas | Ojos | Boca | Mejillas / nariz |
|---|---|---|---|---|
| `_alegria` | — | `eyeSquint` 0,25 | `mouthSmile` 0,75, `mouthDimple` 0,20 | `cheekSquint` 0,50 |
| `_tristeza` | `browInnerUp` 0,75, `browDown` 0,15 | `eyeSquint` 0,10 | `mouthFrown` 0,65, `mouthShrugLower` 0,30 | — |
| `_sorpresa` | `browInnerUp` 0,85, `browOuterUp` 0,85 | `eyeWide` 0,70 | `mouthFunnel` 0,20, `mouthLowerDown` 0,25 | — |
| `_pregunta` | `browInnerUp` 1,00, `browOuterUp` 1,00 | `eyeWide` 0,55 | `mouthPress` 0,30, `mouthRollLower` 0,15 | — |
| `_negacion` | `browDown` 0,45, `browInnerUp` 0,10 | — | `mouthShrugLower` 0,45, `mouthPress` 0,35, `mouthFrown` 0,55 | `noseSneer` 0,15 |

### Los seis hallazgos que cambian la decisión

1. **Las cinco variantes mueven la cara y ninguna es invisible.** El Δ pico va de 3,66 mm
   (`_negacion`, ≈ 4,6 px) a 8,31 mm (`_pregunta`, ≈ 10,5 px). Ninguna queda por debajo del
   umbral de 1 mm. La más sutil es `_negacion`, y aun así mueve la boca 3,37 mm y las cejas
   2,34 mm.
2. **Las cinco se distinguen entre sí, pero hay dos pares cercanos.** El par más parecido es
   `_sorpresa` vs `_pregunta`: 2,89 mm de pico y **0,86 mm de RMS**, porque las dos suben las
   cejas (0,85 vs 1,00) y solo se separan por la boca (`mouthFunnel` vs `mouthPress`). El
   segundo es `_tristeza` vs `_negacion`: 1,07 mm de RMS, porque comparten la boca fruncida y
   se separan sobre todo por `browInnerUp`. Todos los demás pares superan 1,6 mm de RMS.
3. **Cada variante es la misma cara en las 50 señas.** La matriz de pesos de
   `0049_alegria` es exactamente la de `0000_alegria`, y lo mismo con las otras cuatro. No
   hay adaptación por seña: el marcador es una pose de la cara que se estampa sobre
   cualquier seña. Eso hace que la tabla tipo → variante sea la unidad correcta (no hay
   información por seña que elegir), pero también que la cara sea la misma en dos señas cualesquiera.
4. **La expresión es una pose fija, no una animación.** El rango de peso a lo largo del clip
   es **0,00 mm** en las cinco: el marcador aparece y se queda. Lo único que se mueve en la
   cara durante la seña es el parpadeo, que viene igual en el clip base.
5. **Las variantes mueven solo la cara.** Los 250 clips variantes tienen exactamente los
   mismos canales de hueso y los mismos valores que su base (tolerancia 0,01° / 0,01 mm).
   No agregan movimiento de cabeza, tronco ni manos. En particular, el «movimiento lateral
   de la cabeza» que PR-AVT-05 espera para la negación **no lo puede dar la variante**: o
   viene en el clip de la seña, o hay que animarlo en Cascadeur.
6. **El clip base ya tiene cara: reposo más parpadeo.** Los pesos faciales del base son cero
   salvo `eyeBlink` (peso 1 en un keyframe). Por eso «afirmación = cara neutra» no necesita
   ningún trabajo: el clip base ya es esa cara.

### El recorrido del tipo, de `PhraseType` a `state.currentType`

Se revisó el camino completo y **el tipo llega en cada oración**. No hace falta cambiar el
contrato para la opción A.

| Paso | Archivo y línea | Qué pasa |
|---|---|---|
| 1. El backend parte la frase en oraciones | `../signall-backend/app/nlp/nlp_service_impl.py:36-37`, `app/orchestrator/orchestrator.py:18-24` | `split(text)` devuelve las oraciones; `translate_stream` las traduce en paralelo y las entrega **en orden**, con `index` y `total` reales desde la primera. |
| 2. Cada oración se clasifica sola | `app/nlp/nlp_service_impl.py:46-48`, `app/nlp/gloss_analyzer.py:78-108` | `analyze(sentence)` corre **sobre la oración**, no sobre la frase. Si una frase se parte en dos, cada mitad tiene su propio tipo. |
| 3. El tipo viaja en el segmento | `app/nlp/nlp_service_impl.py:87-88`, `app/domain/models.py:34-36`, `app/controller/controller.py:289-295` | `ValidatedSegment(type, glosses)`; el frame es `{"type":"segment","index","total","segment":{"type","glosses"}}`. |
| 4. Android lo parsea | `data/SignAllWebSocketClient.kt:252-263`, `domain/Translation.kt` | `PhraseType.from(...)` mapea los cinco valores; un valor desconocido cae en `UNKNOWN` y no rompe. |
| 5. Se guarda por ítem de cola | `ui/state/MainScreenState.kt:949-999`, `:1686` | `queueItemsFor(segment)` se llama **por oración** y pone `type = segment.type` en cada glosa de esa oración. |
| 6. Cruza el puente | `ui/webview/SignAllWebViewController.kt:105-110` | `put("type", item.type.wire)`: el JSON que recibe la escena lleva el tipo por ítem. |
| 7. `app.js` lo recibe | `app.js:825-836` | `loadQueueItem` hace `state.currentType = item.type || ""` justo antes de cargar el clip. |
| 8. Hoy solo se usa en el diagnóstico | `app.js:290` | Única lectura: la línea `tipo:` del overlay de depuración. `pickVariant` (`app.js:802`) no lo mira. |

Consecuencias:

- **El tipo está disponible exactamente donde se elige el clip** (`loadQueueItem` →
  `pickVariant`), sin tocar Kotlin ni el WebSocket. Es el punto de enganche natural.
- **Cuando una frase se parte en varias oraciones, cada una conserva su tipo.** El orden de
  la cola lo decide `index` / la cadena de entrega de `MainScreenState`; el tipo no participa
  del orden. Una frase como «No vienes. ¿Vienes?» produce dos ítems con tipos distintos, y
  el avatar puede cambiar de cara entre oración y oración.
- **El mismo signo puede aparecer en dos oraciones con tipos distintos** («Vienes.» y
  «¿Vienes?»). Cualquier caché que guarde la variante elegida por URL quedaría incorrecta:
  la caché actual (`signCache`, `app.js:350`) guarda por URL y devuelve **todos** los clips
  de la seña, así que la selección tiene que seguir ocurriendo después, en `pickVariant`.
- El backend **no manda** `secondary_signals` en el segmento ni ningún campo de emoción;
  `secondary_signals` es de colisiones gramaticales y se queda en la caché del backend.

### Propuesta de tabla tipo → variante (para PR-AVT-05)

Separación explícita: **lo medido** sale de la sección anterior; **la correspondencia
lingüística es una hipótesis** y la tiene que validar alguien que sepa LSC. La decisión es
del owner, y por eso esta tabla **no se escribió** en
`../signall-docs/borradores/plan-pruebas/01-diseno/avatar/pruebas.md`: se propone acá para
que el owner la apruebe y la pegue en PR-AVT-05 antes de correr la prueba.

> **Superado el 2026-11-05.** La tabla que sigue fue la propuesta sobre los GLB del
> 2026-10-26 (clip base + cinco caras emocionales). El export del 2026-11-04 trae otras
> cuatro caras —`_afirmativo`, `_pregunta`, `_negacion`, `_orden`— y ningún clip base, así
> que la correspondencia vigente es: `AFFIRMATIVE` → `_afirmativo`; `YES_NO_QUESTION` y
> `CONTENT_QUESTION` → `_pregunta`; `NEGATIVE` → `_negacion`; `IMPERATIVE` → `_orden`.
> Se conserva la tabla como historia de la medición, no como estado actual.

| Tipo (backend) | Qué espera PR-AVT-05 hoy | Variante propuesta | Lo que la variante mide | Tipo de dato |
|---|---|---|---|---|
| `AFFIRMATIVE` | Cara neutra, sin cambios en las cejas | **base** (ninguna variante) | El base tiene peso facial 0 salvo parpadeo | Medido: el base ya es neutro |
| `YES_NO_QUESTION` | Cejas levantadas durante toda la frase | **`_pregunta`** | `browInnerUp` y `browOuterUp` a 1,00; 8,31 mm (10,5 px) | Medido: es la variante que más sube las cejas |
| `CONTENT_QUESTION` | Cejas fruncidas hacia abajo durante la seña interrogativa | **abierto** | `_pregunta` sube las cejas (lo contrario); `_negacion` baja las cejas 0,45 pero es la cara de negación; `_tristeza` sube `browInnerUp` 0,75 y frunce la boca, no las cejas | **Hipótesis lingüística**: elegir entre base, `_negacion` o un clip nuevo |
| `NEGATIVE` | Ceño fruncido y movimiento lateral de la cabeza en NUNCA | **`_negacion`** para la cara | `browDown` 0,45 + boca fruncida; 3,66 mm (4,6 px). **No agrega cabeza** (hallazgo 5) | Cara: medido. Cabeza: no lo da la variante |
| `IMPERATIVE` | Ceño fruncido y expresión firme durante toda la frase | **abierto** | El único candidato «firme» es `_negacion` (3,66 mm), que se confunde con negación; `_tristeza` (6,24 mm) lee como tristeza | **Hipótesis lingüística** |

Propuesta de **corte mínimo** (opción A con la cautela de la C): activar solo las tres filas
medidas y dejar `CONTENT_QUESTION` e `IMPERATIVE` con el clip base hasta que el validador
LSC diga qué cara corresponde. Eso permite correr PR-AVT-05 sin inventar expresiones, y las
dos filas abiertas quedan como resultado explícito de la prueba, no como deuda escondida.

Dos advertencias que salen de la medición y afectan la tabla:

- **No usar `_sorpresa` y `_pregunta` para dos tipos distintos** si se quiere que la cara
  distinga: son el par más parecido (0,86 mm de RMS) y comparten la subida de cejas. Si
  `CONTENT_QUESTION` termina mapeada a `_sorpresa` y `YES_NO_QUESTION` a `_pregunta`, en
  pantalla las dos preguntas se van a ver casi iguales.
- **PR-AVT-05 pide para la negación un movimiento lateral de cabeza que la variante no
  tiene.** La prueba debe separar «cara» (lo que mide el marcador) de «cabeza» (lo que
  tendría que venir en el clip de la seña o en un trabajo nuevo de Cascadeur), o el criterio
  va a fallar por una razón que no es la cara.

### Cómo quedaron activados (implementado el 2026-11-04)

**App (`sign-ai-android`).** Se implementó el mínimo de la opción A **sin tocar los
assets ni el backend**, y se verificó en el Redmi Note 9S con el mock:

1. `embeddedClipsFor` (`app.js`) devuelve `{ clips, variants }`: `clips` son las tomas
   (`0000`, `0000.001`, …) y `variants` es un `Map` sufijo → clip con las cinco caras.
   Separarlos es lo que evita que el sorteo de tomas elija una cara.
2. `pickVariant(clips, variants, type)` aplica `FACE_VARIANT_BY_TYPE`; si la variante
   pedida no existe, usa el base. El azar quedó solo entre tomas.
3. `loadQueueItem` pasa `item.type` a `pickVariant` y `state.currentType` sigue siendo el
   tipo del `segment`. No hubo cambio de contrato.
4. El overlay de diagnóstico muestra `cara:` junto a `tipo:` (`tipo: NEGATIVE  cara:
   negacion`). Es la evidencia que falta para PR-AVT-05.
5. `signCache` sigue por URL y devuelve todas las tomas y variantes; la selección ocurre
   después, así que dos oraciones con el mismo signo y distinto tipo no se pisan.

La tabla implementada, literal a la propuesta medida:

| Tipo | Variante | Estado |
|---|---|---|
| `AFFIRMATIVE` | base | medido |
| `YES_NO_QUESTION` | `_pregunta` | medido |
| `NEGATIVE` | `_negacion` | medido (cara; sin cabeza) |
| `CONTENT_QUESTION` | base | pendiente de validación LSC |
| `IMPERATIVE` | base | pendiente de validación LSC |

**Backend (`sign-ai-backend`).** **Nada.** El tipo ya viaja por oración, con `index`/`total`
reales y sin campo emocional. No hace falta tocar frames, métricas ni catálogo. Solo habría
trabajo si se retomara la opción B, que el owner descartó.

**Repo de animaciones (`../signall-avatar`).** Nada para la opción A. Solo habría trabajo si
el validador LSC pide caras distintas por seña (hoy la misma cara se repite en las 50) o
movimiento de cabeza para la negación; eso es una tanda nueva de Cascadeur.

**Documentos.** La tabla aprobada va en PR-AVT-05
(`../signall-docs/borradores/plan-pruebas/01-diseno/avatar/pruebas.md`), que todavía tiene
un TODO y una expectativa de cabeza que la variante no cumple. Eso lo decide y lo pega el
owner. En Overleaf no se tocó nada.

**Herramientas.** `tools/mock_backend.py` acepta `--type <TIPO>` para probar cada fila sin
el backend real, y ya no devuelve el eco de `text` como `transcript` (el backend real no lo
hace y el eco disparaba el bucle del modo en vivo).

## Resumen de lo encontrado

- `Juan_final.glb` y `Pauline_final.glb` estaban el 2026-10-24 en la raíz del repositorio, sin rastrear por Git. Se trataron como entradas originales: no se editaron. Desde el 2026-10-26 viven en `../material-3d/Avatares/`, fuera del repo, con el resto del material 3D. Desde el 2026-10-27 la fuente vigente es `../signall-avatar/models/` (rama `dev`); la copia de `material-3d/` queda como versión anterior.
- Cada archivo contiene las 50 señas (`0000`–`0049`) y seis clips por seña: base, `_alegria`, `_tristeza`, `_sorpresa`, `_pregunta` y `_negacion`. Son **300 clips por avatar**.
- Ambos tienen un esqueleto de 102 articulaciones y canales de animación facial para cejas, ojos, mandíbula y boca. La lectura de los GLB encontró, en las 50 señas de cada archivo, que los tracks de huesos coinciden entre la versión base y sus cinco variantes, mientras cambian los tracks de pesos de malla. Esto confirma que los archivos ofrecen variaciones faciales sin cambiar los movimientos de huesos; no confirma que cada expresión sea lingüísticamente adecuada para una categoría.
- Cada archivo ocupa decenas de MB: Juan, **37.872.312 bytes**; Pauline, **35.272.616 bytes**. Juntos son **73.144.928 bytes** antes del empaquetado. Son cifras leídas de los archivos, no el tamaño del APK.
- La app **no carga todavía ninguno de esos dos archivos**. `app.js` carga `app/src/main/assets/web/models/avatar.glb` (12.978.476 bytes) y resuelve una sola animación por seña. El selector de Ajustes muestra Julián, Samuel y Ximena, pero solo persiste esa elección; no cambia el modelo.
- El backend sí entrega el **tipo gramatical** de cada oración. No clasifica alegría, tristeza ni sorpresa. La app ya recibe el tipo, pero no lo usa para seleccionar una variante facial.

### Identificación de las entradas

| Archivo | Tamaño | SHA-256 |
|---|---:|---|
| `Juan_final.glb` | 37.872.312 bytes | `2d87e29c3dce58d78dc1a6f3e48bbad456d3cb2511e462d120999949dfdd8748` |
| `Pauline_final.glb` | 35.272.616 bytes | `c91afb2be2d7888aa334f518e61fea75bd9d954bbd2f60f77bc05de1395c8f34` |

El análisis fue estático: leyó las cabeceras, nombres de clips, articulaciones y canales del GLB. No se hizo una inspección visual de las animaciones ni una prueba en el dispositivo.

## Qué dice cada fuente

| Fuente revisada | Qué aporta | Consecuencia para el plan |
|---|---|---|
| Código de la app (`main`, commit `1c027f002af29c5bc67a2a67671546bc704d66d6`) | `CharacterPicker` persiste el personaje, pero `MainScreenState.setCharacter()` no lo comunica a la escena. `app.js` carga una URL fija. El elemento de cola ya lleva `type` hasta JavaScript, donde hoy solo se conserva para depuración. | El selector necesita controlar la carga del modelo. La selección facial puede aprovechar el campo `type` existente **si** se decide una tabla de correspondencias gramaticales; no hace falta cambiar el WebSocket solo para eso. El resolutor actual no reconoce sufijos como `_pregunta` o `_alegria`. |
| Requerimientos consolidados v0.16, fechados 2026-10-21 (`../signall-docs`) | RF-NLP-03 define cinco tipos gramaticales. RF-AVT-03 acota el comportamiento requerido a mover las cejas según el tipo; aún lo marca pendiente. PR-AVT-05 pide una tabla de expresiones esperadas, escrita por el equipo antes de la ronda, y plantea probar dos frases de cada uno de los cinco tipos. | Antes de activar expresiones hay que acordar y documentar esa tabla. No se debe inferir que una categoría gramatical equivale a una emoción. |
| Overleaf SRS, `main.tex`, versión 524 consultada el 2026-10-24 | El resumen habla de expresiones faciales según el tipo de frase; define cinco tipos gramaticales y el ajuste «Personaje» como selector del personaje que ejecuta las señas. No proporciona una correspondencia entre tipos y expresiones emocionales. | Confirma la finalidad general del selector y de los marcadores, pero deja abierto el mapeo concreto. |
| Overleaf SDD, `main.tex`, versión 212 consultada el 2026-10-24 | Describe «los 50 archivos GLB» incluidos en la app y contiene una nota pendiente para agregar avatares, señas y archivos faciales. | El owner decidió usar dos GLB completos, uno por avatar, cada uno con 50 señas y sus variantes. El SDD debe actualizarse para describir ese empaquetado; este agente no escribe en Overleaf. |
| Overleaf Memoria, `sections/descripcionGeneral.tex`, versión 98 consultada el 2026-10-24 | Presenta las expresiones faciales y los marcadores no manuales como necesarios para la coherencia del mensaje, pero no especifica cuándo seleccionar cada clip. | Aporta el objetivo general, no una tabla de ejecución. |
| Plan de pruebas de `../signall-docs`, bloque Avatar, PR-AVT-05 | La tabla esperada para la cara y las frases de prueba son dependencias previas de la verificación. | Esa tabla debe ser un entregable antes de dar por implementados o validados los marcadores. El plan de pruebas debe ampliarse si el owner lo decide. |

## Matriz de diferencias y seguimiento documental

Esta tabla registra cómo se relacionan el código, los assets, los requisitos, las pruebas y la tesis. Toda persona que haga trabajo posterior debe actualizar su estado y agregar cualquier diferencia nueva, incluso si el resultado es que no hace falta modificar el documento.

| ID | Fuente y fecha/versión | Lo que dice la fuente | Código, assets o decisión del owner | Diferencia / estado | Acción y responsable |
|---|---|---|---|---|---|
| DOC-1 | Código app `main`, commit `1c027f0`; `app.js` y `Settings.kt` | La escena carga un `avatar.glb`; el selector actual guarda Julián/Samuel/Ximena, pero no cambia el modelo. | El owner decidió Juan y Pauline, dos GLB completos, con Juan predeterminado. | **Resuelto el 2026-10-26.** El selector carga `models/juan.glb` o `models/pauline.glb` y `avatar.glb` ya no existe. | Hecho: `Character` con Juan/Pauline, `?avatar=<id>`, `SignAll.setAvatar`, `avatarLoaded`/`avatarFailed` y specs actualizados. |
| DOC-2 | Overleaf SDD, `main.tex`, versión 212; consultada 2026-10-24 | Describe 50 archivos GLB en la app y deja pendiente documentar avatares, señas y archivos faciales. | Dos archivos recibidos; cada uno contiene 50 señas y 300 clips. El owner eligió empaquetarlos como un GLB completo por avatar. | **Divergencia; dirección resuelta por el owner.** | Actualizar la descripción de assets del SDD para reflejar dos GLB completos. Solo el owner edita Overleaf. |
| DOC-3 | `../signall-docs/documentos/requerimientos/SignAll_Requerimientos_Consolidados_v0.16.md`, 2026-10-21, RF-AVT-03 | El alcance actual del requisito es mover las cejas según el tipo de oración. | El owner decidió usar expresiones de todo el rostro disponibles en los GLB. | **Divergencia; dirección resuelta por el owner.** | Proponer al owner ampliar RF-AVT-03 y alinear la documentación consolidada antes de implementar. El agente no edita el documento de requisitos. |
| DOC-4 | Overleaf SRS, `main.tex`, versión 524; consultada 2026-10-24 | El resumen pide marcadores faciales según el tipo de frase y el ajuste Personaje selecciona el personaje. No fija clips ni una tabla de expresiones. | El owner eligió Juan/Pauline y rostro completo; falta el mapeo tipo→variante. | **Compatible en el objetivo general; detalle abierto.** | Mantener la decisión de mapeo pendiente y reflejarla en la tabla aprobada de PR-AVT-05. Overleaf sigue siendo de solo lectura para el agente. |
| DOC-5 | `../signall-docs/borradores/plan-pruebas/01-diseno/avatar/pruebas.md`, PR-AVT-05 | Hace falta una tabla de expresiones esperadas por los cinco tipos gramaticales; la tabla se escribe antes de la ronda. | El 2026-11-04 hay una **propuesta** de tabla medida (ver «Propuesta de tabla tipo → variante» en este plan): afirmación → base, pregunta sí/no → `_pregunta`, negación → `_negacion`; pregunta de contenido y orden quedan abiertas a validación LSC. | **Implementada en la app el 2026-11-04** para las tres filas medidas; verificado en el Redmi Note 9S. La tabla de PR-AVT-05 sigue con TODO y pide para la negación un movimiento de cabeza que la variante no aporta. | El owner valida las dos filas abiertas y pega la tabla en PR-AVT-05 antes de correr la prueba. No se editó `pruebas.md`. |
| DOC-6 | Backend, commit `c8aa516` (2026-11-04); `PhraseType`, `GlossAnalyzer`, contrato WS | Entrega un tipo gramatical entre cinco; **el tipo se calcula por oración**, no por frase. No clasifica emociones. `secondary_signals` es de colisiones gramaticales y no viaja en `segment`. | El recorrido completo está verificado: el tipo llega en cada `segment`, se guarda por ítem de cola y está disponible en `app.js` al elegir el clip (`state.currentType`), donde hoy solo se usa en el diagnóstico. | **Opción B descartada por el owner (2026-11-04); se trabaja la A.** No hace falta cambiar el contrato: el dato ya está donde se necesita. | **Implementado en `app.js`** (`embeddedClipsFor` separa tomas de variantes, `pickVariant` aplica la tabla, `loadQueueItem` pasa el tipo). Backend: nada. |
| DOC-9 | `models/juan.glb` y `models/pauline.glb` empaquetados (2026-10-27); medición 2026-11-04 | Las variantes existen para las 50 señas, pero no se sabía cuánto mueven la cara ni si se distinguen. | Cada variante es **la misma cara en las 50 señas**, es una **pose fija** (0,00 mm de cambio durante el clip) y **solo mueve la cara**: los 250 clips variantes tienen los mismos huesos que su base. Δ pico: 3,66–8,31 mm. Par más parecido: `_sorpresa`–`_pregunta` (0,86 mm de RMS). | **Medición hecha; consecuencia de alcance.** El marcador se puede decidir por tipo de oración, no por seña. La cabeza de la negación no la da la variante. | Escribir la tabla con esta limitación explícita; si LSC pide caras por seña o cabeza, es trabajo nuevo en `../signall-avatar`. |
| DOC-7 | OpenSpec local, `specs/ui/spec.md` y `specs/avatar/spec.md` | Describe el selector como UI-only y una escena con un único GLB sin animación de expresiones. | El owner decidió selector funcional con dos avatares; el rostro quedó fuera de alcance el 2026-10-26. | **Resuelto el 2026-10-26.** Los dos specs describen los dos GLB, el selector funcional y el bloqueo en reposo; `specs/pipeline/spec.md` describe el empaquetado nuevo. | Hecho. El mapeo facial sigue pendiente y así se declara en los specs. |
| DOC-8 | Overleaf SRS, versión 524; `../signall-docs` PR-AVT-06 | El tamaño del APK con todos los GLB está pendiente de medir; PR-AVT-06 pide medir los assets y el instalador. | Los dos GLB empaquetados suman 63,74 MB (34,28 + 29,46). Medido el 2026-10-26 en el Redmi Note 9S: APK debug 48 MB, release sin firmar 34 MB, PSS ~390 MB con Juan. | **Medición del APK hecha; falta el registro formal en PR-AVT-06.** | Registrar build, modelo y dispositivo en el plan de pruebas y en `docs/PLAN-MEDICION.md`. Responsable: el owner al actualizar los documentos. |

### Registro de cambios del plan

Agregar una fila por cada trabajo posterior relacionado con esta iniciativa. No borrar filas; corregirlas solo si se demuestra que el dato original era erróneo y explicar el motivo.

| Fecha | Trabajo realizado | Repo/archivos y fuentes | Diferencias/decisiones actualizadas | Pendiente |
|---|---|---|---|---|
| 2026-10-24 | Investigación inicial de GLB, backend, selector y documentos; `git pull --ff-only` de `../signall-docs` (ya estaba al día). | Ver “Fuentes técnicas y procedencia” al final. | Se registraron DOC-1–DOC-8; owner decidió dos GLB completos, rostro completo y cambio solo en reposo. | Correspondencia tipo gramatical→variante, por consultar con validadores LSC. |
| 2026-10-26 | Implementación del selector y del empaquetado; los marcadores faciales quedaron fuera de alcance por decisión del owner. | `tools/pack_avatars.py` nuevo; `models/juan.glb`, `models/pauline.glb` y `models/avatars.json`; `app.js`; `Settings.kt`, `SignAllCommands.kt`, `MainScreenState.kt`, `AvatarWebView.kt`, `SignAllWebViewController.kt`, `CharacterPicker.kt`; specs `avatar`, `ui` y `pipeline`; `AGENTS.md`. | DOC-1 y DOC-7 resueltos; DOC-8 con la medición del APK; rostro completo pasa a «fuera de alcance» hasta que el owner lo retome. | La tabla facial (DOC-5) queda sin uso; el SDD de Overleaf (DOC-2) sigue sin describir los dos GLB. |
| 2026-10-27 | Reempaquetado con los GLB del repo hermano `../signall-avatar` (rama `dev`, commit `cf014ef`), que corrigen la cadera. | `tools/pack_avatars.py`; `models/juan.glb`, `models/pauline.glb`, `models/avatars.json`, `manifest.json`; `AGENTS.md`, `docs/SISTEMA.md`, `openspec/config.yaml`, `specs/pipeline/spec.md`, `specs/avatar/spec.md`, `tools/README.md`. | DOC-8 actualizado a 63,74 MB. Sin cambios en nombres ni duraciones de clip; `spine` pasa de 8,89/8,92 cm de desvío a 0,00 cm. | Probar en el dispositivo que la representación ya no se va de costado. Los pies pueden deslizarse un poco en alguna seña (efecto secundario anotado en el hermano). |
| 2026-11-04 | Orden 8 de `docs/historia/PLAN-SEMANA-13.md`: medición real de las cinco variantes faciales, recorrido del tipo gramatical y propuesta de tabla y de cambios. El owner fijó el enfoque A y descartó B. | `tools/measure_face_markers.py` (nuevo); `docs/mediciones/marcadores-2026-11-04.md` y `.json` (nuevos); este plan; `openspec/sessions/2026-11-04-marcadores-faciales-medicion.md`. Backend leído en `c8aa516`; app leída en `3cf2be2`. | DOC-5 y DOC-6 actualizados; DOC-9 agregado con la limitación de los assets. Se corrigió el nombre del inventario: es `models/avatars.json`, no `avatares.json`. | Validación LSC de las filas de pregunta de contenido y orden; escribir la tabla aprobada en PR-AVT-05. `AGENTS.md` y otros documentos todavía dicen `avatares.json`. |
| 2026-11-04 | **Activación de los marcadores faciales** por pedido del owner: se implementó el mapeo tipo → variante de la propuesta medida, para tener la base funcional. | `app/src/main/assets/web/app.js`: `FACE_MARKER_SUFFIXES`, `FACE_VARIANT_BY_TYPE`, `embeddedClipsFor` (tomas vs. variantes), `faceMarkerSuffix`, `pickVariant` con tipo, `loadQueueItem` y `state.currentVariant` en el overlay. `tools/mock_backend.py`: `--type` y sin eco de `text` como `transcript`. `openspec/specs/avatar/spec.md` y este plan. | Verificado en el Redmi Note 9S con el mock: `YES_NO_QUESTION` → `0038_pregunta` y `NEGATIVE` → `0038_negacion`, con `tipo: NEGATIVE  cara: negacion` en el overlay. Las tres filas medidas quedan activas; pregunta de contenido y orden siguen en base. | Validación LSC de las dos filas abiertas. La cabeza de la negación sigue sin existir. |
| 2026-11-05 | **Regeneración con los GLB de Samuel** (`../signall-avatar`, `fc6f372`, 2026-11-04): 50 señas × 4 marcas faciales (`_afirmativo`, `_pregunta`, `_negacion`, `_orden`), 200 clips, sin clip base; los huesos son idénticos entre variantes y solo cambian los pesos. | `tools/pack_avatars.py` (SUFFIXES, referencia de pose `0029_afirmativo`, manifiesto con 4 variantes), `tools/split_signs.py` (indentación del `*/` generado), `app/src/main/assets/web/models/juan.glb` (30,32 MB) y `pauline.glb` (27,85 MB), `models/avatars.json`, `manifest.json`, `SignsCatalog.kt`, `app.js` (`FACE_MARKER_SUFFIXES`, `FACE_VARIANT_BY_TYPE`, `DEFAULT_FACE_VARIANT`, `embeddedClipsFor`, `pickVariant`), specs `avatar`/`pipeline`/`ui`, `openspec/config.yaml`, `AGENTS.md`, `docs/SISTEMA.md`, `tools/README.md`, `tools/measure_face_markers.py` (guarda). | `AFFIRMATIVE` → `_afirmativo`, `YES_NO_QUESTION` y `CONTENT_QUESTION` → `_pregunta`, `NEGATIVE` → `_negacion`, `IMPERATIVE` → `_orden`. Verificado en el Redmi Note 9S con el backend real: `Yo como.` → `0038_afirmativo`, `0007_afirmativo`; `No quiero ir.` → `0038_negacion`, `0037_negacion`; `Tu comes?` → `0039_pregunta`, `0007_pregunta`; `Como estas?` → `0026_pregunta`; `Firme el documento.` → `0038_orden`. | `measure_face_markers.py` sigue midiendo el layout viejo: adaptarlo si se quiere medir las cuatro caras nuevas. Validación LSC de las cuatro caras y de la decisión de compartir `_pregunta` entre los dos tipos de pregunta. |
| 2026-11-05 (seguimiento) | **Medición de las cuatro caras y ajustes de UI.** Se adaptó el script de medición al layout sin clip base (referencia `_afirmativo`) y se corrieron los dos avatares; se apagó la grilla del suelo por defecto y se sacaron las glosas del indicador superior. | `tools/measure_face_markers.py`; `docs/mediciones/marcadores-2026-11-05.md` y `.json` (nuevos); `Settings.kt` y `SettingsRepository.kt` (grilla); `MainScreenState.kt` (se eliminó el `caption`); specs `avatar` y `ui`; `AGENTS.md`; `tools/README.md`. | `_pregunta` sube las cejas 10,39 mm de pico, `_negacion` y `_orden` mueven la boca y bajan las cejas 5,34 mm; las tres se distinguen entre sí y las cuatro son pose fija. El indicador de la esquina ya no repite la glosa: la píldora sobre el avatar es la única superficie. La grilla arranca apagada y una instalación vieja se limpia con `grid_default_off`. | Validación LSC de las cuatro caras y de compartir `_pregunta` entre los dos tipos de pregunta. |
| 2026-11-12 | **Capa de marcadores no manuales y movimiento humano**, por pedido del owner (el avatar se veía robótico: cara fija por tipo de oración y cabeza casi quieta). Se midió que en los clips la cabeza se mueve 2°–4° donde el intérprete la mueve 10° o más, y se recuperó de los puntos de cara y cuerpo de LSC50 lo que hizo el intérprete en cada seña. | `tools/extract_nmm.py` y `assets/web/nmm.json` (nuevos); `assets/web/humanize.js` (nuevo); `app.js` (`setHumanize`, orden del render loop, overlay); `tools/pack_avatars.py` y `tools/approved_tracks.py` (conservar 11 morph targets de la boca sin romper la firma de las curvas aprobadas); `models/juan.glb` (28,10 MB), `models/pauline.glb` (27,97 MB), `models/avatars.json`, `manifest.json`; `tools/test_humanize.mjs`; spec `avatar`; `AGENTS.md`; `tools/README.md`. Evidencia en `../data/evidencias/avatar/humanizacion-2026-11-12/`. | DOC-9 cambia: la cara ya **no** es una pose fija por tipo (la seña suma la suya) y la negación ya tiene movimiento lateral de cabeza, que es lo que espera PR-AVT-05. Los 400 clips siguen idénticos a los aprobados (61.200 + 20.600 canales de hueso comparados valor a valor). Alineación clip–video: correlación de muñecas ≥ 0,83 en 47 de 50 señas; las más bajas son `0044` (0,58), `0034` (0,63) y `0015` (0,70). | **Validación LSC** de la tabla `PROSODY` (es una hipótesis). **Prueba en el teléfono**: no había dispositivo conectado; no se midió rendimiento ni se instaló. Revisar a ojo `0015`, `0034` y `0044`. RF-AVT-03 y PR-AVT-05 describen menos de lo que ahora hace la escena: decide el owner si se actualizan. Las cifras de PR-AVT-06 (tamaño) cambian con los GLB nuevos. |

### Qué significa «el backend decide cuándo» en el estado actual

El backend calcula una categoría gramatical con `GlossAnalyzer`; no se la pregunta al LLM. Los valores son `AFFIRMATIVE`, `YES_NO_QUESTION`, `CONTENT_QUESTION`, `NEGATIVE` e `IMPERATIVE`. El analizador usa señales gramaticales y una prioridad cuando detecta más de una. El frame enviado a Android incluye el tipo principal y las glosas.

`secondary_signals` no significa emoción: son otras categorías gramaticales detectadas cuando hay una colisión. Se guardan en la caché del backend, pero no se incluyen en el `segment` que recibe Android. No existe hoy un campo de emoción o de expresión facial, ni un clasificador de alegría, tristeza o sorpresa.

Por lo tanto, el backend puede decir «esta oración es una pregunta» o «es negativa»; la app necesita una tabla acordada que traduzca ese tipo a uno de los clips del avatar. Usar los sufijos `_alegria`, `_tristeza` o `_sorpresa` por su significado exigiría una señal de emoción que hoy no existe. Agregar esa clasificación sería una ampliación del backend y del contrato entre repositorios, no un cambio exclusivo del selector.

## Decisiones ya indicadas por el owner

- El avatar solo se puede cambiar en **reposo absoluto**. Mientras haya captura, una frase lista o enviada, traducción, cola o representación en curso, el selector no debe permitir el cambio. La recomendación del plan es habilitarlo únicamente en `Inactivo`, con micrófono y modo en vivo apagados y sin turno, cola ni repetición pendientes.
- Juan es el avatar actual y Pauline la nueva. El valor inicial para instalaciones nuevas será Juan. Las preferencias guardadas con las opciones antiguas se normalizarán a Juan, ya que esas opciones nunca cambiaron el modelo real.
- Se usarán **dos GLB completos**, uno por avatar. El alcance de los marcadores se había fijado como **rostro completo**, no solo las cejas; el **2026-10-26 el owner lo dejó fuera de alcance**: se siguen representando solo los clips base de las señas.
- El cambio de avatar se habilita en cualquier estado sin operación activa: `Inactivo`, `Desconectado` y `Error`. Es la lectura de la lista del owner, más amplia que la recomendación original de habilitarlo solo en `Inactivo`.
- Sigue abierta la correspondencia entre el tipo de oración y el clip que se debe reproducir. El owner consultará esa decisión; el plan conserva las alternativas en la sección siguiente.

## Plan de implementación propuesto

### 0. Alinear la documentación y cerrar la tabla de expresiones

1. Antes de implementar, proponer la actualización de RF-AVT-03 en `../signall-docs`: el alcance decidido es rostro completo, mientras v0.16 solo pide cejas. El owner gestiona los documentos de requisitos.
2. Proponer la actualización del SDD de Overleaf: el empaquetado decidido es dos GLB completos (Juan y Pauline), con 50 señas × seis variantes cada uno, no 50 GLB por seña. El owner debe aplicar los cambios en Overleaf.
3. La tabla rostro completo ↔ tipo de oración está **propuesta y medida** desde el 2026-11-04; falta la validación LSC y la aprobación del owner. PR-AVT-05 ya exige escribir lo esperado para cada tipo antes de probar, y `pruebas.md` todavía tiene el TODO.

#### Opciones para elegir cuándo usar los clips

El 2026-11-04 el owner eligió la **A** y descartó la **B**. La **C** no es una opción
aparte: es la forma prudente de arrancar la A (activar solo las filas validadas y dejar el
base en las demás).

| Opción | Qué haría | Estado |
|---|---|---|
| **A. Tabla gramatical revisada** | Mantener los cinco tipos que ya entrega el backend y acordar para cada tipo qué variante facial usar. No cambia el backend ni el WebSocket. La propuesta medida está en «Medición y propuesta de marcadores (2026-11-04)». | **Elegida por el owner el 2026-11-04.** Falta la validación LSC de las filas de pregunta de contenido y orden. |
| **B. Agregar clasificación emocional** | Definir cómo reconocer alegría, tristeza y sorpresa y enviar esa señal con un campo nuevo del segmento. | **Descartada el 2026-11-04:** la clasificación se intentó y no se logró, y el backend no manda ninguna señal emocional. |
| **C. Habilitar primero solo las categorías validadas** | Activar solo las variantes con correspondencia aprobada; usar el clip base en las demás. | **Corte mínimo propuesto dentro de la A**: tres filas medidas y dos en base hasta que LSC defina. |

### 1. Preparar y verificar los assets

- Conservar los dos GLB originales como entradas inmutables y registrar hash, tamaño, cantidad de clips, articulaciones y canales faciales en un inventario reproducible.
- Confirmar que las 50 señas y las seis variantes estén presentes en ambos archivos; validar que los nombres de nodo de cada clip puedan resolverse en su modelo.
- Definir el mecanismo de empaquetado para **dos modelos completos**. `tools/pack_signall.py` produce hoy un único avatar y no se debe asumir que puede procesar estos dos archivos sin cambios.
- No editar manualmente los binarios ni regenerarlos desde la fuente del avatar actual: los archivos nuevos son una entrada distinta y deben conservarse como tal.

### 2. Hacer funcional el selector existente

- Reemplazar las opciones actuales por Juan y Pauline, conservando el desplegable existente y sus accesibilidades.
- Persistir la selección y cargar ese avatar al iniciar. Normalizar los valores antiguos al valor Juan.
- Enviar el identificador de avatar a la escena mediante una vía aditiva del puente Kotlin–JavaScript; no cambiar nombres o comandos existentes.
- Deshabilitar el selector fuera de reposo absoluto. Mantener el modelo anterior hasta confirmar el criterio de reposo y evitar que se cambie de avatar durante una operación activa.

### 3. Cargar el modelo seleccionado en la escena

- Sustituir la URL única por un catálogo local de rutas seguras para Juan y Pauline. Los archivos permanecen dentro de la app; no se descargan del backend.
- Cargar un único modelo en memoria gráfica a la vez. Al cambiar en reposo, detener y limpiar el modelo anterior, construir el mezclador y el índice del nuevo, reaplicar velocidad, tema, cámara y ajustes, y señalar el fin de carga a Compose.
- Conservar el contrato de las rutas `signs/<id>.glb` como identificadores de seña. Modificar el resolutor para asociar cada identificador a los clips `0000`–`0049` del avatar seleccionado y a la variante que autorice la tabla de marcadores.
- Mostrar un estado de carga durante el cambio y no habilitar el envío de una frase hasta que el nuevo avatar esté listo. Mantener `avatarReady` como señal de escena lista al inicio; definir una señal aditiva para las cargas posteriores.

### 4. Activar los marcadores que se aprueben

- En la primera versión, usar solo el tipo gramatical ya enviado, salvo que el owner autorice ampliar el contrato.
- Aplicar el mapeo aprobado por oración a todos los clips de sus glosas; si falta una variante para una seña, usar el clip base y registrar el caso, no inventar otra expresión.
- Confirmar en pantalla qué cambia por variante y que las manos y el cuerpo mantengan el gesto previsto. La inspección de los GLB encontró los tracks articulares iguales entre base y variantes, pero la legibilidad lingüística requiere validación con personas que conocen LSC.
- No usar `alegria`, `tristeza` o `sorpresa` por inferencia del texto mientras no exista una clasificación emocional validada.

### 5. Medir y verificar antes de cerrar

- Antes de la función, agregar instrumentación local para tiempo de carga por avatar, tiempo de cambio, primera seña y resultado de resolución de clips. Los valores se reportarán con su modelo, dispositivo y build; no se asumirán umbrales que los documentos no fijan.
- Probar las 50 señas en Juan y Pauline; probar los seis nombres de clip donde el mapeo los permita; revisar errores de pistas no resueltas y limpieza al alternar varias veces.
- Ejecutar PR-AVT-05 con la tabla aprobada, dos frases por tipo según el plan actual, grabación de pantalla y resultado por tipo. Validar comprensión/legibilidad con el bloque de pruebas con personas, no solo con una inspección del GLB.
- Medir tamaño final del APK y carga en un dispositivo real. Comparar con el modelo actual de 12.978.476 bytes; los dos nuevos GLB suman 73.144.928 bytes en crudo. No concluir que el cambio es aceptable hasta observar tamaño del paquete, memoria y tiempos reales.
- Verificar que la app compile, se instale y que el selector solo se habilite en el estado acordado.

### 6. Actualizar la documentación después de resolver las divergencias

- Actualizar los specs locales `openspec/specs/avatar/spec.md` y `openspec/specs/ui/spec.md`, que hoy describen un avatar único y un selector UI-only con nombres anteriores.
- Proponer al owner las correcciones correspondientes en `../signall-docs`: RF-AVT-03, la descripción de los GLB, la tabla de marcadores y PR-AVT-05. El owner edita los requisitos y diagramas.
- El Overleaf es de solo lectura para esta sesión. Si el SRS o el SDD necesitan ajustes, se informa al owner; no se escriben desde el agente.
- Si se incorpora una señal emocional al backend, revisar y actualizar el contrato en ambos repositorios antes de implementar el cambio.

## Situación después de las respuestas del owner

- Resueltos: dos GLB completos; rostro completo; selector bloqueado hasta el reposo absoluto; Juan como predeterminado.
- **Resuelto el 2026-11-04:** se trabaja la **opción A** (tabla tipo gramatical → variante). La **B** queda descartada porque la clasificación emocional se intentó y no se logró, y el backend no manda ninguna señal emocional. Los marcadores son gramaticales, no emocionales.
- **Actualizado el 2026-11-05:** los GLB de Samuel (`../signall-avatar`, `fc6f372`) traen las cuatro marcas gramaticales y ningún clip base. `FACE_VARIANT_BY_TYPE` quedó: afirmación → `_afirmativo`, pregunta sí/no y pregunta de contenido → `_pregunta`, negación → `_negacion`, orden → `_orden`. Verificado en el Redmi Note 9S con el backend real para los cinco tipos. Queda la **validación LSC** de las cuatro caras y de compartir `_pregunta` entre los dos tipos de pregunta.
- `../signall-docs` se actualizó mediante `git pull --ff-only` el 2026-10-24. `main` ya estaba al día, sin cambios nuevos: commit `dc8d49c79285489815164adaf8a1fa7ef6e83b24`.

## Fuentes técnicas y procedencia

- App en `main`, commit `1c027f002af29c5bc67a2a67671546bc704d66d6`: `Settings.kt`, `SettingsRepository.kt`, `CharacterPicker.kt`, `MainScreenState.kt`, `SignAllWebViewController.kt`, `SignAllCommands.kt`, `assets/web/app.js`.
- Backend, commit `78006a523875adaa11c72831e4997310d47191ab`: `app/domain/models.py`, `app/nlp/gloss_analyzer.py`, `app/controller/controller.py`, `docs/android-integration.md` y `docs/sdd.md`.
- Backend leído el 2026-11-04 (HEAD `c8aa516`, con cambios locales del owner sin commitear): `app/domain/models.py`, `app/nlp/gloss_analyzer.py`, `app/nlp/nlp_service_impl.py`, `app/orchestrator/orchestrator.py` y `app/controller/controller.py`. App leída en `3cf2be2`.
- Documentación consolidada, `main` actualizado por pull sin cambios, commit `dc8d49c79285489815164adaf8a1fa7ef6e83b24`: `documentos/requerimientos/SignAll_Requerimientos_Consolidados_v0.16.md`, `modificaciones/requerimientos.md` y `plan-pruebas/01-diseno/avatar/`.
- Tesis consultada el 2026-10-24: Overleaf SRS (`main.tex`, versión 524), SDD (`main.tex`, versión 212) y Memoria (`sections/descripcionGeneral.tex`, versión 98). Las versiones consultadas no indican por sí mismas la fecha en que el owner editó cada archivo.
- Reglas de trabajo aplicadas: `AGENTS.md`, y los skills `tres-fuentes-de-verdad`, `contrato-websocket`, `overleaf-lectura`, `compose-best-practices` y `sesion-openspec`.
- Repo hermano `../signall-avatar`, rama `dev`, commit `cf014ef` (2026-10-26): `models/Juan.glb` (37.988.016 bytes, sha256 `bd77c77c…`), `models/Pauline.glb` (35.272.616 bytes, sha256 `316bfbff…`), su `models/README.md` y `openspec/specs/animacion-cascadeur/spec.md`. De ahí salieron los GLB del 2026-10-27.
