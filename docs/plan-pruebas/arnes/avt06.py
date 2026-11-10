#!/usr/bin/env python3
"""PR-AVT-06 · Peso de las animaciones.

Lee los GLB empaquetados en la app y suma los bytes de los accessors que usan
las animaciones (entradas y salidas de cada sampler, sin contar dos veces el
mismo accessor). También mide el APK cuando se le pasa un archivo.

    python3 docs/plan-pruebas/arnes/avt06.py \
        --modelos app/src/main/assets/web/models \
        --apks app/build/outputs/apk/debug/app-debug.apk \
        --salida /tmp/opencode/p1-2026-11-10/evidencias/AVT
"""

from __future__ import annotations

import argparse
import csv
import json
import struct
from collections import Counter
from pathlib import Path

TAMANO_COMPONENTE = {5120: 1, 5121: 1, 5122: 2, 5123: 2, 5125: 4, 5126: 4}
TIPOS = {
    "SCALAR": 1,
    "VEC2": 2,
    "VEC3": 3,
    "VEC4": 4,
    "MAT2": 4,
    "MAT3": 9,
    "MAT4": 16,
}


def leer_glb(ruta: Path) -> tuple[dict, bytes]:
    datos = ruta.read_bytes()
    magia, version, _ = struct.unpack_from("<III", datos, 0)
    if magia != 0x46546C67:
        raise ValueError(f"{ruta} no es un GLB")
    desplazamiento = 12
    gltf: dict = {}
    binario = b""
    while desplazamiento < len(datos):
        largo, tipo = struct.unpack_from("<II", datos, desplazamiento)
        cuerpo = datos[desplazamiento + 8 : desplazamiento + 8 + largo]
        if tipo == 0x4E4F534A:
            gltf = json.loads(cuerpo.decode("utf-8"))
        elif tipo == 0x004E4942:
            binario = cuerpo
        desplazamiento += 8 + largo + ((4 - largo % 4) % 4 if largo % 4 else 0)
    return gltf, binario


def bytes_de_accessor(gltf: dict, indice: int) -> int:
    accessor = gltf["accessors"][indice]
    if "bufferView" in accessor:
        vista = gltf["bufferViews"][accessor["bufferView"]]
        cantidad = accessor["count"]
        elemento = TAMANO_COMPONENTE[accessor["componentType"]] * TIPOS[accessor["type"]]
        return cantidad * elemento
    return 0


def medir(ruta: Path) -> dict:
    gltf, _ = leer_glb(ruta)
    animaciones = gltf.get("animations", [])
    usados: set[int] = set()
    for animacion in animaciones:
        for sampler in animacion.get("samplers", []):
            usados.add(sampler["input"])
            usados.add(sampler["output"])
    bytes_animacion = sum(bytes_de_accessor(gltf, i) for i in usados)

    por_sena: Counter[str] = Counter()
    por_clip: list[int] = []
    for animacion in animaciones:
        indices = {s["input"] for s in animacion["samplers"]} | {s["output"] for s in animacion["samplers"]}
        peso = sum(bytes_de_accessor(gltf, i) for i in indices)
        por_clip.append(peso)
        nombre = animacion.get("name", "")
        sena = nombre.split("_")[0] if "_" in nombre else nombre
        por_sena[sena] += peso

    return {
        "archivo": ruta.name,
        "bytes_glb": ruta.stat().st_size,
        "clips": len(animaciones),
        "senas": len(por_sena),
        "bytes_animacion": bytes_animacion,
        "porcentaje": round(100 * bytes_animacion / ruta.stat().st_size, 2),
        "por_sena_promedio": round(bytes_animacion / len(por_sena)) if por_sena else 0,
        "por_clip_promedio": round(sum(por_clip) / len(por_clip), 1) if por_clip else 0,
        "por_clip_min": min(por_clip) if por_clip else 0,
        "por_clip_max": max(por_clip) if por_clip else 0,
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--modelos", required=True, type=Path)
    ap.add_argument("--apks", nargs="*", type=Path, default=[])
    ap.add_argument("--salida", required=True, type=Path)
    args = ap.parse_args()
    args.salida.mkdir(parents=True, exist_ok=True)

    filas = [medir(ruta) for ruta in sorted(args.modelos.glob("*.glb"))]
    apks = [{"archivo": a.name, "bytes": a.stat().st_size} for a in args.apks if a.exists()]

    with (args.salida / "PR-AVT-06_E_20261110_01.csv").open("w", newline="", encoding="utf-8") as f:
        campos = list(filas[0].keys()) if filas else ["archivo"]
        w = csv.DictWriter(f, fieldnames=campos, delimiter=";")
        w.writeheader()
        w.writerows(filas)
    resumen = {"modelos": filas, "apks": apks}
    (args.salida / "PR-AVT-06_E_20261110_01.json").write_text(
        json.dumps(resumen, ensure_ascii=False, indent=1), encoding="utf-8"
    )
    for fila in filas:
        print(json.dumps(fila, ensure_ascii=False))
    for apk in apks:
        print(json.dumps(apk, ensure_ascii=False))


if __name__ == "__main__":
    main()
