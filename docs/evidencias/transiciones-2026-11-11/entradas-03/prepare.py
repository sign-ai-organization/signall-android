"""Entradas 0020–0029: nueve propuestas y referencia 0028, sin tocar la app.

Ejecutar desde raíz del repo. Fuentes: group-05/06/07 y 0028-entry hermanas.
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
JOINS={'0020':16/24,'0021':8/24,'0022':19/24,'0023':19/24,'0024':21/24,
       '0025':15/24,'0026':15/24,'0027':19/24,'0029':17/24}
HOLDS={'0020':10/24,'0021':2/24,'0022':14/24,'0023':14/24,'0024':16/24,
       '0025':10/24,'0026':6/24,'0027':14/24,'0029':10/24}
LEADS={}
REFERENCES={'0028'}
SIGNS=set(JOINS)|set(LEADS)|REFERENCES

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
    for group,signs in [('group-05',{'0020','0021','0022'}),
                        ('group-06',{f'{i:04d}' for i in range(23,28)}),
                        ('group-07',{'0028','0029'})]:
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
    # 0028 ya tiene salto y entrada aprobados. Copiar todos sus canales de esa
    # fuente, no solo brazos: así no se pierde la corrección de primera postura.
    reference_path=OUT.parent/'0028-entry'/f'{avatar}-despues.glb'
    ref_raw=reference_path.read_bytes();ref,ref_bin=g.read_glb(reference_path)
    provenance.append({'group':'0028-entry','sha256':hashlib.sha256(ref_raw).hexdigest()})
    ref_clips={a['name']:a for a in ref['animations']}
    assert ref['nodes']==before['nodes']
    for animation in before['animations']:
        if animation['name'].split('_')[0] not in REFERENCES:continue
        approved=ref_clips[animation['name']]
        assert animation['channels']==approved['channels']
        for sampler,ref_sampler in zip(animation['samplers'],approved['samplers']):
            assert g.read_accessor(before,before_bin,sampler['input'])==g.read_accessor(ref,ref_bin,ref_sampler['input'])
            values=g.read_accessor(ref,ref_bin,ref_sampler['output'])
            sampler['output']=append_accessor(before,before_bin,ref['accessors'][ref_sampler['output']],values)
    before['buffers'][0]['byteLength']=len(before_bin)
    g.write_glb(OUT/f'{avatar}-antes.glb',before,before_bin)
    j=copy.deepcopy(before);data=bytearray(before_bin)
    checks=[]
    for original,animation,app_clip in zip(before['animations'],j['animations'],app['animations']):
        sign=animation['name'].split('_')[0]
        if sign not in SIGNS:continue
        if sign in REFERENCES:
            assert animation==original
            checks.append({'clip':animation['name'],'method':'approved_reference',
                           'added_seconds':0,'join_seconds':.75,'protected_from_seconds':0,
                           'changed_samplers':0,'approved_return_values_exact':True})
            continue
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
            sampler['output']=append_accessor(j,data,before['accessors'][old_sampler['output']],new_values)
            if is_arm:
                for k in range(0,len(new_values),4):assert abs(sum(x*x for x in new_values[k:k+4])-1)<1e-5
            changed+=1
        assert changed>0
        checks.append({'clip':animation['name'],'method':'prepend' if sign in LEADS else 'replace_entry',
                       'added_seconds':LEADS.get(sign,0),'join_seconds':JOINS.get(sign),
                       'protected_from_seconds':JOINS.get(sign),
                       'changed_samplers':changed,'approved_return_values_exact':True})
    j['buffers'][0]['byteLength']=len(data)
    assert j['nodes']==before['nodes'] and data[:len(before_bin)]==before_bin
    for a,b in zip(before['animations'],j['animations']):
        assert a['channels']==b['channels']
        if a['name'].split('_')[0] not in SIGNS:assert a==b
    assert len(checks)==40
    g.write_glb(OUT/f'{avatar}-despues.glb',j,data)
    assert source.read_bytes()==source_raw
    reports.append({'avatar':avatar,'input_sha256':hashlib.sha256(source_raw).hexdigest(),
                    'approved_sources':provenance,'windows':{s:[1/24,JOINS.get(s,LEADS.get(s,.75))] for s in sorted(SIGNS)},
                    'entry_joins':JOINS,'hold_rest_until':HOLDS,'added_preparation_seconds':LEADS,
                    'nodes_and_other_160_animations_exact':True,'source_unchanged':True,
                    'approved_references':sorted(REFERENCES),
                    'limits_provisional':True,'checks':checks})
(OUT/'verification.json').write_text(json.dumps(reports,indent=2))
print(json.dumps([{k:v for k,v in r.items() if k!='checks'} for r in reports],indent=2))
