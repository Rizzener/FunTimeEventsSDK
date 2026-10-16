"""Keep accepted Phase 3 fixtures intact; avoid overlapping the local player/NPC."""
import json
from pathlib import Path
root=Path(__file__).resolve().parent/'data'
data=json.loads((root/'phase3/tracker-sessions.json').read_text(encoding='utf-8'))
for row in data:
    row['fixture_revision']=4
    if row['name']=='copper':row['pos']=[2092,-50,2006]
    if row['name']=='warden':row['pos']=[-2008,-50,-1994]
(root/'phase4/tracker-sessions.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Generated Phase 4 scenes; NPC/chest positions and golden payloads unchanged')
