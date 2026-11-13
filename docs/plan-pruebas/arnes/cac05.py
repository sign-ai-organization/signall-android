#!/usr/bin/env python3
"""PR-CAC-05 · Guardar una traducción nueva no debe retrasar la respuesta.

Mide, con un LLM falso de retardo fijo (`llm_falso.py`) y la caché vacía, cuánto tarda la respuesta de
una frase nueva cuando el backend la **guarda** (modo `normal`) y cuando **no la guarda** (modo `vacio`:
el LLM devuelve contenido vacío y el backend no guarda una respuesta vacía). Las dos condiciones pasan
por el mismo camino hasta el final: una sola llamada al LLM, con el mismo retardo; solo cambia el guardado.

    python docs/plan-pruebas/arnes/cac05.py --datos <carpeta de extraer_anexo.py> \
        --salida ../data/evidencias/plan-pruebas/evidencias/CAC/PR-CAC-05_E_20261110_02 [--invertir]

Se alternan las dos condiciones frase a frase (A, B, A, B...) con 1 s de pausa. `--invertir` cambia
qué mitad de las frases va con guardado, para que el resultado no dependa de las frases.
También mide cuánto tarda en aparecer la fila nueva en la caché (`cache_rows` de `/health`) tras el `done`.
"""

from __future__ import annotations

import argparse
import asyncio
import csv
import json
import time
import urllib.request
from pathlib import Path

import websockets

CALENTAMIENTO = "Hasta luego, nos vemos mañana."


def percentil(valores: list[float], p: float) -> float:
    v = sorted(valores)
    k = (len(v) - 1) * p / 100
    i = int(k)
    return v[i] + (v[min(i + 1, len(v) - 1)] - v[i]) * (k - i)


def resumen(valores: list[float]) -> dict:
    return {"n": len(valores), "p50": round(percentil(valores, 50), 1),
            "p95": round(percentil(valores, 95), 1), "max": round(max(valores), 1)}


def http(metodo: str, url: str) -> dict:
    peticion = urllib.request.Request(url, method=metodo, data=b"" if metodo == "POST" else None)
    with urllib.request.urlopen(peticion, timeout=10) as r:
        return json.loads(r.read())


def filas_cache(base: str) -> int:
    return http("GET", f"{base}/health")["cache_rows"]


async def enviar(url: str, texto: str, base: str) -> dict:
    async with websockets.connect(url, max_size=None) as ws:
        while json.loads(await asyncio.wait_for(ws.recv(), timeout=30)).get("type") != "capability":
            pass
        antes = filas_cache(base)
        t0 = time.perf_counter()
        await ws.send(json.dumps({"type": "text", "text": texto}))
        while True:
            m = json.loads(await asyncio.wait_for(ws.recv(), timeout=60))
            if m.get("type") in ("done", "error"):
                respuesta = round(1000 * (time.perf_counter() - t0), 1)
                fin = m["type"]
                break
        t_done = time.perf_counter()
    guardada = None
    while time.perf_counter() - t_done < 3:  # la fila aparece unos 200 ms después del `done`
        if filas_cache(base) > antes:
            guardada = round(1000 * (time.perf_counter() - t_done), 1)
            break
        await asyncio.sleep(0.02)
    return {"respuesta_ms": respuesta, "fin": fin, "ms_hasta_guardada": guardada}


def frases(datos: Path) -> list[tuple[str, str]]:
    fr01 = json.loads((datos / "FR-01.json").read_text(encoding="utf-8"))
    return [(f[0], f[1]) for f in fr01 if f[3] == "1"]  # solo frases de una oración


async def medir(a: argparse.Namespace) -> list[dict]:
    lista = frases(Path(a.datos))
    if len(lista) < 2 * a.pares:
        raise SystemExit(f"hay {len(lista)} frases de una oración y se piden {2 * a.pares}")
    lista = lista[: 2 * a.pares]
    con_guardado, sin_guardado = lista[0::2], lista[1::2]
    if a.invertir:
        con_guardado, sin_guardado = sin_guardado, con_guardado
    base = a.url.replace("ws://", "http://").rsplit("/ws/", 1)[0]
    http("POST", f"{a.llm}/modo/normal")
    await enviar(a.url, CALENTAMIENTO, base)
    salida = []
    for (ia, ta), (ib, tb) in zip(con_guardado, sin_guardado, strict=True):
        for condicion, ident, texto, modo in (("con_guardado", ia, ta, "normal"),
                                             ("sin_guardado", ib, tb, "vacio")):
            http("POST", f"{a.llm}/modo/{modo}")
            r = await enviar(a.url, texto, base)
            salida.append({"condicion": condicion, "id": ident, "frase": texto, **r})
            await asyncio.sleep(1)
    http("POST", f"{a.llm}/modo/normal")
    return salida


def principal() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="ws://127.0.0.1:8181/ws/translate")
    ap.add_argument("--llm", default="http://127.0.0.1:9100")
    ap.add_argument("--datos", required=True)
    ap.add_argument("--salida", required=True, help="ruta sin extensión")
    ap.add_argument("--pares", type=int, default=10)
    ap.add_argument("--invertir", action="store_true")
    a = ap.parse_args()

    filas = asyncio.run(medir(a))
    con = [f["respuesta_ms"] for f in filas if f["condicion"] == "con_guardado"]
    sin = [f["respuesta_ms"] for f in filas if f["condicion"] == "sin_guardado"]
    guardadas = [f["ms_hasta_guardada"] for f in filas
                 if f["condicion"] == "con_guardado" and f["ms_hasta_guardada"] is not None]
    base = a.url.replace("ws://", "http://").rsplit("/ws/", 1)[0]
    datos = {
        "con_guardado": resumen(con), "sin_guardado": resumen(sin),
        "diferencia_p50_ms": round(percentil(con, 50) - percentil(sin, 50), 1),
        "relacion_p50": round(percentil(con, 50) / percentil(sin, 50), 3),
        "ms_hasta_guardada": resumen(guardadas) if guardadas else None,
        "filas_guardadas": len(guardadas), "filas_esperadas": len(con),
        "filas_cache_al_final": filas_cache(base),
        "llamadas_al_llm": http("GET", f"{a.llm}/llamadas"),
        "errores": sum(f["fin"] != "done" for f in filas),
        "invertido": a.invertir,
    }
    ruta = Path(a.salida)
    with open(ruta.with_suffix(".csv"), "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(filas[0]), delimiter=";")
        w.writeheader()
        w.writerows(filas)
    ruta.with_suffix(".json").write_text(json.dumps(datos, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(datos, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    principal()
