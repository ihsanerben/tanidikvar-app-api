# TanıdıkVar API

Bağımsız backend reposu: Java 21 hedefi, Spring Boot, PostgreSQL ve Flyway. Başka bir uygulama klasörüne veya üst klasördeki ayarlara ihtiyaç duymaz.

## Kurulum ve çalıştırma

Java 21 veya üstü, Python 3 ve çalışan Docker Desktop gerekir. Bu klasörde:

```bash
./run.sh
```

İlk çalıştırmada `scripts/setup-local.sh`, `.env.example` üzerinden `.env` oluşturur ve rastgele yerel DB parolası ve JWT anahtarı üretir. Mevcut DB ayarlarını korur; JWT_SECRET eksik veya boşsa yalnız bu anahtarı ekler. Ardından Compose PostgreSQL ve Mailpit test posta kutusunu başlatır ve API'yi çalıştırır. Loglar terminale yazılır. Ctrl+C API'yi durdurur; PostgreSQL ve kalıcı verisi korunur.

- API: http://localhost:8080/api/health
- Swagger: http://localhost:8080/swagger-ui.html (local profil)
- PostgreSQL: localhost:55432
- Yerel test e-postaları: http://localhost:8025 (Mailpit; SMTP localhost:1025)

API/DB portları ve CORS origin bu reponun `.env` dosyasındadır. Frontend başka adreste çalışıyorsa `CORS_ALLOWED_ORIGIN` ve e-posta bağlantıları için `FRONTEND_URL` değerlerini güncelle. Varsayılan origin `http://localhost:5173`.

Compose proje adı `tanidikvar`, kalıcı volume `tanidikvar_postgres_data` olarak sabittir. Repo dizini değişse de aynı volume kullanılır. Veriyi silmeden DB'yi durdurmak için bu klasörde `docker compose stop postgres` çalıştır. Aynı makinede ikinci bağımsız kurulum için Compose proje adı/port ayarları ayrıca ayrılmalıdır.

Private dosya dizini `.local/storage` altındadır. Gerçek `.env`, `.local`, log ve derleme çıktıları `.gitignore` ile hariç tutulur. `.env.example`, script ve Compose Git'e dahil edilir. Production dağıtımı bu local başlatıcıdan ayrıdır.

## Doğrulama

```bash
./mvnw verify
```

Testler Testcontainers ile ayrı, geçici PostgreSQL kullanır; uygulama verisini değiştirmez. Test için `.env` gerekmez.

## Uygulanmış kapsam

- `GET /api/health`, `GET /api/auth/csrf`, authenticated `GET /api/me`.
- `POST /api/auth/register`, `/login`, `/refresh`, `/logout`, `/resend-verification`, `/verify-email`, `/forgot-password`, `/reset-password`.
- Kayıt, yeniden doğrulama ve şifre sıfırlama talebi mevcut/olmayan adres için aynı boş `202` yanıtını verir. Kayıt otomatik giriş yapmaz; e-posta doğrulanmadan giriş olmaz. Doğrulama/sıfırlama başarılıysa `204`; login/refresh yalnız kullanıcı DTO'su döner.
- Local profilde `/v3/api-docs` ve Swagger açık; diğer endpoint'ler varsayılan kapalıdır.

Access JWT 15 dakika, oturum en fazla 14 gündür. Refresh rotation bu toplam süreyi uzatmaz. Token'lar HttpOnly cookie; veritabanında refresh hash'i tutulur. Reuse bütün aileyi iptal eder; logout mevcut aileyi, şifre sıfırlama bütün kullanıcı oturumlarını iptal eder. Güncel hesap/yetki her access isteğinde DB'den kontrol edilir.

Bütün auth POST istekleri CSRF cookie + `X-XSRF-TOKEN` header ister. Production cookie Secure, local profil HTTP için Secure kapalıdır. CORS tek origin allowlist kullanır. Parolalar BCrypt cost 12 ile saklanır; yeni şifre en az 10 karakter ve UTF-8 olarak en fazla 72 bayttır.

V1 eğitim kataloğu korunur. V2 `users`, `auth_sessions`, `auth_action_tokens` tablolarını ekler. Hepsinde soft delete alanı ve fiziksel DELETE/TRUNCATE engeli bulunur. Tüketilen/iptal edilen token geçmişi saklanır. Uygulanmış migration değiştirilmez.

## E-posta ve yerel kullanım

Kayıt olduktan sonra http://localhost:8025 adresinde e-postayı açıp doğrulama bağlantısına git. Mailpit dışarıya e-posta göndermez; yerel test mesajlarını yakalar. Web'i ayrıca kendi reposunda `npm run dev` veya `./run.sh` ile başlat.

Doğrulama bağlantısı 24 saat, şifre sıfırlama bağlantısı 30 dakika geçerlidir. Token yalnız hash olarak saklanır; link URL fragment'inde taşınır ve form POST ile tüketilir. Bağlantıyı yalnız açmak hesabı değiştirmez. Bir bağlantı kullanıldığında aynı hesaptaki aynı amaçlı diğer bağlantılar da geçersiz olur.

E-posta DB commit'inden sonra sınırlı bellekiçi iş kuyruğunda SMTP ile gönderilir. SMTP başarısızsa güvenli olay logu tutulur; kullanıcı yeniden bağlantı isteyebilir. Kalıcı mail kuyruğu/otomatik yeniden gönderim yoktur. Canlıya çıkarken SMTP sağlayıcısı, TLS ve teslimat izleme ayarları tamamlanmalıdır.

`.env.example` JWT süreleri, `FRONTEND_URL`, `SMTP_*`, `MAIL_FROM` değişkenlerini gösterir. JWT anahtarı en az 32 rastgele baytın Base64 karşılığı olmalıdır; kodda varsayılan secret yoktur. Gerçek `.env` dosyasını paylaşma veya Git'e ekleme.

Auth uçlarında socket IP + işlem başına 15 dakikada 10 istek; refresh için dakikada 60 istek sınırı vardır. `429` yanıtı `Retry-After` taşır. Limiter tek instance belleğindedir, en fazla 10.000 pencere tutar; uygulama yeniden başlayınca sıfırlanır. Forwarded header güvenilmez. Production proxy/çoklu instance politikası yayına hazırlık aşamasındadır.
