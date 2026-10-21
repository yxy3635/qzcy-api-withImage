"""Verify archive and migration failure guards without touching a real Docker daemon."""
from pathlib import Path
import hashlib
import io
import os
import shutil
import struct
import subprocess
import sys
import tarfile
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RELEASE = ROOT / 'backups/releases/imagecreater-prebuilt-20261018'
BASH = shutil.which('bash') if os.name != 'nt' else 'C:/Program Files/Git/bin/bash.exe'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def verify_archive():
    archive = RELEASE.with_suffix('.zip')
    expected = archive.with_suffix('.zip.sha256').read_text().split()[0]
    assert sha(archive.read_bytes()) == expected
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None
        prefix = RELEASE.name + '/'
        names = {p[len(prefix):] for p in z.namelist()}
        assert '.env' not in names
        assert not any(p.startswith(('src/', 'node_modules/', 'backend/src/')) for p in names)
        manifest = z.read(prefix + 'MANIFEST.sha256')
        assert b'\r' not in manifest
        for line in manifest.decode().splitlines():
            digest, name = line.split('  ', 1)
            assert sha(z.read(prefix + name)) == digest, name
        for name in names:
            if name.endswith(('.sh', '.yaml', '.yml')):
                assert b'\r' not in z.read(prefix + name), name
        with zipfile.ZipFile(io.BytesIO(z.read(prefix + 'backend/app.jar'))) as jar:
            configs = [p for p in jar.namelist() if '/application-' in p and p.endswith(('.yml', '.yaml'))]
            assert configs == ['BOOT-INF/classes/application-docker.yml'], configs
            count = 0
            for name in jar.namelist():
                if name.startswith('BOOT-INF/classes/') and name.endswith('.class'):
                    assert struct.unpack('>H', jar.read(name)[6:8])[0] == 61, name
                    count += 1
            assert count > 200
            assert all(i.compress_type == zipfile.ZIP_STORED for i in jar.infolist() if i.filename.startswith('BOOT-INF/lib/') and i.filename.endswith('.jar'))
    print(f'PASS: ZIP hash, manifest, LF endings, no source/secrets/dev config, {count} Java 17 classes, nested JAR layout')


MOCK = r'''#!/usr/bin/env bash
set -eu
printf '%s\n' "$*" >> "$MOCK_LOG"
if [[ "$*" == 'volume inspect '* ]]; then
  [[ "${MOCK_EXISTING:-0}" == 1 ]]
  exit $?
fi
if [[ "$*" == *' exec -T mysql '* ]]; then
  cat >/dev/null
  exit "${MOCK_DB_EXIT:-0}"
fi
if [[ "$*" == *' run --rm -T --no-deps --user 0:0 '* ]]; then
  cat >/dev/null
  exit "${MOCK_IMAGE_EXIT:-0}"
fi
exit 0
'''


def verify_flow(name, action='migrate', *, existing=False, db_fail=False, image_fail=False, marker=False):
    with tempfile.TemporaryDirectory(prefix='imagecreater-migration-') as folder:
        root = Path(folder)
        (root / 'deploy.sh').write_bytes((RELEASE / 'deploy.sh').read_bytes())
        (root / '.env').write_bytes(b'SITE_URL=http://example.invalid\nJWT_SECRET=unit-test-only\n')
        original_env = (root / '.env').read_bytes()
        (root / 'MANIFEST.sha256').write_bytes((sha((root / 'deploy.sh').read_bytes()) + '  deploy.sh\n').encode())
        (root / 'backups').mkdir()
        (root / 'backups/database.sql').write_bytes(b'SELECT 1;\n')
        with tarfile.open(root / 'backups/images.tar.gz', 'w:gz') as tar:
            info = tarfile.TarInfo('example.txt')
            info.size = 4
            tar.addfile(info, io.BytesIO(b'test'))
        (root / 'qa-bin').mkdir()
        (root / 'qa-bin/docker').write_bytes(MOCK.encode())
        (root / 'qa-bin/docker').chmod(0o755)
        if marker:
            (root / '.migration-in-progress').touch()
        env = os.environ.copy()
        env.update(MOCK_EXISTING=str(int(existing)), MOCK_DB_EXIT=str(9 if db_fail else 0), MOCK_IMAGE_EXIT=str(10 if image_fail else 0))
        result = subprocess.run([BASH, '-c', 'export PATH="$PWD/qa-bin:$PATH"; export MOCK_LOG="$PWD/docker-calls.log"; bash deploy.sh "$1"', '_', action], cwd=root, env=env, capture_output=True)
        calls = (root / 'docker-calls.log').read_text() if (root / 'docker-calls.log').exists() else ''
        assert (root / '.env').read_bytes() == original_env
        starts_app = 'up -d --wait --wait-timeout 600\n' in calls
        if existing or db_fail or image_fail or marker:
            assert result.returncode != 0, (name, result.stdout, result.stderr)
            assert not starts_app, calls
            if existing:
                assert ' exec ' not in calls and ' run ' not in calls
            if db_fail:
                assert ' run ' not in calls
            if db_fail or image_fail:
                assert (root / '.migration-in-progress').exists()
        else:
            assert result.returncode == 0, (name, result.stdout, result.stderr)
            assert starts_app, calls
            assert calls.index('mysql redis') < calls.index(' exec ') < calls.index(' run ') < calls.index('up -d --wait --wait-timeout 600\n'), calls
            assert (root / '.migration-complete').exists()
            assert not (root / '.migration-in-progress').exists()
        print('PASS: ' + name)


if __name__ == '__main__':
    verify_archive()
    verify_flow('migration restores database then images before starting app')
    verify_flow('existing volumes are never overwritten', existing=True)
    verify_flow('database import failure prevents image import and application startup', db_fail=True)
    verify_flow('image import failure prevents application startup', image_fail=True)
    verify_flow('incomplete migration blocks later startup', action='start', marker=True)
