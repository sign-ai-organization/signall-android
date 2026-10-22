#!/usr/bin/env python3
"""Leer un `.glb`, recorrer el esqueleto y samplear un clip. Sin dependencias.

Es la base de todo lo que hay que mirar *dentro* de los archivos de señas: la
pose de descanso (`rest_pose.py`) y el render offline (`preview_pose.py`). Vive
acá y no en `split_signs.py` porque los tres lo necesitan, y `read_glb` /
`write_glb` se mudaron desde ahí para que haya **un solo** lector de GLB en el
repo: duplicarlo es la forma más rápida de que un día uno de los dos se quede
viejo.

Self-contained: no necesita `blender`, ni `npm`, ni `three`, ni `numpy`.

Las tres trampas que hacen que esto no sea trivial, y que ya están pisadas acá
(estaban en la metodología de `investigacion.md`):

  1. Los accessors enteros hay que leerlos con su `componentType`: `JOINTS_0` es
     `unsigned short`, no float.
  2. Los `MAT4` (las `inverseBindMatrices`) son **column-major**: hay que
     transponerlos. Sin eso la malla explota.
  3. Al interpolar un clip hay que reemplazar los **tres** canales por nodo
     (T, R y S), no solo la rotación: si se accumulate "un canal por nodo", el
     último que se escriba deja al nodo sin posición.

Uso típico:

    import glb_pose as gp
    js, bin = gp.read_glb("app/src/main/assets/web/models/avatar.glb")
    pose = gp.world_poses(js)                      # el TRS de los nodos
    sjs, sbin = gp.read_glb("app/src/main/assets/web/signs/0029_0000_0000.glb")
    frame = gp.sample_clip(sjs, sbin, 0, t=0.0417)
"""
import json
import struct
from pathlib import Path

# ── Tipos de accessor ─────────────────────────────────────────────────────────

COMPONENTS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4,
              "MAT2": 4, "MAT3": 9, "MAT4": 16}

# componentType → (código struct, tamaño en bytes)
COMPONENT_TYPES = {5120: ("b", 1), 5121: ("B", 1), 5122: ("h", 2),
                   5123: ("H", 2), 5125: ("I", 4), 5126: ("f", 4)}

IDENTITY_Q = (0.0, 0.0, 0.0, 1.0)

# Los tres canales de TRS de un nodo, por el nombre que usa glTF. `weights` no
# está: no es un TRS y `sample_clip` lo saltea.
_POSE_CHANNEL = {"translation": 0, "rotation": 1, "scale": 2}


# ── GLB lectura/escritura ─────────────────────────────────────────────────────

def read_glb(path):
    """Devuelve (json, binario) de un .glb."""
    raw = Path(path).read_bytes()
    magic, version, _length = struct.unpack_from("<III", raw, 0)
    if magic != 0x46546C67:
        raise ValueError(f"{path}: no es un GLB")
    offset, js, binary = 12, None, b""
    while offset < len(raw):
        chunk_len, chunk_type = struct.unpack_from("<II", raw, offset)
        data = raw[offset + 8: offset + 8 + chunk_len]
        if chunk_type == 0x4E4F534A:
            js = json.loads(data.decode("utf-8"))
        elif chunk_type == 0x004E4942:
            binary = data
        offset += 8 + chunk_len + ((4 - chunk_len % 4) % 4 if chunk_len % 4 else 0)
    if js is None:
        raise ValueError(f"{path}: sin chunk JSON")
    return js, binary


def write_glb(path, js, binary):
    """Escribe un .glb. `binary` es exactamente el BIN chunk, sin padding extra."""
    js_bytes = json.dumps(js, separators=(",", ":")).encode("utf-8")
    js_bytes += b" " * ((4 - len(js_bytes) % 4) % 4)
    bin_bytes = binary + b"\x00" * ((4 - len(binary) % 4) % 4)
    total = 12 + 8 + len(js_bytes) + 8 + len(bin_bytes)
    out = struct.pack("<III", 0x46546C67, 2, total)
    out += struct.pack("<II", len(js_bytes), 0x4E4F534A) + js_bytes
    out += struct.pack("<II", len(bin_bytes), 0x004E4942) + bin_bytes
    Path(path).write_bytes(out)
    return len(out)


def f32(x):
    """Un float de Python por el ida y vuelta de float32 (glTF no tiene double)."""
    return struct.unpack("<f", struct.pack("<f", x))[0]

# ── Accessors ─────────────────────────────────────────────────────────────────

def read_accessor(js, binary, index):
    """Accessor como lista plana de floats/ints, en el orden del archivo."""
    acc = js["accessors"][index]
    count, comps = acc["count"], COMPONENTS[acc["type"]]
    code, size = COMPONENT_TYPES[acc["componentType"]]
    if acc.get("sparse"):
        raise ValueError(f"accessor {index}: sparse no soportado")

    bv_index = acc.get("bufferView")
    if bv_index is None:                       # sin bufferView = ceros
        return [0] * (count * comps)

    view = js["bufferViews"][bv_index]
    base = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
    stride = view.get("byteStride") or comps * size
    fmt = f"<{comps}{code}"
    unpack = struct.Struct(fmt).unpack_from

    out = []
    for i in range(count):
        out.extend(unpack(binary, base + i * stride))
    return out


def read_accessor_mat4(js, binary, index):
    """Los `MAT4` de un accessor como lista de 4x4 **row-major**.

    glTF los guarda column-major (es la convención de OpenGL). Este código hace
    matemática con filas, así que se transpone al leer; leerlos como si fueran
    row-major produce una skinning sutilmente mal y una malla que se rompe.
    """
    flat = read_accessor(js, binary, index)
    return [tuple(tuple(flat[m * 16 + c * 4 + r] for c in range(4))
                  for r in range(4))
            for m in range(len(flat) // 16)]


# ── Álgebra 4x4 (row-major) ──────────────────────────────────────────────────

def mat4_identity():
    return ((1.0, 0.0, 0.0, 0.0),
            (0.0, 1.0, 0.0, 0.0),
            (0.0, 0.0, 1.0, 0.0),
            (0.0, 0.0, 0.0, 1.0))


def mat4_mul(a, b):
    """a · b (primero se aplica b: como en three.js, `parent * local`)."""
    return tuple(
        tuple(a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j] + a[i][3] * b[3][j]
              for j in range(4))
        for i in range(4)
    )


def mat4_from_trs(t, q, s):
    """Compone T · R · S para un nodo. `q` es (x, y, z, w), como en glTF."""
    x, y, z, w = q
    x2, y2, z2 = x + x, y + y, z + z
    xx, xy, xz = x * x2, x * y2, x * z2
    yy, yz, zz = y * y2, y * z2, z * z2
    wx, wy, wz = w * x2, w * y2, w * z2
    sx, sy, sz = s
    m = ((1 - (yy + zz)) * sx, (xy - wz) * sy, (xz + wy) * sz, t[0],
         (xy + wz) * sx, (1 - (xx + zz)) * sy, (yz - wx) * sz, t[1],
         (xz - wy) * sx, (yz + wx) * sy, (1 - (xx + yy)) * sz, t[2],
         0.0, 0.0, 0.0, 1.0)
    return tuple(tuple(m[r * 4 + c] for c in range(4)) for r in range(4))


def mat4_transform(m, v):
    """Aplica una 4x4 a un punto (w = 1)."""
    x, y, z = v
    return (m[0][0] * x + m[0][1] * y + m[0][2] * z + m[0][3],
            m[1][0] * x + m[1][1] * y + m[1][2] * z + m[1][3],
            m[2][0] * x + m[2][1] * y + m[2][2] * z + m[2][3])


def mat4_inverse(m):
    """Inversa de una 4x4 por cofactores. Devuelve la de `invers(IBM[k])`."""
    (a00, a01, a02, a03), (a10, a11, a12, a13), \
    (a20, a21, a22, a23), (a30, a31, a32, a33) = m

    b00 = a00 * a11 - a01 * a10
    b01 = a00 * a12 - a02 * a10
    b02 = a00 * a13 - a03 * a10
    b03 = a01 * a12 - a02 * a11
    b04 = a01 * a13 - a03 * a11
    b05 = a02 * a13 - a03 * a12
    b06 = a20 * a31 - a21 * a30
    b07 = a20 * a32 - a22 * a30
    b08 = a20 * a33 - a23 * a30
    b09 = a21 * a32 - a22 * a31
    b10 = a21 * a33 - a23 * a31
    b11 = a22 * a33 - a23 * a32

    det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06
    if not det:
        raise ValueError("matriz singular")
    inv = 1.0 / det

    return (( (a11 * b11 - a12 * b10 + a13 * b09) * inv,
              (-a01 * b11 + a02 * b10 - a03 * b09) * inv,
              (a31 * b05 - a32 * b04 + a33 * b03) * inv,
              (-a21 * b05 + a22 * b04 - a23 * b03) * inv),
            ((-a10 * b11 + a12 * b08 - a13 * b07) * inv,
             (a00 * b11 - a02 * b08 + a03 * b07) * inv,
             (-a30 * b05 + a32 * b02 - a33 * b01) * inv,
             (a20 * b05 - a22 * b02 + a23 * b01) * inv),
            ((a10 * b10 - a11 * b08 + a13 * b06) * inv,
             (-a00 * b10 + a01 * b08 - a03 * b06) * inv,
             (a30 * b04 - a31 * b02 + a33 * b00) * inv,
             (-a20 * b04 + a21 * b02 - a23 * b00) * inv),
            ((-a10 * b09 + a11 * b07 - a12 * b06) * inv,
             (a00 * b09 - a01 * b07 + a02 * b06) * inv,
             (-a30 * b03 + a31 * b01 - a32 * b00) * inv,
             (a20 * b03 - a21 * b01 + a22 * b00) * inv))


def quat_slerp(a, b, t):
    """Slerp de (x, y, z, w), como el interpolant implícito de three.js."""
    ax, ay, az, aw = a
    bx, by, bz, bw = b
    cos = ax * bx + ay * by + az * bz + aw * bw
    if cos < 0.0:                              # camino corto
        bx, by, bz, bw, cos = -bx, -by, -bz, -bw, -cos
    if cos > 0.9995:                           # casi paralelos: lerp normalizado
        return _quat_norm((ax + (bx - ax) * t, ay + (by - ay) * t,
                           az + (bz - az) * t, aw + (bw - aw) * t))
    theta = _acos(cos)
    sin = _sin(theta)
    wa, wb = _sin((1 - t) * theta) / sin, _sin(t * theta) / sin
    return (ax * wa + bx * wb, ay * wa + by * wb,
            az * wa + bz * wb, aw * wa + bw * wb)


def _quat_norm(q):
    x, y, z, w = q
    n = _sqrt(x * x + y * y + z * z + w * w) or 1.0
    return (x / n, y / n, z / n, w / n)


def _acos(x):
    import math
    return math.acos(max(-1.0, min(1.0, x)))


def _sin(x):
    import math
    return math.sin(x)


def _sqrt(x):
    import math
    return math.sqrt(x)


# ── Nodos ─────────────────────────────────────────────────────────────────────

def node_trs(node):
    """(traducción, rotación, escala) de un nodo, con los defaults de glTF.

    Todo pasa por `float`: un JSON reescrito a mano (o el decimal corto que
    escribe `rest_pose.compact`) puede dejar el quaternion como string, y de acá
    en adelante la matemática es numérica.
    """
    return (tuple(float(c) for c in node.get("translation", (0.0, 0.0, 0.0))),
            tuple(float(c) for c in node.get("rotation", IDENTITY_Q)),
            tuple(float(c) for c in node.get("scale", (1.0, 1.0, 1.0))))


def node_by_name(js):
    """{nombre: nodo}. El primero gana si hay nombres repetidos."""
    out = {}
    for index, node in enumerate(js.get("nodes", [])):
        out.setdefault(node.get("name", f"#{index}"), node)
    return out


def local_matrices(js, pose=None):
    """Matriz local de cada nodo, aplicando [pose] encima si viene dado.

    [pose] es `{nombre: (traducción, rotación, escala)}`; los nodos que no estén
    quedan con el TRS del archivo. Es lo que usa `sample_clip` para llevar un
    frame de un `signs/<id>.glb` (que no trae malla pero sí trae todos los
    nodos) al esqueleto del avatar.
    """
    out = []
    for index, node in enumerate(js.get("nodes", [])):
        trs = (pose or {}).get(node.get("name", f"#{index}")) or node_trs(node)
        out.append(mat4_from_trs(*trs))
    return out


def world_poses(js, pose=None, bone_indices=None):
    """FK: `{nombre: (T, R, S)}` en coordenadas de mundo.

    Los `animations` de un export de Cascadeur también mueven la raíz del
    armature, así que la pose se puede armar sobre los nodos del archivo tal
    cual. [bone_indices] filtra el resultado a los huesos de la skin (para
   _medir manos_, por ejemplo).
    """
    nodes = js.get("nodes", [])
    matrices = local_matrices(js, pose)
    children = {index: [] for index in range(len(nodes))}
    parented = set()
    for index, node in enumerate(nodes):
        for child in node.get("children") or []:
            children[index].append(child)
            parented.add(child)
    # Raíz = nodo que ningún otro declara como hijo. Ojo: en este export la
    # raíz (`HG_Branden.001`) **sí** tiene hijos, así que "sin hijos" no sirve
    # para encontrarla.
    roots = [index for index in range(len(nodes)) if index not in parented] or [0]

    wanted = None
    if bone_indices is not None:
        wanted = set(bone_indices)
    result = {}
    # Los hijos se resuelven por índice de nodo, y el orden del archivo no
    # garantiza que el padre venga antes: se recorre desde las raíces.
    stack = [(index, mat4_identity()) for index in reversed(roots)]
    while stack:
        index, parent_world = stack.pop()
        node = nodes[index]
        world = mat4_mul(parent_world, matrices[index])
        name = node.get("name", f"#{index}")
        if wanted is None or index in wanted:
            trs = (pose or {}).get(name)
            if trs is None:
                t, q, s = node_trs(node)
            else:
                t, q, s = trs
            result[name] = (t, q, s, world)
        for child in reversed(children[index]):
            stack.append((child, world))
    return result


def skin_joints(js):
    """Los índices de nodo de los huesos de la primera skin."""
    skins = js.get("skins") or []
    return list(skins[0]["joints"]) if skins else []


def inverse_binds(js, binary):
    """`inverseBindMatrices` de la skin, en el orden de `skin_joints`.

    Salen ya transpostas a row-major (ver [read_accessor_mat4]).
    """
    skins = js.get("skins") or []
    if not skins:
        return []
    ibm = skins[0].get("inverseBindMatrices")
    if ibm is None:
        return [mat4_identity() for _ in skins[0]["joints"]]
    return read_accessor_mat4(js, binary, ibm)


def bone_world_matrices(js, pose=None):
    """`{nombre: matriz de mundo}` de los huesos, con la pose opcional encima.

    Es lo que usa el render offline para la skinning lineal.
    """
    return {name: world[3] for name, world in world_poses(js, pose).items()}


def quat_from_mat3(m):
    """`(x, y, z, w)` de una matriz 3x3 row-major (Shepperd / Kennehan)."""
    trace = m[0][0] + m[1][1] + m[2][2]
    if trace > 0.0:
        s = _sqrt(trace + 1.0) * 2.0
        w = 0.25 * s
        x = (m[2][1] - m[1][2]) / s
        y = (m[0][2] - m[2][0]) / s
        z = (m[1][0] - m[0][1]) / s
    elif m[0][0] > m[1][1] and m[0][0] > m[2][2]:
        s = _sqrt(1.0 + m[0][0] - m[1][1] - m[2][2]) * 2.0
        w = (m[2][1] - m[1][2]) / s
        x = 0.25 * s
        y = (m[0][1] + m[1][0]) / s
        z = (m[0][2] + m[2][0]) / s
    elif m[1][1] > m[2][2]:
        s = _sqrt(1.0 + m[1][1] - m[0][0] - m[2][2]) * 2.0
        w = (m[0][2] - m[2][0]) / s
        x = (m[0][1] + m[1][0]) / s
        y = 0.25 * s
        z = (m[1][2] + m[2][1]) / s
    else:
        s = _sqrt(1.0 + m[2][2] - m[0][0] - m[1][1]) * 2.0
        w = (m[1][0] - m[0][1]) / s
        x = (m[0][2] + m[2][0]) / s
        y = (m[1][2] + m[2][1]) / s
        z = 0.25 * s
    return _quat_norm((x, y, z, w))


def mat4_decompose(m):
    """`(T, R, S)` de una matriz 4x4 row-major compuesta como T · R · S."""
    # En T·R·S la columna j de la matriz es la j de R por s_j: la escala es la
    # norma de cada columna y la rotación sale dividiendo por ella.
    scale = [_sqrt(m[0][c] ** 2 + m[1][c] ** 2 + m[2][c] ** 2) or 1.0
             for c in range(3)]
    rot = [[m[r][c] / scale[c] for c in range(3)] for r in range(3)]
    det = (rot[0][0] * (rot[1][1] * rot[2][2] - rot[1][2] * rot[2][1])
           - rot[0][1] * (rot[1][0] * rot[2][2] - rot[1][2] * rot[2][0])
           + rot[0][2] * (rot[1][0] * rot[2][1] - rot[1][1] * rot[2][0]))
    if det < 0.0:                                  # reflexión: el signo va en x
        scale[0] = -scale[0]
        for r in range(3):
            rot[r][0] = -rot[r][0]
    return ((m[0][3], m[1][3], m[2][3]), quat_from_mat3(rot), tuple(scale))


def bind_pose(js, binary):
    """La pose de bind reconstruida desde las `inverseBindMatrices` del export.

    Sirve para dos cosas:

      1. **Verificar** que el TRS de los nodos *es* la pose de bind (la razón por
         la que cambiar esas rotaciones re-posea al personaje en vez de deformar
         la malla: ver [bind_pose_error]).
      2. **Volver a ver el reposo anterior** una vez que la pose nueva esté
         horneada, que es el único modo de comparar las dos sin tener el
         `.glb` viejo a mano.

    Los nodos que no son huesos (pelo, globos) no aparecen en las IBM: se les
    deja el TRS que ya tenían, que la pose de descanso nunca toca.
    """
    joints = skin_joints(js)
    binds = inverse_binds(js, binary)
    by_name = {js["nodes"][index].get("name"): mat4_inverse(binds[k])
               for k, index in enumerate(joints)}

    nodes = js.get("nodes", [])
    local_matrices = [mat4_from_trs(*node_trs(node)) for node in nodes]
    children, parented = {}, set()
    for index, node in enumerate(nodes):
        children[index] = list(node.get("children") or [])
        parented.update(children[index])
    roots = [index for index in range(len(nodes)) if index not in parented] or [0]

    world = [None] * len(nodes)
    local = [None] * len(nodes)
    stack = [(index, mat4_identity()) for index in reversed(roots)]
    while stack:
        index, parent_world = stack.pop()
        name = nodes[index].get("name")
        world[index] = by_name.get(name) or mat4_mul(parent_world, local_matrices[index])
        local[index] = mat4_mul(mat4_inverse(parent_world), world[index])
        for child in reversed(children[index]):
            stack.append((child, world[index]))

    pose = {}
    for index, node in enumerate(nodes):
        name = node.get("name", f"#{index}")
        # Los huesos se reconstruyen desde las IBM; los demás nodos (pelo,
        # globos) se dejan como estaban, porque la pose de descanso nunca los
        # toca y su TRS del archivo sigue siendo válido.
        pose[name] = mat4_decompose(local[index]) if name in by_name else node_trs(node)
    return pose


def bind_pose_error(js, binary, pose=None):
    """`max |FK(pose) - invers(IBM)|` sobre los huesos, en unidades del export.

    Con la pose por defecto es ~6e-6 (el ruido de float32 del export). Es la
    medición que respalda "cambiar el TRS de los nodos re-posea, no deforma":
    si algún día dejara de cumplirse, la pose horneada estaría rompiendo la
    malla en vez de re-posarla.
    """
    joints = skin_joints(js)
    binds = inverse_binds(js, binary)
    world = world_poses(js, pose)
    worst = 0.0
    for k, index in enumerate(joints):
        bind = mat4_inverse(binds[k])
        matrix = world[js["nodes"][index].get("name")][3]
        for r in range(4):
            for c in range(4):
                worst = max(worst, abs(bind[r][c] - matrix[r][c]))
    return worst


# ── Clips ─────────────────────────────────────────────────────────────────────

def clip_tracks(js, binary, anim_index=0):
    """Los canales de una animación, ya resueltos a listas de valores.

    Devuelve `[(nombre_de_nodo, path, times, values, interpolación)]`, con
    `values` como lista de tuplas (una por keyframe).
    """
    anim = js["animations"][anim_index]
    out = []
    for channel in anim["channels"]:
        sampler = anim["samplers"][channel["sampler"]]
        node_index = channel["target"]["node"]
        name = js["nodes"][node_index].get("name", f"#{node_index}")
        path = channel["target"]["path"]
        interp = sampler.get("interpolation", "LINEAR")

        times = read_accessor(js, binary, sampler["input"])
        flat = read_accessor(js, binary, sampler["output"])
        size = COMPONENTS[js["accessors"][sampler["output"]]["type"]]

        if interp == "CUBICSPLINE":
            # 3 valores por keyframe (in, valor, out); el del medio es el keyframe.
            values = [tuple(flat[i * size + size: i * size + 2 * size])
                      for i in range(len(times))]
        else:
            values = [tuple(flat[i * size:(i + 1) * size]) for i in range(len(times))]

        out.append((name, path, times, values, interp))
    return out


def sample_clip(js, binary, anim_index=0, t=0.0):
    """La pose del clip en el instante [t], como `{nombre: (T, R, S)}`.

    Empieza del TRS de los nodos del propio archivo y lo pisa con lo que el clip
    anima: por eso devuelve **los tres canales por nodo**, no solo la rotación.
    """
    base = {node.get("name", f"#{i}"): node_trs(node)
            for i, node in enumerate(js.get("nodes", []))}
    pose = {name: list(trs) for name, trs in base.items()}

    for name, path, times, values, _interp in clip_tracks(js, binary, anim_index):
        channel = _POSE_CHANNEL.get(path)
        if channel is None:
            # `weights` (morph targets) no es TRS: un avatar con expresión facial
            # animada trae canales así y no hay dónde ponerlos en una pose de
            # huesos. Se ignoran, que es lo que hace que una seña con cara se
            # pueda leer igual.
            continue
        value = _sample_track(times, values, t, path == "rotation")
        pose[name][channel] = value

    return {name: tuple(trs) for name, trs in pose.items()}


def _sample_track(times, values, t, is_rotation):
    """Interpola un canal en [t]. [times] viene ordenado y sin duplicados."""
    if t <= times[0]:
        return values[0]
    if t >= times[-1]:
        return values[-1]

    lo, hi = 0, len(times) - 1
    while hi - lo > 1:                         # búsqueda binaria
        mid = (lo + hi) // 2
        if times[mid] <= t:
            lo = mid
        else:
            hi = mid
    span = times[hi] - times[lo]
    ratio = (t - times[lo]) / span if span else 0.0
    if is_rotation:
        return quat_slerp(values[lo], values[hi], ratio)
    a, b = values[lo], values[hi]
    return tuple(a[i] + (b[i] - a[i]) * ratio for i in range(len(a)))


def clip_duration(js, anim_index=0):
    """Duración del clip en segundos, del accessor de input."""
    anim = js["animations"][anim_index]
    acc = js["accessors"][anim["samplers"][0]["input"]]
    return acc["max"][0] - acc["min"][0]


def first_keyframe_time(js, anim_index=0):
    """El `t` del primer keyframe del clip (no 0: los exports no arrancan en 0)."""
    anim = js["animations"][anim_index]
    acc = js["accessors"][anim["samplers"][0]["input"]]
    return acc["min"][0]


def animated_nodes(js, anim_index=0):
    """Los nombres de nodo que el clip mueve, en cualquier canal."""
    anim = js["animations"][anim_index]
    return {js["nodes"][c["target"]["node"]].get("name", "")
            for c in anim["channels"]}
