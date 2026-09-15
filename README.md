# TanıdıkVar API

Java 21 ve Spring Boot 4.1.1 tabanlı API. PostgreSQL şeması Flyway `V1`–`V18` migration’larıyla yönetilir.

## Çalıştırma

API’yi hostta, PostgreSQL ve Mailpit’i Docker’da çalıştırmak için:

```bash
cp .env.example .env
./run.sh
```

PostgreSQL, Mailpit ve API’yi Docker’da çalıştırmak için:

```bash
./run.sh --docker
./run.sh --status
```

Frontend bu Compose dosyasının parçası değildir. Web reposunda ayrıca `npm run dev` çalıştırılır. `./run.sh --stop` servisleri veriyi koruyarak durdurur; `./run.sh --help` seçenekleri gösterir.

- API: <http://localhost:8080/api/health>
- Swagger: <http://localhost:8080/swagger-ui.html>
- Mailpit: <http://localhost:8025>

## Doğrulama

```bash
./mvnw verify
docker compose config
```

## Yerel hazırlık

Migration katalog veya Manager hesabı oluşturmaz:

```bash
./scripts/seed-local-catalog.sh
```

İlk Manager için önce uygulamada kayıt olup e-postayı doğrula; ardından gerçek adresi `.env` içinde `MANAGER_EMAIL` olarak tanımlayıp repodaki kontrollü promote scriptini kullan. Secret veya parola komut satırına/dokümana yazılmaz.

## Uygulanmış alanlar

Auth/cookie JWT/CSRF, profil ve katalog, sorular, topluluk/Admin yorumları, başvurular, beğeni/görüntülenme, arama/Popülerler, Manager moderasyonu/raporları/audit uygulanmıştır. Güncel özet [ortak mevcut mimari](../docs/project/CURRENT_ARCHITECTURE.md), hedef kurallar [ürün planı](../docs/project/PRODUCT_PLAN.md), production ayarları [deployment rehberi](docs/PRODUCTION_DEPLOYMENT.md) içindedir.

Eski endpoint dökümü ve test sayıları [arşivde](../docs/archive/API_README_HISTORY_2026-09-12.md) korunur. Gerçek API sözleşmesi kod ve üretilen OpenAPI çıktısıdır.
