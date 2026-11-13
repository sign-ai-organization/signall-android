---
name: sesion-openspec
description: Escribir el log de sesión en openspec/sessions/ y mantener el índice al día. Usar al terminar un trabajo no trivial, cuando hay que registrar qué se hizo, qué se decidió y qué quedó pendiente, cuando hay que dejar rastro de un cambio en más de un repo, o cuando hay que actualizar el índice de openspec/sessions/README.md.
---

# La bitácora de sesión

`openspec/sessions/` es la memoria del proyecto. **Mantenerla actualizada es tan importante
como el código**: es lo que permite que una sesión que no estuvo acá retome sin inventar.

## Cuándo hay que escribir

- **Al terminar cada trabajo no trivial.** No trivial = toca código, docs de la app,
  contrato, o una decisión.
- **Al tocar un repo hermano también**, en ese repo. Una entrada acá y otra allá.
- **Cuando el dato venga de afuera**, dejar **de qué repo y de qué commit** salió. Sin
  eso la próxima sesión no puede reproducirlo.

Un fix chico o una lectura no necesitan entrada. Una decisión, un cambio de comportamiento
o una medición **sí**.

## Convención

- **Un archivo por sesión**: `openspec/sessions/YYYY-MM-DD-<slug>.md`. El slug es corto y
  en minúsculas, con guiones.
- **Si ya existe una sesión de hoy, se extiende ese archivo.** No se crea otro.
- Empieza con **título, día, fecha y hora**, y la rama.
- **Corto y factual**: solo lo que un futuro agente necesitaría para retomar.
- **Cada número con su origen.** "95–114 ms medido en el Redmi" o "p50 sobre las 28 frases
  reproducidas por el socket". Un número sin origen no se puede discutir.

## Plantilla

```markdown
# <Título en una línea>

**Fecha:** 2026-10-17, <mañana|tarde|noche> · **Rama:** `flujo-oraciones`

## Qué llegó

<El pedido, o el hallazgo que abrió el trabajo.>

## Qué se hizo

<Los cambios, en orden. Con `archivo:línea` donde sirva para que otro lo encuentre.>

## Qué se decidió

<Cada decisión, con **por qué**. Si la decidió el owner, decilo.>

## Qué se midió

<Los números, cada uno con su origen: cuántos fixtures, en qué device, en qué build.>

## Qué quedó pendiente

<Cada punto, con el repo al que pertenece si es en un hermano.>
```

## Al terminar: el índice

**Toda entrada nueva va en el índice de `openspec/sessions/README.md`.** El índice es lo
que hace que la bitácora se pueda consultar; una entrada sin indexar es medio archivo
perdido.

- Una línea por sesión, con el slug entre backticks, una raya, y **qué pasó** en una
  línea. Si hace falta más, va en la línea siguiente con sangría.
- Si la sesión actualiza una entrada vieja, **no** agregues una nueva: corrige la existente.

Al agregar una entrada, verificá que el índice y los archivos sigan coincidiendo: los
mismos archivos listados, y ninguno listado que no exista.

## Terminología

Estas reglas son del `AGENTS.md` y aplican acá igual:

- **`representar`**, nunca "firmar" ni "signar". Es el término de la tesis.
- **El español neutro es para hablarle al owner**, en el mensaje. En los archivos de este
  repo la convención es la que ya está: las sesiones están escritas en español
  rioplatense. **Mantené el estilo del archivo que estás tocando.**
- **Los números con su origen.**

## Reglas de git que aplican al dejar rastro

- **Acá se commitea directo a `main`**, sin PR ni rama de feature, y sin pedir
  confirmación.
- **En los hermanos, nada de commits sin que el owner lo pida.** Si esta sesión dejó
  entrada en `../signall-backend` o `../signall-stt`, eso se le dice al owner y lo manda
  él.