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
#   tools/verificar-tiempo-real.sh contrato        # solo divisor + protocolo
#   tools/verificar-tiempo-real.sh latencia        # solo la medición grande
#   tools/verificar-tiempo-real.sh dispositivo     # solo lo del teléfono
#   tools/verificar-tiempo-real.sh metricas        # solo /metrics
#
# Rama `flujo-oraciones` (2026-10-15). Antes este script corría
# `verify_incremental.py`, `verify_voice_modes.py` y `verify_realtime_latency.py`
# del backend: los tres son del **camino por palabras** y no existen en esta
# línea, así que el script fallaba siempre y no verificaba nada. Los reemplazan
# `verify_sentence_segmenter.py` + `verify_client_frames.py` (que sí existen) y
# las dos mediciones de acá, `medir-primera-sena.py` y `medir-retraso.py`.
#
# Requisitos: docker compose corriendo en sign-ai-backend, el backend en 8080,
# el STT en 8000 y un dispositivo adb conectado para el bloque de dispositivo.
set -uo pipefail

BACKEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../sign-ai-backend" && pwd)"
STT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../sign-ai-stt" && pwd)"
BACKEND_URL="${SIGNAI_BACKEND_URL:-ws://127.0.0.1:8080/ws/translate}"
FIXTURES="${SIGNAI_FIXTURES:-$STT_DIR/fixtures/audio}"
PKG="com.signai.webview"

# Presupuesto del pipeline: p95 de la primera seña, en ms desde la transcripción
# final (que es lo que mide `rt_first_sign_ms`). Medido el 2026-10-15 sobre los 28
# fixtures con `qwen3:4b-instruct` en Ollama local y la caché vacía: p50 273,
# p95 422. El margen es amplio porque la cifra depende entero del LLM que haya
# atrás — con OpenAI es otra cosa — y este presupuesto es una guarda contra
# regresiones, no un objetivo de producto.
SIGNAI_P95_MAX_MS="${SIGNAI_P95_MAX_MS:-1200}"

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

  # Ya no hay bandera de tiempo real: no hay dos modos de backend, hay dos
  # modos de cliente. Lo que sí tiene que estar es el divisor de oraciones, que
  # es lo que hace que la unidad sea la oración.
  if curl -s -m 5 http://127.0.0.1:8080/health | grep -q '"status":"ok"'; then
    record "backend con el flujo por oraciones" 0
  else
    record "backend con el flujo por oraciones" 1 "si corrés develop, esa línea no tiene divisor de oraciones"
  fi

  if [[ "$backend" == *'"cache_loaded":true'* ]]; then
    record "caché semántico en memoria" 0
  else
    record "caché semántico en memoria" 1 "semantic_cache vacía o preload caído; el LLM va a ir más lento"
  fi

  if [[ "$stt" == *'"status":"ok"'* ]]; then
    echo "  stt: $stt"
    record "STT arriba" 0
    # El STT de este flujo tiene un solo modelo y cierra la frase por los 1,5 s
    # de silencio. El perfil de dos modelos del camino viejo no está y no hace
    # falta: acá solo importa el `final`.
    record "STT de frase completa" 0 "un solo modelo, silencio 1500 ms"
  else
    echo "  stt: ${stt:-sin respuesta}"
    record "STT arriba" 1
  fi
}

# ── 2. El contrato, sin nada corriendo ────────────────────────────────────────
# Los checks que dependen de las dependencias del servicio corren dentro del
# contenedor. En el host faltarían (httpx2 para el TestClient de Starlette), y
# un check que solo pasa en un lado no verifica nada.
in_backend() { docker compose -f "$BACKEND_DIR/docker-compose.yml" exec -T backend "$@"; }

check_contract() {
  banner "2. Divisor de oraciones y protocolo (en el contenedor del backend)"
  if ! docker compose -f "$BACKEND_DIR/docker-compose.yml" ps --status running --services 2>/dev/null | grep -q '^backend$'; then
    record "contenedor del backend corriendo" 1 "docker compose up -d en $BACKEND_DIR"
    return
  fi

  if in_backend python -m scripts.verify_sentence_segmenter >/tmp/verify_seg.out 2>&1; then
    tail -1 /tmp/verify_seg.out | sed 's/^/  /'
    record "el texto se parte en oraciones" 0
  else
    tail -25 /tmp/verify_seg.out | sed 's/^/  /'
    record "el texto se parte en oraciones" 1
  fi

  if in_backend python -m scripts.verify_client_frames >/tmp/verify_frames.out 2>&1; then
    tail -1 /tmp/verify_frames.out | sed 's/^/  /'
    record "protocolo del WebSocket (y que la voz NO se traduzca)" 0
  else
    tail -25 /tmp/verify_frames.out | sed 's/^/  /'
    record "protocolo del WebSocket (y que la voz NO se traduzca)" 1
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

# ── 4. Los dos modos sin hablar ───────────────────────────────────────────────
# Dictado y en vivo mandan el mismo audio por el mismo socket y se separan solo
# en qué le piden al backend —que ya no decide nada— así que la diferencia
# entera está del lado del cliente y no se puede verificar desde acá. Lo que sí
# se verifica sin hablar es que la frase llegue, se divida y se traduzca, que es
# el camino entero salvo el micrófono.
check_flow() {
  banner "4. Flujo por oraciones, extremo a extremo (sin micrófono)"
  local out=/tmp/verify_flujo.out
  py "$(dirname "${BASH_SOURCE[0]}")/medir-retraso.py" >"$out" 2>&1
  grep -E "^(frases|señas por frase|velocidad|representa / habla|  mediana|  con el adelanto|desfase|  mediana)" "$out" \
    | sed 's/^/  /'

  if grep -qE '^frases medidas: 28' "$out"; then
    record "los 28 fixtures llegaron y se tradujeron" 0
  else
    tail -20 "$out" | sed 's/^/  /'
    record "los 28 fixtures llegaron y se tradujeron" 1 "el detalle está en $out"
  fi
}

# ── 5. Primera seña ───────────────────────────────────────────────────────────
check_latency() {
  banner "5. Primera seña, extremo a extremo (audio -> seña en pantalla)"
  local out=/tmp/verify_latency.out
  py "$(dirname "${BASH_SOURCE[0]}")/medir-primera-sena.py" >"$out" 2>&1
  grep -E "^(\`|  p50|  línea base|  de los cuales|  caché semántica)" "$out" | sed 's/^/  /'

  # Ojo con qué número es cuál: `final→seña` es el pipeline (`rt_first_sign_ms`) y
  # `audio→seña` incluye el STT. El presupuesto es sobre el primero, porque el
  # segundo lo domina el STT y no es algo que este repo pueda mover.
  local p95
  # Ojo con el parseo: `grep -oE '[0-9]+'` sobre `p95 5` devuelve DOS números
  # (`95` del nombre de la métrica y `5` del valor), y el `(( ))` de abajo se
  # come el "95\n5" como un entero enorme. Por eso se extrae con sed y no con
  # grep, y por eso hace falta `head -1`: la línea de la línea base ("p50 913 ms /
  # p95 1336 ms") también entra en el grep.
  p95=$(grep -oE 'p50 [0-9]+ ms +p95 [0-9]+ ms' "$out" | head -1 | sed -E 's/.*p95 ([0-9]+) ms.*/\1/')
  if [[ -z "$p95" ]]; then
    record "latencia medida" 1 "la corrida no produjo resumen; mirá $out"
    return
  fi

  # **Con la caché caliente este control no mide nada.** `final→seña` es el
  # pipeline, y con la caché llena no hay pipeline: hay una consulta de
  # embeddings que responde en milisegundos. El número sale p95 ≈ 5 ms, pasa
  # cualquier presupuesto, y parece una mejora enorme. Por eso la corrida se
  # declara no medible en vez de Approved: un control que pasa sin medir es peor
  # que uno que no existe, porque la próxima regresión también "pasa".
  if grep -qE 'caché semántica con [0-9]+ filas' "$out"; then
    record "primera seña p95 = ${p95}ms NO es una medición" 1 \
      "la caché semántica estaba llena: midió el lookup, no el LLM. Para medir en frío: DELETE FROM semantic_cache; y docker compose restart backend"
    return
  fi

  if (( p95 <= SIGNAI_P95_MAX_MS )); then
    record "primera seña p95 = ${p95}ms (pipeline, caché vacía)" 0 "presupuesto ${SIGNAI_P95_MAX_MS}ms"
  else
    record "primera seña p95 = ${p95}ms (pipeline, caché vacía)" 1 "presupuesto ${SIGNAI_P95_MAX_MS}ms; fijate si el LLM de atrás es el de siempre"
  fi
}

# ── 6. Métricas de los dos servicios ─────────────────────────────────────────
check_metrics() {
  banner "5. Métricas"
  echo "  --- backend ---"
  curl -s -m 5 http://127.0.0.1:8080/metrics.json | python3 -c "
import json, sys
d = json.load(sys.stdin)
keys = ('rt_first_sign_ms', 'rt_phrase_total_ms', 'rt_sentence_delay_ms',
            'rt_phrases_total', 'rt_sentences_total', 'rt_glosses_total',
            'audio_bytes_total', 'sessions_total',
            # Las tres que separan "el modelo tardó" de "el socket estaba trabado".
            # `llm_ms` es la llamada al modelo de lenguaje, que antes solo se veía
            # en el log con DEBUG_TRACE_ENABLED; `cache_lookup_ms` es lo que hace
            # que un HIT parezca una mejora; `rt_read_loop_busy_ms` es el tiempo
            # que el bucle de lectura del socket no estuvo leyendo, y es el que
            # que cayó a milisegundos con D-2 (decisión del 2026-10-16).
            'llm_ms', 'cache_lookup_ms', 'rt_read_loop_busy_ms')
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
    if name.startswith(('stt_end_to_final_ms', 'stt_inference_ms', 'stt_partial_lag_ms', 'stt_partial_queue_ms', 'stt_flush')):
        print(f\"  {name:<34} p50={snapshot['p50']:>8} p95={snapshot['p95']:>8} max={snapshot['max']:>8}\")
for name, value in sorted(d['counters'].items()):
    if name.startswith(('stt_dropped_total', 'stt_final_total')):
        print(f'  {name:<34} {value}')
" 2>/dev/null || echo "  (sin métricas del STT)"
}

# ── 7. El teléfono ───────────────────────────────────────────────────────────
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

  # Variante cifrada (`docs/plan-proxy-tls.md`, fase 5). El 443 no se puede
  # mapear en el teléfono (`cannot bind listener: Permission denied`), así que el
  # túnel entra por el 8443. Del lado del teléfono va `-k` a propósito: lo que se
  # prueba acá es que el túnel llega al proxy y que el handshake TLS se completa.
  # Que el teléfono **confíe** en la CA de laboratorio lo demuestra el build
  # `demo` en `adb logcat -s SignAiNet`, no `curl`.
  lab_ca="$BACKEND_DIR/deploy/certs/lab-ca.crt"
  if [[ -f "$lab_ca" ]]; then
    if curl -sf --cacert "$lab_ca" https://127.0.0.1/health >/dev/null 2>&1; then
      adb reverse tcp:8443 tcp:443 >/dev/null 2>&1
      if adb shell "curl -sk -m 5 https://127.0.0.1:8443/health" 2>/dev/null | grep -q '"status":"ok"'; then
        record "el proxy responde con TLS" 0 "host con la CA del laboratorio; teléfono por adb reverse tcp:8443 tcp:443"
      else
        record "el proxy responde con TLS" 1 "el host responde, pero el teléfono no llega por adb reverse tcp:8443 tcp:443"
      fi
    else
      record "el proxy responde con TLS" 1 "deploy/certs/lab-ca.crt existe, pero https://127.0.0.1/health no responde: ¿está el servicio caddy arriba?"
    fi
  else
    echo "  proxy TLS: sin probar (no está $lab_ca)"
  fi

  adb logcat -c >/dev/null 2>&1
  adb shell am force-stop "$PKG" >/dev/null 2>&1
  adb shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
  sleep 10
  echo "  app relanzada. Probá vos con la voz:"
  echo
  echo "  A. Dictado (el micrófono de la fila de arriba) — el paso de confirmar"
  echo "    1. tocá el micrófono — se prende solo, no hay que mantenerlo apretado"
  echo "    2. decí una frase y hacé una pausa de un segundo y medio"
  echo "    3. el micrófono se apaga SOLO y el texto queda en el campo"
  echo "    4. el muñeco NO se movió. Arriba dice 'Texto listo. Toca Traducir para representarlo.'"
  echo "    5. rt_phrases_total en el backend NO se movió: eso es la prueba de que no mandó"
  echo "    6. tocá Traducir: ahora sí representa"
  echo "    7. el icono del micrófono NO cambió: tocarlo de nuevo borra y dicta de cero"
  echo
  echo "  B. En vivo (el TR de la fila de abajo)"
  echo "    1. tocá TR — el micrófono queda deshabilitado, es el mismo"
  echo "    2. hablá: cada pausa de 1,5 s manda la frase sola y el muñeco representa"
  echo "    3. el micrófono NO se apaga entre frases: seguí hablando"
  echo "    4. tocá TR de nuevo para apagarlo: el campo queda LIMPIO, no retenido"
  echo
  echo "  C. Los dos juntos"
  echo "    1. dictá algo, y sin soltar nada tocá TR: cambia de modo, no se pisa"
  echo "    2. al apagar TR, el campo vuelve a mostrar lo dictado que estaba esperando"
  echo
  echo "  mientras tanto, los logs:"
  echo "    adb logcat -s SignAiState SignAiNet SignAiMetrics"
  echo "  en el backend:"
  echo "    curl -s 127.0.0.1:8080/metrics | grep -E '^rt_(phrases|sentences|glosses)_total'"
  record "app relanzada" 0
}

# ── main ─────────────────────────────────────────────────────────────────────
main() {
  local what="${1:-todo}"
  printf '%sVerificación del modo tiempo real%s  %s\n' "$BOLD" "$OFF" "$(date '+%Y-%m-%d %H:%M')"

  case "$what" in
    contrato)    check_health; check_contract ;;
    fixtures)    check_fixtures; check_flow ;;
    flujo)       check_health; check_flow ;;
    latencia)    check_health; check_latency; check_metrics ;;
    dispositivo) check_device ;;
    metricas)    check_metrics ;;
    incremental) echo "'incremental' era del camino por palabras."; echo "usá 'contrato' (divisor de oraciones + protocolo)."; exit 2 ;;
    voz)         echo "'voz' era del camino poracentro."; echo "la diferencia entre dictado y en vivo está en el cliente; probala con la voz en el teléfono (A y B)."; exit 2 ;;
    todo|"")     check_health; check_contract; check_fixtures; check_flow; check_latency; check_metrics; check_device ;;
    *) echo "bloque desconocido: $what"; echo "usá: contrato | fixtures | flujo | latencia | metricas | dispositivo | todo"; exit 2 ;;
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