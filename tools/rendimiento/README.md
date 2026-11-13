# Rendimiento: cómo medir de verdad

Herramientas para medir el rendimiento de la app **Android nativa** en un
dispositivo real.

Nacen del equivalente que existe en el repo hermano `AppMovil`, **adaptado**:
las de acá no hablan con una VM de Dart sino con el pipeline de Android. Por
eso una cosa cambia de signo: en Flutter `dumpsys gfxinfo` **no sirve** y acá
**es la fuente de verdad**.

## La regla de oro antes de medir nada

**Medí en build release, no en debug.** En debug no hay R8 ni optimizaciones de
código, y el número no extrapola a lo que siente el usuario. En debug solo se
miden gestos **entre sí**, porque la inflación es común a todos.

```bash
./gradlew installRelease
adb -s 1ed6699f shell am force-stop com.signall.app
adb -s 1ed6699f shell am start -n com.signall.app/.MainActivity
```

Y para partir de cero en cada medición, todos los scripts resetean el contador
con `dumpsys gfxinfo <pkg> reset`: sin eso se mide todo lo que hizo la app desde
que arrancó, no el gesto.

## Qué hay acá

| Script | Para qué |
|---|---|
| **`gfxinfo.py`** | **La principal.** Reparte cada fotograma en sus fases (espera de entrada, animación, measure+layout, draw, sync, GPU, present), percentiles contra el presupuesto de 16,67 ms, y la latencia real de respuesta al toque. |
| `sf-medir.sh` | Cuántos fotogramas **llegaron** a tiempo, por `dumpsys SurfaceFlinger --latency` de la capa de la app. Complementa a `gfxinfo.py`: ese dice cuánto trabajo hizo la app, este dice cuántos fotogramas salieron. |
| `frames.py` | Reparte los fotogramas en cubos de tiempo: cuánto duró la animación que dispara una acción, y si la app se queda redibujando sola. |
| `perfetto.sh` | Captura de trace para atribuir cuando los dos anteriores ya dijeron qué pero no dónde. |

## Los tres números que hay que mirar siempre

1. **Reparto por fotograma** (`gfxinfo.py`): mediana y p90 contra el presupuesto
   de **16,67 ms** a 60 Hz. El que se pasa es jank.
2. **Latencia toque → primer fotograma**. Si está alta, el problema es de
   entrada o de red, no de dibujo. Sale de `OldestInputEvent`, que es la marca
   que puso el propio input de Android en el evento, así que no está
   contaminada por los cientos de ms que tarda `adb shell input` desde el host.
3. **Vuelta de cada petición de red** — acá es la llamada al backend de SignAll.
   Separa "la app tarda" de "la red tarda". En una app con esqueleto de carga
   son dos problemas distintos y se arreglan distinto.

Y una cuarta, que `gfxinfo.py` reporta aparte porque se esconde:

4. **La espera de la entrada no está dentro de `TOTAL`.** Ocurre *antes* de
   `IntendedVsync`, así que el reparto del fotograma no la muestra, y sin
   embargo se come presupuesto y hace que el fotograma llegue tarde. `gfxinfo.py`
   la imprime como bloque propio: si el jank viene de ahí, el problema es de
   entrada o de lo que esté tapando el hilo de UI, no de dibujado.

## Recetas

Un scroll o un arrastre de drawer (el caso de referencia, para comparar):

```bash
tools/rendimiento/gfxinfo.py "abrir-historial" --sec 4 --jsonl /tmp/t.jsonl \
    --run "adb -s 1ed6699f shell 'input swipe 200 1200 900 1200 400; sleep 1'"
tools/rendimiento/frames.py /tmp/t.jsonl
```

Reproducir una seña, con la red incluida:

```bash
tools/rendimiento/gfxinfo.py reproducir --sec 8 \
    --run "adb -s 1ed6699f shell 'input tap 540 2000; sleep 5'"
```

Y el mismo gesto desde el punto de vista del ojo:

```bash
tools/rendimiento/sf-medir.sh reproducir 8 -- \
    "adb -s 1ed6699f shell 'input tap 540 2000; sleep 5'"
```

Jank en reposo, para ver si la app se redibuja sola:

```bash
tools/rendimiento/gfxinfo.py reposo --sec 5
tools/rendimiento/frames.py /tmp/reposo.jsonl
```

## Trampas que ya se pisaron

- **`dumpsys gfxinfo` no sirve para Flutter.** Da todos los fotogramas en cero.
  Acá sí funciona, y es **más** informativo que `SurfaceFlinger --latency`,
  porque trae los timestamps de cada fase del fotograma. No copies el error del
  repo hermano: acá la herramienta buena es la otra.
- **`--latency` devuelve el anillo, no el gesto.** Los últimos 128 fotogramas de
  toda la capa. Sin `--latency-clear` antes se mide la transición de entrada de
  la app y los ratos en que nadie la tocó: así se obtuvo un "82 % de fotogramas
  lentos" que no se reproducía. `sf-medir.sh` ya lo limpia siempre.
- **Los timestamps del framestats están en el dominio `Uptime:` que imprime el
  encabezado de `gfxinfo`.** No son los de `/proc/uptime` ni los de `date`. En
  este device el encabezado marca ~445477 s y `/proc/uptime` marca ~3141904 s:
  son relojes distintos y anclar con el host da número negativo. Por eso
  `gfxinfo.py` saca el anclaje del propio encabezado, que es el mismo reloj por
  definición.
- **`adb shell` se come las comillas del nombre de la capa.** Re-armatea la
  línea de comando, y `dumpsys` lee "-" como nombre y no encuentra nada. Por eso
  el nombre va con comillas simples adentro de un argumento entre comillas
  dobles.
- **El nombre de la capa no lleva `SurfaceView - `.** En Flutter sí, porque el
  motor abre un `SurfaceView`. Acá el WebView es un `AndroidView`, así que la
  capa es la de la activity: `com.signall.app/com.signall.app.MainActivity#0`.
  `sf-medir.sh` la autodetecta.
- **`gfxinfo.py` y `sf-medir.sh` pueden discrepar, y la discrepancia es
  información.** En la misma medición (un arrastre de drawer) `gfxinfo.py` dio
  12,5 % de jank y `sf-medir.sh` dio 100 % de fotogramas a tiempo con un máximo
  de 17,2 ms. No es un bug de ninguno de los dos: `gfxinfo.py` marca jank cuando
  `FrameCompleted > IntendedVsync + 16,67 ms`, o sea si el fotograma terminó
  *después* de su deadline, mientras que `sf-medir.sh` mide el intervalo entre
  presentaciones. El caso "termina 1 ms tarde en todos los fotogramas pero igual
  llega uno por vsync" es exactamente ese, y es el que importa: la app está al
  borde. Si los dos dan números muy distintos, el de `sf-medir` es el que ve el
  ojo y el de `gfxinfo` es el que dice cuánto margen sobra.
- **La captura sale en 1080 px pero la que se muestra después viene reescalada.**
  Sacar coordenadas de mirar la captura hace tocar cualquier otra cosa. Sacarlas
  de `mobile_list_elements_on_screen`, que están en píxeles reales. Ya se tocó
  un campo equivocado por esto.

## Lo que no se puede medir acá

- **Lo que pasa adentro del WebView.** El render loop de Three.js corre en el
  hilo de UI del proceso, pero Android lo reporta como un solo filtro grande:
  `gfxinfo.py` ve "el hilo de UI tardó 13 ms", no "Three.js tardó 13 ms". Para
  separar eso hay que usar el **modo debug de la app** (Ajustes → AVATAR →
  depurar), que muestra el FPS en el overlay `#status`.
- **Un pinch real en un teléfono sin root.** `sendevent` da `Permission denied`
  sobre el nodo de entrada. En el emulador sí: `tools/pinch.sh`.
- **La red del usuario en campo.** Los tiempos de ida y vuelta que salen son de
  la red de la máquina donde se mide, no necesariamente la del dispositivo en
  el campo.