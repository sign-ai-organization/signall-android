#!/usr/bin/env bash
# PR-REN-04 · Arranque en frío con la base dormida.
#
# Apaga el sistema, espera a que Neon suspenda el cómputo, lo vuelve a encender
# midiendo cuánto tarda en estar listo y manda tres frases para comparar la
# primera traducción con las siguientes.
set -u

BACKEND=../sign-ai-backend
SALIDA=/tmp/opencode/p1-2026-11-10
ARNE=$(cd "$(dirname "$0")" && pwd)
DATOS=$SALIDA/datos

echo "[1] apagando backend y STT $(date -Is)"
docker compose -f $BACKEND/docker-compose.yml stop backend stt

echo "[2] esperando 5 min 40 s a que Neon suspenda el cómputo $(date -Is)"
sleep 340

echo "[3] encendiendo $(date -Is)"
INICIO=$(date +%s%N)
docker compose -f $BACKEND/docker-compose.yml start backend stt >/dev/null

hasta_backend=""
hasta_stt=""
for _ in $(seq 1 600); do
  if [ -z "$hasta_backend" ] && curl -sf -m 2 localhost:8080/health >/dev/null 2>&1; then
    hasta_backend=$(( ($(date +%s%N) - INICIO) / 1000000 ))
  fi
  if [ -z "$hasta_stt" ] && curl -sf -m 2 localhost:8000/health >/dev/null 2>&1; then
    hasta_stt=$(( ($(date +%s%N) - INICIO) / 1000000 ))
  fi
  if [ -n "$hasta_backend" ] && [ -n "$hasta_stt" ]; then break; fi
  sleep 0.2
done

echo "[4] backend listo en ${hasta_backend} ms; STT listo en ${hasta_stt} ms $(date -Is)"
curl -s -m 5 localhost:8080/health > "$SALIDA/evidencias/REN/PR-REN-04_health_E_20261110_01.json"

echo "[5] tres frases $(date -Is)"
python3 "$ARNE/ren04.py" --textos "$DATOS/FR-01.json" \
  --salida "$SALIDA/evidencias/REN/PR-REN-04_E_20261110_01.json"

python3 - "$hasta_backend" "$hasta_stt" <<'PY'
import json, sys, pathlib
ruta = pathlib.Path("/tmp/opencode/p1-2026-11-10/evidencias/REN/PR-REN-04_E_20261110_01.json")
datos = json.loads(ruta.read_text(encoding="utf-8"))
datos["ms_hasta_backend_listo"] = int(sys.argv[1])
datos["ms_hasta_stt_listo"] = int(sys.argv[2])
ruta.write_text(json.dumps(datos, ensure_ascii=False, indent=1), encoding="utf-8")
print(json.dumps({k: v for k, v in datos.items() if k != "frases"}, ensure_ascii=False))
PY
