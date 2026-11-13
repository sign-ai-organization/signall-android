# Auditoría de `specs/pipeline/spec.md` contra el código

**Fecha:** 2026-10-24 · **Rama:** `main`

## Qué llegó

El spec del pipeline se había actualizado a medias el 2026-10-22 (un solo `avatar.glb` con
las 50 señas). El pedido fue auditar cada afirmación contra el código y corregir solo lo que
no coincidiera, sin reescribir el archivo. Había cuatro divergencias ya detectadas: el mixer
reproducía `signs/<id>.glb`, el GLB tenía "cero morph targets", el archivo pesaba "10 MB" y
la tabla del puente no listaba `stop()`.

La auditoría encontró las cuatro y varias más: el nombre de la raíz del armature, la
condición de `flushSegments()`, el "no encadena" de la app, la descripción de la cara (los
huesos sí son joints y los clips los dejan casi quietos), el fallback de una glosa sin clip,
el `setSpeed` y el `setQueue` del puente, los mensajes `signFailed`/`progress` que faltaban,
el "sin red" del CDN y el layout viejo que ya no tiene `signs/`.

## Qué se corrigió

Todo en `openspec/specs/pipeline/spec.md`:

| Afirmación vieja | Lo que hace el código |
|---|---|
| `THREE.AnimationMixer` reproduce `signs/<id>.glb` | reproduce el clip `<id>` embebido en `models/avatar.glb`, resuelto por nombre con `embeddedClipsFor` (`app.js:346-358`) |
| `HG_Branden.001` es la raíz y está animada | en el archivo vigente la raíz es `HG_Branden` y **no está animada**; el `.001` era el export viejo de `GLB Senas` |
| el GLB del APK tiene cero morph targets | tiene **7**, ninguno de expresión facial |
| `flushSegments()` cuando `size >= total` | entrega lo que haya en cada `segment` (D-1, `MainScreenState.kt:818-839`) |
| la app "no encadena" y arma la cola al final | encadena cada oración con `setQueue` + `play()` y `appendQueue` |
| la app pide la primera oración y después recibe todas | el backend manda todos los `segment` en orden; la app los entrega a medida que llegan |
| el GLB del APK pesa 10 MB | **12,98 MB** (`12 978 476` bytes) |
| el puente Kotlin→JS no lista `stop()` | existe como `SignAiCommands.STOP` (`SignAiCommands.kt:85`) y `app.js:935` |
| la cara no son articulaciones de skin y "nada los mueve" | los huesos de cara **sí** están entre los 102 joints; los clips los dejan casi quietos y no hay morphs de expresión |
| una glosa sin clip "va al sign de reserva" | solo si *Seña de prueba si falta* está prendido; apagado (default) se saltea y avisa "Seña no disponible" |

También se completó la tabla JS→Kotlin con `signFailed:<idx>` y `progress:<0..100>`, se
aclaró que los assets no piden red pero Three.js sí viene del CDN, y se documentó que el
layout viejo (`split_signs.py` + `signs/`) sigue en el repo pero `assets/web/signs/` se borró
el 2026-10-22.

Se aprovechó para alinear `openspec/config.yaml`, que tenía las mismas afirmaciones viejas
(assets por seña con `split_signs.py`, raíz `HG_Branden.001` animada, cero morph targets,
"32 de las 50", 10 MB, `stop` ausente). La advertencia de `AGENTS.md` que decía que el spec y
el config describían el pipeline de landmarks se reemplazó por la regla general: si un spec
contradice al código, gana el código y la diferencia se reporta.

## Qué se midió

Todo leyendo el JSON del GLB y los accessors con Python, el 2026-10-24:

- `models/avatar.glb`: **12 978 476 bytes** (12,98 MB), 50 clips `0000`..`0049`, 1 skin con
  **102 joints**, 11 mallas.
- Morph targets que quedan: **7** (`Male`, `LIVE_KEY_PERMANENT`, `Body Proportions` ×4,
  `eyeball_size`). Ninguno de cara. Los 5 canales de pesos por clip valen 1,0 constante.
- Canales por clip: **311**; 306 son TRS de los 102 huesos y 5 son pesos de mallas.
- La raíz `HG_Branden` no aparece en ningún canal de los 50 clips.
- Cara: el mayor rango de un componente del cuaternión de rotación de un nodo de cara en
  los 50 clips es **0,0012** (`jaw`, clip `0015`), contra **1,996** del mayor hueso del
  cuerpo (`thigh.L`, clip `0034`).
- Origen `GLB SignALL (GLB por Sena).glb`: **27 159 508 bytes** (27,16 MB), misma raíz
  `HG_Branden` y 102 joints. El export viejo `GLB Senas/0000 - GRACIAS.glb` tiene
  `HG_Branden.001`.
- Fuente del contrato del WebSocket: `../signall-backend/docs/android-integration.md` §3-4
  (`text`, `end`, `segment`, `done`, `transcript`; la voz no se traduce) y
  `../signall-backend/AGENTS.md`.

## Cómo se verificó

- `./gradlew build`: **BUILD SUCCESSFUL** en 19 s (99 tareas, 9 ejecutadas).
- `./gradlew installDebug`: **no se pudo**. `adb devices` estaba vacío y Gradle falló con
  `DeviceException: No connected devices!`. Queda pendiente instalarlo cuando haya un
  device conectado.

## Qué se decidió

- **El código manda.** Donde el spec no coincidía, se corrigió el spec; no se tocó código.
- **No se agregó ninguna feature.** A6 (marcadores faciales por tipo de frase) sigue siendo
  decisión del owner. Lo único que cambió es el dato: los huesos de cara existen y son
  joints, pero los clips actuales no los mueven.
- **No se abrió Overleaf.** La cookie puede haber vencido y el MCP abre una ventana de
  Chromium; la auditoría se podía cerrar con código + `../signall-docs`, y el SRS no se
  necesitaba para corregir afirmaciones sobre el GLB. Queda pendiente si el owner quiere
  contrastar A6 contra el `.tex`.
- **No se tocaron los hermanos.** `../signall-docs` y `../signall-backend` solo se leyeron.

## Qué quedó pendiente

Divergencias detectadas y **no corregidas** porque no son el archivo de esta sesión (van al
owner o a otra sesión):

- `openspec/specs/avatar/spec.md`: sigue diciendo "cero morph targets" (línea 139), que la
  raíz `HG_Branden.001` está animada (148), "46 clips" (157) y "no hay morph targets" (208);
  también atribuye la pose de descanso a `split_signs.py` (93).
- `openspec/specs/ui/spec.md`: GLS-5 sigue hablando de las 18 señas sin animación (344);
  GLS-10 dice que una glosa sin clip "va directo al sign por defecto", que solo pasa con el
  ajuste prendido (346); BRG-12 sigue con "GLB de 10 MB" (368).
- `docs/CONTRASTE-APPvsDOCUMENTOS.md:280` (A6): dice "no tiene morph targets" y "0
  animaciones", ambas falsas desde el 2026-10-22.
- `app/src/main/java/com/signall/app/ui/state/MainScreenState.kt:851` y `:1529`:
  comentarios que siguen hablando de "las 18 señas sin animación exportada (0032-0049)".
- `../signall-docs/plan-pruebas/00-bases/trazabilidad.md:140`,
  `01-diseno/avatar/general.md:32` y `01-diseno/avatar/pruebas.md:59`: "32 de las 50".
- `../signall-backend/docs/android-integration.md` §7: dice que el catálogo guarda
  `/static/glb/*.glb` y que tiene 3 de las 50 glosas. El seed real
  (`tools/seed_catalog.py`) escribe `signs/<id>.glb` para las 50.
- `docs/PLAN-MEDICION.md:68` y `:258`: "avatar.glb (10 MB)".

## Investigación posterior: Pauline, Juan y marcadores faciales

**Pedido:** investigar y escribir el plan de uso de `Juan_final.glb` y `Pauline_final.glb`, sin implementar.

- Se creó [`docs/PLAN-AVATARES-Y-MARCADORES.md`](../../docs/PLAN-AVATARES-Y-MARCADORES.md), con el plan, el contraste documental, la respuesta sobre el tipo de señal del backend y las preguntas que bloquean una implementación alineada.
- Leídos los dos GLB sin modificarlos: Juan 37.872.312 bytes y Pauline 35.272.616 bytes; cada uno contiene 300 clips (50 señas × base + cinco variantes), 102 articulaciones y canales de pesos faciales. Entre las cinco variantes y el clip base los canales de huesos coinciden en las 50 señas de los dos archivos; los de pesos cambian. Hashes SHA-256 y contexto en el plan.
- Confirmado en `../signall-backend` (`78006a523875adaa11c72831e4997310d47191ab`): `GlossAnalyzer` produce cinco tipos gramaticales; no clasifica emociones. `secondary_signals` son colisiones gramaticales, no emociones, y no viajan en el frame `segment`. El tipo principal ya llega a Android y la app ya lo pasa a JavaScript.
- Contraste: `../signall-docs` v0.16 (2026-10-21) limita RF-AVT-03 a cejas y PR-AVT-05 exige una tabla esperada antes de probar. SRS Overleaf `main.tex` v524 (consulta 2026-10-24) habla de marcadores faciales según tipo gramatical. SDD Overleaf `main.tex` v212 (consulta 2026-10-24) aún describe 50 GLB por seña, mientras la app usa uno y los dos nuevos son archivos completos por avatar. No se resolvió por cuenta propia.
- **Decisión del owner, 2026-10-24:** no permitir cambiar de avatar durante una operación; el selector debe quedar habilitado solo en reposo absoluto.
- **Decisiones del owner, 2026-10-24:** usar dos GLB completos (Juan y Pauline) y todo el rostro, no solo las cejas. Esto requiere proponer actualizar `RF-AVT-03` de `../signall-docs` y el empaquetado descrito por el SDD de Overleaf; no se escribieron esos documentos.
- **Pendiente del owner:** elegir entre las tres opciones del plan para relacionar tipo gramatical y variante facial. El owner consultará esa decisión. No añadir una clasificación emocional ni un campo al WebSocket mientras siga abierta.
- Se ejecutó `git pull --ff-only` en `../signall-docs`: `main` ya estaba al día en `dc8d49c79285489815164adaf8a1fa7ef6e83b24`; no hubo cambios nuevos que alteren la investigación.
- **Instrucción del owner:** `docs/PLAN-AVATARES-Y-MARCADORES.md` es un documento vivo; cualquier persona que trabaje en la iniciativa lo actualiza en cada paso. Se agregaron la matriz de diferencias DOC-1–DOC-8 y el registro de cambios para hacerlo verificable.
- No se modificó código, GLB, backend, documentos de `../signall-docs` ni Overleaf.
