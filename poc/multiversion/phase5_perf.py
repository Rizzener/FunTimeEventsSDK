"""Export only measured counters from strictly verified production runs."""
import csv,json
from run_phase2 import ROOT
from phase5_linux_results import PREFIXES

def main():
    config=json.loads((ROOT/'targets.json').read_text());rows=[];coverage={}
    for environment,prefix in [('Windows','results/phase5')]+[(p.split('/')[-1],p) for p in PREFIXES]:
        coverage[environment]={}
        for spec in config['targets']:
            mc=spec['minecraft'];p=ROOT/prefix/mc/'runtime.json';data=json.loads(p.read_text()) if p.exists() else {}
            coverage[environment][mc]={}
            for mode in ['offline','rest','ws','flags']:
                runs=[r for r in data.get('modes',{}).get(mode,{}).get('runs',[]) if r['status']=='PASS']
                coverage[environment][mc][mode]='PASS' if runs else 'NOT VERIFIED'
                if not runs:continue
                r=runs[-1];raw=json.loads((ROOT/r['path'].replace('\\','/')).read_text(encoding='utf-8'))
                base={'environment':environment,'minecraft':mc,'mode':mode,'evidence':r['path'],
                      'cpu_limit':raw.get('performance_control',{}).get('cpu_limit',2 if environment!='Windows' else 'not capped'),
                      'diagnostic_thread_cpu':raw.get('performance_control',{}).get('thread_cpu',False),
                      'entity_visits':sum(s['entity_visits'] for s in r['reads']),
                      'text_display_reads':sum(s['text_display_reads'] for s in r['reads']),
                      'block_reads':sum(s['block_reads'] for s in r['reads']),
                      'shutdown_threads_empty':raw['runtime'].get('shutdown_threads_empty','NOT MEASURED')}
                for name,m in r['tick_and_callback_performance'].items():
                    rows.append(base|{'category':name,'count':m['count'],'mean_us':m['mean_ns']/1000,'max_us':m['max_ns']/1000,
                                      'mean_bytes':m['mean_bytes'],'max_bytes':m['max_bytes']})
                for name,m in r['tracker_performance'].items():
                    if m[0]:rows.append(base|{'category':'tracker_'+name,'count':m[0],'mean_us':m[1]/m[0]/1000,'max_us':m[2]/1000,
                                             'mean_bytes':'NOT MEASURED','max_bytes':'NOT MEASURED'})
    path=ROOT/'results/phase5/performance.csv'
    if rows:
        with path.open('w',newline='',encoding='utf-8') as f:
            writer=csv.DictWriter(f,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
    (path.with_suffix('.json')).write_text(json.dumps({'coverage':coverage,'rows':rows,'scope':'Wall-clock callback spans under local mixed load; aggregate client-thread allocations; no FPS claim. Tracker spans do not measure per-tracker allocations.'},indent=2)+'\n')
    outliers=[]
    for row in rows:
        count=row['count'];total=row['mean_us']*count
        if count>1 and total>0 and row['max_us']>=total*.5:
            outliers.append(row|{'max_fraction_of_total':row['max_us']/total,
                'remaining_samples_mean_us':(total-row['max_us'])/(count-1)})
    (path.parent/'performance-outliers.json').write_text(json.dumps({'rows':outliers,
        'selection':'The single maximum accounts for at least half of measured total wall time. Every original sample remains in mean/max; remaining mean is descriptive only.',
        'limitation':'Aggregated counters cannot establish GC/JIT/scheduling causes or a version-specific regression. No observations were discarded.'},indent=2)+'\n')
    print('Measured rows',len(rows),flush=True)

if __name__=='__main__':main()
