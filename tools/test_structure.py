import gzip,struct
from pathlib import Path
def text(s):b=s.encode();return struct.pack('>H',len(b))+b
def tag(t,n,p):return bytes([t])+text(n)+p
def integer(n):return struct.pack('>i',n)
def lst(t,items):return bytes([t])+integer(len(items))+b''.join(items)
nbt=tag(10,'',tag(3,'DataVersion',integer(3465))+tag(9,'size',lst(3,[integer(28),integer(12),integer(28)]))+tag(9,'palette',lst(10,[tag(8,'Name',text('minecraft:air'))+b'\x00']))+tag(9,'blocks',lst(10,[]))+tag(9,'entities',lst(10,[]))+b'\x00')
p=Path('src/main/resources/data/automatons/structures/empty.nbt');p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(gzip.compress(nbt))
