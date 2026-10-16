"""Generate small target/consumer declarations; shared Gradle rules do the work."""
import argparse,json,shutil
from pathlib import Path
from run_phase2 import ROOT

def main():
    p=argparse.ArgumentParser();p.add_argument('--targets',required=True);p.add_argument('--metadata',type=Path);a=p.parse_args()
    config=ROOT/'targets.json'
    if a.metadata:
        if config.exists():raise SystemExit('targets.json already pinned; edit reviewed entries explicitly')
        shutil.copy2(a.metadata,config)
    data=json.loads(config.read_text())
    for mc in a.targets.split(','):
        spec=next(t for t in data['targets'] if t['minecraft']==mc);legacy=spec['java']==21
        target=ROOT/'targets'/('mc'+mc);consumer=ROOT/'consumers'/('mc'+mc)
        if target.exists() or consumer.exists():raise SystemExit('Refusing to overwrite existing target '+mc)
        target.mkdir();consumer.mkdir()
        plugin='net.fabricmc.fabric-loom-remap' if legacy else 'net.fabricmc.fabric-loom'
        declarations='import net.fabricmc.loom.task.prod.ServerProductionRunTask\n'
        if legacy:declarations+='import net.fabricmc.loom.task.RemapJarTask\n'
        declarations+='\nplugins { id("'+plugin+'"); `maven-publish` }\n'
        declarations+='tasks.register<ServerProductionRunTask>("phase2Server")\n'
        if legacy:declarations+='tasks.register<RemapJarTask>("remapServerFixtureJar")\n'
        declarations+='apply(from = rootProject.file("gradle/target.gradle"))\n'
        (target/'build.gradle.kts').write_text(declarations,encoding='utf-8')
        c='import net.fabricmc.loom.task.prod.ClientProductionRunTask\n'
        if legacy:c+='import net.fabricmc.loom.task.RemapJarTask\n'
        c+='\nplugins { id("'+plugin+'") version "'+data['loom']+'" }\n'
        c+='tasks.register<ClientProductionRunTask>("prodClient")\n'
        if legacy:c+='tasks.register<RemapJarTask>("remapPeerJar")\n'
        c+='extra["targetMinecraft"] = "'+mc+'"\napply(from = file("../common/consumer.gradle"))\n'
        (consumer/'build.gradle.kts').write_text(c,encoding='utf-8')
        original=ROOT/'consumers/mc1.21.4'
        settings=(original/'settings.gradle.kts').read_text().replace('poc-consumer-1214','poc-consumer-'+mc.replace('.',''))
        (consumer/'settings.gradle.kts').write_text(settings,encoding='utf-8')
        for name in ['gradlew','gradlew.bat','gradle.properties']:
            if (original/name).exists():shutil.copy2(original/name,consumer/name)
        if (original/'gradle').exists():shutil.copytree(original/'gradle',consumer/'gradle')
        print('Generated',mc,plugin,flush=True)

if __name__=='__main__':main()
