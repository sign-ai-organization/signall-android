# Sesión 2026-10-16 — Latencia: dónde medir y qué cambiar

**Fecha:** 2026-10-16 · **Rama:** `flujo-oraciones` · **Alcance:** investigación de
latencia del sistema completo. **Sin cambios de código todavía**: esto son
decisiones y el plan que sale de ellas.

## Por qué arranca esta sesión

El 15 de octubre se cerró el flujo por oraciones en los tres repos. El 16 se
investigó cuánto tarda hoy el sistema de punta a punta y **dónde no hay
medición**. Salieron dos cosas: un bug de bloqueo en el backend, y una
contradicción entre el SDD y el spec de la app.

## Decisiones tomadas

### D-1 — El SDD manda: la app firma cada oración apenas llega

**Decisión del owner (2026-10-16): el SDD manda.** Ante la contradicción entre
el SDD y el spec de la app, se sigue el SDD. La app deja de esperar a la frase
completa.

**El conflicto, textual:**

| Documento | Dice |
|---|---|
| `sign-ai-docs/diagramas/sdd-6.3:61` | "Reproduce cada oración en cuanto llega" |
| `sign-ai-docs/diagramas/sdd-7.4:42` | `Procesando` → `Reproduciendo`, etiqueta "primer segmento recibido" |
| `sign-ai-docs/plan-pruebas/00-bases/alcance.md:66-67` | "Cada oración traducida se envía a la aplicación apenas está lista, y el avatar la reproduce" |
| `sign-ai-backend/docs/android-integration.md:172-174` | "No hay que esperar a las demás: el avatar puede empezar a señar la primera mientras se traducen las siguientes" |
| `sign-ai-android/openspec/specs/ui/spec.md:268` (GLS-1) | "solo arma la cola cuando la frase está completa, para no ejecutar media frase" |
| `sign-ai-android/openspec/specs/ui/spec.md:300` (BRG-14) | "la app arma la cola entera y usa `setQueue` + `play()`" |

Cuatro documentos contra dos líneas. Las dos del spec se escribieron el
2026-10-15, en este mismo trabajo.

**Por qué la razón de GLS-1 no aplica:** "para no ejecutar media frase" venía del
camino por fragmentos, donde el backend mandaba palabras sueltas a mitad de
frase. Con oraciones, un `segment` **es** una oración completa. No hay media
frase que ejecutar.

**Esto no es la opción B** de `auditoria-flujo-2026-10-15.md` §7. La opción A
dice textualmente "el avatar firma oración por oración" y "primera seña: al
terminar la primera oración". Esperar a la segunda para arrancar era lo que no
estaba en el SDD.

**Qué hay que cambiar y en dónde:**

| Repo | Cambio |
|---|---|
| `sign-ai-android` | `flushSegments()` arma la cola con lo que haya e ignora `total`; corregir GLS-1 y BRG-14 |
| `sign-ai-android` | `SignAiCommands`/`app.js`: `setQueue` debe poder anexar, no solo reemplazar |
| `sign-ai-docs` | `modificaciones/requerimientos.md`: el envío oración-por-oración pasa de *Propuesto* a *Acordado* (ya está en `trazabilidad.md:164` como vacío) |
| `sign-ai-docs` | Actualizar `GLS-11`/`STA-15` si el texto alluded a esperar la frase completa |

**Consecuencia que hay que medir después:** con la primera oración entrando sola,
la profundidad de cola baja. `CATCHUP_BACKLOG = 2` cuenta lo que falta *después*
de la seña en curso, así que con una frase de 2 oraciones ya no se alcanza. El
adelanto automático puede dejar de dispararse. No se toca todavía: primero se
mide (el log `cola N/M, adelanto on/off` ya existe).

## Decisiones pendientes

Se van tomando en el orden en que aparecen los hallazgos. Quedan anotadas acá a
medida que se resuelvan.

### P-1 — Cómo sacar la traducción del bucle de lectura sin desordenar frases

`sign-ai-backend/app/controller/router.py:76` hace
`await controller.handle_text(payload)` dentro del `while True` que lee audio.
Medido con `tools/verificar-bloqueo-lectura.py`: traducción de 1091 ms → **991 ms
de la voz del usuario invisible para el STT**, que entra de golpe al terminar.

La restricción documentada está en `sign-ai-backend/docs/performance.md:621-623`:
*"garantizar que los resultados se envíen al cliente en el orden de las frases
(una frase posterior que sale del caché no puede llegar antes que una anterior
que fue al LLM)"*.

Opciones sobre la mesa: (a) las traducciones se encolan, una a la vez, pero el
audio sigue fluyendo; (b) se traducen en paralelo con un buffer de
reordenamiento. **Pendiente de decisión del owner.**

**Nota de ciclo de vida:** `docs/sessions/2026-10-15-vuelta-al-flujo-por-oraciones.md:105-113`
advierte que tocar el cierre de sesión es decisión del owner, porque hoy
`sessions_active` queda en 1 y el `finally` no corre hasta que el STT contesta.