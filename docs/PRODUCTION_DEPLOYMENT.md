# Render ücretsiz pilot — API dağıtımı

Bu belge yalnız Render Free, Neon ve Resend kullanan **uzak pilot** içindir; başarılı uzak deploy kanıtı değildir. Güncel hazırlık, smoke ve geri dönüş adımları [ortak pilot operasyon rehberindedir](../../docs/project/PILOT_OPERATIONS.md). AWS dağıtımı için [ayrı rehbere](../deploy/aws/README.md) bakılır.

`render.yaml` Blueprint'i API'yi kurar. Render ortamında Neon direct JDBC bağlantısı (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`), sabit `JWT_SECRET`, `PILOT_MODE=true`, `COOKIE_SECURE=true`, `MAIL_PROVIDER=resend`, doğrulanmış `MAIL_FROM`, `RESEND_API_KEY`, `FRONTEND_URL` ve `CORS_ALLOWED_ORIGIN` gerekir. Secret'lar Git'e veya web ortamına yazılmaz.

Vercel, web repo kökünü Next.js uygulaması olarak dağıtır. `API_BASE_URL` Render API origin'ini, `NEXT_PUBLIC_SITE_URL` canonical web origin'ini gösterir. Tarayıcı API'ye Next.js'in aynı-origin `/api/backend/...` route handler'ı üzerinden erişir; eski SPA rewrite ve `VITE_API_BASE_URL` ayarları kullanılmaz.

Deploy sonrası API sağlık yanıtını, web üzerinden oturumu ve CSRF davranışını, gerçek e-posta teslimini, Manager erişimini ve katalog durumunu doğrula. Render Free dosya sistemi kalıcı değildir; yeni medya yükleme kapalıdır. Production öncesi backup/restore, rate limiting ve operasyon sahipliği ayrıca tamamlanır.
