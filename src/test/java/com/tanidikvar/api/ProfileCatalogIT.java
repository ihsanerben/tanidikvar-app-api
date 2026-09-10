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
        TestAvatar.ready(jdbc,id);
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
        var selection=mapper.createObjectNode();selection.set("universityId",university.get("id"));selection.set("departmentId",department.get("id"));return selection;
    }
    @Test void managerBulkImportIsAtomicAndSkipsExistingNames()throws Exception{
        var manager=actor("MANAGER");String suffix=UUID.randomUUID().toString();String university="Toplu Üniversite "+suffix,department="Toplu Bölüm "+suffix;
        var body=Map.of("universities",List.of(university),"departments",List.of(department),"reason","AI listesinin kontrollü içe aktarımı");
        mvc.perform(write("POST","/api/manager/catalog/bulk-import",manager,body)).andExpect(status().isCreated()).andExpect(jsonPath("$.universitiesCreated").value(1)).andExpect(jsonPath("$.departmentsCreated").value(1));
        mvc.perform(write("POST","/api/manager/catalog/bulk-import",manager,body)).andExpect(status().isCreated()).andExpect(jsonPath("$.skipped").value(2));
        var invalid=Map.of("universities",List.of("Geri Alınacak "+suffix),"departments",List.of(" "),"reason","Atomiklik kontrolü");
        mvc.perform(write("POST","/api/manager/catalog/bulk-import",manager,invalid)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM universities WHERE name=?",Integer.class,"Geri Alınacak "+suffix)).isZero();
    }
    Map<String,Object> profile(String status,long version){
        var body=new HashMap<String,Object>();body.put("firstName","Ada");body.put("lastName","Yılmaz");body.put("educationStatus",status);body.put("version",version);return body;
    }
    @Test void legacyTagEndpointRequiresManagerAndPersistsReason()throws Exception{
        var manager=actor("MANAGER");String name="İnceleme "+UUID.randomUUID();
        mvc.perform(write("POST","/api/tags",manager,Map.of("name",name))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        var result=mapper.readTree(mvc.perform(write("POST","/api/tags",manager,Map.of("name",name,"reason","Yeni konu sınıflandırması"))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(jdbc.queryForObject("SELECT reason FROM management_actions WHERE target_id=?",String.class,UUID.fromString(result.get("id").asText()))).isEqualTo("Yeni konu sınıflandırması");
        mvc.perform(write("POST","/api/tags",actor("MEMBER"),Map.of("name",name,"reason","Yetkisiz deneme"))).andExpect(status().isForbidden());
    }
    @Test void bulkImportRejectsInvalidEntryBeforeWritingCatalog()throws Exception{
        var manager=actor("MANAGER");String name="Geçersiz içe aktarım "+UUID.randomUUID();
        var body=Map.of("universities",List.of(name),"departments",List.of(" "),"reason","Validation kontrolü");
        mvc.perform(write("POST","/api/manager/catalog/bulk-import",manager,body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM universities WHERE name=?",Integer.class,name)).isZero();
    }
    @Test void managerBulkImportsTagsAndSkipsNormalizedDuplicates()throws Exception{
        var manager=actor("MANAGER");String suffix=UUID.randomUUID().toString();String first="Toplu Tag "+suffix,second="Başka Tag "+suffix;
        var body=Map.of("tags",List.of(first,"  "+first.toLowerCase(Locale.forLanguageTag("tr"))+"  ",second),"reason","Toplu tag listesi");
        mvc.perform(write("POST","/api/manager/catalog/tags/bulk-import",manager,body)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tagsCreated").value(2)).andExpect(jsonPath("$.skipped").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tags WHERE name IN (?,?)",Integer.class,first,second)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM management_actions WHERE action='BULK_CREATE' AND target_type='TAG' AND reason='Toplu tag listesi'",Integer.class)).isGreaterThanOrEqualTo(2);
        mvc.perform(write("POST","/api/manager/catalog/tags/bulk-import",actor("MEMBER"),Map.of("tags",List.of("Yetkisiz "+suffix),"reason","Yetkisiz"))).andExpect(status().isForbidden());
    }
    @Test void bulkTagImportValidatesWholeRequestBeforeWriting()throws Exception{
        var manager=actor("MANAGER");String valid="Yazılmaması gereken "+UUID.randomUUID();
        mvc.perform(write("POST","/api/manager/catalog/tags/bulk-import",manager,Map.of("tags",List.of(valid," "),"reason","Validation kontrolü")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tags WHERE name=?",Integer.class,valid)).isZero();
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
        mvc.perform(write("PUT","/api/me/profile",member,body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.universityId").exists());
        body.put("universityId",link.get("universityId").asText());body.put("departmentId",link.get("departmentId").asText());
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
        var body=profile("UNIVERSITE_OGRENCISI",0);body.put("universityId",UUID.randomUUID());body.put("departmentId",link.get("departmentId").asText());
        mvc.perform(write("PUT","/api/me/profile",first,body)).andExpect(status().isNotFound());
        body.put("universityId",link.get("universityId").asText());
        mvc.perform(write("PUT","/api/me/profile",first,body)).andExpect(status().isOk());
        String university=link.get("universityId").asText();
        mvc.perform(write("PUT","/api/manager/catalog/UNIVERSITY/"+university+"/status",manager,Map.of("deleted",true,"version",0))).andExpect(status().isOk());
        mvc.perform(get("/api/departments")).andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mvc.perform(write("PUT","/api/me/profile",second,body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INACTIVE_CATALOG"));
        body.put("version",1);body.put("biography","Yeni biyografi");
        mvc.perform(write("PUT","/api/me/profile",first,body)).andExpect(status().isOk()).andExpect(jsonPath("$.education.available").value(false));
        mvc.perform(write("PUT","/api/manager/catalog/UNIVERSITY/"+university+"/status",manager,Map.of("deleted",false,"version",1))).andExpect(status().isOk());
        mvc.perform(get("/api/departments")).andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }
    @Test void softDeletedProfileIsIncompleteAndCanBeCompletedWithoutNewIdentity()throws Exception{
        var member=actor("MEMBER");mvc.perform(write("PUT","/api/me/profile",member,profile("YKS_ADAYI",0))).andExpect(status().isOk());
        jdbc.update("UPDATE user_profiles SET deleted_at=CURRENT_TIMESTAMP,version=version+1 WHERE user_id=?",member.id());
        mvc.perform(get("/api/me").cookie(member.cookie())).andExpect(jsonPath("$.role").value("USER"));
        mvc.perform(get("/api/me/profile").cookie(member.cookie())).andExpect(jsonPath("$.completed").value(false)).andExpect(jsonPath("$.firstName").isEmpty()).andExpect(jsonPath("$.version").value(2));
        jdbc.update("UPDATE stored_files SET deleted_at=clock_timestamp() WHERE owner_id=? AND purpose='AVATAR' AND upload_status='READY'",member.id());TestAvatar.ready(jdbc,member.id());
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
    @Test void normalizedDuplicateNamesAreRejected()throws Exception{
        var manager=actor("MANAGER");String name="IŞIK "+UUID.randomUUID();create(manager,"UNIVERSITY",name);
        mvc.perform(write("POST","/api/manager/catalog/UNIVERSITY",manager,Map.of("name","  "+name.toLowerCase(Locale.forLanguageTag("tr"))+"  "))).andExpect(status().isConflict());
        mvc.perform(get("/api/universities").param("q",name.toLowerCase(Locale.forLanguageTag("tr")))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/universities").param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/manager/catalog/INVALID").cookie(manager.cookie())).andExpect(status().isBadRequest());
    }
    @Test void adminsOnlyCreateTagsAndNeedACompletedProfile()throws Exception{
        var admin=actor("ADMIN");var manager=actor("MANAGER");
        mvc.perform(write("POST","/api/tags",admin,Map.of("name","Admin "+UUID.randomUUID()))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        var link=education(manager);var body=profile("UNIVERSITE_OGRENCISI",0);body.put("universityId",link.get("universityId").asText());body.put("departmentId",link.get("departmentId").asText());
        mvc.perform(write("PUT","/api/me/profile",admin,body)).andExpect(status().isOk());
        mvc.perform(write("POST","/api/tags",admin,Map.of("name","Admin "+UUID.randomUUID()))).andExpect(status().isForbidden());
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
        var response=mvc.perform(get("/api/profiles/"+a.id())).andExpect(status().isOk()).andExpect(jsonPath("$.portfolioUrl").value(body.get("portfolioUrl"))).andExpect(jsonPath("$.name").value("Ada Yılmaz")).andExpect(jsonPath("$.createdAt").isNotEmpty()).andReturn().getResponse().getContentAsString();
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
    @Test void adminCanCreateQuestionThroughHttpAndService()throws Exception{
        var admin=actor("ADMIN");mvc.perform(write("PUT","/api/me/profile",admin,profile("YKS_ADAYI",0))).andExpect(status().isOk());
        var content=new com.tanidikvar.api.question.dto.QuestionContent("Admin soru oluşturamaz",null,com.tanidikvar.api.question.entity.QuestionScope.GENERAL,null,null,List.of());
        var request=new com.tanidikvar.api.question.dto.QuestionCreateRequest(UUID.randomUUID(),content);
        mvc.perform(write("POST","/api/questions",admin,request)).andExpect(status().isCreated());
        var second=new com.tanidikvar.api.question.dto.QuestionCreateRequest(UUID.randomUUID(),content);
        org.assertj.core.api.Assertions.assertThat(questionService.create(admin.id(),second).authorId()).isEqualTo(admin.id());
    }
    @Autowired com.tanidikvar.api.question.service.QuestionService questionService;
}
