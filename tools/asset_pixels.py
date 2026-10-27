"""Editable pixel-grid assets for Automatons. No resampling or external dependencies.
Every model unit equals 1/16 block, every UV unit is one source texel.
"""
from pathlib import Path
import json, struct, zlib, uuid, base64, sys
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
ART=ROOT/'art'
def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    atomic_write(path, (json.dumps(data,indent=2)+'\n').encode('utf-8'))

def atomic_write(path, data):
    # Asset previews/indexers may memory-map an existing file on Windows. Publish a
    # complete replacement instead of truncating a file while a reader uses it.
    temporary = path.with_name(path.name+'.'+uuid.uuid4().hex+'.tmp')
    try:
        temporary.write_bytes(data)
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)
def chunk(kind,data):return struct.pack('!I',len(data))+kind+data+struct.pack('!I',zlib.crc32(kind+data)&0xffffffff)
def png(path,pixels):
    path.parent.mkdir(parents=True,exist_ok=True);h=len(pixels);w=len(pixels[0]);raw=b''.join(b'\x00'+b''.join(bytes(c) for c in row) for row in pixels)
    atomic_write(path,b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
def rgb(s):return tuple(bytes.fromhex(s))+(255,)
wood=list(map(rgb,['6a4829','795431','906738','a77b44','b78b50','c2985b']))
metal=list(map(rgb,['555858','6b6e6b','8c8f88','b3b4a8']))
gold=list(map(rgb,['5c3717','b77615','efaa21','ffd554']))
purple=list(map(rgb,['5b3c75','8664aa','af8dcd','dbc0ec']))
def canvas(w=16,h=16):return [[(0,0,0,0) for _ in range(w)] for _ in range(h)]
def rect(p,x,y,w,h,c):
    for yy in range(y,y+h):
        for xx in range(x,x+w):
            if 0<=yy<len(p) and 0<=xx<len(p[0]):p[yy][xx]=c

def material(p,x,y,w,h,kind='plank'):
    for yy in range(h):
        for xx in range(w):
            grain=((xx//3)*7+(yy//2)*3)%3
            if kind=='iron': c=metal[3 if yy%8==0 else 1 if yy%8==7 else 2]
            elif kind=='end':c=wood[(min(xx,yy,w-xx-1,h-yy-1)%3)+2]
            elif kind=='bark':c=wood[1+(xx//2+yy//5)%3]
            else:c=wood[1 if yy%4==3 else 3+(xx//4+yy//4)%3]
            p[y+yy][x+xx]=c
