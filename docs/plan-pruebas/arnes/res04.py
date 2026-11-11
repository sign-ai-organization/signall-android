#!/usr/bin/env python3
"""PR-RES-04 · Falla de la base de datos, casos 1 y 3 (el caso 2 se midió el 10/11 con el arreglo del backend).

    python docs/plan-pruebas/arnes/res04.py --salida docs/plan-pruebas/evidencias/RES/PR-RES-04_E_20261110_02

Laboratorio: contenedores `p8lab-pg` (Postgres con pgvector), `p8lab-backend` (puerto 8180) y LLM real.
- Caso 1, base cortada mientras se traduce: una frase nueva; a los 300 ms del envío se detiene la base
  (`docker stop`). Se mide cuánto tarda la frase nueva y una repetida, si `/health` sigue `ok` durante el
  corte, y, al volver la base (`docker start`), si el backend se recupera SIN reiniciar, si la frase
  siguiente se traduce y se guarda, y si la frase del corte quedó guardada.
- Caso 3, base pausada: `docker pause` 6 s (emula una base que no responde, no es Neon dormida). Una frase
  nueva con la base pausada; cuánto tarda y cuántos segundos pasan hasta que se guarda tras reanudar.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import subprocess
import time
import urllib.request
from pathlib import Path

import websockets

PG, BACKEND = "p8lab-pg", "p8lab-backend"
BASE_HTTP, URL = "http://127.0.0.1:8180", "ws://127.0.0.1:8180/ws/translate"


def docker(*args: str) -> str:
    return subprocess.run(["docker", *args], capture_output=True, text=True, encoding="utf-8").stdout.strip()


def filas_base() -> int | None:
    s = docker("exec", PG, "psql", "-U", "postgres", "-At", "-c", "select count(*) from semantic_cache")
    return int(s) if s.isdigit() else None


def health() -> dict:
    try:
        with urllib.request.urlopen(f"{BASE_HTTP}/health", timeout=5) as r:
            return json.loads(r.read())
    except Exception as e:  # noqa: BLE001
        return {"status": f"sin respuesta: {str(e)[:40]}"}


async def enviar(texto: str, durante=None) -> dict:
    async with websockets.connect(URL, max_size=None) as ws:
        while json.loads(await asyncio.wait_for(ws.recv(), timeout=30)).get("type") != "capability":
            pass
        t0 = time.perf_counter()
        await ws.send(json.dumps({"type": "text", "text": texto}))
        tarea = asyncio.create_task(durante()) if durante else None
        segmentos, error = 0, None
        while True:
            m = json.loads(await asyncio.wait_for(ws.recv(), timeout=60))
            if m.get("type") == "segment":
                segmentos += 1
            if m.get("type") in ("done", "error"):
                if m["type"] == "error":
                    error = m.get("message")
                fin = m["type"]
                break
        ms = round(1000 * (time.perf_counter() - t0), 1)
        if tarea:
            await tarea
        return {"fin": fin, "ms": ms, "segmentos": segmentos, "error": error}


async def esperar_guardada(antes: int, tope_s: float = 20) -> float | None:
    t0 = time.perf_counter()
    while time.perf_counter() - t0 < tope_s:
        n = await asyncio.to_thread(filas_base)
        if n is not None and n > antes:
            return round(time.perf_counter() - t0, 1)
        await asyncio.sleep(0.2)
    return None


async def caso1() -> dict:
    out = {"alcance": "laboratorio aparte: base detenida con docker stop mientras se traduce; no es Neon"}
    out["base_sana"] = {"health": health(), "filas": filas_base(),
                        "frase_nueva_sin_corte": await enviar("Mi abuela cocina para toda la familia.")}
    await asyncio.sleep(1.5)
    antes = filas_base()

    async def cortar():
        await asyncio.sleep(0.3)
        docker("stop", PG)

    out["corte"] = {"frase_nueva": await enviar("Los vecinos celebran el cumpleaños del niño.", cortar)}
    out["corte"]["frase_repetida"] = await enviar("Mi abuela cocina para toda la familia.")
    out["corte"]["health_durante"] = []
    t0 = time.perf_counter()
    while time.perf_counter() - t0 < 30:
        out["corte"]["health_durante"].append([round(time.perf_counter() - t0, 1), health().get("status")])
        await asyncio.sleep(4)
    t_vuelve = time.perf_counter()
    docker("start", PG)
    ok = None
    while time.perf_counter() - t_vuelve < 90:
        if health().get("status") == "ok" and filas_base() is not None:
            ok = round(time.perf_counter() - t_vuelve, 1)
            break
        await asyncio.sleep(1)
    reinicios = docker("inspect", "-f", "{{.RestartCount}}", BACKEND)
    filas1 = filas_base()
    siguiente = await enviar("Tengo hambre y quiero comer pronto.")
    guardada = await esperar_guardada(filas1 or 0)
    out["recuperacion_sin_reiniciar"] = {
        "s_hasta_health_ok_tras_start": ok, "restart_count": reinicios, "health": health(),
        "frase_siguiente": siguiente, "s_hasta_guardada": guardada,
        "filas_antes_del_corte": antes, "filas_al_volver": filas1, "filas_final": filas_base(),
        "frase_del_corte_guardada": (antes is not None and filas1 is not None and filas1 > antes)}
    return out


async def caso3() -> dict:
    out = {"alcance": "laboratorio aparte: base pausada con docker pause 6 s (emula una base que no responde; no es Neon dormida)"}
    antes = filas_base()
    docker("pause", PG)
    t_pausa = time.perf_counter()
    nueva = await enviar("Mi hermano estudia por las noches.")
    out["frase_con_la_base_pausada"] = nueva
    out["health_durante_pausa"] = health()
    await asyncio.sleep(max(0, 6 - (time.perf_counter() - t_pausa)))
    docker("unpause", PG)
    out["s_pausa"] = round(time.perf_counter() - t_pausa, 1)
    out["s_hasta_guardada_tras_reanudar"] = await esperar_guardada(antes or 0)
    out["repetida_despues"] = await enviar("Mi hermano estudia por las noches.")
    out["filas"] = {"antes": antes, "despues": filas_base()}
    return out


def principal() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--salida", required=True, help="ruta sin extensión")
    a = ap.parse_args()
    datos = {"caso_1": asyncio.run(caso1())}
    time.sleep(3)
    datos["caso_3"] = asyncio.run(caso3())
    ruta = Path(a.salida)
    ruta.with_suffix(".json").write_text(json.dumps(datos, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(datos, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    principal()
