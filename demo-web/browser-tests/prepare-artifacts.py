#!/usr/bin/env python3
"""Exercise the actual Release ZIPs and preserve their hashes with every E2E run."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import zipfile

web = Path(__file__).resolve().parents[1]
properties = dict(line.split('=', 1) for line in (web.parent / 'gradle.properties').read_text().splitlines()
                  if '=' in line and not line.startswith('#'))
version = os.environ.get('GHOSTTY_DEMO_VERSION') or os.environ.get('ORG_GRADLE_PROJECT_VERSION_NAME') or properties['VERSION_NAME']
output = web / 'build/browser-test-packages'
output.mkdir(parents=True, exist_ok=True)
manifest = {}
for target in ('js', 'wasm'):
    archive = web / f'build/distributions/demo-web-{target}-{version}.zip'
    destination = output / target
    if destination.exists():
        shutil.rmtree(destination)
    with zipfile.ZipFile(archive) as package:
        package.extractall(destination)
    html = (destination / 'index.html').read_text()
    if f'data-kotlin-target="{target}"' not in html or 'data-pages-docs=' in html:
        raise RuntimeError(f'Incorrect standalone entrypoint: {archive}')
    first_line = (destination / 'offline-assets.js').read_text().splitlines()[0]
    assets = json.loads(first_line.removeprefix('globalThis.__ghosttyAssets=').removesuffix(';'))
    payloads = [key for key in assets if key.endswith('.wasm')]
    if len(payloads) != (2 if target == 'js' else 3):
        raise RuntimeError(f'Unexpected runtime payloads for {target}: {payloads}')
    manifest[target] = {'archive': str(archive), 'sha256': hashlib.sha256(archive.read_bytes()).hexdigest(),
                        'wasmPayloads': payloads, 'entrypoint': str(destination / 'index.html')}
if list((web / 'build/pages').rglob('*.zip')):
    raise RuntimeError('Pages must not contain ZIP downloads')
(output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(output / 'manifest.json')
