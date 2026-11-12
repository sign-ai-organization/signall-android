"""0020 v2: suavizar coordinación corporal temprana sobre la propuesta anterior.

Solo copia, desde raíz del repo. No cambia canales de brazos, manos, dedos/cara.
"""
import copy
import hashlib
import json
import math
import struct
import sys
from pathlib import Path
sys.path.insert(0,str(Path.cwd()/'tools'))
import glb_pose as g

OUT=Path(__file__).parent
BEGIN=36/24
END=51/24
FADE=3/24
BODY={'spine','spine.001','spine.002','spine.003','neck','head','shoulder.L','shoulder.R',
      'thigh.L','thigh.R','shin.L','shin.R','foot.L','foot.R','toe.L','toe.R','heel.02.L','heel.02.R'}
KERNEL=(1,2,3,2,1)

def ease(x):
    x=max(0,min(1,x))
    return 6*x**5-15*x**4+10*x**3

reports=[]
for avatar in ('juan','pauline'):
    source=OUT.parent/'0020-smooth'/f'{avatar}-despues.glb'
    raw=source.read_bytes();j,b=g.read_glb(source);original=copy.deepcopy(j)
    data=bytearray(b);changes=[]
    for animation in j['animations']:
        if not animation['name'].startswith('0020_'):continue
        for c in animation['channels']:
            node=j['nodes'][c['target']['node']]['name'];path=c['target']['path']
            if node not in BODY or path not in ('rotation','translation'):continue
            sampler=animation['samplers'][c['sampler']]
            assert sampler.get('interpolation','LINEAR')=='LINEAR'
            times=g.read_accessor(j,b,sampler['input']);old=g.read_accessor(j,b,sampler['output'])
            # Canales constantes/compactados no tienen la sacudida por frame.
            if len(times)<=2:continue
            assert all(abs(y-x-1/24)<1e-6 for x,y in zip(times,times[1:]))
            width=len(old)//len(times);values=old[:]
            for i,t in enumerate(times):
                if t<=BEGIN+1e-6 or t>=END-1e-6:continue
                current=old[i*width:(i+1)*width]
                total=[0.]*width
                for offset,weight in zip(range(-2,3),KERNEL):
                    k=max(0,min(len(times)-1,i+offset));q=old[k*width:(k+1)*width]
                    sign=-1 if path=='rotation' and sum(x*y for x,y in zip(q,current))<0 else 1
                    for axis in range(width):total[axis]+=q[axis]*weight*sign
                averaged=[v/sum(KERNEL) for v in total]
                strength=min(ease((t-BEGIN)/FADE),ease((END-t)/FADE))
                if path=='rotation':
                    length=math.sqrt(sum(v*v for v in averaged));assert length>1e-8
                    averaged=[v/length for v in averaged]
                    adjusted=g.quat_slerp(current,averaged,strength)
                    assert abs(sum(v*v for v in adjusted)-1)<1e-5
                else:adjusted=[x+(y-x)*strength for x,y in zip(current,averaged)]
                values[i*width:(i+1)*width]=adjusted
            if values==old:continue
            for i,t in enumerate(times):
                if t<=BEGIN+1e-6 or t>=END-1e-6:assert values[i*width:(i+1)*width]==old[i*width:(i+1)*width]
            while len(data)%4:data.append(0)
            packed=struct.pack(f'<{len(values)}f',*values);view=len(j['bufferViews'])
            j['bufferViews'].append({'buffer':0,'byteOffset':len(data),'byteLength':len(packed)});data.extend(packed)
            accessor=copy.deepcopy(j['accessors'][sampler['output']])
            accessor.update(bufferView=view,byteOffset=0);accessor.pop('min',None);accessor.pop('max',None)
            sampler['output']=len(j['accessors']);j['accessors'].append(accessor)
            changes.append({'clip':animation['name'],'node':node,'path':path})
    j['buffers'][0]['byteLength']=len(data)
    assert changes and j['nodes']==original['nodes'] and data[:len(b)]==b
    for before,after in zip(original['animations'],j['animations']):
        assert before['channels']==after['channels']
        if not before['name'].startswith('0020_'):assert before==after
    (OUT/f'{avatar}-antes.glb').write_bytes(raw)
    g.write_glb(OUT/f'{avatar}-despues.glb',j,data)
    assert source.read_bytes()==raw
    reports.append({'avatar':avatar,'source':'0020-smooth v1, propuesta aún no aprobada',
                    'input_sha256':hashlib.sha256(raw).hexdigest(),'windows':{'0020':[BEGIN,END]},
                    'kernel':KERNEL,'taper_seconds':FADE,'changed_tracks':len(changes),'changes':changes,
                    'entry_and_arm_curves_exact':True,'fingers_and_face_exact':True,
                    'other_196_animations_exact':True,'times_and_nodes_exact':True,
                    'source_unchanged':True,'limits_provisional':True})
(OUT/'verification.json').write_text(json.dumps(reports,indent=2))
print(json.dumps([{k:v for k,v in r.items() if k!='changes'} for r in reports],indent=2))
