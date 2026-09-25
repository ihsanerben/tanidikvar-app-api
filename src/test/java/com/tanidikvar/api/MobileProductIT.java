package com.tanidikvar.api;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Product requests use only Bearer auth: no cookie and no CSRF test postprocessor. */
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@org.springframework.test.context.ActiveProfiles("local")
@org.testcontainers.junit.jupiter.Testcontainers
class MobileProductIT {
    @org.testcontainers.junit.jupiter.Container
    static final org.testcontainers.postgresql.PostgreSQLContainer postgres =
            new org.testcontainers.postgresql.PostgreSQLContainer("postgres:17.9-alpine");
    @org.springframework.test.context.DynamicPropertySource
    static void configuration(org.springframework.test.context.DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("app.auth.secret", () -> java.util.Base64.getEncoder().encodeToString(new byte[48]));
    }
    @org.springframework.beans.factory.annotation.Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired tools.jackson.databind.ObjectMapper mapper;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @org.springframework.beans.factory.annotation.Autowired com.tanidikvar.api.auth.service.AuthenticationService auth;
    record Actor(UUID id, jakarta.servlet.http.Cookie cookie) {}
    Actor actor(String role) {
        UUID id = UUID.randomUUID();String email = id + "@example.test";
        jdbc.update("INSERT INTO users(id,email,password_hash,authority,email_verified_at,created_at,updated_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                id,email,passwords.encode("Testing-password!"),role);
        return new Actor(id,new jakarta.servlet.http.Cookie("TV_ACCESS",auth.login(email,"Testing-password!").accessToken()));
    }
    Actor member(String role) throws Exception {
        var actor = actor(role);
        if (!role.equals("MANAGER")) {
            result(write("PUT","/api/me/profile",actor,Map.of("firstName","Ada","lastName","Yılmaz","educationStatus","YKS_ADAYI","version",0)),200);
        }
        return actor;
    }
    JsonNode create(Actor actor,String kind,String name) throws Exception {
        return result(write("POST","/api/manager/catalog/"+kind,actor,Map.of("name",name)),201);
    }
    Map<String,Object> content(String title) {
        return Map.of("title",title,"scope","GENERAL","tagIds",java.util.List.of());
    }
    JsonNode question(Actor actor,Map<String,Object> content) throws Exception {
        return result(write("POST","/api/questions",actor,Map.of("requestId",UUID.randomUUID(),"content",content)),201);
    }

    MockHttpServletRequestBuilder write(String method, String path, Actor actor, Object body) {
        if (path.startsWith("/api/manager/catalog") && body instanceof Map<?, ?> values) {
            var enriched = new HashMap<String, Object>();
            values.forEach((key, value) -> enriched.put(key.toString(), value));
            enriched.put("reason", "İzole mobil entegrasyon testi");
            body = enriched;
        }
        return (method.equals("PUT") ? put(path) : post(path))
                .header("Authorization", "Bearer " + actor.cookie().getValue())
                .contentType("application/json").content(mapper.writeValueAsString(body));
    }

    private JsonNode result(MockHttpServletRequestBuilder request, int status) throws Exception {
        return mapper.readTree(mvc.perform(request).andExpect(status().is(status))
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void bearerProductJourneyPreservesOwnershipVersionsAndCatalogContracts() throws Exception {
        var owner = member("MEMBER");
        var contributor = member("MEMBER");
        var manager = member("MANAGER");
        var university = create(manager, "UNIVERSITY", "Mobil Test Üniversitesi " + UUID.randomUUID());
        String universityId = university.path("id").asText();
        mvc.perform(get("/api/universities").param("q", "Mobil Test").param("page", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
        mvc.perform(get("/api/universities/" + universityId)).andExpect(status().isOk());
        mvc.perform(get("/api/universities/" + universityId + "/catalog-statistics")).andExpect(status().isOk());
        mvc.perform(get("/api/catalog-programs").param("universityId", universityId)).andExpect(status().isOk());
        mvc.perform(get("/api/tanidiklar").param("universityId", universityId)).andExpect(status().isOk());

        var question = question(owner, content("Mobil uygulamada üniversite hayatı nasıl?"));
        String id = question.path("id").asText();
        var answer = result(write("POST", "/api/questions/" + id + "/answers", contributor,
                Map.of("body", "Üniversitede kulüpler aracılığıyla yeni insanlarla tanışabilirsin.")), 201);
        String answerId = answer.path("id").asText();
        result(write("PUT", "/api/questions/" + id + "/like", contributor, Map.of("liked", true, "version", 0)), 200);
        mvc.perform(write("PUT", "/api/questions/" + id + "/like", contributor, Map.of("liked", false, "version", 0)))
                .andExpect(status().isConflict());
        result(write("PUT", "/api/answers/" + answerId + "/like", owner, Map.of("liked", true)), 200);
        var comment = result(write("POST", "/api/answers/" + answerId + "/comments", owner,
                Map.of("body", "Yanıtın için teşekkür ederim.")), 201);
        String commentPath = "/api/answers/" + answerId + "/comments/" + comment.path("id").asText();
        mvc.perform(write("PUT", commentPath, contributor, Map.of("body", "Başkasının yorumunu değiştiremem.", "version", 0)))
                .andExpect(status().isForbidden());
        result(write("PUT", commentPath, owner, Map.of("body", "Ayrıntılı yanıtın için teşekkür ederim.", "version", 0)), 200);
        mvc.perform(write("PUT", "/api/questions/" + id + "/best-answer", owner, Map.of("answerId", answerId)))
                .andExpect(status().isNoContent());
        mvc.perform(write("PUT", "/api/questions/" + id, contributor,
                Map.of("content", content("Yetkisiz soru düzenleme denemesi"), "version", question.path("version").asLong())))
                .andExpect(status().isForbidden());
        result(write("POST", "/api/answers/" + answerId + "/reports", owner,
                Map.of("reason", "Bu içerik için moderasyon incelemesi talep ediyorum.")), 201);
        result(write("PUT", "/api/answers/" + answerId + "/status", contributor,
                Map.of("deleted", true, "version", answer.path("version").asLong())), 200);
        result(write("PUT", "/api/answers/" + answerId + "/status", contributor,
                Map.of("deleted", false, "version", answer.path("version").asLong() + 1)), 200);

        var preferences = result(get("/api/me/notification-preferences")
                .header("Authorization", "Bearer " + owner.cookie().getValue()), 200);
        result(write("PUT", "/api/me/notification-preferences", owner, Map.of("inAppEnabled", true,
                "emailEnabled", false, "emailFrequency", "NEVER", "questionRoutingEnabled", true,
                "version", preferences.path("version").asLong())), 200);
        var profile = result(get("/api/me/profile").header("Authorization", "Bearer " + owner.cookie().getValue()), 200);
        result(write("POST", "/api/me/tanidik-applications", owner, Map.of("requestId", UUID.randomUUID(),
                "profileVersion", profile.path("version").asLong(),
                "coverLetter", "Deneyimlerimle üniversite adaylarına yardımcı olmak istiyorum.")), 201);
        mvc.perform(get("/api/profiles/" + owner.id())).andExpect(status().isOk());
        mvc.perform(get("/api/profiles/" + contributor.id() + "/comments/community"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(answerId));
    }

    @Test
    void commentWritesRespectParentProfileAndConcurrentVersions() throws Exception {
        var owner = member("MEMBER");
        var question = question(owner, content("Yorum güvenliği için örnek soru"));
        String id = question.path("id").asText();
        var answer = result(write("POST", "/api/questions/" + id + "/answers", owner,
                Map.of("body", "Bu soru için yeterince ayrıntılı örnek yanıt.")), 201);
        String path = "/api/answers/" + answer.path("id").asText() + "/comments";
        mvc.perform(write("POST", path, actor("MEMBER"), Map.of("body", "Eksik profil yorumu")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"));
        mvc.perform(write("POST", path, member("MANAGER"), Map.of("body", "Yönetici katkısı")))
                .andExpect(status().isForbidden());
        var comment = result(write("POST", path, owner, Map.of("body", "İlk yorum metni")), 201);
        String updatePath = path + "/" + comment.path("id").asText();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> update = () -> {
                start.await();
                return mvc.perform(write("PUT", updatePath, owner,
                        Map.of("body", "Eşzamanlı düzenlenen yorum", "version", 0)))
                        .andReturn().getResponse().getStatus();
            };
            var first = pool.submit(update);var second = pool.submit(update);start.countDown();
            org.assertj.core.api.Assertions.assertThat(java.util.List.of(first.get(),second.get()))
                    .containsExactlyInAnyOrder(200,409);
        }
        jdbc.update("UPDATE questions SET archived_at=CURRENT_TIMESTAMP WHERE id=?", UUID.fromString(id));
        mvc.perform(write("POST", path, owner, Map.of("body", "Arşivlenmiş soruya yorum")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUESTION_ARCHIVED"));
        mvc.perform(write("PUT", updatePath, owner, Map.of("body", "Arşivde düzenleme", "version", 1)))
                .andExpect(status().isConflict());
        mvc.perform(get(path)).andExpect(status().isOk());
        jdbc.update("UPDATE questions SET deleted_at=CURRENT_TIMESTAMP WHERE id=?", UUID.fromString(id));
        mvc.perform(get(path)).andExpect(status().isNotFound());
    }

    @Test
    void bearerRetentionKeepsCollectionsPrivateAndExposesGamification() throws Exception {
        var owner = member("MEMBER");var other = member("MEMBER");
        String university = create(member("MANAGER"), "UNIVERSITY", "Takip testi " + UUID.randomUUID()).path("id").asText();
        String question = question(owner, content("Mobil kayıt ve puan deneyimi nasıl çalışır?")).path("id").asText();
        result(write("PUT", "/api/me/follows", owner, Map.of("targetType","UNIVERSITY","targetId",university,"active",true)),200);
        result(write("PUT", "/api/me/follows", owner, Map.of("targetType","UNIVERSITY","targetId",university,"active",true)),200);
        mvc.perform(get("/api/me/follows").header("Authorization","Bearer " + owner.cookie().getValue()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/me/follows").header("Authorization","Bearer " + other.cookie().getValue()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        result(write("PUT", "/api/me/saved", owner, Map.of("targetType","QUESTION","targetId",question,"active",true)),200);
        mvc.perform(get("/api/me/saved").header("Authorization","Bearer " + owner.cookie().getValue()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].targetId").value(question));
        result(write("PUT", "/api/me/saved", owner, Map.of("targetType","QUESTION","targetId",question,"active",false)),200);
        mvc.perform(get("/api/me/saved").header("Authorization","Bearer " + owner.cookie().getValue()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/me/saved")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/gamification/profiles/" + owner.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").isNumber()).andExpect(jsonPath("$.title").isString());
        mvc.perform(get("/api/gamification/leaderboard").param("period","WEEKLY").param("size","100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/gamification/profiles/" + owner.id() + "/annual-report").param("year",String.valueOf(java.time.Year.now().getValue())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.year").value(java.time.Year.now().getValue()));
        mvc.perform(get("/api/gamification/profiles/" + owner.id() + "/annual-report").param("year","2019"))
                .andExpect(status().isBadRequest());
        result(write("PUT", "/api/me/gamification/showcase", owner, Map.of("achievementIds",java.util.List.of())),200);
        mvc.perform(write("PUT", "/api/me/gamification/showcase", owner, Map.of("achievementIds",java.util.List.of(UUID.randomUUID()))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/gamification/profiles/" + owner.id() + "/achievements"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }
}
