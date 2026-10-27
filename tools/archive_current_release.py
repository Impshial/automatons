"""Archive the verified and installed 0.7.0 working tree; never edits a player world."""
from pathlib import Path
import hashlib, json, re, shutil, subprocess, zipfile, sys

root = Path(__file__).resolve().parents[1]
library = Path('F:/Minecraft/Automatons')
version = '0.7.0'
ready_only = '--ready-only' in sys.argv

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest().upper()
def read(path):
    data = path.read_bytes()
    return data.decode('utf-16' if data.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig')

receipt = json.loads(read(root/f'build/{version}-reviewed-jar.json'))
jar = root/f'build/libs/automatons-{version}.jar'
assert sha(jar) == receipt['sha256']
assert 'All 153 required tests passed' in read(root/receipt['test_log'])
assert 'BUILD SUCCESSFUL' in read(root/receipt['test_log'])
assert 'BUILD SUCCESSFUL' in read(root/receipt['build_log'])
assert 'BUILD SUCCESSFUL' in read(root/'build/current-server-final.log')
for key, jer in [('jer_review', True), ('no_jer_review', False)]:
    folder = root/receipt[key]
    result = json.loads(read(folder/'current-review-passed.json'))
    assert result['success'] and result['pages'] == 29 and result['jer'] == jer
    assert not (folder/'current-review-failed.txt').exists()
    assert json.loads(read(folder/'smoke-launch.json'))['jar_sha256'] == sha(jar)
    runtime = read(folder/'instance-smoke.log')
    assert 'CURRENT_REVIEW_COMPLETE' in runtime and 'assembly-after-reload' in runtime
    if jer:
        assert 'CURRENT_JER_VALID deep=false entries=1' in runtime
        assert 'CURRENT_JER_VALID deep=true entries=1' in runtime
lumina = root/receipt['lumina_review']
assert json.loads(read(lumina/'smoke-launch.json'))['jar_sha256'] == sha(jar)
runtime = read(lumina/'instance-smoke.log')
assert 'LUMINA_DARK_REVIEW blockLight=0 skyLight=0' in runtime
assert 'LUMINA_CLIENT_BATCH fourInfused=true balanceThirds=0' in runtime
assert len(list((lumina/'screenshots').glob('automatons-lumina-*.png'))) == 16
comparison = json.loads(read(root/'build/lumina-verification/current-comparison.json'))
assert comparison['existingSampleChunksUnchanged'] == 12 and comparison['pendingHistoryUnchanged']
assert json.loads(read(root/'build/current-asset-regeneration.json'))['stable']

installed = Path('C:/Users/impsh/curseforge/minecraft/Instances/Rackworks Tester/mods')/jar.name
backup = None
if not ready_only:
    assert installed.exists() and sha(installed) == sha(jar)
    assert len(list(installed.parent.glob('automatons-*.jar'))) == 1
    install_log = read(root/'build/current-install.log')
    backup = Path(re.search(r'Previous releases preserved in (.+)', install_log).group(1).strip())
    assert sha(backup/'automatons-0.6.0.jar') == '295A47E9A3CCB0DD545966FAB91EE829BE62B702F74304E2F1BB5DFF90273CE1'

for folder in ['documentation', 'releases', f'verification/{version}', f'screenshots/{version}', 'models/resource-models']:
    (library/folder).mkdir(parents=True, exist_ok=True)
for source, destination in [('src/main/resources/data', f'reference/data-pack-{version}'),
        ('src/main/resources/assets/automatons/textures', 'textures'),
        ('src/main/resources/assets/automatons/models', 'models/resource-models'),
        ('src/main/resources/assets/automatons/guides', 'reference/handbook'), ('reference', 'reference')]:
    shutil.copytree(root/source, library/destination, dirs_exist_ok=True)
for path in (root/'docs').glob('*.md'): shutil.copy2(path, library/'documentation'/path.name)
(library/'documentation/USAGE.md').write_text(read(root/'README.md').replace('(docs/', '('), encoding='utf-8')

evidence = library/f'verification/{version}'
for key, label in [('jer_review', 'client-jer'), ('no_jer_review', 'client-no-jer'), ('lumina_review', 'client-lumina')]:
    folder = root/receipt[key]
    output = evidence/label; output.mkdir(exist_ok=True)
    for name in ['instance-smoke.log', 'smoke-launch.json', 'current-review-passed.json']:
        if (folder/name).exists(): shutil.copy2(folder/name, output/name)
    captures = library/f'screenshots/{version}'/label
    captures.mkdir(exist_ok=True)
    for path in (folder/'screenshots').glob('automatons-*.png'): shutil.copy2(path, captures/path.name)
for pattern in ['current-*.log', 'current-*.json', f'{version}-reviewed-jar.json']:
    for path in (root/'build').glob(pattern): shutil.copy2(path, evidence/path.name)
shutil.copy2(root/'build/lumina-verification/current-comparison.json', evidence/'worldgen-comparison.json')
for folder in ['current8-4217', 'current8-8675309', 'current8-minus60241024', 'current-old-save']:
    target = evidence/'worlds'/folder; target.mkdir(parents=True, exist_ok=True)
    shutil.copy2(root/f'build/lumina-verification/{folder}/lumina-world-report.json', target/'report.json')
for name in ['Install-Rackworks-Tester.ps1', f'Install-Rackworks-Tester-{version}.ps1']:
    shutil.copy2(root/'tools/install_test_instance.ps1', library/'releases'/name)
shutil.copy2(jar, library/'releases'/jar.name)
files = sorted(set(n for n in subprocess.check_output(['git','ls-files','-c','-o','--exclude-standard','-z'], cwd=root).decode().split('\0') if n and (root/n).is_file()))
assert not any(n.startswith(('build/','.git/','.gradle/','.tools/','run/','.codex-remote-attachments/')) for n in files)
archive = library/f'releases/automatons-{version}-source.zip'
if not archive.exists():
    with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as out:
        for name in files: out.write(root/name,name)
with zipfile.ZipFile(archive) as check:
    assert check.testzip() is None
    assert set(check.namelist()) == set(files), 'Versioned source snapshot changed; do not overwrite it'
    for name in check.namelist(): assert check.read(name) == (root/name).read_bytes()
manifest = {**receipt, 'minecraft':'1.20.1', 'forge':'47.4.10', 'java':17, 'protocol':8,
    'jar':jar.name, 'source_archive':archive.name, 'source_archive_sha256':sha(archive),
    'source_files':len(files), 'source_base_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
    'source_state':'Working-tree snapshot including all changes through 0.7.0',
    'installation_status':'ready' if ready_only else 'installed', 'installed':None if ready_only else str(installed), 'previous_jar_backup':str(backup) if backup else None,
    'required_game_tests_passed':153, 'guideme':'20.1.15 required both sides',
    'jer':'1.4.0.247 optional client', 'jei':'15.56.0.205 tested optional client',
    'handbook':{'pages':29,'live_recipes':17,'item_links':20,'structures':7,'gui_images':4},
    'worldgen':{'seeds':3,'chunks':36,'size6_ore':911,'size8_ore':1292,'size6_clusters':361,'size8_clusters':301},
    'existing_sample_chunks_unchanged':12,'pending_history_unchanged':True,
    'all_current_game_pngs_preserved':42,'retrogen_live_world_invoked':False,
    'unavailable_checks':['Physical GuideME shortcut press (direct GLFW polling)', 'Dark GuideME theme (not exposed by pinned release)', 'Separate multiplayer client session'],
    'collector_implemented':False, 'optional_mods_installed_by_installer':False}
(library/f'releases/{version}-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
hashfile = library/'releases/SHA256.txt'; hashes = read(hashfile)
for artifact in [library/'releases'/jar.name,archive]:
    line = f'{sha(artifact)}  {artifact.name}'
    if artifact.name in hashes: assert line in hashes.splitlines()
    else: hashes += line+'\n'
hashfile.write_text(hashes,encoding='utf-8')
(library/'README.md').write_text('''# Automatons 0.7.0 release library

Minecraft 1.20.1 / Forge 47.4.10 / Java 17. GuideME 20.1.15 is required on client and server. JEI/JER are optional.

Builders construct from outside their footprint, including empty foundations. This release also adds the handbook, fuel pieces, three-level trunk cuts and larger new-chunk Lumite clusters. Automatons commands and retrogen execution are retired. Supplied texture bytes are preserved.

- [Player guide](documentation/USAGE.md)
- [Verification and runtime limits](documentation/VERIFICATION.md)
- [Handbook authoring](documentation/HANDBOOK-AUTHORING.md)
- [JAR](releases/automatons-0.7.0.jar)
- [Source snapshot](releases/automatons-0.7.0-source.zip)
- [Manifest](releases/0.7.0-manifest.json)

INSTALLATION_STATUS All 153 required server tests passed. Previous versioned releases and diagnostic evidence are preserved.
'''.replace('INSTALLATION_STATUS', 'Ready to install after Rackworks Tester is saved and closed.' if ready_only else 'Installed in Rackworks Tester with 0.6.0 backed up.'),encoding='utf-8')
print(json.dumps({'jar_sha256':sha(jar),'source_sha256':sha(archive),'source_files':len(files),'installation_status':manifest['installation_status']},indent=2))
