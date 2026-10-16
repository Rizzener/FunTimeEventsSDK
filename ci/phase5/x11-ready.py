"""Check a real X11 server before launching any production game process."""
import json,os,subprocess,time
from pathlib import Path

def main():
    display=os.environ['DISPLAY'];output=Path(os.environ['FTE_PHASE5_DISPLAY_RECEIPT'])
    private=os.environ.get('FTE_REQUIRE_PRIVATE_XVFB')=='1'
    checks={};deadline=time.monotonic()+20;last=None
    while time.monotonic()<deadline:
        p=subprocess.run(['xdpyinfo','-display',display],capture_output=True,timeout=5)
        last=p
        if p.returncode==0:break
        time.sleep(.2)
    checks['xdpyinfo_real_X11_server']=last.returncode==0
    if private:
        root=Path(os.environ['FTE_PHASE5_WORKSPACE']);number=display.lstrip(':').split('.')[0]
        checks['private_socket']=(root/'x11-sockets'/('X'+number)).exists()
        checks['private_mount_namespace']=str(Path('/proc/self/ns/mnt').readlink())!=os.environ['FTE_PARENT_MOUNT_NS']
        checks['real_xkbcomp_available']=Path('/usr/bin/xkbcomp').resolve()==Path('/var/tmp/fte-sdk-phase5-01a13db2/tools/usr/bin/xkbcomp')
        checks['no_WSLg_fallback']=os.environ.get('SDL_VIDEODRIVER')=='x11' and os.environ.get('XDG_SESSION_TYPE')=='x11' and not os.environ.get('WAYLAND_DISPLAY') and os.environ.get('XDG_RUNTIME_DIR')==str(root/'xdg')
    output.parent.mkdir(parents=True,exist_ok=True)
    output.with_suffix('.txt').write_bytes(last.stdout+last.stderr)
    result={'status':'PASS' if all(checks.values()) else 'FAIL','kind':'private-xvfb' if private else 'xvfb',
            'display':display,'checks':checks,'mount_namespace':str(Path('/proc/self/ns/mnt').readlink()),'time':time.time()}
    output.write_text(json.dumps(result,indent=2)+'\n');print('DISPLAY_RECEIPT',result,flush=True)
    raise SystemExit(result['status']!='PASS')

if __name__=='__main__':main()
