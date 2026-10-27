#!/usr/bin/env python3
"""Rebuild the approved Automatons structures. Exports need only Python 3.
Optional previews need Pillow. Coordinates here use walking height y=0.
All unspecified cells are PRESERVE, explicit air is CLEAR, other cells PLACE.
"""
from __future__ import annotations
import argparse, collections, csv, gzip, hashlib, json, math, struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DV = 3465
AIR = 'minecraft:air'
VOID = 'minecraft:structure_void'
MOD = dict(totem='automatons:totem', tank='automatons:tank',
           collector='automatons:lumina_collector', infuser='automatons:infusing_pedestal')
DIR = {'north':(0,-1),'east':(1,0),'south':(0,1),'west':(-1,0)}
CW = dict(north='east',east='south',south='west',west='north')

def state(name, **props):
    if ':' not in name: name='minecraft:'+name
    return name+('['+','.join(k+'='+str(v).lower() for k,v in sorted(props.items()))+']' if props else '')

def split_state(s):
    name, _, tail=s.partition('[')
    return name, dict(p.split('=') for p in tail.rstrip(']').split(',')) if tail else {}

# NBT tags are represented as (type, value). No Minecraft libraries required.
def B(v): return (1,v)
def S(v): return (2,v)
def I(v): return (3,v)
def F(v): return (5,v)
def D(v): return (6,v)
def T(v): return (8,v)
def L(t,v): return (9,(t,v))
def C(v): return (10,v)
def IA(v): return (11,v)
def text_bytes(v):
    b=v.encode('utf-8'); return struct.pack('>H',len(b))+b
def payload(t,v):
    if t in (1,2,3,4,5,6): return struct.pack('>'+{1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t],v)
    if t==7: return struct.pack('>i',len(v))+bytes(v)
    if t==8: return text_bytes(v)
    if t==9:
        subtype, values=v
        return bytes([subtype])+struct.pack('>i',len(values))+b''.join(payload(subtype,x) for x in values)
    if t==10: return b''.join(bytes([tag[0]])+text_bytes(k)+payload(*tag) for k,tag in v.items())+b'\0'
    if t in (11,12): return struct.pack('>i',len(v))+b''.join(struct.pack('>i' if t==11 else '>q',x) for x in v)
    raise ValueError(t)
def save_nbt(path, name, data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(gzip.compress(bytes([10])+text_bytes(name)+payload(10,data),mtime=0))
def varint(n):
    out=bytearray()
    while n>=128: out.append((n&127)|128); n>>=7
    out.append(n); return out
def plain(tag):
    t,v=tag
    if t==10:return {k:plain(x) for k,x in v.items()}
    if t==9:return [plain((v[0],x)) for x in v[1]]
    return v

class Plan:
    def __init__(self,name,title,bounds,anchor,kind='worksite'):
        self.name=name; self.title=title; self.bounds=bounds; self.anchor=anchor; self.kind=kind
        self.cells={}; self.roles={}; self.entities=[]; self.meta={}; self.features={}
    def put(self,x,y,z,s,role='structure'):
        p=(x,y,z); self.cells[p]=s; self.roles[p]=role
    def fill(self,x1,x2,y1,y2,z1,z2,s,role='structure'):
        for y in range(y1,y2+1):
            for z in range(z1,z2+1):
                for x in range(x1,x2+1):self.put(x,y,z,s,role)
    def clear(self,x1,x2,y1,y2,z1,z2):self.fill(x1,x2,y1,y2,z1,z2,AIR,'clear')
    def point(self,name,p):self.features[name]=list(p)
    def door(self,x,y,z,facing='south'):
        for h,dy in [('lower',0),('upper',1)]:
            self.put(x,y+dy,z,state('spruce_door',facing=facing,half=h,hinge='left',open=False,powered=False))
        self.point('door',(x,y,z))
    def doubles(self,a,b,facing,tiers=1,label='storage'):
        assert a[1]==b[1]
        dx,dz=DIR[CW[facing]]
        assert (b[0]-a[0],b[2]-a[2]) in [(dx,dz),(-dx,-dz)]
        left,right=(a,b) if (b[0]-a[0],b[2]-a[2])==(dx,dz) else (b,a)
        entries=[]
        for dy in range(tiers):
            for p,t in [(left,'left'),(right,'right')]:
                q=(p[0],p[1]+dy,p[2]);self.put(*q,state('chest',facing=facing,type=t,waterlogged=False));entries.append(list(q))
        self.meta.setdefault('storage_groups',[]).append({'name':label,'blocks':entries,'double_chests':tiers,'empty':True})
    def lantern(self,x,y,z):self.put(x,y,z,state('lantern',hanging=True,waterlogged=False))
    def entity(self,id,pos,nbt,cost,blockpos=None):
        nbt=dict(nbt)
        nbt.setdefault('Rotation',L(5,[-90.,0.] if id=='minecraft:item_frame' else [0.,0.]))
        self.entities.append(dict(id=id,pos=list(pos),nbt=nbt,cost=cost,blockpos=list(blockpos or [math.floor(v) for v in pos])))

def roof(p,x1,x2,z1,z2,y,style='flat',wood='spruce',ridge='z'):
    if style=='flat':p.fill(x1,x2,y,y,z1,z2,state(wood+'_planks'));return
    # Gable ridge runs along z, or x for the ranch. Sloping stair rows, slab ridge.
    for z in range(z1,z2+1):
        for x in range(x1,x2+1):
            d=min(x-x1,x2-x) if ridge=='z' else min(z-z1,z2-z)
            center=(x1+x2)//2 if ridge=='z' else (z1+z2)//2
            along=x if ridge=='z' else z
            face=('east' if x<center else 'west') if ridge=='z' else ('south' if z<center else 'north')
            s=state(wood+'_slab',type='bottom',waterlogged=False) if along==center else state(wood+'_stairs',facing=face,half='bottom',shape='straight',waterlogged=False)
            p.put(x,y+d,z,s)
            if (ridge=='z' and z in (z1,z2)) or (ridge=='x' and x in (x1,x2)):
                for gy in range(y,y+d):p.put(x,gy,z,state(wood+'_planks'))

def shed(p,ox=0,oz=0,peaked=False,totem=(3,0),roofwood='spruce'):
    p.fill(ox,ox+6,-1,-1,oz,oz+6,state('cobblestone'))
    p.clear(ox,ox+6,0,2,oz,oz+6)
    for y in range(3):
        for z in range(7):
            for x in range(7):
                if x in (0,6) or z in (0,6):
                    s=state('oak_log',axis='y') if x in (0,6) and z in (0,6) else state('oak_planks')
                    p.put(ox+x,y,oz+z,s)
    for x in (0,6):p.put(ox+x,1,oz+3,state('glass'))
    p.door(ox+3,0,oz+6)
    p.put(ox+totem[0],0,oz+totem[1],MOD['totem'],'anchor')
    p.point('totem',(ox+totem[0],0,oz+totem[1]))
    roof(p,ox,ox+6,oz,oz+6,3,'gable' if peaked else 'flat',roofwood)
    if peaked:p.put(ox+3,3,oz+5,state('spruce_log',axis='x'))
    p.lantern(ox+3,2,oz+5)

def crafter():
    p=Plan('crafterton_workshop','Crafterton workshop',((0,-1,0),(6,6,6)),(3,0,1))
    shed(p,peaked=True,totem=(3,1))
    p.put(1,0,1,state('crafting_table'));p.point('crafting_table',(1,0,1))
    for z,block in [(2,'furnace'),(3,'blast_furnace')]:
        for y in (0,1):p.put(1,y,z,state(block,facing='east',lit=False))
    p.doubles((5,0,2),(5,0,3),'west',2,'workshop storage')
    p.entity('minecraft:armor_stand',(1.5,0,5.5),{'Rotation':L(5,[0.,0.]),'ArmorItems':L(10,[{}, {}, {}, {}]),'HandItems':L(10,[{},{}])},{'minecraft:armor_stand':1})
    p.point('armor_stand',(1,0,5));return p

def hunter():
    p=Plan('hunterton_shed','Hunterton shed',((0,-1,0),(6,3,6)),(3,0,0));shed(p)
    p.doubles((1,0,1),(1,0,2),'east',2,'hunting storage');return p

def ranch():
    p=Plan('rancherton_ranch','Rancherton shed and pasture',((0,-1,0),(22,7,16)),(1,0,8))
    p.fill(6,22,-1,-1,0,16,state('grass_block',snowy=False),'terrain')
    p.fill(0,6,-1,-1,4,12,state('oak_planks'))
    p.clear(0,6,0,2,4,12);p.clear(7,21,0,2,1,15)
    for y in range(3):
        for z in range(4,13):p.put(0,y,z,state('oak_planks'))
        for x in range(7):
            for z in (4,12):p.put(x,y,z,state('oak_planks'))
        for x in (0,6):
            for z in (4,12):p.put(x,y,z,state('oak_log',axis='y'))
    for x in range(6,23):
        for z in (0,16):p.put(x,0,z,state('oak_fence'))
    for z in range(17):
        for x in (6,22):
            if (x,0,z) not in p.cells or p.cells[(x,0,z)]==AIR:p.put(x,0,z,state('oak_fence'))
    p.put(6,0,8,state('oak_fence_gate',facing='east',in_wall=False,open=False,powered=False));p.point('pen_gate',(6,0,8))
    p.door(3,0,12)
    p.put(1,0,8,MOD['totem'],'anchor');p.point('totem',(1,0,8))
    p.doubles((1,0,5),(2,0,5),'south',label='ranch supplies')
    p.doubles((1,0,11),(2,0,11),'north',label='ranch products')
    for x in (4,5):p.put(x,0,5,state('hay_block',axis='y'))
    p.put(4,0,11,MOD['tank']);p.point('milk_tank',(4,0,11))
    p.entity('minecraft:item_frame',(1.03125,1.5,7.5),{'Facing':B(5),'TileX':I(1),'TileY':I(1),'TileZ':I(7),'Item':C({'id':T('minecraft:shears'),'Count':B(1)}),'ItemRotation':B(0)}, {'minecraft:item_frame':1,'minecraft:shears':1},(1,1,7))
    roof(p,0,6,4,12,3,'gable','spruce','x')
    p.put(3,3,8,state('spruce_log',axis='z'));p.lantern(3,2,8)
    # Connection properties are finalized after all fences, posts, and gate exist.
    for q,s in list(p.cells.items()):
        if s!='minecraft:oak_fence':continue
        props={}
        for face,(dx,dz) in DIR.items():
            n=p.cells.get((q[0]+dx,q[1],q[2]+dz),VOID)
            props[face]=any(k in n for k in ('_fence','_planks','_log'))
        p.put(*q,state('oak_fence',waterlogged=False,**props))
    p.meta['pen']={'outer_xz':[[6,0],[22,16]],'interior_xz':[[7,1],[21,15]],'range_is_enclosed_pen':True}
    return p

def quarry():
    p=Plan('quarryton_surface','Quarryton surface store and shaft',((0,-4,0),(14,3,24)),(7,0,0))
    shed(p,4,0)
    for x,face in [(5,'east'),(9,'west')]:
        for z in (1,4):p.doubles((x,0,z),(x,0,z+1),face,2,'quarry storage')
    for z in (7,8):
        p.put(7,-1,z,state('cobblestone'));p.clear(6,8,0,2,z,z)
    for y in range(-4,0):
        p.put(7,y,9,state('ladder',facing='south',waterlogged=False))
        if y<0:p.put(7,y,8,state('cobblestone'))
    p.clear(7,7,0,2,9,9);p.point('shaft_mouth',(7,0,9))
    p.meta.update(quarry={'footprint_xz':[[0,10],[14,24]],'width':15,'length':15,'do_not_preexcavate':True,'bottom':'runtime bedrock or selected Y'},shaft={'xz':[7,9],'backing_xz':[7,8],'outside_excavation':True,'starter_rungs':4,'extend_at_runtime':True})
    return p

def tower():
    p=Plan('luminariton_tower_courtyard','Luminariton tower and Collector courtyard',((0,-1,-1),(24,12,31)),(12,0,0))
    ox=9
    p.fill(9,15,-1,-1,0,6,state('stone_bricks'))
    p.clear(9,15,0,2,0,6)
    curve=[(2,0),(3,0),(4,0),(1,1),(2,1),(4,1),(5,1),(0,2),(1,2),(5,2),(6,2),(0,3),(6,3)]
    for x,z in curve:
        for y in range(3):p.put(ox+x,y,z,state('stone_bricks'))
    p.put(12,0,0,MOD['totem'],'anchor');p.point('totem',(12,0,0))
    p.fill(9,15,3,3,0,6,state('spruce_planks'));p.clear(9,15,4,7,0,6)
    for y in range(4,8):
        for z in range(7):
            for x in range(7):
                if x in (0,6) or z in (0,6):p.put(ox+x,y,z,state('dark_oak_log',axis='y') if x in (0,6) and z in (0,6) else state('spruce_planks'))
    for x,z in [(9,3),(15,3),(12,6)]:
        for y in (5,6):p.put(x,y,z,state('glass'))
    # Open D2 hatch, ladder facing east with solid C2 backing through upper dismount.
    for y in range(5):
        p.put(12,y,1,state('ladder',facing='east',waterlogged=False))
        p.put(11,y,1,state('stone_bricks') if y<3 else state('dark_oak_log',axis='y'))
    p.point('upper_hatch',(12,3,1));p.point('upper_dismount',(13,4,1))
    for z in (1,2):p.put(10,4,z,MOD['tank'])
    p.put(10,4,4,MOD['infuser']);p.point('infuser',(10,4,4))
    p.doubles((14,4,1),(14,4,2),'west',2,'mage workshop storage')
    # Five-tier pointed roof. Corner blocks make a watertight stepped outline.
    for ring in range(4):
        lo=8+ring;hi=16-ring;za=-1+ring;zb=7-ring;y=8+ring
        for z in range(za,zb+1):
            for x in range(lo,hi+1):
                if x not in (lo,hi) and z not in (za,zb):continue
                if x in (lo,hi) and z in (za,zb):s=state('dark_oak_planks')
                else:
                    f='east' if x==lo else 'west' if x==hi else 'south' if z==za else 'north'
                    s=state('dark_oak_stairs',facing=f,half='bottom',shape='straight',waterlogged=False)
                p.put(x,y,z,s)
    p.put(12,12,3,state('dark_oak_slab',type='bottom',waterlogged=False))
    p.put(12,7,4,state('dark_oak_log',axis='x'));p.lantern(12,6,4)
    for x in (10,14):p.put(x,5,7,state('purple_wall_banner',facing='south'))
    p.fill(0,24,-1,-1,7,31,state('stone_bricks'))
    p.clear(0,24,0,2,7,31)
    beds=[(0,0),(7,0),(14,0),(21,0),(0,7),(21,7),(0,14),(21,14),(0,21),(7,21),(14,21),(21,21)]
    plots=[]
    for x,z0 in beds:
        z=z0+7;p.fill(x,x+3,-1,-1,z,z+3,state('grass_block',snowy=False),'terrain')
        p.put(x+1,0,z+1,MOD['collector'])
        plants=[[xx,0,zz] for zz in range(z,z+4) for xx in range(x,x+4) if (xx,zz)!=(x+1,z+1)]
        plots.append({'bounds_xz':[[x,z],[x+3,z+3]],'collector':[x+1,0,z+1],'planting_cells':plants,'protected':True,'supply_plants_at_runtime':True})
    p.meta['collector_beds']=plots;p.meta['healing_clearing_xz']=[[7,14],[17,24]]
    p.point('healing_position',(12,0,19))
    p.meta['collector_rotation_rule']='Rotate each bed bounds, then place Collector at world minimum x+1,z+1; do not merely rotate the offset Collector cell.'
    p.meta['guard_clearance_blocks']=3
    return p

def fish():
    p=Plan('fisherton_hut_dock','Fisherton shore hut and dock',((0,-2,0),(10,6,16)),(5,0,0))
    shed(p,2,0,peaked=True)
    p.doubles((3,0,1),(3,0,2),'east',2,'fishing storage')
    p.fill(4,6,-1,-1,7,14,state('spruce_planks'));p.clear(4,6,0,2,7,14)
    p.meta['source_water_required']={'bounds_inclusive':[[2,-2,7],[8,-2,16]],'state':'minecraft:water[level=0]','cells':70,'is_site_precondition_not_construction':True,'do_not_supply_water_from_template':True}
    p.meta['dock']={'bounds_xz':[[4,7],[6,14]],'center_walkway_xz':[[5,7],[5,14]],'slots':[{'id':i+1,'pos':[x,0,z],'bay_xz':[[x,z-1],[x,z]],'chair':'runtime_visual_only'} for i,(x,z) in enumerate([(4,14),(6,14),(4,12),(6,12),(4,10),(6,10)])]}
    return p

def farm():
    p=Plan('farmington_field','Farmington 9x9 field',((0,-2,0),(8,1,8)),(4,0,8),'field_component')
    p.fill(0,8,-1,-1,0,8,state('farmland',moisture=7),'terrain');p.clear(0,8,0,1,0,8)
    p.put(4,-2,4,state('dirt'),'terrain');p.put(4,-1,4,state('water',level=0),'irrigation');p.put(4,0,4,state('glass'))
    p.point('covered_irrigation',(4,-1,4))
    p.meta['crop_strips']=[{'name':name,'bounds_xz':[[lo,0],[lo+2,8]],'planting_cells':[[x,0,z] for z in range(9) for x in range(lo,lo+3) if (x,z)!=(4,4)]} for lo,name in [(0,'left'),(3,'middle'),(6,'right')]]
    p.meta['planting']='Empty field; runtime selectors and actual supplies choose wheat, carrots, potatoes, or beetroot.'
    p.meta['water_supply']='One real water-source placement; empty bucket is returned, not consumed.'
    return p

def mining():
    p=Plan('minington_strip_level','Minington repeatable strip level',((-26,-1,-2),(26,3,29)),(0,0,-1),'excavation_plan')
    p.clear(-2,2,0,2,0,4);p.fill(-2,2,-1,-1,0,4,state('cobblestone'))
    p.clear(-1,1,0,1,5,29)
    branches=[]
    for k in range(10):
        z=1+3*k;inner=3 if k<2 else 2
        for side,x1,x2 in [('left',-26,-inner),('right',inner,26)]:
            p.clear(x1,x2,0,1,z,z)
            branches.append({'id':str(k+1)+'_'+side,'bounds':[[x1,0,z],[x2,1,z]],'length':x2-x1+1})
            for x in ([-22,-14,-6] if side=='left' else [6,14,22]):p.put(x,1,z,state('wall_torch',facing='north'))
    p.doubles((-2,0,0),(-1,0,0),'south',2,'left depot')
    p.doubles((1,0,0),(2,0,0),'south',2,'right depot')
    for y in range(4):
        p.put(0,y,-2,state('cobblestone'));p.put(0,y,-1,state('ladder',facing='south',waterlogged=False))
    for z in (6,12,18,24):p.put(1,1,z,state('wall_torch',facing='west'))
    p.meta.update(branches=branches,initial_landing_depth=20,next_level_y_offset=-4,alternating_offset=False,central_corridor_width=3,passage_height=2,landing_height=3,do_not_paste_as_instant_excavation=True)
    p.meta['runtime_rules']='Excavate only claimed passages in order. Stop a branch at water/lava/caves. Preserve unlisted rock. Supply torches/ladders/chests; full storage pauses. Never place generic stone over ore.'
    return p

def mining_parts(full):
    p=Plan('minington_landing','Minington 5x5 depot and ladder access',((-2,-1,-2),(2,3,4)),(0,0,-1),'underground_component')
    for q,s in full.cells.items():
        if -2<=q[0]<=2 and -2<=q[2]<=4:p.put(*q,s,full.roles[q])
    p.meta['storage_groups']=full.meta['storage_groups'];p.meta['first_landing_depth']=20;p.meta['repeat_y_offset']=-4
    s=Plan('minington_shaft_step','Minington four-block ladder extension',((0,0,-1),(0,3,0)),(0,0,0),'underground_component')
    for y in range(4):
        s.put(0,y,-1,state('cobblestone'));s.put(0,y,0,state('ladder',facing='south',waterlogged=False))
    s.meta['repeat_y_offset']=-4
    return [p,s]

def material_counts(p):
    structure=collections.Counter();terrain=collections.Counter();fluid=collections.Counter();anchors=collections.Counter()
    for q,s in p.cells.items():
        bid,props=split_state(s);role=p.roles[q]
        if s in (AIR,VOID):continue
        if role=='anchor':anchors[bid]+=1;continue
        if bid=='minecraft:water':fluid['water_source_placements']+=1;continue
        if bid.endswith('_door') and props.get('half')=='upper':continue
        count=2 if bid.endswith('_slab') and props.get('type')=='double' else 1
        item={'minecraft:wall_torch':'minecraft:torch','minecraft:purple_wall_banner':'minecraft:purple_banner','minecraft:farmland':'minecraft:dirt','minecraft:grass_block':'minecraft:dirt'}.get(bid,bid)
        (terrain if role=='terrain' else structure)[item]+=count
    for e in p.entities:structure.update(e['cost'])
    return {'construction_items':dict(sorted(structure.items())),'terrain_fill_if_not_reused':dict(sorted(terrain.items())),'fluid_operations':dict(fluid),'already_placed_anchor_items':dict(anchors)}

def export(p):
    mn,mx=p.bounds;size=[mx[i]-mn[i]+1 for i in range(3)]
    def local(q):return [q[i]-mn[i] for i in range(3)]
    assert all(all(mn[i]<=q[i]<=mx[i] for i in range(3)) for q in p.cells),p.name
    states=[VOID,AIR]+sorted(set(p.cells.values())-{VOID,AIR});indices={s:i for i,s in enumerate(states)}
    data=bytearray()
    for y in range(mn[1],mx[1]+1):
        for z in range(mn[2],mx[2]+1):
            for x in range(mn[0],mx[0]+1):data.extend(varint(indices[p.cells.get((x,y,z),VOID)]))
    entities=[];native_entities=[]
    for e in p.entities:
        pos=local(e['pos']);bp=local(e['blockpos']);nbt=dict(e['nbt'])
        nbt.setdefault('Rotation',L(5,[-90.,0.] if e['id']=='minecraft:item_frame' else [0.,0.]))
        for k,v in zip(('TileX','TileY','TileZ'),bp):
            if k in nbt:nbt[k]=I(v)
        entities.append({'Id':T(e['id']),'Pos':L(6,pos),**nbt})
        native_entities.append({'pos':L(6,pos),'blockPos':L(3,bp),'nbt':C({'id':T(e['id']),**nbt})})
    bes=[];blocks=[];native_palette=[]
    for s in states:
        bid,props=split_state(s);tag={'Name':T(bid)}
        if props:tag['Properties']=C({k:T(v) for k,v in props.items()})
        native_palette.append(tag)
    for q,s in sorted(p.cells.items(),key=lambda q:(q[0][1],q[0][2],q[0][0])):
        pos=local(q);block={'pos':L(3,pos),'state':I(indices[s])};bid,_=split_state(s)
        if bid in ['minecraft:chest','minecraft:furnace','minecraft:blast_furnace']:
            nbt={'id':T(bid),'Items':L(10,[])};block['nbt']=C(nbt)
            bes.append({'Id':T(bid),'Pos':IA(pos),'Items':L(10,[])})
        # Mod block entities intentionally start with registered defaults, no copied ownership or energy.
        blocks.append(block)
    required=sorted({s.split(':')[0] for s in states if not s.startswith('minecraft:')})
    offsets=[-v for v in local(p.anchor)]
    metadata={'Name':T(p.title),'Author':T('Automatons design'),'RequiredMods':L(8,required),**{k:I(v) for k,v in zip(('WEOffsetX','WEOffsetY','WEOffsetZ'),offsets)}}
    schem={'Version':I(2),'DataVersion':I(DV),'Metadata':C(metadata),'Width':S(size[0]),'Height':S(size[1]),'Length':S(size[2]),'Offset':IA([0,0,0]),'PaletteMax':I(len(states)),'Palette':C({s:I(i) for s,i in indices.items()}),'BlockData':(7,data),'BlockEntities':L(10,bes),'Entities':L(10,entities)}
    save_nbt(ROOT/'schematics'/f'{p.name}.schem','Schematic',schem)
    save_nbt(ROOT/'structures'/f'{p.name}.nbt','',{'DataVersion':I(DV),'size':L(3,size),'palette':L(10,native_palette),'blocks':L(10,blocks),'entities':L(10,native_entities)})
    manifest={'schema':'automatons.schematic-handoff.v1','name':p.name,'title':p.title,'kind':p.kind,'minecraft':'1.20.1','data_version':DV,'dimensions':size,'logical_min':mn,'logical_max':mx,'origin_local':local(p.anchor),'anchor_logical':p.anchor,'coordinate_rule':'x east, z south; logical y=0 is ground walking height. File-local = logical - logical_min. Default front is south; ranch gate faces east.','void_policy':'structure_void means PRESERVE; air means CLEAR. Native NBT omits preserve cells. Never turn omitted cells into air.','features':p.features,'required_mod_blocks':sorted(s for s in states if not s.startswith('minecraft:')),'blocks':[{'pos':local(q),'logical_pos':q,'state':s,'operation':'clear' if s==AIR else 'place','role':p.roles[q]} for q,s in sorted(p.cells.items())],'entities':[{'id':e['id'],'pos':local(e['pos']),'blockpos':local(e['blockpos']),'nbt':plain(C(e['nbt'])),'required_items':e['cost']} for e in p.entities],'material_bill':material_counts(p),'worksite':p.meta,'provenance':'Approved full prompt plus concretized roof, wall, lighting, and support details; see DESIGN-NOTES.txt.'}
    for i,e in enumerate(manifest['entities']):e['nbt']=plain(native_entities[i]['nbt'])
    (ROOT/'manifests'/f'{p.name}.json').write_text(json.dumps(manifest,indent=2)+'\n')
    return manifest

def build():
    plans=[crafter(),hunter(),ranch(),quarry(),tower(),fish(),farm(),mining()]
    plans+=mining_parts(plans[-1])
    for folder in ['schematics','structures','manifests','previews']:(ROOT/folder).mkdir(exist_ok=True)
    manifests=[export(p) for p in plans]
    with (ROOT/'MATERIALS.csv').open('w',newline='') as f:
        w=csv.writer(f);w.writerow(['plan','category','item_or_operation','count'])
        for m in manifests:
            for category,counts in m['material_bill'].items():
                for item,count in counts.items():w.writerow([m['name'],category,item,count])
    (ROOT/'catalog.json').write_text(json.dumps([{'name':m['name'],'title':m['title'],'dimensions':m['dimensions'],'kind':m['kind'],'origin_local':m['origin_local']} for m in manifests],indent=2)+'\n')
    return plans,manifests

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--registry-map',type=Path);args=parser.parse_args()
    if args.registry_map:MOD.update(json.loads(args.registry_map.read_text()))
    plans,manifests=build()
    print(json.dumps({'plans':len(plans),'schem_files':len(plans),'nbt_files':len(plans),'explicit_cells':sum(len(p.cells) for p in plans)}))
