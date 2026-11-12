#!/usr/bin/env python3
"""Marcadores no manuales por seña, medidos sobre el intérprete de LSC50.

Los clips del avatar traen las manos y los brazos del intérprete, pero casi nada
de lo demás: la cabeza se mueve 2°–4° donde el intérprete la mueve 10° o más, y
la cara es una pose fija por tipo de oración. Este script recupera lo que falta
**del mismo video del que salió la seña**, sin inventar gestos:

  * `FACE_LANDMARKS/<id>_0000_0000.csv` — 468 puntos de la cara por fotograma
    (cámara de cara, 50 fps). De ahí salen el giro de la cabeza y, una vez
    quitado ese giro, la boca, las cejas, los ojos y las mejillas.
  * `BODY_LANDMARKS/<id>_0000_0000.csv` — 33 puntos del cuerpo por fotograma
    (cámara de cuerpo). De ahí salen la inclinación lateral del tronco y, con las
    muñecas, **en qué tramo del video está el clip**.

Los clips no conservan el reloj del video (se recortaron en Cascadeur y las
entradas y regresos aprobados cambiaron los extremos), así que cada seña se
alinea: se busca el desplazamiento que mejor hace coincidir la altura de las
muñecas del clip con la de las muñecas del video. Las dos cámaras cubren el
mismo intervalo, así que el tiempo de la cámara de cara se toma proporcional.

Escribe `assets/web/nmm.json`, que `humanize.js` aplica **encima** del clip en
tiempo de ejecución. Los `.glb` no se tocan.

Uso:
    python3 tools/extract_nmm.py [--landmarks DIR] [--dry-run] [--report]

Necesita numpy. No necesita MediaPipe: los puntos ya vienen en el dataset.
"""
import argparse
import csv
import json
import struct
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
WEB = ROOT / "app/src/main/assets/web"
DEFAULT_LANDMARKS = ROOT.parent / "Prototipo Tesis/data/LANDMARKS"
TAKE = "0000_0000"          # voluntario 0, cámara 0: la toma de la que salió el clip
FPS = 24                    # reloj de los clips
FACE_W, FACE_H = 640, 424   # cámara de cara
BODY_W, BODY_H = 1920, 1080

# Puntos de la malla de MediaPipe que no se mueven con la expresión.
STABLE = [10, 151, 9, 168, 6, 197, 195, 4, 33, 133, 263, 362, 127, 356, 234, 454]

# Canales del archivo, en orden. Los ángulos van en décimas de grado y los pesos
# en centésimas: enteros chicos, que comprimen bien dentro del APK.
CHANNELS = [
    "headPitch", "headYaw", "headRoll", "torsoRoll",
    "jawOpen", "mouthSmile_L", "mouthSmile_R", "mouthFrown", "mouthPucker",
    "mouthFunnel", "mouthStretch", "browInnerUp", "browOuterUp_L",
    "browOuterUp_R", "browDown", "eyeBlink", "eyeWide", "cheekSquint",
]
ANGLES = {"headPitch", "headYaw", "headRoll", "torsoRoll"}

# Topes: un punto mal detectado (una mano delante de la cara) no debe torcer el
# cuello del avatar.
HEAD_LIMIT = 16.0
TORSO_LIMIT = 6.0


# ── Lectura ───────────────────────────────────────────────────────────────────

def load_csv(path):
    with open(path, newline="") as fh:
        rows = list(csv.reader(fh))[1:]
    data = np.array([[float(x) if x else np.nan for x in r[1:]] for r in rows])
    return data.reshape(len(rows), -1, 3)


class Glb:
    def __init__(self, path):
        raw = Path(path).read_bytes()
        length = struct.unpack("<I", raw[12:16])[0]
        self.js = json.loads(raw[20:20 + length])
        self.bin = raw[20 + length + 8:]
        self.names = [n.get("name") for n in self.js["nodes"]]
        self.parent = {}
        for i, node in enumerate(self.js["nodes"]):
            for child in node.get("children", []):
                self.parent[child] = i

    def accessor(self, index):
        acc = self.js["accessors"][index]
        view = self.js["bufferViews"][acc["bufferView"]]
        offset = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
        width = {"SCALAR": 1, "VEC3": 3, "VEC4": 4}[acc["type"]]
        return np.frombuffer(self.bin, np.float32, acc["count"] * width, offset) \
            .reshape(acc["count"], width)

    def clip(self, name):
        return next(a for a in self.js["animations"] if a["name"] == name)

    def world_positions(self, clip_name, bones, fps=FPS):
        """Posición de mundo de `bones` en cada fotograma del clip (FK)."""
        anim = self.clip(clip_name)
        tracks = {}
        duration = 0.0
        for ch in anim["channels"]:
            path = ch["target"]["path"]
            if path not in ("rotation", "translation"):
                continue
            sampler = anim["samplers"][ch["sampler"]]
            times = self.accessor(sampler["input"])[:, 0]
            tracks[(ch["target"]["node"], path)] = (times, self.accessor(sampler["output"]))
            duration = max(duration, float(times[-1]))
        count = int(round(duration * fps)) + 1
        clock = np.arange(count) / fps

        def sampled(node, path, default):
            if (node, path) in tracks:
                times, values = tracks[(node, path)]
                out = np.stack([np.interp(clock, times, values[:, k])
                                for k in range(values.shape[1])], 1)
            else:
                out = np.tile(np.array(self.js["nodes"][node].get(path, default), float),
                              (count, 1))
            if path == "rotation":
                out /= np.linalg.norm(out, axis=1, keepdims=True)
            return out

        cache = {}

        def world(node):
            if node in cache:
                return cache[node]
            rot = quat_matrices(sampled(node, "rotation", [0, 0, 0, 1]))
            pos = sampled(node, "translation", [0, 0, 0])
            if node in self.parent:
                prot, ppos = world(self.parent[node])
                pos = ppos + np.einsum("nij,nj->ni", prot, pos)
                rot = prot @ rot
            cache[node] = (rot, pos)
            return cache[node]

        return duration, {b: world(self.names.index(b))[1] for b in bones}


def quat_matrices(q):
    x, y, z, w = q.T
    return np.stack([
        np.stack([1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)], 1),
        np.stack([2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)], 1),
        np.stack([2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)], 1),
    ], 1)


# ── Geometría ─────────────────────────────────────────────────────────────────

def kabsch(src, dst):
    """Semejanza (R, s, t) con dst ≈ s·R·src + t, y el residuo medio."""
    sc, dc = src.mean(0), dst.mean(0)
    a, b = src - sc, dst - dc
    u, sing, vt = np.linalg.svd(a.T @ b)
    sign = np.sign(np.linalg.det(vt.T @ u.T))
    rot = vt.T @ np.diag([1, 1, sign]) @ u.T
    scale = (sing * [1, 1, sign]).sum() / (a ** 2).sum()
    shift = dc - scale * rot @ sc
    resid = np.sqrt((((scale * (rot @ src.T).T + shift) - dst) ** 2).sum(1).mean())
    return rot, scale, shift, resid


def euler_yxz(rot):
    """Ángulos (cabeceo, giro, ladeo) en grados con R = Ry·Rx·Rz."""
    pitch = np.arcsin(np.clip(-rot[1, 2], -1, 1))
    yaw = np.arctan2(rot[0, 2], rot[2, 2])
    roll = np.arctan2(rot[1, 0], rot[1, 1])
    return np.degrees([pitch, yaw, roll])


def smooth(series, sigma):
    """Gaussiano con bordes reflejados; `series` es (n,) o (n, k)."""
    if sigma <= 0 or len(series) < 3:
        return series
    radius = max(1, int(3 * sigma))
    kernel = np.exp(-0.5 * (np.arange(-radius, radius + 1) / sigma) ** 2)
    kernel /= kernel.sum()
    arr = np.asarray(series, float)
    flat = arr.reshape(len(arr), -1)
    padded = np.pad(flat, ((radius, radius), (0, 0)), mode="edge")
    out = np.stack([np.convolve(padded[:, k], kernel, mode="valid")
                    for k in range(flat.shape[1])], 1)
    return out.reshape(arr.shape)


def fill_gaps(values, valid):
    """Interpola los fotogramas no válidos entre sus vecinos válidos."""
    if valid.all() or not valid.any():
        return values
    idx = np.arange(len(values))
    flat = values.reshape(len(values), -1).copy()
    for k in range(flat.shape[1]):
        flat[~valid, k] = np.interp(idx[~valid], idx[valid], flat[valid, k])
    return flat.reshape(values.shape)


def clamp01(x):
    return np.clip(x, 0.0, 1.0)


# ── Cara ──────────────────────────────────────────────────────────────────────

def face_features(landmarks):
    """Canales de cabeza y cara por fotograma de la cámara de cara."""
    # Mundo del avatar: x a la derecha de la imagen, y arriba, z hacia la cámara.
    pts = np.stack([landmarks[..., 0] * FACE_W, -landmarks[..., 1] * FACE_H,
                    -landmarks[..., 2] * FACE_W], -1)
    count = len(pts)
    # Reposo del intérprete: los primeros fotogramas, antes de levantar las manos.
    ref = np.median(pts[:max(3, min(8, count // 6))], 0)
    iod = np.linalg.norm(ref[33] - ref[263])

    angles = np.zeros((count, 3))
    flat = np.zeros_like(pts)      # la cara sin el giro de la cabeza
    resid = np.zeros(count)
    for i, frame in enumerate(pts):
        rot, _, _, _ = kabsch(ref[STABLE], frame[STABLE])
        angles[i] = euler_yxz(rot)
        back, scale, shift, resid[i] = kabsch(frame[STABLE], ref[STABLE])
        flat[i] = scale * (back @ frame.T).T + shift
    # Una mano delante de la cara deforma la malla: el ajuste rígido deja de
    # cerrar. Esos fotogramas se descartan y se interpolan.
    valid = resid < max(0.035 * iod, 2.5 * np.median(resid))
    angles = fill_gaps(angles, valid)
    flat = fill_gaps(flat, valid)
    angles = smooth(angles, 2.0)
    flat = smooth(flat, 1.2)

    y = flat[..., 1] / iod
    x = flat[..., 0] / iod
    ry, rx = ref[:, 1] / iod, ref[:, 0] / iod

    def dist(a, b, source=flat):
        return np.linalg.norm(source[..., a, :] - source[..., b, :], axis=-1) / iod

    # Boca. 13/14 son el borde interior de los labios; 61/291 las comisuras.
    gap = dist(13, 14) - np.linalg.norm(ref[13] - ref[14]) / iod
    width = dist(61, 291) / (np.linalg.norm(ref[61] - ref[291]) / iod) - 1.0
    center = (y[:, 0] + y[:, 17]) / 2
    ref_center = (ry[0] + ry[17]) / 2
    # Cuánto sube cada comisura respecto del centro de la boca. La 61 queda a la
    # izquierda de la imagen: es el lado derecho del intérprete y del avatar.
    corner_r = (y[:, 61] - center) - (ry[61] - ref_center)
    corner_l = (y[:, 291] - center) - (ry[291] - ref_center)
    jaw = clamp01(gap / 0.30)
    wide = clamp01(width / 0.22)
    smile_r = clamp01(np.maximum(corner_r / 0.07, 0) * 0.7 + wide * 0.5)
    smile_l = clamp01(np.maximum(corner_l / 0.07, 0) * 0.7 + wide * 0.5)
    lift = np.maximum(corner_r, corner_l)
    stretch = clamp01(wide - np.maximum(lift / 0.05, 0)) * 0.7
    frown = clamp01(-(corner_r + corner_l) / 2 / 0.06)
    narrow = clamp01(-width / 0.16)
    funnel = narrow * clamp01(gap / 0.10)
    pucker = narrow * (1 - clamp01(gap / 0.10))

    # Cejas, medidas contra la línea de los ojos del mismo lado.
    def brow(points, eye):
        height = y[:, points].mean(1) - y[:, eye].mean(1)
        return height - (ry[points].mean() - ry[eye].mean())

    inner = (brow([107], [133, 33]) + brow([336], [362, 263])) / 2
    outer_r = brow([70, 63], [33, 133])
    outer_l = brow([300, 293], [263, 362])
    mid = (brow([105, 66], [33, 133]) + brow([334, 296], [263, 362])) / 2
    brow_in = clamp01(inner / 0.075)
    brow_out_r = clamp01(outer_r / 0.065)
    brow_out_l = clamp01(outer_l / 0.065)
    brow_down = clamp01(-mid / 0.05)

    # Ojos: apertura del párpado sobre el ancho del ojo, contra el propio
    # intérprete con los ojos abiertos.
    ear = (dist(159, 145) / dist(33, 133) + dist(386, 374) / dist(263, 362)) / 2
    open_ref = np.percentile(ear, 75)
    ratio = ear / open_ref
    blink = clamp01((0.78 - ratio) / 0.45)
    eye_wide = clamp01((ratio - 1.12) / 0.30)
    cheek = clamp01((smile_r + smile_l) / 2 * 0.7)

    return {
        "headPitch": angles[:, 0], "headYaw": angles[:, 1], "headRoll": angles[:, 2],
        "jawOpen": jaw, "mouthSmile_L": smile_l, "mouthSmile_R": smile_r,
        "mouthFrown": frown, "mouthPucker": pucker, "mouthFunnel": funnel,
        "mouthStretch": stretch, "browInnerUp": brow_in,
        "browOuterUp_L": brow_out_l, "browOuterUp_R": brow_out_r,
        "browDown": brow_down, "eyeBlink": blink, "eyeWide": eye_wide,
        "cheekSquint": cheek,
    }, float(1 - valid.mean())


# ── Cuerpo y alineación ───────────────────────────────────────────────────────

def body_features(landmarks):
    """Inclinación lateral del tronco y altura de las muñecas, por fotograma."""
    px = landmarks[..., 0] * BODY_W
    py = -landmarks[..., 1] * BODY_H
    shoulders = np.stack([(px[:, 11] + px[:, 12]) / 2, (py[:, 11] + py[:, 12]) / 2], 1)
    hips = np.stack([(px[:, 23] + px[:, 24]) / 2, (py[:, 23] + py[:, 24]) / 2], 1)
    spine = shoulders - hips
    # Positivo = el tronco cae hacia la derecha de la imagen (+x del avatar).
    lean = np.degrees(np.arctan2(spine[:, 0], spine[:, 1]))
    lean = smooth(lean - np.median(lean[:max(3, len(lean) // 8)]), 1.2)
    scale = np.linalg.norm(spine, axis=1).mean()
    wrists = {
        # 15 = muñeca izquierda del intérprete, 16 = derecha.
        "L": (py[:, 15] - hips[:, 1]) / scale,
        "R": (py[:, 16] - hips[:, 1]) / scale,
    }
    return -lean, wrists   # giro alrededor de +z: caer a +x es ángulo negativo


def align(clip_wrists, video_wrists):
    """Fotograma del video que corresponde a cada fotograma del clip.

    Devuelve (velocidad, desplazamiento, correlación). Se prueban pocas
    velocidades alrededor de 1: los clips son recortes del video, no versiones
    estiradas, así que 1 gana salvo que otra sea claramente mejor.
    """
    clip_len = len(clip_wrists["L"])
    video_len = len(video_wrists["L"])
    best = (1.0, 0.0, -2.0)
    for rate in (1.0, 0.9, 1.1, 0.8, 1.25, 1.4):
        penalty = 0.0 if rate == 1.0 else 0.06
        for offset in np.arange(-clip_len * 0.6, video_len, 0.5):
            where = rate * np.arange(clip_len) + offset
            inside = (where >= 0) & (where <= video_len - 1)
            if inside.sum() < 0.6 * clip_len:
                continue
            scores = []
            for side in ("L", "R"):
                a = clip_wrists[side][inside]
                b = np.interp(where[inside], np.arange(video_len), video_wrists[side])
                if a.std() < 0.01 or b.std() < 0.01:
                    continue
                scores.append(np.corrcoef(a, b)[0, 1] * min(a.std(), b.std()))
            if not scores:
                continue
            weight = sum(min(clip_wrists[s][inside].std(),
                             np.interp(where[inside], np.arange(video_len),
                                       video_wrists[s]).std())
                         for s in ("L", "R")
                         if clip_wrists[s][inside].std() >= 0.01)
            score = sum(scores) / max(weight, 1e-6) - penalty
            if score > best[2]:
                best = (rate, float(offset), float(score))
    return best


# ── Empaquetado ───────────────────────────────────────────────────────────────

def encode(name, values):
    if name in ANGLES:
        return [int(round(v * 10)) for v in values]
    return [int(round(v * 100)) for v in values]


def build(landmarks_dir, glb_path, report=False):
    glb = Glb(glb_path)
    signs = {}
    rows = []
    for index in range(50):
        sid = f"{index:04d}"
        face_csv = landmarks_dir / "FACE_LANDMARKS" / f"{sid}_{TAKE}.csv"
        body_csv = landmarks_dir / "BODY_LANDMARKS" / f"{sid}_{TAKE}.csv"
        if not face_csv.exists() or not body_csv.exists():
            rows.append((sid, "sin landmarks"))
            continue
        duration, hands = glb.world_positions(f"{sid}_afirmativo", ["hand.L", "hand.R", "spine"])
        hip = hands["spine"][:, 1]
        torso = 0.55  # largo cadera–hombros del avatar, para la misma unidad
        clip_wrists = {s: (hands[f"hand.{s}"][:, 1] - hip) / torso for s in ("L", "R")}
        lean, video_wrists = body_features(load_csv(body_csv))
        rate, offset, score = align(clip_wrists, video_wrists)

        face, dropped = face_features(load_csv(face_csv))
        frames = len(clip_wrists["L"])
        body_len = len(lean)
        face_len = len(face["jawOpen"])
        body_at = np.clip(rate * np.arange(frames) + offset, 0, body_len - 1)
        face_at = body_at / (body_len - 1) * (face_len - 1)

        channels = {}
        for name in CHANNELS:
            if name == "torsoRoll":
                series = np.interp(body_at, np.arange(body_len), lean)
                series = np.clip(series, -TORSO_LIMIT, TORSO_LIMIT)
            else:
                series = np.interp(face_at, np.arange(face_len), face[name])
                if name in ANGLES:
                    series = np.clip(series, -HEAD_LIMIT, HEAD_LIMIT)
            # El clip entra y sale por el reposo del avatar, no por el del
            # intérprete: los extremos se llevan a cero para que la capa no
            # deje la cabeza girada cuando el cuerpo ya volvió.
            edge = min(6, frames // 4)
            window = np.ones(frames)
            ramp = 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, edge))
            window[:edge] = ramp
            window[-edge:] = ramp[::-1]
            if name != "eyeBlink":
                series = series * window
            channels[name] = encode(name, series)
        # Un canal que nunca se mueve no viaja.
        channels = {k: v for k, v in channels.items() if any(abs(x) > 2 for x in v)}
        signs[sid] = {"n": frames, "c": channels}
        peak = {k: max(abs(x) for x in v) for k, v in channels.items()}
        rows.append((sid, frames, body_len, rate, offset, score, dropped, peak))

    if report:
        print("seña  clip  video  vel   desfase  corr   cara-desc  picos")
        for row in rows:
            if len(row) == 2:
                print(row[0], row[1])
                continue
            sid, frames, body_len, rate, offset, score, dropped, peak = row
            head = "/".join(f"{peak.get(k, 0) / 10:.0f}" for k in ("headPitch", "headYaw", "headRoll"))
            face = " ".join(f"{k[:9]}={peak[k]}" for k in
                            ("jawOpen", "mouthSmile_L", "mouthPucker", "browInnerUp", "browDown", "eyeBlink")
                            if k in peak)
            print(f"{sid}  {frames:4d}  {body_len:5d}  {rate:.2f}  {offset:7.1f}  {score:5.2f}  "
                  f"{dropped * 100:5.1f}%    cabeza {head}°  {face}")
    return {"version": 1, "fps": FPS, "take": TAKE,
            "units": {"angles": "decimas de grado", "weights": "centesimas"},
            "signs": signs}, rows


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--landmarks", type=Path, default=DEFAULT_LANDMARKS)
    parser.add_argument("--glb", type=Path, default=WEB / "models/juan.glb")
    parser.add_argument("--out", type=Path, default=WEB / "nmm.json")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--report", action="store_true")
    args = parser.parse_args()

    data, rows = build(args.landmarks, args.glb, report=args.report or args.dry_run)
    done = sum(1 for r in rows if len(r) > 2)
    text = json.dumps(data, separators=(",", ":"))
    print(f"{done}/50 señas · {len(text) / 1024:.0f} KB")
    if not args.dry_run:
        args.out.write_text(text + "\n")
        print("escrito", args.out.relative_to(ROOT))


if __name__ == "__main__":
    main()
