package com.tanidikvar.api;

import com.tanidikvar.api.auth.service.AuthMailMessage;
import com.tanidikvar.api.auth.service.ResendAuthMailDelivery;
import com.tanidikvar.api.config.RestClientConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendAuthMailDeliveryTest {
    @Test
    void providesTheRestClientBuilderRequiredByTheProductionDelivery() {
        try (var context = new AnnotationConfigApplicationContext(RestClientConfiguration.class)) {
            context.getBean(RestClient.Builder.class);
        }
    }

    @Test
    void sendsTheTransactionalMessageThroughTheHttpsApi() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var delivery = new ResendAuthMailDelivery(builder, "https://resend.test/emails", "re_test_key");
        server.expect(requestTo("https://resend.test/emails"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_test_key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"from":"TanıdıkVar <noreply@example.com>","to":"student@example.com","subject":"Doğrula","text":"https://app.example/verify-email#token=test"}
                        """))
                .andRespond(withSuccess());

        delivery.send(new AuthMailMessage("TanıdıkVar <noreply@example.com>", "student@example.com", "Doğrula",
                "https://app.example/verify-email#token=test"));

        server.verify();
    }
}
