"""Copy the user's authoritative shard PNGs verbatim, without image processing."""
from pathlib import Path
import hashlib
import json
import struct

ROOT = Path(__file__).resolve().parents[1]


def import_textures():
    source = ROOT / 'reference/shards-0.7.3'
    target = ROOT / 'src/main/resources/assets/automatons/textures/item'
    manifest = json.loads((source / 'manifest.json').read_text(encoding='utf-8'))
    pending = []
    for name, entry in manifest.items():
        data = (source / name).read_bytes()
        assert data[:8] == b'\x89PNG\r\n\x1a\n', name
        assert struct.unpack('>II', data[16:24]) == (16, 16), name
        assert hashlib.sha256(data).hexdigest() == entry['sha256'], name
        pending.append((target / entry['target'], data))
    target.mkdir(parents=True, exist_ok=True)
    for path, data in pending:
        if not path.exists() or path.read_bytes() != data:
            path.write_bytes(data)
    print('Preserved both supplied 16x16 shard PNGs byte-for-byte.')


if __name__ == '__main__':
    import_textures()
