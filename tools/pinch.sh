#!/usr/bin/env bash
# Un pinch de dos dedos por `sendevent`, para el emulador.
#
# ## Por qué existe
#
# Ni `adb shell input` ni el MCP de `mobile-mcp` hacen multitáctil: `input tap`
# y `input swipe` son de un solo puntero, y `mobile-mcp` no expone pinches. La
# única forma de ver el zoom de la pantalla completa en el emulador es hablar con
# el driver de entrada.
#
# ## Por qué un script y no un comando
#
# Porque el emulador de la API 36 expone **un dispositivo de entrada por dedo**:
# `virtio_input_multi_touch_1` es `/dev/input/event2`, el segundo es `event3`, y
# así hasta el once. No hay slots que cambiar: cada dedo es su propio archivo, y
# un pinch es mandar los eventos de los dos archivos. Por eso el `MT_SLOT` no
# aparece en el protocolo de abajo.
#
# Y ojo con el número: `dumpsys input` numera los dispositivos de entrada desde 1
# y ese número **no** es el del nodo. `dumpsys` dice "device 3" para el segundo
# dedo, que es `/dev/input/event3`; el primer dedo es "device 2" y
# `/dev/input/event2`. Se empezó por `event3` y el pinch no hacía nada: los dos
# eventos iban al mismo dedo.
#
# ## Las coordenadas
#
# Los ejes del dispositivo van de 0 a 32767, y Android los mapea linealmente al
# ancho y al alto de la pantalla. El script trabaja en **esa** unidad, así que
# `centro 16383 16383` es el centro de la pantalla sea cual sea la orientación.
#
# ## Uso
#
#   ./tools/pinch.sh <x> <y> <medio separations inicial> <separación final> [pasos]
#
# Las separaciones van en unidades de dispositivo. Ejemplo: un pinch que abre
# desde 1200 a 4800, o sea zoom 4, centrado en la pantalla.
set -euo pipefail

DEV="${DEV:-emulator-5554}"   # solo emulador o root: en un fisico sin root da Permission denied
DEDO1="${DEDO1:-/dev/input/event2}"
DEDO2="${DEDO2:-/dev/input/event3}"

X="${1:?falta x}"
Y="${2:?falta y}"
SEP0="${3:-1200}"
SEP1="${4:-4800}"
PASOS="${5:-12}"

# ABS_MT_POSITION_X=53, ABS_MT_POSITION_Y=54, ABS_MT_TRACKING_ID=57,
# ABS_MT_PRESSURE=58, ABS_MT_TOUCH_MAJOR=48. SYN_REPORT=0 con EV_SYN=0.
ev() { adb -s "$DEV" shell sendevent "$1" "$2" "$3" "$4"; }

# Coloca un dedo en (x, y) y lo levanta si `id` es negativo.
dedo() {
  local evento="$1" x="$2" y="$3" id="$4"
  ev "$evento" 3 53 "$x"
  ev "$evento" 3 54 "$y"
  ev "$evento" 3 48 1280
  ev "$evento" 3 58 800
  ev "$evento" 3 57 "$id"
  ev "$evento" 0 0 0
}

# Los dos dedos se apoyan casi juntos: un frame cada uno, y el pinch arranca en
# el frame siguiente.
dedo "$DEDO1" $((X - SEP0 / 2)) "$Y" 1
dedo "$DEDO2" $((X + SEP0 / 2)) "$Y" 2

paso=$(( (SEP1 - SEP0) / PASOS ))
[ "$paso" -eq 0 ] && paso=1
sep="$SEP0"
for _ in $(seq 1 "$PASOS"); do
  sep=$((sep + paso))
  dedo "$DEDO1" $((X - sep / 2)) "$Y" 1
  dedo "$DEDO2" $((X + sep / 2)) "$Y" 2
done

# Y se levantan. Sin esto el dedo queda apoyado y el siguiente `input tap` de
# la sesión lo encuentra como un puntero ya abajo.
dedo "$DEDO1" $((X - sep / 2)) "$Y" -1
dedo "$DEDO2" $((X + sep / 2)) "$Y" -1

echo "pinch $SEP0 -> $SEP1 en ($X, $Y) [zoom x$((SEP1 / SEP0))]"
