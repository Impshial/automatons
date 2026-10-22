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
    size=struct.unpack('>II',data[16:24]);expected=(64,64) if path.parent.name=='entity' else (16,16)
    assert size==expected,(path,size,expected)
model=json.loads((root/'art/lumberton-model.json').read_text())
ys=[]
for part in model['parts']:
    origin,position,size,uv=part['origin'],part['from'],part['size'],part['uv'];assert all(isinstance(n,int) for n in origin+position+size+uv)
    ys.extend([origin[1]+position[1],origin[1]+position[1]+size[1]])
    assert uv[0]+2*(size[0]+size[2])<=64 and uv[1]+size[1]+size[2]<=64
assert min(ys)==0 and max(ys)==24
for path in (resources/'assets/automatons/models/block').glob('*.json'):
    m=json.loads(path.read_text())
    for cube in m['elements']:
        dimensions=[cube['to'][i]-cube['from'][i] for i in range(3)]
        for face,data in cube['faces'].items():
            uv=data['uv'];expected=(dimensions[0],dimensions[1]) if face in ['north','south'] else (dimensions[2],dimensions[1]) if face in ['east','west'] else (dimensions[0],dimensions[2])
            assert (uv[2]-uv[0],uv[3]-uv[1])==expected,(path,face)
print(f'PASS: {count} JSON assets, {len(textures)} texture dimensions, 24-unit rig, and all block-face UV densities.')
