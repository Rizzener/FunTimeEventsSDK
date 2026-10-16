"""Mechanical change for response DTOs only: callers cannot mutate cached backing lists."""
from pathlib import Path
import re
ROOT=Path(__file__).resolve().parent
for name in ['BansListResponse','PlayersListResponse','PlayerDataResponse','LootAreaResponse','MineInfoResponse','CaptchaResponse']:
    path=ROOT/'core/src/main/java/net/funtimeevents/model'/(name+'.java')
    text=path.read_text(encoding='utf-8')
    text=re.sub(r'(public List<[^>]+> \w+\(\) \{ return )(\w+)(; \})',
                lambda m:m[1]+m[2]+' == null ? null : new java.util.ArrayList<>('+m[2]+')'+m[3],text)
    path.write_text(text,encoding='utf-8')
print('Defensive copies in 7 response-list getters; payload models unchanged')
