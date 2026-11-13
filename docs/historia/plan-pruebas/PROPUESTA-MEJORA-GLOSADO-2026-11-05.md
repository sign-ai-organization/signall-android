# Propuesta para subir la exactitud del glosado

**Fecha:** 2026-11-05
**Origen:** ronda de evaluación (cierre `5b8c28d`), pruebas `PR-INT-02` y `PR-LLM-02`.
**Estado:** propuesta. No hay código escrito ni decisión tomada.

---

## Resumen en una frase

El 57 % de frases exactas no mide cuánto español entiende el modelo: mide cuántas
frases terminan escritas **exactamente** como el catálogo tiene la palabra. De las
13 frases que fallan, **11 conservan el significado** y se pierden por forma, no por
comprensión; por eso hay margen para subir el número sin cambiar el modelo ni la
arquitectura.

---

## 1. Qué mide realmente el 57 %

El criterio de `PR-INT-02` compara la lista de glosas que devuelve el sistema contra
la lista de glosas del corpus `FR-01`, glosa por glosa y en orden. Una frase cuenta
como exacta solo si **todas** sus glosas coinciden, en la misma forma y en el mismo
orden.

Eso mide una cosa muy estricta: la coincidencia literal con la forma canónica de un
vocabulario cerrado de 50 señas.

El catálogo no acepta variantes. `normalize_gloss`
(`app/validation/catalog_repository.py`) solo pliega mayúsculas, acentos y
puntuación; **no** pliega plurales, femeninos ni conjugaciones. Cuando el modelo
escribe `BIENVENIDOS` y el catálogo tiene `BIENVENIDO`, la glosa no resuelve y
`_repair_glosses` (`app/nlp/llm_client.py`) la descarta. La seña existe; lo que no
coincide es el texto.

Y ahí está el punto importante para la tesis: **la seña de `NIÑO` y la de `NIÑOS` es
la misma animación**. El sistema no falló al representar; falló al escribir, y una
comparación textual lo castigó.

Métrica complementaria ya medida: **99 glosas de 112 (88,4 %)**. Es decir, el sistema
produce casi nueve de cada diez glosas esperadas. El 57 % castiga el conjunto entero
por la diferencia de una palabra.

---

## 2. La medición: qué escribió el modelo, en crudo

Se reprodujeron las 13 frases que fallan con el **mismo modelo oficial**
(`gpt-4o-mini`), el **mismo prompt** de producción y el **mismo catálogo de 50
glosas**, pidiendo la respuesta antes del recorte contra el catálogo. Doce de las
trece salieron idénticas a la ronda; una (`FR-01-010`) varió un poco, que es lo
esperable incluso con temperatura 0.

Reproducción: ver §9. Resultado guardado en `sonda-glosado.log` junto a este archivo.

| Frase | El modelo escribió (crudo) | El catálogo tiene | Diagnóstico |
|---|---|---|---|
| `FR-01-001` | `HOLA, BUENOS DIAS, BIENVENIDOS` | `HOLA, BUENOS DIAS, BIENVENIDO` | plural |
| `FR-01-006` | `GRACIAS, TODOS, NINOS, NINAS, COMER, GUSTO` | `GRACIAS, TODOS, NIÑO, NIÑA, COMER, CON GUSTO` | plural, sin Ñ, frase partida |
| `FR-01-008` | `ESTAR, BIEN` | `TU, BIEN` | verbo conjugado; falta el pronombre |
| `FR-01-010` | `HERMANO, TRISTE, YO, SENTIR, MAL` | `TU, HERMANO, TRISTE, SENTIR, MAL` | **pronombre equivocado** (`YO` por `TU`) |
| `FR-01-011` | `PERDON, ESTAR, TRISTE` | `PERDON, TU, TRISTE` | verbo conjugado; falta el pronombre |
| `FR-01-012` | `HERMANO, ABURRIDO` | `TU, HERMANO, ABURRIDO` | se comió el posesivo |
| `FR-01-013` | `HOLA, COMO ESTAS, COMO, TU, LLAMAR` | `HOLA, COMO ESTAS, COMO, TU, NOMBRE` | expresión idiomática |
| `FR-01-015` | `MUJER, JUICIOSA, HOMBRE` | `MUJER, JUCIOSO, QUIEN, HOMBRE` | femenino; **perdió el interrogativo** |
| `FR-01-016` | `POR QUE, ESTAS, TRISTE` | `POR QUE, TU, TRISTE` | conjugado; falta el pronombre |
| `FR-01-024` | `SENTIR, MUY, BIEN, NIÑA, NUNCA, TRISTE` | `NIÑA, SENTIR, MUCHO, BIEN, NUNCA, TRISTE` | sinónimo **y orden** |
| `FR-01-026` | `CON PERMISO, DAME PASAR, POR FAVOR` | `PERMISO, POR FAVOR` | frase que no es glosa |
| `FR-01-027` | `NINOS, TRABAJAR, TODOS` | `NIÑO, TODOS, TRABAJAR` | plural y sin Ñ |
| `FR-01-029` | `HOLA, FAMILIA, SENTIR, BIENVENIDOS` | `HOLA, FAMILIA, BIENVENIDO` | plural; glosa inventada |

**Solo dos de las trece tienen un error de criterio real:** `FR-01-010` (dijo `YO`
donde la referencia espera `TU`) y `FR-01-015` (se comió el `QUIEN`). Las otras once
son de forma.

---

## 3. Los cuatro grupos

Los grupos se superponen: una misma frase puede tener dos causas.

| Grupo | Frases | Qué lo causa |
|---|---:|---|
| **Flexión** (plural, femenino, Ñ) | 5 | El catálogo tiene una forma y el modelo escribió otra |
| **Verbo conjugado o cópula** | 3 | `ESTAR`, `ESTAS` no están en el catálogo |
| **Pronombre `TU` implícito** | 4 | La frase lo implica pero no lo dice con una palabra |
| **Sinónimos y frases hechas** | 3 | `MUY`→`MUCHO`, `CON PERMISO`→`PERMISO`, `llamarse`→`NOMBRE` |

---

## 4. Qué NO es el problema

Conviene dejarlo escrito para no equivocar el arreglo:

- **No es falta de comprensión del español.** El modelo produjo el contenido
  correcto en 11 de las 13.
- **No es el modelo.** Cambiar a uno más grande no cambia el resultado: el propio
  `llm_client.py` documenta que dos modelos distintos fallan igual con el mismo
  prompt, y esta medición lo confirma. El problema es la interfaz entre lo que el
  modelo escribe y lo que el catálogo acepta.
- **No es el avatar.** El avatar representa el 98 % de las glosas que recibe, en
  orden. Lo que falta se pierde antes.
- **No es la caché.** La caché semántica falla por la misma razón que el modelo (1
  de 10 variantes con sinónimos), así que la misma tabla de equivalencias mejora
  las dos.

---

## 5. Las tres propuestas

| | Qué es | Costo | Efecto esperado | Riesgo |
|---|---|---|---|---|
| **1. Capa de vocabulario** | Reglas de forma y una tabla de equivalencias | 1–2 semanas | 17 → **26–28 de 30** (87–93 %) | Bajo, aditivo, medible con las 30 frases |
| **2. Motor de reglas lingüísticas** | Lematizador, etiquetado gramatical, reordenamiento real | 1–2 meses | 87 % → **93–95 %** | Medio: peso, latencia, lema equivocado |
| **3. Motor propio** | Sintaxis del español + gramática de la LSC desde cero | Meses | — | Alto: es otra tesis |

### Propuesta 1 — Capa de vocabulario (la recomendada)

No es un motor. Son tres reglas y una tabla, **después** del modelo y **antes** del
validador:

1. **Resolver flexión al buscar en el catálogo.** Si la glosa no coincide exacta,
   probar el singular, el femenino/masculino y la variante con Ñ. `NIÑOS` resuelve a
   `NIÑO`, `JUICIOSA` a `JUICIOSO`, `BIENVENIDOS` a `BIENVENIDO`.
2. **Poner el pronombre que la referencia espera.** Si el verbo está en segunda
   persona (`estás`, `tienes`, `comes`) o hay un posesivo (`tu hermano`), agregar
   `TU`. Si está en primera (`estoy`, `siento`), `YO`.
3. **Tabla de equivalencias.** `MUY`→`MUCHO`, `GUSTO`→`CON GUSTO`,
   `CON PERMISO`→`PERMISO`, `llamarse`→`NOMBRE`, y las que aparezcan al medir.

**Dónde se toca (para la próxima sesión, no ahora):**

- Un módulo nuevo, por ejemplo `app/nlp/gloss_lexicon.py`, con las reglas y la tabla.
- `app/nlp/llm_client.py::_repair_glosses`: aplicar la cadena de resolución antes de
  descartar una glosa.
- **Detalle técnico que hay que resolver primero:** las reglas 2 y 3 necesitan la
  **frase original**, y hoy `_repair_glosses` solo recibe la lista de glosas. Hay que
  pasarle el texto (cambio de firma) o hacer un paso previo en `transform`.
- **No tocar `normalize_gloss`.** Es la llave del catálogo y de la caché; agregarle
  flexión ahí puede fusionar glosas distintas. La resolución va en una capa aparte.

**Aritmética del efecto esperado** (en el papel, hay que medirla):

| Arreglo | Frases que se vuelven exactas | Total |
|---|---|---:|
| Flexión | `001`, `027`, `006` (con equivalencia) | 3 |
| Pronombre `TU` | `008`, `011`, `012`, `016` | 4 |
| Equivalencias | `013`, `026` | 2 |
| Reinsertar interrogativo | `015` (riesgoso: hay que ubicarlo en su lugar) | 1 |
| **Suma** | | **9–10** |

17 + 9 = **26 de 30 (87 %)**; con la reubicación del interrogativo, **27 (90 %)**.
Si además se corrige el pronombre de `FR-01-010`, **28 (93 %)**.

**Los dos casos que no son de forma** (los únicos donde el modelo no dijo lo correcto):

- **`FR-01-015` — completar, no adivinar.** El modelo se comió un `QUIEN` que la frase
  dice explícitamente. Regla: si la oración tiene una palabra interrogativa y la
  respuesta no la trae, se agrega al principio de esa oración. Como el segmentador ya
  trabaja oración por oración, la posición sale sola (`QUIEN, HOMBRE`). El dato está
  en el texto de entrada, así que no se inventa nada.
- **`FR-01-010` — borrar, que es más delicado.** El modelo puso `YO` donde la
  referencia espera `TU`. Regla posible: si hay posesivo `TU`, el sujeto es tercera
  persona y sobra un pronombre de primera. El riesgo es que la capa no puede
  distinguir un `YO` equivocado de uno correcto —`FR-01-019` («Yo nunca como mucho»)
  lo necesita y hoy pasa—, así que la regla tiene que mirar el sujeto, no la lista de
  glosas. Y antes hay una decisión de interpretación: **«¿Se siente mal?» es ambiguo**
  (usted o él); la referencia eligió `TU` y el modelo eligió `YO`. Hasta que el owner
  fije esa lectura, la regla no se escribe.

`FR-01-024` y `FR-01-029` no se arreglan con esta capa: una necesita reordenamiento
real, la otra tiene una glosa inventada (`SENTIR` por «siéntanse»).

**El riesgo que hay que medir, no suponer:** las reglas nuevas pueden **romper**
frases que hoy pasan (por ejemplo, agregar `TU` donde la referencia no lo espera).
Por eso la verificación no es "las 13 mejoraron", sino **las 30 corridas de nuevo,
con regresiones contadas**. Si una regla sube tres y baja dos, no sirve.

**Cómo se mide:** la misma corrida de `PR-INT-02` (30 frases por la app, comparación
contra `FR-01`), antes y después, con el mismo modelo y la misma caché. Y las
variantes `FR-03` de `PR-CAC-03`, que deberían mejorar por la misma tabla.

### Propuesta 2 — Motor de reglas lingüísticas

Lematizador de español (spaCy o similar), etiquetado gramatical y reordenamiento
tema-comentario. Sube del 87 % al 93–95 %, pero **los últimos cinco puntos cuestan
diez veces más que los primeros treinta**: agrega un modelo de decenas de MB, suma
latencia por frase, y un lema mal elegido puede mandar a la seña equivocada — peor
que no representar nada, porque el error es silencioso.

Solo tiene sentido si la tesis quiere defender el reordenamiento como aporte propio.
Hoy el reordenamiento ni siquiera se mide: `FR-01` espera un orden que el sistema
acierta en 28 de 30 por casualidad del modelo, no por una regla.

### Propuesta 3 — Motor propio

Sintaxis del español más gramática de la LSC (tema-comentario, clasificadores,
verbos espaciales, marcas no manuales) desde cero. Es un problema de investigación
abierto, no un capítulo de esta tesis. **No se recomienda.** El alcance de la tesis
es un pipeline que funciona de punta a punta, no un sistema nuevo de traducción.

---

## 6. Caso aparte: `JUCIOSO`

Queda anotado porque **condiciona la propuesta 1**: si el catálogo sigue diciendo
`JUCIOSO`, la regla de femenino tiene que mapear `JUICIOSA`→`JUCIOSO`, que es feo y
frágil.

Lo que dice la fuente original (`data/LSC50.pdf`, tablas 1 y 2):

- Tabla 1: `JUCIOSO — JUCIOS` (la traducción al inglés tampoco es una palabra).
- Tabla 2, seña `0029`: `JUCIOSO`.

O sea: **la errata está en el dataset, no la introdujo el proyecto**; se copió tal
cual. En español lo correcto es `JUICIOSO`.

Decisión D8 del plan de recuperación: «se corrige en todos lados, previa confirmación
del listado original». La confirmación ya está hecha y el listado original **dice
`JUCIOSO`**. Entonces la decisión real no es «¿cuál es la palabra correcta?» sino:

- **(a) Respetar el dataset** y dejar `JUCIOSO`, documentando que es la etiqueta
  original. Conserva la trazabilidad con LSC50.
- **(b) Corregir a `JUICIOSO`** en catálogo, clip y referencias. Queda bien en
  español, pero se aparta de la etiqueta del dataset y hay que decirlo.

En cualquiera de las dos, la capa de la propuesta 1 puede aceptar las dos formas como
alias, así que **no bloquea el trabajo**. Es decisión del owner.

---

## 7. Decisiones que no se pueden tomar por cuenta propia

1. **El `TU` implícito.** La referencia de `FR-01` espera `TU` en «¿Estás bien?»,
   pero el prompt actual dice literalmente «no agregues pronombres que la frase no
   dice». **El prompt y la referencia se contradicen.** Hay que decidir cuál manda
   antes de escribir la regla.
2. **`FR-01-024`.** La referencia espera `NIÑA` primero, cruzando el límite entre las
   dos oraciones. Puede ser un defecto de la referencia y no del sistema.
3. **La tabla de equivalencias como parte del pipeline.** Cambia qué se puede afirmar
   sobre el «motor de NLP» en el capítulo de diseño.
4. **`JUCIOSO` / `JUICIOSO`** (§6).
5. **Dónde vive la tabla.** ¿Constante en el código o columna del catálogo? Una
   columna escala; una constante es más simple y no toca la base. Choca con D-7 solo
   si se mete en el prompt, y esta propuesta **no** lo hace.

---

## 8. Cómo se reporta en la tesis

No presentar el 57 % solo. Presentar los dos niveles y la descomposición:

> La exactitud estricta contra la forma canónica del catálogo es del 57 % (17/30), y
> la exactitud a nivel de glosa es del 88,4 % (99/112). Las 13 diferencias se
> concentran en flexión, pronombre implícito y sinónimos: el contenido se conserva y
> la forma escrita no coincide. Dos de las trece son errores de criterio del modelo.

Eso explica el número en vez de esconderlo, y deja el lugar para mostrar la mejora
medida después de la propuesta 1, con la misma vara y las mismas 30 frases.

---

## 9. Cómo reproducir la medición

El script `sonda-glosado.py` (junto a este archivo) corre **dentro del contenedor del
backend**, con la configuración de producción y sin tocar la caché:

```bash
docker cp docs/plan-pruebas/sonda-glosado.py sign-ai-backend-backend-1:/tmp/
docker exec -i sign-ai-backend-backend-1 python /tmp/sonda-glosado.py
```

Imprime, por cada frase: lo que escribió el modelo en crudo, lo que sobrevive al
recorte, y qué glosa se pierde. Son 13 llamadas al modelo; el costo es despreciable.

---

## Anexo: las 13 diferencias, en una línea cada una

1. `001` — plural `BIENVENIDOS` por `BIENVENIDO`.
2. `006` — `NINOS`, `NINAS` (sin Ñ y en plural) y `GUSTO` por `CON GUSTO`.
3. `008` — `ESTAR` fuera del catálogo y sin `TU`.
4. `010` — `YO` donde la referencia espera `TU`.
5. `011` — `ESTAR` fuera del catálogo y sin `TU`.
6. `012` — se perdió el posesivo `TU`.
7. `013` — `LLAMAR` por `NOMBRE`.
8. `015` — `JUICIOSA` por `JUCIOSO`, y se perdió `QUIEN`.
9. `016` — `ESTAS` conjugado y sin `TU`.
10. `024` — `MUY` por `MUCHO`, y `NIÑA` fuera de lugar.
11. `026` — `CON PERMISO` y `DAME PASAR` no son glosas del catálogo.
12. `027` — `NINOS` por `NIÑO`.
13. `029` — `BIENVENIDOS` por `BIENVENIDO`, más un `SENTIR` inventado.
