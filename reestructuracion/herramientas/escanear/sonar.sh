#!/usr/bin/env bash
# Envía un repositorio a SonarQube local (http://localhost:9000) con el sonar-scanner
# oficial, corriendo en Docker. No instala nada en el host.
#
# Uso:
#   reestructuracion/herramientas/escanear/sonar.sh sign-ai-android
#   reestructuracion/herramientas/escanear/sonar.sh sign-ai-backend
#
# Necesita un token. Se lee, en este orden:
#   1. la variable de entorno SONAR_TOKEN
#   2. reestructuracion/herramientas/.env.sonar   (ignorado por git: `.env*`)
#
#   SONAR_TOKEN=sqp_xxxxxxxx reestructuracion/herramientas/escanear/sonar.sh sign-ai-backend
#
# El token se genera en SonarQube: My Account → Security → Generate Tokens.
# Los proyectos se crean solos en el primer escaneo si el usuario tiene permiso.

set -euo pipefail

AQUI="$(cd "$(dirname "$0")" && pwd)"
HERR="$(cd "$AQUI/.." && pwd)"
RAIZ="$(cd "$AQUI/../../../.." && pwd)"

repo="${1:-}"
if [ -z "$repo" ]; then
  echo "Uso: $(basename "$0") <nombre-del-repo>   (ej: sign-ai-android)" >&2
  exit 2
fi

ruta="$RAIZ/$repo"
if [ ! -d "$ruta" ]; then
  echo "No existe el repositorio: $ruta" >&2
  exit 2
fi

if [ -z "${SONAR_TOKEN:-}" ] && [ -f "$HERR/.env.sonar" ]; then
  # shellcheck disable=SC1091
  . "$HERR/.env.sonar"
fi

if [ -z "${SONAR_TOKEN:-}" ]; then
  echo "Falta SONAR_TOKEN (variable de entorno o $HERR/.env.sonar)." >&2
  exit 2
fi

# Fuente o no el archivo, el token tiene que llegar al contenedor: `.` lo deja como
# variable de shell y `docker -e` solo copia variables exportadas.
export SONAR_TOKEN

if [ ! -f "$ruta/sonar-project.properties" ]; then
  echo "Falta $repo/sonar-project.properties. La plantilla está en" >&2
  echo "reestructuracion/herramientas/plantillas/" >&2
  exit 2
fi

if ! curl -fsS http://localhost:9000/api/system/status 2>/dev/null | grep -q '"status":"UP"'; then
  echo "SonarQube no responde en http://localhost:9000." >&2
  echo "Levantarlo con: docker compose -f $HERR/docker-compose.sonarqube.yml up -d" >&2
  exit 2
fi

echo "Escaneando $repo con el proyecto '$repo'…"
docker run --rm \
  --network host \
  -e SONAR_TOKEN \
  -e SONAR_HOST_URL=http://localhost:9000 \
  -v "$ruta":/usr/src \
  -w /usr/src \
  sonarsource/sonar-scanner-cli:latest \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token="$SONAR_TOKEN" \
  -Dsonar.projectKey="$repo" \
  -Dsonar.projectName="$repo"

echo
echo "Resultado: http://localhost:9000/dashboard?id=$repo"
