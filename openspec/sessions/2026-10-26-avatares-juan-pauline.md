# El selector de personaje pasa a ser real: Juan y Pauline, dos GLB completos

**Fecha:** 2026-10-26, madrugada · **Rama:** `main`

## Qué llegó

Ejecutar [`docs/PLAN-AVATARES-Y-MARCADORES.md`](../../docs/PLAN-AVATARES-Y-MARCADORES.md),
que estaba escrito el 2026-10-24. El selector de «Personaje» de Ajustes mostraba Julián,
Samuel y Ximena y **no cambiaba nada**: el avatar era siempre el mismo archivo. El pedido
concreto del owner: que el selector funcione con **Juan** y **Pauline**, cada uno con su
GLB completo, y que **las expresiones faciales queden de lado** — «solo mostramos las
señas como lo estamos mostrando ahora».

## Qué se hizo

1. **`tools/pack_avatars.py`** (nuevo). Verifica 1 skin, **102 articulaciones** y los 300
   nombres de clip esperados; hornea la pose de descanso con la regla de `rest_pose.py`
   (46 huesos, primer keyframe de `0029`); poda los morph targets que ninguna de las 300
   variantes anima, reusando `repack`/`morphs_to_keep` de `pack_signall.py`; escribe
   `models/juan.glb`, `models/pauline.glb`, `models/avatares.json`, `manifest.json` y
   `domain/SignsCatalog.kt`.
2. **Assets.** Se borró `models/avatar.glb` (12,98 MB) con `git rm` y entran los dos
   archivos nuevos. Los originales `Juan_final.glb` y `Pauline_final.glb` (71 MB) se
   sacaron de la raíz del repo a **`../material-3d/Avatares/`** a pedido del owner
   («no puede ser que estén así de feo»), con los hashes verificados antes y después del
   movimiento. `pack_avatars.py` los lee de ahí por defecto (`--source-dir` sigue
   existiendo).
3. **Kotlin.** `Character` ahora tiene `Juan` y `Pauline` con sus ids; Juan es el
   predeterminado y los valores viejos (`Julián`, `Samuel`, `Ximena`) se normalizan a Juan
   al leerlos. `SignAiCommands.setAvatar(id)` es aditivo. `AvatarWebView` pasa el avatar
   inicial por `?avatar=<id>`. `MainScreenState` gana `avatarLoading`, `canSwitchAvatar`,
   `setCharacter`, `onAvatarLoaded` y `onAvatarFailed`; la barra de abajo y el botón de
   reproducir quedan bloqueados mientras el avatar carga.
4. **`app.js`.** Carga el avatar elegido, indexa sus 300 clips, libera geometría, materiales
   y texturas del anterior con `dispose()` y vuelve a resolver la seña en pantalla. El
   modelo nuevo se parsea **antes** de soltar el viejo, así un cambio fallido deja el
   anterior visible. Solo resuelve los clips base: el resolutor no reconoce los sufijos
   faciales a propósito.
5. **Documentación.** `AGENTS.md`, `.gitignore`, `openspec/specs/{avatar,ui,pipeline}`,
   `openspec/config.yaml`, `tools/README.md`, `tools/seed_catalog.py`, `docs/SISTEMA.md`,
   `docs/PLAN-MEDICION.md`, `docs/historia/CONTRASTE-APPvsDOCUMENTOS.md` y el plan, que pasa de
   «no se implementó nada» a «implementado el 2026-10-26» con una sección de resultado.

## Qué se decidió

- **Los marcadores faciales quedan fuera de alcance** (owner, 2026-10-26). Los morph
  targets se conservan en el asset para no reempaquetar el día que se retome, pero la
  escena no los escribe y se reproduce solo el clip base.
- **Se puede cambiar de avatar en `Inactivo`, `Desconectado` y `Error`**, no solo en
  `Inactivo` como recomendaba el plan: la lista del owner enumera operaciones activas y en
  esos dos estados no hay ninguna.
- **Los originales salen del repo** a `../material-3d/Avatares/`, que es donde ya vive el
  resto del material 3D. Se decidió con el owner entre esa carpeta y una dentro del repo.
- **El id viaja por URL y por `setAvatar`**, no por un frame nuevo del WebSocket: el
  contrato con el backend no se toca en esta sesión.

## Qué se midió

Todo en el Redmi Note 9S físico (serial `1ed6699f`), 2026-10-26, leyendo `SignAIWeb` y
`SignAiState` de logcat:

- **Arranque con Juan:** 1,81–2,50 s con caché de página caliente; 3,42 s en frío.
- **Arranque con Pauline:** 1,95 s.
- **Cambio en caliente:** 1,90–2,57 s; de eso, 1,18–1,95 s es el parseo del GLB.
- **APK:** 48 MB en debug, 34 MB en release sin firmar. **PSS** con Juan cargado: ~390 MB.
- **Empaquetado:** Juan 34 207 768 bytes (de 37 872 312); Pauline 29 460 916 (de
  35 272 616). Hashes de origen verificados contra los del plan: `2d87e29c…` y `c91afb2b…`.
- **Prueba punta a punta con el backend real** (el de la imagen Docker con el `app/` actual
  montado, Ollama `qwen3:4b-instruct`, STT `medium` en la GPU): «tengo hambre» →
  `HAMBRE, POR FAVOR`, dos señas, la primera a 59 ms del envío.

## Qué quedó pendiente

- **`Pauline_final.glb` no pasa `rest_pose.check_bind`**: el TRS de 2,00 de los nodos que
  no son brazo no es la pose de bind. Se registró como `bind_warning` en
  `models/avatares.json` y en el dispositivo se ve correcto; queda para revisar en el
  export.
- **La tabla tipo de oración → variante facial** (DOC-5) sigue sin decidir y sin uso.
- **El SDD de Overleaf** todavía describe 50 archivos GLB; lo tiene que actualizar el owner
  (este repo no escribe en Overleaf).
- **PR-AVT-06**: registrar la medición del APK, la memoria y la carga en el plan de pruebas
  de `../signall-backend`/`../signall-docs`.
- `../signall-backend` y `../signall-stt` **no se tocaron**: se levantaron con la imagen ya
  construida y el código actual montado de solo lectura. No hay commits que mandar allá.
