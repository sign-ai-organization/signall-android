# Ronda de evaluación de las 74 pruebas

**Día:** jueves 5 de noviembre de 2026. **Sesión:** fase 3 del plan
`docs/plan-pruebas/PLAN-2026-11-05-recuperacion-y-ronda-oficial.md`.

- **Objetivo:** correr las 74 pruebas sobre el sistema arreglado, con `gpt-4o-mini`, y dejar
  el libro de evaluación con las 74 filas de la ronda diagnóstica y las 74 de esta.
- **Antes:** la fase 1 (24 pruebas recuperadas) y la fase 2 (S1–S10) cerradas el mismo día;
  los arreglos verificados y desplegados, no solo escritos.
- **Cómo:** cinco subagentes por carril, cada uno dueño de un recurso (teléfono, socket de
  texto, STT/GPU, reinicios/TLS/Neon, estático), y la sesión maestra para el cierre.

## Lo que se hizo

### Carril E · estático (3 + bloqueadas)

`PR-TRA-01` Falla (los dos imports que se saltan la interfaz), `PR-TRA-02` Pasa (ruff 0;
mypy 7 errores conocidos) y `PR-AVT-06` Se reporta (medido por accesores: 2,82 MB de animación
sobre 30,3 MB). Las 15 bloqueadas quedaron con su motivo; `INT-03` salió de la lista.

### Carril B · backend de texto (15)

Un Falla (`LLM-02` 9/10) y tres Se reporta; el resto Pasa. Cerró los arreglos S1 y S3:
`VAL-01` **10/10** variantes (antes 8/10) y `LLM-05` **Pasa**. Números centrales: `LLM-01`
mini 17/30 exactas, gpt-4o 20/30, local 17/30; `SEG-01` 79,0 %; `HUE-03` 65,5 %; `CAC-04`
0/30 falsos aciertos; `CAC-03` 17/30.

### Carril C · audio y STT (7)

`STT-01` WER 1,77 %, `STT-02` Pasa, `STT-03` **Falla** (0/8 finales con texto, 1 parcial
«¡Grr!»), `STT-05` cuatro umbrales empatados, `REN-01` primer parcial 1 845,9 ms, `RES-06`
20/20 con WER 0. `REN-06` quedó fuera de la lista del carril y se cerró al final desde la
sesión maestra: p50 242,5 / 254,3 / 329,8 ms con 1, 2 y 3 sesiones, 0 textos cruzados.

### Carril A · dispositivo (15)

9 Pasa, 4 Se reporta, 2 Falla. Cerró S9 (`AND-01` Pasa, aviso visible). `AVT-01` abre en
4,60–4,83 s, `AVT-02` 50/50 animaciones, `INT-06` 60 frases en 30 min con PSS 375–384 MB.
Las dos fallas: `INT-02` (17/30 exactas, 88,4 % de glosas; el avatar representa todo lo que
recibe) y `AVT-03` (la cola aguanta una frase pendiente, la tercera corta las anteriores sin
dejar rastro). Teléfono restaurado byte a byte.

### Carril D · estado compartido (19)

9 Pasa, 8 Se reporta, 2 Falla. `INT-03` pasó de Bloqueada a **Pasa** con la cirugía del
catálogo (50 → 41 → 50, respaldo idéntico). `TRA-03` pasó de Falla a **Pasa**: el clon arranca
y `/health` da 50 glosas a los 449,5 s. `SEC-01` midió TLS extremo a extremo. Las dos fallas:
`CAC-05` (el mantenimiento a mano exige reiniciar, contra lo que pide la ficha) y `RES-03`
(S5 mantiene la sesión y el texto, pero el micrófono no vuelve solo).

### Cierre

- `PR-REN-06` re-medido (faltaba) y `PR-VAL-03` completado con la app: ciclo 50 → 49 → 50 con
  la frase FR-01-007 y capturas de los tres pasos. Queda en **Pasa**.
- `resultados-consolidados.xlsx` generado con **148 filas** y `Resumen` verificado.
- `ANALISIS-2026-11-05.md`, `CONCLUSIONES-2026-11-05.md` y `PRUEBAS-2026-11-05.md` escritos;
  `ARREGLOS-2026-11-05.md` ganó §6 con el número nuevo de cada arreglo.

## Decisiones de la sesión

| Tema | Quedó |
|---|---|
| `REN-06` fuera del carril C | Se cerró desde la sesión maestra con el mismo colector, al final y sin solapar |
| `VAL-03` sin teléfono en el carril D | Se completó la observación en el dispositivo, en vez de dejarla como «Se reporta» |
| Evidencias descartadas | `SEC-01` (`_01`, `_02`) y `SEC-04` (`_01`, `_02`) llevan `_descartada` y no se citan |
| `PR-CAC-03` baja de 19/30 a 17/30 | Es el costo de D5 (0,03); se reporta, no se «arregla» |
| `PR-LLM-02` queda en 9/10 | El borde es del parser (`CO-04-008`), no del modelo; se documenta como arreglo incompleto |
| Bloqueadas | Siguen 15; ninguna se marca «sin ejecutar» |

## Trampas que costaron tiempo

- **El STT fuera de la red del backend** tras un `docker compose up -d`: las sesiones nacen
  con `capability{audio:false}` aunque `/health` diga ok. Se corrige con
  `docker network connect --alias stt ...` y se comprueba con una frase de humo.
- **`adb logcat` vuelca el buffer entero** al reiniciarse: sin deduplicar por (timestamp,
  mensaje), el emparejamiento de frases miente.
- **`websockets` no está en `~/.venv`**: los colectores de audio corren con `python3` del
  sistema y el venv es para openpyxl. `REN-06` falló una vez por eso.
- **`docker logs` escribe por `stderr`** y **`docker stats` por nombre fijo** no ve los
  contenedores temporales.
- **Neon dormida** deja el backend `degraded`; el arnés reintenta y exige `catalog_loaded`.
- **Tres análisis del arnés daban veredicto falso** y se corrigieron en el carril D:
  `SEC-01` leía un pcap viejo y no entendía IPv6; `SEC-04` contaba las columnas `bytea` de
  `pg_opclass`; `INT-03` leía el logcat de otra prueba.

## Lo que queda

- **Decide el owner:** la exactitud del glosado (17/30), la contradicción `CAC-05` ↔
  `RNF-MNT-05`, la ventana de S11/S12, el tope de la cola del avatar, la recuperación del
  micrófono y cuáles de las 15 bloqueadas se desbloquean.
- **Documentos:** las divergencias encontradas (pgvector vs NumPy, «Overleaf» en el SRS,
  `.env`/`local.properties` no versionados, tablas de audios de AU-02/03/04) se reportan en
  `CONCLUSIONES-2026-11-05.md` §5; no se tocaron a mitad de la ronda.
- **Hermanos:** sin cambios de código en esta ronda; no hubo cambio de contrato ni commit.
