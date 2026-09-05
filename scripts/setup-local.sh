#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PYTHON'
from pathlib import Path
import secrets
p = Path('.env')
if not p.exists():
    contents = Path('.env.example').read_text().replace('DB_PASSWORD=\n', 'DB_PASSWORD=' + secrets.token_hex(24) + '\n')
    with p.open('x') as stream:
        stream.write(contents)
    p.chmod(0o600)
    print('Yerel .env oluşturuldu; erişim bilgileri ekrana yazılmadı.')
else:
    print('Mevcut .env korundu.')
Path('.local/storage').mkdir(parents=True, exist_ok=True)
Path('.local/storage').chmod(0o700)
PYTHON
