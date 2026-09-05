# TanıdıkVar API

Bağımsız backend reposu: Java 21 hedefi, Spring Boot, PostgreSQL ve Flyway. Başka bir uygulama klasörüne veya üst klasördeki ayarlara ihtiyaç duymaz.

## Tek komutla Docker

Docker Desktop ve Python 3 açık/kurulu olsun. API reposunda:

```bash
./run.sh --docker
```

PostgreSQL, Mailpit, API ve web birlikte derlenir/başlatılır; servislerin health kontrolü beklenir. Web http://localhost:5173, API http://localhost:8080, e-postalar http://localhost:8025. Java/Node kurulumu Docker çalıştırması için gerekmez. Web Nginx üzerinden aynı origin `/api` yoluna bağlanır.

Web repo varsayılan olarak `../tanidikvar-app-web` konumunda aranır. Ayrı bir konumda clone ettiysen API `.env` içine `WEB_BUILD_CONTEXT=/tam/yol/web-reposu` yaz. API/web Dockerfile ve ignore dosyaları kendi repolarındadır; ortak kök dosyası gerekmez. API'yi tek başına kullanmak web reposunu gerektirmez.

`DOCKER_WEB_PORT` farklıysa launcher e-posta/CORS origin'ini otomatik `http://localhost:<port>` yapar. Farklı host kullanacaksan `DOCKER_WEB_ORIGIN` ayarla. Sonraki kod değişiklikleri için aynı komutu tekrar çalıştır; bu Docker akışı derlenmiş uygulamadır, hot reload yapmaz.

Durdurma (veri korunur):

```bash
docker compose --profile app stop
```

Loglar: `docker compose logs -f api web`. DB volume aynı `tanidikvar_postgres_data`; container storage `tanidikvar_api_storage` volume'ündedir. Local `.local/storage` ile ayrı dizinlerdir; henüz dosya yükleme verisi yoktur. Production HTTPS/SMTP/deployment ayarları bu yerel Compose akışından ayrıdır.

## Ayrı geliştirme süreçleri

Docker API/web çalışıyorsa önce `docker compose stop api web` ile portları boşalt. Eski geliştirme akışı korunur: API `./run.sh`, web kendi reposunda `npm run dev`.

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

## Profil ve katalog

- `GET/PUT /api/me/profile`: yalnız kendi profilin; istemciden kullanıcı/otorite atanmaz. Ad/soyad ve eğitim durumu zorunlu; öğrencide üniversite-bölüm, mezunda ayrıca yıl gerekir. Biyografi/meslek/şirket isteğe bağlıdır. Fotoğraf dosyası desteği 9. adımdadır.
- `GET /api/universities`, `/api/departments`, `/api/tags`, `/api/universities/{id}/departments`: public, aktif kayıtlar, `q/page/size`. Liste cevabı `items/page/size/totalElements`; varsayılan boyut 20, üst sınır 100.
- `POST /api/tags`: tamamlanmış profilli Admin veya Manager yeni tag oluşturur.
- `/api/manager/catalog/{kind}`: Manager listeleme/ekleme; `{kind}` UNIVERSITY, DEPARTMENT, TAG. `PUT /{id}` ad günceller; `PUT /{id}/status` `{deleted,version}` ile soft delete/geri yükler. Manager listesinde `includeDeleted=true` kullanılabilir.
- `/api/manager/university-departments`: Manager `universityId` ile listeleme ve POST eşleştirme; `PUT /{id}/status` soft delete/geri yükleme.

V3 `user_profiles`, `tags`, `management_actions` oluşturur. İlk iki migration değişmez. İsimler Türkçe harf dönüşümü/boşluk normalizasyonuyla unique tutulur; aynı üniversite-bölüm çifti yeniden oluşturulamaz. Yeni seçimde pasif ebeveyn/eşleşme reddedilir; mevcut profilde aynı eğitim referansı korunabilir. Parent geri yükleme bağımsız silinmiş çocuk kaydı geri getirmez.

Profil ve katalog güncellemesinde istemci `version` gönderir. Stale form 409 STALE_VERSION alır; otomatik üzerine yazılmaz. İlk profil için version=0. Profil tamamlama eğitim rolünü günceller; ADMIN/MANAGER yetkisi korunur. InteractionPolicy profil ön koşulunu taşır; yeni soru/cevap mutasyonları sonraki teslimlerde bu kontrolü kullanacaktır.

## İlk Manager

Önce normal kayıt ol ve e-posta adresini doğrula. Sonra bu repo içinde, örnekteki adresi kendi adresinle değiştir:

```bash
docker compose exec -T postgres psql -U tanidikvar -d tanidikvar -v ON_ERROR_STOP=1 -v manager_email='hesabin@example.com' < scripts/promote-manager.sql
```

DB kullanıcı/adını değiştirdiysen `-U/-d` değerlerini kendi ayarlarınla eşleştir. Script yalnız belirtilen aktif ve e-postası doğrulanmış hesabı MANAGER yapar; hazır yönetici hesabı veya parola üretmez. Başlangıç yetkilendirmesini audit kaydına yazar. Sayfayı yenileyince `/manager` ekranı açılır. Katalog başlangıçta boş olabilir; önce üniversite ve ortak bölüm ekle, ardından eşleştir.

## Soru yönetimi

- Public `GET /api/questions`: en yeni aktif sorular; `scope`, `universityId`, `universityDepartmentId`, `tagId`, `page`, `size` filtreleri. Varsayılan size=20, üst sınır 100. Eğitim ve tag filtreleri birlikte uygulanır.
- Public `GET /api/questions/{id}`: arşivlenmiş soru okunur; soft-deleted soru 404 döner.
- Authenticated `GET /api/me/questions`: yalnız oturum sahibinin soruları; arşivdekiler dahil, soft-deleted kayıtlar hariç.
- `POST /api/questions`: tamamlanmış profil gerekir. Body `{requestId, content}`; requestId UUID’dir. Aynı kullanıcı/gönderim anahtarı tekrarında aynı soru döner; değiştirilmiş içerikle aynı anahtar 409 REQUEST_CONFLICT alır. Yeni anahtarla aynı başlıkta ayrı soru açılabilir.
- `PUT /api/questions/{id}`: `{version, content}`; yalnız sahibi ve aktif soru. Admin/Manager başka kullanıcının sorusunu bu uçtan düzenleyemez.
- `POST /api/questions/{id}/archive`: `{version}`; yalnız sahibi. Arşivleme keşif listesinden çıkarır, bağlantıdan okumayı korur; tekrar arşivleme idempotenttir. Bu teslimde yeniden açma ve Manager moderasyon ucu yoktur.

`content`: title (10–200 karakter), body (isteğe bağlı, en fazla 5000), scope, universityId, universityDepartmentId, tagIds (0–5 farklı UUID). GENERAL iki eğitim alanını boş; UNIVERSITY yalnız universityId; UNIVERSITY_DEPARTMENT yalnız universityDepartmentId kullanır. Üniversite-bölüm eşleşmesinden üniversite türetilir. Başlık boşlukları normalize edilir, içerik düz metindir.

V4 `questions` ve `question_tags` tablolarını ekler; scope CHECK, FK, unique gönderim anahtarı, birleşik tag PK ve DELETE/TRUNCATE engelleri vardır. V1–V3 değişmez. Soru/tag yazımları tek transaction’dır; düzenleme/arşivleme soru → kullanıcı → katalog sırasıyla kilitlenir, birden fazla tag kimlik sırasıyla kilitlenir. Eski version 409 STALE_VERSION alır. Gerçek içerik değişikliğinde editedAt güncellenir; ilk yayın tarihi korunur. Değişmeyen içerik version/tarih artırmaz.

Yeni katalog seçimi aktif olmalıdır. Mevcut pasif eğitim/tag referansı düzenlemede aynı kalabilir; kaldırılmış tag bağı aynı satır üzerinden geri etkinleştirilir, pasif tag yeniden eklenemez. Hesabı veya profili soft-deleted yazarın adı/kimliği public cevapta açılmaz; “Katılımcı” gösterilir. Soru beğenileri ve görüntülenmeleri sonraki teslimlerdir; soru kartı sayaçları henüz eklenmedi. Topluluk cevapları aşağıdaki uçlardan yönetilir.

## Topluluk cevapları

- Public `GET /api/questions/{id}/answers`: yalnız görünür COMMUNITY cevapları; ilk yayın tarihi/id artan sırada, `page/size`, varsayılan 20, en fazla 100. `totalElements` aktif topluluk cevabı sayısını verir.
- Authenticated `GET /api/questions/{id}/my-answer`: yalnız oturum sahibinin topluluk cevabı; kaldırılmış satır dahil. Henüz cevap yoksa 204. Başka yazarın kaldırılmış içeriği public listeye verilmez.
- `POST /api/questions/{id}/answers`: `{body}`; tamamlanmış profil ve aktif soru gerekir. 10–5000 karakter düz metin. Aynı mevcut metnin tekrarı aynı cevabı döndürür; farklı ikinci cevap 409 ANSWER_EXISTS, kaldırılmış kayıt 409 ANSWER_REMOVED alır. Otomatik geri yükleme yapılmaz.
- `PUT /api/answers/{id}`: `{body,version}`; yalnız sahibi, tamamlanmış profil ve aktif soru/cevap. Gerçek metin değişikliğinde editedAt/version güncellenir; publishedAt korunur. Değişmeyen metin no-op’tur.
- `PUT /api/answers/{id}/status`: `{deleted,version}`; yalnız sahibi ve tamamlanmış profil. Soft delete arşivlenmiş soruda da yapılabilir; geri yükleme yalnız aktif soruda aynı satırdan yapılır. İlk yayın ve son düzenleme tarihleri korunur. Eski version 409 STALE_VERSION döndürür; eski silme isteği yeniden yüklenmiş cevabı gizleyemez.

V5 `answers` tablosunu ekler: `(question_id,author_id,answer_kind)` silinmiş satırlar dahil unique, FK, body/version CHECK ve DELETE/TRUNCATE engeli. Bu aşamada answer_kind yalnız COMMUNITY olabilir; doğrulanmış ADMIN türü, doğrulama FK’sı ve günlük kota 10. adımda yeni migration ile eklenecek. Önceki migration’lar değişmez. Admin/Manager topluluk cevabı verebilir; bu doğrulanmış admin cevabı değildir ve kota tüketmez.

Bütün cevap mutasyonları soru → kullanıcı sırasıyla kilitlenir. QuestionAccessService soru görünürlüğü/arşiv kilidini diğer feature’lara sunar; cevap servisi başka feature repository’sine bağlanmaz. Arşivleme ile yeni cevap yarışı aynı soru kilidiyle çözülür. Soru soft-deleted ise liste, kendi cevabı ve bütün mutasyonlar 404 döner; çocuk cevap satırı fiziksel silinmez. Silinmiş yazar/profil adı Katılımcı olarak gösterilir. Yeni cevap POST’unun birebir tekrarı arşivde de mevcut satırı okuyabilir; yeni yayın yapamaz.
