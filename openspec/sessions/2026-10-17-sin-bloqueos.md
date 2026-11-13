# A14 — que la app no se pueda quedar congelada

**Fecha:** 2026-10-17, noche · **Rama:** `flujo-oraciones` · **Alcance:** solo este
repo. El backend y el STT no se tocaron; lo que de ellos quedó como deuda está en
[`CONTRASTE-BACKENDvsDOCUMENTOS.md`](../../../signall-backend/docs/historia/CONTRASTE-BACKENDvsDOCUMENTOS.md).
Overleaf no se tocó: es de solo lectura, y el texto que hay que pegarle está en
§6 de [`docs/historia/CONTRASTE-APPvsDOCUMENTOS.md`](../../docs/historia/CONTRASTE-APPvsDOCUMENTOS.md).

## Qué llegó

El owner reprodujo un congelamiento: la app se quedaba en *Procesando* y no salía.
No era el bug que A5 y A7 ya habían arreglado (una frase sin ninguna seña
representable), sino otro: **una frase que la app mandó y que nadie contestó**.

La sesión anterior había hecho la investigación y entregado una tabla de veinte
casos con seis cambios propuestos; el owner los aprobó y dijo "arranca con los
arreglos". Esto es esa ejecución.

## Las tres decisiones que él tomó

| Decisión | Qué | Por qué ese número |
|---|---|---|
| El reloj que saca a la app de *Procesando* | **15 s de silencio**, y el resultado es *Error* | El mismo plazo que ya usa el reloj de la transcripción, y 5 s por encima del tope que el backend le pone al LLM (`llm_timeout_ms = 10000`), así que una frase válida siempre tiene tiempo |
| Apagar el micrófono o el TR con una frase en vuelo | **Cancela** la frase y su texto vuelve al campo editable | El interruptor es también el botón de parar. Antes no paraba |
| Una seña que no se puede descargar | **Se saltea**, se sigue con las demás, y el indicador dice "Seña no disponible" | Es el único texto propio que la tabla de estados del SRS permite |

Las tres están en §7 del documento de contraste, con el texto propuesto para el SRS
en §6.

## Lo que cambió en el código

Siete cambios: los seis-propuestos, más uno que encontró la propia verificación.

| # | Cambio | Dónde | Spec |
|---|---|---|---|
| 1 | `armTurnSilence()`: un reloj de **silencio** de 15 s, rearmado con cada frame | `ui/state/MainScreenState.kt` | `STA-23` |
| 2 | `pingInterval(20 s)` en el WebSocket | `data/SignAiWebSocketClient.kt` | `STA-24` |
| 3 | `cancelPhraseByMicOff()` + `StaleTurn`: apagar el micrófono cancela la frase en vuelo | `ui/state/MainScreenState.kt` | `STA-25` |
| 4 | El `catch` de `loadQueueItem` saltea la seña y sigue, y avisa con el mensaje nuevo `signFailed:<i>` | `assets/web/app.js` + `AvatarWebView.kt` | `STA-26`, `BRG-18` |
| 5 | `onReceivedError` y `onRenderProcessGone` en el WebView, con `sceneDead` para que la app no siga fingiendo | `ui/webview/AvatarWebView.kt` | `STA-27` |
| 6 | `appendsInFlight` + `finishTurnWhenQueueSettles()`: el `done` espera a la cola | `ui/state/MainScreenState.kt` | `STA-28` |
| 7 | `queueDeliveryGate`: la cola se entrega **en orden de oración** | `ui/state/MainScreenState.kt` | `STA-29` |

El 7 no estaba en la lista: lo encontró la verificación del 6, y era el peor de los
dos (la frase se cortaba a mitad, en silencio). El detalle está abajo.

## Lo que se midió, en el Redmi Note 9S

Todo contra el backend falso de `/tmp/opencode/fake-backend.py`, que decide qué
hacer con una palabra clave en la frase mandada. Los números son de ahí, no de la
teoría.

| Qué | Cómo se provocó | Resultado |
|---|---|---|
| El congelamiento | `SIN-RESPUESTA`: ni una oración ni un `done` | El reloj disparó a los **15,001 ms** exactos (`22:22:37,934` → `22:22:52,935`); *Error* con el punto rojo y a los 3 s *Inactivo* con el panel libre |
| La caída silenciosa | Backend congelado con `SIGSTOP`: el socket abierto, nadie contesta | Detectada a los ~40 s con `sent ping but didn't receive pong within 20000ms`, y la app pasó a *Desconectado* sola. **Antes no se detectaba nunca** |
| Apagar el TR con frase en vuelo | TR prendido, frase mandada, backend mudo, TR apagado a los 5 s | `frase cancelada por el apagado del micrófono (seña en curso=false)`: de *Procesando* a **Texto listo** con la frase de vuelta en el campo editable. A los 18 s seguía así, sin falso *Error* |
| Una seña que no carga | Un `.glb` de basura servido en el medio de una frase de tres | `No se pudo cargar la seña` → `se saltea` → **la tercera seña se representó igual**, y en pantalla "Seña no disponible" |
| El `done` antes que la cola | Un `.glb` **bueno** con 2 s de retraso en la primera oración | `el fin de la frase llegó con la cola todavía armándose: se espera`, y el turno cerró 1,8 s después |

**Lo que no se pudo probar:** el WebView muriendo (caso 5). Hace falta que el
navegador mate su proceso de render y no hay forma de pedirlo por `adb`. El código
está y compila; lo que no se miró es cómo se ve en pantalla cuando pasa.

## Dos defectos que encontró la propia verificación

**El orden de la cola (grave).** `appendQueue` decidía "esta es la primera tanda"
(`!queueStarted`) **antes** de resolver los assets. Con dos oraciones del mismo turno
que llegan juntas y resuelven en distinto orden —una cuya seña tarda 2 s en bajarse y
otra que resuelve al instante— **las dos salían como la primera**: las dos hacían
`setQueue` + `play()` y la segunda **pisaba** la cola que la primera ya estaba
representando. Medido: la frase se cortaba a mitad, en silencio, y el turno se cerraba
sobre una cola que ya no era la de la frase. Con solo mover la lectura adentro de la
coroutine el pisamiento desaparece pero la entrega queda en orden de descarga, y el
avatar representa la frase al revés. Lo que lo arregla es separar las dos cosas: los
assets se resuelven **en paralelo** y la entrega se serializa con `queueDeliveryGate`.
Después del arreglo, en la misma prueba: seña 0 a los 2186 ms y seña 1 a los 4105 ms,
en orden.

**Un fallo falso propio (del cambio 1).** El reloj de silencio declaraba
"El servidor no respondió" quince segundos después de una cancelación: la frase la
canceló el usuario y la app ya estaba en *Texto listo* con el texto en el campo. Lo
que hace ahora, cuando no hay frase en vuelo y lo que se esperaba era el `done` de una
cancelada, es **olvidar el descarte en silencio**. Se detectó porque la primera
verificación del caso 3 dio el falso *Error* justo a los 15 s.

## La deuda que queda, dicha

- **El `done` no dice a qué frase pertenece.** Cancelar es local —el protocolo no tiene
  un frame para cancelar— así que la app tiene que tragarse un `done` a ciegas. Con un
  id de turno en `segment`/`done` se arregla de verdad, y es **aditivo**: es el cambio
  propuesto en el §2 del contraste del backend. No se tocó el backend.
- **`onRenderProcessGone` devuelve `false`**, por decisión del owner (2026-10-17,
  preguntada): el sistema mata la app y el launcher la levanta con todo funcionando.
  La alternativa —devolver `true` y que la app sobreviva— dejaría un canvas negro
  por el resto de la sesión, porque un WebView sin proceso de render no renderiza
  nunca más. Perder lo que había en el campo es menos malo que un avatar muerto.
  `onReceivedError` es otro caso y no se toca así: ahí el WebView vive.
- **Los 15 s no están en ningún documento.** El SRS dice *qué* señal mostrar y nunca
  cuánto esperar. Está anotado en §6 #5 del contraste de la app, con el texto
  propuesto para pegar en el SRS.
- **Los 15 s pueden dar un falso *Error* contra el backend real, y no se midió.** El
  tope del LLM son 10 s, pero **la consulta a Neon no tiene tope** (§1 del contraste
  del backend): si esa consulta tarda 15 s, la frase es válida y la app la va a
  declarar fallida. Contra el backend falso eso no se ve, porque el falso responde en
  milisegundos. **Los 15 s son un número de la app, no del sistema**: hasta que el
  backend ponga `command_timeout`, el reloj es una apuesta. Si el owner quiere que sea
  una apuesta más chica, el único número a cambiar es `TurnSilenceMs`.
- **`readTimeout` sigue en 0**, que es lo correcto para una sesión larga; ahora hay un
  latido que la vigila.

## Cómo se verificó

`./gradlew build` y `./gradlew installDebug -Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate`
con `adb reverse tcp:8080 tcp:8080`. El backend real no arrancó: necesita Neon y clave
de LLM, y para estos casos hacía falta poder **provocar** la falla, no que la falla
pasara sola.

Un detalle de la sesión: el teléfono se bloqueó con PIN en mitad de la verificación y
`adb` no lo puede desbloquear. Se verificó todo lo que se puede ver por log con el
teléfono bloqueado (el ping, con el backend congelado por `SIGSTOP`), y el owner lo
desbloqueó para el resto.