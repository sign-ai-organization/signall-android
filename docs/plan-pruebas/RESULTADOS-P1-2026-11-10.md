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
| Libro de resultados | `resultados-consolidados.xlsx`, con las 17 filas como ronda «Ronda P1 10/11» (se cargan con `agregar-ronda.py`) |

## 8. Cómo se reprodujo

```bash
# datos del Anexo C
python3 docs/plan-pruebas/arnes/extraer_anexo.py --salida /tmp/p1/datos

# pruebas que corren dentro del contenedor del backend
docker cp /tmp/p1/datos sign-ai-backend-backend-1:/datos
docker exec -i sign-ai-backend-backend-1 env DATOS=/datos EVIDENCIA=/evidencia \
    python - < docs/plan-pruebas/arnes/seg01.py     # y hue.py, cac.py, llm.py

# pruebas locales
python3 docs/plan-pruebas/arnes/tra01.py --backend ../sign-ai-backend --salida /tmp/p1/evidencias/TRA
python3 docs/plan-pruebas/arnes/avt06.py --modelos app/src/main/assets/web/models --salida /tmp/p1/evidencias/AVT

# pruebas por el socket
python3 docs/plan-pruebas/arnes/ws.py ren08 --audios /tmp/p1/wav --textos /tmp/p1/datos/FR-01.json --salida ...
python3 docs/plan-pruebas/arnes/ws.py ren09 --minutos 5 --audios /tmp/p1/wav --textos /tmp/p1/datos/FR-01.json --salida ...
```
