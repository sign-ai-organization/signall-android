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

# Si la frase contiene esta palabra se responde con un error, para probar el
# camino de fallo del cliente.
FAIL_WITH = "FALLA"

# Puesto por --ollama.
USE_OLLAMA = False


def animation_bytes() -> bytes:
    return DEFAULT_ANIMATION.read_bytes()


def gloss_filename(gloss: str) -> str:
    """`YO` → `yo.json`. El backend guarda la ruta; el nombre del archivo es
    cosa de este mock."""
    safe = "".join(c if c.isalnum() or c in "-_" else "_" for c in gloss.strip())
    return f"{safe or 'glosa'}.json"


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
                {"text": g, "available": True, "glb_path": f"/static/{gloss_filename(g)}"}
                for g in glosses
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
        return Response(
            200,
            "OK",
            Headers([("Content-Type", "application/json")]),
            animation_bytes(),
        )
    return Response(404, "Not Found", Headers(), b"not found")


async def handle_session(websocket) -> None:
    print(f"[mock] sesión abierta desde {websocket.remote_address}")

    try:
        async for message in websocket:
            if isinstance(message, bytes):
                # Audio real: el backend se lo reenvía a Whisper. Acá se ignora.
                print(f"[mock] audio recibido ({len(message)} bytes), ignorado")
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

            # La llamada al LLM bloquea: fuera del event loop, para que el
            # heartbeat siga respondiendo mientras espera.
            loop = asyncio.get_running_loop()
            try:
                result = await loop.run_in_executor(None, build_result, text)
            except (urllib.error.URLError, OSError, KeyError, TimeoutError) as error:
                print(f"[mock] LLM falló: {error}")
                print("[mock] ¿arrancaste Ollama? ¿lo calentaste con "
                      f"`ollama run {OLLAMA_MODEL} \"hola\"`?")
                await websocket.send(
                    json.dumps({"type": "error", "message": f"Mock: el LLM falló ({error})."})
                )
                continue

            glosses = [g["text"] for g in result[0]["glosses"]]
            print(f"[mock] glosas: {glosses}")
            await websocket.send(json.dumps(result))
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
        print(f"[mock] animación servida: {DEFAULT_ANIMATION.name}")
        print("[mock] Ctrl+C para salir")
        await asyncio.Future()


if __name__ == "__main__":
    if "--ollama" in sys.argv:
        USE_OLLAMA = True
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n[mock] cerrado")
