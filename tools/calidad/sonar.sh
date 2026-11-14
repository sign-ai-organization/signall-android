#!/usr/bin/env bash
# Envía un repositorio a SonarQube local (http://localhost:9000) con el
# sonar-scanner oficial, corriendo en Docker. No instala nada en el host.
#
# Uso:
#   tools/calidad/sonar.sh signall-android
#   tools/calidad/sonar.sh signall-backend
#   tools/calidad/sonar.sh signall-stt
#
# Necesita un token. Se lee, en este orden:
#   1. la variable de entorno SONAR_TOKEN
#   2. ~/.config/signall/sonar.env   (fuera del repo, nunca se commitea)
#
#   SONAR_TOKEN=sqp_xxxxxxxx tools/calidad/sonar.sh signall-backend
#
# El token se genera en SonarQube: My Account → Security → Generate Tokens.
# Los proyectos se crean solos en el primer escaneo si el usuario tiene permiso.

set -euo pipefail

AQUI="$(cd "$(dirname "$0")" && pwd)"
RAIZ="$(cd "$AQUI/../../.." && pwd)"
TOKEN_ENV="${XDG_CONFIG_HOME:-$HOME/.config}/signall/sonar.env"

repo="${1:-}"
if [ -z "$repo" ]; then
  echo "Uso: $(basename "$0") <nombre-del-repo>   (ej: signall-android)" >&2
  exit 2
fi

ruta="$RAIZ/$repo"
if [ ! -d "$ruta" ]; then
  echo "No existe el repositorio: $ruta" >&2
  exit 2
fi

if [ -z "${SONAR_TOKEN:-}" ] && [ -f "$TOKEN_ENV" ]; then
  # shellcheck disable=SC1090
  . "$TOKEN_ENV"
fi

if [ -z "${SONAR_TOKEN:-}" ]; then
  echo "Falta SONAR_TOKEN (variable de entorno o $TOKEN_ENV)." >&2
  exit 2
fi

# Fuente o no el archivo, el token tiene que llegar al contenedor: `.` lo deja como
# variable de shell y `docker -e` solo copia variables exportadas.
export SONAR_TOKEN

if [ ! -f "$ruta/sonar-project.properties" ]; then
  echo "Falta $repo/sonar-project.properties." >&2
  exit 2
fi

# La clave del proyecto la manda el sonar-project.properties, no el nombre de la
# carpeta local.
clave=$(sed -n 's/^sonar\.projectKey=//p' "$ruta/sonar-project.properties" | tr -d '\r')

if ! curl -fsS http://localhost:9000/api/system/status 2>/dev/null | grep -q '"status":"UP"'; then
  echo "SonarQube no responde en http://localhost:9000." >&2
  echo "Levantarlo con: docker start signall-sonarqube signall-sonar-db" >&2
  exit 2
fi

echo "Escaneando $repo con el proyecto '$clave'…"
docker run --rm \
  --network host \
  -e SONAR_TOKEN \
  -e SONAR_HOST_URL=http://localhost:9000 \
  -v "$ruta":/usr/src \
  -w /usr/src \
  sonarsource/sonar-scanner-cli:latest \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token="$SONAR_TOKEN"

echo
echo "Resultado: http://localhost:9000/dashboard?id=$clave"
