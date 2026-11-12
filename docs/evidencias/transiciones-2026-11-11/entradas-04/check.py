"""Verifica los GLB serializados, no solo los valores en memoria del generador."""
import json
import sys
from pathlib import Path
sys.path.insert(0,str(Path.cwd()/'tools'))
import glb_pose as g

folder=Path(__file__).parent
reports=json.loads((folder/'verification.json').read_text())
results=[]
for report in reports:
    avatar=report['avatar']
    a,b=g.read_glb(folder/f'{avatar}-antes.glb')
    j,d=g.read_glb(folder/f'{avatar}-despues.glb')
    assert a['nodes']==j['nodes']
    entries={c['clip']:c for c in report['checks']}
    checked=0;unchanged=0
    for before,after in zip(a['animations'],j['animations']):
        assert before['channels']==after['channels']
        if before['name'] not in entries:
            assert before==after;unchanged+=1;continue
        entry=entries[before['name']]
        sign=before['name'].split('_')[0]
        channels={c['sampler']:c for c in before['channels']}
        rest_count=0
        for i,(old,new) in enumerate(zip(before['samplers'],after['samplers'])):
            ot=g.read_accessor(a,b,old['input']);nt=g.read_accessor(j,d,new['input'])
            ov=g.read_accessor(a,b,old['output']);nv=g.read_accessor(j,d,new['output'])
            width=len(ov)//len(ot)
            c=channels[i];node=j['nodes'][c['target']['node']]
            arm=c['target']['path']=='rotation' and node.get('name') in {
                f'{bone}.{side}' for bone in ('upper_arm','forearm','hand') for side in ('L','R')}
            assert len(nv)==len(nt)*width
            if entry['method']=='prepend':
                n=round(entry['added_seconds']*24)
                assert nv[n*width:]==ov
                assert all(abs(t+entry['added_seconds']-u)<1e-6 for t,u in zip(ot,nt[n:]))
                assert len(nt)==len(ot)+n
                if arm:assert nv[:4]==node['rotation'];rest_count+=1
            elif old!=new:
                assert arm and ot==nt
                assert nv[:4]==node['rotation'];rest_count+=1
                protected=entry['protected_from_seconds']
                new_return=report.get('new_return_windows',{}).get(sign)
                for k,t in enumerate(ot):
                    hold=report.get('hold_rest_until',{}).get(sign,ot[0])
                    if t<=hold+1e-6:assert nv[k*width:k*width+width]==node['rotation']
                    if t>=protected-1e-6 and (new_return is None or t<=new_return[0]+1e-6):assert ov[k*width:k*width+width]==nv[k*width:k*width+width]
                    if new_return and t>=new_return[1]-1e-6:
                        q=nv[k*width:k*width+width]
                        assert min(max(abs(x-y) for x,y in zip(q,node['rotation'])),max(abs(x+y) for x,y in zip(q,node['rotation'])))<1e-6
            else:assert ov==nv and ot==nt
        assert rest_count==6
        checked+=1
    assert checked==40 and unchanged==160
    results.append({'avatar':avatar,'checked_clips':checked,'other_clips_exact':unchanged,
                    'initial_six_rotations_match_rest':True,'eight_approved_returns_exact':True,
                    'two_new_returns_reach_rest':True,
                    'prepended_clips_preserve_all_original_samples':True,
                    'protected_gesture_samples_exact':True,'input_output_counts_valid':True})
(folder/'checks.json').write_text(json.dumps(results,indent=2))
print(json.dumps(results,indent=2))
