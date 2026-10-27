#!/usr/bin/env python3
"""Exact geometry previews, not Minecraft screenshots. Requires Pillow."""
import argparse,json,math
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
from build_schematics import build,split_state,AIR,VOID,MOD
ROOT=Path(__file__).resolve().parents[1]
FONT='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
def font(n):
    try:return ImageFont.truetype(FONT,n)
    except OSError:return ImageFont.load_default()
def color(s):
    b=s.split('[')[0]
    for part,c in [('totem','#8457aa'),('collector','#936bb4'),('infusing','#ad89c9'),(':tank','#70b9bb'),('chest','#bd8539'),('crafting','#98714c'),('blast_furnace','#535d64'),('furnace','#727b80'),('cobblestone','#858b91'),('stone_bricks','#9ca6a9'),('grass_block','#7d9c58'),('farmland','#a27850'),(':dirt','#8b6c48'),('glass','#a5d9df'),('water','#72b0d4'),('hay_block','#cead4f'),('_door','#785734'),('dark_oak','#544435'),('oak_log','#816447'),('oak_planks','#c5a26e'),('spruce','#967951'),('fence','#a88b60'),('ladder','#b99159'),('torch','#ffc65a'),('lantern','#e4b954'),('purple','#8d5ba7')]:
        if part in b:return c
    return '#bab6a7'
def rgb(h):return tuple(int(h.lstrip('#')[i:i+2],16) for i in (0,2,4))
def shade(h,k):return tuple(max(0,min(255,round(v*k))) for v in rgb(h))
def excel(n):
    s='';n+=1
    while n:n,r=divmod(n-1,26);s=chr(65+r)+s
    return s
def symbol(s):
    b=s.split('[')[0]
    for part,label in [('totem','T'),('collector','C'),(':tank','Tk'),('infusing','I'),('chest','Ch'),('crafting','CT'),('blast_furnace','BF'),('furnace','F'),('ladder','L'),('_door','D'),('fence_gate','G'),('hay_block','H'),('glass','G')]:
        if part in b:return label
    return ''

def floorplan(p,y,label,suffix,bounds=None,label_origin=(0,0)):
    mn,mx=bounds or p.bounds;w=mx[0]-mn[0]+1;d=mx[2]-mn[2]+1
    cell=min(72,max(25,int(1500/max(w,d))));margin=76
    img=Image.new('RGB',(max(860,w*cell+margin*2),d*cell+195),'#f8f6ef');dr=ImageDraw.Draw(img)
    dr.text((28,20),p.title,fill='#253c43',font=font(26))
    dr.text((28,56),label+'  |  one square = one block',fill='#58666a',font=font(16))
    top=112
    req=p.meta.get('source_water_required',{}).get('bounds_inclusive')
    quarry=p.meta.get('quarry',{}).get('footprint_xz')
    for z in range(mn[2],mx[2]+1):
        for x in range(mn[0],mx[0]+1):
            s=p.cells.get((x,y,z),VOID);floor=p.cells.get((x,y-1,z),VOID)
            c=color(s) if s not in (AIR,VOID) else color(floor) if floor not in (AIR,VOID) else '#e9e9e2'
            if s==AIR and floor==VOID:c='#fffdf7'
            if req and req[0][0]<=x<=req[1][0] and req[0][2]<=z<=req[1][2] and floor==VOID:c='#d5e9f2'
            if quarry and quarry[0][0]<=x<=quarry[1][0] and quarry[0][1]<=z<=quarry[1][1]:c='#d8dccb'
            xx=margin+(x-mn[0])*cell;zz=top+(z-mn[2])*cell
            dr.rectangle((xx,zz,xx+cell,zz+cell),fill=c,outline='#c6c8bf',width=1)
            t=symbol(s)
            if t:dr.text((xx+cell/2,zz+cell/2),t,fill='#152a32',font=font(max(12,int(cell*.29))),anchor='mm')
    for x in range(mn[0],mx[0]+1):
        lx=x-label_origin[0]
        t=str(x) if p.kind=='excavation_plan' else excel(lx) if lx>=0 else str(lx)
        dr.text((margin+(x-mn[0]+.5)*cell,top-15),t,fill='#47575c',font=font(min(17,cell//2)),anchor='mm')
    for z in range(mn[2],mx[2]+1):dr.text((margin-17,top+(z-mn[2]+.5)*cell),str(z-label_origin[1]+1),fill='#47575c',font=font(min(17,cell//2)),anchor='mm')
    for e in p.entities:
        if math.floor(e['pos'][1])!=y:continue
        x,_,z=e['blockpos'];xx=margin+(x-mn[0]+.5)*cell;zz=top+(z-mn[2]+.5)*cell
        dr.text((xx,zz),'A' if 'armor' in e['id'] else 'S',fill='#6b315d',font=font(max(14,cell//3)),anchor='mm')
    for slot in p.meta.get('dock',{}).get('slots',[]):
        x,_,z=slot['pos'];xx=margin+(x-mn[0]+.5)*cell;zz=top+(z-mn[2]+.5)*cell
        dr.ellipse((xx-cell*.35,zz-cell*.35,xx+cell*.35,zz+cell*.35),fill='#f6ead2',outline='#5c6b6a',width=2)
        dr.text((xx,zz),str(slot['id']),fill='#33464b',font=font(max(14,cell//3)),anchor='mm')
    dr.text((28,img.height-54),'T Totem   C Collector   Tk Tank   I Infuser   Ch Chest   L Ladder   A Armor stand',font=font(14),fill='#485d62')
    dr.text((28,img.height-31),'Pale gray = preserve terrain. Blue = required existing water. Roof omitted.',font=font(14),fill='#485d62')
    img.save(ROOT/'previews'/f'{p.name}_{suffix}.png')

def iso(p):
    W,H=1400,1000;img=Image.new('RGB',(W,H),'#f8f6ef');dr=ImageDraw.Draw(img)
    dr.text((40,25),p.title,font=font(30),fill='#253c43')
    dr.text((40,68),'Geometry preview  |  schematic blocks, with simplified material colors',font=font(18),fill='#58666a')
    cells={q:s for q,s in p.cells.items() if s not in (AIR,VOID)}
    # Water is an annotated site requirement, never part of the dock build file.
    water=p.meta.get('source_water_required',{}).get('bounds_inclusive')
    ghost={}
    if water:
        for z in range(water[0][2],water[1][2]+1):
            for x in range(water[0][0],water[1][0]+1):ghost[(x,water[0][1],z)]='minecraft:water'
    quarry=p.meta.get('quarry',{}).get('footprint_xz')
    if quarry:
        for z in range(quarry[0][1],quarry[1][1]+1):
            for x in range(quarry[0][0],quarry[1][0]+1):ghost[(x,-1,z)]='minecraft:grass_block'
    allcells={**ghost,**cells}
    def unproj(x,y,z):return (x-z)*.866,(x+z)*.5-y
    corners=[unproj(x+dx,y+dy,z+dz) for x,y,z in allcells for dx,dy,dz in [(0,0,0),(1,0,0),(0,1,0),(0,0,1),(1,1,1)]]
    ax=min(a for a,b in corners);bx=max(a for a,b in corners);ay=min(b for a,b in corners);by=max(b for a,b in corners)
    sc=min((W-110)/(bx-ax),(H-200)/(by-ay));ox=(W-(bx-ax)*sc)/2-ax*sc;oy=125-ay*sc
    def pt(x,y,z):a,b=unproj(x,y,z);return(a*sc+ox,b*sc+oy)
    def face(points,c):dr.polygon([pt(*q) for q in points],fill=c,outline=shade('#526262',.75))
    def box(x0,y0,z0,x1,y1,z1,c):
        face([(x1,y0,z0),(x1,y0,z1),(x1,y1,z1),(x1,y1,z0)],shade(c,.74))
        face([(x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)],shade(c,.88))
        face([(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)],shade(c,1.08))
    for (x,y,z),s in sorted(allcells.items(),key=lambda a:(sum(a[0]),a[0][1],a[0][2])):
        b,props=split_state(s);c=color(s)
        if 'ladder' in b or 'torch' in b:continue
        if b.endswith('_fence'):
            box(x+.375,y,z+.375,x+.625,y+1.5,z+.625,c)
            for k in (0.4,0.9):
                if props.get('north')=='true':box(x+.4375,y+k,z,x+.5625,y+k+.1875,z+.5,c)
                if props.get('south')=='true':box(x+.4375,y+k,z+.5,x+.5625,y+k+.1875,z+1,c)
                if props.get('east')=='true':box(x+.5,y+k,z+.4375,x+1,y+k+.1875,z+.5625,c)
                if props.get('west')=='true':box(x,y+k,z+.4375,x+.5,y+k+.1875,z+.5625,c)
            continue
        if b.endswith('_fence_gate'):
            if props.get('facing') in ('east','west'):box(x+.4,y,z,x+.6,y+1.4,z+1,c)
            else:box(x,y,z+.4,x+1,y+1.4,z+.6,c)
            continue
        if b.endswith('_door'):
            if props.get('facing') in ('north','south'):box(x,y,z+.8125,x+1,y+1,z+1,c)
            else:box(x+.8125,y,z,x+1,y+1,z+1,c)
            continue
        if b.endswith('_stairs'):
            box(x,y,z,x+1,y+.5,z+1,c)
            f=props['facing'];dx0=.5 if f=='east' else 0;dx1=.5 if f=='west' else 1;dz0=.5 if f=='south' else 0;dz1=.5 if f=='north' else 1
            box(x+dx0,y+.5,z+dz0,x+dx1,y+1,z+dz1,c);continue
        height=.88 if 'chest' in b else .45 if '_slab' in b else 1
        if '_fence' in b:height=1.45
        if b.endswith('_door'):height=1
        if 'lantern' in b:height=.5
        if '_wall_banner' in b:height=.1
        yy=y+height
        if (x+1,y,z) not in allcells:face([(x+1,y,z),(x+1,y,z+1),(x+1,yy,z+1),(x+1,yy,z)],shade(c,.74))
        if (x,y,z+1) not in allcells:face([(x,y,z+1),(x+1,y,z+1),(x+1,yy,z+1),(x,yy,z+1)],shade(c,.88))
        if (x,y+1,z) not in allcells:face([(x,yy,z),(x+1,yy,z),(x+1,yy,z+1),(x,yy,z+1)],shade(c,1.08))
    dr.text((40,H-55),'Exact cells and block states are in the files. Stair/fence/fixture shapes are simplified here.',font=font(17),fill='#58666a')
    if water:dr.text((40,H-30),'Blue water is a site requirement; the builder does not create this fishing pool.',font=font(17),fill='#58666a')
    elif quarry:dr.text((40,H-30),'Green quarry footprint is preserved terrain, not an already dug pit.',font=font(17),fill='#58666a')
    img.save(ROOT/'previews'/f'{p.name}_isometric.png')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--registry-map',type=Path);args=parser.parse_args()
    if args.registry_map:MOD.update(json.loads(args.registry_map.read_text()))
    plans,_=build()
    for p in plans:
        floorplan(p,0,'Ground / work level','plan')
        if p.kind=='worksite':iso(p)
        if p.name.startswith('luminariton'):
            floorplan(p,4,'Upper workshop, walking height +4; tower-local grid','upper',((9,3,0),(15,7,6)),(9,0))
            floorplan(p,0,'Open lower tower; tower-local grid','tower_plan',((9,-1,0),(15,3,6)),(9,0))
    sheet=Image.new('RGB',(1800,2020),'#f8f6ef');d=ImageDraw.Draw(sheet)
    d.text((35,25),'AUTOMATONS  /  NEW WORKSITES',font=font(33),fill='#253c43')
    d.text((35,75),'Six complete building schematics. Four additional farm and mining components are included.',font=font(20),fill='#58666a')
    for i,p in enumerate(plans[:6]):
        im=Image.open(ROOT/'previews'/f'{p.name}_isometric.png');im.thumbnail((880,620))
        sheet.paste(im,(10+(i%2)*900,130+(i//2)*625))
    sheet.save(ROOT/'previews'/'OVERVIEW.png')
    rows=[]
    for p in plans:
        pictures=[f'{p.name}_plan.png']
        if p.kind=='worksite':pictures.insert(0,f'{p.name}_isometric.png')
        if p.name.startswith('luminariton'):pictures.extend([f'{p.name}_tower_plan.png',f'{p.name}_upper.png'])
        rows.append('<section><h2>'+p.title+'</h2>'+''.join('<a href="previews/'+v+'"><img src="previews/'+v+'"></a>' for v in pictures)+'</section>')
    (ROOT/'PREVIEW.html').write_text('<!doctype html><html><meta charset="utf-8"><title>Automatons schematic previews</title><style>body{font:16px system-ui;background:#f8f6ef;color:#253c43;max-width:1500px;margin:40px auto;padding:20px}section{border-top:1px solid #b7c4c2;padding:24px 0}img{max-width:100%;height:auto}h2{font-size:28px}a{display:block;margin:20px 0}</style><h1>Automatons schematics</h1><p>Exact block layouts with simplified colors. These previews are generated from the same geometry as the schematic files, not in-game screenshots.</p>'+''.join(rows)+'</html>')
    print('Rendered',len(list((ROOT/'previews').glob('*.png'))),'preview images including the contact sheet.')
