# Corrección de las afirmaciones viejas sobre el GLB y la cara

**Fecha:** 2026-10-26 · **Rama:** `main`

## Qué llegó

Cerrar [`docs/historia/PENDIENTES-AUDITORIA-2026-10-24.md`](../../docs/historia/PENDIENTES-AUDITORIA-2026-10-24.md):
corregir, sin reescribir de cero, las frases que habían quedado viejas en
`openspec/specs/avatar/spec.md`, `openspec/specs/ui/spec.md`,
`docs/historia/CONTRASTE-APPvsDOCUMENTOS.md`, `docs/PLAN-MEDICION.md` y los comentarios de
`MainScreenState.kt`. Cada dato nuevo tenía que verificarse contra el GLB y el código;
ninguno se copió de la sesión del 2026-10-24. A6 no se implementa, los hermanos no se
editan y Overleaf no se abre.

## Qué se midió, y de dónde sale

Todo leyendo el JSON y los accessors de
`app/src/main/assets/web/models/avatar.glb` con `tools/glb_pose.py`, el **2026-10-26**:

- **12 978 476 bytes (12,98 MB)**, 50 clips `0000`..`0049`, 1 skin con **102 joints**,
  11 mallas, 114 nodos.
- **7 morph targets**: `Male`, `LIVE_KEY_PERMANENT`, `Body Proportions` ×4 y
  `eyeball_size`. Ninguno de expresión facial. Los 5 canales de pesos por clip valen
  **1,0 constante** en los 50 clips.
- **311 canales por clip**: 306 TRS + 5 de pesos, que apuntan a mallas
  (`HG_Body_facial`, `HG_Chinos_Male.002`, `HG_Dress_Shirt_Collar_Male.002`,
  `HG_Leather_Dress_Shoe_Male.002`, `HG_Sweater_1_Male.002`).
- Raíz **`HG_Branden`** (índice 113): **no es joint** y **ningún canal de los 50 clips
  la toca**. `HG_Branden.001` es la raíz del export viejo `GLB Senas/`.
- **31 nodos de cara, los 31 joints**: `eyeball*`, `brow_*`, `eye_blink_open_*`,
  `eye_squint_*`, `jaw*`, `mouth_*`.
- Rango máximo de un componente del cuaternión de rotación: **0,0012** en `jaw` (clip
  `0015`) para la cara, contra **1,996** en `thigh.L` (clip `0034`) para el cuerpo.
- Cero huesos con prefijo `DEF-` en el archivo vigente y en `GLB Senas/0000 - GRACIAS.glb`.
- Los 50 ids de `SignsCatalog.kt` coinciden con los 50 clips.

**Continuidad entre señas, remedida sobre los 50 clips** (la publicada era de los 46 del
layout viejo): con la posición de `hand.L`, distancia entre el final de un clip y el
inicio de cualquier otro, sobre los 2450 pares ordenados → **mediana 5,6 cm** y
**81 % de los pares por debajo de 10 cm**. El cluster: mano izquierda en
`(+31,5, +91,0, +17,6) cm`, desviación estándar 3,2–4,3 cm; derecha en
`(−12,1, +91,8, +26,3) cm`, 2,4–6,0 cm.

## Qué se corrigió

| Archivo | Afirmación vieja | Ahora |
|---|---|---|
| `specs/avatar/spec.md:11` | "102 huesos" | 102 articulaciones de skin |
| `specs/avatar/spec.md:93` | la pose se hornea "al final de `split_signs.py`", con dos comandos de ejemplo | pipeline `pack_signall.py`, que lee el clip `0029` del propio archivo. Los dos comandos documentados (`rest_pose.py --dry-run`, `preview_pose.py --sheet`) **se probaron y hoy fallan** porque esperan `assets/web/signs/` |
| `specs/avatar/spec.md:108` | "`DEF-` en los huesos largos y de la cara" | puntos en brazo, dedos y palma; cero `DEF-` |
| `specs/avatar/spec.md:119` | raíz `HG_Branden.001` | `HG_Branden`; el `.001` era el export viejo y la raíz no está animada |
| `specs/avatar/spec.md:129` | los huesos de cara "no son articulaciones de skin" | los 31 sí son joints |
| `specs/avatar/spec.md:139` | cero morph targets, "no están driven por nada", expresión "horneada en el clip" | 7 morphs (ninguno facial), la escena no los maneja, y los clips dejan la cara casi quieta (0,0012 contra 1,996) |
| `specs/avatar/spec.md:148` | los tracks animan la raíz; por eso `app.js` cuenta nodos | la raíz no se anima; el motivo son los 5 canales de pesos a mallas |
| `specs/avatar/spec.md:157` | 46 clips, 5,9 cm, 79 % | 50 clips, 5,6 cm y 81 %, medidos sobre los 50 |
| `specs/avatar/spec.md:208` | "no hay morph targets ni blendshapes" | 7 morphs de cuerpo/ropa/ojo, ninguno facial, y no hay blendshapes de cara |
| `specs/ui/spec.md:279` | las 18 glosas sin animación "son las que el APK no tiene" | hoy ninguna; el caso queda redactado a futuro |
| `specs/ui/spec.md:344` | el fallback alcanzaba a las 18 | histórico; hoy no alcanza a ninguna, y apagado saltea y avisa |
| `specs/ui/spec.md:346` | una glosa sin clip "va directo al sign por defecto" | con el ajuste prendido usa la reserva; apagado se saltea y avisa; sin red en los dos casos |
| `specs/ui/spec.md:368` | GLB de 10 MB y `avatarReady` = "personaje en escena" | 12,98 MB y `avatarReady` = clips indexados + seña de arranque + shaders + frame pintado |
| `docs/historia/CONTRASTE-APPvsDOCUMENTOS.md` | A6 "sin morph targets / 0 animaciones"; B4/B5 sin resolver; §5 "hoy hay 32"; resumen y §9 #4 | A6 con los datos medidos y la conclusión igual; B4/B5 resuelto (validar contenido, no exportar); §5 con 50 desde el 2026-10-22 |
| `docs/PLAN-MEDICION.md` | "`avatar.glb` (10 MB)" en §2.2 y en el plan | 12,98 MB, sin tocar presupuesto ni timeout |
| `MainScreenState.kt` | cuatro comentarios con "las 18 señas sin animación (0032–0049)" | "una glosa sin clip (hoy ninguna)" |

## Lo que apareció y no estaba en la lista

Se corrigió porque es la misma afirmación vieja, en el mismo archivo o en la misma
sección:

- `MainScreenState.kt:871`: "el catálogo anuncia las 50 glosas pero solo 32 tienen
  export". Va en la misma función que el comentario de la línea 851.
- `specs/ui/spec.md` **GLS-11** y **GLS-12** decían que la app "arma la cola completa
  cuando llega el último" y "la cola se arma cuando `size >= total`", que contradice a
  GLS-13 y a `flushSegments()`.
- `specs/ui/spec.md` **BRG-11** decía 28 huesos faciales; son 31.
- `specs/avatar/spec.md` § Bugs conocidos: "la asimetría viene horneada en el clip" no
  se sostiene si los clips mueven la cara 0,0012.

**Se dejó sin tocar, para otra pasada (no estaba en el pedido):**

- `app.js:382-385` repite que los exports animan la raíz `HG_Branden001` y que por eso
  se cuentan nodos; el motivo real son los canales de pesos. `app.js:97` y `:640` siguen
  hablando de "las 32 señas" del layout viejo.
- `tools/rest_pose.py` (docstring) dice que lo llama `split_signs.py`, y
  `tools/seed_catalog.py` (docstring) dice "verificado contra los 32 archivos".
- `docs/PLAN-AVATARES-Y-MARCADORES.md` no se tocó: es un documento vivo de otra
  iniciativa y no contiene estas afirmaciones.

## Qué se decidió

- **El código manda.** Donde el documento no coincidía, se corrigió el documento; no se
  tocó comportamiento.
- **A6 sigue sin implementarse.** Los huesos de cara existen y son joints, pero los
  clips los dejan casi quietos. Implementar marcadores es rig + assets + escena, y
  decisión del owner.
- **No se abrió Overleaf.** No hizo falta para corregir afirmaciones sobre el GLB.
- **No se tocaron los hermanos.** Ni `../signall-docs` ni `../signall-backend`.

## Propuestas para documentos que no se editan desde acá

No se aplicaron; quedan para el owner.

**`../signall-docs/plan-pruebas`**

- `00-bases/trazabilidad.md:140` (RF-INT-HW-04): "hoy hay 32 de las 50" →
  "hoy hay 50 de las 50 (2026-10-22)".
- `00-bases/trazabilidad.md:141` (RF-CAT-01), no estaba en la lista: "hoy guarda rutas
  pensadas para un servidor de archivos que ya no existe" → el seed escribe
  `signs/NNNN_0000_0000.glb`, el mismo layout que resuelve la app sin red (verificado en
  `tools/seed_catalog.py:114`).
- `01-diseno/avatar/general.md:32`: "(hoy 32 de las 50)" → "(hoy 50 de las 50)".
- `01-diseno/avatar/pruebas.md:59`: "lleva 32 de las 50 animaciones" → "lleva las 50".

**`../signall-backend/docs/android-integration.md`**

- Líneas 219 y 221: `glb_path` `/static/glb/hola.glb` y `/static/glb/como.glb` →
  `signs/0030_0000_0000.glb` y `signs/0044_0000_0000.glb`. **Ojo:** el path real no es
  el que suponía el pendiente; sale de la Table 2 que usa `tools/seed_catalog.py`:
  `HOLA` es el índice 30 y `COMO` el 44 (`GRACIAS` es el 0).
- §7, "Falta acordar el formato de `glb_path`": ya está acordado y sembrado —
  `signs/NNNN_0000_0000.glb` para las 50.
- §7, "El catálogo tiene 3 de las 50 señas": el seed escribió las 50.

## Cómo se verificó

- `./gradlew build`: **BUILD SUCCESSFUL** en 1 m 15 s (99 tareas, 22 ejecutadas).
- `./gradlew installDebug`: **no se pudo**, `adb devices` no lista ningún device. Queda
  pendiente instalarlo cuando haya uno conectado.
- `python3 tools/pack_signall.py --dry-run`: corre y reporta **46 rotaciones pisadas** y
  los 7 morphs podados, con las manos de 0,524 a 0,215/0,227 m del eje. Es el comando que
  quedó documentado en el spec.
- `python3 tools/rest_pose.py --dry-run` y `python3 tools/preview_pose.py --sheet`: se
  probaron y **fallan**, porque las dos esperan `assets/web/signs/`, que se borró.
- `git diff --check`: sin errores. `openspec/config.yaml` parsea como YAML.
- `openspec/sessions/`: **30 sesiones** además del `README.md`, y el índice tiene esas
  30 líneas, todas con archivo real.

## Qué quedó pendiente

- Instalar en el device cuando haya uno.
- **A6**: decidir si los marcadores faciales se implementan o se sacan del SRS. Para
  implementarlos hace falta la correspondencia tipo de frase → clip facial, que sigue
  abierta en [`docs/PLAN-AVATARES-Y-MARCADORES.md`](../../docs/PLAN-AVATARES-Y-MARCADORES.md).
- **Validar el contenido de `0032`–`0049`** con el owner y el director: existen y se
  reproducen, pero nadie confirmó que cada una sea la seña correcta.
- Las propuestas de arriba, y los comentarios viejos de `app.js`, `rest_pose.py` y
  `seed_catalog.py`.
- Si el owner quiere contrastar A6 o el "50" contra el SRS/SDD de Overleaf, avisar antes
  de abrir (la cookie puede haber vencido y el MCP lanza una ventana de Chromium) y leer
  solo.
