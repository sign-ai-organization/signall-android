---
name: escritura-tesis
description: Escribir y revisar los documentos de la tesis (SRS, SDD, memoria, plan de pruebas, plan de administración y VFP) con la voz del proyecto, no con la de un modelo. Usar al redactar o corregir un .tex, al revisar un párrafo antes de compilar, o cuando el owner pida que un texto "no suene a IA".
---

# Escribir los documentos con la voz del proyecto

Estas reglas salen de una revisión del Plan de Administración del 2026-11-18 y de las
decisiones del owner. No son un manual de estilo académico general: son la voz de SignAll.
El objetivo es que un párrafo se pueda leer en voz alta sin tropezar y que no delate que lo
escribió una máquina.

La prueba de una frase son cuatro preguntas:

1. ¿Quién hace la acción?
2. ¿Cuándo pasa: siempre, ya pasó o todavía no?
3. ¿Cuántas ideas lleva?
4. ¿Qué palabra sobra?

Si alguna no tiene respuesta clara, la frase se reescribe.

## Cómo se revisa un documento

Una revisión se hace por secciones, nunca del documento completo de una vez. Un documento de
ochenta páginas no se corrige en un solo paso: se corrige una sección, se compila, se mira y se
sigue con la siguiente.

Se trabaja en un solo documento a la vez y solo entre los que están asignados para la sesión.
En `../signall-docs/revision/README.md` está el reparto: por ejemplo, una persona edita
`latex/administracion/` y `latex/sdd/`, y nada más. Los demás documentos se leen para
contrastar; no se abren para corregir.

1. Se lee la sección tal como está, sin proponer cambios todavía.
2. Se contrasta lo que afirma con los otros documentos y con el código (ver abajo).
3. Se listan los hallazgos con su número de línea y la regla que incumplen.
4. Se reescribe solo esa sección. Lo que ya está bien no se toca.
5. Se compila con el skill `documentos-tesis` y se revisa el PDF de esa sección.
6. Se anota el cambio en el registro que corresponda: `signall-docs/cambios` u
   `openspec/sessions`.
7. Se pasa a la siguiente sección.

- No se propone un reescrito global del documento en un solo mensaje. Si la sección es muy
  larga, se parte en subsecciones.
- Un hallazgo que cruza secciones se anota y se resuelve cuando toque esa otra sección; no se
  arregla a mitad de camino.
- Un hallazgo que pertenece a otro documento no se corrige en esta sesión: se anota en
  `revision/<nombre>.md` o en `signall-docs/cambios`, con archivo y línea, para quien edite ese
  documento. Si es una decisión que cruza varios, va a `openspec/sessions`.
- El documento se compila al cerrar cada sección, no al final.
- El cambio de un término en todo el documento (por ejemplo, uno que fije el glosario) se
  aplica en la sección que se está tocando. No se hace un barrido global antes de revisar.

### Contraste con los otros documentos y con el código

Una sección no se revisa sola. Lo que afirma sobre el sistema tiene que coincidir con el resto
de los documentos y con lo que el código hace. Esto vale sobre todo para lo técnico y lo
funcional: componentes, flujos, frames del WebSocket, métricas, nombres de archivo, cifras y
comportamiento de la aplicación.

Por cada sección:

1. Se identifican las afirmaciones verificables: qué componente hace qué, con qué nombre, en
   qué puerto, con qué métrica y con qué cifra.
2. Se busca cada una en los otros documentos de `../signall-docs/latex/` y se compara la
   versión de cada uno.
3. Si la afirmación describe algo implementado, se busca en el repositorio que lo implementa:
   este repo para la aplicación, `../signall-backend` para el servidor y el contrato,
   `../signall-stt` para la transcripción.
4. Se anota el resultado: coincide, o no coincide y con qué archivo y línea.

- No se contrasta cada palabra: se contrasta lo que el lector podría comprobar y lo que otra
  sección ya afirma.
- Una contradicción entre dos documentos no se resuelve por costumbre. Se citan las dos
  versiones con archivo y línea, se reporta y se pregunta cuál manda, como en el skill
  `fuentes-de-verdad`.
- Una diferencia entre el documento y el código tampoco se arregla por cuenta propia: puede
  que el documento esté atrás o que el código esté atrás. Se reporta y se pregunta.
- Si el hallazgo pertenece a otro documento o a otro repo, se registra en `revision/<nombre>.md`
  o en `signall-docs/cambios`, con archivo y línea, para la sesión de ese lado. Corregirlo aquí
  dejaría el resto desalineado en silencio.
- Una afirmación técnica que no se puede verificar se marca como pendiente; no se da por
  cierta porque suene bien.

## Voz y persona

Se escribe en impersonal con "se". El sujeto de una acción es el sistema, el equipo o el
documento, nunca un "nosotros" de blog.

| Sí | No |
|---|---|
| Se validaron las glosas con intérpretes de LSC. | Validamos las glosas con intérpretes. |
| Se eligió Scrum por el tamaño del equipo. | Nosotros elegimos Scrum. |
| El sistema traduce español a LSC. | Nuestro sistema traduce español a LSC. |
| El equipo decide los cambios menores por consenso. | Decidimos los cambios menores por consenso. |

- "El equipo" se usa solo cuando importa quién decide o quién responde. Si no importa, se
  usa "se".
- No se usa el voseo, ni "vosotros", ni el tuteo. Español neutro.
- No se usa "usted" salvo en una cita textual o en un texto institucional heredado.
- No se escribe "nuestro" para hablar del sistema ni del proyecto. "Nuestro prototipo" es
  marketing; "el prototipo" es el documento.

## Tiempos verbales

Una sola regla, con tres casos:

| Qué se cuenta | Tiempo | Ejemplo |
|---|---|---|
| Cómo es el sistema o qué exige una prueba | Presente | El sistema traduce solo de español hablado a LSC. |
| Lo que ya ocurrió | Pretérito | Las fases 1 a 3 se completaron antes del 10 de noviembre. |
| Lo que todavía no ocurrió | Futuro | El correo de entrega se enviará el 22 de noviembre. |

- No se usa el presente por futuro. "Las siete entregas se envían juntas" está mal si
  todavía no se enviaron: "se enviarán".
- No se cambia de tiempo dentro de un párrafo sin una razón. Si el párrafo mezcla plan y
  resultado, se parte en dos.
- El presente normativo no es una excusa para escribir en presente lo que ya pasó. Un
  resultado en presente se lee como un plan.

## Longitud y ritmo

- Una idea por frase.
- Promedio de 15 a 25 palabras. Techo de 35, salvo enumeraciones y definiciones.
- Un párrafo tiene de 3 a 6 frases y un solo tema.
- Después de una definición larga, va una frase corta. El ritmo se construye con el
  contraste, no con adornos.
- Si una frase tiene dos "que" encadenados, dos paréntesis o dos "mediante", se parte.
- La prueba física: si no se puede leer de un aire, se parte.

## Puntuación

| Signo | Regla |
|---|---|
| Coma | La normal. Nunca antes de la "y" final de una enumeración (coma de Oxford). |
| Punto y coma | Solo separa elementos largos de una lista o dos cláusulas muy unidas. No es adorno. |
| Dos puntos | Anuncian una lista o una consecuencia directa. No se usan para respirar. |
| Paréntesis | Aclaración breve o sigla. Máximo uno por párrafo. No meten una idea nueva. |
| Raya (—) | Prohibida en el cuerpo del texto. Solo se conserva en títulos de normas y nombres de archivo. |
| Comillas | «» en prosa; `` '' en LaTeX. Para términos o nombres, no para énfasis. |
| Guion de rango | En LaTeX se escribe `6.1--6.3`; no se cambia por raya. |

- La raya no reemplaza una coma ni un paréntesis. Si el inciso importa, va entre comas; si
  es secundario, entre paréntesis; si es otra idea, va en otra frase.
- No se usan puntos suspensivos para crear suspenso.
- No se abre una frase con "Y" ni con "Pero" en un documento formal.

## Léxico

### Anglicismos

La decisión es por término y ya está tomada. No se traduce lo que el equipo fijó en inglés, y
no se deja en inglés lo que tiene un equivalente que el documento ya usa.

**Se traducen.**

| Se escribe | No se escribe |
|---|---|
| flujo de procesamiento; flujo de NLP; flujo de traducción | pipeline |
| conjunto de datos; conjunto de datos LSC50 | dataset; dataset LSC50 |
| aplicación; "App Android" solo como etiqueta del carril del BPMN | app |
| de extremo a extremo | end-to-end |
| desplegar | deployar |
| prueba | test |

Las tres primeras filas rigen para los documentos de la tesis. Las tres últimas son reglas para
texto nuevo. El cambio de "pipeline", "dataset" y "app" se aplica en el documento asignado, en
la sección donde aparece, cuando esa sección se revisa. Si otro documento usa la forma vieja, se
anota en el archivo de cambios para la sesión de ese documento; no se edita desde aquí. No se
hace un barrido de los términos que se quedan.

**Se quedan en inglés.** No se traducen aunque exista una palabra española. La lista es una
decisión del owner, no una preferencia de estilo.

| Término | Cómo se escribe | Nota |
|---|---|---|
| backend | backend | Sin guion. "Servidor" se usa cuando importa el rol físico. |
| frontend | frontend | Nombre del rol y del lado de la aplicación. |
| prompt | prompt | "Prompt engineering" también se queda. |
| embeddings | embedding, embeddings | "Representación vectorial" solo en la definición del glosario. |
| software | software | Aceptado por la RAE; en redonda. |
| hardware | hardware | Aceptado por la RAE; en redonda. |
| sprint | sprint | Unidad de Scrum. "Iteración" es el ciclo, no el sprint. |
| proxy | proxy inverso | Primera vez "servidor proxy", si aporta. |
| token | token | Autenticación. |
| commit | commit | Término de Git. |
| streaming | streaming | "Flujo de streaming" y "canal de streaming". |
| gateway | gateway | Se queda tal cual. |
| release | release | "Compilación de release". |
| framework | framework | El título de RF-INT-SW-04 no cambia. |
| pool | pool | "Agrupación de conexiones (pool)" si hace falta aclarar. |

- `cache`, `chunk` y `debug` se quedan en el código. En prosa se escribe caché, fragmento de
  audio y depuración.
- Las siglas y los nombres propios no se traducen ni se ponen en cursiva: WebSocket, WSS, API,
  URL, JSON, HTTP, HTTPS, TLS, GLB, glTF, WebGL, WebView, FPS, Scrum, Kanban, Docker,
  PostgreSQL, Neon, Whisper, OpenAI, Three.js, Git, GitHub, Android, Kotlin, Compose, FastAPI,
  spaCy, LangChain, Ollama, C4, BPMN, PERT, RACI, WBS, SRS, SDD, VFP. Las siglas se expanden
  la primera vez; los nombres propios no.
- Los identificadores del contrato no se tocan: frames del WebSocket, métricas `rt_*`, columnas
  del catálogo y nombres de componentes del código se escriben igual en los dos lados.
- En las etiquetas de los diagramas se respeta la forma de la figura: Backend, App Android,
  Reverse Proxy, STT Container. En prosa se escribe backend, aplicación Android, proxy inverso
  y contenedor STT.
- Un término conservado se define una vez, en el glosario del documento, y se escribe igual en
  todos. Si otro documento usa otra forma, se anota como hallazgo; no se edita desde aquí.
  "backend" y "back-end" no pueden convivir.
- Un anglicismo que no esté en estas tablas no se traduce ni se deja por cuenta propia: se
  pregunta al owner con el término, la frase donde aparecería y una alternativa en español. La
  respuesta se agrega a la tabla, para no volver a preguntarla.

**Cursiva.** El extranjerismo no adaptado va en cursiva la primera vez que aparece en el
documento que se está revisando y en su entrada del glosario; después va en redonda. La cursiva
se aplica una vez en ese documento, no en cada aparición. Si la primera aparición cae en una
tabla, un título o una figura, la cursiva va en la primera aparición en prosa. "Software" y
"hardware" van en redonda siempre, y las siglas y los nombres propios también.

### Términos del dominio

| Término | Se escribe | No se escribe |
|---|---|---|
| Nombre del sistema | SignAll | Sign All, signall, SignAL |
| Lengua | Lengua de Señas Colombiana (LSC) | lenguaje de señas, lengua de signos |
| Unidad | seña | signo, firma, gesto |
| Salida del NLP | glosa (en mayúsculas y sin tildes) | traducción, palabra |
| Acción del avatar | el avatar representa las señas | el avatar firma, signa o traduce las señas |
| Acción del usuario | enviar una frase | mandar una frase |
| Acción del sistema | el sistema traduce español a LSC | el sistema interpreta (salvo "intérprete automático") |
| Producto | intérprete automático | traductor automático |
| Personas | persona sorda, usuario sordo, persona oyente | sordomudo, discapacitado auditivo, normoyente |
| Fuente | conjunto de datos LSC50 | dataset LSC50, corpus LSC50 |
| Selección | corpus de 50 señas | dataset de 50 señas |

- "Lengua" es el término de la tesis. "Lenguaje" solo vale para "lenguaje de programación"
  o "procesamiento de lenguaje natural" (NLP).
- No se inventa terminología. Si una palabra no está en `signall-docs`, es del owner hasta
  que la avale.

### Números y evidencia

- Toda cifra lleva su origen y su fecha o commit. Una cifra de fixture no es una cifra de
  persona.
- Se separa lo medido de lo estimado y de lo supuesto. Se escribe "medido", "estimado" o
  "supuesto", no "aproximadamente" para tapar un dato que falta.
- Decimal con coma: "0,04". Miles con punto: "555.000". Porcentaje con espacio: "78,0 %".
- Fechas completas: "22 de noviembre de 2026". Rangos en LaTeX: `13 jul -- 10 ago 2026`.
- Moneda: "USD 10", no "$10" ni "10 USD" mezclados en el mismo documento.
- Los identificadores van en `\texttt{}`: `RF-`, `RNF-`, `RD-`, `CU-`, `PR-`.

### Repetición

Los conectores son estructura y se repiten porque el texto los necesita. "Sin embargo",
"además" y "por eso" pueden aparecer decenas de veces en un documento largo y está bien: no
hay un cupo. El problema no es la cuenta, es el patrón.

| Señal | Qué hacer |
|---|---|
| El mismo conector dos veces en el mismo párrafo | Se cambia o se corta uno. |
| El mismo conector abre dos párrafos seguidos | Se cambia el arranque de uno. |
| Una perífrasis ("de forma continua", "a través de", "permite") hace la misma función varias veces en una sección | Se varía o se corta la mitad. |
| Todo el documento usa la misma fórmula para la misma función | Se revisa: eso es una firma, no un estilo. |

- Una palabra rara repetida tres veces en un párrafo es un problema. Un conector repetido cinco
  veces en cien páginas no lo es.
- La variedad no es un fin. Si la alternativa suena forzada, se deja el conector y se corta la
  frase.

## Lo que nunca se escribe

| No se escribe | Se escribe |
|---|---|
| "no solo X, sino también Y" | "X. También Y." o "X e Y." |
| "no es X, es Y" / "más que X, es Y" | "Es Y." Si el contraste importa: "No es X. Es Y." |
| "es importante destacar que" | Nada. Se dice el hecho. |
| "cabe mencionar", "cabe resaltar", "vale la pena señalar" | Nada. |
| "en el mundo de", "en la era de", "hoy en día" | "en [contexto concreto]" o nada. |
| "en este sentido" | "por eso" o nada. |
| "robusto", "innovador", "de vanguardia", "revolucionario" | Qué hace y con qué evidencia. |
| "juega un papel fundamental", "se erige como", "pieza clave" | "es", "sirve para". |
| "sinergia", "aprovechar al máximo" | La acción concreta. |
| "en conclusión", "en resumen", "en definitiva", "como se puede observar" | Nada. La última frase agrega, no repite. |
| "resulta fundamental", "resulta interesante" | "es" o nada. |
| "el hecho de que" | "que", o se reescribe la frase. |
| "a nivel de" | "en". |
| "en base a" | "con base en". |
| "el mismo X" | "X". |
| "realizar" o "llevar a cabo" | "hacer", "ejecutar", "medir", "validar". |
| "se debe" o "se puede" como relleno | El verbo directo. |
| "de forma continua", cuando es relleno | "durante el desarrollo", "en cada sprint", "mientras corre". |
| "a través de", cuando es relleno | "con", "por", "mediante" o el verbo directo. |
| "en lugar de", cuando es relleno | "y no", o dos frases. |
| "permite", cuando es relleno | "deja", "puede", "hace". |
| Raya (—) para un inciso | Coma, paréntesis o punto. |

Las cuatro perífrasis de la tabla ("de forma continua", "a través de", "en lugar de", "permite")
no son prohibiciones: son relleno cuando no dicen nada y se quedan cuando dicen algo. El resto
de la tabla no tiene condición.

- La lista se aplica a la frase, no a la idea. "El proyecto no sustituye la interpretación
  humana" es correcto. Lo que se prohíbe es la oposición de folleto: "no solo sustituye,
  sino que también...".
- Los conectores no tienen cupo. La repetición se revisa por patrón, no por conteo (ver
  "Repetición").
- Las enumeraciones de tres solo se quedan si cada elemento es real y verificable. "Rápido,
  seguro y escalable" se va si el documento no mide las tres cosas.

## Listas, tablas y enumeraciones

- Se usa lista cuando los ítems son cortos, paralelos y se escanean: requerimientos,
  pruebas, roles, riesgos.
- Se usa prosa cuando cada ítem lleva una explicación o un punto y coma. Una viñeta con
  "X; el sistema Y" es prosa disfrazada de lista.
- No hay listas de un solo ítem.
- La frase que introduce la lista termina en dos puntos solo si anuncia la lista. No se
  escribe "los siguientes:".
- Los ítems mantienen la misma forma: todos con verbo, o todos sin verbo. No se mezcla.
- Una tabla se anuncia en el texto antes de aparecer: "La Tabla~\ref{...} resume...".
- No se anidan listas más de dos niveles.

## Antes y después

Estos casos salen del Plan de Administración y son ejemplos de la regla, no cambios
aplicados al documento.

### Una apertura que no venda

**Antes.** "Este documento presenta el plan de administración del proyecto de grado
SignAll, cuyo propósito es desarrollar y validar un prototipo funcional de intérprete
automático que traduzca el español hablado hacia Lengua de Señas Colombiana (LSC) mediante
una arquitectura de inteligencia artificial y un avatar tridimensional."

**Después.** "Este documento describe la organización y el control del proyecto de grado
SignAll, que construye un prototipo de intérprete automático de español a LSC. El prototipo
cubre un corpus cerrado de 50 señas (LSC50) y se apoya en tres componentes: reconocimiento
de voz (STT), conversión de español a glosas de LSC y un avatar 3D que reproduce las
animaciones en una aplicación Android."

### Una oposición que no sea de folleto

**Antes.** "El proyecto no pretende sustituir la interpretación humana general, sino
explorar hasta qué punto un sistema automatizado puede apoyar la comunicación."

**Después.** "El proyecto no sustituye la interpretación humana. Su aporte es evidencia
técnica y lingüística sobre la viabilidad del enfoque."

### Una frase de 95 palabras

**Antes.** "La fase 6 (Cierre y entrega) comienza el 10 de noviembre de 2026, el mismo día
en que cierra la fase 4 y empieza la fase 5 (Evaluación Final), por lo que las dos se
solapan del 10 al 15 de noviembre: la consolidación de los documentos formales (sección 11)
avanza mientras se ejecuta la ronda de evaluación y se completa en los días posteriores a
su cierre, dado que el correo de entrega del Trabajo de Grado debe enviarse el domingo 22 de
noviembre de 2026 antes de las 8:00 p.m."

**Después.** "La fase 6 (Cierre y entrega) empieza el 10 de noviembre de 2026, el mismo día
en que cierran la fase 4 y arranca la fase 5 (Evaluación Final). Las fases 5 y 6 se solapan
del 10 al 15 de noviembre. Durante ese lapso avanza la consolidación de los documentos
formales (sección 11) y se ejecuta la ronda de evaluación. El correo de entrega del Trabajo
de Grado debe enviarse el domingo 22 de noviembre antes de las 8:00 p.m., así que la
consolidación termina en los días posteriores al cierre de la ronda."

### Una lista que debía ser prosa

**Antes.** Cuatro viñetas, cada una con punto y coma: "El reconocimiento de señas o la
traducción en sentido inverso; el sistema traduce únicamente de español hablado a LSC."

**Después.** "Quedan fuera del alcance el reconocimiento de señas y la traducción inversa,
el vocabulario abierto, el uso del espacio y la concordancia gramatical en las animaciones,
y el despliegue sobre un servidor dedicado. El sistema traduce solo de español hablado a
LSC, con las 50 señas del catálogo, y el servidor corre sobre equipos personales del equipo."

### Un cierre que no repite

**Antes.** "No se incluyen anexos adicionales en esta versión del documento: la información
necesaria para entender el proyecto está contenida en las secciones previas, y los
diagramas y tablas de calendarización están en el lugar del documento que les corresponde
(secciones 8 y 9)."

**Después.** "Esta versión no incluye anexos; las tablas y los diagramas están en las
secciones 8 y 9."

## Antes de dar un párrafo por terminado

1. Se lee el párrafo en voz alta. Si se queda sin aire, se parte la frase.
2. Se cuentan las palabras de cada oración. Más de 35, se parte.
3. Se busca la lista negra en el `.tex`:

   ```bash
   grep -n -i -E "no solo|sino también|es importante|cabe (destacar|mencionar|resaltar)|vale la pena|en el mundo|en la era|hoy en día|en este sentido|en conclusión|en resumen|en definitiva|como se puede observar|resulta (fundamental|interesante)|a nivel de|en base a|el mismo" main.tex
   ```

4. Se revisa la repetición: ningún conector dos veces en el mismo párrafo, ningún arranque
   repetido en dos párrafos seguidos.
5. Se verifica la persona (impersonal con "se") y el tiempo (presente, pasado o futuro).
6. Se verifican los términos de la tabla, los anglicismos contra las dos listas y las cifras
   con su origen.
7. Solo después de esto se compila con el skill `documentos-tesis`, al cerrar la sección.

## Excepciones acordadas

- El objetivo general del Plan de Administración (sección 6.2) no se reescribe sin
  aprobación del equipo. La observación sobre "lenguaje español" vive en
  `../signall-docs/revision/diego.md`.
- Las citas textuales, los títulos de normas y los nombres de archivo conservan su
  ortografía, incluidas las rayas de los títulos ISO.
- Las tablas, los identificadores y los datos no se tocan por estilo.
- El texto institucional heredado (portadas, plantillas) se respeta.
- Una corrección de redacción se aplica. Un cambio de contenido se pregunta primero, con el
  skill `fuentes-de-verdad`.

## Qué no hace este skill

- No decide si el documento o el código tiene razón. Eso es `fuentes-de-verdad`.
- No edita más de un documento a la vez: solo los asignados, y de a uno. Lo demás se propone o
  se anota.
- No corrige el código ni los otros documentos: el contraste produce hallazgos que se
  reportan.
- No revisa un documento completo en un solo paso: se va por secciones.
- No cambia cifras, requisitos, alcance ni fechas.
- No borra archivos, borradores ni `.md`: si algo sobra, lo decide el owner, no esta skill.
- No reescribe de más: si alcanza con una frase, no se toca la sección completa.
- No edita ni commitea `signall-docs` sin que el owner lo pida.
