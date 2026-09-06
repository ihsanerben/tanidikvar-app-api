package com.tanidikvar.api;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class FoundationIT {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.9-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("app.auth.secret", () -> java.util.Base64.getEncoder().encodeToString(new byte[48]));
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    @org.springframework.transaction.annotation.Transactional
    void starterCatalogHasRealRelationshipsAndDoesNotReactivateExistingDecisions() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM universities",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM departments",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tags",Integer.class)).isZero();
        jdbc.execute(java.nio.file.Files.readString(java.nio.file.Path.of("scripts/seed-local-catalog.sql")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tags WHERE created_by IS NULL",Integer.class)).isEqualTo(19);
        mvc.perform(get("/api/universities").param("size", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(10));
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT department_id) FROM university_departments WHERE university_id=(SELECT id FROM universities WHERE name='Dokuz Eylül Üniversitesi')", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM university_departments WHERE university_id IN (SELECT id FROM universities WHERE name LIKE '% Üniversitesi')", Integer.class)).isEqualTo(75);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM university_departments ud JOIN universities u ON u.id=ud.university_id
                JOIN departments d ON d.id=ud.department_id
                WHERE u.name='Dokuz Eylül Üniversitesi'
                """, Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM university_departments ud JOIN universities u ON u.id=ud.university_id
                JOIN departments d ON d.id=ud.department_id
                WHERE u.name='İstanbul Teknik Üniversitesi' AND d.name IN ('Tıp','Hukuk','İşletme','Elektrik-Elektronik Mühendisliği')
                """, Integer.class)).isZero();
        jdbc.update("UPDATE tags SET deleted_at=CURRENT_TIMESTAMP WHERE normalized_name='kampüs'");
        jdbc.update("UPDATE universities SET deleted_at=CURRENT_TIMESTAMP WHERE name='Boğaziçi Üniversitesi'");
        jdbc.execute(java.nio.file.Files.readString(java.nio.file.Path.of("scripts/seed-local-catalog.sql")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tags",Integer.class)).isEqualTo(19);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM tags WHERE normalized_name='kampüs'",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM universities WHERE name='Boğaziçi Üniversitesi' AND deleted_at IS NULL", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM university_departments WHERE university_id IN (SELECT id FROM universities WHERE name LIKE '% Üniversitesi')", Integer.class)).isEqualTo(75);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void localCleanupOnlySoftDeletesExactSyntheticIdentitiesAndPreservesOtherAccounts() throws Exception {
        jdbc.execute(java.nio.file.Files.readString(java.nio.file.Path.of("scripts/seed-local-catalog.sql")));
        UUID synthetic=UUID.randomUUID(), preserved=UUID.randomUUID(), question=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users(id,email,password_hash,created_at,updated_at) VALUES
                (?,?,'test-only',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
                (?,?,'test-only',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, synthetic,"browser-profile-"+synthetic+"@example.test",preserved,"other-"+preserved+"@example.test");
        jdbc.update("INSERT INTO questions(id,author_id,request_id,title,scope) VALUES (?,?,?,'Synthetic question for cleanup','GENERAL')", question,synthetic,UUID.randomUUID());
        UUID catalog=UUID.randomUUID();
        jdbc.update("INSERT INTO universities(id,name,normalized_name) VALUES (?,'Test Üniversitesi abcdef12','test üniversitesi abcdef12')",catalog);
        Long preservedVersion=jdbc.queryForObject("SELECT version FROM users WHERE id=?",Long.class,preserved);
        jdbc.execute(java.nio.file.Files.readString(java.nio.file.Path.of("scripts/cleanup-local-test-data.sql")));
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM users WHERE id=?",Boolean.class,synthetic)).isTrue();
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM questions WHERE id=?",Boolean.class,question)).isTrue();
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM universities WHERE id=?",Boolean.class,catalog)).isTrue();
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL AND version=? FROM users WHERE id=?",Boolean.class,preservedVersion,preserved)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM universities WHERE name='Dokuz Eylül Üniversitesi' AND deleted_at IS NULL",Integer.class)).isEqualTo(1);
    }

    @Test
    void healthChecksRealDatabaseAndReturnsRequestId() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.database").value("up"))
                .andExpect(header().exists("X-Request-ID"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class)).isEqualTo(13);
    }

    @Test
    void corsAllowsOnlyConfiguredOrigin() throws Exception {
        mvc.perform(get("/api/health").header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(get("/api/health").header("Origin", "https://untrusted.example"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void csrfIsAvailableAndMutationRequiresIt() throws Exception {
        mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk()).andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(post("/api/questions"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post("/api/questions").with(csrf()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty()).andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void openApiDescribesImplementedEndpointsOnly() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.info.title").value("TanıdıkVar API"))
                .andExpect(jsonPath("$.paths['/api/health']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/csrf']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists());
    }

    @Test
    void softDeletePreservesRelationsAndUniqueness() {
        UUID university = UUID.randomUUID(); UUID department = UUID.randomUUID();
        String name = "university-" + university;
        jdbc.update("INSERT INTO universities(id,name,normalized_name) VALUES (?,?,?)", university, name, name);
        jdbc.update("INSERT INTO departments(id,name,normalized_name) VALUES (?,?,?)", department, department.toString(), department.toString());
        jdbc.update("INSERT INTO university_departments(id,university_id,department_id) VALUES (?,?,?)", UUID.randomUUID(), university, department);
        jdbc.update("UPDATE universities SET deleted_at = CURRENT_TIMESTAMP WHERE id=?", university);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM universities WHERE id=? AND deleted_at IS NULL", Integer.class, university)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM university_departments WHERE university_id=?", Integer.class, university)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO universities(id,name,normalized_name) VALUES (?,?,?)", UUID.randomUUID(), name, name))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void databaseRejectsPhysicalDeletionAndInvalidRelations() {
        for (String table : new String[]{"universities", "departments", "university_departments"}) {
            assertThatThrownBy(() -> jdbc.execute("DELETE FROM " + table))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE manager_profiles, question_likes, question_views, universities, departments, university_departments, user_profiles, questions, question_tags, answers, admin_applications, stored_files, users, auth_sessions, auth_action_tokens, tags, management_actions, question_assignments"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO university_departments(id,university_id,department_id) VALUES (?,?,?)", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
