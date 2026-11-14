# Propuesta para subir la exactitud del glosado

**Día:** jueves 5 de noviembre de 2026.
**Origen:** cierre de la ronda de evaluación (`ea4cfbf`), fallo `PR-INT-02` (17/30 frases
exactas, 88,4 % de glosas).

- **Objetivo:** saber **por qué** falla el 43 % de las frases antes de proponer un arreglo,
  en vez de asumir que hace falta otro modelo o un motor nuevo.
- **Cómo:** se reprodujeron las 13 frases que fallan con el modelo oficial, el prompt de
  producción y el catálogo de 50 glosas, pidiendo la respuesta **antes** del recorte, con
  `docs/plan-pruebas/sonda-glosado.py` dentro del contenedor del backend.
- **Resultado:** el hallazgo cambia la lectura del número y está escrito, con las tres
  propuestas y sus costos, en `docs/historia/plan-pruebas/PROPUESTA-MEJORA-GLOSADO-2026-11-05.md`.

## Lo que se midió

Doce de las trece frases salieron idénticas a la ronda; `FR-01-010` varió un poco, que es lo
esperable incluso con temperatura 0. **Solo dos de las trece tienen un error de criterio del
modelo** (`010` dijo `YO` donde la referencia espera `TU`; `015` perdió el `QUIEN`). Las otras
once son de **forma**: plural (`BIENVENIDOS` por `BIENVENIDO`), sin Ñ (`NINOS`), femenino
(`JUICIOSA` por `JUCIOSO`), verbo conjugado (`ESTAR`, `ESTAS` no están en el catálogo),
pronombre implícito y sinónimos (`MUY` por `MUCHO`, `CON PERMISO` por `PERMISO`).

La causa está localizada: `normalize_gloss` pliega mayúsculas, acentos y puntuación, pero **no
flexión**; cuando el modelo escribe otra forma, `_repair_glosses` la descarta. La seña existe
y la animación es la misma (`NIÑO` y `NIÑOS` comparten clip): el sistema falla al escribir, no
al representar.

## Las tres propuestas

1. **Capa de vocabulario** (1–2 semanas): flexión, pronombre y tabla de equivalencias después
   del modelo. Efecto estimado 17 → 26–28 de 30, con el riesgo de romper frases que hoy pasan,
   así que se mide sobre las 30 y se cuentan regresiones.
2. **Motor de reglas lingüísticas** (1–2 meses): lematizador y reordenamiento real; sube del
   87 % al 93–95 % y los últimos puntos cuestan diez veces más.
3. **Motor propio** (meses): sintaxis del español y gramática de la LSC; es otra tesis. No se
   recomienda.

## Caso aparte: `JUCIOSO`

Se verificó el PDF original (`data/LSC50.pdf`, tablas 1 y 2): **el dataset dice `JUCIOSO`** y
lo traduce como «JUCIOS». La errata viene de la fuente, no la introdujo el proyecto; en español
lo correcto es `JUICIOSO`. La decisión D8 («corregir previa confirmación del listado») ya tiene
su confirmación, y la decisión real es respetar la etiqueta del dataset o corregirla apartándose
de ella. Queda para el owner y no bloquea la propuesta 1.

## Lo que quedó

- `docs/historia/plan-pruebas/PROPUESTA-MEJORA-GLOSADO-2026-11-05.md` — el análisis completo, las tres
  propuestas, las decisiones abiertas y cómo reportarlo en la tesis.
- `docs/plan-pruebas/sonda-glosado.py` y `sonda-glosado.log` — la medición reproducible.
- **Sin código tocado, sin cambio de contrato, sin commits en los hermanos.** La propuesta 1
  se analiza en otra sesión.

## Decisiones abiertas

1. El `TU` implícito: la referencia de `FR-01` lo espera y el prompt dice lo contrario.
2. `FR-01-024`: la referencia espera `NIÑA` cruzando el límite entre oraciones.
3. Si la tabla de equivalencias es parte del pipeline (cambia qué se afirma del «motor de NLP»).
4. `JUCIOSO` / `JUICIOSO`.
5. Dónde vive la tabla: constante en el código o columna del catálogo.
