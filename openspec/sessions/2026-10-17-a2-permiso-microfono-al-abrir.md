# A2 — El permiso de micrófono se pide al abrir la app

**Fecha:** 2026-10-17, tarde · **Rama:** `flujo-oraciones` · **Alcance:** solo este
repo. No se tocó Overleaf ni ningún hermano.

## Qué llegó

El pedido fue agarrar uno de los siete ítems de `docs/historia/CONTRASTE-APPvsDOCUMENTOS.md` y
hacerlo completo: código, build e instalación en el device. Se eligió **A2**, que es el
más chico y el que el propio documento sugiere primero porque verifica el ciclo
compilar-instalar-probar entero sin meter nada difícil en el medio.

## Qué se hizo

El SRS dice (`main.tex:837`, y `RF-UI-04` en `../signall-docs`, que piden lo mismo): la
solicitud del micrófono aparece **la primera vez que se abre la aplicación**. La app la
pedía la primera vez que se tocaba cualquiera de los dos interruptores, así que el
diálogo aparecía con el dedo del usuario ya encima del control.

- **`ui/MainScreen.kt`** — un `LaunchedEffect(Unit)` pide `RECORD_AUDIO` al abrir la
  pantalla principal. Concederlo **no** prende el micrófono: eso sigue siendo del toque
  del interruptor. El camino viejo (`withMicPermission`) **se conserva como red de
  seguridad**: si al abrir no se concedió, tocar el micrófono reintenta. Se extrajo
  `Context.hasMicPermission()` porque el chequeo estaba duplicado dentro del composable.
- **`data/MicPermissionPrompt.kt`** (nuevo, 37 líneas) — un `boolean` en el archivo de
  preferencias `signai` (clave `mic_prompted`) que recuerda que ya se preguntó.
- **`openspec/specs/ui/spec.md`** — fila nueva `CTL-14` con la regla y el porqué.

## Qué se decidió

**El flag propio es obligatorio; `shouldShowRequestPermissionRationale()` no sirve.**
Se implementó primero con esa API para no repetir el aviso de la negativa en cada arranque,
y **no funciona
en el teléfono donde se prueba**: devuelve `false` tanto si nunca se preguntó como si ya
no se va a preguntar nunca, que son dos casos distintos que la app tiene que diferenciar.

Lo que pasa en MIUI: **una sola negativa deja el permiso en `USER_FIXED`**. Medido en el
Redmi Note 9S el 2026-10-17 —

```
$ adb shell dumpsys package com.signall.app | grep -A2 "runtime permissions"
  android.permission.RECORD_AUDIO: granted=false, flags=[ USER_SET|USER_FIXED|… ]
```

`USER_FIXED` significa que el sistema no vuelve a mostrar el diálogo y responde `false`
en el acto. Con el criterio de la API, el aviso de `onMicPermissionDenied()` **nunca
salía**, que es justo lo que `RF-UI-04` pide ("si el usuario los deniega, la app muestra
un mensaje indicándolo"). El flag propio lo resuelve sin depender de cómo sea el ROM.

Que la pregunta salga **una sola vez** es lo que dice el SRS ("la primera vez que se abre")
y lo que hace `RF-UI-04`; el flag es lo que lo sostiene.

## Qué se midió

Cuatro casos verificados en el **Redmi Note 9S físico** (Android 11, MIUI), build de
debug instalada con `-Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate`:

| # | Estado previo | Resultado |
|---|---|---|
| 1 | Sin permiso, nunca preguntado | El diálogo **"¿Permitir que SignALL grabe audio?"** aparece al abrir, sin tocar nada |
| 2 | Denegado en ese diálogo | Aviso "Sin permiso de micrófono la app no puede traducir voz." en el hint, y el campo de texto y Traducir **siguen disponibles** |
| 3 | Segundo arranque, ya denegado | **Ni diálogo ni aviso**: entra derecho a Main |
| 4 | Permiso concedido | **Ningún diálogo** al abrir |

En el caso 1 el diálogo se verificó leyendo el árbol de accesibilidad
(`uiautomator dump`): tres botones, *Mientras la app está en uso* / *Solo esta vez* /
*No permitir*. En el caso 2 el aviso se verificó con captura de pantalla, porque dura 3 s
y no llega a aparecer en un `dump`.

## Qué quedó pendiente

- **Los otros seis ítems** del contraste: A1, A3, A4, A5, A7 y A13. El owner los pidió
  después, de menor a mayor complejidad: **A13 → A3 → A5 → A1 → A4 + A7**. El registro
  de avance vive en `docs/historia/CONTRASTE-APPvsDOCUMENTOS.md` §8 y §9, que es donde también se
  anotan las diferencias y los problemas que aparecen.
- **A4 tiene una decisión del owner sin resolver** (`CONTRASTE-APPvsDOCUMENTOS.md` §7):
  si la desconexión respeta el modo TR o corta el micrófono como cualquier error. `fail()`
  hoy lo apaga.
- Al releer `MainScreenState.kt` se vio que sigue `MaxReconnectAttempts = 5` (`:50`),
  que contradice la decisión del owner del 2026-10-17 de reconexión **sin límite** de
  intentos. No se tocó: es parte de A4.
- **No se pudo ejercitar el camino del toque del micrófono**: el botón está deshabilitado
  sin backend (`micAvailable = state.backendConnected`) y en esta sesión no había ninguno
  corriendo en `127.0.0.1:8080`. Ese camino no cambió de comportamiento —solo se le
  extrajo el chequeo a un helper—, así que no era necesario, pero queda dicho.

## Continuación (2026-10-17, más tarde)

La misma sesión siguió con el resto de los ítems, de menor a mayor complejidad. **A13**
quedó en `openspec/sessions/2026-10-17-metrica-y-wss.md`, que además tiene el detalle de
los dos últimos ítems.