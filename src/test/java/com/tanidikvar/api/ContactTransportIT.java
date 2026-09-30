package com.tanidikvar.api;

import com.tanidikvar.api.auth.service.AuthMailDelivery;
import com.tanidikvar.api.auth.service.AuthMailMessage;
import jakarta.servlet.http.Cookie;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("local")
@Testcontainers
class ContactTransportIT {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.9-alpine");
    @DynamicPropertySource static void configure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("app.auth.secret", () -> Base64.getEncoder().encodeToString(new byte[48]));
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean AuthMailDelivery delivery;
    private MockHttpServletRequestBuilder contact() {
        return post("/api/contact").contentType("application/json")
            .with(request -> {request.setRemoteAddr(UUID.randomUUID().toString());return request;})
            .content("""
                {"name":"Mobil test","email":"mobile@example.test","subject":"İletişim testi","message":"Mobil istemciden gelen test mesajı."}
                """);
    }
    @Test void anonymousNativeJsonReachesMailDelivery() throws Exception {
        mvc.perform(contact()).andExpect(status().isAccepted());
        var message = org.mockito.ArgumentCaptor.forClass(AuthMailMessage.class);
        verify(delivery).send(message.capture());
        assertThat(message.getValue().text()).contains("Mobil istemciden gelen test mesajı.", "mobile@example.test");
    }
    @Test void webCookiesStillRequireRealCsrfCookieAndHeader() throws Exception {
        for (String name : new String[]{"TV_ACCESS", "TV_REFRESH"}) {
            mvc.perform(contact().cookie(new Cookie(name,"stale"))).andExpect(status().isForbidden());
            mvc.perform(contact().cookie(new Cookie(name,"stale")).header("Authorization","Bearer invalid"))
                .andExpect(status().isForbidden());
        }
        verify(delivery,never()).send(any());
        var csrf=mvc.perform(get("/api/auth/csrf")).andReturn().getResponse();
        mvc.perform(contact().cookie(csrf.getCookie("XSRF-TOKEN"),new Cookie("TV_REFRESH","stale"))
            .header("X-XSRF-TOKEN",mapper.readTree(csrf.getContentAsString()).get("token").asText()))
            .andExpect(status().isAccepted());
        verify(delivery).send(any());
    }
    @Test void untrustedOriginFormPostsAndInvalidFieldsDoNotSendMail() throws Exception {
        mvc.perform(contact().header("Origin","https://untrusted.example")).andExpect(status().isForbidden());
        mvc.perform(contact().contentType("text/plain")).andExpect(status().isForbidden());
        mvc.perform(contact().contentType("application/x-www-form-urlencoded")).andExpect(status().isForbidden());
        mvc.perform(contact().content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/questions").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        verify(delivery,never()).send(any());
    }
    @Test void nativeContactIsStillRateLimited() throws Exception {
        String address=UUID.randomUUID().toString();
        for(int attempt=0;attempt<10;attempt++) mvc.perform(contact().with(request->{request.setRemoteAddr(address);return request;}))
            .andExpect(status().isAccepted());
        mvc.perform(contact().with(request->{request.setRemoteAddr(address);return request;})).andExpect(status().isTooManyRequests());
        verify(delivery,times(10)).send(any());
    }
}
