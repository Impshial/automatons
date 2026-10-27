"""Export only handbook scene NBTs from current worksite blueprints.

Markdown pages in resourcepacks/Automatons-Guide are the editable source of truth.
This optional exporter never writes pages or screenshots; ordinary text edits
need only a resource reload, not this script or a mod build.
"""
from pathlib import Path
import gzip, hashlib, json, struct

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
BOOK = ROOT / 'resourcepacks/Automatons-Guide/assets/automatons/guides/automatons/handbook'


def nbt_string(value):
    b=value.encode('utf-8'); return struct.pack('>H',len(b))+b
def tag(kind,name,payload): return bytes([kind])+nbt_string(name)+payload
def integer(n): return struct.pack('>i',n)
def listing(kind, values): return bytes([kind])+integer(len(values))+b''.join(values)
def compound(*values): return b''.join(values)+b'\0'
def structure(name, size, blocks):
    palette=list(dict.fromkeys(b['state'] for b in blocks)); states=[]
    for state in palette:
        block,_,properties=state.partition('[')
        fields=[tag(8,'Name',nbt_string(block))]
        if properties:
            fields.append(tag(10,'Properties',compound(*[tag(8,k,nbt_string(v)) for k,v in (p.split('=',1) for p in properties.rstrip(']').split(','))])))
        states.append(compound(*fields))
    entries=[]
    for block in blocks:
        fields=[tag(9,'pos',listing(3,[integer(block[k]) for k in ['x','y','z']])),tag(3,'state',integer(palette.index(block['state'])))]
        if block.get('nbt'): fields.append(tag(10,'nbt',block['nbt']))
        entries.append(compound(*fields))
    data=tag(10,'',compound(tag(3,'DataVersion',integer(3465)),tag(9,'size',listing(3,[integer(i) for i in size])),tag(9,'palette',listing(10,states)),tag(9,'blocks',listing(10,entries)),tag(9,'entities',listing(10,[]))))
    dest=BOOK/f'assets/structures/{name}.nbt';dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(gzip.compress(data,mtime=0))

manifest={}
for name in ['minehead', 'warehouse', 'builder', 'lumber', 'farm']:
    source=RES/f'data/automatons/buildings/{name}.json';data=json.loads(source.read_text(encoding='utf-8'))
    blocks=[dict(b) for b in data['blocks']]
    # The existing Totem is not a supplied construction cost. Show its real anchor in the illustration.
    x,y,z=data['anchor'];blocks=[b for b in blocks if (b['x'],b['y'],b['z']) != (x,y,z)]
    blocks.append({'x':x,'y':y,'z':z,'state':'automatons:totem'})
    structure(name,data['size'],blocks)
    manifest[name]={'source':str(source.relative_to(ROOT)).replace('\\','/'),'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'size':data['size'],'anchor':data['anchor'],'framesExcludedFromScene':data.get('frames',[])}
(BOOK/'assets/structures/source-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
corners=[]
for i in range(4):
    item=compound(tag(8,'id',nbt_string('automatons:lumite_shard')),tag(1,'Count',b'\1'))
    corners.append(compound(tag(10,'Item',item)))
structure('infusion',[1,1,1],[{'x':0,'y':0,'z':0,'state':'automatons:infusing_pedestal','nbt':compound(tag(8,'id',nbt_string('automatons:infuser')),tag(3,'Version',integer(3)),tag(9,'Corners',listing(10,corners)))}])
structure('network',[9,2,5],[{'x':x,'y':0,'z':z,'state':'minecraft:grass_block'} for x in range(9) for z in range(5)]+[{'x':1,'y':1,'z':2,'state':'automatons:pedestal'},{'x':6,'y':1,'z':2,'state':'automatons:totem'},{'x':7,'y':1,'z':2,'state':'minecraft:chest[facing=south,type=single,waterlogged=false]'}])
print('Exported seven handbook scenes; Markdown and screenshots left untouched.')
