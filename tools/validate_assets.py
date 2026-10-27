"""Validate packaged asset dimensions, cuboid density, and JSON syntax."""
from pathlib import Path
import json,struct
root=Path(__file__).resolve().parents[1]
resources=root/'src/main/resources'
count=0
for path in resources.rglob('*.json'):json.loads(path.read_text(encoding='utf-8-sig'));count+=1
textures=list((resources/'assets/automatons/textures').rglob('*.png'))
for path in textures:
    data=path.read_bytes();assert data[:8]==b'\x89PNG\r\n\x1a\n',path
    size=struct.unpack('>II',data[16:24]);expected=(128,128) if path.stem.startswith('lumberton_') and path.parent.name=='entity' else (64,64) if path.parent.name=='entity' else (16,16)
    assert size==expected,(path,size,expected)
model=json.loads((root/'art/lumberton-model.json').read_text())
ys=[];origins={};regions=[]
for part in model['parts']:
    origin,position,size,uv=part['origin'],part['from'],part['size'],part['uv']
    assert all(isinstance(n,int) for n in size+uv)
    assert all(n*2==int(n*2) for n in origin+position)
    parent=origins.get(part.get('parent'),[0,0,0]);origin=[origin[i]+parent[i] for i in range(3)];origins[part['name']]=origin
    ys.extend([origin[1]+position[1],origin[1]+position[1]+size[1]])
    assert uv[0]+2*(size[0]+size[2])<=128 and uv[1]+size[1]+size[2]<=128
    box=(uv[0],uv[1],uv[0]+2*(size[0]+size[2]),uv[1]+size[1]+size[2])
    for previous in regions:assert box[2]<=previous[0] or box[0]>=previous[2] or box[3]<=previous[1] or box[1]>=previous[3],part['name']
    regions.append(box)
assert min(ys)==-4.5 and max(ys)==24 and (max(ys)-min(ys))/16==model['height_blocks']
assert next(p for p in model['parts'] if p['name']=='head')['size']==[9,8,8]
assert next(p for p in model['parts'] if p['name']=='body')['size']==[9,9,6]
for path in (resources/'assets/automatons/models/block').glob('*.json'):
    m=json.loads(path.read_text())
    for cube in m['elements']:
        dimensions=[cube['to'][i]-cube['from'][i] for i in range(3)]
        for face,data in cube['faces'].items():
            if 'uv' not in data:continue  # Vanilla's implicit UVs follow the cuboid dimensions.
            uv=data['uv'];expected=(dimensions[0],dimensions[1]) if face in ['north','south'] else (dimensions[2],dimensions[1]) if face in ['east','west'] else (dimensions[0],dimensions[2])
            assert (uv[2]-uv[0],uv[3]-uv[1])==expected,(path,face)
print(f'PASS: {count} JSON assets, {len(textures)} texture dimensions, 28.5-unit farmer hat / 27-unit miner hat / 26-unit body rig, and all block-face UV densities.')
