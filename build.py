"""Portable checksum-pinned Java 21 build. AI-generated; see AI_DISCLOSURE.md."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def archive(path, entries):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as out:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            out.writestr(info, data, compresslevel=9)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--java-home', type=Path, default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--offline', action='store_true')
    parser.add_argument('--cache-dir', type=Path, default=ROOT / '.cache/dependencies')
    parser.add_argument('--component', choices=['fix', 'trace'], default='fix')
    args = parser.parse_args()
    executables = {}
    for tool in ['javac', 'java']:
        exe = (Path(args.java_home) / 'bin' / (tool + ('.exe' if os.name == 'nt' else ''))) if args.java_home else shutil.which(tool)
        if not exe:
            raise RuntimeError('JDK 21 required: set JAVA_HOME or --java-home')
        executables[tool] = str(exe)
    version = subprocess.run([executables['javac'], '-version'], capture_output=True, text=True, check=True)
    if not re.search(r'javac 21(?:\.|\s|$)', version.stdout + version.stderr):
        raise RuntimeError('Use JDK 21 for the reproducible release build')
    deps = []
    for dependency in json.loads((ROOT / 'dependencies.lock.json').read_text(encoding='utf8')):
        path = args.cache_dir / dependency['path']
        if not path.exists() or digest(path) != dependency['sha256']:
            if args.offline:
                raise RuntimeError(f'Missing or invalid cached dependency: {dependency["path"]}')
            path.parent.mkdir(parents=True, exist_ok=True)
            request = urllib.request.Request(dependency['base'] + dependency['path'], headers={'User-Agent': 'CreateTrainSyncFix-build/1.0'})
            with urllib.request.urlopen(request, timeout=60) as response:
                data = response.read()
            if hashlib.sha256(data).hexdigest() != dependency['sha256']:
                raise RuntimeError(f'Dependency checksum mismatch: {dependency["path"]}')
            path.write_bytes(data)
        deps.append(path)
    component = ROOT if args.component == 'fix' else ROOT / 'tools/train-trace'
    output = ROOT / 'build' / args.component
    classes, tests = output / 'classes', output / 'test-classes'
    # Fixed build subdirectories only; no caller-supplied recursive deletion targets.
    for folder in [classes, tests]:
        if not folder.resolve().is_relative_to((ROOT / 'build').resolve()):
            raise RuntimeError('Build path escaped workspace')
        if folder.exists():
            shutil.rmtree(folder)
        folder.mkdir(parents=True)
    cp = os.pathsep.join(map(str, deps[:3]))
    sources = sorted(map(str, (component / 'src').rglob('*.java')))
    subprocess.run([executables['javac'], '--release', '21', '-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', str(classes), *sources], check=True)
    if args.component == 'fix':
        test_cp = os.pathsep.join(map(str, [classes, *deps[:5]]))
        test_sources = sorted(map(str, (ROOT / 'tests').rglob('*.java')))
        subprocess.run([executables['javac'], '--release', '21', '-encoding', 'UTF-8', '-proc:none', '-cp', test_cp, '-d', str(tests), *test_sources], check=True)
        run_cp = os.pathsep.join(map(str, [classes, tests, *deps[3:5]]))
        for test, arguments in [('trainsyncfix.RecoveryTest', []), ('trainsyncfix.TargetBytecodeTest', [str(deps[5])])]:
            subprocess.run([executables['java'], '-cp', run_cp, test, *arguments], check=True)
    metadata = (component / 'resources/META-INF/neoforge.mods.toml').read_text(encoding='utf8')
    version = re.search(r'^version\s*=\s*"([^"]+)"', metadata, re.M)[1]
    modid = re.search(r'^modId\s*=\s*"([^"]+)"', metadata, re.M)[1]
    entries = {}
    for folder in [classes, component / 'resources']:
        for path in sorted(folder.rglob('*')):
            if path.is_file():
                entries[path.relative_to(folder).as_posix()] = path.read_bytes()
    entries['LICENSE'] = (ROOT / 'LICENSE').read_bytes()
    entries['AI_DISCLOSURE.md'] = (ROOT / 'AI_DISCLOSURE.md').read_bytes()
    entries['META-INF/MANIFEST.MF'] = f'Manifest-Version: 1.0\r\nImplementation-Title: {modid}\r\nImplementation-Version: {version}\r\n\r\n'.encode()
    jar = ROOT / 'dist' / f'{modid}-{version}.jar'
    archive(jar, entries)
    jar.with_suffix('.jar.sha256').write_text(digest(jar) + '  ' + jar.name + '\n', encoding='ascii')
    print(f'Built {jar.name}: {digest(jar)}')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, subprocess.CalledProcessError) as error:
        print(f'Build failed: {error}', file=sys.stderr)
        sys.exit(1)
