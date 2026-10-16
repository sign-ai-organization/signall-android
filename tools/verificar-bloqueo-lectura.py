#!/usr/bin/env python3
"""¿El bucle de lectura del WebSocket del backend se bloquea durante una traducción?

Por qué importa
---------------
`app/controller/router.py` lee frames del cliente en un `while True` y, para un
frame `text`, hace `await controller.handle_text(payload)` **en línea**. Ese
`await` dura toda la traducción. Mientras dura, el bucle no lee audio, así que el
audio del micrófono no se reenvía al STT: se queda en el socket.

La consecuencia no es que se pierda audio (no se pierde), sino que **el STT ve la
voz con retraso**. Su contador `_silence_ms` sólo avanza por frames recibidos, así
que el cierre por silencio también se empuja. En en vivo eso significa que cada
frase se transcribe después de lo que realmente pasó, y el retraso se acumula de
frase en frase.

Cómo lo mide
------------
Audio a 1x (30 ms por chunk, como `AudioCapture`) en una tarea, una frase fría en
otra, y un muestreador **independiente** que consulta `audio_chunks_total` del
backend cada 200 ms sin esperar ningún mensaje. Si el bucle está vivo, el
contador sube ~33 chunks/s. Si está bloqueado, se congela durante la traducción y
salta de golpe al terminarla.

Uso
---
    python3 tools/verificar-bloqueo-lectura.py

Sale con código 1 si el atraso pasó `--max-retraso-ms` (por defecto 500).
"""

from __future__ import annotations

import argparse
import asyncio
import json
import random
import time
import urllib.request

try:
    import websockets
except ImportError:  # pragma: no cover
    raise SystemExit("falta `websockets`: pip install websockets")

URL = "ws://127.0.0.1:8080/ws/translate"
SILENCIO = bytes(960)  # PCM16 a 16 kHz: 30 ms de silencio digital, bajo el umbral
CHUNK_MS = 30

# Palabras que no están en el catálogo de LSC50 ni en las filas de
# `semantic_cache`. Hace falta una oración **semánticamente** distinta, no una
# cadena distinta: la caché compara embeddings, y dos frases que sólo cambian en
# las últimas cinco letras quedan a distancia < 0.05 y salen como HIT en 29 ms.
SUJETOS = ["acólito", "podadora", "jamonero", "ultramarina",
           "canastillero", "encuadernadora", "sinfonietta"]
VERBOS = ["descolgó", "amortiguó", "homologó", "refiltró", "injertó",
          "desconchó", "coronó"]
COMPLEMENTOS = ["velamen", "tejalumbre", "kiosco", "candiles",
                "afilador", "badajo", "estantería"]


def frase_fria() -> str:
    """Dos oraciones que dan de lleno en el LLM, para que la traducción sea lenta."""
    return (f"El {random.choice(SUJETOS)} {random.choice(VERBOS)} el {random.choice(COMPLEMENTOS)}. "
            f"La {random.choice(SUJETOS)} {random.choice(VERBOS)} la {random.choice(COMPLEMENTOS)}.")


def chunks_leidos() -> float:
    with urllib.request.urlopen("http://127.0.0.1:8080/metrics.json", timeout=3) as r:
        return json.load(r)["counters"].get("audio_chunks_total", 0.0)


async def medir(espera_s: float, tras_envio_s: float) -> dict:
    enviados = 0
    eventos: list[tuple[str, int]] = []
    muestras: list[tuple[float, float, float]] = []
    base = chunks_leidos()

    async with websockets.connect(URL, max_size=None) as ws:
        inicio = time.monotonic()

        async def audio() -> None:
            nonlocal enviados
            while True:
                await ws.send(SILENCIO)
                enviados += 1
                await asyncio.sleep(CHUNK_MS / 1000)

        async def recibir() -> None:
            while True:
                msg = json.loads(await ws.recv())
                tipo = msg.get("type")
                if tipo == "segment":
                    eventos.append((f"segment {msg['index']}/{msg['total']}",
                                    round((time.monotonic() - inicio) * 1000)))
                elif tipo in ("done", "error"):
                    eventos.append((tipo, round((time.monotonic() - inicio) * 1000)))
                    return

        t_audio = asyncio.create_task(audio())
        t_rx = asyncio.create_task(recibir())

        await asyncio.sleep(espera_s)          # audio solo, sin traducción
        base_trad, enviados_trad = chunks_leidos(), enviados
        t_trad = time.monotonic()

        async def muestrear() -> None:
            while True:
                muestras.append((time.monotonic() - t_trad,
                                 chunks_leidos() - base_trad,
                                 enviados - enviados_trad))
                await asyncio.sleep(0.2)

        t_muestreo = asyncio.create_task(muestrear())
        await ws.send(json.dumps({"type": "text", "text": frase_fria()}, ensure_ascii=False))

        await t_rx
        t_done = time.monotonic()
        await asyncio.sleep(tras_envio_s)      # deja drenar lo que quedó
        for t in (t_muestreo, t_audio):
            t.cancel()
        await asyncio.gather(t_muestreo, t_audio, return_exceptions=True)

    return {
        "eventos": eventos,
        "duracion_ms": round((t_done - t_trad) * 1000),
        "muestras": muestras,
        "retraso_max_s": max((env - lei) / 33.3 for _, lei, env in muestras),
        "enviados_durante": enviados - enviados_trad,
        "leidos_durante": chunks_leidos() - base_trad,
    }


async def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--max-retraso-ms", type=float, default=500.0,
                    help="retraso máximo tolerable del audio durante la traducción")
    args = ap.parse_args()

    print("midiendo el bloqueo del bucle de lectura…")
    r = await medir(espera_s=3.0, tras_envio_s=1.5)

    print()
    print("=" * 74)
    print("BLOQUEO DEL BUCLE DE LECTURA DEL WEBSOCKET")
    print("=" * 74)
    print(f"  eventos (ms desde el inicio): {r['eventos']}")
    print(f"  la traducción duró {r['duracion_ms']} ms")
    print()
    print(f"  {'t (s)':>7} | {'leídos':>8} {'Δ':>5} | {'enviados':>9} {'Δ':>5} | {'atraso':>8}")
    print(f"  {'-'*7}-+-{'-'*8}-+-{'-'*5}-+-{'-'*9}-+-{'-'*5}-+-{'-'*8}")
    pl = pe = 0.0
    for t_rel, lei, env in r["muestras"]:
        print(f"  {t_rel:>7.2f} | {lei:>8.0f} {lei-pl:>5.0f} | {env:>9.0f} {env-pe:>5.0f} | "
              f"{(env-lei)/33.3:>7.2f}s"
              f"{'  <-- traducción' if 0 <= t_rel*1000 <= r['duracion_ms'] else ''}")
        pl, pe = lei, env

    atraso_ms = r["retraso_max_s"] * 1000
    print()
    print(f"  retraso máximo del audio .......... {atraso_ms:.0f} ms")
    print(f"  presupuesto ....................... {args.max_retraso_ms:.0f} ms")
    print("=" * 74)

    if atraso_ms > args.max_retraso_ms:
        print("FALLA: el backend no leyó audio mientras traducía. `router.py` espera")
        print("`handle_text` dentro del bucle de lectura; el audio se queda en el socket")
        print("y el STT ve la voz con retraso.")
        return 1
    print("ok: el bucle de lectura siguió leyendo durante la traducción")
    return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))