from pathlib import Path
import json,zipfile,collections
root=Path(__file__).resolve().parents[1]
oldjar=Path('F:/Minecraft/Automatons/releases/automatons-0.5.0.jar')
with zipfile.ZipFile(oldjar) as z:
    old={Path(n).stem:json.loads(z.read(n)) for n in z.namelist() if n.startswith('data/automatons/recipes/') and n.endswith('.json')}
new={p.stem:json.loads(p.read_text(encoding='utf-8')) for p in (root/'src/main/resources/data/automatons/recipes').glob('*.json')}
lang=json.loads((root/'src/main/resources/assets/automatons/lang/en_us.json').read_text(encoding='utf-8'))
def name(id):
    if id.startswith('#'):return id[1:].split(':')[1].replace('_',' ')+' tag'
    ns,key=id.split(':')
    return lang.get('item.'+ns+'.'+key,lang.get('block.'+ns+'.'+key,key.replace('_',' ').title())).removeprefix('Retired ')
def inputs(r):
    if r is None:return 'Not craftable'
    entries=r.get('components',r.get('ingredients',[]))
    if 'pattern' in r:entries=[r['key'][c] for row in r['pattern'] for c in row if c!=' ']
    counts=collections.Counter(e.get('item','#'+e.get('tag','unknown')) for e in entries)
    return '; '.join(f'{v} {name(k)}' for k,v in counts.items())
rows=['# Recipe changes — 0.5.0 → 0.6.0','','Compared against the archived 0.5.0 JAR and current recipe JSON. All outputs are one item; the Automaton uses pedestal assembly. The old completed core was called Magica Core; its unchanged registry now displays Lumina Core. Layouts of surviving existing recipes are unchanged.','','| Output | 0.5.0 ingredients | 0.6.0 ingredients |','| --- | --- | --- |']
for key in sorted(set(old)|set(new)):
    before=inputs(old.get(key));after=inputs(new.get(key))
    if key=='magica_core':before='World infusion: one Empty Core + 50 Magica'
    rows.append('| '+name('automatons:'+key)+' | '+before+' | '+after+' |')
rows+=['','The new Lumina Core uses the exact shaped cross: §.I. / IEI / .I.§ (I = Infused Lumite Shard; E = Empty Core). Its four shards contain 100 Lumina already paid. The Empty Core accepts raw uncharged shards; the infuser uses a diamond. Partial/legacy-credit items cannot disappear into these uncharged ingredients.','','All eleven upgrade-only outputs are removed. The ten upgrades were the plate’s only consumers. No surviving other amethyst ingredient remained after those removals, so no additional infused-shard substitutions were necessary.','','Source comparison: §tools/recipe_report.py§.']
(root/'docs/RECIPE-CHANGES-0.6.md').write_text(('\n'.join(rows)+'\n').replace('§',chr(96)),encoding='utf-8')
print('Compared',len(old),'old and',len(new),'new recipes.')

