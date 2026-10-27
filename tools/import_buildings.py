"""Import supplied Sponge v2 blueprints; runtime only reads generated block states."""
import gzip
import json
import shutil
from collections import Counter
from pathlib import Path
from import_mine_arch import read_nbt

ROOT = Path(__file__).resolve().parents[1]


def decode(source):
    root = read_nbt(gzip.decompress(source.read_bytes()))
    root = root.get('Schematic', root)
    assert root['Version'] == 2 and root['DataVersion'] == 3465
    w, h, d = (root[k] for k in ('Width', 'Height', 'Length'))
    palette = {v: k for k, v in root['Palette'].items()}
    ids, value, shift = [], 0, 0
    for byte in root['BlockData']:
        value |= (byte & 127) << shift
        if byte & 128:
            shift += 7
        else:
            ids.append(value)
            value = shift = 0
    assert len(ids) == w * h * d and shift == 0
    blocks = [{'x': i % w, 'y': i // (w*d), 'z': i // w % d,
               'state': palette[state]} for i, state in enumerate(ids)]
    materials = Counter()
    for b in blocks:
        block = b['state'].split('[')[0]
        if block in ('minecraft:air', 'automatons:totem') or 'half=upper' in b['state']:
            continue
        item = 'minecraft:torch' if block == 'minecraft:wall_torch' else block
        materials[item] += 2 if 'type=double' in b['state'] else 1
    return {'size': [w, h, d], 'source_offset': root.get('Offset'),
            'materials': dict(sorted(materials.items())), 'blocks': blocks}


def main():
    configs = [
        ('minehead', 'Automatons-Rustic-Minehead.schem', [7, 7, 7], [3, 1, 3],
         {'cobblestone': 59, 'oak_log': 33, 'cobblestone_stairs': 28,
          'cobblestone_slab': 10, 'oak_planks': 8, 'lantern': 4}),
        ('warehouse', 'Automatons-Runnerton-Warehouse.schem', [7, 6, 8], [3, 1, 7],
         {'oak_planks': 81, 'oak_log': 38, 'oak_slab': 56,
          'cobblestone': 26, 'glass': 12, 'chest': 16, 'torch': 4, 'oak_door': 1})]
    for name, filename, size, anchor, counts in configs:
        saved = ROOT / 'reference' / filename
        sources = [saved] if saved.exists() else list((ROOT / '.codex-remote-attachments').rglob('*' + filename))
        assert len(sources) == 1, sources
        data = decode(sources[0])
        if name == 'minehead':
            # Requested open access: retain the supplied reference, omit its hatch at runtime.
            for block in data['blocks']:
                if block['state'].startswith('minecraft:oak_trapdoor'):
                    block['state'] = 'minecraft:air'
            data['materials'].pop('minecraft:oak_trapdoor', None)
        assert data['size'] == size, data['size']
        assert data['materials'] == {'minecraft:' + k: v for k, v in counts.items()}, data['materials']
        data['anchor'] = anchor
        target = ROOT / 'src/main/resources/data/automatons/buildings' / (name + '.json')
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
        if sources[0].resolve() != saved.resolve():
            shutil.copy2(sources[0], saved)
        print(name, json.dumps({k: v for k, v in data.items() if k != 'blocks'}))
    for pattern, destination in [('*Runnerton-Codex-Prompt-1.txt', 'runnerton-specification.txt'),
                                  ('*Rustic-Minehead-Preview.jpg', 'minehead-preview.jpg')]:
        if not (ROOT / 'reference' / destination).exists():
            source, = (ROOT / '.codex-remote-attachments').rglob(pattern)
            shutil.copy2(source, ROOT / 'reference' / destination)


if __name__ == '__main__':
    main()
