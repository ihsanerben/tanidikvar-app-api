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
class ProfileCatalogIT {
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
        if((path.startsWith("/api/manager/catalog")||path.startsWith("/api/manager/university-departments"))&&body instanceof Map<?,?> map){var enriched=new HashMap<String,Object>();map.forEach((k,v)->enriched.put(k.toString(),v));enriched.putIfAbsent("reason","Test kataloğu yönetimi");body=enriched;}
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
    @Test void candidateCompletionChangesCurrentRoleAndEnablesInteraction()throws Exception{
        var member=actor("MEMBER");
        mvc.perform(get("/api/me/profile").cookie(member.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(false)).andExpect(jsonPath("$.version").value(0));
        assertThatThrownBy(()->interaction.requireCompleted(member.id())).isInstanceOf(com.tanidikvar.api.common.error.DomainException.class);
        mvc.perform(write("PUT","/api/me/profile",member,profile("YKS_ADAYI",0))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/me").cookie(member.cookie())).andExpect(jsonPath("$.role").value("YKS_ADAYI")).andExpect(jsonPath("$.profileCompleted").value(true));
        interaction.requireCompleted(member.id());
    }
    @Test void profileOwnershipAndAuthorityCannotBeOverriddenByBody()throws Exception{
        var member=actor("MEMBER");var victim=actor("MEMBER");var body=profile("YKS_ADAYI",0);body.put("userId",victim.id());body.put("authority","MANAGER");
        mvc.perform(write("PUT","/api/me/profile",member,body)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_profiles WHERE user_id=?",Integer.class,victim.id())).isZero();
        mvc.perform(get("/api/me").cookie(member.cookie())).andExpect(jsonPath("$.role").value("YKS_ADAYI"));
        mvc.perform(get("/api/manager/catalog/TAG").cookie(member.cookie())).andExpect(status().isForbidden());
        mvc.perform(get("/api/me/profile")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/me/profile").cookie(member.cookie()).contentType("application/json").content(mapper.writeValueAsString(body))).andExpect(status().isForbidden());
    }
    @Test void studentGraduateAndCandidateConstraintsAreValidatedOnServerAndDatabase()throws Exception{
        var manager=actor("MANAGER");var member=actor("MEMBER");var link=education(manager);var body=profile("UNIVERSITE_OGRENCISI",0);
        mvc.perform(write("PUT","/api/me/profile",member,body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.universityDepartmentId").exists());
        body.put("universityDepartmentId",link.get("id").asText());
        mvc.perform(write("PUT","/api/me/profile",member,body)).andExpect(status().isOk()).andExpect(jsonPath("$.education.universityId").value(link.get("universityId").asText()));
        body.put("educationStatus","MEZUN");body.put("version",1);body.put("graduationYear",9999);
        mvc.perform(write("PUT","/api/me/profile",member,body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.graduationYear").exists());
        body.put("graduationYear",2025);
        mvc.perform(write("PUT","/api/me/profile",member,body)).andExpect(status().isOk());
        mvc.perform(get("/api/me").cookie(member.cookie())).andExpect(jsonPath("$.role").value("MEZUN"));
        body.put("educationStatus","YKS_ADAYI");body.put("version",2);
        mvc.perform(write("PUT","/api/me/profile",member,body)).andExpect(status().isBadRequest());
        assertThatThrownBy(()->jdbc.update("UPDATE user_profiles SET education_status='YKS_ADAYI' WHERE user_id=?",member.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void managerUsesIndependentIdentityInsteadOfEducationProfile()throws Exception{
        var manager=actor("MANAGER");mvc.perform(write("PUT","/api/me/profile",manager,profile("YKS_ADAYI",0))).andExpect(status().isForbidden());
        mvc.perform(get("/api/me").cookie(manager.cookie())).andExpect(jsonPath("$.role").value("MANAGER")).andExpect(jsonPath("$.profileCompleted").value(false));
    }
    @Test void parallelProfileUpdatesRejectStaleFormInsteadOfLosingChanges()throws Exception{
        var member=actor("MEMBER");var gate=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            Callable<Integer> call=()->{gate.await();return mvc.perform(write("PUT","/api/me/profile",member,profile("YKS_ADAYI",0))).andReturn().getResponse().getStatus();};
            var a=executor.submit(call);var b=executor.submit(call);gate.countDown();
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_profiles WHERE user_id=?",Integer.class,member.id())).isEqualTo(1);
    }
    @Test void missingOrInactiveEducationCannotBeNewlySelectedAndOldReferencesSurvive()throws Exception{
        var manager=actor("MANAGER");var first=actor("MEMBER");var second=actor("MEMBER");var link=education(manager);
        var body=profile("UNIVERSITE_OGRENCISI",0);body.put("universityDepartmentId",UUID.randomUUID());
        mvc.perform(write("PUT","/api/me/profile",first,body)).andExpect(status().isNotFound());
        body.put("universityDepartmentId",link.get("id").asText());
        mvc.perform(write("PUT","/api/me/profile",first,body)).andExpect(status().isOk());
        String university=link.get("universityId").asText();
        mvc.perform(write("PUT","/api/manager/catalog/UNIVERSITY/"+university+"/status",manager,Map.of("deleted",true,"version",0))).andExpect(status().isOk());
        mvc.perform(get("/api/universities/"+university+"/departments")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(write("PUT","/api/me/profile",second,body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INACTIVE_EDUCATION"));
        body.put("version",1);body.put("biography","Yeni biyografi");
        mvc.perform(write("PUT","/api/me/profile",first,body)).andExpect(status().isOk()).andExpect(jsonPath("$.education.available").value(false));
        mvc.perform(write("PUT","/api/manager/catalog/UNIVERSITY/"+university+"/status",manager,Map.of("deleted",false,"version",1))).andExpect(status().isOk());
        mvc.perform(get("/api/universities/"+university+"/departments")).andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test void softDeletedProfileIsIncompleteAndCanBeCompletedWithoutNewIdentity()throws Exception{
        var member=actor("MEMBER");mvc.perform(write("PUT","/api/me/profile",member,profile("YKS_ADAYI",0))).andExpect(status().isOk());
        jdbc.update("UPDATE user_profiles SET deleted_at=CURRENT_TIMESTAMP,version=version+1 WHERE user_id=?",member.id());
        mvc.perform(get("/api/me").cookie(member.cookie())).andExpect(jsonPath("$.role").value("USER"));
        mvc.perform(get("/api/me/profile").cookie(member.cookie())).andExpect(jsonPath("$.completed").value(false)).andExpect(jsonPath("$.firstName").isEmpty()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(write("PUT","/api/me/profile",member,profile("YKS_ADAYI",2))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_profiles WHERE user_id=?",Integer.class,member.id())).isEqualTo(1);
    }
    @Test void managerCatalogCreateRenameSoftDeleteRestoreAndAuditAreAtomic()throws Exception{
        var manager=actor("MANAGER");String name="Test "+UUID.randomUUID();var entry=create(manager,"TAG",name);String id=entry.get("id").asText();
        mvc.perform(write("PUT","/api/manager/catalog/TAG/"+id,manager,Map.of("name",name+" Yeni","version",0))).andExpect(status().isOk());
        mvc.perform(write("PUT","/api/manager/catalog/TAG/"+id+"/status",manager,Map.of("deleted",true,"version",1))).andExpect(status().isOk());
        mvc.perform(get("/api/tags").param("q",name)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/manager/catalog/TAG").cookie(manager.cookie()).param("q",name).param("includeDeleted","true")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(write("POST","/api/manager/catalog/TAG",manager,Map.of("name",name+" Yeni"))).andExpect(status().isConflict());
        mvc.perform(write("PUT","/api/manager/catalog/TAG/"+id+"/status",manager,Map.of("deleted",false,"version",2))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM management_actions WHERE target_id=?",Integer.class,UUID.fromString(id))).isEqualTo(4);
    }
    @Test void normalizedDuplicateNamesAndPairsAreRejected()throws Exception{
        var manager=actor("MANAGER");String name="IŞIK "+UUID.randomUUID();create(manager,"UNIVERSITY",name);
        mvc.perform(write("POST","/api/manager/catalog/UNIVERSITY",manager,Map.of("name","  "+name.toLowerCase(Locale.forLanguageTag("tr"))+"  "))).andExpect(status().isConflict());
        var link=education(manager);
        mvc.perform(write("POST","/api/manager/university-departments",manager,Map.of("universityId",link.get("universityId").asText(),"departmentId",link.get("departmentId").asText()))).andExpect(status().isConflict());
        mvc.perform(get("/api/universities").param("q",name.toLowerCase(Locale.forLanguageTag("tr")))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/universities").param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/manager/catalog/INVALID").cookie(manager.cookie())).andExpect(status().isBadRequest());
    }
    @Test void adminsOnlyCreateTagsAndNeedACompletedProfile()throws Exception{
        var admin=actor("ADMIN");var manager=actor("MANAGER");
        mvc.perform(write("POST","/api/tags",admin,Map.of("name","Admin "+UUID.randomUUID()))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"));
        var link=education(manager);var body=profile("UNIVERSITE_OGRENCISI",0);body.put("universityDepartmentId",link.get("id").asText());
        mvc.perform(write("PUT","/api/me/profile",admin,body)).andExpect(status().isOk());
        mvc.perform(write("POST","/api/tags",admin,Map.of("name","Admin "+UUID.randomUUID()))).andExpect(status().isCreated());
        mvc.perform(get("/api/manager/catalog/TAG").cookie(admin.cookie())).andExpect(status().isForbidden());
        assertThatThrownBy(()->catalog.create(admin.id(),CatalogKind.UNIVERSITY,"Unauthorized")).isInstanceOf(com.tanidikvar.api.common.error.DomainException.class);
        jdbc.update("UPDATE users SET authority='MEMBER' WHERE id=?",admin.id());
        mvc.perform(write("POST","/api/tags",admin,Map.of("name","Rejected"))).andExpect(status().isForbidden());
    }
    @Test void newTablesRejectPhysicalDeletion() {
        for(String table:List.of("user_profiles","tags","management_actions"))assertThatThrownBy(()->jdbc.execute("DELETE FROM "+table)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.execute("TRUNCATE question_likes, question_views, user_profiles,tags,management_actions, questions, question_tags, answers, question_assignments")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void optionalLinksAreValidatedPublicAndNeverExposePrivateAccountFields()throws Exception{
        var a=actor("MEMBER");var body=profile("YKS_ADAYI",0);body.put("linkedinUrl","https://www.linkedin.com/in/ada");body.put("portfolioUrl","https://portfolio.example.test/work");
        mvc.perform(write("PUT","/api/me/profile",a,body)).andExpect(status().isOk()).andExpect(jsonPath("$.linkedinUrl").value(body.get("linkedinUrl")));
        var response=mvc.perform(get("/api/profiles/"+a.id())).andExpect(status().isOk()).andExpect(jsonPath("$.portfolioUrl").value(body.get("portfolioUrl"))).andExpect(jsonPath("$.name").value("Ada Yılmaz")).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("email","password","document","verification","version");
        for(String invalid:List.of("javascript:alert(1)","data:text/html,bad","//example.test","https://user:pass@example.test","https://example.test/\\evil")){
            body.put("version",1);body.put("portfolioUrl",invalid);mvc.perform(write("PUT","/api/me/profile",a,body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.portfolioUrl").exists());
        }
        body.put("portfolioUrl","");body.put("linkedinUrl","https://linkedin.com.example.test/in/ada");mvc.perform(write("PUT","/api/me/profile",a,body)).andExpect(status().isBadRequest());
        body.put("linkedinUrl","");mvc.perform(write("PUT","/api/me/profile",a,body)).andExpect(status().isOk()).andExpect(jsonPath("$.linkedinUrl").isEmpty());
        mvc.perform(write("PUT","/api/me/profile",a,body)).andExpect(status().isConflict());
        jdbc.update("UPDATE users SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",a.id());mvc.perform(get("/api/profiles/"+a.id())).andExpect(status().isNotFound());
        mvc.perform(get("/api/profiles/"+actor("MANAGER").id())).andExpect(status().isNotFound());
    }
    @Test void adminCannotCreateQuestionEvenThroughService()throws Exception{
        var admin=actor("ADMIN");mvc.perform(write("PUT","/api/me/profile",admin,profile("YKS_ADAYI",0))).andExpect(status().isOk());
        var content=new com.tanidikvar.api.question.dto.QuestionContent("Admin soru oluşturamaz",null,com.tanidikvar.api.question.entity.QuestionScope.GENERAL,null,null,List.of());
        var request=new com.tanidikvar.api.question.dto.QuestionCreateRequest(UUID.randomUUID(),content);
        mvc.perform(write("POST","/api/questions",admin,request)).andExpect(status().isForbidden());
        assertThatThrownBy(()->questionService.create(admin.id(),request)).isInstanceOf(com.tanidikvar.api.common.error.DomainException.class).hasMessageContaining("Adminler soru oluşturamaz");
    }
    @Autowired com.tanidikvar.api.question.service.QuestionService questionService;
}
