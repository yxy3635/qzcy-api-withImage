"""Package a flat frontend/backend update for the existing Compose deployment."""
from datetime import datetime, timezone, timedelta
from pathlib import Path
import hashlib
import io
import json
import re
import stat
import struct
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NAME = 'imagecreater-update-20261022-prompt-rules.zip'
DEST = ROOT.parent / NAME
MANIFEST = 'MANIFEST-20261022-PROMPT-RULES.sha256'
BUILD_INFO = 'BUILD-INFO-20261022-PROMPT-RULES.json'
EXCLUDED_TESTS = {'com.qzcy.backend.BackendApplicationTests',
                  'com.qzcy.backend.service.impl.RelayTokenDeletionIntegrationTest'}

WEB_DOCKERFILE = '''FROM nginx:stable-alpine
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY dist/ /usr/share/nginx/html/
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \\
    CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
'''
WEB_IGNORE = '**\n!Dockerfile\n!.dockerignore\n!dist/\n!dist/**\n!deploy/\n!deploy/nginx.conf\n'
BACKEND_IGNORE = '**\n!Dockerfile\n!.dockerignore\n!app.jar\n!healthcheck.sh\n'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def text_file(path):
    return path.read_text(encoding='utf-8-sig').replace('\r\n', '\n').encode('utf-8')


def main():
    assert not DEST.exists() and not DEST.with_suffix('.zip.sha256').exists(), f'Refusing to overwrite {DEST}'
    backend_log = (ROOT / 'backend/prompt-rule-release-package.log').read_text(encoding='utf-8', errors='replace')
    frontend_log = (ROOT / 'prompt-rule-release-frontend.log').read_text(encoding='utf-8', errors='replace')
    assert 'BUILD SUCCESS' in backend_log
    assert 'built in' in frontend_log and 'vue-tsc --build' in frontend_log
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    suites = []
    for report in sorted((ROOT / 'backend/target/surefire-reports').glob('TEST-*.xml')):
        suite = ET.parse(report).getroot()
        if suite.attrib['name'] in EXCLUDED_TESTS:
            continue
        suites.append(suite.attrib['name'])
        for key in totals:
            totals[key] += int(suite.attrib.get(key, 0))
    assert totals['tests'] > 300 and totals['failures'] == totals['errors'] == 0, totals
    jar_source = ROOT / 'backend/target/backend-0.0.1-SNAPSHOT.jar'
    jar_buffer = io.BytesIO()
    excluded_resources = []
    with zipfile.ZipFile(jar_source) as source, zipfile.ZipFile(jar_buffer, 'w') as target:
        for info in source.infolist():
            if Path(info.filename).name in {'application-dev.yml', 'application-prod.yml',
                                            'application-dev.yaml', 'application-prod.yaml'}:
                excluded_resources.append(info.filename)
                continue
            # Spring Boot nested dependencies must keep their original STORED layout.
            target.writestr(info, source.read(info.filename))
    files = {f'dist/{p.relative_to(ROOT / "dist").as_posix()}': p.read_bytes()
             for p in sorted((ROOT / 'dist').rglob('*')) if p.is_file()}
    assert files.get('dist/index.html')
    assert any(b'autoBanThreshold' in data for name, data in files.items() if name.endswith('.js'))
    files.update({
        'Dockerfile': WEB_DOCKERFILE.encode('utf-8'),
        '.dockerignore': WEB_IGNORE.encode('utf-8'),
        'backend/app.jar': jar_buffer.getvalue(),
        'backend/Dockerfile': text_file(ROOT / 'deploy/prebuilt/Dockerfile.backend-update'),
        'backend/.dockerignore': BACKEND_IGNORE.encode('utf-8'),
        'backend/healthcheck.sh': text_file(ROOT / 'deploy/prebuilt/healthcheck.sh'),
        'UPDATE-20261022.md': text_file(ROOT / 'docs/update-prompt-rules-20261022.md'),
        'docs/relay-prompt-rules.md': text_file(ROOT / 'docs/relay-prompt-rules.md'),
    })
    for name in ['relay-prompt-auto-ban-desktop.png', 'relay-prompt-auto-ban-mobile.png']:
        files['docs/screenshots/' + name] = (ROOT / 'docs/screenshots' / name).read_bytes()
    info = {
        'built_at': datetime.now(timezone(timedelta(hours=8))).isoformat(timespec='seconds'),
        'source': 'Current working tree, including all earlier uncommitted updates',
        'git_base': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'layout': 'Flat update archive: extract into existing project root; dist/ and backend/app.jar',
        'java': 'Build JDK 21; class target Java 17; container runtime Java 17',
        'frontend_command': 'npm run build',
        'frontend_checks': 'Vue type check and production build passed; mocked browser UI checks passed in preceding implementation turn',
        'backend_command': 'mvnw.cmd -B -ntp -Dtest=*,!BackendApplicationTests,!RelayTokenDeletionIntegrationTest package',
        'backend_tests': totals,
        'backend_passed': totals['tests'] - totals['skipped'],
        'backend_suites': suites,
        'excluded_tests': sorted(EXCLUDED_TESTS),
        'test_limitations': 'Two application integration suites require local environment configuration and were excluded. Dedicated MySQL suites may be skipped without opt-in environment variables. Docker is not installed locally; no real container startup or production deployment was performed.',
        'deployment_guide': 'UPDATE-20261022.md',
        'overwrites': ['dist/', 'backend/app.jar', 'backend/Dockerfile', 'backend/.dockerignore', 'backend/healthcheck.sh', 'Dockerfile', '.dockerignore'],
        'preserves': ['.env', 'compose.yaml', 'deploy/nginx.conf', 'database', 'image volumes'],
        'migration': 'Automatic startup migration of prompt-rule settings/hits tables and threshold column; CREATE/ALTER privileges required',
        'default_auto_ban_threshold': 3,
        'excluded_jar_resources': excluded_resources,
        'source_jar_sha256': sha(jar_source.read_bytes()),
        'jar_sha256': sha(files['backend/app.jar']),
    }
    files[BUILD_INFO] = (json.dumps(info, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    files[MANIFEST] = ''.join(f'{sha(data)}  {name}\n' for name, data in sorted(files.items())).encode('ascii')
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
        assert not {'.env', 'compose.yaml', 'deploy/nginx.conf'} & set(archive.namelist())
        for name in archive.namelist():
            assert not name.startswith(('/', '../', 'src/', 'backend/src/', 'node_modules/'))
            assert '\\' not in name and '..' not in Path(name).parts
            assert archive.read(name) == files[name]
            if name.endswith(('.sh', 'Dockerfile', '.dockerignore', '.md')):
                assert b'\r' not in archive.read(name), name
        for line in archive.read(MANIFEST).decode().splitlines():
            digest, name = line.split('  ', 1)
            assert sha(archive.read(name)) == digest, name
        for asset in re.findall(r'(?:src|href)="/(assets/[^"?]+)', archive.read('dist/index.html').decode()):
            assert 'dist/' + asset in files, asset
    with zipfile.ZipFile(io.BytesIO(files['backend/app.jar'])) as jar:
        assert jar.testzip() is None
        assert 'BOOT-INF/classes/application-docker.yml' in jar.namelist()
        assert all(Path(n).name not in {'application-dev.yml', 'application-prod.yml', 'application-dev.yaml', 'application-prod.yaml'} for n in jar.namelist())
        for name in jar.namelist():
            if name.startswith('BOOT-INF/classes/') and name.endswith('.class'):
                assert struct.unpack('>H', jar.read(name)[6:8])[0] == 61, name
        for name in ['service/RelayPromptRuleService.class', 'controller/RelayPromptRuleConfigController.class',
                     'dto/RelayPromptRuleConfigDto.class', 'util/RelayPromptStreamProbe.class', 'util/RelayPromptPolicyError.class']:
            assert 'BOOT-INF/classes/com/qzcy/backend/' + name in jar.namelist(), name
        assert all(item.compress_type == zipfile.ZIP_STORED for item in jar.infolist()
                   if item.filename.startswith('BOOT-INF/lib/') and item.filename.endswith('.jar'))
    digest = sha(DEST.read_bytes())
    DEST.with_suffix('.zip.sha256').write_text(f'{digest}  {NAME}\n', encoding='ascii', newline='\n')
    print(json.dumps({'archive': str(DEST), 'bytes': DEST.stat().st_size, 'sha256': digest,
                      'files': len(files), 'backend_tests': totals,
                      'verification': 'ZIP CRC, payload parity, manifest, frontend references, Java 17 classes, policy classes, Spring Boot nested JAR layout, LF text files, no environment secrets or local profile resources'}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
