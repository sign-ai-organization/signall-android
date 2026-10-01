#!/usr/bin/env python3
"""Mock de `../sign-ai-backend` para probar la app sin Neon ni Whisper.

Habla exactamente el mismo protocolo que el backend real, en un solo puerto:

- `GET /health` → `{"status": "ok"}`
- `GET /static/<cual>.json` → una animación (por defecto, la seña horneada
  `app/src/main/assets/web/sign.json` de este repo, para que la app tenga algo
  real que descargar y reproducir)
- `WS /ws/translate` → acepta `{"type": "text", "text": "<frase>"}` y responde
  con `{"type": "transcript", ...}` y después un **array** de segmentos, igual
  que `Controller.send_result`

Todas las glosas vienen con `available: true` y una ruta de animación, así que la
app descarga el archivo por HTTP y lo reproduce: eso ejercita el camino que más
riesgo tiene (descarga nativa + `PathHandler` `/signs/` del WebView).

## Glosas: fijas o con el LLM real

Por defecto devuelve las glosas fijas de abajo, que hace la prueba determinista.
Con `--ollama` llama a tu Ollama local usando **el mismo prompt de glosado del
backend** (`../sign-ai-backend/app/nlp/llm_client.py`) y por el mismo endpoint
compatible con OpenAI, así que se ve la traducción real:

    python tools/mock_backend.py --ollama

Ojo con Ollama: la primera llamada carga el modelo en memoria y tarda ~1 min,
más que el `LLM_TIMEOUT_MS` del backend. Calentalo antes con
`ollama run qwen3:4b-instruct "hola"`, o la primera frase va a fallar y la
segunda anda.

## Uso

    pip install websockets
    python tools/mock_backend.py            # glosas fijas
    python tools/mock_backend.py --ollama   # glosas del LLM real

Y en la app. En el emulador `10.0.2.2` ya es el host, no hace falta nada:

    ./gradlew installDebug

En un celular físico, la IP de tu compu en la red local (nunca `127.0.0.1`):

    ./gradlew installDebug -Psignai.backendWsUrl=ws://<TU-IP-LAN>:8080/ws/translate

No escucha audio real: ignora los frames binarios. Es para ver la app, no para
medir nada.
"""

from __future__ import annotations

import asyncio
import json
import pathlib
import sys
import urllib.error
import urllib.request

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import local_stt  # noqa: E402

try:
    import websockets
    from websockets.asyncio.server import serve
    from websockets.http11 import Headers, Response
except ImportError:  # pragma: no cover
    sys.exit("Falta la dependencia: pip install websockets")

HOST = "0.0.0.0"
PORT = 8080

OLLAMA_URL = "http://localhost:11434/v1/chat/completions"
OLLAMA_MODEL = "qwen3:4b-instruct"
# La primera llamada carga el modelo en memoria: hay que darle margen.
OLLAMA_TIMEOUT_S = 180

REPO = pathlib.Path(__file__).resolve().parent.parent
DEFAULT_ANIMATION = REPO / "app/src/main/assets/web/sign.json"

# Copia del SYSTEM_PROMPT de ../sign-ai-backend/app/nlp/llm_client.py. Si el
# backend lo cambia, hay que cambiarlo acá también.
GLOSS_SYSTEM_PROMPT = (
    "You translate short Spanish sentences into LSC (Lengua de Señas "
    "Colombiana) glosses, following these general sign-language glossing "
    "conventions:\n"
    "- Topic-comment order: the topic of the sentence goes first.\n"
    "- Drop articles (el, la, los, las, un, una) and the copula (ser/estar) "
    "when the meaning stays clear without them.\n"
    "- Use the base/infinitive form of verbs, never conjugated.\n"
    "- For content questions (qué, cómo, dónde, cuándo, quién, cuánto), "
    "place the question word at the end.\n"
    "- Place negation (no, nunca, tampoco) at the end of the sentence.\n"
    "\n"
    "A gloss is a single word representing one sign: UPPERCASE, no accent "
    "marks (á->A, é->E, í->I, ó->O, ú->U — keep Ñ as is), no punctuation. "
    "Respond with ONLY the glosses, separated by commas, nothing else — "
    "no explanation, no sentence-type classification, no separator other "
    "than a comma.\n"
    "\n"
    "Examples:\n"
    "Input: ¿Cuál es su nombre?\n"
    "Output: NOMBRE,TU,QUE\n"
    "Input: No tengo el documento conmigo.\n"
    "Output: YO,DOCUMENTO,TENER,NO\n"
    "Input: Firme aquí, por favor.\n"
    "Output: FAVOR,AQUI,FIRMAR"
)

# Glosas fijas, para que la prueba sea estable y no dependa del modelo.
FIXED_GLOSSES = ["YO", "UNIVERSIDAD", "IR"]

# Una sola animación real alcanza para la demo, pero si todas las glosas
# recibieran el MISMO archivo se vería "la misma seña repetida N veces" y no
# dejaría claro que la cola está reproduciendo ítems distintos. Así que el
# archivo se corta en ANIMATION_SLICES porciones: cada glosa recibe una parte
# distinta de la misma seña, y se nota que el avatar va cambiando de movimiento.
#
# NO es así en el sistema real: cada glosa tiene su propia animación, que es lo
# que descarga la app (el backend guarda una ruta distinta por glosa).
ANIMATION_SLICES = 4

# Si la frase contiene esta palabra se responde con un error, para probar el
# camino de fallo del cliente.
FAIL_WITH = "FALLA"

# Puesto por --ollama.
USE_OLLAMA = False
# Puesto por --stt: transcribe el audio con whisper.cpp local.
USE_STT = False


def animation_bytes() -> bytes:
    return DEFAULT_ANIMATION.read_bytes()


def animation_slice(index: int) -> bytes:
    """Porción `index` de la animación, para que cada glosa se vea distinta.

    El archivo real de cada seña sería una animación completa; acá se usa un
    solo sign.json partido en ANIMATION_SLICES trozos (ver la nota de esa
    constante).
    """
    data = json.loads(animation_bytes())
    frames = data.get("frames") or []
    if not frames:
        return animation_bytes()

    total = len(frames)
    start = (index % ANIMATION_SLICES) * total // ANIMATION_SLICES
    end = ((index % ANIMATION_SLICES) + 1) * total // ANIMATION_SLICES
    chunk = frames[start:end] or frames
    return json.dumps(
        {"stem": f"{data.get('stem', '0000_0000_0000')}_p{index}", "fps": data.get("fps", 24),
         "total_frames": len(chunk), "frames": chunk}
    ).encode()


def glosses_from_ollama(text: str) -> list[str]:
    """Traduce con el LLM local, igual que el backend real."""
    body = json.dumps(
        {
            "model": OLLAMA_MODEL,
            "messages": [
                {"role": "system", "content": GLOSS_SYSTEM_PROMPT},
                {"role": "user", "content": text},
            ],
            "temperature": 0.0,
        }
    ).encode()
    request = urllib.request.Request(
        OLLAMA_URL,
        data=body,
        headers={"Content-Type": "application/json", "Authorization": "Bearer ollama"},
    )
    with urllib.request.urlopen(request, timeout=OLLAMA_TIMEOUT_S) as response:
        payload = json.loads(response.read())
    content = payload["choices"][0]["message"]["content"]
    separator = "," if "," in content else None
    return [g.strip().upper() for g in content.split(separator) if g.strip()]


def build_result(text: str) -> list[dict]:
    """Un segmento con una glosa por palabra, como devuelve el backend."""
    if USE_OLLAMA:
        glosses = glosses_from_ollama(text)
    else:
        glosses = list(FIXED_GLOSSES)
    return [
        {
            "type": "AFFIRMATIVE",
            "glosses": [
                {
                    "text": g,
                    "available": True,
                    # La ruta lleva el índice: el mock reparte la única
                    # animación disponible en ANIMATION_SLICES porciones.
                    "glb_path": f"/static/{i}.json",
                }
                for i, g in enumerate(glosses)
            ],
        }
    ]


def handle_http(connection, request) -> Response | None:
    """Sirve /health y /static/* en el mismo puerto que el WebSocket.

    Devuelve `None` en la ruta del WebSocket para que el handshake continúe.
    """
    path = request.path.split("?", 1)[0]

    if path.startswith("/ws/"):
        return None
    if path == "/health":
        return Response(
            200,
            "OK",
            Headers([("Content-Type", "application/json")]),
            json.dumps(
                {"status": "ok", "mock": True, "llm": USE_OLLAMA and OLLAMA_MODEL or "fijas"}
            ).encode(),
        )
    if path.startswith("/static/"):
        name = path[len("/static/"):]
        # El nombre lleva el índice de la glosa en la respuesta.
        index = int(name.split(".")[0]) if name.split(".")[0].isdigit() else 0
        return Response(
            200,
            "OK",
            Headers([("Content-Type", "application/json")]),
            animation_slice(index),
        )
    return Response(404, "Not Found", Headers(), b"not found")


async def send_translation(websocket, loop: asyncio.AbstractEventLoop, text: str) -> None:
    """LLM -> glosas. La llamada bloquea, así que va fuera del event loop."""
    try:
        result = await loop.run_in_executor(None, build_result, text)
    except (urllib.error.URLError, OSError, KeyError, TimeoutError) as error:
        print(f"[mock] LLM falló: {error}")
        print('[mock] ¿arrancaste Ollama? ¿lo calentaste con `ollama run '
              f'{OLLAMA_MODEL} "hola"`?')
        await websocket.send(
            json.dumps({"type": "error", "message": f"Mock: el LLM falló ({error})."})
        )
        return

    glosses = [g["text"] for g in result[0]["glosses"]]
    print(f"[mock] glosas: {glosses}")
    await websocket.send(json.dumps(result))


async def handle_session(websocket) -> None:
    print(f"[mock] sesión abierta desde {websocket.remote_address}")
    loop = asyncio.get_running_loop()
    utterance = local_stt.Utterance()
    transcribing: asyncio.Task | None = None
    warned_audio = False

    async def transcribe_and_translate(pcm: bytes) -> None:
        """Transcribe then translate, without ever stopping the read loop.

        Runs as a background task on purpose: Whisper takes seconds and the
        phone keeps streaming audio the whole time. If the loop stopped reading
        while it worked, the socket buffer would fill and the client would get
        a broken pipe in the middle of the phrase.
        """
        seconds = len(pcm) / 2 / local_stt.SAMPLE_RATE
        try:
            spoken = await loop.run_in_executor(None, local_stt.transcribe, pcm)
        except Exception as error:  # noqa: BLE001 - nunca debe tumbar la sesión
            print(f"[stt] falló: {error}")
            await websocket.send(
                json.dumps({"type": "error", "message": f"Mock: la transcripción falló ({error})."})
            )
            return

        spoken = spoken.strip()
        if not spoken:
            print(f"[stt] {seconds:.1f}s de audio, no se entendió nada")
            return

        print(f"[stt] {seconds:.1f}s de audio -> {spoken!r}")
        await websocket.send(
            json.dumps({"type": "transcript", "text": spoken, "final": True})
        )
        await send_translation(websocket, loop, spoken)

    try:
        async for message in websocket:
            if isinstance(message, bytes):
                if not USE_STT:
                    if not warned_audio:
                        print("[mock] audio ignorado; arrancá con --stt para transcribirlo")
                        warned_audio = True
                    continue
                utterance.add(message)
                if utterance.should_finalize:
                    pcm = utterance.pcm()
                    utterance.reset()
                    # One transcription at a time; a burst of audio while one
                    # is running is dropped rather than queued (and the next
                    # silence window picks up whatever comes next).
                    if transcribing is None or transcribing.done():
                        transcribing = asyncio.ensure_future(
                            transcribe_and_translate(pcm)
                        )
                continue

            try:
                payload = json.loads(message)
            except json.JSONDecodeError:
                print(f"[mock] frame no JSON, ignorado: {message!r}")
                continue

            if payload.get("type") != "text":
                print(f"[mock] tipo ignorado: {payload.get('type')!r}")
                continue

            text = (payload.get("text") or "").strip()
            print(f"[mock] frase: {text!r}")

            if FAIL_WITH in text.upper():
                await websocket.send(
                    json.dumps({"type": "error", "message": "Mock: no se pudo traducir."})
                )
                continue

            await websocket.send(
                json.dumps({"type": "transcript", "text": text, "final": True})
            )
            await send_translation(websocket, loop, text)
    except websockets.exceptions.ConnectionClosed:
        # Normal: the app dropped the session, it reconnects on its own.
        print("[mock] sesión cerrada por el cliente")


async def main() -> None:
    if not DEFAULT_ANIMATION.is_file():
        sys.exit(f"No encuentro la animación de ejemplo: {DEFAULT_ANIMATION}")

    async with serve(handle_session, HOST, PORT, process_request=handle_http):
        print(f"[mock] escuchando en ws://{HOST}:{PORT}/ws/translate")
        if USE_OLLAMA:
            print(f"[mock] glosas: LLM real ({OLLAMA_MODEL}) vía {OLLAMA_URL}")
            print(f'[mock] calentá antes: ollama run {OLLAMA_MODEL} "hola"')
        else:
            print(f"[mock] glosas fijas: {FIXED_GLOSSES}")
        if USE_STT:
            model = local_stt.model_path()
            if model is None:
                print("[mock] --stt pedido pero no hay whisper.cpp ni modelo en "
                      f"{local_stt.CACHE}")
                return
            print(f"[mock] STT local: whisper.cpp con {model.name}")
        else:
            print("[mock] STT apagado: el audio se ignora (--stt para transcribirlo)")
        print(f"[mock] animación servida: {DEFAULT_ANIMATION.name}")
        print("[mock] Ctrl+C para salir")
        await asyncio.Future()


if __name__ == "__main__":
    if "--ollama" in sys.argv:
        USE_OLLAMA = True
    if "--stt" in sys.argv:
        USE_STT = True
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n[mock] cerrado")
