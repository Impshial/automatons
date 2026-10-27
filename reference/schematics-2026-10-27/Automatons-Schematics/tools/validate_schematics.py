#!/usr/bin/env python3
"""Independent binary reader checks. Requires nbtlib; optional official blocks report."""
import argparse,collections,json,sys
from pathlib import Path
import nbtlib
ROOT=Path(__file__).resolve().parents[1]
VOID='minecraft:structure_void';AIR='minecraft:air'
DIR={'north':(0,-1),'east':(1,0),'south':(0,1),'west':(-1,0)}
CW=dict(north='east',east='south',south='west',west='north')
CCW={v:k for k,v in CW.items()}
def split(s):
    n,_,t=s.partition('[');return n,dict(p.split('=') for p in t.rstrip(']').split(',')) if t else {}
def statestr(p):
    props=p.get('Properties',{});return str(p['Name'])+('['+','.join(k+'='+str(v) for k,v in sorted(props.items()))+']' if props else '')
def decode_vars(a):
    out=[];v=shift=0
    for x in a:
        b=int(x)&255;v|=(b&127)<<shift
        if b&128:shift+=7;assert shift<35
        else:out.append(v);v=shift=0
    assert shift==0;return out
def counts_from_blocks(cells,entities,roles):
    c=collections.Counter();terrain=collections.Counter();anchors=collections.Counter();fluids=collections.Counter()
    for q,s in cells.items():
        n,props=split(s);role=roles[q]
        if n in (AIR,VOID):continue
        if role=='anchor':anchors[n]+=1;continue
        if n=='minecraft:water':fluids['water_source_placements']+=1;continue
        if n.endswith('_door') and props['half']=='upper':continue
        n={'minecraft:wall_torch':'minecraft:torch','minecraft:purple_wall_banner':'minecraft:purple_banner','minecraft:farmland':'minecraft:dirt','minecraft:grass_block':'minecraft:dirt'}.get(n,n)
        (terrain if role=='terrain' else c)[n]+=2 if n.endswith('_slab') and props['type']=='double' else 1
    for e in entities:
        n=str(e['Id']);c[n]+=1
        if 'Item' in e:c[str(e['Item']['id'])]+=int(e['Item']['Count'])
    return {'construction_items':dict(c),'terrain_fill_if_not_reused':dict(terrain),'fluid_operations':dict(fluids),'already_placed_anchor_items':dict(anchors)}
def validate(report):
    results=[];allplans={}
    for path in sorted((ROOT/'manifests').glob('*.json')):
        m=json.loads(path.read_text());name=m['name'];n=nbtlib.load(ROOT/'schematics'/f'{name}.schem');v=nbtlib.load(ROOT/'structures'/f'{name}.nbt')
        dims=[int(n[k]) for k in ('Width','Height','Length')];assert dims==m['dimensions'];assert list(map(int,v['size']))==dims
        assert int(n['Version'])==2 and int(n['DataVersion'])==3465==int(v['DataVersion'])
        pal={int(i):s for s,i in n['Palette'].items()};assert sorted(pal)==list(range(len(pal))) and len(pal)==int(n['PaletteMax'])
        ids=decode_vars(n['BlockData']);w,h,d=dims;assert len(ids)==w*h*d
        decoded={(i%w,i//(w*d),(i//w)%d):pal[t] for i,t in enumerate(ids) if pal[t]!=VOID}
        expected={tuple(b['pos']):b['state'] for b in m['blocks']};assert decoded==expected,(name,'manifest vs schematic')
        pp=[statestr(x) for x in v['palette']];native={tuple(map(int,b['pos'])):pp[int(b['state'])] for b in v['blocks']};assert native==decoded,(name,'native vs schematic')
        assert list(map(int,n['Offset']))==[0,0,0]
        offsets=[int(n['Metadata'][k]) for k in ('WEOffsetX','WEOffsetY','WEOffsetZ')]
        assert offsets==[-x for x in m['origin_local']]
        # WorldEdit 7.2: origin = root Offset - WEOffset. The anchor must paste at zero.
        assert [m['origin_local'][i]+offsets[i] for i in range(3)]==[0,0,0]
        logical={tuple(b['logical_pos']):b['state'] for b in m['blocks']};allplans[name]=(m,logical)
        for s in pal.values():
            bid,props=split(s)
            if report and bid.startswith('minecraft:'):
                assert bid in report,(name,bid)
                for k,value in props.items():assert value in report[bid].get('properties',{}).get(k,[]),(name,bid,k,value)
        chests=0
        for q,s in logical.items():
            bid,props=split(s)
            if bid=='minecraft:chest':
                chests+=1;t=props['type'];assert t in ('left','right')
                face=(CW if t=='left' else CCW)[props['facing']];dx,dz=DIR[face]
                other=logical.get((q[0]+dx,q[1],q[2]+dz));assert other
                on,op=split(other);assert on==bid and op['facing']==props['facing'] and op['type']!=t
                above=logical.get((q[0],q[1]+1,q[2]),VOID)
                assert above in (AIR,VOID) or above.startswith('minecraft:chest'),(name,'blocked chest lid',q)
            if bid=='minecraft:ladder':
                dx,dz=DIR[props['facing']];back=logical.get((q[0]-dx,q[1],q[2]-dz),VOID)
                assert back not in (AIR,VOID) and not back.startswith('automatons:'),(name,'ladder backing',q,back)
            if bid.endswith('_door'):
                dy=1 if props['half']=='lower' else -1
                on,op=split(logical[(q[0],q[1]+dy,q[2])]);assert on==bid and op['half']!=props['half']
        for be in n['BlockEntities']:assert not len(be.get('Items',[])) and 'LootTable' not in be
        for b in v['blocks']:
            if 'nbt' in b:assert not len(b['nbt'].get('Items',[]))
        assert len(n['Entities'])==len(v['entities'])==len(m['entities'])
        for e,ve,me in zip(n['Entities'],v['entities'],m['entities']):
            assert str(e['Id'])==str(ve['nbt']['id'])==me['id']
            assert list(map(float,e['Pos']))==list(map(float,ve['pos']))==me['pos']
            assert 'UUID' not in e and 'UUID' not in ve['nbt']
            assert len(e['Rotation'])==2 and len(ve['nbt']['Rotation'])==2
        roles={tuple(b['pos']):b['role'] for b in m['blocks']};assert counts_from_blocks(decoded,n['Entities'],roles)==m['material_bill'],(name,'material mismatch')
        results.append({'plan':name,'dimensions':dims,'explicit_cells':len(decoded),'double_chests':chests//2,'entities':len(n['Entities']),'status':'passed'})
    # Approved, independent coordinate constraints.
    wanted={'crafterton_workshop':4,'hunterton_shed':4,'rancherton_ranch':4,'quarryton_surface':16,'luminariton_tower_courtyard':4,'fisherton_hut_dock':4,'minington_strip_level':8,'minington_landing':8}
    for name,count in wanted.items():assert sum(s.startswith('minecraft:chest') for s in allplans[name][1].values())==count
    m,c=allplans['crafterton_workshop'];assert c[(3,0,1)]=='automatons:totem';assert m['entities'][0]['blockpos']==[1,1,5]
    m,c=allplans['quarryton_surface'];assert m['worksite']['quarry']['footprint_xz']==[[0,10],[14,24]]
    assert not any(0<=x<=14 and 10<=z<=24 for x,y,z in c)
    assert m['features']['shaft_mouth']==[7,0,9]
    m,c=allplans['luminariton_tower_courtyard'];assert c[(12,0,0)]=='automatons:totem'
    assert c[(12,3,1)].startswith('minecraft:ladder') and not any('trapdoor' in s for s in c.values())
    for x in range(9,16):
        for z in range(4,7):
            for y in range(3):assert c[(x,y,z)]==AIR
    beds=m['worksite']['collector_beds'];assert len(beds)==12 and sum(len(b['planting_cells']) for b in beds)==180
    covered=set()
    for b in beds:
        (x,z),(x2,z2)=b['bounds_xz'];assert [x+1,0,z+1]==b['collector'];assert c[tuple(b['collector'])]=='automatons:lumina_collector'
        plot={(xx,zz) for xx in range(x,x2+1) for zz in range(z,z2+1)};assert len(plot)==16 and not plot&covered;covered|=plot
    m,c=allplans['fisherton_hut_dock'];dock=m['worksite']['dock'];assert len(dock['slots'])==6
    for x in range(4,7):
        for z in range(7,15):assert c[(x,-1,z)]=='minecraft:spruce_planks'
    assert m['worksite']['source_water_required']['cells']==70 and not any(s.startswith('minecraft:water') for s in c.values())
    m,c=allplans['farmington_field'];assert c[(4,-1,4)]=='minecraft:water[level=0]' and c[(4,0,4)]=='minecraft:glass'
    assert [len(s['planting_cells']) for s in m['worksite']['crop_strips']]==[27,26,27]
    m,c=allplans['minington_strip_level'];branches=m['worksite']['branches'];assert len(branches)==20
    assert [b['length'] for b in branches]==[24]*4+[25]*16
    assert m['worksite']['next_level_y_offset']==-4
    for b in branches:
        a,z=b['bounds'];assert z[1]-a[1]+1==2 and z[2]==a[2]
    out={'status':'passed','reader':'nbtlib 2.0.4 (independent of exporter)','vanilla_block_report':bool(report),'data_version':3465,'plans':results,'limitations':['No Forge mod repository or client was supplied. Custom registry defaults and owner/job initialization need integration verification.','No in-game AI navigation, lighting simulation, WorldEdit paste, or full client rendering was run.']}
    (ROOT/'VALIDATION.json').write_text(json.dumps(out,indent=2)+'\n');print(json.dumps(out,indent=2))
if __name__=='__main__':
    a=argparse.ArgumentParser();a.add_argument('--block-report',type=Path);args=a.parse_args()
    validate(json.loads(args.block_report.read_text()) if args.block_report else None)
