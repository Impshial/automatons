"""Prepare current new-chunk samples; existing baseline worlds are never reset or migrated."""
from pathlib import Path
import shutil,json
root=Path(__file__).resolve().parents[1]/'build/lumina-verification'
root.mkdir(parents=True,exist_ok=True)
for label,seed in [('current8-4217',4217),('current8-8675309',8675309),('current8-minus60241024',-60241024)]:
    p=root/label;p.mkdir(exist_ok=True)
    (p/'eula.txt').write_text('eula=true\n',encoding='utf-8')
    (p/'server.properties').write_text(f'online-mode=false\nserver-ip=127.0.0.1\nserver-port=25576\nlevel-name=world\nlevel-seed={seed}\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nmax-tick-time=180000\nallow-flight=true\n',encoding='utf-8')
print(root)
