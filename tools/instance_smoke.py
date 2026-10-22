"""Launch the user-designated Forge instance for an isolated visual smoke test.
Uses installed game libraries and an offline test identity, never reads launcher credentials.
Restores options.txt when the test process exits. The smoke harness only opens Automatons Lab.
"""
from pathlib import Path
import json,re,subprocess,zipfile,shutil,sys,time
PROJECT=Path(__file__).resolve().parents[1]
INSTANCE=Path(r'C:\Users\impsh\curseforge\minecraft\Instances\Rackworks Tester')
INSTALL=Path(r'C:\Users\impsh\curseforge\minecraft\Install')
JAVA=Path(r'C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot\bin\java.exe')
BASE=json.loads((INSTALL/'versions/1.20.1/1.20.1.json').read_text())
FORGE=json.loads((INSTALL/'versions/forge-47.4.10/forge-47.4.10.json').read_text())
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
jvm=[a+',1.20.1.jar' if a.startswith('-DignoreList=') else a for a in jvm]
game=args(BASE['arguments']['game'])+args(FORGE['arguments']['game'])+['--width','1280','--height','800']
argfile=PROJECT/'build/instance-smoke.args'
argfile.write_text('\n'.join('"'+a.replace('\\','/').replace('"','\\"')+'"' for a in jvm+[FORGE['mainClass']]+game),encoding='utf-8')
if '--inspect' in sys.argv:
    print('Ready: '+str(len(classpath))+' installed libraries, Forge 47.4.10, offline smoke identity.');sys.exit(0)
jar=PROJECT/'build/libs/automatons-0.1.0.jar';target=INSTANCE/'mods'/jar.name
backup=INSTANCE/'.automatons-test-backups'/time.strftime('%Y%m%d-%H%M%S');backup.mkdir(parents=True)
if target.exists():shutil.copy2(target,backup/target.name)
shutil.copy2(jar,target)
lab=INSTANCE/'saves/Automatons Lab'
if not lab.exists():shutil.copytree(PROJECT/'run/saves/Automatons Lab',lab)
options=INSTANCE/'options.txt';original=options.read_bytes() if options.exists() else None
if original is not None:shutil.copy2(options,backup/'options.txt')
try:
    with (PROJECT/'instance-smoke.log').open('w',encoding='utf-8') as log:
        process=subprocess.Popen([str(JAVA),'@'+str(argfile)],cwd=INSTANCE,stdout=log,stderr=subprocess.STDOUT)
        print('Instance client PID',process.pid,flush=True)
        result=process.wait(timeout=300)
        print('Instance client exit',result,flush=True)
finally:
    if original is not None:options.write_bytes(original)
sys.exit(result)


