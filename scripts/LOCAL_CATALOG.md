# Yerel başlangıç kataloğu

Elle çalıştırılan `scripts/seed-local-catalog.sql`, 10 tanınan üniversite ve 10 yaygın bölümden oluşan küçük bir başlangıç kataloğu ekler. Bu liste ölçülmüş veya resmî bir “en popüler 10” sıralaması değildir. Kayıtlar normal katalog kayıtlarıdır; Manager tarafından yönetilebilir. Aynı normalize ad varsa mevcut kayıt kullanılır; pasif kayıt/eşleşme otomatik geri açılmaz.

Bölümler: Bilgisayar Mühendisliği, Elektrik-Elektronik Mühendisliği, Endüstri Mühendisliği, İnşaat Mühendisliği, Makine Mühendisliği, Mimarlık, Tıp, Hukuk, Psikoloji, İşletme.

Profil iki basit seçim kutusu kullanır; arama ve önceki/sonraki yoktur. Her liste ilk 10 aktif seçeneği alır. Bölümler seçilen üniversiteye bağlıdır: başlangıçtaki 10 bölümün yalnız o üniversitede bulunanları gösterilir. Mevcut profilin liste dışında/pasif bir seçimi varsa geçmiş bilgi korunur ve yeni seçeneklerden biriyle değiştirilebilir. Diğer ekranlarda da arama kutusu yerine hazır seçim listesi kullanılır; liste seçenekleri sayfalı API üzerinden tamamlanır.

## Üniversiteler ve eşleşme kaynakları

Aşağıdaki resmî akademik sayfalar 6 Eylül 2026 tarihinde kontrol edildi. Eğitim dili varyantları ayrı bölüm değildir; Makina/Makine ve Elektrik ve Elektronik/Elektrik-Elektronik adları katalogda ortak adla gösterilir. Elektrik veya Elektronik ve Haberleşme ayrı bölümlerdir; Elektrik-Elektronik yerine eşleştirilmez. Liste kapsamlı bir program/kontenjan rehberi değildir.

| Üniversite | Başlangıç listesinden bölüm sayısı | Kaynak |
|---|---:|---|
| Boğaziçi Üniversitesi | 7 | [Ders kataloğu](https://katalog.bogazici.edu.tr/) |
| Orta Doğu Teknik Üniversitesi | 8 | [Lisans programları](https://www.metu.edu.tr/tr/lisans-programlari) |
| İstanbul Teknik Üniversitesi | 5 | [Tüm bölümler](https://www.itu.edu.tr/tum-bolumler) |
| Hacettepe Üniversitesi | 9 | [Lisans/önlisans öğretimi](https://www.hacettepe.edu.tr/ogretim/lisansonlisans_ogretim) |
| Ankara Üniversitesi | 8 | [Mühendislik bölümleri](https://www.eng.ankara.edu.tr/bolumler/), [öğretim programları](https://oidb.ankara.edu.tr/ogretim-programlari-ve-ders-icerikleri/) |
| İstanbul Üniversitesi | 5 | [Akademik birimler](https://tercihim.istanbul.edu.tr/index.php/academics/), [bilgisayar mühendisliği](https://tercihim.istanbul.edu.tr/index.php/bbtf/), [psikoloji](https://tanitimedebiyat.istanbul.edu.tr/tr/content/bolumlerimiz-1/psikoloji) |
| Ege Üniversitesi | 7 | [Fakülteler](https://ege.edu.tr/tr-11372/fakulteler.html), [mühendislik bölümleri](https://muhfak.ege.edu.tr/), [işletme](https://iibf.ege.edu.tr/index.php), [psikoloji](https://ebp.ege.edu.tr/DereceProgramlari/Detay/1/106/2701/932001) |
| Marmara Üniversitesi | 10 | [Lisans programları](https://meobs.marmara.edu.tr/Program/programlar-hakkinda-bilgi/lisans-900002) |
| Yıldız Teknik Üniversitesi | 6 | [Fakülteler ve bölümler](https://yildiz.edu.tr/egitim/akademik-birimler/fakulteler) |
| Dokuz Eylül Üniversitesi | 10 | [Akademik birimler](https://fen.deu.edu.tr/tr/cift-anadal-yandal/), [bölüm temsilcilikleri](https://kariyer.deu.edu.tr/tr/temsilcilerimiz/) |

Aynı script ayrıca ürün planındaki 19 gerçek konu tagini ekler. Sistem taglerinde oluşturucu null’dır; sahte hesap oluşturulmaz.

## Manuel yükleme

Migration’lar yalnız şemayı kurar; uygulamanın açılması veri eklemez. V11 yalnız sistem taglerinin boş oluşturucuya sahip olabilmesini sağlar.

API klasöründe, backend migration’ları tamamlandıktan sonra:

```sh
./scripts/seed-local-catalog.sh
```

Script yalnız Compose içindeki yerel PostgreSQL servisine bağlanır. Tüm eklemeler tek transaction’da yapılır; hata halinde geri alınır. Tekrar çalıştırmak kayıt çoğaltmaz ve pasif kayıtları açmaz. Kullanıcı, soru, cevap veya başvuru oluşturmaz. SQL dosyası bir SQL istemcisinden de transaction içinde çalıştırılabilir.

6 Eylül 2026’da kullanıcı talebiyle yerel veritabanı yedeksiz sıfırlandı. Katalog otomatik yüklenmez; yukarıdaki komut elle çalıştırılır.

## Eski test verilerini soft delete ile temizleme

`cleanup-local-test-data.sql` uygulama başlangıcına bağlı değildir. Yalnız yerel kullanımda, hedef veriler incelendikten sonra elle çalıştırılır. Tek atomik SQL bloğu ve 5 saniyelik kilit zaman aşımı kullanır. Hata halinde tamamı geri alınır; tekrar çalıştırmak pasif satırların sürümünü/tarihini değiştirmez.

Yalnız şu kesin kalıplar hedeflenir:

- `browser-<UUID>@example.test` ve `browser-profile-<UUID>@example.test` hesapları ve bunlara bağlı içerik, başvuru, dosya, oturum, token, etkileşim ve işlem geçmişi.
- `Test Üniversitesi <8 hex>` / `Başvuru Üniversitesi <8 hex>` ve `Test Bölümü <8 hex>` / `Başvuru Bölümü <8 hex>` katalogları ve eşleşmeleri.
- Sentetik hesapların oluşturduğu `Konu <8 hex>` / `Test Kampüs <8 hex>` tagleri.

Test sorularının altındaki etkileşimler de pasifleştirilir. Diğer hesaplar ve profiller değişmez; blok sonunda bunun otomatik kontrolü vardır. Gerçek profildeki eski test eğitim referansı tarihsel olarak korunur; kullanıcı profilden yeni üniversite/bölüm seçebilir. Veritabanı satırları ve yüklenen dosyaların fiziksel içeriği silinmez. Mailpit gelen kutusu bu SQL işleminin kapsamı dışındadır.

API repo kökünde, yalnız incelenmiş yerel Docker veritabanı için:

```sh
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1' < scripts/cleanup-local-test-data.sql
```

Önizleme için aynı SQL içeriği `BEGIN;` ve `ROLLBACK;` arasında çalıştırılır. Tarayıcı testleri tekrar sentetik veri oluşturur; yerel denemeye dönmeden önce temizlik yeniden uygulanabilir. Backend entegrasyon testleri ayrı Testcontainers veritabanlarını kullanır.
