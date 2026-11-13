# Rótulo de la seña en curso sobre el avatar

**Fecha:** 2026-11-02, noche · **Rama:** `main`

## Qué llegó

Pedido del owner: mientras el avatar representa una frase, ver arriba de la
escena **la seña que está saliendo en ese momento** —una sola, la que la escena
acaba de arrancar—, no la frase entera. Ejemplo: sale `HOLA`, después
`COMO ESTAS`. El owner aclaró que es una feature **nueva**, que no está en los
documentos.

## Qué se hizo

- `app/src/main/java/com/signall/app/ui/state/MainScreenState.kt:390`:
  `currentGloss: String?`, estado público de solo lectura.
- `MainScreenState.kt:339`: `onSignStart()` resuelve el índice de la cola contra
  `turnQueueItems` y escribe el rótulo. **No hay frame nuevo** entre Kotlin y la
  escena: se reusa el `signStart:<índice>:<jsMs>` que ya existía.
- `MainScreenState.kt:1490`: se apaga en `clearTurnState()`, que es el punto por
  el que pasan `fail()`, `onDisconnected()`, el apagado del micrófono que corta
  la frase y el arranque de una frase nueva.
- `MainScreenState.kt:2486`: se apaga en `onPlaybackDone()`.
- `MainScreenState.kt:2385`: se apaga en `attachWebView()`. Un WebView nuevo no
  tiene cola; cubre salir de *Acerca de* a mitad de una frase, donde la escena se
  destruye y su `onPlaybackDone` ya no llega.
- `app/src/main/java/com/signall/app/ui/screens/stage/AvatarStage.kt:126`:
  parámetro `currentGloss`, y la píldora arriba al centro (una sola, con
  `AnimatedContent` adentro para que el ancho acompañe el cambio de texto).
  `GlossTopGap` = 36 dp bajo la barra de estado.
- `app/src/main/java/com/signall/app/ui/MainScreen.kt:249`: cableado.

El `caption` de depuración (frase entera, arriba a la izquierda con Ajustes →
Avanzado) **no se tocó**: sigue siendo la vista de diagnóstico.

## Qué se decidió

- **Posición: encima de la cabeza, no al 25 % del alto.** El owner había dicho
  "la mitad de la mitad de arriba", pero el 25 % cae sobre el torso y las manos,
  que en LSC son lo que hay que mirar. Se le presentaron las dos opciones con su
  costo y eligió la banda libre sobre la cabeza (`STG-9` deja ~215 px de aire en
  el Redmi). Si en otro teléfono la cabeza sube, el número a mover es
  `GlossTopGap`.
- **Una seña por vez, resuelta por índice**, y no la oración: es lo que pidió el
  owner y es exactamente lo que la escena ya avisa.
- **Texto de la glosa tal cual llega** (mayúsculas incluidas, p. ej.
  `COMO ESTAS`), porque es el mismo texto que ya usa el panel de conversación.
  Si el owner prefiere minúsculas, es un `lowercase()` en el rótulo.
- **Píldora `surface` + borde `outline`, `h4`**, como el botón Repetir, con
  fundido de `SignAiMotion.Fast` (0 ms con "reducir animaciones").
- **Sin `pointerInput`**: no participa del hit-test, así que los toques en esa
  zona siguen llegando al WebView del avatar.

## Qué se midió

- **Redmi Note 9S** (1080×2400, densidad 440), build `debug` con
  `-Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate` y
  `adb reverse tcp:8080 tcp:8080`.
- Como el backend real no estaba levantado, se usó un **backend falso en
  `/tmp/opencode/fake_backend.py`** (fuera del repo): responde a
  `{"type":"text"}` con un `segment` de varias glosas y su `done`. La app no
  habla con nada más, así que ejercita el camino real completo.
- `logcat -s SignAiState`: `signStart` por cada índice de cola, uno cada ~1,95 s.
- En pantalla y en el árbol de accesibilidad se vio el rótulo cambiar con la
  seña: `COMO ESTAS` → `POR FAVOR` → `HOLA`. El nodo se publica como
  `Seña en curso: <glosa>`; el canvas del avatar no publica nada.
- Con una cola de dos señas, el rótulo **desapareció solo** al terminar
  (captura con el estado en *Inactivo* y el botón Repetir).
- `./gradlew build installDebug` pasa; la app quedó instalada en el teléfono.

## Qué quedó pendiente

- **El SRS no pide este rótulo** (la fila *Reproduciendo* solo menciona "Seña no
  disponible"). El owner lo sabe y lo agrega él en Overleaf; la spec de la app
  ya lo registra como `STG-10` en `openspec/specs/ui/spec.md`.
- Con Ajustes → Avanzado prendido, el `caption` de depuración puede ocupar dos
  líneas y acercarse al rótulo. Es solo de diagnóstico y no se tocó.
- Si una glosa no está en el catálogo, no entra a la cola y el rótulo la saltea;
  el aviso de "Seña no disponible" sigue saliendo por el indicador de estado,
  como antes.
