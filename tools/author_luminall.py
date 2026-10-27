"""Author code-native models/recipes using existing vanilla textures at native texel density."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'

def write(relative, value):
    path = RES / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')

def box(start, end, texture):
    return {'from': start, 'to': end, 'faces': {
        face: {'texture': '#' + texture} for face in ['north','south','west','east','up','down']}}

for name in ['tank', 'lumina_collector']:
    collector = name == 'lumina_collector'
    frame = 'stone' if collector else 'glass'
    elements = [box([0,0,0],[16,3,16], frame),
                box([0,13,0],[16,15,16], frame),
                box([0,15,0],[16,16,16], frame)]
    for x in [2,13]:
        for z in [2,13]:
            elements.append(box([x,3,z],[x+1,13,z+1], frame))
    elements += [box([3,3,2],[13,13,3],'glass'), box([3,3,13],[13,13,14],'glass'),
                 box([2,3,3],[3,13,13],'glass'), box([13,3,3],[14,13,13],'glass')]
    if not collector:
        elements = [box([0,0,0],[16,16,16],'glass')]
    if collector:
        elements = [box([4,0,4],[12,2,12],'stone'),
                    box([5,2,5],[11,10,11],'stone'),
                    box([0,10,0],[16,12,16],'stone'),
                    box([0,12,0],[16,16,2],'stone'),
                    box([0,12,14],[16,16,16],'stone'),
                    box([0,12,2],[2,16,14],'stone'),
                    box([14,12,2],[16,16,14],'stone'),
                    box([6,12,6],[10,16,10],'stone')]
    write(f'assets/automatons/models/block/{name}.json', {
        'parent':'minecraft:block/block','render_type':'minecraft:cutout',
        'textures':{'stone':'minecraft:block/stone','glass':'minecraft:block/glass',
                    'particle':f'minecraft:block/{frame}'},
        'elements':elements})
    write(f'assets/automatons/models/item/{name}.json', {'parent':f'automatons:block/{name}'})
    write(f'assets/automatons/blockstates/{name}.json', {'variants':{'':{'model':f'automatons:block/{name}'}}})
    write(f'data/automatons/loot_tables/blocks/{name}.json', {'type':'minecraft:block','pools':[
        {'rolls':1,'entries':[{'type':'minecraft:item','name':f'automatons:{name}','functions':[
            {'function':'minecraft:copy_nbt','source':'block_entity','ops':[
                {'source':'Tank','target':'BlockEntityTag.Tank','op':'replace'},
                {'source':'Bottle','target':'BlockEntityTag.Bottle','op':'replace'},
                {'source':'MachineFuelThirds','target':'BlockEntityTag.MachineFuelThirds','op':'replace'},
                {'source':'MachineFuelFraction','target':'BlockEntityTag.MachineFuelFraction','op':'replace'}]}]}]}]})

write('data/automatons/loot_tables/blocks/tank.json', {'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'automatons:tank'}]}]})

write('assets/automatons/blockstates/luminall.json', {'variants':{'':{'model':'minecraft:block/water'}}})
write('assets/automatons/models/item/luminall_bucket.json', {
    'parent':'forge:item/bucket','loader':'forge:fluid_container','fluid':'automatons:luminall'})
write('assets/automatons/models/item/luminall_bottle.json', {
    'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/potion_overlay','layer1':'minecraft:item/potion'}})
for name, pattern, key in [
    ('tank',['GGG','GEG','GGG'],{'G':{'item':'minecraft:glass'},'E':{'item':'automatons:empty_magica_core'}}),
    ('lumina_collector',['SCS','CTC','SCS'],{'S':{'item':'minecraft:stone'},'C':{'item':'automatons:magica_core'},'T':{'item':'automatons:tank'}})
]:
    write(f'data/automatons/recipes/{name}.json', {'type':'automatons:collector' if name=='lumina_collector' else 'minecraft:crafting_shaped',
        'pattern':pattern,'key':key,'result':{'item':f'automatons:{name}','count':1}})
    write(f'data/automatons/advancements/recipes/{name}.json', {'parent':'minecraft:recipes/root',
        'criteria':{'has_materials':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['automatons:empty_magica_core']}]}}},
        'rewards':{'recipes':[f'automatons:{name}']}})
write('data/automatons/tags/fluids/luminall.json', {'replace':False,'values':['automatons:luminall','automatons:flowing_luminall']})
path=RES/'data/minecraft/tags/blocks/mineable/pickaxe.json'
data=json.loads(path.read_text(encoding='utf-8'))
for name in ['tank','lumina_collector']:
    if f'automatons:{name}' not in data['values']:data['values'].append(f'automatons:{name}')
write(path.relative_to(RES), data)
path=RES/'assets/automatons/lang/en_us.json'
data=json.loads(path.read_text(encoding='utf-8'))
data.update({
    'block.automatons.tank':'Tank','block.automatons.lumina_collector':'Lumina Collector',
    'block.automatons.luminall':'Luminall','fluid_type.automatons.luminall':'Luminall',
    'item.automatons.luminall_bucket':'Luminall Bucket','item.automatons.luminall_bottle':'Luminall Bottle',
    'tooltip.automatons.collector':'Condenses Lumina from a nearby garden. Hold a Wand to see its boundary.',
    'tooltip.automatons.tank':'Stores fluids. Use buckets or compatible pipes; Shift-right-click with a Wand to retain fluid; ordinary breaking spills it.',
    'tooltip.automatons.fluid_amount':'%s / %s mB',
    'tooltip.automatons.luminall_bottle':'Drink to restore up to %s Lumina; partial bottles work too.',
    'tooltip.automatons.stored_bottle':'Stored bottle: %s mB',
})
write(path.relative_to(RES), data)
print('Authored Tank, Collector and Luminall assets/recipes; existing PNGs unchanged.')
