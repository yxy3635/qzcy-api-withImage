"""Package already-built frontend/backend into a source-free Linux Compose release."""
from datetime import datetime
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import stat
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TEMPLATE = ROOT / 'deploy/prebuilt'
OUT = ROOT / 'backups/releases'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--name', default=f'imagecreater-prebuilt-{datetime.now():%Y%m%d}')
    args = parser.parse_args()
    name = args.name
    if not name.startswith('imagecreater-prebuilt-') or Path(name).name != name or any(c in name for c in '/\\:'):
        raise SystemExit('Invalid release name')
    target = OUT / name
    archive = OUT / f'{name}.zip'
    if target.exists() or archive.exists():
        raise SystemExit(f'Refusing to overwrite an existing release: {target}')
    jar = ROOT / 'backend/target/backend-0.0.1-SNAPSHOT.jar'
    assert jar.is_file() and (ROOT / 'dist/index.html').is_file(), 'Build frontend/backend first'
    excluded_tests = ['BackendApplicationTests', 'RelayTokenDeletionIntegrationTest', 'RelayTodayUsageMysqlTest', 'RelayReportMysqlTest']
    reports = [p for p in sorted((ROOT / 'backend/target/surefire-reports').glob('TEST-*.xml'))
               if not any(p.name.endswith('.' + test + '.xml') for test in excluded_tests)]
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for report in reports:
        suite = ET.parse(report).getroot()
        for key in totals:
            totals[key] += int(suite.attrib.get(key, 0))
    assert reports and totals['failures'] == totals['errors'] == 0, 'Backend tests must pass first'
    target.mkdir(parents=True)
    (target / 'backend').mkdir()
    (target / 'deploy').mkdir()
    (target / 'tools').mkdir()
    (target / 'backups').mkdir()
    (target / 'deploy/updates').mkdir()
    (target / 'docs').mkdir()
    for src, dst in {
        TEMPLATE / 'compose.yaml': 'compose.yaml',
        TEMPLATE / 'deploy.sh': 'deploy.sh',
        TEMPLATE / 'README.md': 'README.md',
        TEMPLATE / 'healthcheck.sh': 'backend/healthcheck.sh',
        TEMPLATE / 'export-old-server.sh': 'tools/export-old-server.sh',
        ROOT / '.env.example': '.env.example',
        ROOT / 'deploy/nginx.conf': 'deploy/nginx.conf',
        ROOT / 'deploy/02-production-defaults.sql': 'deploy/02-production-defaults.sql',
        ROOT / 'backend/src/main/resources/db/schema.sql': 'deploy/schema.sql',
        ROOT / 'deploy/updates/security-20261019.sql': 'deploy/updates/security-20261019.sql',
        ROOT / 'docs/fix-account-security-20261019.md': 'docs/fix-account-security-20261019.md',
        ROOT / 'LICENSE': 'LICENSE',
    }.items():
        # Linux scripts and configuration must not have Windows CRLF or a BOM.
        (target / dst).write_bytes(src.read_text(encoding='utf-8-sig').replace('\r\n', '\n').encode('utf-8'))
    readme = target / 'README.md'
    readme.write_text(readme.read_text(encoding='utf-8').replace('imagecreater-prebuilt-20261018', name), encoding='utf-8', newline='\n')
    shutil.copytree(ROOT / 'dist', target / 'dist')
    excluded = []
    with zipfile.ZipFile(jar) as source, zipfile.ZipFile(target / 'backend/app.jar', 'w') as dest:
        for info in source.infolist():
            if Path(info.filename).name in {'application-dev.yml', 'application-prod.yml', 'application-dev.yaml', 'application-prod.yaml'}:
                excluded.append(info.filename)
                continue
            # Preserve compression and metadata, especially STORED nested dependencies.
            dest.writestr(info, source.read(info.filename))
    (target / 'backups/README.txt').write_text(
        '迁移时上传旧服务器 database.sql、images.tar.gz、server.env 和 BACKUP.sha256 到此目录。\n'
        '部署包不包含线上或本地开发数据。详见上一级 README.md。\n', encoding='utf-8', newline='\n')
    git_head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    build_info = {
        'built_at': datetime.now().astimezone().isoformat(timespec='seconds'),
        'source': 'Current working tree including uncommitted changes',
        'git_base': git_head,
        'java': 'Build JDK 21, Maven release target 17; deployment runtime Java 17',
        'frontend': 'npm run build (Vue type check + Vite production build)',
        'frontend_tests': 'Not run for this security release; frontend type check and production build passed',
        'backend_command': 'mvnw.cmd -q -Dtest=*,!' + ',!'.join(excluded_tests) + ' package',
        'backend_tests': totals,
        'excluded_integration_tests': excluded_tests,
        'test_limitations': 'Full-application and unrelated MySQL integration suites excluded. Security MySQL test used connection-local temporary tables on local MySQL 9.1. No production database accessed, Docker startup or real SMTP delivery tested.',
        'security_tests': 39,
        'security_upgrade': 'docs/fix-account-security-20261019.md',
        'incremental_sql': 'deploy/updates/security-20261019.sql',
        'original_jar_sha256': digest(jar),
        'excluded_jar_resources': excluded,
        'jar_sha256': digest(target / 'backend/app.jar'),
        'container_verification': 'Docker is not installed locally; actual container startup and migration require target-server verification',
        'server_build': 'No source compilation or docker build. Official runtime images only.',
    }
    (target / 'BUILD-INFO.json').write_text(json.dumps(build_info, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    files = sorted(p for p in target.rglob('*') if p.is_file())
    manifest = '\n'.join(f'{digest(p)}  {p.relative_to(target).as_posix()}' for p in files) + '\n'
    (target / 'MANIFEST.sha256').write_text(manifest, encoding='utf-8', newline='\n')
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for p in sorted(target.rglob('*')):
            if not p.is_file():
                continue
            entry = zipfile.ZipInfo.from_file(p, f'{name}/{p.relative_to(target).as_posix()}')
            entry.create_system = 3
            mode = 0o755 if p.suffix == '.sh' else 0o644
            entry.external_attr = (stat.S_IFREG | mode) << 16
            entry.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(entry, p.read_bytes())
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None, 'Corrupt ZIP'
    (OUT / f'{name}.zip.sha256').write_text(f'{digest(archive)}  {archive.name}\n', encoding='ascii', newline='\n')
    print(json.dumps({'archive': str(archive), 'bytes': archive.stat().st_size, 'sha256': digest(archive), 'backend_tests': totals, 'excluded': excluded}, indent=2))


if __name__ == '__main__':
    main()
