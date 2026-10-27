"""Editable pixel-grid assets for Automatons. No resampling or external dependencies.
Every model unit equals 1/16 block, every UV unit is one source texel.
"""
from pathlib import Path
import json, struct, zlib, uuid, base64, sys
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
ART=ROOT/'art'
def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
def chunk(kind,data):return struct.pack('!I',len(data))+kind+data+struct.pack('!I',zlib.crc32(kind+data)&0xffffffff)
def png(path,pixels):
    path.parent.mkdir(parents=True,exist_ok=True);h=len(pixels);w=len(pixels[0]);raw=b''.join(b'\x00'+b''.join(bytes(c) for c in row) for row in pixels)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
def rgb(s):return tuple(bytes.fromhex(s))+(255,)
wood=list(map(rgb,['6a4829','795431','906738','a77b44','b78b50','c2985b']))
metal=list(map(rgb,['555858','6b6e6b','8c8f88','b3b4a8']))
gold=list(map(rgb,['5c3717','b77615','efaa21','ffd554']))
purple=list(map(rgb,['5b3c75','8664aa','af8dcd','dbc0ec']))
def canvas(w=16,h=16):return [[(0,0,0,0) for _ in range(w)] for _ in range(h)]
def rect(p,x,y,w,h,c):
    for yy in range(y,y+h):
        for xx in range(x,x+w):
            if 0<=yy<len(p) and 0<=xx<len(p[0]):p[yy][xx]=c

def material(p,x,y,w,h,kind='plank'):
    for yy in range(h):
        for xx in range(w):
            grain=((xx//3)*7+(yy//2)*3)%3
            if kind=='iron': c=metal[3 if yy%8==0 else 1 if yy%8==7 else 2]
            elif kind=='end':c=wood[(min(xx,yy,w-xx-1,h-yy-1)%3)+2]
            elif kind=='bark':c=wood[1+(xx//2+yy//5)%3]
            else:c=wood[1 if yy%4==3 else 3+(xx//4+yy//4)%3]
            p[y+yy][x+xx]=c
from author_worker_rig import author
if '--rig-only' in sys.argv:
    author()
    sys.exit(0)
# Hand-maintained UI translations are authoritative and survive art regeneration.
existing_language=json.loads((RES/'assets/automatons/lang/en_us.json').read_text(encoding='utf-8'))
for name,kind in [('planks','plank'),('bark','bark'),('log_end','end'),('iron','iron')]:
    p=canvas();material(p,0,0,16,16,kind);png(RES/f'assets/automatons/textures/block/{name}.png',p)
stone=canvas()
for y in range(16):
    for x in range(16):stone[y][x]=metal[0 if y%8==7 or (x+(8 if y//8 else 0))%16==15 else 1+(x//4+y//3)%2]
png(RES/'assets/automatons/textures/block/stone.png',stone)
core=canvas()
for y in range(16):
    for x in range(16):core[y][x]=gold[0 if x in [0,15] or y in [0,15] else 2+(x//4+y//4)%2]
png(RES/'assets/automatons/textures/block/core.png',core)

def cube(fr,to,texture):
    x,y,z=fr;xx,yy,zz=to
    return {'from':fr,'to':to,'faces':{
        'north':{'texture':'#'+texture,'uv':[x,16-yy,xx,16-y]},'south':{'texture':'#'+texture,'uv':[x,16-yy,xx,16-y]},
        'east':{'texture':'#'+texture,'uv':[z,16-yy,zz,16-y]},'west':{'texture':'#'+texture,'uv':[z,16-yy,zz,16-y]},
        'up':{'texture':'#'+texture,'uv':[x,z,xx,zz]},'down':{'texture':'#'+texture,'uv':[x,z,xx,zz]}}}
textures={s:'automatons:block/'+s for s in ['planks','bark','log_end','iron','stone','core']};textures['particle']=textures['planks']
models={
'pedestal':[cube([1,0,1],[15,3,15],'stone'),cube([4,3,4],[12,10,12],'stone'),cube([0,10,0],[16,12,16],'iron'),cube([1,12,1],[15,13,15],'stone'),cube([6,12,6],[10,14,10],'core')],
# Stepped octagonal upright; textures map at one pixel per model unit.
'totem':[cube([3,0,3],[13,2,13],'stone')]+[cube([x0,y0,z0],[x1,y1,z1],'planks') for y0,y1 in [(2,3),(5,12)] for x0,z0,x1,z1 in [(5,4,11,12),(4,5,5,11),(11,5,12,11)]]+[cube([4,3,4],[12,5,12],'iron'),cube([4,12,4],[12,14,12],'iron'),cube([5,14,4],[11,16,12],'core'),cube([4,14,5],[5,16,11],'core'),cube([11,14,5],[12,16,11],'core')]}
for name,elements in models.items():
    write(RES/f'assets/automatons/models/block/{name}.json',{'parent':'minecraft:block/block','textures':textures,'elements':elements,'display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.75,.75,.75]}}})
    write(RES/f'assets/automatons/blockstates/{name}.json',{'variants':{'':{'model':f'automatons:block/{name}'}}})
    write(RES/f'assets/automatons/models/item/{name}.json',{'parent':f'automatons:block/{name}'})
    write(RES/f'data/automatons/loot_tables/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'automatons:{name}'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
wand=canvas()
for i in range(2,12):rect(wand,i,15-i,2,2,wood[1]);wand[15-i][i]=wood[4]
for x,y in [(10,2),(11,1),(12,0),(13,1),(14,2),(13,3),(12,4),(11,3)]:wand[y][x]=purple[(x+y)%4]
rect(wand,11,2,2,2,gold[3]);png(RES/'assets/automatons/textures/item/wand.png',wand)
write(RES/'assets/automatons/models/item/wand.json',{'parent':'minecraft:item/handheld','textures':{'layer0':'automatons:item/wand'}})
lang={'itemGroup.automatons':'Automatons','block.automatons.pedestal':'Automaton Pedestal','block.automatons.totem':'Automaton Totem','item.automatons.wand':'Automaton Wand','entity.automatons.lumberton':'Lumberton','entity.automatons.fallen_timber':'Fallen Timber','item.automatons.lumberton_test_egg':'Lumberton Test Egg (Development)'}
for family,title in [('storage','Totem Range Upgrade'),('lumber','Lumber Range'),('speed','Work Speed'),('efficiency','Tool Enchantment')]:
    for tier in [1,2]:
        name=f'{family}_{tier}';p=canvas();rect(p,2,1,12,14,metal[0]);rect(p,3,2,10,12,metal[3]);rect(p,4,3,8,10,wood[1]);c=gold[2] if tier==1 else purple[2]
        if family=='storage':rect(p,5,5,6,5,c);rect(p,5,7,6,1,wood[0]);p[7][8]=metal[3]
        if family=='lumber':rect(p,7,5,2,7,wood[4]);rect(p,5,4,6,4,c)
        if family=='speed':
            for k in range(4):p[5+k][5+k]=c;p[8+k][8-k]=c
        if family=='efficiency':
            rect(p,7,4,2,8,c);rect(p,5,7,6,2,c);p[5][5]=purple[3];p[11][10]=purple[3]
        for k in range(tier):p[13][6+k*3]=gold[3]
        png(RES/f'assets/automatons/textures/item/{name}.png',p);write(RES/f'assets/automatons/models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'automatons:item/{name}'}});lang[f'item.automatons.{name}']=f'{title} {"I" if tier==1 else "II"}'
write(RES/'assets/automatons/models/item/lumberton_test_egg.json',{'parent':'minecraft:item/template_spawn_egg'})
write(RES/'assets/automatons/lang/en_us.json',lang)

def shaped(name,pattern,keys):
    write(RES/f'data/automatons/recipes/{name}.json',{'type':'minecraft:crafting_shaped','pattern':pattern,'key':{k:({'tag':v[1:]} if v.startswith('#') else {'item':v}) for k,v in keys.items()},'result':{'item':'automatons:'+name}})
write(RES/'data/automatons/tags/items/axes.json',{'replace':False,'values':[f'minecraft:{s}_axe' for s in ['wooden','stone','iron','golden','diamond','netherite']]})
write(RES/'data/automatons/tags/blocks/tree_logs.json',{'replace':False,'values':[f'minecraft:{s}_log' for s in ['oak','spruce','birch','jungle','acacia','dark_oak','cherry','mangrove']]})
write(RES/'data/automatons/tags/blocks/storage_chests.json',{'replace':False,'values':['minecraft:chest','minecraft:trapped_chest']})
write(RES/'data/minecraft/tags/blocks/mineable/pickaxe.json',{'replace':False,'values':['automatons:pedestal']})
write(RES/'data/minecraft/tags/blocks/mineable/axe.json',{'replace':False,'values':['automatons:totem']})
write(RES/'pack.mcmeta',{'pack':{'pack_format':15,'description':'Automatons resources'}})
print('Authored 16x16 block/item textures, recipes, tags, loot, and language.')

# Redesign 0.2: reproducible pixel art and the complete survival dependency chain.
white=list(map(rgb,['46414f','77717f','b9b3c5','f3edff']))
black=list(map(rgb,['17171d','26252e','353440','4c4958']))
for name,palette in [('white_stone',white),('black_stone',black)]:
    p=canvas()
    for y in range(16):
        for x in range(16):p[y][x]=palette[1+(x//3+y//4)%3]
    png(RES/f'assets/automatons/textures/block/{name}.png',p)
def sprite(name,p,title):
    png(RES/f'assets/automatons/textures/item/{name}.png',p)
    write(RES/f'assets/automatons/models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'automatons:item/{name}'}})
    lang[f'item.automatons.{name}']=title
for name,full in [('empty_magica_core',False),('magica_core',True)]:
    p=canvas()
    for y in range(2,14):
        for x in range(2,14):
            d=abs(x-7.5)+abs(y-7.5)
            if d<=6:p[y][x]=white[2] if d>=5 else purple[0] if full else (90,70,120,75)
            if full and d<=4:p[y][x]=purple[2 if (x+y)%3 else 1]
            if full and d<=2:p[y][x]=white[3]
    p[4][7]=white[3];p[5][6]=white[3];p[10][10]=purple[3]
    sprite(name,p,'Lumina Core' if full else 'Empty Core')
p=canvas();material(p,4,3,8,9,'plank');rect(p,3,2,10,1,metal[1]);rect(p,3,12,10,2,metal[2]);rect(p,6,6,1,2,gold[3]);rect(p,9,6,1,2,gold[3]);rect(p,7,10,2,1,wood[0]);sprite('automaton_head',p,'Automaton Head')
p=canvas();material(p,3,3,10,10,'plank');rect(p,2,3,2,11,metal[2]);rect(p,12,3,2,11,metal[2]);rect(p,5,6,6,4,purple[0]);rect(p,6,6,1,4,white[3]);rect(p,9,6,1,4,white[3]);sprite('automaton_core',p,'Automaton Torso')
for name,title,length in [('automaton_arm','Automaton Arm',12),('automaton_leg','Automaton Leg',10)]:
    p=canvas();material(p,5,2,6,length,'bark');rect(p,5,2,6,2,metal[2]);rect(p,5,length,6,2,metal[1]);rect(p,6,4,1,length-5,wood[5]);
    if name.endswith('leg'):rect(p,4,12,8,2,metal[2])
    sprite(name,p,title)
p=canvas();rect(p,2,3,12,10,metal[0]);rect(p,3,4,10,8,metal[2]);rect(p,4,5,8,6,metal[3]);rect(p,6,6,4,4,purple[1]);p[6][6]=white[3];p[11][3]=gold[2];p[4][12]=gold[2];sprite('upgrade_plate',p,'Upgrade Plate')
p=canvas();material(p,5,0,6,5,'plank');rect(p,6,2,1,1,gold[3]);rect(p,9,2,1,1,gold[3]);rect(p,7,4,2,1,wood[0]);material(p,5,6,6,5,'plank');rect(p,7,7,2,2,gold[2]);material(p,2,6,2,7,'bark');material(p,12,6,2,7,'bark');rect(p,2,6,2,1,metal[2]);rect(p,12,6,2,1,metal[2]);rect(p,2,11,2,1,metal[2]);rect(p,12,11,2,1,metal[2]);material(p,5,12,2,4,'bark');material(p,9,12,2,4,'bark');rect(p,5,15,2,1,metal[2]);rect(p,9,15,2,1,metal[2]);sprite('automaton',p,'Automaton')
# Same item silhouette/UVs, a luminous monochrome material for its permanent display.
hologram=[[(int(145+(r+g+b)/3*.42),int(110+(r+g+b)/3*.54),int(205+(r+g+b)/3*.19),a) for r,g,b,a in row] for row in p]
png(RES/'assets/automatons/textures/item/automaton_display.png',hologram)
# Black/white, half-block infuser with four short upward claws.
itex={k:'automatons:block/'+k for k in ['white_stone','black_stone','iron']};itex['particle']=itex['black_stone']
elements=[cube([2,0,2],[14,2,14],'black_stone'),cube([3,2,3],[13,3,13],'white_stone'),cube([5,3,5],[11,6,11],'black_stone')]
for x,z in [(3,3),(11,3),(3,11),(11,11)]:elements += [cube([x,5,z],[x+2,7,z+2],'white_stone'),cube([x,7,z],[x+2,8,z+2],'iron')]
write(RES/'assets/automatons/models/block/infusing_pedestal.json',{'parent':'minecraft:block/block','textures':itex,'elements':elements,'display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.85,.85,.85]}}})
write(RES/'assets/automatons/models/item/infusing_pedestal.json',{'parent':'automatons:block/infusing_pedestal'})
write(RES/'assets/automatons/blockstates/infusing_pedestal.json',{'variants':{'':{'model':'automatons:block/infusing_pedestal'}}})
write(RES/'data/automatons/loot_tables/blocks/infusing_pedestal.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'automatons:infusing_pedestal'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
lang['block.automatons.infusing_pedestal']='Infusing Pedestal';lang['entity.automatons.lumberton']='Automaton';lang['entity.automatons.magica_orb']='Lumina';lang['item.automatons.lumberton_test_egg']='Automaton Test Egg (Development)'
write(RES/'assets/automatons/lang/en_us.json',lang)

# Final rig pass replaces historical packed/head sprites and the packed model.
lang.update(existing_language)
write(RES/'assets/automatons/lang/en_us.json',lang)
author()
woods=['oak','spruce','birch','jungle','acacia','dark_oak','mangrove','cherry']
write(RES/'data/automatons/tags/items/overworld_logs.json',{'replace':False,'values':[f'minecraft:{prefix}{wood}_{form}' for wood in woods for prefix in ['', 'stripped_'] for form in ['log','wood']]})
write(RES/'data/automatons/tags/items/overworld_planks.json',{'replace':False,'values':[f'minecraft:{w}_planks' for w in woods]})
MC='minecraft:';MOD='automatons:'
shaped('empty_magica_core',[' G ','GAG',' G '],{'G':MC+'glass','A':MOD+'lumite_shard'})
shaped('infusing_pedestal',['I I','DAD','BBB'],{'I':MC+'iron_ingot','D':MC+'polished_diorite','A':MC+'diamond','B':MC+'polished_deepslate'})
shaped('pedestal',['SCS','IGI','SCS'],{'S':MC+'stone_bricks','C':MOD+'magica_core','I':MC+'iron_ingot','G':MC+'gold_ingot'})
shaped('totem',[' C ','ILI',' S '],{'C':MOD+'magica_core','I':MC+'iron_ingot','L':'#automatons:overworld_logs','S':MC+'stone_bricks'})
shaped('wand',['  C',' S ','S  '],{'C':MOD+'magica_core','S':MC+'stick'})
shaped('automaton_core',['LIL','CRC','LIL'],{'L':'#automatons:overworld_logs','I':MC+'iron_ingot','C':MOD+'magica_core','R':MC+'redstone'})
def shapeless(name,ingredients):
    assert 1<=len(ingredients)<=9,(name,len(ingredients))
    write(RES/f'data/automatons/recipes/{name}.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'tag':v[1:]} if v.startswith('#') else {'item':v} for v in ingredients],'result':{'item':MOD+name}})
shaped('automaton_head',['LPL','NRN','LIL'],{'L':'#automatons:overworld_logs','P':'#automatons:overworld_planks','N':MC+'iron_nugget','R':MC+'redstone','I':MC+'iron_ingot'})
shaped('automaton_arm',['L','I'],{'L':'#automatons:overworld_logs','I':MC+'iron_ingot'})
shaped('automaton_leg',['L','P','I'],{'L':'#automatons:overworld_logs','P':'#automatons:overworld_planks','I':MC+'iron_ingot'})
# Retire only the known old four-slot recipe generated above.
(RES/'data/automatons/recipes/lumberton.json').unlink(missing_ok=True)
write(RES/'data/automatons/recipes/automaton.json',{'type':'automatons:creation','components':[{'item':MOD+n} for n in ['automaton_head','automaton_arm','automaton_core','automaton_arm','automaton_leg','automaton_leg']]})
for path in (RES/'data/automatons/recipes').glob('*.json'):
    data=json.loads(path.read_text(encoding='utf-8'))
    if data['type'].startswith('minecraft:crafting_'):
        write(RES/f'data/automatons/advancements/recipes/{path.stem}.json',{'parent':'minecraft:recipes/root','criteria':{'has_materials':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['automatons:lumite_shard']}]}}},'rewards':{'recipes':[MOD+path.stem]}})
reward_tags={'logs':[MC+w+'_log' for w in woods], 'crops':[MC+x for x in ['wheat','carrots','potatoes','beetroots','nether_wart']], 'fruit':[MC+'pumpkin',MC+'melon'],'berries':[MC+'sweet_berry_bush'],'flowers':['#minecraft:flowers'],'stems':[MC+x for x in ['sugar_cane','cactus','bamboo']], 'leaves':[MC+w+'_leaves' for w in woods], 'grass':[MC+x for x in ['grass','tall_grass','fern','large_fern']]}
for category,values in reward_tags.items():write(RES/f'data/automatons/tags/blocks/magica/{category}.json',{'replace':False,'values':values})
write(RES/'data/minecraft/tags/blocks/mineable/pickaxe.json',{'replace':False,'values':['automatons:pedestal','automatons:infusing_pedestal']})
print('Authored redesign items, infuser, complete recipes, recipe-book unlocks and Lumina source tags.')

# 0.2.2: quiet symmetrical pedestal stone, dedicated to the home pedestal.
p=canvas()
for y in range(16):
    for x in range(16):
        edge=min(x,y,15-x,15-y)
        p[y][x]=rgb(['34363f','a9a7b3','737280','62616f'][min(edge,3)])
# The inset follows the square frame; no brick/checker noise.
png(RES/'assets/automatons/textures/block/pedestal_stone.png',p)
model=json.loads((RES/'assets/automatons/models/block/pedestal.json').read_text(encoding='utf-8'))
model['textures']['stone']='automatons:block/pedestal_stone'
model['textures']['particle']='automatons:block/pedestal_stone'
write(RES/'assets/automatons/models/block/pedestal.json',model)
# Slender diagonal handle, silver collar and a white-hot violet diamond core.
p=canvas()
for i in range(1,10):
    rect(p,i,14-i,2,2,rgb('33283c'));p[14-i][i]=rgb('88678f')
for x,y in [(8,6),(9,7),(9,5),(10,6)]:p[y][x]=rgb('b6b2c5')
for y in range(0,8):
    for x in range(8,16):
        d=abs(x-11.5)+abs(y-3.5)
        if d<=4:p[y][x]=rgb('4b335f')
        if d<=3:p[y][x]=rgb('aa70e9')
        if d<=2:p[y][x]=rgb('dbb9ff')
        if d<=1:p[y][x]=rgb('ffffff')
p[1][10]=rgb('f4eaff');p[2][9]=rgb('f4eaff')
png(RES/'assets/automatons/textures/item/wand.png',p)
# Keep the optional integration strings reproducible when regenerating art.
lang.update({'jei.automatons.assembly':'Automaton Assembly','jei.automatons.infusion':'Lumina Infusion','jei.automatons.recipes':'Recipes','jei.automatons.assembly.help':'Take the result at your pedestal. No extra Lumina cost.','jei.automatons.infusion.cost':'%s Lumina per shard','jei.automatons.infusion.help':'One shard per corner. Stay within %s blocks. Four corners share the transfer rate.'})
write(RES/'assets/automatons/lang/en_us.json',lang)

# Current milestone owns final infusion models, vocabulary, and the surviving recipe graph.
from author_lumina import author as author_lumina
author_lumina()
