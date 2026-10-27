"""Convert the supplied Sponge schematic to portable, reviewable placement data."""
import gzip
import json
import struct
import sys
from collections import Counter
from pathlib import Path


def read_nbt(data):
    offset = 0

    def number(fmt):
        nonlocal offset
        value = struct.unpack_from('>' + fmt, data, offset)[0]
        offset += struct.calcsize(fmt)
        return value

    def string():
        nonlocal offset
        size = number('H')
        value = data[offset:offset + size].decode('utf-8')
        offset += size
        return value

    def payload(kind):
        nonlocal offset
        if kind in (1, 2, 3, 4, 5, 6):
            return number({1: 'b', 2: 'h', 3: 'i', 4: 'q', 5: 'f', 6: 'd'}[kind])
        if kind == 8:
            return string()
        if kind == 9:
            subtype, length = number('b'), number('i')
            return [payload(subtype) for _ in range(length)]
        if kind == 10:
            result = {}
            while (subtype := number('b')):
                name = string()
                result[name] = payload(subtype)
            return result
        if kind in (7, 11, 12):
            return [number({7: 'B', 11: 'i', 12: 'q'}[kind]) for _ in range(number('i'))]
        raise ValueError(kind)

    kind = number('b')
    string()
    return payload(kind)


def convert(source):
    root = read_nbt(gzip.decompress(source.read_bytes()))
    root = root.get('Schematic', root)
    width, height, length = (root[k] for k in ('Width', 'Height', 'Length'))
    block_data = root.get('Blocks', root)
    palette = {v: k for k, v in block_data['Palette'].items()}
    encoded = block_data.get('Data', block_data.get('BlockData'))
    ids, value, shift = [], 0, 0
    for byte in encoded:
        value |= (byte & 127) << shift
        if byte & 128:
            shift += 7
        else:
            ids.append(value)
            value, shift = 0, 0
    assert len(ids) == width * height * length
    blocks = []
    for index, state in enumerate(ids):
        name = palette[state]
        blocks.append({'x': index % width, 'y': index // (width * length),
                       'z': (index // width) % length, 'state': name})
    counts = Counter(b['state'].split('[')[0] for b in blocks)
    assert (width, height, length) == (5, 6, 5), (width, height, length)
    assert counts['minecraft:cobblestone'] == 38 and counts['minecraft:oak_planks'] == 4
    assert counts['minecraft:oak_trapdoor'] == 1
    return {'size': [width, height, length], 'source_offset': root.get('Offset', []),
            'materials': dict(counts), 'blocks': blocks}


if __name__ == '__main__':
    source = Path(sys.argv[1])
    root = Path(__file__).resolve().parents[1]
    result = convert(source)
    destination = root / 'src/main/resources/data/automatons/mine/arch.json'
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k: v for k, v in result.items() if k != 'blocks'}))
    for y in range(6):
        print('Y', y)
        for z in range(5):
            print(''.join({'minecraft:air': '.', 'minecraft:cobblestone': '#',
                           'minecraft:oak_planks': 'P', 'minecraft:oak_trapdoor': 'T'}[
                               b['state'].split('[')[0]] for b in result['blocks']
                         if b['y'] == y and b['z'] == z))
