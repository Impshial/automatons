"""Archive the installed, verified 0.9.1 release without replacing earlier releases."""
from pathlib import Path
import hashlib,json,re,shutil,subprocess,zipfile
root=Path(__file__).resolve().parents[1];library=Path('F:/Minecraft/Automatons');version='0.9.1'
read=lambda p:p.read_text(encoding='utf-8-sig')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest().upper()
jar=root/f'build/libs/automatons-{version}.jar'
installed=Path('C:/Users/impsh/curseforge/minecraft/Instances/Rackworks Tester/mods')/jar.name
assert installed.is_file() and sha(installed)==sha(jar)
assert len(list(installed.parent.glob('automatons-*.jar')))==1
for log,marker in [('portable-final-tests','All 32 required tests passed'),('portable-client-1','Instance client exit 0'),('portable-assets','PASS:'),('portable-art','PASS:'),('portable-handbook','PASS:'),('portable-package','PASS:'),('portable-install','Installed ')]:
    assert marker in read(root/f'build/{log}.log'),log
full=read(root/'build/portable-full-tests-1.log')
failures=re.findall(r'LogTestReporter\]: (\S+) failed!',full)
assert failures==['distant_lumberton_follows_partial_routes_home'],failures
assert '232 GAME TESTS COMPLETE' in full
smoke=root/'build/packaged-smoke/20261025-232321'
client=read(smoke/'instance-smoke.log')
for marker in ['MACHINE_VISUAL_COMPLETE','DRAIN_STANDING_PASS','DRAIN_STEP_OFF_PASS']:assert marker in client,marker
assert json.loads(read(smoke/'smoke-launch.json'))['jar_sha256']==sha(jar)
releases=library/'releases';target=releases/jar.name;archive=releases/f'automatons-{version}-source.zip'
assert not target.exists() and not archive.exists(),'Release archive already exists; do not overwrite'
evidence=library/f'verification/{version}';evidence.mkdir(parents=True,exist_ok=True)
for p in (root/'build').glob('portable-*.log'):shutil.copy2(p,evidence/p.name)
for p in (root/'build').glob('mining-access-*.log'):shutil.copy2(p,evidence/p.name)
for p in (smoke/'screenshots').glob('automatons-machine-*.png'):shutil.copy2(p,evidence/p.name)
for name in ['instance-smoke.log','smoke-launch.json']:shutil.copy2(smoke/name,evidence/name)
shutil.copytree(root/'art/machine-0.9.1',evidence/'previews',dirs_exist_ok=True)
paths=sorted({p for p in subprocess.check_output(['git','ls-files','-c','-o','--exclude-standard','-z'],cwd=root).decode().split('\0') if p and (root/p).is_file()})
assert not any(p.startswith(('build/','.git/','.gradle/','.tools/','run/','.codex-remote-attachments/')) for p in paths)
shutil.copy2(jar,target)
with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for p in paths:z.write(root/p,p)
with zipfile.ZipFile(archive) as z:assert z.testzip() is None
shutil.copytree(root/'resourcepacks/Automatons-Guide',releases/version/'resourcepacks/Automatons-Guide')
for p in (root/'docs').glob('*.md'):shutil.copy2(p,library/'documentation'/p.name)
for name in [f'Install-Rackworks-Tester-{version}.ps1','Install-Rackworks-Tester.ps1']:shutil.copy2(root/'tools/install_test_instance.ps1',releases/name)
for name in [f'install_handbook_pack-{version}.py','install_handbook_pack.py']:shutil.copy2(root/'tools/install_handbook_pack.py',releases/name)
manifest=dict(version=version,jar=target.name,sha256=sha(jar),source_archive=archive.name,source_archive_sha256=sha(archive),source_files=len(paths),installation_status='installed',installed=str(installed),changes='Mine startup/ladder/recall repairs; tunnel default; Luna natural stones; nonoverlapping Machine faces; portable Machine and Tank; proportional bottle and bulk Tank/bucket refueling; Lumina Drain; temporary fluid spills; edge-triggered engine key and startup feedback; whole Operator Station selection.',validation='The 232-test full regression run passed 231 tests; its only failure was a non-ticking far chunk in the 117-block recall fixture. Correcting the fixture chunk bounds and adding a seat-face test produced a final 32/32 focused pass, covering all changed mining, Machine and fluid behavior. Across both runs 233 distinct tests passed. The exact installed JAR passed the isolated Forge client review, including held-R repeat, startup HUD, real driving, operator boarding, filled Tank/Drain rendering, conserved drain transfer and step-off stopping. Native texture/UV, no-overlap geometry, handbook and package checks passed. Diagnostic logs are retained.',install_receipt=read(root/'build/portable-install.log').strip())
(releases/f'{version}-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
with (releases/'SHA256.txt').open('a',encoding='utf-8') as f:
    for p in [target,archive]:f.write(f'{sha(p)}  {p.name}\n')
(library/'README.md').write_text('# Automatons 0.9.1\n\nInstalled in Rackworks Tester for Minecraft 1.20.1 / Forge 47.4.10.\n\nIncludes mine access and recall fixes, portable Machines and Tanks, bulk refueling, a craftable Lumina Drain, Tank spills, corrected Machine faces and Operator Station selection. See documentation/MACHINE.md, documentation/LUMINALL.md, releases/0.9.1-manifest.json and verification/0.9.1/. The editable handbook is in releases/0.9.1/resourcepacks/Automatons-Guide/.\n',encoding='utf-8')
print(json.dumps({'version':version,'jar':str(target),'sha256':sha(jar),'source':str(archive),'evidence':str(evidence)},indent=2))
