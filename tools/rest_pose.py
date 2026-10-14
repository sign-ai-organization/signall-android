#!/usr/bin/env python3
"""Hornea en `models/avatar.glb` la pose de descanso con los brazos abajo.

La pose de reposo del avatar **es** el TRS de sus nodos: es la pose que Cascadeur
dejó en el export, y por lo tanto tiene los brazos "medio abiertos", como de
maniquí (medido: manos a 52 cm del eje, 35° bajo la horizontal).

No hace falta volver a Blender. De los 2424 keyframes de las 32 señas, 851
tienen los dos brazos colgando, y **`signs/0029_0000_0000.glb` (JUICIOSO),
variante 0, keyframe 0 (t = 0.0417 s)** es uno de ellos y además:

  * es una pose por la que el personaje **ya pasó**, así que el skinning, la
    ropa y el pelo son válidos por construcción — no hay que confiar en que un
    giro de hueso a ojo no rompa la malla;
  * la cara no se toca: ningún hueso de expresión se mueve en ese frame.

Se copian **solo las rotaciones** de la cadena de brazos (46 huesos, L y R). Ni
la cara, ni el torso, ni las piernas. Las traducciones y las escalas no se
tocan: en este export son las de bind (difieren del frame en 2e-4, ruido de
float32) y cambiarlas no aporta nada.

Por qué esto **re-posea** en vez de deformar: el TRS de los nodos del avatar es
exactamente la pose de bind (`bind_pose_error` ≈ 6e-6, verificado sobre los 102
huesos). Cambiar esas rotaciones deja intactas las `inverseBindMatrices` y los
accessors `POSITION`, así que ni el `bbox` de `split_signs.py` ni el `pivot` de
`app.js` cambian, y los clips de seña siguen resolviendo los mismos tracks.

Uso:

    python3 tools/rest_pose.py            # hornea en models/avatar.glb
    python3 tools/rest_pose.py --dry-run  # solo el informe, no escribe

Es un paso del pipeline: `split_signs.py` lo llama al final. **`avatar.glb` no
se edita a mano**, se regenera.
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import glb_pose as gp  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
WEB = REPO / "app/src/main/assets/web"
AVATAR = WEB / "models/avatar.glb"

# El frame de referencia: primer keyframe del clip de JUICIOSO.
REFERENCE = "0029_0000_0000.glb"
REFERENCE_VARIANT = 0

# La cadena de brazos, y nada más. Con estos prefijos sobre `nombre + lado`
# salen 46 huesos exactos: 2 shoulder + 2 upper_arm + 2 forearm + 2 hand +
# 8 palm.* + 24 f_* + 6 thumb*, de los dos lados.
ARM_PREFIXES = ("shoulder", "upper_arm", "forearm", "hand", "palm.", "f_", "thumb")
SIDES = (".L", ".R")
EXPECTED_BONES = 46


def arm_chain(js):
    """Los nombres de los huesos de la cadena de brazos, en orden de archivo."""
    return [node["name"] for node in js.get("nodes", [])
            if node.get("name", "").endswith(SIDES)
            and any(node["name"].startswith(p) for p in ARM_PREFIXES)]


def check_chain(names):
    """Falla ruidosamente si el esqueleto no es el que se espera.

    El riesgo real no es que falte un hueso sino que el patrón matchee algo que
    no es un hueso de brazo (un `hand_*` de la cara, por ejemplo) y lo poseemos
    sin querer. Que salgan 46 y que los dos lados tengan el mismo número es la
    comprobación barata.
    """
    if len(names) != EXPECTED_BONES:
        raise SystemExit(
            f"la cadena de brazos tiene {len(names)} huesos, se esperaban "
            f"{EXPECTED_BONES}:\n  {sorted(names)}")
    for side in SIDES:
        count = sum(1 for n in names if n.endswith(side))
        if count != EXPECTED_BONES // len(SIDES):
            raise SystemExit(f"lado {side}: {count} huesos, desbalanceado")
    return names


def reference_pose(signs_dir=WEB / "signs"):
    """La pose del frame de referencia: `{nombre: (T, R, S)}`."""
    path = Path(signs_dir) / REFERENCE
    if not path.is_file():
        raise SystemExit(
            f"falta el clip de referencia {path}.\n"
            "Se genera con `python3 tools/split_signs.py` (que es quien llama a "
            "este script).")
    js, binary = gp.read_glb(path)
    t = gp.first_keyframe_time(js, REFERENCE_VARIANT)
    return gp.sample_clip(js, binary, REFERENCE_VARIANT, t), t


def bake(avatar_js, pose, names):
    """Pisa en [avatar_js] las rotaciones de la cadena. Devuelve qué se tocó.

    Idempotente de verdad: los valores salen del clip de referencia, nunca de los
    que ya tiene el avatar. Correrlo dos veces da el mismo archivo.
    """
    touched, worst = [], 0.0
    by_name = gp.node_by_name(avatar_js)
    for name in names:
        if name not in pose:
            raise SystemExit(f"el clip de referencia no anima {name}")
        node, rotation = by_name[name], pose[name][1]
        old = node.get("rotation", gp.IDENTITY_Q)
        worst = max(worst, max(abs(a - b) for a, b in zip(old, rotation)))
        node["rotation"] = list(rotation)
        touched.append(name)
    return touched, worst


def compact(avatar_js, names):
    """Deja las rotaciones pisadas como float32, ready para el JSON.

    No existe: el punto de esto es que quede escrito acá. Se documenta la
    trampa porque es cara y no avisa — `tools/rest_pose.py` lo hizo mal una vez
    y el síntoma fue un avatar invisible:

    > Escribir el quaternion como **string** con un decimal corto deja el `.glb`
    > un 1% más chico, pero `GLTFLoader` no coerciona: `Quaternion.fromArray()`
    > copia el string tal cual, la matriz del hueso queda con NaN y el
    > personaje no se ve, sin un error en la consola.

    Los floats van como float de Python: `json.dumps` los escribe con la
    representación más corta que vuelve a dar el mismo float32, que es
    exactamente como los grabó el exportador de Blender. Por eso el archivo no
    cambia de tamaño.
    """
    for name in names:
        node = gp.node_by_name(avatar_js)[name]
        node["rotation"] = [gp.f32(c) for c in node["rotation"]]


def hand_positions(js, pose=None):
    """Posición de las manos por FK: `{lado: (x, y, z)}` en unidades del export."""
    world = gp.world_poses(js, pose)
    return {side: gp.mat4_transform(world[f"hand.{side}"][3], (0.0, 0.0, 0.0))
            for side in ("L", "R")}


def check_bind(js, binary):
    """Verifica que el archivo sea un avatar sano antes de tocarlo.

    Lo que se comprueba es que el TRS de los nodos sea la pose de bind
    (reconstruida desde las IBM), **salvo en la cadena de brazos**, que puede
    estar ya horneada: correr el script dos veces sobre el mismo archivo tiene
    que dar lo mismo, y no fallar.

    Por fuera de la cadena de brazos, cualquier desviación significa que el
    export no es el que esperamos, y ahí hornear deformaría la malla en vez de
    re-posarla — que es un fallo que no aparece en ningún error.
    """
    bind = gp.bind_pose(js, binary)
    arm = set(arm_chain(js))
    worst_out, worst_arm = 0.0, 0.0
    for name, node in gp.node_by_name(js).items():
        if name not in bind:
            continue
        got, want = gp.node_trs(node), bind[name]
        # q y −q son el mismo giro: `bind_pose` puede devolver el signo
        # contrario al del archivo y no sería ninguna diferencia.
        if sum(a * b for a, b in zip(got[1], want[1])) < 0:
            want = (want[0], tuple(-c for c in want[1]), want[2])
        delta = max(max(abs(a - b) for a, b in zip(branch_x, branch_y))
                    for branch_x, branch_y in zip(got, want))
        if name in arm:
            worst_arm = max(worst_arm, delta)
        else:
            worst_out = max(worst_out, delta)
    if worst_out > 1e-4:
        raise SystemExit(
            f"el TRS de {worst_out:.2e} de los nodos que no son brazo no es la pose "
            "de bind. Revisar el export antes de hornear la pose de descanso.")
    return worst_arm


def bake_rest_pose(js, signs_dir=WEB / "signs"):
    """Pisa en [js] la pose de descanso. Devuelve el informe como dict.

    Es lo que llama `split_signs.py` al final, para que regenerar los assets no
    pierda la pose. No escribe el archivo: eso lo hace quien tiene el binario.
    """
    names = check_chain(arm_chain(js))
    pose, t = reference_pose(signs_dir)
    before = hand_positions(js)
    touched, worst = bake(js, pose, names)
    after = hand_positions(js)
    compact(js, names)
    return {"t": t, "before": before, "after": after,
            "bones": len(touched), "delta": worst}


def report_lines(report, indent="  "):
    """El informe de [bake_rest_pose] como líneas para imprimir."""
    lines = [f"{indent}pose de descanso desde {REFERENCE} variante "
             f"{REFERENCE_VARIANT}, keyframe 0 (t = {report['t']:.4f}s)"]
    for side in ("L", "R"):
        b, a = report["before"][side], report["after"][side]
        lines.append(f"{indent}  mano {side}  eje {abs(b[0]):.3f} -> {abs(a[0]):.3f} m"
                     f"   altura {b[1]:.3f} -> {a[1]:.3f} m")
    lines.append(f"{indent}  {report['bones']} rotaciones pisadas "
                 f"(max |dq| = {report['delta']:.4f})")
    return lines


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--avatar", type=Path, default=AVATAR)
    parser.add_argument("--signs", type=Path, default=WEB / "signs")
    parser.add_argument("--dry-run", action="store_true",
                        help="informa sin escribir el archivo")
    args = parser.parse_args()

    js, binary = gp.read_glb(args.avatar)
    check_bind(js, binary)
    before_size = args.avatar.stat().st_size
    report = bake_rest_pose(js, args.signs)
    print("\n".join(report_lines(report)))

    if args.dry_run:
        print("    --dry-run: no se escribió nada")
        return
    size = gp.write_glb(args.avatar, js, binary)
    print(f"    escrito {args.avatar.name} ({size / 1e6:.2f} MB, "
          f"{size - before_size:+d} bytes)")


if __name__ == "__main__":
    main()
