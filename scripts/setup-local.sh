#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PYTHON'
from pathlib import Path
import secrets
import base64
p = Path('.env')
if not p.exists():
    contents = Path('.env.example').read_text().replace('DB_PASSWORD=\n', 'DB_PASSWORD=' + secrets.token_hex(24) + '\n')
    with p.open('x') as stream:
        stream.write(contents)
    p.chmod(0o600)
    print('Yerel .env oluşturuldu; erişim bilgileri ekrana yazılmadı.')
else:
    print('Mevcut .env korundu.')
contents = p.read_text()
lines = contents.splitlines()
if not any(line.startswith('JWT_SECRET=') and line.partition('=')[2].strip() for line in lines):
    lines = [line for line in lines if not line.startswith('JWT_SECRET=')]
    lines.append('JWT_SECRET=' + base64.b64encode(secrets.token_bytes(48)).decode('ascii'))
    p.write_text('\n'.join(lines) + '\n')
    p.chmod(0o600)
    print('Yerel JWT anahtarı oluşturuldu; mevcut DB ayarları korundu.')
Path('.local/storage').mkdir(parents=True, exist_ok=True)
Path('.local/storage').chmod(0o700)
PYTHON
