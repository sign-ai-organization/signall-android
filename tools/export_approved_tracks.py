#!/usr/bin/env python3
"""Congela los clips elegidos de la revisión visual en approved_tracks.json.gz.

python3 tools/export_approved_tracks.py --candidates /carpeta/de/visores
Solo para una nueva aprobación explícita; el empaquetador normal no lo ejecuta.
La base se regenera en memoria desde los originales, sin editar assets.

Los `despues.glb` de los visores **no se versionan**: el archivo congelado es la
fuente de lo aprobado y lo único que hace falta para empaquetar. Regenerarlo de
cero exige esas copias y la aprobación del owner; no se hace por rutina.
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path

import approved_tracks as at
import glb_pose as gp
import pack_avatars as pack


def export(candidates, output):
    result = {'format': 1, 'approval': '2026-11-11: 50 señas, entradas 01–05; 0020 v3 y 0028 completas',
              'source_repo': '../signall-avatar', 'source_commit': 'fc6f372c8412010d85925fe342fdcf0c073fbdf1',
              'avatars': {}}
    for avatar, label, filename in pack.AVATARS:
        source = pack.SOURCE_DEFAULT / filename
        base, binary = gp.read_glb(source)
        pack.check_source(base, label)
        pack.bake_rest_pose(base, binary, avatar, pack.sha256(source))
        base, binary = pack.ps.repack(base, binary, pack.ps.morphs_to_keep(base, binary))
        baseline_sha = at.glb_sha256(base, binary)
        tracks, provenance, cache = [], {}, {}
        for animation in base['animations']:
            sign = int(animation['name'].split('_')[0])
            group = f'entries-{sign // 10 + 1:02d}' if sign != 20 else '0020-smooth-v3'
            if group not in cache:
                path = candidates / group / f'{avatar}-despues.glb'
                raw = path.read_bytes()
                js, data = gp.read_glb(path)
                # Los candidatos se armaron sobre la base compactada: los valores
                # de las curvas son los mismos, pero el binario no es prefijo.
                assert js['nodes'] == base['nodes'] and data[:len(binary)] == binary
                cache[group] = (js, data, {a['name']: a for a in js['animations']})
                provenance[group] = hashlib.sha256(raw).hexdigest()
            js, data, clips = cache[group]
            selected = clips[animation['name']]
            assert animation['channels'] == selected['channels']
            assert len(animation['samplers']) == len(selected['samplers'])
            channels = {c['sampler']: c for c in animation['channels']}
            assert len(channels) == len(animation['channels'])
            for index, (old, new) in enumerate(zip(animation['samplers'], selected['samplers'])):
                assert old.get('interpolation', 'LINEAR') == new.get('interpolation', 'LINEAR')
                track = {'clip': animation['name'], 'sampler': index,
                         'node': base['nodes'][channels[index]['target']['node']]['name'],
                         'path': channels[index]['target']['path']}
                changed = False
                for field in ('input', 'output'):
                    before = gp.read_accessor(base, binary, old[field])
                    after = gp.read_accessor(js, data, new[field])
                    track[f'{field}_sha256'] = at.values_hash(before)
                    if before != after:
                        track[field] = after
                        changed = True
                if changed:
                    tracks.append(track)
        assert len({t['clip'] for t in tracks}) == 200
        result['avatars'][avatar] = {'baseline_sha256': baseline_sha, 'approved_sources': provenance, 'tracks': tracks}
        print(f'{avatar}: 200 clips, {len(tracks)} samplers distintos; base {baseline_sha}')
    raw = json.dumps(result, separators=(',', ':'), ensure_ascii=False).encode('utf-8')
    output.write_bytes(gzip.compress(raw, mtime=0))
    print(f'{output}: {output.stat().st_size} bytes')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidates', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=at.PATCH)
    args = parser.parse_args()
    export(args.candidates, args.output)
