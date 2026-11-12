"""Copia aislada: sustituir solo primera postura TRS de 0028 por la segunda.

Parte de grupo 07 aprobado, no de las fuentes originales ni de los assets app.
Ejecutar desde la raíz del repo. No cambia las curvas posteriores ni los tiempos.
"""
import copy
import hashlib
import json
import struct
import sys
from pathlib import Path
sys.path.insert(0,str(Path.cwd()/'tools'))
import glb_pose as g

folder=Path(__file__).parent
report=[]
for avatar in ('juan','pauline'):
    source=folder.parent/'group-07'/f'{avatar}-despues.glb'
    raw=source.read_bytes()
    j,binary=g.read_glb(source)
    original=copy.deepcopy(j)
    data=bytearray(binary)
    changes=[]
    for animation in j['animations']:
        if not animation['name'].startswith('0028_'):continue
        handled=set()
        for channel in animation['channels']:
            path=channel['target']['path']
            if path not in ('translation','rotation','scale'):continue
            index=channel['sampler']
            if index in handled:continue
            handled.add(index)
            sampler=animation['samplers'][index]
            assert sampler.get('interpolation','LINEAR') in ('LINEAR','STEP')
            accessor=j['accessors'][sampler['output']]
            comps=g.COMPONENTS[accessor['type']]
            times=g.read_accessor(j,binary,sampler['input'])
            values=g.read_accessor(j,binary,sampler['output'])
            assert len(times)>=2 and len(values)==len(times)*comps
            first=values[:comps]
            if first==values[comps:comps*2]:continue
            if abs(times[1]-2/24)>=1e-6:
                # Canales compactados entre extremos del clip: no tienen un
                # salto en la segunda muestra temporal, no se editan.
                assert len(times)==2 and times[1]>2
                continue
            assert abs(times[0]-1/24)<1e-6 and abs(times[1]-2/24)<1e-6
            values[:comps]=values[comps:comps*2]
            while len(data)%4:data.append(0)
            packed=struct.pack(f'<{len(values)}f',*values)
            view=len(j['bufferViews'])
            j['bufferViews'].append({'buffer':0,'byteOffset':len(data),'byteLength':len(packed)})
            data.extend(packed)
            updated=copy.deepcopy(accessor)
            updated.update(bufferView=view,byteOffset=0)
            updated.pop('min',None)
            updated.pop('max',None)
            sampler['output']=len(j['accessors'])
            j['accessors'].append(updated)
            changes.append({'clip':animation['name'],'node':j['nodes'][channel['target']['node']]['name'],
                            'path':path,'before':first,'after':values[:comps]})
    j['buffers'][0]['byteLength']=len(data)
    assert changes and j['nodes']==original['nodes'] and data[:len(binary)]==binary
    for before,after in zip(original['animations'],j['animations']):
        if not before['name'].startswith('0028_'):assert before==after
        else:
            assert before['channels']==after['channels']
            for old,new in zip(before['samplers'],after['samplers']):
                assert old['input']==new['input']
                if old==new:continue
                old_values=g.read_accessor(original,binary,old['output'])
                new_values=g.read_accessor(j,data,new['output'])
                comps=g.COMPONENTS[j['accessors'][new['output']]['type']]
                assert old_values[comps:]==new_values[comps:]
                assert new_values[:comps]==old_values[comps:2*comps]
    (folder/f'{avatar}-antes.glb').write_bytes(raw)
    g.write_glb(folder/f'{avatar}-despues.glb',j,data)
    assert source.read_bytes()==raw
    report.append({'avatar':avatar,'source':'grupo 07 aprobado, con regreso ya corregido',
                   'input_sha256':hashlib.sha256(raw).hexdigest(),
                   'windows':{'0028':[1/24,2/24]},
                   'changed_first_samples':len(changes),'all_later_samples_exact':True,
                   'weights_untouched':True,'other_196_animations_exact':True,
                   'times_and_nodes_exact':True,'source_unchanged':True,'changes':changes})
(folder/'verification.json').write_text(json.dumps(report,indent=2))
print(json.dumps([{k:v for k,v in r.items() if k!='changes'} for r in report],indent=2))
