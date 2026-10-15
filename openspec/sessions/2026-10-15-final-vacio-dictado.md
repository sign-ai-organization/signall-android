# El `final` vacío de una frase dictada: 15 s de espera y un error que no pasó

- **Día:** miércoles
- **Fecha:** 2026-10-15
- **Hora:** 10:03–11:20

## Qué se hizo

Probando el modo dictado en el Redmi Note 9S con la pila de `develop` levantada, apagar el
micrófono con una frase abierta dio un fallo que no era un fallo:

```
10:03:16  micrófono apagado (venía de Dictation)
          dictado: esperando el final de la frase abierta para mandarla
10:03:31  fallo: No llegó la transcripción. Probá de nuevo.     ← 15 s después
```

El STT, en el mismo momento:

```
[timing] final (flush): audio=2.7s transcripcion=334ms atraso=0ms descartado=no_speech texto=''
```

El `flush` fue correcto e instantáneo. El problema era que **nadie lo atendió**.

## El bug tenía tres caras

Está en el contrato entre los tres repos, y arreglarlo en uno solo lo cambiaba de lugar.

1. **`../sign-ai-stt` — `app/session.py`, `flush()`**: terminaba en
   `return result.to_payload("final") if result.text else None`. Con el `end` del cliente
   ya no hay nadie preguntando, pero el cliente **sí había preguntado**: si el filtro de
   descarte se comía el audio no salía nada. Lo mismo con `if self._speech_samples == 0:
   return None`, que es el caso de que el silencio ya hubiera cerrado la frase y el `end`
   llegara tarde. El camino por silencio (`feed`) sigue descartando los vacíos: ahí nadie
   pregunta y mandarlos sería ruido en la conexión.

2. **`../sign-ai-backend` — `app/controller/controller.py`**: `send_transcript` ya está
   antes del `return` por texto vacío (línea 251 vs 253), pero `self._utterance_mode = None`
   está **después** (línea 271). Con un `final` vacío de dictado, el modo quedaba pegado.
   Eso es lo peligroso: si el cliente ya había pasado a tiempo real, el utterance
   siguiente heredaba `dictation` y sus frases se iban al input **en vez de firmarse**,
   sin nada en pantalla que lo explicara. Por eso el arreglo del punto 1 sin este era
   cambiar un bug por otro.

3. **Esta app — `MainScreenState.kt`, `handleEvent`**: `if (event.text.isBlank()) return`
   descartaba el `final` vacío, así que aunque llegara, `processingJob` seguía corriendo y
   el timeout de 15 s vencía igual.

El tiempo real ya tenía la salida correcta (`heardAnythingThisSession` →
"No se escuchó nada."); el dictado no la tenía para este caso.

## Los cambios

- **`sign-ai-stt`**: `flush()` devuelve `dict` y responde siempre, con `drop_reason="empty"`
  cuando no queda audio y con el payload real cuando el filtro descartó el audio.
  `app/main.py` manda ese payload sin la guarda `if result:`, que ya no puede ser falsa.
- **`sign-ai-backend`**: libera `_utterance_mode` también en el `return` por texto vacío,
  con un log (`dictated: empty final, releasing capture mode`) para que el camino sea
  diagnosticable. Dos checks nuevos en `scripts/verify_client_frames.py`: que el `final`
  vacío se reenvía con `final`/`dictation` correctos, y que el utterance siguiente en
  tiempo real **sí se firma**.
- **`sign-ai-android`**: el `final` vacío pasa cuando viene marcado como dictado; en tiempo
  real se sigue descartando. `onDictationTranscript` ya tenía el resto del camino
  (`sendDictation()`, que con texto vacío muestra "No se escuchó nada."), así que solo
  faltaba dejarlo llegar y cancelar el timeout.
- **`sign-ai-backend/docs/android-integration.md`**: decía literalmente "puede venir vacío:
  ignorarlo", que era la instrucción que producía el bug. Ahora hay una sección con la
  tabla de qué hacer según `dictation`, y se documentó el flag `dictation`, que no
  estaba en la doc pese a ser parte del contrato.

## Un efecto secundario que hubo que cerrar

El arreglo del punto 3 volvía alcanzable un final vacío de dictado **sin texto previo**:
si el usuario había escrito algo a mano, prendía el micrófono sin decir nada y lo apagaba,
`inputText = live` le ponía `""` encima y le borraba lo que había escrito. `onDictationTranscript`
solo escribe en el input cuando hay palabras. No hay otra forma de recuperar ese texto.

## Cómo se comprobó

- `python -m scripts.verify_client_frames` en el contenedor del backend: **64 PASS, 0 FAIL**
  (los 58 previos más 6 nuevos).
- Script contra el STT real (`ws://127.0.0.1:8000/ws/transcribe`): `end` sin audio →
  `{"type":"final","text":"","audio_ms":0}`; `end` con 180 ms de audio → `{"type":"final",
  "text":"","audio_ms":180}`. Antes no salía nada. Con 400 ms y 700 ms el texto normal, sin
  cambios.
- Script contra el backend real (`ws://127.0.0.1:8080/ws/translate`): el `final` vacío llega
  como `{"type":"transcript","text":"","final":true,"dictation":true}`, y el utterance
  siguiente en tiempo real se firma (`BIEN, BIEN`).
- En el físico: con el ambiente real, `final (flush): descartado=logprob texto=''` y la app
  volvió a `Idle` en menos de un segundo con "No se escuchó nada.".

La combinación exacta (texto ya dictado **más** un `final` vacío) no se pudo forzar en el
físico porque depende de que la frase abierta del STT caiga sobre audio que su filtro
rechace, y el ambiente no ayuda. Queda cubierta por el test del Controller y por el script
del circuito completo, no por el teléfono.

## Pendiente

- Los cambios del backend y del STT están **sin commitear**, como manda su regla de git
  (PR contra `develop`, lo manda el owner). La app sí se puede commitear directo.
- La app se compiló con `-Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate` y
  `adb reverse tcp:8080 tcp:8080`, como siempre para el físico.