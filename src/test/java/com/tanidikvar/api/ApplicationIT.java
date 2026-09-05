package com.tanidikvar.api;
import com.tanidikvar.api.auth.service.AuthenticationService;
import jakarta.servlet.http.Cookie;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.mock.web.MockMultipartFile;
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
 static final Path storage=Path.of(System.getProperty("java.io.tmpdir"),"tanidikvar-application-test-"+UUID.randomUUID());
 @DynamicPropertySource static void configuration(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);
  p.add("app.auth.secret",()->Base64.getEncoder().encodeToString(new byte[48]));p.add("app.storage-directory",storage::toString);
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
 static byte[] pdf(){return "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);}
 RequestBuilder upload(Actor a,UUID request,long version,byte[] bytes){return multipart("/api/me/admin-applications").file(new MockMultipartFile("document","belge.pdf","application/pdf",bytes)).file(new MockMultipartFile("request","","application/json",mapper.writeValueAsBytes(Map.of("requestId",request,"profileVersion",version)))).cookie(a.cookie()).with(csrf());}
 JsonNode submit(Actor a)throws Exception{return mapper.readTree(mvc.perform(upload(a,UUID.randomUUID(),1,pdf())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
 String decision(JsonNode a){return "/api/manager/admin-applications/"+a.get("id").asText()+"/decision";}
 void approve(Actor m,JsonNode a)throws Exception{mvc.perform(write(decision(a),m,Map.of("status","APPROVED","version",0))).andExpect(status().isOk()).andExpect(jsonPath("$.activeVerification").value(true));}
 String role(Actor a){return jdbc.queryForObject("SELECT authority FROM users WHERE id=?",String.class,a.id());}
 String active(Actor a){return jdbc.queryForObject("SELECT active_verification_application_id::text FROM users WHERE id=?",String.class,a.id());}
 @Test void snapshotAndOwnerManagerDocumentAccess()throws Exception{
  var a=student();var other=student();var m=actor("MANAGER");var app=submit(a);String file=app.get("documentFileId").asText(),path="/api/files/"+file+"/download";
  mvc.perform(get(path)).andExpect(status().isUnauthorized());
  mvc.perform(get(path).cookie(other.cookie())).andExpect(status().isNotFound());
  mvc.perform(get(path).cookie(a.cookie())).andExpect(status().isOk()).andExpect(header().string("Content-Disposition","attachment; filename=\"belge.pdf\"")).andExpect(header().string("Cache-Control","no-store")).andExpect(content().bytes(pdf()));
  mvc.perform(get(path).cookie(m.cookie())).andExpect(status().isOk());
  jdbc.update("UPDATE users SET authority='ADMIN' WHERE id=?",other.id());mvc.perform(get(path).cookie(other.cookie())).andExpect(status().isNotFound());
  jdbc.update("UPDATE user_profiles SET first_name='Değişti',version=version+1 WHERE user_id=?",a.id());
  jdbc.update("UPDATE universities SET name='Yeni ad' WHERE id=(SELECT university_id FROM university_departments WHERE id=(SELECT university_department_id FROM user_profiles WHERE user_id=?))",a.id());
  mvc.perform(get("/api/me/admin-applications").cookie(a.cookie())).andExpect(jsonPath("$.items[0].firstName").value("Ada")).andExpect(jsonPath("$.items[0].universityName").value(app.get("universityName").asText())).andExpect(jsonPath("$.items[0].documentSha256").doesNotExist());
  mvc.perform(get("/api/me/admin-applications").cookie(other.cookie())).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get("/api/manager/admin-applications").cookie(other.cookie())).andExpect(status().isForbidden());
  approve(m,app);assertThat(role(a)).isEqualTo("ADMIN");assertThat(active(a)).isEqualTo(app.get("id").asText());
  mvc.perform(get("/api/me").cookie(a.cookie())).andExpect(jsonPath("$.role").value("ADMIN"));
 }
 @Test void eligibilityCsrfAndValidation()throws Exception{
  var a=student();var m=actor("MANAGER");
  mvc.perform(upload(a,UUID.randomUUID(),1,"fake.pdf".getBytes())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FILE"));
  mvc.perform(upload(a,UUID.randomUUID(),1,new byte[10*1024*1024+1])).andExpect(status().isBadRequest());
  mvc.perform(upload(a,UUID.randomUUID(),2,pdf())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
  jdbc.update("UPDATE user_profiles SET education_status='YKS_ADAYI',university_department_id=NULL WHERE user_id=?",a.id());
  mvc.perform(upload(a,UUID.randomUUID(),1,pdf())).andExpect(status().isForbidden());
  mvc.perform(upload(m,UUID.randomUUID(),1,pdf())).andExpect(status().isForbidden());
  mvc.perform(multipart("/api/me/admin-applications").file(new MockMultipartFile("document",pdf())).cookie(a.cookie())).andExpect(status().isForbidden());
  mvc.perform(multipart("/api/me/admin-applications").cookie(a.cookie()).with(csrf())).andExpect(status().isBadRequest());
 }
 @Test void duplicateSubmissionAndConflictingRetry()throws Exception{
  var a=student();UUID request=UUID.randomUUID();
  var first=mapper.readTree(mvc.perform(upload(a,request,1,pdf())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
  mvc.perform(upload(a,request,1,pdf())).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(first.get("id").asText()));
  mvc.perform(upload(a,request,2,pdf())).andExpect(status().isConflict());
  mvc.perform(upload(a,UUID.randomUUID(),1,pdf())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPLICATION_PENDING"));
  assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_applications WHERE applicant_id=?",Long.class,a.id())).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM stored_files WHERE owner_id=? AND upload_status='FAILED' AND deleted_at IS NOT NULL",Long.class,a.id())).isEqualTo(1);
 }
 @Test void rejectionReapplicationAndReverificationPreservePriorApproval()throws Exception{
  var a=student();var m=actor("MANAGER");var first=submit(a);
  mvc.perform(write(decision(first),m,Map.of("status","REJECTED","version",0,"reason","  "))).andExpect(status().isBadRequest());
  mvc.perform(write(decision(first),m,Map.of("status","REJECTED","version",0,"reason","Belge okunmuyor."))).andExpect(status().isOk());
  assertThat(role(a)).isEqualTo("MEMBER");
  var approved=submit(a);approve(m,approved);
  var second=submit(a);mvc.perform(write(decision(second),m,Map.of("status","REJECTED","version",0,"reason","Yeni belge uygun değil."))).andExpect(status().isOk());
  assertThat(role(a)).isEqualTo("ADMIN");assertThat(active(a)).isEqualTo(approved.get("id").asText());
  var third=submit(a);approve(m,third);assertThat(active(a)).isEqualTo(third.get("id").asText());
  mvc.perform(write(decision(first),m,Map.of("status","APPROVED","version",1))).andExpect(status().isConflict());
 }
 @Test void revokeClosesPendingAndOldDecisionCannotRestoreAuthority()throws Exception{
  var a=student();var m=actor("MANAGER");var first=submit(a);approve(m,first);var pending=submit(a);
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
   var left=pool.submit(()->{gate.await();return mvc.perform(upload(a,request,1,pdf())).andReturn().getResponse();});
   var right=pool.submit(()->{gate.await();return mvc.perform(upload(a,request,1,pdf())).andReturn().getResponse();});gate.countDown();
   var l=left.get(15,TimeUnit.SECONDS);var r=right.get(15,TimeUnit.SECONDS);assertThat(l.getStatus()).isEqualTo(201);assertThat(r.getStatus()).isEqualTo(201);
   assertThat(mapper.readTree(l.getContentAsString()).get("id")).isEqualTo(mapper.readTree(r.getContentAsString()).get("id"));
  }
  assertThat(jdbc.queryForObject("SELECT count(*) FROM stored_files WHERE owner_id=? AND upload_status='READY'",Long.class,a.id())).isEqualTo(1);
 }
 @Test void twoManagersCannotDecideSameVersion()throws Exception{
  var a=student();var m=actor("MANAGER");var n=actor("MANAGER");var app=submit(a);var gate=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)){
   var left=pool.submit(()->{gate.await();return mvc.perform(write(decision(app),m,Map.of("status","APPROVED","version",0))).andReturn().getResponse().getStatus();});
   var right=pool.submit(()->{gate.await();return mvc.perform(write(decision(app),n,Map.of("status","REJECTED","reason","Uygun değil.","version",0))).andReturn().getResponse().getStatus();});gate.countDown();
   assertThat(List.of(left.get(15,TimeUnit.SECONDS),right.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
  }
  String state=jdbc.queryForObject("SELECT status FROM admin_applications WHERE id=?",String.class,UUID.fromString(app.get("id").asText()));assertThat(role(a)).isEqualTo(state.equals("APPROVED")?"ADMIN":"MEMBER");
 }
 @Test void decisionRollbackDoesNotGrantPartialAuthority()throws Exception{
  var a=student();var m=actor("MANAGER");var app=submit(a);String id=app.get("id").asText();
  jdbc.execute("CREATE FUNCTION fail_application_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.target_id='"+id+"'::uuid THEN RAISE EXCEPTION 'test failure'; END IF; RETURN NEW; END $$");
  jdbc.execute("CREATE TRIGGER fail_application_audit BEFORE INSERT ON management_actions FOR EACH ROW EXECUTE FUNCTION fail_application_audit()");
  try{mvc.perform(write(decision(app),m,Map.of("status","APPROVED","version",0))).andExpect(status().isServiceUnavailable());assertThat(role(a)).isEqualTo("MEMBER");assertThat(active(a)).isNull();assertThat(jdbc.queryForObject("SELECT status FROM admin_applications WHERE id=?",String.class,UUID.fromString(id))).isEqualTo("PENDING");}
  finally{jdbc.execute("DROP TRIGGER fail_application_audit ON management_actions");jdbc.execute("DROP FUNCTION fail_application_audit()");}
 }
 @Test void failedStorageIsPrivateAndRetainsMetadata()throws Exception{
  var a=student();
  // A directory at the UUID target makes CREATE_NEW fail without deleting any existing data.
  var storageBean=new com.tanidikvar.api.file.service.PrivateStorage(storage.toString());UUID id=UUID.randomUUID();Files.createDirectories(storage.resolve(id.toString()));
  assertThatThrownBy(()->storageBean.write(id,pdf())).isInstanceOf(com.tanidikvar.api.common.error.DomainException.class);
  assertThat(Files.isDirectory(storage.resolve(id.toString()))).isTrue();
  mvc.perform(upload(a,UUID.randomUUID(),999,pdf())).andExpect(status().isConflict());
  UUID failed=jdbc.queryForObject("SELECT id FROM stored_files WHERE owner_id=? AND upload_status='FAILED'",UUID.class,a.id());
  assertThat(Files.exists(storage.resolve(failed.toString()))).isTrue();
  mvc.perform(get("/api/files/"+failed+"/download").cookie(a.cookie())).andExpect(status().isNotFound());
 }
 @Test void concurrentReverificationApprovalAndRevocationCannotRestoreByAccident()throws Exception{
  var a=student();var m=actor("MANAGER");var n=actor("MANAGER");var first=submit(a);approve(m,first);var pending=submit(a);var gate=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)){
   var approval=pool.submit(()->{gate.await();return mvc.perform(write(decision(pending),m,Map.of("status","APPROVED","version",0))).andReturn().getResponse().getStatus();});
   var removal=pool.submit(()->{gate.await();return mvc.perform(post("/api/manager/users/"+a.id()+"/revoke-admin").cookie(n.cookie()).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(Map.of("verificationId",first.get("id").asText(),"reason","Yeniden kontrol gerekiyor.")))).andReturn().getResponse().getStatus();});
   gate.countDown();int accepted=approval.get(15,TimeUnit.SECONDS),revoked=removal.get(15,TimeUnit.SECONDS);
   if(revoked==204){assertThat(accepted).isEqualTo(409);assertThat(role(a)).isEqualTo("MEMBER");assertThat(active(a)).isNull();}
   else{assertThat(revoked).isEqualTo(409);assertThat(accepted).isEqualTo(200);assertThat(active(a)).isEqualTo(pending.get("id").asText());}
  }
 }
 @Test void interruptedUploadRecoveryAndHiddenApplicationCloseDocumentAccess()throws Exception{
  var a=student();UUID abandoned=UUID.randomUUID();Files.createDirectories(storage);Files.write(storage.resolve(abandoned.toString()),pdf());
  jdbc.update("INSERT INTO stored_files(id,owner_id,purpose,storage_key,original_name,content_type,byte_size,upload_status,created_at) VALUES (?,?,'VERIFICATION',?,'belge.pdf','application/pdf',?,'UPLOADING',clock_timestamp()-interval '2 hours')",abandoned,a.id(),abandoned.toString(),pdf().length);
  var app=submit(a);
  assertThat(jdbc.queryForObject("SELECT upload_status FROM stored_files WHERE id=?",String.class,abandoned)).isEqualTo("FAILED");
  assertThat(Files.exists(storage.resolve(abandoned.toString()))).isTrue();
  mvc.perform(get("/api/files/"+abandoned+"/download").cookie(a.cookie())).andExpect(status().isNotFound());
  jdbc.update("UPDATE admin_applications SET deleted_at=clock_timestamp() WHERE id=?",UUID.fromString(app.get("id").asText()));
  mvc.perform(get("/api/files/"+app.get("documentFileId").asText()+"/download").cookie(a.cookie())).andExpect(status().isNotFound());
  assertThat(Files.exists(storage.resolve(app.get("documentFileId").asText()))).isTrue();
 }
 @Test void revokeAuditFailureRollsBackAuthorityAndPendingClosure()throws Exception{
  var a=student();var m=actor("MANAGER");var first=submit(a);approve(m,first);var pending=submit(a);
  jdbc.execute("CREATE FUNCTION fail_revoke_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.target_id='"+a.id()+"'::uuid AND NEW.action='REVOKE_ADMIN' THEN RAISE EXCEPTION 'test failure'; END IF; RETURN NEW; END $$");
  jdbc.execute("CREATE TRIGGER fail_revoke_audit BEFORE INSERT ON management_actions FOR EACH ROW EXECUTE FUNCTION fail_revoke_audit()");
  try{
   mvc.perform(post("/api/manager/users/"+a.id()+"/revoke-admin").cookie(m.cookie()).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(Map.of("verificationId",first.get("id").asText(),"reason","Kontrol")))).andExpect(status().isServiceUnavailable());
   assertThat(role(a)).isEqualTo("ADMIN");assertThat(active(a)).isEqualTo(first.get("id").asText());
   assertThat(jdbc.queryForObject("SELECT status FROM admin_applications WHERE id=?",String.class,UUID.fromString(pending.get("id").asText()))).isEqualTo("PENDING");
  }finally{jdbc.execute("DROP TRIGGER fail_revoke_audit ON management_actions");jdbc.execute("DROP FUNCTION fail_revoke_audit()");}
 }
 byte[] png()throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(800,400,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}
 JsonNode avatar(Actor a)throws Exception{return mapper.readTree(mvc.perform(multipart("/api/me/avatar").file(new MockMultipartFile("file","avatar.png","image/png",png())).cookie(a.cookie()).with(csrf())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 @Test void avatarReencodedPublicReplacedAndRemovedWithoutPhysicalDelete()throws Exception{
  var a=student();String first=avatar(a).get("fileId").asText();var bytes=mvc.perform(get("/api/avatars/"+first)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
  assertThat(ImageIO.read(new java.io.ByteArrayInputStream(bytes)).getWidth()).isEqualTo(512);
  String second=avatar(a).get("fileId").asText();mvc.perform(get("/api/avatars/"+first)).andExpect(status().isNotFound());
  assertThat(Files.exists(storage.resolve(first))).isTrue();
  mvc.perform(get("/api/files/"+second+"/download").cookie(a.cookie())).andExpect(status().isNotFound());
  mvc.perform(post("/api/me/avatar/remove").cookie(a.cookie()).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(get("/api/avatars/"+second)).andExpect(status().isNotFound());assertThat(Files.exists(storage.resolve(second))).isTrue();
  mvc.perform(multipart("/api/me/avatar").file(new MockMultipartFile("file","evil.png","image/png","<svg/>".getBytes())).cookie(a.cookie()).with(csrf())).andExpect(status().isBadRequest());
 }
 @Test void physicalDeleteSnapshotAndOwnershipConstraints()throws Exception{
  var a=student();var other=student();var app=submit(a);UUID id=UUID.fromString(app.get("id").asText());var m=actor("MANAGER");approve(m,app);
  assertThatThrownBy(()->jdbc.update("UPDATE admin_applications SET submitted_first_name='Changed' WHERE id=?",id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("UPDATE users SET active_verification_application_id=? WHERE id=?",id,other.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.execute("DELETE FROM stored_files")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.execute("DELETE FROM admin_applications")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  jdbc.update("UPDATE users SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",a.id());
  mvc.perform(get("/api/files/"+app.get("documentFileId").asText()+"/download").cookie(m.cookie())).andExpect(status().isNotFound());
 }
}
