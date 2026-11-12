import json
import math
import sys
from pathlib import Path
sys.path.insert(0,str(Path.cwd()/'tools'))
import glb_pose as g

folder=Path(__file__).parent
reports=json.loads((folder/'verification.json').read_text())
results=[]
def angle(p,q):
    dot=abs(sum(x*y for x,y in zip(p,q)))/math.sqrt(sum(x*x for x in p)*sum(x*x for x in q))
    return math.degrees(2*math.acos(min(1,dot)))

for report in reports:
    avatar=report['avatar'];a,b=g.read_glb(folder/f'{avatar}-antes.glb');j,d=g.read_glb(folder/f'{avatar}-despues.glb')
    targets={(c['clip'],c['node'],c['path']) for c in report['changes']}
    begin,end=report['windows']['0020'];metrics=[];changed=0
    assert a['nodes']==j['nodes']
    for before,after in zip(a['animations'],j['animations']):
        assert before['channels']==after['channels']
        if not before['name'].startswith('0020_'):
            assert before==after;continue
        for c in after['channels']:
            node=j['nodes'][c['target']['node']]['name'];path=c['target']['path']
            old=before['samplers'][c['sampler']];new=after['samplers'][c['sampler']]
            assert old['input']==new['input']
            times=g.read_accessor(a,b,old['input']);ov=g.read_accessor(a,b,old['output']);nv=g.read_accessor(j,d,new['output'])
            if (after['name'],node,path) not in targets:
                assert old==new and ov==nv;continue
            changed+=1;width=len(ov)//len(times)
            assert len(nv)==len(ov)
            for i,t in enumerate(times):
                if t<=begin+1e-6 or t>=end-1e-6:assert ov[i*width:(i+1)*width]==nv[i*width:(i+1)*width]
            if path=='rotation':
                indices=[i for i,t in enumerate(times) if i>0 and begin+1e-6<t<=end+1e-6]
                previous=max(angle(ov[(i-1)*4:i*4],ov[i*4:(i+1)*4]) for i in indices)
                current=max(angle(nv[(i-1)*4:i*4],nv[i*4:(i+1)*4]) for i in indices)
                assert current<previous+1e-3,(avatar,node,previous,current)
                for i in range(0,len(nv),4):assert abs(sum(x*x for x in nv[i:i+4])-1)<1e-5
                metrics.append({'clip':after['name'],'node':node,'max_frame_degrees_before':previous,'max_frame_degrees_after':current})
    assert changed==report['changed_tracks']
    results.append({'avatar':avatar,'checked_changed_tracks':changed,'all_non_target_tracks_exact':True,
                    'outside_window_exact':True,'times_and_nodes_exact':True,
                    'other_196_animations_exact':True,'rotation_delta_checks':metrics})
(folder/'checks.json').write_text(json.dumps(results,indent=2))
print(json.dumps([{k:v for k,v in r.items() if k!='rotation_delta_checks'} for r in results],indent=2))
for r in results:
    print(r['avatar'],[m for m in r['rotation_delta_checks'] if m['clip']=='0020_afirmativo' and m['node'] in ('thigh.L','thigh.R','shoulder.L','shoulder.R')])
