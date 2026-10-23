#!/usr/bin/env python3
"""Genera los recursos de marca (icono, logo, launcher icon) desde los PNG maestros.

Por qué un script y no los PNG sueltos
--------------------------------------
Los dos masters (`docs/marca/icono-signall.png`, `docs/marca/logo-signall.png`)
son la única copia que hay en el repo —las copias sueltas de la raíz se borraron el
2026-10-22— y **no** se pueden usar tal cual en la app:

- El icono tiene la mano como **agujero** (alfa 0) sobre un azul sólido, así que
  al pintar el tile sobre cualquier fondo la mano toma el color del fondo. Para el
  launcher eso está bien (es el language de adaptive icons), pero dentro de la app
  hace falta la mano en blanco.
- El logo tiene la mitad "Sign" en azul marino (#09152E). Sobre el fondo oscuro de
  la marca nueva (#0C142C) ese texto desaparece, así que hacen falta dos variantes:
  la original para el tema claro y una con el marino en blanco para el oscuro.
- El launcher icon necesita la mano como **path vectorial** (foreground del adaptive
  icon), no un PNG: un PNG dentro de un adaptive icon se ve mal en todas las máscaras
  y no escala.

Este script hace las tres cosas y es idempotente: se puede volver a correr cada vez
que cambien los masters.

Uso
---
    python3 tools/generar-marca.py

Depende de `potrace` (`apt install potrace`) y de Pillow. No toca nada fuera de
`app/src/main/res`.
"""

from __future__ import annotations

import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"

MARCA = ROOT / "docs" / "marca"
MASTER_ICON = MARCA / "icono-signall.png"
MASTER_LOGO = MARCA / "logo-signall.png"

# La marca, en un solo lugar. `1C4CFC` es el azul que trae el PNG (1D4CFD) redondeado
# al acento queapprobó el owner; `0C142C` es el fondo del tema oscuro.
ACCENT = "#1C4CFC"
LOGO_NAVY_MAX_BLUE = 120  # más azul que esto es el "ALL"; menos, el marino que va a blanco

# Tamaños de salida. Los PNG viven en `drawable-xxhdpi` (densidad 3.0) y son
# suficientes para el uso más grande de la app con cualquier pantalla de hasta 4.0:
# el tile más grande es 68dp (About) → 272px @4.0 < 288, y el logo 160dp → 640 < 720.
ICON_PX = 288
LOGO_PX = 720

# Adaptive icon: viewport 108x108 y el sistema solo deja ver los 72x108 centrales.
# El master es un cuadrado 528px con la mano en el 54.5% del ancho y el 75.5% del
# alto; mapear el master a los 72dp visibles reproduce el icono tal cual bajo
# cualquier máscara. El radio máximo de la mano es 0.401 del ancho, o sea 28.9dp
# desde el centro: entra en el círculo inscrito de 36dp, así que no se recorta.
ADAPTIVE_VIEWPORT = 108.0
ADAPTIVE_VISIBLE = 72.0
LEGACY_VIEWPORT = 640.0
CORNER_RATIO = 0.21  # radio del cuadrado redondeado del master, medido


# --------------------------------------------------------------------------------------
# Lectura del master
# --------------------------------------------------------------------------------------
def hole_of(alpha: np.ndarray) -> np.ndarray:
    """Devuelve la mano: la zona transparente que NO toca el borde del tile.

    El master no tiene una mano blanca, tiene una mano **agujero**: un cuadrado azul
    con un hueco con forma de mano. Reachability desde el borde separa el hueco de las
    esquinas redondeadas, que también son transparentes y se colarían en el bbox.
    """
    solid = alpha > 200
    outside = ~solid
    height, width = solid.shape
    reachable = np.zeros_like(solid)
    stack = []
    for x in range(width):
        for y in (0, height - 1):
            if outside[y, x] and not reachable[y, x]:
                reachable[y, x] = True
                stack.append((y, x))
    for y in range(height):
        for x in (0, width - 1):
            if outside[y, x] and not reachable[y, x]:
                reachable[y, x] = True
                stack.append((y, x))
    while stack:
        y, x = stack.pop()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < height and 0 <= nx < width and outside[ny, nx] and not reachable[ny, nx]:
                reachable[ny, nx] = True
                stack.append((ny, nx))
    return outside & ~reachable


def load_icon_canvas() -> tuple[np.ndarray, np.ndarray, int]:
    """Devuelve (RGBA cuadrado con el master centrado, máscara de la mano, lado)."""
    raw = np.array(Image.open(MASTER_ICON).convert("RGBA"))
    alpha = raw[..., 3]
    ys, xs = np.nonzero(alpha)
    cropped = raw[ys.min(): ys.max() + 1, xs.min(): xs.max() + 1]

    height, width = cropped.shape[:2]
    side = max(width, height)
    top = (side - height) // 2
    left = (side - width) // 2
    canvas = np.zeros((side, side, 4), dtype=np.uint8)
    canvas[top: top + height, left: left + width] = cropped

    hand = hole_of(canvas[..., 3])
    return canvas, hand, side


# --------------------------------------------------------------------------------------
# Trazado de la mano (potrace -> pathData de VectorDrawable)
# --------------------------------------------------------------------------------------
# potrace solo emite `M` absoluto y `c`/`l`/`z` relativos. El parser cubre esos cuatro
# comandos y tira si aparece otro: si potrace cambiara, que falle acá y no en el
# VectorDrawable, que compila igual aunque dibuje cualquier cosa.
TOKEN = re.compile(r"[A-Za-z]|[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?")
PAIRS = {"M": 1, "L": 1, "C": 3, "Z": 0}


def fmt(value: float) -> str:
    text = f"{value:.3f}".rstrip("0").rstrip(".")
    return text or "0"


def trace_hand(hand: np.ndarray, tmp: Path) -> list[tuple[str, list[tuple[float, float]]]]:
    """Traza la mascara con potrace y devuelve los segmentos ya en y-down.

    potrace escribe en un espacio con el origen abajo-izquierda
    (`translate(0,H) scale(1,-1)`), asi que aca se invierte la y para dejar todo en el
    espacio con el origen arriba-izquierda, que es el de Compose y el del
    `VectorDrawable`.
    """
    if not shutil.which("potrace"):
        sys.exit("falta potrace: sudo apt install potrace")

    height, _ = hand.shape
    pbm = tmp / "mano.pbm"
    Image.fromarray(np.where(hand, 0, 255).astype(np.uint8), "L").save(pbm)
    svg = tmp / "mano.svg"
    # -O 0.2 / -a 1.0 es donde la curva deja de mejorar (IoU 0.961 contra la mascara;
    # el resto del error es el umbral del alfa) y ademas da el path mas corto.
    subprocess.run(
        ["potrace", "-s", "-O", "0.2", "-a", "1.0", "-t", "2", "-u", "1",
         "-o", str(svg), str(pbm)],
        check=True,
    )
    body = svg.read_text().split('d="', 1)[1].split('"', 1)[0]

    tokens = TOKEN.findall(body)
    segments: list[tuple[str, list[tuple[float, float]]]] = []
    current = (0.0, 0.0)
    index = 0
    while index < len(tokens):
        command = tokens[index]
        index += 1
        if command.upper() not in PAIRS:
            sys.exit(f"potrace emitio '{command}', que este script no sabe leer")
        if command.upper() == "Z":
            segments.append(("Z", []))
            continue
        # potrace usa la forma "polybezier" de SVG: una sola letra seguida de varios
        # grupos de coordenadas, y cada grupo repite el punto final del anterior. Por
        # eso se leen todos los números que siguen hasta la próxima letra, no solo un
        # grupo.
        start = index
        while index < len(tokens) and not tokens[index].isalpha():
            index += 1
        coords = [float(v) for v in tokens[start:index]]
        pairs = PAIRS[command.upper()]
        if len(coords) % (pairs * 2):
            sys.exit(f"potrace emitio {len(coords)} numeros para '{command}'")
        points: list[tuple[float, float]] = []
        for group in range(0, len(coords), pairs * 2):
            # Ojo: los `pairs` grupos de un comando sonTODOS relativos al punto de
            # partida del grupo, no encadenados. `c 1,2 3,4 5,6` desde (0,0) es la
            # curva (1,2) (3,4) (5,6); encadenarlos da (1,2) (4,6) (9,12), que es
            # otra figura. Y el punto de partida del grupo siguiente es el final del
            # anterior, no su ultimo punto de control.
            base = current
            for point in range(pairs):
                dx, dy = coords[group + point * 2], coords[group + point * 2 + 1]
                if command.isupper():
                    x, y = dx, dy
                else:
                    x, y = base[0] + dx, base[1] + dy
                # Se invierte la y para salir, pero `current` sigue en el espacio de
                # potrace: mezclar los dos espacios fue el segundo bug de este parser.
                points.append((x, height - y))
            current = (x, y)
        segments.append((command.upper(), points))
    return segments


def to_path_data(
    segments: list[tuple[str, list[tuple[float, float]]]],
    scale: float,
    offset: float,
) -> str:
    """Escala los segmentos al viewport del `VectorDrawable` y los arma como pathData.

    Todo sale en coordenadas absolutas: un `path` de `VectorDrawable` no lleva
    transform (solo el `<group>` la tiene) y unas absolutas se leen en el Android
    Studio sin tener que imaginar una matriz.
    """
    out: list[str] = []
    for command, points in segments:
        if not points:                      # el "Z" final, que no lleva coordenadas
            out.append(command)
            continue
        # Una curva por comando. potrace mete cinco curvas en un solo `c` (la forma
        # "polybezier" de SVG); Android la acepta, pero en el XML del recurso queda
        # mucho más claro un `C` por curva.
        step = PAIRS[command]
        for start in range(0, len(points), step):
            out.append(command)
            for x, y in points[start:start + step]:
                out.append(fmt(x * scale + offset))
                out.append(fmt(y * scale + offset))
    return " ".join(out)


# --------------------------------------------------------------------------------------
# Salidas
# --------------------------------------------------------------------------------------
def rounded_square(viewport: float, ratio: float) -> str:
    """Cuadrado con las esquinas redondeadas, al radio del master (21% del lado).

    Los valores van calculados, no concatenados: `H640-134.4` no es un `H` con dos
    números, es un `H` con uno y un `-134.4` suelto que el parser tira.
    """
    r = viewport * ratio
    v = viewport - r
    return " ".join(
        (
            f"M{fmt(r)},0",
            f"H{fmt(v)}",
            f"A{fmt(r)},{fmt(r)} 0 0 1 {fmt(viewport)},{fmt(r)}",
            f"V{fmt(v)}",
            f"A{fmt(r)},{fmt(r)} 0 0 1 {fmt(v)},{fmt(viewport)}",
            f"H{fmt(r)}",
            f"A{fmt(r)},{fmt(r)} 0 0 1 0,{fmt(v)}",
            f"V{fmt(r)}",
            f"A{fmt(r)},{fmt(r)} 0 0 1 {fmt(r)},0",
            "Z",
        )
    )


def write_vector(name: str, body: str, comment: str, viewport: float) -> None:
    """Escribe un `VectorDrawable`.

    `viewport` es el espacio en el que está escrito el `pathData` y tiene que
    coincidir con el que se escaló: declarar 640 para una mano dibujada en el
    viewport de 108 del adaptive icon la deja de 6x6 dp en una esquina, y lo que se ve
    en el launcher es un bloque azul sin nada.
    """
    path = RES / "drawable" / f"{name}.xml"
    path.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!--\n  {comment}\n  Generado por tools/generar-marca.py: no editar a mano.\n-->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="108dp"\n'
        f'    android:height="108dp"\n'
        f'    android:viewportWidth="{fmt(viewport)}"\n'
        f'    android:viewportHeight="{fmt(viewport)}">\n'
        f"{body}\n</vector>\n"
    )
    print(f"  {path.relative_to(ROOT)}  (viewport {fmt(viewport)})")


def vector_body(fill: str, tile: str | None, hand: str) -> str:
    lines: list[str] = []
    if tile:
        lines.append(
            "    <path\n"
            f'        android:fillColor="{tile}"\n'
            f'        android:pathData="{rounded_square(LEGACY_VIEWPORT, CORNER_RATIO)}" />'
        )
    lines.append(
        "    <path\n"
        f'        android:fillColor="{fill}"\n'
        f'        android:pathData="{hand}" />'
    )
    return "\n".join(lines)


def write_icon_png(canvas: np.ndarray, hand: np.ndarray) -> None:
    """Tile cuadrado azul con la mano en blanco, como lo diseño la marca."""
    tile = np.zeros_like(canvas)
    tile[..., :3] = np.array([0x1D, 0x4C, 0xFD], dtype=np.uint8)
    tile[..., 3] = canvas[..., 3]
    tile[hand] = (255, 255, 255, 255)
    image = Image.fromarray(tile, "RGBA").resize((ICON_PX, ICON_PX), Image.LANCZOS)
    path = RES / "drawable-xxhdpi" / "ic_signall.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)
    print(f"  {path.relative_to(ROOT)}  ({ICON_PX}px, {path.stat().st_size // 1024} KB)")


def write_logo_pngs() -> None:
    raw = Image.open(MASTER_LOGO).convert("RGBA")
    alpha = raw.getchannel("A")
    cropped = raw.crop(alpha.getbbox())

    array = np.array(cropped).astype(np.int16)
    # El logo tiene exactamente dos colores: azul marino y el azul de marca. Con más
    # azul que el umbral es el "ALL" y se deja; el resto es el "Sign".
    is_navy = array[..., 2] <= LOGO_NAVY_MAX_BLUE
    ambiguous = int(((array[..., 2] > LOGO_NAVY_MAX_BLUE) & (array[..., 1] > 120)).sum())
    if ambiguous:
        print(f"  ojo: {ambiguous} px del logo no son ni marino ni azul de marca")

    for suffix, navy in (("on_light", None), ("on_dark", (255, 255, 255))):
        variant = array.copy()
        if navy is not None:
            for channel in range(3):
                variant[..., channel][is_navy] = navy[channel]
        image = Image.fromarray(variant.astype(np.uint8), "RGBA")
        height = round(image.height * LOGO_PX / image.width)
        image = image.resize((LOGO_PX, height), Image.LANCZOS)
        path = RES / "drawable-xxhdpi" / f"logo_signall_{suffix}.png"
        image.save(path, optimize=True)
        print(
            f"  {path.relative_to(ROOT)}  ({LOGO_PX}x{height}, "
            f"{path.stat().st_size // 1024} KB)"
        )


def patch_values(accent: str) -> None:
    colors = RES / "values" / "colors.xml"
    text = colors.read_text()
    text = re.sub(
        r'(<color name="ic_launcher_background">)[^<]*(</color>)',
        rf"\g<1>{accent}\g<2>",
        text,
    )
    colors.write_text(text)
    print(f"  {colors.relative_to(ROOT)}  (ic_launcher_background = {accent})")

    strings = RES / "values" / "strings.xml"
    text = strings.read_text()
    text = re.sub(r'(<string name="app_name">)[^<]*(</string>)', r"\g<1>SignALL\g<2>", text)
    strings.write_text(text)
    print(f"  {strings.relative_to(ROOT)}  (app_name = SignALL)")


def path_points(path_data: str) -> list[tuple[float, float]]:
    """Vuelve a leer el pathData emitido, para poder medirlo.

    Acepta las dos formas: una curva por comando y la forma "polybezier" (una letra
    seguida de varios grupos), que es la que escribe potrace.
    """
    tokens = TOKEN.findall(path_data)
    points: list[tuple[float, float]] = []
    index = 0
    while index < len(tokens):
        command = tokens[index]
        index += 1
        if command.upper() == "Z":
            continue
        start = index
        while index < len(tokens) and not tokens[index].isalpha():
            index += 1
        coords = [float(v) for v in tokens[start:index]]
        points.extend((coords[i], coords[i + 1]) for i in range(0, len(coords), 2))
    return points


def check_against_mask(
    segments: list[tuple[str, list[tuple[float, float]]]],
    hand: np.ndarray,
    side: int,
) -> None:
    """La curva trazada tiene que caer donde está la mano.

    No sobra: potrace escribe su path en una forma comprimida (un comando con varios
    grupos, todos relativos al inicio del grupo, en un espacio con el origen abajo) y
    dos errores de lectura dan una figura que *parece* una mano pero está corrida y
    más grande. Comparar el bbox es barato y corta el error en el acto.
    """
    xs: list[float] = []
    ys: list[float] = []
    for _command, points in segments:
        for x, y in points:
            xs.append(x)
            ys.append(y)
    mask_ys, mask_xs = np.nonzero(hand)
    bounds = (
        ("x", (min(xs), max(xs)), (int(mask_xs.min()), int(mask_xs.max()))),
        ("y", (min(ys), max(ys)), (int(mask_ys.min()), int(mask_ys.max()))),
    )
    for label, got, expected in bounds:
        if abs(got[0] - expected[0]) > 3 or abs(got[1] - expected[1]) > 3:
            sys.exit(
                f"el path trazado no coincide con la mascara: {label} "
                f"{got[0]:.0f}..{got[1]:.0f}, la mascara {expected[0]}..{expected[1]} "
                f"(canvas de {side}px)"
            )
    print(f"  curva trazada: x {min(xs):.0f}..{max(xs):.0f}, y {min(ys):.0f}..{max(ys):.0f} (ok)")


def main() -> None:
    for master in (MASTER_ICON, MASTER_LOGO):
        if not master.exists():
            sys.exit(f"falta el master {master}")

    print("Leyendo los masters…")
    canvas, hand, side = load_icon_canvas()
    ys, xs = np.nonzero(hand)
    print(
        f"  tile {side}px, mano {xs.max() - xs.min() + 1}x{ys.max() - ys.min() + 1}px "
        f"({(xs.max() - xs.min() + 1) / side:.1%} del ancho)"
    )

    with tempfile.TemporaryDirectory() as tmpdir:
        segments = trace_hand(hand, Path(tmpdir))
        check_against_mask(segments, hand, side)

        print("Escribiendo el launcher icon…")
        # El master (528px) entra en los 72dp que el sistema deja ver, y el margen
        # sobrante va a cada lado: 18 = (108 - 72) / 2. Con el offset al revés el
        # tile se sale del viewport y la mano aparece cortada.
        adaptive_hand = to_path_data(
            segments,
            ADAPTIVE_VISIBLE / side,
            (ADAPTIVE_VIEWPORT - ADAPTIVE_VISIBLE) / 2,
        )
        legacy_hand = to_path_data(segments, LEGACY_VIEWPORT / side, 0.0)

        margin = (ADAPTIVE_VIEWPORT - ADAPTIVE_VISIBLE) / 2
        xs = [v for pair in path_points(adaptive_hand) for v in pair[:1]]
        ys = [v for pair in path_points(adaptive_hand) for v in pair[1:]]
        edge = ADAPTIVE_VIEWPORT - margin
        if min(xs) < margin - 0.5 or max(xs) > edge + 0.5 or min(ys) < margin - 0.5 or max(ys) > edge + 0.5:
            sys.exit(
                f"la mano se sale de la zona visible: x {min(xs):.1f}..{max(xs):.1f}, "
                f"y {min(ys):.1f}..{max(ys):.1f}, y lo visible es "
                f"{margin:.0f}..{edge:.0f}"
            )
        print(
            f"  mano en el adaptive icon: x {min(xs):.1f}..{max(xs):.1f}, "
            f"y {min(ys):.1f}..{max(ys):.1f} (lo visible es {margin:.0f}..{edge:.0f})"
        )

        write_vector(
            "ic_launcher_foreground",
            vector_body("#FFFFFF", None, adaptive_hand),
            "Adaptive icon: la mano en blanco. El fondo es @color/ic_launcher_background"
            "\n  (el acento de la marca), y la máscara del sistema hace el cuadrado"
            "\n  redondeado. La mano se escaló al 72/108 que el sistema deja ver.",
            ADAPTIVE_VIEWPORT,
        )
        write_vector(
            "ic_launcher_monochrome",
            vector_body("#000000", None, adaptive_hand),
            "Adaptive icon monocromo (Android 13+): el launcher tiñe esta capa con el"
            "\n  color del tema del sistema.",
            ADAPTIVE_VIEWPORT,
        )
        for name in ("ic_launcher", "ic_launcher_legacy", "ic_launcher_round"):
            write_vector(
                name,
                vector_body("#FFFFFF", ACCENT, legacy_hand),
                "Icono plano, sin adaptive icon. Con minSdk 26 el launcher siempre usa"
                "\n  `mipmap-anydpi-v26`; esto queda para que las tres rutas digan lo"
                "\n  mismo.",
                LEGACY_VIEWPORT,
            )

    print("Escribiendo los PNG de la UI…")
    write_icon_png(canvas, hand)
    write_logo_pngs()

    print("Actualizando los valores…")
    patch_values(ACCENT)


if __name__ == "__main__":
    main()