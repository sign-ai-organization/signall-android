#!/usr/bin/env zsh
# Ayudas para manejar el emulador/dispositivo Android en este repo (SignAll
# WebView, Kotlin/Gradle), basadas en mobilecli
# (https://github.com/mobile-next/mobilecli).
#
# Sustituye el flujo manual de `adb exec-out`, `adb shell input tap`, etc.
# Internamente habla con `mobilecli`, igual que el de AppMovil — misma
# interfaz, mismos binarios. NO envuelve `./gradlew`: eso sigue siendo
# directo. El wrapper solo cubre la parte "device": instalar el APK, lanzar
# la app, leer logs, tomar capturas, etc.
#
# NO es un script de la app: es una caja de herramientas para quien está
# sentado probando, o para que un agente (Claude, opencode) lo invoque.
# Vive en el repo a propósito, igual que `mock_backend.py`.
#
# Uso:
#   tools/dev.sh devices                  lista los dispositivos
#   tools/dev.sh shot <nombre>            captura a /tmp/shots/<nombre>.png
#   tools/dev.sh tap <x> <y>              toca en coords del device (sin factor)
#   tools/dev.sh swipe <x1> <y1> <x2> <y2> [ms]
#   tools/dev.sh text "hola mundo"        escribe (los espacios van tal cual)
#   tools/dev.sh key <KEY>                botón: HOME, BACK, POWER, VOLUME_UP/DOWN
#   tools/dev.sh back                     atajo de `key BACK`
#   tools/dev.sh log [n]                  últimas n líneas del device log
#   tools/dev.sh clearlog                 borra buffer de logcat (vía adb)
#   tools/dev.sh tree                     dump del UI tree con refs (@eN)
#   tools/dev.sh install <path.apk>       instala una app vía mobilecli
#   tools/dev.sh install-debug            ./gradlew installDebug + relanzar app
#   tools/dev.sh launch <pkg>             lanza una app por package name
#   tools/dev.sh terminate <pkg>          termina una app
#   tools/dev.sh apps                     lista apps instaladas
#   tools/dev.sh fg                       app en foreground
#   tools/dev.sh logcat-tag <tag> [n]     filtra logcat por tag (ej: SignAllWeb)
#   tools/dev.sh doctor                   valida entorno (mobilewright doctor)
#
# El package de este repo es `com.signall.app` y la activity de entrada
# es `MainActivity`. Se puede fijar con $APP_PKG y $APP_ACTIVITY.
#
# Sin escala: mobilecli trabaja en píxeles reales del device. La captura sale
# a 1080x2400 y las coords de tap/swipe van en la misma unidad.
set -euo pipefail

SHOTS_DIR="${DEV_SHOTS_DIR:-/tmp/shots}"
mkdir -p "$SHOTS_DIR"

APP_PKG="${APP_PKG:-com.signall.app}"
APP_ACTIVITY="${APP_ACTIVITY:-com.signall.app.MainActivity}"

cmd="${1:-help}"
shift || true

# Helper: arma el flag --device si $MOBILECLI_DEVICE está seteado
_dev_flag() {
  if [[ -n "${MOBILECLI_DEVICE:-}" ]]; then
    print -rn -- "--device=$MOBILECLI_DEVICE"
  fi
}

case "$cmd" in
  # --- dispositivos y estado ---
  devices)        mobilecli devices ;;
  doctor)         mobilewright doctor ;;
  fg)             mobilecli apps foreground $(_dev_flag) ;;
  apps)           mobilecli apps list $(_dev_flag) ;;

  # --- capturas y árbol ---
  shot)           name="${1:?falta el nombre}"
                  mobilecli screenshot $(_dev_flag) --output "$SHOTS_DIR/$name.png" >/dev/null
                  print -r -- "$SHOTS_DIR/$name.png" ;;
  tree)           mobilecli snapshot $(_dev_flag) ;;

  # --- input básico (sin escala) ---
  tap)            [[ $# -ge 2 ]] || { print -ru2 -- "uso: tap <x> <y>"; exit 2; }
                  mobilecli io tap $(_dev_flag) "$1,$2" ;;
  swipe)          [[ $# -ge 4 ]] || { print -ru2 -- "uso: swipe <x1> <y1> <x2> <y2> [ms]"; exit 2; }
                  ms="${5:-300}"
                  mobilecli io swipe $(_dev_flag) "$1,$2" "$3,$4" --duration "$ms" ;;
  text)           [[ $# -ge 1 ]] || { print -ru2 -- 'uso: text "hola mundo"'; exit 2; }
                  mobilecli io text $(_dev_flag) "$1" ;;
  key)            [[ $# -ge 1 ]] || { print -ru2 -- "uso: key <HOME|BACK|POWER|VOLUME_UP|VOLUME_DOWN>"; exit 2; }
                  mobilecli io button $(_dev_flag) "$1" ;;
  back)           mobilecli io button $(_dev_flag) BACK ;;

  # --- ciclo de vida de apps ---
  install)        [[ $# -ge 1 ]] || { print -ru2 -- "uso: install <path.apk>"; exit 2; }
                  mobilecli apps install $(_dev_flag) "$1" ;;
  install-debug)  # El flujo del AGENTS.md § Build: compile, install, lanzar.
                  ( cd "${0:A:h:h}" && ./gradlew installDebug ) || { print -ru2 -- "gradlew installDebug falló"; exit 1; }
                  mobilecli apps terminate $(_dev_flag) "$APP_PKG" 2>/dev/null || true
                  mobilecli apps launch $(_dev_flag) "$APP_PKG/$APP_ACTIVITY" ;;
  launch)         [[ $# -ge 1 ]] || { print -ru2 -- "uso: launch <package>"; exit 2; }
                  mobilecli apps launch $(_dev_flag) "$1" ;;
  terminate)      [[ $# -ge 1 ]] || { print -ru2 -- "uso: terminate <package>"; exit 2; }
                  mobilecli apps terminate $(_dev_flag) "$1" ;;

  # --- logs ---
  log)            n="${1:-40}"
                  mobilecli device logs $(_dev_flag) --limit "$n" ;;
  logcat-tag)     [[ $# -ge 1 ]] || { print -ru2 -- "uso: logcat-tag <tag> [n]"; exit 2; }
                  n="${2:-80}"
                  # logcat filtra por texto; para este repo el tag clave es "SignAllWeb"
                  # (ver AGENTS.md § "Kotlin ↔ JS bridge")
                  adb logcat -d 2>/dev/null | grep -i "$1" | tail -"$n" ;;
  clearlog)       adb logcat -c ;;

  # --- ayuda ---
  *)              sed -n '2,46p' "$0" | sed 's/^# \{0,1\}//' ;;
esac
