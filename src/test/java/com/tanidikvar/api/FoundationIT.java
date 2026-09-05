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
    void healthChecksRealDatabaseAndReturnsRequestId() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.database").value("up"))
                .andExpect(header().exists("X-Request-ID"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class)).isEqualTo(8);
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
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE question_likes, question_views, universities, departments, university_departments, user_profiles, questions, question_tags, answers, admin_applications, stored_files, users, auth_sessions, auth_action_tokens, tags, management_actions, question_assignments"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO university_departments(id,university_id,department_id) VALUES (?,?,?)", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
