#!/usr/bin/env bash
# Captura un trace de Perfetto del device, para cuando `gfxinfo.py` y
# `sf-medir.sh` ya dijeron QUE hay jank pero no donde.
#
# ## Cuando hace falta
#
# `gfxinfo.py` da el reparto por fase. `sf-medir.sh` da cuantos fotogramas
# llegaron. Entre los dos contestan casi siempre. Perfetto es para el resto:
#
# - el jank viene de un hilo que no es el de UI ni el de render (GC, un
#   executor, un hilo del WebView)
# - hay contention de lock entre el hilo de UI y el de render
# - hay jank en reposo y hay que ver quien esta despierto
#
# ## Uso
#
#   tools/rendimiento/perfetto.sh <etiqueta> [segundos] [categorias]
#
#   tools/rendimiento/perfetto.sh reproducir 8
#   tools/rendimiento/perfetto.sh reposo 5 "sched freq idle gfx view am wm"
#
# El trace queda en ~/perfetto-traces/perfetto-<etiqueta>.perfetto-trace y se
# abre arrastrandolo a https://ui.perfetto.dev (no hay que subir nada).
#
# ## No hace falta root
#
# Android 9+ escribe solo: el `perfetto` del device delega en el servicio
# `traced_probes`, que tiene permisos para /data/misc/perfetto-traces. Verificado
# sin root en el Redmi Note 9S (Android 11).
#
# Es un trace de TODO el sistema, no solo de la app: eso es a proposito, porque
# para atribuir jank importa ver quien COMPITE por el hilo. En la UI se filtra
# por el proceso (el PID esta en el titulo de cada pista).
#
# ## Cosas que ya se saben de este trace
#
# - El render loop de Three.js vive DENTRO del WebView y corre en el hilo de UI
#   del proceso, no en uno propio. Por eso un gesto sobre el avatar compite con
#   el render de la escena por el mismo hilo, y por eso el filtro de CPU en
#   `gfxinfo.py` aparece grande. No es un bug: es como esta armado.
# - La categoria `gfx` sola no alcanza. Hay que pedir `view` y `sched` ademas,
#   porque lo que hace el canvas pasa por SurfaceFlinger y no por el pipeline de
#   vistas de Compose.
set -euo pipefail

PKG="${APP_PKG:-com.signall.app}"
DEV="${DEV:-$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')}"
ETIQUETA="${1:?falta la etiqueta}"
DUR="${2:-6}"
CATS="${3:-sched freq idle gfx view am wm}"

SALIDA="${PERFETTO_OUT_DIR:-$HOME/perfetto-traces}"
mkdir -p "$SALIDA"
RUTA="$SALIDA/perfetto-${ETIQUETA}.perfetto-trace"
REMOTO=/data/misc/perfetto-traces/perfetto-signai

echo "### capturando ${DUR}s [$CATS] en $DEV para $PKG"
adb -s "$DEV" shell "perfetto -o $REMOTO -t ${DUR}s $CATS"

adb -s "$DEV" shell ls -l "$REMOTO"
adb -s "$DEV" pull "$REMOTO" "$RUTA"
adb -s "$DEV" shell rm -f "$REMOTO"

echo
echo "### trace en $RUTA"
echo "### abrir arrastrandolo a https://ui.perfetto.dev"
echo "### En el trace, buscar el proceso $PKG y filtrar por 'View' para ver"
echo "### donde se va el tiempo del hilo de UI."