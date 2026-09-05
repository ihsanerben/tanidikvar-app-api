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
class EngagementIT {
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
    void view(String q,UUID event)throws Exception {mvc.perform(post("/api/questions/"+q+"/views").with(csrf()).contentType("application/json").content(mapper.writeValueAsString(Map.of("openingEventId",event)))).andExpect(status().isNoContent());}
    void like(Actor a,String q,boolean liked,long version)throws Exception {mvc.perform(write("PUT","/api/questions/"+q+"/like",a,Map.of("liked",liked,"version",version))).andExpect(status().isOk());}
    JsonNode stats(String q)throws Exception {return mapper.readTree(mvc.perform(get("/api/questions/"+q+"/statistics")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    @Test void viewsArePublicIdempotentAndGetNeverWrites()throws Exception {
        var a=member("MEMBER");String q=question(a);UUID event=UUID.randomUUID();
        mvc.perform(get("/api/questions/"+q)).andExpect(jsonPath("$.statistics.viewCount").value(0));
        assertThat(stats(q).get("viewCount").asLong()).isZero();
        view(q,event);view(q,event);view(q,UUID.randomUUID());
        assertThat(stats(q).get("viewCount").asLong()).isEqualTo(2);
        mvc.perform(get("/api/questions/"+q)).andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.statistics.viewCount").value(2));
        mvc.perform(post("/api/questions/"+q+"/views").contentType("application/json").content(mapper.writeValueAsString(Map.of("openingEventId",UUID.randomUUID())))).andExpect(status().isForbidden());
        mvc.perform(post("/api/questions/"+q+"/views").with(csrf()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        assertThat(stats(q).get("viewCount").asLong()).isEqualTo(2);
    }
    @Test void likeRequiresAuthenticationProfileCsrfAndValidVersion()throws Exception {
        var a=member("MEMBER");var incomplete=actor("MEMBER");String q=question(a),path="/api/questions/"+q+"/like";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(put(path).with(csrf()).contentType("application/json").content("{\"liked\":true,\"version\":0}")).andExpect(status().isUnauthorized());
        mvc.perform(put(path).cookie(a.cookie()).contentType("application/json").content("{\"liked\":true,\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(write("PUT",path,incomplete,Map.of("liked",true,"version",0))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"));
        for(Object body:List.of(Map.of("liked",true),Map.of("liked",true,"version",-1)))mvc.perform(write("PUT",path,a,body)).andExpect(status().isBadRequest());
        mvc.perform(write("PUT",path,a,Map.of("liked",true,"version",0,"userId",incomplete.id()))).andExpect(status().isOk());
        mvc.perform(get(path).cookie(incomplete.cookie())).andExpect(jsonPath("$.liked").value(false));
        assertThat(stats(q).get("likeCount").asLong()).isEqualTo(1);
    }
    @Test void unlikeAndRelikeKeepOneRowAndFirstTimeAndRejectStaleWrites()throws Exception {
        var a=member("MEMBER");String q=question(a),path="/api/questions/"+q+"/like";
        like(a,q,false,0);like(a,q,true,0);
        var first=jdbc.queryForObject("SELECT first_liked_at FROM question_likes WHERE question_id=?",java.time.OffsetDateTime.class,UUID.fromString(q));
        like(a,q,true,1);like(a,q,false,1);assertThat(stats(q).get("likeCount").asLong()).isZero();
        like(a,q,true,2);mvc.perform(write("PUT",path,a,Map.of("liked",false,"version",1))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM question_likes WHERE question_id=?",Long.class,UUID.fromString(q))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT first_liked_at FROM question_likes WHERE question_id=?",java.time.OffsetDateTime.class,UUID.fromString(q))).isEqualTo(first);
        mvc.perform(get(path).cookie(a.cookie())).andExpect(jsonPath("$.liked").value(true)).andExpect(jsonPath("$.version").value(3));
    }
    @Test void parallelDuplicateViewsAndLikesRemainUnique()throws Exception {
        var a=member("MEMBER");String q=question(a);UUID event=UUID.randomUUID();var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(8)) {
            var futures=new ArrayList<Future<Integer>>();
            for(int i=0;i<8;i++){final boolean isView=i<4;futures.add(pool.submit(()->{gate.await();return mvc.perform(isView?post("/api/questions/"+q+"/views").with(csrf()).contentType("application/json").content(mapper.writeValueAsString(Map.of("openingEventId",event))):write("PUT","/api/questions/"+q+"/like",a,Map.of("liked",true,"version",0))).andReturn().getResponse().getStatus();}));}
            gate.countDown();var statuses=new ArrayList<Integer>();for(var f:futures)statuses.add(f.get(20,TimeUnit.SECONDS));
            assertThat(statuses.stream().filter(v->v==204).count()).isEqualTo(4);assertThat(statuses.stream().filter(v->v==200).count()).isEqualTo(1);assertThat(statuses.stream().filter(v->v==409).count()).isEqualTo(3);
        }
        assertThat(stats(q).get("viewCount").asLong()).isEqualTo(1);assertThat(stats(q).get("likeCount").asLong()).isEqualTo(1);
    }
    @Test void archivedQuestionsCountViewsAndAllowUnlikeButNoNewLike()throws Exception {
        var a=member("MEMBER");String q=question(a);like(a,q,true,0);
        mvc.perform(write("POST","/api/questions/"+q+"/archive",a,Map.of("version",0))).andExpect(status().isOk());
        view(q,UUID.randomUUID());like(a,q,false,1);
        mvc.perform(write("PUT","/api/questions/"+q+"/like",a,Map.of("liked",true,"version",2))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUESTION_ARCHIVED"));
        assertThat(stats(q).get("viewCount").asLong()).isEqualTo(1);assertThat(stats(q).get("likeCount").asLong()).isZero();
    }
    @Test void hiddenQuestionsAndEventsDoNotLeakOrResurrect()throws Exception {
        var a=member("MEMBER");String q=question(a);UUID event=UUID.randomUUID();view(q,event);like(a,q,true,0);
        jdbc.update("UPDATE question_views SET deleted_at=clock_timestamp() WHERE opening_event_id=?",event);view(q,event);
        assertThat(stats(q).get("viewCount").asLong()).isZero();
        jdbc.update("UPDATE questions SET deleted_at=clock_timestamp() WHERE id=?",UUID.fromString(q));
        for(String suffix:List.of("","/statistics","/like"))mvc.perform(get("/api/questions/"+q+suffix).cookie(a.cookie())).andExpect(status().isNotFound());
        mvc.perform(write("PUT","/api/questions/"+q+"/like",a,Map.of("liked",false,"version",1))).andExpect(status().isNotFound());
        mvc.perform(post("/api/questions/"+q+"/views").with(csrf()).contentType("application/json").content(mapper.writeValueAsString(Map.of("openingEventId",UUID.randomUUID())))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM question_views WHERE question_id=?",Long.class,UUID.fromString(q))).isEqualTo(1);
    }
    @Test void crossQuestionEventReuseConflictsAndDatabaseProtectsIdentityAndSoftDelete()throws Exception {
        var a=member("MEMBER");String q=question(a),other=question(a);UUID event=UUID.randomUUID();view(q,event);like(a,q,true,0);
        mvc.perform(post("/api/questions/"+other+"/views").with(csrf()).contentType("application/json").content(mapper.writeValueAsString(Map.of("openingEventId",event)))).andExpect(status().isConflict());
        assertThat(stats(other).get("viewCount").asLong()).isZero();
        for(String table:List.of("question_likes","question_views")) {
            assertThatThrownBy(()->jdbc.execute("DELETE FROM "+table)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(()->jdbc.execute("TRUNCATE "+table)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(()->jdbc.update("UPDATE "+table+" SET question_id=? WHERE question_id=?",UUID.fromString(other),UUID.fromString(q))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        assertThatThrownBy(()->jdbc.update("UPDATE question_likes SET first_liked_at=clock_timestamp() WHERE question_id=?",UUID.fromString(q))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE question_views SET viewed_at=clock_timestamp() WHERE opening_event_id=?",event)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void countsFollowVisibleAnswersAndListCarriesTotals()throws Exception {
        var a=member("MEMBER");String q=question(a);view(q,UUID.randomUUID());like(a,q,true,0);var answer=answer(a,q,"Görünür topluluk deneyim cevabı");
        assertThat(stats(q).get("totalAnswerCount").asLong()).isEqualTo(1);
        mvc.perform(get("/api/me/questions").cookie(a.cookie())).andExpect(jsonPath("$.items[0].statistics.likeCount").value(1)).andExpect(jsonPath("$.items[0].statistics.communityAnswerCount").value(1));
        mvc.perform(write("PUT","/api/answers/"+answer.get("id").asText()+"/status",a,Map.of("deleted",true,"version",0))).andExpect(status().isOk());
        assertThat(stats(q).get("totalAnswerCount").asLong()).isZero();
        mvc.perform(write("PUT","/api/answers/"+answer.get("id").asText()+"/status",a,Map.of("deleted",false,"version",1))).andExpect(status().isOk());
        assertThat(stats(q).get("communityAnswerCount").asLong()).isEqualTo(1);
    }
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.tanidikvar.api.engagement.service.EngagementService engagement;
    @Test void outerTransactionFailureRollsBackBothInteractions()throws Exception {
        var a=member("MEMBER");String q=question(a);var id=UUID.fromString(q);
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);
        assertThatThrownBy(()->tx.executeWithoutResult(status->{
            engagement.like(id,a.id(),new com.tanidikvar.api.engagement.dto.LikeRequest(true,0L));
            engagement.view(id,new com.tanidikvar.api.engagement.dto.ViewRequest(UUID.randomUUID()));
            throw new IllegalStateException("synthetic rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(stats(q).get("likeCount").asLong()).isZero();assertThat(stats(q).get("viewCount").asLong()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM question_likes WHERE question_id=?",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM question_views WHERE question_id=?",Long.class,id)).isZero();
    }

}
