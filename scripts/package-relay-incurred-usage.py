"""Create and verify a flat backend-only release from the tested Java 17 build."""
from pathlib import Path
from datetime import datetime, timezone, timedelta
import hashlib
import io
import json
import stat
import struct
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NAME = 'imagecreater-backend-billing-20261020-v2.zip'
DEST = ROOT.parent / NAME
EXCLUDED_TESTS = {'com.qzcy.backend.BackendApplicationTests',
                  'com.qzcy.backend.service.impl.RelayTokenDeletionIntegrationTest'}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def text_file(path):
    return path.read_text(encoding='utf-8-sig').replace('\r\n', '\n').encode('utf-8')


def main():
    assert not DEST.exists(), f'Refusing to overwrite {DEST}'
    log = (ROOT / 'backend/relay-incurred-usage-package.log').read_text(encoding='utf-8', errors='replace')
    assert 'BUILD SUCCESS' in log
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for report in sorted((ROOT / 'backend/target/surefire-reports').glob('TEST-*.xml')):
        suite = ET.parse(report).getroot()
        if suite.attrib['name'] in EXCLUDED_TESTS:
            continue
        for key in totals:
            totals[key] += int(suite.attrib.get(key, 0))
    assert totals['tests'] > 300 and totals['failures'] == totals['errors'] == 0, totals
    jar_path = ROOT / 'backend/target/backend-0.0.1-SNAPSHOT.jar'
    jar_bytes = io.BytesIO()
    excluded_resources = []
    with zipfile.ZipFile(jar_path) as source, zipfile.ZipFile(jar_bytes, 'w') as target:
        for info in source.infolist():
            if Path(info.filename).name in {'application-dev.yml', 'application-prod.yml',
                                            'application-dev.yaml', 'application-prod.yaml'}:
                excluded_resources.append(info.filename)
                continue
            target.writestr(info, source.read(info.filename))
    files = {
        'app.jar': jar_bytes.getvalue(),
        'Dockerfile': text_file(ROOT / 'deploy/prebuilt/Dockerfile.backend-update'),
        'healthcheck.sh': text_file(ROOT / 'deploy/prebuilt/healthcheck.sh'),
        'BILLING-20261020.md': text_file(ROOT / 'docs/fix-relay-incurred-usage-20261020.md'),
    }
    info = dict(
        built_at=datetime.now(timezone(timedelta(hours=8))).isoformat(timespec='seconds'),
        source='Current working tree including earlier uncommitted project updates',
        admin_bootstrap='Skip initial password validation and default admin creation when any ADMIN exists; preserve existing usernames',
        java='Build and tests Java 17.0.13; class target Java 17; deployment runtime Java 17',
        command='mvnw.cmd -Dtest=*,!BackendApplicationTests,!RelayTokenDeletionIntegrationTest package',
        backend_tests=totals, passed=totals['tests'] - totals['skipped'],
        excluded_tests=sorted(EXCLUDED_TESTS),
        limitations='Two full-application tests require a valid local JWT_SECRET and were excluded. '
                    'Three dedicated MySQL environment tests skipped. No production deployment, '
                    'provider invoice API reconciliation, Docker startup or real MySQL settlement test.',
        excluded_jar_resources=excluded_resources,
        source_jar_sha256=sha(jar_path.read_bytes()), jar_sha256=sha(files['app.jar']),
        guide='BILLING-20261020.md', frontend_changed=False, manual_sql_required=False,
    )
    files['BUILD-INFO.json'] = (json.dumps(info, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    files['BILLING-20261020-MANIFEST.sha256'] = ''.join(
        f'{sha(data)}  {name}\n' for name, data in sorted(files.items())).encode('ascii')
    with zipfile.ZipFile(DEST, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for name, data in sorted(files.items()):
            entry = zipfile.ZipInfo(name)
            entry.create_system = 3
            entry.external_attr = (stat.S_IFREG | (0o755 if name.endswith('.sh') else 0o644)) << 16
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, data)
    with zipfile.ZipFile(DEST) as archive:
        assert archive.testzip() is None
        assert set(archive.namelist()) == set(files)
        for name, data in files.items():
            assert archive.read(name) == data
        for line in archive.read('BILLING-20261020-MANIFEST.sha256').decode().splitlines():
            digest, name = line.split('  ', 1)
            assert sha(archive.read(name)) == digest
    with zipfile.ZipFile(io.BytesIO(files['app.jar'])) as jar:
        assert jar.testzip() is None
        assert 'BOOT-INF/classes/application-docker.yml' in jar.namelist()
        assert not any(Path(n).name in {'application-dev.yml', 'application-prod.yml'} for n in jar.namelist())
        for name in ['service/impl/RelayDispatchServiceImpl.class', 'service/impl/PaymentServiceImpl.class',
                     'service/UpstreamErrors.class', 'mapper/UserMapper.class',
                     'config/AdminAccountInitializer.class']:
            binary = jar.read('BOOT-INF/classes/com/qzcy/backend/' + name)
            assert struct.unpack('>IHH', binary[:8])[2] == 61, name
        assert all(entry.compress_type == zipfile.ZIP_STORED for entry in jar.infolist()
                   if entry.filename.startswith('BOOT-INF/lib/') and entry.filename.endswith('.jar'))
    digest = sha(DEST.read_bytes())
    DEST.with_suffix('.zip.sha256').write_text(f'{digest}  {NAME}\n', encoding='ascii', newline='\n')
    print(json.dumps(dict(archive=str(DEST), bytes=DEST.stat().st_size, sha256=digest,
                          backend_tests=totals, passed=info['passed']), ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
