package com.tanidikvar.api;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real cookie/header flow, isolated from SecurityMockMvc's csrf() repository replacement. */
@SpringBootTest(properties = "app.secure-cookies=true")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("local")
@Testcontainers
class CookieCsrfIT {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.9-alpine");
    @DynamicPropertySource static void configuration(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", postgres::getJdbcUrl); p.add("spring.datasource.username", postgres::getUsername);
        p.add("spring.datasource.password", postgres::getPassword);
        p.add("app.auth.secret", () -> Base64.getEncoder().encodeToString(new byte[48]));
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper mapper;
    @Test void realCsrfCookieHeaderAndSecureAuthenticationCookies() throws Exception {
        var csrf = mvc.perform(get("/api/auth/csrf")).andExpect(cookie().exists("XSRF-TOKEN")).andReturn().getResponse();
        assertThat(csrf.getCookie("XSRF-TOKEN").getSecure()).isTrue();
        assertThat(csrf.getCookie("XSRF-TOKEN").isHttpOnly()).isFalse();
        jdbc.update("INSERT INTO users(id,email,password_hash,email_verified_at,created_at,updated_at) VALUES (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                UUID.randomUUID(), "cookie@example.test", passwords.encode("Cookie-test-password!"));
        String body = mapper.writeValueAsString(Map.of("email", "cookie@example.test", "password", "Cookie-test-password!"));
        mvc.perform(post("/api/auth/login").contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").cookie(csrf.getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", "wrong")
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        var login = mvc.perform(post("/api/auth/login").cookie(csrf.getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", mapper.readTree(csrf.getContentAsString()).get("token").asText())
                .contentType("application/json").content(body)).andExpect(status().isOk()).andReturn().getResponse();
        for (String name : List.of("TV_ACCESS", "TV_REFRESH")) {
            assertThat(login.getCookie(name).getSecure()).isTrue(); assertThat(login.getCookie(name).isHttpOnly()).isTrue();
        }
        assertThat(login.getHeader("Cache-Control")).contains("no-store");
    }
}
