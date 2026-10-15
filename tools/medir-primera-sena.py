"""Remide la primera seña con el flujo por oraciones.

§7 de `../sign-ai-docs/auditoria-flujo-2026-10-15.md`: la línea base publicada
(913 ms p50 / 1336 ms p95) es del camino por palabras y **no es comparable** con
el flujo por oraciones. Con oraciones la primera seña sale al terminar la primera
oración, no a mitad de camino, así que el número sube por construcción y hay que
remedirlo antes de citarlo otra vez.

Este script hace lo que hacía `verify_realtime_latency.py` —reproducir los 28
fixtures por el WebSocket real a velocidad 1x— pero con el contrato que hay
ahora: el backend **no traduce la voz**, así que el guion tiene que hacer lo que
hace la app, que es mandar la transcripción como `text` recién cuando la frase
se cerró.

Mide dos cosas, y la diferencia entre ellas es justamente lo que cambió:

- `audio→seña`   — del primer sample de audio al primer `segment`. Es lo que
                    percibe el usuario, e **incluye** todo el STT.
- `final→seña`   — de la transcripción final al primer `segment`. Es lo que mide
                    `rt_first_sign_ms` en el backend: pipeline puro, sin el STT.

Con el camino por palabras la segunda daba ~400 ms. Con oraciones sube porque hay
que esperar a que la frase entera esté transcrita y las oracionesTranslator
estén hechas: eso es el costo conocido de la opción A, y conviene que salga de acá
y no de una conjetura.
"""

import asyncio
import json
import statistics
import sys
import wave
from pathlib import Path

try:
    import websockets
except ModuleNotFoundError:
    sys.exit("falta `websockets`: instalalo en el venv que uses (pip install websockets)")

URL = "ws://127.0.0.1:8080/ws/translate"
AUDIO = Path("/home/diegocachy/tesisTesteos/SignAI/sign-ai-stt/fixtures/audio")

# Un frame de 30 ms es lo que manda `AudioCapture`; reproducirlo a la misma
# velocidad es lo más cerca de hablar que se llega sin una persona.
CHUNK_MS = 30
SAMPLE_RATE = 16000
REALTIME = True           # a 1x. Con False no es una medición de nada.


async def una_frase(wav: Path) -> dict | None:
    """Reproduce un fixture por el socket, como la app: audio → `final` → `text`."""
    with wave.open(str(wav), "rb") as wf:
        if wf.getframerate() != SAMPLE_RATE or wf.getnchannels() != 1:
            raise RuntimeError(f"{wav.name}: se esperaba PCM16 mono a {SAMPLE_RATE} Hz")
        audio = wf.readframes(wf.getnframes())

    bytes_por_chunk = SAMPLE_RATE * 2 * CHUNK_MS // 1000  # 2 bytes por muestra

    async with websockets.connect(URL, max_size=None) as ws:
        bucle = asyncio.get_running_loop()
        t_audio = None

        # ── fase de voz: lo que hace el micrófono ────────────────────────────
        for offset in range(0, len(audio), bytes_por_chunk):
            if t_audio is None:
                t_audio = bucle.time()
            await ws.send(audio[offset : offset + bytes_por_chunk])
            if REALTIME:
                await asyncio.sleep(CHUNK_MS / 1000)

        # El STT cierra la frase por 1,5 s de silencio. Mandar el `end` lo cierra
        # al instante, pero eso **no** es lo que pasa en el dispositivo: ahí la
        # pausa es real. Por eso se espera un poco más que el silencio antes de
        # mandar el `end`, para que el cierre lo haga el STT y no el `end`.
        await asyncio.sleep(1.8)
        await ws.send(json.dumps({"type": "end"}))

        frase = None
        t_final = None
        while True:
            msg = json.loads(await asyncio.wait_for(ws.recv(), timeout=30))
            tipo = msg.get("type")
            if tipo == "error":
                raise RuntimeError(msg["message"])
            if tipo == "transcript" and msg.get("final"):
                frase = msg["text"]
                t_final = bucle.time()
                break

        if not frase.strip():
            return None

        # ── fase de traducción: lo que hace la app al decidir mandar ──────────
        t_envio = bucle.time()
        await ws.send(json.dumps({"type": "text", "text": frase}, ensure_ascii=False))

        primero = None
        oraciones = 0
        while True:
            msg = json.loads(await asyncio.wait_for(ws.recv(), timeout=60))
            tipo = msg.get("type")
            if tipo == "segment":
                oraciones = msg["total"]
                if primero is None:
                    primero = bucle.time()
            elif tipo == "done":
                break
            elif tipo == "error":
                raise RuntimeError(msg["message"])

        if primero is None:
            return None

        return {
            "frase": frase,
            "oraciones": oraciones,
            "audio_a_sena_ms": round((primero - t_audio) * 1000),
            "final_a_sena_ms": round((primero - t_final) * 1000),
            "espera_transcripcion_ms": round((t_envio - t_audio) * 1000),
        }


def p50(vals):
    return statistics.median(vals)


async def main() -> None:
    print(f"=== primera seña, flujo por oraciones ({URL}) ===")
    if not AUDIO.exists():
        sys.exit(f"no están los fixtures en {AUDIO}")

    filas = []
    fallos = []
    for path in sorted(AUDIO.glob("*.wav")):
        try:
            r = await una_frase(path)
        except Exception as exc:                      # noqa: BLE001
            fallos.append(f"{path.stem}: {exc}")
            continue
        if r is None:
            continue
        r["id"] = path.stem
        filas.append(r)
        print(
            f"  {path.stem:24} {r['oraciones']} or.  "
            f"audio→seña {r['audio_a_sena_ms']:6} ms   "
            f"final→seña {r['final_a_sena_ms']:5} ms   "
            f"(STT {r['espera_transcripcion_ms']} ms)"
        )

    print()
    for f in fallos:
        print(f"  ERROR {f}")

    if not filas:
        sys.exit("ningún fixture produjo una traducción")

    a = [f["audio_a_sena_ms"] for f in filas]
    t = [f["final_a_sena_ms"] for f in filas]
    stt = [f["espera_transcripcion_ms"] for f in filas]

    print(f"frases medidas: {len(filas)}")
    print()
    print("`final→seña` es lo que mide `rt_first_sign_ms` (pipeline, sin el STT):")
    print(f"  p50 {p50(t):.0f} ms   p95 {sorted(t)[int(len(t)*0.95)-1]} ms   "
          f"min {min(t)} ms   max {max(t)} ms")
    print("  línea base del camino por palabras: p50 913 ms / p95 1336 ms  ← NO COMPARABLE")
    print()
    print("`audio→seña` es lo que percibe el usuario (incluye el STT):")
    print(f"  p50 {p50(a):.0f} ms   min {min(a)} ms   max {max(a)} ms")
    print(f"  de los cuales el STT se lleva p50 {p50(stt):.0f} ms")
    print()
    print(f"oraciones por frase: p50 {p50([f['oraciones'] for f in filas]):.0f}, "
          f"máx {max(f['oraciones'] for f in filas)}")

    json.dump(filas, open("/tmp/opencode/primera_sena.json", "w"), indent=1)
    print("\nfilas crudas en /tmp/opencode/primera_sena.json")


if __name__ == "__main__":
    asyncio.run(main())