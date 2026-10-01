#!/usr/bin/env python3
"""
Genera los assets de seña que la app carga desde `assets/web/signs/`.

Cada asset es un JSON con la misma forma que consume `app.js`:

    { "stem": "...", "fps": 24, "total_frames": N, "frames": [ ... ] }

y cada frame trae `hands`, `body` y, opcionalmente, `face`, más dos campos
que **no** son landmarks sino el estado del corrector:

    "_corrector_state":   "NORMAL" | "CONTACT" | "HOLD" | "BLEND"
    "_hand_span_ratio":   {"Left": 0.98, "Right": 1.01}

`app.js` los lee para dos cosas que sin ellos no tienen con qué trabajar:
el `CONTACT_BLEND` de las muñecas, y el deadzone de la falange distal
(`HAND_SPAN_DEGRADED_RATIO`, que compara el span de este frame contra el
baseline EMA del corrector). Sin estos dos campos, la mitad de las
mejoras de movimiento quedan dormidas.

## De dónde salen los datos

Los CSVs de LSC50 (`BODY_LANDMARKS`, `HANDS_LANDMARKS/{LEFT,RIGHT}_HAND_LANDMARKS`,
`FACE_LANDMARKS`), uno por vídeo `{seña}_{repetición}_{ángulo}`. Se pueden apuntar
con `--data`, o con la variable `SIGNAI_LSC50`. Por defecto se busca en
`../SignAI/data/LANDMARKS` y en `~/Descargas/LSC50/LANDMARKS`.

## Correcciones aplicadas (por eso este script existe)

`pose_filter` (One-Euro) sobre el cuerpo: la muñeca cruda tiene ~12× el
jitter del hombro y ese ruido entra directo al IK de brazo.
`HandCorrector` (CONTACT/HOLD/BLEND + One-Euro) sobre las manos: la mano
ocupa ~3% del frame, así que las falanges quedan al nivel del ruido de
MediaPipe.

Ambas son correcciones **offline**: se hornean en el JSON una vez y el
WebView no paga ningún filtro en runtime. Por eso van aquí y no en el
backend — el backend no tiene (todavía) una ruta de assets de señas.

## Uso

    # una seña concreta, para probar
    python3 tools/build_signs.py --sign 0000_0000_0000

    # las 50 señas del vocabulario, un vídeo cada una (rep 0, ángulo 0)
    python3 tools/build_signs.py --vocab --rep 0 --angle 0

    # ver qué hay disponible
    python3 tools/build_signs.py --list
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import pathlib
import sys
from concurrent.futures import ProcessPoolExecutor

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from hand_corrector import HandCorrector  # noqa: E402
from pose_filter import PoseFilter  # noqa: E402

REPO = pathlib.Path(__file__).resolve().parent.parent
OUT_DIR = REPO / "app/src/main/assets/web/signs"

# Subcarpetas de FACE_LANDMARKS: el LSC50 trae una con sufijo de versión.
FACE_SUBDIRS = ("", "1", "2")

DEFAULT_ROOTS = (
    REPO.parent / "data/LANDMARKS",
    pathlib.Path.home() / "Descargas/LSC50/LANDMARKS",
)


def find_data_root(explicit: str | None = None) -> pathlib.Path:
    """Localiza `LANDMARKS/`. Explícito > env > rutas por defecto."""
    candidates = []
    if explicit:
        candidates.append(pathlib.Path(explicit))
    env = os.getenv("SIGNAI_LSC50")
    if env:
        candidates.append(pathlib.Path(env))
    candidates.extend(DEFAULT_ROOTS)

    for path in candidates:
        # Se acepta el propio LANDMARKS o su contenedor.
        if (path / "BODY_LANDMARKS").is_dir():
            return path
        if path.name == "LANDMARKS" and (path / "BODY_LANDMARKS").is_dir():
            return path
    tried = "\n  ".join(str(c) for c in candidates)
    raise SystemExit(
        "No encontré los CSV de LSC50. Probé:\n  " + tried +
        "\nPasalo con --data RUTA o SIGNAI_LSC50=RUTA"
    )


# ── Parsing de CSV ────────────────────────────────────────────────────────────

def _rows(path: pathlib.Path) -> list[dict]:
    with path.open(newline="") as handle:
        return list(csv.DictReader(handle))


def _point(row: dict, index: int) -> dict:
    return {
        "x": float(row.get(f"landmark_{index}_x", 0)),
        "y": float(row.get(f"landmark_{index}_y", 0)),
        "z": float(row.get(f"landmark_{index}_z", 0)),
    }


def parse_hand_csv(path: pathlib.Path, label: str) -> list[dict]:
    """21 landmarks de mano por frame → [{"hand", "landmarks"}, ...]."""
    if not path.exists():
        return []
    frames = []
    for row in _rows(path):
        frames.append({
            "hand": label,
            "landmarks": [_point(row, i) for i in range(21)],
        })
    return frames


def parse_body_csv(path: pathlib.Path) -> list[list[dict]]:
    """33 landmarks de cuerpo (MediaPipe Pose) por frame."""
    if not path.exists():
        return []
    return [[_point(row, i) for i in range(33)] for row in _rows(path)]


def parse_face_csv(path: pathlib.Path) -> list[dict]:
    """
    Subconjunto de FaceMesh que usa la animación de cara.

    Este avatar no tiene huesos de cara (el rig es AutoRigPro, 68 huesos), así
    que `app.js` ya no lee `frame.face`. El parseo se conserva para no perder
    el dato: si algún día se vuelve a poner un rig con cara, está todo aquí.
    """
    if not path.exists():
        return []
    keys = [13, 14, 78, 308, 70, 107, 55, 300, 336, 285, 159, 145, 386, 374, 1, 168]
    return [{str(i): _point(row, i) for i in keys} for row in _rows(path)]


def face_path(data_root: pathlib.Path, stem: str) -> pathlib.Path | None:
    base = data_root / "FACE_LANDMARKS"
    for sub in FACE_SUBDIRS:
        candidate = base / sub / f"{stem}.csv" if sub else base / f"{stem}.csv"
        if candidate.exists():
            return candidate
    return None


# ── Construcción del asset ────────────────────────────────────────────────────

def build_stem(data_root: pathlib.Path, stem: str, with_face: bool = True) -> dict | None:
    """Arma el JSON de una seña, con las correcciones ya aplicadas."""
    left = parse_hand_csv(
        data_root / "HANDS_LANDMARKS/LEFT_HAND_LANDMARKS" / f"{stem}.csv", "Left")
    right = parse_hand_csv(
        data_root / "HANDS_LANDMARKS/RIGHT_HAND_LANDMARKS" / f"{stem}.csv", "Right")
    body = parse_body_csv(data_root / "BODY_LANDMARKS" / f"{stem}.csv")

    n = max(len(left), len(right))
    if n == 0:
        return None

    # Filtro de cuerpo ANTES del IK de brazos. Crudo, el jitter de la muñeca es
    # ~12× el del hombro (medido sobre las 50 señas, ver pose_filter.py).
    if body:
        pose_filter = PoseFilter()
        body = [pose_filter.apply(frame) for frame in body]

    face = parse_face_csv(face_path(data_root, stem)) if with_face else []

    frames: list[dict] = []
    for i in range(n):
        hands = []
        if i < len(left):
            hands.append(left[i])
        if i < len(right):
            hands.append(right[i])
        frame = {"hands": hands}
        if i < len(body):
            frame["body"] = body[i]
        # Cara viene a otra tasa (163 frames vs 59): se re-muestrea por índice.
        if face and body:
            idx = round(i * len(face) / len(body))
            if idx < len(face):
                frame["face"] = face[idx]
        elif i < len(face):
            frame["face"] = face[i]
        frames.append(frame)

    # Corrección de manos: CONTACT/HOLD/BLEND + One-Euro. Devuelve los frames
    # con `_corrector_state` y `_hand_span_ratio` ya puestos.
    corrected, stats = HandCorrector().process_sequence(frames)

    return {
        "stem": stem,
        "fps": 24,
        "total_frames": n,
        "frames": corrected,
        "corrector_stats": {
            "contact": stats["contact_frames"],
            "hold_l": stats["hold_l_frames"],
            "hold_r": stats["hold_r_frames"],
            "blend": stats["blend_frames"],
        },
    }


# Las coordenadas de MediaPipe vienen en 0-1 y salen con 17 decimales, que es
# ruido de coma flotante: `0.5774523019790649`. Los thresholds del lado del
# render (deadzone de falange ~1e-3, HAND_SPAN_DEGRADED_RATIO) son de orden
# 1e-3, así que 1e-5 no cambia ninguna decisión — y a 5 decimales el asset
# pesa la mitad (50 señas: 20 MB → 9 MB de APK).
PRECISION = 5


def _round_numbers(obj, digits: int):
    if isinstance(obj, dict):
        return {k: (round(v, digits) if isinstance(v, float) else _round_numbers(v, digits))
                for k, v in obj.items()}
    if isinstance(obj, list):
        return [_round_numbers(x, digits) for x in obj]
    return obj


def build_and_write(args: tuple) -> str:
    data_root, stem, out_dir, with_face = args
    asset = build_stem(data_root, stem, with_face=with_face)
    if asset is None:
        return f"{stem}: sin CSV de manos, se omite"
    out_dir.mkdir(parents=True, exist_ok=True)
    target = out_dir / f"{stem}.json"
    asset["frames"] = _round_numbers(asset["frames"], PRECISION)
    target.write_text(json.dumps(asset, separators=(",", ":")))
    stats = asset["corrector_stats"]
    kb = target.stat().st_size / 1024
    return (f"{stem}: {asset['total_frames']:3d} frames  {kb:6.1f} KB  "
            f"contact={stats['contact']} blend={stats['blend']} "
            f"hold={stats['hold_l']}/{stats['hold_r']}")


# ── CLI ───────────────────────────────────────────────────────────────────────

def available_stems(data_root: pathlib.Path) -> list[str]:
    base = data_root / "HANDS_LANDMARKS/LEFT_HAND_LANDMARKS"
    if not base.is_dir():
        return []
    seen = set()
    for path in sorted(base.glob("*.csv")):
        parts = path.stem.split("_")
        # La convención del LSC50 es SEÑA_REPETICIÓN_ÁNGULO.
        if len(parts) == 3 and path.stem not in seen:
            seen.add(path.stem)
    return sorted(seen)


def vocab_stems(data_root: pathlib.Path, rep: int, angle: int) -> list[str]:
    """Un vídeo por seña del vocabulario, si existe."""
    base = data_root / "HANDS_LANDMARKS/LEFT_HAND_LANDMARKS"
    out = []
    for path in sorted(base.glob(f"*_{rep:04d}_{angle:04d}.csv")):
        out.append(path.stem)
    return out


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--data", help="Ruta a LANDMARKS (o su contenedor)")
    parser.add_argument("--sign", action="append", default=[],
                        help="Seña concreta SEÑA_REP_ÁNGULO (repetible)")
    parser.add_argument("--vocab", action="store_true",
                        help="Un vídeo por cada seña del vocabulario")
    parser.add_argument("--rep", type=int, default=0, help="Repetición con --vocab")
    parser.add_argument("--angle", type=int, default=0, help="Ángulo con --vocab")
    parser.add_argument("--out", default=str(OUT_DIR), help="Directorio destino")
    parser.add_argument("--no-face", action="store_true",
                        help="No incluir `face` (este avatar no la usa)")
    parser.add_argument("--list", action="store_true", help="Listar y salir")
    parser.add_argument("--jobs", type=int, default=os.cpu_count() or 4)
    args = parser.parse_args()

    data_root = find_data_root(args.data)
    print(f"datos: {data_root}")

    if args.list:
        stems = available_stems(data_root)
        print(f"{len(stems)} vídeos de señas disponibles")
        for stem in stems[:60]:
            print(f"  {stem}")
        if len(stems) > 60:
            print(f"  ... y {len(stems) - 60} más")
        vocab = vocab_stems(data_root, args.rep, args.angle)
        print(f"\nvocabulario (rep {args.rep}, ángulo {args.angle}): {len(vocab)} señas")
        return

    stems = list(args.sign)
    if args.vocab or not stems:
        stems += vocab_stems(data_root, args.rep, args.angle)
    stems = sorted(set(stems))
    if not stems:
        raise SystemExit("No hay señas que generar. ¿Ruta correcta? (--list para ver)")

    out_dir = pathlib.Path(args.out)
    print(f"generando {len(stems)} señas → {out_dir}\n")
    payload = [(data_root, stem, out_dir, not args.no_face) for stem in stems]

    # El corrector es CPU-bound en Python plano: los procesos lo usan.
    with ProcessPoolExecutor(max_workers=max(1, args.jobs)) as pool:
        for line in pool.map(build_and_write, payload):
            print("  " + line)
    print(f"\nlisto: {len(stems)} assets")


if __name__ == "__main__":
    main()