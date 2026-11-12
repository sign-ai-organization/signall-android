"""Aplica curvas aprobadas, congeladas sobre un empaquetado base exacto.

No depende de /tmp ni de los visores: approved_tracks.json.gz conserva solo los
samplers diferentes de las copias elegidas, con hashes de base y procedencia.
"""
import copy
import gzip
import hashlib
import json
import math
import struct
from pathlib import Path

import glb_pose as gp

PATCH = Path(__file__).with_name('approved_tracks.json.gz')


def glb_sha256(js, binary):
    text = json.dumps(js, separators=(',', ':')).encode('utf-8')
    text += b' ' * (-len(text) % 4)
    padded = binary + b'\0' * (-len(binary) % 4)
    digest = hashlib.sha256()
    for chunk in (struct.pack('<III', 0x46546C67, 2, 28 + len(text) + len(padded)),
                  struct.pack('<II', len(text), 0x4E4F534A), text,
                  struct.pack('<II', len(padded), 0x004E4942), padded):
        digest.update(chunk)
    return digest.hexdigest()


def values_hash(values):
    return hashlib.sha256(struct.pack(f'<{len(values)}f', *values)).hexdigest()


def append_accessor(js, data, template, values, is_time=False):
    if template['componentType'] != 5126 or not all(math.isfinite(v) for v in values):
        raise ValueError('curva aprobada inválida: solo float32 finitos')
    width = gp.COMPONENTS[template['type']]
    if not values or len(values) % width:
        raise ValueError('curva aprobada con longitud inválida')
    while len(data) % 4:
        data.append(0)
    raw = struct.pack(f'<{len(values)}f', *values)
    view = len(js['bufferViews'])
    js['bufferViews'].append({'buffer': 0, 'byteOffset': len(data), 'byteLength': len(raw)})
    data.extend(raw)
    accessor = copy.deepcopy(template)
    accessor.update(bufferView=view, byteOffset=0, count=len(values) // width)
    accessor.pop('min', None)
    accessor.pop('max', None)
    if is_time:
        if any(x >= y for x, y in zip(values, values[1:])):
            raise ValueError('tiempos no crecientes')
        accessor.update(min=[values[0]], max=[values[-1]])
    index = len(js['accessors'])
    js['accessors'].append(accessor)
    return index


def rewrite_animation_accessors(js, data):
    """Reescribe los accessors de animación vivos, uno por view y deduplicados.

    `repack` copia por view: si los samplers comparten un view grande, los bytes
    reemplazados quedan dentro del rango copiado. Acá cada curva viva pasa a su
    propio view, y las iguales (las cuatro variantes faciales comparten huesos)
    apuntan al mismo accessor. Así el compactado descarta lo viejo de verdad.
    """
    seen = {}
    remapped = {}
    data = bytearray(data)
    for animation in js['animations']:
        for field in ('input', 'output'):
            for sampler in animation['samplers']:
                index = sampler[field]
                if index in remapped:
                    sampler[field] = remapped[index]
                    continue
                values = gp.read_accessor(js, data, index)
                key = (tuple(values) if len(values) <= 4096 else values_hash(values),
                       js['accessors'][index]['type'], js['accessors'][index]['componentType'])
                if key in seen:
                    remapped[index] = seen[key]
                else:
                    remapped[index] = append_accessor(
                        js, data, js['accessors'][index], values, field == 'input')
                    seen[key] = remapped[index]
                sampler[field] = remapped[index]
    js['buffers'][0]['byteLength'] = len(data)
    return bytes(data), len(seen)


def apply(js, binary, avatar_id):
    config = json.loads(gzip.decompress(PATCH.read_bytes()))
    if config['format'] != 1:
        raise ValueError('versión desconocida de curvas aprobadas')
    approved = config['avatars'][avatar_id]
    if glb_sha256(js, binary) != approved['baseline_sha256']:
        raise ValueError(f'{avatar_id}: cambió el empaquetado base; revisar las curvas aprobadas')
    clips = {a['name']: a for a in js['animations']}
    data = bytearray(binary)
    for track in approved['tracks']:
        animation = clips[track['clip']]
        sampler = animation['samplers'][track['sampler']]
        channel = next(c for c in animation['channels'] if c['sampler'] == track['sampler'])
        target = (js['nodes'][channel['target']['node']]['name'], channel['target']['path'])
        if target != (track['node'], track['path']):
            raise ValueError('el sampler aprobado apunta a otro nodo/canal')
        for field in ('input', 'output'):
            before = gp.read_accessor(js, binary, sampler[field])
            if values_hash(before) != track[f'{field}_sha256']:
                raise ValueError(f'{avatar_id}/{track["clip"]}: cambió una curva base')
        times = track.get('input', gp.read_accessor(js, binary, sampler['input']))
        values = track.get('output', gp.read_accessor(js, binary, sampler['output']))
        old_times = gp.read_accessor(js, binary, sampler['input'])
        old_values = gp.read_accessor(js, binary, sampler['output'])
        width = len(old_values) // len(old_times)
        if len(values) != len(times) * width:
            raise ValueError('conteo input/output inconsistente')
        if track['path'] == 'rotation':
            if any(abs(sum(v*v for v in values[i:i+4]) - 1) > 1e-5
                   for i in range(0, len(values), 4)):
                raise ValueError('cuaternión aprobado no normalizado')
        for field in ('input', 'output'):
            if field in track:
                sampler[field] = append_accessor(js, data, js['accessors'][sampler[field]],
                                                 track[field], field == 'input')
    js['buffers'][0]['byteLength'] = len(data)
    return bytes(data), {'approval': config['approval'], 'patch_sha256': hashlib.sha256(PATCH.read_bytes()).hexdigest(),
                         'samplers': len(approved['tracks']), 'clips': len(clips)}
