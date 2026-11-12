"""Audita cambios de un frame de 0020 sin editar GLB. Ejecutar desde raíz."""
import hashlib
import json
import math
import sys
from pathlib import Path
sys.path.insert(0,str(Path.cwd()/'tools'))
import glb_pose as g

folder=Path(__file__).parent
reports=[]
names={'upper_arm.L','upper_arm.R','forearm.L','forearm.R','hand.L','hand.R'}
for avatar in ('juan','pauline'):
    original_path=Path(f'app/src/main/assets/web/models/{avatar}.glb')
    approved_path=folder.parent/'entries-03'/f'{avatar}-despues.glb'
    original,original_bin=g.read_glb(original_path)
    approved,approved_bin=g.read_glb(approved_path)
    originals={a['name']:a for a in original['animations']}
    for animation in approved['animations']:
        if not animation['name'].startswith('0020_'):continue
        source=originals[animation['name']]
        assert animation['channels']==source['channels']
        events=[];unchanged=0
        for c in animation['channels']:
            node=approved['nodes'][c['target']['node']]
            path=c['target']['path'];s=animation['samplers'][c['sampler']]
            os=source['samplers'][c['sampler']]
            ts=g.read_accessor(approved,approved_bin,s['input'])
            values=g.read_accessor(approved,approved_bin,s['output'])
            if not(path=='rotation' and node.get('name') in names):
                assert values==g.read_accessor(original,original_bin,os['output'])
                assert ts==g.read_accessor(original,original_bin,os['input'])
                unchanged+=1
            if path not in ('rotation','translation','scale'):continue
            width=len(values)//len(ts)
            for i,t in enumerate(ts[1:],1):
                if t<1.5 or t-ts[i-1]>.05:continue
                p=values[(i-1)*width:i*width];q=values[i*width:(i+1)*width]
                if path=='rotation':
                    dot=abs(sum(x*y for x,y in zip(p,q)))/math.sqrt(sum(x*x for x in p)*sum(x*x for x in q))
                    delta=math.degrees(2*math.acos(min(1,dot)))
                else:delta=math.sqrt(sum((x-y)**2 for x,y in zip(p,q)))
                events.append({'node':node.get('name'),'path':path,'from':ts[i-1],'to':t,'delta':delta,
                               'identical_to_original':not(path=='rotation' and node.get('name') in names)})
        reports.append({'avatar':avatar,'clip':animation['name'],'unchanged_other_channels':unchanged,
                        'original_sha256':hashlib.sha256(original_path.read_bytes()).hexdigest(),
                        'approved_sha256':hashlib.sha256(approved_path.read_bytes()).hexdigest(),
                        'events':sorted(events,key=lambda e:e['delta'],reverse=True)})
(folder/'audit.json').write_text(json.dumps(reports,indent=2))
(folder/'verification.json').write_text(json.dumps([{'avatar':a,'windows':{'0020':[1.958333,2.208333]},'diagnosis_only':True} for a in ('juan','pauline')],indent=2))
print(f'{len(reports)} clips auditados; canales fuera de las seis rotaciones idénticos al original.')
