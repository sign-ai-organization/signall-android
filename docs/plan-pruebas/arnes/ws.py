#!/usr/bin/env python3
"""Cliente WebSocket del plan de pruebas · PR-REN-09 y PR-REN-08.

Corre en el host contra el backend canónico (por defecto `ws://127.0.0.1:8080/ws/translate`):

    python3 docs/plan-pruebas/arnes/ws.py ren09 --minutos 5
    python3 docs/plan-pruebas/arnes/ws.py ren08

`ren09` manda, en orden y en tiempo real, los audios de las 10 frases de FR-01-A,
espera la transcripción, reenvía la frase como `text` y cuenta cuántas frases
completa en el tiempo pedido. `ren08` mide los bytes que suben y bajan por el
socket en las mismas 10 frases, por voz y por texto.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import time
import wave
from pathlib import Path

import websockets

AUDIOS = {
    "FR-01-001": "001.wav",
    "FR-01-005": "007.wav",
    "FR-01-007": "013.wav",
    "FR-01-011": "019.wav",
    "FR-01-013": "025.wav",
    "FR-01-014": "031.wav",
    "FR-01-019": "037.wav",
    "FR-01-023": "043.wav",
    "FR-01-025": "049.wav",
    "FR-01-026": "055.wav",
}


def leer_wav(ruta: Path) -> tuple[bytes, int]:
    with wave.open(str(ruta), "rb") as w:
        return w.readframes(w.getnframes()), w.getframerate()


async def una_frase(ws, audio: bytes, frecuencia: int, texto: str, tiempo_real: bool) -> dict:
    """Manda el audio, espera el cierre y reenvía la frase como texto."""
    subido = 0
    bajado = 0
    transcripcion = ""
    segmentos = 0
    inicio = time.perf_counter()

    tamano = int(frecuencia * 0.03) * 2  # fragmentos de 30 ms, PCM16
    for desplazamiento in range(0, len(audio), tamano):
        trozo = audio[desplazamiento : desplazamiento + tamano]
        await ws.send(trozo)
        subido += len(trozo)
        if tiempo_real:
            await asyncio.sleep(0.03)
    await ws.send(json.dumps({"type": "end"}))

    while True:
        mensaje = await asyncio.wait_for(ws.recv(), timeout=30)
        bajado += len(mensaje.encode()) if isinstance(mensaje, str) else len(mensaje)
        if isinstance(mensaje, str):
            datos = json.loads(mensaje)
            if datos.get("type") == "transcript" and datos.get("final"):
                transcripcion = datos.get("text", "")
                break

    await ws.send(json.dumps({"type": "text", "text": texto}))
    subido += len(json.dumps({"type": "text", "text": texto}).encode())
    while True:
        mensaje = await asyncio.wait_for(ws.recv(), timeout=60)
        bajado += len(mensaje.encode()) if isinstance(mensaje, str) else len(mensaje)
        if isinstance(mensaje, str):
            datos = json.loads(mensaje)
            if datos.get("type") == "segment":
                segmentos += 1
            if datos.get("type") == "done":
                break
    return {
        "transcripcion": transcripcion,
        "segmentos": segmentos,
        "subido": subido,
        "bajado": bajado,
        "ms": round(1000 * (time.perf_counter() - inicio), 1),
    }


async def ren09(url: str, minutos: float, carpeta: Path, salida: Path) -> None:
    frases = list(AUDIOS.items())
    fin = time.perf_counter() + minutos * 60
    completas = 0
    errores = 0
    inicio = time.perf_counter()
    detalle = []
    async with websockets.connect(url, max_size=None) as ws:
        while time.perf_counter() < fin:
            identificador, archivo = frases[completas % len(frases)]
            audio, frecuencia = leer_wav(carpeta / archivo)
            try:
                resultado = await una_frase(ws, audio, frecuencia, _texto(identificador), tiempo_real=True)
                if resultado["segmentos"] == 0:
                    errores += 1
                detalle.append({"id": identificador, **resultado})
            except Exception as error:  # noqa: BLE001 - un fallo se cuenta, no corta la prueba
                errores += 1
                detalle.append({"id": identificador, "error": str(error)[:200]})
            completas += 1
    duracion = time.perf_counter() - inicio
    resumen = {
        "frases": completas,
        "errores": errores,
        "segundos": round(duracion, 1),
        "por_minuto": round(60 * completas / duracion, 1),
        "detalle": detalle,
    }
    salida.parent.mkdir(parents=True, exist_ok=True)
    salida.write_text(json.dumps(resumen, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps({k: v for k, v in resumen.items() if k != "detalle"}, ensure_ascii=False))


async def ren08(url: str, carpeta: Path, salida: Path) -> None:
    filas = []
    async with websockets.connect(url, max_size=None) as ws:
        for identificador, archivo in AUDIOS.items():
            audio, frecuencia = leer_wav(carpeta / archivo)
            resultado = await una_frase(ws, audio, frecuencia, _texto(identificador), tiempo_real=False)
            filas.append({"id": identificador, "via": "voz", **resultado})
        for identificador in AUDIOS:
            inicio = time.perf_counter()
            subido = len(json.dumps({"type": "text", "text": _texto(identificador)}).encode())
            bajado = 0
            segmentos = 0
            await ws.send(json.dumps({"type": "text", "text": _texto(identificador)}))
            while True:
                mensaje = await asyncio.wait_for(ws.recv(), timeout=60)
                bajado += len(mensaje.encode()) if isinstance(mensaje, str) else len(mensaje)
                if isinstance(mensaje, str):
                    datos = json.loads(mensaje)
                    if datos.get("type") == "segment":
                        segmentos += 1
                    if datos.get("type") == "done":
                        break
            filas.append(
                {
                    "id": identificador,
                    "via": "texto",
                    "subido": subido,
                    "bajado": bajado,
                    "segmentos": segmentos,
                    "ms": round(1000 * (time.perf_counter() - inicio), 1),
                }
            )
    resumen = {"filas": filas}
    salida.parent.mkdir(parents=True, exist_ok=True)
    salida.write_text(json.dumps(resumen, ensure_ascii=False, indent=1), encoding="utf-8")
    for via in ("texto", "voz"):
        grupo = [f for f in filas if f["via"] == via]
        print(
            via,
            "sube media", round(sum(f["subido"] for f in grupo) / len(grupo)),
            "baja media", round(sum(f["bajado"] for f in grupo) / len(grupo)),
        )


TEXTOS: dict[str, str] = {}


def _texto(identificador: str) -> str:
    return TEXTOS[identificador]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("modo", choices=["ren09", "ren08"])
    ap.add_argument("--url", default="ws://127.0.0.1:8080/ws/translate")
    ap.add_argument("--minutos", type=float, default=5)
    ap.add_argument("--audios", type=Path, required=True)
    ap.add_argument("--textos", type=Path, required=True)
    ap.add_argument("--salida", type=Path, required=True)
    args = ap.parse_args()
    global TEXTOS
    TEXTOS = {f[0]: f[1] for f in json.loads(args.textos.read_text(encoding="utf-8"))}
    if args.modo == "ren09":
        asyncio.run(ren09(args.url, args.minutos, args.audios, args.salida))
    else:
        asyncio.run(ren08(args.url, args.audios, args.salida))


if __name__ == "__main__":
    main()
