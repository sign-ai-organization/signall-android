# GLB + AnimationMixer — el avatar y las señas entran en la app

- **Día:** martes
- **Fecha:** 2026-10-13
- **Hora:** 21:00–22:00

## Qué se hizo

Se cambió por completo de dónde salen el avatar y las señas, y cómo se reproducen.

**Antes:** `app.js` era un motor procedural. Cargaba `avatar.glb` (un muñeco
AutoRigPro de 883 KB) y reproducía las señas desde 50 archivos `.json` de
landmarks de MediaPipe, interpolando poses hueso por hueso en JavaScript.

**Ahora:** el personaje se carga una vez desde `models/avatar.glb` y cada seña es
un GLB con la animación ya horneada, que se reproduce con
`THREE.AnimationMixer`. `app.js` pasó de 1139 a ~420 líneas.

Layout de assets:

```
app/src/main/assets/web/
  models/avatar.glb      10.33 MB   el muñeco, una vez
  signs/<id>.glb          5.89 MB   32 señas, solo animación (180 KB promedio)
  manifest.json                      gloss, variantes, fps, frames, sha256
```

329.7 MB de exports crudos → 16.22 MB (20× menos), y los 11 MB de JSON
desaparecieron.

## De dónde salieron los assets

El modelador entregó 32 exports en `GLB Senas/`, cada uno con el **muñeco
completo adentro** (mismo personaje Hotgen, 102 huesos + 28 huesos faciales, 315 MB
en total). `tools/split_signs.py` los separa: saca el personaje del primer
archivo y deja en cada seña solo los tracks de animación, conservando los
nodos para que los nombres de los tracks sigan encontrando su hueso.

La separación se validó **hueso por hueso**, muestreando las quaterniones de
ambos archivos en los mismos instantes: diferencia máxima `0.00e+0` sobre
309.273 comparaciones. No es "se ven parecidos"; los números son idénticos.

## Bugs que aparecieron (y valen para el futuro)

1. **`AnimationMixer` se ata al root con el que se construyó.** Recargar una
   escena y reusar el mixer deja la nueva en pose de reposo, sin error. Pasó en
   un visor de prueba propio.
2. **El clip anima la raíz del armature.** Los exports de Cascadeur traen
   tracks de posición, rotación *y* escala sobre `HG_Branden001`. Poner ahí la
   normalización (escala a 1.7, apoyo en el piso) hace que el mixer la pise
   frame a frame: el muñeco aparece de perfil y sin escala, sin un solo error en
   consola. **La normalización va en un objeto padre (pivot).**
3. **Un chequeo de tracks por nombre de hueso marca falsos positivos.** Los
   exports animan también la raíz, que es un nodo pero no un bone del skeleton.
   Contando contra el set de *nodos* del avatar: 14214/14214 resuelven.
4. **`signs/` con extensión `.json` cuando el backend ya servía `.glb`**:
   `mock_backend.py` seguía Emitiendo la ruta vieja y la app pedía
   `/signs/<GLOSA>_<hash>.json`, que el `PathHandler` servía como JSON. El
   síntoma es un `.glb` que el `GLTFLoader` rechaza con "Unsupported asset",
  wrapped en un error que no dice nada de la extensión.

## Rendimiento

Medido en el Redmi Note 9S (1080×2400, dpr 2.75) con `tools/rendimiento/gfxinfo.py`:

| | mediana | p99 | jank |
|---|---|---|---|
| antes (dpr sin tope) | 51.1 ms | 75.2 ms | **99.2%** |
| dpr topado a 1.75 | 11.3 ms | 25.7 ms | 10.8% |
| + sin `DoubleSide` | 11.3 ms | 17.9 ms | **6.7%** |

El WebView renderiza a pantalla completa, así que sin tope el device dibujaba
19.6 megapíxeles por fotograma y el GPU quedaba en 37.75 ms. `DoubleSide` sobre
mallas cerradas era overdraw puro: mismo aspecto, 4% más de jank.

## Verificación en el device

Con `tools/mock_backend.py` + `adb reverse` + `-Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate`:
el avatar carga (117 nodos), una frase entra, la cola de 3 señas se reproduce, el
caption las lista, al terminar salta `onPlaybackDone` y Kotlin muestra
"Reproducción completada" con el botón Repetir.

## Decisiones

- **Los assets van locales en el APK**, no en el backend: cero latencia, cero
  modo de falla, y `RF-INT-HW-04` ya lo exigía.
- **Separar `models/` y `signs/` es obligatorio**: Hotgen comparte un esqueleto
  entre outfits y géneros, así que cambiar de muñeco no obliga a reexportar las
  señas. Con el formato anterior, cambiar de personaje significaba rehacer todo.
- **A Samuel no se le pide trabajo extra.** Pide los `.glb` como siempre y el
  script separa. Lo único que hay que vigilar es que use el mismo muñeco, que es
  el único error que no avisa — por eso `split_signs.py` compara la huella del
  esqueleto de cada seña contra el del avatar y sale con código 1 si no coinciden.

## Pendiente

- **Fix de las tildes en el backend — hecho.** `CatalogRepository.search()` era
  `self.cache.get(gloss)`, comparación exacta, así que una emisión acentuada del
  LLM (`CÓMO`, `TÚ`) nunca encontraba la fila canónica y la app caía al sign por
  defecto. Ahora las dos puntas se pliegan con `normalize_gloss()`, que conserva
  la `Ñ` a propósito: descomponer con `unicodedata` y quitar diacríticos
  convertiría `NIÑO` en `NINO`, otra glosa y otra seña. Commit `7a73d60` en
  `feat/android-client-support-v2`, con `scripts/verify_catalog_normalization.py`
  (21 variantes que deben empatar, 3 pares que **no** deben colapsar, 4 entradas
  degeneradas). `main` del backend quedó intacto.
  **Pendiente de él**: abrir el PR de la v2 a `develop`.
- **Rama v1 del backend: borrada, sin pérdida.** `feat/android-client-support`
  tenía `bab1cd8` (apuntar el `LLMClient` a un endpoint local compatible con
  OpenAI — el soporte del modo `--ollama` del mock), que **no** está en `main`
  ni en la v2. Se borró la rama, pero el commit sigue vivo en GitHub y se
  recupera con `git fetch origin bab1cd8`. **Si quieren esa feature, hay que
  hacer cherry-pick de `bab1cd8` sobre la v2.**
  Ojo: `main` **local** del backend está atrasado (`b277541` vs `origin/main`
  `6c06c21`), así que comparar ramas contra el ref local miente — usar `origin/`.
- **18 señas faltan** (`0032`–`0049`: POR FAVOR … MUCHO). Sin `.blend` tampoco.
  Cuando lleguen, van en `GLB Senas/` y se corre `split_signs.py`.
- **Sembrar el catálogo de Neon**: 50 glosas con `gloss_key` **sin tildes** (el
  LLM las emite sin tildes y el backend las compara exacto: `BUENOS DÍAS` →
  `BUENOS DIAS`), más columnas `fps`, `total_frames`, `asset_bytes`,
  `asset_sha256`. El `manifest.json` ya tiene todo eso.
- **Licencia de Hotgen sin verificar.** Es un pack comercial; está en el backlog
  como P1 y bloquea el uso en la tesis.
- **`Base.glb`** que dejó el modelador es solo el armature (6.5 KB, 0 mallas,
  0 texturas): sirve para confirmar el esqueleto, no como muñeco. Sus 102
  huesos coinciden exactamente con los del avatar.
- **`tools/build_signs.py` y `tools/patch_webview_glue.py`** quedaron sin uso
  (armaban el pipeline JSON viejo). Marcados como obsoletos en `tools/README.md`,
  no borrados: documentan cómo se armaron los datos de LSC50.
- **ADB-012** (formato de animación: solo movimiento vs movimiento+malla) queda
  contestada por estos GLB; sigue en estado *Propuesta*.