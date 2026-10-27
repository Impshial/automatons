"""Code-native models using vanilla materials; no raster textures are generated."""
from pathlib import Path
import json
from cuboid_surfaces import surfaces
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
def write(path,data):
    p=RES/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
def box(lo,hi,texture):
    return {'from':lo,'to':hi,'faces':{f:{'texture':'#'+texture} for f in ['up','down','north','south','east','west']}}
drain=box([0,0,0],[16,2,16],'iron')
drain['faces']['up']={'texture':'#grate','uv':[0,0,16,16]}
write('assets/automatons/models/block/lumina_drain.json',{'render_type':'minecraft:cutout','textures':{'particle':'minecraft:block/iron_block','iron':'minecraft:block/iron_block','grate':'minecraft:block/iron_trapdoor'},'elements':[drain]})
write('assets/automatons/models/item/lumina_drain.json',{'parent':'automatons:block/lumina_drain'})
write('assets/automatons/blockstates/lumina_drain.json',{'variants':{'':{'model':'automatons:block/lumina_drain'}}})
write('data/automatons/loot_tables/blocks/lumina_drain.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'automatons:lumina_drain'}]}]})
write('data/automatons/recipes/lumina_drain.json',{'type':'minecraft:crafting_shaped','pattern':['III','SCS'],'key':{'I':{'item':'minecraft:iron_bars'},'S':{'item':'minecraft:stone'},'C':{'item':'automatons:empty_magica_core'}},'result':{'item':'automatons:lumina_drain'}})
write('data/automatons/advancements/recipes/lumina_drain.json',{'parent':'minecraft:recipes/root','criteria':{'has_core':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['automatons:empty_magica_core']}]}}},'rewards':{'recipes':['automatons:lumina_drain']}})
write('data/automatons/loot_tables/blocks/tank.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'automatons:tank'}]}]})
variants={}
for kind,texture in enumerate(['water_still','water_still','lava_still']):
    variants[f'kind={kind}']={'model':f'automatons:block/fluid_spill_{kind}'}
    write(f'assets/automatons/models/block/fluid_spill_{kind}.json',{'render_type':'minecraft:translucent','textures':{'particle':f'minecraft:block/{texture}','fluid':f'minecraft:block/{texture}'},'elements':[{'from':[1,0,1],'to':[15,.25,15],'faces':{'up':{'texture':'#fluid','tintindex':0,'uv':[1,1,15,15]}}}]})
write('assets/automatons/blockstates/fluid_spill.json',{'variants':variants})
# A miniature of the actual iron chassis, retaining its surface patches and Tank.
cubes=json.loads((RES/'assets/automatons/machine/chassis.json').read_text())
tank={'from':[-8,16,-19.52],'to':[8,32,-3.52],'texture':'minecraft:block/glass'}
surfaces([tank]);cubes.append(tank)
textures={'particle':'minecraft:block/iron_block'};elements=[]
names={(0,-1):'west',(0,1):'east',(1,-1):'down',(1,1):'up',(2,-1):'north',(2,1):'south'}
for i,c in enumerate(cubes):
    key=f't{i}';textures[key]=c['texture']
    for face in c['faces']:
        axis=face['axis'];u=(axis+1)%3;v=(axis+2)%3
        a,b,d,e=face['rect'];lo=list(c['from']);hi=list(c['to'])
        lo[u]=a;hi[u]=d;lo[v]=b;hi[v]=e
        def scaled(p):return [p[0]/3+8,p[1]/3,p[2]/3+8]
        elements.append({'from':scaled(lo),'to':scaled(hi),'faces':{names[axis,face['sign']]:{'texture':'#'+key,'uv':[0,0,16,16]}}})
write('assets/automatons/models/item/packed_machine.json',{'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':textures,'elements':elements,'display':{'gui':{'rotation':[30,225,0],'scale':[.7,.7,.7]},'ground':{'translation':[0,3,0],'scale':[.4,.4,.4]},'fixed':{'scale':[.5,.5,.5]},'thirdperson_righthand':{'rotation':[75,45,0],'scale':[.375,.375,.375]},'firstperson_righthand':{'rotation':[0,45,0],'scale':[.4,.4,.4]}}})
p=RES/'assets/automatons/lang/en_us.json';data=json.loads(p.read_text(encoding='utf-8'))
data.update({'block.automatons.lumina_drain':'Lumina Drain','block.automatons.fluid_spill':'Evaporating fluid','item.automatons.packed_machine':'The Machine (Packed)','tooltip.automatons.tank':'Shift-right-click with a Wand to carry its fluid. Ordinary breaking spills it.','tooltip.automatons.luminall_bottle':'Drink to restore up to %s Lumina; partial bottles work too.'})
write(p.relative_to(RES),data)
print('Authored portable Machine, drain, spill and empty Tank loot; native textures retained.')
