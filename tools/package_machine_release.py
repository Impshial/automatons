"""Verify and archive the installed 0.9.0 release, retaining all earlier releases."""
from pathlib import Path
import hashlib,json,shutil,subprocess,zipfile

root=Path(__file__).resolve().parents[1]
version='0.9.0'
library=Path('F:/Minecraft/Automatons')
releases=library/'releases'
evidence=library/'verification'/version
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
jar=root/f'build/libs/automatons-{version}.jar'
installed=Path('C:/Users/impsh/curseforge/minecraft/Instances/Rackworks Tester/mods')/jar.name
assert installed.is_file() and sha(installed)==sha(jar)
assert len(list(installed.parent.glob('automatons-*.jar')))==1
for name, marker in [('full-tests-2','All 219 required tests passed'),('final-tests','All 19 required tests passed'),('build','BUILD SUCCESSFUL'),('assets','PASS:'),('handbook','PASS:'),('art','PASS:'),('package','PASS:'),('install','Installed '),('visual-3','Instance client exit 0')]:
    assert marker in (root/f'build/machine-{name}.log').read_text(encoding='utf-8-sig'),name
assert 'MACHINE_VISUAL_COMPLETE' in (root/'instance-smoke.log').read_text(encoding='utf-8')
target=releases/jar.name
archive=releases/f'automatons-{version}-source.zip'
assert not target.exists() and not archive.exists(),'Release is immutable; use another version'
evidence.mkdir(parents=True,exist_ok=True)
for p in (root/'build').glob('machine-*.log'):shutil.copy2(p,evidence/p.name)
shutil.copy2(root/'instance-smoke.log',evidence/'packaged-client.log')
for folder in ['machine-0.9.0','lumite-0.9.0']:shutil.copytree(root/'art'/folder,evidence/folder,dirs_exist_ok=True)
smoke_dirs=sorted((root/'build/packaged-smoke').iterdir(),key=lambda p:p.name)
smoke=next(p for p in reversed(smoke_dirs) if (p/'instance-smoke.log').exists() and 'MACHINE_VISUAL_COMPLETE' in (p/'instance-smoke.log').read_text(encoding='utf-8'))
for p in (smoke/'screenshots').glob('automatons-machine-*.png'):shutil.copy2(p,evidence/p.name)
shutil.copy2(smoke/'smoke-launch.json',evidence/'smoke-launch.json')
assert json.loads((smoke/'smoke-launch.json').read_text())['jar_sha256'].lower()==sha(jar),'Client did not test the final JAR'
shutil.copy2(jar,target)
paths=sorted({p for p in subprocess.check_output(['git','ls-files','-c','-o','--exclude-standard','-z'],cwd=root).decode().split('\0') if p and (root/p).is_file()})
assert not any(p.startswith(('build/','.git/','.gradle/','.tools/','run/','.codex-remote-attachments/')) for p in paths)
with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for p in paths:z.write(root/p,p)
with zipfile.ZipFile(archive) as z:assert z.testzip() is None
shutil.copytree(root/'resourcepacks/Automatons-Guide',releases/version/'resourcepacks/Automatons-Guide')
for p in (root/'docs').glob('*.md'):shutil.copy2(p,library/'documentation'/p.name)
(library/'USAGE.md').write_text((root/'README.md').read_text(encoding='utf-8').replace('(docs/','(documentation/'),encoding='utf-8')
for name in [f'Install-Rackworks-Tester-{version}.ps1','Install-Rackworks-Tester.ps1']:shutil.copy2(root/'tools/install_test_instance.ps1',releases/name)
for name in [f'install_handbook_pack-{version}.py','install_handbook_pack.py']:shutil.copy2(root/'tools/install_handbook_pack.py',releases/name)
manifest=dict(version=version,jar=target.name,sha256=sha(target),source_archive=archive.name,source_archive_sha256=sha(archive),source_files=len(paths),installation_status='installed',installed=str(installed),changes='The Machine, Operatorton, native iron-heavy models, brighter 16x16 Lumite interiors and editable handbook pages.',validation='The full 219-test server suite passed. The final 19-test Machine suite additionally verified persisted cleanup when either assignment endpoint is unloaded. Packaged Forge client verified models, named selection, synchronized multiline Program editor, both cargo pages, manual key driving, HUD and operator boarding. Asset, handbook, native texture, recipe and package checks passed. Earlier diagnostic failures and corrections are preserved in the logs.',install_receipt=(root/'build/machine-install.log').read_text(encoding='utf-8-sig').strip())
(releases/f'{version}-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
with (releases/'SHA256.txt').open('a',encoding='utf-8') as f:
    for p in [target,archive]:f.write(f'{sha(p).upper()}  {p.name}\n')
(library/'README.md').write_text('# Automatons 0.9.0\n\nInstalled in Rackworks Tester for Minecraft 1.20.1 / Forge 47.4.10.\n\nThe Machine adds manual driving, Blade and Bore attachments, Program cards, chunk surface clearing, 108 cargo slots and Operatorton drivers. Machine materials use native Minecraft textures with iron framing. Lumite interiors are brighter without changing their vanilla backgrounds or block geometry.\n\nSee USAGE.md, documentation/MACHINE.md, releases/0.9.0-manifest.json and verification/0.9.0/. The editable handbook is in releases/0.9.0/resourcepacks/Automatons-Guide/.\n',encoding='utf-8')
print(json.dumps(manifest,indent=2))
