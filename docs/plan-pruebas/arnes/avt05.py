#!/usr/bin/env python3
"""PR-AVT-05 · Marcadores no manuales.

Manda por la app las 10 frases de FR-01-A (dos por tipo de frase), lee el panel
de diagnóstico del avatar (`tipo` y `cara`) mientras se representan, graba la
pantalla y saca una captura con la cara a mitad de la primera seña.

Hace falta una compilación con `-Psignall.devTools=true` (es la que trae el panel)
y `-Psignall.backendWsUrl=ws://127.0.0.1:8080/ws/translate`. El tipo esperado es
el de la última oración de la frase, como dice FR-01.

    ~/venv/bin/python docs/plan-pruebas/arnes/avt05.py --serie RZCW90DTMQJ \
        --fr01 ../signall-docs/borradores/plan-pruebas/02-datos/FR-01-frases-dominio.md \
        --salida ../signall-docs/evidencias/plan-pruebas/evidencias/AVT --fecha 20261111 --ronda E
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import subprocess
import time
from pathlib import Path

import uiautomator2 as u2

FR01_A = ["FR-01-001", "FR-01-005", "FR-01-007", "FR-01-011", "FR-01-013",
          "FR-01-014", "FR-01-019", "FR-01-023", "FR-01-025", "FR-01-026"]
TIPO_ESPERADO = {"Afirmación": "AFFIRMATIVE", "Pregunta de sí o no": "YES_NO_QUESTION",
                 "Pregunta de contenido": "CONTENT_QUESTION", "Negación": "NEGATIVE", "Orden": "IMPERATIVE"}
CARA_ESPERADA = {"AFFIRMATIVE": "afirmativo", "YES_NO_QUESTION": "pregunta", "CONTENT_QUESTION": "pregunta",
                 "NEGATIVE": "negacion", "IMPERATIVE": "orden"}
LINEA = re.compile(r"cola:\s*(\d+)/(\d+)\s+tipo:\s*(\S+)\s+cara:\s*(\S+)")


def adb(serie, *a, **kw):
    return subprocess.run(["adb", "-s", serie, *a], capture_output=True, **kw)


def frases(ruta: Path):
    for linea in ruta.read_text(encoding="utf-8").splitlines():
        m = re.match(r"\|\s*(FR-01-\d{3})\s*\|(.*?)\|(.*?)\|", linea)
        if m and m.group(1) in FR01_A:
            yield m.group(1), m.group(2).strip(), m.group(3).strip()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--serie", required=True)
    ap.add_argument("--fr01", required=True)
    ap.add_argument("--salida", required=True)
    ap.add_argument("--fecha", required=True)
    ap.add_argument("--ronda", default="E")
    ap.add_argument("--paquete", default="com.signall.app")
    args = ap.parse_args()
    out = Path(args.salida)
    out.mkdir(parents=True, exist_ok=True)

    adb(args.serie, "shell", "am", "force-stop", args.paquete)
    adb(args.serie, "shell", "am", "start", "-n", f"{args.paquete}/.MainActivity")
    time.sleep(10)
    d = u2.connect(args.serie)

    def panel() -> str:
        e = d(textContains="cola:")
        return e.info.get("text", "") if e.exists else ""

    filas = []
    for n, (id_, texto, tipo) in enumerate(frases(Path(args.fr01)), 1):
        esp_tipo = TIPO_ESPERADO[tipo]
        base = f"PR-AVT-05_{args.ronda}_{args.fecha}"
        remoto = f"/sdcard/{base}_{n:02d}.mp4"
        grab = subprocess.Popen(["adb", "-s", args.serie, "shell", "screenrecord", "--time-limit", "25",
                                 "--size", "540x1170", "--bit-rate", "3000000", remoto])
        time.sleep(1.5)
        d(className="android.widget.EditText").click()
        time.sleep(0.6)
        d.send_keys(texto, clear=True)
        time.sleep(0.6)
        d(textContains="Traducir").click()
        vistos, captura, fin = [], False, time.time() + 22
        while time.time() < fin:
            t = panel()
            m = LINEA.search(t)
            if m and m.group(3) != "-":
                par = (m.group(3), m.group(4))
                if not vistos or vistos[-1] != par:
                    vistos.append(par)
                if not captura and "play: true" in t:
                    adb(args.serie, "shell", "screencap", "-p", f"/sdcard/{base}_{n:02d}.png")
                    captura = True
            if vistos and "play: false" in t and m and m.group(1) == m.group(2):
                break
            time.sleep(0.2)
        time.sleep(1.5)
        grab.terminate()
        grab.wait()
        time.sleep(1)
        adb(args.serie, "pull", remoto, str(out / f"{base}_{n:02d}.mp4"))
        if captura:
            adb(args.serie, "pull", f"/sdcard/{base}_{n:02d}.png", str(out / f"{base}_{n:02d}.png"))
        adb(args.serie, "shell", "rm", remoto, f"/sdcard/{base}_{n:02d}.png")
        ult_tipo, ult_cara = vistos[-1] if vistos else ("-", "-")
        ok = ult_tipo == esp_tipo and ult_cara == CARA_ESPERADA[esp_tipo]
        filas.append({"n": n, "id": id_, "frase": texto, "tipo_esperado": tipo, "tipo_codigo_esperado": esp_tipo,
                      "cara_esperada": CARA_ESPERADA[esp_tipo], "secuencia_tipo_cara": " | ".join(f"{a}/{b}" for a, b in vistos),
                      "tipo_final": ult_tipo, "cara_final": ult_cara, "coincide": ok,
                      "grabacion": f"{base}_{n:02d}.mp4"})
        print(id_, "OK " if ok else "NO ", tipo, "->", filas[-1]["secuencia_tipo_cara"], flush=True)
        time.sleep(2)

    pref = f"PR-AVT-05_{args.ronda}_{args.fecha}_tabla"
    with (out / f"{pref}.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=list(filas[0]))
        w.writeheader()
        w.writerows(filas)
    por_tipo = {}
    for f in filas:
        por_tipo.setdefault(f["tipo_esperado"], []).append(f["coincide"])
    (out / f"{pref}.json").write_text(json.dumps({"coinciden": sum(f["coincide"] for f in filas), "total": len(filas),
                                                  "por_tipo": {k: f"{sum(v)}/{len(v)}" for k, v in por_tipo.items()},
                                                  "filas": filas}, indent=2, ensure_ascii=False), encoding="utf-8")
    print({k: f"{sum(v)}/{len(v)}" for k, v in por_tipo.items()})
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
