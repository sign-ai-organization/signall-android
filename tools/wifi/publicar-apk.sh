#!/usr/bin/env bash
#
# Publica el APK `demo` para instalarlo desde el navegador del teléfono, sin cable.
#
#   tools/wifi/publicar-apk.sh
#   SIGNALL_HOST=192.168.80.49 tools/wifi/publicar-apk.sh
#   SIGNALL_WS_URL=wss://otra-maquina/ws/translate tools/wifi/publicar-apk.sh
#   tools/wifi/publicar-apk.sh --no-build      # republica el APK que ya está
#
# Qué hace:
#   1. resuelve la IP de la PC en la red local,
#   2. avisa si el certificado de laboratorio no cubre esa IP,
#   3. compila `demo` con esa dirección adentro, así el APK recién instalado
#      conecta sin tocar nada,
#   4. copia el APK a `../signall-backend/deploy/apk/`, que Caddy sirve en
#      `https://<IP>/apk/`,
#   5. escribe la página de descarga con SHA, versión, tamaño y fecha,
#   6. imprime la URL y un QR para el teléfono.
#
# El APK pesa ~40 MB: la primera descarga por Wi-Fi tarda. La página muestra el
# tamaño para que nadie crea que se colgó.
#
# El plan completo está en `../signall-backend/docs/plan-wifi.md`, fase 2.

set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_DIR="$(cd "$APP_DIR/../signall-backend" && pwd)"
APK_DIR="$BACKEND_DIR/deploy/apk"
BUILD_DIR="$APP_DIR/app/build/outputs/apk/demo"
BUILD=1

for arg in "$@"; do
  case "$arg" in
    --no-build) BUILD=0 ;;
    -h|--help) sed -n '2,24p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "argumento desconocido: $arg" >&2; exit 2 ;;
  esac
done

host_ip() {
  ip -4 route get 1.1.1.1 2>/dev/null \
    | sed -n 's/.* src \([0-9.]*\).*/\1/p' \
    | head -1
}

HOST="${SIGNALL_HOST:-$(host_ip)}"
if [[ -z "$HOST" ]]; then
  echo "no pude resolver la IP de la red local; pasá SIGNALL_HOST=192.168.x.x" >&2
  exit 2
fi

WS_URL="${SIGNALL_WS_URL:-wss://$HOST/ws/translate}"

# El certificado es de una identidad, no del proxy: si la IP cambió por DHCP el
# TLS falla y el síntoma se ve en la app, no acá. Mejor avisar antes.
if [[ -f "$BACKEND_DIR/deploy/certs/lab.pem" ]]; then
  if ! openssl x509 -in "$BACKEND_DIR/deploy/certs/lab.pem" -noout -ext subjectAltName 2>/dev/null \
      | grep -q -- "$HOST"; then
    echo "aviso: el certificado no incluye $HOST; reemitilo (deploy/README.md) o el teléfono va a rechazar el TLS" >&2
  fi
fi

if (( BUILD )); then
  echo "compilando demo con $WS_URL ..."
  ( cd "$APP_DIR" && ./gradlew --quiet assembleDemo -Psignall.backendWsUrl="$WS_URL" )
fi

APK="$(find "$BUILD_DIR" -name '*.apk' -print -quit 2>/dev/null || true)"
if [[ -z "$APK" ]]; then
  echo "no encontré el APK en $BUILD_DIR; corré sin --no-build" >&2
  exit 1
fi

META="$BUILD_DIR/output-metadata.json"
VERSION="$(python3 - "$META" <<'PY' 2>/dev/null || true
import json, sys
try:
    element = json.load(open(sys.argv[1]))["elements"][0]
    print(f'{element["versionName"]} ({element["versionCode"]})')
except Exception:
    pass
PY
)"
VERSION="${VERSION:-desconocida}"

cd "$APP_DIR"
SHA="$(git rev-parse --short HEAD)"
if [[ -n "$(git status --porcelain)" ]]; then
  SHA="$SHA-dirty"
fi
SIZE="$(du -h "$APK" | cut -f1)"
DATE="$(date '+%Y-%m-%d %H:%M')"

mkdir -p "$APK_DIR"
cp "$APK" "$APK_DIR/signall-demo.apk"
cp "$APK" "$APK_DIR/signall-demo-$SHA.apk"

# Keep the last three versioned copies: each one is 40 MB and the download page
# only links the current one.
ls -1t "$APK_DIR"/signall-demo-*.apk 2>/dev/null | tail -n +4 | xargs -r rm -f

cat > "$APK_DIR/index.html" <<HTML
<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>SignAll — APK</title>
<style>
  :root { color-scheme: dark; }
  body { margin: 0; min-height: 100vh; display: grid; place-items: center;
         background: #12141a; color: #f2f4f8;
         font-family: system-ui, -apple-system, "Segoe UI", sans-serif; }
  main { width: min(34rem, 92vw); padding: 2rem; }
  h1 { margin: 0 0 .25rem; font-size: 1.6rem; letter-spacing: -.02em; }
  p  { margin: 0 0 1.5rem; color: #9aa3b2; line-height: 1.5; }
  a  { display: block; padding: 1.1rem; border-radius: 1rem; text-align: center;
       background: #1c4cfc; color: #fff; text-decoration: none;
       font-weight: 600; font-size: 1.1rem; }
  dl { display: grid; grid-template-columns: auto 1fr; gap: .5rem 1rem;
       margin: 1.75rem 0 0; font-size: .9rem; }
  dt { color: #9aa3b2; }
  dd { margin: 0; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
  small { display: block; margin-top: 1.5rem; color: #6f7888; line-height: 1.5; }
</style>
</head>
<body>
<main>
  <h1>SignAll</h1>
  <p>Tocá el botón, bajá el APK y aceptá la instalación. Es el build <code>demo</code>:
     habla <code>wss://</code> con el backend de esta máquina.</p>
  <a href="signall-demo.apk">Descargar el APK · $SIZE</a>
  <dl>
    <dt>Versión</dt><dd>$VERSION</dd>
    <dt>Build</dt><dd>$SHA</dd>
    <dt>Publicado</dt><dd>$DATE</dd>
    <dt>Servidor</dt><dd>$WS_URL</dd>
  </dl>
  <small>Si el teléfono dice que no puede instalar apps desconocidas, habilitá la
     instalación para este navegador en Ajustes y volvé a tocar el botón. La app
     reemplaza a la anterior sin borrar los datos.</small>
</main>
</body>
</html>
HTML

cat > "$APK_DIR/latest.json" <<JSON
{
  "version": "$VERSION",
  "sha": "$SHA",
  "size": "$SIZE",
  "published_at": "$DATE",
  "ws_url": "$WS_URL",
  "apk": "signall-demo.apk"
}
JSON

echo
echo "APK publicado"
echo "  página:   https://$HOST/apk/"
echo "  archivo:  https://$HOST/apk/signall-demo.apk"
echo "  servidor: $WS_URL"
echo "  build:    $VERSION · $SHA · $SIZE · $DATE"
echo
echo "abrí la página en el teléfono, o escaneá:"
echo
qrencode -t ansiutf8 "https://$HOST/apk/" || true
