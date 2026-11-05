#!/usr/bin/env python3
"""Mide cuánto mueve la cara cada variante facial de los avatares (DOC-5/DOC-6).

La pregunta del plan no es «los tracks difieren» —eso ya se sabía— sino **cuánto**
mueve la cara cada variante, si las cuatro se distinguen entre sí y si alguna es
tan sutil que en pantalla no se ve. Este script contesta eso con tres lecturas
del GLB empaquetado, sin renderizar y sin abrir Blender:

  1. **Alcance geométrico del morph.** Cada target de malla guarda, por vértice,
     cuánto se mueve ese vértice con peso 1. De ahí sale el desplazamiento
     máximo del target en milímetros. Un peso de 0,2 sobre un target de 10 mm
     mueve la cara 2 mm; sobre uno de 1 mm la mueve 0,2 mm, que no se ve.
  2. **Curva de pesos del clip.** Los canales `weights` de la animación dan el
     peso de cada target en cada keyframe. Se samplea el clip completo y se
     calculan pico, media y desplazamiento efectivo `peso · alcance`.
  3. **Diferencia contra el clip de referencia.** Los GLB del 2026-11-04 no
     traen clip base: cada seña tiene cuatro caras gramaticales. Para cada seña
     se compara cada variante contra `_afirmativo` —la que la app usa como
     respaldo— en tiempo normalizado, así que un clip más largo no infla la
     diferencia. Esa es la cara que el usuario **ve cambiar** al cambiar el tipo
     de oración: si dos variantes pesan lo mismo, no se distinguen.

Los umbrales de visibilidad son una **hipótesis**, no una medición en device:
< 1 mm apenas se percibe en el encuadre de la app, 1–2 mm es marginal y > 3 mm
se ve. La conversión a píxeles usa el encuadre por defecto (`FRAME_CAMERA` a
2,3 m, fov 45°, canvas de 2400 px de alto): 1 mm ≈ 1,3 px. Es una cuenta, no una
captura.

Uso:

    python3 tools/measure_face_markers.py                 # los dos avatares
    python3 tools/measure_face_markers.py --json out.json --md out.md
"""
import argparse
import datetime
import json
import math
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import glb_pose as gp  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
MODELS = REPO / "app/src/main/assets/web/models"
AVATARS = ("juan", "pauline")
# Las cuatro marcas gramaticales del export del 2026-11-04. No hay clip base.
SUFFIXES = ("afirmativo", "pregunta", "negacion", "orden")
# La app usa `_afirmativo` cuando el tipo de oración falta o no está en la
# tabla, así que es la referencia natural para medir cuánto agrega cada otra.
REFERENCE_VARIANT = "afirmativo"
COMPARED_SUFFIXES = tuple(v for v in SUFFIXES if v != REFERENCE_VARIANT)
SIGN_IDS = [f"{i:04d}" for i in range(50)]

# Los morphs de forma base no son expresión facial: vienen con peso 1 o con un
# valor fijo y no los mueve ninguna variante. Se miden aparte y no entran al
# promedio de la cara, porque si no el «movimiento» de la cara sería el tamaño
# del cuerpo.
UTILITY_TARGETS = {"Male", "LIVE_KEY_PERMANENT", "LIVE_KEY_TEMP_height_150",
                   "Body Proportions", "eyeball_size"}

# Regiones para leer qué parte del rostro mueve cada variante.
REGIONS = (
    ("cejas", ("brow",)),
    ("ojos", ("eye",)),
    ("boca", ("mouth",)),
    ("mejillas", ("cheek",)),
    ("nariz", ("nose",)),
)

SAMPLE_COUNT = 81  # puntos por clip para las curvas; los clips traen 5–40 keys

VISIBLE_MM = 3.0
MARGINAL_MM = 1.0
# 1 mm en el encuadre por defecto: 2 * 2,3 m * tan(22,5°) = 1,905 m de alto
# visible en un canvas de 2400 px → 2400 / 1905 = 1,26 px/mm.
PX_PER_MM = 1.26


def target_names(mesh):
    return list((mesh.get("extras") or {}).get("targetNames") or [])


def read_flat(js, binary, index):
    """Accessor como lista plana, con soporte de `sparse`.

    `glb_pose.read_accessor` no soporta sparse a propósito (el empaquetado no lo
    necesita), pero algunos morphs de los avatares vienen sparse: unos pocos
    vértices guardados como (índice, valor) sobre una base de ceros. Sin esto la
    medición del alcance se cae justo en esos targets.
    """
    acc = js["accessors"][index]
    try:
        return gp.read_accessor(js, binary, index)
    except ValueError:
        pass
    comps = gp.COMPONENTS[acc["type"]]
    code, _size = gp.COMPONENT_TYPES[acc["componentType"]]
    values = [0.0] * (acc["count"] * comps)
    sparse = acc["sparse"]
    idx_info = sparse["indices"]
    idx_code, _ = gp.COMPONENT_TYPES[idx_info["componentType"]]
    idx_view = js["bufferViews"][idx_info["bufferView"]]
    idx_base = idx_view.get("byteOffset", 0) + idx_info.get("byteOffset", 0)
    indices = list(struct.unpack_from(
        f"<{sparse['count']}{idx_code}", binary, idx_base))
    val_info = sparse["values"]
    val_view = js["bufferViews"][val_info["bufferView"]]
    val_base = val_view.get("byteOffset", 0) + val_info.get("byteOffset", 0)
    flat = struct.unpack_from(f"<{sparse['count'] * comps}{code}", binary, val_base)
    for k, target in enumerate(indices):
        for c in range(comps):
            values[target * comps + c] = flat[k * comps + c]
    return values


def region_of(name):
    for region, prefixes in REGIONS:
        if any(name.startswith(p) for p in prefixes):
            return region
    return "otros"


def reach_mm(js, binary, mesh):
    """Desplazamiento máximo de cada target de la malla, en milímetros."""
    reaches = []
    prim = mesh["primitives"][0]
    for target in prim.get("targets") or []:
        acc_index = target.get("POSITION")
        if acc_index is None:
            reaches.append(0.0)
            continue
        flat = read_flat(js, binary, acc_index)
        worst = 0.0
        for i in range(0, len(flat), 3):
            worst = max(worst, math.sqrt(flat[i] ** 2 + flat[i + 1] ** 2
                                         + flat[i + 2] ** 2))
        reaches.append(worst * 1000.0)
    return reaches


def face_meshes(js):
    """Índices de malla con morphs faciales."""
    out = []
    for index, mesh in enumerate(js.get("meshes", [])):
        names = target_names(mesh)
        if not names or not any(n not in UTILITY_TARGETS for n in names):
            continue
        out.append(index)
    return out


def weight_tracks(js, binary, anim_index, mesh_index):
    """Curva de pesos de un clip sobre una malla: `(times, matrix)` o `None`."""
    anim = js["animations"][anim_index]
    prim = js["meshes"][mesh_index]["primitives"][0]
    count = len(prim.get("targets") or [])
    for channel in anim["channels"]:
        if channel["target"]["path"] != "weights":
            continue
        node = js["nodes"][channel["target"]["node"]]
        if node.get("mesh") != mesh_index:
            continue
        sampler = anim["samplers"][channel["sampler"]]
        times = gp.read_accessor(js, binary, sampler["input"])
        flat = gp.read_accessor(js, binary, sampler["output"])
        if not count:
            return None
        matrix = [flat[k * count:(k + 1) * count] for k in range(len(times))]
        return times, matrix
    return None


def sample_matrix(times, matrix, u):
    """Peso de cada target en la fracción `u` del clip (interpolación lineal)."""
    start, end = times[0], times[-1]
    t = start + (end - start) * u
    if t <= start:
        return list(matrix[0])
    if t >= end:
        return list(matrix[-1])
    lo, hi = 0, len(times) - 1
    while hi - lo > 1:
        mid = (lo + hi) // 2
        if times[mid] <= t:
            lo = mid
        else:
            hi = mid
    span = times[hi] - times[lo] or 1.0
    ratio = (t - times[lo]) / span
    a, b = matrix[lo], matrix[hi]
    return [a[i] + (b[i] - a[i]) * ratio for i in range(len(a))]


def curve(times, matrix, samples=SAMPLE_COUNT):
    return [sample_matrix(times, matrix, k / (samples - 1)) for k in range(samples)]


def summarize_curve(samples, reaches, facial):
    """Pico y media del desplazamiento real (mm) de una curva de pesos."""
    peak = 0.0
    peak_no_blink = 0.0
    total = 0.0
    n = 0
    for row in samples:
        for t, weight in enumerate(row):
            if not facial[t][0]:
                continue
            disp = abs(weight) * reaches[t]
            peak = max(peak, disp)
            if facial[t][1]:
                peak_no_blink = max(peak_no_blink, disp)
            total += disp
            n += 1
    return {
        "peak_mm": peak,
        "peak_no_blink_mm": peak_no_blink,
        "mean_mm": total / n if n else 0.0,
    }


def compare_curves(samples, reference_samples, reaches, names, facial):
    """Diferencia de la variante contra la referencia, en mm."""
    per_target_weight = [0.0] * len(reaches)
    per_target_mm = [0.0] * len(reaches)
    rms_acc = 0.0
    n = 0
    for row, reference_row in zip(samples, reference_samples):
        for t, (weight, reference) in enumerate(zip(row, reference_row)):
            if not facial[t][0] or not facial[t][1]:
                # El parpadeo existe igual en la referencia y en la variante y
                # no es un marcador gramatical: se excluye del delta.
                continue
            delta = abs(weight - reference)
            per_target_weight[t] = max(per_target_weight[t], delta)
            per_target_mm[t] = max(per_target_mm[t], delta * reaches[t])
            rms_acc += (delta * reaches[t]) ** 2
            n += 1
    regions = {}
    for t, name in enumerate(names):
        if not facial[t][0] or not facial[t][1]:
            continue
        region = region_of(name)
        regions[region] = max(regions.get(region, 0.0), per_target_mm[t])
    ranked = sorted(
        ((names[t], per_target_weight[t], per_target_mm[t])
         for t in range(len(names)) if facial[t][0] and facial[t][1]),
        key=lambda item: item[2], reverse=True)
    return {
        "peak_mm": max(per_target_mm) if per_target_mm else 0.0,
        "rms_mm": math.sqrt(rms_acc / n) if n else 0.0,
        "changed_targets": sum(1 for w in per_target_weight if w >= 0.05),
        "regions_mm": regions,
        "targets": ranked,
    }


def signature(matrix, reaches, names, facial, threshold=0.1):
    """Pesos sostenidos de la variante: qué targets mueve y cuánto, en mm."""
    row = matrix[0]
    out = []
    for t, weight in enumerate(row):
        if not facial[t][0] or not facial[t][1] or abs(weight) < threshold:
            continue
        out.append((names[t], round(weight, 3), round(abs(weight) * reaches[t], 2)))
    # El mismo nombre puede existir en la malla de la cara y en la de los ojos
    # con alcances distintos: se deja el mayor, que es el que se ve.
    merged = {}
    for name, weight, mm in out:
        if name not in merged or mm > merged[name][2]:
            merged[name] = (name, weight, mm)
    return sorted(merged.values(), key=lambda item: item[2], reverse=True)


def bone_channel_signature(js, binary, anim_index):
    """Firma de los canales de hueso: (nodo, path) → valores por keyframe."""
    out = {}
    for name, path, times, values, _ in gp.clip_tracks(js, binary, anim_index):
        if path == "weights":
            continue
        out[(name, path)] = (times, values)
    return out


def _quat_angle(a, b):
    """Ángulo entre dos quaterniones, normalizándolos primero.

    Sin normalizar, un quaternion con |q|² = 0,9999995 da 0,08° entre dos
    valores **idénticos**: el ruido de float32 se leería como una diferencia.
    """
    na = math.sqrt(sum(x * x for x in a)) or 1.0
    nb = math.sqrt(sum(x * x for x in b)) or 1.0
    dot = abs(sum(x * y for x, y in zip(a, b)) / (na * nb))
    return math.degrees(2.0 * math.acos(max(-1.0, min(1.0, dot))))


def bone_difference(sig_a, sig_b):
    """Máxima diferencia entre dos firmas de hueso, o `None` si no coinciden."""
    if set(sig_a) != set(sig_b):
        return None
    worst_deg = 0.0
    worst_mm = 0.0
    for key, (times_a, values_a) in sig_a.items():
        times_b, values_b = sig_b[key]
        if len(times_a) != len(times_b):
            return None
        for a, b in zip(values_a, values_b):
            if key[1] == "rotation":
                worst_deg = max(worst_deg, _quat_angle(a, b))
            else:
                worst_mm = max(worst_mm, max(abs(x - y) for x, y in zip(a, b)) * 1000.0)
    return {"rotation_deg": worst_deg, "linear_mm": worst_mm}


def measure_avatar(avatar):
    js, binary = gp.read_glb(MODELS / f"{avatar}.glb")
    mesh_indexes = face_meshes(js)
    details = {}
    for mesh_index in mesh_indexes:
        mesh = js["meshes"][mesh_index]
        names = target_names(mesh)
        reaches = reach_mm(js, binary, mesh)
        # facial[t] = (es facial, cuenta para el marcador). El parpadeo es
        # facial pero no marcador: existe igual en todas las variantes.
        facial = [(name not in UTILITY_TARGETS, not name.startswith("eyeBlink"))
                  for name in names]
        details[mesh_index] = {"names": names, "reaches": reaches, "facial": facial}

    clips = {anim.get("name"): i for i, anim in enumerate(js["animations"])}
    needed = [f"0000_{suffix}" for suffix in SUFFIXES]
    missing = [name for name in needed if name not in clips]
    if missing:
        raise SystemExit(
            "measure_face_markers.py espera el layout del 2026-11-04: cuatro "
            "marcas gramaticales (_afirmativo, _pregunta, _negacion, _orden) y "
            f"ningún clip base. En {avatar}.glb faltan {missing}.")
    result = {
        "avatar": avatar,
        "reference": REFERENCE_VARIANT,
        "meshes": [],
        "signatures": {},
        "signs": {},
        "bone_differences": [],
        "across_signs": {},
    }
    for mesh_index, info in details.items():
        facial_count = sum(1 for is_facial, _ in info["facial"] if is_facial)
        result["meshes"].append({
            "mesh": js["meshes"][mesh_index].get("name"),
            "targets": len(info["names"]),
            "facial_targets": facial_count,
            "max_reach_mm": max((info["reaches"][t]
                                 for t, (is_facial, _) in enumerate(info["facial"])
                                 if is_facial), default=0.0),
        })

    # Firma de cada variante: los pesos son los mismos en las 50 señas, así que
    # se leen del clip de la seña 0000 y se verifica abajo que no cambien.
    for variant in SUFFIXES:
        index = clips[f"0000_{variant}"]
        merged = []
        for mesh_index, info in details.items():
            track = weight_tracks(js, binary, index, mesh_index)
            if track is None:
                continue
            merged.extend(signature(track[1], info["reaches"],
                                    info["names"], info["facial"]))
        best = {}
        for name, weight, mm in merged:
            if name not in best or mm > best[name][2]:
                best[name] = (name, weight, mm)
        result["signatures"][variant] = sorted(
            best.values(), key=lambda item: item[2], reverse=True)

    # ¿La expresión cambia durante la seña o es una pose fija? Rango de peso a
    # lo largo del clip, para el target que más se mueva.
    temporal = {}
    for variant in SUFFIXES:
        index = clips[f"0000_{variant}"]
        worst_weight, worst_mm = 0.0, 0.0
        for mesh_index, info in details.items():
            track = weight_tracks(js, binary, index, mesh_index)
            if track is None:
                continue
            samples = curve(*track)
            for t, (is_facial, is_marker) in enumerate(info["facial"]):
                if not is_facial or not is_marker:
                    continue
                values = [row[t] for row in samples]
                span = max(values) - min(values)
                if span > worst_weight:
                    worst_weight = span
                    worst_mm = span * info["reaches"][t]
        temporal[variant] = {"weight_range": worst_weight, "mm": worst_mm}
    result["temporal"] = temporal

    for sign in SIGN_IDS:
        per_sign = {}
        # No hay clip base: la referencia es la variante que la app usa por
        # defecto (`_afirmativo`). Se compara cada otra cara contra ella.
        reference_index = clips[f"{sign}_{REFERENCE_VARIANT}"]
        reference_sigs = bone_channel_signature(js, binary, reference_index)
        for variant in SUFFIXES:
            name = f"{sign}_{variant}"
            index = clips[name]
            entry = {"clip": name, "faces": {}, "bone": None}
            if variant != REFERENCE_VARIANT:
                diff = bone_difference(reference_sigs,
                                       bone_channel_signature(js, binary, index))
                entry["bone"] = diff
                if diff is None or diff["rotation_deg"] > 0.01 \
                        or diff["linear_mm"] > 0.01:
                    result["bone_differences"].append({"clip": name, "diff": diff})
            for mesh_index, info in details.items():
                track = weight_tracks(js, binary, index, mesh_index)
                if track is None:
                    entry["faces"][str(mesh_index)] = None
                    continue
                samples = curve(*track)
                absolute = summarize_curve(samples, info["reaches"], info["facial"])
                face = {
                    "absolute_peak_mm": round(absolute["peak_mm"], 3),
                    "absolute_mean_mm": round(absolute["mean_mm"], 3),
                }
                if variant != REFERENCE_VARIANT:
                    ref_track = weight_tracks(js, binary, reference_index, mesh_index)
                    ref_samples = (curve(*ref_track) if ref_track
                                   else [[0.0] * len(info["names"])] * len(samples))
                    delta = compare_curves(samples, ref_samples, info["reaches"],
                                           info["names"], info["facial"])
                    face["delta_peak_mm"] = round(delta["peak_mm"], 3)
                    face["delta_rms_mm"] = round(delta["rms_mm"], 3)
                    face["changed_targets"] = delta["changed_targets"]
                entry["faces"][str(mesh_index)] = face
            per_sign[variant] = entry
        result["signs"][sign] = per_sign

        # ¿La cara de esta variante es la misma en todas las señas? Se compara
        # la matriz de pesos del clip contra la de la seña 0000.
        for variant in SUFFIXES:
            same = True
            for mesh_index in details:
                here = weight_tracks(js, binary, clips[f"{sign}_{variant}"], mesh_index)
                first = weight_tracks(js, binary, clips[f"0000_{variant}"], mesh_index)
                if here is None or first is None:
                    continue
                if here[1] != first[1]:
                    same = False
            result["across_signs"].setdefault(variant, []).append(same)

    # Distinción entre variantes: diferencia de las curvas completas, no solo
    # del pico. Dos variantes pueden compartir el pico (las cejas) y diferir en
    # el resto de la cara; el RMS lo ve y el pico solo no.
    pairs = {}
    for a_i, va in enumerate(SUFFIXES):
        for vb in SUFFIXES[a_i + 1:]:
            peaks, rms_values, region_diffs = [], [], {}
            for sign in SIGN_IDS:
                worst = 0.0
                rms_acc = 0.0
                n = 0
                for mesh_index, info in details.items():
                    ta = weight_tracks(js, binary, clips[f"{sign}_{va}"], mesh_index)
                    tb = weight_tracks(js, binary, clips[f"{sign}_{vb}"], mesh_index)
                    if not ta or not tb:
                        continue
                    sa, sb = curve(*ta), curve(*tb)
                    for row_a, row_b in zip(sa, sb):
                        for t, (wa, wb) in enumerate(zip(row_a, row_b)):
                            if not info["facial"][t][0] or not info["facial"][t][1]:
                                continue
                            diff = abs(wa - wb) * info["reaches"][t]
                            worst = max(worst, diff)
                            rms_acc += diff ** 2
                            n += 1
                            region = region_of(info["names"][t])
                            region_diffs[region] = max(
                                region_diffs.get(region, 0.0), diff)
                peaks.append(worst)
                rms_values.append(math.sqrt(rms_acc / n) if n else 0.0)
            pairs[f"{va}|{vb}"] = {
                "mean_peak_mm": sum(peaks) / len(peaks),
                "mean_rms_mm": sum(rms_values) / len(rms_values),
                "min_peak_mm": min(peaks),
                "signs_under_1mm": sum(1 for p in peaks if p < 1.0),
                "regions_mm": {k: round(v, 2) for k, v in region_diffs.items()},
            }
    result["variant_pairs"] = pairs
    return result


def json_summary(results):
    """Versión chica para el JSON versionado.

    El detalle por target se repite igual en las 50 señas, así que se guarda una
    vez en `signatures` y por seña solo queda el número que se usa en la tabla.
    El Markdown conserva el detalle legible; el script puede regenerar todo
    desde los GLB.
    """
    out = []
    for result in results:
        per_sign = {}
        for sign, variants in result["signs"].items():
            per_sign[sign] = {
                variant: {
                    "peak_mm": round(max(f["delta_peak_mm"]
                                         for f in entry["faces"].values()), 3),
                    "rms_mm": round(max(f["delta_rms_mm"]
                                        for f in entry["faces"].values()), 3),
                }
                for variant, entry in variants.items()
                if variant != REFERENCE_VARIANT
            }
        out.append({
            "avatar": result["avatar"],
            "reference": result["reference"],
            "meshes": result["meshes"],
            "signatures": result["signatures"],
            "temporal": result["temporal"],
            "variant_pairs": result["variant_pairs"],
            "across_signs": {variant: all(same)
                             for variant, same in result["across_signs"].items()},
            "bone_differences": result["bone_differences"],
            "per_sign": per_sign,
        })
    return out


def _median(values):
    ordered = sorted(values)
    return ordered[len(ordered) // 2]


def build_markdown(results):
    today = datetime.date.today().isoformat()
    lines = []
    lines.append("# Medición de los marcadores faciales (morph targets)\n")
    lines.append(f"Generado por `tools/measure_face_markers.py` el {today} sobre "
                 "los GLB empaquetados de la app (`models/juan.glb` y "
                 "`models/pauline.glb`), con el layout del 2026-11-04: **200 clips "
                 "por avatar** (50 señas × `_afirmativo`, `_pregunta`, `_negacion`, "
                 "`_orden`) y **sin clip base**.\n")
    lines.append("Cómo se mide: cada target de malla trae el desplazamiento de sus "
                 "vértices a peso 1 (mm). El clip dice cuánto pesa cada target en "
                 "cada momento. `peso × alcance` es cuánto se mueve la cara de "
                 "verdad. El **Δ contra el clip de referencia** (`_afirmativo`, la "
                 "variante que la app usa por defecto) es lo que cada cara agrega "
                 "sobre esa, comparando en tiempo normalizado. El parpadeo "
                 "(`eyeBlink`) se excluye del Δ porque está en todas las "
                 "variantes. `≈px` usa el encuadre por defecto (1 mm ≈ 1,26 px en "
                 "un canvas de 2400 px): es una cuenta, no una captura.\n")
    for result in results:
        avatar = result["avatar"]
        lines.append(f"## {avatar.capitalize()}\n")
        lines.append("| Malla | Targets | Faciales | Alcance máx. (mm) |")
        lines.append("|---|---:|---:|---:|")
        for mesh in result["meshes"]:
            lines.append(f"| {mesh['mesh']} | {mesh['targets']} | "
                         f"{mesh['facial_targets']} | {mesh['max_reach_mm']:.2f} |")
        lines.append("")
        lines.append("### Qué hace cada variante\n")
        lines.append("Pesos sostenidos del clip (los mismos en las 50 señas; ver "
                     "abajo). Solo targets con peso ≥ 0,10. Entre paréntesis, el "
                     "desplazamiento en mm con ese peso.\n")
        lines.append("| Variante | Targets que mueve (peso → mm) |")
        lines.append("|---|---|")
        for variant in SUFFIXES:
            items = result["signatures"][variant]
            text = ", ".join(f"`{name}` {w:g} → {mm:g}" for name, w, mm in items[:8])
            rest = len(items) - 8
            if rest > 0:
                text += f" (+{rest} más)"
            lines.append(f"| `_{variant}` | {text} |")
        lines.append("")
        lines.append("### Δ contra el clip de referencia, por variante\n")
        lines.append("La referencia es `_afirmativo` (la cara que la app usa por "
                     "defecto). Mediana de las 50 señas; entre paréntesis, mínimo y "
                     "máximo. «Δ RMS» mide el parecido de toda la curva, no solo el "
                     "pico. La referencia no se compara consigo misma.\n")
        lines.append("| Variante | Δ pico (mm) | ≈px | Δ RMS (mm) | Cambia durante la seña (mm) | Target que más mueve | Misma cara en las 50 señas |")
        lines.append("|---|---:|---:|---:|---:|---|---|")
        for variant in COMPARED_SUFFIXES:
            peaks, rms_values = [], []
            for sign in SIGN_IDS:
                face = [v for v in result["signs"][sign][variant]["faces"].values() if v]
                peaks.append(max(v["delta_peak_mm"] for v in face))
                rms_values.append(max(v["delta_rms_mm"] for v in face))
            same = all(result["across_signs"].get(variant, [True]))
            top = result["signatures"][variant][0]
            lines.append(
                f"| `_{variant}` | {_median(peaks):.2f} ({min(peaks):.2f}–{max(peaks):.2f}) "
                f"| {_median(peaks) * PX_PER_MM:.1f} | {_median(rms_values):.2f} "
                f"| {result['temporal'][variant]['mm']:.2f} "
                f"| `{top[0]}` (peso {top[1]:g}) | {'sí' if same else 'no'} |")
        same_reference = all(result["across_signs"].get(REFERENCE_VARIANT, [True]))
        lines.append("")
        lines.append(f"`_{REFERENCE_VARIANT}` (referencia) también es la misma cara "
                     f"en las 50 señas: {'sí' if same_reference else 'no'}.\n")
        lines.append("### Distinción entre variantes\n")
        lines.append("Diferencia de las curvas completas. `Δ pico` es el target que "
                     "más difiere; `Δ RMS` es el parecido de la cara entera. Regiones: "
                     "dónde está la mayor diferencia.\n")
        lines.append("| Par | Δ pico medio (mm) | Δ RMS medio (mm) | Δ pico mínimo (mm) | Región que más difiere |")
        lines.append("|---|---:|---:|---:|---|")
        for pair, data in result["variant_pairs"].items():
            first, second = pair.split("|")
            region = max(data["regions_mm"], key=data["regions_mm"].get)
            lines.append(
                f"| `_{first}` vs `_{second}` | {data['mean_peak_mm']:.2f} "
                f"| {data['mean_rms_mm']:.2f} | {data['min_peak_mm']:.2f} "
                f"| {region} ({data['regions_mm'][region]:.2f} mm) |")
        lines.append("")
        lines.append("### Por seña: Δ pico contra la referencia (mm)\n")
        lines.append("Cada celda es el máximo desplazamiento facial que agrega esa "
                     "variante sobre `_afirmativo` en la misma seña. Si una columna "
                     "sale idéntica en las 50 filas, no es un error de la tabla: la "
                     "variante es la misma cara en todas las señas.\n")
        lines.append("| Seña | " + " | ".join(f"`_{v}`" for v in COMPARED_SUFFIXES) + " |")
        lines.append("|---|" + "---:|" * len(COMPARED_SUFFIXES))
        for sign in SIGN_IDS:
            cells = []
            for variant in COMPARED_SUFFIXES:
                face = [v for v in result["signs"][sign][variant]["faces"].values() if v]
                cells.append(f"{max(v['delta_peak_mm'] for v in face):.2f}")
            lines.append(f"| {sign} | " + " | ".join(cells) + " |")
        lines.append("")
        lines.append("### Huesos: referencia contra las otras caras\n")
        if result["bone_differences"]:
            lines.append("| Clip | Rotación (grados) | Traslación/escala (mm) |")
            lines.append("|---|---:|---:|")
            for diff in result["bone_differences"]:
                if diff["diff"] is None:
                    lines.append(f"| {diff['clip']} | canales distintos | — |")
                else:
                    lines.append(f"| {diff['clip']} | {diff['diff']['rotation_deg']:.4f} "
                                 f"| {diff['diff']['linear_mm']:.4f} |")
        else:
            lines.append("Ninguna diferencia: los 150 clips variantes (50 señas × 3 "
                         "caras) tienen los mismos canales de hueso y los mismos "
                         "valores que la referencia de su seña (tolerancia 0,01° / "
                         "0,01 mm). **Las variantes mueven la cara y nada más**: no "
                         "agregan cabeza, tronco ni manos.\n")
        lines.append("")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", type=Path)
    parser.add_argument("--md", type=Path)
    args = parser.parse_args()

    results = [measure_avatar(avatar) for avatar in AVATARS]
    md = build_markdown(results)
    print(md)
    if args.json:
        args.json.write_text(json.dumps(json_summary(results), indent=1,
                                        ensure_ascii=False))
    if args.md:
        args.md.write_text(md)


if __name__ == "__main__":
    main()
