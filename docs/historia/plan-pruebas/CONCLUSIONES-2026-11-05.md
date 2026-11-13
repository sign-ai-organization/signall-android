# Conclusiones de la ronda de evaluación — 2026-11-05

Ronda oficial sobre el sistema arreglado, con `gpt-4o-mini`, las 74 pruebas y evidencia
`_E_20261105_`. Este documento dice **qué significa cada número y qué queda por decidir**;
los resultados crudos están en `resultados-consolidados.xlsx` y el análisis en
`ANALISIS-2026-11-05.md`.

## 0. Cómo se lee

- «Pasa» y «Falla» son juicios contra el criterio de la ficha. «Se reporta» es una prueba de
  caracterización: mide y no juzga. «Bloqueada» no es fallo: es una prueba que no se puede
  ejecutar con lo que hay.
- Los números de esta ronda se midieron en el sistema arreglado; los del diagnóstico, en el
  sistema anterior. Comparar los dos es legítimo **solo** para las pruebas que no cambiaron
  de método.
- Las cifras de latencia de esta ronda son del camino por **oraciones**. La línea base
  publicada de 913 ms p50 era del camino por palabras y **no es comparable**.

## 1. Resumen en una página

La ronda cierra con **27 Pasa, 8 Falla, 24 Se reporta y 15 Bloqueadas** sobre 74 pruebas.

Los arreglos de la fase 2 cerraron **cuatro fallos** (`AND-01`, `CAC-04`, `LLM-05`, `VAL-01`)
y **no rompieron ninguna prueba que ya pasaba**. Un quinto fallo, `TRA-03`, pasó por
infraestructura y no por código; y `INT-03`, que estaba bloqueada, quedó ejecutable con la
cirugía del catálogo y **Pasa**.

Quedan **ocho fallos**, y no son ocho problemas distintos:

| Grupo | Pruebas | Qué es |
|---|---|---|
| **Calidad del glosado** | `INT-02`, `LLM-02` | El sistema entiende la frase pero pierde o cambia palabras al glosarla: 17/30 exactas, 88,4 % de glosas, 9/10 de formato |
| **Arreglos incompletos** | `STT-03`, `AVT-03`, `RES-03` | S4 no cubre parciales; la cola aguanta una frase y no dos; el micrófono no vuelve solo cuando regresa el STT |
| **Deuda declarada** | `VAL-02`, `TRA-01` | Falta el índice único de `gloss` y la interfaz del orquestador |
| **Contradicción de documentos** | `CAC-05` | La ficha pide «de inmediato»; el SRS dice «después de reiniciar» |

La conclusión honesta: **el producto funciona de punta a punta** (app, avatar, seguridad,
resiliencia y sesión larga pasan o se reportan bien) y **el margen de mejora está en el
glosado**, no en el avatar ni en la infraestructura.

## 2. Qué se ejecutó y qué no

### 2.1. Conteo del corte

| Estado | Diagnóstico | Evaluación |
|---|---:|---:|
| Pasa | 21 | 27 |
| Falla | 13 | 8 |
| Se reporta | 24 | 24 |
| Bloqueada | 16 | 15 |

### 2.2. Lo que hay que tener presente antes de citar un número

- **`PR-INT-02` mide dos cosas:** que el avatar represente lo que recibe (29/30 frases, 98/99
  señas, 0 turnos sin fin) y que las glosas coincidan con la referencia (17/30, 88,4 %). El
  avatar cumple; el glosado no.
- **`PR-REN-07` da dos lecturas de fluidez:** 58,5 fps en la ventana de 120 cuadros y 70,5 %
  de cuadros janky en el histórico de `gfxinfo`. No se contradicen: la primera es una ventana
  corta, la segunda acumula toda la sesión del sistema.
- **`PR-REN-08` midió solo la mitad de texto:** la voz no se pudo inyectar al micrófono desde
  el arnés. Se reporta lo medido (204 B por frase) y lo que falta.
- **`PR-STT-04` cambió de resultado según el entorno:** con el LLM remoto, large-v3 entra en la
  GPU de 6 GB; con el modelo local no entraba. El número es válido para el sistema oficial.
- **`PR-CAC-03` bajó de 19/30 a 17/30** por el umbral 0,03: es el precio de tener 0/30 falsos
  aciertos. No es un retroceso del producto, es la decisión D5.
- **`PR-VAL-03`** se completó con la observación en el teléfono después del carril; el
  resultado final incluye las capturas de los tres pasos.

## 3. Los problemas raíz, uno por uno

### S1 · El punto final y el preámbulo hacían perder glosas — **cerrado casi entero**

La normalización ahora quita el punto: `PR-VAL-01` pasó de 8/10 a **10/10** variantes y de
Falla a Pasa. El parser tolerante subió `PR-LLM-02` parte B de 6/10 a **9/10**. Queda un borde
del parser: `CO-04-008` trae «TIO, TRABAJAR. Espero que te sirva.» y el parser devuelve solo
TIO, porque descarta la oración de cortesía junto con TRABAJAR. La respuesta trae las dos
glosas: no es el modelo.

### S2 · La traducción no alcanza la referencia — **abierto**

`PR-INT-02`: **17/30 frases exactas** y **99 de 112 glosas** (88,4 %). En el diagnóstico eran
14/30 y 98/112. Las reglas nuevas del prompt no movieron la exactitud de forma medible
(`PR-LLM-01`: 17/30 con mini, 20/30 con gpt-4o, 17/30 con el local). Es el problema raíz de la
tesis y el que más pesa en el resultado.

### S3 · Texto que no es glosa llegaba a la pantalla — **cerrado**

`PR-LLM-05` pasa: por el socket llegan 58 glosas, 48 con `available=false`, y la app filtra
esas 48. De las 18 frases con instrucciones, 12 no muestran ninguna seña —que es lo correcto—
en vez de mostrar la negativa en inglés o la instrucción glosada.

### S4 · Tos y carraspeo se transcribían — **a medio cerrar**

`PR-STT-03`: los **finales** con texto bajaron de 2/8 a **0/8** y por POST siguen 0/8. Pero un
**parcial** «¡Grr!» se cuela en AU-03-006: la barrera no cubre parciales. La ficha mide texto
producido por el servicio, así que sigue en Falla.

### S5 · Una caída del STT dejaba sin texto y en silencio — **cerrado en lo esencial**

`PR-RES-03`: con el STT caído el backend mantiene la sesión, manda `capability{audio:false}`,
la app avisa «Sin servicio de voz: podés seguir traduciendo por texto.» y la frase escrita se
tradujo y representó en **13 951 ms**. Lo que falla es la vuelta: cuando el STT regresa, el
micrófono no se reactiva solo.

### S6 · La caché devolvía glosas de otra frase — **cerrado**

`PR-CAC-04`: **0/30** falsos aciertos con umbral 0,03 (antes 1/30 con 0,05). `PR-CAC-02`
confirma el intercambio: 18/30 paráfrasis con 0/30 trampas. `PR-CAC-03` baja a 17/30.

### S7 · La caché no reconoce sinónimos — **abierto y sin cambio**

Sigue en **1/10** para sinónimos (`PR-CAC-03`). Es una limitación del embedding elegido, no
un defecto de implementación.

### S8 · Una frase nueva cortaba la que sonaba — **mejorado, no cerrado**

`PR-AVT-03`: la primera frase ya se representa entera (6 542 ms) y la cola aguanta una pendiente.
Con tres envíos seguidos, la tercera descarta la más vieja y el corte es silencioso. El uso
normal no lo dispara (`PR-INT-02`: 29/30 completas).

### S9 · El aviso del permiso no se veía — **cerrado**

`PR-AND-01` pasa: el aviso «Sin permiso de micrófono la app no puede escuchar. Activalo en
Ajustes.» se ve en pantalla y TalkBack lo lee.

### S10 · El STT caía a CPU y `/health` seguía diciendo `cuda` — **cerrado**

`/health` publica `device` y `requested_device`, los dos en `cuda`. `PR-STT-04` midió los tres
tamaños con la GPU libre y `PR-REN-05` la diferencia GPU/CPU (255 ms contra 3 849 ms).

### S11 · Dos importaciones se saltan la interfaz — **abierto (deuda)**

`PR-TRA-01` sigue en Falla: `controller → Orchestrator` (sin ABC) y `llm_client →
normalize_gloss`. No afecta al usuario; exige una ventana de refactor.

### S12 · El catálogo acepta una glosa duplicada — **abierto (deuda)**

`PR-VAL-02` sigue en Falla: `idx_catalog_gloss` no es único. Cambiar el esquema exige
respaldo, migración y prueba de arranque.

## 4. Defectos de las fichas y del conjunto de datos

- **F1 · `PR-CAC-05` contra `RNF-MNT-05`** — confirmado. La ficha pide mantenimiento «de
  inmediato»; el SRS dice «después de reiniciar». Decide el owner.
- **F2 · `PR-INT-03` no se podía preparar** — **resuelto**: la cirugía del catálogo se aprobó
  (D6), se corrió con respaldo (50 → 41 → 50) y la prueba pasa.
- **F3 · `PR-AVT-05` sigue en TODO** y depende de un experto en LSC. Sigue bloqueada (D7).
- **F4 · El método de `PR-AVT-06` sobrecontaba** — en esta ronda se midió por accesores
  alcanzables: la animación de Juan ocupa 2,82 MB de 30,3 MB (9,31 %).
- **F5 · `PR-STT-02` describe un corte forzado** — en esta ronda 5/10 audios cerraron por
  `limite8` y 3/10 no coinciden con la etiqueta literal de la ficha. La ficha sigue
  desalineada con el conjunto de audios.
- **F6 · `PR-STT-03` no tiene requerimiento propio** — sigue igual; la trazabilidad apunta a
  otro requisito.
- **F7 · `PR-VAL-01/02` piden más que el SRS** — `VAL-01` ahora pasa; `VAL-02` sigue pidiendo
  el índice único que el SRS no exige.
- **F8 · La glosa `JUCIOSO` está mal escrita** — no se corrigió: D8 pide confirmar el listado
  original del LSC50 antes de tocarlo.

## 5. Divergencias nuevas que encontró esta ronda

1. **`demo_sign_fallback` es de la app**, no del backend (`SettingsRepository.KEY_DEMO_FALLBACK`).
2. **pgvector en los documentos, NumPy en el código**: el SDD y el SRS describen pgvector; el
   código hace producto punto en memoria.
3. **El SRS todavía dice «sobre Overleaf»**, retirado el 2026-11-04.
4. **`PR-TRA-03` reconstruye `origin/main` (`8f27bf8`)**, no el árbol con los cambios sin
   commitear de los hermanos; `.env` y `local.properties` no están versionados.

Ninguna se corrigió por cuenta propia: van al owner con su cita y su fecha.

## 6. Lo que queda por decidir

1. **La exactitud del glosado**: aceptar 17/30 (57 %) como resultado de la tesis o abrir una
   ventana de mejora del prompt y del glosado. Es la decisión con más impacto.
2. **`PR-CAC-05`**: armonizar la ficha con el SRS (mantenimiento en caliente) o cambiar el
   producto (invalidar la caché al escribir en la base).
3. **S11 y S12**: abrir la ventana de arquitectura y la migración del índice único, con
   respaldo y prueba de arranque.
4. **La cola del avatar**: decidir si el tope es dos o tres frases y hacer visible el descarte.
5. **La recuperación del micrófono** cuando vuelve el STT: es un defecto acotado de S5.
6. **`STT-03`**: extender la barrera `non_speech` a los parciales o aceptar el hueco.
7. **Las 15 bloqueadas**: cuáles se desbloquean con código de laboratorio (`AVT-04`,
   `SEC-03`, `HUE-02`, `RES-05`) y cuáles se declaran «no aplica» (`SEG-03`, `HUE-01`,
   `CAC-01`).
8. **D8 (`JUCIOSO`)** y **D7 (`AVT-05`)**: siguen esperando al owner.
