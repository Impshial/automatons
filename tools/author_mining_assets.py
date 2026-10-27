"""Reproducible mining sprites and data; leaves previous assets untouched."""
from pathlib import Path
import json
import struct
import zlib

ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
def write(path, value):
    p=ROOT/path
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
def png(path, pixels):
    def chunk(kind,data):
        return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    data=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))
    data+=chunk(b'IDAT',zlib.compress(b''.join(b'\0'+bytes(c for p in row for c in p) for row in pixels)))+chunk(b'IEND',b'')
    (ROOT/path).write_bytes(data)

lang_path=ROOT/'assets/automatons/lang/en_us.json'
lang=json.loads(lang_path.read_text(encoding='utf-8-sig'))
for tier in (1,2):
    name=f'mining_{tier}'
    pixels=[[(0,0,0,0) for _ in range(16)] for _ in range(16)]
    for y in range(2,14):
        for x in range(2,14):
            pixels[y][x]=(52,45,60,255) if min(x,y,15-x,15-y)==2 else (122,115,131,255)
    for y in range(4,12):
        for x in range(4,12):
            if abs(x-7.5)+abs(y-7.5)<=4:pixels[y][x]=(231,170,56,255)
    for y in range(5,11):
        pixels[y][7]=(245,239,255,255) if y<8 else (86,59,113,255)
    for i in range(tier):
        for y in range(11,14):pixels[y][10+i*2]=(224,201,255,255)
    png(Path(f'assets/automatons/textures/item/{name}.png'),pixels)
    write(Path(f'assets/automatons/models/item/{name}.json'),{'parent':'minecraft:item/generated','textures':{'layer0':f'automatons:item/{name}'}})
    lang[f'item.automatons.{name}']='Retired Mining Range '+('I' if tier==1 else 'II')
    lang.pop(f'tooltip.automatons.{name}',None)
write(Path('assets/automatons/lang/en_us.json'),lang)
print('Authored inert legacy Mining Range item art; no recipes or unlocks.')
