"""0028: entrada directa desde reposo nuevo, sobre la copia sin salto aprobada."""
import copy
import hashlib
import json
import struct
import sys
from pathlib import Path
sys.path.insert(0,str(Path.cwd()/'tools'))
import glb_pose as g

folder=Path(__file__).parent
BEGIN=10/24
JOIN=18/24
names={f'{bone}.{side}' for bone in ('upper_arm','forearm','hand') for side in ('L','R')}
reports=[]
for avatar in ('juan','pauline'):
    source=folder.parent/'0028-start'/f'{avatar}-despues.glb'
    raw=source.read_bytes()
    j,b=g.read_glb(source)
    original=copy.deepcopy(j)
    data=bytearray(b)
    count=0
    for animation in j['animations']:
        if not animation['name'].startswith('0028_'):continue
        for channel in animation['channels']:
            node=j['nodes'][channel['target']['node']]
            if channel['target']['path']!='rotation' or node.get('name') not in names:continue
            sampler=animation['samplers'][channel['sampler']]
            assert sampler.get('interpolation','LINEAR')=='LINEAR'
            times=g.read_accessor(j,b,sampler['input'])
            values=g.read_accessor(j,b,sampler['output'])
            anchor=min(range(len(times)),key=lambda i:abs(times[i]-JOIN))
            assert abs(times[anchor]-JOIN)<1e-6
            target=values[4*anchor:4*anchor+4]
            rest=node['rotation']
            before=values[:]
            for i,t in enumerate(times):
                if t>=JOIN-1e-6:continue
                if t<=BEGIN+1e-6:q=rest
                else:
                    x=(t-BEGIN)/(JOIN-BEGIN)
                    weight=6*x**5-15*x**4+10*x**3
                    q=g.quat_slerp(rest,target,weight)
                values[4*i:4*i+4]=q
            # Comprobación de muestras: unión y gesto/regreso posteriores exactos.
            for i,t in enumerate(times):
                q=values[4*i:4*i+4]
                assert abs(sum(x*x for x in q)-1)<1e-5
                if t>=JOIN-1e-6:assert q==before[4*i:4*i+4]
                if t<=BEGIN+1e-6:assert q==rest
            while len(data)%4:data.append(0)
            packed=struct.pack(f'<{len(values)}f',*values)
            view=len(j['bufferViews'])
            j['bufferViews'].append({'buffer':0,'byteOffset':len(data),'byteLength':len(packed)})
            data.extend(packed)
            accessor=copy.deepcopy(j['accessors'][sampler['output']])
            accessor.update(bufferView=view,byteOffset=0)
            accessor.pop('min',None);accessor.pop('max',None)
            sampler['output']=len(j['accessors']);j['accessors'].append(accessor)
            count+=1
    assert count==24
    j['buffers'][0]['byteLength']=len(data)
    assert j['nodes']==original['nodes'] and data[:len(b)]==b
    for before,after in zip(original['animations'],j['animations']):
        assert before['channels']==after['channels']
        if not before['name'].startswith('0028_'):assert before==after
    (folder/f'{avatar}-antes.glb').write_bytes(raw)
    g.write_glb(folder/f'{avatar}-despues.glb',j,data)
    assert source.read_bytes()==raw
    reports.append({'avatar':avatar,'source':'0028-start: inicio sin salto aprobado',
                    'input_sha256':hashlib.sha256(raw).hexdigest(),
                    'windows':{'0028':[BEGIN,JOIN]},'changed_rotation_tracks':count,
                    'starts_in_approved_rest':True,'all_samples_from_join_exact':True,
                    'approved_return_exact':True,'other_196_animations_exact':True,
                    'other_channels_and_nodes_exact':True,'times_exact':True,
                    'source_unchanged':True,'entry_limits_provisional':True})
(folder/'verification.json').write_text(json.dumps(reports,indent=2))
print(json.dumps(reports,indent=2))
