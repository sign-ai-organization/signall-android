# Ronda de evaluación · carril P1 (23 pruebas de texto y estático)

**Fecha:** 2026-11-06, mañana · **Rama:** `main`

## Qué llegó

El owner repartió las 74 pruebas del plan en tres carriles y pidió correr **P1 completo** (23
pruebas): SEG-01/03, HUE-01/02/03, CAC-01/02/03/04, LLM-01 a 05, VAL-01/02/03, INT-01,
AVT-06, REN-08/09 y TRA-01/02. El libro `resultados-consolidados.xlsx` ya tenía la ronda
diagnóstica (la del 5/11) y le faltaba la otra. El owner dejó el teléfono y el entorno a
disposición; no hay otros carriles corriendo.

## Qué se hizo

- **Preflight y sello** (`evidencias/PREFLIGHT/SELLO-P1_E_20261106_01.json`): backend `9c74e76`,
  STT `9787e49`, app `298e80c`, APK `debug` `52958cfc…`, `gpt-4o-mini` con `llm_base_url=""`,
  umbral 0,03, caché vacía, 50 filas de catálogo + 13 sinónimos, Redmi Note 9S `1ed6699f`.
- **Datos:** se extrajeron FR-01…FR-11, CO-01…CO-05, AU-05 y CT-01/03 del anexo C del plan con
  un parser propio (`/tmp/opencode/p1-2026-11-06/scripts/parse_anexo.py`). Los 10 textos de
  AU-01 se transcribieron de nuevo con el STT (POST `/transcribe`) para SEG-01.
- **SEG/HUE:** el segmentador y la huella se corrieron dentro del contenedor, con los casos
  completos. SEG-03 comparó cuatro métodos; HUE-01 comparó reglas, spaCy mediano y
  `gpt-4o-mini`; HUE-02 apagó una señal a la vez por monkeypatch, sin tocar el código.
- **CAC:** CAC-01/02 se midieron con embeddings reales (MiniLM y mpnet) sobre las 30 frases de
  FR-01 y las 60 variantes; CAC-03/04 se midieron por el WebSocket, vaciando la caché con
  `DELETE` + `POST /admin/cache/reload` antes de cada una.
- **LLM:** LLM-01/02/03/04/05 se midieron con el cliente del LLM directo (sin caché). El modelo
  local `qwen3:4b-instruct` se consultó desde el host (Ollama está atado a 127.0.0.1 y el
  contenedor no lo alcanza) con el mismo prompt y temperatura 0; el parseo y la reparación se
  aplicaron dentro del contenedor. LLM-05 se completó por el WebSocket para ver lo que recibe el
  usuario.
- **VAL:** VAL-01 y VAL-02 dentro del contenedor (validador, catálogo, intento de duplicado y
  `pg_stat_user_tables` antes/después de 30 frases). VAL-03 con la app real: se guardó la fila de
  HAMBRE, se borró, se reinició el backend, se restauró y se reinició otra vez, con capturas y
  logcat en los tres estados.
- **INT/AVT/REN/TRA:** INT-01 por el WebSocket (texto de una oración, de dos y audio). AVT-06
  releyendo los bufferViews de los GLB y el APK. REN-08 con un proxy TCP que cuenta bytes en los
  dos sentidos; REN-09 con 5 minutos de frases por voz en una sola sesión. TRA-01 con un
  escáner AST de importaciones; TRA-02 con ruff, mypy y `./gradlew ktlintCheck`.
- **Cierre:** 50 archivos de evidencia en `evidencias/<BLOQUE>/PR-*_E_20261106_*`, 23 JSON en
  `/tmp/opencode/p1-2026-11-06/resultados/` (la forma que consume `generar-consolidado.py`) y
  `resultados-consolidados.xlsx` con **148 filas**. Se reinstaló `debug` (mismo hash) y se
  verificó una frase de humo.

## Qué se decidió

- **P1 corrió solo, y por eso pudo reiniciar el backend.** El carril dice que P1 no reinicia
  contenedores para no pisar a P2; como no había otro carril corriendo, los reinicios de VAL-02
  y VAL-03 se hicieron acá y se anotaron. En una ronda con P2 y P3 en paralelo, VAL-03 tiene que
  coordinarse con el dueño del teléfono y del backend.
- **`clean` de caché por SQL + `/admin/cache/reload`** en vez de reiniciar el backend para cada
  prueba de CAC. Es el mecanismo que existe desde la Fase 5 y evita mover el estado del servidor.
- **No se tocó código de la app ni del backend.** Los únicos cambios en la base son la fila de
  HAMBRE que se borra y se restaura en VAL-03 y las dos inserciones de prueba de VAL-02, todas
  limpiadas.
- **e5-small y spaCy `sm` quedaron sin medir** porque los pesos no están en la máquina y
  Hugging Face responde 401. Se reporta como limitación del ambiente, no como falla del sistema.
- **VAL-02 se marca Pasa con observación**: el catálogo tiene 50 filas y la base rechaza la
  repetida, pero `/health` informa 63 porque cuenta los 13 sinónimos. El número del gauge no es
  el del catálogo.

## Qué se midió

- **SEG-01:** 156/200 (78,0 %); 7/9 límite, 20/29 borde, 129/162 no borde; cortes precisión
  0,903 y cobertura 0,808; AU-05 7/10; los 191 del laboratorio 191/191. SEG-03: el híbrido del
  sistema gana (78,0 % / 1,71 ms) a solo puntuación (62,8 % / 0,02 ms), puntuación+marcadores
  (67,5 % / 0,06 ms) y spaCy sents (59,2 % / 4,71 ms).
- **HUE-03:** 151/200 (75,5 %); con puntuación 84/100 y sin puntuación 67/100; contenido 40/40,
  orden 22/40. HUE-02: completa 75,5 %; sin interrogativa 57,0 %, sin negación 58,5 %, sin
  signo 67,0 %, sin imperativo 65,5 %. HUE-01: reglas 75,5 %, spaCy md 59,5 %, gpt-4o-mini
  88,0 % (USD 0,0047 por 200).
- **CAC-01/02:** MiniLM, umbral 0,03 → 19/30 paráfrasis y 0/30 trampas, 14,5 ms/embedding,
  1.223 MB; mpnet, umbral 0,04 → 23/30 y 0/30, 36,6 ms, 1.844 MB. En 0,05 MiniLM ya acepta 2
  trampas. CAC-03: 17/30 (formato 9/10, orden 7/10, sinónimos 1/10). CAC-04: 0/30.
- **LLM:** gpt-4o-mini secuencia 20/30, conjunto 22/30, 107/107 en catálogo, 654 ms y USD
  0,0024; gpt-4o 25/30, 26/30, 108/108, 722 ms y USD 0,0397; qwen 21/30, 22/30, 100/100 y 462 ms.
  LLM-02: 0 problemas en las reales y 9/10 fabricadas (falla CO-04-008). LLM-03: 27/30 (90,0 %)
  con las cinco iguales. LLM-04: 0 vacías; 14 glosas fuera de catálogo en los números, como se
  espera. LLM-05: 18/18 sin fuga; 3 negativas en prosa rescatadas como PERDON.
- **VAL:** VAL-01 50/50 reales, 10/10 inexistentes y 10/10 variantes. VAL-02 50 únicas, 0 sin
  categoría, 0 ruta inválida, `uq_catalog_gloss` rechaza la repetida, 0 consultas a `catalog`
  durante 30 frases. VAL-03: 2 señas → 1 seña → 2 señas, 0 archivos de código tocados, APK
  idéntico.
- **INT-01:** 3 recorridos sin problemas. AVT-06: Juan 2.823.936 B de animación (9,31 % de
  30.323.680 B), por seña 56.485 B (41.136–79.592); Pauline 4.491.912 B (16,13 %); APK debug
  40.295.947 B, demo 40.272.974 B, release 35.065.285 B.
- **REN-08:** texto 40 B sube / 166 B baja por frase; voz 96.620 B sube / 202 B baja.
  **REN-09:** 66 frases en 302,9 s = 13,1/min (11–14 por minuto completo), 0 errores; latencia
  texto→última oración p50 1,4 ms (caché) y p95 747,5 ms.
- **TRA-01:** 6 aristas, 4 por interfaz y 2 saltos (`normalize_gloss` en `llm_client.py` y
  `gloss_lexicon.py`). **TRA-02:** ruff 0, mypy 0 en 42 archivos, ktlint 0.

## Qué quedó pendiente

- **P2 y P3:** las 51 pruebas restantes siguen «Sin ejecutar» (40) o «Bloqueada» (11) en el
  libro. El carril P1 no las toca.
- **e5-small** (PR-CAC-01) y **spaCy `sm`** (PR-HUE-01): conseguir los pesos y repetir solo esas
  dos comparaciones.
- **PR-LLM-02** (CO-04-008): el borde del parser que pierde `TRABAJAR` sigue abierto; es del
  backend, no de P1.
- **PR-TRA-01:** decidir si `normalize_gloss` cuenta como salto de interfaz o como kernel
  compartido. Si cuenta, hay que moverlo a un módulo neutral.
- **PR-VAL-03:** decidir si el descarte silencioso de la glosa retirada cumple «salir como no
  disponible» o si el lector debe emitir `available=false` para que la app avise.
- **PR-VAL-02:** decidir si `/health.catalog_glosses` debe contar solo las 50 canónicas y no los
  sinónimos. Es un cambio de una línea en `main.py`, pero cambia un número que ya se citó en
  otras rondas.
