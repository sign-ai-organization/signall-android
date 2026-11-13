# Herramientas para controlar el móvil

> Documento de **referencia completa**. No lo cargues de entrada: es largo y la
> mayoría de las sesiones solo necesitan un cuarto de esto.
>
> **Si sos un agente**, cargá primero el skill `controlar-app-movil`
> (`.opencode/skills/controlar-app-movil/`): trae la decisión de qué usar, los
> workflows y los errores ya pisados, en menos tokens. Volvé a este archivo solo
> si necesitás el detalle de alguna tool en particular.
>
> Índice rápido:
>
> | Herramienta | Qué es | Cuándo |
> |---|---|---|
> | [mobile-mcp](#mobile-mcp--la-vía-principal) | MCP de mobile-next sobre `mobilecli` | Tocar, leer, instalar, lanzar |
> | [tools/dev.sh](#toolsdevsh--el-mismo-motor-por-shell) | Wrapper sobre `mobilecli` | Cuando el MCP no está, o desde un shell |
> | [Maestro](#maestro--flows-repetibles) | Flows YAML | Guiones idénticos en cada corrida |
> | [tools/rendimiento/](#rendimiento--medir-de-verdad) | Scripts de medición | Cuando el reporte es "se siente lento" |
> | [tools/pinch.sh](#pinch) | Pinch de dos dedos por `sendevent` | Solo emulador |

## Lo que **no** hay acá, y por qué

Este repo es **Android nativo** (Kotlin + Compose). El repo hermano
`AppMovil` es Flutter, y su stack tiene dos herramientas que acá no aplican y
que no conviene copiar:

| Falta | Por qué |
|---|---|
| **`dart-mcp`** | Habla el protocolo de la VM de Dart. No hay Dart acá. |
| **`marionette`** | Habla el **árbol de widgets de Flutter**. No hay árbol de widgets. |
| **`flutter run --profile`** | No hay `flutter run`. El equivalente es un build release. |
| **`timeline.py`** (VM service) | Lee la timeline del motor de Flutter. Acá la lectura correcta es `dumpsys gfxinfo ... framestats`, que es además **más** informativa. |

Lo que sí se porta tal cual: **`mobilecli` + `mobile-mcp` + Maestro +
`sendevent` para el pinch**. Eso habla Android, no Flutter.

La consecuencia práctica: **no existe hot reload.** El ciclo es
`./gradlew installDebug` + relanzar, siempre. El MCP no lo cambia.

---

## mobile-mcp — la vía principal

MCP de [mobile-next/mobile-mcp](https://github.com/mobile-next/mobile-mcp),
configurado en el `opencode.json` de este repo. Telemetry off con
`MOBILEMCP_DISABLE_TELEMETRY=1`. Envuelve `mobilecli` como tools, así que las
dos capas ven el mismo device y comparten el daemon en `~/.mobilecli/`.

Es la vía más general y la única que funciona **exactamente igual en cualquier
device** sin tocar una línea de código de la app.

| Tool | Para qué |
|---|---|
| `mobile_list_available_devices` | Lista devices. **Siempre primero**: no hay device implícito. |
| `mobile_list_elements_on_screen` | Árbol de accesibilidad con `ref` (`@e31`) y bounds. **Usar antes de tocar.** |
| `mobile_take_screenshot` | PNG. Para juzgar apariencia, no para sacar coordenadas. |
| `mobile_click_on_screen_at_coordinates` | Tocar. Acepta `ref` o coordenadas. |
| `mobile_type_keys` / `mobile_press_button` / `mobile_swipe_on_screen` | Input básico |
| `mobile_install_app` / `mobile_launch_app` / `mobile_terminate_app` | Ciclo de vida |
| `mobile_get_device_logs` | Logs del device, filtrables por package |
| `mobile_batch_commands` | Agrupa tap → type → tap en un round-trip. **Cada llamada es un viaje al device**: agrupá. |

### Prioridad de selectores

| | |
|---|---|
| 1. `ref` de `mobile_list_elements_on_screen` | Lo más estable dentro de una sesión. Cámbialo si el árbol cambia. |
| 2. `contentDescription` | Sale de `.semantics { contentDescription = "..." }`. Estable entre corridas. |
| 3. Texto visible | Cambia si se toca la traducción. |
| 4. Coordenadas | Último recurso. Se rompe con cualquier cambio de layout. |

### Limitación dura: el avatar no existe para estas herramientas

`android.webkit.WebView` aparece en el árbol **sin ningún hijo**:

```
@e26 [android.webkit.WebView]
```

El canvas de Three.js no publica nada a accesibilidad. Consecuencia: **no hay
forma de tocar ni inspeccionar el avatar con `mobile-mcp`, con Maestro ni con
`uiautomator`.** Tampoco sirve tomar coordenadas de una captura del avatar: lo
que hay adentro cambia con la animación.

Para el estado de la escena usá estos dos caminos:

- **`tools/dev.sh logcat-tag SignAllWeb 80`** — el bridge JS→Kotlin loguea a ese
  tag (ver `AGENTS.md` § Kotlin ↔ JS bridge).
- **Modo debug de la app** (Ajustes → AVATAR → depurar): enciende el overlay
  `#status` y el grid de la escena. Es lo único que muestra FPS y estado del
  playback en pantalla.

### El WebView se come los toques

`AvatarWebView` es un `AndroidView`, o sea un `View` real del árbol de Android:
toma **cada** toque dentro de sus bounds. Por eso `EdgeDrawer.kt` tiene el
layering que describe el `AGENTS.md` § Gotchas — si un drawer no abre, el
problema es ahí, no en el gesto ni en el agente.

---

## `tools/dev.sh` — el mismo motor por shell

Wrapper fino sobre `mobilecli`. Vive en el repo a propósito: `/tmp` se borra en
cada reinicio y perder el guion obligaría a reconstruir las coordenadas cada
vez.

```bash
tools/dev.sh devices                  # ver dispositivos
tools/dev.sh shot antes               # captura a /tmp/shots/antes.png
tools/dev.sh tree                     # árbol de UI con refs @eN
tools/dev.sh tap 540 1200             # tocar (coordenadas reales del device)
tools/dev.sh text "hola"              # escribir
tools/dev.sh back                     # atrás
tools/dev.sh log 40                   # últimas líneas del device log
tools/dev.sh logcat-tag SignAllWeb 80  # el bridge JS→Kotlin
tools/dev.sh install-debug            # ./gradlew installDebug + relanzar
```

`install-debug` es el subcomando propio de este repo: AppMovil no lo tiene
porque su flujo de build es `flutter run`. Acá es el atajo que el `AGENTS.md`
§ Build exige.

### Sin escala: el factor 1.172 murió

`mobilecli` trabaja en píxeles reales del device (1080×2400), y la captura sale
en la misma unidad que `tap`/`swipe`. Cuando algo no responde, **NO** es la
escala — es que el elemento no estaba donde pensabas o el árbol cambió. Usá
`tools/dev.sh tree` para ver qué hay realmente en pantalla.

### Si `adb` se cuelga

`adb devices` colgado o `Lost connection to device` = murió el servidor de adb:

```bash
adb kill-server && adb start-server && adb devices
```

---

## Maestro — flows repetibles

Maestro `2.11.0`, en `~/.maestro/bin` (ya está en el `.zshrc`). Flow en YAML,
sin código.

```bash
export PATH="$PATH:$HOME/.maestro/bin"
maestro --version
maestro test tools/maestro/smoke-avatar.yaml
```

Los flows viven en `tools/maestro/`.

### Cómo escribir un selector en Compose

Maestro identifica por texto o por `id`. En Compose, el `id` sale del
`contentDescription` del `.semantics {}`:

```kotlin
// Kotlin: esto es lo que Maestro ve como id
Surface(onClick = onOpenHistory, modifier = Modifier.semantics {
    this.contentDescription = "Historial reciente"
})
```

```yaml
# YAML: y así se toca
- tapOn: { id: "Historial reciente" }
```

Los `contentDescription` que ya existen en la app, para no inventar:

| Texto / id | Dónde |
|---|---|
| `Abrir Conversación` | Handle del drawer izquierdo (cerrado) |
| `Abrir Ajustes` | Handle del drawer derecho (cerrado) |
| `Cerrar panel` | Handle del drawer **abierto** — ambiguo, hay dos |
| `Cerrar` | Botón X de cada panel y del bottom sheet |
| `Traducir` | Botón de envío (deshabilitado si el campo está vacío) |
| `Entrada por voz` | Botón del micrófono |
| `Historial reciente` | Icono del historial, en la barra inferior |
| `Repetir reproducción` | El botón "Repetir" sobre el avatar |
| `Reutilizar <frase>` | Item del bottom sheet de historial |
| `Repetir la seña de <frase>` | Item del panel de conversación |
| `Cargando…` | El splash |
| `Volver` | Botón de volver de la pantalla About |

### Dos trampas de selector

**`Cerrar panel` es ambiguo.** Con un drawer abierto, los dos handles pueden
exponer ese texto. `tapOn: "Cerrar panel"` pega en el primero. Usá
`tapOn: { id: "Cerrar" }`, que es el botón X del panel y no se confunde.

**`Traducir` aparece dos veces** en el árbol: el `Surface` con el
`contentDescription` y el `TextView` con el texto. Maestro toma el primero, que
es el correcto, pero si un flow se pone raro, `assertVisible` en el texto y
`tapOn` en el `Surface` es la forma explícita.

### Limitaciones

- Es un ejecutor, no un inspector: no devuelve el árbol, solo ejecuta.
- No recompila. El código hay que instalarlo antes.
- Los flows fallan de forma opaca si el texto cambió: el error es "element not
  found" y no dice qué vio realmente.
- **No ve dentro del WebView**, así que ningún flow puede verificar el avatar.

---

## Rendimiento — medir de verdad

Vive en `tools/rendimiento/`, con su propio
[`README.md`](rendimiento/README.md). Lo mínimo para arrancar:

```bash
# medir en build release: en debug no hay R8 y el número no extrapola
./gradlew installRelease
adb -s 1ed6699f shell am force-stop com.signall.app
adb -s 1ed6699f shell am start -n com.signall.app/.MainActivity

# la principal: reparto por fotograma, latencia de toque, red
tools/rendimiento/gfxinfo.py reproducir --sec 4 \
    --run "adb -s 1ed6699f shell 'input tap 540 2000; sleep 3'"
```

| Script | Para qué |
|---|---|
| `gfxinfo.py` | **La principal.** Reparte cada fotograma de `dumpsys gfxinfo ... framestats` en sus fases (input, animación, traversals, draw, GPU), percentiles contra 16,67 ms, y latencia toque → primer fotograma |
| `sf-medir.sh` | Cuántos fotogramas **llegaron** a tiempo, por `dumpsys SurfaceFlinger --latency` de la capa de la app |
| `frames.py` | Reparte los fotogramas en cubos de tiempo: cuánto dura la animación que dispara una acción y si la app se queda redibujando sola |
| `perfetto.sh` | Captura de trace para atribuir cuando los dos anteriores no alcanzan |

### Por qué acá `gfxinfo` sí funciona

En AppMovil (Flutter) `dumpsys gfxinfo` **no sirve**: da todos los fotogramas en
cero y hay que leer `SurfaceFlinger --latency`. Acá Compose dibuja por el pipeline
normal de Android, así que `gfxinfo framestats` es **la** fuente de verdad, y es
más informativa: trae los timestamps de cada fase del fotograma, no solo el
total.

Verificado contra `com.signall.app` en el Redmi Note 9S: `Total frames
rendered: 332`, con la tabla `PROFILEDATA` de 17 columnas por fotograma.

### La capa de SurfaceFlinger

El nombre de la capa **no** lleva el prefijo `SurfaceView - ` que tiene en
Flutter: el WebView de esta app es un `AndroidView`, no un `SurfaceView`.

```
com.signall.app/com.signall.app.MainActivity#0
```

`sf-medir.sh` lo autodetecta, así que no hay que hardcodearlo.

### La regla de oro de este proyecto

**Un reporte de lentitud sin medición no es un diagnóstico.** El proyecto hermano
se cayó una vez en eso: un antecedente de lentitud de fotogramas que se resolvió
antes **no se reproducía**, y el síntoma real ("todo se siente lento") no era
jank sino espera. El número sin medición es una hipótesis.

El orden correcto es siempre el mismo:

1. **`gfxinfo.py`** para el reparto por fotograma y la latencia de toque.
2. **`sf-medir.sh`** para el mismo gesto desde el punto de vista del ojo.
3. Recién ahí, **`frames.py`** o **`perfetto.sh`** para atribuir.

Y dos avisos que están escritos porque ya costaron una medición entera:

- **`--latency` devuelve el anillo, no el gesto.** Los últimos 128 fotogramas de
  toda la capa. Sin `--latency-clear` antes se mide la transición de entrada de
  la app y los ratos en que nadie la tocó: así se obtuvo un "82 % de fotogramas
  lentos" que no se reproducía. `sf-medir.sh` ya lo limpia siempre.
- **Los timestamps de `--latency` no son el reloj del host ni `/proc/uptime`.**
  Anclar el "primer fotograma" a un reloj da número negativo. Para el instante
  absoluto hay que usar el fotograma claro: el primero de la lista es, por
  definición, el posterior al `--latency-clear`.

---

## pinch

Ni `adb shell input` ni `mobile-mcp` hacen multitáctil: `input tap` y `input
swipe` son de un solo puntero, y `mobile-mcp` no expone pinches.

`tools/pinch.sh` inyecta el pinch por `sendevent`, hablando con el driver de
entrada:

```bash
./tools/pinch.sh 16383 16383 1200 4800 12    # centro, zoom x4
```

Solo funciona donde el nodo de entrada sea escribible (emulador, root). En el
Redmi sin root da `Permission denied`. Para ver el zoom de la pantalla completa
en el emulador, es la única vía.

Los detalles del protocolo — por qué un archivo de evento por dedo, por qué el
número de `dumpsys input` no es el del nodo, y por qué las coordenadas van de 0
a 32767 — están comentados en el propio script.

---

## agent-device — instalado, pero no se usa

Se evaluó [agent-device](https://github.com/callstack/agent-device) (Callstack),
competidor directo de `mobile-mcp`. Instaló bien, `doctor` pasó, detectó los
dispositivos, abrió la app y soporta 40 comandos.

**Pero el comando `snapshot` — sobre el que se apoya casi todo lo demás — falla
siempre:**

```
Android snapshot helper failed: output could not be parsed
```

El log da la causa: `UiAutomationService ... already registered!`. No es MIUI: se
probó también en el emulador limpio y falla igual.

El framework `e2e` (`e2e.tester.army`) **depende de agent-device** para móvil,
así que tampoco funciona. `mobilewright` está instalado pero sin usar: sirve
para escribir tests E2E, que este repo no tiene.