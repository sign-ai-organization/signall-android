#!/usr/bin/env python3
"""Mock de `../sign-ai-backend` para probar la app sin Neon, Whisper ni Ollama.

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

Uso:

    pip install websockets
    python tools/mock_backend.py

Y en la app, apuntando al emulador (10.0.2.2 es el host desde el emulador):

    ./gradlew installDebug

Ojo: no escucha audio real — ignora los frames binarios y siempre responde con
la misma frase de prueba. Es para ver la app, no para medir nada.
"""

from __future__ import annotations

import asyncio
import json
import pathlib
import sys

try:
    import websockets
    from websockets.asyncio.server import serve
    from websockets.http11 import Headers, Response
except ImportError:  # pragma: no cover
    sys.exit("Falta la dependencia: pip install websockets")

HOST = "0.0.0.0"
PORT = 8080

REPO = pathlib.Path(__file__).resolve().parent.parent
DEFAULT_ANIMATION = REPO / "app/src/main/assets/web/sign.json"

# Se responden siempre estas glosas, para que la prueba sea estable.
# `phrase_type` es un PhraseType del backend: define los marcadores no manuales.
RESULT = [
    {
        "type": "AFFIRMATIVE",
        "glosses": [
            {"text": "YO", "available": True, "glb_path": "/static/yo.json"},
            {"text": "UNIVERSIDAD", "available": True, "glb_path": "/static/universidad.json"},
            {"text": "IR", "available": True, "glb_path": "/static/ir.json"},
        ],
    }
]

# Si la frase contiene esta palabra se responde con un error, para probar el
# camino de fallo del cliente.
FAIL_WITH = "FALLA"


def animation_bytes() -> bytes:
    return DEFAULT_ANIMATION.read_bytes()


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
            json.dumps({"status": "ok", "mock": True}).encode(),
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
            await asyncio.sleep(0.4)  # imitates the LLM round trip
            await websocket.send(json.dumps(RESULT))
    except websockets.exceptions.ConnectionClosed:
        # Normal: the app dropped the session, it reconnects on its own.
        print("[mock] sesión cerrada por el cliente")


async def main() -> None:
    if not DEFAULT_ANIMATION.is_file():
        sys.exit(f"No encuentro la animación de ejemplo: {DEFAULT_ANIMATION}")

    async with serve(
        handle_session,
        HOST,
        PORT,
        process_request=handle_http,
    ):
        print(f"[mock] escuchando en ws://{HOST}:{PORT}/ws/translate")
        print(f"[mock] {len(RESULT[0]['glosses'])} glosas por respuesta, animación: "
              f"{DEFAULT_ANIMATION.name}")
        print("[mock] Ctrl+C para salir")
        await asyncio.Future()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n[mock] cerrado")
