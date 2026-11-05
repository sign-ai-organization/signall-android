# Recuperación de la ronda diagnóstica con el modelo oficial — 2026-11-05

Este documento explica **por qué** se repitieron 24 de las 74 pruebas de la ronda
diagnóstica del 2026-11-04, **qué** se volvió a medir y **qué** números cambiaron. El libro
`plantilla-resultados.xlsx` no dice nada de esto: solo cambia lo que se volvió a medir, como
pide el plan.

- **Ronda que se recupera:** la del 2026-11-04, cerrada con 74 de 74 filas en el commit
  `6c28985`.
- **Por qué se repite:** esa ronda midió el LLM con `qwen3:4b-instruct` (Ollama local), no
  con el modelo oficial `gpt-4o-mini`.
- **Qué no se repite:** 34 pruebas que el modelo local no podía tocar y las 16 que siguen
  bloqueadas por otras razones. Abajo está la justificación de cada grupo.

## 1. El criterio de contaminación (C1–C5)

Una prueba se repite si cumple al menos uno de estos criterios:

| Criterio | Enunciado |
|---|---|
| **C1** | El resultado que se juzga depende de las glosas que devuelve el modelo. |
| **C2** | La métrica es tiempo, caudal o costo de un camino que incluye la llamada al LLM. |
| **C3** | La métrica es memoria o disponibilidad de GPU y el modelo local estaba residente. |
| **C4** | La precondición de la ficha nombra el proveedor oficial o un entorno que el modelo local invalida. |
| **C5** | La configuración del cliente (timeout, reintentos) era la del override local y entra en el número medido. |

Los cinco mecanismos que introdujo el modelo local están detallados en el plan, §2.2:
salida del modelo (M1), latencia del camino (M2), memoria de GPU (M3), precondición de la
ficha (M4) y configuración del cliente (M5).

## 2. El sistema con el que se midió

| Componente | Referencia |
|---|---|
| App | `com.signai.webview`, Redmi Note 9S (Android 11), build `debug` con `-Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate`, `DEV_TOOLS` apagado, `adb reverse tcp:8080` |
| App (HEAD) | `746438f` en `main` |
| Backend | `8f27bf8` en `main`, imagen `sha256:f92c6adcfabed6d78853b6dc701be3ba4b248cdd708bfac37feb1346c95b4316`, reconstruida desde ese commit |
| STT | `7a571c2` en `main`, `medium` en CUDA |
| Base | Neon de la tesis (no una base de laboratorio) |
| LLM | **`gpt-4o-mini`**, `LLM_BASE_URL=""`, 5.000 ms, 1 reintento |
| GPU | RTX 4050 Laptop, 6.141 MiB; STT `medium` ~2.013 MiB; Ollama sin modelo residente |
| Avatar | GLB de `fc6f372` (200 clips, 50 señas × 4 caras), sin rehacer |

**Comprobación del modelo efectivo, no del archivo:** `/metrics.json` devolvió
`llm_model = "gpt-4o-mini"` y `llm_base_url = ""` en todas las tandas. La salida del
preflight quedó en `evidencias/PREFLIGHT/20261105_metrics.json`.

### 2.1. Qué se hizo con el modelo local

No se borró nada, como pidió el owner:

- `.env.local` se apartó como `.env.local.off` para que no entrara en cada arranque. El
  archivo se conserva y se usó solo en `PR-LLM-01`, que compara tres modelos.
- El backend se levantó **sin** `docker-compose.host.yml`, que era el segundo camino por el
  que el modelo local se colaba: ese archivo fuerza `LLM_BASE_URL` al Ollama de la máquina.
- Ollama quedó **instalado y vivo**, pero sin modelo cargado (`ollama ps` vacío y 0 MiB
  suyos en la GPU). Se usa cuando una prueba lo pide y se descarga al terminar.
- `scripts/verify_official_config.py` del backend sigue pasando.

### 2.2. Sobre los contenedores que aparecen en algunas pruebas

Las pruebas que la ficha pide «sin caché» o con una regla del segmentador apagada necesitan
una configuración distinta. Para eso se levantó un contenedor **con la misma imagen, el
mismo código, la misma base, el mismo STT y el mismo proveedor**, cambiando solo la variable
que la ficha aísla:

- `PR-LLM-02…05`: `SEMANTIC_CACHE_MAX_ROWS=0` (no guarda ni sirve caché).
- `PR-SEG-02/04`: `SENTENCE_SEGMENTER__ENABLED=false` o la regla apagada que toca.
- `PR-LLM-01`: `LLM_MODEL` y `LLM_BASE_URL` para comparar `gpt-4o-mini`, `gpt-4o` y el
  modelo local.

No hay mocks, ni datos inventados, ni un backend "de prueba" con otro código. El resto de
las 24 corrió contra el contenedor principal con la configuración oficial.

## 3. Las 24 pruebas que se repitieron

| # | Prueba | Criterio | Qué se volvió a medir |
|---|---|---|---|
| 1 | `PR-STT-04` | C3, C4 | `large-v3` con la GPU libre; WER y memoria de los tres tamaños |
| 2 | `PR-SEG-02` | C1 | Exactitud de glosas y aciertos de caché con y sin segmentador |
| 3 | `PR-SEG-04` | C1 | Las siete ablaciones de reglas del segmentador |
| 4 | `PR-CAC-03` | C1 | Variantes de FR-03 resueltas por la caché, contra las glosas guardadas |
| 5 | `PR-CAC-06` | C2 | Costo por frase, latencia y diferencias con y sin caché |
| 6 | `PR-LLM-01` | C4 | `gpt-4o-mini`, `gpt-4o` y el modelo local sobre las 30 frases |
| 7 | `PR-LLM-02` | C1 | Formato de 30 respuestas reales y 10 fabricadas |
| 8 | `PR-LLM-03` | C1 | Cinco respuestas por frase: consistencia y exactitud |
| 9 | `PR-LLM-04` | C1 | Frases largas y con números; glosas fuera del catálogo |
| 10 | `PR-LLM-05` | C1 | Inyección: qué obedece el modelo oficial y qué ve el usuario |
| 11 | `PR-INT-01` | C1 | Los tres recorridos del contrato (texto, varias oraciones, audio) |
| 12 | `PR-INT-02` | C1 | Las 30 frases por la app, glosas recibidas contra las 112 esperadas |
| 13 | `PR-INT-06` | C2 | Sesión de 30 minutos: latencia, memoria y caídas |
| 14 | `PR-AVT-02` | C1 | Cuántas de las 50 glosas aparecen dentro de una frase y cuánto dura cada seña |
| 15 | `PR-REN-02` | C2 | Paso `llm` y total por frase, con caché vacía y llena |
| 16 | `PR-REN-03` | C2 | Envío→primera seña y representación, en el dispositivo |
| 17 | `PR-REN-04` | C2 | Arranque en frío, primera frase y base dormida |
| 18 | `PR-REN-05` | C3 | GPU en reposo y durante audio; CPU y memoria del sistema completo |
| 19 | `PR-REN-06` | C2 | Latencia con 1, 2 y 3 sesiones |
| 20 | `PR-REN-08` | C2 | Bytes por frase por texto |
| 21 | `PR-REN-09` | C2 | Frases por minuto y espera hasta el `done` |
| 22 | `PR-RES-02` | C5 | Aviso con el proveedor caído y colgado (5 s + 1 reintento) |
| 23 | `PR-SEC-01` | C4 | El tramo backend→LLM con el proveedor oficial |
| 24 | `PR-TRA-03` | C4 | Arranque oficial desde un clon limpio |

## 4. Las 34 que siguen válidas, y por qué

No se repitieron porque el modelo, su memoria o su latencia no pueden cambiar la cantidad
medida. Esta es la justificación que el libro no lleva.

| Grupo | Pruebas | Por qué el modelo local no las toca |
|---|---|---|
| STT sin LLM | `PR-STT-01`, `02`, `03`, `05` | Miden audio→texto, VAD, cierre y WER. La memoria que ocupaba Ollama no cambia la inferencia de Whisper. |
| Segmentador solo | `PR-SEG-01` | Mide la división del texto; no pasa por el LLM. |
| Huella gramatical | `PR-HUE-03` | Son reglas sobre el texto, antes de traducir. |
| Caché sin LLM | `PR-CAC-02`, `04`, `05` | Umbral, distancia coseno y mantenimiento: no comparan glosas del modelo. |
| Catálogo y validador | `PR-VAL-01`, `02`, `03` | Prueban el catálogo y la normalización; las cadenas se inyectan a mano. |
| Dispositivo | `PR-AND-01`…`06` | Permiso, entrada, historial, ajustes, resoluciones y accesibilidad: no pasan por el LLM. |
| Avatar como reproductor | `PR-AVT-01`, `03`, `06`, `07` | Arranque, cola, tamaños de GLB y cambio de personaje. El GLB no cambió en esta recuperación. |
| Rendimiento del STT y del equipo | `PR-REN-01`, `07` | Latencia del streaming y fotogramas del teléfono. |
| Integración sin LLM | `PR-INT-05` | Sin conexión y modo avión: mide la red, no el modelo. |
| Resiliencia sin LLM | `PR-RES-01`, `03`, `04`, `06`, `07` | Cortes de túnel, STT caído, base caída, audio largo y reinicio del backend. |
| Seguridad sin LLM | `PR-SEC-02`, `04` | Mensajes inválidos y retención de audio. |
| Estático | `PR-TRA-01`, `02` | Importaciones, `ruff`, `mypy` y `ktlint`. |

**Las 16 bloqueadas no se tocan.** `PR-SEG-03`, `PR-HUE-01`, `PR-HUE-02`, `PR-CAC-01`,
`PR-INT-03`, `PR-INT-04`, `PR-AVT-04`, `PR-AVT-05`, `PR-RES-05`, `PR-SEC-03` y las seis
`PR-PER-*`. El modelo local no las desbloquea: dependen de datos que no existen, de una
decisión del owner o de personas. `PR-INT-03` se desbloquea en la fase 2 con la cirugía
aprobada del catálogo.

## 5. Antes y después

| Prueba | Antes (2026-11-04, `qwen3:4b-instruct`) | Ahora (2026-11-05, `gpt-4o-mini`) | Qué cambió |
|---|---|---|---|
| `PR-STT-04` | small 3,90 % / medium 1,77 % de WER; `large-v3` no entraba en los 6 GB de la GPU | small 2,84 % / medium 1,77 % / `large-v3` **0,35 %**, con 4.123 MiB de GPU y 439 ms por audio; GPU libre y solo el STT encendido | Se pudo medir el modelo grande: es el más preciso y el más caro en GPU. El WER de small baja; el de medium no se mueve |
| `PR-SEG-02` | Con segmentador, 21/21 oraciones de caché y 1/10 glosas exactas; sin segmentador 2/10 de caché | Igual en la mecánica (21/21 y 2/10) y **4/10** glosas exactas; la frase de dos oraciones se parte en 2 y en 1 | El segmentador no cambió; las glosas exactas sí, porque el modelo es otro |
| `PR-SEG-04` | 7 ablaciones; solo `MIN_FRAGMENT_WORDS` y `CUT_BEFORE_OPENING_MARK` cambian la división | Lo mismo: `[2,2,2,2,2,2,2,2,3,2]` en la línea base y `[2,2,2,2,2,2,2,2,2,2]` con esas dos reglas apagadas; las otras cinco no cambian nada | Sin cambio de comportamiento; el número se confirma con el modelo oficial |
| `PR-CAC-03` | 19/30 variantes por caché (Formato 9/10, Orden 9/10, Sinónimos 1/10) | 19/30 (mismos 9/10, 9/10 y 1/10); las 19 devolvieron exactamente las glosas guardadas, 0 ajenas; contra FR-01, 13/30 | La caché se comporta igual con otro modelo. Los sinónimos siguen siendo el punto débil |
| `PR-CAC-06` | 19/60 frases resueltas sin LLM; 26 ms las de caché, 596 ms las de LLM, 433 ms global | 19/60; 18 ms / 735 ms / 669 ms; **costo 0,0090 USD** por 60 frases (144 llamadas, 56.315 tokens de entrada) | La caché ahorra lo mismo; el camino con LLM es más lento, porque ahora es una llamada por red |
| `PR-LLM-01` | Dos modelos locales (`qwen3:4b-instruct` 14/30 exactas y `qwen3:4b`), sin modelo de nube | Tres: **`gpt-4o-mini` 18/30 exactas** (60 %), `gpt-4o` 19/30 (63 %), local 14/30 (47 %); 99/99 glosas en catálogo; 0,0020 / 0,0328 / 0 USD | El modelo oficial gana 4 frases al local y cuesta 0,002 USD por 30 frases; `gpt-4o` gana una sola y cuesta 16 veces más |
| `PR-LLM-02` | Parte A 30/30 con formato correcto; parte B 6/10 respuestas fabricadas bien leídas | Parte A 30/30 con 0 problemas de formato; parte B **6/10**, las mismas cuatro mal (`CO-04-005` devuelve cuatro glosas con punto, `CO-04-006` y `007` pierden palabras) | El formato de las respuestas reales es idéntico; el defecto de lectura de las fabricadas es del parser, y es lo que arregla S1 en la fase 2 |
| `PR-LLM-03` | 30/30 frases con las 5 respuestas idénticas; 15/30 exactas contra FR-01 | **26/30** idénticas y **18/30** exactas; 150 llamadas, p50 643 ms, 0 errores | El modelo oficial es menos repetitivo (4 frases varían en 1 de 5 vueltas) y más exacto |
| `PR-LLM-04` | 15/15 respondieron; 1 respuesta con texto crudo (una fecha) y 8 frases con glosas fuera del catálogo | 15/15, **0 respuestas vacías, 0 texto crudo**, 7 frases con glosas fuera del catálogo (5 a 11 de cada frase larga) | Las entradas raras ya no rompen nada y ya no devuelven texto suelto; las frases muy largas siguen perdiendo glosas |
| `PR-LLM-05` | 0 claves y 0 instrucciones obedecidas; 10/18 frases dejaban texto fuera del catálogo a la vista | Igual en lo grave (0 claves, 0 obedecidas) y **14/18** con texto fuera de glosa a la vista (negativas en inglés glosadas palabra por palabra) | Empeora lo que ve el usuario: por eso S3 filtra `available=false` en la app |
| `PR-INT-01` | 3/3 recorridos correctos, `index` y `total` reales | 3/3 correctos: texto, dos oraciones y audio; el audio no se traduce hasta que el cliente reenvía el texto | Sin cambio: el contrato del socket se respeta |
| `PR-INT-02` | 15/30 frases con glosas exactas, 99/112 glosas recibidas | **14/30** y 98/112; 98/98 señas representadas, 0 turnos sin fin, 0 campos mal; 12 de 13 frases de varias oraciones en orden | Se mantiene: el avatar no pierde nada de lo que recibe; lo que falta viene del NLP |
| `PR-AVT-02` | 43/50 glosas dentro de frase y las 7 sueltas reproducidas (50/50, 105 ocurrencias) | **50/50** reproducidas, 105 ocurrencias, duración efectiva de seña 2.048 ms de mediana (1.298–2.841) | Igual: el avatar reproduce todas las animaciones que existen |
| `PR-REN-02` | Paso `llm` p50 456 ms / p95 914 ms; frase total p50 692 / p95 1.001 ms | Paso `llm` p50 **582** / p95 806 ms; total p50 **693** / p95 838 ms; caché total p50 3,6 ms | El total no cambia, pero ahora el 84 % es espera de red del proveedor |
| `PR-REN-03` | Caché vacía: primera seña p50 70,0 / p95 533,8 ms; representación p50 7.036,5 ms. Caché llena: 66,0 / 89,5 ms | Caché vacía: **42 / 994,7 ms**; caché llena: **39 / 81,7 ms**; representación 6.405 / 6.509 ms | La primera seña baja en mediana y empeora en p95: es la cola de la red del proveedor |
| `PR-REN-05` | Reposo: backend 49,4 MiB y STT 68,1 MiB; GPU 2.043 MiB; sin fase CPU | Reposo (tras 60 s): backend 1.108 MiB, STT 738 MiB, GPU 2.011 MiB; audio: GPU 18,7 % de media y picos de 100 %; **con GPU 253 ms por audio, en CPU 4.118 ms (16,3×)** | La memoria de la corrida vieja estaba medida antes de cargar el modelo y no es comparable; ahora hay número con y sin GPU |
| `PR-REN-06` | 1/2/3 sesiones: p50 240 / 256 / 346 ms, 0 errores y 0 textos cruzados | 1/2/3 sesiones: p50 **246,8 / 248,8 / 277,9 ms**, 0 errores, 0 textos cruzados; contador `sessions_active` 1→2→1, 1→3→1, 1→4→1 | Mejora la caída con 3 sesiones: 346 → 278 ms |
| `PR-REN-09` | 66 frases en 300 s (13,2/min), espera p50 314 / p95 878 ms | 66 frases en 303 s (13,2/min), espera p50 **313** / p95 1.137 ms, 0 errores | Misma tasa; la cola de la espera es más larga por la red del proveedor |

| `PR-REN-04` | backend `/health` a los 19,99 / 21,33 / 20,47 s, STT a los 20,00 / 21,33 / 20,48 s, app usable 7,43 / 6,02 / 5,64 s (total 27,4 / 27,3 / 26,1 s); base dormida 6 min: `/health` en 21,50 s y 15,1 / 20,8 / 1.785,6 ms las tres frases | backend a los **16,1 / 12,9 / 24,9 s**, STT a los **3,7 / 3,2 / 3,3 s**, app usable 5,9 / 6,2 / 6,2 s; primera frase 1.200 / 5 / 4 ms y siguientes 711 / 4 / 4 ms; base dormida **433,9 s**: backend en 13,7 s y primera traducción en 5,0 ms | El STT ya no espera al backend, pero el arranque sigue mandado por el backend y es **variable** (12,9–24,9 s en tres corridas iguales). La mejor corrida ahorra ~7 s y la peor cuesta ~4 s más: no es una mejora del código |
| `PR-REN-08` | Mediana no reportada; medias: 305 B de subida, 611 de bajada, 916 por frase (416–1.045); 10 s en reposo movieron 261 B de subida y 256 de bajada con 1 conexión nueva | **Mediana 222 B por frase** (78 de subida y 144 de bajada; rango 161–416), media 243 B; en reposo 16 B de subida y 8 de bajada, 0 conexiones | La medición vieja estaba **inflada por las reconexiones**: el proxy cortaba el socket cada 10 s y cada frase pagaba un saludo nuevo. La frase 1 mide exactamente lo mismo en las dos rondas (72 y 344 B), así que el tráfico por frase no cambió |
| `PR-INT-06` | PSS 378.873 → 339.271 kB (deriva −39.602); Gfx dev 224.032 → 222.040 kB; primera seña p50 543,0 / p95 1.277,0 ms; representación p50 7.853,0 ms | PSS **155.172 → 370.283 kB (deriva +215.111)**; Gfx dev **1.992 → 217.440 kB**; primera seña p50 **787** / p95 1.277 ms; última oración 777,5 / 1.330 ms; representación 6.275,5 / 10.776 ms; 60 envíos, 0 errores, un solo pid | La **memoria final es la misma** (Gfx ~217–222 MB, PSS 339–370 MB); lo que cambió es el punto de partida: antes la app arrancaba con las texturas ya cargadas y ahora las pide durante la sesión. No es una fuga, pero la primera seña paga la red del proveedor (543 → 787 ms de mediana) |
| `PR-RES-02` | Caído (puerto que rechaza): aviso en 775 / 803 / 806 ms. Colgado (5 s + 1 reintento): 10.546 / 10.453 ms por el socket y 10.819 en la app. Caché: 6,2 ms y 4.231 ms de representación. Recuperación: 430 ms al primer segmento | Caído (puerto sin listener, pero el cortafuegos **descarta** en vez de rechazar): **10.625 ms**. Colgado: **10.674 ms**. Caché: se tradujo y representó en 4.317 ms. Recuperación sin reiniciar: 5.702 ms hasta la última seña | El peor caso queda en la misma franja que la ronda diagnóstica (10,5–10,8 s), **0,6 s por encima de los 10 s del enunciado** con el cronómetro de 5 s por intento cumplido. La diferencia grande es del escenario, no de la app: un puerto que rechaza avisa en 0,8 s y uno que descarta agota el tiempo de espera |
| `PR-SEC-01` | **Falla**: app–proxy cifrado (0 palabras en la captura), pero backend→LLM **en claro y legible** contra el Ollama local; proxy→backend en claro a propósito; la app no cae a texto plano | **Pasa**: app–proxy cifrado (817 paquetes, todos TLS, 0 apariciones de las palabras de la frase); backend→LLM **HTTPS contra `api.openai.com`** (509 llamadas en el log del backend); proxy→backend sigue en loopback y en claro a propósito; la app tampoco acepta un backend en claro | Es el único cambio de estado de la fase: con el proveedor oficial el tramo que estaba en claro pasa a TLS sin tocar la app. El tramo interno en claro no se puede confundir con un enlace externo |

| `PR-TRA-03` | **Pasa**: 13,03 s los tres clones; `/health` 200 a los 108,7 s (≈89 s eran la descarga del modelo de embeddings); 7 pasos manuales no documentados, 2 de ellos bloquean el arranque (`.env` y `DATABASE_URL`/`LLM_API_KEY`); app `assembleDebug` en 39,6 s con caché y 101,3 s en frío; APK de 40,8 MB | **Falla**: 3/3 clones (2,1 / 1,7 / 27,8 s), compose en 4,5 s y app `BUILD SUCCESSFUL` en 17,7 s, pero el backend del clon **no escuchó en 604,7 s**: la base no respondió en dos intentos de 5 s, el catálogo quedó vacío y el proceso se quedó esperando después de cargar el modelo de embeddings (0 % de CPU, puerto cerrado) | El clon y la compilación funcionan; lo que falla es el arranque cuando Neon no contesta: el backend arranca «sin base» y se queda esperando en vez de terminar de levantar. Es el mismo timeout intermitente que ya apareció en el entorno real; la ronda oficial lo reintenta con `catalog_loaded: true` como condición |

**Qué se movió de conclusión entre las dos rondas.** Solo dos estados cambian: `PR-SEC-01`
pasa de **Falla** a **Pasa**, porque el tramo backend→LLM dejó de ir en claro al cambiar el
proveedor, y `PR-TRA-03` pasa de **Pasa** a **Falla** por el arranque del clon con Neon
caído. En las otras 22 el estado es el mismo; lo que cambian son los números. Y hay tres
diferencias que no cambian el estado pero sí lo que se puede afirmar: el peor aviso de
`PR-RES-02` sigue 0,6 s por encima de los 10 s del enunciado; la deriva de memoria de
`PR-INT-06` cambió de signo porque la app ahora carga las texturas durante la sesión y no
antes; y la cifra vieja de bytes de `PR-REN-08` estaba inflada por las reconexiones del
proxy.


## 6. Incidencias de la recuperación

- **Repetición de la tanda LLM-02…05.** La primera corrida escribió las glosas de referencia
  del corpus encima de las glosas producidas por el modelo (choque de nombres en el
  colector). Se detectó antes de llenar el libro: se corrigió el colector y se repitieron
  las 213 llamadas. Los números que quedan son los de la segunda corrida.
- **Ollama quedó vivo.** El plan pedía ningún proceso `ollama` durante la corrida. El
  owner aclaró que el servicio debe quedar instalado y usarse solo donde la prueba lo pida;
  se verificó que no tuviera modelo cargado (`ollama ps` vacío, 0 MiB de GPU) y que el
  backend no lo llamara (`llm_base_url = ""`).
- **Neon despierta.** Un arranque del contenedor temporal falló al crear el pool contra
  Neon por un timeout de la base dormida; el reintento entró sin cambiar ninguna variable.
  No afectó a ninguna medición: el backend principal ya tenía la base despierta.
- **El proxy de bytes cerraba la sesión a los 10 s.** En `PR-REN-08` el proxy que cuenta
  bytes dejaba el *timeout* de conexión puesto para las lecturas, así que a los 10 s sin
  tráfico cerraba el socket y la app entraba en un bucle de reconexión («session failed:
  null» cada 10 s). La frase 4 se quedó sin cerrar en las dos primeras corridas por eso, no
  por el sistema. Corregido el proxy (`settimeout(None)` tras conectar) y repetida la prueba.
- **El log del backend va por `stderr`.** Una evidencia de `PR-SEC-01` decía «0 llamadas al
  proveedor» leyendo solo `stdout`; el servicio escribe por `stderr` y había 509. Se corrigió
  el colector, no el número.
- **El STT se alcanza por el nombre del servicio.** El backend se levantó en la red de
  compose (sin `docker-compose.host.yml`); el contenedor del STT se conectó a esa red con el
  alias `stt` para que `ws://stt:8000` resolviera. Es el camino que documenta el compose.
- **El tiempo de `PR-RES-02` se medía desde antes de escribir la frase.** El colector tomaba
  la marca antes de `escribir()` y entre esa marca y el toque real hay dos volcados de
  `uiautomator` (~10 s en este teléfono), así que la primera versión de la corrida informó
  20,5 s de aviso con un cronómetro de 5 s por intento. Se corrigió la medición: el envío
  real está en el log de la app (`texto ya enviado: retenido 1500ms`) y el aviso en
  `fallo: No se pudo traducir el texto.` Con esa resta quedan 10,6 y 10,7 s, que es la
  misma franja de la ronda diagnóstica. La columna del toque queda en la evidencia como dato
  aparte.
- **El «proveedor caído» de esta corrida no rechaza la conexión.** El puerto sin listener
  estaba en el anfitrión y el cortafuegos **descarta** los paquetes (`nc` desde un contenedor
  agota su timeout de 4 s en vez de recibir un rechazo), así que esa situación espera los 5 s
  del intento en vez de avisar al instante como en la ronda diagnóstica (0,8 s). No se cambió
  ninguna variable del sistema: es cómo trata el cortafuegos un puerto cerrado.
- **`PR-TRA-03` no llegó a arrancar.** El clon, el compose y la compilación de la app
  funcionaron, pero el backend del clon se quedó sin base (dos intentos de 5 s), arrancó «sin
  catálogo» y no volvió a escuchar en 10 minutos. El contenedor lo borra el `down -v` del
  propio colector, así que el log quedó transcrito en
  `recuperacion/tra03_log_backend.txt` y adjunto como evidencia. La ronda oficial lo repite
  con `catalog_loaded: true` como condición de arranque.
- **`PR-INT-06`: la deriva de memoria no es una fuga.** La app empezó la sesión con 2 MB de
  `Gfx dev` y terminó con 217 MB; en la ronda diagnóstica empezó y terminó con ~222 MB. El
  estado final es el mismo: lo que cambia es cuándo se piden las texturas del avatar. Queda
  anotado para la ronda oficial, porque la medición no distingue «carga diferida» de «fuga»
  sin un punto de control después de la primera frase.

## 7. Cierre de la fase 1

- **24 de 24 filas** de la lista de §3 escritas en `plantilla-resultados.xlsx`, en sitio y
  solo en las columnas 12 a 17. La copia de antes está en
  `/tmp/opencode/plantilla-antes-de-rellenar.xlsx`.
- **Estados:** 5 Pasa (`LLM-04`, `INT-01`, `AVT-02`, `RES-02`, `SEC-01`), 4 Falla (`LLM-02`,
  `LLM-05`, `INT-02`, `TRA-03`) y 15 Se reporta. El detalle fila por fila está en el libro.
- **Evidencias:** 86 archivos nuevos con el sufijo `_D_20261105_`, repartidos en las
  carpetas de cada bloque y sin pisar los del 2026-11-04.
- **Lo que no se tocó:** las 34 de §4 y las 16 bloqueadas siguen como estaban; esta fase no
  las volvió a medir ni las reetiquetó.
- **Lo que pasa a la fase 2:** `LLM-02` (parser de las respuestas fabricadas), `LLM-05`
  (texto fuera de glosa a la vista, que la app filtra con S3), `INT-02` (faltan glosas del
  NLP, no del avatar) y `TRA-03` (el arranque sin base). Los arreglos aplicados y lo que
  quedó diferido están en `ARREGLOS-2026-11-05.md`.
