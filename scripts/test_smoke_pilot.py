import unittest
from email.message import Message
from smoke_pilot import validate

class SmokeContractTest(unittest.TestCase):
    def headers(self, kind='application/json'):
        headers = Message()
        headers['Content-Type'] = kind
        return headers
    def test_html_and_wrong_json_are_rejected(self):
        with self.assertRaises(ValueError):
            validate('/api/health', self.headers('text/html'), b'<html>SPA</html>', True)
        with self.assertRaises(ValueError):
            validate('/api/health', self.headers(), b'{"status":"ok","database":"down"}', True)
    def test_csrf_cookie_and_cache_contract(self):
        headers = self.headers()
        payload = b'{"token":"synthetic-test","headerName":"X-XSRF-TOKEN"}'
        with self.assertRaises(ValueError):
            validate('/api/auth/csrf', headers, payload, True)
        headers['Set-Cookie'] = 'XSRF-TOKEN=synthetic-test; Path=/; SameSite=Lax; Secure'
        headers['Cache-Control'] = 'private, no-store'
        validate('/api/auth/csrf', headers, payload, True)
    def test_valid_health_and_list(self):
        validate('/api/health', self.headers(), b'{"status":"ok","database":"up"}', True)
        validate('/api/questions?size=1', self.headers(), b'{"items":[],"totalElements":0}', True)

if __name__ == '__main__':
    unittest.main()
