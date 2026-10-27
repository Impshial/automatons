"""Render the actual cuboids and their Minecraft texels; does not modify game textures."""
from pathlib import Path
import io,json,math,zipfile
from PIL import Image,ImageDraw,ImageFont
from cuboid_surfaces import surfaces
ROOT=Path(__file__).resolve().parents[1];ASSETS=ROOT/'src/main/resources/assets/automatons';OUT=ROOT/'art/machine-0.9.1';OUT.mkdir(parents=True,exist_ok=True)
vanilla=zipfile.ZipFile('C:/Users/impsh/curseforge/minecraft/Install/versions/1.20.1/1.20.1.jar')
cache={}
def texture(name):
    if name not in cache:
        ns,path=name.split(':');data=vanilla.read(f'assets/{ns}/textures/{path}.png') if ns=='minecraft' else (ASSETS/f'textures/{path}.png').read_bytes()
        cache[name]=Image.open(io.BytesIO(data)).convert('RGBA');assert cache[name].size==(16,16),name
    return cache[name]
image=Image.new('RGB',(1660,820),'#20252e');draw=ImageDraw.Draw(image)
def font(n):return ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',n)
draw.text((35,24),'AUTOMATONS  /  THE MACHINE',font=font(20),fill='#cab6e0')
draw.text((35,60),'Iron chassis, native Minecraft textures',font=font(36),fill='#f2eef9')
draw.text((35,112),'Actual model geometry · 16 pixels per block · Copper limited to small rivets',font=font(20),fill='#b5bbc8')
chassis=json.loads((ASSETS/'machine/chassis.json').read_text());tank=json.loads((ASSETS/'models/block/tank.json').read_text())
for element in tank['elements']:
    material=element['faces']['up']['texture'][1:]
    chassis.append({'from':[element['from'][0]-8,element['from'][1]+16,element['from'][2]-11.52],'to':[element['to'][0]-8,element['to'][1]+16,element['to'][2]-11.52],'texture':tank['textures'][material]})
for index,(attachment,title,subtitle) in enumerate([('','Bare chassis','3 × 2 × 3 · four cargo compartments'),('blade','Blade Attachment','3 × 2 × 4 · soft terrain and Lumina'),('bore','Bore Attachment','3 × 3 × 4 · nine diamond-tipped drills')]):
    cx=280+index*550;cy=505;scale=4.5
    draw.rounded_rectangle((15+index*550,165,545+index*550,717),radius=14,fill='#292f3a',outline='#485061')
    cubes=chassis+(json.loads((ASSETS/f'machine/{attachment}.json').read_text()) if attachment else [])
    faces=[]
    for cube in cubes:
        if 'faces' not in cube: surfaces([cube])
        lo,hi=cube['from'],cube['to'];tex=texture(cube['texture']);color=int(cube.get('color','ffffffff'),16);tint=[color>>16&255,color>>8&255,color&255]
        for patch in cube['faces']:
            if patch['sign']<0:continue
            normal=patch['axis'];u,v=(normal+1)%3,(normal+2)%3;shade=[.75,1,.9][normal]
            au,bv,cu,dv=patch['rect']
            for a in range(math.floor(au),math.ceil(cu)):
                for b in range(math.floor(bv),math.ceil(dv)):
                    vertices=[]
                    for aa,bb in [(max(a,au),max(b,bv)),(min(a+1,cu),max(b,bv)),(min(a+1,cu),min(b+1,dv)),(max(a,au),min(b+1,dv))]:
                        p=[0,0,0];p[normal]=hi[normal];p[u]=aa;p[v]=bb;vertices.append(p)
                    pixel=tex.getpixel((a%16,(15-b)%16));
                    if pixel[3]<128:continue
                    rgb=tuple(int(pixel[i]*tint[i]/255*(1 if cube.get('glow') else shade)) for i in range(3))
                    faces.append((sum(sum(p) for p in vertices)/4,vertices,rgb))
    for _,vertices,color in sorted(faces,key=lambda f:f[0]):
        draw.polygon([(cx+(p[0]-p[2])*scale,cy+(p[0]+p[2])*scale*.48-p[1]*scale) for p in vertices],fill=color)
    draw.text((cx,662),title,font=font(25),fill='#f3eef8',anchor='mm');draw.text((cx,696),subtitle,font=font(16),fill='#b8becc',anchor='mm')
draw.text((35,751),'Low operator seat · 108 cargo slots · The existing glass Tank · Violet-white engine details',font=font(22),fill='#dfd4eb')
draw.text((35,787),'Offline model preview. In-game lighting, animation and glass rendering will differ.',font=font(16),fill='#a8adba')
image.save(OUT/'machine-preview.png');print(OUT/'machine-preview.png')
