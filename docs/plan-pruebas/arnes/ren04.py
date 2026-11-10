#!/usr/bin/env python3
"""PR-REN-04 · Arranque en frío: la primera traducción frente a las siguientes.

Se corre después de levantar el sistema, contra `ws://127.0.0.1:8080/ws/translate`:

    python3 docs/plan-pruebas/arnes/ren04.py --textos <FR-01.json> \
        --salida <evidencia.json>

Manda tres frases de FR-01-C seguidas y mide, de cada una, el tiempo hasta el
primer `segment` y hasta el `done`.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import time
from pathlib import Path

import websockets

FRASES = ["FR-01-001", "FR-01-007", "FR-01-017"]


async def medir(url: str, textos: list[str]) -> list[dict]:
    filas = []
    async with websockets.connect(url, max_size=None) as ws:
        for texto in textos:
            inicio = time.perf_counter()
            primero = None
            await ws.send(json.dumps({"type": "text", "text": texto}))
            while True:
                mensaje = json.loads(await asyncio.wait_for(ws.recv(), timeout=60))
                if mensaje.get("type") == "segment" and primero is None:
                    primero = (time.perf_counter() - inicio) * 1000
                if mensaje.get("type") == "done":
                    total = (time.perf_counter() - inicio) * 1000
                    break
            filas.append(
                {
                    "frase": texto,
                    "ms_primer_segmento": round(primero, 1) if primero is not None else None,
                    "ms_total": round(total, 1),
                }
            )
    return filas


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="ws://127.0.0.1:8080/ws/translate")
    ap.add_argument("--textos", required=True, type=Path)
    ap.add_argument("--salida", required=True, type=Path)
    args = ap.parse_args()
    textos = {f[0]: f[1] for f in json.loads(args.textos.read_text(encoding="utf-8"))}
    filas = asyncio.run(medir(args.url, [textos[i] for i in FRASES]))
    resumen = {"frases": filas}
    args.salida.parent.mkdir(parents=True, exist_ok=True)
    args.salida.write_text(json.dumps(resumen, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps(resumen, ensure_ascii=False))


if __name__ == "__main__":
    main()
