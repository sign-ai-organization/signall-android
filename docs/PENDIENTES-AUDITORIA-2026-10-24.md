# Pendientes de la auditoría del 2026-10-24

Trabajo de una sesión siguiente. **No reescribir de cero**: corregir solo las frases que no
coinciden con el código o con el GLB. Cada número nuevo lleva su origen; si no se midió, se
dice que no se midió. **Las líneas son orientativas**: buscar la frase exacta antes de
editar, porque pueden haberse corrido.

## Reglas

- **El código manda.** Si un documento contradice al código, se corrige el documento y se
  reporta la diferencia. No se elige por cuenta propia ni se inventa un número.
- **A6 no se implementa.** Los marcadores faciales por tipo de frase siguen siendo decisión
  del owner. Solo se corrigen los datos: los huesos de cara existen y son joints, pero los
  clips actuales los dejan casi quietos.
- **`../signall-docs` no se edita.** Las correcciones del plan de pruebas se proponen en la
  sesión y se le avisan al owner.
- **`../signall-backend` no se commitea.** Su documentación la edita el owner; la corrección
  exacta de `docs/android-integration.md` se propone, no se aplica.
- **Overleaf es solo lectura.** Avisar antes de abrirlo porque el MCP puede lanzar una
  ventana de Chromium con perfil aislado. No escribir ni compilar en Overleaf.
- **No tocar** `Juan_final.glb` ni `Pauline_final.glb` (sin seguimiento, son del owner).
- Al terminar: `./gradlew build`, `./gradlew installDebug` si hay device (si no, decirlo),
  sesión en `openspec/sessions/` con su línea en el índice, y commit directo a `main` solo
  con los archivos de este repo.

## Datos verificados que hay que usar

Medidos el 2026-10-24 leyendo el JSON y los accessors de
`app/src/main/assets/web/models/avatar.glb`:

- Archivo: **12 978 476 bytes (12,98 MB)**, 50 clips `0000`..`0049`, 1 skin con **102
  joints**, 11 mallas.
- Morph targets: **7**, no cero. Son `Male`, `LIVE_KEY_PERMANENT`, `Body Proportions` ×4 y
  `eyeball_size`. Ninguno es de expresión facial. Los 5 canales de pesos por clip valen 1,0
  constante.
- Canales por clip: **311**. 306 son TRS de los 102 huesos y 5 son pesos de mallas
  (`HG_Body_facial`, `HG_Chinos_Male.002`, `HG_Dress_Shirt_Collar_Male.002`,
  `HG_Leather_Dress_Shoe_Male.002`, `HG_Sweater_1_Male.002`).
- Raíz del armature: **`HG_Branden`** en el archivo vigente. **No es joint y no está
  animada**: ningún canal de los 50 clips la toca. `HG_Branden.001` era el export viejo de
  `../material-3d/GLB Senas/`.
- Cara: los nodos `brow_*`, `eye_blink_open_*`, `eye_squint_*`, `eyeball*`, `jaw*` y
  `mouth_*` **sí están entre los 102 joints**. Los clips los dejan casi quietos: el mayor
  rango de un componente del cuaternión de rotación en los 50 clips es **0,0012** (`jaw`,
  clip `0015`), contra **1,996** del mayor hueso del cuerpo (`thigh.L`, clip `0034`).
- Las 50 glosas de LSC50 tienen clip desde el 2026-10-22.
- `Settings.demoSignFallback` está **apagado por defecto**. Con una glosa sin clip: si está
  prendido se representa con la seña de reserva; si está apagado se saltea y se avisa "Seña
  no disponible" por glosa. En los dos casos **no se toca la red**.
- `setQueue` solo reemplaza la cola; el arranque lo hacen `play()` o `playFrom()`.
- `setSpeed(v)` recibe `0..1`, `app.js` lo escala por 2 y lo limita a `0.1..3`; un `0` cae
  en `0.5` por el operador lógico del JS.

## 1. `openspec/specs/avatar/spec.md`

- **Línea 108.** Dice que los exports usan `DEF-` en los huesos largos y de la cara, y la
  línea 122 dice que no existe ningún `DEF-`. Dejar solo lo verdadero: puntos en la cadena
  del brazo, dedos y palma; **cero huesos `DEF-`**.
- **Línea 119.** `HG_Branden.001` → `HG_Branden` en el archivo vigente; aclarar que el
  `.001` era el export viejo y que la raíz no está animada.
- **Línea 139.** "cero morph targets" → **7**, ninguno de expresión facial. "No están driven
  por nada" → la escena no los maneja y los clips los dejan casi quietos; los huesos de cara
  **sí son joints**.
- **Líneas 148-150.** Dicen que los tracks animan la raíz `HG_Branden.001`. En el archivo
  vigente no la anima ningún canal. El motivo por el que `app.js` cuenta nodos y no huesos
  son los 5 canales de pesos que apuntan a mallas, no la raíz.
- **Línea 157.** "Los 46 clips" → **50 clips**. Ojo: las estadísticas de 5,9 cm de mediana y
  79 % de pares continuos se midieron sobre los 46 clips del layout viejo. Antes de
  reescribirlas, medirlas sobre los 50 clips del GLB vigente; si no se miden, etiquetarlas
  como históricas y no como estado actual.
- **Línea 208.** "No hay morph targets ni blendshapes" → hay **7 morph targets**, ninguno de
  expresión facial; no hay blendshapes de cara.
- **Línea 93.** La pose de descanso ya no se hornea "al final de `split_signs.py`": el
  pipeline vigente es `tools/pack_signall.py`, que usa las funciones de `rest_pose.py` y lee
  el primer keyframe del clip `0029` del propio archivo. `split_signs.py` queda para el
  layout viejo.
- Revisar también la línea 11 ("102 huesos"): son **102 articulaciones de skin**.

## 2. `openspec/specs/ui/spec.md`

- **Línea 279 (STA-9).** "las 18 glosas de LSC50 sin animación exportada (`0032`–`0049`)
  son las que el catálogo anuncia pero el APK no tiene" → hoy las 50 tienen clip. El caso
  que queda es una glosa futura sin clip; redactarlo en futuro.
- **Línea 344 (GLS-5).** "el fallback solo alcanzaba a las 18 señas sin animación" es
  histórico. Hoy no alcanza a ninguna; el ajuste se conserva para una glosa futura sin
  clip.
- **Línea 346 (GLS-10).** "Una glosa de las 18 ... Va directo al sign por defecto sin tocar
  la red" → hoy no hay ninguna. Si aparece una futura: con `demoSignFallback` prendido usa
  la seña de reserva; apagado se saltea y avisa "Seña no disponible". En los dos casos sin
  red.
- **Línea 368 (BRG-12).** "GLB de 10 MB" → **12,98 MB**. Además, `avatarReady` ya no es "el
  personaje parseado y en escena": se manda cuando los 50 clips están indexados, la seña de
  arranque resuelta, los shaders compilados y un frame pintado con el avatar adentro.

## 3. `docs/CONTRASTE-APPvsDOCUMENTOS.md`

- **Línea 280 (A6).** Dice "no tiene morph targets" y "`avatar.glb` viene con 0
  animaciones", ambas falsas desde el 2026-10-22. Corregir: 7 morph targets (ninguno
  facial), 50 clips, huesos de cara que son joints y clips que los dejan casi quietos. La
  conclusión de A6 no cambia: implementar marcadores es trabajo de rig/assets + escena y
  decisión del owner.
- **Línea 282 (B4/B5).** "¿Quién exporta las 18 señas que faltan?" quedó resuelto: las 50
  están en el GLB único desde el 2026-10-22. Lo que falta es validar el contenido de
  `0032`–`0049` con el owner/director, no exportarlas.
- **§5, líneas 317-326.** "Hoy hay 32" → **50** desde el 2026-10-22. Las 18 existen. Hoy
  ninguna glosa llega con una ruta inexistente; `isOwnAssetPath()` protege el caso futuro.
- **Línea 720.** "las 18 señas que faltan" → ya no faltan; el pendiente es validar el
  contenido.
- **Línea 729.** "el catálogo sí anuncia las 18, lo que no hay es el clip" → hoy las 50
  tienen clip; el aviso por glosa sigue existiendo para el caso futuro.

## 4. `docs/PLAN-MEDICION.md`

- **Líneas 68 y 258.** "`avatar.glb` (10 MB)" → **12,98 MB**. No cambiar el presupuesto ni
  el timeout si no se está midiendo otra cosa.

## 5. Comentarios de `MainScreenState.kt`

Son comentarios, no comportamiento. Corregirlos y volver a compilar:

- **Línea 158.** "the 18 LSC50 signs still unexported" → "an LSC50 sign whose clip is
  missing (none today)".
- **Línea 851.** "las 18 señas de LSC50 sin animación exportada (0032–0049)" → "una glosa
  sin clip (hoy ninguna)".
- **Líneas 1528-1529.** "the 18 LSC50 signs whose animation is not exported yet
  (0032–0049)" → "a sign whose clip is missing (none today)".

## 6. Propuestas para documentos que no se editan desde acá

Anotar la corrección exacta en la sesión y avisarle al owner. No aplicar.

### `../signall-docs/plan-pruebas`

- `00-bases/trazabilidad.md:140`: "hoy hay 32 de las 50" → "hoy hay 50 de las 50 (2026-10-22)".
- `01-diseno/avatar/general.md:32`: "hoy 32 de las 50" → "50 de las 50".
- `01-diseno/avatar/pruebas.md:59`: "lleva 32 de las 50 animaciones" → "lleva las 50".

### `../signall-backend/docs/android-integration.md`

- Ejemplo de `segment` (líneas 211 y 213): `glb_path` `/static/glb/hola.glb` y
  `/static/glb/como.glb` → `signs/0000_0000_0000.glb` y `signs/0044_0000_0000.glb` (o el
  path real del catálogo; verificarlo antes).
- §7 (líneas 393-402): "Falta acordar el formato de `glb_path`" y "El catálogo tiene 3 de
  las 50" están viejos. El seed real (`tools/seed_catalog.py`) escribe `signs/<id>.glb`
  para las 50 y el catálogo se sembró con las 50.

### Overleaf

Si el owner pide contrastar A6 o el "50" contra el SRS/SDD, avisar antes de abrir y leer
solo. No escribir. Reportar la cita con su fecha (`main.tex:...`) y no corregir el `.tex`.

## 7. Verificación de cierre

- `./gradlew build` y `./gradlew installDebug` (si hay device).
- `git diff --check`; si se toca `config.yaml`, validar el YAML.
- Confirmar que `openspec/sessions/README.md` y los archivos de `openspec/sessions/`
  coinciden: misma cantidad, ninguno listado que no exista.
- Commit directo a `main`, solo los archivos de este repo.
