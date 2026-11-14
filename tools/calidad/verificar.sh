#!/usr/bin/env bash
# Verificación local de la app: lo mismo que antes corría en GitHub Actions.
#
#     tools/calidad/verificar.sh
#
# Corre `./gradlew build` (compila, y con eso ktlint y el lint de Android) y
# después gitleaks sobre el árbol y la historia de los cinco repositorios. Sale
# con el primer código distinto de cero, así que sirve igual antes de commitear.
#
# No hay CI en el repositorio a propósito: todo se verifica en esta máquina.
# SonarQube también es local y se corre aparte:
#
#     tools/calidad/sonar.sh signall-android

set -euo pipefail

AQUI="$(cd "$(dirname "$0")" && pwd)"
RAIZ="$(cd "$AQUI/../.." && pwd)"

cd "$RAIZ"

echo "== ./gradlew build =="
./gradlew build --no-daemon

echo
echo "== gitleaks =="
"$AQUI/leaks.sh"
