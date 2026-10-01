#!/usr/bin/env python3
"""
Splice del movimiento de `signai-avatar/app/static/app.js` al app.js del WebView.

Se corre una vez, no es parte del build: deja el `app.js` del WebView con el
retargeting del prototipo (IK de brazos con sus correcciones, bisagra
anatómica de falanges, orientación de muñeca) y los bits de Android —cola,
bridge, debug, fondo, tema, cámara— intactos.

Existe porque los dos archivos se llaman `app.js` y se confunden fácil: esto
deja escrito de dónde sale cada bloque, para que la diferencia sea evidente
en vez de tener que deducirla leyendo.

Los bloques se localizan por su primera línea, no por número, así que el
script sigue funcionando si alguno de los dos archivos cambia de largo.

    python3 tools/splice_movement.py           # escribe
    python3 tools/splice_movement.py --check   # solo reporta
"""

from __future__ import annotations

import argparse
import pathlib

REPO = pathlib.Path(__file__).resolve().parent.parent
PROTO_PATH = REPO.parent / "signai-avatar/app/static/app.js"
WEB_PATH = REPO / "app/src/main/assets/web/app.js"


def find(lines: list[str], prefix: str, what: str) -> int:
    for i, line in enumerate(lines):
        if line.startswith(prefix):
            return i  # 0-based
    raise SystemExit(f"No encontré {what} ({prefix!r})")


def span(lines: list[str], first: int, last: int) -> list[str]:
    """Del primer índice al último exclusive; se come el blanco separador."""
    return lines[first:last]


def drop_face_block(lines: list[str]) -> list[str]:
    """
    Saca el `if (frameData.face) { applyFace(...) }` de `applyFrame`.

    Hace falta quitar el `if` Y su cuerpo:filtrar solo la línea del `if` deja el
    `applyFace(...)` suelto, que es un return fuera de función.

    Se hace por conteo de llaves en vez de por texto, porque el bloque podría
    ganar otra línea sin que el filtro se entere.
    """
    out: list[str] = []
    i = 0
    while i < len(lines):
        if "frameData.face" in lines[i] and lines[i].lstrip().startswith("if"):
            # Saltar hasta el cierre del bloque, contando llaves.
            depth = 0
            while i < len(lines):
                depth += lines[i].count("{") - lines[i].count("}")
                i += 1
                if depth <= 0:
                    break
            continue
        out.append(lines[i])
        i += 1
    return out


def splice(proto: list[str], web: list[str]) -> list[str]:
    # Puntos de corte. Del WebView se conservan estado, debug, setup de Three.js
    # y carga del GLB; del prototipo, todo el movimiento.
    web_state = find(web, "// ── Estado global", "estado del webview")
    web_ik = find(web, "// ── IK de brazos", "IK del webview")
    web_loop = find(web, "// ── Loop de reproducción", "loop del webview")

    proto_consts = find(proto, "// ── Dedos: landmarks", "constantes de dedos")
    proto_state = find(proto, "// ── Estado global", "estado del prototipo")
    proto_ik = find(proto, "// ── IK de brazos", "IK del prototipo")
    proto_face = find(proto, "// ── Animación facial", "cara del prototipo")
    proto_frame = find(proto, "function applyFrame", "applyFrame del prototipo")
    proto_reset = find(proto, "// Restaura la rest pose", "resetPose del prototipo")

    out: list[str] = []

    # ── 1. Constantes de dedos, del prototipo ─────────────────────────────────
    # FINGER_LM, HINGE_FINGERS, FLEX_*, acoplamientos DIP/MCP, span degradado.
    # Los helpers de conversión (lmDir/mpToThree) no cambiaron entre versiones
    # y se copian tal cual para no depender del bloque importado.
    # Los imports van PRIMERO: el bloque de constantes del prototipo arranca
    # después de los suyos, así que si se copian tal cual el módulo queda sin
    # THREE y revienta al cargar.
    out += [
        "",
        'import * as THREE from "three";',
        'import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";',
        'import { OrbitControls } from "three/addons/controls/OrbitControls.js";',
    ]

    out += span(proto, proto_consts, proto_state)
    # lmDir/mpToThree NO se copian: el WebView ya los declara en su cabecera y
    # son idénticos a los del prototipo (no cambiaron entre versiones).

    # ── 2. Estado, debug, setup y carga del GLB: del WebView ─────────────────
    # Se editan a mano antes de correr el splice (ver git log): el `state` gana
    # `boneFlexAxis` y los alphas de dedo, y el registro de huesos el fallback
    # por `skeleton.bones`.
    out += span(web, web_state, web_ik)

    # ── 3. IK + retargeting + bisagra, del prototipo ──────────────────────────
    # Es todo el bloque desde el IK hasta la animación facial. Lo que el
    # WebView tenía acá era una versión anterior de este mismo código.
    out += span(proto, proto_ik, proto_face)

    # ── 4. applyFrame, del prototipo, sin la cara ─────────────────────────────
    # Este rig (AutoRigPro, 68 huesos) no tiene huesos faciales, así que la
    # parte de cara no se porta. El applyMnm (marcadores no manuales) también
    # se cae con ella: solo movía cejas.
    # applyFrame + resetPose, del prototipo, sin la parte de cara.
    movement = span(proto, proto_frame, proto_reset)
    out += drop_face_block(movement)

    # resetPose es lo mismo en los dos, pero vive justo después de applyFrame en
    # el prototipo y antes del loop en el WebView; se toma de acá para que el
    # bloque siga junto y no quede partido por el medio.
    out += proto[proto_reset:proto_reset + 8]

    # ── 5. Loop de reproducción y API de Android, del WebView ────────────────
    # Cola, `SignAIBridge.postMessage`, debug, fondo, tema, cámara, long press.
    out += web[web_loop:]

    return out


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true",
                        help="Solo verifica y reporta, no escribe")
    args = parser.parse_args()

    proto = PROTO_PATH.read_text().split("\n")
    web = WEB_PATH.read_text().split("\n")
    result = splice(proto, web)

    if args.check:
        print(f"OK — el splice daría {len(result)} líneas (ahora hay {len(web)})")
        return

    WEB_PATH.write_text("\n".join(result))
    print(f"escrito: {WEB_PATH} ({len(result)} líneas, antes {len(web)})")


if __name__ == "__main__":
    main()