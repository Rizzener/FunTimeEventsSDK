"""Linux TIME_WAIT probe regression; a live listener must still be rejected."""
import json,socket
from run_phase2 import ROOT

def main():
    server=socket.socket();server.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
    server.bind(('127.0.0.1',0));port=server.getsockname()[1];server.listen()
    client=socket.create_connection(('127.0.0.1',port));accepted,_=server.accept()
    accepted.close();assert client.recv(1)==b'';client.close();server.close()
    rows={}
    for reuse in [False,True]:
        with socket.socket() as probe:
            if reuse:probe.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
            try:probe.bind(('127.0.0.1',port));rows[str(reuse)]='PASS'
            except OSError as error:rows[str(reuse)]={'errno':error.errno,'error':str(error)}
    with socket.socket() as listener:
        listener.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1);listener.bind(('127.0.0.1',0));listener.listen()
        with socket.socket() as probe:
            probe.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
            try:probe.bind(listener.getsockname());live=False
            except OSError as error:live=error.errno==98
    checks={'old_probe_rejects_TIME_WAIT':isinstance(rows['False'],dict) and rows['False']['errno']==98,
            'new_probe_accepts_closed_fixture_port':rows['True']=='PASS','new_probe_rejects_live_listener':live}
    output=ROOT/'results/phase5/local-environment/linux-port-regression.json';output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps({'status':'PASS' if all(checks.values()) else 'FAIL','checks':checks,'rows':rows},indent=2)+'\n')
    print(checks);raise SystemExit(not all(checks.values()))

if __name__=='__main__':main()
