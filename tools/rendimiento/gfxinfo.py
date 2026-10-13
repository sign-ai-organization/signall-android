#!/usr/bin/env python3
# Reparte el costo de cada fotograma de la app Android nativa y mide la
# latencia real de respuesta al toque.
#
# Es la herramienta principal de `tools/rendimiento/`. Lee
# `dumpsys gfxinfo <pkg> framestats`, que para una app Compose trae los
# timestamps de CADA FASE del fotograma, no solo el total.
#
# ## Por que `gfxinfo` y no `SurfaceFlinger`
#
# En el repo hermano (Flutter) `dumpsys gfxinfo` no sirve: da todos los
# fotogramas en cero y hay que leer `dumpsys SurfaceFlinger --latency`. Aca
# Compose dibuja por el pipeline normal de Android, asi que `gfxinfo` es la
# fuente de verdad Y es mas informativa. `sf-medir.sh` queda como complemento
# porque cuenta lo que llego a pantalla, que es otra pregunta.
#
# ## Las fases
#
# Las 17 columnas del CSV de PROFILEDATA, en el orden del ciclo del fotograma:
#
#   IntendedVsync        el deadline teorico del fotograma
#   Vsync                el vsync real
#   OldestInputEvent     timestamp del evento de entrada mas viejo que afecta a este fotograma
#   NewestInputEvent     el mas nuevo
#   HandleInputStart     el hilo de UI empieza a procesar la entrada
#   AnimationStart       arranca el calculo de la animacion
#   PerformTraversalsStart   arranca measure/layout
#   DrawStart            arranca el dibujado
#   SyncQueued / SyncStart / IssueDrawCommandsStart   sincronizacion con RenderThread
#   SwapBuffers          se entrego al compositor
#   FrameCompleted       el GPU termino; de aca sale el total
#   Dequeue/QueueBufferDuration   milisegundos (no nanosegundos!) de buffer
#   GpuCompleted         timestamp de fin de GPU
#
# El reparto sale de restas entre esas marcas, y el total de
# `FrameCompleted - IntendedVsync`, que es lo que se compara contra el deadline.
#
# ## La trampa del reloj
#
# Los timestamps del framestats estan en el dominio `Uptime:` que imprime el
# propio encabezado de `gfxinfo` (SystemClock.uptimeNanos, NO cuenta deep
# sleep). NO son los de `/proc/uptime` ni los de `date`. Por eso el anclaje se
# saca del encabezado de gfxinfo, que es el mismo reloj por definicion, y no de
# un reloj del host: anclar con un reloj ajeno da numeros negativos.
#
# Y la latencia de toque se mide desde `OldestInputEvent`, que es el timestamp
# que puso el propio input de Android en el evento. Medirla desde el host
# contaminaria el numero con los cientos de ms que tarda `adb shell input`, que
# es del host y no de la app.
#
# ## Uso
#
#   gfxinfo.py <etiqueta> [--pkg PKG] [--sec N] [--jsonl RUTA] [--run CMD]
#   gfxinfo.py reproducir --sec 4 --run "adb -s 1ed6699f shell 'input tap 540 2000'"
#   gfxinfo.py scroll    --sec 4 --run "adb -s 1ed6699f shell 'input swipe 540 1900 540 700 3000'"
#
# <etiqueta>  nombre de la medicion, sale en el resumen
# --pkg        package Android (default com.signai.webview)
# --sec        ventana de medicion en segundos (default 3). El comando de
#              --run corre en paralelo, en el HOST.
# --jsonl      vuelca los fotogramas crudos en JSONL para frames.py
# --run        comando que produce el gesto, entre comillas
#
# ## La regla de oro antes de medir nada
#
# MEDÍ EN BUILD RELEASE. En debug no hay R8 ni optimizaciones y el numero no
# extrapola a lo que siente el usuario. En debug solo se miden gestos entre si,
# porque la inflamacion es comun a todos.
import argparse
import json
import os
import re
import subprocess
import sys
import time
from collections import Counter

# El presupuesto del fotograma. 60 Hz es lo que declara este panel; el valor
# real sale del primer renglon de `dumpsys SurfaceFlinger --latency`.
REFRESH_HZ = 60.0
BUDGET_MS = 1000.0 / REFRESH_HZ

# Orden del CSV de PROFILEDATA.
COLS = [
    "Flags", "IntendedVsync", "Vsync", "OldestInputEvent", "NewestInputEvent",
    "HandleInputStart", "AnimationStart", "PerformTraversalsStart",
    "DrawStart", "SyncQueued", "SyncStart", "IssueDrawCommandsStart",
    "SwapBuffers", "FrameCompleted", "DequeueBufferDuration",
    "QueueBufferDuration", "GpuCompleted",
]

# 9223372036854775807 es el centinela de "no hay evento de entrada".
SIN_INPUT = 9223372036854775807


def adb(dev, *args, binary=False):
    cmd = ["adb"] + (["-s", dev] if dev else []) + list(args)
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0 and not binary:
        sys.exit(f"adb fallo: {' '.join(cmd)}\n{r.stderr.strip()}")
    return r.stdout


def uptime_ns(dev, pkg):
    """El `Uptime:` del encabezado de gfxinfo, en ns.

    Es el mismo reloj que usan los timestamps del framestats, asi que es el
    unico reloj del que se puede honestamente colgar el anclaje.
    """
    out = adb(dev, "shell", "dumpsys", "gfxinfo", pkg)
    m = re.search(r"^Uptime:\s*(\d+)", out, re.M)
    return int(m.group(1)) * 1_000_000 if m else None


def parse_frames(text):
    """Saca las filas del CSV de PROFILEDATA."""
    m = re.search(r"---PROFILEDATA---(.*?)---PROFILEDATA---", text, re.S)
    if not m:
        return []
    body = m.group(1)
    filas = []
    for linea in body.splitlines():
        linea = linea.strip()
        if not linea or linea.startswith("Flags,"):
            continue
        p = linea.split(",")
        if len(p) < len(COLS):
            continue
        try:
            filas.append({c: int(v) for c, v in zip(COLS, p)})
        except ValueError:
            # Un"Some trailing column" o una fila de stats viejos.
            continue
    return filas


def fases(f):
    """Reparte el fotograma en fases, en ms. None donde la marca no existe."""
    ms = 1e-6

    def d(a, b):
        va, vb = f.get(a), f.get(b)
        if va is None or vb is None or va == 0 or vb == 0 or vb < va:
            return None
        return (vb - va) * ms

    entrada = None
    if f.get("OldestInputEvent") not in (None, 0, SIN_INPUT):
        entrada = f["OldestInputEvent"]

    fases_ = {
        # Lo que el input espero antes de que el hilo de UI lo tocara.
        "entrada->UI": (f["HandleInputStart"] - entrada) * ms
        if entrada and f.get("HandleInputStart", 0) >= entrada else None,
        "animacion": d("HandleInputStart", "AnimationStart"),
        "measure+layout": d("AnimationStart", "PerformTraversalsStart"),
        "draw (hilo UI)": d("PerformTraversalsStart", "DrawStart"),
        "sync+issue draw": d("DrawStart", "IssueDrawCommandsStart"),
        "GPU": d("IssueDrawCommandsStart", "GpuCompleted"),
        "swap+present": d("SwapBuffers", "FrameCompleted"),
    }
    fases_["TOTAL"] = d("IntendedVsync", "FrameCompleted")
    # El deadline era IntendedVsync + presupuesto. Lo que se pasa es jank.
    deadline = f.get("IntendedVsync", 0) + int(BUDGET_MS * 1e6)
    fases_["_deadline"] = deadline
    fases_["_entrada"] = entrada
    return fases_


def pct(xs, p):
    if not xs:
        return None
    xs = sorted(xs)
    i = min(len(xs) - 1, int(len(xs) * p))
    return xs[i]


def bucket(d):
    if d < BUDGET_MS:          return "a tiempo (<%.2f ms)" % BUDGET_MS
    if d < BUDGET_MS * 2:      return "1 frame tarde"
    if d < BUDGET_MS * 4:      return "2-3 frames tarde"
    if d < BUDGET_MS * 8:      return "4-7 frames tarde"
    return "8+ frames tarde"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("etiqueta")
    ap.add_argument("--dev", default=os.environ.get("DEV", ""))
    ap.add_argument("--pkg", default=os.environ.get("APP_PKG", "com.signai.webview"))
    ap.add_argument("--sec", type=float, default=3.0)
    ap.add_argument("--jsonl")
    ap.add_argument("--run", default=None)
    a = ap.parse_args()

    # Uptime de gfxinfo en ns, del mismo reloj que el framestats.
    t0 = uptime_ns(a.dev, a.pkg)

    # `dumpsys gfxinfo reset` pone el contador en cero: sin esto se mide todo lo
    # que hizo la app desde que arranco, no el gesto.
    adb(a.dev, "shell", "dumpsys", "gfxinfo", a.pkg, "reset")

    if a.run:
        # El gesto corre en el HOST, no en el dispositivo: `adb` es una
        # herramienta del host y no existe en el celu.
        proc = subprocess.Popen(a.run, shell=True,
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    else:
        proc = None

    time.sleep(a.sec)
    if proc:
        proc.wait()

    texto = adb(a.dev, "shell", "dumpsys", "gfxinfo", a.pkg, "framestats")
    filas = parse_frames(texto)

    if not filas:
        print(f"### {a.etiqueta}: sin fotogramas")
        print("    La app no dibujo nada en la ventana, o no esta en foreground.")
        print("    Checkear: tools/dev.sh fg")
        raise SystemExit(1)

    # Latencia de toque: el primer fotograma con un OldestInputEvent, medido
    # desde la marca que puso el propio input de Android. No desde el host.
    lat = None
    for f in filas:
        oe = f.get("OldestInputEvent", 0)
        if oe not in (0, SIN_INPUT) and f.get("HandleInputStart", 0) >= oe:
            lat = ((f["HandleInputStart"] - oe) * 1e-6,
                   ((f["IntendedVsync"] - oe) * 1e-6) if f["IntendedVsync"] >= oe else None)
            break

    filas.sort(key=lambda f: f["IntendedVsync"])
    datos = []
    for f in filas:
        z = fases(f)
        total = z["TOTAL"]
        if total is None or total <= 0:
            continue
        datos.append({
            "intended": f["IntendedVsync"],
            "frame_completed": f["FrameCompleted"],
            "total_ms": total,
            "jank": f["FrameCompleted"] > z["_deadline"],
            "phases": {k: v for k, v in z.items() if not k.startswith("_")},
            "deadline_ns": z["_deadline"],
            "oldest_input": z["_entrada"],
        })

    if a.jsonl:
        with open(a.jsonl, "w") as fh:
            for d in datos:
                fh.write(json.dumps(d) + "\n")

    totales = [d["total_ms"] for d in datos]
    n = len(totales)
    jank = sum(1 for d in datos if d["jank"])
    print(f"### {a.etiqueta}   (paquete {a.pkg})")
    print(f"    fotogramas en la ventana: {n}   presupuesto: {BUDGET_MS:.2f} ms "
          f"({REFRESH_HZ:.0f} Hz)")
    print(f"    total por fotograma:  mediana {pct(totales,.5):.1f} ms   "
          f"p90 {pct(totales,.9):.1f} ms   p99 {pct(totales,.99):.1f} ms   "
          f"max {max(totales):.1f} ms")
    print(f"    jank (el deadline se paso): {jank}/{n}  ({100*jank/n:.1f} %)")
    print()

    # OJO con esto: la espera de la entrada ocurre ANTES de IntendedVsync, asi
    # que no aparece en TOTAL y sin embargo se come presupuesto y hace que el
    # fotograma llegue tarde. Por eso se reporta por separado: si el jank viene
    # de aca, el problema es de entrada (o de lo que tape el hilo de UI), no de
    # dibujado.
    ent_jank = [d["phases"]["entrada->UI"] for d in datos
                if d["jank"] and d["phases"].get("entrada->UI") is not None]
    ent_ok = [d["phases"]["entrada->UI"] for d in datos
              if not d["jank"] and d["phases"].get("entrada->UI") is not None]
    if ent_jank:
        print("    espera de entrada (fuera de TOTAL, consume presupuesto):")
        print(f"      en los fotogramas janky:    mediana {pct(ent_jank,.5):6.1f} ms"
              f"   p90 {pct(ent_jank,.9):6.1f} ms   (n={len(ent_jank)})")
        if ent_ok:
            print(f"      en los que no eran janky: mediana {pct(ent_ok,.5):6.1f} ms"
                  f"   (n={len(ent_ok)})")
        print()

    print("    reparto por fase (mediana de los fotogramas que no son jank):")
    limpios = [d for d in datos if not d["jank"]] or datos
    for nombre in ["entrada->UI", "animacion", "measure+layout", "draw (hilo UI)",
                   "sync+issue draw", "GPU", "swap+present", "TOTAL"]:
        vals = [d["phases"].get(nombre) for d in limpios]
        vals = [v for v in vals if v is not None]
        if not vals:
            continue
        print(f"      {nombre:<18} {pct(vals,.5):6.2f} ms   p90 {pct(vals,.9):6.2f} ms")
    print()

    print("    como se repartieron los fotogramas:")
    bc = Counter(bucket(d["total_ms"]) for d in datos)
    for b in ["a tiempo (<%.2f ms)" % BUDGET_MS, "1 frame tarde", "2-3 frames tarde",
              "4-7 frames tarde", "8+ frames tarde"]:
        c = bc.get(b, 0)
        if c:
            print(f"      {c:4d}  ({100*c/n:5.1f} %)  {b}")
    print()

    if lat:
        ent_ui, ent_frame = lat
        print("    latencia de toque:")
        print(f"      evento de entrada -> hilo de UI:  {ent_ui:.1f} ms")
        if ent_frame is not None:
            print(f"      evento de entrada -> vsync objetivo: {ent_frame:.1f} ms")
    else:
        print("    latencia de toque: sin dato (el gesto no genero eventos de "
              "entrada en la ventana; el comando de --run no toco la pantalla)")

    if a.jsonl:
        print(f"\n    crudo: {a.jsonl}")


if __name__ == "__main__":
    main()