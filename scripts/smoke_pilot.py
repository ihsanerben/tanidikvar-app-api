"""Read-only pilot contract check; never prints response bodies or CSRF values."""
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from http.cookies import SimpleCookie

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None

def validate(path, headers, body, secure):
    if 'application/json' not in headers.get('Content-Type', '').lower():
        raise ValueError('JSON yerine farklı içerik geldi; API proxy yolunu kontrol et.')
    try:
        data = json.loads(body)
    except (ValueError, UnicodeError):
        raise ValueError('Geçersiz JSON yanıtı.') from None
    if not isinstance(data, dict):
        raise ValueError('Beklenen API nesnesi bulunamadı.')
    if path == '/api/health':
        if data.get('status') != 'ok' or data.get('database') != 'up':
            raise ValueError('API/veritabanı sağlıklı değil.')
    elif path.startswith('/api/questions'):
        if not isinstance(data.get('items'), list) or not isinstance(data.get('totalElements'), int):
            raise ValueError('Soru listesi sözleşmesi doğrulanamadı.')
    else:
        if not isinstance(data.get('token'), str) or not data['token'] or data.get('headerName') != 'X-XSRF-TOKEN':
            raise ValueError('CSRF sözleşmesi doğrulanamadı.')
        cookies = SimpleCookie()
        for value in headers.get_all('Set-Cookie', []):
            cookies.load(value)
        csrf = cookies.get('XSRF-TOKEN')
        if not csrf or csrf['path'] != '/' or csrf['samesite'].lower() != 'lax' or (secure and not csrf['secure']):
            raise ValueError('CSRF cookie kapsamı veya Secure/SameSite ayarı hatalı.')
        if 'no-store' not in headers.get('Cache-Control', '').lower():
            raise ValueError('CSRF yanıtında no-store eksik.')

def main():
    if len(sys.argv) != 2:
        raise ValueError('Kullanım: bash scripts/smoke-pilot.sh https://web-veya-api-host')
    url = urllib.parse.urlsplit(sys.argv[1])
    if url.username or url.password or url.query or url.fragment or url.path not in ('', '/'):
        raise ValueError('Secret, yol veya sorgu içermeyen origin adresi kullan.')
    if not url.hostname or (url.scheme != 'https' and not (url.scheme == 'http' and url.hostname in ('localhost', '127.0.0.1'))):
        raise ValueError('Uzak pilot HTTPS kullanmalıdır; HTTP yalnız localhost için kabul edilir.')
    origin = urllib.parse.urlunsplit((url.scheme, url.netloc, '', '', ''))
    opener = urllib.request.build_opener(NoRedirect)
    for path in ('/api/health', '/api/questions?size=1', '/api/auth/csrf'):
        try:
            with opener.open(urllib.request.Request(origin + path, headers={'Accept': 'application/json'}), timeout=90) as response:
                validate(path, response.headers, response.read(1048576), url.scheme == 'https')
        except urllib.error.HTTPError as error:
            raise ValueError(f'{path}: HTTP {error.code}; yönlendirme/hata başarılı sayılmaz.') from None
        except (urllib.error.URLError, TimeoutError):
            raise ValueError(f'{path}: bağlantı kurulamadı veya zaman aşımı.') from None
    print('Pilot API sözleşmesi geçti; gerçek e-posta ve tarayıcı oturum testi ayrıca gereklidir.')

if __name__ == '__main__':
    try:
        main()
    except ValueError as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
