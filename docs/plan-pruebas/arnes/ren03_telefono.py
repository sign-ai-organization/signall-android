#!/usr/bin/env python3
"""PR-REN-03 · Latencia de extremo a extremo con el teléfono, por texto (situaciones «caché» y «LLM»).

    python docs/plan-pruebas/arnes/ren03_telefono.py --situacion cache --datos <carpeta de extraer_anexo.py> \
        --salida ../signall-docs/evidencias/plan-pruebas/evidencias/REN/PR-REN-03_E_20261110_02
    python docs/plan-pruebas/arnes/ren03_telefono.py --situacion llm   --datos ... --salida ...

Método (el del 06/11): las 10 frases de FR-01-A, cada una 5 veces, escritas en la app con `adb input text`
(en ASCII, sin acentos ni «¿», como el 06/11) y enviadas con «Traducir». Los tiempos salen del logcat
del teléfono (reloj del teléfono): `ms_envio_a_primera_sena` es la línea `envío→primera seña` de
`SignAllMetrics`; `ms_envio_a_ultima_sena` es de `texto ya enviado` a `representado`; `ms_representacion`
es el `representado: N señas en X ms`. Se reporta con las 50 corridas y sin la primera repetición (40).
- `cache`: antes de medir se envían las 10 frases al backend para que estén en la caché.
- `llm`: antes de cada envío se vacía la caché (`delete` y `POST /admin/cache/reload` con `X-Admin-Token: lab`).
Laboratorio: `p8lab-pg`, `p8lab-stt`, `p8lab-backend` (8180, `ADMIN_TOKEN=lab`) y el túnel `adb reverse tcp:8080 tcp:8180`.
"""

from __future__ import annotations

import argparse
import asyncio
import csv
import json
import re
import subprocess
import time
import unicodedata
import urllib.request
from pathlib import Path

import websockets

ADB = r"C:\Users\Sergio\AppData\Local\Android\Sdk\platform-tools\adb.exe"
APP = "com.signall.app"
CAMPO, TRADUCIR = (291, 2132), (288, 1510)  # «Traducir» sube a y=1510 con el teclado abierto
IDS = ["FR-01-001", "FR-01-005", "FR-01-007", "FR-01-011", "FR-01-013",
       "FR-01-014", "FR-01-019", "FR-01-023", "FR-01-025", "FR-01-026"]
PUERTO = 8180                 # laboratorio; el canónico es 8080 (se cambia con --puerto)
BASE = f"http://127.0.0.1:{PUERTO}"
BACKEND = "p8lab-backend"     # contenedor del backend (se cambia con --backend)


def adb(*args: str) -> str:
    return subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", timeout=60).stdout


def ascii_(texto: str) -> str:
    t = unicodedata.normalize("NFD", texto)
    return "".join(c for c in t if unicodedata.category(c) != "Mn").replace("¿", "").replace("¡", "")


def ahora() -> float:
    return float(adb("shell", "date +%s.%N").strip())


def percentil(v: list[float], p: float) -> float:
    v = sorted(v)
    k = (len(v) - 1) * p / 100
    i = int(k)
    return v[i] + (v[min(i + 1, len(v) - 1)] - v[i]) * (k - i)


def resumen(v: list[float]) -> dict | None:
    if not v:
        return None
    return {"n": len(v), "p50": round(percentil(v, 50), 1), "p95": round(percentil(v, 95), 1), "max": round(max(v), 1)}


def lineas(desde: float) -> list[tuple[float, str]]:
    out = []
    for l in adb("logcat", "-d", "-v", "epoch", "-s", "SignAllState", "SignAllMetrics").splitlines():
        m = re.match(r"\s*(\d+\.\d+)\s+\d+\s+\d+\s+\w\s+(.*)", l)
        if m and float(m.group(1)) >= desde:
            out.append((float(m.group(1)), m.group(2)))
    return out


def esperar(patron: str, desde: float, tope: float):
    t0 = time.time()
    while time.time() - t0 < tope:
        for t, l in lineas(desde):
            if re.search(patron, l):
                return t, l
        time.sleep(0.7)
    return None


def vaciar_cache() -> None:
    subprocess.run(["docker", "exec", "p8lab-pg", "psql", "-U", "postgres", "-At", "-c", "delete from semantic_cache"],
                   capture_output=True)
    urllib.request.urlopen(urllib.request.Request(f"{BASE}/admin/cache/reload", method="POST", data=b"",
                                                  headers={"X-Admin-Token": "lab"}), timeout=15).read()


async def precalentar(textos: list[str]) -> None:
    for t in textos:
        async with websockets.connect(f"ws://127.0.0.1:{PUERTO}/ws/translate", max_size=None) as ws:
            while json.loads(await ws.recv()).get("type") != "capability":
                pass
            await ws.send(json.dumps({"type": "text", "text": t}))
            while json.loads(await asyncio.wait_for(ws.recv(), timeout=60)).get("type") not in ("done", "error"):
                pass
    await asyncio.sleep(2)


def una_corrida(texto: str, llm: bool) -> dict:
    if llm:
        vaciar_cache()
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("logcat", "-c")
    t0 = ahora()
    adb("shell", "input", "tap", str(CAMPO[0]), str(CAMPO[1]))
    time.sleep(0.6)
    adb("shell", "input", "text", "'" + texto.replace(" ", "%s") + "'")
    time.sleep(0.5)
    adb("shell", "input", "tap", str(TRADUCIR[0]), str(TRADUCIR[1]))
    env = esperar(r"texto ya enviado", t0, 8)
    fin = esperar(r"representado:", t0, 60) if env else None
    time.sleep(2)
    ls = lineas(t0)
    r = {"completo": bool(env and fin), "t_envio_epoch": env[0] if env else None}
    if env and fin:
        pri = next((l for t, l in ls if "envío→primera seña" in l), "")
        rep = next((l for t, l in ls if "representado:" in l), "")
        a = re.search(r"primera seña: (\d+) ms", pri)
        b = re.search(r"representado: (\d+) señas en (\d+)ms", rep)
        r["ms_envio_a_primera_sena"] = int(a.group(1)) if a else None
        r["ms_envio_a_ultima_sena"] = round(1000 * (fin[0] - env[0]))
        r["ms_representacion"] = int(b.group(2)) if b else None
        r["n_senas"] = int(b.group(1)) if b else None
    return r


def principal() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--situacion", choices=["cache", "llm"], required=True)
    ap.add_argument("--datos", required=True)
    ap.add_argument("--salida", required=True, help="ruta sin extensión")
    ap.add_argument("--reps", type=int, default=5)
    ap.add_argument("--puerto", type=int, default=8180, help="puerto del backend (laboratorio 8180, canónico 8080)")
    ap.add_argument("--backend", default="p8lab-backend", help="contenedor del backend")
    ap.add_argument("--no-vaciar", action="store_true",
                    help="no vaciar la caché antes de medir (canónico o rama de Neon): solo se precalienta")
    a = ap.parse_args()
    global PUERTO, BASE, BACKEND
    PUERTO, BASE, BACKEND = a.puerto, f"http://127.0.0.1:{a.puerto}", a.backend
    fr01 = {f[0]: f[1] for f in json.loads((Path(a.datos) / "FR-01.json").read_text(encoding="utf-8"))}
    frases = [(i, ascii_(fr01[i])) for i in IDS]
    llm = a.situacion == "llm"

    adb("start-server")
    adb("reverse", "tcp:8080", f"tcp:{PUERTO}")
    adb("shell", "am", "force-stop", APP)
    adb("logcat", "-c")
    adb("shell", "monkey", "-p", APP, "-c", "android.intent.category.LAUNCHER", "1")
    esperar(r"silencio del STT|backend session", 0, 40)
    time.sleep(10)
    if not llm:
        if not a.no_vaciar:
            vaciar_cache()
        asyncio.run(precalentar([t for _, t in frases]))
    desde_log = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())

    filas = []
    for rep in range(1, a.reps + 1):
        for ident, texto in frases:
            r = una_corrida(texto, llm)
            if not r["completo"]:  # p. ej. la app recién arrancada no tomó el toque: un reintento
                r = {**una_corrida(texto, llm), "reintento": True}
            filas.append({"corrida": len(filas) + 1, "rep": rep, "frase": ident, "texto_enviado_a_la_app": texto, **r})
            print(len(filas), ident, r.get("ms_envio_a_primera_sena"), r.get("ms_envio_a_ultima_sena"), flush=True)

    tr = subprocess.run(["docker", "logs", BACKEND, "--since", desde_log], capture_output=True, text=True, encoding="utf-8")
    fuentes = re.findall(r"'source': '(\w+)'", tr.stdout + tr.stderr)

    def stats(f: list[dict]) -> dict:
        ok = [x for x in f if x["completo"] and x.get("ms_envio_a_primera_sena") is not None]
        return {k: resumen([x[k] for x in ok]) for k in ("ms_envio_a_primera_sena", "ms_envio_a_ultima_sena", "ms_representacion")}

    datos = {"situacion": a.situacion, "todas": stats(filas), "sin_rep1": stats([f for f in filas if f["rep"] > 1]),
             "corridas": len(filas), "incompletas": sum(not f["completo"] for f in filas),
             "fuentes_en_el_backend": {s: fuentes.count(s) for s in set(fuentes)},
             "nota": "Tiempos del teléfono (SignAllMetrics/SignAllState, logcat -v epoch, reloj del teléfono). "
                     "ultima seña = de «texto ya enviado» a «representado». Texto escrito con adb input text, en ASCII."}
    ruta = Path(a.salida)
    with open(ruta.with_suffix(".csv"), "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["corrida", "rep", "frase", "texto_enviado_a_la_app", "t_envio_epoch", "completo",
                                          "ms_envio_a_primera_sena", "ms_envio_a_ultima_sena", "ms_representacion", "n_senas"],
                           delimiter=";", extrasaction="ignore")
        w.writeheader()
        w.writerows(filas)
    ruta.with_suffix(".json").write_text(json.dumps(datos, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(datos, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    principal()
