# Ronda diagnóstica del plan de pruebas: 74 de 74

**Fecha:** 2026-11-04 (la corrida cruzó la medianoche; el cierre fue el 2026-11-05) ·
**Rama:** `main` · **Repos tocados:** este (solo `docs/plan-pruebas/` y esta bitácora).
Ningún cambio de código en la app, el backend ni el STT, y ningún commit en los hermanos.

## Qué llegó

El owner pidió ejecutar la tarjeta **8b · Correr las pruebas** de `PLAN-SEMANA-13`: las **74**
pruebas del plan en la ronda diagnóstica, con un agente, sobre el celular, el backend y el
STT, y anotar cada resultado en `docs/plan-pruebas/plantilla-resultados.xlsx`. Las fichas ya
estaban copiadas en las once primeras columnas: no se retipearon y no se abrió Overleaf salvo
para lo que ya se sabía (el plan está fijado en 74: 73 fichas más `PR-REN-09`).

## Qué se hizo

1. Se comprobó el terreno: backend `:8080` con 50 glosas y caché cargada, STT `medium` en
   CUDA, Redmi Note 9S (Android 11) con la app instalada, `RECORD_AUDIO` y `adb reverse`.
2. Se clasificaron las 74 **antes de ejecutar**: 59 corrían con un agente y 15 no, con el
   motivo. La clasificación se le mostró al owner y no se reordenó la tabla.
3. Se armó un arnés fuera del repo (`/tmp/opencode/pruebas/`): cliente del WS del backend
   (texto y audio), cliente del STT (streaming real y POST), lectura de las tablas de
   `../signall-docs/borradores/plan-pruebas/02-datos/`, WER, evidencias y resultados en JSON.
4. Se ejecutó en tandas que no compitieran por el mismo recurso: STT, backend y celular en
   paralelo; después caché/ablaciones/rendimiento con el backend quieto; después
   resiliencia/seguridad, que reinician contenedores.
5. Se llenaron las columnas 12 a 17 **desde los JSON**, sin regenerar el libro (regenerarlo
   borra los resultados).
6. Se generó `ANALISIS-2026-11-04.md` para leer los resultados por causa y decidir.

## Resultado

| Bloque | Total | Pasa | Falla | Se reporta | Bloqueada |
|---|---:|---:|---:|---:|---:|
| STT | 5 | 1 | 1 | 3 | 0 |
| SEG | 4 | 0 | 0 | 3 | 1 |
| HUE | 3 | 0 | 0 | 1 | 2 |
| CAC | 6 | 0 | 2 | 3 | 1 |
| LLM | 5 | 1 | 2 | 2 | 0 |
| VAL | 3 | 1 | 2 | 0 | 0 |
| INT | 6 | 2 | 1 | 1 | 2 |
| AND | 6 | 4 | 1 | 1 | 0 |
| AVT | 7 | 3 | 1 | 1 | 2 |
| REN | 9 | 0 | 0 | 9 | 0 |
| RES | 7 | 5 | 1 | 0 | 1 |
| SEC | 4 | 2 | 1 | 0 | 1 |
| TRA | 3 | 2 | 1 | 0 | 0 |
| PER | 6 | 0 | 0 | 0 | 6 |
| **Total** | **74** | **21** | **13** | **24** | **16** |

## Las 13 que fallan

| Prueba | Qué pasó |
|---|---|
| `PR-STT-03` | 2 de 8 audios sin voz producen texto por streaming (tos y carraspeos); por POST se descartan |
| `PR-CAC-04` | 1 falso acierto de negación a distancia 0,042 (umbral 0,05) |
| `PR-CAC-05` | Borrar o corregir una fila de la caché por SQL no tiene efecto hasta reiniciar |
| `PR-LLM-02` | 4 de 10 respuestas fabricadas se leen mal (pierden glosas, parten palabras) |
| `PR-LLM-05` | 10 de 18 devuelven texto fuera del catálogo y 3 obedecen; el texto llega a la pantalla |
| `PR-VAL-01` | 8 de 10 variantes: las dos con punto final no se reconocen |
| `PR-VAL-02` | Una glosa repetida con otro `id_sign` se acepta (no hay único sobre `gloss`) |
| `PR-INT-02` | 99 glosas recibidas de 112 esperadas; 15 de 30 frases no coinciden (es del glosado) |
| `PR-AND-01` | Con el permiso de micrófono negado no hay aviso en pantalla; revocarlo mata el proceso |
| `PR-AVT-03` | Solo la última de 3 frases encoladas se completa: el producto reemplaza la cola (`STA-18`) |
| `PR-TRA-01` | 2 de 5 importaciones entre componentes no pasan por una interfaz |
| `PR-RES-03` | Con el STT caído el backend cierra la sesión y la app entra en bucle; el texto tampoco funciona |
| `PR-SEC-01` | App–proxy cifrado, pero backend–LLM en claro y legible |

## Las 16 bloqueadas

`PR-SEG-03`, `PR-HUE-01`, `PR-HUE-02`, `PR-CAC-01` (no hay alternativas implementadas y
probarlas es tocar código) · `PR-AVT-04` (no hay instrumentación de posiciones entre señas) ·
`PR-AVT-05` (ficha en TODO y juicio visual) · `PR-INT-03` (FR-11 supone 9 señas sin animación
y hoy las 50 tienen clip) · `PR-INT-04` (una persona tiene que hablar al micrófono) ·
`PR-RES-05` (no hay gancho para forzar un error interno) · `PR-SEC-03` (pide base de prueba y
la del sistema es Neon) · `PR-PER-01` a `PR-PER-06` (necesitan intérpretes de LSC o usuarios).

## Decisiones que quedan abiertas (owner)

1. **Umbral de la caché**: 0,03 (0 falsos, 18/30) · 0,05 actual (2 falsos, 21/30) · 0,08
   (7 falsos, 23/30).
2. **Cola del avatar**: cambiar el criterio del plan o el comportamiento del producto
   (`PR-AVT-03` contra `STA-18`).
3. **Criterio de inyección**: `Falla` si el usuario no debe ver texto que no sea glosa;
   `Pasa` si basta con no representar una seña equivocada (`PR-LLM-05`).
4. **`PR-INT-03`**: hacer cirugía temporal del catálogo para provocar «Seña no disponible» o
   dejarla bloqueada.
5. **Reglas del segmentador**: 5 de 7 no cambian nada en este corpus (`PR-SEG-04`).
6. **Mantenimiento de la caché**: documentar «reiniciar después de tocar la tabla» o cambiar
   el diseño (`PR-CAC-05`).
7. **`PR-RES-03`**: decidir si el texto debe seguir funcionando con el STT caído (hoy no).

## Matices de la corrida (no citar un número sin esto)

- **El LLM es local** (`qwen3:4b-instruct`), no el `gpt-4o-mini` oficial: los bloques LLM, CAC
  y las latencias del backend no son comparables con la línea base publicada.
- **La GPU es compartida** con Ollama (2.776 MiB) y el STT (1.988 MiB); por eso `large-v3` no
  entra y cae a CPU **sin avisar** (`/health` sigue diciendo `cuda`).
- **La base es Neon, la de la tesis**: las pruebas de caché la vaciaron y `VAL-03` retiró y
  repuso una fila de `catalog` (quedó en 50). `SEC-03` no se corrió por eso.
- **El backend tenía cambios sin commitear** sobre `c8aa516`; la imagen medida es
  `sha256:33b65e7…` del 2026-11-04 00:05.
- **Dos pruebas se corrieron dos veces** por contaminación (`REN-01` y `REN-05`); se reporta
  la corrida limpia y las cifras coinciden dentro de ~15 ms.
- **`INT-06` se reinició** porque dos colectores escribieron en la misma app a la vez; la
  corrida contaminada no se usa.
- **El owner tocó la pantalla dos veces** por accidente; las muestras en curso se repitieron.
- **Streaming y POST del STT no miden lo mismo** (3,90 % vs 1,77 % de WER): el VAD se come
  texto en todos los umbrales y `AU-01-014` queda vacío siempre.
- **`REN-03` y `REN-08` tienen solo la mitad de texto**: la mitad de voz necesita una persona
  hablando al micrófono del celular.
- **`AVT-06`**: el método literal de la ficha sobrecuenta 5× (las 300 animaciones comparten
  `bufferView`); la medición buena es por rangos de accesores.
- **`TRA-03`**: `.env.example` tal cual no levanta (sin `LLM_API_KEY` el backend muere a los
  18 s) y el comando de arranque no está en el README: 7 pasos manuales, 0 parches no
  versionados.
- **El equipo es el de escritorio**, no el servidor de la tesis.

## Qué quedó en el repo

- `docs/plan-pruebas/plantilla-resultados.xlsx`: las 74 filas con Estado, Resultado, Evidencia,
  Fecha, Ejecutor y Ronda. Las once primeras columnas no se tocaron.
- `docs/plan-pruebas/evidencias/`: **261 archivos** (CSV, JSON, PNG, TXT y un MP4) con la
  convención `PR-<BLOQUE>-<NN>_D_20261104_<nn>.<ext>`.
- `docs/historia/plan-pruebas/ANALISIS-2026-11-04.md`: los resultados por causa, las decisiones y los
  matices, para trabajar en otra sesión.
- `docs/historia/plan-pruebas/PRUEBAS-2026-11-04-1626.md`: la bitácora de la sesión con lo que hay que
  mirar (sin los resultados, que viven en el libro).

## Lo que queda abierto

- **Promover `PR-REN-09` en el plan formal**: el libro tiene 74 filas y el documento dice 73.
- **`RNF-DES-09` no está en el SRS** (el SRS va hasta `RNF-DES-08`).
- **Cerrar el TODO de `PR-REN-06`**: el contador de sesiones existe y funcionó (1→2→3→4).
- **`PR-INT-03`**: si se quiere el aviso de «Seña no disponible» medido, hay que decidir la
  cirugía del catálogo.
- **La mitad de voz** de `REN-03` y `REN-08`, y las seis de personas, siguen necesitando
  personas.
- **Una sesión aparte** tomó los resultados y está escribiendo las conclusiones
  (`CONCLUSIONES-2026-11-04.md`); esta sesión no la tocó.
