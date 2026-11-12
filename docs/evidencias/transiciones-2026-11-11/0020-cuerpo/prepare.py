"""0020: copia con corrección local del tirón corporal; fuentes y app intactas."""
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
BEGIN=49/24
END=54/24
TARGETS={('spine','translation'),('spine','rotation'),
         ('spine.001','rotation'),('spine.002','rotation'),('spine.003','rotation'),
         ('neck','rotation'),('head','rotation'),
         ('shoulder.L','translation'),('shoulder.L','rotation'),
         ('shoulder.R','translation'),('shoulder.R','rotation'),
         ('thigh.L','rotation'),('shin.L','rotation'),('foot.L','rotation'),('toe.L','rotation'),
         ('thigh.R','rotation'),('shin.R','rotation'),('foot.R','rotation')}

def angle(a,b):
    dot=abs(sum(x*y for x,y in zip(a,b)))/math.sqrt(sum(x*x for x in a)*sum(x*x for x in b))
    return math.degrees(2*math.acos(min(1,dot)))

reports=[]
for avatar in ('juan','pauline'):
    # Pauline no exporta canales de traslación de hombros: conservar sus nodos,
    # no añadir un movimiento nuevo donde el export mantiene un offset fijo.
    targets=TARGETS if avatar=='juan' else TARGETS-{('shoulder.L','translation'),('shoulder.R','translation')}
    source=OUT.parent/'entries-03'/f'{avatar}-despues.glb'
    raw=source.read_bytes();j,b=g.read_glb(source);original=copy.deepcopy(j)
    data=bytearray(b);changes=[]
    for animation in j['animations']:
        if not animation['name'].startswith('0020_'):continue
        touched=set()
        for channel in animation['channels']:
            node=j['nodes'][channel['target']['node']]['name'];path=channel['target']['path']
            if (node,path) not in targets:continue
            touched.add((node,path))
            sampler=animation['samplers'][channel['sampler']]
            assert sampler.get('interpolation','LINEAR')=='LINEAR'
            times=g.read_accessor(j,b,sampler['input']);values=g.read_accessor(j,b,sampler['output'])
            width=len(values)//len(times)
            start=min(range(len(times)),key=lambda i:abs(times[i]-BEGIN))
            end=min(range(len(times)),key=lambda i:abs(times[i]-END))
            assert abs(times[start]-BEGIN)<1e-6 and abs(times[end]-END)<1e-6
            left=values[start*width:(start+1)*width];right=values[end*width:(end+1)*width]
            before=values[:]
            for i in range(start+1,end):
                x=(times[i]-BEGIN)/(END-BEGIN);weight=6*x**5-15*x**4+10*x**3
                q=g.quat_slerp(left,right,weight) if path=='rotation' else [x+(y-x)*weight for x,y in zip(left,right)]
                values[i*width:(i+1)*width]=q
            assert values[:(start+1)*width]==before[:(start+1)*width]
            assert values[end*width:]==before[end*width:]
            while len(data)%4:data.append(0)
            packed=struct.pack(f'<{len(values)}f',*values);view=len(j['bufferViews'])
            j['bufferViews'].append({'buffer':0,'byteOffset':len(data),'byteLength':len(packed)})
            data.extend(packed);accessor=copy.deepcopy(j['accessors'][sampler['output']])
            accessor.update(bufferView=view,byteOffset=0);accessor.pop('min',None);accessor.pop('max',None)
            sampler['output']=len(j['accessors']);j['accessors'].append(accessor)
            changes.append({'clip':animation['name'],'node':node,'path':path,
                            'max_frame_delta_before':max(angle(before[(i-1)*4:i*4],before[i*4:(i+1)*4]) for i in range(start+1,end+1)) if path=='rotation' else None})
        assert touched==targets
    j['buffers'][0]['byteLength']=len(data)
    assert j['nodes']==original['nodes'] and data[:len(b)]==b
    for a,c in zip(original['animations'],j['animations']):
        assert a['channels']==c['channels']
        if not a['name'].startswith('0020_'):assert a==c
    assert len(changes)==len(targets)*4
    (OUT/f'{avatar}-antes.glb').write_bytes(raw)
    g.write_glb(OUT/f'{avatar}-despues.glb',j,data)
    assert source.read_bytes()==raw
    reports.append({'avatar':avatar,'source':'entries-03 aprobado, sin arreglo corporal',
                    'input_sha256':hashlib.sha256(raw).hexdigest(),'windows':{'0020':[BEGIN,END]},
                    'changed_tracks':len(changes),'changes':changes,'entry_and_arm_curves_exact':True,
                    'fingers_and_face_exact':True,'other_196_animations_exact':True,
                    'times_and_nodes_exact':True,'source_unchanged':True,'limits_provisional':True})
(OUT/'verification.json').write_text(json.dumps(reports,indent=2))
print(json.dumps([{k:v for k,v in r.items() if k!='changes'} for r in reports],indent=2))
