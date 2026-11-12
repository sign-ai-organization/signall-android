"""Prueba aislada: modifica únicamente los extremos de 0000, no producción."""
import copy
import hashlib
import json
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path.cwd() / 'tools'))
import glb_pose as g

out = Path(__file__).parent
report = []
for avatar in ('juan', 'pauline'):
    src = Path(f'app/src/main/assets/web/models/{avatar}.glb')
    original_bytes = src.read_bytes()
    j, binary = g.read_glb(src)
    original = copy.deepcopy(j)
    data = bytearray(binary)
    touched = 0
    for animation in j['animations']:
        if not animation['name'].startswith('0000_'):
            continue
        for channel in animation['channels']:
            node = j['nodes'][channel['target']['node']]
            if channel['target']['path'] != 'rotation' or node.get('name') not in {
                f'{bone}.{side}' for bone in ('upper_arm', 'forearm', 'hand') for side in ('L', 'R')
            }:
                continue
            sampler = animation['samplers'][channel['sampler']]
            assert sampler.get('interpolation', 'LINEAR') == 'LINEAR'
            times = g.read_accessor(j, binary, sampler['input'])
            old = g.read_accessor(j, binary, sampler['output'])
            values = []
            for i, t in enumerate(times):
                u = (t - times[0]) / (times[-1] - times[0])
                distance = min(u, 1 - u)
                q = old[4*i:4*i+4]
                if distance < .2:
                    x = max(0., min(1., distance / .2))
                    weight = 1 - (6*x**5 - 15*x**4 + 10*x**3)
                    q = g.quat_slerp(q, node['rotation'], weight)
                else:
                    assert q == old[4*i:4*i+4]
                values.extend(q)
            while len(data) % 4:
                data.append(0)
            raw = struct.pack(f'<{len(values)}f', *values)
            view = len(j['bufferViews'])
            j['bufferViews'].append({'buffer': 0, 'byteOffset': len(data), 'byteLength': len(raw)})
            data.extend(raw)
            accessor = copy.deepcopy(j['accessors'][sampler['output']])
            accessor.update(bufferView=view, byteOffset=0)
            accessor.pop('min', None)
            accessor.pop('max', None)
            sampler['output'] = len(j['accessors'])
            j['accessors'].append(accessor)
            touched += 1
    j['buffers'][0]['byteLength'] = len(data)
    assert touched == 24
    for before, after in zip(original['animations'], j['animations']):
        if not before['name'].startswith('0000_'):
            assert before == after
    assert j['nodes'] == original['nodes']
    assert bytes(data[:len(binary)]) == binary
    (out / f'{avatar}-antes.glb').write_bytes(original_bytes)
    g.write_glb(out / f'{avatar}-despues.glb', j, data)
    assert src.read_bytes() == original_bytes
    report.append({'avatar': avatar, 'input_sha256': hashlib.sha256(original_bytes).hexdigest(),
                   'sample': '0000, cuatro variantes faciales', 'modified_rotation_tracks': touched,
                   'untouched_middle': '20%–80% de cada canal',
                   'other_196_animations_unchanged': True, 'original_unchanged': True})
(out / 'verification.json').write_text(json.dumps(report, indent=2, ensure_ascii=False))
print(json.dumps(report, indent=2, ensure_ascii=False))
