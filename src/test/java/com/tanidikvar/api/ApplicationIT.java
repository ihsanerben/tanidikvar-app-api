package com.tanidikvar.api;
import com.tanidikvar.api.auth.service.AuthenticationService;
import jakarta.servlet.http.Cookie;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@ActiveProfiles("local")
@Testcontainers
class ApplicationIT {
 @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.9-alpine");
 @DynamicPropertySource static void configuration(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);
  p.add("app.auth.secret",()->Base64.getEncoder().encodeToString(new byte[48]));
 }
 @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper mapper;@Autowired PasswordEncoder passwords;@Autowired AuthenticationService auth;
 record Actor(UUID id,Cookie cookie){}
 Actor actor(String role){UUID id=UUID.randomUUID();String email=id+"@example.test";jdbc.update("INSERT INTO users(id,email,password_hash,authority,email_verified_at,created_at,updated_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,email,passwords.encode("Testing-password!"),role);return new Actor(id,new Cookie("TV_ACCESS",auth.login(email,"Testing-password!").accessToken()));}
 MockHttpServletRequestBuilder write(String path,Actor a,Object body){return put(path).cookie(a.cookie()).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(body));}
 Actor student(){var a=actor("MEMBER");UUID u=UUID.randomUUID(),d=UUID.randomUUID(),e=UUID.randomUUID();
  jdbc.update("INSERT INTO universities(id,name,normalized_name) VALUES (?,?,?)",u,"Test Üniversitesi "+u,u.toString());
 jdbc.update("INSERT INTO departments(id,name,normalized_name) VALUES (?,?,?)",d,"Bilgisayar "+d,d.toString());
  jdbc.update("INSERT INTO university_departments(id,university_id,department_id) VALUES (?,?,?)",e,u,d);
  jdbc.update("INSERT INTO user_profiles(user_id,first_name,last_name,education_status,university_department_id) VALUES (?, 'Ada','Yılmaz','UNIVERSITE_OGRENCISI',?)",a.id(),e);return a;
 }
 Actor candidate(){var a=actor("MEMBER");jdbc.update("INSERT INTO user_profiles(user_id,first_name,last_name,education_status) VALUES (?, 'Yiğit','Öztürk','YKS_ADAYI')",a.id());return a;}
 RequestBuilder submitRequest(Actor a,UUID request,long version)throws Exception{return post("/api/me/admin-applications").cookie(a.cookie()).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(Map.of("requestId",request,"profileVersion",version,"coverLetter","Deneyimlerimle topluluğa fayda sağlamak istiyorum.")));}
 JsonNode submit(Actor a)throws Exception{return mapper.readTree(mvc.perform(submitRequest(a,UUID.randomUUID(),1)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
 // Simulate a pending application created under the previous re-verification policy.
 JsonNode legacyPending(Actor a)throws Exception{
  String verification=active(a);
  jdbc.update("UPDATE users SET authority='MEMBER',active_verification_application_id=NULL WHERE id=?",a.id());
  try{return submit(a);}finally{jdbc.update("UPDATE users SET authority='TANIDIK',active_verification_application_id=? WHERE id=?",UUID.fromString(verification),a.id());}
 }
 @Test void approvedAdminSeesHistoryButCannotSubmitAgain()throws Exception{
  var a=student();var m=actor("MANAGER");var approved=submit(a);approve(m,approved);
  mvc.perform(submitRequest(a,UUID.randomUUID(),1)).andExpect(status().isForbidden());
  mvc.perform(get("/api/me/admin-applications").cookie(a.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
 }
 @Test void documentIsOptionalAndManagerCanApproveDocumentlessApplication()throws Exception{
  var a=student();var m=actor("MANAGER");UUID request=UUID.randomUUID();
  var application=mapper.readTree(mvc.perform(submitRequest(a,request,1)).andExpect(status().isCreated()).andExpect(jsonPath("$.documentFileId").doesNotExist()).andReturn().getResponse().getContentAsString());
  approve(m,application);
  assertThat(role(a)).isEqualTo("TANIDIK");assertThat(active(a)).isEqualTo(application.get("id").asText());
  assertThat(jdbc.queryForObject("SELECT count(*) FROM stored_files WHERE owner_id=? AND purpose='VERIFICATION'",Long.class,a.id())).isZero();
 }
 @Test void yksCandidateCanSubmitAndBeApprovedWithoutUniversitySnapshot()throws Exception{
  var a=candidate();var m=actor("MANAGER");var application=submit(a);
  assertThat(application.get("educationStatus").asString()).isEqualTo("YKS_ADAYI");assertThat(application.get("universityName").isNull()).isTrue();assertThat(application.get("departmentName").isNull()).isTrue();
  approve(m,application);assertThat(role(a)).isEqualTo("TANIDIK");assertThat(active(a)).isEqualTo(application.get("id").asText());
 }
 String decision(JsonNode a){return "/api/manager/admin-applications/"+a.get("id").asText()+"/decision";}
 void approve(Actor m,JsonNode a)throws Exception{mvc.perform(write(decision(a),m,Map.of("status","APPROVED","version",0))).andExpect(status().isOk()).andExpect(jsonPath("$.activeVerification").value(true));}
 String role(Actor a){return jdbc.queryForObject("SELECT authority FROM users WHERE id=?",String.class,a.id());}
 String active(Actor a){return jdbc.queryForObject("SELECT active_verification_application_id::text FROM users WHERE id=?",String.class,a.id());}
 @Test void snapshotDoesNotExposeDocumentMetadata()throws Exception{
  var a=student();var other=student();var m=actor("MANAGER");var app=submit(a);
  jdbc.update("UPDATE user_profiles SET first_name='Değişti',version=version+1 WHERE user_id=?",a.id());
  jdbc.update("UPDATE universities SET name='Yeni ad' WHERE id=(SELECT university_id FROM university_departments WHERE id=(SELECT university_department_id FROM user_profiles WHERE user_id=?))",a.id());
  mvc.perform(get("/api/me/admin-applications").cookie(a.cookie())).andExpect(jsonPath("$.items[0].firstName").value("Ada")).andExpect(jsonPath("$.items[0].universityName").value(app.get("universityName").asText())).andExpect(jsonPath("$.items[0].documentSha256").doesNotExist());
  mvc.perform(get("/api/me/admin-applications").cookie(other.cookie())).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get("/api/manager/admin-applications").cookie(other.cookie())).andExpect(status().isForbidden());
  approve(m,app);assertThat(role(a)).isEqualTo("TANIDIK");assertThat(active(a)).isEqualTo(app.get("id").asText());
  mvc.perform(get("/api/me").cookie(a.cookie())).andExpect(jsonPath("$.role").value("TANIDIK"));
 }
 @Test void eligibilityCsrfAndValidation()throws Exception{
  var a=student();var m=actor("MANAGER");
  mvc.perform(submitRequest(a,UUID.randomUUID(),2)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
  var incomplete=actor("MEMBER");
  mvc.perform(submitRequest(incomplete,UUID.randomUUID(),1)).andExpect(status().isForbidden());
  mvc.perform(submitRequest(m,UUID.randomUUID(),1)).andExpect(status().isForbidden());
  mvc.perform(post("/api/me/admin-applications").cookie(a.cookie()).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
 }
 @Test void duplicateSubmissionAndConflictingRetry()throws Exception{
  var a=student();UUID request=UUID.randomUUID();
  var first=mapper.readTree(mvc.perform(submitRequest(a,request,1)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
  mvc.perform(submitRequest(a,request,1)).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(first.get("id").asText()));
  mvc.perform(submitRequest(a,request,2)).andExpect(status().isConflict());
  mvc.perform(submitRequest(a,UUID.randomUUID(),1)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPLICATION_PENDING"));
  assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_applications WHERE applicant_id=?",Long.class,a.id())).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM stored_files WHERE owner_id=? AND purpose='VERIFICATION'",Long.class,a.id())).isZero();
 }
 @Test void rejectionReapplicationAndReverificationPreservePriorApproval()throws Exception{
  var a=student();var m=actor("MANAGER");var first=submit(a);
  mvc.perform(write(decision(first),m,Map.of("status","REJECTED","version",0,"reason","  "))).andExpect(status().isBadRequest());
  mvc.perform(write(decision(first),m,Map.of("status","REJECTED","version",0,"reason","Belge okunmuyor."))).andExpect(status().isOk());
  assertThat(role(a)).isEqualTo("MEMBER");
  var approved=submit(a);approve(m,approved);
  var second=legacyPending(a);mvc.perform(write(decision(second),m,Map.of("status","REJECTED","version",0,"reason","Yeni belge uygun değil."))).andExpect(status().isOk());
  assertThat(role(a)).isEqualTo("TANIDIK");assertThat(active(a)).isEqualTo(approved.get("id").asText());
  var third=legacyPending(a);approve(m,third);assertThat(active(a)).isEqualTo(third.get("id").asText());
  mvc.perform(write(decision(first),m,Map.of("status","APPROVED","version",1))).andExpect(status().isConflict());
 }
 @Test void revokeClosesPendingAndOldDecisionCannotRestoreAuthority()throws Exception{
  var a=student();var m=actor("MANAGER");var first=submit(a);approve(m,first);var pending=legacyPending(a);
  var body=Map.of("verificationId",first.get("id").asText(),"reason","Doğrulama geçersiz.");
  mvc.perform(post("/api/manager/users/"+a.id()+"/revoke-admin").cookie(m.cookie()).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(body))).andExpect(status().isNoContent());
  assertThat(role(a)).isEqualTo("MEMBER");assertThat(active(a)).isNull();
  mvc.perform(write(decision(pending),m,Map.of("status","APPROVED","version",0))).andExpect(status().isConflict());
  mvc.perform(get("/api/me/admin-applications").cookie(a.cookie())).andExpect(jsonPath("$.items[0].status").value("REJECTED")).andExpect(jsonPath("$.items[1].status").value("APPROVED")).andExpect(jsonPath("$.items[1].activeVerification").value(false));
  mvc.perform(get("/api/manager/admin-applications").cookie(a.cookie())).andExpect(status().isForbidden());
  assertThat(jdbc.queryForObject("SELECT count(*) FROM management_actions WHERE target_id=? AND action='REVOKE_ADMIN' AND reason IS NOT NULL",Long.class,a.id())).isEqualTo(1);
  submit(a);
 }
 @Test void concurrentSubmissionsProduceOneApplication()throws Exception{
  var a=student();UUID request=UUID.randomUUID();var gate=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)){
   var left=pool.submit(()->{gate.await();return mvc.perform(submitRequest(a,request,1)).andReturn().getResponse();});
   var right=pool.submit(()->{gate.await();return mvc.perform(submitRequest(a,request,1)).andReturn().getResponse();});gate.countDown();
   var l=left.get(15,TimeUnit.SECONDS);var r=right.get(15,TimeUnit.SECONDS);assertThat(l.getStatus()).isEqualTo(201);assertThat(r.getStatus()).isEqualTo(201);
   assertThat(mapper.readTree(l.getContentAsString()).get("id")).isEqualTo(mapper.readTree(r.getContentAsString()).get("id"));
  }
  assertThat(jdbc.queryForObject("SELECT count(*) FROM stored_files WHERE owner_id=? AND purpose='VERIFICATION'",Long.class,a.id())).isZero();
 }
 @Test void twoManagersCannotDecideSameVersion()throws Exception{
  var a=student();var m=actor("MANAGER");var n=actor("MANAGER");var app=submit(a);var gate=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)){
   var left=pool.submit(()->{gate.await();return mvc.perform(write(decision(app),m,Map.of("status","APPROVED","version",0))).andReturn().getResponse().getStatus();});
   var right=pool.submit(()->{gate.await();return mvc.perform(write(decision(app),n,Map.of("status","REJECTED","reason","Uygun değil.","version",0))).andReturn().getResponse().getStatus();});gate.countDown();
   assertThat(List.of(left.get(15,TimeUnit.SECONDS),right.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
  }
  String state=jdbc.queryForObject("SELECT status FROM admin_applications WHERE id=?",String.class,UUID.fromString(app.get("id").asText()));assertThat(role(a)).isEqualTo(state.equals("APPROVED")?"TANIDIK":"MEMBER");
 }
 @Test void decisionRollbackDoesNotGrantPartialAuthority()throws Exception{
  var a=student();var m=actor("MANAGER");var app=submit(a);String id=app.get("id").asText();
  jdbc.execute("CREATE FUNCTION fail_application_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.target_id='"+id+"'::uuid THEN RAISE EXCEPTION 'test failure'; END IF; RETURN NEW; END $$");
  jdbc.execute("CREATE TRIGGER fail_application_audit BEFORE INSERT ON management_actions FOR EACH ROW EXECUTE FUNCTION fail_application_audit()");
  try{mvc.perform(write(decision(app),m,Map.of("status","APPROVED","version",0))).andExpect(status().isServiceUnavailable());assertThat(role(a)).isEqualTo("MEMBER");assertThat(active(a)).isNull();assertThat(jdbc.queryForObject("SELECT status FROM admin_applications WHERE id=?",String.class,UUID.fromString(id))).isEqualTo("PENDING");}
  finally{jdbc.execute("DROP TRIGGER fail_application_audit ON management_actions");jdbc.execute("DROP FUNCTION fail_application_audit()");}
 }
 @Test void concurrentReverificationApprovalAndRevocationCannotRestoreByAccident()throws Exception{
  var a=student();var m=actor("MANAGER");var n=actor("MANAGER");var first=submit(a);approve(m,first);var pending=legacyPending(a);var gate=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)){
   var approval=pool.submit(()->{gate.await();return mvc.perform(write(decision(pending),m,Map.of("status","APPROVED","version",0))).andReturn().getResponse().getStatus();});
   var removal=pool.submit(()->{gate.await();return mvc.perform(post("/api/manager/users/"+a.id()+"/revoke-admin").cookie(n.cookie()).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(Map.of("verificationId",first.get("id").asText(),"reason","Yeniden kontrol gerekiyor.")))).andReturn().getResponse().getStatus();});
   gate.countDown();int accepted=approval.get(15,TimeUnit.SECONDS),revoked=removal.get(15,TimeUnit.SECONDS);
   if(revoked==204){assertThat(accepted).isEqualTo(409);assertThat(role(a)).isEqualTo("MEMBER");assertThat(active(a)).isNull();}
   else{assertThat(revoked).isEqualTo(409);assertThat(accepted).isEqualTo(200);assertThat(active(a)).isEqualTo(pending.get("id").asText());}
  }
 }
 @Test void revokeAuditFailureRollsBackAuthorityAndPendingClosure()throws Exception{
  var a=student();var m=actor("MANAGER");var first=submit(a);approve(m,first);var pending=legacyPending(a);
  jdbc.execute("CREATE FUNCTION fail_revoke_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.target_id='"+a.id()+"'::uuid AND NEW.action='REVOKE_ADMIN' THEN RAISE EXCEPTION 'test failure'; END IF; RETURN NEW; END $$");
  jdbc.execute("CREATE TRIGGER fail_revoke_audit BEFORE INSERT ON management_actions FOR EACH ROW EXECUTE FUNCTION fail_revoke_audit()");
  try{
   mvc.perform(post("/api/manager/users/"+a.id()+"/revoke-admin").cookie(m.cookie()).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(Map.of("verificationId",first.get("id").asText(),"reason","Kontrol")))).andExpect(status().isServiceUnavailable());
   assertThat(role(a)).isEqualTo("TANIDIK");assertThat(active(a)).isEqualTo(first.get("id").asText());
   assertThat(jdbc.queryForObject("SELECT status FROM admin_applications WHERE id=?",String.class,UUID.fromString(pending.get("id").asText()))).isEqualTo("PENDING");
  }finally{jdbc.execute("DROP TRIGGER fail_revoke_audit ON management_actions");jdbc.execute("DROP FUNCTION fail_revoke_audit()");}
 }
 @Test void physicalDeleteSnapshotAndOwnershipConstraints()throws Exception{
  var a=student();var other=student();var app=submit(a);UUID id=UUID.fromString(app.get("id").asText());var m=actor("MANAGER");approve(m,app);
  assertThatThrownBy(()->jdbc.update("UPDATE admin_applications SET submitted_first_name='Changed' WHERE id=?",id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("UPDATE users SET active_verification_application_id=? WHERE id=?",id,other.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.execute("DELETE FROM stored_files")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.execute("DELETE FROM admin_applications")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  jdbc.update("UPDATE users SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",a.id());
 }

 @Test void avatarUploadEndpointsAreNoLongerPublished()throws Exception{
  var a=actor("MEMBER");
  mvc.perform(post("/api/me/avatar").cookie(a.cookie()).with(csrf())).andExpect(status().isForbidden());
  mvc.perform(get("/api/me/avatar").cookie(a.cookie())).andExpect(status().isForbidden());
 }
}
