# AWS EC2 dağıtımı

Bu klasör TanıdıkVar API'yi tek bir EC2 sunucusunda Docker ile çalıştırır.
Neon PostgreSQL ve Resend aynen kalır. Caddy, `api.tanidikvar.online` için
HTTPS sertifikasını alır ve istekleri API container'ına iletir.

## AWS'de kurulacak kaynaklar

- Bölge: `eu-central-1` (Frankfurt)
- EC2: Ubuntu 24.04 LTS, `t3a.small` (2 GB RAM) tercih edilir. `t3.micro`
  küçük pilot için çalışabilir fakat Java uygulaması için daha dar pay bırakır.
- EBS: en az 20 GB `gp3`; API'nin isteğe bağlı avatar/PDF dosyaları bu diskte
  `deploy/aws/data` altında kalır.
- Elastic IP: DNS kaydının sabit bir IP'ye işaret etmesi için.
- Güvenlik grubu: internetten yalnız `80/tcp` ve `443/tcp`; yönetim için SSH
  kullanılacaksa `22/tcp` yalnız kendi IP adresinden. Session Manager
  kullanılacaksa 22 açılmaz ve instance rolüne `AmazonSSMManagedInstanceCore`
  eklenir.

Render servisinin çalışır halde kalması geri dönüş içindir. AWS health ve web
akışları doğrulanmadan Render silinmez veya durdurulmaz.

## Sunucuda ilk kurulum

EC2'ye bağlandıktan sonra Docker Engine ve Compose plugin'i kurulur. Ubuntu
üzerinde:

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl git docker.io docker-compose-v2
sudo usermod -aG docker "$USER"
```

Oturumu kapatıp yeniden açtıktan sonra repoyu sunucuda private bir dizine al:

```bash
git clone https://github.com/ihsanerben/tanidikvar-app-api.git
cd tanidikvar-app-api/deploy/aws
cp .env.production.example .env.production
chmod 600 .env.production
```

`.env.production` içine yalnız sunucuda gerçek Neon/Resend değerlerini gir.
`JWT_SECRET` üretmek için:

```bash
openssl rand -base64 48
```

Sonra API'yi başlat:

```bash
./deploy.sh
curl -fsS https://api.tanidikvar.online/api/health
```

## DNS ve web geçişi

1. Hostinger DNS'e `A` kaydı ekle: `api` → EC2 Elastic IP, TTL `60`.
2. DNS yayıldıktan sonra Caddy sertifikayı otomatik alır; health URL'si JSON
   dönmelidir.
3. Web reposundaki Vercel rewrite hedefini Render adresinden
   `https://api.tanidikvar.online` adresine değiştir ve Vercel deployunu yap.
4. Tarayıcıdan kayıt, doğrulama e-postası, giriş, sayfa yenileme, çıkış ve
   dosyasız/dosyalı Admin başvurusunu dene.
5. Sorun varsa Vercel rewrite'ını tekrar Render adresine al; Neon ve Resend
   değişmediği için geri dönüş yalnız bu yönlendirmedir.

## Güncelleme ve yedek

Sunucuda yeni kodu almak için:

```bash
git pull --ff-only origin main
cd deploy/aws
./deploy.sh
```

`deploy/aws/data` EBS üzerinde kalır fakat tek başına yedek değildir. İlk
kalıcı kullanım öncesinde EBS snapshot planı ve Neon için ayrı restore denemesi
oluşturulmalıdır.
