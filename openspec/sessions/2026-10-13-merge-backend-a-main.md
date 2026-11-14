# Sesión: merge de la integración de backend al tronco

- **Día:** martes
- **Fecha:** 2026-10-13
- **Hora:** ~18:15–18:25 (local)

## Qué se hizo

Se mergeó `feat/integracion-backend-v2` a `main` y se pusheó directo a `origin`
(sin PR, según la convención de `AGENTS.md`).

## Qué se decidió

- **"El máster" es `main`.** Este repo (`sign-ai-android`) no tiene rama
  `master`; el tronco es `main` y `origin/HEAD` apunta ahí. El `master` que sí
  existe es el del repo padre `../SignAI` (`SamuOsorio/SignAI`), parado en
  2026-08-27: sus 12 archivos bajo `webview/` son un esqueleto viejo
  (`appKotlin` borrado del árbol, 128 borrados sin commitear) y no rastrea el
  código actual. No es destino de este trabajo — el webview es su propio repo.
- **Merge fast-forward.** `main` no tenía commits propios (0 detrás, 2 adelante),
  así que la historia queda lineal: `84737aa..99e468e`. Nada se perdió.
- **Build verificado antes de mergear**: `./gradlew build` en verde
  (`BUILD SUCCESSFUL`), sin errores de lint.

## Qué se verificó en el dispositivo

Redmi Note 9S (Android 11) vía `mobile-mcp`:

- Instalado con la config de teléfono físico, no la de emulador:
  `adb reverse tcp:8080 tcp:8080` +
  `./gradlew installDebug -Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate`.
  Sin ese `-P`, el default `ws://10.0.2.2:8080/...` del `build.gradle.kts` no
  resuelve en un celular real (ver `puesta-en-marcha.md`).
- App arranca, el avatar carga, el tema oscuro se aplica y el header muestra
  **"Sin conexión con el servidor"**, que es lo correcto: el mock no estaba
  corriendo en la laptop al momento de la prueba.

## Pendiente

- El mock/backend (`ws://127.0.0.1:8080`) queda apagado: levantar
  `tools/mock_backend.py` o el backend real para probar el flujo completo con
  micrófono. El túnel `adb reverse` ya quedó puesto, hay que repetirlo si se
  desconecta el cable.
- `feat/integracion-backend-v2` sigue existiendo local y ya está contenida en
  `main`. Se puede borrar cuando quiera.