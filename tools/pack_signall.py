#!/usr/bin/env python3
"""Empaqueta las 50 señas de LSC50 en UN solo `models/avatar.glb`.

El modelo de assets anterior era uno por seña: cada export de Cascadeur trae el
personaje entero adentro (10.3 MB), así que `tools/split_signs.py` los partía en
`models/avatar.glb` (el muñeco) + `signs/<id>.glb` (solo la animación). Con 50
señas eso son 50 archivos que hay que versionar y, en el device, un `.glb` que
bajar antes de que el muñeco empiece a moverse.

Este script hace el camino corto: un archivo con el personaje, su cara y las 50
señas como **clips de animación** con el id de la seña (`0000`..`0049`). La
escena indexa los clips al cargar el avatar y resuelve `signs/<id>.glb` por
nombre sin pedir nada (ver `embeddedClipsFor` en `app.js`), así que la primera
seña de una frase no espera ninguna descarga.

Qué hace, en orden:

  1. **Verifica** que el archivo sea el que esperamos: un solo skin y exactamente
     los clips `0000`..`0049`, sin nombres repetidos. Un clip que falte o que
     sobre se avisa acá y no en la cara del usuario.
  2. **Hornea la pose de descanso** con la misma regla que `rest_pose.py` (los 46
     huesos de la cadena de brazos, desde el primer keyframe de `0029`), pero
     leyendo el frame de referencia del propio archivo: con todo adentro ya no
     hace falta `signs/0029_0000_0000.glb`. Sin esto el muñeco se queda en la
     pose de maniquí del export y vuelve a ella al terminar cada frase.
  3. **Poda los morph targets que nadie anima.** El export trae 68 blendshapes de
     cara en el cuerpo; las animaciones solo mueven un puñado. Los que quedan en
     0 en todos los keyframes (y en el peso por defecto) son 15 MB de deltas que
     `three.js` igual sube a la GPU. Se quedan los animados y los que ya pesan
     algo por defecto, que son los que deforman la malla.
  4. **Compacta** el buffer: se copian solo los `bufferView` que quedan en uso,
     alineados a 4 bytes, y se reescriben los accessors.
  5. **Escribe** `models/avatar.glb`, `manifest.json` y `domain/SignsCatalog.kt`
     (el mismo generador que `split_signs.py`, para que la lista y el archivo no
     se puedan desincronizar).

Uso:

    python3 tools/pack_signall.py                  # "GLB SignALL/" -> assets
    python3 tools/pack_signall.py --dry-run        # informe, sin escribir
    python3 tools/pack_signall.py OTRO.glb DESTINO

`avatar.glb` **no se edita a mano**: se regenera con este script.
"""
import argparse
import copy
import hashlib
import json
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import glb_pose as gp  # noqa: E402
import rest_pose  # noqa: E402
from split_signs import write_catalog  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
SRC_DEFAULT = REPO / "GLB SignALL"
DEST_DEFAULT = REPO / "app/src/main/assets/web"

# Los ids de las 50 glosas de LSC50, en orden. Un clip con otro nombre no se
# reproduce nunca —la escena busca `signs/<id>_....glb`— así que es un error y
# no un aviso.
SIGNS = [f"{i:04d}" for i in range(50)]

COMPONENTS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}
COMPONENT_BYTES = {5120: 1, 5121: 1, 5122: 2, 5123: 2, 5125: 4, 5126: 4}
COMPONENT_FMT = {5120: "b", 5121: "B", 5122: "h", 5123: "H", 5125: "I", 5126: "f"}

# Peso por debajo del cual un morph target se considera "no animado". Los
# exports traen ruido de float en los pesos de los shapes que no se usan.
WEIGHT_EPSILON = 1e-4


# ── Lectura de accessors ──────────────────────────────────────────────────────

def accessor_values(js, binary, index):
    """Los valores de un accessor como lista plana, con sparse ya aplicado."""
    acc = js["accessors"][index]
    size = COMPONENTS[acc["type"]]
    fmt = COMPONENT_FMT[acc["componentType"]]
    total = acc["count"] * size

    values = [0] * total
    if "bufferView" in acc:
        view = js["bufferViews"][acc["bufferView"]]
        base = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
        stride = view.get("byteStride") or size * COMPONENT_BYTES[acc["componentType"]]
        if stride == size * COMPONENT_BYTES[acc["componentType"]]:
            values = list(struct.unpack_from("<" + fmt * total, binary, base))
        else:
            for i in range(acc["count"]):
                row = base + i * stride
                values[i * size:(i + 1) * size] = struct.unpack_from("<" + fmt * size, binary, row)
    sparse = acc.get("sparse")
    if sparse:
        idx_acc = sparse["indices"]
        idx_view = js["bufferViews"][idx_acc["bufferView"]]
        idx_base = idx_view.get("byteOffset", 0) + idx_acc.get("byteOffset", 0)
        idx_fmt = COMPONENT_FMT[idx_acc["componentType"]]
        indices = struct.unpack_from("<" + idx_fmt * sparse["count"], binary, idx_base)
        val_acc = sparse["values"]
        val_view = js["bufferViews"][val_acc["bufferView"]]
        val_base = val_view.get("byteOffset", 0) + val_acc.get("byteOffset", 0)
        vals = struct.unpack_from("<" + fmt * sparse["count"] * size, binary, val_base)
        for n, target in enumerate(indices):
            values[target * size:(target + 1) * size] = vals[n * size:(n + 1) * size]
    return values


# ── Validación ────────────────────────────────────────────────────────────────

def check_source(js):
    """Falla ruidosamente si el archivo no es el avatar con las 50 señas."""
    if len(js.get("skins", [])) != 1:
        raise SystemExit(f"se esperaba 1 skin, hay {len(js.get('skins', []))}")
    names = [a.get("name", "") for a in js.get("animations", [])]
    if len(names) != len(set(names)):
        dup = sorted({n for n in names if names.count(n) > 1})
        raise SystemExit(f"clips con nombre repetido: {dup}")
    missing = [s for s in SIGNS if s not in names]
    extra = [n for n in names if n not in SIGNS]
    if missing or extra:
        raise SystemExit(
            "los clips no son las 50 señas 0000..0049:\n"
            f"  faltan: {missing or 'ninguno'}\n"
            f"  sobran: {extra or 'ninguno'}")
    return names


# ── Pose de descanso ──────────────────────────────────────────────────────────

def bake_rest_pose(js, binary):
    """Hornea la pose de descanso usando el clip `0029` del propio archivo.

    Misma regla que `rest_pose.py` —los 46 huesos de la cadena de brazos, el
    primer keyframe— pero la referencia está adentro, no en `signs/`.
    """
    names = rest_pose.check_chain(rest_pose.arm_chain(js))
    index = next(i for i, a in enumerate(js["animations"]) if a["name"] == rest_pose.REFERENCE_ID)
    t = gp.first_keyframe_time(js, index)
    pose = gp.sample_clip(js, binary, index, t)
    before = rest_pose.hand_positions(js)
    touched, worst = rest_pose.bake(js, pose, names)
    after = rest_pose.hand_positions(js)
    rest_pose.compact(js, names)
    return {"t": t, "before": before, "after": after,
            "bones": len(touched), "delta": worst}


# ── Morph targets ─────────────────────────────────────────────────────────────

def animated_morphs(js, binary):
    """`{mesh: {índice de morph}}` con los que algún keyframe mueve de verdad."""
    used = {}
    for mesh_index, mesh in enumerate(js["meshes"]):
        count = len(mesh["primitives"][0].get("targets", []))
        if count:
            used[mesh_index] = set()
    for anim in js.get("animations", []):
        for channel in anim["channels"]:
            if channel["target"].get("path") != "weights":
                continue
            mesh_index = js["nodes"][channel["target"]["node"]]["mesh"]
            count = len(js["meshes"][mesh_index]["primitives"][0]["targets"])
            values = accessor_values(js, binary, anim["samplers"][channel["sampler"]]["output"])
            for i, value in enumerate(values):
                if abs(value) > WEIGHT_EPSILON:
                    used[mesh_index].add(i % count)
    return used


def morphs_to_keep(js, binary):
    """Los morph targets que hay que conservar: animados o con peso por defecto.

    Un shape con peso por defecto distinto de 0 ya está deformando la malla sin
    que ningún keyframe lo toque: sacarlo cambia la forma del personaje.
    """
    keep = animated_morphs(js, binary)
    for mesh_index, weights in enumerate(
            m.get("weights") for m in js["meshes"]):
        if not weights:
            continue
        keep[mesh_index] |= {i for i, w in enumerate(weights) if abs(w) > 1e-6}
    return keep


# ── Compactado ────────────────────────────────────────────────────────────────

class Compactor:
    """Copia a un buffer nuevo solo lo que queda referenciado.

    El buffer del export tiene 57 `bufferView` para ~30 000 accessors, así que un
    accessor no es un bloque: la geometría está **interleaved** (un view mezcla
    POSITION, NORMAL, TEXCOORD, COLOR, JOINTS y WEIGHTS de los mismos vértices) y
    los morph targets comparten un view gigante de datos sparse.

    Por eso el compactado trabaja por view:

      * cada view que queda en uso se copia **una sola vez**, y solo el tramo
        que los accessors vivos necesitan (del menor `byteOffset` al mayor);
      * cada accessor conserva su `byteOffset` relativo al tramo copiado, así que
        el interleaving sigue siendo válido sin desarmarlo;
      * los views que solo alimentaban morph targets podados no se copian.

    Copiar por accessor —lo primero que uno escribe— parece lo mismo y no lo es:
    duplica el bloque interleaved una vez por atributo (medido: 19 MB de
    geometría donde hay 3.5 MB).
    """

    def __init__(self, source_js, source_binary, needed_accessors, needed_views):
        self.src = source_js
        self.src_binary = source_binary
        self.blob = bytearray()
        self.new_views = []
        self.accessors = []
        self.copied = {}
        self.ranges = {}
        for index in needed_accessors:
            self._register(index)
        for index in needed_views:
            # Las texturas no cuelgan de un accessor: se copia el view entero.
            self._register_range(index, 0, self.src["bufferViews"][index]["byteLength"])
        self.copied_views = {}
        self._copy_views()

    # -- planificación --------------------------------------------------------

    def _spans(self, index):
        """[(view, offset_dentro_del_view, largo)] de todo lo que usa el accessor."""
        acc = self.src["accessors"][index]
        out = []
        if "bufferView" in acc:
            view = self.src["bufferViews"][acc["bufferView"]]
            size = COMPONENTS[acc["type"]] * COMPONENT_BYTES[acc["componentType"]]
            stride = view.get("byteStride") or size
            length = (acc["count"] - 1) * stride + size if acc["count"] else size
            out.append((acc["bufferView"], acc.get("byteOffset", 0), length))
        sparse = acc.get("sparse")
        if sparse:
            for part in ("indices", "values"):
                node = sparse[part]
                # `values` no declara componentType: hereda el del accessor.
                component = node.get("componentType", acc["componentType"])
                size = COMPONENT_BYTES[component] * (
                    1 if part == "indices" else COMPONENTS[acc["type"]])
                out.append((node["bufferView"], node.get("byteOffset", 0),
                            sparse["count"] * size))
        return out

    def _register(self, index):
        for view, offset, length in self._spans(index):
            self._register_range(view, offset, length)

    def _register_range(self, view, offset, length):
        low, high = self.ranges.get(view, (offset, offset + length))
        self.ranges[view] = (min(low, offset), max(high, offset + length))

    def _copy_views(self):
        for view_index in sorted(self.ranges):
            low, high = self.ranges[view_index]
            view = self.src["bufferViews"][view_index]
            base = view.get("byteOffset", 0)
            while len(self.blob) % 4:
                self.blob.append(0)
            new_view = {"buffer": 0, "byteOffset": len(self.blob),
                        "byteLength": high - low}
            if "byteStride" in view:
                new_view["byteStride"] = view["byteStride"]
            if "target" in view:
                new_view["target"] = view["target"]
            self.blob += self.src_binary[base + low:base + high]
            self.new_views.append(new_view)
            self.copied_views[view_index] = (len(self.new_views) - 1, low)

    # -- escritura ------------------------------------------------------------

    def _view_ref(self, view_index, offset):
        new_index, low = self.copied_views[view_index]
        return new_index, offset - low

    def accessor(self, index):
        """El accessor [index] en el archivo nuevo."""
        if index in self.copied:
            return self.copied[index]
        src = self.src["accessors"][index]
        acc = {"componentType": src["componentType"], "count": src["count"],
               "type": src["type"]}
        for key in ("min", "max", "normalized", "name"):
            if key in src:
                acc[key] = src[key]
        if "bufferView" in src:
            acc["bufferView"], offset = self._view_ref(
                src["bufferView"], src.get("byteOffset", 0))
            if offset:
                acc["byteOffset"] = offset
        if "sparse" in src:
            sparse = src["sparse"]
            new_sparse = {"count": sparse["count"]}
            for part in ("indices", "values"):
                node = sparse[part]
                view, offset = self._view_ref(node["bufferView"],
                                              node.get("byteOffset", 0))
                entry = {"bufferView": view}
                if offset:
                    entry["byteOffset"] = offset
                if "componentType" in node:
                    entry["componentType"] = node["componentType"]
                new_sparse[part] = entry
            acc["sparse"] = new_sparse
        self.accessors.append(acc)
        self.copied[index] = len(self.accessors) - 1
        return self.copied[index]

    def floats(self, values, count, source_accessor):
        """Un accessor float denso con [values], para los pesos filtrados."""
        while len(self.blob) % 4:
            self.blob.append(0)
        offset = len(self.blob)
        self.blob += struct.pack("<" + "f" * len(values), *values)
        self.new_views.append({"buffer": 0, "byteOffset": offset,
                               "byteLength": len(values) * 4})
        acc = {
            "bufferView": len(self.new_views) - 1,
            "componentType": source_accessor["componentType"],
            "count": count,
            "type": source_accessor["type"],
        }
        self.accessors.append(acc)
        return len(self.accessors) - 1

    def finish(self, js, keep_bounds):
        """Cierra el buffer y limpia lo que glTF no exige.

        `min`/`max` solo son obligatorios en los accessors de `POSITION` (three.js
        avisa si faltan y no puede calcular el bounding box de la malla); en los
        de animación son 8 números por sampler y son ~3 MB de JSON. Se conservan
        también en los SCALAR, que es de donde `glb_pose.clip_duration` saca la
        duración de un clip.
        """
        while len(self.blob) % 4:
            self.blob.append(0)
        for index, acc in enumerate(self.accessors):
            if index in keep_bounds or acc["type"] == "SCALAR":
                continue
            acc.pop("min", None)
            acc.pop("max", None)
        js["bufferViews"] = self.new_views
        js["accessors"] = self.accessors
        js["buffers"] = [{"byteLength": len(self.blob)}]
        return bytes(self.blob)


def needed_accessors(js, keep):
    """Los accessors que siguen en uso después de podar los morph targets."""
    needed = set()
    for mesh_index, mesh in enumerate(js["meshes"]):
        order = keep.get(mesh_index, [])
        for prim in mesh["primitives"]:
            needed.update(prim["attributes"].values())
            if "indices" in prim:
                needed.add(prim["indices"])
            for i in order:
                needed.update(prim["targets"][i].values())
    for skin in js["skins"]:
        if "inverseBindMatrices" in skin:
            needed.add(skin["inverseBindMatrices"])
    for anim in js["animations"]:
        for channel in anim["channels"]:
            sampler = anim["samplers"][channel["sampler"]]
            needed.add(sampler["input"])
            if channel["target"].get("path") == "weights":
                mesh_index = js["nodes"][channel["target"]["node"]]["mesh"]
                if not keep.get(mesh_index):
                    continue
            needed.add(sampler["output"])
    return needed


def repack(js, binary, keep):
    """Aplica la poda de morph targets y compacta el buffer. Devuelve (js, bin)."""
    source = copy.deepcopy(js)
    out = copy.deepcopy(js)
    out.pop("accessors", None)
    out.pop("bufferViews", None)
    comp = Compactor(source, binary, needed_accessors(source, keep),
                     {image["bufferView"] for image in source.get("images", [])})

    # 1. morph targets podados (los nombres y los pesos por defecto, con ellos)
    remap = {}
    for mesh_index, order in keep.items():
        order = sorted(order)
        remap[mesh_index] = order
        for prim_index, prim in enumerate(source["meshes"][mesh_index]["primitives"]):
            targets = prim.get("targets", [])
            if not targets:
                continue
            out["meshes"][mesh_index]["primitives"][prim_index]["targets"] = [
                {attr: comp.accessor(index) for attr, index in targets[i].items()}
                for i in order
            ]
        mesh_out = out["meshes"][mesh_index]
        if "extras" in mesh_out and "targetNames" in mesh_out["extras"]:
            names = mesh_out["extras"]["targetNames"]
            mesh_out["extras"]["targetNames"] = [names[i] for i in order]
        if "weights" in mesh_out:
            mesh_out["weights"] = [mesh_out["weights"][i] for i in order]
    for node in out["nodes"]:
        if "weights" in node and node.get("mesh") in remap:
            node["weights"] = [node["weights"][i] for i in remap[node["mesh"]]]

    # 2. geometría y skin
    for mesh_index, mesh in enumerate(source["meshes"]):
        for prim_index, prim in enumerate(mesh["primitives"]):
            for attr, index in prim["attributes"].items():
                out["meshes"][mesh_index]["primitives"][prim_index]["attributes"][attr] = \
                    comp.accessor(index)
            if "indices" in prim:
                out["meshes"][mesh_index]["primitives"][prim_index]["indices"] = \
                    comp.accessor(prim["indices"])
    for skin in out["skins"]:
        if "inverseBindMatrices" in skin:
            skin["inverseBindMatrices"] = comp.accessor(skin["inverseBindMatrices"])

    # 3. animaciones: los pesos se recortan y un mesh sin morphs pierde el canal
    for anim_index, anim in enumerate(source["animations"]):
        channels, samplers = [], []
        for channel in anim["channels"]:
            sampler = anim["samplers"][channel["sampler"]]
            target = {"input": comp.accessor(sampler["input"])}
            if channel["target"].get("path") == "weights":
                mesh_index = source["nodes"][channel["target"]["node"]]["mesh"]
                order = remap.get(mesh_index, [])
                if not order:
                    continue  # sin morphs no hay nada que animar
                total = len(source["meshes"][mesh_index]["primitives"][0]["targets"])
                values = accessor_values(source, binary, sampler["output"])
                filtered = []
                for frame in range(len(values) // total):
                    base = frame * total
                    filtered.extend(values[base + i] for i in order)
                target["output"] = comp.floats(
                    filtered, len(filtered), source["accessors"][sampler["output"]])
            else:
                target["output"] = comp.accessor(sampler["output"])
            if "interpolation" in sampler:
                target["interpolation"] = sampler["interpolation"]
            channels.append({**channel, "sampler": len(samplers)})
            samplers.append(target)
        out["animations"][anim_index]["channels"] = channels
        out["animations"][anim_index]["samplers"] = samplers

    # min/max que hay que conservar: los POSITION de cada primitiva y los de sus
    # morph targets (los llama igual, y three.js avisa si faltan).
    bounds = set()
    for mesh in out["meshes"]:
        for prim in mesh["primitives"]:
            bounds.add(prim["attributes"]["POSITION"])
            for target in prim.get("targets", []):
                if "POSITION" in target:
                    bounds.add(target["POSITION"])
    return out, comp.finish(out, bounds)


# ── Manifest e informe ────────────────────────────────────────────────────────

def build_manifest(path, js, fps=24):
    """El inventario de las 50 señas, con la forma que espera `seed_catalog.py`.

    `bytes` y `sha256` son los del **archivo entero**, no los de un clip: con las
    señas empaquetadas no existe un asset por seña, y el catálogo de Neon los
    guarda igual porque lo que le importa es qué versión del personaje se usó.
    `frames` es la cantidad de keyframes del clip (no los fps × duración: los
    clips son sparse, se exportan con los keyframes que hacen falta).
    """
    binary = path.read_bytes()
    sha = hashlib.sha256(binary).hexdigest()
    entries = []
    for anim in js["animations"]:
        tracks = {channel["target"]["path"] for channel in anim["channels"]}
        entries.append({
            "sign_id": anim["name"],
            "gloss": None,  # la glosa la pone el catálogo del backend, no el asset
            "path": f"signs/{anim['name']}_0000_0000.glb",
            "clip": anim["name"],
            "bundled": "avatar",
            "variants": 1,
            "variant_names": [anim["name"]],
            "fps": fps,
            "frames": max(js["accessors"][s["input"]]["count"] for s in anim["samplers"]),
            "duration_s": round(gp.clip_duration(js, js["animations"].index(anim)), 3),
            "tracks": len(anim["channels"]),
            "faces": "weights" in tracks,
            "bytes": len(binary),
            "sha256": sha,
        })
    return entries


def size_report(js, binary):
    """Cuánto pesa cada cosa, para poder discutir el archivo con números."""
    acc = js["accessors"]

    def total(indices):
        seen = set()
        for index in indices:
            a = acc[index]
            size = COMPONENTS[a["type"]] * COMPONENT_BYTES[a["componentType"]]
            views = {a["bufferView"]} if "bufferView" in a else set()
            sparse = a.get("sparse")
            if sparse:
                views |= {sparse["indices"]["bufferView"], sparse["values"]["bufferView"]}
            for view in views:
                seen.add(view)
        return sum(js["bufferViews"][v]["byteLength"] for v in seen)

    geometry, morphs, animation, weights = set(), set(), set(), set()
    for mesh in js["meshes"]:
        for prim in mesh["primitives"]:
            geometry |= set(prim["attributes"].values())
            if "indices" in prim:
                geometry.add(prim["indices"])
            for target in prim.get("targets", []):
                morphs |= set(target.values())
    for anim in js["animations"]:
        for channel in anim["channels"]:
            sampler = anim["samplers"][channel["sampler"]]
            (weights if channel["target"]["path"] == "weights" else animation).update(
                (sampler["input"], sampler["output"]))
    textures = sum(js["bufferViews"][image["bufferView"]]["byteLength"]
                   for image in js.get("images", []))
    return {
        "animaciones (huesos)": total(animation),
        "animaciones (cara, pesos)": total(weights),
        "geometría": total(geometry),
        f"morph targets ({sum(len(m['primitives'][0].get('targets', [])) for m in js['meshes'])})": total(morphs),
        "texturas": textures,
        "buffer": len(binary),
    }


def find_source(source):
    if source.is_file():
        return source
    files = sorted(source.glob("*.glb"))
    if not files:
        raise SystemExit(f"no hay ningún .glb en {source}")
    if len(files) > 1:
        raise SystemExit(
            f"hay {len(files)} .glb en {source}; hay que dejar uno o pasarlo por parámetro")
    return files[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("source", type=Path, nargs="?", default=SRC_DEFAULT,
                        help="el .glb con las 50 señas, o la carpeta que lo contiene")
    parser.add_argument("dest", type=Path, nargs="?", default=DEST_DEFAULT,
                        help="assets/web (donde viven models/ y manifest.json)")
    parser.add_argument("--dry-run", action="store_true",
                        help="informa sin escribir el archivo")
    args = parser.parse_args()

    source = find_source(args.source)
    js, binary = gp.read_glb(source)
    before_size = source.stat().st_size
    names = check_source(js)
    print(f"origen    : {source.name} ({before_size / 1e6:.2f} MB, {len(names)} clips)")

    report = bake_rest_pose(js, binary)
    print("\n".join(rest_pose.report_lines(
        report, indent="  pose      ", reference=f"el clip {rest_pose.REFERENCE_ID} del mismo archivo")))

    keep = morphs_to_keep(js, binary)
    before_morphs = {i: len(m["primitives"][0].get("targets", []))
                     for i, m in enumerate(js["meshes"]) if i in keep}
    js, binary = repack(js, binary, keep)
    after_morphs = {i: len(js["meshes"][i]["primitives"][0].get("targets", [])) for i in keep}
    for index, count in before_morphs.items():
        name = js["meshes"][index].get("name", f"#{index}")
        print(f"  morphs    {name}: {count} -> {after_morphs[index]}")

    if args.dry_run:
        print(f"\n  --dry-run: no se escribió nada ({len(binary) / 1e6:.2f} MB en memoria)")
        return

    dest = args.dest
    avatar = dest / "models/avatar.glb"
    avatar.parent.mkdir(parents=True, exist_ok=True)
    size = gp.write_glb(avatar, js, binary)
    try:
        shown = avatar.relative_to(REPO)
    except ValueError:
        shown = avatar
    print(f"\n  escrito   {shown} ({size / 1e6:.2f} MB, "
          f"{(size - before_size) / 1e6:+.2f} MB contra el origen)")

    for label, value in size_report(js, binary).items():
        print(f"  {label:38s} {value / 1e6:6.2f} MB")

    manifest = build_manifest(avatar, js)
    (dest / "manifest.json").write_text(
        json.dumps(manifest, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"  manifest  signos: {len(manifest)}")

    count = write_catalog(manifest, packed=True)
    print(f"  catálogo  SignsCatalog.kt ({count} señas, clips dentro del avatar)")


if __name__ == "__main__":
    main()
