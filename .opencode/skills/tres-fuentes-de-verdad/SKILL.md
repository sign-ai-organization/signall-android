---
name: tres-fuentes-de-verdad
description: Revisar las tres fuentes de verdad del proyecto —el código, ../sign-ai-docs y el Overleaf de la tesis— antes de decidir o implementar, y resolver qué hacer cuando no coinciden. Usar cuando una decisión técnica tenga que contrastarse contra los documentos, cuando el código y un documento no digan lo mismo, cuando haya que decidir si se adapta el código al documento o al revés, o cuando el owner pida una auditoría de conformidad.
---

# Las tres fuentes de verdad

Decisión del owner del **2026-10-17**. Hay **tres** fuentes que hay que mirar antes de
decidir o de implementar, y **ninguna se resuelve por cuenta propia**:

1. **Este repo** — el código.
2. **`../sign-ai-docs`** — requerimientos consolidados, diagramas del SDD, plan de
   pruebas.
3. **El Overleaf de la tesis** — SRS, Memoria, SDD, PMP, PPP.

El owner considera el Overleaf **más importante** que este repo.

## El flujo

1. **Antes de decidir o de implementar**, leer qué dicen los documentos de las tres.
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
| `../sign-ai-docs/modificaciones/requerimientos.md` lo tiene como **Propuesto** o **Acordado** | La decisión ya está tomada: hay que aplicar lo acordado |

Ese último es el más útil: **`modificaciones/requerimientos.md` es la bandeja de cambios
pendientes**, con estados Propuesto / Acordado / Hecho. Ahí caen las cosas que la app ya
hace y el documento todavía no dice.

**Si tras esa búsqueda sigue sin estar claro cuál está mal: preguntar.** No elegir por
inercia.

## Cuando `sign-ai-docs` y Overleaf se contradigan entre ellos

**No hay ganador automático.** Esto es distinto del caso anterior, y es explícito: una
sesión **reporta la contradicción y pregunta**. No elige una fuente, no promedia las dos
y no deja que ninguna gane por costumbre.

Se citan **las dos versiones con su fecha**. Un "el SRS dice X" sin fecha no se puede
discutir.

## Citas: fuente y fecha, siempre

Cuando citemos una fuente, **decimos cuál y de qué fecha**. Es una regla del `AGENTS.md` y
es lo que hace discutible una cifra o una afirmación.

## Overleaf: aviso antes de leer

La revisión de Overleaf tiene un costo en tiempo: cuando la cookie venció (~5 días) el MCP
**abre una ventana de Chromium con perfil aislado** para que el owner se loguee.

**Avisale al owner que vas a leer, antes de hacerlo.** No se hace por sorpresa a mitad de
una implementación.

De Overleaf se **lee, nunca se escribe**. El detalle del bloqueo, y por qué tiene que
estar en `policies` y no en `permissions`, está en el skill `overleaf-lectura`.

## El resultado

Una divergencia se cierra de una de estas tres formas, y **siempre se le dice al owner
cuál**:

- **Se adapta el código al documento.** (Lo normal cuando el documento manda en el SDD.)
- **Se anota que el documento quedó atrás**, con la evidencia de cuándo y por qué.
- **Se pregunta**, cuando no se puede determinar cuál de los dos está mal.

Cuando el documento que está mal es uno de Overleaf, **lo corrige el owner y no la
sesión**: el `.tex` no se toca. Lo que sí puede hacer una sesión es anotarlo en
`../sign-ai-docs/modificaciones/requerimientos.md` o en el log de sesión.

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

Cerrada la decisión, cargá el skill `sesion-openspec`: la bitácora lleva la decisión, su
fecha y la evidencia.