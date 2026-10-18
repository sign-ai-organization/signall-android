# Contraste: el backend contra los documentos

Documento de trabajo para una sesión **aparte**. Nació el 2026-10-17, en la sesión de
los estados de la app (`openspec/sessions/2026-10-17-estados-y-desconexion.md`), de un
hallazgo que es del backend y no de la app.

**Vive en este repo y no en `../sign-ai-backend`** por dos razones: los documentos de
contraste del proyecto están acá (`docs/CONTRASTE-APPvsDOCUMENTOS.md` es el gemelo de
este para la app), y el repo del backend tiene 14 archivos modificados sin commitear
del trabajo del 2026-10-15 en la rama `flujo-oraciones` que **no son míos**. Meter un
documento nuevo en medio de ese árbol sería revolverlo.

## Qué es este documento

El backend contra las tres fuentes de verdad, con una regla por sesión:

| Fuente | Dónde | Cómo se trata |
|---|---|---|
| El código | `../sign-ai-backend` | **Solo lectura** salvo que el owner lo pida en un mensaje. Nunca se commitea sin que lo pida (PR contra `develop`). |
| Los documentos del proyecto | `../sign-ai-docs` | Los edita el owner. Un cambio grande se propone, no se aplica en silencio. |
| La tesis | Overleaf (SRS, SDD, Memoria, PMP, PPP) | **Solo lectura.** Si un `.tex` está mal, lo corrige el owner. Acá se anota qué corregir y con qué texto. |

Y la regla que no se negocia: **cuando `../sign-ai-docs` y Overleaf se contradigan
entre ellos, no hay ganador automático.** Se reportan las dos versiones con su fecha y
se pregunta. Cuando el que está mal es el código, se arregla el código; cuando el que
está mal es un documento del proyecto, se propone el cambio y lo aplica el owner.

## Estado de los repos al 2026-10-17

- `../sign-ai-backend`, rama **`flujo-oraciones`** (no mergeada), 14 archivos
  modificados sin commitear del 2026-10-15. **No tocar sin que lo pida.**
- `../sign-ai-docs`, rama `main`.
- Este repo, rama `flujo-oraciones`, con trabajo sin pushear.

---

## Hallazgo 1 — La base de datos no tiene tiempo máximo, y el LLM sí

**Dónde:** `../sign-ai-backend/app/persistence/database.py` (el pool) y
`app/nlp/semantic_cache.py` (las consultas).

**Qué pasa.** Lo primero que hace el backend al traducir una frase es consultar la
caché semántica. Esa consulta **no tiene tiempo máximo**: el pool se crea con
`asyncpg.create_pool(...)` sin `command_timeout`, y `pool.acquire()` tampoco tiene
tiempo de espera. Si Neon no contesta —se suspende, se cae el dato móvil, un NAT de por
medio— el backend no manda ni una oración ni un error: se queda pensando, y la app se
queda esperando en *Procesando* sin ningún reloj que la saque de ahí.

El LLM, en cambio, **sí** tiene tope: `llm_timeout_ms = 10000`
(`app/config/settings.py:32`), y cuando se pasa sale el frame `error`
(`app/controller/controller.py:153-159`), que la app ya sabe mostrar. O sea que **lo
que cuelga sin límite es la base, no el modelo**.

**Las dos esperas sin techo, separadas por gravedad:**

| # | Dónde | Qué espera | Si Neon no contesta |
|---|---|---|---|
| 1 | `semantic_cache._lookup_neon` (`semantic_cache.py:248-274`): `pool.acquire()` y `conn.fetchrow(...)` | La consulta de cada frase | **La frase se queda esperando para siempre.** El usuario ve la app congelada en *Procesando* y no hay forma de salir. Es el hallazgo grave |
| 2 | El arranque: `catalog_repository.load_all()` y `semantic_cache.load_all()` (`app/main.py:47-57`) | La carga inicial | El backend **no termina de arrancar**. La app no abre sesión, ve *Desconectado* y reintenta cada ≤5 s. Molesto pero **visible y se recupera solo** cuando Neon vuelve |

**Por qué la #1 es más probable de lo que parece.** El arranque está diseñado a
propósito para no depender de la base: si la precarga falla, lo dice y sigue
funcionando con consultas sueltas (`semantic_cache.py:135-137`, *"could not preload
semantic_cache; falling back to per-lookup queries"*), y también se puede apagar con
`semantic_cache_preload`. Ese camino de reserva es justamente el que no tiene tiempo
máximo: convierte un problema del arranque en un colgamiento **por cada frase, para
siempre**. Los embeddings no son un riesgo: son CPU local en un hilo aparte
(`asyncio.to_thread`, 12-25 ms).

**Qué cambiaría (propuesta, no aplicada):**

- `command_timeout` en el pool, con un valor del orden de los 5-10 s, para que una
  consulta colgada **levante** en vez de colgar. Con eso, la #1 se convierte en el
  `error` que el backend ya sabe mandar y la app ya sabe mostrar.
- `timeout` en el `pool.acquire()`, que hoy también espera sin límite si el pool se
  agotó (5-10 conexiones, `db_pool_min_size`/`max_size` en `settings.py:18-19`).
- Con eso, la #2 se arregla sola: el arranque falla rápido, entra el camino de reserva,
  y el camino de reserva ya tiene techo.

**Pendiente de decisión del owner:** el valor. No hay ningún número en el SRS ni en
`../sign-ai-docs` para el tiempo máximo de una consulta. El 10 s del LLM es el único
número cercano que existe en el código y sirve de referencia.

**Dónde va la respuesta:** acá, en §"Hallazgos", con el `archivo:línea` del commit en el
que se tome la decisión. Y la sesión de log, en `openspec/sessions/` del repo del
backend, cuando el owner la abra.

---

## Hallazgo 2 — El `done` no dice a qué frase pertenece, y la app necesita saberlo

**Por qué salió de acá:** el 2026-10-17, al implementar la cancelación de una frase en
la app (`docs/CONTRASTE-APPvsDOCUMENTOS.md` §7, A14). Es el único punto del contrato
que esta ronda tocaron los dos lados sin querer.

**Qué pasa.** Cancelar una frase —el usuario apaga el micrófono o el TR mientras espera
una traducción— es **local**: la app deja de esperarla, pero el backend no tiene forma
de enterarse y sigue mandando las oraciones que le faltan y su `done`. La app tiene que
descartar esas oraciones y **tragarse** ese `done` sin saber si es el de la frase
cancelada o el de la frase que el usuario mandó después.

Hoy no hay forma de saberlo: `done` es `{"type": "done"}` y nada más
(`SignAiWebSocketClient.parseMessage`, `data/SignAiWebSocketClient.kt:188`), y `segment`
solo lleva `index`/`total` de la frase. El resultado es una carrera que la app resuelve
a ojo: si el `done` viejo se pasa, el turno nuevo se queda esperando y la app declara
un fallo a los 15 s que no es cierto.

**Dos formas de arreglarlo, y por qué la segunda es mejor:**

| Opción | Qué cambia | Lo que cuesta |
|---|---|---|
| Un frame `cancel` del cliente al backend | El backend sabe que la frase ya no interesa y no manda nada más | Hay que tocar el orquestador (`app/orchestrator/orchestrator.py`, `translate_stream` 18) y sumarle un tipo de frame al protocolo. La app lo mandaría al cancelar |
| Un **id de turno** en `segment` y `done` | Cada frame dice de qué frase es; la app descarta por id y no traga nada a ciegas | Es aditivo y más barato: no cambia ninguna lógica del backend, solo agrega un campo. Es el mismo `index`/`total` que ya viaja, pero por turno |

**Lo que la app hizo mientras tanto** (para que la deuda quede acotada y no rota):
`MainScreenState.StaleTurn` descarta las oraciones de la frase cancelada y se traga el
`done` siguiente; si ese `done` no llega en 15 s, **olvida el descarte en silencio**, sin
mostrar un fallo que no ocurrió. Está en `openspec/specs/ui/spec.md` `STA-25`.

**Pendiente de decisión del owner**: nada de esto se toca sin que lo pida. El backend
tiene además 14 archivos modificados sin commitear de la rama `flujo-oraciones`.

---

## Por auditar (semilla para la próxima sesión)

Ninguno de estos está revisado todavía. La columna de "estado" es el punto de partida.

| # | Qué auditar | Por qué | Estado |
|---|---|---|---|
| 1 | ¿El `error` del backend siempre llega? `controller.py:153-159` | Es lo que le permite a la app salir de *Procesando* sin freezes. Si hay algún camino que se cuelgue sin error, el freeze vuelve | Sin revisar |
| 2 | El `done` siempre se manda después del último `segment` | La app cierra el turno con eso; sin `done` la frase nunca termina | Sin revisar |
| 3 | Nombres de métrica: `rt_*` del backend contra `SignAiMetrics` de la app | Un número que no se instrumentó en los dos lados no se puede comparar con nada | Sin revisar |
| 4 | RNF-DES-01 (latencia con caché / con LLM) contra lo que el backend mide de verdad | Los valores del documento están marcados como estimados, pendientes de medición | Sin revisar |
| 5 | Columnas del catálogo en Neon contra `SignsCatalog.kt` y `domain/SignsCatalog.kt` | Son el contrato de los assets; 18 de las 50 glosas se anuncian sin clip | Sin revisar |
| 6 | RF-INT-COM-01: "el sistema detectará una desconexión" | Con lo del hallazgo 1, esa detección **no** se cumple en la mitad de los caminos | Sin revisar |
| 7 | El protocolo del STT y `silence_timeout_ms` | La app lo lee por `/health` para mostrarlo en depuración; si cambia el nombre, se rompe en silencio | Sin revisar |
| 8 | ¿Puede `done` llegar **sin** ningún `segment`? | La app trata `done` sin oraciones como "esa frase no produjo ninguna seña" y cierra el turno. Si el backend puede mandar `done` por una frase vacía sin querer, eso cierra turnos que no se cerraron | Sin revisar |
