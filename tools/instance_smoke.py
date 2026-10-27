"""Launch the user-designated Forge instance for an isolated visual smoke test.
Uses installed game libraries and an offline test identity, never reads launcher credentials.
Restores options.txt and Automatons JARs on exit. The harness only opens Automatons Lab.
--isolated copies the designated mod set/config into a fresh build folder; it never changes the live instance.
"""
from pathlib import Path
import json,re,subprocess,zipfile,shutil,sys,time,hashlib
PROJECT=Path(__file__).resolve().parents[1]
SOURCE_INSTANCE=Path(r'C:\Users\impsh\curseforge\minecraft\Instances\Rackworks Tester')
INSTANCE=SOURCE_INSTANCE
if '--isolated' in sys.argv:
    INSTANCE=PROJECT/'build/packaged-smoke'/time.strftime('%Y%m%d-%H%M%S')
    INSTANCE.mkdir(parents=True,exist_ok=False)
    for folder in ['mods','config','defaultconfigs','resourcepacks']:
        source=SOURCE_INSTANCE/folder
        if source.exists():shutil.copytree(source,INSTANCE/folder)
    if (SOURCE_INSTANCE/'options.txt').exists():shutil.copy2(SOURCE_INSTANCE/'options.txt',INSTANCE/'options.txt')
    # Test the external release pack in the disposable instance without touching
    # the user's editable guide or enabling development-source overrides.
    from install_handbook_pack import PACK_NAME, enable_pack
    shutil.copytree(PROJECT/'resourcepacks'/PACK_NAME, INSTANCE/'resourcepacks'/PACK_NAME, dirs_exist_ok=True)
    smoke_options=INSTANCE/'options.txt'
    smoke_options.write_bytes(enable_pack(smoke_options.read_bytes() if smoke_options.exists() else b''))
    print('Isolated game directory:',INSTANCE,flush=True)
    if '--with-jer' in sys.argv:
        shutil.copy2(PROJECT/'build/upstream-current/jer.jar',INSTANCE/'mods/JustEnoughResources-1.20.1-1.4.0.247.jar')
    if '--without-jer' in sys.argv:
        for p in (INSTANCE/'mods').glob('*.jar'):
            if 'justenoughresources' in p.name.lower(): p.unlink()
INSTALL=Path(r'C:\Users\impsh\curseforge\minecraft\Install')
JAVA=Path(r'C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot\bin\java.exe')
BASE=json.loads((INSTALL/'versions/1.20.1/1.20.1.json').read_text(encoding='utf-8'))
FORGE=json.loads((INSTALL/'versions/forge-47.4.10/forge-47.4.10.json').read_text(encoding='utf-8'))
assert FORGE['mainClass']=='cpw.mods.bootstraplauncher.BootstrapLauncher'
NATIVES=PROJECT/'build/smoke-natives';NATIVES.mkdir(parents=True,exist_ok=True)
def allowed(entry):
    if not entry.get('rules'):return True
    result=False
    for rule in entry['rules']:
        os=rule.get('os') or {}
        if os.get('name','windows')!='windows' or os.get('arch','amd64') not in ['amd64','x86_64']:continue
        if any(bool(v) for v in (rule.get('features') or {}).values()):continue
        result=rule['action']=='allow'
    return result
libs={}
for lib in BASE['libraries']+FORGE['libraries']:
    if not allowed(lib):continue
    parts=lib['name'].split(':');key=parts[0]+':'+parts[1]+(':'+parts[3] if len(parts)>3 else '')
    libs[key]=lib
classpath=[];missing=[]
for lib in libs.values():
    artifact=lib.get('downloads',{}).get('artifact',{})
    if artifact.get('path'):path=INSTALL/'libraries'/artifact['path']
    else:
        group,name,version,*classifier=lib['name'].split(':');path=INSTALL/'libraries'/group.replace('.','/')/name/version/(name+'-'+version+('-'+classifier[0] if classifier else '')+'.jar')
    if not path.exists():missing.append(str(path));continue
    classpath.append(path.as_posix())
    if 'natives-windows' in path.name:
        with zipfile.ZipFile(path) as archive:
            for name in archive.namelist():
                if name.lower().endswith('.dll'):(NATIVES/Path(name).name).write_bytes(archive.read(name))
if missing:raise SystemExit('Missing installed libraries:\n'+'\n'.join(missing))
classpath.append((INSTALL/'versions/1.20.1/1.20.1.jar').as_posix())
variables={'natives_directory':NATIVES.as_posix(),'launcher_name':'AutomatonsSmoke','launcher_version':'1','classpath':';'.join(classpath),'library_directory':(INSTALL/'libraries').as_posix(),'classpath_separator':';','version_name':'forge-47.4.10','auth_player_name':'AutomatonsTest','game_directory':INSTANCE.as_posix(),'assets_root':(INSTALL/'assets').as_posix(),'assets_index_name':BASE['assetIndex']['id'],'auth_uuid':'f37d81b70e384c42bbebfeac507a9124','auth_access_token':'0','clientid':'0','auth_xuid':'0','user_type':'legacy','version_type':'release'}
def expand(value):return re.sub(r'\$\{([^}]+)\}',lambda m:variables[m[1]],value)
def args(entries):
    result=[]
    for entry in entries:
        if isinstance(entry,str):result.append(expand(entry))
        elif allowed(entry):result.extend(expand(v) for v in (entry['value'] if isinstance(entry['value'],list) else [entry['value']]))
    return result
jvm=['-Xmx4G','-Dautomatons.visualSmoke=true']+args(BASE['arguments']['jvm'])+args(FORGE['arguments']['jvm'])
if '--quick' in sys.argv:jvm.append('-Dautomatons.smokeTicks='+('640' if '--jei-review' in sys.argv else '570'))
if '--jei-review' in sys.argv:jvm.append('-Dautomatons.jeiReview=true')
if '--fall-review' in sys.argv:jvm.append('-Dautomatons.fallReview=true')
if '--infusion-review' in sys.argv:jvm.append('-Dautomatons.infusionReview=true')
if '--mining-review' in sys.argv:jvm.append('-Dautomatons.miningReview=true')
if '--running-review' in sys.argv:jvm.append('-Dautomatons.runningReview=true')
if '--worksite-review' in sys.argv:jvm.append('-Dautomatons.worksiteReview=true')
if '--lumina-review' in sys.argv:jvm.append('-Dautomatons.luminaReview=true')
if '--current-review' in sys.argv:jvm.append('-Dautomatons.currentReview=true')
if '--luminall-review' in sys.argv:jvm.append('-Dautomatons.luminallReview=true')
if '--machine-review' in sys.argv:jvm.append('-Dautomatons.machineReview=true')
jvm=[a+',1.20.1.jar' if a.startswith('-DignoreList=') else a for a in jvm]
game=args(BASE['arguments']['game'])+args(FORGE['arguments']['game'])+['--width','1280','--height','960']
argfile=PROJECT/'build/instance-smoke.args'
argfile.write_text('\n'.join('"'+a.replace('\\','/').replace('"','\\"')+'"' for a in jvm+[FORGE['mainClass']]+game),encoding='utf-8')
if '--inspect' in sys.argv:
    print('Ready: '+str(len(classpath))+' installed libraries, Forge 47.4.10, offline smoke identity.');sys.exit(0)
version=re.search(r'(?m)^mod_version=(.+)$',(PROJECT/'gradle.properties').read_text()).group(1).strip()
jar=PROJECT/'build/libs'/f'automatons-{version}.jar';target=INSTANCE/'mods'/jar.name
assert jar.is_file(), f'Build {version} first'
backup=INSTANCE/'.automatons-test-backups'/time.strftime('%Y%m%d-%H%M%S');backup.mkdir(parents=True)
original_mods=list((INSTANCE/'mods').glob('automatons-*.jar'))
# Finish every backup before changing the selected game directory.
for old in original_mods:
    assert old.resolve().parent==(INSTANCE/'mods').resolve()
    shutil.copy2(old,backup/old.name)
options=INSTANCE/'options.txt';original=options.read_bytes() if options.exists() else None
if original is not None:shutil.copy2(options,backup/'options.txt')
try:
    for old in original_mods:old.unlink()
    shutil.copy2(jar,target)
    if '--isolated' in sys.argv:
        (INSTANCE/'smoke-launch.json').write_text(json.dumps({'version':version,
            'jar_sha256':hashlib.sha256(target.read_bytes()).hexdigest().upper(),
            'options':sys.argv[1:]},indent=2),encoding='utf-8')
    lab=INSTANCE/'saves/Automatons Lab'
    if not lab.exists():shutil.copytree(PROJECT/'run/saves/Automatons Lab',lab)
    # Review the new default without inheriting the old lab world's display setting.
    lab_config=lab/'serverconfig/automatons-server.toml'
    if lab_config.exists():
        text=lab_config.read_text(encoding='utf-8')
        lab_config.write_text(re.sub(r'(?m)^(\s*displayHeight\s*=\s*)1\.75\s*$',r'\g<1>1.4',text),encoding='utf-8')
    with (PROJECT/'instance-smoke.log').open('w',encoding='utf-8') as log:
        process=subprocess.Popen([str(JAVA),'@'+str(argfile)],cwd=INSTANCE,stdout=log,stderr=subprocess.STDOUT)
        print('Instance client PID',process.pid,flush=True)
        try:
            result=process.wait(timeout=360)
        except subprocess.TimeoutExpired:
            # Only terminate the test process started above, never another Minecraft session.
            process.terminate()
            process.wait(timeout=15)
            result=124
            print('Smoke client exceeded its six-minute limit; see instance-smoke.log.',flush=True)
        if '--current-review' in sys.argv and result == 0:
            if not (INSTANCE/'current-review-passed.json').exists() or (INSTANCE/'current-review-failed.txt').exists():
                result=1
                print('Handbook/JER review did not pass; inspect the review marker and runtime log.',flush=True)
        if '--luminall-review' in sys.argv and result == 0:
            log.flush()
            if 'LUMINALL_VISUAL_COMPLETE' not in (PROJECT/'instance-smoke.log').read_text(encoding='utf-8'):
                result=1
                print('Luminall visual review did not complete; inspect the runtime log.',flush=True)
        print('Instance client exit',result,flush=True)
        if '--machine-review' in sys.argv and 'MACHINE_VISUAL_COMPLETE' not in (PROJECT/'instance-smoke.log').read_text(encoding='utf-8'):
            result=1
            print('Machine review did not complete.',flush=True)
finally:
    if '--isolated' in sys.argv and (PROJECT/'instance-smoke.log').exists():
        shutil.copy2(PROJECT/'instance-smoke.log',INSTANCE/'instance-smoke.log')
    if original is not None:options.write_bytes(original)
    elif options.exists():options.unlink()
    # The smoke is temporary. Final deployment occurs only after reviewing its results.
    if target.exists():target.unlink()
    for old in original_mods:shutil.copy2(backup/old.name,old)
sys.exit(result)
