#!/usr/bin/env python3
"""PR-REN-02 · Latencia del procesamiento (ficha: 10 frases de FR-01-B × 5, por texto).

Uso (laboratorio con la caché vacía y el LLM real):

    python docs/plan-pruebas/arnes/ren02.py --url ws://127.0.0.1:8180/ws/translate \
        --contenedor p8lab-backend --salida docs/plan-pruebas/evidencias/REN/PR-REN-02_E_20261110_02

Método (igual que el del 06/11): una conexión nueva por envío; los tiempos por paso salen de las
líneas `Pipeline trace for ...` del log del backend, en el mismo orden de los envíos. El primer
envío de cada frase es la situación «LLM» (la caché vacía no la tiene); los otros cuatro, la
situación «caché». Antes de medir se manda una frase que NO es de FR-01-B para quitar el arranque
en frío (carga perezosa del modelo de embeddings y del cliente del LLM); esa frase no se cuenta.
Las frases salen del CSV del 06/11, para medir exactamente las mismas.
"""

from __future__ import annotations

import argparse
import ast
import asyncio
import csv
import json
import re
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path

import websockets

AQUI = Path(__file__).resolve().parent
FUENTE_FRASES = AQUI.parent / "evidencias/REN/PR-REN-02_E_20261106_01.csv"
CALENTAMIENTO = "Hasta luego, nos vemos mañana."
TRAZA = re.compile(r"Pipeline trace for '(.*?)': (\{'steps_ms'.*\})\s*$")


def percentil(valores: list[float], p: float) -> float:
    v = sorted(valores)
    if len(v) == 1:
        return v[0]
    k = (len(v) - 1) * p / 100
    i = int(k)
    return v[i] + (v[min(i + 1, len(v) - 1)] - v[i]) * (k - i)


def resumen(valores: list[float]) -> dict | None:
    if not valores:
        return None
    return {"n": len(valores), "p50": round(percentil(valores, 50), 1),
            "p95": round(percentil(valores, 95), 1), "max": round(max(valores), 1)}


def frases() -> list[tuple[str, str]]:
    vistas: dict[str, str] = {}
    with open(FUENTE_FRASES, encoding="utf-8-sig", newline="") as f:
        for fila in csv.DictReader(f, delimiter=";"):
            vistas.setdefault(fila["id"], fila["frase"])
    return list(vistas.items())


async def enviar(url: str, texto: str) -> tuple[float, str]:
    async with websockets.connect(url, max_size=None) as ws:
        # La sesión manda `capability` cuando termina de abrirse (en el laboratorio, sin STT, tarda
        # ≈4 s). Se espera antes de contar el tiempo para que `ws_ms` mida solo la frase.
        while json.loads(await asyncio.wait_for(ws.recv(), timeout=30)).get("type") != "capability":
            pass
        t0 = time.perf_counter()
        await ws.send(json.dumps({"type": "text", "text": texto}))
        while True:
            m = json.loads(await asyncio.wait_for(ws.recv(), timeout=60))
            if m.get("type") in ("done", "error"):
                return round(1000 * (time.perf_counter() - t0), 1), m["type"]


async def medir(url: str, repeticiones: int) -> list[dict]:
    await enviar(url, CALENTAMIENTO)
    envios = []
    for rep in range(1, repeticiones + 1):
        for ident, texto in frases():
            ms, fin = await enviar(url, texto)
            envios.append({"rep": rep, "id": ident, "frase": texto, "ws_ms": ms, "fin": fin})
    return envios


def trazas(contenedor: str, desde: str) -> list[dict]:
    p = subprocess.run(["docker", "logs", contenedor, "--since", desde], capture_output=True,
                       text=True, encoding="utf-8")
    salida = []
    for linea in (p.stdout + p.stderr).splitlines():
        m = TRAZA.search(linea)
        if m:
            salida.append({"frase": m.group(1), **ast.literal_eval(m.group(2))})
    return salida


def principal() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="ws://127.0.0.1:8180/ws/translate")
    ap.add_argument("--contenedor", default="p8lab-backend")
    ap.add_argument("--salida", required=True, help="ruta sin extensión")
    ap.add_argument("--repeticiones", type=int, default=5)
    a = ap.parse_args()

    desde = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    envios = asyncio.run(medir(a.url, a.repeticiones))
    time.sleep(1)
    tr = [t for t in trazas(a.contenedor, desde) if t["frase"] != CALENTAMIENTO]
    if len(tr) != len(envios):
        raise SystemExit(f"{len(tr)} trazas para {len(envios)} envíos: no se puede emparejar")

    filas = []
    for e, t in zip(envios, tr, strict=True):
        if e["frase"] != t["frase"]:
            raise SystemExit(f"desfase en {e['id']}: {e['frase']!r} contra {t['frase']!r}")
        fuentes = [s["source"] for s in t["sentences"]]
        situacion = "llm" if "llm" in fuentes else "cache"
        llm = [s["steps_ms"]["llm"] for s in t["sentences"] if "llm" in s["steps_ms"]]
        emb = [s["steps_ms"]["cache.embed"] for s in t["sentences"] if "cache.embed" in s["steps_ms"]]
        filas.append({**e, "situacion": situacion, "oraciones": len(t["sentences"]),
                      "fuentes": "/".join(fuentes), "total_traza_ms": t["steps_ms"]["total"],
                      "segmentacion_ms": t["steps_ms"]["segmentation"],
                      "validacion_ms": t["steps_ms"]["validation"], "llm_ms": llm, "embed_ms": emb})

    def con(situacion: str) -> list[dict]:
        return [f for f in filas if f["situacion"] == situacion]

    cache, llm = con("cache"), con("llm")
    datos = {
        "llm": {"n_frases": len(llm), "total_traza": resumen([f["total_traza_ms"] for f in llm]),
                "llm_por_oracion": resumen([x for f in llm for x in f["llm_ms"]]),
                "embedding": resumen([x for f in llm for x in f["embed_ms"]])},
        "cache": {"n_frases": len(cache), "total_traza": resumen([f["total_traza_ms"] for f in cache]),
                  "segmentacion": resumen([f["segmentacion_ms"] for f in cache]),
                  "validacion": resumen([f["validacion_ms"] for f in cache]),
                  "ws_total": resumen([f["ws_ms"] for f in cache])},
        "distribucion_fuente": {"llm": len(llm), "cache": len(cache)},
        "errores": sum(f["fin"] != "done" for f in filas),
    }
    base = Path(a.salida)
    with open(base.with_suffix(".csv"), "w", encoding="utf-8-sig", newline="") as f:
        campos = ["rep", "id", "frase", "situacion", "oraciones", "fuentes", "ws_ms",
                  "total_traza_ms", "segmentacion_ms", "validacion_ms", "llm_ms", "embed_ms", "fin"]
        w = csv.DictWriter(f, fieldnames=campos, delimiter=";", extrasaction="ignore")
        w.writeheader()
        for fila in filas:
            w.writerow({**fila, "llm_ms": json.dumps(fila["llm_ms"]), "embed_ms": json.dumps(fila["embed_ms"])})
    base.with_suffix(".json").write_text(json.dumps(datos, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(datos, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    principal()
