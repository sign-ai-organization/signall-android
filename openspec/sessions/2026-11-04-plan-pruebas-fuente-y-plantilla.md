# Plan de pruebas: cuál es la fuente de verdad, la 7.3 y la plantilla de resultados

**Fecha:** 2026-11-04 · **Rama:** `main` · **Repos tocados:** este y `../sign-ai-docs`

## Qué llegó

El owner pidió resolver, antes de escribir nada, cuál de tres candidatos es la fuente de
verdad del plan de pruebas, porque no coincidían y había tres conteos vivos. Después: llenar
la sección 7.3 «Herramientas e Instrumentación» con lo que existe de verdad, y armar la
plantilla de resultados en `.xlsx`. Sin ejecutar pruebas, sin tocar código y sin cambiar el
plan más allá de la 7.3.

## La fuente de verdad

Las tres candidatas, medidas el 2026-11-04:

| Fuente | Fecha real | Fichas | Estado interno |
|---|---|---|---|
| Overleaf «Plan de Pruebas» (`6add1e0e35efb959c10cd0fb`) | **2026-11-03 00:18** (hora Colombia), versión 38 | **73** | coherente: texto, tabla y fichas dicen 73 |
| `../sign-ai-docs/latex/plan-pruebas/main.tex` | commit `1e249c1` del 2026-10-29 | 72 | **copia atrasada**: difiere en 133 líneas y en `\documentclass` (12pt vs 10pt) |
| `../sign-ai-docs/borradores/plan-pruebas/` (74 archivos: 69 `.md` + 5 `.gs`) | 2026-11-04 (commit `8526c30`, solo AU-01) | 73 | **incoherente**: §9 dice 72, la tabla dice 72, `resumen.md` dice 73 y 74 |

**Gana Overleaf.** Tres razones: el `README.md` de `sign-ai-docs` dice que los documentos
formales se editan en Overleaf y que el repo guarda una copia para versionar; Overleaf es el
estado formal más reciente; y es el único de los tres que cuadra consigo mismo. La fecha que
se creía («última edición 2026-10-29») estaba vencida: Overleaf se editó el 2026-11-03.

Los tres números no eran el mismo conjunto:

- **PR-PER-06 «Flujo completo por voz»** existe **solo en Overleaf**, agregada el 2026-11-03
  con ficha, texto, tabla e inventario de trazabilidad.
- **PR-REN-09 «Tasa de procesamiento»** existe **solo en los `.md`** (commit `9ff39cf` del
  2026-10-27) y **nunca entró** al `.tex` ni a Overleaf.
- El «74» de `resumen.md` es un renglón de total que suma 73 filas.

**Decisión del owner:** PR-REN-09 **entra**, y el plan pasa a **74**. El argumento es
`RNF-DES-09`, que **sí** está en el SRS vigente: está en `latex/srs/main.tex` (línea 2674,
commit `059eeaf` del 2026-10-27) pero **no** en el SRS de Overleaf (`6a122a19bb9a5dd9d390ba3d`,
v645), que llega hasta RNF-DES-08. El owner aclaró que el SRS del repo es el actualizado y
que sus compañeros todavía no lo subieron a Overleaf.

## Qué se hizo

### 1. Sección 7.3 de Overleaf, con control de cambios

Proyecto `6add1e0e35efb959c10cd0fb`, `main.tex`, **versión 38 → 39**, `tracked: true`,
`tracked_change_count` 46 → 93. Compila limpio: **0 errores**.

| Línea | Qué cambió |
|---|---|
| 688 | Se borra el `TODO` rojo «esto falta verificarlo cuando se hagan las pruebas». Dejó de ser cierto: el inventario se verificó contra el código de los tres repos el 2026-11-04. |
| 688 | Backend: se agrega el puerto 8080, el contenido de `/metrics.json`, la mediana y el percentil 95 sobre 512 muestras, `rt_sentence_delay_ms`, los contadores `rt_phrases_total`, `rt_sentences_total`, `rt_glosses_total`, `rt_phrase_errors_total`, `rt_cache_total` y las sesiones. Se agrega que `/metrics.json` no pide autenticación. |
| 690 | Traza por frase: se detallan los pasos reales de la caché (`cache.exact`, `cache.embed`, `cache.similarity`, la espera del bloqueo), la variable `DEBUG_TRACE_ENABLED` y los mensajes `trace` y `sentence_trace`. |
| 692 | Se conserva el párrafo de los dos tramos del tiempo hasta la primera seña (era correcto). |
| 694 | **Nuevo:** la instrumentación del celular. `SignAiMetrics`, `SignAiNet`, `SignAIWeb`, qué mide cada uno y que la consola de la escena exige `-Psignai.devTools=true`. |
| 696 | **Nuevo:** el servicio de transcripción en el puerto 8000, `/metrics` y `/metrics.json`, y los nombres `stt_*`. |
| 698 | Seguridad: se reemplaza la lista de «qué hace falta» por las herramientas concretas: el proxy inverso con TLS en 443 y CA de laboratorio, la compilación `demo`, `tcpdump` sobre el lazo local, `openssl s_client` y la captura del tráfico al LLM por `LLM_BASE_URL`. |
| 700 | Resiliencia: detener y arrancar cada contenedor, dejar el LLM sin respuesta por configuración, forzar un error interno, reiniciar con clientes conectados y cortar la conexión desde el teléfono. |
| 702 | **Nuevo:** la nota de verificación, con la fecha y con qué queda por confirmar (que las herramientas alcancen, no que existan). |

### 2. Plantilla de resultados

`docs/plan-pruebas/` de este repo (se armó primero en `sign-ai-docs` y se movió acá
porque el trabajo sigue en este repositorio):

- `plantilla-resultados.xlsx` — 16 hojas: Portada, Resumen y una por bloque (14). **74 filas**,
  una por prueba. Las 73 fichas salen de Overleaf v39 copiadas literalmente; PR-REN-09 sale
  del borrador porque todavía no está en Overleaf.
- `README.md` — el significado de cada columna, los cuatro estados, la convención de nombres
  de las evidencias y lo que falta.
- `generar-plantilla.py` — el generador, para poder rehacer el libro cuando cambie el plan.

La Portada explica los cuatro estados y los doce campos de la ficha; el Resumen calcula
totales por bloque y por ronda con `COUNTA`, `COUNTIF` y `COUNTIFS`; cada hoja de bloque tiene
encabezado congelado, filtros, texto ajustado, lista desplegable y color por estado.

Verificado con `soffice --headless --convert-to pdf`: 44 páginas, sin errores de conversión.

## Hallazgos que quedan abiertos

1. **PR-REN-09 no está en Overleaf.** El libro tiene 74 filas y el plan formal dice 73. Para
   que coincidan hay que promoverla: texto 73→74, tabla REN 8→9, inventario de requerimientos
   y tablas de trazabilidad. No se hizo porque excede la 7.3 que se autorizó.
2. **`RNF-DES-09` no está en el SRS de Overleaf.** Está en el del repo. Es deuda del SRS, no
   del plan.
3. **El `TODO` de PR-REN-06 ya se puede cerrar.** La ficha dice «por confirmar que el backend
   lo tiene» sobre el contador de sesiones abiertas, y el backend lo tiene: `sessions_active`
   y `sessions_total` (`app/controller/router.py:231-232, 239, 277`, publicados en `/metrics`).
4. ~~**`AGENTS.md` está atrasado en un punto.**~~ **Resuelto en la misma sesión por el owner:**
   decía que la consola JS de la escena necesitaba «depurar prendido en Ajustes → AVANZADO»,
   cuando desde el 2026-11-03 el modo debug es una condición de compilación
   (`BuildConfig.DEV_TOOLS`, `-Psignai.devTools=true`), no una preferencia
   (`SettingsRepository.kt:22-23`). `AGENTS.md` ya trae la trampa del build en su lugar. En la
   7.3 se escribió lo que dice el código.
5. **La copia del `.tex` en `../sign-ai-docs/latex/plan-pruebas/` no es la de Overleaf.** Se
   refresca con `Menu → Download → Source`.
6. **Los `.md` siguen con los conteos viejos** (72 en la sección 9 y en su tabla, 73 y 74 en
   `resumen.md`). No se tocaron.

## Evidencia

- Overleaf: `main.tex` v39, 93 cambios con control de cambios pendientes (46 de la sesión del
  2026-11-03 y 47 de esta), compilación con 0 errores.
- Plantilla: `docs/plan-pruebas/plantilla-resultados.xlsx`, 58.687 bytes, 16 hojas, 74 filas.
- PDF de verificación: 44 páginas, A4 para Portada y Resumen, A3 para las hojas de bloque.

## Dónde quedó cada cosa

| Qué | Ruta |
|---|---|
| La plantilla, su README y su generador | `docs/plan-pruebas/` de este repo |
| La 7.3 nueva | Overleaf, proyecto «Plan de Pruebas», `main.tex` líneas 686-703 |
| Esta bitácora | `openspec/sessions/2026-11-04-plan-pruebas-fuente-y-plantilla.md` |
| Nada pendiente en `sign-ai-docs` | la carpeta `03-resultados/` se movió acá y ese repo volvió a como estaba |

No se commiteó nada en ninguno de los dos repos: queda a la vista del owner.
