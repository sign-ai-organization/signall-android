# Plan de pruebas · carril P1 · ronda de evaluación 2026-11-06

Documento de trabajo con **todo lo que se hizo en el carril P1** (las 23 pruebas de texto y
estático), los resultados completos, las observaciones y lo que no se pudo medir. No sustituye
al plan ni al libro: las fichas mandan y el libro es la fuente de los resultados. Este archivo
explica cómo se llegó a esos números.

- **Fecha de ejecución:** 06/11/2026.
- **Ronda:** evaluación (la segunda ronda; la del 5/11 quedó como diagnóstica).
- **Carril:** P1, texto y estático.
- **Ejecutor:** agente IA, sesión del 2026-11-06.
- **Rama:** `main`, commit `72fd58f`.
- **Libro:** `resultados-consolidados.xlsx`, 148 filas (74 de la ronda diagnóstica + 74 de la
  ronda de evaluación). Las 23 de P1 están llenas; las otras 51 quedaron «Sin ejecutar» o
  «Bloqueada» hasta que las corran P2 y P3.
- **Evidencia:** `evidencias/<BLOQUE>/PR-*_E_20261106_*`, 50 archivos.
- **Sello:** `evidencias/PREFLIGHT/SELLO-P1_E_20261106_01.json`.

---

## 1. Qué se hizo, en orden

### 1.1. Preflight y sello del ambiente

Antes de medir nada se comprobó que el sistema fuera el que se quería medir y se guardó el
sello. Sin ese sello una fila no cuenta: es lo que faltó el 4/11, cuando se midió con un modelo
local sin saberlo.

| Dato | Valor |
|---|---|
| Servidor | canónico (la única máquina disponible) |
| Commit backend | `9c74e76` |
| Commit STT | `9787e49` |
| Commit app | `298e80c` |
| APK | `debug`, sha256 `52958cfcab1ee6dcddaa1edfa2e47b44a2c5f02a55c7b8f2ec78fdb39a047034`, 40.295.947 B |
| LLM efectivo | `gpt-4o-mini`, `llm_base_url=""`, temperatura 0 |
| Umbral de caché | 0,03 |
| Filas en caché al empezar | 0 |
| Catálogo | 50 filas + 13 sinónimos; `/health` informa 63 |
| STT | `medium`, `cuda`, silencio 1.500 ms |
| Base | Neon `ep-shy-meadow-axe2zkz4-pooler`, org `sign-ai` |
| Teléfono | Redmi Note 9S, Android 11, `1ed6699f` |
| FR-01 | sha256 de los 30 textos: `8d0f260ac4a2421afc74a5c46421e722ab991b8dd1772750b6a0291b42119632` |
| Audios | `manifiesto.csv` sha256: `3934ab636ef5d28267d01563c98bc8d5d3f0a240917e246f56fef17764b182c7` |
| Caché vacía | sí |
| Seña de reserva | no aplica en esta versión (el ajuste no existe) |

### 1.2. Datos de entrada

Los conjuntos del anexo C del plan se extrajeron a JSON con un parser propio, para no
transcribirlos a mano y no crear una segunda copia que se desincronice:

- FR-01 (30 frases), FR-02 (90 opciones), FR-03 (30 paráfrasis), FR-04 (30 trampas), FR-05
  (10 recombinadas), FR-06 (15 largas o con números), FR-07 (18 con instrucciones), FR-08
  (10 con caracteres peligrosos), FR-11 (6 con glosas sin seña).
- CO-01 (200 casos del segmentador), CO-02 (100 oraciones de la huella), CO-03 (70 glosas del
  validador), CO-04 (10 respuestas fabricadas), CO-05 (13 mensajes inválidos).
- AU-05 (10 textos con su división), CT-01 (HAMBRE) y CT-03 (150 opciones incorrectas).

Los 10 textos de AU-01 se volvieron a transcribir con el STT (`POST /transcribe` sobre los WAV
de 16 kHz) para que SEG-01 comparara contra una transcripción de esta ronda y no contra una
copiada de otra.

### 1.3. Método por bloque

- **SEG y HUE:** se corrió el código del backend dentro del contenedor, con los casos completos.
  No se tocó el código: para la ablación de HUE-02 se apagaron las señales con monkeypatch en
  memoria, una a la vez.
- **CAC:** CAC-01/02 se midieron con embeddings reales (MiniLM y mpnet) sobre las 30 frases de
  FR-01 y las 60 variantes, replicando el método de la ronda anterior para que los números sean
  comparables. CAC-03/04 se midieron por el WebSocket, vaciando la caché antes de cada una con
  `DELETE FROM semantic_cache` + `POST /admin/cache/reload` (el endpoint de la Fase 5), sin
  reiniciar el backend.
- **LLM:** LLM-01 a 05 se midieron con el cliente del LLM directo, sin caché, que es lo que pide
  la ficha. El modelo local `qwen3:4b-instruct` se consultó desde el host (Ollama está atado a
  127.0.0.1 y el contenedor no lo alcanza) con el mismo prompt y temperatura 0; el parseo y la
  reparación se aplicaron dentro del contenedor con el mismo código del backend. LLM-05 se
  completó por el WebSocket para ver lo que recibe el usuario.
- **VAL:** VAL-01 y VAL-02 dentro del contenedor (validador, catálogo, intento de duplicado y
  `pg_stat_user_tables` antes/después de 30 frases). VAL-03 con la app real: se guardó la fila de
  HAMBRE, se borró, se reinició el backend, se restauró y se reinició otra vez, con capturas y
  logcat en los tres estados.
- **INT, AVT, REN, TRA:** INT-01 por el WebSocket (texto de una oración, de dos y audio).
  AVT-06 releyendo los `bufferViews` de los GLB y el APK. REN-08 con un proxy TCP que cuenta
  bytes en los dos sentidos; REN-09 con 5 minutos de frases por voz en una sola sesión. TRA-01
  con un escáner AST de importaciones; TRA-02 con ruff, mypy y `./gradlew ktlintCheck`.

### 1.4. Cierre

- 50 archivos de evidencia con la convención `_E_20261106_*`.
- 23 JSON de resultado en `docs/plan-pruebas/resultados-evaluacion/`, con la forma que consume
  `generar-consolidado.py`.
- `resultados-consolidados.xlsx` regenerado con **148 filas**.
- App `debug` reinstalada (mismo hash) y una frase de humo al final: `TU, HAMBRE`, 2 señas
  representadas en 3.827 ms.

---

## 2. Resultados de las 23 pruebas

**Resumen: 8 Pasa · 2 Falla · 13 Se reporta · 0 Bloqueada.**

| ID | Prueba | Estado | Resultado en una línea |
|---|---|---|---|
| PR-SEG-01 | Exactitud del segmentador | Se reporta | 156/200 (78,0 %); 7/9 límite; 20/29 borde; AU-05 7/10 |
| PR-SEG-03 | Alternativas de segmentación | Se reporta | El híbrido del sistema gana: 78,0 % contra 67,5 / 62,8 / 59,2 |
| PR-HUE-01 | Alternativas de tipo de frase | Se reporta | Reglas 75,5 %; spaCy md 59,5 %; gpt-4o-mini 88,0 % |
| PR-HUE-02 | Reglas de la huella | Se reporta | Completa 75,5 %; sin interrogativa 57,0 %; sin negación 58,5 % |
| PR-HUE-03 | Exactitud del tipo de frase | Se reporta | 151/200 (75,5 %); contenido 40/40, orden 22/40 |
| PR-CAC-01 | Alternativas de embeddings | Se reporta | MiniLM 19/30; mpnet 23/30; e5-small no disponible |
| PR-CAC-02 | Calibración del umbral | Se reporta | 0,03: 19/30 paráfrasis y 0/30 trampas; 0,05 ya acepta 2 trampas |
| PR-CAC-03 | Aciertos de la caché | Se reporta | 17/30: formato 9/10, orden 7/10, sinónimos 1/10 |
| PR-CAC-04 | Falsos aciertos | **Pasa** | 0/30, en los tres tipos de trampa |
| PR-LLM-01 | Alternativas de LLM | Se reporta | gpt-4o-mini 20/30; gpt-4o 25/30; qwen 21/30 |
| PR-LLM-02 | Formato de salida | **Falla** | Reales 0 problemas; fabricadas 9/10; falla CO-04-008 |
| PR-LLM-03 | Consistencia | Se reporta | 27/30 (90,0 %) con las cinco respuestas iguales |
| PR-LLM-04 | Entradas problemáticas | **Pasa** | 0 vacías; números fuera de catálogo, como se espera |
| PR-LLM-05 | Inyección de instrucciones | **Pasa** | 18/18 sin fuga; solo glosas o no disponibles |
| PR-VAL-01 | Validación contra el catálogo | **Pasa** | 50/50 reales, 10/10 inexistentes, 10/10 variantes |
| PR-VAL-02 | Catálogo cargado y completo | **Pasa** | 50 únicas; la base rechaza la repetida; 0 consultas al traducir |
| PR-VAL-03 | Agregar una seña sin tocar código | **Pasa** | 50 → 49 → 50 con la app; 0 archivos de código tocados |
| PR-INT-01 | Contrato entre componentes | **Pasa** | 3 recorridos, campos, orden y totales correctos |
| PR-AVT-06 | Peso de las animaciones | Se reporta | Juan 2,82 MB de animación; APK debug 40,30 MB |
| PR-REN-08 | Datos móviles | Se reporta | Texto 40 B sube / 166 B baja; voz 96,6 KB sube / 202 B baja |
| PR-REN-09 | Tasa de procesamiento | Se reporta | 66 frases en 302,9 s = 13,1/min, 0 errores |
| PR-TRA-01 | Separación de componentes | **Falla** | 6 aristas: 4 por interfaz, 2 se saltan (`normalize_gloss` ×2) |
| PR-TRA-02 | Análisis estático | **Pasa** | ruff 0, mypy 0 en 42 archivos, ktlint 0 |

---

## 3. Detalle prueba por prueba

### PR-SEG-01 · Exactitud del segmentador

- **Qué se midió:** qué tan bien el segmentador divide un texto en oraciones.
- **Entrada:** los 200 casos de CO-01 (191 de uso corriente y 9 límite) y las transcripciones
  frescas de los 10 textos de AU-01, comparadas con la división de AU-05.
- **Método:** se pasó cada texto por el segmentador de producción. En la división esperada, `‖`
  es un corte obligatorio y `|` uno opcional (cortar o no es igual de válido). Los 9 casos límite
  incluyen texto vacío, una palabra, texto muy largo y solo muletillas.
- **Resultado:** **156/200 (78,0 %)**.
  - Con puntuación 20/20; sin puntuación 12/20; registro de datos 23/28; coloquial 22/23;
    muletillas 3/20; negativas e imperativas 17/21; casos que no se deben dividir 22/22;
    números dictados 16/17; trampas 14/20.
  - Casos límite 7/9; los 29 marcados como borde 20/29; no borde 129/162.
  - Cortes: precisión 0,903 (130/144) y cobertura 0,808 (118/146).
  - AU-05: 7/10 transcripciones divididas como el plan.
  - Los 191 casos del laboratorio: 191/191 idénticos al `lab_output`.
- **Interpretación:** el segmentador es fuerte con puntuación y débil con muletillas (3/20) y
  sin puntuación (12/20). Los dos casos que fallan de los 9 límite son el texto largo sin
  puntuación y la oración única de 59 palabras, que se parte donde no debería.
- **Evidencia:** `SEG/PR-SEG-01_E_20261106_01.csv` (200 filas) y `.json`.

### PR-SEG-03 · Alternativas de segmentación

- **Qué se midió:** si hay un método mejor que el del sistema.
- **Entrada:** los 191 casos de uso corriente de CO-01.
- **Métodos comparados:** el del sistema (`hibrido_ligero`), solo puntuación, puntuación +
  marcadores del discurso, y las oraciones de spaCy (`es_core_news_md`).
- **Resultado:**

| Método | Exactos | Borde 29 | Precisión | Cobertura | ms/texto |
|---|---:|---:|---:|---:|---:|
| Híbrido ligero (sistema) | **149/191 (78,0 %)** | **20/29** | **0,935** | **0,798** | 1,71 |
| Solo puntuación | 120/191 (62,8 %) | 15/29 | 0,949 | 0,465 | 0,02 |
| Puntuación + marcadores | 129/191 (67,5 %) | 17/29 | 0,938 | 0,588 | 0,06 |
| spaCy sents md | 113/191 (59,2 %) | 15/29 | 0,836 | 0,439 | 4,71 |

- **Interpretación:** el método del sistema es el que más acierta y el que mejor cubre los
  cortes obligatorios. No hay un método mejor; no se anota cambio de diseño. El precio es 1,71
  ms por texto, que es despreciable frente a la llamada al LLM.
- **Evidencia:** `SEG/PR-SEG-03_E_20261106_01.csv` y `.json`.

### PR-HUE-01 · Alternativas para el tipo de frase

- **Qué se midió:** qué forma de obtener el tipo de frase (afirmación, pregunta de sí o no,
  pregunta de contenido, negación u orden) rinde mejor.
- **Entrada:** las 100 oraciones de CO-02, con y sin puntuación (200 entradas).
- **Alternativas:** las reglas del sistema; spaCy en su modelo mediano; `gpt-4o-mini`
  (clasificación cero ejemplos, temperatura 0).
- **Resultado:** reglas **75,5 % (151/200)**; spaCy md **59,5 % (119/200)** a 4,54 ms por
  oración; `gpt-4o-mini` **88,0 % (176/200)** con un costo de **USD 0,0047** por las 200
  entradas.
- **Interpretación:** el LLM es la mejor alternativa (+12,5 puntos), sobre todo en órdenes
  (37/40 contra 22/40 de las reglas) y preguntas de sí o no (30/40 contra 17/40). Pero cuesta
  una llamada por oración y agrega latencia; se reporta como cambio de diseño posible, no se
  adopta en esta ronda.
- **Limitación:** el modelo pequeño de spaCy (`es_core_news_sm`) no está instalado y no se pudo
  descargar. Se midió el mediano, que es el que usa el segmentador.
- **Evidencia:** `HUE/PR-HUE-01_E_20261106_01.csv` y `.json`.

### PR-HUE-02 · Reglas de la huella

- **Qué se midió:** cuánto aporta cada señal de la huella gramatical.
- **Entrada:** las mismas 200 entradas de CO-02.
- **Método:** se apagó una señal a la vez (signo de interrogación, palabra interrogativa,
  negación, imperativo) y se volvió a clasificar.
- **Resultado:**

| Configuración | Exactitud |
|---|---:|
| Huella completa | **75,5 %** |
| Sin el signo de interrogación | 67,0 % |
| Sin la palabra interrogativa | 57,0 % |
| Sin la negación | 58,5 % |
| Sin el imperativo | 65,5 % |

- **Interpretación:** las cuatro señales aportan. La que más pesa es la palabra interrogativa
  (−18,5 puntos); la que menos, el imperativo (−10,0). Ninguna es candidata a quitarse.
- **Evidencia:** `HUE/PR-HUE-02_E_20261106_01.csv` y `.json`.

### PR-HUE-03 · Exactitud del tipo de frase

- **Qué se midió:** qué tan bien la huella final identifica el tipo de cada frase.
- **Entrada:** las 100 oraciones de CO-02, con y sin puntuación (200 entradas).
- **Resultado:** **151/200 (75,5 %)**. Con puntuación 84/100; sin puntuación 67/100.
  - Por tipo: afirmación 34/40; pregunta de sí o no 17/40; pregunta de contenido 40/40;
    negación 38/40; orden 22/40.
  - Borde 8/42; colisiones (señales de más de un tipo) 6.
- **Matriz de confusión (esperado → obtenido):**

| Esperado → | Afirmación | Sí/no | Contenido | Negación | Orden |
|---|---:|---:|---:|---:|---:|
| Afirmación | 34 | 0 | 0 | 4 | 2 |
| Sí/no | 16 | 17 | 3 | 4 | 0 |
| Contenido | 0 | 0 | 40 | 0 | 0 |
| Negación | 2 | 0 | 0 | 38 | 0 |
| Orden | 18 | 0 | 0 | 0 | 22 |

- **Interpretación:** las preguntas de contenido son perfectas (40/40) y la negación es muy
  buena (38/40). El fallo grande es la **orden**: 18 de 40 se leen como afirmación porque el
  verbo imperativo no está en la lista cerrada. Las preguntas de sí o no sin puntuación siguen
  siendo el límite conocido (17/40), porque en español no tienen una marca léxica.
- **Evidencia:** `HUE/PR-HUE-03_E_20261106_01.csv` (200 filas) y `.json`.

### PR-CAC-01 · Alternativas de embeddings

- **Qué se midió:** qué modelo de embeddings reconoce mejor las paráfrasis sin confundir las
  trampas.
- **Entrada:** las 30 frases de FR-01 cargadas, y las 30 paráfrasis de FR-03 y las 30 trampas de
  FR-04.
- **Método:** por cada modelo se indexan las 30 frases con su tipo; cada variante se compara con
  el vecino más cercano del mismo tipo. Distancia = 1 − coseno (lo que calcula pgvector `<=>`).
  El mejor umbral es el que reconoce más paráfrasis sin aceptar ninguna trampa.
- **Resultado:**

| Modelo | Mejor umbral | Paráfrasis | Trampas | ms/embedding | Memoria |
|---|---:|---:|---:|---:|---:|
| `paraphrase-multilingual-MiniLM-L12-v2` (el del sistema) | 0,03 | **19/30** | **0/30** | 14,49 | 1.223 MB |
| `paraphrase-multilingual-mpnet-base-v2` | 0,04 | **23/30** | **0/30** | 36,64 | 1.844 MB |
| `multilingual-e5-small` | — | no medido | no medido | — | — |

- **Interpretación:** mpnet reconoce 4 paráfrasis más que MiniLM, a 2,5 veces el tiempo de
  embedding y 1,5 veces la memoria. Es un cambio de diseño posible, no una urgencia.
- **Limitación:** `e5-small` no se pudo cargar porque los pesos no están en la máquina y
  Hugging Face responde 401. mpnet sí estaba en la caché local y se pudo medir.
- **Evidencia:** `CAC/PR-CAC-01_E_20261106_01.csv` y `.json`.

### PR-CAC-02 · Calibración del umbral

- **Qué se midió:** cómo cambian los aciertos y los falsos aciertos según el umbral, para elegir
  el definitivo.
- **Entrada:** las 30 paráfrasis de FR-03 y las 30 trampas de FR-04, con las 30 frases de FR-01
  cargadas.
- **Resultado (MiniLM):**

| Umbral | Paráfrasis | Trampas |
|---:|---:|---:|
| 0,01 | 14/30 | 0/30 |
| 0,02 | 18/30 | 0/30 |
| **0,03** | **19/30** | **0/30** |
| 0,035 | 20/30 | 1/30 |
| 0,04 | 20/30 | 2/30 |
| 0,05 | 22/30 | 2/30 |
| 0,08 | 24/30 | 7/30 |
| 0,12 | 25/30 | 12/30 |
| 0,20 | 27/30 | 18/30 |

- **Interpretación:** 0,03 es el umbral que más paráfrasis reconoce sin aceptar ninguna trampa.
  A partir de 0,035 aparece el primer falso acierto, y en 0,08 ya se cuelan 7 de 30. El umbral
  actual está bien elegido.
- **Evidencia:** `CAC/PR-CAC-02_E_20261106_01.csv` y `.json`.

### PR-CAC-03 · Aciertos de la caché

- **Qué se midió:** si la caché reutiliza la traducción cuando la frase es la misma aunque
  cambie un poco.
- **Entrada:** las 30 frases de FR-01 y las 30 variantes de FR-03: 10 de formato, 10 de
  sinónimos y 10 de orden.
- **Método:** se vació la caché, se tradujeron las 30 frases de FR-01 y después cada variante.
  Una variante acierta si la oración cambiada sale de la caché con las glosas de su propia
  frase.
- **Resultado:** **17/30**.
  - Formato **9/10**; orden **7/10**; sinónimos **1/10**.
  - Las 13 fallas son misses de caché (la oración cambiada fue al LLM), no falsos aciertos.
- **Interpretación:** el reconocimiento de formato y de orden funciona razonablemente. El punto
  débil son los sinónimos: la tabla `catalog_synonym` tiene 13 filas y no cubre las variantes de
  FR-03 (`regular`, `prudente`, `señor`, `jamás`, `disculpe`, `excelente`, `un poquito`,
  `chicos`, `bastante`). Solo acierta 1 de 10.
- **Evidencia:** `CAC/PR-CAC-03_E_20261106_01.csv` y `.json`.

### PR-CAC-04 · Falsos aciertos

- **Qué se midió:** si la caché confunde frases que se parecen pero significan otra cosa.
- **Entrada:** las 30 frases de FR-01 y las 30 trampas de FR-04: 10 con negación, 10 con una
  palabra distinta y 10 con otra persona.
- **Resultado:** **0/30 falsos aciertos**. Negación 0/10; palabra distinta 0/10; otra persona
  0/10.
- **Interpretación:** ninguna trampa salió de la caché con glosas de otra frase. El criterio se
  cumple.
- **Evidencia:** `CAC/PR-CAC-04_E_20261106_01.csv` y `.json`.

### PR-LLM-01 · Alternativas de LLM

- **Qué se midió:** qué modelo traduce mejor el texto a glosas.
- **Entrada:** las 30 frases de FR-01, sin caché, con el mismo prompt y temperatura 0.
- **Resultado:**

| Modelo | Secuencia exacta | Mismo conjunto | Orden relativo | Glosas en catálogo | ms/frase | Costo |
|---|---:|---:|---:|---:|---:|---:|
| `gpt-4o-mini` (el del sistema) | 20/30 | 22/30 | 27/30 | 107/107 | 654,0 | USD 0,002383 |
| `gpt-4o` | **25/30** | **26/30** | **28/30** | 108/108 | 721,9 | USD 0,039743 |
| `qwen3:4b-instruct` (local) | 21/30 | 22/30 | 29/30 | 100/100 | 462,5 | USD 0 |

- **Interpretación:** `gpt-4o` acierta 5 frases más que `gpt-4o-mini` (25 contra 20) a 16,7
  veces el costo. `qwen` local es el más rápido y gratis, y su orden relativo es el mejor
  (29/30), pero produce menos glosas en catálogo (100/100, sin ninguna fuera) y 21/30 exactas.
  No hay un cambio de modelo que se justifique solo por este número.
- **Evidencia:** `LLM/PR-LLM-01_E_20261106_01.csv` (90 filas) y `.json`.

### PR-LLM-02 · Formato de salida

- **Qué se midió:** que lo que devuelve el modelo se lea bien y que una respuesta mal formada no
  rompa el sistema.
- **Entrada:** las 30 respuestas reales de FR-01 y las 10 respuestas fabricadas de CO-04.
- **Resultado:** parte A, **0/30 respuestas reales con problemas de formato**. Parte B,
  **9/10 fabricadas**.
  - Falla **CO-04-008**: `TIO, TRABAJAR. Espero que te sirva.` se lee como solo `TIO`; el parser
    corta en el punto y descarta la oración siguiente, con lo que se pierde `TRABAJAR`.
- **Interpretación:** el único borde que queda es del parser, no del modelo: la respuesta trae
  las dos glosas. La ficha pide que ninguna respuesta se lea mal, así que la prueba **falla**.
- **Evidencia:** `LLM/PR-LLM-02_E_20261106_01.csv` y `.json`.

### PR-LLM-03 · Consistencia

- **Qué se midió:** cuánto varía el modelo al traducir la misma frase varias veces.
- **Entrada:** las 30 frases de FR-01, cinco veces cada una.
- **Resultado:** **27/30 (90,0 %)** dieron las cinco respuestas iguales.
  - Varían FR-01-010 (alterna `TU` y `YO`), FR-01-026 (a veces duplica `PERMISO`) y FR-01-030
    (a veces pierde `HERMANO`).
- **Interpretación:** la consistencia es alta. Las tres frases que varían son las que combinan
  pronombre implícito o dos oraciones con el mismo verbo.
- **Evidencia:** `LLM/PR-LLM-03_E_20261106_01.csv` (30 filas × 5 respuestas) y `.json`.

### PR-LLM-04 · Entradas problemáticas

- **Qué se midió:** cómo responde el modelo ante oraciones muy largas y ante números.
- **Entrada:** las 15 frases de FR-06: 5 muy largas y 10 con números.
- **Resultado:** **0 respuestas vacías**; las 5 oraciones largas, 0 glosas fuera del catálogo;
  las 10 con números, **14 glosas fuera del catálogo** (los dígitos), que es lo esperado porque
  el corpus no tiene números. El sistema siguió funcionando y representó la frase. Tiempo
  mediano 743,3 ms.
- **Interpretación:** el criterio se cumple: nada rompe el sistema. Los números salen como
  glosas no disponibles, que es lo que la ficha pide.
- **Evidencia:** `LLM/PR-LLM-04_E_20261106_01.csv` y `.json`.

### PR-LLM-05 · Inyección de instrucciones

- **Qué se midió:** si un hablante puede cambiar el comportamiento del modelo diciéndole
  instrucciones.
- **Entrada:** las 18 frases de FR-07, por el WebSocket, para ver qué recibe el usuario.
- **Resultado:** **18/18** frases: el usuario solo ve glosas del catálogo o glosas marcadas como
  no disponibles; **0 fugas** de configuración. Tres respuestas fueron negativas en prosa («Lo
  siento, no puedo ayudar con eso») y el lector rescató `PERDON`; dos dejaron palabras sensibles
  como glosas no disponibles (`CLAVE`, `SISTEMA`), sin valores. Ninguna respuesta obedeció la
  instrucción.
- **Interpretación:** el criterio se cumple. Las palabras sensibles que aparecen son glosas del
  catálogo o no disponibles, no datos de configuración.
- **Evidencia:** `LLM/PR-LLM-05_E_20261106_01.csv` y `.json`.

### PR-VAL-01 · Validación de glosas contra el catálogo

- **Qué se midió:** que el validador reconozca las glosas reales y marque como no disponibles
  las que no existen.
- **Entrada:** las 70 glosas de CO-03: 50 reales, 10 inexistentes y 10 variantes.
- **Resultado:** **50/50 reales**, **10/10 inexistentes**, **10/10 variantes**: 3 con tilde, 3 en
  minúsculas, 2 con punto y 2 con un espacio de más.
- **Interpretación:** el criterio se cumple completo.
- **Evidencia:** `VAL/PR-VAL-01_E_20261106_01.csv` y `.json`.

### PR-VAL-02 · Catálogo cargado y completo

- **Qué se midió:** que el catálogo tenga las 50 señas completas, sin repetidas, que la base
  rechace una repetida y que el sistema no consulte la tabla al traducir.
- **Resultado:**
  - 50 filas, 50 glosas distintas, 50 `id_sign`, 0 sin categoría, 0 ruta inválida, 0 duplicadas.
  - La base rechaza la misma glosa con un `id_sign` nuevo (`uq_catalog_gloss`) y el mismo `id`
    (clave primaria).
  - En memoria: **50 glosas canónicas + 13 sinónimos = 63 llaves**. `/health` informa **63**
    porque cuenta los sinónimos.
  - Durante las 30 frases de FR-01: **0 consultas a `catalog`** (`seq_scan` e `idx_scan` sin
    cambio).
- **Interpretación:** el catálogo está completo y la falla de la ronda anterior (no había índice
  único) ya está corregida. Queda una observación: el número de `/health` no es el del catálogo
  (63 contra 50) porque suma los alias.
- **Evidencia:** `VAL/PR-VAL-02_E_20261106_01.csv` (50 filas) y `.json`.

### PR-VAL-03 · Agregar una seña sin tocar código

- **Qué se midió:** que agregar una seña al catálogo no exija modificar el código del backend.
- **Entrada:** la seña de CT-01 (HAMBRE, `id_sign=19`), su fila del catálogo y la frase
  FR-01-007.
- **Resultado:**
  1. Con la fila: el WebSocket devuelve `TU` y `HAMBRE`, las dos disponibles. La app representa
     **2 señas en 3.809 ms**.
  2. Se borra la fila (catálogo en 49) y se reinicia el backend: el WebSocket devuelve solo
     `TU`; `HAMBRE` se descarta antes de la validación. La app representa **1 seña en 1.772 ms**.
  3. Se restaura la fila (catálogo en 50) y se reinicia: vuelven `TU` y `HAMBRE`, **2 señas en
     3.782 ms**.
  - Archivos de código del backend tocados: **0**. No se cambió código ni asset de la app; el
    APK recompilado es idéntico (`52958cfc…`).
- **Observación:** cuando se retira HAMBRE, la glosa no se muestra como «no disponible», se
  descarta en la reparación de glosas y la app simplemente deja de representarla. El criterio
  literal dice «sale como no disponible»; el efecto visible es que no se representa. Queda
  anotado para que el owner decida si eso basta o si el lector debe emitir `available=false`.
- **Evidencia:** `VAL/PR-VAL-03_E_20261106_01.json`, `.txt` y tres capturas `.png`.

### PR-INT-01 · Contrato entre componentes

- **Qué se midió:** que lo que el backend entrega a la app tenga el formato, el orden y los
  totales que ella espera, y que acepte lo que ella envía.
- **Entrada:** una frase de una oración de FR-01-C, una de varias oraciones de FR-01-D y un
  audio de AU-01.
- **Resultado:** **3 recorridos sin problemas.**
  - Texto de una oración: `capability → sentence_trace → segment → trace → done`.
  - Texto de dos oraciones: índices 0/2 y 1/2, total 2, dos `segment`.
  - Audio: `transcript` final, el texto reenviado → `segment → done`.
  - Los campos de cada segment (`type`, `glosses` con `text`, `available`, `glb_path`,
    `semantic_category`), el orden y los totales coinciden. `/health` responde.
- **Interpretación:** el contrato se cumple.
- **Evidencia:** `INT/PR-INT-01_E_20261106_01.csv` y `.json`.

### PR-AVT-06 · Peso de las animaciones

- **Qué se midió:** cuánto ocupan las animaciones y el instalador.
- **Método:** se leyeron los `bufferViews` que usan los samplers de cada animación en los GLB
  empaquetados, se agruparon por seña (4 variantes de cara) y se midió el APK.
- **Resultado:**

| Avatar | GLB | Animación | % del GLB | Por seña (prom.) | Mín–máx |
|---|---:|---:|---:|---:|---:|
| Juan | 30.323.680 B | **2.823.936 B** | 9,31 % | 56.485 B | 41.136–79.592 |
| Pauline | 27.850.104 B | **4.491.912 B** | 16,13 % | 89.838 B | 60.372–114.756 |

  - Por clip (Juan): promedio 53.148,8 B; mínimo 37.800; máximo 76.256.
  - Instalador: **debug 40.295.947 B**; demo 40.272.974 B; release 35.065.285 B.
- **Interpretación:** la animación pesa poco frente al GLB completo; la mayor parte del archivo
  es geometría y morph targets de la cara. El APK creció frente a la ronda anterior (38,94 MB
  debug entonces, 40,30 MB ahora).
- **Evidencia:** `AVT/PR-AVT-06_E_20261106_01.csv` y `.json`.

### PR-REN-08 · Datos móviles

- **Qué se midió:** cuántos datos consume una traducción, por texto y por voz.
- **Método:** un proxy TCP local cuenta los bytes que suben y bajan; el cliente WebSocket habla
  con el proxy.
- **Resultado:**

| Modo | Sube (prom.) | Rango | Baja (prom.) | Rango |
|---|---:|---:|---:|---:|
| Texto | **40,4 B** | 29–67 | **165,8 B** | 103–372 |
| Voz | **96.620,6 B** | 56.879–126.465 | **202,3 B** | 115–456 |

- **Interpretación:** el consumo es muy bajo, sobre todo lo que baja: el avatar vive en la app,
  así que la respuesta solo trae glosas y metadatos. La voz sube el audio (~96 KB por frase de
  ~4 s); el texto sube solo el frame.
- **Evidencia:** `REN/PR-REN-08_E_20261106_01.csv` y `.json`.

### PR-REN-09 · Tasa de procesamiento

- **Qué se midió:** cuántas frases por minuto procesa el sistema con una sesión activa.
- **Entrada:** las 10 frases de FR-01-A con sus audios de AU-01, repetidas en orden durante
  5 minutos.
- **Resultado:** **66 frases en 302,9 s = 13,1 frases/min** (11–14 por minuto en los cinco
  minutos completos; el minuto parcial suma 1), 66 completadas, **0 errores**. Latencia texto →
  última oración: p50 1,4 ms y p95 747,5 ms (las frases repetidas aciertan en caché; el p95 es
  la primera vez de cada una).
- **Interpretación:** el ritmo está limitado por la duración del audio, no por el pipeline: cada
  frase tarda ~4,5 s porque así dura el audio. El sistema sostiene una sesión de 5 minutos sin
  errores.
- **Evidencia:** `REN/PR-REN-09_E_20261106_01.csv` (66 filas) y `.json`.

### PR-TRA-01 · Separación de componentes

- **Qué se midió:** que los componentes se comuniquen solo por sus interfaces.
- **Entrada:** el código del backend.
- **Resultado:** **6 aristas entre los cuatro componentes** (procesamiento semántico, catálogo,
  orquestación y transcripción); **4 pasan por interfaz** (`OrchestratorService`,
  `SttServiceClient`, `NLPService`, `ValidatorService`) y **2 se saltan la interfaz**:
  `normalize_gloss`, importado desde `validation` por `llm_client.py` y por `gloss_lexicon.py`.
- **Interpretación:** la sesión ya no importa el `Orchestrator` concreto (eso se corrigió), pero
  el salto de `normalize_gloss` sigue y ahora está en dos archivos. La prueba **falla** con el
  criterio literal. Es una función pura, no un servicio; el owner decide si cuenta como salto o
  si se mueve a un módulo neutral.
- **Evidencia:** `TRA/PR-TRA-01_E_20261106_01.csv` y `.json`.

### PR-TRA-02 · Análisis estático

- **Qué se midió:** que el código siga la guía de estilo y pase el análisis sin advertencias
  críticas.
- **Resultado:** backend `ruff 0.17.1`, **0 advertencias**; `mypy 2.4.0`, **0 errores en 42
  archivos**; Android `ktlintCheck`, **exit 0, sin advertencias**.
- **Interpretación:** el criterio se cumple.
- **Evidencia:** `TRA/PR-TRA-02_E_20261106_01.txt` y `_02.txt`.

---

## 4. Observaciones: lo que más importa

1. **PR-VAL-02 ya pasa la parte que fallaba, pero el contador engaña.** La base rechaza la glosa
   repetida (`uq_catalog_gloss`, migración 005), que era la falla de la ronda anterior. Pero
   `/health` publica `catalog_glosses: 63` porque `CatalogRepository` mete los 13 sinónimos en el
   mismo diccionario. El operario lee 63 glosas donde hay 50.

2. **PR-CAC-03 sigue en 17/30 y los sinónimos apenas aportan 1/10.** La tabla
   `catalog_synonym` tiene 13 filas y no cubre las variantes de FR-03. El reconocimiento de
   formato (9/10) y de orden (7/10) sí funciona.

3. **mpnet reconoce 4 paráfrasis más que MiniLM** (23/30 contra 19/30, con 0 trampas) a 2,5
   veces el tiempo de embedding y 1,5 veces la memoria. Es un cambio de diseño posible, no una
   urgencia.

4. **El LLM como clasificador de tipo de frase sube 12,5 puntos** (88,0 % contra 75,5 % de las
   reglas), sobre todo en órdenes (37/40 contra 22/40) y preguntas de sí o no (30/40 contra
   17/40). Cuesta una llamada por oración; se reporta, no se adopta.

5. **PR-LLM-02 sigue fallando en el mismo borde del parser**: `TIO, TRABAJAR. Espero que te
   sirva.` se lee como solo `TIO`. La oración de cortesía después del punto se come `TRABAJAR`.

6. **PR-TRA-01 sigue fallando, pero cambió la causa.** La sesión ya no importa el `Orchestrator`
   concreto (usa `OrchestratorService`) y eso cierra el salto anterior. Ahora hay dos saltos de
   `normalize_gloss`: `llm_client.py` y el nuevo `gloss_lexicon.py` lo importan desde
   `validation`.

7. **PR-VAL-03 cumple el ciclo, pero la glosa retirada no se anuncia.** Al borrar HAMBRE, la app
   representa solo `TU`; no muestra «no disponible» porque `_repair_glosses` descarta la glosa
   antes de la validación. El criterio dice «sale como no disponible»; el efecto visible es que
   deja de aparecer.

8. **El segmentador es fuerte con puntuación y débil con muletillas.** 20/20 con puntuación,
   12/20 sin puntuación y 3/20 con muletillas. Es el mismo patrón de la ronda anterior.

9. **La huella gramatical es perfecta en preguntas de contenido (40/40) y muy buena en negación
   (38/40), pero la orden es el hueco (22/40).** 18 de 40 órdenes se leen como afirmación porque
   el verbo imperativo no está en la lista cerrada.

---

## 5. Lo que no se pudo medir (y por qué)

Esta es la respuesta a la pregunta de si eran requisitos necesarios que no se pudieron usar.
**No, no eran requisitos funcionales del sistema: eran alternativas de comparación.** El sistema
funciona con los modelos que tiene; lo que faltó fue un punto de comparación, no una pieza del
producto.

| Qué faltó | Qué es | ¿Es un requisito? | Efecto |
|---|---|---|---|
| `multilingual-e5-small` (PR-CAC-01) | Tercer modelo de embeddings para comparar | No. La ficha es «comparación de alternativas»; el sistema usa MiniLM y funciona | La comparación quedó con 2 de 3 modelos. Se reporta, no se bloquea |
| `es_core_news_sm` (PR-HUE-01) | Modelo pequeño de spaCy para comparar | No. El sistema usa `es_core_news_md`; la ficha compara alternativas | La comparación quedó con 1 de 2 tamaños de spaCy. Se reporta |
| Las 51 pruebas de P2 y P3 | Pruebas de otros carriles | No son de P1 | Quedan «Sin ejecutar» o «Bloqueada» hasta que las corran los otros carriles |

- **`e5-small`:** los pesos no están descargados en esta máquina y Hugging Face responde 401
  (no hay acceso a los repositorios de modelos desde el contenedor). `mpnet` sí estaba en la
  caché local y se pudo medir. No es un fallo del sistema: es una limitación del ambiente.
- **`spaCy sm`:** solo está instalado `es_core_news_md`; el paquete `sm` no está y no se pudo
  descargar. Se midió el mediano, que es el que usa el segmentador en producción.
- **Lo que sí es funcional y se midió completo:** VAL-01, VAL-02, VAL-03, INT-01, CAC-03,
  CAC-04, LLM-02, LLM-04, LLM-05, TRA-02. No quedó ningún requisito funcional de P1 sin cubrir.

Una aclaración sobre las 11 «Bloqueada» que aparecen en la ronda de evaluación: son las mismas
de la ronda diagnóstica (AVT-04, AVT-05, INT-04, PER-01 a 06, etc.), no son de P1 y no cambian
por esta corrida.

---

## 6. Qué queda pendiente y qué decisiones son del owner

- **P2 y P3:** las 51 pruebas restantes siguen «Sin ejecutar» (40) o «Bloqueada» (11) en el
  libro. El carril P1 no las toca.
- **e5-small y spaCy `sm`:** conseguir los pesos y repetir solo esas dos comparaciones.
- **PR-LLM-02 (CO-04-008):** el borde del parser que pierde `TRABAJAR` sigue abierto; es del
  backend, no de P1.
- **PR-TRA-01:** decidir si `normalize_gloss` cuenta como salto de interfaz o como kernel
  compartido. Si cuenta, hay que moverlo a un módulo neutral.
- **PR-VAL-03:** decidir si el descarte silencioso de la glosa retirada cumple «salir como no
  disponible» o si el lector debe emitir `available=false` para que la app avise.
- **PR-VAL-02:** decidir si `/health.catalog_glosses` debe contar solo las 50 canónicas y no los
  sinónimos. Es un cambio de una línea en `main.py`, pero cambia un número que ya se citó en
  otras rondas.
- **Reinicios en una ronda paralela:** VAL-02 y VAL-03 reinician el backend. En esta corrida se
  pudo porque no había otro carril; en una ronda con P2 y P3 en paralelo hay que coordinarlo con
  quien tenga el backend y el teléfono.

---

## 7. Dónde está cada cosa

| Qué | Dónde |
|---|---|
| Libro con las dos rondas | `docs/plan-pruebas/resultados-consolidados.xlsx` (148 filas) |
| Resultados de P1 (23 JSON) | `docs/plan-pruebas/resultados-evaluacion/PR-*.json` |
| Evidencia | `docs/plan-pruebas/evidencias/<BLOQUE>/PR-*_E_20261106_*` (50 archivos) |
| Sello del ambiente | `docs/plan-pruebas/evidencias/PREFLIGHT/SELLO-P1_E_20261106_01.json` |
| Resumen legible | este archivo, `docs/historia/plan-pruebas/RESULTADOS-P1-2026-11-06.md` |
| Bitácora de sesión | `openspec/sessions/2026-11-06-ronda-p1-23-pruebas.md` |
| Commit | `72fd58f` en `main` |

## 8. Estado final del sistema

Backend `ok` con 50 filas de catálogo y la caché vacía; STT `ok` con `medium`/`cuda`; app `debug`
reinstalada con el mismo APK (`52958cfc…`) y abierta en «Inactivo». Sin procesos del arnés, sin
contenedores temporales y sin el proxy de Ollama.

## 9. Cómo se reprodujo (para otra sesión)

Los guiones de la corrida quedaron fuera del repositorio, en
`/tmp/opencode/p1-2026-11-06/scripts/` (el `/tmp` se puede limpiar; por eso los resultados y la
evidencia sí están versionados). Los pasos que importan:

- `parse_anexo.py` extrae los conjuntos del anexo C a JSON.
- `transcribe_au01.py` transcribe los 10 audios de AU-01 con el STT.
- `c_seg.py`, `c_hue.py`, `c_hue03.py`, `c_cac.py`, `c_llm.py`, `c_val.py`, `c_val03.py` corren
  dentro del contenedor con `PYTHONPATH=/app`.
- `h_ws.py` (INT-01, CAC-03/04, LLM-04/05, REN-09), `h_ren08.py` (proxy de bytes),
  `h_tra01.py` (importaciones), `h_avt06.py` (GLB) y `h_val02.py` corren en el host.
- `clear_cache.sh` vacía la caché con `DELETE` + `POST /admin/cache/reload`.
- `build_results.py` escribe la evidencia y los JSON; `generar-consolidado.py` arma el libro.

La regla que se siguió en todo momento: **antes de medir, el sello; después de medir, la
evidencia y el número con su origen.**
