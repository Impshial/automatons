"""Offline region metadata audit for disposable retrogen evidence; never edits a world."""
from pathlib import Path
import struct,zlib,gzip,json,sys,collections
root=Path(__file__).resolve().parents[1]
source=root/'build/packaged-smoke/20261024-015706/saves/Automatons Lab/region'
target=root/'build/lumina-verification/regen-safe/world/region'
def status(data):
    at=0
    def num(fmt):
        nonlocal at
        v=struct.unpack_from('>'+fmt,data,at)[0];at+=struct.calcsize(fmt);return v
    def string():
        nonlocal at
        n=num('H');v=data[at:at+n].decode('utf-8');at+=n;return v
    def skip(k):
        nonlocal at
        if k in (1,2,3,4,5,6):at+={1:1,2:2,3:4,4:8,5:4,6:8}[k]
        elif k==8:
            n=num('H');at+=n
        elif k in (7,11,12):
            n=num('i');at+=n*{7:1,11:4,12:8}[k]
        elif k==9:
            subtype=num('B');n=num('i')
            for _ in range(n):skip(subtype)
        elif k==10:
            while (kind:=num('B')):string();skip(kind)
        else:raise ValueError(k)
    assert num('B')==10;string()
    while (k:=num('B')):
        name=string()
        if name=='Status':assert k==8;return string()
        skip(k)
    raise ValueError('Missing chunk status')
def scan(directory):
    out={}
    for p in directory.glob('r.*.*.mca'):
        _,rx,rz,_=p.name.split('.');rx=int(rx)*32;rz=int(rz)*32
        with p.open('rb') as f:
            header=f.read(4096)
            for i in range(1024):
                loc=int.from_bytes(header[4*i:4*i+4],'big')
                if not loc:continue
                f.seek((loc>>8)*4096);n=int.from_bytes(f.read(4),'big');kind=f.read(1)[0];data=f.read(n-1)
                if kind==2:data=zlib.decompress(data)
                elif kind==1:data=gzip.decompress(data)
                elif kind!=3:raise ValueError(('Unsupported compression',kind))
                out[(rx+i%32,rz+i//32)]=status(data)
    return out
old=scan(source);new=scan(target)
result={'beforeChunks':len(old),'afterChunks':len(new),'newCoordinates':sorted(set(new)-set(old)),
        'missingCoordinates':sorted(set(old)-set(new)),
        'statusChanges':[{'chunk':k,'before':v,'after':new[k]} for k,v in old.items() if k in new and new[k]!=v],
        'beforeStatuses':dict(collections.Counter(old.values())),'afterStatuses':dict(collections.Counter(new.values()))}
(root/'build/lumina-retrogen-region-audit.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result,indent=2))
assert not result['newCoordinates'] and not result['missingCoordinates'] and not result['statusChanges'], 'Retrogen changed terrain-generation scope/status'
