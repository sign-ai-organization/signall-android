# Fase 4 · Android (AVT-03 y RES-03)

**Día:** viernes 6 de noviembre de 2026. **Rama:** `ramaSergio`.

- **Objetivo:** aplicar la fase 4 del plan de implementación (AVT-03 decisión, RES-03 fix).
- **Hallazgo:** el código de las dos ya existía desde el commit `f3677ab`; el plan estaba
  desactualizado en ese punto.

## Lo que ya estaba hecho

- **RES-03:** `BackendEvent.Capabilities`, el caso `capability` en `SignAiWebSocketClient.kt` y
  `sttAvailable` en `MainScreenState` (que deshabilita el micrófono). El backend envía el frame
  en `../signall-backend/app/controller/controller.py` (`send_capability`).
- **AVT-03:** opción B (decisión D3 del 2026-11-05): una frase nueva se encola detrás de la que
  suena, con tope `MaxQueuedPhrases = 1`.

## Lo que se cambió

`MainScreenState.kt`: `clearTurnState()` y `appendQueue` ya no vacían `turnQueueItems` mientras
la escena sigue representando (`queuePlaying`). La escena numera las señas de forma acumulada
al encolar, y `onSignStart` resuelve la glosa contra esa lista; vaciarla dejaba el rótulo del
avatar en blanco o con otra glosa durante el resto de la frase.

## Verificación

- `./gradlew build` (con ktlint): pasa. `installDebug`: instalado en el teléfono.
- **No se probó en el device**: la prueba la hace el owner con el backend levantado.

## Pendiente (otro lado)

- `../signall-backend/docs/android-integration.md` no describe el frame `capability`. No se
  tocó: es otro repositorio.
