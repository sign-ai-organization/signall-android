#!/usr/bin/env python3
"""Empaqueta los dos avatares completos (Juan y Pauline) para la app.

Los exports finales de Cascadeur viven versionados en el repo hermano
`../signall-avatar/models/` (`Juan.glb` y `Pauline.glb`, rama `dev`, commit
`fc6f372`, 2026-11-04). Cada archivo trae el personaje, su esqueleto de 102
articulaciones y **200 clips**: las 50 señas de LSC50 (`0000`..`0049`) por
cuatro marcas faciales —`_afirmativo`, `_pregunta`, `_negacion` y `_orden`—.
No hay clip base: las cuatro variantes comparten los mismos canales de hueso y
solo cambian los pesos de la cara. Los originales no se editan: son la entrada
inmutable.

`../material-3d/Avatares/` conserva la versión anterior (`Juan_final.glb` y
`Pauline_final.glb`, 2026-10-24). Sigue sirviendo para comparar, pero **ya no es
la fuente**: la versión del 2026-10-26 corrigió la cadera (el avatar se iba de
costado al representar) y volvió a aplicar la corrección de cabeza; la del
2026-11-04 reemplazó las cinco caras emocionales por las cuatro marcas
gramaticales.

Este script hace el empaquetado que la app necesita, con las curvas de entrada,
regreso y correcciones localizadas aprobadas el 2026-11-11:

  1. **Verifica** que cada archivo sea el que esperamos: un skin, 102
     articulaciones y exactamente los 200 nombres de clip esperados.
  2. **Hornea la pose de descanso** con la misma regla de `rest_pose.py` (los 46
     huesos de la cadena de brazos, desde el primer keyframe de `0029`). Como ya
     no hay clip base, se usa la variante `0029_afirmativo`: las cuatro
     variantes comparten los huesos, así que la pose de brazos es la misma. Los
     dos archivos nuevos vienen con la pose de maniquí del export: sin esto, al
     terminar una frase los brazos saltarían a esa pose en vez de volver al
     reposo que la app ya tiene.
  3. **Poda los morph targets que nadie anima** y compacta el buffer, reusando
     las funciones de `pack_signall.py`. Los que alguna de las 300 variantes
     mueve se conservan: la cara no se toca en esta etapa, pero el asset queda
     listo para cuando el owner apruebe los marcadores.
  4. **Aplica curvas aprobadas** de `approved_tracks.json.gz`, verificando el
     hash del empaquetado base. Incluye las cuatro preparaciones añadidas y las
     selecciones completas de `0020` v3 y `0028`; no depende de copias temporales.
  5. **Escribe** `models/juan.glb`, `models/pauline.glb`, el inventario
     `models/avatars.json` y `manifest.json`.

El archivo de salida conserva las 200 animaciones. La app elige una de las
cuatro por tipo de oración desde el 2026-11-04; no existe una toma neutra.

Uso:

    python3 tools/pack_avatars.py                 # los dos, desde ../signall-avatar/models
    python3 tools/pack_avatars.py --dry-run       # informe, sin escribir
    python3 tools/pack_avatars.py --source-dir OTRA/CARPETA

Los dos `.glb` de origen se versionan en el repo hermano `../signall-avatar`
(rama `dev`, carpeta `models/`): son la entrega final del pipeline de Cascadeur
y solo se reemplazan cuando el owner aprueba una versión nueva. Sus hashes,
tamaños y conteos quedan en `models/avatars.json`, que es lo que permite
reproducir el empaquetado desde otro clon.
"""
import argparse
import copy
import hashlib
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import glb_pose as gp  # noqa: E402
import approved_tracks  # noqa: E402
import pack_signall as ps  # noqa: E402
import rest_pose  # noqa: E402
from split_signs import write_catalog  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
DEST_DEFAULT = REPO / "app/src/main/assets/web"
# Los exports finales se versionan en el repo hermano `../signall-avatar` (rama
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
# Las cuatro marcas faciales del export del 2026-11-04. No hay clip base.
SUFFIXES = ("afirmativo", "pregunta", "negacion", "orden")
DEFAULT_SUFFIX = "afirmativo"
EXPECTED_CLIP_COUNT = len(SIGN_IDS) * len(SUFFIXES)
EXPECTED_JOINTS = 102
FPS = 24

# Morph targets que ningún clip anima pero que la capa de marcadores no manuales
# (`assets/web/humanize.js`) mueve en tiempo de ejecución con los datos de
# `nmm.json`: la boca del intérprete. Sin ellos la mandíbula no puede abrirse ni
# los labios cerrarse en «o», porque la poda los saca por no tener keyframes.
RUNTIME_MORPHS = (
    "jawOpen", "mouthClose", "mouthFunnel", "mouthPucker",
    "mouthStretch_L", "mouthStretch_R", "mouthLowerDown_L", "mouthLowerDown_R",
    "mouthUpperUp_L", "mouthUpperUp_R", "cheekPuff",
)


def morphs_with_runtime(js, binary):
    """La poda de siempre más los `RUNTIME_MORPHS`, por nombre y por malla."""
    keep = ps.morphs_to_keep(js, binary)
    for mesh_index, mesh in enumerate(js["meshes"]):
        names = mesh.get("extras", {}).get("targetNames", [])
        wanted = {i for i, name in enumerate(names) if name in RUNTIME_MORPHS}
        if wanted and mesh_index in keep:
            keep[mesh_index] |= wanted
    return keep


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
                         f"{EXPECTED_CLIP_COUNT} (50 señas × 4 variantes)")

    expected = set()
    for sign in SIGN_IDS:
        for suffix in SUFFIXES:
            expected.add(f"{sign}_{suffix}")
    missing = sorted(expected - set(names))
    extra = sorted(set(names) - expected)
    if missing or extra:
        raise SystemExit(
            f"{name}: los clips no son las 50 señas × 4 variantes:\n"
            f"  faltan: {missing[:20]}{'…' if len(missing) > 20 else ''}\n"
            f"  sobran: {extra[:20]}{'…' if len(extra) > 20 else ''}")

    nodes = len(js.get("nodes", []))
    for anim in js["animations"]:
        for channel in anim["channels"]:
            if channel["target"]["node"] >= nodes:
                raise SystemExit(f"{name}: el clip {anim.get('name')} apunta a un nodo "
                                 f"inexistente ({channel['target']['node']})")
    return names


def apply_approved_rest_pose(js, avatar_id, source_sha):
    """Aplica solo el reposo aprobado; nunca modifica curvas ni el binario.

    Las rotaciones son específicas de estos exports: una nueva fuente requiere
    revisar la pose, no reutilizarla silenciosamente sobre otro esqueleto.
    """
    config = json.loads((Path(__file__).with_name("approved_rest_pose.json")).read_text())
    approved = config["avatars"][avatar_id]
    if source_sha != approved["source_sha256"]:
        raise SystemExit(f"{avatar_id}: cambió el GLB de origen; revisar el reposo aprobado")
    rotations = approved["rotations"]
    expected = {f"{bone}.{side}" for bone in ("upper_arm", "forearm", "hand")
                for side in ("L", "R")}
    if set(rotations) != expected:
        raise SystemExit(f"{avatar_id}: el reposo debe contener exactamente seis huesos")
    indices = {node.get("name"): i for i, node in enumerate(js["nodes"])}
    for name, rotation in rotations.items():
        if (len(rotation) != 4 or not all(math.isfinite(v) for v in rotation)
                or abs(sum(v * v for v in rotation) - 1) > 1e-5):
            raise SystemExit(f"{avatar_id}: rotación inválida para {name}")
    required = {indices[name] for name in rotations}
    for animation in js["animations"]:
        animated = {c["target"]["node"] for c in animation["channels"]
                    if c["target"]["path"] == "rotation"}
        if not required <= animated:
            raise SystemExit(f"{avatar_id}: {animation['name']} depende de rotaciones de reposo")
    for name, rotation in rotations.items():
        js["nodes"][indices[name]]["rotation"] = rotation[:]


def bake_rest_pose(js, binary, avatar_id, source_sha):
    """Hornea la pose de descanso desde `0029_afirmativo` del propio archivo.

    Ya no hay clip `0029` sin sufijo. Las cuatro variantes de una seña tienen
    los mismos canales de hueso (solo cambian los pesos de la cara), así que
    tomar la variante `afirmativo` da exactamente la misma pose de brazos.
    Sobre esa base se aplican las seis rotaciones aprobadas el 2026-11-11.
    """
    names = rest_pose.check_chain(rest_pose.arm_chain(js))
    reference = f"{rest_pose.REFERENCE_ID}_{DEFAULT_SUFFIX}"
    index = next(i for i, a in enumerate(js["animations"])
                 if a["name"] == reference)
    t = gp.first_keyframe_time(js, index)
    pose = gp.sample_clip(js, binary, index, t)
    before = rest_pose.hand_positions(js)
    old_rotations = {n["name"]: n.get("rotation", gp.IDENTITY_Q)[:]
                     for n in js["nodes"] if n.get("name") in names}
    touched, worst = rest_pose.bake(js, pose, names)
    apply_approved_rest_pose(js, avatar_id, source_sha)
    worst = max(abs(a - b) for n in js["nodes"] if n.get("name") in names
                for a, b in zip(old_rotations[n["name"]], n["rotation"]))
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


def sign_clips(js):
    """Las 50 señas, en orden de id, con la variante por defecto y las cuatro.

    No hay clip base: la variante `afirmativo` es la representante para los
    datos que se toman una sola vez por seña (duración, frames, tracks).
    """
    by_name = {a.get("name"): a for a in js["animations"]}
    return [(sign, by_name[f"{sign}_{DEFAULT_SUFFIX}"],
             [by_name[f"{sign}_{suffix}"] for suffix in SUFFIXES])
            for sign in SIGN_IDS]


def build_manifest(path, js, avatar_sha):
    """El inventario de las 50 señas, con la forma que espera `seed_catalog.py`.

    `bytes` y `sha256` son los del archivo del avatar por defecto (Juan): con
    dos avatares no existe un hash único por seña. El inventario completo de
    los dos archivos está en `avatars.json`.
    """
    entries = []
    for sign, representative, variants in sign_clips(js):
        tracks = {channel["target"]["path"]
                  for channel in representative["channels"]}
        entries.append({
            "sign_id": sign,
            "gloss": None,
            "path": f"signs/{sign}_0000_0000.glb",
            "clip": f"{sign}_{DEFAULT_SUFFIX}",
            "bundled": "avatars",
            "variants": len(variants),
            "variant_names": [f"{sign}_{s}" for s in SUFFIXES],
            "fps": FPS,
            "frames": max(js["accessors"][s["input"]]["count"]
                          for s in representative["samplers"]),
            "duration_s": round(
                gp.clip_duration(js, js["animations"].index(representative)), 3),
            "tracks": len(representative["channels"]),
            "faces": "weights" in tracks,
            "bytes": path.stat().st_size,
            "sha256": avatar_sha,
        })
    return entries


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--source-dir", type=Path, default=SOURCE_DEFAULT,
                        help="carpeta con Juan.glb y Pauline.glb "
                             "(por defecto ../signall-avatar/models)")
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
        report = bake_rest_pose(js, binary, avatar_id, source_sha)
        for line in rest_pose.report_lines(
            report, indent="  ",
            reference=f"el clip 0029_{DEFAULT_SUFFIX} del mismo archivo",
        ):
            print(line)
        print("  reposo    ajuste aprobado 2026-11-11: brazos a los lados y manos intermedias")

        before_morphs = sum(
            len(m["primitives"][0].get("targets", []))
            for m in js["meshes"])
        # Las curvas aprobadas se firmaron contra el empaquetado **sin** los
        # morphs de tiempo de ejecución. Ese empaquetado se rearma acá solo para
        # comprobar su hash; el que se escribe lleva además esos morphs, y cada
        # curva sigue verificando el hash de la curva que reemplaza.
        check_js, check_binary = ps.repack(js, binary, ps.morphs_to_keep(js, binary))
        approved_tracks.apply(check_js, check_binary, avatar_id)
        del check_js, check_binary

        base_keep = ps.morphs_to_keep(js, binary)
        full_keep = morphs_with_runtime(js, binary)
        # Por malla: en qué columna del canal de pesos nuevo quedó cada morph
        # del empaquetado firmado, y cuántas columnas tiene ahora.
        layouts = {
            mesh: ([sorted(full_keep[mesh]).index(i) for i in sorted(base_keep[mesh])],
                   len(full_keep[mesh]))
            for mesh in full_keep if full_keep[mesh] != base_keep[mesh]
        }
        js, binary = ps.repack(js, binary, full_keep)
        after_morphs = sum(
            len(m["primitives"][0].get("targets", []))
            for m in js["meshes"])
        print(f"  morphs    {before_morphs} -> {after_morphs} "
              f"(los que alguna variante anima, más {len(RUNTIME_MORPHS)} de la boca "
              f"para los marcadores no manuales)")

        binary, transitions = approved_tracks.apply(js, binary, avatar_id,
                                                    check_baseline=False,
                                                    weight_layouts=layouts)
        # Las curvas vivas pasan a views propios y deduplicados; después el
        # compactado puede descartar los accessors viejos sin bytes muertos.
        binary, curves = approved_tracks.rewrite_animation_accessors(js, binary)
        js, binary = ps.repack(js, binary, morphs_with_runtime(js, binary))
        print(f"  curvas    200 clips aprobados, {transitions['samplers']} samplers "
              f"ajustados, {curves} curvas vivas; {len(binary) / 1e6:.2f} MB de binario")

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
                "variants": list(SUFFIXES),
                "default_variant": DEFAULT_SUFFIX,
                "joints": len(js["skins"][0]["joints"]),
                "rest_pose": {
                    "reference": (f"{rest_pose.REFERENCE_ID}_{DEFAULT_SUFFIX} "
                                  "(keyframe 0)"),
                    "bones": report["bones"],
                    "delta": report["delta"],
                    "adjustment": "approved_rest_pose.json (2026-11-11)",
                    "adjusted_bones": 6,
                },
                "bind_warning": warning,
                "approved_transitions": transitions,
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
