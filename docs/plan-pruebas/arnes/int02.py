#!/usr/bin/env python3
"""PR-INT-02 · Recorrido completo por texto.

Escribe en la app, una por una, las 30 frases de FR-01 (con `uiautomator2`, que
sí admite tildes y «¿»), toca «Traducir» y lee del `logcat` (`SignAiNet`) las
glosas que la app recibió de cada oración. Compara la lista de glosas de la
frase con la esperada de FR-01.

Hace falta: el teléfono por USB con `adb reverse tcp:8080 tcp:8080`, el backend
arriba, y la app instalada. No vacía la caché del backend: se mide con la que
haya (el script la anota al empezar y al terminar con /health).

    ~/venv/bin/python docs/plan-pruebas/arnes/int02.py \
        --serie RZCW90DTMQJ --fr01 ../signall-docs/borradores/plan-pruebas/02-datos/FR-01-frases-dominio.md \
        --salida ../data/evidencias/plan-pruebas/evidencias/INT --fecha 20261111 --ronda E
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import subprocess
import time
import urllib.request
from pathlib import Path

import uiautomator2 as u2

SEGMENTO = re.compile(r"SignAiNet: segment (\d+)/(\d+) turn=(\d+) -> (.*)$")


def adb(serie, *args) -> str:
    return subprocess.run(["adb", "-s", serie, *args], capture_output=True, text=True).stdout


def frases(ruta: Path):
    for linea in ruta.read_text(encoding="utf-8").splitlines():
        m = re.match(r"\|\s*(FR-01-\d{3})\s*\|(.*?)\|(.*?)\|(.*?)\|(.*?)\|", linea)
        if m:
            yield m.group(1), m.group(2).strip(), m.group(3).strip(), [g.strip() for g in m.group(5).split(",")]


def salud() -> dict:
    with urllib.request.urlopen("http://localhost:8080/health", timeout=5) as r:
        return json.load(r)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--serie", required=True)
    ap.add_argument("--fr01", required=True)
    ap.add_argument("--salida", required=True)
    ap.add_argument("--fecha", required=True)
    ap.add_argument("--ronda", default="E")
    ap.add_argument("--paquete", default="com.signai.webview")
    args = ap.parse_args()

    lista = list(frases(Path(args.fr01)))
    assert len(lista) == 30, len(lista)
    antes = salud()

    adb(args.serie, "shell", "am", "force-stop", args.paquete)
    adb(args.serie, "shell", "am", "start", "-n", f"{args.paquete}/.MainActivity")
    time.sleep(8)
    d = u2.connect(args.serie)

    crudo, filas = [], []
    for id_, texto, tipo, esperadas in lista:
        adb(args.serie, "logcat", "-c")
        espera_boton = time.time() + 20
        while not d(textContains="Traducir").exists and time.time() < espera_boton:
            time.sleep(0.5)
        d(className="android.widget.EditText").click() if d(className="android.widget.EditText").exists else d.click(386, 1960)
        time.sleep(0.6)
        d.send_keys(texto, clear=True)
        time.sleep(0.8)
        d(textContains="Traducir").click()
        # Se espera a que la frase termine de representarse, o 60 s.
        fin = time.time() + 60
        log = ""
        while time.time() < fin:
            time.sleep(1.5)
            log = adb(args.serie, "logcat", "-d", "-s", "SignAiNet", "SignAiState", "SignAiMetrics")
            if "representado:" in log or "No se pudo traducir" in log:
                break
        crudo.append(f"### {id_} · {texto}\n{log}")
        segs = {}
        for linea in log.splitlines():
            m = SEGMENTO.search(linea)
            if m:
                segs[int(m.group(1))] = (int(m.group(2)), m.group(4).strip())
        total = max((n for n, _ in segs.values()), default=0)
        obtenidas = []
        for i in range(total):
            if i in segs and segs[i][1]:
                obtenidas += [g.strip() for g in segs[i][1].split(",") if g.strip()]
        exacta = obtenidas == esperadas
        filas.append({"id": id_, "frase": texto, "tipo": tipo, "oraciones_recibidas": f"{len(segs)}/{total}",
                      "esperado": ",".join(esperadas), "obtenido": ",".join(obtenidas), "exacta": exacta,
                      "representada": "representado:" in log})
        print(f"{id_} {'OK ' if exacta else 'DIF'} {','.join(obtenidas)}", flush=True)
        time.sleep(1.8)  # «Traducir» queda bloqueado 1,5 s tras enviar
    despues = salud()

    pref = f"PR-INT-02_{args.ronda}_{args.fecha}_01"
    out = Path(args.salida)
    out.mkdir(parents=True, exist_ok=True)
    with (out / f"{pref}.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=list(filas[0]))
        w.writeheader()
        w.writerows(filas)
    (out / f"{pref}.txt").write_text("\n\n".join(crudo), encoding="utf-8")
    exactas = sum(f["exacta"] for f in filas)
    (out / f"{pref}.json").write_text(json.dumps({
        "exactas": exactas, "total": len(filas), "porcentaje": round(100 * exactas / len(filas), 1),
        "diferentes": [f for f in filas if not f["exacta"]],
        "salud_antes": antes, "salud_despues": despues,
        "metodo": "frases escritas en la app con uiautomator2; glosas leídas de logcat SignAiNet; la caché no se vació",
    }, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"exactas {exactas}/{len(filas)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
