#!/usr/bin/env python3
"""PR-AVT-04 · Continuidad entre señas.

Para cada paso de una seña a la siguiente en las 30 frases de FR-01 calcula la
diferencia de posición entre el **último fotograma** de la primera seña y el
**primero** de la segunda. Lo hace sobre los clips del propio GLB del avatar,
sin teléfono: la app arranca el clip siguiente sin pasar por el reposo
(`startClip` en `app.js`: fundición de 100 ms), así que lo que se ve en el salto
es justo la distancia entre esos dos fotogramas. Es la misma medida con la que
se diseñó la fundición (`openspec/changes/archive/2026-10-17-reposo-y-crossfade`
§4.1: salto directo entre pares y 10 cm como límite de «continuo»), ahora sobre
los pasos reales de las frases y no sobre todos los pares.

Posición = posición de mundo de cada hueso de la skin (FK con `tools/glb_pose.py`,
en centímetros, en las unidades del modelo, antes del 0,9378 de escala de la
app). «Salto de manos» es la mayor distancia entre las dos muñecas (`hand.L` y
`hand.R`); «salto máximo» es la mayor distancia de cualquier hueso de la skin.

    python3 docs/plan-pruebas/arnes/avt04.py \
        --glb app/src/main/assets/web/models \
        --fr01 ../sign-ai-docs/borradores/plan-pruebas/02-datos/FR-01-frases-dominio.md \
        --salida docs/plan-pruebas/evidencias/AVT --fecha 20261111
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import re
import statistics
import sys
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI.parents[2] / "tools"))
import glb_pose as gp  # noqa: E402
from seed_catalog import LSC50, canonical  # noqa: E402

GLOSA_A_ID = {canonical(g): n for n, g, _ in LSC50}
# FR-01 escribe JUCIOSO en una frase: es una errata de JUICIOSO (seña 0029).
GLOSA_A_ID["JUCIOSO"] = GLOSA_A_ID["JUICIOSO"]
LIMITE_CONTINUO_CM = 10.0  # el de la investigación de la fundición
VARIANTES = ("afirmativo", "pregunta", "negacion", "orden")


def frases_fr01(ruta: Path) -> list[dict]:
    frases = []
    for linea in ruta.read_text(encoding="utf-8").splitlines():
        m = re.match(r"\|\s*(FR-01-\d{3})\s*\|(.*)\|(.*)\|(.*)\|(.*)\|(.*)\|", linea)
        if m:
            frases.append({"id": m.group(1), "texto": m.group(2).strip(), "tipo": m.group(3).strip(),
                           "glosas": [g.strip() for g in m.group(5).split(",") if g.strip()]})
    return frases


def posicion(mundo) -> tuple[float, float, float]:
    return (mundo[0][3] * 100, mundo[1][3] * 100, mundo[2][3] * 100)  # m -> cm


def distancia(a, b) -> float:
    return math.dist(a, b)


class Avatar:
    def __init__(self, ruta: Path):
        self.js, self.bin = gp.read_glb(str(ruta))
        self.huesos = gp.skin_joints(self.js)
        self.nombres = {n.get("name", ""): i for i, n in enumerate(self.js["nodes"])}
        self.anim = {a.get("name"): i for i, a in enumerate(self.js["animations"])}
        self.cache: dict[tuple[str, str], dict] = {}

    def pose(self, clip: str, extremo: str) -> dict:
        """{hueso: (x, y, z)} en cm en el primer o el último fotograma del clip."""
        clave = (clip, extremo)
        if clave not in self.cache:
            i = self.anim[clip]
            acc = self.js["accessors"][self.js["animations"][i]["samplers"][0]["input"]]
            t = acc["min"][0] if extremo == "inicio" else acc["max"][0]
            mundo = gp.world_poses(self.js, gp.sample_clip(self.js, self.bin, i, t), self.huesos)
            self.cache[clave] = {n: posicion(v[3]) for n, v in mundo.items()}
        return self.cache[clave]

    def salto(self, clip_a: str, clip_b: str) -> dict:
        fin, ini = self.pose(clip_a, "fin"), self.pose(clip_b, "inicio")
        por_hueso = {n: distancia(fin[n], ini[n]) for n in fin}
        manos = {m: por_hueso[m] for m in ("hand.L", "hand.R")}
        peor = max(por_hueso, key=por_hueso.get)
        return {"manos_cm": max(manos.values()), "mano_izq_cm": manos["hand.L"], "mano_der_cm": manos["hand.R"],
                "maximo_cm": por_hueso[peor], "hueso_maximo": peor,
                "medio_cm": statistics.fmean(por_hueso.values())}


def percentil(valores: list[float], p: float) -> float:
    v = sorted(valores)
    k = (len(v) - 1) * p / 100
    lo, hi = math.floor(k), math.ceil(k)
    return v[lo] + (v[hi] - v[lo]) * (k - lo)


def resumen(pasos: list[dict]) -> dict:
    out = {"pasos": len(pasos)}
    for campo in ("manos_cm", "maximo_cm", "medio_cm"):
        v = [p[campo] for p in pasos]
        out[campo] = {"promedio": round(statistics.fmean(v), 2), "mediana": round(statistics.median(v), 2),
                      "p90": round(percentil(v, 90), 2), "p95": round(percentil(v, 95), 2), "maximo": round(max(v), 2)}
    manos = [p["manos_cm"] for p in pasos]
    out["sobre_limite_manos"] = sum(1 for x in manos if x > LIMITE_CONTINUO_CM)
    out["pct_sobre_limite_manos"] = round(100 * out["sobre_limite_manos"] / len(pasos), 1)
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--glb", required=True)
    ap.add_argument("--fr01", required=True)
    ap.add_argument("--salida", required=True)
    ap.add_argument("--fecha", required=True)
    args = ap.parse_args()

    frases = frases_fr01(Path(args.fr01))
    assert len(frases) == 30, f"FR-01 trae {len(frases)} frases, no 30"
    faltan = {g for f in frases for g in f["glosas"] if canonical(g) not in GLOSA_A_ID}
    assert not faltan, f"glosas sin seña: {faltan}"

    resultado = {}
    filas = []
    for avatar in ("juan", "pauline"):
        av = Avatar(Path(args.glb) / f"{avatar}.glb")
        pasos = []
        for f in frases:
            for i in range(len(f["glosas"]) - 1):
                a, b = f["glosas"][i], f["glosas"][i + 1]
                ca = f"{GLOSA_A_ID[canonical(a)]:04d}_afirmativo"
                cb = f"{GLOSA_A_ID[canonical(b)]:04d}_afirmativo"
                s = av.salto(ca, cb)
                paso = {"avatar": avatar, "frase": f["id"], "paso": i + 1, "de": a, "a": b,
                        "misma_sena": a == b, **s}
                pasos.append(paso)
                filas.append(paso)
        resultado[avatar] = resumen(pasos)
        # ¿La variante facial cambia dónde están las manos? Se compara cada clip
        # contra su versión afirmativa, en los dos extremos.
        dif = 0.0
        for n in range(50):
            for v in VARIANTES[1:]:
                for ext in ("inicio", "fin"):
                    base, otra = av.pose(f"{n:04d}_afirmativo", ext), av.pose(f"{n:04d}_{v}", ext)
                    dif = max(dif, *(distancia(base[m], otra[m]) for m in ("hand.L", "hand.R")))
        resultado[avatar]["max_dif_manos_entre_variantes_cm"] = round(dif, 4)
        top = sorted(pasos, key=lambda p: -p["manos_cm"])[:5]
        resultado[avatar]["mayores"] = [(p["frase"], p["paso"], p["de"], p["a"], round(p["manos_cm"], 1)) for p in top]

    pref = f"PR-AVT-04_D_{args.fecha}_01"
    out = Path(args.salida)
    out.mkdir(parents=True, exist_ok=True)
    campos = ["avatar", "frase", "paso", "de", "a", "misma_sena", "manos_cm", "mano_izq_cm", "mano_der_cm",
              "maximo_cm", "hueso_maximo", "medio_cm"]
    with (out / f"{pref}.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=campos)
        w.writeheader()
        for p in filas:
            w.writerow({k: (round(v, 2) if isinstance(v, float) else v) for k, v in p.items() if k in campos})
    (out / f"{pref}.json").write_text(json.dumps(
        {"limite_continuo_cm": LIMITE_CONTINUO_CM, "metodo": "último fotograma de A contra primero de B, clips afirmativo, FK sobre los huesos de la skin",
         "resultado": resultado}, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(resultado, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
