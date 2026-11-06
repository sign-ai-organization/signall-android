# Ronda de evaluación · carril P1 (texto y estático) · 2026-11-06

Resultados de las **23 pruebas del carril P1** sobre el sistema de hoy. El libro
`resultados-consolidados.xlsx` quedó con **148 filas**: la ronda diagnóstica (la del 5/11) y la
ronda de evaluación, con las 23 de P1 llenas y las otras 51 marcadas «Sin ejecutar» o
«Bloqueada» hasta que las corran P2 y P3.

- **Ejecutor:** agente IA (P1), sesión del 2026-11-06.
- **Evidencia:** `evidencias/<BLOQUE>/PR-*_E_20261106_*`, 50 archivos.
- **Sello:** `evidencias/PREFLIGHT/SELLO-P1_E_20261106_01.json`.

## Sello del ambiente

| Dato | Valor |
|---|---|
| Servidor | canónico (única máquina disponible) |
| Backend | `9c74e76` · STT `9787e49` · app `ddcb791` |
| APK | `debug`, sha256 `52958cfc…` (40.295.947 B), reinstalado al cierre |
| LLM | `gpt-4o-mini`, `llm_base_url=""`, temperatura 0 |
| Caché | umbral 0,03; **0 filas al empezar** |
| Catálogo | 50 filas + 13 sinónimos; `/health` informa 63 |
| STT | `medium`, `cuda`, silencio 1.500 ms |
| Base | Neon `ep-shy-meadow-axe2zkz4-pooler` (org `sign-ai`) |
| Teléfono | Redmi Note 9S, Android 11, `1ed6699f` |
| FR-01 | sha256 de los 30 textos `8d0f260a…` |
| Audios | `manifiesto.csv` sha256 `3934ab63…` |

## Las 23 pruebas

| ID | Prueba | Estado | Resultado en una línea |
|---|---|---|---|
| PR-SEG-01 | Exactitud del segmentador | Se reporta | 156/200 (78,0 %); 7/9 límite; 20/29 borde; AU-05 7/10 |
| PR-SEG-03 | Alternativas de segmentación | Se reporta | El híbrido del sistema gana: 78,0 % vs 67,5 / 62,8 / 59,2 |
| PR-HUE-01 | Alternativas de tipo de frase | Se reporta | Reglas 75,5 %; spaCy md 59,5 %; gpt-4o-mini 88,0 % |
| PR-HUE-02 | Reglas de la huella | Se reporta | Completa 75,5 %; sin interrogativa 57,0 %; sin negación 58,5 % |
| PR-HUE-03 | Exactitud del tipo de frase | Se reporta | 151/200 (75,5 %); contenido 40/40, orden 22/40 |
| PR-CAC-01 | Alternativas de embeddings | Se reporta | MiniLM 19/30; mpnet 23/30; e5-small no disponible (HF 401) |
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

**Resumen:** 8 Pasa · 2 Falla · 13 Se reporta · 0 Bloqueada en el carril.

## Hallazgos que cambian decisiones

1. **PR-VAL-02 queda en Pasa pero el contador engaña.** El catálogo tiene 50 filas y la base ya
   rechaza la glosa repetida (`uq_catalog_gloss`, migración 005), que era la falla de la ronda
   anterior. Pero `/health` publica `catalog_glosses: 63` porque `CatalogRepository` mete los 13
   sinónimos en el mismo diccionario. El número que lee un operario dice 63 glosas donde hay 50.
2. **PR-CAC-03 sigue en 17/30 y los sinónimos apenas aportan 1/10.** La tabla
   `catalog_synonym` tiene 13 filas y no cubre las variantes de FR-03 (`regular`, `prudente`,
   `señor`, `jamás`, `disculpe`, `excelente`, `un poquito`, `chicos`, `bastante`). El
   reconocimiento de formato (9/10) y de orden (7/10) sí funciona.
3. **mpnet reconoce 4 paráfrasis más que MiniLM** (23/30 contra 19/30, con 0 trampas) a 2,5
   veces el tiempo de embedding y 1,5 veces la memoria. Es un cambio de diseño posible, no una
   urgencia. `e5-small` no se pudo medir: los pesos no están disponibles en esta máquina
   (Hugging Face responde 401).
4. **El LLM como clasificador de tipo de frase sube 12,5 puntos** (88,0 % contra 75,5 % de las
   reglas), pero cuesta una llamada por oración. Se reporta; no se adopta.
5. **PR-LLM-02 sigue fallando en el mismo borde del parser**: `TIO, TRABAJAR. Espero que te
   sirva.` se lee como solo `TIO`. La oración de cortesía después del punto se come `TRABAJAR`.
6. **PR-TRA-01 sigue fallando, pero cambió la causa.** La sesión ya no importa el `Orchestrator`
   concreto (usa `OrchestratorService`), y eso cierra el salto anterior. Ahora hay dos saltos de
   `normalize_gloss`: `llm_client.py` y el nuevo `gloss_lexicon.py` lo importan desde
   `validation`. Es una función pura, no un servicio; el owner decide si cuenta como salto.
7. **PR-VAL-03 cumple el ciclo pero la glosa retirada no se anuncia.** Al borrar HAMBRE, la app
   representa solo `TU`; no muestra «no disponible» porque `_repair_glosses` descarta la glosa
   antes de la validación. El criterio dice «sale como no disponible»; el efecto visible es que
   deja de aparecer. Queda anotado para que el owner decida si eso basta.

## Lo que no se pudo medir en esta máquina

- **e5-small** (PR-CAC-01): los pesos no están descargados y Hugging Face responde 401.
- **spaCy pequeño** (PR-HUE-01): solo está instalado `es_core_news_md`; el `sm` no se pudo
  descargar. Se midió el mediano, que es el que usa el segmentador.
- **Las 51 pruebas de P2 y P3**: no son de este carril. En el libro quedan «Sin ejecutar» (40) o
  «Bloqueada» (11) con el motivo de la ronda diagnóstica.

## Estado final del sistema

Backend `ok` con 50 filas de catálogo y la caché vacía; STT `ok` con `medium`/`cuda`; app `debug`
reinstalada con el mismo APK (`52958cfc…`) y abierta en «Inactivo». Sin procesos del arnés, sin
`docker` temporales y sin el proxy de Ollama.
