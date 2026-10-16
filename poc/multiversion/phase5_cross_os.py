"""Compare independently published Windows/Linux artifacts, with missing != PASS."""
import hashlib,json
from run_phase2 import ROOT
from phase5_linux_results import PREFIXES

def main():
    config=json.loads((ROOT/'targets.json').read_text());version=config['sdk_version'];rows={}
    for spec in config['targets']:
        mc=spec['minecraft'];folder='artifacts/'+version+'/mc'+mc;stem='mc'+mc+'-'+version;files={}
        available=[prefix for prefix in PREFIXES if all((ROOT/prefix/folder/(stem+s)).exists() for s in ['.jar','-sources.jar','.pom','.module'])]
        linux_prefix=available[0] if available else PREFIXES[0]
        for suffix in ['.jar','-sources.jar','.pom','.module']:
            name=stem+suffix;windows=ROOT/'results/phase5'/folder/name;linux=ROOT/linux_prefix/folder/name
            row={'status':'NOT VERIFIED','linux_prefix':linux_prefix}
            if windows.exists():row['windows']=hashlib.sha256(windows.read_bytes()).hexdigest()
            if linux.exists():row['linux']=hashlib.sha256(linux.read_bytes()).hexdigest()
            if 'windows' in row and 'linux' in row:
                row['byte_equal']=row['windows']==row['linux']
                row['status']='PASS' if row['byte_equal'] else 'FAIL'
                if suffix=='.pom':
                    normalize=lambda b:b.replace(b'\r\n',b'\n')
                    assert normalize(b'<version>1</version>\r\n')!=normalize(b'<version>2</version>\n')
                    row['equal_content_only_line_endings_may_differ']=normalize(windows.read_bytes())==normalize(linux.read_bytes())
                    if row['equal_content_only_line_endings_may_differ']:
                        row['status']='PASS';row['note']='Gradle XML POM uses CRLF on Windows and LF on Linux; original bytes and distinct SHA-256 are retained.'
            files[name]=row
        rows[mc]={'status':'PASS' if all(r['status']=='PASS' for r in files.values()) else 'FAIL' if any(r['status']=='FAIL' for r in files.values()) else 'NOT VERIFIED','files':files}
    result={'status':'FAIL' if any(r['status']=='FAIL' for r in rows.values()) else 'PASS' if all(r['status']=='PASS' for r in rows.values()) else 'NOT VERIFIED','targets':rows}
    (ROOT/'results/phase5/cross-os.json').write_text(json.dumps(result,indent=2)+'\n')
    print(result['status'],sum(r['status']=='PASS' for r in rows.values()),'/13',flush=True)
    raise SystemExit(result['status']!='PASS')

if __name__=='__main__':main()
