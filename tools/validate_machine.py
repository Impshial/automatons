"""Check Machine recipe totals, model footprint/density inputs and Lumite palette provenance."""
from pathlib import Path
import json, hashlib, zipfile
from PIL import Image

root=Path(__file__).resolve().parents[1]
res=root/'src/main/resources'
models=res/'assets/automatons/machine'
vanilla=Path('C:/Users/impsh/curseforge/minecraft/Install/versions/1.20.1/1.20.1.jar')
with zipfile.ZipFile(vanilla) as jar:
    for name in ['chassis','blade','bore']:
        boxes=json.loads((models/f'{name}.json').read_text())
        planes=[]
        for i,b in enumerate(boxes):
            for f in b['faces']:
                axis,sign=f['axis'],f['sign'];plane=b['to' if sign>0 else 'from'][axis]
                for j,oa,op,rect in planes:
                    if i==j or oa!=axis or abs(op-plane)>1e-6:continue
                    r=f['rect']
                    overlap=min(r[2],rect[2])-max(r[0],rect[0])>1e-6 and min(r[3],rect[3])-max(r[1],rect[1])>1e-6
                    assert not overlap,(name,'coplanar visible faces',i,j,f,rect)
                planes.append((i,axis,plane,f['rect']))
        for b in boxes:
            assert all(a<c for a,c in zip(b['from'],b['to'])),b
            namespace,path=b['texture'].split(':')
            import io
            image=Image.open(io.BytesIO(jar.read(f'assets/{namespace}/textures/{path}.png'))) if namespace=='minecraft' else Image.open(res/f'assets/{namespace}/textures/{path}.png')
            assert image.size==(16,16),(name,b['texture'],image.size)
        if name=='bore':
            pivots={tuple(b['pivot']) for b in boxes if b.get('group')=='drill'}
            assert len(pivots)==9,pivots
        for b in boxes:
            assert b['from'][0]>=-24.1 and b['to'][0]<=24.1
            assert b['from'][1]>=0 and b['to'][1]<=(48 if name=='bore' else 32)
            assert b['from'][2]>=-24.1 and b['to'][2]<=(24.1 if name=='chassis' else 40)
recipes=res/'data/automatons/recipes'
assert json.loads((recipes/'machine_frame.json').read_text())['result']['count']==2
assert json.loads((recipes/'machine_track.json').read_text())['result']['count']==2
bore=json.loads((recipes/'bore_attachment.json').read_text())
assert bore['type']=='automatons:bore' and ''.join(bore['pattern']).count('D')==3
manifest=json.loads((root/'reference/current-run/ore-emissive-pixels.json').read_text())
results={}
for name in ['lumite_ore_stone_texture','lumite_ore_deepslate_texture']:
    old=Image.open(root/f'art/lumite-0.7.2/{name}.png').convert('RGBA')
    new=Image.open(res/f'assets/automatons/textures/block/{name}.png').convert('RGBA')
    assert old.size==new.size==(16,16)
    mask={tuple(p) for p in manifest[name]['cavity_pixels']}
    changed=[]
    for y in range(16):
        for x in range(16):
            a,b=old.getpixel((x,y)),new.getpixel((x,y))
            if a!=b:
                assert (x,y) in mask and sum(b[:3])>sum(a[:3]) and a[3]==b[3],(name,x,y,a,b)
                changed.append([x,y])
    assert len(changed)>=35,(name,len(changed))
    results[name]={'changed_pixels':len(changed),'sha256':hashlib.sha256((res/f'assets/automatons/textures/block/{name}.png').read_bytes()).hexdigest()}
print('PASS: native 16x16 material textures; formed footprints; exactly nine drill pivots; recipe yields; untouched ore backgrounds/rims; '+json.dumps(results))
