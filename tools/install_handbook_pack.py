"""Install and enable the unpacked handbook without overwriting local edits."""
import argparse
import hashlib
import json
from pathlib import Path

PACK_NAME = 'Automatons-Guide'
GUIDE_PATH = Path('assets/automatons/guides/automatons/handbook')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def enable_pack(data):
    """Change only the resourcePacks option, retaining existing selections/order."""
    bom = data.startswith(b'\xef\xbb\xbf')
    text = data.decode('utf-8-sig')
    newline = '\r\n' if '\r\n' in text else '\n'
    lines = text.splitlines(keepends=True)
    indices = [i for i, line in enumerate(lines) if line.startswith('resourcePacks:')]
    if len(indices) > 1:
        raise ValueError('Duplicate resourcePacks option; no files changed')
    selected = json.loads(lines[indices[0]].partition(':')[2]) if indices else []
    if not isinstance(selected, list) or not all(isinstance(item, str) for item in selected):
        raise ValueError('Invalid resourcePacks option; no files changed')
    pack_id = 'file/' + PACK_NAME
    if pack_id in selected:
        return data
    selected.append(pack_id)
    line = 'resourcePacks:' + json.dumps(selected, ensure_ascii=False, separators=(',', ':')) + newline
    if indices:
        lines[indices[0]] = line
    else:
        if lines and not lines[-1].endswith(('\r', '\n')):
            lines[-1] += newline
        lines.append(line)
    return (b'\xef\xbb\xbf' if bom else b'') + ''.join(lines).encode('utf-8')


def write_atomic(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive creation avoids overwriting an unrelated pending file.
    temporary = path.with_name(path.name + '.automatons-pending')
    stream = temporary.open('xb')
    try:
        with stream:
            stream.write(data)
        temporary.replace(path)
    finally:
        if temporary.exists():
            temporary.unlink()


def install(source, instance, backup):
    source, instance, backup = source.resolve(), instance.resolve(), backup.resolve()
    target = (instance / 'resourcepacks' / PACK_NAME).resolve()
    if not target.is_relative_to(instance) or not backup.is_relative_to(instance):
        raise ValueError('Pack and backup must stay inside the selected instance')
    if source == target or backup.is_relative_to(target):
        raise ValueError('Source, installed pack and backup must be separate')
    metadata = json.loads((source / 'pack.mcmeta').read_text(encoding='utf-8'))
    if metadata['pack']['pack_format'] != 15 or not (source / GUIDE_PATH / 'index.md').is_file():
        raise ValueError('Expected a complete Minecraft 1.20.1 handbook pack')
    state_path = instance / '.automatons-guide-defaults.json'
    previous = json.loads(state_path.read_text(encoding='utf-8')) if state_path.exists() else {}
    if previous and previous.get('format') != 1:
        raise ValueError('Unknown handbook installation record; no files changed')
    hashes = dict(previous.get('files', {}))
    writes, preserved = {}, []
    for origin in sorted(source.rglob('*')):
        if not origin.is_file():
            continue
        if not origin.resolve().is_relative_to(source):
            raise ValueError('Source pack contains a file outside its folder')
        relative = origin.relative_to(source).as_posix()
        destination = (target / relative).resolve()
        if not destination.is_relative_to(target):
            raise ValueError('Installed pack contains a path outside its folder')
        incoming = origin.read_bytes()
        current = destination.read_bytes() if destination.exists() else None
        old_hash = hashes.get(relative)
        if current == incoming:
            hashes[relative] = digest(incoming)
        elif (current is None and old_hash is None) or (current is not None and digest(current) == old_hash):
            writes[destination] = incoming
            hashes[relative] = digest(incoming)
        else:
            # Keep custom text, assets and deliberate deletions across releases.
            preserved.append(relative)
    options = instance / 'options.txt'
    writes[options] = enable_pack(options.read_bytes() if options.exists() else b'')
    writes[state_path] = (json.dumps({'format': 1, 'files': hashes}, indent=2) + '\n').encode('utf-8')
    originals = {}
    for path, data in writes.items():
        if not path.resolve().is_relative_to(instance):
            raise ValueError('Write outside the selected instance')
        original = path.read_bytes() if path.exists() else None
        if original != data:
            originals[path] = original
    # Back up all changes before writing any installed file.
    for path, original in originals.items():
        if original is not None:
            saved = backup / path.relative_to(instance)
            saved.parent.mkdir(parents=True, exist_ok=True)
            with saved.open('xb') as stream:
                stream.write(original)
    applied = []
    try:
        for path in originals:
            write_atomic(path, writes[path])
            applied.append(path)
    except Exception:
        for path in reversed(applied):
            if originals[path] is None:
                path.unlink()
            else:
                write_atomic(path, originals[path])
        raise
    return {'pack': str(target), 'changed_files': len(originals), 'preserved_edits': preserved}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--instance', type=Path, required=True)
    parser.add_argument('--backup', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(install(args.source, args.instance, args.backup), indent=2))
