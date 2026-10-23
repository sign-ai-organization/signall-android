---
name: contrato-websocket
description: Cambiar el contrato entre la app Android y los repos hermanos (frames del WebSocket /ws/translate, nombres de glosas, columnas del catálogo de Neon, nombres de métricas) sin romper el otro lado en silencio. Usar antes y después de tocar cualquier cosa que cruce de un repo a otro, cuando se agrega o renombra un campo del protocolo, o cuando hay que cambiar un nombre de métrica rt_* o de SignAiMetrics.
---

# Cambiar el contrato entre repos

El contrato entre la app y el backend **son frames de WebSocket, no imports**. Eso
significa una cosa muy concreta: **un cambio se rompe sin que el compilador avise**, y
muchas veces sin que la app falle de forma visible — se rompe en silencio.

Por eso este skill es un procedimiento, no una lista de campos.

## Antes de cambiar nada

1. **Leé el contrato en los dos lados.** El del backend es
   `../sign-ai-backend/docs/android-integration.md` (423 líneas) — es la fuente de verdad
   y hay que **actualizarlo en el mismo cambio**. Del lado de la app, el contrato de
   salida es `domain/SignAiCommands.kt` y el parser está en `WebViewSignAiController`.
2. **Leé las reglas del repo que vas a tocar antes de escribir en él.** El backend tiene
   `CLAUDE.md`: código en inglés sin excepción, y **nunca hacer `git commit` ni
   `git push`**. El STT tiene `README.md`.
3. **Fijate qué prueba cubre el contrato y corréla antes de tocar**, para tener la línea de
   base. Del lado del backend, `python -m scripts.verify_client_frames` es la que fija lo
   que importa: que la frase se mande como `text`, que exista `end`, y **que la voz no se
   traduzca**.

## Los cuatro invariantes

Un único WebSocket, de la app **solo al backend**: `:8080/ws/translate`. La app nunca
habla con el STT ni con Neon.

1. **Solo se traduce lo que el cliente manda como `text`.** La voz entra por el mismo
   socket pero vuelve como `transcript`, y esa transcripción **no se traduce**.
2. **`segment` es una oración, con `index` y `total` reales desde el primero.** No
   existen `stream`, `provisional` ni `source_text`.
3. **El `final` siempre llega**, incluso vacío.
4. **Los mismos nombres de métrica en los dos lados.**

## La trampa del eco, y por qué el invariante 1 existe

Si el backend hiciera eco de la frase escrita, la app lo tomaría por una frase de voz y
la reenviaría. **Ese bucle existió y quemó el proceso**: una frase repetida ~2.900
veces, con el 44 % de CPU.

No es hipotético y no es un caso límite: es lo que pasa si el invariante 1 se rompe por
accidente. Por eso `handle_text` no manda ningún `transcript` y `verify_client_frames.py`
lo fija como prueba. Si tocás esa ruta, corré esa prueba.

## Los nombres de métrica

`rt_*` en el backend, `SignAiMetrics` en el device. **Un número que no se instrumentó en
las dos partes no se puede comparar con nada.**

`rt_first_sign_ms` conserva el nombre aunque cambiara lo que mide, para que se pueda
comparar contra la línea base — aunque **no** sea comparable: la línea base publicada
(913 ms p50 / 1336 ms p95) es **del camino por palabras**, y con oraciones la cifra sube
por construcción.

**Antes de citar un número, instrumentá la métrica.** Agregar la métrica va **antes** que
agregar la feature.

## Después de cambiar

1. **El otro lado tiene que quedar actualizado en el mismo trabajo.** Un cambio de
   contrato commiteado en un solo lado es un cambio a medias.
2. **Decile al owner, en el mensaje, qué se cambió, en qué repos y qué falta del otro
   lado.** Pendiente del otro lado es deuda visible, no un detalle: si queda algo, se dice
   con esas tres cosas.
3. **Sin commits en los hermanos** sin que el owner lo pida (PR contra `develop`). Acá sí
   se commitea directo a `main`.
4. **Cargá el skill `sesion-openspec`** y dejá rastro en los dos repos: qué se cambió, en
   qué, y de qué commit del hermano salió el dato.

## Cambios aditivos y cambios que rompen

| | Cómo se hace |
|---|---|
| **Campo nuevo** | Aditivo. El parser es tolerante a propósito: un backend viejo que mande campos que la app ya no espera, o al revés, **no la rompe**. Agregalo en los dos lados en el mismo cambio. |
| **Campo renombrado o borrado** | Rompe en silencio. Se necesitan los dos lados en el mismo commit lógico, o una ventana de compatibilidad con los dos nombres. |

## No confundir los tres repositorios

`docs/SISTEMA.md` tiene el detalle de cada hermano: el `CLAUDE.md` del backend, el
protocolo del STT, y por qué `../signai-avatar` **no** es la fuente de los GLB de esta
app.