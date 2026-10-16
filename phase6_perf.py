"""Export measured production callback/tracker spans; wall time is not CPU/FPS."""
import argparse,csv,json,os
from run_phase2 import ROOT
def main():
    p=argparse.ArgumentParser();p.add_argument('--targets',required=True);p.add_argument('--allow-partial',action='store_true');a=p.parse_args()
    prefix=ROOT/os.environ.get('FTE_PHASE5_EVIDENCE','results/phase6');rows=[]
    for mc in a.targets.split(','):
        result=json.loads((prefix/mc/'runtime.json').read_text());assert result['status']=='PASS' or a.allow_partial
        for mode,data in result['modes'].items():
            if data['status']!='PASS':
                assert a.allow_partial
                continue
            run=[r for r in data['runs'] if r['status']=='PASS'][-1]
            for category,stat in run['tick_and_callback_performance'].items():
                rows.append({'minecraft':mc,'mode':mode,'kind':'callback','category':category,**stat})
            for category,stat in run['tracker_performance'].items():
                rows.append({'minecraft':mc,'mode':mode,'kind':'tracker','category':category,
                    'count':stat[0],'mean_ns':stat[1]/stat[0] if stat[0] else 0,'max_ns':stat[2],
                    'mean_bytes':None,'max_bytes':None})
    fields=['minecraft','mode','kind','category','count','mean_ns','max_ns','mean_bytes','max_bytes']
    out=prefix/('performance-'+a.targets.replace(',','_')+'.csv')
    with out.open('w',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=fields);writer.writeheader();writer.writerows(rows)
    print('PERFORMANCE_EXPORTED',len(rows),out)
if __name__=='__main__':main()
