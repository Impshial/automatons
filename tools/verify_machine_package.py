from pathlib import Path
import zipfile,json,re
root=Path(__file__).resolve().parents[1]
version=re.search(r'(?m)^mod_version=(.+)$',(root/'gradle.properties').read_text()).group(1).strip()
jar=root/f'build/libs/automatons-{version}.jar'
with zipfile.ZipFile(jar) as z:
    names=set(z.namelist())
    assert 'dev/automatons/Machine.class' in names and 'dev/automatons/client/ProgramEditor.class' in names
    assert not any(n.startswith('assets/automatons/guides/') for n in names),'Handbook pages must remain external'
    resources=root/'src/main/resources'
    count=0
    for p in resources.rglob('*'):
        if p.is_file() and p.suffix in ['.png','.json','.nbt']:
            rel=p.relative_to(resources).as_posix()
            if rel.startswith('assets/automatons/guides/'):continue
            assert z.read(rel)==p.read_bytes(),rel
            count+=1
    assert f'version="{version}"' in z.read('META-INF/mods.toml').decode().replace(' ','')
    for name in ['lumina_collector']:
        loot=json.loads(z.read(f'data/automatons/loot_tables/blocks/{name}.json'))
        assert any(op['source']=='MachineFuelThirds' for op in loot['pools'][0]['entries'][0]['functions'][0]['ops'])
    tank=json.loads(z.read('data/automatons/loot_tables/blocks/tank.json'))
    assert not tank['pools'][0]['entries'][0].get('functions'),'Ordinary Tank drops must be empty'
    for name in ['MachineItem','LuminaDrain','FluidSpill']:
        assert f'dev/automatons/{name}.class' in names
print(f'PASS: {version} JAR matches {count} authored assets/data/structures; portable/fluid classes present; handbook external; ordinary Tank loot empty.')
