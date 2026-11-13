# Plan de pruebas · carril P1 · ronda de evaluación 2026-11-10

Documento de trabajo con lo que se hizo en el carril P1 en la tercera ronda: las **17 pruebas
que no estaban marcadas como «Pasó · no repetir»** en el reparto del 10/11. No sustituye al
plan ni al libro: las fichas mandan y el libro es la fuente de los resultados.

- **Fecha de ejecución:** 10/11/2026.
- **Ronda:** evaluación (repite solo lo pendiente del carril 1).
- **Carril:** P1, texto y estático (25 pruebas; 8 ya habían pasado el 6/11 y no se repiten).
- **Ejecutor:** agente IA, sesión del 2026-11-10.
- **Rama:** `main`, commit `dc90b99` de la app, `a7d4eec` del backend, `4824979` del STT.
- **Sello:** `evidencias/PREFLIGHT/SELLO-P1_E_20261110_01.json`.
- **Arnés:** `docs/plan-pruebas/arnes/` (esta vez queda versionado; el del 6/11 se perdió con
  el `/tmp`).

---

## 1. Qué se corrió y qué no

| # | Prueba | Estado 6/11 | Estado 10/11 |
|---|---|---|---|
| 1 | PR-SEG-01 · Exactitud del segmentador | Se reporta | Se reporta |
| 2 | PR-SEG-03 · Alternativas de segmentación | Se reporta | Se reporta |
| 3 | PR-HUE-01 · Alternativas para el tipo de frase | Se reporta | Se reporta |
| 4 | PR-HUE-02 · Reglas de la huella | Se reporta | Se reporta |
| 5 | PR-HUE-03 · Exactitud del tipo de frase | Se reporta | Se reporta |
| 6 | PR-CAC-01 · Alternativas de embeddings | Se reporta | Se reporta |
| 7 | PR-CAC-02 · Calibración del umbral | Se reporta | Se reporta |
| 8 | PR-CAC-03 · Aciertos de la caché | Se reporta | Se reporta |
| 9 | PR-LLM-01 · Alternativas de LLM | Se reporta | Se reporta |
| 10 | PR-LLM-02 · Formato de salida | **Falla** | **Falla** |
| 11 | PR-LLM-03 · Consistencia | Se reporta | Se reporta |
| 12 | PR-AVT-06 · Peso de las animaciones | Se reporta | Se reporta |
| 13 | PR-REN-04 · Arranque en frío | Se reporta (era de P2) | Se reporta |
| 14 | PR-REN-08 · Datos móviles | Se reporta | Se reporta |
| 15 | PR-REN-09 · Tasa de procesamiento | Se reporta | Se reporta |
| 16 | PR-TRA-01 · Separación de componentes | **Falla** | **Falla** |
| 17 | PR-TRA-03 · Levantar el entorno desde cero | Pasa (era de P2) | **Pasa** |

**Resumen: 1 Pasa · 2 Falla · 14 Se reporta.** Las dos fallas siguen siendo las mismas del
6/11 y ninguna tiene arreglo de código desde entonces.

---

## 2. Resultados en una línea

| ID | Resultado |
|---|---|
| PR-SEG-01 | 172/200 (86,0 %); 7/9 casos límite; AU-05 6/10; precisión 0,931 y cobertura 0,806 |
| PR-SEG-03 | El híbrido del sistema gana: 86,4 % contra 75,4 / 68,6 / 64,9 |
| PR-HUE-01 | Reglas 75,5 %; spaCy md 45,0 %; gpt-4o-mini 86,0 % |
| PR-HUE-02 | Completa 75,5 %; sin interrogativa 47,0 %; sin negación 58,5 %; sin imperativa 65,5 % |
| PR-HUE-03 | 151/200 (75,5 %); contenido 40/40, negación 38/40, orden 22/40 |
| PR-CAC-01 | MiniLM 22/34 con umbral 0,04; mpnet 18/34 con 0,02; e5-small no disponible (401) |
| PR-CAC-02 | Con el umbral del sistema (0,05): 23/34 paráfrasis y 1 trampa; con 0,03: 21/34 y 0 |
| PR-CAC-03 | 23/34 oraciones cambiadas: formato 11/12, orden 9/10, sinónimos 3/12; 1 falso acierto |
| PR-LLM-01 | gpt-4o-mini 20/30; gpt-4o 24/30; qwen3:4b 20/30 (medido desde el host) |
| PR-LLM-02 | Reales 0/30 con problemas; fabricadas 9/10; sigue fallando CO-04-008 |
| PR-LLM-03 | 28/30 con las cinco respuestas iguales |
| PR-AVT-06 | Juan 2.823.936 B de animación (9,31 % del GLB); Pauline 4.491.912 B (16,13 %); APK 40.297.811 B |
| PR-REN-04 | Backend listo en 18,5 s y STT en 6,0 s; primera traducción 1.186 ms y siguientes 917–1.129 ms |
| PR-REN-08 | Texto 64 B sube / 498 B baja; voz 132.638 B sube / 575 B baja (medias por frase) |
| PR-REN-09 | 68 frases en 303,9 s = 13,4/min, 0 errores |
| PR-TRA-01 | 6 aristas: 4 por interfaz, 2 saltos (`normalize_gloss` ×2) → **Falla** |
| PR-TRA-03 | Clon limpio + venv + `pip install` + `uvicorn` → `/health` `ok` |

---

## 3. Detalle prueba por prueba

### PR-SEG-01 · Exactitud del segmentador

- **Qué se midió:** qué tan bien el segmentador divide un texto en oraciones.
- **Resultado:** 172/200 (86,0 %). Por grupo: con puntuación 20/20; sin puntuación 12/20;
  registro 23/28; coloquial 22/23; muletillas 18/20; negativas e imperativas 17/21; casos que
  no se deben dividir 22/22; números 16/17; trampas 15/20. Casos límite 7/9. AU-05 6/10.
- **Por qué sube frente al 78,0 % del 6/11:** el arnés del 6/11 comparaba la división esperada
  con los corchetes de las muletillas **como texto literal** (`[eh]`, `[o sea]`), así que el
  sistema no podía acertar nunca. La ficha dice que una muletilla «se puede pegar a un
  fragmento vecino o descartar», y el arnés de esta ronda genera todas las variantes válidas.
  El grupo Muletillas pasa de 3/20 a 18/20 por eso, no por un cambio del sistema.
- **Evidencia:** `evidencias/SEG/PR-SEG-01_E_20261110_01.csv` y `.json`.

### PR-SEG-03 · Alternativas de segmentación

- **Resultado:** híbrido del sistema 86,4 % (165/191); puntuación + marcadores 75,4 %;
  solo puntuación 68,6 %; spaCy `sents` 64,9 %. Coste: 2,19 ms por texto el híbrido,
  0,02 el de solo puntuación.
- **Interpretación:** el híbrido gana por 11 puntos al siguiente, y ese es el que está en
  producción. La comparación se reimplementó (el arnés del 6/11 no se conservó), así que las
  cifras no son comparables una a una con las del 6/11; el orden sí es el mismo.
- **Evidencia:** `evidencias/SEG/PR-SEG-03_E_20261110_01.csv` y `.json`.

### PR-HUE-01 · Alternativas para el tipo de frase

- **Resultado:** reglas 75,5 % (151/200); spaCy md 45,0 %; gpt-4o-mini 86,0 % (172/200).
- **Detalle:** el modelo gana sobre todo en órdenes (40/40 contra 22/40) y preguntas de sí o
  no (23/40 contra 17/40). Cuesta una llamada por oración.
- **Aviso:** la alternativa «spaCy md» usa solo las etiquetas morfológicas del modelo
  (`Mood=Imp`, `PronType`, polaridad) y rinde 45,0 %; el 59,5 % del 6/11 venía de otro arnés
  que no se conservó. No es una regresión del sistema: las reglas no se tocaron.
- **Evidencia:** `evidencias/HUE/PR-HUE-01_E_20261110_01.*`.

### PR-HUE-02 · Reglas de la huella

- **Resultado:** completa 75,5 %; sin interrogativa 47,0 %; sin negación 58,5 %; sin
  imperativa 65,5 %.
- **Interpretación:** la interrogativa es la regla que más aporta (28,5 puntos), después la
  negación (17,0) y la imperativa (10,0). El hueco sigue siendo la orden: 18 de 40 se leen
  como afirmación porque el verbo imperativo no está en la lista cerrada.
- **Evidencia:** `evidencias/HUE/PR-HUE-02_E_20261110_01.*`.

### PR-HUE-03 · Exactitud del tipo de frase

- **Resultado:** 151/200 (75,5 %). Contenido 40/40, negación 38/40, afirmación 34/40,
  sí o no 17/40, orden 22/40.
- **Evidencia:** `evidencias/HUE/PR-HUE-03_E_20261110_01.*`.

### PR-CAC-01 · Alternativas de embeddings

- **Cómo se midió:** con el mismo cálculo que hace `SemanticCache` —normalización, sinónimos
  del catálogo, vectores normalizados, distancia 1 − coseno y partición por tipo de frase—
  pero **por oración**, que es la unidad real de la caché, no por frase completa.
- **Resultado:** MiniLM (el del sistema) reconoce 22/34 oraciones cambiadas con su mejor
  umbral (0,04) y 0 trampas; mpnet reconoce 18/34 con 0,02 y 0 trampas; `multilingual-e5-small`
  no se pudo cargar (el repositorio responde 401 en esta máquina).
- **Interpretación:** con la unidad correcta, mpnet **no** supera a MiniLM (al revés que en la
  medición por frase completa del 6/11). No hay razón para cambiar de modelo.
- **Evidencia:** `evidencias/CAC/PR-CAC-01_E_20261110_01.*`.

### PR-CAC-02 · Calibración del umbral

- **Resultado:** la curva completa está en la evidencia. Con el umbral que trae el `.env` del
  10/11 (**0,05**): 23/34 paráfrasis y **1 trampa aceptada**. Con 0,03: 21/34 y 0 trampas.
  A 0,06 ya se aceptan 3 trampas.
- **Hallazgo:** el umbral se subió de 0,03 a 0,05 entre rondas. A 0,05 la caché empieza a
  aceptar trampas; la que se midió es `FR-04-005` («Hoy se siente más o menos» contra «Hoy me
  siento más o menos», distancia 0,031).
- **Evidencia:** `evidencias/CAC/PR-CAC-02_E_20261110_01.*`.

### PR-CAC-03 · Aciertos de la caché

- **Resultado:** 23/34 oraciones cambiadas con umbral 0,05: formato 11/12, orden 9/10,
  sinónimos 3/12. Un falso acierto.
- **Interpretación:** el reconocimiento de formato y de orden funciona; los sinónimos siguen
  siendo el hueco (3/12), igual que el 1/10 del 6/11. La tabla `catalog_synonym` tiene 13
  filas y no cubre las variantes de FR-03.
- **Evidencia:** `evidencias/CAC/PR-CAC-03_E_20261110_01.*`.

### PR-LLM-01 · Alternativas de LLM

- **Resultado:** gpt-4o-mini 20/30 exactas; gpt-4o 24/30; `qwen3:4b-instruct` 20/30.
- **El modelo local se midió desde el host**, no desde el contenedor: Ollama escucha en
  `127.0.0.1:11434` y el cortafuegos filtra el puente de Docker, así que desde el contenedor
  todas las llamadas agotan el tiempo. Se usó el mismo prompt, temperatura 0 y la misma
  reparación contra el catálogo (`evidencias/LLM/PR-LLM-01-qwen_E_20261110_01.json`).
- **Método:** se compara la lista de glosas reparada contra la referencia de FR-01, exigiendo
  igualdad exacta. El resultado coincide con el del 6/11 (20/30 y 25/30), así que las dos
  rondas son comparables con este criterio.
- **Evidencia:** `evidencias/LLM/PR-LLM-01_E_20261110_01.*`.

### PR-LLM-02 · Formato de salida

- **Resultado:** parte A, 0/30 respuestas reales con problemas de formato. Parte B, 9/10
  fabricadas.
- **Sigue fallando CO-04-008:** `TIO, TRABAJAR. Espero que te sirva.` se lee como
  `TIO, TRABAJAR. ESPERO QUE TE SIRVA`: el parser corta por comas y no reconoce la frase de
  cortesía que viene después del punto. La ficha pide que ninguna respuesta se lea mal, así
  que la prueba **falla**.
- **Evidencia:** `evidencias/LLM/PR-LLM-02_E_20261110_01.*`.

### PR-LLM-03 · Consistencia

- **Resultado:** 28/30 frases devuelven las mismas glosas en las cinco llamadas (93,3 %).
- **Evidencia:** `evidencias/LLM/PR-LLM-03_E_20261110_01.*`.

### PR-AVT-06 · Peso de las animaciones

- **Resultado:** Juan 2.823.936 B de animación sobre un GLB de 30.323.680 B (9,31 %), 200 clips
  de 50 señas; Pauline 4.491.912 B sobre 27.850.104 B (16,13 %). APK `debug` 40.297.811 B.
- **Interpretación:** los GLB no cambiaron desde el 6/11; el número es idéntico, como se
  esperaba. El APK creció 1.864 B.
- **Evidencia:** `evidencias/AVT/PR-AVT-06_E_20261110_01.*`.

### PR-REN-08 · Datos móviles

- **Resultado:** por texto, 64 B suben y 498 B bajan por frase (media de las 10 de FR-01-A);
  por voz, 132.638 B suben (audio PCM16 de 16 kHz) y 575 B bajan.
- **Método:** se midió en el socket, con el mismo cliente que usa el plan, contando los bytes
  de cada frame. El teléfono entra por `adb reverse`, así que el tráfico es de loopback y no
  de red móvil: el número es el que consume el protocolo, no el que factura el operador.
- **Evidencia:** `evidencias/REN/PR-REN-08_E_20261110_01.json`.

### PR-REN-09 · Tasa de procesamiento

- **Resultado:** 68 frases en 303,9 s = **13,4 frases por minuto**, 0 errores.
- **Método:** los 10 audios de FR-01-A en orden y en tiempo real, con transcripción, envío de
  la frase y espera del `done`, durante 5 minutos.
- **Evidencia:** `evidencias/REN/PR-REN-09_E_20261110_01.json`.

### PR-REN-04 · Arranque en frío

- **Qué se midió:** el sistema apagado (backend y STT) durante 5 min 40 s; se enciende y se
  mide cuánto tarda en estar listo; después se mandan tres frases de FR-01-C seguidas.
- **Resultado:** el **STT** está listo en **6.037 ms** y el **backend** en **18.516 ms** (carga
  el catálogo, los sinónimos, la caché de 107 filas, el modelo de embeddings, spaCy y hace el
  warm-up del LLM). Con la caché vacía, la primera traducción tarda **1.186 ms** y las dos
  siguientes **917** y **1.129 ms**.
- **No se pudo medir la base dormida:** el cómputo de Neon siguió activo (`started_at`
  17:50:24Z) después de los 5 min 40 s sin uso, así que la primera traducción no pagó el
  despertar de la base. Con la caché cargada las tres frases salen en menos de 4 ms, que es el
  efecto de la caché y no el de la base.
- **Evidencia:** `evidencias/REN/PR-REN-04_E_20261110_01.json` y
  `evidencias/REN/PR-REN-04_health_E_20261110_01.json`.

### PR-TRA-01 · Separación de componentes

- **Resultado:** 6 aristas entre los cuatro componentes; 4 pasan por interfaz
  (`OrchestratorService`, `SttServiceClient`, `NLPService`, `ValidatorService`) y **2 se saltan
  la interfaz**: `normalize_gloss`, importado desde `validation` por `llm_client.py` y por
  `gloss_lexicon.py`.
- **Interpretación:** es la misma causa del 6/11, sin cambios. La decisión sigue siendo del
  owner: si `normalize_gloss` es una función pura compartida (kernel) la prueba pasa con ese
  criterio; si cuenta como salto, hay que moverla a un módulo neutral.
- **Evidencia:** `evidencias/TRA/PR-TRA-01_E_20261110_01.*`.

### PR-TRA-03 · Levantar el entorno desde cero

- **Resultado:** **Pasa.** Clon limpio del repositorio, `venv` nuevo, `pip install -r
  requirements.txt` y `uvicorn app.main:app` en el puerto 8090: `/health` responde `ok` con el
  catálogo cargado (63 entradas contando sinónimos) y la caché leída.
- **Nota de ambiente:** la instalación completa exige torch con CUDA, que no cupo en el disco
  libre de esta máquina; se instaló primero torch de CPU con el índice oficial de PyTorch y
  después el `requirements.txt`. El procedimiento del repositorio no cambió.
- **Evidencia:** `evidencias/TRA/PR-TRA-03_E_20261110_01.*`.

---

## 4. Observaciones

1. **El 78,0 % del 6/11 estaba subestimado por el arnés**, no por el sistema: las muletillas
   se comparaban con sus corchetes. El número comparable es 86,0 %.
2. **El umbral de la caché cambió de 0,03 a 0,05** y con eso aparece el primer falso acierto
   medido (`FR-04-005`, distancia 0,031). La prueba PR-CAC-04 («Falsos aciertos») está marcada
   como pasada el 6/11 con 0/30; con el umbral actual **no se puede afirmar que siga pasando**
   sin repetirla. No se repitió porque no estaba en las 17 pendientes.
3. **La caché se mide por oración, no por frase.** Con la unidad correcta, mpnet deja de ser
   mejor que MiniLM y los sinónimos siguen en 3/12.
4. **PR-LLM-02 sigue fallando en el mismo borde del parser.** El arreglo es del backend.
5. **PR-TRA-01 sigue fallando por la misma causa** y espera una decisión del owner.
6. **El LLM como clasificador de tipo de frase sube 10,5 puntos** (86,0 % contra 75,5 %) y
   cuesta una llamada por oración.
7. **El arnés de esta ronda queda versionado** en `docs/plan-pruebas/arnes/`, para que la
   próxima sesión no tenga que reconstruirlo desde cero.

---

## 5. Lo que no se pudo medir

| Qué | Por qué | Efecto |
|---|---|---|
| `multilingual-e5-small` (PR-CAC-01) | El repositorio de Hugging Face responde 401 en esta máquina | La comparación queda con 2 de 3 modelos |
| Las 6 pruebas de personas (PR-PER-*) | Dependen de convocar intérpretes (supuesto S-05) | No son del carril P1 |

---

## 6. Qué queda pendiente y qué decisiones son del owner

- **PR-LLM-02:** el parser del backend tiene que reconocer una frase de cortesía después del
  punto. Es un arreglo del backend, no del carril.
- **PR-TRA-01:** decidir si `normalize_gloss` cuenta como salto de interfaz o como kernel
  compartido. Si cuenta, hay que moverlo a un módulo neutral.
- **PR-CAC-04:** el umbral pasó a 0,05 y ya hay un falso acierto medido. Hay que decidir si se
  repite esa prueba o si se vuelve a 0,03.
- **PR-VAL-02:** `/health` sigue informando 63 glosas porque cuenta los 13 sinónimos.

---

## 7. Dónde está cada cosa

| Qué | Dónde |
|---|---|
| Sello del ambiente | `evidencias/PREFLIGHT/SELLO-P1_E_20261110_01.json` |
| Evidencia de cada prueba | `evidencias/<BLOQUE>/PR-*_E_20261110_*` |
| Arnés de la ronda | `docs/plan-pruebas/arnes/` |
| Bitácora de sesión | `openspec/sessions/2026-11-10-ronda-p1-17-pendientes.md` |
| Resultados en el formato que lee el libro | `docs/plan-pruebas/resultados-p1-2026-11-10/PR-*.json` |
| Libro de resultados | `resultados-consolidados-p1.xlsx`, en dos rondas: diagnóstica (06/11, 74 filas) y evaluación (10/11, 18 filas, solo del carril 1). El libro general, con las filas de todos los carriles, es `resultados-consolidados.xlsx` y no se toca |

## 8. Cómo se reprodujo

```bash
# datos del Anexo C
python3 docs/plan-pruebas/arnes/extraer_anexo.py --salida /tmp/p1/datos

# pruebas que corren dentro del contenedor del backend
docker cp /tmp/p1/datos sign-ai-backend-backend-1:/datos
docker exec -i sign-ai-backend-backend-1 env DATOS=/datos EVIDENCIA=/evidencia \
    python - < docs/plan-pruebas/arnes/seg01.py     # y hue.py, cac.py, llm.py

# pruebas locales
python3 docs/plan-pruebas/arnes/tra01.py --backend ../signall-backend --salida /tmp/p1/evidencias/TRA
python3 docs/plan-pruebas/arnes/avt06.py --modelos app/src/main/assets/web/models --salida /tmp/p1/evidencias/AVT

# pruebas por el socket
python3 docs/plan-pruebas/arnes/ws.py ren08 --audios /tmp/p1/wav --textos /tmp/p1/datos/FR-01.json --salida ...
python3 docs/plan-pruebas/arnes/ws.py ren09 --minutos 5 --audios /tmp/p1/wav --textos /tmp/p1/datos/FR-01.json --salida ...
```

---

## 9. Segunda parte: los tres arreglos y la repetición

Después de la primera corrida el owner pidió atacar los tres huecos de menor riesgo. Se
arreglaron en el backend y se volvieron a correr las pruebas afectadas. **Los estados de §1 y
§3 son los de la primera corrida**; los de esta parte están en la tabla de abajo y son los que
quedaron en el libro. La §10 sigue con la tercera parte: los umbrales que faltaban y TRA-01.

### Qué se cambió

| Dónde | Cambio |
|---|---|
| `../signall-backend/app/nlp/llm_client.py` | `_parse_glosses` vuelve a partir cada trozo por fin de oración, además del separador que ya usaba. La frase de cortesía que sigue al punto deja de pegarse a la última glosa. |
| `../signall-backend/app/nlp/gloss_analyzer.py` | La lista cerrada de verbos imperativos suma siete (`deme`, `anote`, `adjunte`, `acompañeme`, `vuelva`, `explique`, `actualice`); se saltan los marcadores de arranque (`entonces`, `ahora`, `luego`, …) y, si la oración abre con una cláusula y una coma, se mira la primera palabra de la parte que sigue a la coma. |
| `../signall-backend/app/persistence/migrations/007_catalog_synonyms_fr03.sql` | 12 sinónimos nuevos en `catalog_synonym` (de 13 a 25 filas), idempotentes. |
| `../signall-backend/.env` | `SEMANTIC_CACHE_SIMILARITY_THRESHOLD` de 0,05 a **0,04**. |
| `arnes/cac.py` | Ahora también escribe la evidencia de PR-CAC-04 (falsos aciertos), que antes no se generaba. |

### Resultados

| ID | Antes (10/11, mañana) | Ahora | Estado |
|---|---|---|---|
| PR-HUE-03 | 151/200 (75,5 %); orden 22/40 | **168/200 (84,0 %); orden 39/40** | Se reporta |
| PR-HUE-01 | reglas 75,5 %; LLM 86,0 % | reglas **84,0 %**; spaCy 45,0 %; LLM 85,0 % | Se reporta |
| PR-HUE-02 | completa 75,5 %; sin interrogativa 47,0 %; sin negación 58,5 % | completa **84,0 %**; sin interrogativa 55,5 %; sin negación 67,0 %; sin imperativa 65,5 % | Se reporta |
| PR-CAC-03 | 23/34; sinónimos 3/12 | **32/34**; formato 11/12, orden 9/10, sinónimos **12/12**; 0 falsos | Se reporta |
| PR-CAC-04 | no se había corrido con el umbral 0,05 | **0/30 trampas** con 0,04 | **Pasa** |
| PR-CAC-02 | 0,05: 23/34 y 1 trampa | **0,04: 32/34 y 0 trampas**; 0,03: 31/34 y 0; 0,05: 33/34 y 2 | Se reporta |
| PR-CAC-01 | MiniLM 22/34; mpnet 18/34 | **MiniLM 32/34** (0,04); mpnet 28/34 (0,02) | Se reporta |
| PR-LLM-02 | 0/30 reales con problemas; 9/10 fabricadas; fallaba CO-04-008 | **0/30 reales con problemas; 10/10 fabricadas**; CO-04-008 devuelve TIO, TRABAJAR | **Pasa** |
| PR-LLM-01 | mini 20/30; 4o 24/30; qwen 20/30 | mini 20/30; 4o 23/30; qwen no medido (Ollama no alcanzable desde el contenedor) | Se reporta |
| PR-LLM-03 | 28/30 | 27/30 | Se reporta |

En el libro, la ronda de evaluación queda con **29 filas: 14 Pasa, 1 Falla y 14 Se reporta**. La
única falla es PR-TRA-01, que sigue esperando la decisión del owner sobre `normalize_gloss`.

### Cómo se midió, y qué hay que saber

- **Evidencia:** `evidencias/{HUE,CAC,LLM}/PR-*_E_20261110_02.*` (la `_01` es la corrida de la
  mañana, antes de los arreglos).
- **La imagen del backend no se pudo reconstruir** (`docker compose build` falla por falta de
  espacio en disco). Para medir se copiaron los dos archivos al contenedor en marcha y se
  reinició; el cambio de verdad está en el repositorio. La próxima vez que se reconstruya la
  imagen, el código va solo.
- **La lista de verbos se amplió con las órdenes del mismo corpus con el que se mide** (CO-02,
  registro de datos básicos). La ganancia es real en el dominio, pero está medida sobre el
  conjunto que se usó para elegir los verbos: no es una ganancia ciega. La lista sigue cerrada y
  acotada al dominio, como pide ADR-002.
- **La única orden que se pierde** es la variante sin puntuación de «Antes de continuar, firme el
  consentimiento»: sin la coma no hay señal de que la orden empiece después. Es el borde que la
  heurística no puede resolver sin un parser.
- **`qwen3:4b-instruct` no se midió desde el contenedor** (Ollama escucha en `127.0.0.1:11434` y
  el cortafuegos filtra el puente de Docker). La cifra de 20/30 es la de la mañana, medida desde
  el host con el mismo prompt.

---

## 10. Tercera parte: los umbrales que faltaban y TRA-01

Un compañero del equipo revisó los resultados de este carril y señaló dos cosas: cinco pruebas
que quedaron en «Se reporta» solo porque no tenían un mínimo con el cual juzgarlas, y una que
fallaba. Las dos son solucionables, y el plan de pruebas ya lo dice: la ronda de evaluación
«finaliza cuando se han aplicado las correcciones acordadas, se han ejecutado todas las
pruebas y **se cuenta con umbrales definidos**».

### 10.1 Los cinco umbrales

| Prueba | Tipo | Lo que decía la ficha | Lo que dice ahora | Medido el 10/11 |
|---|---|---|---|---|
| PR-SEG-01 | Verificación | «Umbral por fijar después de la ronda diagnóstica» | Al menos el **75 %** de los 200 casos | 86,0 % (172/200) |
| PR-HUE-03 | Verificación | Ídem | Al menos el **70 %** de las 200 oraciones | 84,0 % (168/200) |
| PR-CAC-03 | Verificación | Ídem | Al menos el **50 %** de las oraciones cambiadas y **0** acierto equivocado | 94,1 % (32/34), 0 falsos |
| PR-CAC-02 | Caracterización | «El umbral definitivo se decide después…» | El umbral queda en **0,04** | 32/34 paráfrasis, 0 trampas |
| PR-REN-09 | Caracterización | «El mínimo de frases por minuto se fija después…» | Mínimo de **10 frases por minuto** | 13,4 por minuto, 0 errores |

**De dónde salen los valores: de la ronda diagnóstica del 06/11, no de la del 10/11.** El plan y
los requerimientos lo piden así («fijar el mínimo con los valores de la ronda diagnóstica»), y es
lo que hace defendible el resultado: el criterio existe **antes** de la medición que se juzga
con él. Si el mínimo se sacara del 10/11, la ronda de evaluación se estaría calificando a sí
misma.

| Prueba | Lo que mide la ronda diagnóstica | El mínimo quedó | Margen |
|---|---|---|---|
| PR-SEG-01 | 78,0 % (156/200) | 75 % | 3 puntos |
| PR-HUE-03 | 75,5 % (151/200) | 70 % | 5,5 puntos |
| PR-CAC-03 | 56,7 % (17/30), sin acierto equivocado | 50 % y 0 falsos | 6,7 puntos |
| PR-REN-09 | 13,1 frases por minuto | 10 por minuto | 3,1 |

El 78,0 % del 06/11 estaba además subestimado por el arnés (§4.1), así que el mínimo de PR-SEG-01
queda por debajo de lo que el sistema ya hacía.

**No todas las cinco pueden quedar en «Pasa», y no es un descuido.** El plan define los tipos
de prueba en su Tabla 3.1: *Verificación* «compara el resultado con un requerimiento o criterio y
responde si pasa o falla», y *Caracterización* «mide y reporta, **sin un criterio de
aprobación**; sus resultados sirven para fijar umbrales después». PR-CAC-02 y PR-REN-09 son de
caracterización, así que lo que se hace con ellas es dejar el número escrito —0,04 y 10— y se
quedan en «Se reporta». Las tres de verificación sí se califican, y las tres pasan.

### 10.2 TRA-01: la única falla, destrabada

La falla era la misma del 06/11: de 6 aristas entre los cuatro componentes, 2 se saltaban la
interfaz, y las dos eran la misma importación. `normalize_gloss` —la función que dobla una glosa
a la clave con la que el catálogo y el NLP coinciden— estaba dentro del componente de validación
(`app/validation/catalog_repository.py`), así que el NLP tenía que importarla de ahí.

**Qué se hizo:** la función y sus dos tablas de traducción pasaron a
`app/domain/gloss_text.py`, que es la capa que los dos componentes ya comparten
(`app/nlp/models.py` importa `PhraseType` de `app.domain.models`, y
`app/validation/catalog_repository.py` importa `CatalogEntry` de ahí). La función es pura: sin
estado, sin base de datos, sin asincronía. Importarla no arrastra ninguna implementación, así
que la arista desaparece sin tocar el criterio de la prueba.

**Resultado:** el arnés (que lee solo el AST, sin necesidad del contenedor) da **4 aristas, 4 por
interfaz, 0 saltos**. **PR-TRA-01 pasa.** Evidencia: `evidencias/TRA/PR-TRA-01_E_20261110_02.*`.

El SDD no nombra esta función ni en la narrativa ni en los diagramas, así que no hubo que
tocarlo; el cambio quedó anotado en `docs/sdd-changes.md` del backend.

### 10.3 Los dos libros

El 10/11 el carril 2 dejó sus 25 filas de la ronda de evaluación en
`resultados-consolidados.xlsx` (commit `e89073e`), sobre el libro de dos rondas. Como el Excel
es un archivo binario, dos carriles sobre el mismo archivo no se fusionan en el historial sin
perder trabajo, y la instrucción es no tocar lo del otro carril.

Quedan entonces **dos libros**, y cada uno es de un carril:

| Archivo | Qué trae | De quién es |
|---|---|---|
| `resultados-consolidados.xlsx` | 74 de la ronda diagnóstica y 43 de la de evaluación (18 del carril 1 con los estados de la mañana y 25 del carril 2) | El general. **No se toca**: queda como lo dejó el carril 2 |
| `resultados-consolidados-p1.xlsx` | 74 de la ronda diagnóstica y 18 de la de evaluación, con los umbrales ya fijados (§10.1) y TRA-01 en Pasa (§10.2) | Solo del carril 1 |

El libro del carril 1 queda así:

| Ronda | Pruebas | Pasa | Falla | Se reporta | Bloqueada | Sin ejecutar |
|---|---|---|---|---|---|---|
| Diagnóstica (06/11) | 74 | 17 | 6 | 27 | 0 | 24 |
| De evaluación (10/11) | 18 | 7 | 0 | 11 | 0 | 0 |

La ronda de evaluación del carril 1 pasa de 3 Pasa, 1 Falla y 14 Se reporta a **7 Pasa, 0 Falla
y 11 Se reporta**.

Los dos libros se juntan en uno solo cuando terminen los demás carriles.

### 10.4 Dónde quedó cada cosa

| Repositorio | Qué |
|---|---|
| `sign-ai-docs` | Los criterios de las cinco fichas en el plan de pruebas y los tres valores del SRS (RF-NLP-04, RF-NLP-06 y RNF-DES-09), con los borradores y el registro de cambios alineados. Commit `b23692c`, directo a `main` |
| `sign-ai-backend` | `normalize_gloss` en `app/domain/gloss_text.py`, el umbral 0,04 por defecto y la migración 007. Rama `fix/glosado-hue-cac-llm-p1`, PR #13 contra `develop`, ya fusionado en `develop` y llevado a `main` |
| `sign-ai-android` | El libro del carril 1 (`resultados-consolidados-p1.xlsx`), la evidencia de TRA-01 y este informe |
