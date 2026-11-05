# Plan de recuperación y ronda oficial — 2026-11-05

Este documento **no ejecuta nada**: es el plan de la sesión que viene. Describe qué se
descubrió al cerrar la ronda diagnóstica, qué pruebas quedaron contaminadas por el modelo
local, cómo se recuperan, qué se arregla después y cómo se corre la ronda de evaluación con
subagentes sin que se pisen ni se queden en loop.

- **Estado de partida:** ronda diagnóstica cerrada, **74 de 74** filas (commit `6c28985`).
- **Motivo del plan:** la ronda midió con **Ollama local `qwen3:4b-instruct`**, no con el
  modelo oficial `gpt-4o-mini`.
- **Regla de oro:** no se pasa a la fase 2 sin cerrar la fase 1, y no se pasa a la fase 3 sin
  cerrar la fase 2. Cada fase tiene su criterio de cierre y su entregable.
- **Lo que el owner pidió:** dejar `gpt-4o-mini` como modelo por defecto y **no borrar** la
  posibilidad de usar el modelo local; repetir solo las pruebas efectivamente contaminadas;
  arreglar después; correr la ronda completa con subagentes; actualizar el libro de
  diagnóstico y entregar dos Excel.

**Objetivo de la sesión ejecutora (sirve como `goal`):** terminar las tres fases de este
plan —recuperar con `gpt-4o-mini` las 24 pruebas contaminadas y actualizar el libro
diagnóstico, aplicar y verificar los arreglos P0 con la cirugía de `PR-INT-03`, y correr la
ronda de evaluación de 74 pruebas con subagentes— dejando `resultados-consolidados.xlsx` y
los MD de las dos rondas.

---

## 1. Punto de partida

### 1.1. Lo que ya existe

| Pieza | Estado |
|---|---|
| `plantilla-resultados.xlsx` | 74 filas, columnas 12–17 completas, sin celdas vacías |
| Evidencias | 261 archivos en `evidencias/<BLOQUE>/`, convención `PR-<BLOQUE>-<NN>_D_20261104_<nn>` |
| `ANALISIS-2026-11-04.md` | 74/74: **21 Pasa · 13 Falla · 24 Se reporta · 16 Bloqueada** |
| `PRUEBAS-2026-11-04-1626.md` | Bitácora y matices de la ronda |
| `CONCLUSIONES-2026-11-04.md` | Problemas por causa; **escrito con el corte de 67/74**, desactualizado en conteos |
| `openspec/sessions/2026-11-04-ronda-diagnostica-74-pruebas.md` | Bitácora de sesión |

### 1.2. El sistema que se midió

| Componente | Versión / estado |
|---|---|
| App | `com.signai.webview`, Redmi Note 9S (Android 11), `DEV_TOOLS` apagado, `adb reverse tcp:8080` |
| Backend | commit `c8aa516` **más cambios sin commitear** en `controller`, `router`, `semantic_cache`, `database`, `settings`, `main` (y más archivos) |
| STT | commit `7a571c2`, `medium` en CUDA |
| Base | Neon de la tesis (no una base de laboratorio) |
| LLM | **Ollama local `qwen3:4b-instruct`** por `.env.local`; 2.776 MiB de GPU |
| GPU | RTX 4050 Laptop, 6.141 MiB; STT 1.988 MiB; total 4.792/6.141 |

### 1.3. La deuda documental que deja la ronda

- `CONCLUSIONES-2026-11-04.md` sigue diciendo **67 de 74** y marca siete filas como
  pendientes. La ronda ya está cerrada. En la fase 1 se le agrega una sección
  «Actualización» con los números recuperados; no se reescribe el documento.
- El backend tiene cambios sin commitear. Sin un commit de referencia, la fase 1 no es
  reproducible: hay que congelarlo antes de empezar (decisión D1).
- `RNF-AV-03` está duplicado en el SRS (`main.tex:2757` y `2892`) y el SRS dice que el APK
  pesa 78,19 MB contra 38,94 MB debug / 33,88 MB release medidos. Son correcciones de
  documento, no de código.

---

## 2. El hallazgo que ordena todo: la contaminación por el modelo local

### 2.1. Cómo entró el modelo local

No fue un accidente de una prueba: fue una configuración permanente de la máquina.

1. `app/config/settings.py` declara `env_file=(".env", ".env.local")` y **pydantic lee
   `.env` primero y `.env.local` después**. Cualquier valor de `.env.local` gana.
2. En esta máquina, `.env.local` existe y contiene `LLM_MODEL=qwen3:4b-instruct`,
   `LLM_BASE_URL=http://host.docker.internal:11434/v1`, `LLM_TIMEOUT_MS=10000` y
   `LLM_MAX_RETRIES=0`.
3. `docker-compose.yml` pasa `.env.local` al contenedor cuando el archivo existe
   (`required: false`). No hay que hacer nada para que entre.
4. Además, `docker-compose.host.yml` **fuerza** `LLM_BASE_URL=http://127.0.0.1:11434/v1`
   como variable de entorno. Aunque `.env.local` no exista, ese override vuelve a apuntar
   al Ollama local.
5. Ollama mantenía **2.776 MiB** de los 6.141 de la GPU durante toda la ronda.

El archivo `.env` sí tiene la configuración oficial (`gpt-4o-mini`, `LLM_BASE_URL` vacío,
5.000 ms, 1 reintento) y `scripts/verify_official_config.py` ya verifica que sea así. El
problema no es el diseño: es que **nadie comprobó el modelo efectivo del servicio que estaba
corriendo**, y la evidencia no lo registró.

### 2.2. Los cinco mecanismos de contaminación

| Mecanismo | Qué cambia | Pruebas afectadas |
|---|---|---|
| **M1. Salida del modelo** | Las glosas que devuelve `qwen3:4b-instruct` no son las de `gpt-4o-mini`: cambia la precisión, las omisiones, los sinónimos y el texto fuera del catálogo | LLM-01…05, INT-01, INT-02, AVT-02, SEG-02, SEG-04, CAC-03 |
| **M2. Latencia del camino** | El p50/p95 del pipeline incluye la inferencia local (604 ms por frase en LLM-01, 456 ms en REN-02) y el costo es 0 porque el modelo es local | REN-02, REN-03, REN-04, REN-06, REN-08, REN-09, INT-06, CAC-06 |
| **M3. Memoria de GPU** | Ollama ocupa 2.776 MiB; con el STT `medium` el sistema queda en 4.792/6.141. Eso es lo que impidió cargar `large-v3` | STT-04, REN-05 |
| **M4. Precondición de la ficha** | `PR-STT-04` pide «solo el servicio de transcripción encendido» y `PR-SEC-01` pide que el enlace con el LLM sea HTTPS. Con Ollama residente y HTTP local, las dos precondiciones eran imposibles | STT-04, SEC-01, LLM-01 |
| **M5. Configuración del cliente** | `.env.local` cambia `LLM_TIMEOUT_MS` a 10.000 y `LLM_MAX_RETRIES` a 0. El «peor caso» de `PR-RES-02` se midió con esa configuración, no con la oficial | RES-02 |

Del mismo momento (no del modelo) viene la violación de precondición de `PR-STT-04`: la
máquina tenía el sistema completo arriba, no solo el STT.

Los cuatro defectos de operación que hicieron posible la contaminación se citan en la tabla
de arreglos como **E1–E4**:

| ID | Defecto de operación |
|---|---|
| **E1** | `.env.local` existe en la máquina y entra sin avisar en cada arranque. |
| **E2** | `docker-compose.host.yml` fuerza `LLM_BASE_URL` al Ollama local, aunque no haya `.env.local`. |
| **E3** | La evidencia no registra el modelo efectivo con el que se corrió. |
| **E4** | `PR-STT-04` corrió con el sistema completo encendido, no solo con el STT. |

### 2.3. Qué NO contamina

- Todo lo que mide el **dispositivo**: permisos, historial, ajustes, resoluciones,
  accesibilidad, fotogramas, cambio de avatar.
- Todo lo que mide **reglas o datos**: la huella gramatical (`PR-HUE-03`), el catálogo y el
  validador (`PR-VAL-*`), el índice único, la importación entre componentes.
- Todo lo que mide **audio→texto** sin pasar por el LLM: `PR-STT-01`, `02`, `03` y `05`.
  La memoria ocupada no cambia el WER ni el VAD.
- Las **trampas de la caché** por embeddings (`PR-CAC-02`, `PR-CAC-04`) y su mantenimiento
  (`PR-CAC-05`).
- Las **fallas provocadas** que no dependen del modelo: `PR-RES-01`, `03`, `04`, `06`, `07`,
  `PR-SEC-02`, `04`.
- El **avatar como reproductor**: tamaño de los GLB, cola, arranque, cambio de personaje.

---

## 3. Criterio de contaminación y mapa de las 74

Una prueba se repite si cumple **al menos uno** de estos criterios:

| Criterio | Enunciado |
|---|---|
| **C1** | El resultado que se juzga depende de las glosas que devuelve el modelo. |
| **C2** | La métrica es tiempo, caudal o costo de un camino que incluye la llamada al LLM. |
| **C3** | La métrica es memoria o disponibilidad de GPU y el modelo local estaba residente. |
| **C4** | La precondición de la ficha nombra el proveedor oficial o un entorno que el modelo local invalida. |
| **C5** | La configuración del cliente (timeout, reintentos) era la del override local y entra en el número medido. |

Una prueba **no** se repite cuando el modelo, su memoria o su latencia no pueden cambiar la
cantidad medida. Esa justificación va al documento MD de la fase 1, **no al Excel**.

### 3.1. Las 24 que se repiten

| # | Prueba | Criterio | Qué número puede cambiar | Costo aprox. |
|---|---|---|---|---|
| 1 | `PR-STT-04` | C3, C4 | `large-v3` puede entrar en la GPU; WER y memoria con la GPU libre | 40 min |
| 2 | `PR-SEG-02` | C1 | Exactitud de glosas contra FR-05 y aciertos de caché | 20 min |
| 3 | `PR-SEG-04` | C1 | Comparación de glosas entre ablaciones | 30 min |
| 4 | `PR-CAC-03` | C1 | La línea «8/30 contra la referencia» y el contenido de la caché | 10 min |
| 5 | `PR-CAC-06` | C2 | Costo por frase (ya no es 0), latencia con LLM y 6/60 diferencias | 15 min |
| 6 | `PR-LLM-01` | C4 | Los tres modelos de la ficha: `gpt-4o-mini`, uno grande de la nube y uno local | 30 min |
| 7 | `PR-LLM-02` | C1 | Respuestas reales del modelo oficial y lectura de las fabricadas | 15 min |
| 8 | `PR-LLM-03` | C1 | Consistencia y exactitud con `gpt-4o-mini` (150 llamadas) | 15 min |
| 9 | `PR-LLM-04` | C1 | Glosas fuera del catálogo y latencia con frases largas | 10 min |
| 10 | `PR-LLM-05` | C1 | Inyección: qué obedece el modelo oficial | 10 min |
| 11 | `PR-INT-01` | C1 | Glosas del recorrido de 3 entradas | 5 min |
| 12 | `PR-INT-02` | C1 | Glosas recibidas contra las 112 esperadas | 15 min |
| 13 | `PR-INT-06` | C2 | p50/p95 de 30 minutos y memoria del backend | 35 min |
| 14 | `PR-AVT-02` | C1 | Cuántas de las 50 aparecen dentro de una frase | 20 min |
| 15 | `PR-REN-02` | C2 | Paso `llm` y total por frase | 25 min |
| 16 | `PR-REN-03` | C2 | Envío→primera seña y representación | 25 min |
| 17 | `PR-REN-04` | C2 | Arranque, warm-up y primera frase | 35 min |
| 18 | `PR-REN-05` | C3 | GPU en reposo y durante audio; «sistema completo» | 25 min |
| 19 | `PR-REN-06` | C2 | Latencia con 1, 2 y 3 sesiones | 15 min |
| 20 | `PR-REN-08` | C2 | Bytes por frase (dependen del número de glosas) | 10 min |
| 21 | `PR-REN-09` | C2 | Frases por minuto y espera hasta el `done` | 15 min |
| 22 | `PR-RES-02` | C5 | Aviso con el proveedor caído y colgado (5 s + 1 reintento) | 10 min |
| 23 | `PR-SEC-01` | C4 | El tramo backend→LLM pasa a HTTPS | 25 min |
| 24 | `PR-TRA-03` | C4 | Pasos manuales del arranque oficial y tiempos | 20 min |

**Total:** unas 6 horas de corrida en serie, 3–4 horas si se agrupan por recurso. Con
preparación y escritura: **un día de trabajo**.

### 3.2. Las 34 que quedan válidas (y por qué)

| Grupo | Pruebas | Por qué el modelo no las toca |
|---|---|---|
| STT sin LLM | 01, 02, 03, 05 | Miden audio→texto, VAD, cierre y WER. La memoria ocupada por Ollama no cambia la inferencia. |
| Segmentador solo | 01 | Mide la división del texto; no pasa por el LLM. |
| Huella gramatical | 03 | Son reglas sobre el texto, antes de traducir. |
| Caché sin LLM | 02, 04, 05 | Umbral, distancia coseno y mantenimiento: no comparan glosas del modelo. |
| Catálogo y validador | 01, 02, 03 | Prueban el catálogo y la normalización; las cadenas se inyectan a mano. |
| Dispositivo | AND 01–06 | Permiso, entrada, historial, ajustes, resoluciones y accesibilidad. |
| Avatar como reproductor | AVT 01, 03, 06, 07 | Arranque, cola, tamaños de GLB y cambio de personaje. |
| Rendimiento del STT y del equipo | REN 01, 07 | Latencia del streaming y fotogramas del teléfono. |
| Integración sin LLM | INT 05 | Sin conexión y modo avión: mide la red, no el modelo. |
| Resiliencia sin LLM | RES 01, 03, 04, 06, 07 | Cortes de túnel, STT caído, base caída, audio largo y reinicio del backend. |
| Seguridad sin LLM | SEC 02, 04 | Mensajes inválidos y retención de audio. |
| Estático | TRA 01, 02 | Importaciones, `ruff`, `mypy` y `ktlint`. |

Esta es **la nota que pide el owner**: el detalle de por qué cada una sigue siendo válida se
escribe en `RECUPERACION-2026-11-05.md`, no en el Excel. El libro no dice nada de la
contaminación; solo cambia lo que se volvió a medir.

### 3.3. Las 16 bloqueadas no se tocan

`PR-SEG-03`, `PR-HUE-01`, `PR-HUE-02`, `PR-CAC-01`, `PR-INT-03`, `PR-INT-04`, `PR-AVT-04`,
`PR-AVT-05`, `PR-RES-05`, `PR-SEC-03` y las seis `PR-PER-*`. El modelo local no las
desbloquea. `PR-INT-03` y `PR-AVT-05` dependen de una decisión del owner (D6 y D7).

---

## 4. FASE 1 — recuperar la ronda diagnóstica

**Objetivo:** que las 74 filas del libro de diagnóstico estén medidas con el modelo oficial,
sin repetir lo que no hacía falta y sin perder la trazabilidad de lo que se repitió.

### 4.1. Preparación (antes de correr nada)

1. **Congelar el código — hecho el 2026-11-05.** El backend quedó en `8f27bf8`, en `main` y
   en `develop`, con los cambios que estaban en el árbol de trabajo. Anotar
   `git rev-parse HEAD` de los tres repos y el `sha256` de la imagen del backend antes de
   correr. Sin esto, la fase no es reproducible.
2. **Sacar Ollama de la GPU, sin borrarlo.**
   ```bash
   ollama ps                      # debe quedar vacío
   ollama stop qwen3:4b-instruct  # o systemctl --user stop ollama
   nvidia-smi --query-gpu=memory.used,memory.total --format=csv
   ```
3. **Apagar el override local, sin borrarlo.**
   ```bash
   mv ../sign-ai-backend/.env.local ../sign-ai-backend/.env.local.off
   ```
   El archivo se conserva para `PR-LLM-01`; solo se aparta.
4. **Levantar con la configuración oficial.** `docker compose up -d --force-recreate` **sin**
   `docker-compose.host.yml`. Con el proveedor en la nube no hace falta red host: el
   contenedor sale a internet y el STT se alcanza por el nombre del servicio.
5. **Comprobar el modelo efectivo, no el archivo.** Es la verdad que importa:
   ```bash
   curl -s localhost:8080/metrics.json | jq '.config.llm_model, .config.llm_base_url'
   # esperado: "gpt-4o-mini" y ""
   ```
   `/metrics.json` ya publica `config.llm_model` y `config.llm_base_url` redactado
   (`../sign-ai-backend/app/main.py:243`). `/health` no lo publica; por eso el preflight usa
   `/metrics.json`.
6. **Comprobar el resto del terreno:** `/health` con 50 glosas, STT `/health` con `medium` y
   `cuda`, teléfono conectado, `adb reverse`, permiso `RECORD_AUDIO`, build `debug` con
   `-Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate` y `DEV_TOOLS` apagado.
7. **Parametrizar el arnés.** Hoy `lib.py` tiene `FECHA = "04/11/2026"` y
   `FECHA_ARCHIVO = "20261104"` fijos, y `rellenar-libro.py` escribe todos los JSON que
   encuentre. Antes de la fase 1:
   - `SIGNAI_FECHA`, `SIGNAI_FECHA_ARCHIVO`, `SIGNAI_RONDA=D` y
     `SIGNAI_RESULTADOS=/tmp/opencode/pruebas/resultados-recuperacion`.
   - `rellenar-libro.py --resultados <dir> --solo <IDs>` para escribir **solo** las 24 filas
     recuperadas y no tocar las otras 50.
   - Consecutivo de evidencia: que siga desde el último archivo del mismo día y prueba, para
     no sobrescribir.
8. **Anotar el estado inicial de la caché** (filas y si está precargada) y la base Neon
   despierta. Las pruebas de caché la van a vaciar.

### 4.2. Orden de ejecución

Se agrupa por recurso para no repetir preparaciones:

| Lote | Pruebas | Necesita | Duración |
|---|---|---|---|
| **L1 · STT aislado** | STT-04 | Solo STT; GPU libre; tres tamaños | 40 min |
| **L2 · Texto y NLP** | LLM-01…05, INT-01, INT-02, SEG-02, SEG-04 | Backend quieto; caché vacía en las que lo piden | 2 h |
| **L3 · Caché y rendimiento del backend** | CAC-03, CAC-06, REN-02, REN-03, REN-06, REN-08, REN-09 | Backend quieto; reinicios controlados | 1 h 30 |
| **L4 · Dispositivo y avatar** | AVT-02 | Celular exclusivo | 20 min |
| **L4b · Sesión larga** | INT-06 | Celular exclusivo, 30 min sin interrupciones | 35 min |
| **L5 · Arranque y recursos** | REN-04, REN-05 | Reinicio de contenedores; base dormida | 1 h |
| **L6 · Resiliencia y seguridad** | RES-02, SEC-01 | Proveedor caído/colgado; build `demo` con TLS | 35 min |
| **L7 · Instalación limpia** | TRA-03 | Clon nuevo; sin tocar el entorno principal | 20 min |

`PR-INT-06` es el único bloque de 30 minutos: se corre con el teléfono solo y sin nadie
tocando la pantalla; cualquier interrupción invalida la corrida.

**Regla de oro de la fase:** una prueba que falla es un resultado, no un motivo para
reintentar. Se repite una sola vez si hay una causa escrita (por ejemplo, un colector
huérfano), y la repetición se anota.

### 4.3. Evidencia y libro

- **Evidencia nueva, nunca sobrescrita:** `PR-<BLOQUE>-<NN>_D_<AAAAMMDD>_<nn>.<ext>` con la
  fecha de la corrida. La carpeta `evidencias/` del 2026-11-04 se conserva intacta como
  historia; la nueva corrida deja sus propios archivos.
- **Libro de diagnóstico:** se actualizan **en el mismo archivo** las columnas 12–17 de las
  24 filas recuperadas. Las 50 restantes no se tocan. La columna «Fecha» pasa a la fecha de
  la recuperación; «Ronda» sigue siendo `Ronda diagnóstica`.
- **Sin notas en el Excel.** Ni una celda nueva, ni un comentario, ni una hoja extra. El
  Excel no sabe que hubo contaminación.
- **La nota va al MD:** `RECUPERACION-2026-11-05.md` con (a) el criterio C1–C5, (b) la lista
  de las 24 con qué se repitió y por qué, (c) la lista de las 34 con la justificación de por
  qué siguen válidas, (d) la tabla antes/después de los números que cambiaron.

### 4.4. Entregables de la fase 1

| Archivo | Qué contiene |
|---|---|
| `plantilla-resultados.xlsx` | 74 filas; 24 re-medidas con el modelo oficial |
| `RECUPERACION-2026-11-05.md` | Criterio, lista de válidas, tabla de cambios, incidencias |
| `evidencias/<BLOQUE>/PR-..._D_<fecha>_...` | Evidencia nueva de las 24 |
| `CONCLUSIONES-2026-11-04.md` | Sección «Actualización 2026-11-05» con los números nuevos y qué conclusión cambió |
| `openspec/sessions/<fecha>-recuperacion-modelo-oficial.md` | Bitácora de la sesión |

### 4.5. Criterio de cierre (puerta a la fase 2)

1. Las **24** tienen estado, resultado y evidencia; ninguna queda «sin ejecutar».
2. Las **50** no recuperadas conservan su fila y su evidencia originales.
3. `/metrics.json` de la corrida dice `llm_model = gpt-4o-mini` y `llm_base_url = ""`; se
   guarda esa salida como evidencia de la fase.
4. La tabla antes/después está escrita; cada cambio de conclusión está marcado.
5. `ollama ps` quedó vacío y no hay ningún proceso `ollama` durante la corrida.
6. No se tocó código de producto en esta fase, salvo la instrumentación mínima que el owner
   autorice (por ejemplo, publicar el modelo en `/health`).

### 4.6. Qué se espera que cambie (hipótesis, no conclusiones)

| Prueba | Hoy (modelo local) | Hipótesis a verificar |
|---|---|---|
| `PR-SEC-01` | Falla: backend→LLM en claro | Pasa: el tramo pasa a HTTPS |
| `PR-STT-04` | `large-v3` no entra en 6 GB | Puede entrar con 2,7 GB de GPU libres; hay que medirlo |
| `PR-LLM-01/03` | 14/30 y 15/30 exactas | Número nuevo, no comparable con la línea base de 913 ms (esa era del camino por palabras) |
| `PR-INT-02` / `PR-AVT-02` | 99/112 y 43/50 | El número de glosas depende del prompt y del modelo; puede subir o bajar |
| `PR-RES-02` | 10,5 s con 10 s y 0 reintentos | Con 5 s + 1 reintento el peor caso ronda los 10 s; verificar |
| `PR-REN-*` | p50 con inferencia local | Latencia de red al proveedor; puede ser mayor o menor |
| `PR-CAC-06` | Costo 0 | Costo real por frase; hay que instrumentarlo o estimarlo con tokens |

Ninguna de estas filas se da por buena hasta que la corrida lo muestre.

---

## 5. FASE 2 — los arreglos

**Objetivo:** arreglar lo que la fase 1 confirme como defecto, en orden de gravedad, con una
prueba que lo verifique. No se arregla lo que resultó ser un artefacto del modelo local.

### 5.1. Cómo se decide qué arreglar

Para cada problema de `CONCLUSIONES` se aplica una de tres etiquetas después de la fase 1:

- **Confirmado:** el número nuevo sigue incumpliendo. Se arregla.
- **Corregido por el modelo oficial:** el número nuevo cumple. No se toca código; se anota
  el cambio y se actualiza la conclusión.
- **Descartado:** era un artefacto de la corrida (precondición violada, colector duplicado,
  configuración local). Se anota y no se arregla.

### 5.2. Tabla maestra de arreglos

**P0 — bloquean el cierre de la tesis o la ronda oficial**

| # | ID | Arreglo | Archivo(s) a tocar | Tipo | ¿Decisión del owner? | Verifica |
|---|---|---|---|---|---|---|
| 1 | S5 | Si el STT no conecta, **no cerrar la sesión del cliente**: seguir aceptando `text` y deshabilitar solo el audio | `../sign-ai-backend/app/controller/router.py:235-240` y el controlador | Backend | No: `RNF-AV-03` ya lo exige | RES-03 |
| 2 | S4 | Segunda barrera de no-voz en streaming: aplicar `rms`/`logprob`/`no_speech` al final del buffer, no solo en `POST` | `../sign-ai-stt/app/transcriber.py`, `app/main.py` | STT | Sí: ¿barrera en el STT o en el backend? | STT-03, STT-05 |
| 3 | S1 | Parser tolerante: quitar punto final, aceptar preámbulo, no partir glosas de dos palabras; `normalize_gloss` sin punto | `../sign-ai-backend/app/nlp/llm_client.py` (`_parse_glosses`, `_repair_glosses`), `app/validation/catalog_repository.py` | Backend | No | LLM-02, VAL-01 |
| 4 | S3 | El usuario solo ve glosas: filtrar las `available=false` del subtítulo y del historial; recortar texto crudo en el backend | `app/src/main/java/.../MainScreenState.kt:1932`, `llm_client.py` | App + backend | Sí: criterio de `PR-LLM-05` | LLM-05, LLM-04 |
| 5 | S9 | Aviso de permiso visible **sin** `DEV_TOOLS`, y aviso al reabrir con el permiso revocado | `MainScreenState.kt:1626` y `:2341` | App | No: `RF-UI-04` | AND-01 |
| 6 | S8 | Cola del avatar: encolar o mantener el reemplazo | `app/src/main/assets/web/app.js:1027`, `MainScreenState`; o el plan y el SRS | App o documento | Sí | AVT-03 / `STA-18` |
| 7 | S2 | Precisión: ajustar prompt y reparador; fijar referencia y umbral de `RNF-DES-07` | `llm_client.py`, `../sign-ai-docs/latex/srs/main.tex` | Backend + doc | Sí: umbral | LLM-01/03, INT-02 |
| 8 | S10 | `/health` del STT con el **dispositivo efectivo**, no el pedido | `../sign-ai-stt/app/transcriber.py:98-104`, `app/main.py:35-43` | STT | No | STT-04, REN-05 |
| 9 | S6 | Umbral de la caché y margen de seguridad | `../sign-ai-backend/app/nlp/semantic_cache.py`, `settings.py` | Backend | Sí: 0,03 / 0,05 / 0,08 | CAC-02, CAC-04 |
| 10 | S12 | Índice único sobre `gloss` en el catálogo | Migración SQL + `catalog_repository` | Datos | Sí: esquema | VAL-02 |
| 11 | S11 | Interfaz para `Orchestrator` y uso de `ValidatorService` en `llm_client` | `../sign-ai-backend/app/orchestrator/`, `app/nlp/llm_client.py` | Backend | No | TRA-01 |
| 12 | E1–E4 | Modelo local como opción explícita: separar el override de red del de LLM, preflight obligatorio, documentar el arranque oficial | `docker-compose.host.yml`, `.env.local.example`, `docs/android-integration.md`, `scripts/` | Config + docs | No | Preflight de la fase 3 |

**P1 — fichas, datos y documentos**

| # | ID | Arreglo | Dónde | ¿Decisión del owner? |
|---|---|---|---|---|
| 13 | F1 | `PR-CAC-05`: la ficha debe incluir el reinicio que `RNF-MNT-05` ya exige | `../sign-ai-docs/latex/plan-pruebas/main.tex` | Sí: ficha o SRS |
| 14 | F2 | `PR-INT-03`: cirugía temporal del catálogo — **aprobada el 2026-11-05**: camino real, con respaldo y el MCP de Neon; se ejecuta al final de la fase 2 | Datos / plan | Sí |
| 15 | F3 | `PR-AVT-05`: las cuatro caras ya están en el GLB nuevo; falta escribir la ficha y validarlas con una persona de LSC | Plan | Sí |
| 16 | F4 | `PR-AVT-06`: método por rangos de accesores (el literal sobrecuenta 5×) | Plan | No |
| 17 | F5 | `PR-STT-02`: describir el corte real («pausa más cercana», no «forzado») | Plan | No |
| 18 | F6 | `PR-STT-03`: trazabilidad a un RF de no-voz, no a `RF-STT-02` | Plan / SRS | Sí |
| 19 | F7 | `PR-VAL-01/02`: alinear la ficha con `RF-CAT-04` y `RNF-BD-02`, o subir el requisito | Plan / SRS | Sí |
| 20 | F8 | `JUCIOSO` → `JUICIOSO`: catálogo, clip y referencias | Neon + avatar + datos | Sí |
| 21 | F9 | `RNF-DES-07`: fijar el umbral con los números de la fase 1 | SRS | Sí |
| 22 | F10 | `PR-LLM-01`: nombrar el «modelo más grande de la nube» y dejar escrito que el local es parte de la comparación | Plan | Sí |
| 23 | F11 | `PR-SEC-01`: decir «con el proveedor oficial» en el entorno | Plan | No |
| 24 | F12 | `PR-STT-04`: exigir GPU libre y solo el STT, como ya dice el procedimiento | Plan | No |
| 25 | D1 | `RNF-AV-03` duplicado — **hecho el 2026-11-05**: se quitó el bloque repetido que estaba bajo «Requerimientos de Desempeño» y se dejó el de «Atributos del Sistema» | SRS | No |
| 26 | D2 | APK de 78,19 MB — **hecho el 2026-11-05**: el SRS ahora dice 33,9 MB release / 39,0 MB debug, medidos sobre los APK del 2026-11-05 | SRS | Sí |

**Nota del 2026-11-05 — GLB nuevo con cuatro caras (hecho en otra sesión).**
`../signai-avatar` quedó en `fc6f372`: **200 clips** (50 señas × 4 caras: `afirmativo`,
`pregunta`, `negacion`, `orden`), **sin clip base** y sin las caras emocionales anteriores.
`juan.glb` pesa 30,32 MB y `pauline.glb` 27,85 MB. Se adaptó `tools/pack_avatars.py`, se
regeneraron `models/avatars.json`, `manifest.json` y `domain/SignsCatalog.kt`, y `app.js`
elige la cara por tipo de oración. **Consecuencia para este plan:** `PR-AVT-06` hay que
re-medirlo (los tamaños y el APK cambiaron) y el SRS ya se actualizó a 35,1/40,3 MB;
`PR-AVT-02` sigue en las 24; `PR-AVT-05` sigue bloqueada hasta la validación con una persona
de LSC. **No se rehace la actualización del GLB.**

**P2 — mejoras que no bloquean**

| # | ID | Arreglo | Dónde |
|---|---|---|---|
| 27 | H1/H2 | «Texto más grande» no escala el indicador; «contraste» solo cambia el aro | `AvatarStage.kt`, `Color.kt` |
| 28 | H3 | La huella pierde 35 puntos sin puntuación | `app/nlp/gloss_analyzer.py` |
| 29 | H4 | El segmentador baja a 55 % sin puntuación | `app/nlp/segmenter/` |
| 30 | H5 | La retención de 1,5 s borra lo tecleado | `MainScreenState.kt` |
| 31 | H6 | Los ajustes no exponen `checked` ni el avatar elegido | UI de ajustes |
| 32 | H7 | Seis mensajes inválidos quedan mudos | `router.py` |
| 33 | H8 | Dos métricas de rendimiento no miden lo que dicen | Métricas de la app |
| 34 | H9 | Una caída del backend pierde el resto de la frase sin devolver el texto | App + backend |

### 5.3. El arreglo del modelo local, en concreto

No se borra nada. Se separa lo que hoy está mezclado:

1. **`.env.local` sigue existiendo** como override de desarrollo, y `.env.local.example`
   sigue documentándolo. `scripts/verify_official_config.py` ya lo verifica.
2. **`docker-compose.host.yml` deja de forzar `LLM_BASE_URL`.** Ese archivo es para la red
   host; el modelo local va a un `docker-compose.local-llm.yml` aparte, que se usa solo en
   `PR-LLM-01` y en la comparación de la ficha.
3. **Preflight obligatorio de ronda:** un script que lea `/metrics.json`, `/health`,
   `ollama ps` y `nvidia-smi`, y **aborte** si el modelo no es `gpt-4o-mini`, si hay un
   `llm_base_url` configurado o si hay memoria de GPU ocupada por Ollama. Sin ese chequeo no
   se lanza ninguna prueba.
4. **La evidencia lleva el modelo:** la salida de `/metrics.json` se guarda como primera
   evidencia de cada tanda. Así, la próxima vez la contaminación se detecta leyendo, no
   sospechando.

### 5.4. Orden de trabajo

1. **Revisión de hipótesis** (medio día): leer la tabla antes/después de la fase 1 y marcar
   cada S como confirmado, corregido o descartado.
2. **P0 en orden de dependencia:** S5 → S4 → S1 → S3 → S9 → S10 → S8 → S6 → S2 → S12 → S11.
   S2 va después de S1 porque comparte el parser; S6 y S8 esperan la decisión del owner.
3. **P1 (fichas y documentos)** en paralelo con P0, pero sin tocar el libro de resultados.
4. **P2** solo si sobra tiempo antes de la ronda; si no, queda como deuda declarada.

Cada arreglo se cierra con: `./gradlew build` y `./gradlew ktlintCheck` en la app,
`ruff` y `mypy` en el backend, `./gradlew installDebug` en el teléfono, y la prueba del
listado que lo verifica.

### 5.5. Entregable de la fase 2

`ARREGLOS-<fecha>.md`, con una fila por arreglo: problema, causa, archivo y commit, prueba
que lo verifica, número antes y número después. Sin números inventados: si no se volvió a
medir, se dice.

### 5.6. Criterio de cierre (puerta a la fase 3)

1. Todos los P0 están mergeados o explícitamente diferidos con una razón.
2. `./gradlew build`, `./gradlew ktlintCheck` y `./gradlew installDebug` pasan.
3. `ruff` y `mypy` del backend no agregan errores nuevos.
4. La prueba que verifica cada arreglo se volvió a correr y su número está en el documento.
5. El backend y el STT tienen commit de referencia; los hermanos tienen su PR contra
   `develop` (no se commitea en ellos sin que el owner lo pida).

---

## 6. FASE 3 — ronda de evaluación oficial

**Objetivo:** correr las **74 pruebas** sobre el sistema arreglado, con `gpt-4o-mini`, y
dejar el libro de evaluación completo. Es la ronda que se cita en la tesis.

### 6.1. Precondición

- Fase 2 cerrada.
- Preflight de la fase 1 en verde, con el commit nuevo.
- Arnés parametrizado para `_E_` y fecha de la corrida.
- Teléfono restaurado, `adb reverse`, permiso de audio, build `debug` oficial.
- Presupuesto: la ronda hace del orden de **1.000 a 1.500 llamadas** al proveedor. Es un
  gasto de pocos dólares, pero se anota el consumo antes y después y se corta si se dispara.

### 6.2. Diseño de subagentes y carriles

El principio es simple: **en paralelo solo lo que no comparte estado**. Lo que reinicia un
contenedor, vacía la caché, cambia el modelo del STT o usa el teléfono va solo.

| Carril | Agente | Pruebas | Recurso exclusivo | Total |
|---|---|---|---|---|
| **A · Dispositivo** | 1 agente dueño del teléfono | AND-01…06, AVT-01/02/03/07, INT-02/05/06, REN-07 | El Redmi y `adb` | 14 |
| **B · Backend de texto** | 1 agente | LLM-01…05, SEG-01, HUE-03, CAC-02/03/04, VAL-01/02/03, INT-01, REN-08/09 | El backend, sin reiniciarlo | 16 |
| **C · Audio y STT** | 1 agente | STT-01/02/03/05, REN-01/06, RES-06 | El STT y la GPU | 7 |
| **D · Estado compartido** | 1 agente, al final | STT-04, SEG-02/04, CAC-05/06, REN-02/03/04/05, RES-01/02/03/04/07, SEC-01/02/04, TRA-03 | Reinicios, caché, TLS, clon y, si hace falta, el teléfono | 18 |
| **E · Estático** | 1 agente | TRA-01/02, AVT-06, las 16 bloqueadas | Nada | 3 + 16 |

**Oleadas:**

1. **Oleada 1 (paralela):** A + B + C + E. No comparten estado: A usa el teléfono, B el
   socket de texto del backend, C el STT y E lee archivos.
2. **Oleada 2 (serial):** D, una prueba a la vez. Es donde se reinicia, se vacía la caché,
   se cambia el modelo del STT y se usa el teléfono junto con reinicios. Mientras D corre,
   A, B y C están parados.
3. **Oleada 3 (cierre):** dentro de D, al final, SEC-01 con el build `demo`; al terminar se
   reinstala `debug`. Es el único momento en que se reemplaza la app instalada.

Regla de conflicto: si dos pruebas necesitan el mismo recurso, **manda el carril**, no el
agente. Un agente no puede “esperar” a que otro termine: si su prueba no es de su carril, no
la corre. Los 14 + 16 + 7 + 18 + 3 = 58 ejecutables quedan repartidos sin solaparse; las 16
bloqueadas las marca E con su motivo.

### 6.3. Reglamento de operación para los subagentes

Se copia tal cual en el encargo de cada subagente.

```text
REGLAS DE OPERACIÓN (no negociables)

1. Un recurso, un dueño. El teléfono lo usa solo el agente A. El STT solo el C.
   El backend no se reinicia desde A, B ni C: los reinicios son de D.

2. Todo comando que pueda tardar más de 30 s va en background con `timeout`.
   No se espera “a ojo”: se lanza, se sigue con otra cosa y se recoge el resultado
   cuando el sistema avisa. Un `timeout` máximo de 45 min por comando; el único
   que lo necesita cerca es INT-06, con 40 min.

3. Prohibido `sleep N` como espera de operario. Los únicos `sleep` válidos son los
   del procedimiento (la retención de 1,5 s entre frases y los 30 s de INT-06),
   dentro del bucle de la prueba.

4. Prohibido `pgrep -f "script.py"`: se encuentra a sí mismo. Usar
   `pgrep -f "[s]cript.py"`. Prohibido `pkill -f <nombre>`: puede matar la shell.

5. Un solo colector por prueba. Si ya hay uno corriendo, no se lanza otro: se lee
   su salida o se espera su archivo de cierre.

6. Ningún proceso hijo queda vivo al terminar el turno. Antes de reportar:
   `ps -ef | grep "[t]u_script"`, `adb shell ps`, `docker ps`.

7. La evidencia no se sobrescribe. Nombre con `_E_<AAAAMMDD>_<nn>`, consecutivo
   automático.

8. Una prueba que falla es un resultado. Se repite una sola vez, con causa escrita.
   Si falla dos veces seguidas con el mismo síntoma, se para el carril y se reporta.

9. Si el sistema queda en un estado raro (app en otra pantalla, backend caído,
   caché a medias), no se “arregla” a mano: se reporta y lo decide la sesión maestra.

10. Cada prueba termina con su JSON de resultado y su línea de evidencia. Sin eso,
    la prueba no existe.

11. Todo trabajo en background deja un log o un archivo de progreso que crece. Si en
    5 minutos no hay salida nueva ni archivo de cierre, el comando está colgado: se
    mata, se anota la causa y se sigue. Para saber si terminó se mira el archivo de
    cierre, no se sondea con un bucle.

12. No se queda mirando con `tail -f` ni con `watch`: eso también es un bucle. Se lee
    el archivo cuando el proceso avisa, o al final. Cada tanda imprime un avance
    (`[3/10] PR-LLM-02`) para que se vea que está viva.
```

### 6.4. Presupuesto de tiempo

| Oleada | Duración | Qué la puede estirar |
|---|---|---|
| Preflight y arranque | 30 min | Base dormida, descarga del modelo de embeddings |
| Oleada 1 | 3–4 h | INT-06 (30 min) y las pruebas de avatar |
| Oleada 2 | 3–4 h | STT-04 (tres modelos) y TRA-03 (clon limpio) |
| Oleada 3 y cierre | 1 h | SEC-01 con TLS |
| Libro y análisis | 2–3 h | Alguna fila que haya que repetir |

Total: **un día largo**, con tope duro de 12 horas. Si se pasa, se corta y se retoma al día
siguiente; el libro se llena igual.

### 6.5. Evidencia y libros

- Evidencia con `_E_` y fecha de la corrida, en `evidencias/<BLOQUE>/`.
- `plantilla-resultados.xlsx` **no se toca** en la fase 3: sigue siendo el libro de
  diagnóstico.
- El libro de evaluación es el consolidado (ver §7).
- `ANALISIS-<fecha>.md`, `PRUEBAS-<fecha>.md` y `CONCLUSIONES-<fecha>.md` de la ronda de
  evaluación, con la misma estructura que los del 2026-11-04.
- `openspec/sessions/<fecha>-ronda-evaluacion.md`.

### 6.6. Criterio de cierre

1. Las 74 filas tienen estado, resultado y evidencia; las bloqueadas dicen qué faltó.
2. No hay ninguna fila «sin ejecutar» sin justificar.
3. El `Resumen` del libro cuadra con las filas.
4. Cada arreglo de la fase 2 tiene su número nuevo en el documento de arreglos.
5. Están los dos Excel y los MD de la ronda.

---

## 7. Los dos Excel

### 7.1. `plantilla-resultados.xlsx` — solo diagnóstico

Es el libro que ya existe. En la fase 1 se actualizan **en el mismo archivo** las 24 filas
recuperadas. En la fase 3 no se toca. Al final contiene las 74 pruebas de la ronda
diagnóstica, con las 24 medidas con `gpt-4o-mini` y las otras 50 con su medición original.

### 7.2. `resultados-consolidados.xlsx` — diagnóstico + evaluación

Libro nuevo, generado al final de la fase 3:

- Una fila por prueba y por ronda: **74 × 2 = 148 filas**, con la columna «Ronda» ya puesta
  (`Ronda diagnóstica` / `Ronda de evaluación`).
- Las filas de diagnóstico se copian del libro de §7.1; las de evaluación, de los resultados
  de la fase 3.
- El `Resumen` se ajusta para no mentir: la tabla «Por bloque» cuenta **solo la ronda
  diagnóstica** (o muestra las dos columnas, si el owner prefiere), y la tabla «Por ronda»
  cuenta las dos. Un total de 148 en «Por bloque» sería un número falso.
- Se genera con `generar-plantilla.py`, extendido con un modo `--rondas 2`; no se edita a
  mano, para que se pueda reproducir.

**Decisión menor (D10):** ¿«Por bloque» muestra solo diagnóstico o dos columnas por ronda?
Recomendación: dos columnas, porque el consolidado se lee para comparar.

---

## 8. Decisiones del owner

| # | Decisión | Opciones | Consecuencia | Cuándo |
|---|---|---|---|---|
| **D1** | Congelar el backend | Commit de los cambios pendientes / parche versionado | Sin esto no hay fase 1 reproducible | Antes de la fase 1 |
| **D2** | Modelo grande de la nube para `PR-LLM-01` | `gpt-4o` / otro | Cuesta más por llamada; la ficha pide tres modelos | Antes de la fase 1 |
| **D3** | Cola del avatar | Encolar / mantener el reemplazo y corregir el plan | Cambia app o documento; `STA-18` dice reemplazo | Fase 2 |
| **D4** | Criterio de inyección | «No ver texto que no sea glosa» / «basta con no representar una seña equivocada» | Si es lo segundo, `PR-LLM-05` pasa y S3 baja de prioridad | Fase 2 |
| **D5** | Umbral de la caché | 0,03 / 0,05 / 0,08 | 0,03 no da falsos; 0,05 da 21/30 y 2 falsos | Fase 2, con los números nuevos |
| **D6** | `PR-INT-03` | Cirugía temporal del catálogo / dejarla bloqueada | Si se hace, hay que tocar Neon y restaurar | Fase 2 |
| **D7** | `PR-AVT-05` | Escribir la ficha y validar con LSC / dejarla bloqueada | La validación facial queda sin evidencia | Fase 2 |
| **D8** | `JUCIOSO` | Corregir catálogo, clip y referencias / dejarlo | Cambia datos y posiblemente el GLB | Fase 2 |
| **D9** | `RNF-DES-07` | Fijar el umbral con la fase 1 | Es el requisito de precisión de la tesis | Después de la fase 1 |
| **D10** | Resumen del consolidado | Solo diagnóstico / dos columnas por ronda | Afecta cómo se lee el libro final | Fase 3 |

### 8.1. Decisiones cerradas el 2026-11-05

| # | Decisión | Quedó |
|---|---|---|
| **D1** | Congelar el backend | **Hecho**: commit `8f27bf8` en `main` y `develop`, pusheado a `origin` |
| **D2** | Modelo grande para `PR-LLM-01` | **`gpt-4o`** |
| **D3** | Cola del avatar | **Encolar**, con tope de 2 frases o ~20 s; al pasarse, descartar la más vieja y avisar |
| **D4** | Criterio de inyección | **Estricto**: el usuario no ve texto que no sea glosa; se arregla con post-proceso, sin meter el catálogo en el prompt |
| **D5** | Umbral de la caché | **0,03** |
| **D6** | `PR-INT-03` | **Aprobada la cirugía temporal**, camino real, con respaldo y el MCP de Neon; al final de la fase 2 |
| **D7** | `PR-AVT-05` | **Fuera de este plan**: el GLB nuevo está en `../signai-avatar`; la validación con LSC queda pendiente |
| **D8** | `JUCIOSO` | **Se corrige en todos lados**, previa confirmación del listado original del LSC50 |
| **D9** | `RNF-DES-07` | **Después de la fase 1**, con margen sobre lo medido |
| **D10** | Resumen del consolidado | **Dos columnas** por bloque, una por ronda |
| **S4** | Barrera de no-voz | **En el STT**, que es el único que ve el audio |
| **D11** | Pruebas con personas | **Bloqueadas por ahora**; se declaran como tales en el libro |

### 8.2. Verificación del MCP de Neon (2026-11-05)

Se comprobó el acceso de solo lectura antes de aprobar la cirugía de `PR-INT-03`:

- **Proyecto:** `sign-ai`, id `bold-shape-68007559`, org `org-orange-surf-39007829`.
- **Catálogo:** 50 filas.
- **Caché semántica:** 68 filas.
- **Las 9 filas de FR-11 existen y tienen clip:** `BUENAS NOCHES` (3), `ABUELO` (16),
  `TIO` (17), `HAMBRE` (19), `FELIZ` (20), `BIEN` (24), `MAS O MENOS` (27), `ADIOS` (31)
  y `TU` (39).
- **`JUCIOSO` es `id_sign 29`**, categoría `adjetivo`; se corrige cuando se confirme el
  listado original.
- **No se modificó nada** en esta verificación: fueron tres `SELECT`.

Con esto, la cirugía de `PR-INT-03` es viable: se exportan las 9 filas, se borran, se
reinicia el backend, se corren las 6 frases en los dos modos y se restauran. El respaldo
queda como evidencia.

---

## 9. Riesgos y mitigaciones

| Riesgo | Probabilidad | Mitigación |
|---|---|---|
| El modelo oficial tarda más y alguna prueba de latencia se pasa del tope | Media | Medir con la red real; no comparar con la línea base de 913 ms (era otro camino) |
| El costo de las llamadas se dispara | Baja | Presupuesto y tope; se corta la tanda si el consumo se sale |
| `large-v3` sigue sin entrar en 6 GB | Media | Se reporta como límite; no cambia el `medium` elegido |
| El backend sigue con cambios sin commitear | Alta | D1 antes de empezar |
| Un subagente deja un `adb logcat` huérfano y duplica evidencia | Alta | Reglas 5 y 6; un colector por prueba |
| Dos agentes usan el teléfono a la vez | Media | Carril A exclusivo; el resto no toca `adb` |
| El libro se llena con resultados de dos carpetas y se cruzan | Media | `--solo` y `--resultados`; el libro de diagnóstico no se regenera |
| Se confunde «recuperación» con «ronda nueva» | Media | La recuperación es `_D_`; la ronda nueva es `_E_`; carpetas separadas |
| El análisis del 2026-11-04 se cita con números viejos | Alta | Sección «Actualización» y tabla antes/después |

---

## 10. Lo que este plan no hace

- No corre pruebas: esta sesión solo escribe el plan.
- No toca el libro, el código ni los documentos de la tesis.
- No decide por el owner las diez decisiones de §8.
- No arregla nada que la fase 1 no confirme.
- No promete que `gpt-4o-mini` mejore la precisión: eso se mide, no se supone.

---

## 11. Trazabilidad

| Qué | Dónde |
|---|---|
| Ronda diagnóstica cerrada | commit `6c28985`, `docs/plan-pruebas/` |
| Resultados | `plantilla-resultados.xlsx`, columnas 12–17 |
| Evidencias | `docs/plan-pruebas/evidencias/<BLOQUE>/` |
| Análisis por causa | `ANALISIS-2026-11-04.md` |
| Bitácora de la ronda | `PRUEBAS-2026-11-04-1626.md` |
| Conclusiones (corte 67/74) | `CONCLUSIONES-2026-11-04.md` |
| Arnés de la ronda | `/tmp/opencode/pruebas/` (`lib.py`, `rellenar-libro.py`, encargos) |
| Fichas del plan | `../sign-ai-docs/latex/plan-pruebas/main.tex` |
| Config oficial del LLM | `../sign-ai-backend/.env`, `.env.example`, `scripts/verify_official_config.py` |
| Override local | `../sign-ai-backend/.env.local`, `.env.local.example`, `docker-compose.host.yml` |
| Modelo efectivo en runtime | `../sign-ai-backend/app/main.py:243` (`/metrics.json`) |
