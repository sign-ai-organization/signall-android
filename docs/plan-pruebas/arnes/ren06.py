#!/usr/bin/env python3
"""PR-REN-06 · Sesiones simultáneas (ficha: 10 frases de FR-01-A con su audio de AU-01 por sesión).

    python docs/plan-pruebas/arnes/ren06.py --url ws://127.0.0.1:8180/ws/translate \
        --salida ../data/evidencias/plan-pruebas/evidencias/REN/PR-REN-06_E_20261110_02 --calentar

Método (el del 06/11): niveles de 1, 2 y 3 sesiones a la vez más un cuarto nivel de 1 sesión al final.
Cada sesión es una conexión `/ws/translate` que manda las 10 frases en sucesión (rotadas para que dos
sesiones no transcriban la misma frase a la vez), con el audio a ritmo real (PCM16, tramas de 30 ms),
`{"type":"end"}` y, al llegar la transcripción final, el texto como `text` (lo que hace la app).
Latencia = del fin del audio a `done`. El contador es `sessions_active` de `/metrics.json`, leído
cada 0,5 s. Un texto es «ajeno» si la transcripción final de una sesión se parece más a la frase de
otra sesión que a la suya.
"""

from __future__ import annotations

import argparse
import asyncio
import csv
import difflib
import json
import time
import unicodedata
import urllib.request
import wave
from pathlib import Path

import websockets

AQUI = Path(__file__).resolve().parent
AUDIOS = AQUI.parent / "audios"
IDS = ["001", "007", "013", "019", "025", "031", "037", "043", "049", "055"]  # H1, silencio, FR-01-A


def normalizar(texto: str) -> str:
    texto = unicodedata.normalize("NFD", texto.lower())
    texto = "".join(c for c in texto if unicodedata.category(c) != "Mn" or c == "̃")
    return "".join(c for c in texto if c.isalnum() or c == " ").strip()


def percentil(valores: list[float], p: float) -> float:
    v = sorted(valores)
    k = (len(v) - 1) * p / 100
    i = int(k)
    return v[i] + (v[min(i + 1, len(v) - 1)] - v[i]) * (k - i)


def resumen(valores: list[float]) -> dict | None:
    if not valores:
        return None
    return {"n": len(valores), "p50": round(percentil(valores, 50), 1),
            "p95": round(percentil(valores, 95), 1), "max": round(max(valores), 1)}


def frases() -> list[dict]:
    with open(AUDIOS / "manifiesto.csv", encoding="utf-8-sig", newline="") as f:
        filas = {r["id"]: r for r in csv.DictReader(f)}
    return [{"id": f"AU-01-{n}", "texto": filas[f"AU-01-{n}"]["texto_del_plan"],
             "wav": AUDIOS / "16k" / f"AU-01-{n}.wav"} for n in IDS]


def leer_wav(ruta: Path) -> tuple[bytes, int]:
    with wave.open(str(ruta), "rb") as w:
        return w.readframes(w.getnframes()), w.getframerate()


def contador(base: str) -> float:
    with urllib.request.urlopen(f"{base}/metrics.json", timeout=5) as r:
        d = json.loads(r.read())
    for bloque in ("gauges", "counters"):
        for k, v in d.get(bloque, {}).items():
            if k.startswith("sessions_active"):
                return float(v)
    return 0.0


async def abrir(url: str):
    ws = await websockets.connect(url, max_size=None)
    while json.loads(await asyncio.wait_for(ws.recv(), timeout=30)).get("type") != "capability":
        pass
    return ws


async def una_frase(ws, frase: dict) -> dict:
    audio, frecuencia = leer_wav(frase["wav"])
    paso = int(frecuencia * 0.03) * 2
    for d in range(0, len(audio), paso):
        await ws.send(audio[d:d + paso])
        await asyncio.sleep(0.03)
    t_fin = time.perf_counter()
    await ws.send(json.dumps({"type": "end"}))
    transcripcion = ""
    while True:
        m = json.loads(await asyncio.wait_for(ws.recv(), timeout=60))
        if m.get("type") == "transcript" and m.get("final"):
            transcripcion = m.get("text", "")
            break
        if m.get("type") == "error":
            return {"transcripcion": transcripcion, "latencia_ms": None, "fin": "error"}
    await ws.send(json.dumps({"type": "text", "text": transcripcion}))
    while True:
        m = json.loads(await asyncio.wait_for(ws.recv(), timeout=60))
        if m.get("type") in ("done", "error"):
            return {"transcripcion": transcripcion, "fin": m["type"],
                    "latencia_ms": round(1000 * (time.perf_counter() - t_fin), 1)}


async def sesion(url: str, lista: list[dict], offset: int) -> list[dict]:
    ws = await abrir(url)
    salida = []
    try:
        for k in range(len(lista)):
            f = lista[(offset + k) % len(lista)]
            try:
                r = await una_frase(ws, f)
            except Exception as e:  # noqa: BLE001 - un fallo se cuenta, no corta la prueba
                r = {"transcripcion": "", "latencia_ms": None, "fin": f"excepcion: {str(e)[:60]}"}
            salida.append({"id": f["id"], "esperado": f["texto"], **r})
    finally:
        await ws.close()
    return salida


async def nivel(url: str, base: str, lista: list[dict], n: int) -> dict:
    antes = await asyncio.to_thread(contador, base)
    maximo = antes
    hecho = False

    async def sondear():
        nonlocal maximo
        while not hecho:
            maximo = max(maximo, await asyncio.to_thread(contador, base))
            await asyncio.sleep(0.5)

    sonda = asyncio.create_task(sondear())
    res = await asyncio.gather(*(sesion(url, lista, s * 3) for s in range(n)))
    hecho = True
    await sonda
    await asyncio.sleep(2)
    despues = await asyncio.to_thread(contador, base)
    filas = []
    for s, sesion_res in enumerate(res, 1):
        for r in sesion_res:
            parecidos = {f["id"]: difflib.SequenceMatcher(None, normalizar(r["transcripcion"]),
                                                          normalizar(f["texto"])).ratio() for f in lista}
            mejor = max(parecidos, key=parecidos.get)
            ajena = bool(r["transcripcion"]) and mejor != r["id"] and parecidos[mejor] > 0.8
            filas.append({"sesiones": n, "sesion": s, **r, "ajena": ajena})
    return {"filas": filas, "contador": {"antes": antes, "max": maximo, "despues": despues}}


async def medir(url: str, calentar: bool) -> list[dict]:
    base = url.replace("ws://", "http://").rsplit("/ws/", 1)[0]
    lista = frases()
    if calentar:  # una pasada de una sesión: carga el modelo de embeddings y llena la caché con las frases
        await sesion(url, lista, 0)
    niveles = []
    for n in (1, 2, 3, 1):
        niveles.append(await nivel(url, base, lista, n))
        await asyncio.sleep(3)
    return niveles


def principal() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="ws://127.0.0.1:8180/ws/translate")
    ap.add_argument("--salida", required=True, help="ruta sin extensión")
    ap.add_argument("--calentar", action="store_true")
    a = ap.parse_args()
    niveles = asyncio.run(medir(a.url, a.calentar))
    todo, resumen_niveles = [], []
    for i, nv in enumerate(niveles, 1):
        lat = [f["latencia_ms"] for f in nv["filas"] if f["latencia_ms"] is not None]
        resumen_niveles.append({
            "nivel": i, "sesiones": nv["filas"][0]["sesiones"], "frases": len(nv["filas"]),
            "latencia_ms": resumen(lat), "errores": sum(f["fin"] != "done" for f in nv["filas"]),
            "ajenas": sum(f["ajena"] for f in nv["filas"]), "contador": nv["contador"]})
        todo += [{"nivel": i, **f} for f in nv["filas"]]
    ruta = Path(a.salida)
    with open(ruta.with_suffix(".csv"), "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["nivel", "sesiones", "sesion", "id", "esperado", "transcripcion",
                                          "latencia_ms", "fin", "ajena"], delimiter=";")
        w.writeheader()
        w.writerows(todo)
    ruta.with_suffix(".json").write_text(json.dumps(resumen_niveles, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(resumen_niveles, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    principal()
