---
name: controlar-app-movil
description: Conducir la app Android nativa de SignAI (Kotlin/Compose + WebView) en el emulador o en el dispositivo físico - compilarla, instalarla, tocar pantallas, leer el árbol de accesibilidad, verificar cambios en pantalla y medir rendimiento. Usar cuando haya que probar, verificar, depurar o mostrar en pantalla algo de la app, cuando el usuario pida "probá en el móvil", "levantá la app", "instalala y probala", "verificá en el dispositivo", o cuando reporte que la app se siente lenta o va lento.
---

# Controlar la app móvil

Este repo es **Android nativo** (Kotlin + Compose), no Flutter. Eso cambia el
tooling: no hay VM service, no hay hot reload y no hay `flutter run`. Aplicar un
cambio es `./gradlew installDebug` y volver a lanzar.

| ¿Qué necesitás? | Usá |
|---|---|
| Tocar la app: botones, campos, drawers | **mobile-mcp** (`mobile_list_elements_on_screen` y después tocar) |
| Compilar + instalar + relanzar | **`tools/dev.sh install-debug`** (= `./gradlew installDebug`) |
| Lo que no sea la app: sistema, launcher, otra app | **mobile-mcp** |
| Un guion idéntico en cada corrida | **maestro** (YAML en `tools/maestro/`) |
| **Medir si la app va lenta** | **`tools/rendimiento/`** |
| Un pinch de dos dedos | `tools/pinch.sh` (solo emulador) |

Fallback si el MCP no está disponible: `tools/dev.sh`, que habla con el mismo
`mobilecli` por debajo.

## Reglas que te van a ahorrar tiempo

**Nunca adivines coordenadas.** Leé el árbol primero
(`mobile_list_elements_on_screen`) y usá los `ref` que devuelve. Sacá las
coordenadas de ahí, no de una captura.

**Sin escala.** `mobilecli` trabaja en píxeles reales del device (1080×2400 en el
Redmi). Capturas y `tap`/`swipe` están en la misma unidad. Si algo no responde,
**no** es un factor de escala: es que el árbol cambió o el elemento no estaba
donde pensabas.

**El avatar 3D es invisible para toda esta instrumentación.** La capa
`android.webkit.WebView` aparece en el árbol **sin hijos**: el canvas de Three.js
no expone nada a accesibilidad. No vas a poder tocar ni leer el avatar con
`mobile-mcp` ni con Maestro. Para el estado de la escena hay dos caminos, y
ninguno es el árbol:

- `tools/dev.sh logcat-tag SignAIWeb 80` — el bridge JS→Kotlin loguea a ese tag.
- Prender el modo debug de la app (Ajustes → AVATAR → depurar) y leer el overlay
  `#status`, que es parte de la escena.

**El WebView se come los toques.** Es un `AndroidView`, o sea un View real: toma
cada toque dentro de sus bounds. Si un drawer no abre, el problema es el
layering de `EdgeDrawer.kt` (ver el comentario `ORO` de ese archivo y el
`AGENTS.md` § Gotchas), no el gesto.

**Los dos handles de drawer comparten texto cuando están abiertos.** Con un
drawer abierto, el handle del drawer abierto dice `"Cerrar panel"`. Un
`tapOn: "Cerrar panel"` de Maestro pega en el primero que encuentra. Cerrá con el
botón `"Cerrar"` del panel, que es inequívoco.

**`Traducir` empieza deshabilitado** si el campo está vacío. En el árbol aparece
como `(disabled)`. Escribí algo antes de esperar que se habilite.

**Tocar un campo por `adb` a veces no abre el teclado a la primera**: el primer
tap solo le da el foco. Toca dos veces.

**MIUI.** El Redmi Note 9S corre MIUI (Android 11), que mete mano en permisos y a
veces deja instalaciones a medias o salta diálogos que truncan la sesión. Si algo
falla raro, **no asumas que el código está mal**: mirá si el teléfono pidió algo.

## Elegí el dispositivo

```bash
adb devices    # 1ed6699f = Redmi Note 9S (MIUI), emulator-5554 = VMApp_API36
```

- **Si hay un físico conectado, usalo.** Siempre.
- Si no hay ninguno, **preguntá antes** de arrancar el emulador: *"¿Querés que
  use el emulador `VMApp_API36` ahora?"*
- Si el usuario confirma y el emulador no está iniciado, iniciá `VMApp_API36`. Si
  ya está iniciado, **no lo reinicies**.

## Compilar, instalar y relanzar

El `AGENTS.md` § Build dice que es **obligatorio** instalar después de cada
cambio. Es un solo comando:

```bash
tools/dev.sh install-debug
```

Eso corre `./gradlew installDebug`, termina la app y la vuelve a lanzar. Para
las partes por separado:

```bash
./gradlew installDebug                          # solo compilar + instalar
tools/dev.sh launch                              # relanzar (com.signai.webview/.MainActivity)
tools/dev.sh terminate                           # cerrar la app
tools/dev.sh install app/build/outputs/apk/debug/app-debug.apk
```

`install-debug` corre Gradle **en primer plano** y puede tardar minutos. Para
builds largos, desmembralo y andá leyendo el log:

```bash
cd /home/diegocachy/tesisTesteos/SignAI/webview
setsid nohup ./gradlew installDebug > /tmp/gradle.log 2>&1 < /dev/null &
tail -f /tmp/gradle.log
```

## Workflow típico: tocar algo y verificarlo

```ts
// 1. Ver qué hay en pantalla (SIEMPRE antes de tocar)
//    -> Element @e31 [android.view.View] "Historial reciente"
//    -> Element @e33 [android.view.View] "Traducir"  (disabled)

// 2. Tocar por ref, sin coordenadas
mobile_click_on_screen_at_coordinates({ ref: "@e31" })

// 3. Confirmar que pasó: el árbol ya cambió
mobile_list_elements_on_screen({})
```

Y el loop completo de "verificar un cambio":

```bash
tools/dev.sh install-debug
tools/dev.sh shot post-build
tools/dev.sh logcat-tag SignAIWeb 40
```

## Si la app no levanta

Por orden, y sin adivinar:

```bash
tools/dev.sh logcat-tag SignAIWeb 60     # el bridge JS→Kotlin
adb -s 1ed6699f logcat -d AndroidRuntime:E System.err:W '*:S' | tail -40
tools/dev.sh fg                          # ¿está en foreground?
```

`adb devices` colgado o `Lost connection to device` = murió el servidor de adb:
`adb kill-server && adb start-server && adb devices`.

## Verificar que lo que escribiste persistió

No hay backend contra el que comparar en este repo, pero sí almacenamiento local.
La historia y los ajustes viven en SharedPreferences `signai/history`:

```bash
adb -s 1ed6699f shell "run-as com.signai.webview cat /data/data/com.signai.webview/shared_prefs/signai.xml"
```

`run-as` solo funciona en builds debug. Para probar una escritura de verdad,
recorré el flujo en la app y después leé el XML: es el equivalente acá de
"verificar contra la base" que en el otro repo se hace por SQL.

## Maestro — flows repetibles

```bash
export PATH="$PATH:$HOME/.maestro/bin"
maestro test tools/maestro/smoke-avatar.yaml
```

Flujos en `tools/maestro/`. Usalo cuando el guion tiene que ser **idéntico**
cada corrida (smoke de arranque, regresión). Maestro identifica por texto o por
`id`, y en Compose el `id` sale del `contentDescription` del `.semantics {}`.
Si el guion cambia con la pantalla, `mobile-mcp` se adapta mejor.

Limitaciones: Maestro no devuelve el árbol, no ve dentro del WebView, y falla
opaco si un texto cambió.

## Si el reporte es "la app se siente lenta" — medí antes de decir nada

Este proyecto ya se cayó una vez en eso: un antecedente de lentitud que se dio
por resuelto **no se reproducía**, y el síntoma real no era jank sino espera. Un
número sin medición es una hipótesis.

```bash
# 1. la app en build release: en debug no hay optimizaciones de R8 y el
#    número no extrapola a lo que siente el usuario
./gradlew installRelease
adb -s 1ed6699f shell am force-stop com.signai.webview
adb -s 1ed6699f shell am start -n com.signai.webview/.MainActivity

# 2. medir
tools/rendimiento/gfxinfo.py reproducir --sec 4 \
    --run "adb -s 1ed6699f shell 'input tap 540 2000; sleep 3'"
tools/rendimiento/sf-medir.sh reproducir 4 -- \
    "adb -s 1ed6699f shell 'input tap 540 2000; sleep 3'"
```

Los tres números que hay que mirar siempre:

1. **Reparto por fotograma** contra el presupuesto de **16,67 ms**. El que se
   pasa es jank.
2. **Latencia toque → primer fotograma**. Si está alta, el problema es de entrada
   o de red, no de dibujo.
3. **Vuelta de cada petición de red** — acá es la llamada al backend de
   SignAI. Separa "la app tarda" de "la red tarda".

Dos trampas que ya costaron una medición entera en el repo hermano:

- **`dumpsys gfxinfo` no sirve para Flutter**: da todos los fotogramas en cero.
  **Acá sí sirve**, porque Compose dibuja por el pipeline normal de Android. Es la
  lectura principal, y no la de `SurfaceFlinger`.
- **`--latency` devuelve el anillo de los últimos 128 fotogramas de toda la
  capa**, no los del gesto. Sin `--latency-clear` antes, se mide la transición de
  entrada de la app.

Detalle, recetas y el resto de las trampas:
[`tools/rendimiento/README.md`](../../../tools/rendimiento/README.md).

## Herramientas instaladas y descartadas — no las propongas

- **`agent-device`** (Callstack): instalado, `doctor` pasa, pero `snapshot` falla
  siempre con `UiAutomationService already registered`. Se reproduce también en el
  emulador limpio, así que no es MIUI. **No funciona.**
- **`e2e`** (`e2e.tester.army`): su motor móvil depende de agent-device.
  **No funciona.**
- **`mobilewright`**: instalado, sin usar. Sirve para escribir tests E2E, que no
  hacemos.

## Para más detalle

- [`tools/HERRAMIENTAS.md`](../../../tools/HERRAMIENTAS.md) — referencia completa:
  las tools de `mobile-mcp`, las diferencias entre las cuatro herramientas, cómo
  tocar dentro de Compose, y cómo se ve la capa WebView.
- [`tools/rendimiento/README.md`](../../../tools/rendimiento/README.md) — las
  herramientas de medir rendimiento: qué mide cada una, las recetas y las
  trampas ya pisadas.
- [`tools/README.md`](../../../tools/README.md) — arranque del emulador,
  recuperación cuando adb se cuelga, cómo operar desde un agente.