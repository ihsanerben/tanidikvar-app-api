# AWS EC2 dağıtımı

Bu klasör TanıdıkVar API'yi tek bir EC2 sunucusunda Docker ile çalıştırır.
Neon PostgreSQL ve Resend aynen kalır. Caddy, `api.tanidikvar.online` için
HTTPS sertifikasını alır ve istekleri API container'ına iletir.

## AWS'de kurulacak kaynaklar

- Bölge: `eu-central-1` (Frankfurt)
- EC2: Ubuntu 24.04 LTS, `t3a.small` (2 GB RAM) tercih edilir. `t3.micro`
  küçük pilot için çalışabilir fakat Java uygulaması için daha dar pay bırakır.
- EBS: en az 20 GB `gp3`; PostgreSQL verisi ve geçmiş medya uyumluluk alanı bu diskte
  `deploy/aws/data` altında kalır.
- Elastic IP: DNS kaydının sabit bir IP'ye işaret etmesi için.
- Güvenlik grubu: internetten yalnız `80/tcp` ve `443/tcp`; yönetim için SSH
  kullanılacaksa `22/tcp` yalnız kendi IP adresinden. Session Manager
  kullanılacaksa 22 açılmaz ve instance rolüne `AmazonSSMManagedInstanceCore`
  eklenir.

API'nin güncel canlı ortamı AWS EC2'dir. Vercel `/api` isteklerini
`https://api.tanidikvar.online` adresine yönlendirir.

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
   yeni dosya yüklemesi içermeyen Tanıdık başvurusunu dene.
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

## GitHub Actions ile otomatik güncelleme

`.github/workflows/deploy-aws.yml`, `main` dalına her push sonrasında önce tam
Maven doğrulamasını çalıştırır. Testler başarılıysa GitHub OIDC ile AWS rolünü
üstlenir ve Systems Manager Run Command üzerinden bu sunucuda aynı güvenli
güncelleme akışını yürütür. Statik AWS access key veya production `.env` içeriği
GitHub'a eklenmez.

GitHub reposunda `Settings > Environments` altında `production` ortamını ve
`Settings > Secrets and variables > Actions > Variables` altında şunları ekle:

- `AWS_REGION`: instance'ın bölgesi; örneğin `eu-central-1`
- `AWS_INSTANCE_ID`: EC2 instance kimliği; örneğin `i-0123456789abcdef0`
- `AWS_DEPLOY_ROLE_ARN`: GitHub'ın üstleneceği IAM rolünün ARN'i

EC2 instance profiline `AmazonSSMManagedInstanceCore` politikası bağlı olmalı ve
instance Systems Manager `Fleet Manager > Managed nodes` ekranında çevrimiçi
görünmelidir. GitHub OIDC rolünün trust policy'si yalnız
`ihsanerben/tanidikvar-app-api` reposunun `main` dalına izin vermelidir. Rolün
izin politikası yalnız hedef instance üzerinde `ssm:SendCommand` ve gönderilen
komutun sonucunu okumak için gereken `ssm:GetCommandInvocation` yetkilerini
içermelidir.

Sunucudaki repo yolu `/home/ubuntu/tanidikvar-app-api` olmalıdır. Repo farklı
bir yerdeyse workflow içindeki iki yol birlikte değiştirilir. İlk kurulumdan
sonra GitHub'da `Actions > Test and deploy API to AWS > Run workflow` ile elle
bir deneme yapılır; sonraki başarılı `main` push'ları otomatik deploy edilir.
