# Tooling del agente: control del móvil

**Día:** martes
**Fecha:** 2026-10-13
**Hora:** 18:30

## Qué se hizo

Se replicó el stack de control de móvil del repo `AppMovil` a este repo, **adaptado
a Android nativo**. AppMovil es Flutter; acá no hay ni una línea de Dart, así que
`dart-mcp`, `marionette`, `flutter run --profile` y `timeline.py` (que habla la
timeline del motor de Flutter) **no aplican** y no se copiaron. Lo que habla
Android — `mobilecli`, `mobile-mcp`, Maestro, `sendevent` — sí se copió.

Lo que ya existía y se dejó como estaba: `tools/dev.sh` y el MCP `mobile-mcp` en
`opencode.json`. Los dos ya eran equivalentes a los de AppMovil (`dev.sh` incluso
tiene `install-debug` y `logcat-tag`, que allá no existen porque su flujo de build
es `flutter run`).

### Archivos nuevos

| Archivo | Qué es |
|---|---|
| `.opencode/skills/controlar-app-movil/SKILL.md` | El skill que carga un agente para operar el device |
| `tools/HERRAMIENTAS.md` | Referencia completa del stack: tools de `mobile-mcp`, tabla de `contentDescription`, trampas |
| `tools/rendimiento/gfxinfo.py` | **La principal.** Reparto por fotograma con `dumpsys gfxinfo framestats` + latencia de toque |
| `tools/rendimiento/sf-medir.sh` | Fotogramas que llegaron a pantalla (`SurfaceFlinger --latency`) |
| `tools/rendimiento/frames.py` | Duración de la animación y repintado en reposo |
| `tools/rendimiento/perfetto.sh` | Trace para atribuir |
| `tools/rendimiento/README.md` | Recetas y trampas de medición |
| `tools/pinch.sh` | Pinch de dos dedos por `sendevent` (emulador/root) |
| `tools/maestro/{smoke-avatar,drawers,historial,ejemplo-flow}.yaml` | Flows repetibles |

`tools/README.md` y el § "Tooling del agente" de `AGENTS.md` quedaron actualizados.

## Lo que se decidió

- **El skill se llama igual (`controlar-app-movil`) pero es otro.** Se puso en
  `webview/.opencode/skills/` y no en la raíz de SignAI, porque webview es el único
  repo con device y con `./gradlew installDebug`.
- **`dumpsys gfxinfo` acá es la fuente de verdad, al revés que en Flutter.** En
  AppMovil no sirve (da todos los fotogramas en cero) y hay que leer
  `SurfaceFlinger`. Acá Compose dibuja por el pipeline normal de Android, así que
  `gfxinfo framestats` es la principal y además es más informativa: trae los
  timestamps de cada fase del fotograma.
- **La capa de SurfaceFlinger no lleva el prefijo `SurfaceView - `** que tiene en
  Flutter, porque el WebView es un `AndroidView`, no un `SurfaceView`. Se
  autodetecta.
- **Midiendo en build release, no en debug.** Es el equivalente de lo que allá
  decía "en `--profile`, nunca en debug", sin `--profile` que exista.

## Lo que se descubrió probando en el Redmi Note 9S

1. **El avatar 3D es invisible para toda esta instrumentación.** En el árbol sale
   como `android.webkit.WebView` **sin ningún hijo**. No se puede tocar ni
   inspeccionar con `mobile-mcp`, Maestro ni `uiautomator`. Quedó escrito en el
   `SKILL.md`, en `HERRAMIENTAS.md`, en `tools/README.md` y en `AGENTS.md`: para el
   estado de la escena hay que usar el tag `SignAIWeb` de logcat o el modo debug de
   la app (Ajustes → AVATAR → depurar).
2. **Maestro no anda en este Redmi (MIUI).** El driver de Android revienta al
   arrancar:
   `FATAL EXCEPTION: Instr: androidx.test.runner.AndroidJUnitRunner ...
   IllegalStateException: Cannot call disconnect() while connecting UiAutomation`.
   Los cuatro flows quedan escritos y correctos, pero hay que correrlos en un
   emulador con imagen AOSP. Es el mismo problema de fondo que el
   `UiAutomationService already registered` de `agent-device`.
3. **Los timestamps del framestats de `gfxinfo` están en el dominio `Uptime:` del
   encabezado, no en `/proc/uptime`.** En el device: el encabezado marca
   ~445477 s y `/proc/uptime` ~3141904 s. Anclar con un reloj del host da número
   negativo. `gfxinfo.py` saca el anclaje del propio encabezado, que es el mismo
   reloj por definición.
4. **La espera de la entrada no está dentro de `TOTAL`.** Ocurre antes de
   `IntendedVsync`, así que el reparto del fotograma no la muestra, y sin embargo
   se come presupuesto. `gfxinfo.py` la reporta como bloque propio.
5. **`gfxinfo.py` y `sf-medir.sh` pueden discrepar, y la discrepancia es
   información.** En la misma medición: `gfxinfo.py` dio 12,5 % de jank,
   `sf-medir.sh` dio 100 % a tiempo con máximo de 17,2 ms. `gfxinfo` mide si el
   fotograma terminó después de su deadline; `sf-medir` mide el intervalo entre
   presentaciones. "Termina 1 ms tarde en todos los fotogramas pero igual llega
   uno por vsync" produce exactamente ese par de números, y es el caso de la app
   al borde.
6. **Perfetto funciona sin root** en Android 9+: el `perfetto` del device delega
   en `traced_probes`, que puede escribir en `/data/misc/perfetto-traces`.
   Verificado en el Redmi (Android 11).
7. **El render loop de Three.js corre en el hilo de UI, no en uno propio.** Se ve
   en la medición: ~59,4 fotogramas por segundo parejos en reposo, y el filtro de
   UI de `gfxinfo` grande. No es un bug — es como está armado el WebView.

## Verificado en el dispositivo

- `tools/rendimiento/gfxinfo.py`, `sf-medir.sh`, `frames.py` y `perfetto.sh`:
  los cuatro corrieron contra `com.signall.app` en `1ed6699f` y dieron
  números coherentes.
- El árbol de accesibilidad expone los `contentDescription` que usan los flows:
  `Abrir Conversación`, `Abrir Ajustes`, `Cerrar`, `Traducir` (con su `TextView`
  gemela y `(disabled)` cuando el campo está vacío), `Entrada por voz`,
  `Historial reciente`, `Escribe una frase...`.

## Pendiente

- **Correr los flows de Maestro en un emulador.** No se pudo en el Redmi por el
  punto 2. Hay que arrancar `VMApp_API36` y pasarlos una vez para confirmar que los
  `contentDescription` andan como selector.
- **`historial.yaml` y `drawers.yaml` no se ejecutaron nunca**, por lo mismo. El
  contenido está verificado contra el código y contra el árbol del device, pero no
  contra una corrida real de Maestro.
- `historial.yaml` asume que el backend responde. Con el backend caído la pantalla
  muestra "Sin conexión con el servidor" y el flow falla en el `assertVisible` del
  resultado: es el fallo correcto, pero conviene tenerlo presente al correrlo.