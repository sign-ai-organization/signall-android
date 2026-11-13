# Recuperación de la ronda diagnóstica y arreglos P0

**Día:** jueves 5 de noviembre de 2026, madrugada. **Sesión:** ejecución del plan
`docs/historia/plan-pruebas/PLAN-2026-11-05-recuperacion-y-ronda-oficial.md`, fases 1 y 2.

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

Detalle completo en `docs/historia/plan-pruebas/ARREGLOS-2026-11-05.md`. Resumen:

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

Los cambios de `../signall-backend` y `../signall-stt` quedan **sin commit** en el árbol de
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

**Carril B · backend de texto (15 pruebas, cerrado a las 06:15).** 8 Se reporta, 5 Pasa,
2 Falla. Números que importan: `gpt-4o-mini` 17/30 secuencias exactas y 795 ms de mediana (en
la fase 1 había dado 18/30 porque el prompt cambió con S2); `gpt-4o` 20/30 por 16 veces el
costo; el modelo local 17/30 con 516 ms, o sea que **el local no era el problema**: los
timeouts viejos eran del camino bridge→host filtrado por `ufw`. `PR-LLM-02` queda en 9/10
(la misma `CO-04-008` de S1). `PR-VAL-02` falla por el índice único que S12 dejó pendiente.
`PR-CAC-03` baja a 17/30 con el umbral 0,03 (antes 19/30 con 0,05) y `PR-CAC-04` queda en
**0/30 falsos aciertos**. `PR-HUE-03` 65,5 % y `PR-SEG-01` 79,0 % de acierto de corte.

Dos cosas que deja anotadas el carril B: el `llm01_rec.py` de la recuperación todavía corre el
modelo local por el camino de bridge (el bueno es `backend_tmp_host.sh`), y
`../signall-backend/docs/android-integration.md` no describe el frame `capability` y sigue
hablando de «3 de las 50 señas» y de traducción automática de la voz.

**Carril C · audio y STT (7 pruebas, cerrado).** 4 Se reporta, 2 Pasa, 1 Falla.
`PR-STT-01` WER 1,77 % (los 5 errores en 3 audios); `PR-STT-02` 10/10 cierres por el mecanismo
correcto; `PR-STT-05` los cuatro umbrales de VAD empatan (3,90 % de WER, 1/60 sin texto, 0/8
sin voz con texto), así que no hay dato para mover el 2; `PR-REN-01` primer parcial p50 1,85 s
y fin de voz → final p50 1,53 s; `PR-RES-06` 20/20 frases del audio largo de 96 s con WER 0.
**`PR-STT-03` quedó en Falla**: los finales fueron 0/8, pero el servicio devolvió un parcial
«¡Grr!» en AU-03-006 y la barrera `non_speech` no cubre los parciales. Se dejó anotado como
pendiente en `ARREGLOS` §2 en vez de tocar el STT a mitad de la ronda.

**Carril A · dispositivo y avatar (15 pruebas, cerrado).** 9 Pasa, 4 Se reporta, 2 Falla.
`PR-AVT-01` abre en 4,60–4,83 s (mediana 4,66; interno 2,00 s) y vuelve al reposo 10/10;
`PR-AVT-02` 50/50 animaciones (103 ocurrencias, mediana 2,55 s); `PR-AVT-07` cambia de avatar
en 2,57 s / 2,07 s y persiste; `PR-AND-03` 12/12 frases en Historial, tope 20; `PR-AND-04` y
`PR-AND-06` verifican tema, velocidad, tamaño y las cuatro opciones de accesibilidad con
contraste 1,54 → 4,15. `PR-INT-06` 60 frases en 30 min con PSS estable en 375–384 MB, p50
envío→1.ª seña 787 ms y representación p50 6,28 s; `PR-REN-07` 58,5 fps con 70,5 % de cuadros
janky y intervalos entre señas p50 2,11 s; `PR-REN-08` 204 B por frase de texto (la mitad de
voz no se pudo medir). **`PR-INT-02` sigue en Falla**: 29/30 frases completas y 98/99 señas,
pero 17/30 frases exactas y 99 de 112 glosas (88,4 %) — todo lo que falta es NLP, no avatar.
**`PR-AVT-03` sigue en Falla**: la cola nueva aguanta una frase pendiente, pero con tres
envíos seguidos la tercera corta las dos anteriores en silencio (sin línea «cola cortada»).
`PR-AND-05` documentó que la actividad es `portrait` fija: el giro no se puede probar, y eso es
el producto, no un fallo. Teléfono restaurado byte a byte (`signai.xml` md5 `b3889a13…`),
`RECORD_AUDIO` concedido, un solo `adb reverse` y ningún colector vivo.

**Documentos desactualizados que encontró el carril C** (no se tocaron): `RNF-DES-02` del SRS
sigue con un «TODO: fijar el rango con los datos reales», y las tablas de AU-02, AU-03 y AU-04
dicen que faltan grabar audios que ya están grabados y se usaron.

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
