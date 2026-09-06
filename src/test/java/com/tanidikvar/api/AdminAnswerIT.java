package com.tanidikvar.api;
import com.tanidikvar.api.auth.service.AuthenticationService;
import com.tanidikvar.api.answer.service.AdminAnswerService;
import com.tanidikvar.api.application.service.ApplicationService;
import com.tanidikvar.api.application.dto.RevokeRequest;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.*;
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
@Import(AdminAnswerIT.TimeConfiguration.class)
class AdminAnswerIT {
 @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.9-alpine");
 static final AtomicReference<Instant> now=new AtomicReference<>(Instant.parse("2026-09-05T20:59:59Z"));
 @TestConfiguration static class TimeConfiguration {
  @Bean @Primary Clock controlledClock(){return new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return Clock.fixed(now.get(),zone);}public Instant instant(){return now.get();}};}
 }
 @BeforeEach void resetClock(){now.set(Instant.parse("2026-09-05T20:59:59Z"));}
 @DynamicPropertySource static void configuration(DynamicPropertyRegistry p){p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);p.add("app.auth.secret",()->Base64.getEncoder().encodeToString(new byte[48]));}
 @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper mapper;@Autowired PasswordEncoder passwords;@Autowired AuthenticationService auth;@Autowired ApplicationService applications;
 record Actor(UUID id,Cookie cookie){}
 Actor actor(String role){UUID id=UUID.randomUUID();String email=id+"@example.test";jdbc.update("INSERT INTO users(id,email,password_hash,authority,email_verified_at,created_at,updated_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,email,passwords.encode("Testing-password!"),role);return new Actor(id,new Cookie("TV_ACCESS",auth.login(email,"Testing-password!").accessToken()));}
 MockHttpServletRequestBuilder write(String method,String path,Actor a,Object body){return (method.equals("PUT")?put(path):post(path)).cookie(a.cookie()).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(body));}
 Actor member(){var a=actor("MEMBER");UUID u=UUID.randomUUID(),d=UUID.randomUUID(),e=UUID.randomUUID();jdbc.update("INSERT INTO universities(id,name,normalized_name) VALUES (?,?,?)",u,"Test Üniversitesi "+u,u.toString());jdbc.update("INSERT INTO departments(id,name,normalized_name) VALUES (?,?,?)",d,"Bilgisayar "+d,d.toString());jdbc.update("INSERT INTO university_departments(id,university_id,department_id) VALUES (?,?,?)",e,u,d);jdbc.update("INSERT INTO user_profiles(user_id,first_name,last_name,education_status,university_department_id) VALUES (?,'Ada','Yılmaz','UNIVERSITE_OGRENCISI',?)",a.id(),e);return a;}
 UUID verification(Actor a,String education){
  UUID file=UUID.randomUUID(),v=UUID.randomUUID();jdbc.update("INSERT INTO stored_files(id,owner_id,purpose,storage_key,original_name,content_type,byte_size,upload_status) VALUES (?,?,'VERIFICATION',?,'belge.pdf','application/pdf',10,'READY')",file,a.id(),file.toString());
  jdbc.update("INSERT INTO admin_applications(id,applicant_id,request_id,submitted_first_name,submitted_last_name,education_status,university_department_id,university_name,department_name,graduation_year,document_file_id,document_sha256,profile_version,status,reviewed_by,reviewed_at) SELECT ?,p.user_id,?,'Ada','Yılmaz',?,p.university_department_id,'Doğrulanmış Üniversite','Bilgisayar',?,?,'test-hash',p.version,'APPROVED',p.user_id,clock_timestamp() FROM user_profiles p WHERE p.user_id=?",v,UUID.randomUUID(),education,education.equals("MEZUN")?2025:null,file,a.id());
  jdbc.update("UPDATE users SET authority='ADMIN',active_verification_application_id=? WHERE id=?",v,a.id());return v;
 }
 Actor admin(){var a=member();verification(a,"UNIVERSITE_OGRENCISI");return a;}
 String question(Actor ignored)throws Exception{var owner=member();return mapper.readTree(mvc.perform(write("POST","/api/questions",owner,Map.of("requestId",UUID.randomUUID(),"content",Map.of("title","Üniversite deneyimleri hakkında soru","scope","GENERAL","tagIds",List.of())))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();}
 String questionAs(Actor owner)throws Exception{return mapper.readTree(mvc.perform(write("POST","/api/questions",owner,Map.of("requestId",UUID.randomUUID(),"content",Map.of("title","Üniversite deneyimleri hakkında soru","scope","GENERAL","tagIds",List.of())))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();}
 void assign(Actor a,String q,long version)throws Exception{mvc.perform(write("PUT","/api/questions/"+q+"/assignment",a,Map.of("assigned",true,"version",version))).andExpect(status().isOk());}
 JsonNode publish(Actor a,String q)throws Exception{return mapper.readTree(mvc.perform(write("POST","/api/questions/"+q+"/admin-answers",a,Map.of("body","Üniversitede edindiğim gerçek deneyim."))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
 String path(JsonNode a){return "/api/admin-answers/"+a.get("id").asText();}
 UUID verification(Actor a){return jdbc.queryForObject("SELECT active_verification_application_id FROM users WHERE id=?",UUID.class,a.id());}
 @Test void assignmentIsPrivateVersionedCancellableAndDoesNotConsumeQuota()throws Exception{
  var a=admin();var b=admin();String q=question(a);
  mvc.perform(get("/api/questions/"+q+"/my-admin-answer").cookie(a.cookie())).andExpect(jsonPath("$.assignment.version").value(0));
  assign(a,q,0);assign(b,q,0);
  mvc.perform(get("/api/me/admin-quota").cookie(a.cookie())).andExpect(jsonPath("$.used").value(0));
  mvc.perform(write("PUT","/api/questions/"+q+"/assignment",a,Map.of("assigned",false,"version",0))).andExpect(status().isConflict());
  mvc.perform(write("PUT","/api/questions/"+q+"/assignment",a,Map.of("assigned",false,"version",1))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
  mvc.perform(get("/api/me/assignments").cookie(a.cookie())).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get("/api/me/assignments").cookie(b.cookie())).andExpect(jsonPath("$.totalElements").value(1));
  assign(a,q,2);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM question_assignments WHERE admin_id=?",Long.class,a.id())).isEqualTo(1);
 }
 @Test void onlyVerifiedCompleteAdminsCanAssignAndPublishAndCommunityRemainsSeparate()throws Exception{
  var a=admin();var b=member();var m=actor("MANAGER");String q=question(a),url="/api/questions/"+q+"/admin-answers";
  mvc.perform(get(url)).andExpect(status().isOk());
  mvc.perform(post(url).with(csrf()).contentType("application/json").content("{\"body\":\"Deneme cevap metnidir.\"}")).andExpect(status().isUnauthorized());
  mvc.perform(write("POST",url,a,Map.of("body","Deneme cevap metnidir."))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ASSIGNMENT_REQUIRED"));
  for(var invalid:List.of(b,m)){mvc.perform(write("PUT","/api/questions/"+q+"/assignment",invalid,Map.of("assigned",true,"version",0))).andExpect(status().isForbidden());mvc.perform(write("POST",url,invalid,Map.of("body","Deneme cevap metnidir."))).andExpect(status().isForbidden());}
  mvc.perform(put("/api/questions/"+q+"/assignment").cookie(a.cookie()).contentType("application/json").content("{\"assigned\":true,\"version\":0}")).andExpect(status().isForbidden());
  assign(a,q,0);var answer=publish(a,q);
  mvc.perform(write("POST","/api/questions/"+q+"/answers",a,Map.of("body","Ayrı topluluk deneyim metnidir."))).andExpect(status().isCreated());
  mvc.perform(get("/api/questions/"+q+"/answers")).andExpect(jsonPath("$.totalElements").value(1));
  mvc.perform(get(url)).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].documentFileId").doesNotExist());
  mvc.perform(get("/api/questions/"+q+"/statistics")).andExpect(jsonPath("$.adminAnswerCount").value(1)).andExpect(jsonPath("$.communityAnswerCount").value(1)).andExpect(jsonPath("$.totalAnswerCount").value(2));
  mvc.perform(write("PUT","/api/answers/"+answer.get("id").asText(),a,Map.of("body","Yanlış türe yazma denemesi","version",0))).andExpect(status().isNotFound());
  mvc.perform(write("PUT",path(answer),b,Map.of("body","Başkasının cevabını değiştirme","version",0))).andExpect(status().isForbidden());
  jdbc.update("UPDATE user_profiles SET deleted_at=clock_timestamp() WHERE user_id=?",a.id());
  mvc.perform(write("PUT",path(answer)+"/status",a,Map.of("deleted",true,"version",0))).andExpect(status().isForbidden());
 }
 @Test void sixParallelQuestionsAllowOnlyFivePublicationsAndNoPartialSixthAnswer()throws Exception{
  var a=admin();var qs=new ArrayList<String>();for(int i=0;i<6;i++){String q=question(a);assign(a,q,0);qs.add(q);}
  var gate=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(6)){
   var futures=new ArrayList<Future<Integer>>();for(String q:qs)futures.add(pool.submit(()->{gate.await();return mvc.perform(write("POST","/api/questions/"+q+"/admin-answers",a,Map.of("body","Paralel yayınlanan Admin deneyimi."))).andReturn().getResponse().getStatus();}));
   gate.countDown();var statuses=new ArrayList<Integer>();for(var f:futures)statuses.add(f.get(20,TimeUnit.SECONDS));assertThat(statuses.stream().filter(s->s==201).count()).isEqualTo(5);assertThat(statuses.stream().filter(s->s==409).count()).isEqualTo(1);
  }
  mvc.perform(get("/api/me/admin-quota").cookie(a.cookie())).andExpect(jsonPath("$.used").value(5)).andExpect(jsonPath("$.remaining").value(0));
  assertThat(jdbc.queryForObject("SELECT count(*) FROM answers WHERE author_id=? AND answer_kind='ADMIN'",Long.class,a.id())).isEqualTo(5);
 }
 @Test void duplicateFirstPublicationConsumesOneSlotAndDifferentAdminsCanReply()throws Exception{
  var a=admin();var b=admin();String q=question(a);assign(a,q,0);assign(b,q,0);var gate=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)){var l=pool.submit(()->{gate.await();return publish(a,q).get("id").asText();});var r=pool.submit(()->{gate.await();return publish(a,q).get("id").asText();});gate.countDown();assertThat(l.get(10,TimeUnit.SECONDS)).isEqualTo(r.get(10,TimeUnit.SECONDS));}
  publish(b,q);mvc.perform(get("/api/questions/"+q+"/admin-answers")).andExpect(jsonPath("$.totalElements").value(2));
  mvc.perform(get("/api/me/admin-quota").cookie(a.cookie())).andExpect(jsonPath("$.used").value(1));
 }
 @Test void removalRestoreAndEditKeepQuotaPublicationAndVerificationEvenAfterReverification()throws Exception{
  var a=admin();String q=question(a);assign(a,q,0);var answer=publish(a,q);UUID old=verification(a);
  mvc.perform(write("PUT","/api/questions/"+q+"/assignment",a,Map.of("assigned",false,"version",1))).andExpect(status().isOk());
  mvc.perform(write("PUT",path(answer),a,Map.of("body","Düzenlenmiş üniversite deneyimim.","version",0))).andExpect(status().isOk()).andExpect(jsonPath("$.editedAt").isNotEmpty());
  mvc.perform(write("PUT",path(answer)+"/status",a,Map.of("deleted",true,"version",1))).andExpect(status().isOk());
  verification(a,"MEZUN");
  mvc.perform(write("PUT",path(answer)+"/status",a,Map.of("deleted",false,"version",2))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ASSIGNMENT_REQUIRED"));
  assign(a,q,2);mvc.perform(write("PUT",path(answer)+"/status",a,Map.of("deleted",false,"version",2))).andExpect(status().isOk()).andExpect(jsonPath("$.publishedAt").value(answer.get("publishedAt").asText())).andExpect(jsonPath("$.educationStatus").value("UNIVERSITE_OGRENCISI"));
  mvc.perform(get("/api/admins/"+a.id())).andExpect(jsonPath("$.educationStatus").value("MEZUN"));
  assertThat(jdbc.queryForObject("SELECT verification_application_id FROM answers WHERE id=?",UUID.class,UUID.fromString(answer.get("id").asText()))).isEqualTo(old);
  mvc.perform(get("/api/me/admin-quota").cookie(a.cookie())).andExpect(jsonPath("$.used").value(1));
  mvc.perform(write("PUT",path(answer)+"/status",a,Map.of("deleted",true,"version",1))).andExpect(status().isConflict());
 }
 @Test void midnightInTurkeyResetsQuotaButOldRestorationNeverConsumesNewDay()throws Exception{
  var a=admin();String q=question(a);assign(a,q,0);var answer=publish(a,q);
  mvc.perform(get("/api/me/admin-quota").cookie(a.cookie())).andExpect(jsonPath("$.day").value("2026-09-05")).andExpect(jsonPath("$.used").value(1)).andExpect(jsonPath("$.resetsAt").value("2026-09-05T21:00:00Z"));
  now.set(Instant.parse("2026-09-05T21:00:00Z"));
  mvc.perform(get("/api/me/admin-quota").cookie(a.cookie())).andExpect(jsonPath("$.day").value("2026-09-06")).andExpect(jsonPath("$.used").value(0));
  mvc.perform(write("PUT",path(answer)+"/status",a,Map.of("deleted",true,"version",0))).andExpect(status().isOk());
  mvc.perform(write("PUT",path(answer)+"/status",a,Map.of("deleted",false,"version",1))).andExpect(status().isOk());
  mvc.perform(get("/api/me/admin-quota").cookie(a.cookie())).andExpect(jsonPath("$.used").value(0));
  String next=question(a);assign(a,next,0);publish(a,next);mvc.perform(get("/api/me/admin-quota").cookie(a.cookie())).andExpect(jsonPath("$.used").value(1));
 }
 @Test void revokedAuthorCanRemoveButCannotEditRestoreOrPublishAndPublicHistoryIsMarked()throws Exception{
  var a=admin();var m=actor("MANAGER");String q=question(a);assign(a,q,0);var answer=publish(a,q);
  applications.revoke(m.id(),a.id(),new RevokeRequest(verification(a),"Doğrulama kaldırıldı."));
  mvc.perform(get("/api/admins/"+a.id())).andExpect(jsonPath("$.activeAdmin").value(false)).andExpect(jsonPath("$.answerCount").value(1)).andExpect(jsonPath("$.email").doesNotExist());
  mvc.perform(get("/api/questions/"+q+"/admin-answers")).andExpect(jsonPath("$.items[0].activeAdmin").value(false));
  mvc.perform(write("PUT",path(answer),a,Map.of("body","Yeni bir düzenleme denemesi","version",0))).andExpect(status().isForbidden());
  mvc.perform(write("PUT",path(answer)+"/status",a,Map.of("deleted",true,"version",0))).andExpect(status().isOk());
  mvc.perform(write("PUT",path(answer)+"/status",a,Map.of("deleted",false,"version",1))).andExpect(status().isForbidden());
  mvc.perform(get("/api/admins/"+a.id()+"/answers")).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get("/api/me/admin-answers").cookie(a.cookie())).andExpect(jsonPath("$.totalElements").value(1));
 }
 @Test void archivedQuestionsAllowRemovalButNoEditRestoreOrNewAssignment()throws Exception{
  var a=admin();var owner=member();String q=questionAs(owner);assign(a,q,0);var answer=publish(a,q);
  mvc.perform(write("POST","/api/questions/"+q+"/archive",owner,Map.of("version",0))).andExpect(status().isOk());
  mvc.perform(get("/api/admins/"+a.id()+"/answers")).andExpect(jsonPath("$.totalElements").value(1));
  mvc.perform(write("PUT",path(answer),a,Map.of("body","Arşivde değişiklik denemesi","version",0))).andExpect(status().isConflict());
  mvc.perform(write("PUT",path(answer)+"/status",a,Map.of("deleted",true,"version",0))).andExpect(status().isOk());
  mvc.perform(write("PUT",path(answer)+"/status",a,Map.of("deleted",false,"version",1))).andExpect(status().isConflict());
  mvc.perform(write("PUT","/api/questions/"+q+"/assignment",a,Map.of("assigned",false,"version",1))).andExpect(status().isOk());
  mvc.perform(write("PUT","/api/questions/"+q+"/assignment",a,Map.of("assigned",true,"version",2))).andExpect(status().isConflict());
 }
 @Test void hiddenQuestionAndDeletedAuthorNeverLeakProfileSnapshot()throws Exception{
  var a=admin();String q=question(a);assign(a,q,0);publish(a,q);
  jdbc.update("UPDATE user_profiles SET deleted_at=clock_timestamp() WHERE user_id=?",a.id());
  mvc.perform(get("/api/admins/"+a.id())).andExpect(status().isNotFound());
  mvc.perform(get("/api/questions/"+q+"/admin-answers")).andExpect(jsonPath("$.items[0].authorName").value("Katılımcı")).andExpect(jsonPath("$.items[0].authorId").isEmpty()).andExpect(jsonPath("$.items[0].universityName").isEmpty());
  jdbc.update("UPDATE questions SET deleted_at=clock_timestamp() WHERE id=?",UUID.fromString(q));
  mvc.perform(get("/api/questions/"+q+"/admin-answers")).andExpect(status().isNotFound());
  mvc.perform(get("/api/me/admin-answers").cookie(a.cookie())).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get("/api/me/assignments").cookie(a.cookie())).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get("/api/me/admin-quota").cookie(a.cookie())).andExpect(jsonPath("$.used").value(1));
 }
 @Test void databaseRejectsUnverifiedCrossOwnerReferencesAndPhysicalDeletion()throws Exception{
  var a=admin();var b=admin();String q=question(a);assign(a,q,0);var answer=publish(a,q);
  assertThatThrownBy(()->jdbc.update("INSERT INTO answers(id,question_id,author_id,answer_kind,verification_application_id,body) VALUES (?,?,?,'ADMIN',?,'Geçersiz sahiplik denemesi')",UUID.randomUUID(),UUID.fromString(q),b.id(),verification(a))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("UPDATE answers SET published_at=clock_timestamp() WHERE id=?",UUID.fromString(answer.get("id").asText()))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.execute("DELETE FROM question_assignments")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.execute("TRUNCATE question_assignments")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
 @Test void failedPublicationRollsBackQuotaAndKeepsAssignment()throws Exception{
  var a=admin();String q=question(a);assign(a,q,0);
  jdbc.execute("CREATE FUNCTION fail_admin_answer() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.author_id='"+a.id()+"'::uuid THEN RAISE EXCEPTION 'test failure'; END IF; RETURN NEW; END $$");
  jdbc.execute("CREATE TRIGGER fail_admin_answer AFTER INSERT ON answers FOR EACH ROW EXECUTE FUNCTION fail_admin_answer()");
  try{mvc.perform(write("POST","/api/questions/"+q+"/admin-answers",a,Map.of("body","Rollback olacak Admin cevabı."))).andExpect(status().isServiceUnavailable());mvc.perform(get("/api/me/admin-quota").cookie(a.cookie())).andExpect(jsonPath("$.used").value(0));mvc.perform(get("/api/me/assignments").cookie(a.cookie())).andExpect(jsonPath("$.totalElements").value(1));}
  finally{jdbc.execute("DROP TRIGGER fail_admin_answer ON answers");jdbc.execute("DROP FUNCTION fail_admin_answer()");}
 }
 @Test void concurrentEditsAndRevocationAreSerialized()throws Exception{
  var a=admin();var m=actor("MANAGER");String q=question(a);assign(a,q,0);var answer=publish(a,q);UUID verification=verification(a);var gate=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)){var edit=pool.submit(()->{gate.await();return mvc.perform(write("PUT",path(answer),a,Map.of("body","Eşzamanlı güncelleme metnidir.","version",0))).andReturn().getResponse().getStatus();});var revoke=pool.submit(()->{gate.await();applications.revoke(m.id(),a.id(),new RevokeRequest(verification,"Yetki kaldırıldı."));return true;});gate.countDown();assertThat(edit.get(15,TimeUnit.SECONDS)).isIn(200,403);assertThat(revoke.get(15,TimeUnit.SECONDS)).isTrue();}
  mvc.perform(write("PUT",path(answer),a,Map.of("body","Yetki sonrası düzenleme metni","version",0))).andExpect(status().is4xxClientError());
  assertThat(jdbc.queryForObject("SELECT authority FROM users WHERE id=?",String.class,a.id())).isEqualTo("MEMBER");
 }
}

