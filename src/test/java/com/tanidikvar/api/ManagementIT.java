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
@SpringBootTest @AutoConfigureMockMvc(print=MockMvcPrint.NONE) @ActiveProfiles("local") @Testcontainers
class ManagementIT {
 @org.junit.jupiter.api.io.TempDir static java.nio.file.Path storage;
 @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.9-alpine");
 @DynamicPropertySource static void configuration(DynamicPropertyRegistry p){p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);p.add("app.storage-directory",()->storage.toString());p.add("app.auth.secret",()->Base64.getEncoder().encodeToString(new byte[48]));}
 @Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired PasswordEncoder passwords;@Autowired AuthenticationService auth;
 record Actor(UUID id,String email,Cookie cookie){}
 Actor actor(String role){UUID id=UUID.randomUUID();String email=id+"@example.test";jdbc.update("INSERT INTO users(id,email,password_hash,authority,email_verified_at,created_at,updated_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,email,passwords.encode("Testing-password!"),role);return new Actor(id,email,new Cookie("TV_ACCESS",auth.login(email,"Testing-password!").accessToken()));}
 void profile(Actor a){jdbc.update("INSERT INTO user_profiles(user_id,first_name,last_name,education_status) VALUES (?,'Ada','Yılmaz','YKS_ADAYI')",a.id());}
 UUID question(Actor a){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO questions(id,author_id,request_id,title,scope) VALUES (?,?,?,?,'GENERAL')",id,a.id(),UUID.randomUUID(),"Yönetim sorusu "+id);return id;}
 UUID answer(Actor a,UUID q,UUID verification){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO answers(id,question_id,author_id,answer_kind,verification_application_id,body,published_at) VALUES (?,?,?,?,?,'Korunacak gerçek deneyim metni',clock_timestamp()-interval '1 second')",id,q,a.id(),verification==null?"COMMUNITY":"ADMIN",verification);return id;}
 UUID application(Actor a,Actor manager,boolean approved){UUID uni=UUID.randomUUID(),dep=UUID.randomUUID(),edu=UUID.randomUUID(),file=UUID.randomUUID(),v=UUID.randomUUID();jdbc.update("INSERT INTO universities(id,name,normalized_name) VALUES (?,?,?)",uni,"Test Üniversitesi",uni.toString());jdbc.update("INSERT INTO departments(id,name,normalized_name) VALUES (?,?,?)",dep,"Test Bölümü",dep.toString());jdbc.update("INSERT INTO university_departments(id,university_id,department_id) VALUES (?,?,?)",edu,uni,dep);
  jdbc.update("INSERT INTO stored_files(id,owner_id,purpose,storage_key,original_name,content_type,byte_size,upload_status) VALUES (?,?,'VERIFICATION',?,'test.pdf','application/pdf',10,'READY')",file,a.id(),file.toString());
  jdbc.update("INSERT INTO admin_applications(id,applicant_id,request_id,submitted_first_name,submitted_last_name,education_status,university_department_id,university_name,department_name,document_file_id,document_sha256,profile_version,status,reviewed_by,reviewed_at) VALUES (?,?,?,'Ada','Yılmaz','UNIVERSITE_OGRENCISI',?,'Test Üniversitesi','Test Bölümü',?,'test-hash',0,?,?,CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END)",v,a.id(),UUID.randomUUID(),edu,file,approved?"APPROVED":"PENDING",approved?manager.id():null,approved);
  if(approved)jdbc.update("UPDATE users SET authority='ADMIN',active_verification_application_id=? WHERE id=?",v,a.id());return v;
 }
 MockHttpServletRequestBuilder write(String method,String path,Actor a,Object body){return (method.equals("PUT")?put(path):post(path)).cookie(a.cookie()).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(body));}
 Map<String,Object> change(boolean hidden,long version){return Map.of("hidden",hidden,"version",version,"reason","İnceleme sonucu verilen test kararı");}
 String path(UUID id,String kind){return "/api/manager/content/"+kind+"/"+id+"/status";}
 ResultActions moderate(Actor m,UUID id,String kind,boolean hidden,long version)throws Exception{return mvc.perform(write("PUT",path(id,kind),m,change(hidden,version)));}
 ResultActions disable(Actor m,Actor a,boolean disabled,long version)throws Exception{return mvc.perform(write("PUT","/api/manager/users/"+a.id()+"/status",m,change(disabled,version)));}
 long version(Actor a){return jdbc.queryForObject("SELECT version FROM users WHERE id=?",Long.class,a.id());}
 JsonNode read(String path,Actor a)throws Exception{return mapper.readTree(mvc.perform(get(path).cookie(a.cookie())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 @Test void managerOnlyReadsWritesCsrfValidationAndProtectedAccounts()throws Exception{
  var m=actor("MANAGER");var member=actor("MEMBER");var admin=actor("ADMIN");UUID q=question(member);
  for(String path:List.of("/api/manager/statistics","/api/manager/users","/api/manager/content","/api/manager/actions")){
   mvc.perform(get(path)).andExpect(status().isUnauthorized());mvc.perform(get(path).cookie(member.cookie())).andExpect(status().isForbidden());mvc.perform(get(path).cookie(admin.cookie())).andExpect(status().isForbidden());mvc.perform(get(path).cookie(m.cookie())).andExpect(status().isOk());
  }
  moderate(admin,q,"QUESTION",true,0).andExpect(status().isForbidden());
  mvc.perform(put(path(q,"QUESTION")).cookie(m.cookie()).contentType("application/json").content(mapper.writeValueAsString(change(true,0)))).andExpect(status().isForbidden());
  mvc.perform(write("PUT",path(q,"QUESTION"),m,Map.of("hidden",true,"version",0,"reason","   "))).andExpect(status().isBadRequest());
  disable(m,m,true,version(m)).andExpect(status().isForbidden());
  mvc.perform(get("/api/manager/users").cookie(m.cookie()).param("size","101")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/manager/content").cookie(m.cookie()).param("kind","INVALID")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/manager/users").cookie(m.cookie()).param("status","INVALID")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/manager/users").cookie(m.cookie()).param("q","x".repeat(101))).andExpect(status().isBadRequest());
 }
 @Test void questionHideRestorePreservesArchiveAndContentAndAuditsOnlyChanges()throws Exception{
  var m=actor("MANAGER");var a=actor("MEMBER");UUID q=question(a),answer=answer(a,q,null);jdbc.update("UPDATE questions SET archived_at=CURRENT_TIMESTAMP WHERE id=?",q);String title=jdbc.queryForObject("SELECT title FROM questions WHERE id=?",String.class,q);
  moderate(m,q,"QUESTION",true,0).andExpect(status().isOk()).andExpect(jsonPath("$.title").value(title)).andExpect(jsonPath("$.moderatedAt").isNotEmpty());
  mvc.perform(get("/api/questions/"+q)).andExpect(status().isNotFound());mvc.perform(get("/api/questions/"+q+"/answers")).andExpect(status().isNotFound());
  moderate(m,q,"QUESTION",true,1).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
  moderate(m,q,"QUESTION",false,0).andExpect(status().isConflict());
  moderate(m,q,"QUESTION",false,1).andExpect(status().isOk()).andExpect(jsonPath("$.archivedAt").isNotEmpty());
  mvc.perform(get("/api/questions/"+q)).andExpect(status().isOk()).andExpect(jsonPath("$.archivedAt").isNotEmpty());
  assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM answers WHERE id=?",Boolean.class,answer)).isTrue();
  assertThat(jdbc.queryForObject("SELECT count(*) FROM management_actions WHERE target_id=?",Long.class,q)).isEqualTo(2);
 }
 @Test void moderatedCommunityAnswerCannotBeEditedRecreatedOrRestoredByOwner()throws Exception{
  var m=actor("MANAGER");var a=actor("MEMBER");profile(a);UUID q=question(a),id=answer(a,q,null);String own="/api/answers/"+id;
  moderate(m,id,"COMMUNITY",true,0).andExpect(status().isOk());
  mvc.perform(get("/api/questions/"+q+"/answers")).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get("/api/questions/"+q+"/my-answer").cookie(a.cookie())).andExpect(jsonPath("$.moderatedAt").isNotEmpty());
  mvc.perform(write("PUT",own,a,Map.of("body","Yeni metin ile gizlemeyi aşma","version",1))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANSWER_MODERATED"));
  mvc.perform(write("POST","/api/questions/"+q+"/answers",a,Map.of("body","Korunacak gerçek deneyim metni"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANSWER_MODERATED"));
  mvc.perform(write("PUT",own+"/status",a,Map.of("deleted",false,"version",1))).andExpect(status().isConflict());
  mvc.perform(write("PUT",own+"/status",a,Map.of("deleted",true,"version",1))).andExpect(status().isOk());
  moderate(m,id,"COMMUNITY",false,2).andExpect(status().isOk()).andExpect(jsonPath("$.deletedAt").isNotEmpty()).andExpect(jsonPath("$.moderatedAt").isEmpty());
  mvc.perform(get("/api/questions/"+q+"/answers")).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(write("PUT",own+"/status",a,Map.of("deleted",false,"version",3))).andExpect(status().isOk());
  mvc.perform(get("/api/questions/"+q+"/answers")).andExpect(jsonPath("$.totalElements").value(1));
 }
 @Test void adminModerationUpdatesCountsHistoryFiltersAndPopularityWithoutRefundingQuota()throws Exception{
  var m=actor("MANAGER");var a=actor("MEMBER");profile(a);UUID v=application(a,m,true),q=question(a),id=answer(a,q,v);
  mvc.perform(get("/api/me/admin-quota").cookie(a.cookie())).andExpect(jsonPath("$.used").value(1));
  moderate(m,id,"ADMIN",true,0).andExpect(status().isOk());
  mvc.perform(get("/api/questions/"+q+"/admin-answers")).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get("/api/admins/"+a.id())).andExpect(jsonPath("$.answerCount").value(0));
  mvc.perform(get("/api/questions").param("adminId",a.id().toString())).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get("/api/popular").param("q",q.toString())).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get("/api/questions/"+q)).andExpect(jsonPath("$.statistics.adminAnswerCount").value(0));
  mvc.perform(get("/api/me/admin-quota").cookie(a.cookie())).andExpect(jsonPath("$.used").value(1));
  mvc.perform(write("PUT","/api/admin-answers/"+id,a,Map.of("body","Düzenleme denemesi yeni metin","version",1))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANSWER_MODERATED"));
  mvc.perform(write("PUT","/api/admin-answers/"+id+"/status",a,Map.of("deleted",false,"version",1))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANSWER_MODERATED"));
  moderate(m,id,"ADMIN",false,1).andExpect(status().isOk());
  mvc.perform(get("/api/questions/"+q+"/admin-answers")).andExpect(jsonPath("$.totalElements").value(1));
  mvc.perform(get("/api/popular").param("q",q.toString())).andExpect(jsonPath("$.totalElements").value(1));
 }
 @Test void disabledAdminLosesSessionsTokensAuthorityAndPendingApplicationButRetainsContent()throws Exception{
  var m=actor("MANAGER");var a=actor("MEMBER");profile(a);UUID approved=application(a,m,true),pending=application(a,m,false),q=question(a);answer(a,q,approved);long version=version(a);
  for(String purpose:List.of("VERIFY_EMAIL","RESET_PASSWORD"))jdbc.update("INSERT INTO auth_action_tokens(id,user_id,purpose,token_hash,expires_at,created_at,updated_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP+interval '1 hour',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",UUID.randomUUID(),a.id(),purpose,UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-",""));
  disable(m,a,true,version).andExpect(status().isOk()).andExpect(jsonPath("$.authority").value("MEMBER")).andExpect(jsonPath("$.version").value(version+1));
  mvc.perform(get("/api/me").cookie(a.cookie())).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/questions/"+q)).andExpect(status().isOk()).andExpect(jsonPath("$.authorName").value("Katılımcı")).andExpect(jsonPath("$.authorId").isEmpty());
  mvc.perform(get("/api/questions/"+q+"/admin-answers")).andExpect(jsonPath("$.items[0].authorName").value("Katılımcı")).andExpect(jsonPath("$.items[0].universityName").isEmpty());
  assertThat(jdbc.queryForObject("SELECT status FROM admin_applications WHERE id=?",String.class,pending)).isEqualTo("REJECTED");
  assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions WHERE user_id=? AND revoked_at IS NULL",Long.class,a.id())).isZero();
  assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_action_tokens WHERE user_id=? AND consumed_at IS NULL",Long.class,a.id())).isZero();
  disable(m,a,false,version+1).andExpect(status().isOk()).andExpect(jsonPath("$.authority").value("MEMBER")).andExpect(jsonPath("$.deletedAt").isEmpty());
  mvc.perform(get("/api/me").cookie(a.cookie())).andExpect(status().isUnauthorized());
  var newCookie=new Cookie("TV_ACCESS",auth.login(a.email(),"Testing-password!").accessToken());mvc.perform(get("/api/me").cookie(newCookie)).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("YKS_ADAYI"));
  assertThat(jdbc.queryForObject("SELECT active_verification_application_id IS NULL FROM users WHERE id=?",Boolean.class,a.id())).isTrue();
 }
 @Test void unverifiedAccountsCanBeManagedAndListIsBoundedAndPrivate()throws Exception{
  var m=actor("MANAGER");var a=actor("MEMBER");jdbc.update("UPDATE users SET email_verified_at=NULL WHERE id=?",a.id());
  mvc.perform(get("/api/manager/users").cookie(m.cookie()).param("q",a.email()).param("size","1")).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].passwordHash").doesNotExist()).andExpect(jsonPath("$.items[0].emailVerified").value(false));
  disable(m,a,true,version(a)).andExpect(status().isOk());
  mvc.perform(get("/api/manager/users").cookie(m.cookie()).param("q",a.email()).param("status","VISIBLE")).andExpect(jsonPath("$.totalElements").value(0));
  disable(m,a,false,version(a)).andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(false));
 }
 @Test void simultaneousModerationAndOwnerRestoreCannotPublishHiddenAnswer()throws Exception{
  var m=actor("MANAGER");var a=actor("MEMBER");profile(a);UUID q=question(a),id=answer(a,q,null);jdbc.update("UPDATE answers SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",id);
  var gate=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){
   var hide=pool.submit(()->{gate.await();return moderate(m,id,"COMMUNITY",true,0).andReturn().getResponse().getStatus();});
   var restore=pool.submit(()->{gate.await();return mvc.perform(write("PUT","/api/answers/"+id+"/status",a,Map.of("deleted",false,"version",0))).andReturn().getResponse().getStatus();});gate.countDown();
   assertThat(List.of(hide.get(10,TimeUnit.SECONDS),restore.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
  }
  long version=jdbc.queryForObject("SELECT version FROM answers WHERE id=?",Long.class,id);moderate(m,id,"COMMUNITY",true,version).andExpect(status().isOk());
  mvc.perform(get("/api/questions/"+q+"/answers")).andExpect(jsonPath("$.totalElements").value(0));
 }
 @Test void simultaneousApprovalAndDeactivationCannotLeaveDisabledAdminOrPendingApplication()throws Exception{
  var m=actor("MANAGER");var a=actor("MEMBER");UUID pending=application(a,m,false);long version=version(a);var gate=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)){
   var decision=pool.submit(()->{gate.await();return mvc.perform(write("PUT","/api/manager/admin-applications/"+pending+"/decision",m,Map.of("status","APPROVED","version",0))).andReturn().getResponse().getStatus();});
   var disable=pool.submit(()->{gate.await();return disable(m,a,true,version).andReturn().getResponse().getStatus();});gate.countDown();
   int d=decision.get(10,TimeUnit.SECONDS),s=disable.get(10,TimeUnit.SECONDS);assertThat(d).isIn(200,401,404);assertThat(s).isIn(200,409);
  }
  disable(m,a,true,version(a)).andExpect(status().isOk());assertThat(jdbc.queryForObject("SELECT authority FROM users WHERE id=?",String.class,a.id())).isEqualTo("MEMBER");assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_applications WHERE applicant_id=? AND status='PENDING'",Long.class,a.id())).isZero();
 }
 @Test void auditFailureRollsBackAccountSessionsPendingDecisionsAndContent()throws Exception{
  var m=actor("MANAGER");var a=actor("MEMBER");UUID pending=application(a,m,false),q=question(a);
  jdbc.execute("CREATE FUNCTION fail_management_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.actor_id='"+m.id()+"'::uuid AND NEW.target_type<>'ADMIN_APPLICATION' THEN RAISE EXCEPTION 'test failure'; END IF; RETURN NEW; END $$");
  jdbc.execute("CREATE TRIGGER fail_management_test AFTER INSERT ON management_actions FOR EACH ROW EXECUTE FUNCTION fail_management_test()");
  try{
   disable(m,a,true,version(a)).andExpect(status().isServiceUnavailable());moderate(m,q,"QUESTION",true,0).andExpect(status().isServiceUnavailable());
   assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM users WHERE id=?",Boolean.class,a.id())).isTrue();assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM questions WHERE id=?",Boolean.class,q)).isTrue();
   assertThat(jdbc.queryForObject("SELECT status FROM admin_applications WHERE id=?",String.class,pending)).isEqualTo("PENDING");mvc.perform(get("/api/me").cookie(a.cookie())).andExpect(status().isOk());
  }finally{jdbc.execute("DROP TRIGGER fail_management_test ON management_actions");jdbc.execute("DROP FUNCTION fail_management_test()");}
 }
 @Test void statisticsAndModerationFiltersFollowVisibilityIncludingHiddenParents()throws Exception{
  var m=actor("MANAGER");var a=actor("MEMBER");UUID q=question(a),id=answer(a,q,null);var before=read("/api/manager/statistics",m);moderate(m,id,"COMMUNITY",true,0).andExpect(status().isOk());
  assertThat(read("/api/manager/statistics",m).get("communityAnswers").asLong()).isEqualTo(before.get("communityAnswers").asLong()-1);
  mvc.perform(get("/api/manager/content").cookie(m.cookie()).param("q",q.toString()).param("kind","COMMUNITY").param("status","HIDDEN")).andExpect(jsonPath("$.totalElements").value(1));
  moderate(m,q,"QUESTION",true,0).andExpect(status().isOk());moderate(m,id,"COMMUNITY",false,1).andExpect(status().isOk()).andExpect(jsonPath("$.questionHidden").value(true));
  mvc.perform(get("/api/manager/content").cookie(m.cookie()).param("q",q.toString()).param("kind","COMMUNITY").param("status","VISIBLE")).andExpect(jsonPath("$.totalElements").value(0));
  assertThat(read("/api/manager/statistics",m).get("communityAnswers").asLong()).isEqualTo(before.get("communityAnswers").asLong()-1);
  moderate(m,q,"QUESTION",false,1).andExpect(status().isOk());assertThat(read("/api/manager/statistics",m).get("communityAnswers").asLong()).isEqualTo(before.get("communityAnswers").asLong());
 }
 @Autowired com.tanidikvar.api.answer.service.AnswerService answerService;
 @Test void managerCannotParticipateEvenWithCompletedEducationProfile()throws Exception{
  var m=actor("MANAGER");profile(m);var member=actor("MEMBER");profile(member);UUID q=question(member);
  for(String endpoint:List.of("/api/questions","/api/questions/"+q+"/answers","/api/questions/"+q+"/admin-answers","/api/me/admin-applications"))
   mvc.perform(write("POST",endpoint,m,Map.of())).andExpect(status().isForbidden());
  for(String endpoint:List.of("/api/questions/"+q+"/like","/api/questions/"+q+"/assignment"))mvc.perform(write("PUT",endpoint,m,Map.of())).andExpect(status().isForbidden());
  assertThatThrownBy(()->answerService.create(q,m.id(),new com.tanidikvar.api.answer.dto.AnswerCreateRequest("Yönetim hesabından katkı denemesi"))).isInstanceOf(com.tanidikvar.api.common.error.DomainException.class).hasMessageContaining("Manager");
  mvc.perform(write("POST","/api/questions/"+q+"/answers",member,Map.of("body","Normal üyenin geçerli cevabı"))).andExpect(status().isCreated());
 }
 @Test void managerViewsNeverCountAndAnonymousOpeningsStillCount()throws Exception{
  var m=actor("MANAGER");var a=actor("MEMBER");UUID q=question(a),event=UUID.randomUUID();
  mvc.perform(write("POST","/api/questions/"+q+"/views",m,Map.of("openingEventId",event))).andExpect(status().isNoContent());
  assertThat(jdbc.queryForObject("SELECT count(*) FROM question_views WHERE question_id=?",Long.class,q)).isZero();
  for(int i=0;i<2;i++)mvc.perform(post("/api/questions/"+q+"/views").with(csrf()).contentType("application/json").content(mapper.writeValueAsString(Map.of("openingEventId",event)))).andExpect(status().isNoContent());
  mvc.perform(post("/api/questions/"+q+"/views").with(csrf()).contentType("application/json").content(mapper.writeValueAsString(Map.of("openingEventId",UUID.randomUUID())))).andExpect(status().isNoContent());
  assertThat(jdbc.queryForObject("SELECT count(*) FROM question_views WHERE question_id=?",Long.class,q)).isEqualTo(2);
 }
 @Test void managerIdentityAndAvatarDoNotRequireEducationAndRejectStaleUpdates()throws Exception{
  var m=actor("MANAGER");var member=actor("MEMBER");
  mvc.perform(get("/api/manager/account").cookie(member.cookie())).andExpect(status().isForbidden());
  mvc.perform(write("PUT","/api/manager/account",m,Map.of("firstName","Deniz","lastName","Yönetici","version",0))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
  mvc.perform(write("PUT","/api/manager/account",m,Map.of("firstName","Eski","lastName","Yönetici","version",0))).andExpect(status().isConflict());
  assertThat(jdbc.queryForObject("SELECT count(*) FROM user_profiles WHERE user_id=?",Long.class,m.id())).isZero();
  byte[] bytes;try(var out=new java.io.ByteArrayOutputStream()){javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);bytes=out.toByteArray();}
  var file=new org.springframework.mock.web.MockMultipartFile("file","avatar.png","image/png",bytes);
  var response=mapper.readTree(mvc.perform(multipart("/api/me/avatar").file(file).cookie(m.cookie()).with(csrf())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  mvc.perform(get("/api/avatars/"+response.get("fileId").asText())).andExpect(status().isOk());
  mvc.perform(write("POST","/api/me/avatar/remove",m,Map.of())).andExpect(status().isNoContent());
  mvc.perform(get("/api/avatars/"+response.get("fileId").asText())).andExpect(status().isNotFound());
  mvc.perform(get("/api/me").cookie(m.cookie())).andExpect(jsonPath("$.role").value("MANAGER")).andExpect(jsonPath("$.profileCompleted").value(false));
 }
 @Test void workspaceIncludesHiddenQuestionAndBothKindsWithoutPublicExposure()throws Exception{
  var m=actor("MANAGER");var a=actor("MEMBER");profile(a);UUID v=application(a,m,true),q=question(a),community=answer(a,q,null),admin=answer(a,q,v);
  jdbc.update("UPDATE questions SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",q);jdbc.update("UPDATE answers SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",community);jdbc.update("UPDATE answers SET moderated_at=CURRENT_TIMESTAMP WHERE id=?",admin);
  mvc.perform(get("/api/manager/questions/"+q).cookie(m.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.answers.totalElements").value(2)).andExpect(jsonPath("$.question.moderatedAt").isNotEmpty());
  mvc.perform(get("/api/manager/questions/"+q).cookie(a.cookie())).andExpect(status().isForbidden());mvc.perform(get("/api/questions/"+q)).andExpect(status().isNotFound());
  mvc.perform(get("/api/manager/questions/"+q).cookie(m.cookie()).param("size","101")).andExpect(status().isBadRequest());
 }
 Map<String,Object> classification(String scope,UUID university,List<UUID> tags,long version){var map=new HashMap<String,Object>();map.put("scope",scope);map.put("universityId",university);map.put("tagIds",tags);map.put("version",version);map.put("reason","Kapsam yanlış seçilmiş; belgeye göre düzeltildi.");return map;}
 UUID catalog(String table){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO "+table+"(id,name,normalized_name) VALUES (?,?,?)",id,"Katalog "+id,id.toString());return id;}
 @Test void classificationPreservesAuthorTextTimesAndRequiresReasonVersionAndActiveReferences()throws Exception{
  var m=actor("MANAGER");var a=actor("MEMBER");UUID q=question(a),university=catalog("universities"),tag=catalog("tags");String endpoint="/api/manager/questions/"+q+"/classification";var original=jdbc.queryForMap("SELECT title,body,created_at,edited_at,author_id FROM questions WHERE id=?",q);
  var request=classification("UNIVERSITY",university,List.of(tag),0);
  mvc.perform(write("PUT",endpoint,a,request)).andExpect(status().isForbidden());
  var noReason=new HashMap<>(request);noReason.remove("reason");mvc.perform(write("PUT",endpoint,m,noReason)).andExpect(status().isBadRequest());
  mvc.perform(write("PUT",endpoint,m,request)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
  assertThat(jdbc.queryForMap("SELECT title,body,created_at,edited_at,author_id FROM questions WHERE id=?",q)).isEqualTo(original);
  mvc.perform(write("PUT",endpoint,m,request)).andExpect(status().isConflict());
  mvc.perform(write("PUT",endpoint,m,classification("UNIVERSITY",university,List.of(tag),1))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
  assertThat(jdbc.queryForObject("SELECT count(*) FROM management_actions WHERE target_id=?",Long.class,q)).isEqualTo(1);
  mvc.perform(write("PUT",endpoint,m,classification("GENERAL",null,List.of(),1))).andExpect(status().isOk());
  assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM question_tags WHERE question_id=? AND tag_id=?",Boolean.class,q,tag)).isTrue();
  jdbc.update("UPDATE tags SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",tag);
  mvc.perform(write("PUT",endpoint,m,classification("GENERAL",null,List.of(tag),2))).andExpect(status().isBadRequest());
 }
 @Test void classificationRaceAndAuditFailureCannotPartiallyChangeQuestion()throws Exception{
  var m=actor("MANAGER");var a=actor("MEMBER");UUID q=question(a),u=catalog("universities");String path="/api/manager/questions/"+q+"/classification";var gate=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)){
   var first=pool.submit(()->{gate.await();return mvc.perform(write("PUT",path,m,classification("UNIVERSITY",u,List.of(),0))).andReturn().getResponse().getStatus();});
   var second=pool.submit(()->{gate.await();return moderate(m,q,"QUESTION",true,0).andReturn().getResponse().getStatus();});gate.countDown();assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
  }
  UUID other=question(a);jdbc.execute("CREATE FUNCTION fail_classification_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.target_id='"+other+"'::uuid THEN RAISE EXCEPTION 'test failure'; END IF; RETURN NEW; END $$");jdbc.execute("CREATE TRIGGER fail_classification_test AFTER INSERT ON management_actions FOR EACH ROW EXECUTE FUNCTION fail_classification_test()");
  try{mvc.perform(write("PUT","/api/manager/questions/"+other+"/classification",m,classification("UNIVERSITY",u,List.of(),0))).andExpect(status().isServiceUnavailable());assertThat(jdbc.queryForObject("SELECT scope FROM questions WHERE id=?",String.class,other)).isEqualTo("GENERAL");assertThat(jdbc.queryForObject("SELECT version FROM questions WHERE id=?",Long.class,other)).isZero();}finally{jdbc.execute("DROP TRIGGER fail_classification_test ON management_actions");jdbc.execute("DROP FUNCTION fail_classification_test()");}
 }
 @Test void userDetailAndApplicationHistoryIncludeInactiveUsersAndStayManagerOnly()throws Exception{
  var m=actor("MANAGER");var a=actor("MEMBER");profile(a);UUID approved=application(a,m,true),pending=application(a,m,false),q=question(a);answer(a,q,null);answer(a,q,approved);
  mvc.perform(get("/api/manager/users/"+a.id()).cookie(m.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.questions").value(1)).andExpect(jsonPath("$.communityAnswers").value(1)).andExpect(jsonPath("$.adminAnswers").value(1)).andExpect(jsonPath("$.verificationId").value(approved.toString()));
  disable(m,a,true,version(a)).andExpect(status().isOk());
  mvc.perform(get("/api/manager/users/"+a.id()+"/applications").cookie(m.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
  mvc.perform(get("/api/manager/admin-applications/"+pending).cookie(m.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
  mvc.perform(get("/api/manager/users/"+a.id())).andExpect(status().isUnauthorized());
 }
 @Test void catalogImpactReasonAndActionFiltersMatchActualChanges()throws Exception{
  var m=actor("MANAGER");var a=actor("MEMBER");UUID q=question(a),u=catalog("universities");jdbc.update("UPDATE questions SET scope='UNIVERSITY',university_id=? WHERE id=?",u,q);
  mvc.perform(get("/api/manager/catalog-usage/UNIVERSITY/"+u).cookie(m.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.questions").value(1));
  String endpoint="/api/manager/catalog/UNIVERSITY/"+u+"/status";
  mvc.perform(write("PUT",endpoint,m,Map.of("deleted",true,"version",0))).andExpect(status().isBadRequest());
  mvc.perform(write("PUT",endpoint,m,Map.of("deleted",true,"version",0,"reason","Katalog geçici incelemede"))).andExpect(status().isOk());
  assertThat(jdbc.queryForObject("SELECT university_id FROM questions WHERE id=?",UUID.class,q)).isEqualTo(u);
  var actions=read("/api/manager/actions?action=SOFT_DELETE&targetType=UNIVERSITY&q="+u,m);assertThat(actions.get("totalElements").asLong()).isEqualTo(1);UUID action=UUID.fromString(actions.get("items").get(0).get("id").asText());
  mvc.perform(get("/api/manager/actions/"+action).cookie(m.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.actorName").value(m.email())).andExpect(jsonPath("$.action.reason").value("Katalog geçici incelemede"));
  mvc.perform(get("/api/manager/actions/"+action).cookie(a.cookie())).andExpect(status().isForbidden());
 }
}
