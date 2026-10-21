"""Create flat frontend/backend deployment archives from the verified security release."""
from pathlib import Path
import hashlib
import io
import json
import stat
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT.parent
DATE = '20261019'
RELEASE = f'imagecreater-prebuilt-{DATE}'
SOURCE = ROOT / f'backups/releases/{RELEASE}.zip'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def read_text(path):
    return path.read_text(encoding='utf-8-sig').replace('\r\n', '\n').encode('utf-8')


def main():
    frontend = f'imagecreater-frontend-dist-{DATE}.zip'
    backend = f'imagecreater-backend-{DATE}.zip'
    doc = f'DEPLOY-SEPARATE-{DATE}.md'
    destinations = [OUT / n for n in [frontend, backend, frontend + '.sha256', backend + '.sha256', doc]]
    assert not any(p.exists() for p in destinations), 'Refusing to overwrite existing output'
    expected = SOURCE.with_suffix('.zip.sha256').read_text().split()[0]
    assert digest(SOURCE.read_bytes()) == expected
    prefix = RELEASE + '/'
    with zipfile.ZipFile(SOURCE) as source:
        assert source.testzip() is None
        front_files = {n[len(prefix + 'dist/'):]: source.read(n)
                       for n in source.namelist() if n.startswith(prefix + 'dist/')}
        backend_files = {
            'app.jar': source.read(prefix + 'backend/app.jar'),
            'healthcheck.sh': source.read(prefix + 'backend/healthcheck.sh'),
            'Dockerfile': read_text(ROOT / 'deploy/prebuilt/Dockerfile.backend-update'),
            'sql/security-20261019.sql': source.read(prefix + 'deploy/updates/security-20261019.sql'),
            'ACCOUNT-SECURITY-20261019.md': source.read(prefix + 'docs/fix-account-security-20261019.md'),
            'DEPLOY-SEPARATE-20261019.md': read_text(ROOT / 'docs/deploy-separate-20261019.md'),
            'BUILD-INFO.json': source.read(prefix + 'BUILD-INFO.json'),
        }
    info = json.loads(backend_files['BUILD-INFO.json'])
    info.update(security_upgrade='ACCOUNT-SECURITY-20261019.md', incremental_sql='sql/security-20261019.sql',
                package_layout='Flat backend archive; extract into existing backend directory',
                server_build='No source compilation. Existing source Compose builds a runtime image using the supplied JAR; bind-mounted prebuilt Compose only recreates backend.',
                deployment_guide='DEPLOY-SEPARATE-20261019.md')
    backend_files['BUILD-INFO.json'] = (json.dumps(info, ensure_ascii=False, indent=2) + '\n').encode()
    backend_files['BACKEND-20261019-MANIFEST.sha256'] = ''.join(
        f'{digest(data)}  {name}\n' for name, data in sorted(backend_files.items())).encode()
    for name, files in [(frontend, front_files), (backend, backend_files)]:
        assert all(not n.startswith(('/', '../')) and '\\' not in n and '..' not in Path(n).parts for n in files)
        dest = OUT / name
        with zipfile.ZipFile(dest, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            for entry_name, data in sorted(files.items()):
                entry = zipfile.ZipInfo(entry_name)
                entry.create_system = 3
                mode = 0o755 if entry_name.endswith('.sh') else 0o644
                entry.external_attr = (stat.S_IFREG | mode) << 16
                entry.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(entry, data)
        with zipfile.ZipFile(dest) as archive:
            assert archive.testzip() is None
            assert set(archive.namelist()) == set(files)
            assert all(archive.read(n) == data for n, data in files.items())
        sha = digest(dest.read_bytes())
        dest.with_suffix('.zip.sha256').write_text(f'{sha}  {name}\n', encoding='ascii', newline='\n')
        print(json.dumps(dict(archive=str(dest), bytes=dest.stat().st_size, files=len(files), sha256=sha)))
    assert front_files['index.html'] == (ROOT / 'dist/index.html').read_bytes()
    with zipfile.ZipFile(io.BytesIO(backend_files['app.jar'])) as jar:
        assert jar.testzip() is None
        assert all(Path(n).name not in {'application-dev.yml', 'application-prod.yml'} for n in jar.namelist())
        assert all(i.compress_type == zipfile.ZIP_STORED for i in jar.infolist()
                   if i.filename.startswith('BOOT-INF/lib/') and i.filename.endswith('.jar'))
    (OUT / doc).write_bytes(read_text(ROOT / 'docs/deploy-separate-20261019.md'))
    print('PASS: flat archive paths, CRC, exact payload parity, checksum, sanitized JAR and Spring Boot nested dependencies')


if __name__ == '__main__':
    main()
