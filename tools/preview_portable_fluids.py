"""Offline preview of the actual Tank/drain block JSON and miniature item geometry."""
from pathlib import Path
import io,json,math,zipfile
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];ASSETS=ROOT/'src/main/resources/assets/automatons'
jar=zipfile.ZipFile('C:/Users/impsh/curseforge/minecraft/Install/versions/1.20.1/1.20.1.jar')
im=Image.new('RGB',(1250,580),'#252b34');draw=ImageDraw.Draw(im)
font=lambda n:ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',n)
draw.text((28,20),'Tank, Lumina Drain and carryable Machine',fill='#f1e8fa',font=font(30))
draw.text((28,65),'Actual model geometry with vanilla Minecraft textures. No new bitmap textures.',fill='#c2bdcb',font=font(17))
cache={}
def tex(name):
    if name not in cache:
        ns,path=name.split(':');data=jar.read(f'assets/{ns}/textures/{path}.png') if ns=='minecraft' else (ASSETS/f'textures/{path}.png').read_bytes();cache[name]=Image.open(io.BytesIO(data)).convert('RGBA')
    return cache[name]
axes={'east':(0,1),'west':(0,-1),'up':(1,1),'down':(1,-1),'south':(2,1),'north':(2,-1)}
for i,(title,models) in enumerate([
    ('Glass Tank',[('block/tank',0)]),('Tank with Lumina Drain',[('block/tank',0),('block/lumina_drain',16)]),('Carryable Machine',[('item/packed_machine',0)])]):
    cx=210+i*414;cy=370;scale=8.4
    draw.rounded_rectangle((12+i*414,110,408+i*414,510),radius=12,fill='#303845')
    patches=[]
    for name,height in models:
        model=json.loads((ASSETS/f'models/{name}.json').read_text())
        for element in model['elements']:
            lo=element['from'];hi=element['to']
            for face,settings in element['faces'].items():
                axis,sign=axes[face]
                # Show all glass surfaces; opaque back faces naturally depth-sort behind fronts.
                u,v=(axis+1)%3,(axis+2)%3
                name=model['textures'][settings['texture'][1:]];texture=tex(name)
                a,b,c,d=settings.get('uv',[0,0,16,16])
                for x in range(16):
                    for y in range(16):
                        pixel=texture.getpixel((min(15,int(a+(c-a)*(x+.5)/16)),min(15,int(b+(d-b)*(y+.5)/16))))
                        if pixel[3]<100:continue
                        coords=[]
                        for xx,yy in [(x,y),(x+1,y),(x+1,y+1),(x,y+1)]:
                            p=[0.,0.,0.];p[axis]=hi[axis] if sign>0 else lo[axis]
                            p[u]=lo[u]+(hi[u]-lo[u])*xx/16;p[v]=lo[v]+(hi[v]-lo[v])*yy/16;p[1]+=height
                            coords.append(p)
                        shade=[.75,1,.88][axis] if sign>0 else .6
                        color=tuple(int(k*shade) for k in pixel[:3])
                        patches.append((sum(sum(p) for p in coords)/4,coords,color))
    for _,coords,color in sorted(patches,key=lambda a:a[0]):
        draw.polygon([(cx+(p[0]-p[2])*scale,cy+(p[0]+p[2]-16)*scale*.48-p[1]*scale) for p in coords],fill=color)
    draw.text((cx,473),title,anchor='mm',fill='#f5edf9',font=font(21))
draw.text((28,538),'Offline preview; in-game lighting and transparent rendering will differ.',fill='#b5bcc9',font=font(17))
out=ROOT/'art/machine-0.9.1/portable-fluids-preview.png';out.parent.mkdir(parents=True,exist_ok=True);im.save(out);print(out)
