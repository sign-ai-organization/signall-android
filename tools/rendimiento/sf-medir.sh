#!/usr/bin/env bash
# Mide los fotogramas **presentados** de la capa de pantalla de la app, que es lo
# que ve el ojo. Es el complemento de `gfxinfo.py`: ese dice cuanto trabajo hizo
# la app fase por fase, este dice cuantos fotogramas llegaron a tiempo.
#
# ## Por que las dos
#
# Se puede tener toda la app dentro de presupuesto y igual ver tirones, si el
# compositor nopresenta. Y al reves: jank en el codigo que no llega a notarse.
# Son dos preguntas distintas y hay que responder las dos.
#
# ## Por que `--latency-clear` antes de TODO
#
# `dumpsys SurfaceFlinger --latency` devuelve el **anillo de los ultimos 128
# fotogramas de toda la capa**, no los del gesto. Sin limpiarlo se mide la
# transicion de entrada de la app y los ratos en que nadie la toco. Ya se cayo
# en esa trampa una vez en el repo hermano: dio "82 % de fotogramas lentos"
# cuando el numero real era 100 % a 60 FPS.
#
# ## Por que sortea la trampa de las comillas
#
# `adb shell` rearma la linea de comando y se come las comillas del nombre de la
# capa, que tiene espacios o caracteres raros, y `dumpsys` lee "-" como nombre y
# no encuentra nada. Por eso el nombre va con comillas simples adentro de un
# argumento entre comillas dobles.
#
# ## OJO con el reloj
#
# Los timestamps de `--latency` NO son los de `date +%s%N` ni los de
# `/proc/uptime`. Anclar el "primer fotograma" con un reloj del host da numero
# negativo. Para el instante absoluto hay que usar el fotograma claro: el
# primero de la lista es, por definicion, el posterior al `--latency-clear`.
#
# ## El nombre de la capa
#
# Aca NO lleva el prefijo `SurfaceView - ` que tiene en Flutter: el WebView de
# esta app es un `AndroidView`, no un `SurfaceView`, asi que la capa es la de la
# activity. El nombre se autodetecta, y si no aparece, el script lista las
# candidatas en vez de fallar en silencio.
#
# ## Uso
#   tools/rendimiento/sf-medir.sh <etiqueta> [segundos] -- [comando del gesto]
#
#   <etiqueta>  nombre de la medicion, sale en el resumen
#   segundos    ventana de medicion (default 3)
#   --          a partir de aca va el comando que produce el gesto, que corre en
#               el HOST (no en el dispositivo)
#
#   # un scroll de 3 s
#   tools/rendimiento/sf-medir.sh scroll 3 -- \
#       "adb -s 1ed6699f shell 'input swipe 540 1900 540 700 3000'"
set -u

PKG="${APP_PKG:-com.signai.webview}"
DEV="${DEV:-$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')}"
ETIQUETA="${1:?falta la etiqueta}"
DUR="${2:-3}"
if [ "${3:-}" = "--" ]; then
  shift 3
else
  shift 2 2>/dev/null || shift 1
fi

OUT="${TMPDIR:-/tmp}/sf-${ETIQUETA}.txt"

# El nombre canonico de la capa de la activity. Se prueba primero y, si no
# responde, se busca entre las que reporta SurfaceFlinger.
CAPA="$PKG/$PKG.MainActivity#0"

sf() { adb -s "$DEV" shell "dumpsys SurfaceFlinger '$1' '$CAPA'" 2>/dev/null; }

if ! sf --latency | sed -n '2p' | grep -qx "$CAPA"; then
  # Probar con la activity corta, que es como la nombra --list en algunas
  # versiones.
  CAPA2="$PKG/.MainActivity#0"
  if sf --latency | sed -n '2p' | grep -qx "$CAPA2"; then
    CAPA="$CAPA2"
  else
    echo "### $ETIQUETA: la capa '$CAPA' no respondio. Capas de la app:"
    adb -s "$DEV" shell dumpsys SurfaceFlinger --list 2>/dev/null | grep -i "$PKG"
    echo "Pasale el nombre exacto de esa capa como segundo argumento."
    exit 1
  fi
fi

# El gesto corre en el HOST, no en el dispositivo: `adb` es una herramienta del
# host y no existe en el celu. Por eso el clear y el read son dos llamadas
# separadas y no un unico script remoto. No molesta para lo que se mide aca (las
# diferencias entre fotogramas y la cuenta), y el instante absoluto no se usa:
# ver la nota del reloj mas arriba.
sf --latency-clear >/dev/null
if [ "$#" -gt 0 ]; then
  ( eval "$*" >/dev/null 2>&1 ) &
  ACCION=$!
fi
sleep "$DUR"
sf --latency > "$OUT"
[ "$#" -gt 0 ] && wait "$ACCION" 2>/dev/null

python3 - "$OUT" "$ETIQUETA" <<'PY'
import sys, collections

ruta, etiqueta = sys.argv[1], sys.argv[2]
lineas = [l.rstrip() for l in open(ruta) if l.strip()]
if len(lineas) < 3:
    print(f"### {etiqueta}: sin datos de la capa")
    raise SystemExit

try:
    refresco = int(lineas[0])
except ValueError:
    print(f"### {etiqueta}: renglon de refresco ilegible: {lineas[0]!r}")
    raise SystemExit
if refresco == 0:
    print(f"### {etiqueta}: la pantalla no estaba presenting (refresh 0)")
    raise SystemExit

frames = []
for l in lineas[1:]:
    p = l.split()
    if len(p) != 3:
        continue
    try:
        pt = int(p[1])
    except ValueError:
        continue
    if pt not in (0, 9223372036854775807):
        frames.append(pt)

if len(frames) < 2:
    print(f"### {etiqueta}: sin fotogramas utiles "
          f"(la app no dibujo nada en la ventana, o la capa no es la que dibuja)")
    raise SystemExit

# `refresco` es el periodo en NANOSEGUNDOS. El presupuesto en ms sale
# de ahi, no al reves: 16666666 ns -> 16.67 ms. (1e9/refresco daba 60.)
presupuesto = refresco / 1e6


def bucket(d):
    if d < presupuesto * 1.5:  return "a tiempo"
    if d < presupuesto * 2.5:  return "1 frame tarde"
    if d < presupuesto * 4.5:  return "2-3 frames tarde"
    return "4+ frames tarde"


gaps = [(b - a) / 1e6 for a, b in zip(frames, frames[1:]) if b > a]
buckets = collections.Counter(bucket(g) for g in gaps)
n = len(gaps)
orden = ["a tiempo", "1 frame tarde", "2-3 frames tarde", "4+ frames tarde"]
g = sorted(gaps)

print(f"### {etiqueta}   (fotogramas que llegaron a pantalla)")
print(f"    fotogramas en la ventana: {len(frames)}   "
      f"refresco declarado: {refresco/1e6:.2f} ms "
      f"({1e9/refresco:.0f} Hz)")
for b in orden:
    c = buckets.get(b, 0)
    if c:
        print(f"    {c:4d}  ({100*c/n:5.1f} %)  {b}")
print(f"    entre fotogramas: mediana {g[n//2]:.1f} ms   "
      f"p90 {g[int(n*0.9)]:.1f} ms   max {g[-1]:.1f} ms")
print(f"    presupuesto del panel: {presupuesto:.2f} ms")
PY