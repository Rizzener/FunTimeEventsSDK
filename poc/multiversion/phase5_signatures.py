"""Record actual Minecraft/authlib class signatures, never emulate compatibility."""
import argparse,json,os,shutil,subprocess,zipfile
from pathlib import Path
from run_phase2 import ROOT

def main():
    p=argparse.ArgumentParser();p.add_argument('--targets',required=True);a=p.parse_args()
    output=ROOT/'results/phase5/signatures';output.mkdir(parents=True,exist_ok=True)
    matrix=json.loads((ROOT/'targets.json').read_text())['targets']
    for mc in ([t['minecraft'] for t in matrix] if a.targets=='all' else a.targets.split(',')):
        cache=Path.home()/'.gradle/caches/fabric-loom'
        if mc.startswith('26.'):jars=[cache/mc/'minecraft-client.jar']
        else:
            candidates=[p for p in (cache/'minecraftMaven').rglob('*.jar') if (mc+'-loom.mappings') in str(p) and not any(s in p.name for s in ['sources','intermediary'])]
            jars=[p for p in candidates if any(n in zipfile.ZipFile(p).namelist() for n in ['net/minecraft/network/chat/HoverEvent.class','net/minecraft/client/gui/Gui.class'])]
        authlib=next(t['authlib'] for t in matrix if t['minecraft']==mc)
        profile=list((Path.home()/'.gradle/caches/modules-2/files-2.1/com.mojang/authlib'/authlib).rglob('*.jar'))
        commands=[]
        for label,classes in [('minecraft',['net.minecraft.network.chat.HoverEvent','net.minecraft.network.chat.HoverEvent$ShowText','net.minecraft.resources.ResourceKey','net.minecraft.client.gui.Gui','net.minecraft.server.level.ServerBossEvent','net.minecraft.world.entity.EntityType','net.minecraft.world.entity.EntityTypes']),('authlib',['com.mojang.authlib.GameProfile'])]:
            cmd=['javap','-classpath',os.pathsep.join(str(p) for p in (jars if label=='minecraft' else profile)),'-public','-s',*classes]
            if shutil.which('rtk'):cmd=['rtk','proxy',*cmd]
            result=subprocess.run(cmd,capture_output=True)
            (output/(mc+'-'+label+'.txt')).write_bytes(result.stdout+result.stderr)
            commands.append({'command':cmd,'exit':result.returncode,'note':'Missing alternative class names are expected; public signatures are retained.'})
        (output/(mc+'-origin.json')).write_text(json.dumps({'jars':[str(p) for p in jars+profile],'authlib':authlib,'commands':commands},indent=2)+'\n')
        print(mc,authlib,flush=True)

if __name__=='__main__':main()
