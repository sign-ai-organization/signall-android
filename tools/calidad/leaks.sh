#!/usr/bin/env bash
# Escanea credenciales con gitleaks en TODA la historia de los cinco repositorios.
#
# Uso:  tools/calidad/leaks.sh
#
# Deja un JSON por repositorio en ${XDG_CACHE_HOME:-$HOME/.cache}/signall/informes/.
# Ese JSON puede contener el secreto en claro: vive fuera del repo y no se commitea.
#
# Sale con código 0 si está limpio, 1 si encontró algo (sirve para CI) y 2 si un
# repositorio falta o la herramienta no está.

set -uo pipefail

AQUI="$(cd "$(dirname "$0")" && pwd)"
RAIZ="$(cd "$AQUI/../../.." && pwd)"
INFORMES="${XDG_CACHE_HOME:-$HOME/.cache}/signall/informes"

REPOS=(
  "signall-android"
  "signall-backend"
  "signall-stt"
  "signall-docs"
  "signall-avatar"
)

mkdir -p "$INFORMES"

if ! command -v gitleaks >/dev/null 2>&1; then
  echo "gitleaks no está instalado. Está en ~/.local/bin/gitleaks." >&2
  exit 2
fi

echo "gitleaks $(gitleaks version) · reportes en $INFORMES"
echo

total=0
saltados=0
for repo in "${REPOS[@]}"; do
  ruta="$RAIZ/$repo"
  if [ ! -d "$ruta/.git" ]; then
    # Un repositorio que falta NO es un repositorio limpio: si se salta en
    # silencio, el script dice "sin hallazgos" sin haber mirado nada.
    echo "  $repo: falta $ruta, no se pudo escanear" >&2
    saltados=$((saltados + 1))
    continue
  fi

  salida="$INFORMES/leaks-$repo.json"
  rm -f "$salida"

  # --all incluye todas las ramas, no solo la actual: un secreto en una rama
  # vieja sigue estando en el objeto y sigue siendo un secreto.
  # El reporte sale redactado: alcanza para saber qué rotar sin volver a dejar
  # la credencial escrita en un archivo.
  gitleaks git "$ruta" \
    --log-opts="--all" \
    --report-format json \
    --report-path "$salida" \
    --no-banner >/dev/null 2>&1
  estado=$?

  if [ $estado -eq 0 ]; then
    echo "  $repo: limpio"
    rm -f "$salida"
  else
    n=$(python3 -c "import json,sys;print(len(json.load(open(sys.argv[1]))))" "$salida" 2>/dev/null || echo "?")
    echo "  $repo: $n hallazgo(s) → $salida"
    total=$((total + 1))
  fi
done

echo
if [ $saltados -gt 0 ]; then
  echo "$saltados repositorio(s) no se pudieron escanear. El resultado no sirve como línea base." >&2
  exit 2
fi

if [ $total -eq 0 ]; then
  echo "Sin hallazgos en los cinco repositorios."
  exit 0
fi

echo "$total repositorio(s) con hallazgos. Revisar los JSON y ROTAR la credencial:"
echo "borrar el commit no alcanza, una clave que estuvo en la historia está comprometida."
exit 1
