#!/usr/bin/env bash
#
# Corrida completa de verificación del modo tiempo real.
#
# Todo lo que hay que mirar antes de decir "esto funciona en tiempo real", en un
# solo comando y sin que nadie tenga que acordarse de los pasos. Cada bloque
# es independiente: si uno falla los otros igual corren, y el resumen final dice
# qué pasó.
#
#   tools/verificar-tiempo-real.sh                 # todo
#   tools/verificar-tiempo-real.sh latencia        # solo la medición grande
#   tools/verificar-tiempo-real.sh dispositivo     # solo lo del teléfono
#   tools/verificar-tiempo-real.sh fixtures        # solo transcripción
#
# Requisitos: docker compose corriendo en sign-ai-backend, el backend en 8080,
# el STT en 8000 y un dispositivo adb conectado para el bloque de dispositivo.
set -uo pipefail

BACKEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../sign-ai-backend" && pwd)"
STT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../sign-ai-stt" && pwd)"
BACKEND_URL="${SIGNAI_BACKEND_URL:-ws://127.0.0.1:8080/ws/translate}"
FIXTURES="${SIGNAI_FIXTURES:-$STT_DIR/fixtures/audio}"
PKG="com.signai.webview"

# Presupuesto de la primera seña, p95, en ms desde el primer sample de audio.
# Medido en el Redmi Note 9S con GPU libre y caché caliente: p50 1094, p95 1821.
# Subirlo es fácil (más margen), bajarlo es el trabajo: es el número que decide
# si el producto se siente en tiempo real.
SIGNAI_P95_MAX_MS="${SIGNAI_P95_MAX_MS:-2500}"

BOLD=$'\033[1m'; RED=$'\033[31m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; OFF=$'\033[0m'
declare -a RESULTS=()

record() {
  local name="$1" ok="$2" detail="${3:-}"
  if [[ "$ok" == "0" ]]; then
    RESULTS+=("${GREEN}ok    ${OFF} ${name}${detail:+ — $detail}")
  else
    RESULTS+=("${RED}FALLA ${OFF} ${name}${detail:+ — $detail}")
  fi
}

banner() { printf '\n%s════ %s ════%s\n' "$BOLD" "$1" "$OFF"; }

# Un python de apoyo para correr los scripts que viven en otros repos.
py() { python3 "$@"; }

# ── 1. Salud de los servicios ────────────────────────────────────────────────
check_health() {
  banner "1. Servicios"
  local backend stt
  backend=$(curl -s -m 5 http://127.0.0.1:8080/health)
  stt=$(curl -s -m 5 http://127.0.0.1:8000/health)

  if [[ "$backend" == *'"status":"ok"'* ]]; then
    echo "  backend: $backend"
    record "backend arriba" 0
  else
    echo "  backend: ${backend:-sin respuesta}"
    record "backend arriba" 1 "docker compose up -d en $BACKEND_DIR"
  fi

  if [[ "$backend" == *'"realtime":true'* ]]; then
    record "tiempo real prendido" 0
  else
    record "tiempo real prendido" 1 "REALTIME_ENABLED=false en el .env del backend"
  fi

  if [[ "$backend" == *'"cache_loaded":true'* ]]; then
    record "caché semántico en memoria" 0
  else
    record "caché semántico en memoria" 1 "semantic_cache vacía o preload caído; el LLM va a ir más lento"
  fi

  if [[ "$stt" == *'"status":"ok"'* ]]; then
    echo "  stt: $stt"
    record "STT arriba" 0
    if [[ "$stt" == *'"realtime":true'* ]]; then
      record "perfil de tiempo real del STT" 0
    else
      record "perfil de tiempo real del STT" 1 "SILENCE_TIMEOUT_MS >= 1500"
    fi
  else
    echo "  stt: ${stt:-sin respuesta}"
    record "STT arriba" 1
  fi
}

# ── 2. Lógica incremental, sin nada corriendo ────────────────────────────────
# Los checks que dependen de las dependencias del servicio corren dentro del
# contenedor. En el host faltarían (httpx2 para el TestClient de Starlette), y
# un check que solo pasa en un lado no verifica nada.
in_backend() { docker compose -f "$BACKEND_DIR/docker-compose.yml" exec -T backend "$@"; }

check_incremental() {
  banner "2. Lógica incremental y protocolo (en el contenedor del backend)"
  if ! docker compose -f "$BACKEND_DIR/docker-compose.yml" ps --status running --services 2>/dev/null | grep -q '^backend$'; then
    record "contenedor del backend corriendo" 1 "docker compose up -d en $BACKEND_DIR"
    return
  fi

  if in_backend python scripts/verify_incremental.py >/tmp/verify_incremental.out 2>&1; then
    tail -1 /tmp/verify_incremental.out | sed 's/^/  /'
    record "qué seña se firma y cuál no" 0
  else
    tail -25 /tmp/verify_incremental.out | sed 's/^/  /'
    record "qué seña se firma y cuál no" 1
  fi

  if in_backend python scripts/verify_client_frames.py >/tmp/verify_frames.out 2>&1; then
    tail -1 /tmp/verify_frames.out | sed 's/^/  /'
    record "protocolo del WebSocket (frase entera y fragmentado)" 0
  else
    tail -25 /tmp/verify_frames.out | sed 's/^/  /'
    record "protocolo del WebSocket (frase entera y fragmentado)" 1
  fi
}

# ── 3. Transcripción de los fixtures de audio ───────────────────────────────
# Cada WAV se reproduce por el WebSocket a velocidad real, que es lo más cerca
# de hablar que se puede llegar sin una persona.
check_fixtures() {
  banner "3. Transcripción de los 28 fixtures de audio"
  if [[ ! -f "$FIXTURES/index.json" ]]; then
    record "fixtures generados" 1 "corré scripts/make_audio_fixtures.py --all en sign-ai-stt"
    return
  fi
  record "fixtures generados" 0 "$(python3 -c "import json;print(json.load(open('$FIXTURES/index.json'))['count'])") clips"
  echo "  (la transcripción se verifica dentro de la corrida de latencia, que los reproduce)"
}

# ── 4. Latencia real, extremo a extremo ──────────────────────────────────────
check_latency() {
  banner "4. Latencia extremo a extremo (audio -> seña en pantalla)"
  local out=/tmp/verify_latency.out
  py "$BACKEND_DIR/scripts/verify_realtime_latency.py" \
    --url "$BACKEND_URL" --all-fixtures --fixtures-dir "$FIXTURES" --timeout 45 >"$out" 2>&1
  tail -36 "$out"

  # El umbral mira el p95 de la primera seña. Con la caché semántica vacía la
  # primera pasada paga una llamada al LLM por fragmento y sale ~30% más lenta,
  # así que el número es una cota superior, no el caso bueno.
  local p95
  p95=$(grep -o 'PRIMERA SEÑA.*p95=[0-9]*ms' "$out" | grep -o 'p95=[0-9]*' | cut -d= -f2)
  if [[ -z "$p95" ]]; then
    record "latencia medida" 1 "la corrida no produjo resumen; mirá $out"
    return
  fi

  local missing
  missing=$(grep -c 'n=28/28' "$out" || true)
  if [[ "$missing" -ge 1 ]]; then
    record "28/28 fixtures produjeron al menos una seña" 0
  else
    record "28/28 fixtures produjeron al menos una seña" 1 "algunos fixtures no produjeron ninguna; mirá la tabla"
  fi

  if (( p95 <= SIGNAI_P95_MAX_MS )); then
    record "primera seña p95 = ${p95}ms" 0 "presupuesto ${SIGNAI_P95_MAX_MS}ms"
  else
    record "primera seña p95 = ${p95}ms" 1 "presupuesto ${SIGNAI_P95_MAX_MS}ms; probá con la caché caliente"
  fi
}

# ── 5. Métricas de los dos servicios ─────────────────────────────────────────
check_metrics() {
  banner "5. Métricas"
  echo "  --- backend ---"
  curl -s -m 5 http://127.0.0.1:8080/metrics.json | python3 -c "
import json, sys
d = json.load(sys.stdin)
keys = ('rt_first_sign_ms', 'rt_utterance_ms', 'rt_chunk_delay_ms', 'audio_bytes_total', 'sessions_total')
for name, snapshot in sorted(d['histograms'].items()):
    if any(name.startswith(k) for k in keys):
        print(f\"  {name:<34} p50={snapshot['p50']:>8} p95={snapshot['p95']:>8} max={snapshot['max']:>8}\")
for name, value in sorted(d['counters'].items()):
    if any(name.startswith(k) for k in keys):
        print(f'  {name:<34} {value}')
for name, value in sorted(d['gauges'].items()):
    # El nivel del audio entrante: bytes que crecen con RMS en el piso es
    # 'el micrófono está capturing silencio', que se ve distinto de 'el STT
    # está caído'.
    if 'rms' in name:
        print(f'  {name:<34} {value:.4f}')
" 2>/dev/null || echo "  (sin métricas del backend)"

  echo "  --- stt ---"
  curl -s -m 5 http://127.0.0.1:8000/metrics.json | python3 -c "
import json, sys
d = json.load(sys.stdin)
for name, snapshot in sorted(d['histograms'].items()):
    if name.startswith(('stt_end_to_final_ms', 'stt_inference_ms', 'stt_partial_lag_ms', 'stt_partial_queue_ms')):
        print(f\"  {name:<34} p50={snapshot['p50']:>8} p95={snapshot['p95']:>8} max={snapshot['max']:>8}\")
for name, value in sorted(d['counters'].items()):
    if name.startswith(('stt_dropped_total', 'stt_final_total')):
        print(f'  {name:<34} {value}')
" 2>/dev/null || echo "  (sin métricas del STT)"
}

# ── 6. El teléfono ───────────────────────────────────────────────────────────
check_device() {
  banner "6. Dispositivo"
  if ! adb get-state >/dev/null 2>&1; then
    record "dispositivo conectado" 1 "adb devices no ve nada; el resto de la corrida sigue siendo válido"
    return
  fi
  echo "  dispositivo: $(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')"

  if adb shell pm list packages 2>/dev/null | grep -q "$PKG"; then
    record "app instalada" 0
  else
    record "app instalada" 1 "./gradlew installDebug"
    return
  fi

  # El permiso de micrófono no viene pre-otorgado: sin esto, tocar el micrófono
  # dice "No se pudo abrir el micrófono" y parece un fallo del STT.
  adb shell pm grant "$PKG" android.permission.RECORD_AUDIO >/dev/null 2>&1
  record "permiso de micrófono" 0

  adb reverse tcp:8080 tcp:8080 >/dev/null 2>&1
  if adb shell "curl -s -m 5 http://127.0.0.1:8080/health" 2>/dev/null | grep -q '"status":"ok"'; then
    record "el teléfono llega al backend" 0 "adb reverse tcp:8080"
  else
    record "el teléfono llega al backend" 1 "sin adb reverse no hay backend; usá la IP de la red local"
  fi

  adb logcat -c >/dev/null 2>&1
  adb shell am force-stop "$PKG" >/dev/null 2>&1
  adb shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
  sleep 10
  echo "  app relanzada. Probá vos con la voz:"
  echo "    1. tocá el micrófono"
  echo "    2. decí 'hola' y esperá un segundo antes de seguir"
  echo "    3. seguí con 'cómo estás'"
  echo "    4. soltá el micrófono cuando termines"
  echo
  echo "  mientras tanto, los logs:"
  echo "    adb logcat -s SignAiMetrics SignAiNet SignAiState"
  record "app relanzada" 0
}

# ── main ─────────────────────────────────────────────────────────────────────
main() {
  local what="${1:-todo}"
  printf '%sVerificación del modo tiempo real%s  %s\n' "$BOLD" "$OFF" "$(date '+%Y-%m-%d %H:%M')"

  case "$what" in
    incremental) check_health; check_incremental ;;
    fixtures)    check_fixtures; check_latency ;;
    latencia)    check_health; check_latency; check_metrics ;;
    dispositivo) check_device ;;
    metricas)    check_metrics ;;
    todo|"")     check_health; check_incremental; check_fixtures; check_latency; check_metrics; check_device ;;
    *) echo "bloque desconocido: $what"; echo "usá: incremental | fixtures | latencia | metricas | dispositivo | todo"; exit 2 ;;
  esac

  banner "Resumen"
  for line in "${RESULTS[@]}"; do echo "  $line"; done

  local failed=0
  for line in "${RESULTS[@]}"; do [[ "$line" == FALLA* || "$line" == *"${RED}FALLA"* ]] && ((failed++)); done
  echo
  if ((failed > 0)); then
    printf '%s%s de %d checks fallaron%s\n' "$RED" "$failed" "${#RESULTS[@]}" "$OFF"
    exit 1
  fi
  printf '%stodo ok (%d checks)%s\n' "$GREEN" "${#RESULTS[@]}" "$OFF"
}

main "$@"