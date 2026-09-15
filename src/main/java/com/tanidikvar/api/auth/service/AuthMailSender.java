package com.tanidikvar.api.auth.service;

import com.tanidikvar.api.auth.entity.ActionPurpose;
import java.net.URI;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AuthMailSender {
    private static final Logger log = LoggerFactory.getLogger(AuthMailSender.class);
    private final AuthMailDelivery delivery;
    private final ThreadPoolTaskExecutor executor;
    private final String frontendUrl;
    private final String from;
    public AuthMailSender(AuthMailDelivery delivery, ThreadPoolTaskExecutor authMailExecutor,
            @Value("${app.mail.frontend-url}") String frontendUrl, @Value("${app.mail.from}") String from) {
        this.delivery = delivery; this.executor = authMailExecutor;
        var uri = URI.create(frontendUrl);
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getRawQuery() != null || uri.getRawFragment() != null)
            throw new IllegalArgumentException("Invalid mail frontend URL");
        this.frontendUrl = frontendUrl.replaceAll("/$", ""); this.from = from;
    }
    @TransactionalEventListener
    public void afterCommit(AuthMailEvent event) {
        try { executor.execute(() -> deliver(event)); }
        catch (org.springframework.core.task.TaskRejectedException e) { log.warn("auth_mail_queue_full"); }
    }
    private void deliver(AuthMailEvent event) {
        boolean verify = event.purpose() == ActionPurpose.VERIFY_EMAIL;
        String subject = verify ? "TanıdıkVar — E-posta adresini doğrula" : "TanıdıkVar — Şifreni yenile";
        // Fragment keeps the secret out of frontend access logs and HTTP Referer.
        String url = frontendUrl + (verify ? "/verify-email" : "/reset-password") + "#token=" + event.token();
        String text = (verify ? "E-posta adresini doğrulamak için (24 saat geçerli):" : "Şifreni yenilemek için (30 dakika geçerli):")
                + "\n\n" + url + "\n\nSevgiler,\nTanıdıkVar Ekibi";
        String action = verify ? "E-posta adresimi doğrula" : "Şifremi yenile";
        String intro = verify ? "E-posta adresini doğrulamak için aşağıdaki butonu kullan. Bu bağlantı 24 saat geçerlidir."
                : "Şifreni yenilemek için aşağıdaki butonu kullan. Bu bağlantı 30 dakika geçerlidir.";
        String safeUrl = url.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
        String html = "<div style=\"margin:0;padding:32px 16px;background:#f4f5ef;font-family:Arial,sans-serif;color:#173d31;line-height:1.6\">"
                + "<div style=\"max-width:560px;margin:0 auto;overflow:hidden;border:1px solid #dbe2d5;border-radius:18px;background:#fff;box-shadow:0 8px 28px rgba(23,61,49,.08)\">"
                + "<div style=\"padding:22px 28px;background:#173d31;color:#fff;font-size:22px;font-weight:800;letter-spacing:-.4px\">tanıdıkvar</div>"
                + "<div style=\"padding:30px 28px\"><h1 style=\"margin:0 0 14px;font-size:24px;line-height:1.3\">" + (verify ? "Aramıza hoş geldin!" : "Yeni şifreni oluşturalım") + "</h1>"
                + "<p style=\"margin:0;color:#50645b\">" + intro + "</p>"
                + "<p style=\"margin:28px 0\"><a href=\"" + safeUrl + "\" target=\"_blank\" rel=\"noopener noreferrer\" "
                + "style=\"display:inline-block;padding:14px 22px;border-radius:10px;background:#2f8f57;color:#fff;text-decoration:none;font-weight:700\">"
                + action + "</a></p>"
                + "<p style=\"margin:26px 0 0;color:#50645b\">Sevgiler,<br><strong>TanıdıkVar Ekibi</strong></p></div></div></div>";
        try { delivery.send(new AuthMailMessage(from, event.email(), subject, text, html)); }
        catch (RuntimeException e) { log.warn("auth_mail_delivery_failed purpose={} errorType={} message={}", event.purpose(), e.getClass().getSimpleName(), e.getMessage()); }
    }
}
