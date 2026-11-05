---
name: fuentes-de-verdad
description: Revisar las dos fuentes de verdad del proyecto —el código y ../sign-ai-docs, con los documentos formales en latex/— antes de decidir o implementar, y resolver qué hacer cuando no coinciden. Usar cuando una decisión técnica tenga que contrastarse contra los documentos, cuando el código y un documento no digan lo mismo, cuando haya que decidir si se adapta el código al documento o al revés, o cuando el owner pida una auditoría de conformidad.
---

# Las dos fuentes de verdad

Decisión del owner del **2026-10-17**, actualizada el **2026-11-04**.

1. **Este repo** — el código.
2. **`../sign-ai-docs`** — los documentos: los formales en `latex/` (SRS, SDD, memoria, plan de
   pruebas, plan de administración) y los borradores en `borradores/`, con los cambios
   pendientes en `cambios/`.

Hasta el 2026-11-04 eran **tres**, con el **Overleaf de la tesis** como tercera y como la más
importante para el owner. Ese día el owner la retiró —«el Overleaf ya no sirve de nada, ya no
es una fuente confiable»— y los documentos formales pasaron a vivir en el repositorio. **No
hay una tercera fuente: si algo existe solo en Overleaf, no cuenta como entregado.** El MCP de
Overleaf ya no está en la configuración global.

## El flujo

1. **Antes de decidir o de implementar**, leer qué dicen los documentos.
2. **Al implementar o al revisar**, comprobar que lo que hace el código está **alineado**
   con esos documentos.
3. **Si no están alineados, no seguir por cuenta propia.** Primero determinar cuál de los
   dos está mal —el **documento** o el **código**— y ahí preguntarle al owner.

Una divergencia entre el código y un documento es **un hallazgo que se reporta**, no un
detalle que se tapa.

## Cuál de los dos está mal: se puede determinar

Casi siempre sí, y hay una forma de hacerlo sin adivinar:

| Señal | Apunta a |
|---|---|
| El documento tiene una fecha **posterior** al cambio, y menciona el comportamiento nuevo | El código está atrasado |
| El documento tiene fecha **anterior** y describe algo que el código ya no hace | El documento quedó atrás |
| El código tiene un ID de spec que lo respalda y una fecha de sesión que lo explica | El documento quedó atrás |
| `../sign-ai-docs/cambios/requerimientos.md` lo tiene como **Propuesto** o **Acordado** | La decisión ya está tomada: hay que aplicar lo acordado |

Ese último es el más útil: **`cambios/requerimientos.md` es la bandeja de cambios
pendientes**, con estados Propuesto / Acordado / Hecho. Ahí caen las cosas que la app ya
hace y el documento todavía no dice.

**Si tras esa búsqueda sigue sin estar claro cuál está mal: preguntar.** No elegir por
inercia.

## Cuando dos documentos se contradicen entre ellos

**No hay ganador automático.** Esto es distinto del caso anterior, y es explícito: una
sesión **reporta la contradicción y pregunta**. No elige una fuente, no promedia las dos
y no deja que ninguna gane por costumbre.

Pasa, por ejemplo, entre el SRS y los requerimientos consolidados, o entre el plan de pruebas
y el SRS. Se citan **las dos versiones con su fecha y su archivo**: un «el SRS dice X» sin
fecha no se puede discutir.

## Citas: fuente y fecha, siempre

Cuando se cita una fuente, se dice **cuál y de qué fecha**: `latex/sdd/main.tex` (copia del
2026-10-27) dice X. Y de qué commit, cuando importa. Es una regla del `AGENTS.md` y es lo que
hace discutible una cifra o una afirmación.

## Cómo se lee un documento

Los documentos formales son `.tex`: se leen con el archivo abierto y, cuando hace falta ver
el resultado, se compilan con `make` (ver el skill `documentos-tesis`). Una afirmación sobre
«lo que dice el documento» se sostiene con **archivo y línea**, igual que una del código.

## El resultado

Una divergencia se cierra de una de estas tres formas, y **siempre se le dice al owner
cuál**:

- **Se adapta el código al documento.** (Lo normal cuando el documento manda en el SDD.)
- **Se anota que el documento quedó atrás**, con la evidencia de cuándo y por qué.
- **Se pregunta**, cuando no se puede determinar cuál de los dos está mal.

**Cuál de los dos está mal lo decide el owner.** Después de esa decisión, la sesión puede
escribir el cambio en el `.tex`, compilarlo y anotarlo en `../sign-ai-docs/cambios/`, pero el
texto que resuelve una divergencia de contenido no se pone por cuenta propia: se propone.

## Una advertencia sobre los documentos de este repo

**No todos los specs están al día.** `openspec/specs/pipeline/spec.md` y
`openspec/config.yaml` describen el pipeline viejo de landmarks y `sign.json`, que ya
no existe, y se contradicen con el código. **Contrastalos contra el `AGENTS.md` y contra
`app.js` antes de creerles.** Cuando el código y un spec de acá no coincidan, el código
suele tener razón y el spec estar viejo — pero **eso también se reporta**, no se corrige
en silencio.

Un ejemplo de la clase de error que hay que evitar: `openspec/config.yaml` afirma que los
huesos del avatar **no** usan puntos (`DEF-upper_armL`), cuando las exportaciones de
Cascadeur **sí** (`upper_arm.L`, `thumb.01.L`). Un agente que le creyera renombraría
huesos y rompería la animación sin ver un error.

## Dónde queda el rastro

Cerrada la decisión, cargar el skill `sesion-openspec`: la bitácora lleva la decisión, su
fecha y la evidencia.
