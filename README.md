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

- `GET/PUT /api/me/profile`: yalnız kendi profilin; istemciden kullanıcı/otorite atanmaz. Ad/soyad ve eğitim durumu zorunlu; öğrencide üniversite-bölüm, mezunda ayrıca yıl gerekir. Biyografi/meslek/şirket isteğe bağlıdır. Fotoğraf yükleme/kaldırma uçları aşağıda açıklanır.
- `GET /api/universities`, `/api/departments`, `/api/tags`, `/api/universities/{id}/departments`: public, aktif kayıtlar, `q/page/size`. Liste cevabı `items/page/size/totalElements`; varsayılan boyut 20, üst sınır 100.
- `POST /api/tags`: tamamlanmış profilli Admin yeni tag oluşturur. Manager gerekçeli `/api/manager/catalog/TAG` ucunu kullanır.
- `/api/manager/catalog/{kind}`: Manager listeleme/ekleme; `{kind}` UNIVERSITY, DEPARTMENT, TAG. `PUT /{id}` ad günceller; `PUT /{id}/status` `{deleted,version,reason}` ile soft delete/geri yükler. Manager listesinde `includeDeleted=true` kullanılabilir.
- `/api/manager/university-departments`: Manager `universityId` ile listeleme ve POST eşleştirme; `PUT /{id}/status` soft delete/geri yükleme.

V3 `user_profiles`, `tags`, `management_actions` oluşturur. İlk iki migration değişmez. İsimler Türkçe harf dönüşümü/boşluk normalizasyonuyla unique tutulur; aynı üniversite-bölüm çifti yeniden oluşturulamaz. Yeni seçimde pasif ebeveyn/eşleşme reddedilir; mevcut profilde aynı eğitim referansı korunabilir. Parent geri yükleme bağımsız silinmiş çocuk kaydı geri getirmez.

Profil ve katalog güncellemesinde istemci `version` gönderir. Stale form 409 STALE_VERSION alır; otomatik üzerine yazılmaz. İlk profil için version=0. Profil tamamlama eğitim rolünü günceller; ADMIN yetkisi korunur. Manager eğitim profili düzenleyemez; ayrı yönetim kimliği kullanır. InteractionPolicy etkileşimlerde Manager yasağını ve diğer katılımcılarda profil ön koşulunu uygular.

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
- `POST /api/questions/{id}/archive`: `{version}`; yalnız sahibi. Arşivleme keşif listesinden çıkarır, bağlantıdan okumayı korur; tekrar arşivleme idempotenttir. Sahibi için arşivden yeniden açma yoktur; Manager gizleme/geri yükleme uçları aşağıdadır.

`content`: title (10–200 karakter), body (isteğe bağlı, en fazla 5000), scope, universityId, universityDepartmentId, tagIds (0–5 farklı UUID). GENERAL iki eğitim alanını boş; UNIVERSITY yalnız universityId; UNIVERSITY_DEPARTMENT yalnız universityDepartmentId kullanır. Üniversite-bölüm eşleşmesinden üniversite türetilir. Başlık boşlukları normalize edilir, içerik düz metindir.

V4 `questions` ve `question_tags` tablolarını ekler; scope CHECK, FK, unique gönderim anahtarı, birleşik tag PK ve DELETE/TRUNCATE engelleri vardır. V1–V3 değişmez. Soru/tag yazımları tek transaction’dır; düzenleme/arşivleme soru → kullanıcı → katalog sırasıyla kilitlenir, birden fazla tag kimlik sırasıyla kilitlenir. Eski version 409 STALE_VERSION alır. Gerçek içerik değişikliğinde editedAt güncellenir; ilk yayın tarihi korunur. Değişmeyen içerik version/tarih artırmaz.

Yeni katalog seçimi aktif olmalıdır. Mevcut pasif eğitim/tag referansı düzenlemede aynı kalabilir; kaldırılmış tag bağı aynı satır üzerinden geri etkinleştirilir, pasif tag yeniden eklenemez. Hesabı veya profili soft-deleted yazarın adı/kimliği public cevapta açılmaz; “Katılımcı” gösterilir. Soru beğenileri ve görüntülenmeleri sonraki teslimlerdir; soru kartı sayaçları henüz eklenmedi. Topluluk cevapları aşağıdaki uçlardan yönetilir.

## Topluluk cevapları

- Public `GET /api/questions/{id}/answers`: yalnız görünür COMMUNITY cevapları; ilk yayın tarihi/id artan sırada, `page/size`, varsayılan 20, en fazla 100. `totalElements` aktif topluluk cevabı sayısını verir.
- Authenticated `GET /api/questions/{id}/my-answer`: yalnız oturum sahibinin topluluk cevabı; kaldırılmış satır dahil. Henüz cevap yoksa 204. Başka yazarın kaldırılmış içeriği public listeye verilmez.
- `POST /api/questions/{id}/answers`: `{body}`; tamamlanmış profil ve aktif soru gerekir. 10–5000 karakter düz metin. Aynı mevcut metnin tekrarı aynı cevabı döndürür; farklı ikinci cevap 409 ANSWER_EXISTS, kaldırılmış kayıt 409 ANSWER_REMOVED alır. Otomatik geri yükleme yapılmaz.
- `PUT /api/answers/{id}`: `{body,version}`; yalnız sahibi, tamamlanmış profil ve aktif soru/cevap. Gerçek metin değişikliğinde editedAt/version güncellenir; publishedAt korunur. Değişmeyen metin no-op’tur.
- `PUT /api/answers/{id}/status`: `{deleted,version}`; yalnız sahibi ve tamamlanmış profil. Soft delete arşivlenmiş soruda da yapılabilir; geri yükleme yalnız aktif soruda aynı satırdan yapılır. İlk yayın ve son düzenleme tarihleri korunur. Eski version 409 STALE_VERSION döndürür; eski silme isteği yeniden yüklenmiş cevabı gizleyemez.

V5 `answers` tablosunu ekler: `(question_id,author_id,answer_kind)` silinmiş satırlar dahil unique, FK, body/version CHECK ve DELETE/TRUNCATE engeli. V5 başlangıcında answer_kind yalnız COMMUNITY idi; V7 doğrulanmış ADMIN türü, doğrulama FK’sı ve günlük kota desteğini ekler. Önceki migration’lar değişmez. Admin topluluk cevabı verebilir; Manager katkı veremez; bu doğrulanmış admin cevabı değildir ve kota tüketmez.

Bütün cevap mutasyonları soru → kullanıcı sırasıyla kilitlenir. QuestionAccessService soru görünürlüğü/arşiv kilidini diğer feature’lara sunar; cevap servisi başka feature repository’sine bağlanmaz. Arşivleme ile yeni cevap yarışı aynı soru kilidiyle çözülür. Soru soft-deleted ise liste, kendi cevabı ve bütün mutasyonlar 404 döner; çocuk cevap satırı fiziksel silinmez. Silinmiş yazar/profil adı Katılımcı olarak gösterilir. Yeni cevap POST’unun birebir tekrarı arşivde de mevcut satırı okuyabilir; yeni yayın yapamaz.


## Admin başvuruları ve özel dosyalar

- `GET /api/me/admin-applications`: kendi başvuruların; en yeni önce, `page/size` (20 varsayılan, en fazla 100).
- `POST /api/me/admin-applications`: tek multipart gönderimi; JSON `request` parçası `{requestId,profileVersion}`, PDF `document` parçası. Tamamlanmış öğrenci/mezun profili gerekir. Aynı gönderim anahtarı/profil sürümü/dosya hash’i aynı kaydı döndürür; yalnız bir bekleyen başvuru olabilir.
- `GET /api/manager/admin-applications`: Manager listesi, isteğe bağlı `status=PENDING|APPROVED|REJECTED`, `page/size`.
- `PUT /api/manager/admin-applications/{id}/decision`: `{status,reason,version}`. PENDING kabul/ret; ret gerekçesi zorunlu. Kabul ADMIN yetkisi ve aktif doğrulama bağlantısıyla atomiktir.
- `POST /api/manager/users/{id}/revoke-admin`: `{verificationId,reason}`. Beklenen güncel doğrulamayı kontrol eder, yetkiyi kaldırır ve bekleyen başvuruları gerekçeli ret ile kapatır. Geçmiş onay değişmez. Sonuç 204.
- `GET /api/files/{id}/download`: yalnız belge sahibi veya Manager. Belge/başvuru/hesap kaldırılmışsa 404; başka Adminler erişemez. PDF attachment, no-store, nosniff ve CSP sandbox ile gönderilir.
- `GET /api/me/avatar`: `{fileId}`; fotoğraf yoksa null. `POST /api/me/avatar`: multipart `file` ile yükleme/değiştirme. `POST /api/me/avatar/remove`: soft delete, 204. Tamamlanmış profil gerekir.
- Public `GET /api/avatars/{id}`: aktif hesap/profil ve aktif fotoğraf için PNG; değiştirilen/kaldırılan eski dosyaya erişim kapanır.

Belge PDF ve en fazla 10 MB. PDF başlık/EOF kontrolü temel biçim kontrolüdür; resmi doğrulamayı Manager elle yapar. Fotoğraf JPEG/PNG, en fazla 5 MB ve 16 milyon piksel; ImageIO ile çözümlenip en uzun kenarı 512 piksel olan PNG’ye çevrilir. Orijinal metadata yayımlanmaz. Multipart istek üst sınırı 11 MB; Docker web Nginx sınırı da 11 MB.

V6 `admin_applications`, `stored_files`, `users.active_verification_application_id` ve audit `reason` alanını ekler; V1–V5 değişmez. Snapshot ve tamamlanmış kararlar immutable trigger ile korunur. Sahiplik composite FK, tek bekleyen başvuru ve tek aktif avatar partial unique ile korunur. Kabul/ret/yetki kaldırma aynı kullanıcı kilidi ve tek transaction üzerinden yürür; audit hatasında tamamı geri alınır. Ret eski aktif doğrulamayı değiştirmez. Eski karar sürümü veya değişmiş verificationId 409 döndürür.

Dosya dizini `STORAGE_DIRECTORY` (yerel `.local/storage`, Docker `/app/storage` kalıcı volume). Dosyalar rastgele UUID ile private diske yazılır; orijinal dosya adı dizin/header üretmez. Metadata ayrı transaction’da UPLOADING açılır; başarılı ilişkilendirme READY, hata FAILED + soft delete yapar. Yeni yükleme sırasında bir saatten eski yarım UPLOADING kayıtlar erişime kapalı FAILED durumuna alınır. Süreç kesintisinden sonra otomatik fiziksel dosya silme veya yeniden yayın yoktur. Kullanıcı başına saatte 20 upload kaydı sınırı vardır; bu sayaç DB’de kalır.

Fotoğraf değişiminde eski içerik ve metadata fiziksel olarak korunur. Production saklama süresi, zararlı PDF taraması, kapasite izleme ve storage yedekleme yayına hazırlık aşamasındadır. Bu sürüm tek instance/private disk kullanır.

Doğrulama: `./mvnw verify` ile 70 test geçti. Yeni 14 test belge sahipliği/Manager sınırı, snapshot, gerçek paralel gönderim/karar/yetki kaldırma, audit rollback, yarım upload kurtarma, fotoğraf dönüşümü, eski dosya erişimi ve fiziksel silme engelini kapsar.


## Admin cevapları, atama ve public profil

V7, `question_assignments` ve `answers.verification_application_id` ekler; V1–V6 değişmez. COMMUNITY ve ADMIN aynı soruda/yazarda ayrı tekil kayıtlardır. Admin cevabının ilk yayın kimliği ve onaylı doğrulama bağlantısı değişmez.

- Public `GET /api/questions/{id}/admin-answers`; `POST` aynı yolda `{body}` ile güncel doğrulanmış Admin cevabı yayımlar. Aktif soru, tamamlanmış profil, aktif atama ve günlük hak gerekir.
- `GET /api/questions/{id}/my-admin-answer`: yalnız kendi cevabın (kaldırılmış dahil) ve atama bilgisi.
- `PUT /api/questions/{id}/assignment`: `{assigned,version}`; ilk version=0. Atama/iptal hak tüketmez ve cevap silmez.
- `PUT /api/admin-answers/{id}`: `{body,version}`; güncel Admin kendi görünür cevabını aktif soruda düzenler. Atama veya yeni hak gerekmez.
- `PUT /api/admin-answers/{id}/status`: `{deleted,version}`. Eski Admin de kendi cevabını kaldırabilir. Restore güncel Admin, aktif soru ve atama gerektirir; ilk yayın/doğrulama ve kota korunur.
- `GET /api/me/admin-quota`: Türkiye günü, kullanılan/kalan hak, sabit limit=5 ve reset zamanı.
- `GET /api/me/admin-answers`, `GET /api/me/assignments`: kendi cevap/atama geçmişin, page/size.
- Public `GET /api/admins/{id}` ve `GET /api/admins/{id}/answers`: güvenli profil ve görünür cevap geçmişi. Eski Admin işaretlenir; silinmiş hesap/profil için profil 404’tür. Belge veya ret bilgisi açılmaz.

Kota ayrı sayaç kullanmaz: aynı Admin’in Türkiye günü içinde ilk yayımladığı ADMIN cevaplarını, soft-deleted satırlar dahil sayar. Soru → kullanıcı kilit sırası paralel altıncı yayını engeller. Aynı yayının tekrarı aynı kaydı döndürür; düzenleme/restore hak tüketmez. Arşivde düzenleme/restore kapalı, kaldırma açıktır. Cevap 10–5000 karakter düz metindir. Tüm listeler size=20 varsayılan, en fazla 100 kullanır.

10. adımda `./mvnw verify`: 82 test geçti; altı paralel soru yayını, aynı soruda çoklu Admin/tekrar, Türkiye gece yarısı, revocation yarışı, rollback, gizli yazar/soru ve FK/soft delete doğrulandı.


## Beğeni, görüntülenme ve sayaçlar

V8, `question_likes` ve `question_views` tablolarını ekler; V1–V7 değişmez. Her tablo soft delete, version, zaman alanları, FK ve fiziksel DELETE/TRUNCATE engeli taşır. İlk etkileşim zamanı ve kimlik alanları UPDATE trigger ile korunur.

- Public `GET /api/questions/{id}/statistics`: viewCount, likeCount, communityAnswerCount, adminAnswerCount, totalAnswerCount. Aynı `statistics` nesnesi soru liste/detay/oluşturma/güncelleme yanıtlarında da vardır; liste sayfası için tek toplu sayaç sorgusu yapılır.
- Public `POST /api/questions/{id}/views`: `{openingEventId}` UUID, CSRF zorunlu, başarılı yanıt 204. Aynı açılışın tekrarı no-op; farklı soruda aynı kimlik 409 REQUEST_CONFLICT. Ziyaretçi hesabı/IP/cookie kimliği saklanmaz. Yeni açılış yeni kayıttır; arşiv okununca sayılır, silinmiş soru 404 verir. GET istekleri kayıt yazmaz.
- Authenticated `GET /api/questions/{id}/like`: yalnız oturum sahibinin `{liked,version}` durumu; hiç kayıt yoksa false/0.
- `PUT /api/questions/{id}/like`: `{liked,version}`; tamamlanmış profil gerekir. Soru → kullanıcı kilidi ve sürüm kontrolü uygulanır. Geri alma soft delete, yeniden beğenme aynı satırı kullanır ve first_liked_at korunur. Arşivde yalnız geri alma açıktır. Eski sürüm 409 STALE_VERSION alır.

Sayaçlar silinmiş etkileşim/cevapları dışlar; silinmiş soru hiçbir public sayaç döndürmez. Etkileşimler soru içerik version değerini değiştirmez. Ayrı sayaç tablosu, ziyaretçi tekilleştirmesi veya popülerlik formülü eklenmedi. Dönem sorguları için aktif soru/zaman ve zaman/soru indexleri hazırdır.

11. adımda `./mvnw verify`: 91 test geçti. Ek 9 test public okuma/CSRF, profil ve sahiplik, tekrar/paralel tekillik, eski sürüm, ilk tarih, arşiv, soft delete, gizli ebeveyn, DB koruması ve transaction rollback davranışlarını doğrular. Admin/topluluk toplamlarının ayrımı da testlidir. OpenAPI 50 uygulama yolu içerir.


## Arama ve Popülerler

V9 yalnız `search_fold(text)` fonksiyonu ve görünür cevaplar için dönem indexi ekler; V1–V8 ve katalog tekillik normalizasyonu korunur. PostgreSQL NFKC/büyük-küçük harf/Türkçe harf katlaması `ışık` ve `isik` eşleşmesini sağlar. Metin içerme sorguları kullanılır; `%`/`_` joker değildir, yazım hatası düzeltme yoktur. Katalog seçicileri de bu eşleştirmeyi kullanır.

- `GET /api/questions`: mevcut filtrelere `q`, `departmentId`, `adminId` eklendi. q başlık/açıklama/aktif üniversite/bölüm/tag adlarında aranır. Filtreler birlikte uygulanır; normal sıra en yeni önce.
- `GET /api/popular`: aynı filtreler ve `period=DAILY|WEEKLY|MONTHLY|YEARLY` (varsayılan WEEKLY). Pencereler son 24 saat/7 gün/30 gün/365 gündür. Yanıt mevcut PageResponse<QuestionResponse>; kart sayaçları tüm zamanları kapsar.
- `GET /api/admins?q=...&page=0&size=20`: public profilleri onaylı ad üzerinden arar. Güncel Adminler önce; geçmiş Adminler activeAdmin=false. Silinmiş hesap/profil veya görünür onay geçmişi olmayan kişi listelenmez. Belge/e-posta açılmaz.

Görüntülenme/beğeni/topluluk/Admin katkı ağırlıkları 1/5/10/25. T=istek zamanı, W=pencere saniyesi, t=ilk katkı zamanı için `[T-W,T)` içindeki katkı `ağırlık * (1-(T-t)/(2W))` puan getirir. Katsayı başlangıçta 0,5, yeni katkıda 1’e yaklaşır. Soru yaşı puanı doğrudan etkilemez. Eşit puanda created_at/id azalan sıra; dönemde katkısız veya arşiv/silinmiş soru yoktur. Soft-deleted etkileşim/cevap dışlanır; yeniden etkinleştirme/düzenleme ilk zamanı yenilemez.

Liste, toplam ve toplu kart sayaçları read-only REPEATABLE_READ transaction içinde okunur. Sayfalar ayrı isteklerdir, canlı sıralama değişebilir. Genel event/özet tablosu veya cache eklenmedi. Metin taraması başlangıç için sadedir; büyük veri üzerinde arama indexi ve dönem sorgusu maliyeti yayına hazırlıkta ölçülmelidir.

12. adımda `./mvnw verify`: 101 test geçti. Yeni 10 DiscoveryIT testi Türkçe/literal arama, birleşik filtre, dört pencerenin sınırı, 1/5/10/25 ağırlıklar, doğrusal azaltma, eski soruya yeni katkı, soft delete/arşiv, ilk tarih, eşit puan/sayfalama ve public Admin gizliliğini doğrular. OpenAPI 52 uygulama yoludur. q en fazla 100 karakter; mevcut page 0–10000 ve size 1–100 sınırları korunur.

## Manager paneli ve moderasyon

- `GET /api/manager/statistics`: hesap, başvuru, soru, görünür cevap, beğeni ve detay açılışı toplamları.
- `GET /api/manager/users`: `authority=MEMBER|ADMIN|MANAGER`, `q`, `status=ALL|VISIBLE|HIDDEN`, `page`, `size`; Manager’a özel kullanıcı listesi. Parola/token dönmez.
- `PUT /api/manager/users/{id}/status`: `{hidden,version,reason}`; `hidden=true` hesabı pasifleştirir, `false` geri yükler. Manager hedefleri reddedilir.
- `GET /api/manager/content`: `kind=QUESTION|COMMUNITY|ADMIN`, `q`, `status=ALL|VISIBLE|HIDDEN`, `page`, `size`.
- `PUT /api/manager/content/{kind}/{id}/status`: `{hidden,version,reason}`; yalnız görünürlük değişir, metin ve arşiv durumu korunur.
- `GET /api/manager/actions`: `q`, `action`, `targetType`, `page`, `size`; gerekçeli yönetim geçmişi.

Tüm uçlar güncel Manager yetkisi ister; mutasyonlar CSRF korumalıdır. Liste varsayılan 20, en fazla 100 kayıt; page en fazla 10000. Arama en fazla 100, gerekçe zorunlu 1–1000 karakterdir. Eski sürüm `409 STALE_VERSION` döndürür; aynı sürüm/hedef durum tekrarı yeni audit üretmez.

V10, `answers.moderated_at` ekler; eski migration’lar ve kayıtlar korunur. Cevap `deleted_at` sahibi tarafından kaldırmayı, `moderated_at` Manager gizlemesini tutar. Public görünürlük için ikisi ve sorunun `deleted_at` alanı boş olmalıdır. Private cevap DTO’larında da `moderatedAt` bulunur; Manager gizlemesi varken sahibi düzenleme/geri yükleme yaparsa `409 ANSWER_MODERATED` alır. Sahibi cevabını ayrıca kaldırabilir. Admin günlük kotası ve ilk yayın zamanı değişmez.

Hesap pasifleştirme aynı transaction’da oturum/aksiyon token’larını iptal eder, Admin yetkisini kaldırır, bekleyen başvuruları reddeder ve audit yazar. Geri yükleme eski oturumları veya Admin yetkisini geri getirmez; eğitim/profil korunur. Aktif olmayan yazar public içerikte Katılımcı olur. Hesap pasifken önceki başvuru/dosya erişim kuralları gereği dosyaları indirilemez; Manager başvuru bilgilerini inceleyebilir, kayıtlar saklanır.

Manager işlemleri için profil tamamlanması gerekmez. Soru/cevap moderasyonu mevcut soru → aktör kilit sırasını kullanır; hesap işlemleri Manager → hedef hesap sırasıyla başvuru kararlarıyla aynı kilidi paylaşır. İşlem geçmişi yazılamazsa tüm değişiklikler rollback olur. İstatistikler tek SQL snapshot’ından hesaplanır; cache/arka plan işi eklenmedi.

Bu teslimde `./mvnw verify`: 111 test başarılı; gerçek PostgreSQL üzerinde 10 yeni yönetim testi dahil. Docker ve OpenAPI doğrulandı.

## Yerel kullanım komutları

- `./run.sh --docker`: tüm servisleri derle/başlat.
- `./run.sh --status`: durmuş olanlar dahil Docker servislerini göster; ayar veya veri değiştirmez.
- `./run.sh --stop`: Docker servislerini durdur; DB ve yükleme volume’larını korur.
- `./run.sh --help`: komut özeti.

Seçeneksiz `./run.sh` yerel Java geliştirme akışını korur. Hatalı/ek seçenekler ayar oluşturulmadan reddedilir. Status/stop mevcut `.env` ister; otomatik kurulum yapmaz. Ayrı terminalde çalışan API/web süreçlerini bu komutlar sonlandırmaz; Ctrl+C kullan.

Yerel e-postalar Mailpit `http://localhost:8025` ekranındadır. Uzun kullanımda Docker dosya volume’u ile yerel `.local/storage` dizininin farklı olduğunu dikkate al; aynı çalışma biçiminde kal. Otomatik dosya taşıma/sıfırlama yoktur.

Launcher doğrulaması: `python3 scripts/test-launcher.py` — geçici dosyalar ve sahte Docker komutuyla 4 test; gerçek `.env`, Docker veya kullanıcı kayıtlarını değiştirmez. Uygulama testlerinden bağımsızdır.

## Yerel profil kataloğu ve test temizliği

Manuel `./scripts/seed-local-catalog.sh`, 10 üniversite, 10 bölüm, 75 gerçek üniversite–bölüm eşleşmesi ve 19 tag ekler. Bu küçük başlangıç listesi resmî popülerlik sıralaması değildir. Mevcut pasif katalog kararları korunur. [Liste, kaynaklar ve yerel temizlik betiği](scripts/LOCAL_CATALOG.md). Test temizliği migration’a/başlangıca bağlı değildir; açıkça çalıştırılan atomik SQL yalnız kesin sentetik kalıpları soft delete yapar ve diğer hesap/profillerin değişmediğini doğrular.

V11 yalnız sistem taglerinin oluşturucusunu nullable yapar; migration’lar katalog verisi eklemez. Sistem katalog taglerinde `created_by` null olabilir; bunun için sahte kullanıcı oluşturulmaz. Kullanıcı tarafından oluşturulan tagler aktör kimliğini korur; mevcut yetki/audit kuralları değişmez. Yayınlanmamış eski V11/V12 seed migration’ları yerel sıfırlama kapsamında ayrıldı: Bu ayrımdan sonra V12 yönetim kimliği eklendi; 12 şema migration’ı ve ayrı manuel seed scripti bulunur.

## Kişisel yorum geçmişi

`GET /api/me/answers?page=0&size=20`, oturum sahibinin topluluk cevaplarını soru başlığıyla döndürür. İstemciden kullanıcı kimliği almaz; yalnız authenticated erişim vardır. Yanıt `PageResponse<OwnAnswerResponse>`; her öğe `answer` ve `questionTitle` içerir. Kaldırılmış/gizlenmiş kendi cevapları durumlarıyla görünür; sorunun kendisi soft-deleted ise liste ve sayımdan çıkar. Yeni migration gerekmedi; mevcut author indexi kullanılır. 114 backend testi geçti.

Topluluk cevapları ve `/api/me/answers` içindeki cevap nesnesi güncel `avatarFileId` (nullable) içerir. Avatar, aktif profil ve READY/aktif dosya üzerinden alınır; silinmiş profil/hesap kimliği açığa çıkarılmaz. Şema değişikliği yoktur.


## Manager çalışma alanı — V12 sözleşmesi

Manager soru/cevap yayımlayamaz, beğeni/atama veya Admin başvurusu yapamaz; HTTP ve servis kontrolleri birlikte uygulanır. `POST /api/questions/{id}/views` authenticated Manager için sayaç oluşturmaz. Diğer ziyaretçilerin her detay açılışı davranışı korunur.

- `GET/PUT /api/manager/account`: ayrı yönetim kimliği; PUT `{firstName,lastName,version}`. Eğitim profili gerekmez; mevcut avatar uçları kullanılabilir.
- `GET /api/manager/users/{id}`: hesap, eğitim, doğrulama ve geçmiş dahil katkı sayıları.
- `GET /api/manager/admin-applications/{id}` ve `GET /api/manager/users/{id}/applications?page=0&size=20`: başvuru detayı/geçmişi. Pasif hesabın bilgileri görünür, belge indirme kuralı değişmez.
- `GET /api/manager/questions/{id}?page=0&size=20`: gizli soru ve her iki tür cevabı içeren çalışma detayı.
- `PUT /api/manager/questions/{id}/classification`: `{scope,universityId,universityDepartmentId,tagIds,version,reason}`; yalnız kapsam/tagler değişir, yazar metni ve yayın/düzenleme zamanı korunur.
- `GET /api/manager/catalog-usage/{kind}/{id}`: bağlı profil/soru sayıları; kind UNIVERSITY/DEPARTMENT/UNIVERSITY_DEPARTMENT/TAG.
- `GET /api/manager/actions/{id}`: işlem detayı ve aktör adı. Liste q/action/targetType ile filtrelenir.

Tüm Manager katalog ekleme, ad değiştirme, eşleştirme ve durum istekleri zorunlu 1–1000 karakter reason taşır. Yeni web bu sözleşmeyle birlikte kullanılmalıdır. Mutasyonlar version/CSRF, hedef yetkisi, kilit ve transaction audit kontrollerini korur. V12 mevcut Manager adlarını `manager_profiles` tablosuna taşır; eski profil/içerik/dosyalar korunur. Genel silme veya veritabanı sıfırlama panel ucu yoktur.

Güncel Manager teslimi doğrulaması: 123 backend, 109 frontend testi ve 8 masaüstü + 8 mobil senaryo geçti. Son PDF/katalog değişiklikleri iki cihazda yeniden doğrulandı (6/6). Lint/build ve 67 yollu OpenAPI kontrolü başarılı. E2E ayrı Compose test ortamında çalıştı; günlük veritabanına test verisi yazılmadı.


## Profil bağlantıları ve cevap görünümü

`PUT /api/me/profile` artık opsiyonel `linkedinUrl` ve `portfolioUrl` alanlarını alır. Bağlantılar doğrulanır; LinkedIn alanı linkedin.com altında olmalıdır. `GET /api/profiles/{id}` public eğitim/biyografi/iş bilgileri, avatar ve bu bağlantıları döndürür; özel hesap ve belge bilgileri dönmez. Avatar ilk profil kaydından önce yüklenebilir.

Admin soru oluşturamaz; yalnız sorulara doğrulanmış Admin cevabı verebilir. Admin Panel’de cevaplar “Cevaplarım” altında, topluluk geçmişi “Yorumlarım” altında bulunur; Sorularım ve Admin başvurusu bağlantıları Admin’den kaldırılmıştır. Soru detayında Admin/Topluluk cevapları sekmeleri, varsayılan Admin sekmesi ve kalp ikonlu idempotent beğeni düğmesi vardır. V13 migration uygulanmalıdır.
