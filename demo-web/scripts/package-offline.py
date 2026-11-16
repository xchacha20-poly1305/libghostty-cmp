#!/usr/bin/env python3
"""Package a webpack distribution for direct file:// use. No local server required."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import shutil

parser = argparse.ArgumentParser()
parser.add_argument('source', type=Path)
parser.add_argument('destination', type=Path)
parser.add_argument('--skiko', type=Path, default=Path('demo-web/build/compose/skiko-for-web-runtime/skiko.wasm'))
parser.add_argument('--target', choices=('js', 'wasm'), required=True)
args = parser.parse_args()
source = args.source.resolve()
destination = args.destination.resolve()
if source == destination or source in destination.parents or destination in source.parents:
    raise ValueError('Source and destination must be separate directories')
if destination.exists():
    shutil.rmtree(destination)
shutil.copytree(source, destination)
assets = {}
skiko_digest = hashlib.sha256(args.skiko.read_bytes()).digest()
skiko_keys = []
# Compose copies the JS runtime as resources while webpack also bundles its imported modules.
# The raw runtime is unused by the bundled entrypoint; validate it before omitting that copy.
if args.target == 'js':
    if hashlib.sha256((source / 'skiko.wasm').read_bytes()).digest() != skiko_digest:
        raise RuntimeError('Copied JS Skiko payload differs from the configured runtime')
for path in sorted(source.rglob('*')):
    if not path.is_file() or path.suffix in ('.js', '.html', '.map'):
        continue
    key = path.relative_to(source).as_posix()
    if args.target == 'js' and key in ('skiko.wasm', 'skiko.mjs', 'skikod8.mjs', 'js-reexport-symbols.mjs'):
        continue
    content = path.read_bytes()
    mime = 'application/wasm' if path.suffix == '.wasm' else 'application/octet-stream'
    assets[key] = [mime, base64.b64encode(content).decode('ascii')]
    if hashlib.sha256(content).digest() == skiko_digest:
        skiko_keys.append(key)
if len(skiko_keys) != 1:
    raise RuntimeError('Expected exactly one Skiko Wasm payload; review the dependency loader')
if 'ghostty-vt.wasm' not in assets:
    raise RuntimeError('Ghostty Wasm binary missing from browser distribution')
(destination / 'offline-assets.js').write_text(
    'globalThis.__ghosttyAssets=' + json.dumps(assets, separators=(',', ':')) + ';\n' +
    'globalThis.__ghosttyAssetAliases=' + json.dumps({'skiko.wasm': skiko_keys[0]}) + ';\n')
shutil.copyfile(Path(__file__).with_name('offline-runtime.js'), destination / 'offline-runtime.js')
page = destination / 'index.html'
marker = '<script src="demo-web.js"></script>'
html = page.read_text()
if html.count(marker) != 1:
    raise RuntimeError('HTML entrypoint changed; review offline resource initialization')
page.write_text(html.replace('<html lang="en">', f'<html lang="en" data-kotlin-target="{args.target}">').replace(marker, '<script src="offline-assets.js"></script>\n<script src="offline-runtime.js"></script>\n' + marker))
# Wasm and the Latin mono face live in offline-assets.js; retain the font license for attribution.
for path in destination.rglob('*'):
    if path.is_file() and path.suffix in ('.wasm', '.mjs', '.otf', '.ttf', '.woff', '.woff2', '.map'):
        path.unlink()
(destination / 'README.txt').write_text(
    f'Ghostty Web Terminal — Kotlin/{args.target}\n\nUnzip the complete folder, then open index.html in your browser.\n'
    'No server or installation is needed. The terminal itself runs without a network connection.\n'
    'CJK and emoji fonts download from Google Fonts and jsDelivr when the browser is online.\n' +
    ('The Kotlin/Wasm application requires WasmGC.\n' if args.target == 'wasm' else
     'The Kotlin/JS application uses WebAssembly for Ghostty and Skiko.\n') +
    'WebGL 2 is required.\n'
    'This is a simulated shell with an in-memory filesystem. Refresh to reset it.\n'
    'Clipboard API availability depends on browser permissions; ordinary browser paste events are supported.\n'
    'Bundled Latin font license: fonts/OFL.txt. Source: https://github.com/xchacha20-poly1305/libghostty-cmp\n')
print(destination / 'index.html')
