"""Archive the verified expansion using the existing versioned distribution layout."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
library = Path('F:/Minecraft/Automatons')
version = re.search(r'(?m)^mod_version=(.+)$', (root / 'gradle.properties').read_text()).group(1).strip()
parser = argparse.ArgumentParser()
parser.add_argument('--client', type=Path, required=True)
parser.add_argument('--focused-log', default='expansion-tests')
args = parser.parse_args()
client = args.client.resolve()
assert client.is_relative_to((root / 'build/packaged-smoke').resolve())
read = lambda p: p.read_text(encoding='utf-8-sig', errors='replace')
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest().upper()
jar = root / f'build/libs/automatons-{version}.jar'
installed = Path('C:/Users/impsh/curseforge/minecraft/Instances/Rackworks Tester/mods') / jar.name
assert installed.is_file() and sha(installed) == sha(jar), 'Install the verified JAR first'
assert len(list(installed.parent.glob('automatons-*.jar'))) == 1
counts = {}
assert re.fullmatch(r'[a-z0-9-]+', args.focused_log), 'Expected a build log basename'
for name in ['full-regression-tests', args.focused_log]:
    log = read(root / f'build/{name}.log')
    match = re.search(r'All (\d+) required tests passed', log)
    assert match and 'BUILD SUCCESSFUL' in log and 'Exception stopping' not in log, name
    counts[name] = int(match.group(1))
for name in ['assets', 'handbook', 'machine-art', 'package']:
    assert 'PASS:' in read(root / f'build/expansion-{name}.log'), name
assert 'Ran 5 tests' in read(root / 'build/expansion-guide-install.log')
assert 'OK' in read(root / 'build/expansion-guide-install.log')
assert 'Imported 10 native layouts' in read(root / 'build/expansion-structures.log')
smoke = read(client / 'instance-smoke.log')
assert 'AUTOMATONS_WORKSITE_VISUAL_COMPLETE' in smoke and 'CURRENT_GUIDE_VALID' in smoke
assert json.loads(read(client / 'smoke-launch.json'))['jar_sha256'] == sha(jar)
release = library / 'releases'
release.mkdir(parents=True, exist_ok=True)
target, source = release / jar.name, release / f'automatons-{version}-source.zip'
assert not target.exists() and not source.exists(), 'Never overwrite a released archive'
evidence = library / 'verification' / version
evidence.mkdir(parents=True, exist_ok=True)
for pattern in ['expansion-*.log', 'full-regression*.log', 'builder-*.log']:
    for path in (root / 'build').glob(pattern):
        shutil.copy2(path, evidence / path.name)
for name in ['instance-smoke.log', 'smoke-launch.json']:
    shutil.copy2(client / name, evidence / name)
shutil.copytree(client / 'screenshots', evidence / 'screenshots', dirs_exist_ok=True)
paths = sorted(set(subprocess.check_output(
    ['git', 'ls-files', '-c', '-o', '--exclude-standard', '-z'], cwd=root
).decode().split('\0')) - {''})
paths = [p for p in paths if (root / p).is_file()]
assert not any(p.startswith(('build/', '.git/', '.gradle/', '.tools/', 'run/')) for p in paths)
with zipfile.ZipFile(source, 'x', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for path in paths:
        archive.write(root / path, path)
with zipfile.ZipFile(source) as archive:
    assert archive.testzip() is None
shutil.copy2(jar, target)
contents = release / version
shutil.copytree(root / 'resourcepacks/Automatons-Guide', contents / 'resourcepacks/Automatons-Guide')
shutil.copytree(root / 'build/expansion-schematics', contents / 'schematics')
shutil.copy2(root / 'build/expansion-import-report.json', contents / 'material-bills.json')
(library / 'documentation').mkdir(exist_ok=True)
for path in (root / 'docs').glob('*.md'):
    shutil.copy2(path, library / 'documentation' / path.name)
for name in [f'Install-Rackworks-Tester-{version}.ps1', 'Install-Rackworks-Tester.ps1']:
    shutil.copy2(root / 'tools/install_test_instance.ps1', release / name)
for name in [f'install_handbook_pack-{version}.py', 'install_handbook_pack.py']:
    shutil.copy2(root / 'tools/install_handbook_pack.py', release / name)
manifest = dict(version=version, jar=target.name, sha256=sha(jar),
                source_archive=source.name, source_sha256=sha(source), source_files=len(paths),
                installation_status='installed', installed=str(installed), tests=counts,
                client_review=str(client), structures=10, handbook_pages=40,
                limitations='Fresh worlds; no multiplayer or long-duration compatibility claim.',
                install_receipt=read(root / 'build/expansion-install.log').strip())
(release / f'{version}-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
with (release / 'SHA256.txt').open('a', encoding='utf-8') as hashes:
    for path in [target, source]:
        hashes.write(f'{sha(path)}  {path.name}\n')
(library / 'README.md').write_text(
    f'# Automatons {version}\n\nInstalled in Rackworks Tester. Use a fresh test world.\n\n'
    f'JAR, source, matching schematics, material bills and editable handbook: releases/. '
    f'Checks and screenshots: verification/{version}/. Current guides: documentation/.\n', encoding='utf-8')
print(json.dumps(manifest, indent=2))
