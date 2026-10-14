# Seed del catálogo en Neon — las 50 glosas de LSC50

- **Día:** miércoles
- **Fecha:** 2026-10-14
- **Hora:** 00:00–00:40

## Qué se hizo

Se sembró la tabla `catalog` de Neon con las 50 glosas de LSC50 y se corrigió un
agujero que solo apareció una vez que la base announcements las 50.

## El seed

`tools/seed_catalog.py --apply` sobre el proyecto `bold-shape-68007559`
(org `org-orange-surf-39007829`). Las 50 salen de la **Table 2** del paper, no
de los nombres de archivo: son las que definen el índice `NNNN` que empareja
`signs/NNNN_0000_0000.glb`. Las 3 filas previas (CASA, COMER, CUADERNO) apuntaban
a `/static/glb/*.glb`, una ruta que el backend nunca sirvió, y COMER además
tenía un `id_sign` que no era el de su clase.

Resultado: 50 filas, 32 con metadatos de asset, 18 sin animación (`0032`–`0049`).
Las glosas van **sin tilde y sin `¿`/`?`** — lo que produce el prompt del
backend — pero **la `Ñ` se conserva**, porque `NIÑO` y `NINO` son dos señas.

Se agregaron 4 columnas nullable: `fps`, `total_frames`, `asset_bytes`,
`asset_sha256`. Nullable a propósito: las 18 sin animación no tienen archivo de
dónde sacarlas. La secuencia `catalog_id_sign_seq` quedó en 49 después del
`DELETE`+`INSERT`; sin resetear, el próximo `nextval` devolvía 4 y chocaba con
el PK.

### Verificación

Backup previo de la tabla vieja en `/tmp/opencode/catalog-backup/`.

- **90/90 comprobaciones end-to-end** con el `CatalogRepository` **real** del
  backend (el que ya tiene el fix de normalización en `main`), alimentado con
  las filas que quedaron en Neon — no con una reimplementación: las 50 resuelven
  a su propio path, 32 variantes acentuadas/con interrogación resuelven bien, 4
  glosas inexistentes dan `None`, `NINO` no resuelve y `Ñ` sí, y `COMO`/`COMO
  ESTAS` y `QUE`/`POR QUE` no se pisan.
- **Triple cruce Neon ↔ disco ↔ `SignsCatalog.kt`**: los 32 `.glb` existen en el
  APK, con **sha256 y tamaño idénticos** a lo que dice la base, y el conjunto
  coincide exactamente en los tres lados.

## El bug que destapó el seed

Con el catálogo vacío, `available_signs()` del mock devolvía solo lo que había
en disco, así que **nunca se anunciaba una seña sin animación** y el 404
garantizado de esas rutas no se podía reproducir en el dispositivo.

Con las 50 sembradas pasa. `MainScreenState.startQueue()` caía así:

```kotlin
local != null -> local
gloss.available -> assets.resolve(...)   // <-- las 18 sin animación entran acá
```

Las 18 llegan con `available == true` y un `signs/<id>.glb` que no tenemos, así
queiban al camino de descarga y el backend respondía 404 —un round trip por
cada vez que el usuario decía "POR FAVOR", "MUCHO", "TODOS"— para terminar
igual en el sign por defecto. Un **36% del vocabulario**.

Agregado `SignsCatalog.isOwnAssetPath()` (`GLS-10`): un path `signs/*.glb` es
nuestro layout, así que si no lo tenemos tampoco lo va a servir nadie. Solo baja
por `SignAssetRepository` lo que está **fuera** de ese layout, que es donde
viviría un asset futuro alojado en el backend.

### Medido en el Redmi, misma frase y mismas glosas

| | peticiones `.glb` | resultado |
|---|---|---|
| sin `isOwnAssetPath` | 2 | `GET /signs/0032… → 404`, `GET /signs/0049… → 404` |
| con `isOwnAssetPath` | **0** | caída directa al sign por defecto |

`signs/0026` (COMO ESTAS, que sí está exportada) nunca se pidió en ninguno de los
dos casos: sale del APK.

Para que el caso se pudiera reproducir, el mock ahora resuelve glosa → seña
contra la **misma tabla LSC50** que siembra el seed, y loguea los `GET` de
`.glb` que no sirve.

## Lo que casi hizo perder una hora

`python3 tools/mock_backend.py > log` deja el log **vacío** aunque el backend
esté sirviendo: Python bloquea el stdout cuando no es una TTY. Con `python3 -u`
apareció al instante. El indicio de que algo andaba mal fue ver la app en
"Reproducción completada" con el log del backend completamente mudo.

El segundo: **el teclado abierto mueve `Traducir`**. Con el teclado abierto el
botón está en `y≈1550`; cerrándolo, en `y≈2255`. Un tap en 2258 con el teclado
abierto no toca nada y parece un botón muerto.

## Corregido de paso

- Comentarios que decían que el catálogo "seguía vacío": en
  `MainScreenState.kt` y en el **generador** `split_signs.py`, que es donde
  importa corregirlos porque el `.kt` se regenera.
- `mock_backend.py` loguea los 404 de assets y acepta `--glosses "A,B,C"`.

Al regenerar con `split_signs.py` los **33 `.glb` y `manifest.json` salieron
byte-idénticos**: el split es reproducible.

## Pendiente

- **18 señas sin animación** (`0032`–`0049`). Cuando lleguen, a `GLB Senas/` y
  `split_signs.py`; el backend los anuncia apenas la tabla los tiene y la app los
  resuelve sola por path.
- **El backend tiene que reiniciarse** para que `load_all()` lea el catálogo
  nuevo: lo carga una sola vez al arrancar y no se invalida.
- **Licencia de Hotgen sin verificar** — es lo que bloquea el uso en la tesis.
- `demoSignFallback` sigue en `true`: es lo que hace que las 18 muestren algo en
  vez de "Seña no disponible". Sigue siendo decisión de Diego.