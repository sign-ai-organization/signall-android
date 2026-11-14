# A13, A3, A5, A7 + A4 + A1 — del contraste al SRS

**Fecha:** 2026-10-17, tarde y noche · **Rama:** `flujo-oraciones` · **Alcance:** solo
este repo. Overleaf no se tocó: es de solo lectura, y lo que de ahí hay que cambiar se
deja escrito acá para que lo pegue el owner.

## Qué llegó

El owner pidió seguir con los ítems que quedaban de
`docs/historia/CONTRASTE-APPvsDOCUMENTOS.md` **de menor a mayor complejidad**, anotando las
diferencias que aparecieran en ese mismo documento y marcando los estados. El orden
que se siguió: A2 → A13 → A3 → A5 → **A4 + A7 + A1**.

Lo de A2 está en `2026-10-17-a2-permiso-microfono-al-abrir.md`. Acá está el resto.

## La herramienta de verificación

El backend real (`../signall-backend`) necesita base de datos y clave de LLM, así
que no arrancó. Para poder **medir** en vez de suponer se escribió un backend falso
en `/tmp/opencode/fake-backend.py`, **fuera del repo**, que habla lo justo del
protocolo de `/ws/translate` y decide qué hacer con una palabra clave en el texto:

| En el texto | Qué hace |
|---|---|
| (nada) | Dos oraciones con glosas que sí tenemos |
| `SIN-SENA` | Una oración con una glosa cuya animación no está exportada (0035) |
| `SIN-RESPUESTA` | No responde nunca |
| `ERROR` | Manda un frame `error` |
| `CAER` | Cierra la sesión sin mandar nada |
| `CORTE` | Manda la primera oración y **después** corta la sesión |

Y responde `transcript` (parcial con el primer chunk de audio, `final` con el `end`),
que es lo mínimo para poder probar el dictado sin el STT.

**Un detalle del entorno que costó tiempo y conviene no volver a tropezar:** con
`adb reverse`, **matar el backend no propaga la caída**. El túnel deja la conexión
medio abierta y OkHttp —que no tiene timeout de lectura, a propósito— no se entera
nunca. Por eso las pruebas de desconexión se hicieron **pidiéndole al backend que
cierre la sesión** (`ws.transport.abort()`), no matándolo. Y la red local tampoco
servía: el teléfono no alcanzaba a `192.168.78.102:8080`.

## Qué se hizo, por ítem

| ID | Qué | Commit |
|---|---|---|
| **A13** | La métrica de "tiempo hasta la primera seña" (`SignAiMetrics.onFirstSign`, `turnSentAt`) | `817f89f` |
| **A3** | `wss://` por default, `ws://` solo como bypass de debug, sin texto en claro en release | `603065c` |
| **A5** | "Seña no disponible" glosa por glosa | `e0b008c` |
| **A7 + A4 + A1** | Los siete estados del SRS, la desconexión que no deja la app trabada, y el tercer estado visible | este commit |

El detalle de cada uno está en `docs/historia/CONTRASTE-APPvsDOCUMENTOS.md` §8, con lo que se
midió en el Redmi Note 9S y lo que no se pudo probar.

## El indicador, después de la segunda vuelta

El owner vio los textos que la app se inventaba y decidió el 2026-10-17 (tarde):

- **En el indicador va el nombre del estado.** Los siete de la tabla, tal cual:
  Inactivo, Grabando, Texto listo, Procesando, Reproduciendo, Error, Desconectado.
  La tabla `tab:estados-ui` no prescribe textos literales —su columna es "qué
  muestra la pantalla" y describe—, y el único mensaje literal de toda la tabla es
  ``Seña no disponible``.
- **"Seña no disponible" es el texto alternativo de *Reproduciendo***: sale
  mientras el avatar representa, en vez de "Reproduciendo", cuando alguna palabra
  no tiene seña.
- **Las glosas y los avisos de 3 s salieron del indicador.** Quedan en el log
  (`SignAiState`) y en pantalla solo con la depuración de Ajustes → Avanzado
  prendida.
- **El indicador creció un 25 %** y **el color del punto dice el estado**: azul
  para el camino normal, ámbar para lo que espera, rojo para lo que falla. Tres
  colores, no siete, y los tres son tokens que ya existían.

Y de paso, dos bugs que aparecieron al probar: una frase **sin ninguna seña
representable** dejaba la app en "Procesando" para siempre, y una frase cuya
**primera oración** era vacía y la segunda no, no se representaba entera.

## Decisiones del owner que cambiaron el trabajo

1. **A1 sale con A7, no con un ícono nuevo.** El SRS solo pide que "el indicador pase
   a *Texto listo*", y el indicador de la app es la etiqueta del escenario. Además ya
   había una decisión tomada en el dispositivo: el ícono del micrófono **no** cambia en
   ese estado porque dejaba de leerse como micrófono.
2. **La desconexión corta el micrófono**, con el aviso, en vez de dejarlo prendido
   grabando hacia un socket muerto. **Esto obliga a cambiar el SRS** — ver abajo.

## Qué hay que cambiar en el SRS

**El owner lo pidió explícitamente: al final hay que darle el texto exacto.** Son dos
puntos, y ninguno es un error del documento: son requisitos que la app cumple distinto
y el documento tiene que reflejarlo. Los números de línea son del `main.tex` del
2026-10-17.

### 1. `main.tex:709` — el micrófono en modo TR

Hoy dice que en modo TR el micrófono **permanece encendido**. Eso es cierto mientras
hay conexión. Hay que agregar la excepción, en el mismo párrafo.

> **Texto para reemplazar el que hoy está en `main.tex:709`:**
>
> En modo TR el micrófono permanece encendido entre frases, mientras la conexión con
> el servidor esté disponible. Si la conexión se pierde, la captura se interrumpe y la
> aplicación lo informa, para que el usuario pueda volver a encender el micrófono: el
> audio no se envía a ninguna parte mientras no hay sesión, y mantener el micrófono
> abierto en ese estado daría la impresión de que se está tomando mientras no se
> representa nada.

### 2. La fila *Desconectado* de la tabla de estados (`main.tex:761-772`)

El texto que el owner cerró el 2026-10-17 no menciona el micrófono, y la app ahora lo
apaga. Hay que agregar una frase al final.

> **Texto para agregar al final de la fila *Desconectado*:**
>
> Si la conexión se pierde con el micrófono encendido, la captura se interrumpe y el
> estado del micrófono pasa a apagado, junto con un aviso que informa que se está
> reconectando. El usuario debe volver a encenderlo manualmente; mientras la conexión
> no se restablezca no hay a dónde enviar el audio.

Y el punto 1 es una referencia cruzada del punto 2, así que **conviene pegarlos
juntos**: si solo se arregla el 2, el 709 sigue contradiciéndolo.

### Lo que **no** hay que cambiar

- La fila *Desconectado* ya dice, y la app ya cumple, la espera creciente hasta 5 s,
  el **sin límite de intentos** y el contador que se reinicia al restablecerse.
- La fila *Texto listo* ya era correcta: la app ahora la cumple con un estado real.

## Qué quedó pendiente

- **La línea base de latencia.** A13 dejó el instrumento; el número real sigue sin
  medirse. La cifra publicada (913 ms p50 / 1336 ms p95) es **del camino por palabras**
  y no es comparable con el de oraciones. Hay que remedir con las 28 frases y el
  backend de verdad.
- **El dominio del backend para release.** En A3 el default quedó en
  `wss://backend.invalid/ws/translate`, un host que no resuelve, porque ningún
  documento nombra uno. Mientras no se pase `-Psignai.backendWsUrl=wss://…`, un APK
  de release no habla con nadie.
- **La palabra "Error" no se ve nunca.** El estado existe, pero `statusLine` le da
  prioridad al mensaje, que es más útil. Anotado en el contraste §9 #12, con las dos
  formas de hacerlo visible si el owner lo quiere al revés.
- **Los ítems que necesitan decisión y no se tocaron:** A6 (marcadores), A15
  (`SignAssetRepository`), B4/B5 (las 18 señas que faltan).