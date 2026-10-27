"""Code-native cuboids and recipes; every material is an existing native 16x16 Minecraft texture."""
from pathlib import Path
import json
from cuboid_surfaces import surfaces

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT/'src/main/resources'
def write(path, value):
    p=RES/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
T={'iron':'minecraft:block/iron_block','dark':'minecraft:block/polished_deepslate','wood':'minecraft:block/oak_planks','seat':'minecraft:block/brown_wool','copper':'minecraft:block/copper_block','diamond':'minecraft:block/diamond_block','violet':'minecraft:block/purple_concrete','white':'automatons:block/white_stone','red':'minecraft:block/redstone_block','glass':'minecraft:block/glass','paper':'minecraft:block/white_concrete'}
def box(lo,hi,texture,**kw):return {'from':lo,'to':hi,'texture':T[texture],**kw}
def frame():
    result=[]
    for x in [0,13]:
        for z in [0,13]:result.append(box([x,0,z],[x+3,16,z+3],'iron'))
    for y in [0,13]:
        for z in [0,13]:result.append(box([3,y,z],[13,y+3,z+3],'iron'))
        for x in [0,13]:result.append(box([x,y,3],[x+3,y+3,13],'iron'))
    # Four small copper rivets, under 2% of the full block's face area.
    for x in [1,14]:
        for y in [1,14]:result.append(box([x,y,15.95],[x+1,y+1,16.05],'copper'))
    return result
parts={'machine_frame':frame()}
parts['machine_track']=[box([1,1,0],[15,15,16],'dark'),box([0,4,5],[16,12,11],'iron')]+[box([0,0,z],[16,2,z+2],'dark') for z in range(0,16,4)]+[box([0,14,z],[16,16,z+2],'dark') for z in range(0,16,4)]
parts['attachment_mount']=frame()+[box([3,4,0],[7,10,8],'dark'),box([9,4,0],[13,10,8],'dark'),box([4,5,0],[6,9,1],'iron'),box([10,5,0],[12,9,1],'iron')]
parts['operator_station']=[box([0,0,0],[16,3,16],'iron'),box([3,3,4],[13,6,14],'seat'),box([3,6,12],[13,12,15],'seat'),box([1,3,2],[3,7,15],'wood'),box([13,3,2],[15,7,15],'wood'),box([3,6,1],[13,8,3],'dark'),box([7,8,1],[9,9,3],'violet')]
parts['cargo_compartment']=[box([1,1,1],[15,15,15],'wood')]+[box([x,0,0],[x+2,16,16],'iron') for x in [0,14]]+[box([0,y,0],[16,y+2,16],'iron') for y in [0,14]]+[box([7,6,0],[9,10,1],'iron')]
parts['lumina_engine']=[box([1,1,1],[15,15,15],'dark')]+frame()+[box([x,y,0],[x+3,y+3,1],'violet') for x in [3,10] for y in [3,10]]+[box([7,15,7],[9,16,9],'red')]
parts['blade_attachment']=[box([0,2,2],[16,5,5],'iron'),box([0,5,4],[16,11,6],'iron'),box([0,11,5],[16,13,8],'dark'),box([2,4,6],[5,8,15],'dark'),box([11,4,6],[14,8,15],'dark')]
parts['bore_attachment']=[box([0,0,13],[16,16,16],'dark')]
for x in [2.5,8,13.5]:
    for y in [2.5,8,13.5]:
        for radius,z0,z1,material in [(2.3,10,13,'iron'),(1.7,7,10,'dark'),(1.2,4,7,'iron'),(.6,2,4,'diamond')]:parts['bore_attachment'].append(box([x-radius,y-radius,z0],[x+radius,y+radius,z1],material))
parts['program']=[box([3,2,7],[13,14,8],'dark'),box([4,3,6.9],[12,13,7],'paper'),box([5,5,6.8],[8,9,6.9],'violet'),box([9,4,6.8],[11,6,6.9],'red')]+[box([x,11,6.8],[x+1,13,6.9],'iron') for x in [5,7,9]]
for name,cubes in parts.items():
    elements=[];textures={'particle':T['iron']}
    for c in surfaces(cubes):
        key='t'+str(list(T.values()).index(c['texture']));textures[key]=c['texture']
        for patch in c['faces']:
            axis,sign=patch['axis'],patch['sign'];u,v=(axis+1)%3,(axis+2)%3
            lo,hi=list(c['from']),list(c['to']);lo[u],lo[v],hi[u],hi[v]=patch['rect']
            face=[['west','east'],['down','up'],['north','south']][axis][int(sign>0)]
            axes=(0,2) if face in ['up','down'] else (2,1) if face in ['west','east'] else (0,1)
            uv=[lo[a]-c['from'][a] for a in axes]+[hi[a]-c['from'][a] for a in axes]
            elements.append({'from':lo,'to':hi,'faces':{face:{'texture':'#'+key,'uv':uv}}})
    category='item' if name in ['blade_attachment','bore_attachment','program'] else 'block'
    write(f'assets/automatons/models/{category}/{name}.json',{'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':textures,'elements':elements,'display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.7,.7,.7]},'ground':{'scale':[.3,.3,.3]}}})
    if category=='block':
        write(f'assets/automatons/blockstates/{name}.json',{'variants':{'':{'model':f'automatons:block/{name}'}}})
        write(f'assets/automatons/models/item/{name}.json',{'parent':f'automatons:block/{name}'})
        write(f'data/automatons/loot_tables/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'automatons:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})

chassis=[box([-15,3,-24],[15,8,24],'iron'),box([-24,8,-24],[24,11.15,-20],'iron'),box([-24,8,20],[24,11.15,24],'iron')]
for x in [-24,14]:
    chassis.append(box([x,1,-23],[x+10,10,23],'dark'))
    for z in range(-24,21,4):chassis.append(box([x+.05,10.05,z+.05],[x+9.95,11,z+1.95],'dark',group='tread'))
    for z in [-17,0,17]:chassis.append(box([x-.05,3,z-3],[x+10.05,8,z+3],'iron'))
for x in [-23,10]:
    for z in [-21,-3]:
        chassis.append(box([x,12,z],[x+13,28,z+14],'wood'))
        for y in [12,26]:chassis.append(box([x-.05,y,z-.05],[x+13.05,y+2,z+14.05],'iron'))
        chassis.append(box([x+5,18,z+14.05],[x+8,22,z+14.6],'iron'))
chassis += [box([-7,10,6],[7,13,21],'seat'),box([-7,13,6],[7,22,9],'seat'),box([-10,10,20],[10,19,24],'iron'),box([-5,18,22],[5,20,24],'dark'),box([-1,20,22],[1,21,24],'violet',glow=True),box([-7,11,-24],[7,28,-13],'dark')]
for x in [-6,3]:
    for y in [14,22]:chassis.append(box([x,y,-24.1],[x+3,y+3,-24],'white',color='ffd9bbf5',glow=True))
for c in chassis:
    if c['from'][0]>=-10 and c['to'][0]<=10 and c['from'][1]>=10 and c['from'][2]>=6:
        c['station']=True
for x in [-14,13]:chassis.append(box([x,9,23.95],[x+1,11,24.05],'copper'))
blade=[box([-24,0,36],[24,4,40],'iron'),box([-24,4,33],[24,10,37],'iron'),box([-24,10,30],[24,20,34],'iron'),box([-24,20,29],[24,22,34],'dark')]
for x in [-16,12]:blade += [box([x,8,20],[x+4,12,33],'dark'),box([x,20,30],[x+4,22,32],'violet',glow=True)]
bore=[box([-24,0,24],[24,48,27],'dark')]
for x in [-24,22]:bore.append(box([x,0,24],[x+2,48,28],'iron'))
for y in [0,46]:bore.append(box([-24,y,24],[24,y+2,28],'iron'))
for x in [-16,0,16]:
    for y in [8,24,40]:
        for radius,z0,z1,material in [(6.3,27.05,30,'iron'),(4.5,30,33,'dark'),(3,33,36,'iron'),(1.5,36,39.8,'diamond')]:
            bore.append(box([x-radius,y-radius,z0],[x+radius,y+radius,z1],material,group='drill',pivot=[x,y,27]))
for name,data in [('chassis',chassis),('blade',blade),('bore',bore)]:write(f'assets/automatons/machine/{name}.json',surfaces(data))

ingredients={'I':'minecraft:iron_ingot','C':'minecraft:copper_ingot','F':'automatons:machine_frame','S':'automatons:infused_lumite_shard','K':'automatons:magica_core','P':'minecraft:piston','H':'minecraft:chain','D':'minecraft:diamond_pickaxe','L':'minecraft:leather','R':'minecraft:redstone','B':'minecraft:redstone_block','T':'minecraft:chest','A':'minecraft:paper','W':'#minecraft:planks'}
recipes={'machine_frame':(['ICI','C C','ICI'],2),'machine_track':(['IHI','I I','IHI'],2),'attachment_mount':(['IPI','CFC','I I'],1),'blade_attachment':(['FFF','SSS','III'],1),'bore_attachment':(['DDD','FKF','ICI'],1),'operator_station':([' L ','IRI','WSW'],1),'cargo_compartment':(['III','ITI','III'],1),'lumina_engine':(['IKI','KBK','CKC'],1),'program':(['AR','AS'],1)}
for name,(pattern,count) in recipes.items():
    keys={k:({'tag':ingredients[k][1:]} if ingredients[k].startswith('#') else {'item':ingredients[k]}) for k in set(''.join(pattern)) if k!=' '}
    write(f'data/automatons/recipes/{name}.json',{'type':'automatons:bore' if name=='bore_attachment' else 'minecraft:crafting_shaped','pattern':pattern,'key':keys,'result':{'item':'automatons:'+name,'count':count}})
    write(f'data/automatons/advancements/recipes/{name}.json',{'parent':'minecraft:recipes/root','criteria':{'has_iron':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:iron_ingot']}]}}},'rewards':{'recipes':['automatons:'+name]}})
lang=json.loads((RES/'assets/automatons/lang/en_us.json').read_text(encoding='utf-8'))
names={'machine_track':'Track Block','machine_frame':'Frame','attachment_mount':'Attachment Mount','operator_station':'Operator Station','cargo_compartment':'Cargo Compartment','lumina_engine':'Lumina Engine','blade_attachment':'Blade Attachment','bore_attachment':'Bore Attachment','program':'Program'}
for key,value in names.items():lang[('item' if key in ['blade_attachment','bore_attachment','program'] else 'block')+'.automatons.'+key]=value
lang.update({'entity.automatons.machine':'The Machine','key.automatons.machine_engine':'Machine Engine','key.automatons.machine_inventory':'Machine Inventory','key.categories.automatons':'Automatons'})
write('assets/automatons/lang/en_us.json',lang)
for tag in ['mineable/pickaxe','needs_stone_tool']:
    p=f'data/minecraft/tags/blocks/{tag}.json';data=json.loads((RES/p).read_text())
    for key in names:
        if key not in ['blade_attachment','bore_attachment','program'] and 'automatons:'+key not in data['values']:data['values'].append('automatons:'+key)
    write(p,data)
print('Authored Machine models, native-texture materials, recipes and translations.')
