# Resiliencia en la app · PR-RES-01 y PR-RES-03

**Fecha:** 2026-11-08, tarde · **Rama:** `main` · **Commit:** `f2d5cdb`

## Qué llegó

La ronda del carril P2 (`docs/historia/plan-pruebas/RESULTADOS-CARRIL-2-2026-11-06.md`) dejó seis pruebas en
**Falla**. El owner pidió arreglar las dos que son de la app: `PR-RES-01` y `PR-RES-03`. Para la
primera, precisó que la barra de texto debe recuperar la frase **sea que se dijo por el micrófono
o que se escribió**.

## Qué se hizo

- **`PR-RES-01` (`MainScreenState.onDisconnected`).** Causa: al perderse la sesión con una frase en
  traducción, solo se soltaba la retención del texto (`releaseSentText`), pero no se devolvía al
  campo; pasados `SentTextHoldMs` (1,5 s) el temporizador ya había vaciado la barra. Ahora la frase
  cancelada vuelve a `inputText`, con `dictationPending = true` para que Traducir quede disponible.
  Voz y texto escrito pasan por el mismo `pendingText`, así que una sola rama cubre las dos.
  No pisa un texto más nuevo que el usuario ya haya escrito, y no restaura un Repetir (`isReplay`).
  Si el avatar ya estaba representando, el comportamiento no cambia (decisión documentada en el
  propio método).
- **`PR-RES-03` (`MainScreenState.watchForVoiceToReturn` + `SignAiWebSocketClient.probeVoice`).**
  Causa: el backend le dice a la sesión si hay voz solo **al abrirla** (frame `capability`), así que
  una sesión abierta sin STT nunca se entera de que volvió. Ahora, mientras `sttAvailable` es falso,
  cada 5 s (`VoiceProbeMs`) se abre un WebSocket desechable, se lee su primer `capability` y se
  cierra (tope de 4 s, `PROBE_TIMEOUT_MS`, por si el backend es viejo y no manda el frame). Si dice
  `audio=true` y la app está ociosa (sin micrófono, sin frase en vuelo, sin representar, estado
  *Inactivo*), se cierra la sesión real una vez y el reconectador abre otra con voz. Mientras la
  respuesta sea «no», no hay *Desconectado* ni parpadeos. **Backend sin cambios.**
- **Voseo.** `MicUnavailableNotice` pasa de «podés» a «puedes» (`AGENTS.md`: nada de voseo).
- **Verificación en el Redmi Note 11 (`61ecd28e`)** contra un backend de laboratorio (`p4lab-*`:
  Postgres 16 + pgvector propio, catálogo de 50 glosas, STT con `medium` en cuda, LLM apuntado a una
  dirección muerta para que una frase nueva quede ~10 s en vuelo). Sin tocar Neon ni el stack
  canónico. Todo se retiró al terminar (contenedores, red y volumen).
  - `PR-RES-03`: STT detenido y app reiniciada → aviso con «puedes»; STT encendido 19:04:43 → app
    reabre sola a las 19:05:05 (22 s, ≈15 s de carga del modelo) → mic habilitado y encendible.
  - `PR-RES-01`: frase escrita, corte a los 3,5 s (`adb kill-server`) → `cancelando=true`, la frase
    vuelve a la barra, pantalla «Texto listo» con la frase.
  - Evidencia: `docs/plan-pruebas/evidencias/RES/PR-RES-01_E_20261108_01.txt` y
    `PR-RES-03_E_20261108_01.txt`.
- `./gradlew build` pasa (ktlint incluido). Instalado `debug` en el teléfono.

## Qué se decidió

- **El arreglo de `PR-RES-03` es solo de la app.** La alternativa era que el backend avisara con un
  frame cuando el STT vuelve; cruza el contrato y toca un repo en el que no se commitea sin
  permiso, así que se eligió el sondeo desde el cliente. Si más adelante el backend manda ese
  aviso, `watchForVoiceToReturn` se puede reemplazar sin cambiar nada más.
- **El sondeo abre una sesión de backend por intento** (conecta al STT y lo suelta): con el STT
  caído son ~12 sesiones/min por teléfono con voz apagada. Aceptable para la tesis; con muchos
  clientes habría que subir el intervalo.
- **No se tocó `MicPermissionNotice`**, que también usa voseo («Activalo»): no es de estas pruebas.

## Qué quedó pendiente

- **No probado:** la ruta por micrófono de `PR-RES-01` (sin altavoz; el código es común con la de
  texto); los otros dos momentos del corte (hablando, representando) sin repetir; y en `PR-RES-03`
  el caso «STT detenido a mitad de una frase».
- **Observación sin explicar:** en `PR-RES-01` la app tardó ≈7 s en detectar el corte (el 6/11,
  ≈2,4 s). No se investigó; puede ser el método (`kill-server`) o el heartbeat de 20 s.
- Siguen en **Falla** las otras cuatro del carril P2 (`STT-03`, `CAC-05`, `RES-04`, `SEC-03`), que
  son de los repos hermanos o dependen de decisiones del owner (ver §5.3 de los resultados).
- Voseo restante en la app: `MicPermissionNotice` («Activalo») y «Apagá el micrófono…» en
  `requestTranslation`.
