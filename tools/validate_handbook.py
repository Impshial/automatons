"""Validate guide-root navigation, relative links, IDs, scenes and authoritative artwork."""
from pathlib import Path
import argparse, gzip, hashlib, json, re
from import_mine_arch import read_nbt

root=Path(__file__).resolve().parents[1]
res=root/'src/main/resources'
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--pack', type=Path, default=root/'resourcepacks/Automatons-Guide')
parser.add_argument('--baseline', action='store_true', help='Check the original release content counts too')
args=parser.parse_args()
pack=args.pack.resolve()
assert json.loads((pack/'pack.mcmeta').read_text(encoding='utf-8'))['pack']['pack_format']==15
book=pack/'assets/automatons/guides/automatons/handbook'
pages={p.relative_to(book).as_posix():p for p in book.rglob('*.md')}
assert 'index.md' in pages, 'Missing handbook start page'
parents={};indexed={};recipes=set();structures=set()
for name,p in pages.items():
    text=p.read_text(encoding='utf-8')
    assert text.startswith('---\n'),name
    front=text.split('---',2)[1]
    parent=re.search(r'parent: "([^"]+)"',front)
    if parent:
        parents[name]=parent[1];assert parent[1] in pages,(name,parent[1])
    assert re.search(r'position: \d+',front),name
    for item in re.findall(r'^  - "(automatons:[^"]+)"',front,re.M):
        assert item not in indexed,(item,name,indexed.get(item));indexed[item]=name
        assert (res/f'assets/automatons/models/item/{item.split(":")[1]}.json').exists(),item
    for target in re.findall(r'\]\(([^)]+)\)',text):
        assert not target.startswith(('http:','https:')),(name,'offline guide external link',target)
        path,_,anchor=target.partition('#');dest=(p.parent/path).resolve() if path else p
        assert dest.is_file(),(name,target)
        if anchor: assert f'<a name="{anchor}" />' in dest.read_text(encoding='utf-8'),(name,target)
    for item in re.findall(r'<(?:ItemImage|ItemLink|RecipeFor|RecipesFor)\s+id="automatons:([^"]+)"',text):
        assert (res/f'assets/automatons/models/item/{item}.json').exists(),(name,item)
    for recipe in re.findall(r'<Recipe\s+id="automatons:([^"]+)"',text):
        assert (res/f'data/automatons/recipes/{recipe}.json').exists(),(name,recipe);recipes.add(recipe)
    for source in re.findall(r'<ImportStructure\s+src="([^"]+)"',text):
        asset=(p.parent/source).resolve();assert asset.is_file(),(name,source)
        structure=read_nbt(gzip.decompress(asset.read_bytes()))
        assert structure['DataVersion']==3465 and structure['blocks'] and not structure['entities'],source
        structures.add(asset)
for name in parents:
    seen=set();at=name
    while at in parents:
        assert at not in seen,('navigation cycle',at);seen.add(at);at=parents[at]
ore_art=json.loads((root/'reference/current-run/ore-emissive-pixels.json').read_text())
for name in ['lumite_ore_stone_texture','lumite_ore_deepslate_texture']:
    entry=ore_art[name]
    source=root/entry.get('source',f'reference/current-run/{name}.png')
    target=res/f'assets/automatons/textures/block/{name}.png'
    assert source.read_bytes()==target.read_bytes(),('Ore differs from authoritative artwork',name)
    assert hashlib.sha256(target.read_bytes()).hexdigest()==entry['sha256'],name
manifest=json.loads((book/'assets/structures/source-manifest.json').read_text())
for entry in manifest.values():assert hashlib.sha256((root/entry['source']).read_bytes()).hexdigest()==entry['sha256']
if args.baseline:
    assert len(pages)==30 and len(recipes)==19 and len(indexed)==24 and len(structures)==7,(len(pages),len(recipes),len(indexed),len(structures))
else:
    for name in ['armored_head', 'armored_torso', 'armored_arm', 'armored_leg', 'dead_plant', 'dead_crop', 'gravestone']:
        assert f'automatons:{name}' in indexed, ('Missing expansion item association', name)
    assert 'guardington' in recipes, 'Missing live armored assembly recipe'
print(f'PASS: {len(pages)} pages; {len(recipes)} live recipes; {len(indexed)} unique item associations; {len(structures)} valid vanilla structures; all links/anchors/navigation and authoritative PNG bytes.')
