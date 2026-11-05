# Carril D de la ronda de evaluación (estado compartido) — 2026-11-05

**Día:** jueves 5 de noviembre de 2026.
**Rama:** `main` (sin commits: la ronda es de medición, no de código).

## Qué es esta sesión

Subagente del carril D de la **ronda de evaluación** del plan de pruebas: 19 pruebas que
comparten los recursos que nadie más tocó en la ronda (reinicios del backend, catálogo de
Neon, contenedores temporales, modelo del STT, TLS del `demo`). Los carriles A, B, C y E ya
habían terminado; el teléfono, el backend, el STT y Neon quedaron para este carril.

Orden seguido (de menos a más invasivo, como pide el encargo): REN-02, REN-03, SEG-02,
SEG-04, CAC-05, CAC-06, STT-04, REN-04, REN-05, RES-01/02/03/04/07, SEC-02, SEC-04, SEC-01,
INT-03 y TRA-03.

Entorno de partida verificado antes de empezar: backend `ok` en `127.0.0.1:8080` con
`gpt-4o-mini`, `llm_base_url=""`, umbral de caché `0.03` y `catalog_glosses: 50`; STT
`medium` en `cuda`; teléfono `1ed6699f` con `adb reverse tcp:8080 tcp:8080`, `RECORD_AUDIO`
concedido y la app `debug` en «Inactivo».

## Resultados

Cada fila tiene su JSON en `/tmp/opencode/pruebas/resultados-evaluacion/` y sus evidencias
`_E_20261105_*` en `docs/plan-pruebas/evidencias/<BLOQUE>/`. Los colectores y los `save_*`
que arman la tabla viven en `/tmp/opencode/pruebas/recuperacion/` (fuera del repo, como el
resto del arnés de la ronda) y leen el JSON crudo de cada corrida (`_*.json`).

| Prueba | Estado | Números clave |
|---|---|---|
| PR-REN-02 | Se reporta | 10 frases de FR-01-B × 5 por situación. LLM: total p50 730 / p95 990 ms; llm p50 666 / p95 935 ms; embed p50 39 ms. Caché: total p50 0,8 / p95 4,1 ms; cliente p50 4,0 ms. |
| PR-REN-03 | Se reporta | 100 turnos de texto (10 frases × 5 × 2). Caché vacía: primera seña p50 63,5 / p95 896 ms (15 llamadas al LLM); caché llena: 42 / 82,7 ms (0 al LLM). Representación p50 ≈ 7,1 s. |
| PR-SEG-02 | Se reporta | Con segmentador: 21 oraciones en FR-05, 21/21 (100 %) de caché; sin segmentador: 10 unidades, 0/10 de caché. Glosas iguales a la referencia 3/10 en las dos. |
| PR-SEG-04 | Se reporta | 7 reglas apagadas: solo `MIN_FRAGMENT_WORDS=2` y `CUT_BEFORE_OPENING_MARK=false` cambian la división (20-21 oraciones); las glosas quedan iguales al baseline 10/10 en las 7; 5 reglas no cambian nada en este corpus. |
| PR-CAC-05 | Falla | 13 envíos → 10 filas, 0 duplicados, 0 vacías; nueva 640–1351 ms (mediana 805) vs repetida 3,6–4,5 ms; guardado 127–7138 ms después del `done`. Falla el mantenimiento a mano: borrar/corregir con SQL exige reiniciar (el SRS RNF-MNT-05 lo admite). LLM caído: «No se pudo traducir el texto.» en 463 ms, sin guardar. |
| PR-CAC-06 | Se reporta | 60 frases de FR-09: con caché 17/60 (28,3 %) sin LLM (mediana 11 ms) y 43 con LLM (mediana 744 ms); sin caché 0/60 y 60 llamadas (mediana 761 ms). Costo estimado 146 llamadas / 69.802 tok entrada / 974 salida = 0,0111 USD. Glosas iguales 54/60. |
| PR-STT-04 | Se reporta | 60 audios de AU-01: small WER 2,84 % / 112,6 ms por audio / 859 MiB; medium 1,77 % / 249,6 ms / 2.171 MiB; large-v3 0,35 % / 443,8 ms / 4.123 MiB. Los tres cargaron en CUDA (el LLM remoto deja la GPU libre: large-v3, que en la ronda diagnóstica cayó a CPU, ahora sí entra). |
| PR-REN-04 | Se reporta | 3 arranques en frío: backend 13,5–14,2 s, STT 2,9–4,3 s, app usable 6,3–6,9 s. Arranque 1 (LLM): 990 y 877 ms; arranques 2-3 (caché): 4–5 ms. Base sin uso 435 s: backend 14,4 s; la frase ya estaba en caché y no confirma el efecto de la base dormida. |
| PR-REN-05 | Se reporta | RTX 4050 Laptop (6141 MiB), 12 núcleos. Reposo: GPU 0 % / 2011 MiB; backend 0,2 % CPU / 1101 MiB; STT 0,16 % / 738 MiB. 30 frases: backend 9,7 % CPU (pico 21,9 %). 10 audios: GPU 22,4 % media (pico 100 %), STT 67,2 % CPU. Un audio de 4,5 s: GPU 255 ms vs CPU int8 3849 ms (15,1×); STT en CPU 284 % de CPU / 1178 MiB. |
| PR-RES-01 | Pasa | Mientras dicta: «Desconectado» en 798 ms y el micrófono vuelve en 751 ms al volver el túnel. Antes de mandar: corte 787 ms, reconexión 1964 ms, frase 16199 ms. Mientras traduce: corte 760 ms, reconexión 3066 ms, barra vacía. Mientras reproduce: corte 816 ms, reconexión 1907 ms, frase 13502 ms. Los ms de «frase» incluyen ~10 s de tecleo por automatización; no son latencia del sistema. |
| PR-RES-02 | Pasa | LLM caído: frase en caché representada en 4336 ms, frase nueva avisa en 10482 ms. LLM colgado: aviso en 10534 ms. Recuperación sin reiniciar: 7313 ms. |
| PR-RES-03 | Falla | STT caído: aviso «Sin servicio de voz: podés seguir traduciendo por texto.» y el texto sigue (13951 ms). Al volver el STT (5468 ms) el micrófono no se recupera: los toques no hacen nada y el aviso queda; al relanzar la app sí pasa a «Grabando». |
| PR-RES-04 | Pasa | Base caída: backend `degraded` en 14,6 s con 0 glosas (ese tiempo incluye bajar el modelo de embeddings, que en el contenedor temporal no está en caché); 3/3 glosas con `available=false` en 732,9 ms; la app avisa «glosas sin animación» y «Esa frase no produjo ninguna seña». Al volver, reconecta en 1567 ms y la frase se representa en 13432 ms, sin reiniciar la app. |
| PR-RES-07 | Pasa | En reposo: /health con 50 glosas en 31,5 ms, reconexión inmediata (0 ms en el log) y frase nueva en 14252 ms. En medio de una traducción: reconexión 20011 ms y frase nueva 13547 ms, sin reiniciar la app. Los ms de «frase» incluyen ~10 s de tecleo por automatización. |
| PR-SEC-01 | Pasa | App→Caddy: 1033 paquetes en 443 por loopback y 0 apariciones de las palabras (TLS). Backend→LLM: 100 paquetes en 443 desde la red de Docker y 0 apariciones. Caddy→backend: 528 paquetes en 8080 por loopback y las palabras se leen (2 de «hermano», 2 de «aburrido»). Contra texto plano la app rechaza con «Unable to parse TLS packet header». |
| PR-SEC-02 | Pasa | 13/13 mensajes inválidos sin caer el servicio; /health `ok` con 50 glosas; la sesión abierta siguió traduciendo y la frase válida respondió 13/13. Cierres: 12 sin código y 1 con 1009 (el de 20 MB). |
| PR-SEC-04 | Pasa | 0 archivos nuevos con audio en backend y STT, 0 columnas bytea o de audio en las tablas de la app (las 2 bytea son de `pg_opclass`), 0 líneas de log con audio en el backend. Caché de 0 a 14 filas, todas de texto; el texto de 10/10 frases quedó guardado (7/10 en una fila y el resto por oración). |
| PR-INT-03 | Pasa | Catálogo a 41 glosas y backend reiniciado: las 6 frases de FR-11 devolvieron 17 glosas, 9 con seña y 8 sin seña; de las 9 que el plan quita, 7 aparecieron y ninguna volvió como disponible. Sin reserva, 6/6 mostraron el resto de su frase y hubo 5 avisos «Seña no disponible»; las 2 frases sin ninguna glosa disponible no reprodujeron nada y avisaron «Esa frase no produjo ninguna seña». Con reserva, 6/6 con la seña genérica. Catálogo restaurado a 50 y frase de humo en 7,2 ms. |
| PR-TRA-03 | Pasa | Clon limpio de los 3 repos: backend 2,6 s, STT 1,8 s, app 29,8 s (3/3 rc=0). `docker compose -p tra03 up -d --build` ok en 379,5 s; `/health` del clon ok con 50 glosas en 449,5 s; `./gradlew assembleDebug` del clon ok en 31,7 s. Parches no versionados: `.env` y `local.properties`. Al terminar, el entorno principal volvió a `ok` con 50 glosas. |

Nota de lectura: en las pruebas de resiliencia, los tiempos de «frase nueva» (por ejemplo
16199, 14252 o 13432 ms) incluyen unos 10 s de tecleo por automatización y **no son
latencia del sistema**; los de corte y reconexión sí se leen del logcat y no incluyen
tecleo.

## Divergencias y decisiones que se reportan (no se resuelven acá)

- **`demo_sign_fallback` no es del backend.** El encargo del carril dice que la seña de
  reserva se cambia «en la configuración del backend»; el código
  (`SettingsRepository.KEY_DEMO_FALLBACK`, `Settings.demoSignFallback` en
  `MainScreenState.queueItemsFor`) y el propio plan
  (`borradores/plan-pruebas/01-diseno/integracion/general.md`: «La seña de reserva es un
  ajuste de la aplicación») dicen que es un ajuste de la app. La prueba se corrió cambiando
  la preferencia del teléfono.
- **PR-CAC-05 contra RNF-MNT-05.** La ficha de CAC-05
  (`latex/plan-pruebas/main.tex:1449`) exige que borrar o corregir una fila de la caché a
  mano se refleje «de inmediato» al volver a traducir; el SRS
  (`latex/srs/main.tex:2948-2952`, `RNF-MNT-05`) dice que el cambio se refleja **después de
  reiniciar el servidor** porque la caché vive en memoria. Se reportan las dos cosas; no se
  elige.
- **PR-TRA-03 y los parches locales.** El criterio pide reconstruir sin parches no
  versionados, pero el `.env` (DATABASE_URL y clave del proveedor) y `local.properties`
  (`sdk.dir`) no están versionados. Se copian del repo real y quedan anotados. Además, el
  2026-11-05 los árboles de trabajo de `../sign-ai-backend` y `../sign-ai-stt` tienen
  cambios sin commitear (de los arreglos de la ronda), así que el clon reconstruye el
  **estado publicado** en `origin/main` (`8f27bf8` en los dos), no lo que corre en el
  entorno principal.
- **La caché se busca con pgvector en los documentos, pero corre en memoria.** El SDD
  (`main.tex:393`, `:515`) y el SRS (`main.tex:2771`) dicen que la comparación por
  similitud la hace la extensión pgvector de PostgreSQL; el código
  (`app/nlp/semantic_cache.py`) calcula el producto punto con NumPy sobre matrices en
  memoria y usa la base solo para cargar y persistir. Medido en PR-REN-02:
  `cache.similarity` p50 0,1 ms y `cache.embed` p50 39 ms. Se reporta, no se resuelve.
- **El SRS todavía dice que los documentos se redactan «sobre Overleaf»**
  (`main.tex:2753`), cuando Overleaf se retiró el 2026-11-04 y los documentos viven en
  `../sign-ai-docs/latex/`. Es una frase suelta de las restricciones de diseño.

## Correcciones del arnés en esta sesión (leer antes de reusar una evidencia)

- **PR-SEC-01.** La primera pasada analizó el `sec01.pcap` de `recuperacion/sec01/` —una
  corrida vieja— con un parser que no entendía IPv6 ni el prefijo de interfaz y que daba
  `0` paquetes en los tres tramos, con lo cual el «Pasa» no probaba nada. Se rehizo sobre
  `recuperacion/sec01_E/` separando los tramos por puerto e interfaz: 443 en loopback
  (app→Caddy, 1033 paquetes), 443 desde la red de Docker (backend→LLM, 100 paquetes) y 8080
  en loopback (Caddy→backend, 528 paquetes, donde la frase se lee en claro con un cliente
  sin compresión). Evidencia válida: `PR-SEC-01_E_20261105_03.*`.
- **PR-SEC-04.** El veredicto original contaba las columnas `bytea` de `pg_opclass` (catálogo
  del sistema) como si fueran de la aplicación, y exigía una fila de caché por frase. Se
  corrigió lo primero y lo segundo se comprueba cubriendo la transcripción con las filas
  guardadas (el segmentador parte «Perdón, ¿estás triste?» en dos): 10/10 frases. Evidencia
  válida: `PR-SEC-04_E_20261105_03.*`; las `_01` y `_02` quedan renombradas `_descartada`.
- **PR-INT-03.** `dev.esperar_representado` lee `/tmp/opencode/pruebas/_live.log`, que
  apuntaba al logcat de PR-RES-01; el primer turno quedó sin tiempo y el resto del análisis
  emparejaba los turnos del log con un índice global que se repetía en la segunda fase. Se
  reapuntó el enlace al log de la prueba y el veredicto se calcula con las líneas
  `representado:` / `no produjo ninguna seña` del log, no con la espera del arnés.

## Pendientes

- **Del otro lado, ninguno**: el carril no cambió frames, glosas, columnas ni nombres de
  métrica, así que `../sign-ai-backend` y `../sign-ai-stt` no tienen nada que actualizar.
  Tampoco se commiteó ni se pusheó en ningún repo.
- Las dos fallas piden decisión del owner, no de esta sesión: (1) que la app vuelva a leer
  la capacidad de audio cuando el STT se recupera, y (2) si manda la ficha de PR-CAC-05 o
  `RNF-MNT-05` sobre cuándo se refleja un cambio hecho a mano en la caché.
- Estado final del sistema: backend `ok` con 50 glosas, STT `medium` en `cuda`, app `debug`
  instalada con `adb reverse tcp:8080`, `demo_sign_fallback=false` y sin contenedores
  temporales (el `-p tra03` se baja con `down -v`).
