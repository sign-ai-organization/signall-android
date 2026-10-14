#!/usr/bin/env python3
"""Render offline de una pose del avatar, sin three.js y sin instalar la app.

No hay Blender ni un navegador headless en la máquina, así que la única forma
de *mirar* una pose antes de gastar un `installDebug` es renderizar el `.glb`
acá: skinning lineal con las `inverseBindMatrices` del export, el mismo `pivot`
que calcula `app.js` y la misma cámara. Sirvió para elegir la pose de descanso
(`openspec/changes/reposo-y-crossfade/img/`).

Usa `numpy` y `PIL`, que no son dependencias del pipeline de assets: acá se
muestra una imagen, no se escribe un `.glb`.

    # la pose que tiene hoy el archivo (después de hornear, la nueva)
    python3 tools/preview_pose.py --pose rest --out /tmp/reposo.png

    # el reposo viejo, reconstruido desde las IBM (sigue siendo reproducible
    # después de hornear la nueva)
    python3 tools/preview_pose.py --pose bind --out /tmp/antes.png

    # las cuatro poses de la comparación, en una sola tira
    python3 tools/preview_pose.py --sheet --out /tmp/comparacion.png
"""
import argparse
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import glb_pose as gp  # noqa: E402
import rest_pose  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
WEB = REPO / "app/src/main/assets/web"
AVATAR = WEB / "models/avatar.glb"
APP_JS = WEB / "app.js"

SIZE = (420, 700)          # el canvas de la app es vertical
BG = (13, 15, 26)          # el fondo por defecto de la escena
SHADE = (168, 168, 160)


# ── Cámara ────────────────────────────────────────────────────────────────────

def app_camera():
    """La cámara de `app.js`, leída del archivo.

    `app.js` declara FRAME_LIFT / FRAME_CAMERA / FRAME_TARGET y los mueve juntos
    con cada cambio de encuadre (la cámara sube sin cambiar el ángulo). Leerlos
    de ahí, en vez de hardcodearlos, es lo que hace que este render siga
    mostrando lo mismo que la app. Las expresiones son aritmética sobre
    `FRAME_LIFT`, que es el único nombre que se sustituye.
    """
    text = APP_JS.read_text(encoding="utf-8")
    lift = re.search(r"FRAME_LIFT\s*=\s*([\d.]+)", text).group(1)

    def vector(name):
        raw = re.search(rf"{name}\s*=\s*\[([^\]]+)\]", text).group(1)
        return np.array([eval(expr.replace("FRAME_LIFT", lift),          # noqa: S307
                               {"__builtins__": {}}, {})
                         for expr in raw.split(",")], dtype=np.float64)

    fov = re.search(r"PerspectiveCamera\(\s*([\d.]+)", text).group(1)
    return vector("FRAME_CAMERA"), vector("FRAME_TARGET"), float(fov)


def project(points, camera, target, fov):
    """Puntos del export → píxeles de la imagen, con la misma convención de three."""
    forward = target - camera
    forward = forward / np.linalg.norm(forward)
    right = np.cross(forward, np.array([0.0, 1.0, 0.0]))
    right = right / np.linalg.norm(right)
    up = np.cross(right, forward)

    view = (points - camera) @ np.stack([right, up, forward], axis=1)
    depth = view[:, 2]
    # fov vertical: la app usa PerspectiveCamera(45, aspecto, ...) con fov vertical
    scale = SIZE[1] / (2.0 * np.tan(np.radians(fov) / 2.0))
    x = SIZE[0] / 2.0 + view[:, 0] * scale / np.maximum(depth, 1e-6)
    y = SIZE[1] / 2.0 - view[:, 1] * scale / np.maximum(depth, 1e-6)
    return np.stack([x, y], axis=1), depth


# ── Geometría ─────────────────────────────────────────────────────────────────

def load_primitives(js, binary):
    """[(POSITION, JOINTS_0, WEIGHTS_0, triángulos, node_index)] de cada primitiva."""
    out = []
    for node_index, node in enumerate(js.get("nodes", [])):
        if "mesh" not in node:
            continue
        for prim in js["meshes"][node["mesh"]]["primitives"]:
            attrs = prim["attributes"]
            if "JOINTS_0" not in attrs:
                continue
            count = js["accessors"][attrs["POSITION"]]["count"]
            indices = (np.array(gp.read_accessor(js, binary, prim["indices"]),
                                dtype=np.int32).reshape(-1, 3)
                       if "indices" in prim else
                       np.arange(count, dtype=np.int32).reshape(-1, 3))
            out.append((
                np.array(gp.read_accessor(js, binary, attrs["POSITION"]),
                         dtype=np.float64).reshape(-1, 3),
                np.array(gp.read_accessor(js, binary, attrs["JOINTS_0"]),
                         dtype=np.int32).reshape(-1, 4),
                np.array(gp.read_accessor(js, binary, attrs["WEIGHTS_0"]),
                         dtype=np.float64).reshape(-1, 4),
                indices,
                node_index,
            ))
    return out


def skinning_matrices(js, binary, pose):
    """`skinMatrix[j] = mundo(hueso j) · IBM[j]`, la LBS de three.js."""
    joints = gp.skin_joints(js)
    binds = gp.inverse_binds(js, binary)
    world = gp.world_poses(js, pose)
    out = np.zeros((len(joints), 4, 4))
    for k, index in enumerate(joints):
        bone = world[js["nodes"][index].get("name")][3]
        out[k] = np.array(bone) @ np.array(binds[k])
    return out


def mesh_bbox(js, primitives):
    """El bbox de la malla **sin** skinning, que es lo que mide `app.js`.

    `Box3.expandByObject` de three.js aplica la matriz del nodo, no la del
    esqueleto: por eso cambiar la pose de los huesos no lo mueve, y por eso el
    encuadre no cambia al hornear la pose de descanso.
    """
    local = np.array(gp.local_matrices(js))
    lo = np.array([1e9, 1e9, 1e9])
    hi = -lo
    for position, _j, _w, _t, node_index in primitives:
        m = local[node_index]
        points = position @ m[:3, :3].T + m[:3, 3]
        lo = np.minimum(lo, points.min(axis=0))
        hi = np.maximum(hi, points.max(axis=0))
    return lo, hi


def posed_positions(primitives, skin):
    """Aplica la skinning lineal: `p' = Σ wⱼ · skin[j] · p`."""
    out = []
    for position, joints, weights, _indices, _node in primitives:
        result = np.zeros_like(position)
        # Aplanado (vértice, hueso, peso) para agrupar por hueso y hacer una
        # sola multiplicación por cada uno: recorrer los huesos con una máscara
        # por vértice es O(n · huesos) y tarda minutos.
        vertex, slot = np.nonzero(weights)
        bone = joints[vertex, slot]
        weight = weights[vertex, slot]
        order = np.argsort(bone, kind="stable")
        vertex, bone, weight = vertex[order], bone[order], weight[order]
        edges = np.searchsorted(bone, np.arange(len(skin) + 1))
        for j in range(len(skin)):
            lo, hi = edges[j], edges[j + 1]
            if hi <= lo:
                continue
            rows = vertex[lo:hi]
            homo = np.concatenate([position[rows], np.ones((hi - lo, 1))], axis=1)
            result[rows] += (homo @ skin[j].T)[:, :3] * weight[lo:hi, None]
        out.append(result)
    return out


# ── Render ────────────────────────────────────────────────────────────────────

def render(js, binary, pose, label="", zoom=1.0, lift=0.0):
    primitives = load_primitives(js, binary)
    skin = skinning_matrices(js, binary, pose)
    points = posed_positions(primitives, skin)

    lo, hi = mesh_bbox(js, primitives)
    scale = 1.7 / (hi[1] - lo[1])           # el pivot de app.js: alto 1.7, piso en y=0
    center = (hi + lo) / 2.0
    offset = np.array([-center[0] * scale, -lo[1] * scale, -center[2] * scale])

    camera, target, fov = app_camera()
    target = target + np.array([0.0, lift, 0.0])
    image = Image.new("RGB", SIZE, BG)
    draw = ImageDraw.Draw(image)

    triangles = []
    for (position, joints, weights, indices, _node), posed in zip(primitives, points):
        p = posed * scale + offset
        for face in indices:
            triangles.append((p[face[0]], p[face[1]], p[face[2]]))

    # Pintor: de atrás hacia adelante, con plano hacia la cámara descartado.
    light = np.array([0.4, 0.6, 0.7])
    light /= np.linalg.norm(light)
    batch = []
    for a, b, c in triangles:
        normal = np.cross(b - a, c - a)
        area = np.linalg.norm(normal)
        if area < 1e-12:
            continue
        normal = normal / area
        if normal @ (camera - a) <= 0:
            continue                                    # cara de atrás
        lambert = 0.25 + 0.75 * max(0.0, float(normal @ light))
        shade = tuple(int(min(255, channel * lambert)) for channel in SHADE)
        depth = float((a + b + c)[1:].sum())
        batch.append((depth, a, b, c, shade))
    batch.sort(key=lambda t: t[0])

    for _depth, a, b, c, shade in batch:
        pixels, _depth = project(np.stack([a, b, c]), camera, target, fov * zoom)
        pa, pb, pc = pixels
        draw.polygon([tuple(pa), tuple(pb), tuple(pc)], fill=shade)

    if label:
        ImageDraw.Draw(image).text((8, SIZE[1] - 18), label, fill=(140, 150, 170))
    return image


# ── Poses ─────────────────────────────────────────────────────────────────────

def pose_rest(js, binary):
    """La pose que tiene el archivo ahora mismo."""
    return None


def pose_bind(js, binary):
    """La pose de bind: el reposo anterior a hornear la pose de descanso."""
    return gp.bind_pose(js, binary)


def reference_frame(signs_dir=WEB / "signs"):
    """El frame de referencia de `rest_pose`, tal cual sale del clip."""
    sjs, sbin = gp.read_glb(Path(signs_dir) / rest_pose.REFERENCE)
    variant = rest_pose.REFERENCE_VARIANT
    return gp.sample_clip(sjs, sbin, variant, gp.first_keyframe_time(sjs, variant))


def pose_ref(js, binary):
    """El frame de referencia completo (brazos + torso + cara), sin hornear."""
    return reference_frame()


def pose_ref_arms(js, binary):
    """Solo la cadena de brazos del frame de referencia: lo que se hornea."""
    frame = reference_frame()
    return {name: frame[name] for name in rest_pose.arm_chain(js)}


def mirror(pose):
    """Espeja `.L` sobre `.R`: `q_R = (x, −y, −z, w)` respecto de `q_L`.

    La regla vale **en la pose de bind** y no en cualquier pose: promediar una
    pose de animación con su espejo cae fuera del rango para el que la malla
    fue tejida y aparecen costuras abiertas. Está en el render solo para dejar
    a la vista por qué está descartada.
    """
    out = dict(pose or {})
    for name in list(out):
        if name.endswith(".L") and name[:-2] + ".R" in out:
            t, q, s = out[name]
            out[name[:-2] + ".R"] = (t, (-q[0], q[1], q[2], -q[3]), s)
    return out


def average_starts(js, binary, signs_dir=WEB / "signs"):
    """El promedio de los primeros keyframes de todos los clips de `signs/`.

    Se alinea el signo de cada cuaternión contra la pose de bind antes de
    promediar (q y −q son el mismo giro: sin alinear, se cancelan entre sí y el
    promedio da cualquier cosa). También está descartado: sale asimétrico y con
    la cabeza girada.
    """
    bind = {name: np.array(trs[1]) for name, trs in gp.world_poses(js).items()}
    accumulated, count = None, 0
    for path in sorted(Path(signs_dir).glob("*.glb")):
        sjs, sbin = gp.read_glb(path)
        for variant in range(len(sjs["animations"])):
            frame = gp.sample_clip(sjs, sbin, variant,
                                   gp.first_keyframe_time(sjs, variant))
            if accumulated is None:
                accumulated = {name: [np.array(trs[0]), np.array(trs[1]),
                                      np.array(trs[2])] for name, trs in frame.items()}
            else:
                for name, (t, q, s) in frame.items():
                    if name not in accumulated:
                        continue
                    q = np.array(q)
                    if name in bind and float(q @ bind[name]) < 0:
                        q = -q
                    accumulated[name][0] += np.array(t)
                    accumulated[name][1] += q
                    accumulated[name][2] += np.array(s)
            count += 1

    out = {}
    for name, (t, q, s) in accumulated.items():
        norm = np.linalg.norm(q) or 1.0
        out[name] = (tuple(t / count), tuple(q / norm), tuple(s / count))
    return out


POSES = {
    "rest": (pose_rest, "B  el archivo, ya horneado"),
    "bind": (pose_bind, "A  reposo anterior"),
    "ref": (pose_ref, "0029 keyframe 0 completo"),
    "arms": (pose_ref_arms, "0029 solo brazos"),
    "avg": (average_starts, "C  promedio de los inicios"),
    "mirror": (lambda js, b: mirror(pose_ref_arms(js, b)), "D  espejado"),
}
SHEET = ("bind", "rest", "avg", "mirror")


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--avatar", type=Path, default=AVATAR)
    parser.add_argument("--pose", default="rest", choices=sorted(POSES))
    parser.add_argument("--sheet", action="store_true",
                        help="las cuatro poses de la comparación, en una tira")
    parser.add_argument("--closeup", action="store_true",
                        help="encuadre cerrado del torso (--sheet)")
    parser.add_argument("--out", type=Path, default=REPO / "preview.png")
    args = parser.parse_args()

    js, binary = gp.read_glb(args.avatar)

    if args.sheet:
        images = []
        for name in SHEET:
            fn, label = POSES[name]
            images.append(render(js, binary, fn(js, binary), label,
                                 zoom=0.5 if args.closeup else 1.0,
                                 lift=0.45 if args.closeup else 0.0))
        sheet = Image.new("RGB", (SIZE[0] * len(images), SIZE[1]), BG)
        for i, image in enumerate(images):
            sheet.paste(image, (i * SIZE[0], 0))
        sheet.save(args.out)
        print(f"  {args.out}  ({sheet.size[0]}x{sheet.size[1]})")
        return

    fn, label = POSES[args.pose]
    image = render(js, binary, fn(js, binary), label)
    image.save(args.out)
    print(f"  {args.out}  ({SIZE[0]}x{SIZE[1]})  {label}")


if __name__ == "__main__":
    main()
