package com.tanidikvar.api.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "resend")
public class ResendAuthMailDelivery implements AuthMailDelivery {
    private final RestClient client;
    private final String endpoint;
    private final String apiKey;

    public ResendAuthMailDelivery(RestClient.Builder builder,
            @Value("${app.mail.resend-endpoint}") String endpoint,
            @Value("${app.mail.resend-api-key}") String apiKey) {
        if (apiKey.isBlank()) throw new IllegalArgumentException("RESEND_API_KEY is required when MAIL_PROVIDER=resend");
        this.client = builder.build();
        this.endpoint = endpoint;
        this.apiKey = apiKey;
    }

    @Override
    public void send(AuthMailMessage message) {
        client.post().uri(endpoint).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(new ResendEmailRequest(message.from(), message.to(), message.subject(), message.text(), message.html()))
                .retrieve().toBodilessEntity();
    }

    private record ResendEmailRequest(String from, String to, String subject, String text, String html) {}
}
