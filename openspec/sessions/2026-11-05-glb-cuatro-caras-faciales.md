# GLB nuevo de Samuel: cuatro marcas faciales y regeneración de assets

**Fecha:** 2026-11-05, noche · **Rama:** `main`

## Qué llegó

El owner pidió traer de `../signall-avatar` (rama `dev`) el GLB nuevo que dejó Samuel
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

## Seguimiento: pendientes cerrados y dos ajustes de UI (misma noche)

### `measure_face_markers.py` adaptado y medición nueva

- El script pasa a `SUFFIXES = ("afirmativo", "pregunta", "negacion", "orden")` y usa
  **`_afirmativo` como referencia** (la cara que la app usa por defecto), porque el
  layout nuevo no tiene clip base. Las secciones de Δ, la tabla por seña y la
  comparación de huesos se reescribieron contra esa referencia; la fecha del informe
  sale de `datetime.date.today()`.
- Se corrió sobre los dos GLB empaquetados y quedó en
  `docs/mediciones/marcadores-2026-11-05.md` y `.json`:
  - `_pregunta` es la cara que más se distingue: sube las cejas **10,39 mm** de pico
    (`browInnerUp`, peso 1,25) y **3,96 mm** de RMS contra `_afirmativo`.
  - `_negacion` mueve la boca y baja las cejas: **5,34 mm** de pico, **2,35 mm** de RMS.
  - `_orden` baja las cejas y aprieta la boca: **5,34 mm** de pico, **2,27 mm** de RMS
    (`browDown_L` peso 0,75).
  - Las tres se distinguen entre sí (`negacion` vs `orden`: 3,66 mm de pico, 0,76 mm de
    RMS) y **las cuatro son una pose fija**: 0,00 mm de cambio dentro de la seña.
  - Huesos: los 150 clips variantes (50 señas × 3 caras) tienen los mismos canales y
    valores que `_afirmativo`; solo cambia la cara.

### Grilla del suelo apagada por defecto

- `Settings.grid` pasa de `true` a `false`. `SettingsRepository` **no lee** el valor
  guardado cuando `DEV_TOOLS` está apagado, y una instalación vieja se limpia **una sola
  vez** con la marca `grid_default_off`: el `true` viejo lo escribía cualquier cambio de
  ajustes, así que no era una elección. Desde esa marca, prenderla en AVANZADO la
  persiste.
- Verificado en el Redmi Note 9S con build `devTools`: la escena ya no dibuja el suelo y
  el valor guardado viejo no reaparece.

### El indicador ya no repite la glosa

- Se eliminó el `caption` de `MainScreenState`. `statusLine` ya no arma
  `"Reproduciendo · <glosas>"` ni siquiera con `DEV_TOOLS`: el indicador dice el nombre
  del estado y, con depuración, el aviso de `statusOverride`.
- La píldora sobre el avatar (`STG-10`) queda como la **única** superficie que dice qué
  seña se está representando. Verificado con una captura durante la reproducción:
  arriba a la izquierda `Reproduciendo` y en la píldora `YO`, al mismo tiempo.
- Specs `ui` actualizadas: `STA-13`, `STG-10` y `SET-8`.

## Qué quedó pendiente

- Validación **LSC** de las cuatro caras y de la decisión de compartir `pregunta` entre
  pregunta sí/no y pregunta de contenido. La medición del 2026-11-05 da el material para
  esa revisión, pero no la reemplaza.
- `../signall-avatar` quedó con `dev` en `fc6f372` (fast-forward local); esta sesión no
  creó commits ahí.
