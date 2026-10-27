#!/usr/bin/env python3
"""Empaqueta los dos avatares completos (Juan y Pauline) para la app.

Los exports finales de Cascadeur viven versionados en el repo hermano
`../signai-avatar/models/` (`Juan.glb` y `Pauline.glb`, rama `dev` desde el
commit `cf014ef`, 2026-10-26). Cada archivo trae el personaje, su esqueleto de
102 articulaciones y **300 clips**: las 50 señas de LSC50 (`0000`..`0049`) por
seis variantes —la base, `_alegria`, `_tristeza`, `_sorpresa`, `_pregunta` y
`_negacion`—. Los originales no se editan: son la entrada inmutable.

`../material-3d/Avatares/` conserva la versión anterior (`Juan_final.glb` y
`Pauline_final.glb`, 2026-10-24). Sigue sirviendo para comparar, pero **ya no es
la fuente**: la versión del 2026-10-26 corrigió la cadera (el avatar se iba de
costado al representar) y volvió a aplicar la corrección de cabeza.

Este script hace el empaquetado que la app necesita, sin cambiar ninguna
animación:

  1. **Verifica** que cada archivo sea el que esperamos: un skin, 102
     articulaciones y exactamente los 300 nombres de clip esperados.
  2. **Hornea la pose de descanso** con la misma regla de `rest_pose.py` (los 46
     huesos de la cadena de brazos, desde el primer keyframe de `0029`). Los dos
     archivos nuevos vienen con la pose de maniquí del export: sin esto, al
     terminar una frase los brazos saltarían a esa pose en vez de volver al
     reposo que la app ya tiene.
  3. **Poda los morph targets que nadie anima** y compacta el buffer, reusando
     las funciones de `pack_signall.py`. Los que alguna de las 300 variantes
     mueve se conservan: la cara no se toca en esta etapa, pero el asset queda
     listo para cuando el owner apruebe los marcadores.
  4. **Escribe** `models/juan.glb`, `models/pauline.glb`, el inventario
     `models/avatars.json` y `manifest.json`.

El archivo de salida conserva las 300 animaciones. La app usa hoy solo los
clips base: el resolutor de `app.js` no reconoce los sufijos faciales a
propósito mientras los marcadores estén fuera de alcance.

Uso:

    python3 tools/pack_avatars.py                 # los dos, desde ../signai-avatar/models
    python3 tools/pack_avatars.py --dry-run       # informe, sin escribir
    python3 tools/pack_avatars.py --source-dir OTRA/CARPETA

Los dos `.glb` de origen se versionan en el repo hermano `../signai-avatar`
(rama `dev`, carpeta `models/`): son la entrega final del pipeline de Cascadeur
y solo se reemplazan cuando el owner aprueba una versión nueva. Sus hashes,
tamaños y conteos quedan en `models/avatars.json`, que es lo que permite
reproducir el empaquetado desde otro clon.
"""
import argparse
import copy
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import glb_pose as gp  # noqa: E402
import pack_signall as ps  # noqa: E402
import rest_pose  # noqa: E402
from split_signs import write_catalog  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
DEST_DEFAULT = REPO / "app/src/main/assets/web"
# Los exports finales se versionan en el repo hermano `../signai-avatar` (rama
# `dev`, carpeta `models/`). No se editan: son la entrada inmutable del
# empaquetado. `../material-3d/Avatares/` conserva la versión anterior del
# 2026-10-24, que ya no es la fuente.
SOURCE_DEFAULT = REPO.parent / "signai-avatar" / "models"

# El orden define el orden del inventario; el `id` es el que viaja a la escena.
AVATARS = (
    ("juan", "Juan", "Juan.glb"),
    ("pauline", "Pauline", "Pauline.glb"),
)

SIGN_IDS = [f"{i:04d}" for i in range(50)]
SUFFIXES = ("alegria", "tristeza", "sorpresa", "pregunta", "negacion")
EXPECTED_CLIP_COUNT = len(SIGN_IDS) * (1 + len(SUFFIXES))
EXPECTED_JOINTS = 102
FPS = 24


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def check_source(js, name):
    """Falla ruidosamente si el archivo no es uno de los dos avatares completos."""
    if len(js.get("skins", [])) != 1:
        raise SystemExit(f"{name}: se esperaba 1 skin, hay {len(js.get('skins', []))}")
    joints = len(js["skins"][0].get("joints", []))
    if joints != EXPECTED_JOINTS:
        raise SystemExit(f"{name}: el skin tiene {joints} articulaciones, se esperaban "
                         f"{EXPECTED_JOINTS}")

    names = [a.get("name", "") for a in js.get("animations", [])]
    if len(names) != len(set(names)):
        dup = sorted({n for n in names if names.count(n) > 1})
        raise SystemExit(f"{name}: clips con nombre repetido: {dup}")
    if len(names) != EXPECTED_CLIP_COUNT:
        raise SystemExit(f"{name}: hay {len(names)} clips, se esperaban "
                         f"{EXPECTED_CLIP_COUNT} (50 señas × 6 variantes)")

    expected = set()
    for sign in SIGN_IDS:
        expected.add(sign)
        for suffix in SUFFIXES:
            expected.add(f"{sign}_{suffix}")
    missing = sorted(expected - set(names))
    extra = sorted(set(names) - expected)
    if missing or extra:
        raise SystemExit(
            f"{name}: los clips no son las 50 señas × 6 variantes:\n"
            f"  faltan: {missing[:20]}{'…' if len(missing) > 20 else ''}\n"
            f"  sobran: {extra[:20]}{'…' if len(extra) > 20 else ''}")

    nodes = len(js.get("nodes", []))
    for anim in js["animations"]:
        for channel in anim["channels"]:
            if channel["target"]["node"] >= nodes:
                raise SystemExit(f"{name}: el clip {anim.get('name')} apunta a un nodo "
                                 f"inexistente ({channel['target']['node']})")
    return names


def bake_rest_pose(js, binary):
    """Hornea la pose de descanso desde el clip base `0029` del propio archivo."""
    names = rest_pose.check_chain(rest_pose.arm_chain(js))
    index = next(i for i, a in enumerate(js["animations"])
                 if a["name"] == rest_pose.REFERENCE_ID)
    t = gp.first_keyframe_time(js, index)
    pose = gp.sample_clip(js, binary, index, t)
    before = rest_pose.hand_positions(js)
    touched, worst = rest_pose.bake(js, pose, names)
    after = rest_pose.hand_positions(js)
    rest_pose.compact(js, names)
    return {"t": t, "before": before, "after": after,
            "bones": len(touched), "delta": worst}


def bind_report(js, binary, name):
    """Comprueba la pose de bind sin frenar el empaquetado.

    `rest_pose.check_bind` es la guarda de `pack_signall.py`: si un nodo que no
    es de la cadena de brazos no está en su pose de bind, hornear la pose de
    descanso podría deformar la malla. Acá solo se tocan las rotaciones de los
    46 huesos de brazos, así que una diferencia fuera de esa cadena no cambia lo
    que este script hace; se informa para que el owner la revise en el export.
    """
    try:
        rest_pose.check_bind(js, binary)
        return None
    except SystemExit as exc:
        print(f"  ATENCIÓN  {name}: {exc}")
        return str(exc)


def base_clips(js):
    """Los 50 clips base, en orden de id, con sus cinco variantes."""
    by_name = {a.get("name"): a for a in js["animations"]}
    return [(sign, by_name[sign],
             [by_name[f"{sign}_{suffix}"] for suffix in SUFFIXES])
            for sign in SIGN_IDS]


def build_manifest(path, js, avatar_sha):
    """El inventario de las 50 señas, con la forma que espera `seed_catalog.py`.

    `bytes` y `sha256` son los del archivo del avatar por defecto (Juan): con
    dos avatares no existe un hash único por seña. El inventario completo de
    los dos archivos está en `avatars.json`.
    """
    entries = []
    for sign, base, variants in base_clips(js):
        tracks = {channel["target"]["path"] for channel in base["channels"]}
        entries.append({
            "sign_id": sign,
            "gloss": None,
            "path": f"signs/{sign}_0000_0000.glb",
            "clip": sign,
            "bundled": "avatars",
            "variants": 1 + len(variants),
            "variant_names": [sign, *(f"{sign}_{s}" for s in SUFFIXES)],
            "fps": FPS,
            "frames": max(js["accessors"][s["input"]]["count"]
                          for s in base["samplers"]),
            "duration_s": round(
                gp.clip_duration(js, js["animations"].index(base)), 3),
            "tracks": len(base["channels"]),
            "faces": "weights" in tracks,
            "bytes": path.stat().st_size,
            "sha256": avatar_sha,
        })
    return entries


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--source-dir", type=Path, default=SOURCE_DEFAULT,
                        help="carpeta con Juan.glb y Pauline.glb "
                             "(por defecto ../signai-avatar/models)")
    parser.add_argument("--dest", type=Path, default=DEST_DEFAULT,
                        help="assets/web (donde viven models/ y manifest.json)")
    parser.add_argument("--dry-run", action="store_true",
                        help="informa sin escribir nada")
    args = parser.parse_args()

    dest = args.dest
    models = dest / "models"
    models.mkdir(parents=True, exist_ok=True)

    inventory = []
    juan = None
    for avatar_id, label, filename in AVATARS:
        source = args.source_dir / filename
        if not source.is_file():
            raise SystemExit(f"falta {source}")
        source_bytes = source.stat().st_size
        source_sha = sha256(source)
        print(f"\n{label}")
        print(f"  origen    {source} ({source_bytes / 1e6:.2f} MB)")
        print(f"  sha256    {source_sha}")

        js, binary = gp.read_glb(source)
        check_source(js, label)
        warning = bind_report(js, binary, label)
        report = bake_rest_pose(js, binary)
        for line in rest_pose.report_lines(
            report, indent="  ", reference="el clip 0029 del mismo archivo",
        ):
            print(line)

        keep = ps.morphs_to_keep(js, binary)
        before_morphs = sum(
            len(m["primitives"][0].get("targets", []))
            for m in js["meshes"])
        js, binary = ps.repack(js, binary, keep)
        after_morphs = sum(
            len(m["primitives"][0].get("targets", []))
            for m in js["meshes"])
        print(f"  morphs    {before_morphs} -> {after_morphs} "
              f"(se conservan los que alguna variante anima)")

        out = models / f"{avatar_id}.glb"
        if args.dry_run:
            print(f"  --dry-run: {out.name} no se escribió "
                  f"({len(binary) / 1e6:.2f} MB de binario en memoria)")
        else:
            size = gp.write_glb(out, js, binary)
            print(f"  escrito   {out.relative_to(REPO) if out.is_relative_to(REPO) else out}"
                  f" ({size / 1e6:.2f} MB, "
                  f"{(size - source_bytes) / 1e6:+.2f} MB contra el origen)")
            if avatar_id == "juan":
                juan = (out, js)
            inventory.append({
                "id": avatar_id,
                "label": label,
                "path": f"models/{out.name}",
                "bytes": size,
                "sha256": sha256(out),
                "source": filename,
                "source_bytes": source_bytes,
                "source_sha256": source_sha,
                "clips": len(js["animations"]),
                "signs": len(SIGN_IDS),
                "variants": ["base", *SUFFIXES],
                "joints": len(js["skins"][0]["joints"]),
                "rest_pose": {
                    "reference": f"{rest_pose.REFERENCE_ID} (keyframe 0)",
                    "bones": report["bones"],
                    "delta": report["delta"],
                },
                "bind_warning": warning,
            })

    if args.dry_run:
        print("\n--dry-run: no se escribió nada")
        return

    inventory_path = models / "avatars.json"
    inventory_path.write_text(
        json.dumps(inventory, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"\n  inventario {inventory_path.relative_to(REPO)}")

    if juan is None:
        raise SystemExit("no se generó el avatar por defecto (Juan)")
    manifest = build_manifest(*juan, inventory[0]["sha256"])
    (dest / "manifest.json").write_text(
        json.dumps(manifest, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"  manifest   signos: {len(manifest)} (bytes y sha256 de Juan)")
    count = write_catalog(
        manifest,
        packed=True,
        generator="tools/pack_avatars.py",
        avatar_models=("`assets/web/models/juan.glb` or "
                       "`assets/web/models/pauline.glb`"),
    )
    print(f"  catálogo   SignsCatalog.kt ({count} señas, clips dentro del avatar)")


if __name__ == "__main__":
    main()
