# Sesión 2026-10-16 — Latencia: dónde medir y qué cambiar

**Fecha:** 2026-10-16 · **Rama:** `flujo-oraciones` · **Alcance:** investigación de
latencia del sistema completo. **Nada implementado todavía**: esto son las
decisiones y el plan que sale de ellas.

## Por qué arranca esta sesión

El 15 de octubre se cerró el flujo por oraciones en los tres repos. El 16 se
investigó cuánto tarda hoy el sistema de punta a punta y **dónde no hay
medición**. Salieron tres cosas: una puerta que se traba en el backend, cables que
no se sueltan al cerrar la app, y una contradicción entre el SDD y el spec.

## El número de partida

De punta a punta, con 28 frases reproducidas por el socket (backend y STT reales,
`qwen3:4b-instruct` en Ollama local, caché semántica vacía):

```
el usuario hablando ................................ 1,62 s
esperar el cierre por silencio ...................... 1,50 s   ← SILENCE_TIMEOUT_MS
inferencia de Whisper medium/cuda/fp16 ............. 0,24 s   ← stt_final_ms p50
traducir ............................................ 0,27 s   ← frío; en caché, 1 ms
──────────────────────────────────────────────────────────
depurado por el socket hasta el primer segmento ..... 4,01 s
```

De eso, 3,1 s son espera. **Y el tramo final —del segmento recibido hasta que el
avatar se mueve en pantalla— nunca se midió.** No hay número para esa parte.

## Decisiones tomadas

### D-1 — El SDD manda: la app representa cada oración apenas llega

**Decisión del owner (2026-10-16): el SDD manda.** Ante la contradicción entre el
SDD y el spec de la app, se sigue el SDD. La app deja de esperar a la frase
completa.

| Documento | Dice |
|---|---|
| `sign-ai-docs/diagramas/sdd-6.3:61` | "Reproduce cada oración en cuanto llega" |
| `sign-ai-docs/diagramas/sdd-7.4:42` | `Procesando` → `Reproduciendo`, etiqueta "primer segmento recibido" |
| `sign-ai-docs/plan-pruebas/00-bases/alcance.md:66-67` | "Cada oración traducida se envía a la aplicación apenas está lista, y el avatar la reproduce" |
| `sign-ai-backend/docs/android-integration.md:172-174` | "No hay que esperar a las demás: el avatar puede empezar a señar la primera mientras se traducen las siguientes" |
| `sign-ai-android/openspec/specs/ui/spec.md:268` (GLS-1) | "solo arma la cola cuando la frase está completa, para no ejecutar media frase" |
| `sign-ai-android/openspec/specs/ui/spec.md:300` (BRG-14) | "la app arma la cola entera y usa `setQueue` + `play()`" |

Cuatro documentos contra dos líneas. Las dos del spec se escribieron el
2026-10-15, en este mismo trabajo.

**Por qué la razón de GLS-1 no aplica:** "para no ejecutar media frase" venía del
camino por fragmentos, donde el backend mandaba palabras sueltas a mitad de frase.
Con oraciones, un `segment` **es** una oración completa.

**Esto no es la opción B** de `auditoria-flujo-2026-10-15.md` §7. La opción A dice
"el avatar representa oración por oración" y "primera seña: al terminar la
primera oración".

**Efecto medido:** con una frase fría de 3 oraciones, la primera estuvo lista a
1297 ms y la última a 1866 ms. La app esperaba las dos: **569 ms de avatar parado
con la primera seña ya disponible**.

**Qué hay que cambiar y en dónde:**

| Repo | Cambio |
|---|---|
| `sign-ai-android` | `flushSegments()` representa lo que haya e ignora `total`; corregir GLS-1 y BRG-14 |
| `sign-ai-android` | Reusar `appendQueue`/`playFrom`, que ya existen y no los llama nadie (BRG-14) |
| `sign-ai-docs` | `modificaciones/requerimientos.md`: el envío oración-por-oración pasa de *Propuesto* a *Acordado* |

**Corrección a una hipótesis propia:** sePensó que con la primera oración entrando
sola el adelanto automático dejaría de dispararse. **Es falso.** `CATCHUP_BACKLOG`
cuenta lo que falta *después* de la seña en curso: con 3 oraciones se alcanza en
los dos casos, con 2 oraciones no se alcanza en ninguno. Lo único que cambia es en
qué instante se prende. Queda para medir, no para cambiar.

### D-2 — La traducción sale del bucle de lectura, una a la vez

`sign-ai-backend/app/controller/router.py:76` hace
`await controller.handle_text(payload)` dentro del `while True` que lee audio.

**Medido** con `tools/verificar-bloqueo-lectura.py` (audio a 1x, traducción fría en
paralelo, muestreador independiente del bucle de recepción): traducción de 1091 ms
→ **991 ms de la voz del usuario invisible para el STT**, que entra de golpe al
terminar.

**Decisión del owner (2026-10-16): opción A.** La traducción va a su propia tarea,
pero solo hay una a la vez; el audio sigue fluyendo siempre.

**Por qué A y no "traducir en paralelo con reordenamiento":** la app ya impide
dos frases en vuelo (`MainScreenState.kt:693` en vivo, `:831` desde Traducir). El
comentario del código lo dice con la razón exacta de
`SignAiBottomBar.kt:114-115`: *"intercalados, el `index` de las oraciones deja de
corresponder al orden en que llegaron y el avatar representaría la frase al revés"*.
El buffer de reordenamiento resolvería un caso que la app ya prohíbe.

**Alineación verificada:** el pendiente "Desacoplar la traducción de la lectura del
STT" (`../signall-backend/docs/performance.md:676-681`) pide exactamente "lanzar cada
traducción como tarea y garantizar el orden de las frases" — A cumple las dos
mitades. `performance.md:331-334` lo nombra como requisito.
`../signall-docs/documentos/requerimientos/SignAI_Requerimientos_Consolidados_v0.16.md:160`
(RF-INT-SW-04) y `:553` (RNF-REL-01) exigen no bloquear el hilo principal.
Ningún documento exige traducir frases en paralelo. El docstring de
`controller.py:29-36` **ya afirma que esto está hecho** y no lo está.

### D-3 — Los cables colgando se arreglan junto con la puerta

**Medido:** abriendo y cerrando sesiones a propósito, las conexiones al STT solo
suman — 0 sesiones → 0 conexiones, 5 → 10, 10 → 20. Ninguna se suelta. El motivo
está en `router.py:120-123`: el `gather` espera a una segunda tarea
(`_forward_transcripts`) que bloquea en `recv()` esperando un mensaje del STT que
no llega nunca, así que el `finally` que cierra el socket no corre.

**Decisión del owner (2026-10-16): opción B** — se arregla junto con D-2, porque
toca el mismo archivo.

**Por qué no es urgente:** la memoria que queda es el audio de una frase, el modelo
se carga una sola vez y la GPU no se entera. **Por qué hay que hacerlo:** es lineal
y sin techo (un cable por sesión), y el medidor que lo delataría está roto —dice
siempre 1—, así que nunca se vería venir. Cuando se agoten los descriptores de
archivo el síntoma es "no me conecta", que es difícil de ubicar.

**Nota de ciclo de vida:** `docs/sessions/2026-10-15-vuelta-al-flujo-por-oraciones.md:105-113`
advertía que tocar el cierre de sesión es decisión del owner. Queda tomada (B).

### D-4 — Terminología: `representar`, y nunca `firmar`

**Decisión del owner (2026-10-16).** El término de la tesis es **representar**: el
avatar representa las señas. Y cuando el texto habla de mandar una frase, es
**enviar**.

`firmar` era una palabra inventada en los documentos internos de este repo, nunca
usada en `sign-ai-docs` para el avatar (0 apariciones). Se reemplazó en 38
archivos y 211 ocurrencias de los tres repos. Los identificadores en inglés
(`PlaybackState.Signing`, `signing`) **se quedan**: `signing` es la palabra
correcta en inglés para lengua de señas.

Se agrega a `AGENTS.md` una sección de cómo hablarle al owner: español neutro,
sin jerga sin explicar, números siempre con su origen, no inventar terminología,
una decisión por pregunta, y separar lo medido de lo supuesto.

### D-5 — Se saca el adelanto automático

**Decisión del owner (2026-10-16): quitar.**

El adelanto automático sube la velocidad del avatar a 1,6× cuando hay 2 o más
señas en la fila. Existe por un motivo que, según el owner, **no es el real**: se
creó para que el avatar alcance a quien habla, pero **quien lee el avatar es una
persona sorda que no está mirando a quien habla**. No hay carrera que empatar.

Lo que sí hace es **alterar la velocidad**, que es justo la variable que hay que
medir limpia. En `../signall-docs/plan-pruebas/01-diseno/personas/pruebas.md:165`
está **PR-PER-04** (obligatoria), que le pregunta a usuarios con LSC: *"¿La
velocidad es adecuada? 1 es muy lenta o muy rápida y 5 es adecuada"*. Si el avatar
va a 1,0× unas veces y a 1,6× otras sin avisar, la calificación mezcla dos cosas que
la prueba no separa.

**Además, no está en ningún documento de la tesis.** Aparece solo en `app.js` y en
`auditoria-flujo-2026-10-15.md:153,476` — un archivo que está **sin commitear**.
No está en el documento de requerimientos, ni en el plan de pruebas, ni en ninguno
de los 12 diagramas del SDD.

**Lo que sí está escrito es lo contrario:** `trazabilidad.md:169` y
`modificaciones/requerimientos.md:101` tratan la velocidad del avatar como
**parámetro de prueba que se registra**, no como algo que el sistema ajuste solo.

**A favor de quitarlo:** `sign-ai-backend/CLAUDE.md:63-66` — *"sin umbrales
numéricos en requisitos o lógica funcional"*. `CATCHUP_BACKLOG = 2` y
`CATCHUP_FACTOR = 1.6` son dos números mágicos que nadie eligió con un criterio
documentado.

**Efecto colateral que hay que medir después:** sin adelanto, en las frases largas
el avatar va más lento que quien habla. **Eso está bien** si quien lee no está
mirando a quien habla. PR-PER-04 es la que lo confirma.

**Nota:** `BRG-15` en `openspec/specs/ui/spec.md` describe esta función y hay que
marcarla como eliminada.

### D-6 — El tiempo de silencio, visible en el modo depurar de la app

**Propuesta del owner (2026-10-16), aceptada como trabajo.** El overlay de
depuración del avatar tiene que mostrar el `SILENCE_TIMEOUT_MS` que el STT está
usando, para poder ver en vivo si el cierre por frase se comporta bien.

Hoy el overlay (`app.js`, `Debug`) muestra la cola y el adelanto. El valor del
silencio no aparece en ninguna parte de la app, y vive en el STT, no en el backend.
Hay que decidir de dónde lo saca: `/health` del STT ya lo devuelve
(`{"silence_timeout_ms": 1500}`).

**Con esto se cierra la trampa de P-1:** si el valor está a la vista, cada corrida
de pruebas lo anota sin depender de que alguien lo recuerde.

---

## Plan de ejecución

Sigue la regla del spec (`spec.md:325`): **agregar la métrica antes que la
feature**. Sin esto, las fases 2 no se pueden afirmar en la tesis.

| # | Fase | Repos | Qué |
|---|---|---|---|
| 1 | **Que los números existan** | backend, app, stt | `llm` como métrica real (ya pendiente en `../signall-backend/docs/performance.md:756-763`); una métrica del tiempo que el bucle de lectura estuvo trabado; el mensaje de puente `signStart` con reloj propio; arreglar las dos gauges rotas y `stt_end_to_final_ms` |
| 2 | **La puerta** (D-2) | backend | `handle_text` a su propia tarea, una a la vez |
| 2 | **Los cables** (D-3) | backend | mismo archivo, mismo rato |
| 3 | **La primera oración** (D-1) | android | `flushSegments` + `appendQueue`/`playFrom` |
| 4 | **Tests del STT** | stt | hoy tiene cero; sin ellos, tocar la espera de 1,5 s es a ciegas |
| 5 | **El script de verificación** | android | el control de latencia pasa con p95 = 5 ms porque la caché está caliente; no está midiendo |

Fases 2 y 3 son independientes y no se pisan.

## Pendiente de decisión

### P-1 — La espera de 1,5 s: se queda en 1500, se prepara el experimento

**Decisión del owner (2026-10-16): el valor se queda en 1500 ms.** Se prepara el
experimento, no se cambia nada todavía.

Es el segundo bloque más grande de los 4,01 s. La razón escrita de no bajarlo está
en `sign-ai-stt/docs/CAMBIOSSTT.md:51-56` y el valor está literal en el diagrama
6.3 del SDD: bajarlo parte las frases donde la persona respira, y una frase partida
son dos oraciones y por lo tanto dos señas.

**Lo único que hay medido es 600 (descartado) contra 1500 (actual).** Nadie midió
800, 1000 ni 1200, y el valor actual está donde está porque es el del diagrama, no
porque se haya comprobado que sea el mejor.

**Lo que bloquea la medición:** los 28 fixtures tienen todos `pause_ms = 0`
(verificado sobre `fixtures/audio/*.json`). Son habla continua, sin silencios entre
palabras, así que **no pueden responder** a la pregunta "bajarlo parte frases donde
la persona respira": no hay respiración que medir.

**El experimento ya tiene la herramienta:** `sign-ai-stt/scripts/make_audio_fixtures.py`
tiene `--pause-ms` y es determinista. Generar un juego con pausas de 200, 400 y600
ms —lo que una persona hace al hablar— y medir cuántos cortes aparecen a 800,
1000, 1200 y 1500.

**Trampa antes de medir:** `plan-pruebas/00-bases/marco.md:176-179` obliga a
registrar la configuración de cada corrida y **la lista no incluye el tiempo de
silencio**, aunque `alcance.md:72` obliga a registrarlo. Sin eso el experimento no
es reproducible.

**Nota del owner:** depende mucho de quién habla, así que el valor no se fija con
una cifra de laboratorio sino con las pruebas.

### P-2 — El tamaño del modelo de voz: FUERA de este trabajo

**Aclaración del owner (2026-10-16): esto es del plan de pruebas del STT, no de
este trabajo.** Este trabajo es la latencia de la app y el backend. La comparación
de modelos es otra fase, más adelante. Queda anotada para cuando llegue.

Lo que sí importa de esta investigación, y que es de este trabajo: de 224 finales
del STT en las métricas del 16 de octubre, 187 devolvieron texto y 37 no (17%), y
92 fueron descartados por los filtros de ruido o de probabilidad. Si el filtro se
está comiendo audio real, es un problema de precisión **vivo**. Se deja anotado
para cuando haya voz humana; con audio generado por computadora no se puede
distinguir un descarte correcto de uno equivocado.

### P-3 — Cambios en documentos del owner (`sign-ai-docs`)

`sign-ai-docs` es del owner y no se commitea sin que lo pida.

| Documento | Qué |
|---|---|
| `modificaciones/requerimientos.md` | el envío oración-por-oración de *Propuesto* a *Acordado* |
| `plan-pruebas/00-bases/marco.md:176-179` | agregar el tiempo de silencio a la configuración obligatoria de cada corrida |
| toda la tesis | la tesis dice "reproducir" donde ahora decimos "representar" (D-4) |
| `plan-pruebas/00-bases/trazabilidad.md:151` | la salvedad de RF-INT-SW-04 blames a los embeddings y al segmentador; lo que bloquea segundos es el bucle de lectura, y no está anotado |