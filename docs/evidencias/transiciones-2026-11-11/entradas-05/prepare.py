"""Últimas diez señas 0040–0049: entrada y regreso nuevos sobre copias.

Ejecutar desde raíz del repo. Base: assets actuales con reposo aprobado.
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
NAMES={f'{bone}.{side}' for bone in ('upper_arm','forearm','hand') for side in ('L','R')}
JOINS={'0040':6/24,'0041':13/24,'0042':10/24,'0043':10/24,'0044':15/24,
       '0045':12/24,'0046':12/24,'0047':8/24,'0048':10/24,'0049':15/24}
HOLDS={'0041':8/24,'0042':4/24,'0043':4/24,'0044':9/24,
       '0045':6/24,'0046':7/24,'0047':3/24,'0048':4/24,'0049':9/24}
LEADS={}
NEW_RETURNS={'0040':(23/24,33/24),'0041':(36/24,50/24),'0042':(38/24,52/24),
             '0043':(36/24,49/24),'0044':(42/24,51/24),'0045':(45/24,60/24),
             '0046':(41/24,52/24),'0047':(43/24,52/24),'0048':(39/24,48/24),
             '0049':(36/24,48/24)}
SIGNS=set(JOINS)|set(LEADS)

def append_accessor(j,data,template,values,minimum=False):
    assert template['componentType']==5126 and not template.get('sparse')
    while len(data)%4:data.append(0)
    raw=struct.pack(f'<{len(values)}f',*values)
    view=len(j['bufferViews'])
    j['bufferViews'].append({'buffer':0,'byteOffset':len(data),'byteLength':len(raw)})
    data.extend(raw)
    acc=copy.deepcopy(template)
    acc.update(bufferView=view,byteOffset=0,count=len(values)//g.COMPONENTS[acc['type']])
    acc.pop('min',None);acc.pop('max',None)
    if minimum:acc.update(min=[min(values)],max=[max(values)])
    index=len(j['accessors']);j['accessors'].append(acc)
    return index

reports=[]
for avatar in ('juan','pauline'):
    source=Path(f'app/src/main/assets/web/models/{avatar}.glb')
    source_raw=source.read_bytes()
    app,app_bin=g.read_glb(source)
    before=copy.deepcopy(app);before_bin=bytearray(app_bin)
    provenance=[]
    # Combinar solo las seis curvas aprobadas de las diez señas, sin volver a
    # elegir ventanas de regreso ni copiar indices de accessors de otro GLB.
    for group,signs in []:
        path=OUT.parent/group/f'{avatar}-despues.glb'
        raw=path.read_bytes();js,binary=g.read_glb(path)
        provenance.append({'group':group,'sha256':hashlib.sha256(raw).hexdigest()})
        clips={a['name']:a for a in js['animations']}
        for animation in before['animations']:
            if animation['name'].split('_')[0] not in signs:continue
            approved=clips[animation['name']]
            for channel in animation['channels']:
                node=before['nodes'][channel['target']['node']]
                if channel['target']['path']!='rotation' or node.get('name') not in NAMES:continue
                i=channel['sampler'];sampler=animation['samplers'][i]
                approved_sampler=approved['samplers'][i]
                assert g.read_accessor(before,before_bin,sampler['input'])==g.read_accessor(js,binary,approved_sampler['input'])
                values=g.read_accessor(js,binary,approved_sampler['output'])
                sampler['output']=append_accessor(before,before_bin,js['accessors'][approved_sampler['output']],values)
    before['buffers'][0]['byteLength']=len(before_bin)
    g.write_glb(OUT/f'{avatar}-antes.glb',before,before_bin)
    j=copy.deepcopy(before);data=bytearray(before_bin)
    checks=[]
    for original,animation,app_clip in zip(before['animations'],j['animations'],app['animations']):
        sign=animation['name'].split('_')[0]
        if sign not in SIGNS:continue
        channels={c['sampler']:c for c in animation['channels']}
        assert len(channels)==len(animation['channels'])
        changed=0
        for i,sampler in enumerate(animation['samplers']):
            channel=channels[i];node=j['nodes'][channel['target']['node']]
            is_arm=channel['target']['path']=='rotation' and node.get('name') in NAMES
            if sign not in LEADS and not is_arm:continue
            old_sampler=original['samplers'][i]
            times=g.read_accessor(before,before_bin,old_sampler['input'])
            values=g.read_accessor(before,before_bin,old_sampler['output'])
            width=len(values)//len(times)
            assert len(values)==len(times)*width
            interpolation=sampler.get('interpolation','LINEAR')
            assert interpolation in ('LINEAR','STEP')
            if sign in LEADS:
                delay=LEADS[sign]
                prefix_times=[k/24 for k in range(1,round(delay*24)+1)]
                first=values[:width]
                prefix=[]
                for t in prefix_times:
                    if is_arm:
                        x=(t-prefix_times[0])/(times[0]+delay-prefix_times[0])
                        weight=6*x**5-15*x**4+10*x**3
                        prefix.extend(node['rotation'] if x<=0 else g.quat_slerp(node['rotation'],first,weight))
                    else:prefix.extend(first)
                new_times=prefix_times+[t+delay for t in times]
                assert all(x<y for x,y in zip(new_times,new_times[1:]))
                new_values=prefix+values
                sampler['input']=append_accessor(j,data,before['accessors'][old_sampler['input']],new_times,True)
                assert new_values[len(prefix):]==values
                if is_arm:assert prefix[:4]==node['rotation']
            else:
                join=JOINS[sign]
                gesture=values[:]
                anchor=min(range(len(times)),key=lambda k:abs(times[k]-join))
                assert abs(times[anchor]-join)<1e-6
                target=gesture[anchor*4:anchor*4+4]
                new_values=gesture[:]
                for k,t in enumerate(times):
                    if t>=join-1e-6:continue
                    begin=HOLDS.get(sign,times[0])
                    x=max(0,min(1,(t-begin)/(join-begin)))
                    weight=6*x**5-15*x**4+10*x**3
                    new_values[k*4:k*4+4]=node['rotation'] if x<=0 else g.quat_slerp(node['rotation'],target,weight)
                assert new_values[:4]==node['rotation']
                protected=join
                for k,t in enumerate(times):
                    if t>=protected-1e-6:assert new_values[k*4:k*4+4]==values[k*4:k*4+4]
                if sign in NEW_RETURNS:
                    begin,end=NEW_RETURNS[sign]
                    anchor=min(range(len(times)),key=lambda k:abs(times[k]-begin))
                    last=min(range(len(times)),key=lambda k:abs(times[k]-end))
                    assert abs(times[anchor]-begin)<1e-6 and abs(times[last]-end)<1e-6
                    origin=values[4*anchor:4*anchor+4]
                    for k,t in enumerate(times):
                        if t<=begin+1e-6:continue
                        x=max(0,min(1,(t-begin)/(end-begin)))
                        weight=6*x**5-15*x**4+10*x**3
                        new_values[k*4:k*4+4]=g.quat_slerp(origin,node['rotation'],weight)
            sampler['output']=append_accessor(j,data,before['accessors'][old_sampler['output']],new_values)
            if is_arm:
                for k in range(0,len(new_values),4):assert abs(sum(x*x for x in new_values[k:k+4])-1)<1e-5
            changed+=1
        assert changed>0
        checks.append({'clip':animation['name'],'method':'prepend' if sign in LEADS else 'replace_entry',
                       'added_seconds':LEADS.get(sign,0),'join_seconds':JOINS.get(sign),
                       'protected_from_seconds':JOINS.get(sign),
                       'changed_samplers':changed,'approved_return_values_exact':sign not in NEW_RETURNS})
    j['buffers'][0]['byteLength']=len(data)
    assert j['nodes']==before['nodes'] and data[:len(before_bin)]==before_bin
    for a,b in zip(before['animations'],j['animations']):
        assert a['channels']==b['channels']
        if a['name'].split('_')[0] not in SIGNS:assert a==b
    assert len(checks)==40
    g.write_glb(OUT/f'{avatar}-despues.glb',j,data)
    assert source.read_bytes()==source_raw
    reports.append({'avatar':avatar,'input_sha256':hashlib.sha256(source_raw).hexdigest(),
                    'approved_sources':provenance,'windows':{s:[1/24,JOINS.get(s,LEADS.get(s))] for s in sorted(SIGNS)},
                    'entry_joins':JOINS,'hold_rest_until':HOLDS,'added_preparation_seconds':LEADS,
                    'new_return_windows':NEW_RETURNS,
                    'nodes_and_other_160_animations_exact':True,'source_unchanged':True,
                    'limits_provisional':True,'checks':checks})
(OUT/'verification.json').write_text(json.dumps(reports,indent=2))
print(json.dumps([{k:v for k,v in r.items() if k!='checks'} for r in reports],indent=2))
