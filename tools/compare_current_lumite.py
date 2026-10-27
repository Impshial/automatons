"""Read-only comparison of actual saved ore and face-connected clusters, same seeded chunks."""
from pathlib import Path
import collections, gzip, hashlib, json, math, struct, zlib
from import_mine_arch import read_nbt

ROOT=Path(__file__).resolve().parents[1]/'build/lumina-verification'
SAMPLES=[(0,0),(4,4),(-4,4),(4,-4),(-4,-4),(12,0),(0,12),(-12,0),(0,-12),(12,12),(-12,-12),(12,-12)]

def chunk(world,x,z):
    file=world/f'region/r.{x//32}.{z//32}.mca'
    with file.open('rb') as stream:
        stream.seek(((x%32)+(z%32)*32)*4);location=int.from_bytes(stream.read(4),'big');assert location
        stream.seek((location>>8)*4096);length=int.from_bytes(stream.read(4),'big');compression=stream.read(1)[0];data=stream.read(length-1)
        return read_nbt(zlib.decompress(data) if compression==2 else gzip.decompress(data) if compression==1 else data)

def inspect(world):
    out=[]
    for x,z in SAMPLES:
        data=chunk(world,x,z);ores={};states=hashlib.sha256()
        for section in sorted(data['sections'],key=lambda s:s['Y']):
            if 'block_states' not in section:continue
            palette=section['block_states']['palette'];packed=section['block_states'].get('data',[])
            bits=max(4,(len(palette)-1).bit_length());per=64//bits
            palette_hash=[json.dumps(p,sort_keys=True).encode() for p in palette]
            for i in range(4096):
                index=((packed[i//per] & ((1<<64)-1)) >> ((i%per)*bits)) & ((1<<bits)-1) if packed else 0
                state=palette[index]['Name'];states.update(palette_hash[index]);states.update(b'\0')
                if state in ('automatons:lumite_ore','automatons:deepslate_lumite_ore'):
                    pos=(x*16+i%16,section['Y']*16+i//256,z*16+(i//16)%16);ores[pos]=state
                    assert -32<=pos[1]<=64,pos
        remaining=set(ores);clusters=[]
        while remaining:
            queue=[next(iter(remaining))];size=0
            while queue:
                p=queue.pop()
                if p not in remaining:continue
                remaining.remove(p);size+=1
                for axis in range(3):
                    for d in [-1,1]:
                        q=list(p);q[axis]+=d;queue.append(tuple(q))
            clusters.append(size)
        out.append({'chunk':[x,z],'ore':len(ores),'variants':dict(collections.Counter(ores.values())), 'clusters':sorted(clusters),'blockStateHash':states.hexdigest()})
    return out

report={'method':'Saved actual block states; face-connected clusters counted within each sampled chunk (edge-touching clusters may be split). Same 12 coordinates per seed. No fixed abundance percentage is promised.','seeds':[]}
for old,new in [('seed-4217','current8-4217'),('seed-8675309','current8-8675309'),('seed-minus60241024','current8-minus60241024')]:
    before=inspect(ROOT/old/'world');after=inspect(ROOT/new/'world')
    entry={'baseline':old,'size6':before,'size8':after,'oreBefore':sum(c['ore'] for c in before),'oreAfter':sum(c['ore'] for c in after),'clustersBefore':sum(len(c['clusters']) for c in before),'clustersAfter':sum(len(c['clusters']) for c in after)}
    report['seeds'].append(entry)
before=inspect(ROOT/'seed-4217/world');after=inspect(ROOT/'current-old-save/world')
assert before==after,'Loading the copied existing save changed sampled terrain'
old_history=(ROOT/'current-old-save/pending-history-before.nbt').read_bytes()
new_history=gzip.decompress((ROOT/'current-old-save/world/data/automatons_lumite_retrogen.dat').read_bytes())
assert read_nbt(old_history)==read_nbt(new_history),'Pending history changed'
report['existingSampleChunksUnchanged']=len(before);report['pendingHistoryUnchanged']=True
(ROOT/'current-comparison.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:v for k,v in report.items() if k!='seeds'}))
for e in report['seeds']:print(e['baseline'],e['oreBefore'],'->',e['oreAfter'],'ore;',e['clustersBefore'],'->',e['clustersAfter'],'clusters')
