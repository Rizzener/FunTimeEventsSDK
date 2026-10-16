"""Executable negative controls for the actual Linux evidence verifier."""
import copy,json
from phase5_verify import ROOT,PREFIX,headless_checks,save

def main():
    names=['xdpyinfo_real_X11_server','private_socket','private_mount_namespace','real_xkbcomp_available','no_WSLg_fallback']
    complete={'status':'PASS','kind':'private-xvfb','checks':dict.fromkeys(names,True)}
    accepted=lambda d:all(headless_checks(d,private=True).values())
    checks={'complete_accepted':accepted(complete),'missing_rejected':not accepted({}),
            'incomplete_rejected':not accepted({'status':'PASS','kind':'private-xvfb','checks':{'xdpyinfo_real_X11_server':True}})}
    for name in names:
        d=copy.deepcopy(complete);d['checks'].pop(name);checks['missing_'+name]=not accepted(d)
        d=copy.deepcopy(complete);d['checks'][name]=False;checks['false_'+name]=not accepted(d)
    d=copy.deepcopy(complete);d['kind']='xvfb';checks['nonprivate_rejected']=not accepted(d)
    d=copy.deepcopy(complete);d['status']='FAIL';checks['failed_receipt_rejected']=not accepted(d)
    checks['standard_CI_Xvfb_accepted']=all(headless_checks({'status':'PASS','kind':'xvfb','checks':{'xdpyinfo_real_X11_server':True}}).values())
    result={'status':'PASS' if all(checks.values()) else 'FAIL','checks':checks}
    save(ROOT/PREFIX/'local-environment/headless-negative-controls.json',result)
    print(json.dumps(result));raise SystemExit(result['status']!='PASS')

if __name__=='__main__':main()
