package com.tanidikvar.api;

import com.tanidikvar.api.auth.service.AuthenticationService;
import jakarta.servlet.http.Cookie;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.pilot-mode=true","app.secure-cookies=true"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Testcontainers
class PilotModeIT {
 @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.9-alpine");
 @DynamicPropertySource static void configure(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);
  p.add("app.auth.secret",()->Base64.getEncoder().encodeToString(new byte[48]));
 }
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 @Autowired PasswordEncoder passwords;
 @Autowired AuthenticationService auth;
 @Test void pilotAllowsPhotolessParticipationAndDocumentlessAdminApplicationsButRejectsAvatarUploads()throws Exception{
  UUID id=UUID.randomUUID();String email=id+"@example.test";
  jdbc.update("INSERT INTO users(id,email,password_hash,authority,email_verified_at,created_at,updated_at) VALUES (?,?,?,'MEMBER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,email,passwords.encode("Pilot-test-password!"));
  UUID university=UUID.randomUUID(),department=UUID.randomUUID(),education=UUID.randomUUID();
  jdbc.update("INSERT INTO universities(id,name,normalized_name) VALUES (?,?,'pilot-university')",university,"Pilot Üniversitesi "+university);
  jdbc.update("INSERT INTO departments(id,name,normalized_name) VALUES (?,?,'pilot-department')",department,"Pilot Bölümü "+department);
  jdbc.update("INSERT INTO university_departments(id,university_id,department_id) VALUES (?,?,?)",education,university,department);
  Cookie csrfCookie=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse().getCookie("XSRF-TOKEN");
  Cookie cookie=new Cookie("TV_ACCESS",auth.login(email,"Pilot-test-password!").accessToken());
  mvc.perform(put("/api/me/profile").cookie(cookie,csrfCookie).header("X-XSRF-TOKEN",csrfCookie.getValue()).contentType("application/json").content("{\"firstName\":\"Pilot\",\"lastName\":\"Katılımcı\",\"educationStatus\":\"UNIVERSITE_OGRENCISI\",\"universityDepartmentId\":\""+education+"\",\"version\":0}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(true));
  mvc.perform(post("/api/questions").cookie(cookie,csrfCookie).header("X-XSRF-TOKEN",csrfCookie.getValue()).contentType("application/json").content("{\"requestId\":\""+UUID.randomUUID()+"\",\"content\":{\"title\":\"Pilot ortamında soru oluşturma\",\"scope\":\"GENERAL\",\"tagIds\":[]}}"))
    .andExpect(status().isCreated());
  mvc.perform(multipart("/api/me/avatar").file(new MockMultipartFile("file","test.png","image/png",new byte[]{1})).cookie(cookie,csrfCookie).header("X-XSRF-TOKEN",csrfCookie.getValue()))
    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PILOT_RESTRICTION"));
  mvc.perform(multipart("/api/me/admin-applications")
    .file(new MockMultipartFile("request","request.json","application/json",("{\"requestId\":\""+UUID.randomUUID()+"\",\"profileVersion\":1}").getBytes(java.nio.charset.StandardCharsets.UTF_8)))
    .cookie(cookie,csrfCookie).header("X-XSRF-TOKEN",csrfCookie.getValue()))
    .andExpect(status().isCreated()).andExpect(jsonPath("$.documentFileId").doesNotExist());
  assertThat(jdbc.queryForObject("SELECT count(*) FROM stored_files WHERE owner_id=?",Long.class,id)).isZero();
  assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_applications WHERE applicant_id=?",Long.class,id)).isEqualTo(1);
 }
 @Test void pilotUsesSecureCsrfCookiesAndHidesOpenApi()throws Exception{
  mvc.perform(get("/api/auth/csrf").secure(true)).andExpect(status().isOk()).andExpect(cookie().secure("XSRF-TOKEN",true));
  mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
 }
}
