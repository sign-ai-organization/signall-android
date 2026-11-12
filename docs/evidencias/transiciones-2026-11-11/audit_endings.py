"""Audita los extremos sin editar los assets; ejecutar desde la raíz del repo."""
import hashlib
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path.cwd() / 'tools'))
import glb_pose as g


def degrees(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    norm = math.sqrt(sum(x*x for x in a) * sum(x*x for x in b))
    return round(math.degrees(2 * math.acos(min(1., abs(dot) / norm))), 3)


results = []
names = {f'{bone}.{side}' for bone in ('upper_arm', 'forearm') for side in ('L', 'R')}
for avatar in ('juan', 'pauline'):
    path = Path(f'app/src/main/assets/web/models/{avatar}.glb')
    j, binary = g.read_glb(path)
    reference = next(a for a in j['animations'] if a['name'] == '0029_afirmativo')
    old_rest = {}
    for c in reference['channels']:
        name = j['nodes'][c['target']['node']].get('name')
        if c['target']['path'] == 'rotation' and name in names:
            sampler = reference['samplers'][c['sampler']]
            old_rest[name] = g.read_accessor(j, binary, sampler['output'])[:4]
    rows = []
    for animation in j['animations']:
        row = {'clip': animation['name'], 'duration': 0, 'arms': {}}
        for c in animation['channels']:
            n = j['nodes'][c['target']['node']]
            sampler = animation['samplers'][c['sampler']]
            times = g.read_accessor(j, binary, sampler['input'])
            row['duration'] = max(row['duration'], times[-1])
            if c['target']['path'] != 'rotation' or n.get('name') not in names:
                continue
            rotations = g.read_accessor(j, binary, sampler['output'])
            row['arms'][n['name']] = {
                'end_to_old_rest_degrees': degrees(rotations[-4:], old_rest[n['name']]),
                'last_step_degrees': degrees(rotations[-4:], rotations[-8:-4]),
            }
        rows.append(row)
    results.append({'avatar': avatar, 'asset_sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                    'reference': '0029_afirmativo, primer keyframe (reposo anterior)',
                    'clips': rows})
output = Path(__file__).with_name('all-clips-audit.json')
output.write_text(json.dumps(results, indent=2, ensure_ascii=False))
print(f'Auditados {sum(len(r["clips"]) for r in results)} clips; informe: {output}')
