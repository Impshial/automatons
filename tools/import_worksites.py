"""Import the five approved worksite schematics, with reproducible user-requested edits.

Exports Sponge v2 schematics as well as runtime JSON. Never copies container loot.
The supplied originals remain in reference/worksites/source for comparison.
"""
import gzip
import json
import struct
import sys
import zipfile
from collections import Counter
from pathlib import Path
from import_mine_arch import read_nbt

ROOT = Path(__file__).resolve().parents[1]
NAMES = {'builder': 'Builders-Shed', 'farm': 'Farmers-Shed', 'lumber': 'Lumber-Yard',
         'warehouse': 'Runnerton-Warehouse', 'minehead': 'Rustic-Minehead'}


def string(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def tag(kind, name, payload):
    return bytes([kind]) + string(name) + payload


def compound(values):
    return b''.join(tag(kind, name, data) for kind, name, data in values) + b'\0'


def integer(n):
    return struct.pack('>i', n)


def intarray(values):
    return integer(len(values)) + b''.join(integer(n) for n in values)


def export_schem(data):
    palette = {s: i for i, s in enumerate(dict.fromkeys(b['state'] for b in data['blocks']))}
    encoded = bytearray()
    for b in data['blocks']:
        n = palette[b['state']]
        while n > 127:
            encoded.append((n & 127) | 128)
            n >>= 7
        encoded.append(n)
    entities = []
    for f in data['frames']:
        x, y, z = f['tile']
        entities.append(compound([
            (8, 'Id', string('minecraft:item_frame')),
            (9, 'Pos', b'\6' + integer(3) + struct.pack('>ddd', x+.5, y+.5, z+.96875)),
            (1, 'Facing', b'\2'), (3, 'TileX', integer(x)), (3, 'TileY', integer(y)),
            (3, 'TileZ', integer(z)),
            (10, 'Item', compound([(8, 'id', string(f['item'])), (1, 'Count', b'\1')])),
            (1, 'ItemRotation', b'\0'), (5, 'ItemDropChance', struct.pack('>f', 1))]))
    block_entities = []
    for b in data['blocks']:
        name = b['state'].split('[')[0]
        if name not in ('minecraft:chest', 'automatons:totem'):
            continue
        block_entities.append(compound([
            (8, 'Id', string('automatons:station' if name == 'automatons:totem' else name)),
            (11, 'Pos', intarray([b['x'], b['y'], b['z']]))]))
    values = [(3, 'Version', integer(2)), (3, 'DataVersion', integer(3465))]
    values += [(2, key, struct.pack('>h', n)) for key, n in zip(('Width', 'Height', 'Length'), data['size'])]
    values += [(11, 'Offset', intarray([-v for v in data['anchor']])),
               (10, 'Metadata', compound([(3, 'WEOffset'+axis, integer(-v)) for axis, v in zip('XYZ', data['anchor'])])),
               (3, 'PaletteMax', integer(len(palette))),
               (10, 'Palette', compound([(3, s, integer(i)) for s, i in palette.items()])),
               (7, 'BlockData', integer(len(encoded)) + encoded),
               (9, 'BlockEntities', b'\12' + integer(len(block_entities)) + b''.join(block_entities)),
               (9, 'Entities', b'\12' + integer(len(entities)) + b''.join(entities))]
    return gzip.compress(tag(10, 'Schematic', compound(values)), mtime=0)


def decode(raw):
    n = read_nbt(gzip.decompress(raw))
    assert n['Version'] == 2 and n['DataVersion'] == 3465
    w, h, d = (n[k] for k in ('Width', 'Height', 'Length'))
    palette = {v: k for k, v in n['Palette'].items()}
    ids, value, shift = [], 0, 0
    for byte in n['BlockData']:
        value |= (byte & 127) << shift
        if byte & 128:
            shift += 7
        else:
            ids.append(value)
            value = shift = 0
    assert len(ids) == w*h*d and shift == 0
    blocks = [{'x': i % w, 'y': i // (w*d), 'z': i // w % d, 'state': palette[v]} for i, v in enumerate(ids)]
    anchor, = ([b['x'], b['y'], b['z']] for b in blocks if b['state'].startswith('automatons:totem'))
    frames = []
    for e in n['Entities']:
        assert e['Id'] == 'minecraft:item_frame' and e['Facing'] == 2
        frames.append({'tile': [e['TileX'], e['TileY'], e['TileZ']], 'item': e['Item']['id']})
    return {'version': 3, 'size': [w, h, d], 'anchor': anchor, 'frames': frames, 'blocks': blocks}


def materials(data):
    counts = Counter()
    for b in data['blocks']:
        s = b['state']
        name = s.split('[')[0]
        if name in ('minecraft:air', 'automatons:totem') or 'half=upper' in s:
            continue
        counts['minecraft:torch' if name == 'minecraft:wall_torch' else name] += 2 if 'type=double' in s else 1
    for f in data['frames']:
        counts['minecraft:item_frame'] += 1
        counts[f['item']] += 1
    return dict(sorted(counts.items()))


def main(bundle):
    destination = ROOT / 'reference/worksites'
    source = destination / 'source'
    source.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(bundle) as z:
        assert z.testzip() is None
        for name in z.namelist():
            assert Path(name).name == name, 'Only flat, named reference files are accepted'
            (source / name).write_bytes(z.read(name))
    for key, suffix in NAMES.items():
        name = 'Automatons-' + suffix
        data = decode((source / (name+'.schem')).read_bytes())
        cells = {(b['x'], b['y'], b['z']): b for b in data['blocks']}
        changes = []
        if key == 'minehead':
            for b in data['blocks']:
                if b['state'].startswith('minecraft:oak_trapdoor'):
                    b['state'] = 'minecraft:air'
                    changes.append('Removed mine hatch; open ladder access.')
        if key == 'builder':
            # Adjacent doubles have explicit, non-crossing pair states. Four floor-level
            # pairs remain beside the clear center aisle, all within four of the Totem.
            for x in (2, 6):
                old = [cells[(x, 1, z)]['state'] for z in (2, 3)]
                assert all(s.startswith('minecraft:chest') for s in old)
                assert cells[(x, 1, 4)]['state'].startswith(('minecraft:crafting_table', 'minecraft:stonecutter'))
                cells[(x, 1, 2)]['state'] = cells[(x, 1, 4)]['state']
                for z, state in zip((3, 4), old):
                    cells[(x, 1, z)]['state'] = state
            changes.append('Moved front chest pairs from Z=2,3 to Z=3,4 and workbenches to Z=2; all eight halves within four blocks of the Totem, inside its shared work/chest range.')
        data['materials'] = materials(data)
        data['changes'] = changes
        raw = export_schem(data)
        roundtrip = decode(raw)
        assert roundtrip['blocks'] == data['blocks'] and roundtrip['frames'] == data['frames']
        if key != 'warehouse':
            for b in data['blocks']:
                if b['state'].startswith('minecraft:chest'):
                    assert max(abs(b['x']-data['anchor'][0]), abs(b['z']-data['anchor'][2])) <= 4
        (destination / (name+'.schem')).write_bytes(raw)
        (destination / (name+'-Manifest.json')).write_text(json.dumps(data, indent=2)+'\n', encoding='utf-8')
        (ROOT / 'src/main/resources/data/automatons/buildings' / (key+'.json')).write_text(json.dumps(data, indent=2)+'\n', encoding='utf-8')
        print(key, data['size'], data['anchor'], sum(data['materials'].values()), 'items', changes)


if __name__ == '__main__':
    main(Path(sys.argv[1]))
