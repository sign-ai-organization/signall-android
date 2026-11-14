#!/usr/bin/env python3
"""PR-REN-07 · Rendimiento en el móvil (una corrida).

Mismo método de la diagnóstica del 10/11 (RESULTADOS-P3-2026-11-10.md §7.1):
teléfono descargándose y sin cable (adb inalámbrico), pantalla encendida con el
brillo que ya tenía, app limpiada (`pm clear`), 30 s para asentarse, 60 s con la
app quieta (gfxinfo) y después las 30 frases de FR-01 seguidas por texto.

Fuentes: `dumpsys gfxinfo` reiniciado al empezar la secuencia, `dumpsys meminfo`
cada 30 s, el contador de carga de `dumpsys battery`, y los `signStart` y
`representado` de `SignAllState` / `SignAllMetrics`.

    ~/venv/bin/python docs/plan-pruebas/arnes/ren07.py --serie 192.168.0.14:5555 \
        --fr01 ../signall-docs/borradores/plan-pruebas/02-datos/FR-01-frases-dominio.md \
        --salida ../signall-docs/evidencias/plan-pruebas/evidencias/REN --fecha 20261111 --base 1
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import statistics
import subprocess
import threading
import time
from pathlib import Path

import uiautomator2 as u2

PKG = "com.signall.app"
HORA = re.compile(r"^(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)\.(\d{3})")


def adb(serie, *a) -> str:
    return subprocess.run(["adb", "-s", serie, *a], capture_output=True, text=True).stdout


def bateria(serie) -> dict:
    t = adb(serie, "shell", "dumpsys", "battery")
    g = lambda k: int(re.search(rf"^\s*{k}: (-?\d+)", t, re.M).group(1))
    return {"t": time.time(), "carga_uah": g("Charge counter"), "temp_c": g("temperature") / 10,
            "usb": "USB powered: true" in t, "ac": "AC powered: true" in t, "nivel": g("level")}


def pss_mb(serie) -> float | None:
    m = re.search(r"TOTAL PSS:\s+(\d+)", adb(serie, "shell", "dumpsys", "meminfo", PKG))
    return int(m.group(1)) / 1024 if m else None


def gfx(txt: str) -> dict:
    g = lambda p: re.search(p, txt)
    total = int(g(r"Total frames rendered: (\d+)").group(1))
    jank = g(r"Janky frames: (\d+) \(([\d.]+)%\)")
    out = {"cuadros": total, "janky": int(jank.group(1)), "janky_pct": float(jank.group(2))}
    for p in (50, 90, 95, 99):
        m = g(rf"{p}th percentile: (\d+)ms")
        out[f"p{p}_ms"] = int(m.group(1)) if m else None
    h = g(r"HISTOGRAM: (.*)")
    if h:
        pares = [(int(a), int(b)) for a, b in re.findall(r"(\d+)ms=(\d+)", h.group(1))]
        n = sum(c for _, c in pares)
        out["medio_ms"] = round(sum(ms * c for ms, c in pares) / n, 1) if n else None
        out["peor_ms"] = max((ms for ms, c in pares if c), default=None)
    return out


def percentil(v, p):
    v = sorted(v)
    k = (len(v) - 1) * p / 100
    lo, hi = int(k), min(int(k) + 1, len(v) - 1)
    return v[lo] + (v[hi] - v[lo]) * (k - lo)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--serie", required=True)
    ap.add_argument("--fr01", required=True)
    ap.add_argument("--salida", required=True)
    ap.add_argument("--fecha", required=True)
    ap.add_argument("--base", type=int, required=True, help="1, 2 o 3: la corrida (usa los consecutivos 01-09, 11-19, 21-29)")
    args = ap.parse_args()
    S, out = args.serie, Path(args.salida)
    out.mkdir(parents=True, exist_ok=True)
    nn = lambda k: f"PR-REN-07_E_{args.fecha}_{(args.base - 1) * 10 + k:02d}"

    frases = []
    for l in Path(args.fr01).read_text(encoding="utf-8").splitlines():
        m = re.match(r"\|\s*(FR-01-\d{3})\s*\|(.*?)\|", l)
        if m:
            frases.append((m.group(1), m.group(2).strip()))
    assert len(frases) == 30

    b0 = bateria(S)
    assert not b0["usb"] and not b0["ac"], "el teléfono está cargando: desconecta el cable"
    log = [f"inicio {time.strftime('%F %T')} serie {S} batería {b0}"]

    adb(S, "shell", "pm", "clear", PKG)
    adb(S, "shell", "pm", "grant", "--user", "0", PKG, "android.permission.RECORD_AUDIO")
    adb(S, "reverse", "tcp:8080", "tcp:8080")
    adb(S, "shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(30)
    d = u2.connect(S)

    # 60 s con la app quieta
    adb(S, "shell", "dumpsys", "gfxinfo", PKG, "reset")
    time.sleep(60)
    reposo = gfx(adb(S, "shell", "dumpsys", "gfxinfo", PKG))
    (out / f"{nn(8)}_gfxinfo_reposo_60s.txt").write_text(adb(S, "shell", "dumpsys", "gfxinfo", PKG), encoding="utf-8")

    # secuencia
    adb(S, "logcat", "-c")
    lg = open(out / f"{nn(5)}_logcat.txt", "w", encoding="utf-8")
    proc = subprocess.Popen(["adb", "-s", S, "logcat", "-v", "threadtime", "-s", "SignAllState", "SignAllMetrics", "SignAllNet"],
                            stdout=lg, stderr=subprocess.DEVNULL)
    mem = []
    parar = threading.Event()

    def muestrear():
        while not parar.is_set():
            mem.append((time.time(), pss_mb(S)))
            parar.wait(30)

    hilo = threading.Thread(target=muestrear, daemon=True)
    adb(S, "shell", "dumpsys", "gfxinfo", PKG, "reset")
    b1 = bateria(S)
    hilo.start()
    t_seq = time.time()
    envios = []
    for i, (id_, texto) in enumerate(frases, 1):
        d(className="android.widget.EditText").click()
        time.sleep(0.4)
        d.send_keys(texto, clear=True)
        time.sleep(0.4)
        t_env = time.time()
        d(textContains="Traducir").click()
        fin = t_env + 60
        while time.time() < fin:
            time.sleep(1.5)
            lg.flush()
            if open(out / f"{nn(5)}_logcat.txt", encoding="utf-8").read().count("representado:") >= i:
                break
        envios.append((id_, texto, t_env, time.time()))
        print(f"corrida {args.base} frase {i}/30 {time.time() - t_seq:.0f} s", flush=True)
        time.sleep(1.8)
    t_fin = time.time()
    b2 = bateria(S)
    parar.set()
    g = adb(S, "shell", "dumpsys", "gfxinfo", PKG)
    (out / f"{nn(3)}_gfxinfo.txt").write_text(g, encoding="utf-8")
    proc.terminate()
    lg.close()

    # --- análisis del logcat ---
    lineas = (out / f"{nn(5)}_logcat.txt").read_text(encoding="utf-8").splitlines()
    anio = time.localtime().tm_year

    def epoch(l):
        m = HORA.match(l)
        if not m:
            return None
        mo, dd, hh, mi, ss, ms = map(int, m.groups())
        return time.mktime((anio, mo, dd, hh, mi, ss, 0, 0, -1)) + ms / 1000

    inicios = [e[2] for e in envios] + [t_fin + 999]
    transiciones, represent = [], []
    for k in range(len(envios)):
        js = []
        for l in lineas:
            t = epoch(l)
            if t is None or not (inicios[k] - 0.5 <= t < inicios[k + 1] - 0.5):
                continue
            m = re.search(r"signStart: .*reloj JS (\d+) ms", l)
            if m:
                js.append(int(m.group(1)))
            m = re.search(r"representado: \d+ señas en (\d+)ms", l)
            if m:
                represent.append(int(m.group(1)))
        transiciones += [b - a for a, b in zip(js, js[1:])]
    envio_a_fin = [round(e[3] - e[2], 1) for e in envios]

    with (out / f"{nn(2)}_frases.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(["id", "frase", "envio_a_representado_s"])
        for (id_, texto, _, _), s in zip(envios, envio_a_fin):
            w.writerow([id_, texto, s])
    with (out / f"{nn(4)}_memoria.txt").open("w", encoding="utf-8") as fh:
        for t, v in mem:
            fh.write(f"{time.strftime('%T', time.localtime(t))} PSS_MB {v if v is None else round(v, 1)}\n")
    with (out / f"{nn(6)}_bateria.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(["momento", "hora", "carga_uah", "temp_c", "nivel", "usb", "ac"])
        for nombre, b in (("antes_de_la_app", b0), ("inicio_secuencia", b1), ("fin_secuencia", b2)):
            w.writerow([nombre, time.strftime("%T", time.localtime(b["t"])), b["carga_uah"], b["temp_c"], b["nivel"], b["usb"], b["ac"]])

    seg = t_fin - t_seq
    mah = (b1["carga_uah"] - b2["carga_uah"]) / 1000
    pss = [v for _, v in mem if v]
    resumen = {
        "corrida": args.base, "serie": S, "duracion_s": round(seg), "temperatura_c": [b1["temp_c"], b2["temp_c"]],
        "nivel": [b1["nivel"], b2["nivel"]], "descargando": not (b1["usb"] or b2["usb"] or b1["ac"] or b2["ac"]),
        "cuadros": gfx(g), "reposo_60s": reposo,
        "transicion_ms": {"n": len(transiciones), "p50": round(statistics.median(transiciones)), "p95": round(percentil(transiciones, 95)),
                          "min": min(transiciones), "max": max(transiciones)} if transiciones else None,
        "representacion_ms": {"n": len(represent), "p50": round(statistics.median(represent)), "p95": round(percentil(represent, 95)),
                              "max": max(represent)} if represent else None,
        "envio_a_representado_s": {"p50": round(statistics.median(envio_a_fin), 1), "p95": round(percentil(envio_a_fin, 95), 1)},
        "pss_mb": {"min": round(min(pss)), "max": round(max(pss)), "n": len(pss)} if pss else None,
        "bateria": {"mah": round(mah, 1), "ma": round(mah * 3600 / seg), "nota": "el contador avanza de a 5 mAh"},
    }
    (out / f"{nn(9)}.json").write_text(json.dumps(resumen, indent=2, ensure_ascii=False), encoding="utf-8")
    (out / f"{nn(1)}.txt").write_text("\n".join(log) + "\n" + json.dumps(resumen, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(resumen, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
