"""AI-generated release packaging; explicit allowlist excludes private captures and caches."""
import importlib.util
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('project_build', ROOT / 'build.py')
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)
allowed_roots = {'src', 'resources', 'tests', 'docs', 'research', 'tools', '.github'}
allowed_files = {'README.md', 'LICENSE', 'AI_DISCLOSURE.md', 'CHANGELOG.md',
                 'build.py', 'build.ps1', 'dependencies.lock.json', '.gitignore', '.gitattributes'}
entries = {}
for path in sorted(ROOT.rglob('*')):
    if not path.is_file():
        continue
    relative = path.relative_to(ROOT)
    if any(part in {'__pycache__', '.git', '.cache', 'build', 'dist'} for part in relative.parts):
        continue
    if relative.parts[0] not in allowed_roots and relative.as_posix() not in allowed_files:
        continue
    if path.suffix in {'.class', '.log', '.csv', '.dat', '.jar', '.pyc'}:
        raise RuntimeError(f'Unexpected binary/private file in source allowlist: {relative}')
    entries['create-train-sync-fix/' + relative.as_posix()] = path.read_bytes()
target = ROOT / 'dist/create-train-sync-fix-1.0.0-beta.1-sources.zip'
build.archive(target, entries)
target.with_suffix('.zip.sha256').write_text(build.digest(target) + '  ' + target.name + '\n', encoding='ascii')
print(f'Packaged {len(entries)} source files: {target.name}')
