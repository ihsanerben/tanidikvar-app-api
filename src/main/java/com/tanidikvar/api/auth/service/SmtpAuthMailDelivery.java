package com.tanidikvar.api.auth.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpAuthMailDelivery implements AuthMailDelivery {
    private final JavaMailSender sender;

    public SmtpAuthMailDelivery(JavaMailSender sender) { this.sender = sender; }

    @Override
    public void send(AuthMailMessage message) {
        var mail = new SimpleMailMessage();
        mail.setFrom(message.from());
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.text());
        sender.send(mail);
    }
}
