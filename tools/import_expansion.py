"""Adapt the supplied native structures to the runtime schema, without inventing geometry.

Native NBT is the block source; handoff metadata supplies semantic operations. Preserve cells
are absent. A mismatch aborts the import. No source generator or executable from the ZIP runs.
"""
from pathlib import Path
import gzip
import hashlib
import json
import shutil
from import_mine_arch import read_nbt

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'reference/schematics-2026-10-27/Automatons-Schematics'
DEST = ROOT / 'src/main/resources/data/automatons'


def state_string(entry):
    result = entry['Name']
    if entry.get('Properties'):
        result += '[' + ','.join(f'{k}={v}' for k, v in sorted(entry['Properties'].items())) + ']'
    return result


def canonical(state):
    name, _, properties = state.partition('[')
    return name + ('[' + ','.join(sorted(properties.rstrip(']').split(','))) + ']' if properties else '')


def main():
    reports = []
    for path in sorted((SOURCE / 'manifests').glob('*.json')):
        manifest = json.loads(path.read_text(encoding='utf-8'))
        binary = SOURCE / 'structures' / (path.stem + '.nbt')
        nbt = read_nbt(gzip.decompress(binary.read_bytes()))
        palette = [state_string(entry) for entry in nbt['palette']]
        decoded = {tuple(b['pos']): palette[b['state']] for b in nbt['blocks']}
        expected = {tuple(b['pos']): canonical(b['state']) for b in manifest['blocks']}
        assert decoded == expected, f'NBT/manifest mismatch: {path.stem}'
        assert list(nbt['size']) == manifest['dimensions']
        assert not any('structure_void' in s for s in decoded.values())
        cells = []
        for block in manifest['blocks']:
            x, y, z = block['pos']
            cells.append(dict(x=x, y=y, z=z, state=decoded[(x, y, z)],
                              role=block['role'], operation=block['operation']))
        frames, stands = [], []
        for entity in manifest['entities']:
            if entity['id'] == 'minecraft:item_frame':
                frames.append(dict(tile=entity['blockpos'], item=entity['nbt']['Item']['id'],
                                   facing=entity['nbt']['Facing']))
            elif entity['id'] == 'minecraft:armor_stand':
                assert all(not item for item in entity['nbt']['ArmorItems'])
                stands.append(entity['blockpos'])
            else:
                raise ValueError('Unsupported construction entity: ' + entity['id'])
        data = dict(version=4, size=manifest['dimensions'], anchor=manifest['origin_local'],
                    logical_min=manifest['logical_min'], default_front='south',
                    kind=manifest['kind'], blocks=cells, frames=frames, armor_stands=stands,
                    materials=manifest['material_bill']['construction_items'],
                    material_bill=manifest['material_bill'], features=manifest['features'],
                    worksite=manifest['worksite'])
        target = DEST / 'buildings' / path.name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
        structures = DEST / 'structures' / 'expansion'
        structures.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(binary, structures / binary.name)
        exports = ROOT / 'build/expansion-schematics'
        exports.mkdir(parents=True, exist_ok=True)
        schematic = SOURCE / 'schematics' / (path.stem + '.schem')
        shutil.copyfile(schematic, exports / schematic.name)
        reports.append(dict(name=path.stem, blocks=len(cells), size=data['size'],
                            sha256=hashlib.sha256(binary.read_bytes()).hexdigest(),
                            material_bill=manifest['material_bill']))
    (ROOT / 'build/expansion-import-report.json').write_text(json.dumps(reports, indent=2)+'\n')
    print(f'Imported {len(reports)} native layouts; every explicit cell matches its manifest.')


if __name__ == '__main__':
    main()
