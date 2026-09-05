# TanıdıkVar API

Bağımsız backend reposu: Java 21 hedefi, Spring Boot, PostgreSQL ve Flyway. Başka bir uygulama klasörüne veya üst klasördeki ayarlara ihtiyaç duymaz.

## Kurulum ve çalıştırma

Java 21 veya üstü, Python 3 ve çalışan Docker Desktop gerekir. Bu klasörde:

```bash
./run.sh
```

İlk çalıştırmada `scripts/setup-local.sh`, `.env.example` üzerinden `.env` oluşturur ve rastgele yerel DB parolası üretir; mevcut dosyayı değiştirmez. Ardından Compose PostgreSQL'i başlatır ve API'yi çalıştırır. Loglar terminale yazılır. Ctrl+C API'yi durdurur; PostgreSQL ve kalıcı verisi korunur.

- API: http://localhost:8080/api/health
- Swagger: http://localhost:8080/swagger-ui.html (local profil)
- PostgreSQL: localhost:55432

API/DB portları ve CORS origin bu reponun `.env` dosyasındadır. Frontend başka adreste çalışıyorsa `CORS_ALLOWED_ORIGIN` değerini güncelle. Varsayılan origin `http://localhost:5173`.

Compose proje adı `tanidikvar`, kalıcı volume `tanidikvar_postgres_data` olarak sabittir. Repo dizini değişse de aynı volume kullanılır. Veriyi silmeden DB'yi durdurmak için bu klasörde `docker compose stop postgres` çalıştır. Aynı makinede ikinci bağımsız kurulum için Compose proje adı/port ayarları ayrıca ayrılmalıdır.

Private dosya dizini `.local/storage` altındadır. Gerçek `.env`, `.local`, log ve derleme çıktıları `.gitignore` ile hariç tutulur. `.env.example`, script ve Compose Git'e dahil edilir. Production dağıtımı bu local başlatıcıdan ayrıdır.

## Doğrulama

```bash
./mvnw verify
```

Testler Testcontainers ile ayrı, geçici PostgreSQL kullanır; uygulama verisini değiştirmez. Test için `.env` gerekmez.

## Uygulanmış kapsam

Endpoint'ler: `GET /api/health`, `GET /api/auth/csrf`. Local profilde `/v3/api-docs` ve Swagger açık; diğer istekler varsayılan kapalı. JWT kayıt/giriş sonraki teslimdir.

V1 migration yalnız `universities`, `departments`, `university_departments` tablolarını ve fiziksel DELETE/TRUNCATE engelini oluşturur. Soft delete kullanılır. Henüz katalog entity/repository yoktur. JPA validate ayarı bulunur; entity eşleşmeleri ilgili teslimle eklenecektir. Uygulanmış migration değiştirilmez.
