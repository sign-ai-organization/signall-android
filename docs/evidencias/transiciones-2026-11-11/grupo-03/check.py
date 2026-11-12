import json
import math
import sys
from pathlib import Path
sys.path.insert(0, str(Path.cwd() / 'tools'))
import glb_pose as g

folder = Path(__file__).parent
windows = {'0008': (33/24,47/24), '0009': (32/24,45/24),
           '0010': (33/24,46/24), '0011': (32/24,45/24), '0012': (29/24,43/24)}
results=[]
for avatar in ('juan','pauline'):
    old,b=g.read_glb(folder/f'{avatar}-antes.glb')
    new,d=g.read_glb(folder/f'{avatar}-despues.glb')
    assert old['nodes']==new['nodes'] and d[:len(b)]==b
    counts={sign:0 for sign in windows}
    for a,c in zip(old['animations'],new['animations']):
        assert a['channels']==c['channels'] and a['name']==c['name']
        sign=a['name'].split('_')[0]
        if sign not in windows:
            assert a==c
            continue
        begin,end=windows[sign]
        for old_sampler,new_sampler in zip(a['samplers'],c['samplers']):
            if old_sampler==new_sampler:continue
            assert old_sampler['input']==new_sampler['input']
            ts=g.read_accessor(new,d,new_sampler['input'])
            before=g.read_accessor(old,b,old_sampler['output'])
            after=g.read_accessor(new,d,new_sampler['output'])
            channel=next(ch for ch in c['channels'] if c['samplers'][ch['sampler']] is new_sampler)
            node=new['nodes'][channel['target']['node']]
            assert channel['target']['path']=='rotation'
            assert node['name'] in {f'{bone}.{side}' for bone in ('upper_arm','forearm','hand') for side in ('L','R')}
            previous=math.inf
            for i,t in enumerate(ts):
                q=after[i*4:i*4+4]
                assert abs(sum(x*x for x in q)-1)<1e-5
                if t<=begin+1e-6:assert q==before[i*4:i*4+4]
                # Float32: normalizar el producto antes de medir el ángulo.
                # Sin esto, incluso q contra sí mismo puede dar un ángulo falso.
                rest=node['rotation']
                dot=abs(sum(x*y for x,y in zip(q,rest))) / math.sqrt(sum(x*x for x in q)*sum(x*x for x in rest))
                distance=2*math.acos(min(1,dot))
                if t>=begin-1e-6:
                    assert distance<=previous+1e-5
                    previous=distance
                if t>=end-1e-6:
                    assert min(max(abs(x-y) for x,y in zip(q,node['rotation'])),max(abs(x+y) for x,y in zip(q,node['rotation'])))<1e-6
            counts[sign]+=1
    assert all(count==24 for count in counts.values())
    results.append({'avatar':avatar,'checked_tracks':counts,'central_samples_exact':True,'all_entries_exact':True,'return_angular_distance_monotonic':True,'other_180_animations_unchanged':True})
(folder/'checks.json').write_text(json.dumps(results,indent=2))
print(json.dumps(results,indent=2))
