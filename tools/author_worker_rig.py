"""Author the runtime cuboids, editable rig, and unfiltered one-texel-per-unit atlases together."""
from asset_pixels import *
from worker_outfits import clothing, PALETTES, pixel


def author():
    parts=[]
    def part(name, origin, position, size, material='plank', parent=None, job=0):
        parts.append(dict(name=name, origin=origin, **{'from':position}, size=size,
                          material=material, parent=parent, job=job))
    part('head',[0,6,0],[-4.5,-8,-4],[9,8,8])
    part('body',[0,7,0],[-4.5,0,-3],[9,9,6])
    part('right_arm',[-4.5,7,0],[-3,0,-2],[3,13,4],'bark')
    part('left_arm',[4.5,7,0],[0,0,-2],[3,13,4],'bark')
    part('right_leg',[-2,16,0],[-2,0,-2],[3,8,4],'bark')
    part('left_leg',[2,16,0],[-1,0,-2],[3,8,4],'bark')
    part('neck',[0,0,0],[-2,6,-2],[4,1,4],'iron')
    part('shoulder_r',[0,0,0],[-4.5,7,-2],[1,3,4],'iron')
    part('shoulder_l',[0,0,0],[3.5,7,-2],[1,3,4],'iron')
    clothing(part)
    part('fishing_chair',[0,0,0],[-5,17,-3],[10,1,8],'plank')
    for name, pos, size in [('chair_rl',[-5,18,-3],[1,6,1]),('chair_ll',[4,18,-3],[1,6,1]),
                            ('chair_rb',[-5,18,4],[1,6,1]),('chair_lb',[4,18,4],[1,6,1]),
                            ('chair_back',[-5,10,4],[10,7,1])]:
        part(name,[0,0,0],pos,size,'plank','fishing_chair')
    atlas_size=256
    atlas=canvas(atlas_size,atlas_size)
    x=y=row_height=0
    for p in parts:
        w,h,d=p['size'];bw=2*(w+d);bh=h+d
        if x+bw>atlas_size:x=0;y+=row_height+2;row_height=0
        p['uv']=[x,y];x+=bw+2;row_height=max(row_height,bh)
        u,v=p['uv'];kind=p['material']
        for xx,yy,ww,hh in [(u+d,v,w,d),(u+d+w,v,w,d),(u,v+d,d,h),(u+d,v+d,w,h),(u+d+w,v+d,d,h),(u+2*d+w,v+d,w,h)]:
            if kind in PALETTES:
                for py in range(hh):
                    for px in range(ww):atlas[yy+py][xx+px]=pixel(kind,px,py)
            else:material(atlas,xx,yy,ww,hh,'end' if kind=='bark' and yy==v else kind)
        if p['name'] in ('right_arm','left_arm'):
            for sy in [0,9,10]:rect(atlas,u,v+d+sy,bw,1,metal[1 if sy==10 else 2])
        if p['name'] in ('right_leg','left_leg'):
            rect(atlas,u,v+d,bw,1,metal[2]);rect(atlas,u,v+d+h-1,bw,1,metal[1])
        if p['name']=='parcel':
            rect(atlas,u+d+1,v+d,1,h,rgb('634c31'));rect(atlas,u+d,v+d+1,w,1,rgb('634c31'))
            rect(atlas,u+2*d+w+1,v+d,1,h,rgb('634c31'));rect(atlas,u+2*d+w,v+d+1,w,1,rgb('634c31'))
    assert y+row_height<=atlas_size, (y,row_height)
    body=next(p for p in parts if p['name']=='body');u,v=body['uv']
    for xx,yy in [(4,2),(3,3),(4,3),(5,3),(4,4)]:atlas[v+6+yy][u+6+xx]=gold[2]
    for expression in ['idle','smile','brow','focused','frown','anger','blink','off']:
        a=[row[:] for row in atlas]
        for head in [p for p in parts if p['name'] in ('head','guard_head')]:
            u,v=head['uv']; d=head['size'][2]; fx=u+d; fy=v+d+head['size'][1]-8
            rect(a,fx,fy,9,8,wood[2]);rect(a,fx+1,fy+1,7,6,wood[4])
            for offset in [1,5]:
                eye=fx+offset
                if expression=='off':
                    rect(a,eye,fy+2,3,3,rgb('101116'));continue
                if expression=='blink':
                    rect(a,eye,fy+3,3,1,wood[1]);continue
                top=fy+(2 if expression not in ('focused','smile') else 3)
                height=3 if expression in ('idle','brow','frown') else 2
                rect(a,eye,top,3,height,rgb('f1e9f7'))
                rect(a,eye+1,top,1,height,rgb('a366d5'));a[top][eye+1]=rgb('d9b7f1')
                if expression=='smile':rect(a,eye,fy+5,3,1,wood[3])
                elif expression=='anger':
                    for i in range(3):a[fy+1+(i//2 if offset==1 else (2-i)//2)][eye+i]=wood[0]
                elif expression=='frown':rect(a,eye+(0 if offset==1 else 1),fy+1,2,1,wood[1])
                elif expression=='focused':rect(a,eye,fy+2,3,1,wood[1])
                elif expression=='brow':rect(a,eye,fy+(1 if offset==1 else 2),3,1,wood[1])
        png(RES/f'assets/automatons/textures/entity/lumberton_{expression}.png',a)
        png(ART/f'lumberton-{expression}-atlas-preview.png',[[c for c in row for _ in range(4)] for row in a for _ in range(4)])
    write(ART/'lumberton-model.json',{'texture_size':[atlas_size,atlas_size],'standing_body_height':26,'guard_body_height':40,'units_per_block':16,'parts':parts})
    global_origins={};elements=[];groups={}
    for p in parts:
        parent_origin=global_origins.get(p['parent'],[0,0,0]);origin=[p['origin'][i]+parent_origin[i] for i in range(3)];global_origins[p['name']]=origin
        fr=[p['from'][i]+origin[i] for i in range(3)];to=[fr[i]+p['size'][i] for i in range(3)]
        uid=str(uuid.uuid5(uuid.NAMESPACE_DNS,'automatons.'+p['name']))
        elements.append({'name':p['name'],'type':'cube','uuid':uid,'from':[fr[0],24-to[1],fr[2]],'to':[to[0],24-fr[1],to[2]],'origin':[origin[0],24-origin[1],origin[2]],'box_uv':True,'uv_offset':p['uv'],'faces':{f:{'texture':0,'uv':[0,0,0,0]} for f in ['north','south','east','west','up','down']}})
        groups[p['name']]={'name':p['name'],'origin':[origin[0],24-origin[1],origin[2]],'uuid':str(uuid.uuid5(uuid.NAMESPACE_DNS,'group.'+p['name'])),'children':[uid]}
        if p['parent']:groups[p['parent']]['children'].append(groups[p['name']])
    tex=RES/'assets/automatons/textures/entity/lumberton_idle.png'
    write(ART/'lumberton.bbmodel',{'meta':{'format_version':'4.10','model_format':'modded_entity','box_uv':True},'name':'Automaton','model_identifier':'lumberton','resolution':{'width':atlas_size,'height':atlas_size},'elements':elements,'outliner':[groups[p['name']] for p in parts if not p['parent']],'textures':[{'name':'lumberton_idle.png','id':'0','width':atlas_size,'height':atlas_size,'source':'data:image/png;base64,'+base64.b64encode(tex.read_bytes()).decode()}]})
    def numbers(values):return ', '.join(str(v)+'f' for v in values)
    lines=['    MeshDefinition mesh = new MeshDefinition();','    PartDefinition root = mesh.getRoot();']
    for p in parts:
        variable=p['name']+'Part';parent=p['parent']+'Part' if p['parent'] else 'root';u,v=p['uv']
        lines.append(f'    PartDefinition {variable} = {parent}.addOrReplaceChild("{p["name"]}", CubeListBuilder.create().texOffs({u}, {v}).addBox({numbers(p["from"]+p["size"])}), PartPose.offset({numbers(p["origin"])}));')
    lines.append(f'    return LayerDefinition.create(mesh, {atlas_size}, {atlas_size});')
    source=ROOT/'src/main/java/dev/automatons/client/LumbertonModel.java';java=source.read_text(encoding='utf-8');start=java.index('    MeshDefinition mesh');end=java.index('\n  }',start)
    java=java[:start]+'\n'.join(lines)+java[end:]
    marker='    head.yRot = yaw * Mth.DEG_TO_RAD;'
    if '// Job equipment visibility' in java:
        start=java.index('    // Job equipment visibility');end=java.index(marker,start);java=java[:start]+java[end:]
    visibility='    // Job equipment visibility is derived from the synchronized, persistent job.\n    if (!armored) {\n'
    parent_names={'right_arm':'rightArm','left_arm':'leftArm','right_leg':'rightLeg','left_leg':'leftLeg'}
    for p in parts:
        if p['job']>0:visibility+=f'      {parent_names.get(p["parent"],p["parent"])}.getChild("{p["name"]}").visible = w.job() == {p["job"]};\n'
    visibility+='    }\n'
    java=java.replace(marker,visibility+marker)
    source.write_text(java,encoding='utf-8')
    write(RES/'assets/automatons/models/item/automaton.json',{'parent':'builtin/entity','textures':{'particle':'automatons:block/planks'},'display':{'gui':{'rotation':[15,25,0],'translation':[0,0,0],'scale':[1,1,1]},'ground':{'scale':[.5,.5,.5]},'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.7,.7,.7]}}})
    icon=canvas();material(icon,3,2,10,11,'plank');rect(icon,2,1,12,1,metal[1]);rect(icon,3,13,10,1,metal[2])
    for xx in [4,8]:rect(icon,xx,5,3,3,rgb('f1e9f7'));rect(icon,xx+1,5,1,3,rgb('aa70dc'))
    png(RES/'assets/automatons/textures/item/automaton_head.png',icon)
    # The pedestal display and static fallback share the mouthless, wider face.
    mini=canvas();material(mini,3,0,9,6,'plank');material(mini,3,7,9,4,'plank')
    for xx in [4,8]:rect(mini,xx,2,3,2,rgb('f1e9f7'));rect(mini,xx+1,2,1,2,rgb('aa70dc'))
    for xx in [0,13]:material(mini,xx,7,3,6,'bark');rect(mini,xx,11,3,1,metal[2])
    for xx in [4,9]:material(mini,xx,12,3,4,'bark');rect(mini,xx,15,3,1,metal[2])
    png(RES/'assets/automatons/textures/item/automaton.png',mini)
    hologram=[[(int(145+(r+g+b)/3*.42),int(110+(r+g+b)/3*.54),int(205+(r+g+b)/3*.19),a) for r,g,b,a in row] for row in mini]
    png(RES/'assets/automatons/textures/item/automaton_display.png',hologram)
    print(f'Authored 13 professions, 26-unit ordinary and 40-unit armored bodies, eight expressions and cosmetic chair; {len(parts)} cuboids, atlas {atlas_size}x{atlas_size} at 1 texel/unit.')


if __name__=='__main__':author()
