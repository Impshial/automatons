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
    size=struct.unpack('>II',data[16:24]);expected=(256,256) if path.stem.startswith('lumberton_') and path.parent.name=='entity' else (64,64) if path.parent.name=='entity' else (16,16)
    assert size==expected,(path,size,expected)
model=json.loads((root/'art/lumberton-model.json').read_text())
ys=[];origins={};regions=[]
for part in model['parts']:
    origin,position,size,uv=part['origin'],part['from'],part['size'],part['uv']
    assert all(isinstance(n,int) for n in size+uv)
    assert all(n*2==int(n*2) for n in origin+position)
    parent=origins.get(part.get('parent'),[0,0,0]);origin=[origin[i]+parent[i] for i in range(3)];origins[part['name']]=origin
    ys.extend([origin[1]+position[1],origin[1]+position[1]+size[1]])
    assert uv[0]+2*(size[0]+size[2])<=model['texture_size'][0] and uv[1]+size[1]+size[2]<=model['texture_size'][1]
    box=(uv[0],uv[1],uv[0]+2*(size[0]+size[2]),uv[1]+size[1]+size[2])
    for previous in regions:assert box[2]<=previous[0] or box[0]>=previous[2] or box[3]<=previous[1] or box[1]>=previous[3],part['name']
    regions.append(box)
assert max(ys)==24 and model['standing_body_height']==26 and model['guard_body_height']==40
assert next(p for p in model['parts'] if p['name']=='head')['size']==[9,8,8]
assert next(p for p in model['parts'] if p['name']=='body')['size']==[9,9,6]
assert set(p['job'] for p in model['parts'] if p['job']>0)==set(range(1,13))
assert next(p for p in model['parts'] if p['name']=='guard_head')['origin'][1]+next(p for p in model['parts'] if p['name']=='guard_head')['from'][1]==-16
assert next(p for p in model['parts'] if p['name']=='guard_body')['size']==[12,16,8]
guard_head=next(p for p in model['parts'] if p['name']=='guard_head')
guard_body=next(p for p in model['parts'] if p['name']=='guard_body')
assert guard_head['size']==[11,11,10]
assert guard_body['origin'][1]+guard_body['from'][1]-(guard_head['origin'][1]+guard_head['from'][1]+guard_head['size'][1])==1, 'Guardington should have the same one-unit neck gap as ordinary Automatons'
assert len({p['name'] for p in model['parts']})==len(model['parts'])
for path in (resources/'assets/automatons/models/block').glob('*.json'):
    m=json.loads(path.read_text())
    if 'elements' not in m:
        assert m.get('parent') == 'minecraft:block/cross', (path, 'Unsupported inherited block model')
        assert m.get('render_type') == 'minecraft:cutout', path
        namespace, texture = m['textures']['cross'].split(':', 1)
        assert (resources / 'assets' / namespace / 'textures' / (texture + '.png')).is_file(), path
        continue
    for cube in m['elements']:
        dimensions=[cube['to'][i]-cube['from'][i] for i in range(3)]
        for face,data in cube['faces'].items():
            if 'uv' not in data:continue  # Vanilla's implicit UVs follow the cuboid dimensions.
            uv=data['uv'];expected=(dimensions[0],dimensions[1]) if face in ['north','south'] else (dimensions[2],dimensions[1]) if face in ['east','west'] else (dimensions[0],dimensions[2])
            assert (uv[2]-uv[0],uv[3]-uv[1])==expected,(path,face)
print(f'PASS: {count} JSON assets, {len(textures)} texture dimensions, thirteen profession appearances, 26-unit ordinary / 40-unit armored rigs, non-overlapping entity UVs, and block-face UV density.')

from worker_surfaces import build, audit, OUTPUT
surfaces = build(model)
audit(surfaces, model)
assert json.loads(OUTPUT.read_text(encoding="utf-8")) == surfaces, "Worker surface mesh is stale"
