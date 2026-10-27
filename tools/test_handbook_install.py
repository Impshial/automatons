"""Exercise upgrades against an editable guide and existing Minecraft options."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import install_handbook_pack as installer


class HandbookInstallTests(unittest.TestCase):
    def setUp(self):
        build = (Path(__file__).resolve().parents[1] / 'build').resolve()
        build.mkdir(exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(prefix='handbook-install-', dir=build)
        self.root = Path(self.temp.name).resolve()
        assert self.root.is_relative_to(build)
        self.addCleanup(self.temp.cleanup)
        self.source = self.root / 'source'
        self.instance = self.root / 'instance'
        self.pack = self.instance / 'resourcepacks' / installer.PACK_NAME
        self.source.mkdir()
        self.instance.mkdir()
        (self.source / 'pack.mcmeta').write_text('{"pack":{"pack_format":15}}', encoding='utf-8')
        self.page = installer.GUIDE_PATH / 'index.md'
        self.default(self.page, 'Original handbook')
        self.counter = 0

    def default(self, relative, text):
        target = self.source / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding='utf-8')

    def install(self):
        self.counter += 1
        return installer.install(self.source, self.instance, self.instance / 'backups' / str(self.counter))

    def test_first_install_enables_pack_and_preserves_other_options(self):
        options = self.instance / 'options.txt'
        original = b'gamma:0.75\r\nresourcePacks:["vanilla","file/My Pack"]\r\nfov:90\r\n'
        options.write_bytes(original)
        self.install()
        self.assertEqual((self.pack / self.page).read_text(encoding='utf-8'), 'Original handbook')
        self.assertEqual(options.read_bytes(), original.replace(b'"file/My Pack"]', b'"file/My Pack","file/Automatons-Guide"]'))
        self.assertEqual((self.instance / 'backups/1/options.txt').read_bytes(), original)
        self.assertEqual(self.install()['changed_files'], 0)

    def test_upgrade_keeps_edits_deletions_and_custom_pages(self):
        deleted = installer.GUIDE_PATH / 'deleted.md'
        unchanged = installer.GUIDE_PATH / 'unchanged.md'
        added = installer.GUIDE_PATH / 'added.md'
        custom = installer.GUIDE_PATH / 'custom.md'
        self.default(deleted, 'Delete me')
        self.default(unchanged, 'Old default')
        self.install()
        (self.pack / self.page).write_text('My custom guide', encoding='utf-8')
        (self.pack / deleted).unlink()
        (self.pack / custom).write_text('My extra page', encoding='utf-8')
        self.default(self.page, 'New default guide')
        self.default(deleted, 'New deleted default')
        self.default(unchanged, 'Updated default')
        self.default(added, 'New chapter')
        result = self.install()
        self.assertEqual((self.pack / self.page).read_text(encoding='utf-8'), 'My custom guide')
        self.assertFalse((self.pack / deleted).exists())
        self.assertEqual((self.pack / custom).read_text(encoding='utf-8'), 'My extra page')
        self.assertEqual((self.pack / unchanged).read_text(encoding='utf-8'), 'Updated default')
        self.assertEqual((self.pack / added).read_text(encoding='utf-8'), 'New chapter')
        self.assertEqual(set(result['preserved_edits']), {self.page.as_posix(), deleted.as_posix()})
        self.assertEqual(self.install()['changed_files'], 0)

    def test_unmanaged_existing_page_is_preserved(self):
        target = self.pack / self.page
        target.parent.mkdir(parents=True)
        target.write_text('Existing user guide', encoding='utf-8')
        self.install()
        self.assertEqual(target.read_text(encoding='utf-8'), 'Existing user guide')

    def test_bad_options_fail_before_install_changes(self):
        options = self.instance / 'options.txt'
        options.write_bytes(b'resourcePacks:not json\n')
        with self.assertRaises(json.JSONDecodeError):
            self.install()
        self.assertFalse(self.pack.exists())
        self.assertEqual(options.read_bytes(), b'resourcePacks:not json\n')

    def test_partial_write_failure_restores_previous_install(self):
        self.install()
        original = {p.relative_to(self.pack): p.read_bytes() for p in self.pack.rglob('*') if p.is_file()}
        self.default(self.page, 'Updated guide')
        self.default(installer.GUIDE_PATH / 'new.md', 'Another new page')
        real_write = installer.write_atomic
        calls = 0

        def failing_write(path, data):
            nonlocal calls
            calls += 1
            if calls == 2:
                raise OSError('Simulated disk failure')
            real_write(path, data)

        with patch.object(installer, 'write_atomic', side_effect=failing_write):
            with self.assertRaises(OSError):
                self.install()
        after = {p.relative_to(self.pack): p.read_bytes() for p in self.pack.rglob('*') if p.is_file()}
        self.assertEqual(after, original)


if __name__ == '__main__':
    unittest.main()
