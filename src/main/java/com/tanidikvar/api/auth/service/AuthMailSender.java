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
        try { delivery.send(new AuthMailMessage(from, event.email(), subject, text)); }
        catch (RuntimeException e) { log.warn("auth_mail_delivery_failed purpose={}", event.purpose()); }
    }
}
