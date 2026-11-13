#!/usr/bin/env python3
"""PR-REN-05 · Recursos de una sesión (fase de voz y transcripción con GPU).

    python docs/plan-pruebas/arnes/ren05.py --url ws://127.0.0.1:8180/ws/translate \
        --stt http://127.0.0.1:8100 --contenedores p8lab-backend p8lab-stt \
        --salida ../data/evidencias/plan-pruebas/evidencias/REN/PR-REN-05_E_20261110_02

Mide dos cosas, como el 06/11:
1. Una sesión real con los 10 audios de FR-01-A a ritmo real (la misma que `ren06.py`). Mientras
   dura, cada segundo se lee la GPU (`nvidia-smi`: uso y memoria) y cada ≈2 s los contenedores
   (`docker stats`: procesador y memoria; 100 % es un núcleo). También un reposo de 30 s antes.
2. La transcripción de los 10 audios por `POST /transcribe` con el STT en GPU (`elapsed_ms`).
No repite la CPU explícita ni «pide cuda sin GPU» (arrancar una instancia de CPU tarda ≈3 min cada
una): esas dos ya cumplían sus umbrales el 10/11.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import subprocess
import threading
import time
import urllib.request
import uuid
from pathlib import Path

from ren06 import abrir, frases, percentil, sesion, una_frase


def estadistica(valores: list[float]) -> dict | None:
    if not valores:
        return None
    return {"media": round(sum(valores) / len(valores), 1), "max": round(max(valores), 2), "n": len(valores)}


class Muestreador:
    def __init__(self, contenedores: list[str]) -> None:
        self.contenedores = contenedores
        self.parar = threading.Event()
        self.gpu: list[tuple[float, float, float]] = []
        self.docker: list[tuple[float, str, float, float]] = []
        self.hilos = [threading.Thread(target=self._gpu, daemon=True),
                      threading.Thread(target=self._docker, daemon=True)]

    def _gpu(self) -> None:
        while not self.parar.is_set():
            s = subprocess.run(["nvidia-smi", "--query-gpu=utilization.gpu,memory.used",
                                "--format=csv,noheader,nounits"], capture_output=True, text=True).stdout
            try:
                uso, mem = (float(x) for x in s.strip().splitlines()[0].split(","))
                self.gpu.append((time.time(), uso, mem))
            except (ValueError, IndexError):
                pass
            self.parar.wait(1)

    def _docker(self) -> None:
        while not self.parar.is_set():
            s = subprocess.run(["docker", "stats", "--no-stream", "--format",
                                "{{.Name}};{{.CPUPerc}};{{.MemUsage}}", *self.contenedores],
                               capture_output=True, text=True).stdout
            for linea in s.splitlines():
                try:
                    nombre, cpu, mem = linea.split(";")
                    usado = mem.split("/")[0].strip()
                    mib = float(usado[:-3]) * (1024 if usado.endswith("GiB") else 1)
                    self.docker.append((time.time(), nombre, float(cpu.strip("%")), mib))
                except ValueError:
                    pass

    def iniciar(self) -> None:
        for h in self.hilos:
            h.start()

    def detener(self) -> None:
        self.parar.set()
        for h in self.hilos:
            h.join()

    def resumen(self, t0: float, t1: float) -> dict:
        g = [x for x in self.gpu if t0 <= x[0] <= t1]
        salida = {"duracion_s": round(t1 - t0, 1),
                  "gpu_uso": estadistica([x[1] for x in g]), "gpu_mem_mib": estadistica([x[2] for x in g])}
        for c in self.contenedores:
            d = [x for x in self.docker if t0 <= x[0] <= t1 and x[1] == c]
            salida[c] = {"cpu_pct": estadistica([x[2] for x in d]), "mem_mib": estadistica([x[3] for x in d])}
        return salida


def transcribir(stt: str, wav: Path) -> float:
    frontera = uuid.uuid4().hex
    cuerpo = (f"--{frontera}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{wav.name}\"\r\n"
              f"Content-Type: audio/wav\r\n\r\n").encode() + wav.read_bytes() + f"\r\n--{frontera}--\r\n".encode()
    pet = urllib.request.Request(f"{stt}/transcribe", data=cuerpo, method="POST",
                                 headers={"Content-Type": f"multipart/form-data; boundary={frontera}"})
    with urllib.request.urlopen(pet, timeout=120) as r:
        return float(json.loads(r.read())["elapsed_ms"])


async def fase_voz(url: str, lista: list[dict]) -> None:
    ws = await abrir(url)
    try:
        for f in lista:
            await una_frase(ws, f)
    finally:
        await ws.close()


def principal() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="ws://127.0.0.1:8180/ws/translate")
    ap.add_argument("--stt", default="http://127.0.0.1:8100")
    ap.add_argument("--contenedores", nargs=2, default=["p8lab-backend", "p8lab-stt"])
    ap.add_argument("--salida", required=True, help="ruta sin extensión")
    a = ap.parse_args()
    lista = frases()

    asyncio.run(sesion(a.url, lista, 0))  # calentamiento: modelo de embeddings, caché y GPU
    m = Muestreador(a.contenedores)
    m.iniciar()
    time.sleep(2)
    t0 = time.time()
    time.sleep(30)
    t1 = time.time()
    asyncio.run(fase_voz(a.url, lista))
    t2 = time.time()
    time.sleep(3)
    m.detener()

    ms = [transcribir(a.stt, f["wav"]) for f in lista]
    datos = {"fases": {"reposo": m.resumen(t0, t1), "voz": m.resumen(t1, t2)},
             "transcripcion_10_audios_gpu": {"n": len(ms), "p50": round(percentil(ms, 50), 1),
                                              "p95": round(percentil(ms, 95), 1), "max": max(ms)}}
    Path(a.salida).with_suffix(".json").write_text(json.dumps(datos, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(datos, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    principal()
