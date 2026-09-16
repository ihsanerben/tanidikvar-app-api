package com.tanidikvar.api;

import com.tanidikvar.api.catalog.sync.model.YokAtlasProgram;
import com.tanidikvar.api.catalog.sync.model.YokAtlasSnapshot;
import com.tanidikvar.api.catalog.sync.model.YokAtlasYearStats;
import com.tanidikvar.api.catalog.sync.repository.YokCatalogSyncRepository;
import com.tanidikvar.api.catalog.sync.service.YokCatalogSyncPersistence;
import java.math.BigDecimal;
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
    @Autowired YokCatalogSyncRepository yokSyncRepository;
    @Autowired YokCatalogSyncPersistence yokSyncPersistence;

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
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class)).isEqualTo(46);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema='public' AND table_name IN
                    ('catalog_sync_runs','program_families','academic_units','programs','admission_options','admission_statistics')
                """, Integer.class)).isEqualTo(6);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void normalizedYokCatalogKeepsProductProgramSeparateFromAdmissionOption() throws Exception {
        UUID university=UUID.randomUUID(),family=UUID.randomUUID(),unit=UUID.randomUUID();
        UUID program=UUID.randomUUID(),option=UUID.randomUUID(),education=UUID.randomUUID(),department=UUID.randomUUID();
        String checksum="a".repeat(64);
        jdbc.update("""
                INSERT INTO universities(id,name,normalized_name,city,institution_type,catalog_source,source_university_id)
                VALUES (?,'Örnek Üniversitesi','örnek üniversitesi','İSTANBUL','DEVLET','YOK_ATLAS',173496)
                """,university);
        jdbc.update("""
                INSERT INTO program_families(id,source,source_program_group_id,name,normalized_name,degree_level)
                VALUES (?,'YOK_ATLAS',3118,'Görsel İletişim Tasarımı','görsel iletişim tasarımı','LISANS')
                """,family);
        jdbc.update("""
                INSERT INTO academic_units(id,university_id,source,source_unit_id,name,unit_type,city,district)
                VALUES (?,?,'YOK_ATLAS',292945,'Sanat, Tasarım ve Mimarlık Fakültesi','FAKÜLTE','İSTANBUL','ÜSKÜDAR')
                """,unit,university);
        jdbc.update("""
                INSERT INTO programs(id,university_id,program_family_id,display_name,normalized_name)
                VALUES (?,?,?,'Görsel İletişim Tasarımı','görsel iletişim tasarımı')
                """,program,university,family);
        jdbc.update("""
                INSERT INTO admission_options(id,program_id,academic_unit_id,guide_code,score_type,education_type,language,duration_years)
                VALUES (?,?,?,'105490029','SÖZ','Örgün Öğretim','Türkçe',4)
                """,option,program,unit);
        jdbc.update("""
                INSERT INTO admission_statistics(admission_option_id,guide_year,quota,placed,minimum_score,success_rank,source_payload_checksum)
                VALUES (?,2025,55,55,370.09443,28226,?)
                """,option,checksum);
        jdbc.update("INSERT INTO departments(id,name,normalized_name) VALUES (?,'Görsel İletişim Tasarımı','görsel iletişim tasarımı')",department);
        jdbc.update("INSERT INTO university_departments(id,university_id,department_id,program_id) VALUES (?,?,?,?)",education,university,department,program);

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM admission_statistics stats
                JOIN admission_options option ON option.id=stats.admission_option_id
                JOIN programs program ON program.id=option.program_id
                JOIN academic_units unit ON unit.id=option.academic_unit_id
                WHERE program.id=? AND stats.guide_year=2025 AND unit.district='ÜSKÜDAR'
                """,Integer.class,program)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT program_id FROM university_departments WHERE id=?",UUID.class,education)).isEqualTo(program);
        mvc.perform(get("/api/catalog-programs").param("universityId",university.toString()).param("scoreType","SÖZ"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].educationId").value(education.toString()))
                .andExpect(jsonPath("$.items[0].currentBestRank").value(28226));
        mvc.perform(get("/api/catalog-programs/{id}",program))
                .andExpect(status().isOk()).andExpect(jsonPath("$.options[0].statistics[0].year").value(2025));
        mvc.perform(get("/api/statistics/overview"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.programCount").value(1));
        mvc.perform(get("/api/universities/{id}/catalog-statistics",university))
                .andExpect(status().isOk()).andExpect(jsonPath("$.programCount").value(1));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void yokCatalogSyncIsIdempotentAndPreservesManualUniversityPresentation() {
        UUID university=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO universities(id,name,normalized_name,description,logo_url,accent_primary)
                VALUES (?,'Örnek Üniversitesi','örnek üniversitesi','Manager açıklaması','/logo.svg','#123456')
                """,university);
        var stats=new YokAtlasYearStats(2025,80,79,new BigDecimal("510.12345"),1200,"b".repeat(64));
        var program=new YokAtlasProgram(88079L,377415,"105490029",173496,"Örnek Üniversitesi","DEVLET","İSTANBUL",
                292945L,"Mühendislik Fakültesi",null,"İSTANBUL","ÜSKÜDAR",4001,"Bilgisayar Mühendisliği",
                "Bilgisayar Mühendisliği (İngilizce)","LISANS","SAY","Örgün Öğretim","İngilizce",null,4,java.util.List.of(stats));
        var snapshot=new YokAtlasSnapshot("c".repeat(64),null,java.util.List.of(program));
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        yokSyncRepository.start(first,null,"APPLY");
        yokSyncPersistence.apply(first,snapshot);
        yokSyncRepository.start(second,null,"APPLY");
        yokSyncPersistence.apply(second,snapshot);

        assertThat(yokSyncRepository.find(first).orElseThrow().status()).isEqualTo("SUCCEEDED");
        assertThat(yokSyncRepository.find(second).orElseThrow().status()).isEqualTo("SKIPPED");
        assertThat(jdbc.queryForMap("SELECT description,logo_url,accent_primary FROM universities WHERE id=?",university))
                .containsEntry("description","Manager açıklaması").containsEntry("logo_url","/logo.svg").containsEntry("accent_primary","#123456");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admission_options WHERE guide_code='105490029'",Integer.class)).isEqualTo(1);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void yokCatalogPreviewReportsQualityWithoutWritingCatalogRows() {
        UUID manualUniversity=UUID.randomUUID(),runId=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO universities(id,name,normalized_name,catalog_source)
                VALUES (?,'Örnek Üniversitesi','örnek üniversitesi','MANUAL')
                """,manualUniversity);
        var current=new YokAtlasYearStats(2025,80,79,new BigDecimal("510.12345"),null,"d".repeat(64));
        var program=new YokAtlasProgram(88079L,377415,"105490029",173496,"Örnek Üniversitesi","DEVLET","İSTANBUL",
                null,null,null,"İSTANBUL","ÜSKÜDAR",4001,"Bilgisayar Mühendisliği",
                "Bilgisayar Mühendisliği","LISANS","SAY","Örgün Öğretim","İngilizce",null,4,java.util.List.of(current));
        var snapshot=new YokAtlasSnapshot("e".repeat(64),null,java.util.List.of(program));

        yokSyncRepository.start(runId,null,"PREVIEW");
        yokSyncPersistence.preview(runId,snapshot);

        var run=yokSyncRepository.find(runId).orElseThrow();
        assertThat(run.operation()).isEqualTo("PREVIEW");
        assertThat(run.status()).isEqualTo("SUCCEEDED");
        assertThat(run.qualityReport()).isNotNull();
        assertThat(run.qualityReport().universityCount()).isEqualTo(1);
        assertThat(run.qualityReport().newUniversityCount()).isEqualTo(1);
        assertThat(run.qualityReport().manualNameCollisionCount()).isEqualTo(1);
        assertThat(run.qualityReport().optionsWithoutAcademicUnit()).isEqualTo(1);
        assertThat(run.qualityReport().optionsWithoutCurrentSuccessRank()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admission_options WHERE guide_code='105490029'",Integer.class)).isZero();
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
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE manager_profiles, answer_likes, question_reports, question_likes, question_views, universities, departments, university_departments, user_profiles, questions, question_tags, answers, admin_applications, stored_files, users, auth_sessions, auth_action_tokens, tags, management_actions, question_assignments"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO university_departments(id,university_id,department_id) VALUES (?,?,?)", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
