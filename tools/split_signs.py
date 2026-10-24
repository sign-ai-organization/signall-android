#!/usr/bin/env python3
"""Separa los .glb de las señas en `models/avatar.glb` + `signs/<id>.glb`.

Cada .glb exportado trae el personaje entero adentro, así que el muñeco está
duplicado una vez por seña (32 × 10.3 MB). Esto lo escribe una sola vez en
`models/` y deja en `signs/` solo la animación de cada seña, que es todo lo que
`THREE.AnimationMixer` necesita: resuelve los tracks por nombre de nodo contra
el root que se le pase, no contra los nodos del propio archivo del clip.

Los archivos de seña conservan **todos** los nodos para que los nombres de los
tracks sigan encontrando su hueso; lo que se descarta es malla, materiales,
texturas y skin.

Uso:
    python3 tools/split_signs.py                      # ../material-3d/GLB Senas/ -> assets
    python3 tools/split_signs.py OTRO_DIR DESTINO     # a elección

Además de separar, el script:

  * verifica que todas las señas usen el mismo esqueleto (y avisa loudly si no,
    porque un esqueleto distinto es el único error que no se ve en pantalla: la
    seña carga bien y se queda quieta);
  * regenera `domain/SignsCatalog.kt` desde los archivos realmente escritos, para
    que la lista y el disco no se puedan desincronizar;
  * hornea la **pose de descanso** del avatar (`rest_pose.py`), al final y no
    antes, porque sale de un clip de `signs/` que este mismo script acaba de
    escribir. Sin esto, regenerar dejaría al muñeco con los brazos abiertos.

Self-contained: no necesita `blender`, ni `npm`, ni `three`.
"""
import hashlib
import json
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import glb_pose as gp  # noqa: E402  — el lector de GLB vive ahí para todos
import rest_pose  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
# Los exports por seña viven fuera del repo, en `../material-3d/GLB Senas/`.
SRC_DEFAULT = REPO.parent / "material-3d" / "GLB Senas"
DEST_DEFAULT = REPO / "app/src/main/assets/web"
CATALOG_PATH = REPO / "app/src/main/java/com/signai/webview/domain/SignsCatalog.kt"

# Componentes de accessor → stride en floats, para leer el bbox del personaje.
COMPONENTS = {"VEC2": 2, "VEC3": 3, "VEC4": 4, "SCALAR": 1, "MAT4": 16}

read_glb = gp.read_glb
write_glb = gp.write_glb


# ── Recortes ──────────────────────────────────────────────────────────────────

def strip_character(js, binary):
    """El personaje, sin animaciones."""
    out = json.loads(json.dumps(js))
    out.pop("animations", None)
    used = [e for e in out.get("extensionsUsed", []) if "anim" not in e.lower()]
    if used:
        out["extensionsUsed"] = used
    else:
        out.pop("extensionsUsed", None)
    return out, binary


def strip_to_animation(js, binary):
    """Esqueleto + todas las animaciones; sin malla, materiales, texturas, skin."""
    out = json.loads(json.dumps(js))

    anim_accessors = set()
    for anim in out.get("animations", []):
        for sampler in anim["samplers"]:
            anim_accessors.add(sampler["input"])
            anim_accessors.add(sampler["output"])

    keep_buffers = set()
    for idx in anim_accessors:
        bv = out["accessors"][idx].get("bufferView")
        if bv is not None:
            keep_buffers.add(bv)

    for key in ("meshes", "materials", "textures", "images", "samplers", "skins", "cameras"):
        out.pop(key, None)
    used = [e for e in out.get("extensionsUsed", [])
            if "texture" not in e and "anim" not in e.lower()]
    if used:
        out["extensionsUsed"] = used
    else:
        out.pop("extensionsUsed", None)
    out["extensions"] = {k: v for k, v in out.get("extensions", {}).items()
                         if "texture" not in k}

    # Los nodos se conservan: son los que los tracks necesitan para resolver.
    for node in out.get("nodes", []):
        for key in ("mesh", "skin", "camera"):
            node.pop(key, None)

    # Reconstruir el buffer dejando solo los datos de animación.
    remap, new_views, blob = {}, [], bytearray()
    for idx in sorted(keep_buffers):
        view = out["bufferViews"][idx]
        while len(blob) % 4:
            blob.append(0)
        remap[idx] = len(new_views)
        nv = {k: v for k, v in view.items() if k not in ("buffer", "byteOffset")}
        nv["buffer"] = 0
        nv["byteOffset"] = len(blob)
        new_views.append(nv)
        blob += binary[view.get("byteOffset", 0):view.get("byteOffset", 0) + view["byteLength"]]

    for idx in anim_accessors:
        bv = out["accessors"][idx].get("bufferView")
        if bv is not None:
            out["accessors"][idx]["bufferView"] = remap[bv]

    out["bufferViews"] = new_views
    out["buffers"] = [{"byteLength": len(blob)}]
    if "scenes" in out:
        out["scenes"] = [{"nodes": out["scenes"][0].get("nodes", [0])}]
    else:
        out["scenes"] = [{"nodes": [0]}]
    out["scene"] = 0
    return out, bytes(blob)


# ── Inspección ────────────────────────────────────────────────────────────────

def skeleton_fingerprint(js):
    """Huella del esqueleto: los nombres de los huesos, en orden.

    Es lo que tiene que ser idéntico entre todas las señas. No alcanza con
    comparar meshes — dos personajes pueden compartir esqueleto y no la malla, o
    al revés, y solo el esqueleto es el que decide si la animación pega.
    """
    if "skins" not in js or not js["skins"]:
        return ""
    joints = js["skins"][0].get("joints", [])
    names = [js["nodes"][j].get("name", f"#{j}") for j in joints]
    return hashlib.sha256("|".join(names).encode("utf-8")).hexdigest()[:12]


def mesh_bbox(js, binary):
    """bbox (min, max) del personaje, para reportarlo."""
    lo = [1e9] * 3
    hi = [-1e9] * 3
    for mesh in js.get("meshes", []):
        for prim in mesh["primitives"]:
            acc = js["accessors"][prim["attributes"]["POSITION"]]
            view = js["bufferViews"][acc["bufferView"]]
            n = COMPONENTS.get(acc["type"], 3)
            base = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
            for i in range(acc["count"]):
                v = struct.unpack_from(f"<{n}f", binary, base + i * 4 * n)
                for k in range(3):
                    lo[k] = min(lo[k], v[k])
                    hi[k] = max(hi[k], v[k])
    return lo, hi


def variant_info(js, anim):
    """(frames, duración_s) leyendo el accessor de input del primer sampler."""
    inp = js["accessors"][anim["samplers"][0]["input"]]
    return inp["count"], round(inp["max"][0] - inp["min"][0], 3)


def deaccent(s):
    for a, b in (("Á", "A"), ("É", "E"), ("Í", "I"), ("Ó", "O"), ("Ú", "U"),
                 ("Ü", "U"), ("Ñ", "N")):
        s = s.replace(a, b)
    return s


# ── Catálogo ──────────────────────────────────────────────────────────────────

def write_catalog(manifest, packed=False):
    """Reescribe `domain/SignsCatalog.kt` desde los archivos realmente escritos.

    El comentario de "GENERATED" va primero: quien lo lea tiene que saber que
    editarlo a mano se pierde en la próxima corrida.

    [packed] cambia el origen que se documenta: con el layout de un `.glb` por
    seña lo escribe `split_signs.py` y cada id es un archivo en `signs/`; con el
    archivo único lo escribe `pack_signall.py` y cada id es un **clip** dentro de
    `models/avatar.glb`. La lista y las funciones son las mismas —lo único que
    cambia es dónde vive la animación—, así que el resto del código no distingue.
    """
    ids = [m["sign_id"] + "_0000_0000" for m in manifest]
    lines = []
    for i in range(0, len(ids), 4):
        lines.append("        " + ", ".join(f'"{x}"' for x in ids[i:i + 4]) + ",")

    missing = [f"{i:04d}" for i in range(len(ids), 50)]
    not_exported = (
        f" — {missing[0]}–{missing[-1]} still have no export" if missing else ""
    )
    if packed:
        generator = "tools/pack_signall.py"
        layout = ("each is a clip named `<id>` inside `assets/web/models/avatar.glb`,\n"
                  " * the one file that also holds the character")
        coverage = (f"All {len(ids)} vocabulary signs of LSC50 are here, bundled in the\n"
                    " * avatar. Signs outside this list fall back to [DEFAULT] rather than\n"
                    " * failing (RF-CAT-03).")
        asset_url_doc = (
            "    /**\n"
            "     * The logical path of a sign, as a same-origin URL.\n"
            "     *\n"
            "     * The animation itself is a clip inside `models/avatar.glb`, and the scene\n"
            "     * resolves this path to that clip **by name** without any request: it parses\n"
            "     * `<id>` out of the path and looks it up among the clips it already loaded\n"
            "     * (see `embeddedClipsFor` in `app.js`). The path is kept as the identifier\n"
            "     * because it is also what the backend's catalog answers, so the same string\n"
            "     * means the same sign on both sides.\n"
            "     */")
        local_doc = (
            "     * The backend answers with the path we seeded in `catalog`, and since the\n"
            "     * animation ships inside the avatar the scene resolves it by name, without\n"
            "     * touching the network. A path that matches a sign in [available] is\n"
            "     * accepted; anything else is refused and the caller treats it as \"download\n"
            "     * it instead\".")
        own_doc = (
            "     * The counterpart of [localAssetOrNull]: that one says \"we have it\", this\n"
            "     * one says \"nobody is going to serve it over HTTP either\". Every LSC50 gloss\n"
            "     * has its clip bundled, so a `signs/<id>.glb` in our layout is never a\n"
            "     * remote asset: asking the backend for one would be a guaranteed 404. A\n"
            "     * path that is *not* in this layout is a real remote asset and still goes\n"
            "     * down the download path.")
    else:
        generator = "tools/split_signs.py"
        layout = "each maps to `assets/web/signs/<id>.glb`"
        coverage = (f"Only {len(ids)} of the 50 vocabulary signs are here{not_exported}. Signs outside\n"
                    " * this list fall back to [DEFAULT] rather than failing (RF-CAT-03).")
        asset_url_doc = "    /** Where a sign's animation lives, as a same-origin asset path. */"
        local_doc = (
            "     * The backend answers with the path we seeded in `catalog`, and since the\n"
            "     * assets ship inside the APK the path is already same-origin: the scene\n"
            "     * loads it directly. Only a path that matches a sign we actually ship is\n"
            "     * accepted, so a sign whose animation is not exported yet (0032–0049)\n"
            "     * returns null and the caller falls back instead of requesting a file that\n"
            "     * does not exist.\n"
            "     *\n"
            "     * Anything else — a remote URL, a traversal, a name we do not ship — is\n"
            "     * refused, and the caller treats it as \"download it instead\".")
        own_doc = (
            "     * The counterpart of [localAssetOrNull]: that one says \"we have it\", this\n"
            "     * one says \"nobody is going to serve it over HTTP either\". The catalog\n"
            "     * announces all 50 LSC50 glosses but only {n} have an export, so the\n"
            "     * rest come back with a `signs/<id>.glb` we do not ship. Asking the backend\n"
            "     * for those is a guaranteed 404 that costs a round trip on every utterance\n"
            "     * and changes nothing, so the caller skips straight to the fallback. A path\n"
            "     * that is *not* in this layout is a real remote asset and still goes down\n"
            "     * the download path.").format(n=len(ids))
    text = f"""package com.signai.webview.domain

/**
 * Signs shipped inside the APK, from `{generator}`.
 *
 * One entry per sign of LSC50 that has an exported animation (rep 0, angle 0);
 * {layout}.
 *
 * This file is GENERATED — `{generator}` rewrites it from the assets it
 * just wrote, so the list and the assets cannot drift apart. Editing it
 * by hand will be undone on the next generator run.
 *
 * The character itself is NOT per sign: `assets/web/models/avatar.glb` is loaded
 * once and every sign is a clip retargeted to that same skeleton. Replacing the
 * avatar therefore needs no re-export of the signs.
 *
 * {coverage}
 *
 * These are *animation* assets, not glosses. What a sign *means* still comes
 * from the backend's catalog, which holds all 50 LSC50 glosses: `available` on
 * a translation is about the gloss, not about whether we have the movement. A
 * gloss whose animation is not in this list is announced anyway, and
 * [localAssetOrNull] returns null for it so the caller falls back without
 * asking the backend for a file nobody serves.
 */
object SignsCatalog {{
    val available: List<String> = listOf(
{chr(10).join(lines)}
    )

    /** The sign the app opens on, and the one it falls back to. */
    const val DEFAULT: String = "{ids[0]}"

{asset_url_doc}
    fun assetUrl(id: String): String = "signs/$id.glb"

    /**
     * Resolves a `glb_animation_path` coming from the backend to a local asset,
     * or null when that path is not one of ours.
     *
{local_doc}
    */
    fun localAssetOrNull(path: String): String? {{
        val clean = path.trim()
        if (!clean.startsWith("signs/") || clean.contains("..") || clean.contains("//")) return null
        if (!clean.endsWith(".glb")) return null
        val id = clean.removePrefix("signs/").removeSuffix(".glb")
        return if (id in available) clean else null
    }}

    /**
     * True when [path] is one of *our* asset paths, whether or not we ship it.
     *
{own_doc}
     */
    fun isOwnAssetPath(path: String): Boolean {{
        val clean = path.trim()
        return clean.startsWith("signs/") && clean.endsWith(".glb") &&
            !clean.contains("..") && !clean.contains("//")
    }}
}}
"""
    CATALOG_PATH.write_text(text, encoding="utf-8")
    return len(ids)


# ── main ──────────────────────────────────────────────────────────────────────

def main():
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else SRC_DEFAULT
    dest = Path(sys.argv[2]) if len(sys.argv) > 2 else DEST_DEFAULT
    models = dest / "models"
    signs = dest / "signs"

    files = sorted(src.glob("*.glb"))
    if not files:
        sys.exit(f"no hay .glb en {src}")

    print(f"separando {len(files)} señas de {src}")
    print(f"             hacia {dest}\n")

    # Avatar: se saca del primer archivo, y se compara el esqueleto del resto
    # contra él para detectar si Samuel cambió de muñeco en alguna seña.
    base_js, base_bin = read_glb(files[0])
    avatar_js, avatar_bin = strip_character(base_js, base_bin)
    models.mkdir(parents=True, exist_ok=True)
    avatar_size = write_glb(models / "avatar.glb", avatar_js, avatar_bin)
    base_fingerprint = skeleton_fingerprint(base_js)
    lo, hi = mesh_bbox(base_js, base_bin)
    print(f"  models/avatar.glb{'':>14}{avatar_size / 1e6:7.2f} MB  "
          f"meshes={len(avatar_js.get('meshes', []))} "
          f"images={len(avatar_js.get('images', []))}")
    print(f"  {'':>26}huesos={len(base_js['skins'][0]['joints'])}  "
          f"esqueleto={base_fingerprint}  "
          f"alto={hi[1] - lo[1]:.3f}m  ancho={hi[0] - lo[0]:.3f}m\n")

    manifest = []
    mismatched = []
    total_in = 0

    for path in files:
        js, binary = read_glb(path)
        total_in += path.stat().st_size

        stem = path.stem                       # "0000 - GRACIAS"
        sign_id = stem.split(" - ")[0]         # "0000"
        gloss = stem.split(" - ", 1)[1] if " - " in stem else ""

        fingerprint = skeleton_fingerprint(js)
        if fingerprint != base_fingerprint:
            mismatched.append((sign_id, gloss, fingerprint))

        sign_js, sign_bin = strip_to_animation(js, binary)
        out_name = f"{sign_id}_0000_0000.glb"
        size = write_glb(signs / out_name, sign_js, sign_bin)
        raw = (signs / out_name).read_bytes()

        variants = []
        for anim in sign_js["animations"]:
            frames, duration = variant_info(sign_js, anim)
            variants.append({"name": anim.get("name", ""),
                             "frames": frames, "duration_s": duration})
        frames, duration = variants[0]["frames"], variants[0]["duration_s"]
        fps = round(frames / duration) if duration else 24

        bones = len({sign_js["nodes"][c["target"]["node"]]["name"]
                     for a in sign_js["animations"] for c in a["channels"]})
        manifest.append({
            "sign_id": sign_id,
            "gloss": gloss,
            "gloss_key": deaccent(gloss.upper()),
            "path": f"signs/{out_name}",
            "variants": len(variants),
            "variant_names": [v["name"] for v in variants],
            "fps": fps,
            "frames": frames,
            "duration_s": duration,
            "bytes": size,
            "sha256": hashlib.sha256(raw).hexdigest(),
            "tracks": len(sign_js["animations"][0]["channels"]),
            "bones": bones,
        })
        print(f"  {out_name:<24}{size / 1024:7.1f} KB  "
              f"{len(variants)} variante(s)  {frames}f @ {fps}fps  {bones} huesos")

    (dest / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")

    n_catalog = write_catalog(manifest)

    # La pose de descanso va **último**: sale de `signs/0029_0000_0000.glb`, que
    # este script acaba de escribir. Va en el json del avatar y se vuelve a
    # guardar; el archivo no cambia de tamaño (solo se sobrescriben rotaciones
    # que ya estaban) ni de bbox (el alto/ancho sale de los accessors
    # POSITION, que no se tocan).
    rest_pose.check_bind(avatar_js, avatar_bin)
    print()
    for line in rest_pose.report_lines(rest_pose.bake_rest_pose(avatar_js, signs)):
        print(line)
    avatar_size = write_glb(models / "avatar.glb", avatar_js, avatar_bin)

    avatar_mb = (models / "avatar.glb").stat().st_size
    signs_mb = sum(m["bytes"] for m in manifest)
    print()
    print(f"  entrada   : {len(files)} archivos, {total_in / 1e6:.1f} MB")
    print(f"  models/   : {avatar_mb / 1e6:.2f} MB  (1 archivo)")
    print(f"  signs/    : {signs_mb / 1e6:.2f} MB  ({len(manifest)} archivos, "
          f"promedio {signs_mb / len(manifest) / 1024:.0f} KB)")
    print(f"  total     : {(avatar_mb + signs_mb) / 1e6:.2f} MB   "
          f"({total_in / (avatar_mb + signs_mb):.0f}x menos)")
    print(f"  manifiesto: manifest.json")
    print(f"  catálogo  : SignsCatalog.kt ({n_catalog} señas)")

    if mismatched:
        print(f"\n  ATENCIÓN: {len(mismatched)} señas usan un esqueleto distinto al de "
              f"models/avatar.glb.")
        for sign_id, gloss, fp in mismatched:
            print(f"    {sign_id} {gloss:<28} esqueleto={fp}")
        print("  Esas no se van a mover en la app, sin error visible.")
        sys.exit(1)
    else:
        print(f"\n  {len(files)}/{len(files)} señas comparten el esqueleto de models/avatar.glb.")


if __name__ == "__main__":
    main()