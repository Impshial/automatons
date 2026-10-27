"""0.6 native pixel assets and final recipe graph. Reproducible; no filtering or external art."""
from asset_pixels import *
from import_shard_textures import import_textures
import math

FAMILIES=('storage','lumber','speed','efficiency','mining')
def infuser_model():
    elements=[cube([0,0,0],[16,3,16],'black'),cube([1,2,1],[15,4,15],'white'),
        cube([3,4,3],[13,11,13],'black'),cube([1,11,1],[15,14,15],'white')]
    for x,z in [(2,2),(10,2),(2,10),(10,10)]:
        socket=cube([x,14,z],[x+4,16,z+4],'black')
        del socket['faces']['up']
        elements.append(socket)
        # Tile the top into four rim strips and one inset. No coplanar overlap.
        for x0,z0,x1,z1 in [(x,z,x+4,z+.5),(x,z+3.5,x+4,z+4),
                (x,z+.5,x+.5,z+3.5),(x+3.5,z+.5,x+4,z+3.5)]:
            elements.append({'from':[x0,16,z0],'to':[x1,16,z1],
                'faces':{'up':{'uv':[x0,z0,x1,z1],'texture':'#black'}}})
        elements.append({'from':[x+.5,16,z+.5],'to':[x+3.5,16,z+3.5],
            'faces':{'up':{'uv':[x+.5,z+.5,x+3.5,z+3.5],'texture':'#white'}}})
    return {'parent':'minecraft:block/block',
        'textures':{'white':'automatons:block/white_stone','black':'automatons:block/black_stone',
            'particle':'automatons:block/black_stone'},'elements':elements}

def shaped(name, pattern, keys):
    write(RES/f'data/automatons/recipes/{name}.json', {'type':'minecraft:crafting_shaped','pattern':pattern,
        'key':{k:{'item':v} for k,v in keys.items()},'result':{'item':f'automatons:{name}','count':1}})
def hammer():
    if (RES/"assets/automatons/textures/item/hammer.png").exists():return
    # Handle axis NE/SW; compact steel head perpendicular to it. Each character is one source pixel.
    rows=[
    '................','........#.......','.......#HH#.....','......#HLMM#....',
    '.......#LMMM#...','........#LMMH#..','.......#WB#HH#..','......#WB#..#...',
    '.....#WB#.......','....#WB#........','...#WB#.........','..#WB#..........',
    '.#WB#...........','.#B#............','..#.............','................']
    colors={'.':(0,0,0,0),'#':rgb('303039'),'H':rgb('d4d6d7'),'L':rgb('a7a9ad'),
            'M':rgb('777b82'),'W':rgb('b57c43'),'B':rgb('774b29')}
    pixels=[[colors[c] for c in row] for row in rows]
    assert all(len(row)==16 for row in pixels)
    png(RES/'assets/automatons/textures/item/hammer.png',pixels)
    png(ART/'hammer-native.png',pixels)
    png(ART/'hammer-pixel-preview.png',[[pixel for pixel in row for _ in range(16)] for row in pixels for _ in range(16)])
    write(ART/'hammer-pixels.json',{'palette':{k:list(v) for k,v in colors.items()},'rows':rows})
def cube(a,b,tex,glow=False,cull=False):
    faces={}
    for face in ['down','up','north','south','west','east']:
        f={'texture':'#'+tex}
        if cull:f['cullface']=face
        # Use automatic block UVs: one texture pixel per block model unit.
        faces[face]=f
    e={'from':a,'to':b,'faces':faces}
    if glow:e.update({'shade':False,'forge_data':{'block_light':15,'sky_light':15,'ambient_occlusion':False}})
    return e
def author():
    import_textures()
    hammer()
    for name,stone in [('lumite_ore','stone'),('deepslate_lumite_ore','deepslate')]:
        write(RES/f'assets/automatons/blockstates/{name}.json',{'variants':{'':{'model':f'automatons:block/{name}'}}})
        write(RES/f'assets/automatons/models/item/{name}.json',{'parent':f'automatons:block/{name}'})
        silk={'condition':'minecraft:match_tool','predicate':{'enchantments':[{'enchantment':'minecraft:silk_touch','levels':{'min':1}}]}}
        harvest={'condition':'minecraft:any_of','terms':[{'condition':'minecraft:match_tool','predicate':{'tag':'forge:tools/pickaxes'}}]}
        # Correct-tool gating is supplied by the block and Forge tier tags during the normal harvest lifecycle.
        write(RES/f'data/automatons/loot_tables/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,
            'entries':[{'type':'minecraft:alternatives','children':[{'type':'minecraft:item','name':f'automatons:{name}','conditions':[silk]},
            {'type':'minecraft:item','name':'automatons:lumite_shard','functions':[
            {'function':'minecraft:set_count','count':{'type':'minecraft:uniform','min':1,'max':3}},
            {'function':'minecraft:apply_bonus','enchantment':'minecraft:fortune','formula':'minecraft:ore_drops'},
            {'function':'minecraft:explosion_decay'}]}]}]}]})
    for name,bright in [('lumite_shard',False),('infused_lumite_shard',True)]:
        pixels=canvas()
        for y in range(2,15):
            center=10-(y-2)*.42
            width=1 if y<4 or y>12 else 2
            for x in range(16):
                if abs(x-center)<=width:
                    pixels[y][x]=rgb(('f5eaff' if bright else 'c4a3e2') if x<center else ('b976ff' if bright else '775a9c'))
                elif abs(x-center)<width+1:pixels[y][x]=rgb('5c3d7c')
        if bright:
            for x,y in [(4,3),(3,4),(4,5),(5,4),(12,11),(13,12),(12,13),(11,12)]:pixels[y][x]=rgb('f9edff')
        if not (RES/f'assets/automatons/textures/item/{name}.png').exists():
            png(RES/f'assets/automatons/textures/item/{name}.png',pixels)
        write(RES/f'assets/automatons/models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'automatons:item/{name}'}})
    write(RES/'assets/automatons/models/block/infusing_pedestal.json',infuser_model())
    ores=['automatons:lumite_ore','automatons:deepslate_lumite_ore']
    for path,values in [('minecraft/tags/blocks/mineable/pickaxe',ores+['automatons:pedestal','automatons:infusing_pedestal']),
        ('minecraft/tags/blocks/needs_stone_tool',ores),('forge/tags/blocks/ores/lumite',ores),
        ('forge/tags/blocks/ores',['#forge:ores/lumite']),('forge/tags/items/ores/lumite',ores),
        ('forge/tags/items/ores',['#forge:ores/lumite']),('forge/tags/blocks/ore_rates/singular',ores),
        ('forge/tags/items/ore_rates/singular',ores)]:
        p=RES/f'data/{path}.json';old=json.loads(p.read_text(encoding='utf-8')) if p.exists() else {}
        write(p,{'replace':False,'values':list(dict.fromkeys(old.get('values',[])+values))})
    retired=['upgrade_plate']+[f'{f}_{tier}' for f in FAMILIES for tier in (1,2)]
    for source in [RES, ROOT/'src/generated/resources']:
        for name in retired:
            for directory in ['recipes','advancements/recipes']:
                (source/f'data/automatons/{directory}/{name}.json').unlink(missing_ok=True)
    shaped('empty_magica_core',[' G ','GSG',' G '],{'G':'minecraft:glass','S':'automatons:lumite_shard'})
    shaped('infusing_pedestal',['I I','DSD','BBB'],{'I':'minecraft:iron_ingot','D':'minecraft:polished_diorite','S':'minecraft:diamond','B':'minecraft:polished_deepslate'})
    shaped('magica_core',[' I ','IEI',' I '],{'I':'automatons:infused_lumite_shard','E':'automatons:empty_magica_core'})
    for p in (RES/'data/automatons/recipes').glob('*.json'):
        data=json.loads(p.read_text(encoding='utf-8'))
        serialized=json.dumps(data).replace('minecraft:amethyst_shard','automatons:infused_lumite_shard')
        data=json.loads(serialized)
        if p.stem=='empty_magica_core':data['key']['S']['type']='forge:nbt'
        if p.stem=='magica_core':data['key']['E']['type']='forge:nbt'
        write(p,data)
        if data['type'].startswith('minecraft:crafting_'):
            ingredient={'infusing_pedestal':'minecraft:diamond','empty_magica_core':'automatons:lumite_shard',
                'magica_core':'automatons:infused_lumite_shard','automaton_core':'automatons:magica_core',
                'pedestal':'automatons:magica_core','totem':'automatons:magica_core','wand':'automatons:magica_core'}.get(p.stem,'minecraft:iron_ingot')
            write(RES/f'data/automatons/advancements/recipes/{p.stem}.json',{'parent':'minecraft:recipes/root','criteria':{'has_materials':{
                'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':[ingredient]}]}}},'rewards':{'recipes':['automatons:'+p.stem]}})
    p=RES/'assets/automatons/lang/en_us.json'
    lang={k:v.replace('Magica','Lumina') for k,v in json.loads(p.read_text(encoding='utf-8')).items()}
    lang.update({'block.automatons.lumite_ore':'Lumite Ore','block.automatons.deepslate_lumite_ore':'Deepslate Lumite Ore',
        'item.automatons.lumite_shard':'Lumite Shard','item.automatons.infused_lumite_shard':'Infused Lumite Shard',
        'item.automatons.empty_magica_core':'Empty Core','item.automatons.magica_core':'Lumina Core',
        'jei.automatons.infusion':'Lumina Infusion','jei.automatons.infusion.cost':'%s Lumina per shard',
        'jei.automatons.infusion.help':'One shard per corner. Stay within %s blocks. Four corners share the transfer rate.'})
    for name in retired:
        label=lang.get('item.automatons.'+name,name)
        while label.startswith('Retired '):label=label[8:]
        lang['item.automatons.'+name]='Retired '+label
        lang.pop('tooltip.automatons.'+name,None)
    write(p,lang)
    from author_current_run import author as current_author
    current_author()
    print('Authored current data while preserving supplied/current item and ore PNGs.')
if __name__=='__main__':author()

