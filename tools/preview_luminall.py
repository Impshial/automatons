"""Offline isometric preview of the JSON cuboids with native Minecraft texture texels.

This renders models; it does not create, edit, rescale or replace any game PNG.
"""
from pathlib import Path
import io, json, math, zipfile
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources/assets/automatons'
VANILLA=Path('C:/Users/impsh/curseforge/minecraft/Install/versions/1.20.1/1.20.1.jar')
OUT=ROOT/'art/luminall-0.8.3'
OUT.mkdir(parents=True,exist_ok=True)
archive=zipfile.ZipFile(VANILLA)

def texture(name):
    namespace,path=name.split(':',1)
    data=archive.read(f'assets/{namespace}/textures/{path}.png') if namespace=='minecraft' else (RES/f'textures/{path}.png').read_bytes()
    return Image.open(io.BytesIO(data)).convert('RGBA')

def font(size):
    return ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',size)

image=Image.new('RGBA',(1260,670),(24,27,35,255))
draw=ImageDraw.Draw(image)
draw.text((44,26),'AUTOMATONS  /  LUMINALL',font=font(18),fill='#cbb5e7')
draw.text((44,57),'Tank & Lumina Collector',font=font(34),fill='#f2eef8')
draw.text((44,109),'Model preview using native vanilla stone, glass and tinted water textures',font=font(17),fill='#a9acba')

def render(model_name,center,fraction,bottle_fraction=None):
    model=json.loads((RES/f'models/block/{model_name}.json').read_text(encoding='utf-8'))
    cubes=[]
    for element in model['elements']:
        material=element['faces']['up']['texture'][1:]
        cubes.append((element['from'],element['to'],texture(model['textures'][material]),(255,255,255,255)))
    fluid=texture('minecraft:block/water_still')
    if fraction:
        if model_name == 'lumina_collector':
            for x0,z0,x1,z1 in [(2.05,2.05,13.95,6),(2.05,10,13.95,13.95),(2.05,6,6,10),(10,6,13.95,10)]:
                cubes.append(([x0,12.05,z0],[x1,12.05+3.5*fraction,z1],fluid,(217,187,245,225)))
        else:
            cubes.append(([3.05,3.05,3.05],[12.95,3.05+9.8*fraction,12.95],fluid,(217,187,245,225)))
    if bottle_fraction is not None:
        glass=texture('minecraft:block/glass')
        highlight=texture('automatons:block/white_stone')
        for positions,y0,y1 in [([6,9.65],16.1,21.1),([7,8.65],21.1,23.1)]:
            for x in positions:
                for z in positions:
                    cubes.append(([x,y0,z],[x+.35,y1,z+.35],highlight,(238,246,255,204)))
        cubes += [([6,16.1,6],[10,21.1,10],glass,(255,255,255,200)),
                  ([7,21.1,7],[9,23.1,9],glass,(255,255,255,200))]
        cubes.append(([7,23.1,7],[9,24.1,9],texture('minecraft:block/stripped_oak_log_top'),(255,255,255,255)))
        if bottle_fraction:
            cubes.append(([6.3,16.4,6.3],[9.7,16.4+4.4*bottle_fraction,9.7],fluid,(217,187,245,225)))
    faces=[]
    for lo,hi,tex,tint in cubes:
        # View from positive X/Z, above. Each quad covers at most one original texel.
        for normal,ax,ay,shade in [(1,0,2,1.0),(0,2,1,.78),(2,0,1,.9)]:
            starta,enda=lo[ax],hi[ax]; startb,endb=lo[ay],hi[ay]
            for a in range(math.floor(starta),math.ceil(enda)):
                for b in range(math.floor(startb),math.ceil(endb)):
                    a0,a1=max(starta,a),min(enda,a+1); b0,b1=max(startb,b),min(endb,b+1)
                    vertices=[]
                    for u,v in [(a0,b0),(a1,b0),(a1,b1),(a0,b1)]:
                        p=[0,0,0];p[normal]=hi[normal];p[ax]=u;p[ay]=v;vertices.append(p)
                    color=tex.getpixel((a%16,(15-b)%16 if normal!=1 else b%16))
                    color=tuple(int(color[i]*tint[i]/255*(shade if i<3 else 1)) for i in range(4))
                    if color[3]==0:continue
                    depth=sum(sum(p) for p in vertices)/4
                    faces.append((depth,vertices,color))
    scale=9
    for _,vertices,color in sorted(faces,key=lambda f:f[0]):
        polygon=[(center[0]+(p[0]-p[2])*scale,center[1]+(p[0]+p[2]-16)*scale*.48-p[1]*scale) for p in vertices]
        layer=Image.new('RGBA',image.size)
        ImageDraw.Draw(layer).polygon(polygon,fill=color)
        image.alpha_composite(layer)

for x in [215,630,1045]:
    draw.rounded_rectangle((x-175,166,x+175,539),radius=12,fill='#202631',outline='#373d49')
render('tank',(215,440),0)
render('lumina_collector',(630,440),.45,.35)
render('lumina_collector',(1045,440),1,1)
draw=ImageDraw.Draw(image)
for x,title,subtitle in [(215,'Tank','8,000 mB • any compatible fluid'),(630,'Collector filling','Real reservoir & partial bottle level'),(1045,'Collector full','4,000 mB tank • 250 mB bottle')]:
    draw.text((x,486),title,font=font(22),fill='#f2eef8',anchor='mm')
    draw.text((x,520),subtitle,font=font(14),fill='#bdc0ce',anchor='mm')
draw.text((44,577),'4 × 4 garden footprint  ·  Four Infused Cores  ·  Five Lumina per full bottle',font=font(20),fill='#d6c3ed')
draw.text((44,616),'Offline model illustration; in-game lighting, glass transparency and animation may differ.',font=font(16),fill='#969dab')
image.convert('RGB').save(OUT/'collector-preview.png')
print(OUT/'collector-preview.png')
