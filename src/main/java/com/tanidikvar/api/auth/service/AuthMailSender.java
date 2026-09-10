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
                + "\n\n" + url + "\n\nBu işlemi sen başlatmadıysan bu e-postayı yok sayabilirsin.";
        String action = verify ? "E-posta adresimi doğrula" : "Şifremi yenile";
        String intro = verify ? "E-posta adresini doğrulamak için aşağıdaki butonu kullan. Bu bağlantı 24 saat geçerlidir."
                : "Şifreni yenilemek için aşağıdaki butonu kullan. Bu bağlantı 30 dakika geçerlidir.";
        String safeUrl = url.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
        String html = "<div style=\"font-family:Arial,sans-serif;color:#173d31;line-height:1.6\"><p>" + intro + "</p>"
                + "<p style=\"margin:28px 0\"><a href=\"" + safeUrl + "\" target=\"_blank\" rel=\"noopener noreferrer\" "
                + "style=\"display:inline-block;padding:13px 22px;border-radius:9px;background:#153f35;color:#fff;text-decoration:none;font-weight:700\">"
                + action + "</a></p><p style=\"color:#637369;font-size:13px\">Bu işlemi sen başlatmadıysan bu e-postayı yok sayabilirsin.</p></div>";
        try { delivery.send(new AuthMailMessage(from, event.email(), subject, text, html)); }
        catch (RuntimeException e) { log.warn("auth_mail_delivery_failed purpose={} errorType={} message={}", event.purpose(), e.getClass().getSimpleName(), e.getMessage()); }
    }
}
