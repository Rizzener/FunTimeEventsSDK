"""Bounded local disk housekeeping after all four runtimes of a target exited."""
import json,time
from run_phase2 import ROOT
from phase5_space import reclaim
config=json.loads((ROOT/'targets.json').read_text())
done=set();until=time.monotonic()+6*60*60
stop=ROOT/'results/phase5/local-environment/stop-watch'
while time.monotonic()<until and not stop.exists():
    for t in config['targets']:
        mc=t['minecraft']
        if mc in done:continue
        complete=True
        for mode in ['offline','rest','ws','flags']:
            paths=(ROOT/'results/phase5'/mc/('runtime-'+mode)).glob('attempt-*/result.json')
            complete &= any(json.loads(p.read_text()).get('status')=='PASS' for p in paths)
        if complete:
            reclaim(mc);done.add(mc)
    if len(done)==13:break
    time.sleep(10)
print('Fixture input cleanup finished',sorted(done),flush=True)
