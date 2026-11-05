# Conclusiones de la ronda diagnóstica — 2026-11-04

Este documento lee los resultados de la ronda diagnóstica y saca conclusiones **por problema
raíz**, no por prueba. No es la tabla de resultados (esa está en
`plantilla-resultados.xlsx`), no es el análisis completo (ese está en
`ANALISIS-2026-11-04.md`) y no cambia el libro, el plan ni el código.

- **Ronda:** diagnóstica. **Fecha de ejecución:** 04/11/2026.
- **Resultados usados:** `docs/plan-pruebas/plantilla-resultados.xlsx` y
  `docs/plan-pruebas/ANALISIS-2026-11-04.md`.
- **Corte del análisis usado:** 67 de 74 filas, generado el 2026-11-04 a las 23:50. Las siete
  filas que faltaban en ese corte (RES-02, RES-03, RES-04, RES-07, SEC-01, SEC-04 y REN-04) se
  estaban ejecutando al escribir. Donde una conclusión depende de ellas, se indica.
- **Código contrastado:** este repositorio, `../sign-ai-backend` y `../sign-ai-stt`.
- **Documentos contrastados:** `../sign-ai-docs/latex/` (SRS, SDD, plan de pruebas). Overleaf se
  retiró el 2026-11-04; los documentos formales viven en ese repositorio.
- **Lo que no se hizo:** no se ejecutaron pruebas nuevas, no se editó el libro, no se tocó el
  plan y no se cambió código.

## 0. Cómo se lee

**Severidad**

| Nivel | Significado |
|---|---|
| **Crítica** | Impide una función del sistema o produce una señal equivocada de forma reproducible en el uso normal. |
| **Alta** | El resultado es incorrecto o incompleto de forma visible, o la señal es equivocada en un caso de borde; el sistema sigue funcionando. |
| **Media** | Degrada robustez, latencia o mantenibilidad; no cambia el sentido de lo que se representa. |
| **Baja** | Deuda de mantenibilidad o de instrumentación; no afecta al usuario hoy. |

**Tipo de hallazgo**

- **Sistema:** el código no cumple lo que el sistema promete.
- **Ficha:** la prueba pide algo que el requisito no pide, o mide mal.
- **Conjunto de datos:** el corpus o el catálogo tienen un error.
- **Instrumentación:** lo que se mide no es lo que se cree medir.

**«No concluyente»** significa que la evidencia de esta ronda no alcanza para decidir. No se
rellena con una hipótesis: se propone qué medir después.

## 1. Resumen en una página

La ronda no encontró fallas en el avatar ni en la app como reproductores. El avatar representó
**99 de 99 glosas** que recibió en `PR-INT-02` y las **50 animaciones** en `PR-AVT-02`. Las
fallas están antes del avatar: en el filtro de voz, en la lectura de la respuesta del LLM, en la
caché semántica, en una decisión de producto sobre la cola y en dos puntos donde la app no
avisa (el permiso de micrófono y el envío sin conexión).

| # | Problema raíz | Pruebas que lo muestran | Severidad | ¿Decisión del owner? |
|---|---|---|---|---|
| S1 | El punto final y el preámbulo del LLM hacen que se pierdan glosas en silencio | PR-LLM-02, PR-VAL-01 | Alta | No para el arreglo; sí para el criterio |
| S2 | La traducción no alcanza la referencia: 15/30 frases exactas y 99/112 glosas | PR-LLM-01, PR-LLM-03, PR-INT-02, PR-AVT-02 | Crítica | Sí (umbral y referencia) |
| S3 | Texto que no es glosa llega a la pantalla y al panel de conversación | PR-LLM-05, PR-LLM-04 | Alta | Sí (lectura del criterio) |
| S4 | Tos y carraspeo se transcriben y se representan | PR-STT-03, PR-STT-05, PR-AND-01 | Crítica | Sí (dónde va la barrera) |
| S5 | Una caída del STT deja sin traducción de texto y la app lo calla | PR-RES-03, PR-INT-05 | Crítica | No: el SRS ya lo exige |
| S6 | La caché devuelve glosas de otra frase con sentido distinto | PR-CAC-04, PR-CAC-02 | Alta | Sí (umbral) |
| S7 | La caché no reconoce sinónimos: 1/10 | PR-CAC-03, PR-CAC-06 | Media en producto, alta en la tesis | Sí |
| S8 | Una frase nueva reemplaza la cola y corta la anterior | PR-AVT-03 | Alta | Sí |
| S9 | El aviso del permiso de micrófono no se ve | PR-AND-01 | Alta | Sí |
| S10 | El STT cae a CPU y `/health` sigue diciendo `cuda` | PR-STT-04, PR-REN-05 | Media | No |
| S11 | Dos importaciones se saltan la interfaz | PR-TRA-01 | Baja | No |
| S12 | El catálogo acepta una glosa duplicada y la tapa en silencio | PR-VAL-02 | Baja | Sí (esquema) |

Además hay **nueve defectos de ficha o de conjunto de datos** (sección 4) y **nueve hallazgos
que no hacen fallar una fila** (sección 5). Los más importantes de esos dos grupos son el
`PR-CAC-05`, que mide mal contra el SRS, el `PR-INT-03`, que ya no se puede preparar, y la
variante facial, que hoy está activa en el código pero no se midió.

## 2. Qué se ejecutó y qué no

### 2.1. Conteo del corte usado

- **67 de 74** filas cargadas: 17 Pasa, 11 Falla, 23 Se reporta, 16 Bloqueada.
- **15 bloqueadas** por dependencia externa o falta de instrumentación: SEG-03, HUE-01,
  HUE-02, CAC-01, INT-03, INT-04, AVT-04, AVT-05, RES-05, SEC-03 y las seis PER.
- **7 pendientes** al escribir: RES-02, RES-03, RES-04, RES-07, SEC-01, SEC-04 y REN-04.

Las filas pendientes no cambian el diagnóstico de las secciones 3 y 4: RES-02, RES-04 y RES-07
ya tienen evidencia escrita en `evidencias/RES/`; la evidencia de RES-03 ya muestra el problema
S5, aunque todavía no tenga estado oficial. Cuando la sesión maestra regenere el análisis con
las 74 filas, hay que releer este documento contra esa versión.

### 2.2. Lo que hay que tener presente antes de citar un número

- **El LLM es local (`qwen3:4b-instruct`), no el oficial `gpt-4o-mini`.** Todas las pruebas de
  LLM, caché y latencias del backend miden ese modelo. No son comparables con la línea base
  publicada con el modelo oficial.
- **El backend medido tenía cambios sin commitear sobre `c8aa516`.** Lo medido es ese árbol de
  trabajo, no ese commit.
- **La base es Neon, la de la tesis**, no una base de laboratorio. Las pruebas de caché
  vaciaron `semantic_cache` y `PR-VAL-03` retiró y repuso una fila de `catalog` (quedó en 50).
- **El teléfono es un Redmi Note 9S (Android 11)** y las pruebas de rendimiento se corrieron en
  el equipo de escritorio (Ryzen 5 7535HS, RTX 4050 Laptop de 6 GB), **no en el servidor
  declarado**. El SRS describe el servidor como un equipo personal del equipo de desarrollo
  (`main.tex:1075`); falta confirmar si es la misma máquina. Los números de `REN-05` y `REN-07`
  son de ese equipo.
- **Hubo repeticiones por contaminación:** `PR-REN-01` se repitió por otro cliente, `PR-REN-05`
  por la carga de Ollama, `PR-REN-07` por dos colectores y `PR-INT-06` por dos corridas
  simultáneas. Se reportan las corridas limpias; el detalle está en la bitácora.
- **La app se midió con `DEV_TOOLS` apagado**, que es como vive el usuario. Eso importa para
  S9: los avisos de 3 s existen solo con depuración.

## 3. Problemas raíz del sistema

### S1. El punto final y el preámbulo del LLM hacen que se pierdan glosas en silencio

**Qué se observó.** De las 10 respuestas fabricadas de `CO-04`, **6 se leen bien y 4 mal**
(`PR-LLM-02`). Las cuatro que fallan:

- `HOLA. BUENOS DIAS. BIENVENIDO.` se convierte en 4 tokens (`HOLA.`, `BUENOS`, `DIAS.`,
  `BIENVENIDO.`) y `BUENOS DIAS` se parte.
- `ABUELO, NUNCA, TRABAJAR.` pierde `TRABAJAR`.
- `Claro, aquí tienes la traducción: TU, HAMBRE` se queda solo con `HAMBRE`.
- `TIO, TRABAJAR. Espero que te sirva.` se queda solo con `TIO`.

Ninguna lanza excepción: las glosas desaparecen y el sistema sigue como si nada. En las 30
frases reales de FR-01 no se vio el problema (`30/30` se leen bien), pero el mecanismo está
armado.

**Por qué ocurre (verificado en el código).**

1. `normalize_gloss()` solo quita `¿` y `?`: `_PUNCTUATION = str.maketrans("", "", "¿?")`
   (`../sign-ai-backend/app/validation/catalog_repository.py:18`). El punto final queda dentro
   de la clave y no coincide con ninguna glosa del catálogo. Eso también explica
   `PR-VAL-01`: `FAMILIA.` y `ADIOS.` salen `available=false` aunque el catálogo las tenga.
2. `_parse_glosses()` separa por coma si hay coma y, si no, por espacios
   (`../sign-ai-backend/app/nlp/llm_client.py:212-214`). Sin coma, `BUENOS DIAS` se parte.
3. `_repair_glosses()` descarta toda glosa que no encuentre en el vocabulario y solo deja un
   `logger.info` (`llm_client.py:216-246`). No falla, no avisa y no cuenta.

**A quién afecta.** Al backend y al usuario: la frase llega incompleta al avatar. No es un
defecto del avatar ni de la app.

**Severidad.** Alta. Es pérdida silenciosa de información; en las respuestas reales de esta
ronda no se disparó, pero el `PR-LLM-02` demuestra que el camino existe.

**Tipo.** Sistema.

**Propuesta.** Extender la normalización al punto y a los signos de cierre (`.,;:!…`), y hacer
que una glosa que no se puede mapear deje un `warning` y una métrica (`llm_glosses_dropped`)
antes de descartarla. Para el preámbulo (`Claro, aquí tienes...`), no alcanza con la
puntuación: hay que aceptar solo tokens que existan en el vocabulario y no arrastrar el texto
anterior a la primera glosa válida. Archivos:
`../sign-ai-backend/app/validation/catalog_repository.py` y
`../sign-ai-backend/app/nlp/llm_client.py`.

**Decisión del owner.** El arreglo no la necesita. Sí la necesita la pregunta de si una
respuesta ambigua debe fallar visiblemente o seguir con las glosas que se reconocen; hoy sigue
en silencio.

### S2. La traducción no alcanza la referencia: 15/30 frases exactas y 99/112 glosas

**Qué se observó.** Con `qwen3:4b-instruct`:

- `PR-LLM-01`: **14/30** frases con glosas y orden exactos; 29/30 con el orden relativo
  correcto; 16/30 con glosas faltantes o de más.
- `PR-LLM-03`: **15/30** frases coinciden exactamente con la referencia; las otras 15 difieren
  en las 5 vueltas por igual (temperature 0,0).
- `PR-INT-02`: de **112** glosas que declara el corpus, el backend devolvió **99** (88,4 %); 15
  de 30 frases no coinciden; el avatar representó el 100 % de lo que recibió.
- `PR-AVT-02`: con las 30 frases salieron **43 de las 50** glosas; las otras 7
  (BIENVENIDO, JUCIOSO, NIÑO, NOMBRE, PERSONAS, QUE, SEÑA) no aparecen dentro de una frase,
  pero con una sola palabra sí.

Las diferencias de `PR-INT-02` se reparten así: 9 omisiones, 2 glosas de más, 3 cambiadas y 1
reordenada. Ejemplos concretos de la evidencia `PR-INT-02_D_20261104_01.json`:

| Frase | Esperado | Recibido | Tipo |
|---|---|---|---|
| FR-01-001 | HOLA, BUENOS DIAS, BIENVENIDO | HOLA, BUENOS DIAS | Omisión |
| FR-01-006 | GRACIAS, TODOS, NIÑO, NIÑA, COMER, CON GUSTO | GRACIAS, TODOS, COMER, CON GUSTO | Omisión |
| FR-01-008 | TU, BIEN | BIEN | Omisión |
| FR-01-018 | PERSONAS, DIFERENTE, QUE, SEÑA, TU | DIFERENTE, YO | Omisión y cambio |
| FR-01-003 | ABUELO, TIO, TRABAJAR, FAMILIA | ABUELO, TU, TIO, FAMILIA | Cambio |
| FR-01-024 | NIÑA, SENTIR, MUCHO, BIEN, NUNCA, TRISTE | BIEN, NIÑA, NUNCA, TRISTE | Reorden y omisión |

**Qué se puede afirmar y qué no.** La evidencia **no separa** cuánto pierde el modelo y cuánto
pierde `_repair_glosses`, porque la ronda guardó las glosas finales y no la respuesta cruda del
LLM. Lo que el código permite afirmar es que hay tres causas posibles y distintas:

1. **El modelo omite** por la instrucción «If you do not know the gloss for part of the
   meaning, leave that part out» (`llm_client.py:90-91`). Es el caso probable de FR-01-018
   («Las personas son diferentes. ¿Qué seña usas tú?» → `DIFERENTE, YO`).
2. **El reparador descarta inflexiones** que el catálogo no tiene: si el modelo devuelve
   `NIÑOS`, `NIÑAS` o `BIENVENIDOS`, `normalize_gloss()` no los convierte a `NIÑO`, `NIÑA` o
   `BIENVENIDO` y `_repair_glosses()` los tira. Es una hipótesis compatible con las omisiones
   de FR-01-001, FR-01-006 y FR-01-029, pero **no está medida**.
3. **El catálogo tiene una glosa con error de escritura:** `JUCIOSO` (así, sin la `I`) es la
   glosa de la seña 30 en `CT-03-076`/`CT-03-077`, mientras el español del corpus dice
   «juicioso». El LLM «corrige» a `JUICIOSO`, que no está en el catálogo, y el sistema la marca
   no disponible. Por eso `PR-AVT-02` necesitó escribir `jucioso` en minúscula para que
   saliera. Es un defecto del conjunto de datos, no del avatar.

**A quién afecta.** Al backend (NLP) y al usuario. El avatar cumple: representa todo lo que
recibe.

**Severidad.** Crítica para la tesis: `RNF-DES-07` («proporción mínima de frases con glosas
correctas») tiene el umbral en TODO (`main.tex:2715`), y con 14–15/30 no se puede afirmar que
se cumpla. Para el producto es alta: la frase se entiende peor.

**Tipo.** Sistema y conjunto de datos (la parte de `JUCIOSO`).

**Propuesta.** Antes de tocar el prompt, **medir la separación**: guardar `response.content`
crudo y las glosas después de `_repair_glosses` para las 30 frases. Después:

- si las omisiones vienen del modelo, ajustar el prompt (pronombres implícitos, plurales) o
  subir de modelo;
- si vienen del reparador, mapear plurales y conjugaciones antes de descartar, en
  `llm_client.py`;
- corregir `JUCIOSO` en el catálogo o agregar un alias `JUICIOSO → JUCIOSO` (decisión de
  datos).

Archivos: `../sign-ai-backend/app/nlp/llm_client.py`, el catálogo en Neon y
`../sign-ai-docs/borradores/plan-pruebas/02-datos/FR-01-frases-dominio.md` si se decide que la
referencia debe cambiar.

**Decisión del owner.** Sí, dos: (1) qué umbral de `RNF-DES-07` se va a exigir, y (2) si la
referencia de FR-01 es la correcta en los casos de pronombre implícito («¿Estás bien?» → TU) y
de plural («niños» → NIÑO).

### S3. Texto que no es glosa llega a la pantalla y al panel de conversación

**Qué se observó.** En `PR-LLM-05`, **10 de 18** respuestas entregan al menos una glosa con
texto fuera del catálogo y **3 obedecen** la instrucción del hablante. `FR-07-013` responde en
inglés con un párrafo de ~600 caracteres (5 glosas no disponibles), `FR-07-015` con una lista
numerada de ~900 caracteres (8 glosas) y `FR-07-016` devuelve solo `LISTO`. En `PR-LLM-04`, 8
de 15 frases traen glosas fuera del catálogo: 79 glosas en total.

**Por qué ocurre (verificado en el código).** `formatGlosses()` arma la glosa de la entrada del
panel de conversación con **todas** las glosas, sin mirar `available`:

```kotlin
// app/src/main/java/com/signai/webview/ui/state/MainScreenState.kt:1932
private fun formatGlosses(segments: List<TranslationSegment>): String =
    segments.flatMap { it.glosses }.joinToString(" ") { it.text }
```

El avatar no representa esas glosas (el indicador dice «Seña no disponible»), pero el párrafo
en inglés **se lee en pantalla**. Contra el criterio de la ficha («El usuario solo debe ver
glosas del catálogo o el aviso de seña no disponible»), la fila falla.

**A quién afecta.** Al usuario. Es el único punto de esta lista donde el texto de una inyección
sale del log y llega a la interfaz.

**Severidad.** Alta.

**Tipo.** Sistema.

**Propuesta.** En `formatGlosses()`, incluir solo glosas con `available=true` (o reemplazar el
texto de las no disponibles por la frase «Seña no disponible»), y no volcar el texto crudo en
el panel. El indicador ya cumple su parte. Archivo:
`app/src/main/java/com/signai/webview/ui/state/MainScreenState.kt:1932`.

**Decisión del owner.** Sí, de criterio: si el `PR-LLM-05` se lee como «ninguna seña
equivocada», la fila pasaría a `Pasa`; si se lee como está escrito («el usuario solo ve glosas
o el aviso»), es `Falla`. Aun con la lectura benévola, conviene no mostrar el párrafo.

### S4. Tos y carraspeo se transcriben y se representan

**Qué se observó.** `PR-STT-03`: **2 de 8** audios sin voz producen texto por el camino de uso
(streaming):

- `AU-03-005` (tos): «COUGH | Cof Cof | COUGH COUGH | COUGH COUGH», en 4 finales.
- `AU-03-006` (carraspeos): «Grrrr | Ejem», en 2 finales.

Por `POST /transcribe`, los mismos 8 audios dan **0 textos**. `PR-STT-05` confirma que ningún
umbral de VAD lo arregla: con agresividad 0 y 1 hay 3/8 audios con texto, con 2 y 3 hay 2/8; la
tos y el carraspeo aparecen siempre. El WER sobre voz es 3,90 % en los cuatro umbrales, así que
mover el VAD no mejora una cosa sin empeorar otra.

`PR-AND-01` muestra la otra cara: en dictado, con sonido ambiente continuo, el micrófono quedó
prendido **26 s** sin hablar y el campo recibió el parcial «¡BOS!». El STT no vio 1,5 s de
silencio porque el ruido le pareció voz.

**Por qué ocurre (verificado en el código, y corrige una lectura del análisis).** Las dos vías
—streaming y POST— usan **la misma** función `transcribe_buffer()`
(`../sign-ai-stt/app/transcriber.py:218`). El POST la llama con el archivo completo
(`../sign-ai-stt/app/main.py:92-99`); el streaming la llama con el tramo que el VAD marcó como
voz (`../sign-ai-stt/app/session.py:328-336`). El filtro es el mismo: RMS, `no_speech` y
`logprob` (`transcriber.py:226-278`). No es que el filtro «falte» en streaming: la evidencia
de `AU-03-005` no registra ningún `drop_reason` y el texto «COUGH» sale igual, así que ese
buffer corto pasó los tres umbrales. El POST, con 20 s de audio, produce segmentos de baja
confianza y los descarta por `logprob` o por `rms` (evidencia `PR-STT-03`). El POST tampoco
recorta silencio: pasa el archivo entero y calcula
el RMS sobre el percentil de las tramas más fuertes (`_loud_frames_rms`,
`transcriber.py:207-216`). La causa física de que el buffer corto pase (RMS alto, confianza
alta) es compatible con esos datos, pero **no se midió** el RMS ni el `logprob` de cada final;
es una hipótesis, no un número de esta ronda.

La app no tiene ninguna barrera: en modo en vivo, cualquier `final` con texto se manda a
traducir (`MainScreenState.sendLivePhrase`, `MainScreenState.kt:1295`).

**A quién afecta.** Al usuario (ve señas de una tos), al STT (calidad) y al backend (traduce
basura). No es un defecto del avatar: el avatar representa lo que le llega.

**Severidad.** Crítica para una demo o una sesión real; alta para la tesis, porque no hay un
requisito que cubra el caso (ver F6).

**Tipo.** Sistema.

**Propuesta.** Decidir **dónde va la barrera**, porque el VAD no alcanza:

- en el STT, un segundo filtro sobre el final (duración mínima de voz, clasificador de no voz,
  o una lista de tokens de no voz — frágil);
- en el backend, no traducir un `final` que no tenga al menos una palabra del dominio;
- en la app, no enviar en vivo un `final` de una sola palabra que no esté en el catálogo.

La opción de la app es la más barata y la más fácil de medir, pero deja pasar «COUGH COUGH».
La del STT es la correcta de fondo. Archivos: `../sign-ai-stt/app/session.py` y
`transcriber.py`; si se elige la app, `MainScreenState.kt:1275-1330`.

**Decisión del owner.** Sí: dónde poner la barrera y si se agrega un requisito (ver F6).

### S5. Una caída del STT deja sin traducción de texto y la app lo calla

**Qué se observó.** `PR-RES-03` (evidencia `RES/PR-RES-03_D_20261104_01.csv`):

- Con el STT detenido, al tocar el micrófono la app queda en «Desconectado» y el botón queda
  deshabilitado **sin decir nada**.
- Con el STT detenido, una frase **escrita** y Traducir: «nada; el indicador sigue en
  Desconectado, no aparece ningún aviso y la frase se queda en el campo; el envío se descarta
  en silencio».
- Con el STT levantado y sin reiniciar la app: el texto se traduce en 685 ms y se representa.

`PR-INT-05` muestra el mismo mecanismo sin el STT: con el backend inalcanzable, el botón
Traducir aparece **habilitado** en el árbol de accesibilidad, pero tocarlo no hace nada.

**Por qué ocurre (verificado en el código).**

1. El backend acepta la conexión del cliente, pero **si no puede abrir la del STT** manda un
   error y **cierra la sesión antes de atender nada**. Si `stt_client.connect()` falla, la
   sesión termina ahí (`../sign-ai-backend/app/controller/router.py:224-240`). El texto y la
   voz viajan por el mismo socket, así que sin STT no hay ni texto.
2. En la app, `sendEnabled` no mira `Desconectado`
   (`app/src/main/java/com/signai/webview/ui/screens/bottombar/SignAiBottomBar.kt:113`), pero
   `requestTranslation()` sí rechaza en ese estado y retorna sin aviso
   (`MainScreenState.kt:1446-1451`). El botón se ve habilitado y no hace nada.

**A quién afecta.** Al usuario (no puede traducir texto si el STT se cayó) y al backend (deja
de dar un servicio que no depende del STT).

**Severidad.** Crítica: contradice `RNF-AV-03` («si el servicio de transcripción no responde,
el servidor debe seguir operativo... las frases de la caché se siguen traduciendo»,
`main.tex:2757`). También contradice el criterio de `PR-RES-03` («la entrada de texto sigue
funcionando»).

**Tipo.** Sistema.

**Propuesta.** Abrir la sesión del cliente aunque el STT no esté; conectar el STT en el primer
audio y devolver un error solo para el audio si falla. No es una línea: hoy la sesión arranca
las dos mitades juntas y `_forward_transcripts` termina en cuanto el STT no tiene conexión
(`router.py:251-263`), así que hay que separar el arranque de la mitad de transcripción. En la
app, agregar `playbackState != Desconectado` a `sendEnabled` y mostrar el motivo (el estado
«Desconectado» ya existe; el texto del aviso puede ir en la fila del micrófono). Archivos:
`../sign-ai-backend/app/controller/router.py` y
`app/src/main/java/com/signai/webview/ui/screens/bottombar/SignAiBottomBar.kt:113`.

**Decisión del owner.** No para el fondo: el SRS ya lo exige. Sí para la forma del aviso, que
choca con la decisión de 2026-10-17 de no mostrar textos en el indicador (ver S9).

### S6. La caché devuelve glosas de otra frase con sentido distinto

**Qué se observó.** `PR-CAC-04`: **1 de 30** trampas produce un falso acierto real.
`FR-04-023` envía «Perdón. Mi hermano está contento.»; la oración «Mi hermano está contento.»
sale de la caché y devuelve las glosas de «Estoy contento porque mi hermano está feliz.»
(`YO, CONTENTO, HERMANO, FELIZ`) a distancia **0,042**, por debajo del umbral 0,05. Las otras
12 que la regla mecánica contaría son reutilización literal de una oración idéntica, no falsos
aciertos.

`PR-CAC-02` da la tabla para decidir: 0,03 → 18/30 paráfrasis y **0 falsas**; 0,05 (actual) →
21/30 y 2; 0,08 → 23/30 y 7; 0,30 → 27/30 y 21.

**Por qué ocurre.** El umbral actual es 0,05
(`../sign-ai-backend/app/config/settings.py:84`) y la comparación es por oración
(`semantic_cache.py:226`). Dos oraciones con sujeto distinto pueden quedar a 0,042.

**A quién afecta.** Al usuario: el avatar representa una frase con el sentido cambiado. Es el
peor tipo de error de la caché porque es invisible.

**Severidad.** Alta.

**Tipo.** Sistema (calibración) y decisión de producto.

**Propuesta.** Bajar el umbral a 0,03 (0 falsos, 18/30) o agregar una guarda de polaridad y de
sujeto antes de aceptar el hit. La guarda es más trabajo y no está medida; el umbral es una
línea. Archivos: `../sign-ai-backend/app/config/settings.py:84` y
`semantic_cache.py:226`.

**Decisión del owner.** Sí. `RF-NLP-06` dice que el umbral queda pendiente de los resultados de
las pruebas (`main.tex:2498`); esta ronda es la que lo tiene que fijar.

### S7. La caché no reconoce sinónimos: 1/10

**Qué se observó.** `PR-CAC-03`: **19/30 (63,3 %)** variantes resueltas por caché. Formato
**9/10**, orden **9/10**, sinónimos **1/10**. `PR-CAC-06`: 19/60 frases resueltas sin LLM con
caché, 41/60 con al menos una oración al LLM.

**Qué se puede afirmar.** La caché reconoce la misma frase escrita distinto (formato y orden),
pero no la misma idea con otra palabra. `RF-NLP-06` pide exactamente eso: «Dos formas distintas
de decir lo mismo... recuperan la misma traducción guardada» (`main.tex:2498`). Con 1/10, el
criterio no se cumple.

**Qué no se puede afirmar.** No se sabe si la causa es el modelo de embeddings
(`paraphrase-multilingual-MiniLM-L12-v2`), el umbral o el corpus de sinónimos. `PR-CAC-01`
—comparar modelos de embeddings— quedó **bloqueada** porque el nombre está fijo en
`../sign-ai-backend/app/nlp/embedding_model.py` y no se lee del entorno.

**A quién afecta.** Al usuario por latencia y dependencia del LLM; al backend por costo. No
cambia el sentido de lo que se representa.

**Severidad.** Media para el producto; alta para la tesis si la memoria afirma que la caché
compara por significado.

**Tipo.** Sistema / decisión de alcance.

**Propuesta.** Hacer configurable el modelo de embeddings (una variable de entorno) y correr
`PR-CAC-01` antes de decidir. Bajar el umbral no arregla los sinónimos: a 0,03 el total de
paráfrasis baja de 21/30 a 18/30 y no hay evidencia de que los sinónimos suban; el problema de
los sinónimos no es de umbral.

**Decisión del owner.** Sí: si la tesis va a afirmar «comparación por significado», hay que
medir CAC-01; si no, hay que acotar la afirmación.

### S8. Una frase nueva reemplaza la cola y corta la anterior

**Qué se observó.** `PR-AVT-03`: se enviaron las 3 frases de FR-01-D mientras sonaba la
anterior. Las 3 llegaron al backend y sus oraciones salieron en orden (turnos 51→52→53), pero
**solo la última se representó entera**: la frase 1 se cortó a los 200 ms de haber arrancado
(iba por su 4.ª seña) y la 2 a los 891 ms (también por la 4.ª); ninguna llegó a
«representado:». La frase 3 terminó sus 4 señas en
7.654 ms. De 12 señas que arrancaron, 10 llegaron al final.

**Por qué ocurre (verificado en el código).** `setQueue()` **reemplaza** la cola:
`state.queue = items; state.queueIdx = 0` (`app/src/main/assets/web/app.js:1027-1038`). El
comentario de `requestTranslation()` lo dice a propósito: «`Signing` no bloquea... la escena
reemplaza la cola» (`MainScreenState.kt:1453-1456`). El spec local `STA-18` también lo declara
(`openspec/specs/ui/spec.md`).

**El conflicto de documentos.** No es solo el plan:

- El **plan** (`PR-AVT-03`) espera que «todas las oraciones se muestran en orden».
- El **SRS** `RF-AVT-06` dice «reproducir en orden las oraciones que lleguen una detrás de otra,
  sin solaparlas ni perder ninguna» (`main.tex:2567`).
- El **spec local** `STA-18` dice que una frase nueva **reemplaza** la cola, y el código lo
  hace.

Hay dos lecturas posibles: (a) `RF-AVT-06` habla de las oraciones **de una misma frase**, que
sí se encadenan (21/21 en `PR-SEG-02`), y el caso de varias frases no está especificado; (b)
`RF-AVT-06` cubre también varias frases y entonces el código no lo cumple. La ronda no resuelve
la ambigüedad.

**A quién afecta.** Al usuario (la segunda frase se corta sin aviso) y al avatar (no es su
falla: obedece la cola que recibe).

**Severidad.** Alta para la experiencia; media para la tesis, porque el requisito es ambiguo.

**Tipo.** Sistema y documentos (SRS/plan/spec local).

**Propuesta.** Decidir el producto antes de tocar código:

- **Encolar frases:** `setQueue` deja de reemplazar cuando ya hay una cola y se usa
  `appendQueue`; el avatar termina la frase anterior y sigue con la nueva.
- **Mantener el reemplazo:** corregir el plan y aclarar `RF-AVT-06` para que hable solo de
  oraciones de una misma frase; y agregar un aviso visible de que la frase anterior se
  interrumpió.
- **Híbrido:** reemplazar solo si la frase nueva llega después de X, o pedir confirmación.

Archivos: `app/src/main/assets/web/app.js:1027`, `MainScreenState.kt:1436-1460` y
`../sign-ai-docs/latex/srs/main.tex:2567`.

**Decisión del owner.** Sí, es la decisión de producto de esta ronda.

### S9. El aviso del permiso de micrófono no se ve

**Qué se observó.** `PR-AND-01`:

- Permiso denegado al abrir: la app queda usable (envió una frase por texto y se representó),
  pero **no muestra ningún aviso**. El único texto es el del log: «Sin permiso de micrófono la
  app no puede traducir voz.».
- Revocar el permiso con la app abierta **mata el proceso** (eso es del sistema) y al reabrir
  queda en «Inactivo» sin aviso.
- Tras dos negativas, MIUI deja el permiso en `USER_FIXED` y el sistema auto-deniega sin
  diálogo; la app tampoco dice nada.

**Por qué ocurre (verificado en el código).** `onMicPermissionDenied()` llama a `showStatus()`
(`MainScreenState.kt:2341-2345`), y `showStatus()` **retorna sin escribir nada si
`DEV_TOOLS` está apagado**:

```kotlin
// MainScreenState.kt:1626-1630
private fun showStatus(message: String, ms: Long = StatusOverrideMs) {
    Log.i(TAG, "aviso (no va al indicador): $message")
    if (!BuildConfig.DEV_TOOLS) return
    writeStatusOverride(message, ms)
}
```

Es una decisión del owner del 2026-10-17: el indicador muestra solo el nombre del estado
(`STA-14`). Pero el spec local `CTL-14` dice que «denegarlo no rompe nada: la app trabaja solo
con el texto, con el aviso de 3 s de `onMicPermissionDenied()`», y el SRS `RF-UI-04` exige
«informar al usuario si no puede funcionar sin él» (`main.tex:2085-2089`). El aviso no existe
en pantalla.

**A quién afecta.** Al usuario sordo, que no tiene forma de saber por qué el micrófono no hace
nada. Es el caso que el SRS describe explícitamente.

**Severidad.** Alta (requisito explícito).

**Tipo.** Sistema y documentos (decisión de UI vs `RF-UI-04`).

**Propuesta.** Hacer visible **solo este aviso**, sin volver a poner todos los de 3 s en el
indicador: un texto persistente en la fila del micrófono o en el `stateDescription` del
control, y mostrarlo al abrir si el permiso está denegado. Archivos:
`MainScreenState.kt:2341`, `MainScreen.kt` (arranque) y el componente del panel inferior.

**Decisión del owner.** Sí: confirmar que `RF-UI-04` manda sobre la decisión de 2026-10-17, o
cambiar el requisito.

### S10. El STT cae a CPU y `/health` sigue diciendo `cuda`

**Qué se observó.** `PR-STT-04`:

- `large-v3` **no entra** en la GPU de 6 GB: «CUDA failed with error out of memory». El
  servicio cae solo a CPU int8 y sigue transcribiendo, pero **`/health` sigue diciendo
  `device=cuda`**.
- La reserva fallida deja **3.026 MiB** tomados de la GPU.
- Medido con `small` (3,90 % WER, 120,7 ms/audio) y `medium` (1,77 %, 285,9 ms/audio).
  `PR-REN-05`: CPU int8 es **17,6×** más lento (5.116 ms contra 291 ms por audio).

**Por qué ocurre (verificado en el código).** `load_model()` atrapa la excepción de CUDA y
devuelve un modelo en CPU sin guardar en ningún lado que hubo fallback
(`../sign-ai-stt/app/transcriber.py:87-113`). `/health` devuelve `settings.DEVICE`, que sigue
siendo `cuda` (`../sign-ai-stt/app/main.py:35-43`).

**A quién afecta.** A quien opera el servicio: un `/health` que miente hace que un despliegue
lento se vea como normal. No afecta al usuario si el modelo entra (que es el caso de `medium`,
el que declara el SRS).

**Severidad.** Media.

**Tipo.** Sistema (observabilidad).

**Propuesta.** Guardar el dispositivo real en el estado y devolverlo en `/health`; agregar una
métrica `stt_device{cuda|cpu}` y un contador de fallback. Si se quiere evitar la memoria
tomada, cargar el modelo en un subproceso o liberar la reserva antes de reintentar (esto último
es diseño, no una línea). Archivos: `../sign-ai-stt/app/transcriber.py` y `main.py`.

**Decisión del owner.** No. `RNF-POR-04` ya pide documentar el funcionamiento sin GPU
(`main.tex:3001`); esto es la parte observable de esa documentación.

### S11. Dos importaciones se saltan la interfaz

**Qué se observó.** `PR-TRA-01`: de 5 importaciones entre componentes, **2 se saltan la
interfaz**:

- `nlp/llm_client.py` importa `validation/catalog_repository.normalize_gloss` (la función del
  módulo concreto, no `ValidatorService`).
- `controller/controller.py` importa `orchestrator.orchestrator.Orchestrator` (la clase
  concreta; el orquestador no tiene ABC).

**Por qué importa.** `RNF-MNT-02` exige que los componentes se comuniquen «únicamente a través
de interfaces internas bien definidas» y nombra `NLPService`, `ValidatorService` y
`SttServiceClient` (`main.tex:2946-2950`).

**A quién afecta.** Al backend, a la mantenibilidad. No al usuario.

**Severidad.** Baja.

**Tipo.** Sistema.

**Propuesta.** Mover `normalize_gloss` a un módulo compartido (por ejemplo, `app/domain/`), que
no sea «el repositorio del catálogo»; y agregar una ABC `Orchestrator` que `Controller` reciba.
Archivos: `../sign-ai-backend/app/nlp/llm_client.py`,
`../sign-ai-backend/app/controller/controller.py` y un módulo nuevo para la normalización.

**Decisión del owner.** No.

### S12. El catálogo acepta una glosa duplicada y la tapa en silencio

**Qué se observó.** `PR-VAL-02`: el catálogo tiene 50 glosas, 50 distintas, 50 `id_sign`
distintos, 0 rutas fuera del inventario y 0 consultas a la tabla al traducir. Pero **una glosa
repetida con otro `id_sign` se acepta**: no hay índice único sobre `gloss`, solo
`idx_catalog_gloss` no único.

**Por qué importa.** `load_all()` arma el diccionario en memoria con clave `normalize_gloss` y
`ORDER BY id_sign` (`../sign-ai-backend/app/validation/catalog_repository.py:56-77`). Si hay
dos filas con la misma glosa, la de `id_sign` mayor **pisa** a la otra sin avisar: la seña que
se representa puede ser la que no corresponde.

**A quién afecta.** Al backend y, en el caso malo, al usuario. No es un problema de la app.

**Severidad.** Baja hoy (el catálogo tiene 50 filas únicas); media si se edita a mano.

**Tipo.** Sistema / base de datos.

**Propuesta.** Índice único sobre la glosa normalizada (columna calculada o índice por
expresión) y una comprobación al arrancar que avise si hay colisiones. Archivo: migración de
`catalog` y `catalog_repository.py`.

**Decisión del owner.** Sí, si se cambia el esquema: `RNF-BD-02` solo pide la llave primaria
autoincremental (`main.tex:3023`), no la unicidad de la glosa. La ficha pide más que el SRS.

## 4. Defectos de las fichas y del conjunto de datos

Estos no son defectos del sistema. Se separan para que no se arreglen en el código por error.

### F1. `PR-CAC-05` mide el mantenimiento en caliente que el SRS no exige

La ficha dice: «Se borra y se corrige una entrada y se vuelve a traducir», y **no menciona el
reinicio**. El SRS `RNF-MNT-05` dice lo contrario: «el servidor mantiene la caché cargada en
memoria, así que la corrección se refleja **después de reiniciarlo**» (`main.tex:2971-2975`).
La prueba encontró exactamente eso: borrar o corregir por SQL no se ve hasta reiniciar; al
reiniciar, sí (`PR-CAC-05`).

**Conclusión.** Contra el SRS, el sistema cumple. La fila falla contra la ficha porque la ficha
no incluye el reinicio. **No es un defecto del sistema.** Hay que agregar el paso «reiniciar el
backend» al procedimiento o cambiar `RNF-MNT-05`. Decisión del owner.

### F2. `PR-INT-03` no se puede preparar: FR-11 supone señas sin animación que ya no existen

La ficha pide un catálogo al que se le quitaron a propósito nueve señas (ABUELO, ADIOS, BIEN,
BUENAS NOCHES, FELIZ, HAMBRE, MAS O MENOS, TIO, TU). Hoy **las 50 glosas tienen clip**; en la
prueba, las 6 frases dieron 18/18 glosas disponibles y las 12 corridas representaron todo lo
que recibieron. El interruptor `demo_sign_fallback` no cambió nada observable.

**Conclusión.** El conjunto de datos FR-11 está viejo. Hay que reescribir la ficha para que la
precondición sea alcanzable (por ejemplo, inyectar una glosa inexistente o retirar una fila de
`catalog` como en `PR-VAL-03`) o retirar la prueba. Decisión del owner y del plan.

### F3. `PR-AVT-05` sigue en TODO y los marcadores faciales ya están activos

La ficha no trae la tabla de lo esperado y pide para la negación un movimiento de cabeza que la
variante no aporta. Además, el juicio es visual y lo tiene que hacer alguien que sepa LSC.
Mientras tanto, el código **ya aplica variantes faciales** por tipo de oración:
`FACE_VARIANT_BY_TYPE` y `pickVariant()` en `app/src/main/assets/web/app.js:389-400` y
`853-860`; la sesión del 2026-11-04 las activó y las verificó con el mock y el backend real
(`openspec/sessions/2026-11-04-marcadores-faciales-medicion.md`).

Esto importa porque `PR-HUE-03` mide el tipo de oración y el tipo decide la cara: **35
preguntas de sí o no sin puntuación** caen en `AFFIRMATIVE` y usan la cara base en vez de la de
pregunta (`PR-HUE-03`). No es un defecto del avatar: es el tipo el que llega mal.

**Conclusión.** La ficha está incompleta y la validación LSC está pendiente; el código ya tomó
la delantera. Hay que escribir la tabla en `PR-AVT-05` (decisión del owner) y correrla con
intérpretes. Además, `AGENTS.md` todavía dice que se reproduce «únicamente el clip base»: esa
frase quedó vieja.

### F4. El método de `PR-AVT-06` sobrecuenta 5×

La ficha dice «se mide el tamaño de cada archivo». El método literal —sumar `bufferView` por
clip— **sobrecuenta 5 veces** porque las 300 animaciones comparten un `bufferView`. La medición
buena es por rangos de accesores: Juan ≈ 53 KB por seña, Pauline ≈ 87 KB. El APK debug pesa
38,94 MB y el release 33,88 MB (`PR-AVT-06`).

**Conclusión.** La ficha necesita el método correcto (rangos de accesores) o el número no se
puede reproducir. Decisión del owner del plan.

### F5. `PR-STT-02` describe un corte forzado que no ocurrió

La ficha espera que 004–006 corten «sin pausa donde cortar» y de forma forzada. En la corrida
cortaron **en una pausa** (`buffer_max_pausa`), no de forma forzada: 7 de 10 coinciden con la
forma exacta esperada. El texto no se pierde (WER 0,00 % contra el POST del mismo audio).

**Conclusión.** La ficha o el audio describen mal el caso. Queda la duda de si el umbral de
pausa (`CUT_PAUSE_MIN_MS = 150 ms`) es corto para una lectura natural; eso hay que medirlo con
una persona leyendo, no con el corpus actual. Decisión del owner del plan y del conjunto de
datos.

### F6. `PR-STT-03` no tiene requerimiento: la trazabilidad apunta a otro

La ficha se llama «Filtros de no voz» y su trazabilidad dice `RF-STT-02` (`main.tex:984`), pero
`RF-STT-02` es «Transcripción progresiva» (`main.tex:2410`). En el SRS **no hay un requisito
que diga que el STT no debe inventar texto cuando no hay voz**. El criterio de la ficha
(«Ningún audio debe producir texto») no está respaldado por un RF.

**Conclusión.** Es un hueco de la especificación, no solo de la prueba. Hay que agregar un
requisito (por ejemplo, `RF-STT-06`) o cambiar la trazabilidad y el criterio. Decisión del
owner.

### F7. `PR-VAL-01` y `PR-VAL-02` piden más que el SRS

- `PR-VAL-01` exige reconocer `FAMILIA.` y `ADIOS.`; `RF-CAT-04` enumera «mayúsculas, acentos,
  signos de interrogación y espacios de más» (`main.tex:2627`), **no el punto final**. Contra
  el SRS, el sistema cumple; contra la ficha, no. Aun así conviene aceptar el punto, porque el
  LLM lo emite (`PR-LLM-02`).
- `PR-VAL-02` exige que la base rechace una glosa repetida; `RNF-BD-02` solo pide llave primaria
  autoincremental (`main.tex:3023`). La ficha agrega una condición que el requisito no tiene.
  La condición es razonable (ver S12), pero hay que decidir si se vuelve requisito.

**Conclusión.** Alinear ficha y SRS antes de la ronda de evaluación, para que el estado de la
fila no dependa de cuál de los dos se lea.

### F8. La glosa `JUCIOSO` está mal escrita en el catálogo

La seña 30 se llama `JUCIOSO` en el catálogo y en `CT-03-076`/`CT-03-077`, mientras el español
del corpus dice «juicioso». El LLM devuelve `JUICIOSO`, que no existe, y el sistema la marca no
disponible. Es una de las 7 glosas que no salieron dentro de una frase en `PR-AVT-02`.

**Conclusión.** Defecto del conjunto de datos. Hay que corregir la glosa (y el catálogo) o
agregar un alias. Decisión del owner.

### F9. Los umbrales de las pruebas de caracterización siguen sin fijar

`PR-SEG-01`, `PR-HUE-03`, `PR-CAC-03`, `PR-REN-03`, `PR-REN-04`, `PR-AVT-05` y los
requerimientos `RNF-DES-01`, `RNF-DES-05`, `RNF-DES-07` y `RNF-DES-09` dicen «por fijar» o
TODO. Esta ronda ya dio los números para fijarlos; lo que falta es la decisión. Sin eso, la
ronda de evaluación no tiene contra qué comparar. Es la deuda más grande del plan.

## 5. Hallazgos que no hacen fallar una fila, pero importan

### H1. «Texto más grande» no escala el indicador de estado

`PR-AND-06` midió que con «Texto más grande» prendido el texto «Traducir» crece de 173×49 a
199×57 px, pero el indicador del escenario se queda en **144×46**. La causa está en el código:
`AvatarStage.kt:168` usa `12.sp * StageHintScale` absoluto, no el token de tipografía que escala
con el ajuste. El sistema sí escala (la letra 1.3 del sistema lo agranda: `PR-AND-05` mide
188×60), pero el ajuste propio de la app no. `RF-INT-USR-03` pide que «los elementos y los
textos cambian de tamaño» (`main.tex:2165`); el indicador es un texto y no cambia. Archivo:
`app/src/main/java/com/signai/webview/ui/screens/stage/AvatarStage.kt:168`.

### H2. «Contraste aumentado» solo cambia el aro

`PR-AND-06` midió que el contraste del borde del campo sube de 1,32:1 a 2,82:1 en oscuro y de
1,21:1 a 3,53:1 en claro, porque el ajuste reemplaza `outline` por `#777777`
(`Theme.kt:56`). El color del texto no cambia. `RF-INT-USR-03` dice «los colores aumentan su
contraste en el tema claro y en el oscuro»; el ajuste mejora las líneas, no todos los colores.
No falla la ficha (que mide el borde), pero es una implementación parcial. Decisión de diseño.

### H3. El tipo de oración decide la cara y se equivoca seguido

`PR-HUE-03`: 65,5 % de acierto total; **84 % con puntuación y 47 % sin puntuación**; borde
19 %. Los errores que más importan para la cara: 35 preguntas de sí o no sin «?» caen en
`AFFIRMATIVE`, 3 con «?» caen en `CONTENT_QUESTION`, y 18 órdenes caen en `AFFIRMATIVE` porque
el verbo no está en la lista de 20 imperativos. Con la tabla facial activa (F3), esas frases
usan la cara base. No hay una fila que falle por esto (`PR-HUE-03` es «Se reporta»), pero el
efecto es visible. Hay que medirlo con `PR-AVT-05` y decidir si se amplía la lista de
imperativos y la señal de pregunta.

### H4. El segmentador baja a 55 % sin puntuación, que es el caso de la voz

`PR-SEG-01`: 80,5 % exacto en total, pero **55,0 % en `02_sin_puntuacion`**, 24 casos que
sub-segmentan y 16 que sobre-segmentan. La voz del STT no siempre trae puntuación. Como el
segmentador decide las oraciones que se traducen y se cachean, el 55 % no es solo una métrica:
es la puerta de entrada del camino de voz. No hay fila fallida (es caracterización), pero
conviene mejorar la regla de cierre sin puntuación antes de la evaluación.

### H5. La retención de 1,5 s se come lo que se teclea

`PR-AND-02` anotó la trampa: si se empieza a teclear antes de que venza la retención de
sentText, la app borra lo tecleado. El código explica el mecanismo: durante `sentTextLocked` el
campo está `readOnly` pero `enabled` (`SignAiBottomBar.kt:216-218`); si el teclado ya estaba
abierto o se toca el campo, las teclas no entran y al vencer la retención el campo se vacía. No
es una falla de la ficha, pero es una pérdida de texto del usuario. Propuesta: dejar el campo
`enabled = false` mientras está retenido (se atenúa, pero no invita a escribir) o acortar la
retención.

### H6. Los propios ajustes son poco accesibles

La lectura del árbol de accesibilidad de la ronda anotó que el número del stepper está oculto a
propósito (`clearAndSetSemantics`, `SignAiStepper.kt:93`), que el disparador del selector de
avatar no expone cuál está elegido y que los interruptores no exponen `checked`. **Ojo:** el
código sí usa `Modifier.toggleable(value = checked, role = Role.Switch)` (`Switch.kt:74-83`) y
`Modifier.selectable(selected = selected)` en el menú del avatar
(`CharacterPicker.kt:115-116`), que en Compose deberían exponer el estado. La lectura del árbol
pudo ser parcial. **No concluyente:** hay que repetir la lectura con TalkBack o con un dump de
uiautomator antes de tocar nada. Lo del stepper sí es claro: el valor no se anuncia.

### H7. `PR-SEC-02` pasa, pero seis mensajes inválidos quedan mudos

13/13 mensajes inválidos no tumban el servicio y la siguiente frase válida funciona. Pero **6
mensajes** (4 de texto y 2 binarios que no son audio) se ignoran **sin respuesta y sin cerrar
la sesión**. Además, el de 50.000 caracteres termina en «No se pudo traducir el texto.» tras
7,35 s. No es una caída y el criterio se cumple; es una mejora de protocolo: responder un frame
de error también a los mensajes que hoy se ignoran.

### H8. Dos métricas de rendimiento no miden lo que dicen

- `PR-REN-03`: «envío→última seña» es una **suma** (`envío→última oración` + `representación`)
  y no una medida directa; la app no registra el solape entre la llegada de las oraciones
  siguientes y la representación, así que la suma sobreestima.
- `PR-INT-06`: «envío→primera seña» dejó **38 muestras de 60**; la métrica se emite cuando
  arranca la primera seña del turno y algunos turnos no la registraron.

No invalidan los resultados, pero los números de latencia extremo a extremo hay que leerlos
como cotas, no como medidas exactas. Propuesta: instrumentar el instante de llegada de cada
oración y el fin real de la última seña en `SignAiMetrics`.

### H9. Una caída del backend a mitad de un turno pierde el resto de la frase sin devolver el texto

`PR-RES-07` (fila pendiente al escribir; evidencia `RES/PR-RES-07_D_20261104_01.csv`): si el
backend muere **después** de que salió la primera oración de una frase de dos, la segunda
nunca llega. La app detecta el
silencio a los ~15 s, muestra «Error» («El servidor no respondió.») y vuelve a «Inactivo», pero
el texto **no vuelve al campo**: ya se había liberado a los 1,5 s del envío. La reconexión sí
funciona (1.030 ms) y la frase siguiente se traduce normal, así que el criterio de `PR-RES-07`
se cumple; lo que se pierde es la parte de la frase que no llegó. Es una mejora de resiliencia:
conservar la frase en curso y poder reintentarla, o al menos avisar que se cortó. Archivo:
`MainScreenState.kt` (manejo de `onDisconnected` y del reloj de silencio del turno).

## 6. Lo que no se puede concluir y qué medir después

| # | Pregunta abierta | Por qué no se puede concluir | Qué medir |
|---|---|---|---|
| N1 | ¿La precisión de la traducción con el modelo oficial llega al umbral? | Todas las pruebas de LLM usan `qwen3:4b-instruct` local, no `gpt-4o-mini` | Repetir PR-LLM-01/03 e INT-02 con el modelo oficial y el mismo corpus |
| N2 | ¿Las omisiones vienen del modelo o del reparador? | La ronda guardó las glosas finales, no `response.content` | Loguear la respuesta cruda y el diff antes/después de `_repair_glosses` en 30 frases |
| N3 | ¿La caché puede reconocer sinónimos con otro embedding? | PR-CAC-01 está bloqueada: el modelo está fijo en `embedding_model.py` | Hacer configurable el modelo y correr CAC-01 con 2–3 alternativas |
| N4 | ¿Las variantes faciales se leen bien en LSC? | PR-AVT-05 está bloqueada y la ficha está en TODO | Escribir la tabla y correr con intérpretes de LSC |
| N5 | ¿Cuánto tarda de verdad la última seña? | La métrica de REN-03 es una suma, no una medida con solape | Instrumentar la llegada de cada oración y el fin de la última seña |
| N6 | ¿El jank de REN-07 es del avatar o del arranque? | `gfxinfo` acumula toda la vida de la app; el framestats solo guarda 120 cuadros | Medir por fases: reposo, carga y representación, con el mismo build |
| N7 | ¿La memoria del backend bajó por una fuga o por el ciclo de vida? | 1.057 GiB → 527 MiB en INT-06; no se midió el detalle | Medir RSS por hora con y sin carga, y por proceso/contenedor |
| N8 | ¿Los interruptores exponen `checked` a TalkBack? | La lectura del árbol y el código no coinciden | Repetir con TalkBack y con `uiautomator dump` |
| N9 | ¿El enlace backend→LLM está cifrado en la configuración oficial? | La corrida usó Ollama local por HTTP; SEC-01 no cubre `gpt-4o-mini` | Repetir SEC-01 con el proveedor oficial |

## 7. Qué bloquea el cierre de la tesis y qué es mejora

### 7.1. Bloquea el cierre

1. **La precisión de la traducción no tiene umbral ni modelo oficial medido (S2 + F9).**
   `RNF-DES-07` sigue en TODO y 14–15/30 no se puede defender sin decir contra qué se compara.
   Hay que medir con `gpt-4o-mini` y fijar el umbral.
2. **La evaluación con personas está sin correr (PER-01 a PER-06).** Es la única evidencia de
   `RNF-DES-07`, `RNF-DES-08`, usabilidad (SUS) y naturalidad. No la reemplaza ninguna prueba
   automática.
3. **La cola del avatar choca con el SRS o con el producto (S8).** Hay que decidir si se encola
   o se mantiene el reemplazo y se corrige el documento.
4. **La caché tiene un falso positivo de sentido (S6) y no reconoce sinónimos (S7).** El umbral
   de 0,05 y la afirmación de «comparación por significado» necesitan una decisión con números.
5. **La validación facial está pendiente (F3).** El código ya aplica variantes; sin `PR-AVT-05`
   no hay evidencia de que la cara sea la correcta.
6. **Las fichas que piden más o menos que el SRS (F1, F2, F5, F6, F7).** Si la ronda de
   evaluación usa las fichas actuales, va a medir cosas que el SRS no exige o no va a poder
   preparar `PR-INT-03`.
7. **La caída del STT deja sin texto (S5).** Es una contradicción directa con `RNF-AV-03`; si
   el jurado apaga el STT, la app no puede ni traducir lo escrito.

### 7.2. No bloquea el cierre: es mejora o riesgo de demo

- **S4 (tos y carraspeo):** no cambia el documento, pero en una demo con tos o ruido se
  representan señas de «COUGH». Es el riesgo de demo más visible.
- **S9 (aviso del permiso):** requisito explícito, pero la app sigue usable con texto.
- **S1 (punto final):** la ruta existe, pero en las 30 frases reales no se disparó.
- **S3 (texto no glosa en pantalla):** conviene arreglarlo; no cambia la representación.
- **S10 (fallback a CPU):** afecta al despliegue, no al resultado con `medium`.
- **S11 (importaciones), S12 (duplicado):** deuda de mantenibilidad y de datos.
- **H1–H9:** accesibilidad, métricas, segmentación sin puntuación y robustez del protocolo.

## 8. Contraste con los documentos de la tesis

Esta tabla no cambia ningún documento. Cada fila es una divergencia que el owner tiene que
resolver: si se corrige el código, el requisito o la ficha.

| Requisito | Qué dice | Qué hace el código | Evidencia | Veredicto |
|---|---|---|---|---|
| `RF-AVT-06` (`main.tex:2567`) | Reproducir en orden sin solapar ni perder ninguna | Una frase nueva reemplaza la cola (`app.js:1027`) | PR-AVT-03 | **No cumple** si el requisito cubre varias frases; ambiguo |
| `RF-UI-04` (`main.tex:2085`) | Avisar si no hay permiso de micrófono | El aviso va al log, no a pantalla (`showStatus`, `MainScreenState.kt:1626`) | PR-AND-01 | **No cumple** |
| `RNF-AV-03` (`main.tex:2757`) | El servidor sigue operativo si el STT no responde | Cierra la sesión del cliente si no abre la del STT (`router.py:224-240`) | PR-RES-03 | **No cumple** |
| `RF-NLP-06` (`main.tex:2498`) | Dos formas de decir lo mismo recuperan la misma traducción | Sinónimos 1/10 con el umbral actual | PR-CAC-03 | **No cumple** con el umbral/modelo actual |
| `RF-CAT-04` (`main.tex:2627`) | Tolerar mayúsculas, acentos, signos de interrogación y espacios | Sí, pero no el punto final | PR-VAL-01 | **Cumple el RF**; la ficha pide más |
| `RF-CAT-03` (`main.tex:2618`) | Marcar no disponible y avisar «Seña no disponible» | El indicador avisa, pero el texto crudo llega al panel de conversación | PR-LLM-05 | **Parcial** |
| `RNF-MNT-02` (`main.tex:2946`) | Componentes por interfaces | 2 de 5 importaciones se saltan la interfaz | PR-TRA-01 | **No cumple** |
| `RNF-MNT-05` (`main.tex:2971`) | La corrección de la caché se ve después de reiniciar | Exactamente eso | PR-CAC-05 | **Cumple**; la ficha mide mal |
| `RNF-BD-02` (`main.tex:3023`) | Llave primaria autoincremental | Sí; no hay unicidad de `gloss` | PR-VAL-02 | **Cumple el RF**; la ficha pide más |
| `RF-INT-USR-03` (`main.tex:2165`) | Los textos y elementos cambian de tamaño; el contraste sube | «Texto más grande» no escala el indicador; «contraste» solo el aro | PR-AND-06 | **Parcial** |
| `RF-AVT-03` (`main.tex:2540`) | Gestos faciales según el tipo de oración | Implementado con la tabla de 2026-11-04; sin validación LSC | F3 / `app.js:389` | **Implementado, no verificado** |
| `RF-STT-02` (`main.tex:2410`) | Transcripción progresiva | No tiene relación con «filtros de no voz», que es lo que traza PR-STT-03 | F6 | **Trazabilidad mal puesta** |
| `RNF-DES-07` (`main.tex:2715`) | Proporción mínima de frases correctas | 14–15/30 con el modelo local; umbral TODO | PR-LLM-01/03 | **Sin umbral** |
| `RF-INT-HW-05` / `RNF-POR-04` (`main.tex:2264`, `3001`) | GPU al arrancar; sin GPU sigue en CPU y se documenta | Cae a CPU y `/health` sigue diciendo `cuda` | PR-STT-04 | **Parcial** (funciona, no lo informa) |

Dos hallazgos de auditoría de los documentos:

- **`RNF-AV-03` está definido dos veces** en el SRS, con el mismo texto, en la misma sección
  «Disponibilidad» (`main.tex:2757` y `2892`). Hay que dejar una sola definición.
- **El SRS dice que el APK pesa 78,19 MB** en dos lugares (`main.tex:1067` y `1310`), mientras
  `PR-AVT-06` midió **38,94 MB** debug y **33,88 MB** release. Probablemente el SRS hable del
  tamaño instalado o descomprimido; la palabra «APK» es la que no coincide. Hay que aclararlo.

## 9. Mapa de las 74 filas

| Bloque | Total | Pasa | Falla | Se reporta | Bloqueada | Pendiente | Dónde se discute |
|---|---:|---:|---:|---:|---:|---:|---|
| STT | 5 | 1 | 1 | 3 | 0 | 0 | S4, F5, F6, S10 |
| SEG | 4 | 0 | 0 | 3 | 1 | 0 | H4 |
| HUE | 3 | 0 | 0 | 1 | 2 | 0 | H3, F3 |
| CAC | 6 | 0 | 2 | 3 | 1 | 0 | S6, S7, F1 |
| LLM | 5 | 1 | 2 | 2 | 0 | 0 | S1, S2, S3 |
| VAL | 3 | 1 | 2 | 0 | 0 | 0 | S1, S12, F7, F8 |
| INT | 6 | 2 | 1 | 1 | 2 | 0 | S2, S5, F2, H8 |
| AND | 6 | 4 | 1 | 1 | 0 | 0 | S9, H1, H2, H5, H6 |
| AVT | 7 | 3 | 1 | 1 | 2 | 0 | S8, F3, F4 |
| REN | 9 | 0 | 0 | 8 | 0 | 1 | H8, N5–N7 |
| RES | 7 | 2 | 0 | 0 | 1 | 4 | S5 |
| SEC | 4 | 1 | 0 | 0 | 1 | 2 | H7, N9 |
| TRA | 3 | 2 | 1 | 0 | 0 | 0 | S11 |
| PER | 6 | 0 | 0 | 0 | 6 | 0 | Bloqueo de cierre, §7.1 |

**Los conteos de RES, SEC y REN son del corte de 67 filas**; la columna «Pendiente» marca las
siete filas de §2.1 que todavía no tenían estado. En particular, RES-03 aún no tiene estado
oficial, aunque su evidencia ya está escrita.

**Lectura de la tabla.** Ningún bloque falla entero. Las fallas se concentran en CAC (2),
LLM (2), VAL (2), STT (1), INT (1), AND (1), AVT (1) y TRA (1). De esas 11, **tres filas no
señalan un defecto del sistema tal como está escrito el SRS** (`CAC-05`, `VAL-01` y
`VAL-02`) y **una más no es del avatar** (`INT-02`: el avatar representó todo lo que recibió).

### 9.1. Las 11 fallas del corte, una por una

| Fila | Qué falló | Sección | ¿Defecto del sistema? |
|---|---|---|---|
| PR-STT-03 | 2/8 audios sin voz producen texto | S4 | Sí |
| PR-CAC-04 | 1/30 trampas con falso acierto de negación | S6 | Sí (umbral) |
| PR-CAC-05 | El mantenimiento por SQL no se ve sin reiniciar | F1 | No: la ficha no incluye el reinicio que el SRS exige |
| PR-LLM-02 | 4/10 respuestas fabricadas se leen mal | S1 | Sí |
| PR-LLM-05 | Texto fuera del catálogo llega al usuario | S3 | Sí, además de una decisión de criterio |
| PR-VAL-01 | `FAMILIA.` y `ADIOS.` no se reconocen | S1 / F7 | Parcial: el RF no pide el punto; el LLM lo emite |
| PR-VAL-02 | Una glosa repetida con otro `id_sign` se acepta | S12 / F7 | Parcial: el RF no pide unicidad; el riesgo de sombra es real |
| PR-INT-02 | 99/112 glosas y 15/30 frases exactas | S2 | Sí, aguas arriba; el avatar cumplió |
| PR-AND-01 | No avisa la falta de permiso y el micrófono no se cierra solo | S9 / S4 | Sí, dos causas distintas |
| PR-AVT-03 | Solo la última de 3 frases se representa entera | S8 | Sí, y hay que decidir el producto |
| PR-TRA-01 | 2 de 5 importaciones se saltan la interfaz | S11 | Sí |

## 10. Fuentes y método

- **Resultados:** `docs/plan-pruebas/plantilla-resultados.xlsx` (columnas 12–17) y
  `docs/plan-pruebas/ANALISIS-2026-11-04.md` (corte de 67/74, 23:50).
- **Evidencias citadas:** `docs/plan-pruebas/evidencias/<BLOQUE>/`, en particular
  `PR-LLM-02_D_20261104_01.json`, `PR-LLM-05_D_20261104_01.json`,
  `PR-STT-03_D_20261104_01.json`, `PR-STT-05_D_20261104_01.csv`,
  `PR-CAC-02_D_20261104_01.json`, `PR-CAC-03_D_20261104_01.json`,
  `PR-CAC-04_D_20261104_01.json`, `PR-VAL-01_D_20261104_01.json`,
  `PR-INT-02_D_20261104_01.json`, `PR-AVT-02_D_20261104_01.json`,
  `PR-AVT-03_D_20261104_01.json`, `PR-AND-01_D_20261104_01.json`,
  `PR-AND-06_D_20261104_01.json`, `RES/PR-RES-03_D_20261104_01.csv` y
  `SEC/PR-SEC-02_D_20261104_01.json`.
- **Código verificado:** rutas y líneas citadas en cada sección; se leyó el código de la app,
  del backend y del STT en el estado del 2026-11-05.
- **Documentos:** `../sign-ai-docs/latex/srs/main.tex`, `sdd/main.tex` y
  `plan-pruebas/main.tex`; el borrador de datos de
  `../sign-ai-docs/borradores/plan-pruebas/02-datos/`.
- **Lo que no se verificó en el código:** el estado exacto del build instalado en el teléfono
  durante cada prueba (si incluía o no las variantes faciales), el contenido de la respuesta
  cruda del LLM y la causa de la caída de memoria del backend. Se indica como no concluyente.
