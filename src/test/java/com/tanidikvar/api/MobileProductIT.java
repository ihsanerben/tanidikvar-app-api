package com.tanidikvar.api;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test void catalogCitiesArePublic() throws Exception {
        mvc.perform(get("/api/statistics/cities")).andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
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
        mvc.perform(write("PUT", "/api/answers/" + answerId + "/like", owner, Map.of("liked", true))).andExpect(status().isForbidden());
        var comment = result(write("POST", "/api/answers/" + answerId + "/comments", owner,
                Map.of("body", "Yanıtın için teşekkür ederim.")), 201);
        String commentPath = "/api/answers/" + answerId + "/comments/" + comment.path("id").asText();
        mvc.perform(write("PUT", commentPath, contributor, Map.of("body", "Başkasının yorumunu değiştiremem.", "version", 0)))
                .andExpect(status().isForbidden());
        assertThat(comment.path("editedAt").isNull()).isTrue();
        var editedComment = result(write("PUT", commentPath, owner, Map.of("body", "Ayrıntılı yanıtın için teşekkür ederim.", "version", 0)), 200);
        assertThat(editedComment.path("editedAt").asText()).isNotBlank();
        assertThat(editedComment.path("createdAt")).isEqualTo(comment.path("createdAt"));
        mvc.perform(get(commentPath)).andExpect(status().isOk())
                .andExpect(jsonPath("$.editedAt").value(editedComment.path("editedAt").asText()));
        mvc.perform(write("PUT", "/api/questions/" + id + "/best-answer", owner, Map.of("answerId", answerId))).andExpect(status().isForbidden());
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

    @Test void programFollowIsPrivateFilteredAndReceivesOnlyMatchingQuestions() throws Exception {
        var follower=member("MEMBER");var author=member("MEMBER");
        UUID university=UUID.fromString(create(member("MANAGER"),"UNIVERSITY","Program takibi "+UUID.randomUUID()).path("id").asText());
        UUID department=UUID.fromString(create(member("MANAGER"),"DEPARTMENT","Bölüm "+UUID.randomUUID()).path("id").asText());
        UUID family=UUID.randomUUID(),program=UUID.randomUUID();
        jdbc.update("INSERT INTO program_families(id,source,source_program_group_id,name,normalized_name,degree_level) VALUES (?,'YOK_ATLAS',987654,'Takip bölümü',?,'LISANS')",family,family.toString());
        jdbc.update("INSERT INTO programs(id,university_id,program_family_id,display_name,normalized_name) VALUES (?,?,?,'Takip bölümü',?)",program,university,family,program.toString());
        jdbc.update("INSERT INTO university_departments(id,university_id,department_id,program_id) VALUES (?,?,?,?)",UUID.randomUUID(),university,department,program);
        jdbc.update("INSERT INTO admission_options(id,program_id,guide_code,score_type,education_type,language,scholarship,duration_years) VALUES (?,?,?,'SAY','Örgün','Türkçe','Ücretsiz',4)",UUID.randomUUID(),program,"987654321");
        result(write("PUT","/api/me/follows",follower,Map.of("targetType","PROGRAM","targetId",program,"active",true)),200);
        mvc.perform(get("/api/me/follows").param("targetType","PROGRAM").header("Authorization","Bearer "+follower.cookie().getValue())).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/me/follows").param("targetType","UNIVERSITY").header("Authorization","Bearer "+follower.cookie().getValue())).andExpect(jsonPath("$.totalElements").value(0));
        var specific=new HashMap<String,Object>(content("Bölümümde ders programı nasıl işliyor?"));specific.put("scope","UNIVERSITY_DEPARTMENT");specific.put("universityId",university);specific.put("programId",program);
        var q=question(author,specific);
        mvc.perform(get("/api/questions").param("programId",program.toString())).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(q.path("id").asText()));
        mvc.perform(get("/api/questions").param("programId",UUID.randomUUID().toString())).andExpect(jsonPath("$.totalElements").value(0));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND target_id=?",Long.class,follower.id(),UUID.fromString(q.path("id").asText()))).isEqualTo(1);
        var generic=new HashMap<String,Object>(content("Üniversitede genel olanaklar nasıl işliyor?"));generic.put("scope","UNIVERSITY");generic.put("universityId",university);
        var other=question(author,generic);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND target_id=?",Long.class,follower.id(),UUID.fromString(other.path("id").asText()))).isZero();
        jdbc.update("INSERT INTO education_verifications(id,user_id,verification_type,program_id,verified_at) VALUES (?,?,'MANAGER_REVIEW',?,CURRENT_TIMESTAMP)",UUID.randomUUID(),follower.id(),program);
        result(write("PUT","/api/me/notification-preferences",follower,Map.of("inAppEnabled",true,"emailEnabled",false,"emailFrequency","NEVER","questionRoutingEnabled",true,"version",0,"categories",Map.of("PROGRAM",false))),200);
        specific.put("title","Bölümde yeni derslerin içeriği hakkında başka soru");
        var muted=question(author,specific);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND target_id=?",Long.class,follower.id(),UUID.fromString(muted.path("id").asText()))).isZero();

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
    @Test void notificationTargetsPreferencesAndReadOwnershipStayConsistent() throws Exception {
        var owner=member("MEMBER");var author=member("MEMBER");var replier=member("MEMBER");
        var question=question(owner,content("Bildirimden tam yoruma nasıl gidilir?"));
        String qid=question.path("id").asText();
        var answer=result(write("POST","/api/questions/"+qid+"/answers",author,Map.of("body","Bildirimde doğru yorum kimliğini kullanarak gidilir.")),201);
        String aid=answer.path("id").asText();
        mvc.perform(get("/api/answers/"+aid)).andExpect(status().isOk()).andExpect(jsonPath("$.questionId").value(qid)).andExpect(jsonPath("$.answerKind").value("COMMUNITY"));
        var notes=result(get("/api/me/notifications").param("targetType","ANSWER").param("unread","true").header("Authorization","Bearer "+owner.cookie().getValue()),200);
        org.assertj.core.api.Assertions.assertThat(notes.path("totalElements").asInt()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(notes.path("items").get(0).path("answerId").asText()).isEqualTo(aid);
        org.assertj.core.api.Assertions.assertThat(notes.path("items").get(0).path("questionId").asText()).isEqualTo(qid);
        String noteId=notes.path("items").get(0).path("id").asText();
        mvc.perform(write("PUT","/api/me/notifications/"+noteId+"/read",author,Map.of())).andExpect(status().isNotFound());
        mvc.perform(write("PUT","/api/me/notifications/"+noteId+"/read",owner,Map.of())).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/notifications").param("targetType","ANSWER").param("unread","true").header("Authorization","Bearer "+owner.cookie().getValue())).andExpect(jsonPath("$.totalElements").value(0));
        String commentPath="/api/answers/"+aid+"/comments";
        var comment=result(write("POST",commentPath,owner,Map.of("body","Bu alt yoruma yanıt bekliyorum.")),201);
        var reply=result(write("POST",commentPath,replier,Map.of("body","Doğrudan senin yorumuna yanıt veriyorum.","replyToId",comment.path("id").asText())),201);
        mvc.perform(get(commentPath+"/"+reply.path("id").asText())).andExpect(status().isOk()).andExpect(jsonPath("$.replyToId").value(comment.path("id").asText()));
        mvc.perform(get("/api/me/notifications").param("targetType","ANSWER_COMMENT").header("Authorization","Bearer "+owner.cookie().getValue()))
          .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].targetId").value(reply.path("id").asText())).andExpect(jsonPath("$.items[0].answerId").value(aid));
        var p=result(get("/api/me/notification-preferences").header("Authorization","Bearer "+owner.cookie().getValue()),200);
        var payload=Map.of("version",p.path("version").asLong(),"inAppEnabled",true,"emailEnabled",false,"emailFrequency","NEVER","questionRoutingEnabled",true,"categories",Map.of("ANSWER",false,"REPLY",false));
        var saved=result(write("PUT","/api/me/notification-preferences",owner,payload),200);
        org.assertj.core.api.Assertions.assertThat(saved.path("categories").path("ANSWER").asBoolean()).isFalse();
        mvc.perform(write("PUT","/api/me/notification-preferences",owner,payload)).andExpect(status().isConflict());
        result(write("POST","/api/questions/"+qid+"/answers",replier,Map.of("body","İkinci bir yorumda bildirim tercihini kontrol ediyoruz.")),201);
        result(write("POST",commentPath,replier,Map.of("body","Bildirim tercihinden sonra ikinci yanıt.","replyToId",comment.path("id").asText())),201);
        mvc.perform(get("/api/me/notifications").param("targetType","ANSWER").header("Authorization","Bearer "+owner.cookie().getValue())).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/me/notifications").param("targetType","ANSWER_COMMENT").header("Authorization","Bearer "+owner.cookie().getValue())).andExpect(jsonPath("$.totalElements").value(1));
        jdbc.update("UPDATE answers SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",UUID.fromString(aid));
        mvc.perform(get("/api/answers/"+aid)).andExpect(status().isNotFound());
        mvc.perform(get(commentPath+"/"+reply.path("id").asText())).andExpect(status().isNotFound());
    }

    @Test void seasonalBadgesRequireBothActivityCountAndDistinctDays() throws Exception {
        var owner=member("MEMBER");
        for(int index=0;index<24;index++) jdbc.update("INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version,created_at) VALUES (?,?,'TEST_ACTIVITY',1,'TEST',?,1,?::timestamptz)",UUID.randomUUID(),owner.id(),UUID.randomUUID(),"2030-06-"+String.format("%02d",1+index%5)+" 12:00:00+03");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT count(*) FROM user_achievements WHERE user_id=? AND achievement_key='PREFERENCE_GUIDE'",Long.class,owner.id())).isZero();
        jdbc.update("INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version,created_at) VALUES (?,?,'TEST_ACTIVITY',1,'TEST',?,1,'2030-06-05 12:00:00+03')",UUID.randomUUID(),owner.id(),UUID.randomUUID());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT count(*) FROM user_achievements WHERE user_id=? AND achievement_key='PREFERENCE_GUIDE' AND period_year=2030",Long.class,owner.id())).isEqualTo(1);
        var singleDay=member("MEMBER");
        for(int index=0;index<25;index++) jdbc.update("INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version,created_at) VALUES (?,?,'TEST_ACTIVITY',1,'TEST',?,1,'2030-06-01 12:00:00+03')",UUID.randomUUID(),singleDay.id(),UUID.randomUUID());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT count(*) FROM user_achievements WHERE user_id=? AND achievement_key='PREFERENCE_GUIDE'",Long.class,singleDay.id())).isZero();
    }

    @Test void taskBadgesUnlockOnceAndOnlyChosenOwnedBadgesAppearOnProfile() throws Exception {
        var owner=member("MEMBER");var other=member("MEMBER");
        mvc.perform(get("/api/gamification/achievements")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(24));
        question(owner,content("İlk görev rozeti ne zaman açılır?"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT count(*) FROM user_achievements WHERE user_id=? AND achievement_key='FIRST_QUESTION'",Long.class,owner.id())).isZero();
        for(int index=0;index<4;index++) jdbc.update("INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version) VALUES (?,?,'QUESTION_CREATED',5,'QUESTION',?,1)",UUID.randomUUID(),owner.id(),UUID.randomUUID());
        // Reaching the count on a single day does not unlock a task badge.
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT count(*) FROM user_achievements WHERE user_id=? AND achievement_key='FIRST_QUESTION'",Long.class,owner.id())).isZero();
        for(int day=1;day<=4;day++) jdbc.update("INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version,created_at) VALUES (?,?,'POLL_PARTICIPATION',1,'POLL',?,1,CURRENT_TIMESTAMP-(? * interval '1 day'))",UUID.randomUUID(),owner.id(),UUID.randomUUID(),day);

        UUID earned=jdbc.queryForObject("SELECT id FROM user_achievements WHERE user_id=? AND achievement_key='FIRST_QUESTION'",UUID.class,owner.id());
        question(owner,content("İkinci soruda rozet tekrarlanır mı?"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT count(*) FROM user_achievements WHERE user_id=? AND achievement_key='FIRST_QUESTION'",Long.class,owner.id())).isEqualTo(1);
        mvc.perform(get("/api/gamification/profiles/"+owner.id())).andExpect(jsonPath("$.badges.length()").value(0));
        result(write("PUT","/api/me/gamification/showcase",owner,Map.of("achievementIds",java.util.List.of(earned))),200);
        mvc.perform(get("/api/gamification/profiles/"+owner.id())).andExpect(jsonPath("$.badges[0]").value("İlk Merak"));
        mvc.perform(write("PUT","/api/me/gamification/showcase",other,Map.of("achievementIds",java.util.List.of(earned)))).andExpect(status().isNotFound());
        mvc.perform(write("PUT","/api/me/gamification/showcase",owner,Map.of("achievementIds",java.util.List.of(UUID.randomUUID())))).andExpect(status().isNotFound());
        mvc.perform(get("/api/gamification/profiles/"+owner.id())).andExpect(jsonPath("$.badges[0]").value("İlk Merak"));
        mvc.perform(write("PUT","/api/me/gamification/showcase",owner,Map.of("achievementIds",java.util.List.of(earned,earned)))).andExpect(status().isBadRequest());
        mvc.perform(write("PUT","/api/me/gamification/showcase",owner,Map.of("achievementIds",java.util.List.of(earned,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())))).andExpect(status().isBadRequest());
        result(write("PUT","/api/me/gamification/showcase",owner,Map.of("achievementIds",java.util.List.of())),200);
        mvc.perform(get("/api/gamification/profiles/"+owner.id())).andExpect(jsonPath("$.badges.length()").value(0));
    }

    @Test void achievementCatalogIconsAreUnique() {
        var icons = jdbc.queryForList("SELECT icon FROM achievement_definitions", String.class);
        assertThat(icons).hasSize(24).doesNotHaveDuplicates();
        assertThat(jdbc.queryForObject("SELECT icon FROM achievement_definitions WHERE achievement_key='TENURE_5'",String.class)).isEqualTo("🌲🌲");
    }

    @Test void fiveYearTanidikBadgeUnlocksAtFiveHundredPositiveEvents() throws Exception {
        var manager = member("MANAGER");
        var tanidik = member("TANIDIK");
        var university = create(manager,"UNIVERSITY","Beş Yıl Üniversitesi " + UUID.randomUUID());
        var department = create(manager,"DEPARTMENT","Beş Yıl Bölümü " + UUID.randomUUID());
        var relation = result(write("POST","/api/manager/university-departments",manager,Map.of(
                "universityId",university.path("id").asText(),"departmentId",department.path("id").asText(),"reason","Rozet kazanım testi")),201);
        jdbc.update("INSERT INTO admin_applications(id,applicant_id,request_id,submitted_first_name,submitted_last_name,education_status,university_department_id,university_name,department_name,profile_version,status,reviewed_by,reviewed_at,university_id,department_id,cover_letter) VALUES (?,?,?,?,?,?,?,?,?,?,'APPROVED',?,CURRENT_TIMESTAMP-interval '6 years',?,?,?)",
                UUID.randomUUID(),tanidik.id(),UUID.randomUUID(),"Ada","Yılmaz","UNIVERSITE_OGRENCISI",UUID.fromString(relation.path("id").asText()),"Beş Yıl Üniversitesi","Beş Yıl Bölümü",0,manager.id(),UUID.fromString(university.path("id").asText()),UUID.fromString(department.path("id").asText()),"Doğrulanmış deneyim paylaşımı için yeterli açıklama metnidir.");
        jdbc.update("INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version) SELECT gen_random_uuid(),?,'TEST_ACTIVITY',1,'TEST',gen_random_uuid(),1 FROM generate_series(1,499)",tanidik.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_achievements WHERE user_id=? AND achievement_key='TENURE_5'",Long.class,tanidik.id())).isZero();
        jdbc.update("INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version) VALUES (?,?,'TEST_ACTIVITY',1,'TEST',?,1)",UUID.randomUUID(),tanidik.id(),UUID.randomUUID());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_achievements WHERE user_id=? AND achievement_key='TENURE_5'",Long.class,tanidik.id())).isEqualTo(1);
    }
}
