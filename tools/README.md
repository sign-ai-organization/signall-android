# `tools/` — ayudas para desarrollar y probar

> Esta carpeta **no es parte de la app**: es el kit de herramientas con el
> que opera el desarrollador (humano o agente) mientras prueba en el
> emulador/dispositivo.

## Contenido

- **`mock_backend.py`** — servidor mock del backend de SignAI para correr la
  app sin red. Ver header del archivo para detalles.
- **`dev.sh`** — wrapper sobre `mobilecli` (mobile-next) para manejar el
  emulador Android. Lista, captura, toca, escribe, instala el APK,
  lanza/termina la app, lee logs, dump del UI tree.

## `dev.sh` rápido

```bash
tools/dev.sh devices                  # lista dispositivos
tools/dev.sh shot antes               # captura a /tmp/shots/antes.png
tools/dev.sh tap 540 1200             # tap sin escala, coords reales del device
tools/dev.sh text "hola"              # escribir
tools/dev.sh back                     # atrás
tools/dev.sh tree                     # UI tree con refs @eN
tools/dev.sh install-debug            # ./gradlew installDebug + relanzar la app
tools/dev.sh logcat-tag SignAIWeb 80  # últimas 80 líneas del bridge JS→Kotlin
tools/dev.sh log 40                   # últimas líneas del device log
tools/dev.sh doctor                   # valida entorno (mobilewright doctor)
```

El package por defecto es `com.signai.webview` (este repo). Se puede
sobrescribir con `$APP_PKG` y `$APP_ACTIVITY`. El device se fija con
`$MOBILECLI_DEVICE`.

**Sin escala**: `mobilecli` trabaja en píxeles reales del device (1080×2400),
las capturas y las coords de tap/swipe están en la misma unidad. Si algo no
responde, **no** es el factor 1.172 — es que el árbol cambió o el elemento
no estaba donde pensabas. Usá `tools/dev.sh tree` para ver qué hay.

## Operar desde un agente

Cuando el que opera el dispositivo es un agente (Claude, opencode), la
vía primaria es el **MCP `mobile-mcp`** de mobile-next
([mobile-next/mobile-mcp](https://github.com/mobile-next/mobile-mcp)),
configurado en `opencode.json` de este repo. Expone tools como:

- `mobile_list_available_devices` — lista emuladores/dispositivos.
- `mobile_take_screenshot` — devuelve PNG.
- `mobile_list_elements_on_screen` — UI tree con refs `@eN` y bounding
  boxes. **Usar siempre antes de tocar**, en lugar de adivinar
  coordenadas.
- `mobile_click_on_screen_at_coordinates`, `mobile_type_keys`,
  `mobile_press_button`, `mobile_swipe_on_screen` — input básico.
- `mobile_install_app`, `mobile_launch_app`, `mobile_terminate_app` —
  ciclo de vida de la app (instalar APK, lanzar activity, terminar).
- `mobile_get_device_logs` — logs del device (filtrar por package).

`mobile-mcp` está construido sobre `mobilecli`, así que las dos capas ven
el mismo device y comparten el daemon en `~/.mobilecli/`.

## El flujo de "verificar un cambio"

El `AGENTS.md` § Build dice que es **obligatorio** instalar la app en el
device/emulador después de cada cambio. Con este tooling:

```bash
# humano o agente
./gradlew installDebug                                        # build
tools/dev.sh install app/build/outputs/apk/debug/app-debug.apk # push APK
tools/dev.sh launch com.signai.webview/com.signai.webview.MainActivity
tools/dev.sh shot post-build
tools/dev.sh logcat-tag SignAIWeb 40   # ver mensajes del bridge JS↔Kotlin
```

Con MCP, la versión agente es `mobile_install_app` + `mobile_launch_app`
+ `mobile_take_screenshot` + `mobile_get_device_logs`.

## Stack del tooling del agente

- **`mobilecli`** — CLI unificado para dispositivos (reemplaza `adb`).
  Instalado global vía `npm install -g mobilecli`.
- **`mobile-mcp`** — servidor MCP que envuelve mobilecli como tools para
  agentes. Configurado en este repo en `opencode.json`. Telemetry off vía
  `MOBILEMCP_DISABLE_TELEMETRY=1`.
- **`mobilewright`** — instalado pero **no se usa** (no escribimos tests
  E2E). Su CLI queda disponible para `mobilewright doctor`/`inspect`/
  `screenshot`/`codegen` si alguna vez hace falta.
- **`mobilecli daemon`** — cachea devices entre invocaciones. Expira a
  los 30 min de inactividad. `mobilecli daemon status|stop`.
- **`tools/dev.sh`** — wrapper sobre `mobilecli`. Convive con
  `mock_backend.py`. Si algo se rompe, `adb` puro sigue disponible como
  fallback.
