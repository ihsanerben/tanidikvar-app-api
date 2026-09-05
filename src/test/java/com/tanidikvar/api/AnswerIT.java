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
class AnswerIT {
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
    JsonNode answer(Actor a,String question,String body)throws Exception {return mapper.readTree(mvc.perform(write("POST","/api/questions/"+question+"/answers",a,Map.of("body",body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
    String question(Actor a)throws Exception {return question(a,content("Topluluk cevapları için örnek soru")).get("id").asText();}
    @Test void publicReadingAndProfileCsrfOwnershipAndKindAreEnforced()throws Exception {
        var owner=member("MEMBER");var admin=member("ADMIN");var incomplete=actor("MEMBER");String q=question(owner),path="/api/questions/"+q+"/answers";
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(post(path).with(csrf()).contentType("application/json").content("{\"body\":\"Örnek topluluk cevabıdır\"}")).andExpect(status().isUnauthorized());
        mvc.perform(write("POST",path,incomplete,Map.of("body","Örnek topluluk cevabıdır"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"));
        mvc.perform(post(path).cookie(owner.cookie()).contentType("application/json").content("{\"body\":\"Örnek topluluk cevabıdır\"}")).andExpect(status().isForbidden());
        var a=mapper.readTree(mvc.perform(write("POST",path,admin,Map.of("body","Kampüs yaşamı çok hareketlidir.","authorId",owner.id(),"answerKind","ADMIN"))).andExpect(status().isCreated()).andExpect(jsonPath("$.answerKind").value("COMMUNITY")).andExpect(jsonPath("$.authorId").value(admin.id().toString())).andReturn().getResponse().getContentAsString());
        mvc.perform(write("PUT","/api/answers/"+a.get("id").asText(),owner,Map.of("body","Başkasının cevabını düzenleme","version",0))).andExpect(status().isForbidden());
        mvc.perform(write("PUT","/api/answers/"+a.get("id").asText()+"/status",owner,Map.of("deleted",true,"version",0))).andExpect(status().isForbidden());
        mvc.perform(get(path)).andExpect(jsonPath("$.items[0].email").doesNotExist());
    }
    @Test void eachAuthorHasOneAnswerAndPrivateEndpointOnlyReturnsTheirOwn()throws Exception {
        var owner=member("MEMBER");var other=member("MANAGER");String q=question(owner);
        mvc.perform(get("/api/questions/"+q+"/my-answer").cookie(owner.cookie())).andExpect(status().isNoContent());
        var first=answer(owner,q,"Birinci kişinin özgün deneyimi");var second=answer(other,q,"İkinci kişinin özgün deneyimi");
        mvc.perform(get("/api/questions/"+q+"/answers").param("size","1").param("page","1")).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items[0].id").value(second.get("id").asText()));
        mvc.perform(get("/api/questions/"+q+"/my-answer").cookie(owner.cookie()).param("userId",other.id().toString())).andExpect(jsonPath("$.id").value(first.get("id").asText()));
        mvc.perform(get("/api/questions/"+q+"/my-answer")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/questions/"+q+"/answers").param("size","101")).andExpect(status().isBadRequest());
    }
    @Test void parallelCreationIsSingleAndDifferentSecondAnswerIsRejected()throws Exception {
        var a=member("MEMBER");String q=question(a),path="/api/questions/"+q+"/answers";var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<String> call=()->{gate.await();return mvc.perform(write("POST",path,a,Map.of("body","Kampüs hayatı hakkında bir deneyim"))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();};
            var one=pool.submit(call);var two=pool.submit(call);gate.countDown();
            assertThat(mapper.readTree(one.get(10,TimeUnit.SECONDS)).get("id")).isEqualTo(mapper.readTree(two.get(10,TimeUnit.SECONDS)).get("id"));
        }
        mvc.perform(write("POST",path,a,Map.of("body","Başka bir ikinci cevap denemesi"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANSWER_EXISTS"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM answers WHERE question_id=?",Integer.class,UUID.fromString(q))).isEqualTo(1);
    }
    @Test void editingKeepsPublicationAndConcurrentStaleFormCannotOverwrite()throws Exception {
        var a=member("MEMBER");String q=question(a);var original=answer(a,q,"İlk yayınlanan cevap metni burada");String path="/api/answers/"+original.get("id").asText();
        mvc.perform(write("PUT",path,a,Map.of("body",original.get("body").asText(),"version",0))).andExpect(jsonPath("$.editedAt").isEmpty()).andExpect(jsonPath("$.version").value(0));
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Integer> call=()->{gate.await();return mvc.perform(write("PUT",path,a,Map.of("body","Cevabın güncellenmiş yeni içeriği","version",0))).andReturn().getResponse().getStatus();};
            var one=pool.submit(call);var two=pool.submit(call);gate.countDown();assertThat(List.of(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        mvc.perform(get("/api/questions/"+q+"/answers")).andExpect(jsonPath("$.items[0].publishedAt").value(original.get("publishedAt").asText())).andExpect(jsonPath("$.items[0].editedAt").isNotEmpty());
    }
    @Test void removalAndRestorationKeepIdentityAndInitialPublication()throws Exception {
        var a=member("MEMBER");String q=question(a);var original=answer(a,q,"Silinip geri yüklenecek topluluk cevabı");String path="/api/answers/"+original.get("id").asText();
        mvc.perform(write("PUT",path+"/status",a,Map.of("deleted",true,"version",0))).andExpect(jsonPath("$.deletedAt").isNotEmpty());
        mvc.perform(get("/api/questions/"+q+"/answers")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/questions/"+q+"/my-answer").cookie(a.cookie())).andExpect(jsonPath("$.body").value(original.get("body").asText()));
        mvc.perform(write("POST","/api/questions/"+q+"/answers",a,Map.of("body","Yeni cevap olarak yeniden oluşturma"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANSWER_REMOVED"));
        mvc.perform(write("PUT",path+"/status",a,Map.of("deleted",false,"version",1))).andExpect(jsonPath("$.deletedAt").isEmpty()).andExpect(jsonPath("$.publishedAt").value(original.get("publishedAt").asText())).andExpect(jsonPath("$.editedAt").isEmpty()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(write("PUT",path+"/status",a,Map.of("deleted",false,"version",2))).andExpect(jsonPath("$.version").value(2));
        mvc.perform(write("PUT",path+"/status",a,Map.of("deleted",true,"version",0))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM answers WHERE question_id=?",Integer.class,UUID.fromString(q))).isEqualTo(1);
    }
    @Test void archivedQuestionAllowsReadingAndRemovalButNotEditCreateOrRestore()throws Exception {
        var owner=member("MEMBER");var other=member("MEMBER");String q=question(owner);var a=answer(other,q,"Arşivde korunacak örnek cevap metni");String path="/api/answers/"+a.get("id").asText();
        mvc.perform(write("POST","/api/questions/"+q+"/archive",owner,Map.of("version",0))).andExpect(status().isOk());
        mvc.perform(get("/api/questions/"+q+"/answers")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(write("POST","/api/questions/"+q+"/answers",owner,Map.of("body","Arşivde yeni cevap oluşturulamaz"))).andExpect(status().isConflict());
        mvc.perform(write("PUT",path,other,Map.of("body","Arşivde mevcut cevap düzenlenemez","version",0))).andExpect(status().isConflict());
        mvc.perform(write("PUT",path+"/status",other,Map.of("deleted",true,"version",0))).andExpect(status().isOk());
        mvc.perform(write("PUT",path+"/status",other,Map.of("deleted",false,"version",1))).andExpect(status().isConflict());
    }
    @Test void answerCreationAndArchivingShareTheQuestionLock()throws Exception {
        var owner=member("MEMBER");var other=member("MEMBER");String q=question(owner);var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var create=pool.submit(()->{gate.await();return mvc.perform(write("POST","/api/questions/"+q+"/answers",other,Map.of("body","Arşivleme ile yarışan cevap metni"))).andReturn().getResponse().getStatus();});
            var archive=pool.submit(()->{gate.await();return mvc.perform(write("POST","/api/questions/"+q+"/archive",owner,Map.of("version",0))).andReturn().getResponse().getStatus();});gate.countDown();
            assertThat(archive.get(10,TimeUnit.SECONDS)).isEqualTo(200);int result=create.get(10,TimeUnit.SECONDS);assertThat(result).isIn(201,409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM answers WHERE question_id=?",Integer.class,UUID.fromString(q))).isEqualTo(result==201?1:0);
        }
        mvc.perform(write("POST","/api/questions/"+q+"/answers",owner,Map.of("body","Arşivleme sonrası yeni cevap denemesi"))).andExpect(status().isConflict());
    }
    @Test void hiddenQuestionBlocksAllAnswerAccessWithoutDeletingChildren()throws Exception {
        var a=member("MEMBER");String q=question(a);var answer=answer(a,q,"Gizlenen sorunun altında kalan cevap");String path="/api/answers/"+answer.get("id").asText();
        jdbc.update("UPDATE questions SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",UUID.fromString(q));
        mvc.perform(get("/api/questions/"+q+"/answers")).andExpect(status().isNotFound());
        mvc.perform(get("/api/questions/"+q+"/my-answer").cookie(a.cookie())).andExpect(status().isNotFound());
        mvc.perform(write("PUT",path,a,Map.of("body","Gizli soruda düzenleme denemesi","version",0))).andExpect(status().isNotFound());
        mvc.perform(write("PUT",path+"/status",a,Map.of("deleted",true,"version",0))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM answers WHERE id=?",Boolean.class,UUID.fromString(answer.get("id").asText()))).isTrue();
    }
    @Test void authorityChangesDoNotChangeAnswerKindAndDeletedAccountsAreAnonymized()throws Exception {
        var a=member("ADMIN");String q=question(a);answer(a,q,"Adminin topluluk bölümündeki cevabı");
        jdbc.update("UPDATE users SET authority='MEMBER' WHERE id=?",a.id());
        mvc.perform(get("/api/questions/"+q+"/answers")).andExpect(jsonPath("$.items[0].answerKind").value("COMMUNITY"));
        jdbc.update("UPDATE users SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",a.id());
        mvc.perform(get("/api/questions/"+q+"/answers")).andExpect(jsonPath("$.items[0].authorId").isEmpty()).andExpect(jsonPath("$.items[0].authorName").value("Katılımcı"));
        mvc.perform(write("POST","/api/questions/"+q+"/answers",a,Map.of("body","Silinen hesabın işlem denemesi"))).andExpect(status().isUnauthorized());
    }
    @Test void validationDatabaseUniquenessAndPhysicalDeletionGuards()throws Exception {
        var a=member("MEMBER");String q=question(a);String path="/api/questions/"+q+"/answers";
        for(String body:List.of("          ","kısa","x".repeat(5001)))mvc.perform(write("POST",path,a,Map.of("body",body))).andExpect(status().isBadRequest());
        var answer=answer(a,q,"Veritabanı tekilliği kontrol ediliyor");UUID id=UUID.fromString(answer.get("id").asText());
        assertThatThrownBy(()->jdbc.update("INSERT INTO answers(id,question_id,author_id,body) VALUES (?,?,?,?)",UUID.randomUUID(),UUID.fromString(q),a.id(),"İkinci kayıt doğrudan DB denemesi")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE answers SET answer_kind='ADMIN' WHERE id=?",id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.execute("DELETE FROM answers")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.execute("TRUNCATE answers")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void failedUpdateDoesNotPartiallyChangeAnswer()throws Exception {
        var a=member("MEMBER");String q=question(a);var answer=answer(a,q,"Güncelleme hatasında korunacak metin");UUID id=UUID.fromString(answer.get("id").asText());
        jdbc.execute("CREATE FUNCTION fail_answer_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.id='"+id+"'::uuid THEN RAISE EXCEPTION 'test failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER fail_answer_test AFTER UPDATE ON answers FOR EACH ROW EXECUTE FUNCTION fail_answer_test()");
        try {
            mvc.perform(write("PUT","/api/answers/"+id,a,Map.of("body","Bu değişiklik rollback ile geri alınacak","version",0))).andExpect(status().isServiceUnavailable());
            mvc.perform(get("/api/questions/"+q+"/my-answer").cookie(a.cookie())).andExpect(jsonPath("$.body").value(answer.get("body").asText())).andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.editedAt").isEmpty());
        } finally {jdbc.execute("DROP TRIGGER fail_answer_test ON answers");jdbc.execute("DROP FUNCTION fail_answer_test()");}
    }
    @Test void parallelRestoreAndEditCannotUseTheSameVersion()throws Exception {
        var a=member("MEMBER");String q=question(a);var original=answer(a,q,"Yarış sırasında korunacak tek cevap");String path="/api/answers/"+original.get("id").asText();
        mvc.perform(write("PUT",path+"/status",a,Map.of("deleted",true,"version",0))).andExpect(status().isOk());
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var restore=pool.submit(()->{gate.await();return mvc.perform(write("PUT",path+"/status",a,Map.of("deleted",false,"version",1))).andReturn().getResponse().getStatus();});
            var edit=pool.submit(()->{gate.await();return mvc.perform(write("PUT",path,a,Map.of("body","Aynı sürümle eşzamanlı değişiklik","version",1))).andReturn().getResponse().getStatus();});gate.countDown();
            assertThat(restore.get(10,TimeUnit.SECONDS)).isEqualTo(200);assertThat(edit.get(10,TimeUnit.SECONDS)).isEqualTo(409);
        }
        mvc.perform(get("/api/questions/"+q+"/my-answer").cookie(a.cookie())).andExpect(jsonPath("$.body").value(original.get("body").asText())).andExpect(jsonPath("$.version").value(2));
    }
    @Test void incompleteProfileCannotEditRemoveOrRestoreAnExistingAnswer()throws Exception {
        var a=member("MEMBER");String q=question(a);var original=answer(a,q,"Profil silinince yönetilemeyen cevap");String path="/api/answers/"+original.get("id").asText();
        jdbc.update("UPDATE user_profiles SET deleted_at=CURRENT_TIMESTAMP WHERE user_id=?",a.id());
        mvc.perform(write("PUT",path,a,Map.of("body","Eksik profil ile düzenleme denemesi","version",0))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"));
        mvc.perform(write("PUT",path+"/status",a,Map.of("deleted",true,"version",0))).andExpect(status().isForbidden());
        mvc.perform(write("PUT",path+"/status",a,Map.of("deleted",false,"version",0))).andExpect(status().isForbidden());
    }

}
