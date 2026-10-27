"""Native pixel-grid assets and recipes for the expansion; no supplied geometry is redrawn."""
import json
from asset_pixels import RES, canvas, png, rect, rgb, write

for name, drawing in {
    'dead_plant': [
        '                ', '                ', '    d           ',
        '     b      d   ', '     b     b    ', '  d   b   b     ',
        '   b  b  b      ', '    b b b       ', '     bbb   d    ',
        '      b   b     ', '      b  b      ', '      bbb       ',
        '      b         ', '     bb         ', '    dbbd        ',
        '   ddbbdd       '],
    'dead_crop': [
        '                ', '                ', '                ',
        '                ', '     d          ', '      b   d     ',
        '  d   b   b     ', '   b  b  b  d   ', '   b  b  b b    ',
        '    b b  bb     ', '    b b  b      ', '    bb  bb      ',
        '     b  b       ', '     b  b       ', '    dbbbbd      ',
        '   ddbbbbdd     '],
}.items():
    pixels = canvas()
    for y, row in enumerate(drawing):
        for x, char in enumerate(row):
            if char != ' ':
                pixels[y][x] = rgb('866348' if char == 'b' else '514536')
    png(RES / f'assets/automatons/textures/block/{name}.png', pixels)
    write(RES / f'assets/automatons/models/block/{name}.json', {
        'parent': 'minecraft:block/cross', 'render_type': 'minecraft:cutout',
        'textures': {'cross': f'automatons:block/{name}'}})
    write(RES / f'assets/automatons/blockstates/{name}.json', {
        'variants': {'': {'model': f'automatons:block/{name}'}}})
    write(RES / f'assets/automatons/models/item/{name}.json', {
        'parent': 'minecraft:item/generated', 'textures': {'layer0': f'automatons:block/{name}'}})
    # User override: even enchanted tools and worker clearing must yield nothing.
    write(RES / f'data/automatons/loot_tables/blocks/{name}.json', {'type': 'minecraft:block', 'pools': []})

parts = {'head': 'head', 'core': 'torso', 'arm': 'arm', 'leg': 'leg'}
for wooden, armored in parts.items():
    recipe = json.loads((RES / f'data/automatons/recipes/automaton_{wooden}.json').read_text())
    for symbol, ingredient in recipe['key'].items():
        if ingredient.get('tag') == 'automatons:overworld_logs':
            recipe['key'][symbol] = {'item': 'minecraft:iron_ingot'}
    recipe['result']['item'] = f'automatons:armored_{armored}'
    write(RES / f'data/automatons/recipes/armored_{armored}.json', recipe)
    pixels = canvas()
    bounds = {'head': (3, 3, 10, 10), 'torso': (2, 2, 12, 12), 'arm': (5, 2, 6, 12), 'leg': (5, 2, 6, 13)}[armored]
    x, y, width, height = bounds
    rect(pixels, x, y, width, height, rgb('363c45'))
    rect(pixels, x+1, y+1, width-2, height-2, rgb('a3adb3'))
    rect(pixels, x+1, y+1, width-2, 1, rgb('e5e7df'))
    if armored == 'head':
        rect(pixels, 4, 7, 8, 3, rgb('282c34'))
        rect(pixels, 5, 8, 2, 1, rgb('6ce5b3'))
        rect(pixels, 9, 8, 2, 1, rgb('6ce5b3'))
    elif armored == 'torso':
        rect(pixels, 5, 6, 6, 4, rgb('434851'))
        rect(pixels, 6, 7, 4, 2, rgb('9be5b0'))
    else:
        rect(pixels, x+1, y+height//2, width-2, 2, rgb('59636b'))
    png(RES / f'assets/automatons/textures/item/armored_{armored}.png', pixels)
    write(RES / f'assets/automatons/models/item/armored_{armored}.json', {
        'parent': 'minecraft:item/generated', 'textures': {'layer0': f'automatons:item/armored_{armored}'}})

creation = json.loads((RES / 'data/automatons/recipes/automaton.json').read_text())
for ingredient in creation['components']:
    part = ingredient['item'].removeprefix('automatons:automaton_')
    ingredient['item'] = f'automatons:armored_{parts[part]}'
write(RES / 'data/automatons/recipes/guardington.json', creation)

write(RES / 'data/automatons/recipes/lumina_collector.json', {
    'type': 'automatons:collector', 'pattern': ['SCS', ' T ', 'SCS'],
    'key': {'S': {'item': 'minecraft:stone'}, 'C': {'item': 'automatons:infused_lumite_shard'},
            'T': {'item': 'automatons:tank'}},
    'result': {'item': 'automatons:lumina_collector', 'count': 1}})

lang_path = RES / 'assets/automatons/lang/en_us.json'
lang = json.loads(lang_path.read_text(encoding='utf-8'))
lang.update({'block.automatons.dead_plant': 'Dead Plant', 'block.automatons.dead_crop': 'Dead Crop',
             'entity.automatons.guardington': 'Guardington', 'block.automatons.gravestone': 'Gravestone'})
for part in parts.values():
    lang[f'item.automatons.armored_{part}'] = f'Armored {part.title()}'
write(lang_path, lang)
write(RES / 'assets/automatons/blockstates/gravestone.json', {'variants': {'': {'model': 'automatons:block/gravestone'}}})
write(RES / 'assets/automatons/models/block/gravestone.json', {
    'parent': 'minecraft:block/block', 'textures': {'stone': 'minecraft:block/stone', 'particle': 'minecraft:block/stone'},
    'elements': [{'from': [3, 0, 5], 'to': [13, 13, 11], 'faces': {
        side: {'texture': '#stone'} for side in ['north', 'south', 'east', 'west', 'up', 'down']}}]})
write(RES / 'assets/automatons/models/item/gravestone.json', {'parent': 'automatons:block/gravestone'})
print('Authored dead vegetation (no loot), armored components and collector recipe.')
