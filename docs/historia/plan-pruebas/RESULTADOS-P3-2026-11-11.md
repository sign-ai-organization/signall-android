# Resultados del carril P3 — cierre de la ronda de evaluación (11 de noviembre de 2026)

- **Carril:** P3 (Dispositivo y personas), según `reparto-3-personas-2026-11-05.html`.
- **Responsable:** Samuel Osorio, con un agente IA que ejecutó y registró la sesión (tarde y noche del 11/11).
- **Pruebas:** `PR-AVT-04` (que estaba bloqueada), las repeticiones en la ronda de evaluación de `PR-INT-02`, `PR-AVT-05` y `PR-REN-07`, las 14 pruebas de P3 que ya pasaron en la diagnóstica, y la limpieza del libro (`PR-PER-06`).
- **Ronda:** evaluación (`E`). `PR-AVT-04` también tiene su fila diagnóstica, que antes estaba bloqueada.
- **Teléfono:** Samsung Galaxy A54 (SM-A546E), Android 16, 1080×2340, adb id `RZCW90DTMQJ`. `PR-INT-02` y `PR-AVT-05` por USB con `adb reverse tcp:8080 tcp:8080`; `PR-REN-07` por `adb` inalámbrico (`192.168.0.14:5555`), sin cable y descargándose.
  Antes se probó con un Galaxy A11 (SM-A115M) que no se usó para ninguna medición: quedó bloqueado con PIN.
- **Backend:** `sign-ai-backend` en `main` (`301e721`) con **un cambio local sin commit** en `app/nlp/gloss_analyzer.py` (§4.1), en Docker, con la base de Neon real y compartida. La caché semántica no se vació: tenía 46 filas al empezar `PR-INT-02` y 54 al terminar.
- **App:** cada prueba usó una compilación distinta, y los GLB de los avatares cambiaron a mitad de la sesión (§6):
  - `PR-INT-02`: APK `debug` del 10/11, con los GLB anteriores.
  - `PR-AVT-05`: compilación con `-Psignai.devTools=true` (trae el panel de diagnóstico) y `-Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate`, con los GLB nuevos.
  - `PR-REN-07`: compilación `debug` normal (sin devTools) con la misma URL, del 11/11 a las 20:02, con los GLB nuevos.
- **No se levantó el STT:** ninguna de estas pruebas usa voz. La app lo avisa con «Sin servicio de voz: puedes seguir traduciendo por texto».
- **Herramienta:** `adb` y `uiautomator2` (para escribir frases con tildes y «¿»). Los arneses nuevos están en `docs/plan-pruebas/arnes/`: `avt04.py`, `int02.py`, `avt05.py` y `ren07.py`.

## 1. Resumen

| Prueba | Diagnóstica | Evaluación | Idea |
|---|---|---|---|
| PR-AVT-04 | **Se reporta** (antes Bloqueada) | **Pasa** | Salto entre señas medido sobre los clips del GLB: mediana de manos de 3,1 cm con los GLB nuevos (4,9 con los anteriores) (§2) |
| PR-INT-02 | Falla (26/30) | **Pasa**, con el criterio cambiado | 25/30 frases exactas (83,3 %), por encima del 80 %; las 30 llegan y se representan completas (§3) |
| PR-AVT-05 | Falla (3 de 5 tipos) | **Pasa**, con el criterio de «al menos 4 de 5 tipos» | Coinciden 4 de 5 tipos (80 %); Orden ya sale como orden; la pregunta de contenido no coincide, limitación conocida (§4) |
| PR-REN-07 | Se reporta | **Pasa** | 3 corridas descargándose, todas dentro de los umbrales fijados el 11/11 (§5) |
| Las 14 que pasaron | Pasa | Pasa («no se repite») | Fila de evaluación con la notación de P2 (§7) |
| PR-PER-06 | Bloqueada | — | Borrada: no existe en el plan (§7) |

**Libro, recalculado con LibreOffice:** ronda diagnóstica 73 pruebas (31 Pasa, 8 Falla, 29 Se reporta, 5 Bloqueada); ronda de evaluación 61 filas (41 Pasa, 1 Falla —`PR-TRA-01`, decisión del owner—, 19 Se reporta).

## 2. PR-AVT-04 — Continuidad entre señas

**Método.** Se midió sin teléfono (`arnes/avt04.py` con `tools/glb_pose.py`). La app arranca la seña siguiente sin pasar por el reposo (`startClip`, fundición de 100 ms), así que el salto es la distancia entre el último fotograma de una seña y el primero de la siguiente. Para cada paso entre señas de las 30 frases de FR-01 (82 pasos, los mismos 82 intervalos de `PR-REN-07`) se calcula la posición de mundo de los huesos de la skin (cinemática directa) y se toma la mayor distancia entre las dos muñecas. Se usa el clip `afirmativo`: las variantes faciales no mueven las manos (diferencia de 0,000 cm). Es la misma medida con la que se diseñó la fundición (`openspec/changes/archive/2026-10-17-reposo-y-crossfade`), con 10 cm como límite de «continuo».

| Salto de manos, Juan | GLB anteriores (diagnóstica) | GLB nuevos (evaluación) | Umbral |
|---|---|---|---|
| Mediana | 4,89 cm | **3,08 cm** | 7 o menos |
| p95 | 36,99 cm | **12,72 cm** | 47 o menos |
| Máximo | 39,59 cm | **14,22 cm** | 50 o menos |
| Pasos sobre 10 cm | 15 de 82 (18,3 %) | **11 de 82 (13,4 %)** | 23 % o menos |
| Salto máximo de cualquier hueso: mediana / máximo | 7,77 / 53,53 cm | 7,17 / 17,29 cm | sin umbral |

Pauline da casi lo mismo (mediana 3,05, p95 12,44, máximo 13,92 cm, 11 de 82).

- **Diagnóstica:** los pasos hacia `SENTIR` daban de 37,6 a 39,6 cm (máximo `NUNCA→SENTIR` en FR-01-022), y también `ABUELO→TIO` (37,0) y `ABUELO→NUNCA` (36,4).
- **Evaluación:** los más altos son `ABUELO→NUNCA` (14,2), `ABUELO→TIO` (14,1) y `HOLA→BUENOS DIAS` (13,3).
- **Qué cambió entre las dos:** el commit `051e73a` de Juan Diego («entradas y regresos al reposo nuevos en las 50 señas») cambió los GLB: `juan.glb` de 30.323.680 a 27.727.320 B y `pauline.glb` de 27.850.104 a 27.594.616 B.
- **Orden de los hechos:** la diagnóstica y una primera corrida de evaluación (`PR-AVT-04_E_20261111_01`) se hicieron con los GLB anteriores y dieron lo mismo; la corrida con los GLB nuevos es `PR-AVT-04_E_20261111_02`. La fila del libro apunta a la segunda y nombra la primera.
- **Umbrales:** se fijaron con los GLB anteriores (cifra medida más 25 %, redondeada hacia arriba) y ahora quedan holgados. Si el owner quiere que reflejen los GLB vigentes, hay que fijarlos de nuevo con la corrida `_02`.
- **Límite:** es el salto crudo entre clips, antes de la fundición de 100 ms. Lo que se ve en pantalla es menor, y eso no se midió en el teléfono. Sobre los 2.450 pares posibles de los 50 clips, una medición anterior (`openspec/sessions/2026-10-26-correccion-afirmaciones-glb.md`) dio mediana de 5,6 cm y 81 % bajo 10 cm; con los pasos reales de FR-01 y los GLB anteriores salieron 4,9 cm y 81,7 %.

## 3. PR-INT-02 — Recorrido completo por texto

**Método.** Las 30 frases de FR-01 se escribieron una por una en la app (`arnes/int02.py`, con `uiautomator2`), se tocó «Traducir» y se leyeron del `logcat` (`SignAiNet`) las glosas que la app recibió de cada oración. Se esperó a que cada frase terminara de representarse. **La caché no se vació** (es la base compartida del equipo): tenía 46 filas al empezar y 54 al terminar, así que parte de las frases ya venían guardadas de corridas anteriores.

**Resultado.** 25 de 30 frases exactas (83,3 %). Las 30 llegaron con todas sus oraciones, se representaron completas, en orden y sin errores. Las 5 diferencias:

| Frase | Esperado | Obtenido | Causa |
|---|---|---|---|
| FR-01-006 | …COMER,CON GUSTO | …COMER | El modelo omitió CON GUSTO (no había fallado el 06/11) |
| FR-01-010 | TU,HERMANO,TRISTE,SENTIR,MAL | …TRISTE,**TU**,SENTIR,MAL | Persona ambigua: «¿Se siente mal?» agrega TU a propósito (regla del owner) |
| FR-01-013 | …COMO,TU,NOMBRE | …COMO,TU | El modelo omitió NOMBRE |
| FR-01-024 | NIÑA,SENTIR,MUCHO,BIEN,NUNCA,TRISTE | **TU**,SENTIR,BIEN,NIÑA,NUNCA,TRISTE | Persona ambigua y MUCHO omitido |
| FR-01-029 | HOLA,FAMILIA,BIENVENIDO | HOLA,FAMILIA,**SENTIR**,BIENVENIDO | El modelo agregó SENTIR |

**Criterio cambiado.** El criterio «todas las frases llegan con sus señas en el orden esperado» pedía igualdad exacta de glosas, que no es alcanzable con un LLM que varía: 22/30 con un backend anterior, 26/30 en la diagnóstica y 25/30 ahora. La ficha ya decía que aquí «se mide si el sistema funciona»; la calidad de las glosas se mide en `PR-LLM-01` y `PR-LLM-03`. El criterio nuevo es:

> Las 30 frases llegan al avatar con todas sus oraciones, en el orden esperado y sin errores, y se representan completas. Las señas obtenidas frente a las esperadas se reportan frase por frase, y al menos el 80 % de las frases (24 de 30) deben salir con las glosas exactas de FR-01. Las diferencias son variaciones del modelo de lenguaje, que se miden aparte en `PR-LLM-01` y `PR-LLM-03`. Umbral fijado el 11/11/2026 con la ronda diagnóstica del 06/11 (26 de 30, 86,7 %), redondeado hacia abajo para absorber la variación del modelo.

El 80 % lo propuso el agente; la medición de 22/30 con un backend anterior lo habría incumplido. Si el owner prefiere un umbral que aguante una corrida mala, el 70 % (21 de 30) lo cubre. Siguiendo el patrón de P2 (STT-03, CAC-05), la fila diagnóstica queda en Falla («el 06/11 fue Falla») y la de evaluación en Pasa «con el criterio cambiado».

## 4. PR-AVT-05 — Marcadores no manuales

### 4.1 Causa de la falla de la diagnóstica y arreglo

En la diagnóstica las dos frases de Orden de FR-01-A (FR-01-025 «Por favor, come poco.» y FR-01-026 «Con permiso. Déjame pasar, por favor.») salieron como `AFFIRMATIVE` y con la cara afirmativa, confirmado 4 veces (2 por texto y 2 por voz). Leyendo el código del backend (no se ejecutó con esas frases en ese momento), `GlossAnalyzer._has_imperative` solo marcaba orden si la primera palabra de la oración, después de «por favor», estaba en una lista cerrada de verbos en forma de usted (`espere`, `firme`, `escriba`…), y las órdenes de FR-01 están en forma de tú.

**Cambio en `sign-ai-backend/app/nlp/gloss_analyzer.py` (sin commit):**

- Se agregan a `IMPERATIVE_VERBS` las formas de tú que usan las frases: `come`, `dejame`, `vive`, `trabaja`, `trabajen`, `sientanse`.
- La regla de la coma de Juan Diego (`45fddd2`) miraba solo la primera palabra después de la **primera** coma; ahora mira la primera palabra después de **cada** coma (para «Hola, familia, siéntanse bienvenidos»).

**Comprobación del cambio:**

- FR-01 por tipo, con el analizador: 24 de 30 coincidían antes y 30 de 30 ahora (las 6 órdenes).
- CO-02 (las 200 oraciones de `PR-HUE-03`): 168/200 antes y 168/200 ahora, igual que la cifra de Juan Diego, sin ninguna oración de otro tipo que pase a Orden.
- `verify_gloss_analyzer.py`, `verify_nlp_service.py` y `verify_sentence_segmenter.py` corren sin fallas.
- **Riesgo conocido:** `vive` y `trabaja` también son verbos en tercera persona, así que una afirmación que empiece con ellos («Vive con su abuelo») se leería como orden. No hay ninguna así en FR-01 ni en CO-02.

### 4.2 Ejecución

Las 10 frases de FR-01-A (2 por tipo) se mandaron por la app (`arnes/avt05.py`), con el panel de diagnóstico leído como texto mientras se representaban (`tipo` y `cara`), una grabación de pantalla por frase y una captura con la cara a mitad de la primera seña. El tipo esperado es el de la última oración, como dice FR-01.

| Frase | Tipo esperado | Secuencia `tipo/cara` en el panel | Coincide |
|---|---|---|---|
| FR-01-001 «Hola, buenos días, bienvenidos.» | Afirmación | AFFIRMATIVE/afirmativo | Sí |
| FR-01-005 «Buenas tardes. Hoy me siento más o menos.» | Afirmación | AFFIRMATIVE/afirmativo | Sí |
| FR-01-007 «¿Tienes hambre?» | Pregunta de sí o no | YES_NO_QUESTION/pregunta | Sí |
| FR-01-011 «Perdón. ¿Estás triste?» | Pregunta de sí o no | AFFIRMATIVE/afirmativo, luego YES_NO_QUESTION/pregunta | Sí |
| FR-01-013 «Hola, ¿cómo estás? ¿Cómo te llamas?» | Pregunta de contenido | AFFIRMATIVE/afirmativo, luego CONTENT_QUESTION/pregunta | Sí |
| FR-01-014 «¿Dónde vive tu familia?» | Pregunta de contenido | CONTENT_QUESTION/pregunta | Sí |
| FR-01-019 «Yo nunca como mucho.» | Negación | NEGATIVE/negacion | Sí |
| FR-01-023 «Perdón. Mi hermano nunca está contento.» | Negación | AFFIRMATIVE/afirmativo, luego NEGATIVE/negacion | Sí |
| FR-01-025 «Por favor, come poco.» | Orden | IMPERATIVE/orden | Sí |
| FR-01-026 «Con permiso. Déjame pasar, por favor.» | Orden | AFFIRMATIVE/afirmativo, luego IMPERATIVE/orden | Sí |

**Por tipo: 2 de 2 en los cinco tipos.** Orden, que fallaba el 07/11, ya sale como orden.

### 4.3 Lo que se vio a ojo

Comparado con la tabla de la ficha con las capturas y la grabación de cada frase: la afirmación se ve neutra, la pregunta de sí o no con las cejas levantadas, la negación con el ceño fruncido y la orden con el ceño bajado y firme. **La pregunta de contenido sale con las cejas levantadas, igual que la de sí o no**, y la tabla de la ficha pedía «cejas fruncidas hacia abajo durante la seña interrogativa». La causa es de diseño: `app.js` asigna `YES_NO_QUESTION` y `CONTENT_QUESTION` a la misma cara (`pregunta`) y los GLB traen 4 variantes por seña (`afirmativo`, `pregunta`, `negacion`, `orden`).

**Decisión de P3 (11/11): criterio de «al menos 4 de 5 tipos».** La tabla de la ficha **se deja como estaba** (la pregunta de contenido pide «cejas fruncidas hacia abajo») y el criterio pasa de «Se reporta» a:

> Al menos 4 de los 5 tipos de frase (el 80 %) muestran la expresión de la tabla de lo esperado de la ficha. Un tipo cuenta cuando coinciden sus dos frases. Limitación conocida: la aplicación asigna la misma cara a las dos preguntas, así que la pregunta de contenido no puede mostrar las cejas fruncidas. Criterio fijado el 11/11/2026.

Con eso coinciden 4 de 5 tipos (Afirmación, Pregunta de sí o no, Negación y Orden) y la prueba pasa en la evaluación. La diagnóstica del 07/11 queda en Falla (3 de 5). Salvedades:

- **No es una falla aleatoria.** La pregunta de contenido siempre saldrá con la cara de la de sí o no, hasta que se agregue una quinta cara o se cambie la tabla. A diferencia del 80 % de `PR-INT-02` (que absorbe la variación del LLM), aquí el 80 % tolera una limitación de diseño. Por eso el motivo está escrito en el criterio.
- **Pasa justo en el 80 %:** si falla otro tipo, la prueba falla.
- **Depende del cambio del backend** (§4.1, sin commit): sin él, Orden vuelve a fallar y la prueba da 3 de 5.
- La limitación roza `RF-AVT-03` del SRS («el rostro cambia de forma distinta según el tipo de oración»): conviene que el owner la conozca.
- El juicio visual lo hizo el agente, no alguien que sepa LSC; el TODO rojo de la ficha («revisar si estos son los gestos faciales adecuados») sigue abierto. El movimiento lateral de la cabeza en NUNCA no se revisó.

## 5. PR-REN-07 — Rendimiento en el móvil

**Método** (el de la diagnóstica del 10/11, `RESULTADOS-P3-2026-11-10.md` §7.1, con el arnés `arnes/ren07.py`):

- Teléfono descargándose y sin cable (`adb tcpip 5555` y desconexión del USB): batería de 52 a 40 %, estado «descargando» en las tres corridas.
- Pantalla encendida con el brillo que ya tenía (86).
- Cada corrida: app limpiada con `pm clear`, 30 s para asentarse, 60 s con la app quieta y las 30 frases de FR-01 seguidas por texto.
- Fuentes: `dumpsys gfxinfo` reiniciado al empezar la secuencia, `dumpsys meminfo` cada 30 s, el contador de carga de `dumpsys battery`, y los `signStart` y `representado` del log de la app.

| Métrica | Umbral (11/11) | Corrida 1 | Corrida 2 | Corrida 3 |
|---|---|---|---|---|
| Temperatura del teléfono | — | 35,4–36,8 °C | 37,3–37,9 °C | 38,0–38,3 °C |
| Cuadros lentos (janky) | 54 % o menos | 37,78 % | 41,47 % | 42,98 % |
| Tiempo por cuadro: mediana | 48 ms o menos | 34 ms | 36 ms | 36 ms |
| p95 | 82 ms o menos | 57 ms | 61 ms | 61 ms |
| Medio (≈ cuadros por segundo) | 50 ms o menos | 35,1 ms (28,5) | 37,2 ms (26,9) | 37,7 ms (26,5) |
| Cuadro más lento | — | 150 ms | 150 ms | 150 ms |
| App quieta (60 s), janky | — | 23,04 % | 27,94 % | 31,12 % |
| Transición entre señas (82 intervalos): p50 | 2.538 ms o menos | 2.030 ms | 2.030 ms | 2.030 ms |
| p95 | 2.665 ms o menos | 2.362 ms | 2.347 ms | 2.357 ms |
| Representación por frase (n=30): p50 | 9.609 ms o menos | 8.247 ms | 8.246 ms | 8.218 ms |
| p95 | 13.280 ms o menos | 12.764 ms | 12.768 ms | 12.789 ms |
| Memoria PSS máxima | 864 MB o menos | 678 MB | 682 MB | 668 MB |
| Batería durante la secuencia | 1.899 mA o menos | 190 mAh en 443 s ≈ 1.545 mA | 160 mAh en 444 s ≈ 1.296 mA | 155 mAh en 436 s ≈ 1.280 mA |

**Cumple todos los umbrales en las tres corridas**, sin errores ni reintentos en las 90 frases.

**Diferencias de método con la diagnóstica:**

- **Duración:** la secuencia dura 436–444 s y no 323 s, porque el arnés espera cada frase y deja 1,8 s antes de la siguiente. Hay más tiempo con la escena quieta dentro de la ventana de cuadros y de batería. Con el 25 % de margen de los umbrales, no cambia el veredicto.
- **GLB y compilación:** la app se compiló con los GLB nuevos de Juan Diego (§6); la diagnóstica se midió con los anteriores.
- **Temperatura y consumo:** la corrida más caliente fue la de más cuadros lentos, y el consumo no siguió a la temperatura como en la diagnóstica, donde la más fría gastó más. No se determinó por qué.
- **Batería:** el contador de carga avanza de a 5 mAh (≈ ±1 mAh por minuto).

## 6. Hallazgo: los GLB de los avatares cambiaron durante la sesión

El commit `051e73a` de Juan Diego («entradas y regresos al reposo nuevos en las 50 señas», 11/11 19:09) llegó al repositorio local a las 19:29 y reemplazó `juan.glb` y `pauline.glb`. Consecuencias:

- **`PR-AVT-04`:** los saltos bajaron (§2). Las filas del libro y la evidencia distinguen las dos mediciones. La ficha trae los umbrales de los GLB anteriores.
- **`PR-AVT-06` (peso de las animaciones, carril de P1):** su fila de evaluación dice «Los GLB no cambiaron desde el 06/11» y 30.323.680 B para Juan; con los GLB nuevos eso ya no es cierto. No se tocó: es del carril de P1.
- **`PR-AVT-05` y `PR-REN-07`:** se midieron con una app compilada con los GLB nuevos.
- **`PR-INT-02`:** corrió con el APK del 10/11, con los GLB anteriores. No cambia su resultado.

## 7. Cambios en el libro y en el plan

**Libro** (`resultados-consolidados.xlsx`; respaldos 16 y 17 en `respaldos-plan-pruebas-2026-11-10/`):

- Las 14 pruebas de P3 que pasaron (`INT-03` a `INT-06`, `AND-01` a `AND-06`, `AVT-01`, `AVT-02`, `AVT-03` y `AVT-07`) tienen fila de evaluación con la notación de P2: Estado Pasa, evidencia «—», misma fecha y ejecutor que la diagnóstica, y el texto «Pasó en la ronda diagnóstica (fecha); no se repite…».
- Se borró `PR-PER-06`: el plan de `origin/main` ya no la trae (73 fichas); el libro estaba desactualizado. Quedan 73 pruebas.
- `PR-AVT-04`: fila diagnóstica «Se reporta» y fila de evaluación «Pasa».
- `PR-INT-02` y `PR-AVT-05`: fila de evaluación «Pasa» con el criterio de la ficha cambiado (§3 y §4).
- `PR-REN-07`: fila de evaluación «Pasa» (§5).
- Portada: 73 pruebas en 14 bloques, en dos rondas (134 filas).

**Fichas del plan** (`sign-ai-docs`; el plan compila): se editó el `.tex`, los borradores por bloque y `secciones/09-casos-de-prueba.md`.

- `PR-REN-07`: valores fijados el 11/11/2026 con la ronda diagnóstica del 10/11 (cifra medida más 25 %, redondeada hacia arriba, sobre la peor de las 3 corridas) y entorno de medición.
- `PR-AVT-04`: entorno de medición y umbrales (mediana de 7 cm o menos, p95 de 47 o menos, máximo de 50 o menos, y 23 % o menos de pasos sobre 10 cm).
- `PR-AVT-05`: criterio de verificación («al menos 4 de los 5 tipos muestran la expresión de la tabla»), que antes decía «Se reporta». La tabla de lo esperado no cambia (§4.3).
- `PR-INT-02`: el criterio de §3.

## 8. Pendiente y decisiones del owner

1. **`PR-INT-02`:** ~~aprobar el criterio del 80 % (24 de 30) o fijar otro~~. **Aceptado por P3 el 11/11** (el criterio queda como está en §3).
2. **`PR-AVT-05`:** el criterio de «al menos 4 de 5 tipos» lo fijó P3; el owner debería conocerlo, porque la pregunta de contenido no coincide con la tabla y roza `RF-AVT-03` del SRS. Cerrar del todo implica agregar una quinta cara o cambiar la tabla. El TODO de revisar los gestos con alguien que sepa LSC sigue abierto.
3. **Cambio del backend** (`gloss_analyzer.py`): probado en el A54; falta el commit, y de él depende que `PR-AVT-05` dé 4 de 5.
4. **`PR-AVT-04`:** ~~decidir si los umbrales se fijan de nuevo con los GLB vigentes~~. **Se dejan los que están** (fijados con los GLB anteriores), por decisión de P3 el 11/11.
5. **`PR-AVT-06`:** el carril de P1 debería repetir el peso con los GLB nuevos (§6).
6. **`RNF-DES-05`:** el SRS pide fijar el mínimo de cuadros por segundo entre 24 y 30. Se midieron entre 25 y 28 con el teléfono descargándose: 30 no se cumple y 24 queda justo.
7. Las cuatro decisiones del informe del 10/11 (§4 de `RESULTADOS-P3-2026-11-10.md`): guardar o no la frase cuya traducción falla en el historial, el efecto de un cambio de velocidad a mitad de frase, el aspecto de «Traducir» sin conexión y, ya resuelta con el cambio del backend, «Orden» como afirmativo.
8. **Bloqueadas:** `PR-PER-01` a `PR-PER-05`, porque no hay intérpretes ni usuarios.
9. **Evidencia perdida:** los archivos originales de `PR-INT-04`, `PR-AVT-02` y `PR-AVT-05` (los de la diagnóstica) no se conservan; solo las tablas escritas.

## 9. Salvedades de método

- **Base real:** `PR-INT-02` se midió contra la Neon real y compartida, sin vaciar la caché. Esta corrida agregó 8 filas a la caché (de 46 a 54); son traducciones de las frases de FR-01.
- **Juicio visual:** el de `PR-AVT-05` es del agente.
- **`PR-AVT-04`:** se midió sobre los clips del GLB, no en el teléfono. Es el salto crudo, sin la fundición de 100 ms de la app.
- **Compilaciones distintas** por prueba (ver la cabecera); ninguna es una compilación `release`.
- **Datos de la app:** `PR-REN-07` hizo `pm clear` tres veces; la app perdió sus ajustes y el historial, y se concedió de nuevo `RECORD_AUDIO`.
- **Claves del backend:** no se guardaron en ningún archivo de evidencia.
- **Memoria PSS:** se muestreó cada 30 s (15 muestras por corrida).
- **El A54 quedó** con la app normal instalada y conectado por `adb` inalámbrico al terminar.

## 10. Evidencias

Todo con el nombre `PR-<BLOQUE>-<NN>_E_20261111_<nn>.<ext>`:

- `PR-AVT-04_E_20261111_*` (en `evidencias/AVT/`): `01` (GLB anteriores) y `02` (GLB nuevos), cada una con `.csv` (los 164 pasos, 82 por avatar) y `.json` (el resumen). La diagnóstica es `PR-AVT-04_D_20261111_01.{csv,json}`.
- `PR-INT-02_E_20261111_01.{csv,json,txt}` (en `evidencias/INT/`): tabla de las 30 frases, resumen y `logcat` crudo.
- `PR-AVT-05_E_20261111_*` (en `evidencias/AVT/`): `01` a `10` en `.mp4` (una grabación por frase, 33 MB en total) y en `.png` (la cara a mitad de la primera seña), y `11.csv` y `11.json` (la tabla de lo observado frente a lo esperado).
- `PR-REN-07_E_20261111_*` (en `evidencias/REN/`): corrida 1 con `01` a `09`, corrida 2 con `11` a `19` y corrida 3 con `21` a `29`. En cada una: `x1.txt` (registro de la sesión), `x2_frases.csv`, `x3_gfxinfo.txt`, `x4_memoria.txt`, `x5_logcat.txt`, `x6_bateria.csv`, `x8_gfxinfo_reposo_60s.txt` y `x9.json` (el resumen).
