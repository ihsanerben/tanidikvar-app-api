package com.tanidikvar.api.auth.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpAuthMailDelivery implements AuthMailDelivery {
    private final JavaMailSender sender;

    public SmtpAuthMailDelivery(JavaMailSender sender) { this.sender = sender; }

    @Override
    public void send(AuthMailMessage message) {
        try {
            var mail = sender.createMimeMessage();
            var helper = new MimeMessageHelper(mail, true, "UTF-8");
            helper.setFrom(message.from()); helper.setTo(message.to()); helper.setSubject(message.subject());
            helper.setText(message.text(), message.html()); sender.send(mail);
        } catch (jakarta.mail.MessagingException e) { throw new org.springframework.mail.MailPreparationException(e); }
    }
}
