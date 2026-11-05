# Recuperación de la ronda diagnóstica y arreglos P0

**Día:** jueves 5 de noviembre de 2026, madrugada. **Sesión:** ejecución del plan
`docs/plan-pruebas/PLAN-2026-11-05-recuperacion-y-ronda-oficial.md`, fases 1 y 2.

- **Objetivo:** volver a medir las 24 pruebas que la ronda del 2026-11-04 había medido con
  `qwen3:4b-instruct` (modelo local) usando el modelo oficial `gpt-4o-mini`; después aplicar
  los arreglos P0 que la ronda confirmó, y cerrar con la ronda de evaluación de 74 pruebas.
- **Por qué:** el modelo local contaminaba cinco cosas a la vez (salida, latencia, memoria de
  GPU, precondición de la ficha y configuración del cliente). Los criterios C1–C5 están en el
  plan §2.

## Lo que se hizo

### Preflight (cerrado)

- `.env.local` apartado como `.env.local.off` (no se borró: se usa en `PR-LLM-01`).
- Backend reconstruido desde `8f27bf8`, levantado **sin** `docker-compose.host.yml`.
- `/metrics.json` confirmó `llm_model=gpt-4o-mini` y `llm_base_url=""`; STT `medium` en CUDA;
  teléfono con `adb reverse` y `RECORD_AUDIO`; la app `debug` reinstalada y una frase de humo
  de punta a punta.
- Evidencia en `docs/plan-pruebas/evidencias/PREFLIGHT/20261105_metrics.json`.

### Fase 1 — 24 pruebas recuperadas (cerrada)

- Las 24 medidas y guardadas con evidencia `_D_20261105` (86 archivos): `PR-LLM-01`…`05`,
  `PR-SEG-02/04`, `PR-CAC-03/06`, `PR-INT-01/02/06`, `PR-AVT-02`, `PR-STT-04`,
  `PR-REN-02/03/04/05/06/08/09`, `PR-RES-02`, `PR-SEC-01`, `PR-TRA-03`.
- Las 24 filas escritas en sitio en `plantilla-resultados.xlsx` (columnas 12–17); la copia de
  antes está en `/tmp/opencode/plantilla-antes-de-rellenar.xlsx`.
- `RECUPERACION-2026-11-05.md` completo: §5 con las 24 filas antes/después, §6 incidencias y
  §7 cierre. Estados: 5 Pasa, 4 Falla (`LLM-02`, `LLM-05`, `INT-02`, `TRA-03`), 15 Se reporta.
- La última cadena (`l7c.sh`) corrió `PR-RES-02` y `PR-TRA-03`. `RES-02` pasó con 10,6 s de
  aviso (medido desde el envío, no desde el toque: el toque incluía ~10 s de `uiautomator`).
  `TRA-03` falló: el clon y la app compilaron, pero el backend del clon se quedó sin base y no
  escuchó en 10 min; el log quedó transcrito porque el `down -v` borra el contenedor.

### Fase 2 — arreglos aplicados

Detalle completo en `docs/plan-pruebas/ARREGLOS-2026-11-05.md`. Resumen:

| ID | Repo | Qué |
|---|---|---|
| S1 | backend | Parser tolerante (preámbulo, punto final, viñetas) y puntuación fuera de `normalize_gloss`; separar glosas pegadas solo si todas las palabras están en el catálogo |
| S2 | backend | Dos reglas nuevas en el prompt (no agregar palabras, no omitir contenido) y prohibición de preámbulo |
| S3 | app | `formatGlosses` filtra `available=false`: el usuario no ve texto que no sea glosa |
| S4 | STT | `app/non_speech.py` + `drop_reason="non_speech"`: la tos y los carraspeos ya no llegan a texto por streaming |
| S5 | backend + app | El STT caído **no** cierra la sesión; frame `capability` aditivo y el micrófono se apaga solo |
| S6 | backend | Umbral de caché 0,03 (código, `.env.example` y `.env`) |
| S8 | app | La frase nueva se **encola** (tope 2) en vez de cortar la que suena |
| S9 | app | Aviso de permiso y de servicio de voz visible sin `DEV_TOOLS` |
| S10 | STT | `/health` y `/metrics.json` publican el dispositivo **efectivo** |
| E1, E3 | operación | Hechos en el preflight (`.env.local.off`, `/metrics.json` como evidencia) |
| S11, S12 | — | No se tocaron: deuda de arquitectura y esquema de Neon, fuera de esta ronda |

Los cambios de `../sign-ai-backend` y `../sign-ai-stt` quedan **sin commit** en el árbol de
trabajo, como pide `AGENTS.md`.

### Fase 2 — verificada el 2026-11-05 a las 05:54

Imágenes de backend y STT reconstruidas desde el árbol de trabajo y las comprobaciones de
cada arreglo corridas en orden (`recuperacion/verificar_arreglos.sh`). Resultado: S1 **9/10**,
S4 **0/8** audios sin voz con texto, S5 el texto sigue con el STT apagado y llega
`capability{audio:false}`, S6 umbral **0,03**, S10 `device=cuda`, S9 el aviso del micrófono se
ve. La app `debug` se reinstaló y conecta. El detalle está en `ARREGLOS-2026-11-05.md` §5.

**Trampa nueva:** al reconstruir el STT con `docker compose up -d`, el contenedor quedó fuera
de la red del backend (sin el alias `stt`) y todas las sesiones nacían con `audio:false`
aunque su `/health` dijera `ok`. Se arregla con `docker network connect --alias stt
sign-ai-backend_default sign-ai-stt-stt-1`; quedó escrito en las reglas de los carriles.

### Fase 3 — en curso

- Carril B (backend de texto, 15 pruebas) lanzado a las 05:57 como subagente, en background.
- Carriles C (audio y STT), A (dispositivo) y D (estado compartido) van después, en ese orden,
  con los encargos de `recuperacion/encargos/`.
- `PR-REN-08` pasó del carril B al A (es el dueño del teléfono, que la prueba necesita).
- `PR-INT-03` pasó al carril D (19 pruebas): la cirugía del catálogo la hace quien puede tocar
  Neon y reiniciar el backend, con respaldo y restauración obligatoria.

## Decisiones de la sesión

| Tema | Quedó |
|---|---|
| Colectores con `gpt-4o-mini` | La tanda `PR-LLM-02…05` se repitió entera por un choque de nombres en el colector (las glosas de referencia pisaban las producidas). Los números válidos son los de la segunda corrida |
| Contenedores temporales | Solo se cambia la variable que la ficha aísla; misma imagen, mismo código, misma base, mismo proveedor |
| Modelo local leg de `PR-LLM-01` | Se corrió en un contenedor de red host: `ufw` filtra el camino bridge→host:11434 |
| Fase 2 sin esperar la fase 1 completa | Los arreglos se escribieron mientras corría la cadena de dispositivo, pero **no se desplegaron**: las pruebas de fase 1 que faltan tienen que medir el sistema anterior |
| S12 y S11 | Se dejan para una ventana propia, con respaldo y prueba de arranque |

## Trampas que costaron tiempo

- **Neon dormida** hace arrancar el backend `degraded` (catálogo vacío) con un timeout de 5 s
  en el primer connect. El arnés ahora reintenta el arranque y exige `catalog_loaded`.
- **`adb logcat` vuelca el buffer entero** cada vez que el stream se reinicia, así que el log
  de una corrida larga trae cada línea repetida (medido en `PR-INT-06`: 5.800 líneas, 887
  únicas). Sin deduplicar, el emparejamiento de frases se corre y todo el resultado miente.
  Corregido en el colector y en el reintérprete.
- **El logcat de una prueba nueva seguía leyendo el archivo de la anterior** porque `dev.LIVE`
  es un enlace que solo mueve `dev.logcat_iniciar`. `PR-INT-06` marcaba «no terminó» en frases
  que sí terminaron.
- **El proxy de bytes de `PR-REN-08` cortaba la sesión a los 10 s** porque dejaba el timeout
  de `create_connection` puesto para las lecturas; la app se reconectaba en bucle y una frase
  se quedó sin cerrar dos veces. Es del instrumento, no del sistema.
- **`docker logs` escribe por `stderr`**: leer `stdout` hacía que la evidencia de `PR-SEC-01`
  dijera «0 llamadas al proveedor» con 509 en el log.
- **`docker stats` por nombre fijo** no ve el contenedor temporal del STT (se llama distinto),
  así que la fase CPU de `PR-REN-05` quedó sin muestras propias y hubo que medirlas aparte.

## Lo que queda

- ~~Cerrar la fase 1~~ **Cerrada el 2026-11-05 a las 05:48**: las 24 filas están en
  `plantilla-resultados.xlsx` y `RECUPERACION-2026-11-05.md` quedó con §5 y §7 completos.
  Quedan 5 Pasa, 4 Falla y 15 Se reporta.
- Verificar los arreglos: reconstruir las imágenes de backend y STT y correr las pruebas que
  los miden (`PR-LLM-02/03/05`, `PR-STT-03/05`, `PR-RES-03`, `PR-AND-01`, `PR-AVT-03`,
  `PR-CAC-02/03`).
- `PR-INT-03`: cirugía temporal del catálogo con respaldo y MCP de Neon, al final de la fase 2.
- Fase 3: ronda completa de 74 pruebas con subagentes y `resultados-consolidados.xlsx`.
- Propuestas que necesitan al owner: umbral de `RNF-DES-07` (medido 60 % de frases exactas),
  el aviso visible en la tabla de estados del SRS, y el choque de la cola nueva con `STA-18`.
