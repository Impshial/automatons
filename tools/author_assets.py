"""Editable pixel-grid assets for Automatons. No resampling or external dependencies.
Every model unit equals 1/16 block, every UV unit is one source texel.
"""
from pathlib import Path
import json, struct, zlib, uuid, base64
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
# Vanilla cuboid box UVs, no scale transform.
parts=[
 {'name':'head','origin':[0,6,0],'from':[-3,-6,-3],'size':[6,6,6],'uv':[0,0],'material':'plank'},
 {'name':'body','origin':[0,7,0],'from':[-4,0,-3],'size':[8,9,6],'uv':[0,14],'material':'plank'},
 {'name':'right_arm','origin':[-5,7,0],'from':[-3,0,-2],'size':[3,13,4],'uv':[32,0],'material':'bark'},
 {'name':'left_arm','origin':[5,7,0],'from':[0,0,-2],'size':[3,13,4],'uv':[48,0],'material':'bark'},
 {'name':'right_leg','origin':[-2,16,0],'from':[-2,0,-2],'size':[3,8,4],'uv':[32,20],'material':'bark'},
 {'name':'left_leg','origin':[2,16,0],'from':[-1,0,-2],'size':[3,8,4],'uv':[48,20],'material':'bark'},
 {'name':'neck','origin':[0,0,0],'from':[-2,6,-2],'size':[4,1,4],'uv':[0,34],'material':'iron'},
 {'name':'shoulder_r','origin':[0,0,0],'from':[-5,7,-2],'size':[1,3,4],'uv':[0,42],'material':'iron'},
 {'name':'shoulder_l','origin':[0,0,0],'from':[4,7,-2],'size':[1,3,4],'uv':[12,42],'material':'iron'}]
write(ART/'lumberton-model.json',{'texture_size':[64,64],'height_blocks':1.5,'units_per_block':16,'parts':parts})
atlas=canvas(64,64)
for part in parts:
    u,v=part['uv'];w,h,d=part['size'];kind=part['material']
    material(atlas,u+d,v,w,d,'end' if kind=='bark' else kind);material(atlas,u+d+w,v,w,d,kind)
    material(atlas,u,v+d,d,h,kind);material(atlas,u+d,v+d,w,h,kind);material(atlas,u+d+w,v+d,d,h,kind);material(atlas,u+2*d+w,v+d,w,h,kind)
    if 'arm' in part['name']:
        for sy in [0,9,10]:rect(atlas,u,v+d+sy,2*(w+d),1,metal[2 if sy!=10 else 1])
    if 'leg' in part['name']:rect(atlas,u,v+d,2*(w+d),1,metal[2]);rect(atlas,u,v+d+7,2*(w+d),1,metal[1])
# Amber chest rune on unbroken horizontal plank rows.
for x,y in [(11,22),(10,23),(11,23),(12,23),(11,24),(11,25)]:atlas[y][x]=gold[2 if y%2 else 3]
expressions=['idle','smile','brow','focused','frown','anger']
for face in expressions:
    a=[row[:] for row in atlas]
    # Six-by-six frontal face, eyes separated by two pixels. UV front = [6,6..12,12].
    for xx in [7,10]:rect(a,xx,8,1,2,gold[2]);a[8][xx]=gold[3]
    rect(a,8,10,2,1,wood[0])
    if face=='smile':a[10][7]=wood[0];a[11][8]=wood[0];a[11][9]=wood[0];a[10][10]=wood[0];a[10][8]=wood[4];a[10][9]=wood[4]
    if face=='brow':a[7][7]=wood[0];a[6][10]=wood[0]
    if face=='focused':a[7][7]=wood[0];a[7][10]=wood[0];a[9][7]=wood[1];a[9][10]=wood[1]
    if face=='frown':a[11][7]=wood[0];a[11][10]=wood[0];a[7][8]=wood[0];a[7][9]=wood[0]
    if face=='anger':a[7][7]=wood[0];a[8][8]=wood[0];a[8][9]=wood[0];a[7][10]=wood[0];rect(a,8,11,2,1,wood[0])
    png(RES/f'assets/automatons/textures/entity/lumberton_{face}.png',a)
    png(ART/f'lumberton-{face}-atlas-preview.png',[[c for c in row for _ in range(6)] for row in a for _ in range(6)])
# Editable Blockbench model, bedrock-style local texture box UVs.
elements=[];groups=[]
for p in parts:
    uid=str(uuid.uuid5(uuid.NAMESPACE_DNS,'automatons.'+p['name']));origin=p['origin'];fr=[p['from'][i]+origin[i] for i in range(3)];to=[fr[i]+p['size'][i] for i in range(3)]
    # Blockbench Y points up; transform the Minecraft rig's downwards Y around the feet.
    bbfrom=[fr[0],24-to[1],fr[2]];bbto=[to[0],24-fr[1],to[2]]
    elements.append({'name':p['name'],'type':'cube','uuid':uid,'from':bbfrom,'to':bbto,'origin':[origin[0],24-origin[1],origin[2]],'box_uv':True,'uv_offset':p['uv'],'faces':{f:{'texture':0,'uv':[0,0,0,0]} for f in ['north','south','east','west','up','down']}})
    groups.append({'name':p['name'],'origin':[origin[0],24-origin[1],origin[2]],'uuid':str(uuid.uuid5(uuid.NAMESPACE_DNS,'group.'+p['name'])),'children':[uid]})
tex=RES/'assets/automatons/textures/entity/lumberton_idle.png'
write(ART/'lumberton.bbmodel',{'meta':{'format_version':'4.10','model_format':'modded_entity','box_uv':True},'name':'Lumberton','model_identifier':'lumberton','resolution':{'width':64,'height':64},'elements':elements,'outliner':groups,'textures':[{'name':'lumberton_idle.png','id':'0','width':64,'height':64,'source':'data:image/png;base64,'+base64.b64encode(tex.read_bytes()).decode()}]})
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
for family,title in [('storage','Storage Range'),('lumber','Lumber Range'),('speed','Work Speed'),('efficiency','Tool Enchantment')]:
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
shaped('pedestal',['SAS','IGI','SSS'],{'S':'minecraft:stone_bricks','A':'minecraft:amethyst_shard','I':'minecraft:iron_ingot','G':'minecraft:gold_ingot'})
shaped('totem',[' A ','ILI',' S '],{'A':'minecraft:amethyst_shard','I':'minecraft:iron_ingot','L':'minecraft:oak_log','S':'minecraft:stone_bricks'})
shaped('wand',['  A',' S ','S  '],{'A':'minecraft:amethyst_shard','S':'minecraft:stick'})
for family,ingredient in [('storage','minecraft:chest'),('lumber','minecraft:oak_sapling'),('speed','minecraft:redstone'),('efficiency','minecraft:lapis_lazuli')]:
    shaped(f'{family}_1',[' I ','ACA',' I '],{'I':'minecraft:iron_ingot','A':'minecraft:amethyst_shard','C':ingredient})
    shaped(f'{family}_2',[' G ','AUA',' G '],{'G':'minecraft:gold_ingot','A':'minecraft:amethyst_shard','U':f'automatons:{family}_1'})
write(RES/'data/automatons/recipes/lumberton.json',{'type':'automatons:creation','wood':{'tag':'minecraft:oak_logs'},'wood_count':6,'metal':{'item':'minecraft:iron_ingot'},'metal_count':4,'core':{'item':'minecraft:amethyst_shard'},'tool':{'item':'minecraft:iron_axe'}})
write(RES/'data/automatons/tags/items/axes.json',{'replace':False,'values':[f'minecraft:{s}_axe' for s in ['wooden','stone','iron','golden','diamond','netherite']]})
write(RES/'data/automatons/tags/blocks/tree_logs.json',{'replace':False,'values':[f'minecraft:{s}_log' for s in ['oak','spruce','birch','jungle','acacia','dark_oak','cherry','mangrove']]})
write(RES/'data/automatons/tags/blocks/storage_chests.json',{'replace':False,'values':['minecraft:chest','minecraft:trapped_chest']})
write(RES/'data/minecraft/tags/blocks/mineable/pickaxe.json',{'replace':False,'values':['automatons:pedestal']})
write(RES/'data/minecraft/tags/blocks/mineable/axe.json',{'replace':False,'values':['automatons:totem']})
write(RES/'pack.mcmeta',{'pack':{'pack_format':15,'description':'Automatons resources'}})
print('Authored 16x16 block/item textures, six 64x64 entity atlases, editable model, recipes, tags, loot, and language.')
