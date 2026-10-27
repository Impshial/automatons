"""Current recipes/models. Supplied texture bytes are never rewritten or re-encoded."""
from asset_pixels import ROOT, RES, write
import json, hashlib

def fuel_textures():
    for kind, expected in {
        'coal':'9919774fa5dba825e5592683b93298d576bf5af1438b96e16024704ed431a8db',
        'charcoal':'b129136e26ced6e0d7026dab57ee5622f21412b14f1985f0c5ff5a8a04bc7c99'
    }.items():
        data=(ROOT/f'reference/fuel-pieces-0.7.4/piece_of_{kind}.png').read_bytes()
        assert hashlib.sha256(data).hexdigest()==expected
        assert data[:8]==b'\x89PNG\r\n\x1a\n' and int.from_bytes(data[16:20],'big')==16 and int.from_bytes(data[20:24],'big')==16
        target=RES/f'assets/automatons/textures/item/{kind}_piece.png'
        if not target.exists() or target.read_bytes()!=data:target.write_bytes(data)
        write(RES/f'assets/automatons/models/item/{kind}_piece.json',
            {'parent':'minecraft:item/generated','textures':{'layer0':f'automatons:item/{kind}_piece'}})


def ore_models():
    masks = json.loads((ROOT/'reference/current-run/ore-emissive-pixels.json').read_text())
    for kind, name in [('stone', 'lumite_ore'), ('deepslate', 'deepslate_lumite_ore')]:
        texture = f'lumite_ore_{kind}_texture'
        png = RES/f'assets/automatons/textures/block/{texture}.png'
        mask = masks[texture]
        assert hashlib.sha256(png.read_bytes()).hexdigest() == mask['sha256'], 'Update the explicit emissive pixel map after a deliberate texture edit; do not overwrite the PNG.'
        faces = {side: {'texture': '#ore', 'cullface': side} for side in ['north','south','east','west','up','down']}
        elements = [{'from':[0,0,0], 'to':[16,16,16], 'faces': faces}]
        for u, v in mask['pixels']:
            quads = {
                'north': ([15-u,15-v,-.001],[16-u,16-v,-.001]),
                'south': ([u,15-v,16.001],[u+1,16-v,16.001]),
                'west': ([-.001,15-v,u],[-.001,16-v,u+1]),
                'east': ([16.001,15-v,15-u],[16.001,16-v,16-u]),
                'up': ([u,16.001,v],[u+1,16.001,v+1]),
                'down': ([u,-.001,15-v],[u+1,-.001,16-v]),
            }
            for side,(a,b) in quads.items():
                elements.append({'from':a,'to':b,'shade':False,
                    'forge_data':{'block_light':15,'sky_light':15,'ambient_occlusion':False},
                    'faces':{side:{'texture':'#ore','cullface':side,'uv':[u,v,u+1,v+1]}}})
        write(RES/f'assets/automatons/models/block/{name}.json', {'parent':'minecraft:block/block',
            'textures':{'ore':f'automatons:block/{texture}','particle':f'automatons:block/{texture}'}, 'elements':elements})


def recipe(name, ingredients, item, count, unlock):
    write(RES/f'data/automatons/recipes/{name}.json', {'type':'minecraft:crafting_shapeless',
        'ingredients':ingredients,'result':{'item':item,'count':count}})
    write(RES/f'data/automatons/advancements/recipes/{name}.json', {'parent':'minecraft:recipes/root',
        'criteria':{'has_materials':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':[unlock]}]}}},
        'rewards':{'recipes':[f'automatons:{name}']}})


def author():

    fuel_textures()
    ore_models()
    for kind in ['coal','charcoal']:
        piece=f'automatons:{kind}_piece';parent=f'minecraft:{kind}'
        recipe(f'{kind}_pieces',[{'item':parent}],piece,8,parent)
        recipe(f'{kind}_from_pieces',[{'item':piece} for _ in range(8)],parent,1,piece)
        write(RES/f'assets/automatons/models/item/{kind}_piece.json',
            {'parent':'minecraft:item/generated','textures':{'layer0':f'automatons:item/{kind}_piece'}})
    recipe('automaton_handbook',[{'item':'minecraft:book'},{'tag':'minecraft:planks'}],
        'automatons:automaton_handbook',1,'minecraft:book')
    write(RES/'assets/automatons/models/item/automaton_handbook.json',
        {'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/book'}})
    lang_path=RES/'assets/automatons/lang/en_us.json';lang=json.loads(lang_path.read_text(encoding='utf-8'))
    lang.update({'item.automatons.coal_piece':'Piece of Coal','item.automatons.charcoal_piece':'Piece of Charcoal',
        'item.automatons.automaton_handbook':'Automaton Handbook',
        'guide.automatons.assembly':'Creation Pedestal assembly',
        'jer.automatons.unenchanted':'1–3 raw shards without enchantments; Fortune increases yield',
        'jer.automatons.tool':'Requires a stone-tier or better pickaxe',
        'jer.automatons.estimate':'Default placement estimate before terrain filtering; server settings may differ',
        'jer.automatons.estimate_short':'Default estimate',
        'jer.automatons.settings':'Y=%s to %s; %s attempts/chunk; vein size %s',
        'jer.automatons.silk':'Silk Touch returns the matching ore block'})
    write(lang_path,lang)
    print('Authored fuel/book recipes and supplied-texture ore models without changing any PNG.')


if __name__=='__main__':author()
