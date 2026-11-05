# GLB nuevo de Samuel: cuatro marcas faciales y regeneración de assets

**Fecha:** 2026-11-05, noche · **Rama:** `main`

## Qué llegó

El owner pidió traer de `../signai-avatar` (rama `dev`) el GLB nuevo que dejó Samuel
con cuatro marcas faciales —afirmación, negación, orden y pregunta—, revisar los clips
y nombres reales, regenerar los assets de la app con `tools/pack_avatars.py`, ajustar
`FACE_VARIANT_BY_TYPE` si hacía falta, compilar, instalar y verificar las cuatro caras
en el teléfono físico.

## Qué se hizo

1. **Repo hermano.** `git fetch` + `git pull` sobre `dev`: no había nada nuevo en
   `origin/dev`. El commit con los GLB (`fc6f372`, 2026-11-04) estaba en `origin/main`,
   que ya contenía a `dev`. Se avanzó `dev` por **fast-forward** (`cf014ef..fc6f372`),
   sin crear ningún commit ni tocar archivos del hermano.
2. **Lectura de los GLB nuevos.** `Juan.glb` (34.267.268 B) y `Pauline.glb`
   (33.884.284 B) traen **200 clips**: 50 señas × `afirmativo`, `pregunta`, `negacion`,
   `orden`. **No hay clip base** ni las caras viejas (`_alegria`, `_tristeza`,
   `_sorpresa`). Los 102 huesos y los canales de hueso son idénticos entre las cuatro
   variantes; solo cambian los canales `weights` de los morph targets.
3. **`tools/pack_avatars.py`.** `SUFFIXES` y el conteo esperado pasan a las cuatro
   marcas y 200 clips. La pose de descanso se hornea desde `0029_afirmativo` (ya no
   existe `0029` pelado). El manifiesto describe 4 variantes por seña y usa
   `_afirmativo` como representante para duración/frames/tracks. El inventario agrega
   `default_variant`.
4. **Assets regenerados.** `models/juan.glb` (30.323.680 B), `models/pauline.glb`
   (27.850.104 B), `models/avatars.json`, `manifest.json` y `SignsCatalog.kt`.
   Morph targets que alguna variante anima: **147 → 59** bloques en Juan y
   **139 → 58** en Pauline.
5. **`app.js`.** `FACE_MARKER_SUFFIXES` y `FACE_VARIANT_BY_TYPE` usan las cuatro caras
   nuevas. `DEFAULT_FACE_VARIANT = "afirmativo"` cubre el tipo ausente o desconocido.
   `embeddedClipsFor` devuelve la variante por defecto en `clips` cuando el asset no
   trae clip base, y `pickVariant` prioriza la cara del tipo.
6. **Generador del catálogo.** `tools/split_signs.py` tenía el `*/` del bloque
   `localAssetOrNull` con 4 espacios; el archivo viejo estaba corregido a mano y la
   regeneración lo volvía a romper. Se corrigió la plantilla a 5 espacios.
7. **Documentación.** `AGENTS.md`, `openspec/specs/avatar/spec.md`,
   `openspec/specs/pipeline/spec.md`, `openspec/specs/ui/spec.md`,
   `openspec/config.yaml`, `docs/SISTEMA.md`, `tools/README.md` y
   `docs/PLAN-AVATARES-Y-MARCADORES.md` quedaron con los conteos y nombres nuevos.
   `tools/measure_face_markers.py` ahora falla con un mensaje explícito si se lo corre
   contra el layout nuevo, porque sigue midiendo contra el clip base viejo.

## Qué se decidió

- **`dev` avanza por fast-forward a `fc6f372`.** El GLB no estaba en `dev` sino en
  `main`, y `main` ya contenía a `dev`; mover la rama no crea commits y deja el hermano
  en el mismo árbol que `origin/main`.
- **No hay clip base: `afirmativo` es la cara por defecto** cuando el `type` falta o no
  está en la tabla. Las cuatro caras son gramaticales, no emocionales.
- **Las dos preguntas comparten `pregunta`.** El backend distingue
  `YES_NO_QUESTION` y `CONTENT_QUESTION`, pero el export nuevo solo trae una cara de
  pregunta.
- **La orden usa `orden`**, que es la cuarta marca del export.
- **El path lógico no cambia**: `manifest.json` sigue publicando
  `signs/<id>_0000_0000.glb`; la escena lo resuelve por nombre contra los clips del
  avatar y no pide nada por red.

## Qué se midió

Verificación en el **Redmi Note 9S** (Android 11), build `debug` con
`-Psignai.devTools=true -Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate`,
`adb reverse tcp:8080 tcp:8080` y el **backend real** en el puerto 8080. Los nombres de
clip salen de `adb logcat -s SignAIWeb` (`fundición -> <clip>`, que `app.js` solo
escribe con el diagnóstico encendido):

| Frase enviada | Tipo del backend | Clip reproducido |
|---|---|---|
| `Yo como.` | `AFFIRMATIVE` | `0038_afirmativo`, `0007_afirmativo` |
| `No quiero ir.` | `NEGATIVE` | `0038_negacion`, `0037_negacion` |
| `Tu comes?` | `YES_NO_QUESTION` | `0039_pregunta`, `0007_pregunta` |
| `Como estas?` | `CONTENT_QUESTION` | `0026_pregunta` |
| `Firme el documento.` | `IMPERATIVE` | `0038_orden` |

El overlay de diagnóstico mostró `cara: afirmativo`, `cara: negacion`, `cara: pregunta`
y `cara: orden` en cada caso. La escena indexó **200 clips** en los dos avatares.

Observación: en el primer intento de la mañana el proceso de render del WebView murió
por OOM (`chromium: Render process kill (OOM or update)`, 01:55:30) y la app se
reinició; al relanzarla cargó y reprodujo sin problema. No se reprodujo de nuevo y el
GLB nuevo es más chico que el anterior, así que queda como observación, no como
diagnóstico.

## Qué quedó pendiente

- `tools/measure_face_markers.py` sigue midiendo el layout viejo (clip base + cinco
  caras). Hay que adaptarlo si se quieren medir las cuatro caras nuevas.
- Validación **LSC** de las cuatro caras y de la decisión de compartir `pregunta` entre
  pregunta sí/no y pregunta de contenido.
- `../signai-avatar` quedó con `dev` en `fc6f372` (fast-forward local); esta sesión no
  creó commits ahí.
