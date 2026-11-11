#!/usr/bin/env python3
"""LLM falso compatible con la API de OpenAI, para PR-CAC-05 (sin costo y con retardo fijo).

    python docs/plan-pruebas/arnes/llm_falso.py --puerto 9000 --retardo-ms 700

El backend lo usa con `LLM_BASE_URL=http://<host>:9000/v1`. Responde siempre las glosas
«HOLA TU» tras el retardo. El modo se cambia con `POST /modo/normal` o `POST /modo/vacio`; en
modo `vacio` devuelve contenido vacío, y el backend no guarda una respuesta vacía en la caché.
`GET /llamadas` devuelve cuántas llamadas recibió, por modo.
"""

from __future__ import annotations

import argparse
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ESTADO = {"modo": "normal", "retardo": 0.7, "llamadas": {"normal": 0, "vacio": 0}}
CERRADURA = threading.Lock()


class Manejador(BaseHTTPRequestHandler):
    def _responder(self, codigo: int, cuerpo: dict) -> None:
        datos = json.dumps(cuerpo).encode()
        self.send_response(codigo)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(datos)))
        self.end_headers()
        self.wfile.write(datos)

    def do_GET(self) -> None:  # noqa: N802
        if self.path == "/llamadas":
            self._responder(200, {"modo": ESTADO["modo"], **ESTADO["llamadas"]})
        else:
            self._responder(200, {"ok": True})

    def do_POST(self) -> None:  # noqa: N802
        largo = int(self.headers.get("Content-Length") or 0)
        self.rfile.read(largo)
        if self.path.startswith("/modo/"):
            with CERRADURA:
                ESTADO["modo"] = self.path.rsplit("/", 1)[1]
            self._responder(200, {"modo": ESTADO["modo"]})
            return
        with CERRADURA:
            modo = ESTADO["modo"]
            ESTADO["llamadas"][modo] = ESTADO["llamadas"].get(modo, 0) + 1
        time.sleep(ESTADO["retardo"])
        contenido = "" if modo == "vacio" else "HOLA TU"
        self._responder(200, {
            "id": "falso", "object": "chat.completion", "created": int(time.time()),
            "model": "falso",
            "choices": [{"index": 0, "finish_reason": "stop",
                         "message": {"role": "assistant", "content": contenido}}],
            "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2},
        })

    def log_message(self, *args) -> None:  # silencio
        pass


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--puerto", type=int, default=9000)
    ap.add_argument("--retardo-ms", type=float, default=700)
    a = ap.parse_args()
    ESTADO["retardo"] = a.retardo_ms / 1000
    ThreadingHTTPServer(("0.0.0.0", a.puerto), Manejador).serve_forever()
