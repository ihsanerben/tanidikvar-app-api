package com.tanidikvar.api;

import com.tanidikvar.api.auth.entity.ActionPurpose;
import com.tanidikvar.api.auth.service.AuthMailEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
class MobileAuthenticationIT extends AuthenticationIT {
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder mobile(String operation, Object body) {
        return post("/api/auth/mobile/" + operation).contentType("application/json")
                .with(r -> { r.setRemoteAddr(UUID.randomUUID().toString()); return r; })
                .content(mapper.writeValueAsString(body));
    }
    private tools.jackson.databind.JsonNode mobileSession() throws Exception {
        String email=email(); register(email); verify(email);
        var response=mvc.perform(mobile("login",Map.of("email",email,"password",PASSWORD_MOBILE)))
                .andExpect(status().isOk()).andExpect(cookie().doesNotExist("TV_ACCESS"))
                .andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse();
        return mapper.readTree(response.getContentAsString());
    }
    private static final String PASSWORD_MOBILE="Test-password-123!";

    @Test void bearerRotationReuseAndLogout() throws Exception {
        var session=mobileSession();
        mvc.perform(get("/api/me").header("Authorization","Bearer "+session.get("accessToken").asText()))
                .andExpect(status().isOk());
        var rotated=mapper.readTree(mvc.perform(mobile("refresh",Map.of("refreshToken",session.get("refreshToken").asText())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(rotated.get("refreshToken").asText()).isNotEqualTo(session.get("refreshToken").asText());
        mvc.perform(mobile("refresh",Map.of("refreshToken",session.get("refreshToken").asText()))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization","Bearer "+rotated.get("accessToken").asText())).andExpect(status().isUnauthorized());
        var other=mobileSession();
        mvc.perform(mobile("logout",Map.of("refreshToken",other.get("refreshToken").asText()))).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization","Bearer "+other.get("accessToken").asText())).andExpect(status().isUnauthorized());
    }

    @Test void cookieAndMixedRequestsStillRequireCsrfAndCorsRejectsForeignOrigins() throws Exception {
        var web=account();
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(mobile("refresh",Map.of("refreshToken",refresh(web).getValue())).cookie(access(web))).andExpect(status().isForbidden());
        mvc.perform(post("/api/me/logout-all").cookie(access(web)).header("Authorization","Bearer invalid")).andExpect(status().isForbidden());
        mvc.perform(post("/api/me/logout-all").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(mobile("login",Map.of("email",email(),"password",PASSWORD_MOBILE)).header("Origin","https://untrusted.example"))
                .andExpect(status().isForbidden());
        mvc.perform(mobile("refresh",Map.of())).andExpect(status().isBadRequest());
        mvc.perform(mobile("login",Map.of("email","invalid","password","x"))).andExpect(status().isBadRequest());
    }

    @Test void concurrentRefreshHasOnlyOneWinner() throws Exception {
        var session=mobileSession(); var gate=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Integer> task=()->{gate.await();return mvc.perform(mobile("refresh",Map.of("refreshToken",session.get("refreshToken").asText()))).andReturn().getResponse().getStatus();};
            var a=executor.submit(task);var b=executor.submit(task);gate.countDown();
            assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(200,401);
        }
    }

    @Test void mobileAccountActionsAndClosureRevokeEverySession() throws Exception {
        String email=email();
        mvc.perform(mobile("register",Map.of("email",email,"password",PASSWORD_MOBILE))).andExpect(status().isAccepted());
        mvc.perform(mobile("verify-email",Map.of("token",action(email,ActionPurpose.VERIFY_EMAIL)))).andExpect(status().isNoContent());
        var web=login(email);
        var session=mapper.readTree(mvc.perform(mobile("login",Map.of("email",email,"password",PASSWORD_MOBILE))).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/me/close-account").header("Authorization","Bearer "+session.get("accessToken").asText())
                .contentType("application/json").content(mapper.writeValueAsString(Map.of("password","wrong")))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/close-account").header("Authorization","Bearer "+session.get("accessToken").asText())
                .contentType("application/json").content(mapper.writeValueAsString(Map.of("password",PASSWORD_MOBILE)))).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").cookie(access(web))).andExpect(status().isUnauthorized());
        mvc.perform(mobile("refresh",Map.of("refreshToken",session.get("refreshToken").asText()))).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM users WHERE email=?",Boolean.class,email)).isTrue();
    }

    @Test void exportActualOpenApiContract() throws Exception {
        String json=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var schema=mapper.readTree(json);
        assertThat(schema.path("paths").has("/api/auth/mobile/login")).isTrue();
        for (String path : List.of("/api/me", "/api/me/logout-all", "/api/me/close-account")) {
            String method = path.equals("/api/me") ? "get" : "post";
            var security = schema.path("paths").path(path).path(method).path("security");
            assertThat(security.toString()).contains("accessCookie", "bearerAuth");
            assertThat(security.size()).isEqualTo(2);
        }
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/mobile-openapi.json"),json);
    }

    @Test void mobilePasswordRecoveryAndLogoutAll() throws Exception {
        String email=email(); register(email); verify(email);
        var web=login(email);
        var session=mapper.readTree(mvc.perform(mobile("login",Map.of("email",email,"password",PASSWORD_MOBILE))).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/me/logout-all").header("Authorization","Bearer "+session.get("accessToken").asText())).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").cookie(access(web))).andExpect(status().isUnauthorized());
        mvc.perform(mobile("forgot-password",Map.of("email",email))).andExpect(status().isAccepted());
        assertThat(events.stream(AuthMailEvent.class).anyMatch(e -> e.email().equals(email) && e.mobile())).isTrue();
        String code=action(email,ActionPurpose.RESET_PASSWORD);
        mvc.perform(mobile("reset-password",Map.of("token",code,"password","New-password-123!"))).andExpect(status().isNoContent());
        mvc.perform(mobile("reset-password",Map.of("token",code,"password","New-password-123!"))).andExpect(status().isBadRequest());
        mvc.perform(mobile("login",Map.of("email",email,"password",PASSWORD_MOBILE))).andExpect(status().isUnauthorized());
        mvc.perform(mobile("login",Map.of("email",email,"password","New-password-123!"))).andExpect(status().isOk());
    }

    @Test void mobileRateLimitAndCorsPreflightAreExplicit() throws Exception {
        String address="mobile-rate-"+UUID.randomUUID();
        int last=0;
        for(int i=0;i<100;i++) {
            last=mvc.perform(post("/api/auth/mobile/login").with(r->{r.setRemoteAddr(address);return r;})
                    .contentType("application/json").content("{}")).andReturn().getResponse().getStatus();
            if(last==429)break;
        }
        assertThat(last).isEqualTo(429);
        mvc.perform(options("/api/me").header("Origin","http://localhost:5173")
                .header("Access-Control-Request-Method","GET").header("Access-Control-Request-Headers","Authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"));
        mvc.perform(mobile("refresh",Map.of("refreshToken",mobileSession().get("accessToken").asText()))).andExpect(status().isUnauthorized());
    }
}
