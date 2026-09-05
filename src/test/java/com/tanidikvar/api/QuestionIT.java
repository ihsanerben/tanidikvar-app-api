package com.tanidikvar.api;

import com.tanidikvar.api.auth.config.AuthProperties;
import com.tanidikvar.api.auth.entity.*;
import com.tanidikvar.api.auth.repository.AccountRepository;
import com.tanidikvar.api.auth.service.AuthenticationService;
import com.tanidikvar.api.catalog.service.CatalogService;
import com.tanidikvar.api.catalog.entity.CatalogKind;
import com.tanidikvar.api.profile.service.InteractionPolicy;
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
class QuestionIT {
    @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.9-alpine");
    @DynamicPropertySource static void configuration(DynamicPropertyRegistry p){
        p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);
        p.add("app.auth.secret",()->Base64.getEncoder().encodeToString(new byte[48]));
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthenticationService auth;
    @Autowired InteractionPolicy interaction;
    @Autowired CatalogService catalog;
    record Actor(UUID id,Cookie cookie){}
    Actor actor(String role){
        UUID id=UUID.randomUUID();String email=id+"@example.test";
        jdbc.update("INSERT INTO users(id,email,password_hash,authority,email_verified_at,created_at,updated_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,email,passwords.encode("Testing-password!"),role);
        return new Actor(id,new Cookie("TV_ACCESS",auth.login(email,"Testing-password!").accessToken()));
    }
    MockHttpServletRequestBuilder write(String method,String path,Actor actor,Object body){
        return (method.equals("PUT")?put(path):post(path)).cookie(actor.cookie()).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(body));
    }
    JsonNode create(Actor actor,String kind,String name)throws Exception{
        return mapper.readTree(mvc.perform(write("POST","/api/manager/catalog/"+kind,actor,Map.of("name",name))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    JsonNode education(Actor manager)throws Exception{
        var university=create(manager,"UNIVERSITY","Üniversite "+UUID.randomUUID());var department=create(manager,"DEPARTMENT","Bölüm "+UUID.randomUUID());
        return mapper.readTree(mvc.perform(write("POST","/api/manager/university-departments",manager,Map.of("universityId",university.get("id").asText(),"departmentId",department.get("id").asText())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    Map<String,Object> profile(String status,long version){
        var body=new HashMap<String,Object>();body.put("firstName","Ada");body.put("lastName","Yılmaz");body.put("educationStatus",status);body.put("version",version);return body;
    }
    Actor member(String role)throws Exception {var a=actor(role);mvc.perform(write("PUT","/api/me/profile",a,profile("YKS_ADAYI",0))).andExpect(status().isOk());return a;}
    Map<String,Object> content(String title) {var c=new HashMap<String,Object>();c.put("title",title);c.put("scope","GENERAL");c.put("tagIds",List.of());return c;}
    JsonNode question(Actor a,Map<String,Object> c)throws Exception {return mapper.readTree(mvc.perform(write("POST","/api/questions",a,Map.of("requestId",UUID.randomUUID(),"content",c))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
    @Test void publicReadingProfileGateCsrfAndOwnership()throws Exception {
        var incomplete=actor("MEMBER");var owner=member("MEMBER");var admin=member("ADMIN");var manager=member("MANAGER");
        var c=content("Üniversitede kampüs hayatı nasıl?");var body=Map.of("requestId",UUID.randomUUID(),"content",c);
        mvc.perform(post("/api/questions").with(csrf()).contentType("application/json").content(mapper.writeValueAsString(body))).andExpect(status().isUnauthorized());
        mvc.perform(write("POST","/api/questions",incomplete,body)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"));
        mvc.perform(post("/api/questions").cookie(owner.cookie()).contentType("application/json").content(mapper.writeValueAsString(body))).andExpect(status().isForbidden());
        c.put("authorId",admin.id());var q=question(owner,c);String path="/api/questions/"+q.get("id").asText();
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.authorId").value(owner.id().toString())).andExpect(jsonPath("$.email").doesNotExist());
        for(var other:List.of(admin,manager)) {
            mvc.perform(write("PUT",path,other,Map.of("version",0,"content",c))).andExpect(status().isForbidden());
            mvc.perform(write("POST",path+"/archive",other,Map.of("version",0))).andExpect(status().isForbidden());
        }
        question(admin,c);
    }
    @Test void threeScopesFiltersAndDatabaseConstraints()throws Exception {
        var a=member("MANAGER");var e=education(a);var t=create(a,"TAG","Soru Tag "+UUID.randomUUID());
        var c=content("Üniversite ve bölüm deneyimleri nasıl?");c.put("scope","UNIVERSITY_DEPARTMENT");c.put("universityDepartmentId",e.get("id").asText());c.put("tagIds",List.of(t.get("id").asText()));
        var q=question(a,c);
        mvc.perform(get("/api/questions").param("universityId",e.get("universityId").asText()).param("universityDepartmentId",e.get("id").asText()).param("tagId",t.get("id").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(q.get("id").asText()));
        c.put("universityId",e.get("universityId").asText());
        mvc.perform(write("POST","/api/questions",a,Map.of("requestId",UUID.randomUUID(),"content",c))).andExpect(status().isBadRequest());
        c.remove("universityDepartmentId");c.put("scope","UNIVERSITY");question(a,c);
        assertThatThrownBy(()->jdbc.update("UPDATE questions SET scope='GENERAL' WHERE id=?",UUID.fromString(q.get("id").asText()))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        mvc.perform(get("/api/questions").param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/questions").param("scope","OTHER")).andExpect(status().isBadRequest());
    }
    @Test void repeatedCreationIsIdempotentButSameTitlesAreAllowed()throws Exception {
        var a=member("MEMBER");var c=content("Aynı soru tekrar sorulabilir mi?");var request=Map.of("requestId",UUID.randomUUID(),"content",c);var gate=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<String> call=()->{gate.await();return mvc.perform(write("POST","/api/questions",a,request)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();};
            var one=executor.submit(call);var two=executor.submit(call);gate.countDown();
            assertThat(mapper.readTree(one.get(10,TimeUnit.SECONDS)).get("id")).isEqualTo(mapper.readTree(two.get(10,TimeUnit.SECONDS)).get("id"));
        }
        c.put("title","Aynı gönderimin değiştirilmiş başlığı");
        mvc.perform(write("POST","/api/questions",a,request)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REQUEST_CONFLICT"));
        c.put("title","Aynı soru tekrar sorulabilir mi?");
        question(a,c);assertThat(jdbc.queryForObject("SELECT count(*) FROM questions WHERE author_id=?",Integer.class,a.id())).isEqualTo(2);
    }
    @Test void parallelUpdatesAndNoOpPreserveVersionAndPublication()throws Exception {
        var a=member("MEMBER");var c=content("İlk soru başlığı burada bulunuyor");var q=question(a,c);String path="/api/questions/"+q.get("id").asText();
        mvc.perform(write("PUT",path,a,Map.of("version",0,"content",c))).andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.editedAt").isEmpty());
        c.put("title","Güncellenmiş soru başlığı burada");var gate=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Integer> call=()->{gate.await();return mvc.perform(write("PUT",path,a,Map.of("version",0,"content",c))).andReturn().getResponse().getStatus();};
            var one=executor.submit(call);var two=executor.submit(call);gate.countDown();
            assertThat(List.of(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        mvc.perform(get(path)).andExpect(jsonPath("$.createdAt").value(q.get("createdAt").asText())).andExpect(jsonPath("$.editedAt").isNotEmpty());
    }
    @Test void archiveIsReadableButNotDiscoverableAndCannotBeEdited()throws Exception {
        var a=member("MEMBER");var c=content("Arşivlenecek soru başlığı burada");var q=question(a,c);String id=q.get("id").asText(),path="/api/questions/"+id;
        mvc.perform(write("POST",path+"/archive",a,Map.of("version",0))).andExpect(status().isOk()).andExpect(jsonPath("$.archivedAt").isNotEmpty());
        mvc.perform(write("POST",path+"/archive",a,Map.of("version",0))).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get(path)).andExpect(status().isOk());
        mvc.perform(get("/api/questions")).andExpect(jsonPath("$.items[*].id",org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(id))));
        mvc.perform(get("/api/me/questions").cookie(a.cookie())).andExpect(jsonPath("$.items[0].id").value(id));
        mvc.perform(write("PUT",path,a,Map.of("version",1,"content",c))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUESTION_ARCHIVED"));
        jdbc.update("UPDATE questions SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",UUID.fromString(id));
        mvc.perform(get(path)).andExpect(status().isNotFound());
        mvc.perform(get("/api/me/questions").cookie(a.cookie())).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void tagsAreSoftDeletedAndReusedAndInactiveReferencesCannotBeNewlyAdded()throws Exception {
        var a=member("MANAGER");var e=education(a);var t=create(a,"TAG","Soru "+UUID.randomUUID());UUID tag=UUID.fromString(t.get("id").asText());
        var c=content("Tag geçmişi korunacak olan soru");c.put("scope","UNIVERSITY_DEPARTMENT");c.put("universityDepartmentId",e.get("id").asText());c.put("tagIds",List.of(tag));
        var q=question(a,c);UUID id=UUID.fromString(q.get("id").asText());String path="/api/questions/"+id;
        c.put("tagIds",List.of());mvc.perform(write("PUT",path,a,Map.of("version",0,"content",c))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM question_tags WHERE question_id=? AND tag_id=?",Boolean.class,id,tag)).isTrue();
        c.put("tagIds",List.of(tag));mvc.perform(write("PUT",path,a,Map.of("version",1,"content",c))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM question_tags WHERE question_id=?",Integer.class,id)).isEqualTo(1);
        jdbc.update("UPDATE tags SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",tag);jdbc.update("UPDATE universities SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",UUID.fromString(e.get("universityId").asText()));
        c.put("body","Geçmiş bağlantıları koruyarak açıklama güncellemesi");mvc.perform(write("PUT",path,a,Map.of("version",2,"content",c))).andExpect(status().isOk()).andExpect(jsonPath("$.tags[0].available").value(false));
        mvc.perform(write("POST","/api/questions",a,Map.of("requestId",UUID.randomUUID(),"content",c))).andExpect(status().isBadRequest());
        var general=content("Yeni soruda pasif tag seçilemez");general.put("tagIds",List.of(tag));mvc.perform(write("POST","/api/questions",a,Map.of("requestId",UUID.randomUUID(),"content",general))).andExpect(status().isBadRequest());
    }
    @Test void invalidFieldsMissingTagsAndPhysicalDeleteAreRejected()throws Exception {
        var a=member("MEMBER");var c=content("          ");
        mvc.perform(write("POST","/api/questions",a,Map.of("requestId",UUID.randomUUID(),"content",c))).andExpect(status().isBadRequest());
        c.put("title","Geçersiz tagler içeren soru");c.put("tagIds",List.of(UUID.randomUUID()));
        mvc.perform(write("POST","/api/questions",a,Map.of("requestId",UUID.randomUUID(),"content",c))).andExpect(status().isNotFound());
        UUID tag=UUID.randomUUID();c.put("tagIds",List.of(tag,tag));mvc.perform(write("POST","/api/questions",a,Map.of("requestId",UUID.randomUUID(),"content",c))).andExpect(status().isBadRequest());
        c.put("tagIds",java.util.stream.IntStream.range(0,6).mapToObj(i->UUID.randomUUID()).toList());mvc.perform(write("POST","/api/questions",a,Map.of("requestId",UUID.randomUUID(),"content",c))).andExpect(status().isBadRequest());
        for(String table:List.of("questions","question_tags"))assertThatThrownBy(()->jdbc.execute("DELETE FROM "+table)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.execute("TRUNCATE questions,question_tags, answers, question_assignments")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void failedTagWriteRollsBackQuestionCreation()throws Exception {
        var a=member("MANAGER");var t=create(a,"TAG","Rollback "+UUID.randomUUID());UUID tag=UUID.fromString(t.get("id").asText());
        jdbc.execute("CREATE FUNCTION fail_question_tag_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.tag_id='"+tag+"'::uuid THEN RAISE EXCEPTION 'test failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER fail_question_tag_test BEFORE INSERT ON question_tags FOR EACH ROW EXECUTE FUNCTION fail_question_tag_test()");
        var c=content("Yarım kayıt oluşmaması gereken soru");c.put("tagIds",List.of(tag));UUID request=UUID.randomUUID();
        try {mvc.perform(write("POST","/api/questions",a,Map.of("requestId",request,"content",c))).andExpect(status().isServiceUnavailable());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM questions WHERE request_id=?",Integer.class,request)).isZero();
        } finally {jdbc.execute("DROP TRIGGER fail_question_tag_test ON question_tags");jdbc.execute("DROP FUNCTION fail_question_tag_test()");}
    }
    @Test void archiveAndUpdateRaceKeepsQuestionClosed()throws Exception {
        var a=member("MEMBER");var c=content("Eşzamanlı arşiv ve düzenleme testi");var q=question(a,c);String path="/api/questions/"+q.get("id").asText();
        c.put("title","Eşzamanlı güncellenmiş soru başlığı");var gate=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var edit=executor.submit(()->{gate.await();return mvc.perform(write("PUT",path,a,Map.of("version",0,"content",c))).andReturn().getResponse().getStatus();});
            var archive=executor.submit(()->{gate.await();return mvc.perform(write("POST",path+"/archive",a,Map.of("version",0))).andReturn().getResponse().getStatus();});gate.countDown();
            int editStatus=edit.get(10,TimeUnit.SECONDS),archiveStatus=archive.get(10,TimeUnit.SECONDS);
            assertThat(List.of(editStatus,archiveStatus)).containsExactlyInAnyOrder(200,409);
            if(archiveStatus==409)mvc.perform(write("POST",path+"/archive",a,Map.of("version",1))).andExpect(status().isOk());
        }
        mvc.perform(get(path)).andExpect(jsonPath("$.archivedAt").isNotEmpty());
    }
    @Test void deletedAccountCannotMutateAndDoesNotExposeProfileName()throws Exception {
        var a=member("MEMBER");var c=content("Hesap görünürlüğü için örnek soru");var q=question(a,c);String path="/api/questions/"+q.get("id").asText();
        jdbc.update("UPDATE users SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",a.id());
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.authorId").isEmpty()).andExpect(jsonPath("$.authorName").value("Katılımcı"));
        mvc.perform(write("PUT",path,a,Map.of("version",0,"content",c))).andExpect(status().isUnauthorized());
    }

}
