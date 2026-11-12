#!/usr/bin/env python3
"""Compara los 400 clips integrados contra las copias visualmente elegidas."""
import argparse
import hashlib
import json
from pathlib import Path

import glb_pose as gp
import approved_tracks as at


def check(candidates):
    root = Path(__file__).resolve().parent.parent
    web = root / 'app/src/main/assets/web'
    reports = []
    for avatar in ('juan', 'pauline'):
        path = web / 'models' / f'{avatar}.glb'
        js, binary = gp.read_glb(path)
        cache, count = {}, 0
        for clip in js['animations']:
            sign = int(clip['name'].split('_')[0])
            group = f'entries-{sign // 10 + 1:02d}' if sign != 20 else '0020-smooth-v3'
            if group not in cache:
                source, data = gp.read_glb(candidates / group / f'{avatar}-despues.glb')
                assert source['nodes'] == js['nodes']
                cache[group] = (source, data, {a['name']: a for a in source['animations']})
            source, data, clips = cache[group]
            chosen = clips[clip['name']]
            assert chosen['channels'] == clip['channels']
            for old, new in zip(chosen['samplers'], clip['samplers']):
                assert old.get('interpolation', 'LINEAR') == new.get('interpolation', 'LINEAR')
                for field in ('input', 'output'):
                    assert gp.read_accessor(source, data, old[field]) == gp.read_accessor(js, binary, new[field]), (avatar, clip['name'], field)
            count += 1
        assert count == 200
        reports.append({'avatar': avatar, 'clips_exact_to_approved': count,
                        'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'bytes': path.stat().st_size})
    manifest = json.loads((web / 'manifest.json').read_text())
    assert len(manifest) == 50
    assert all(m['sha256'] == reports[0]['sha256'] and m['bytes'] == reports[0]['bytes'] for m in manifest)
    # Aplicar dos veces debe rechazarse, no duplicar prefijos ni suavizados.
    js, binary = gp.read_glb(web / 'models/juan.glb')
    try:
        at.apply(js, binary, 'juan')
    except ValueError as exc:
        assert 'base' in str(exc)
    else:
        raise AssertionError('aceptó aplicar dos veces las curvas aprobadas')
    print(json.dumps(reports, indent=2))
    return reports


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidates', type=Path, required=True)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    report = check(args.candidates)
    if args.report:
        args.report.write_text(json.dumps(report, indent=2) + '\n')
