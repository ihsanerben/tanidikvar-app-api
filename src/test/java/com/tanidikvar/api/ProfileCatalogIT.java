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
        return (method.equals("PUT")?put(path):method.equals("DELETE")?delete(path):post(path)).cookie(actor.cookie()).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(body));
    }
    JsonNode create(Actor actor,String kind,String name)throws Exception{
        return mapper.readTree(mvc.perform(write("POST","/api/manager/catalog/"+kind,actor,Map.of("name",name))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    JsonNode education(Actor manager)throws Exception{
        var university=create(manager,"UNIVERSITY","Üniversite "+UUID.randomUUID());var department=create(manager,"DEPARTMENT","Bölüm "+UUID.randomUUID());
        var selection=mapper.createObjectNode();selection.set("universityId",university.get("id"));selection.set("departmentId",department.get("id"));return selection;
    }
    @Test void managerCanPermanentlyDeleteOnlyUnusedInactiveUniversity()throws Exception{
        var manager=actor("MANAGER");
        var university=create(manager,"UNIVERSITY","Silinecek Üniversite "+UUID.randomUUID());
        String path="/api/manager/catalog/UNIVERSITY/"+university.get("id").asText();
        mvc.perform(write("DELETE",path,manager,Map.of("version",0))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATALOG_ACTIVE"));
        mvc.perform(write("PUT",path+"/status",manager,Map.of("deleted",true,"version",0))).andExpect(status().isOk());
        mvc.perform(write("DELETE",path,manager,Map.of("version",1))).andExpect(status().isNoContent());
        mvc.perform(get("/api/manager/catalog/UNIVERSITY").cookie(manager.cookie()).param("includeDeleted","true").param("q",university.get("name").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
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
    @Test void publicCatalogDetailsExposeOnlyActiveUniversityAndEducation()throws Exception{
        var manager=actor("MANAGER");var link=education(manager);
        String universityId=link.get("universityId").asText(),departmentId=link.get("departmentId").asText(),departmentName=jdbc.queryForObject("SELECT name FROM departments WHERE id=?",String.class,UUID.fromString(link.get("departmentId").asText()));
        mvc.perform(write("POST","/api/manager/university-departments",manager,Map.of("universityId",universityId,"departmentId",departmentId))).andExpect(status().isCreated());
        mvc.perform(get("/api/universities/"+universityId)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(universityId));
        mvc.perform(write("PUT","/api/manager/universities/"+universityId+"/details",manager,Map.of("city","İstanbul","description","Öğrenci topluluğu için güncel üniversite tanıtımı.","websiteUrl","https://example.edu.tr","logoUrl","https://example.edu.tr/logo.png","accentPrimary","#112233","accentSoft","#EEF0F2","accentForeground","#FFFFFF","reason","Public üniversite bilgileri doğrulandı.","version",0))).andExpect(status().isOk()).andExpect(jsonPath("$.city").value("İstanbul")).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/universities/"+universityId)).andExpect(status().isOk()).andExpect(jsonPath("$.accentPrimary").value("#112233"));
        mvc.perform(get("/api/universities/"+universityId+"/departments/"+departmentId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.universityId").value(universityId)).andExpect(jsonPath("$.departmentId").value(departmentId));
        mvc.perform(get("/api/programs").param("q",departmentName))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/universities/"+UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(write("PUT","/api/manager/catalog/UNIVERSITY/"+universityId+"/status",manager,Map.of("deleted",true,"version",1))).andExpect(status().isOk());
        mvc.perform(get("/api/universities/"+universityId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/universities/"+universityId+"/departments/"+departmentId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/programs").param("q",departmentName)).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }
    @Test void followsSavesNotificationsAndGamificationPreserveLifecycle()throws Exception{
        var member=actor("MEMBER");var manager=actor("MANAGER");var university=create(manager,"UNIVERSITY","Takip Üniversitesi "+UUID.randomUUID());String universityId=university.get("id").asText();
        var follow=Map.of("targetType","UNIVERSITY","targetId",universityId,"active",true);
        mvc.perform(write("PUT","/api/me/follows",member,follow)).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.version").value(0));
        mvc.perform(get("/api/me/follows").cookie(member.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(write("PUT","/api/me/follows",member,Map.of("targetType","UNIVERSITY","targetId",universityId,"active",false))).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/me/follows").cookie(member.cookie())).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(write("PUT","/api/me/saved",member,follow)).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        mvc.perform(write("PUT","/api/me/follows",member,Map.of("targetType","TANIDIK","targetId",member.id(),"active",true))).andExpect(status().isBadRequest());
        mvc.perform(write("PUT","/api/me/saved",member,Map.of("targetType","QUESTION","targetId",UUID.randomUUID(),"active",true))).andExpect(status().isNotFound());
        UUID notification=UUID.randomUUID();jdbc.update("INSERT INTO notifications(id,user_id,notification_type,title,body) VALUES (?,?,?,?,?)",notification,member.id(),"BADGE","Yeni rozet","Yeni bir katkı rozeti kazandın.");
        mvc.perform(get("/api/me/notifications").cookie(member.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].readAt").isEmpty());
        mvc.perform(write("PUT","/api/me/notifications/"+notification+"/read",member,Map.of())).andExpect(status().isNoContent());
        mvc.perform(write("PUT","/api/me/profile",member,profile("YKS_ADAYI",0))).andExpect(status().isOk());
        UUID source=UUID.randomUUID();jdbc.update("INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),member.id(),"HELPFUL_ANSWER",300,"ANSWER",source,1);
        mvc.perform(get("/api/gamification/profiles/"+member.id())).andExpect(status().isOk()).andExpect(jsonPath("$.totalPoints").value(300)).andExpect(jsonPath("$.title").value("Aktif Tanıdık"));
        mvc.perform(get("/api/gamification/profiles/"+member.id()+"/annual-report")).andExpect(status().isOk()).andExpect(jsonPath("$.points").value(300));
        var leaderboard=mapper.readTree(mvc.perform(get("/api/gamification/leaderboard")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(leaderboard).anyMatch(entry->entry.get("userId").asText().equals(member.id().toString())&&entry.get("points").asLong()==300&&entry.get("badges").toString().contains("İlk 100"));
        mvc.perform(get("/api/gamification/leaderboard").param("period","INVALID")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/follows")).andExpect(status().isUnauthorized());
    }
    @Test void evaluationsAreScopedValidatedAndUpdatedInPlace()throws Exception{
        var member=actor("TANIDIK");var manager=actor("MANAGER");var ids=education(manager);String university=ids.get("universityId").asText(),department=ids.get("departmentId").asText();
        var relation=mapper.readTree(mvc.perform(write("POST","/api/manager/university-departments",manager,Map.of("universityId",university,"departmentId",department))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());String program=relation.get("id").asText();
        var profile=profile("UNIVERSITE_OGRENCISI",0);profile.put("universityId",university);profile.put("departmentId",department);profile.put("classYear",2);
        mvc.perform(write("PUT","/api/me/profile",member,profile)).andExpect(status().isOk());
        var evaluation=new HashMap<String,Object>();evaluation.put("universityId",university);evaluation.put("programId",program);evaluation.put("rating",4);evaluation.put("body","Programın akademik ortamı güçlü ve ulaşım olanakları yeterli.");
        mvc.perform(write("PUT","/api/evaluations",member,evaluation)).andExpect(status().isOk()).andExpect(jsonPath("$.rating").value(4)).andExpect(jsonPath("$.version").value(0));
        evaluation.put("rating",5);mvc.perform(write("PUT","/api/evaluations",member,evaluation)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/evaluations").param("universityId",university).param("programId",program)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/evaluations/summary").param("universityId",university).param("programId",program)).andExpect(status().isOk()).andExpect(jsonPath("$.averageRating").value(5.0)).andExpect(jsonPath("$.evaluationCount").value(1));
        mvc.perform(get("/api/gamification/profiles/"+member.id())).andExpect(jsonPath("$.totalPoints").value(8));
        evaluation.put("programId",UUID.randomUUID());mvc.perform(write("PUT","/api/evaluations",member,evaluation)).andExpect(status().isNotFound());
    }
    @Test void tanidikPollCreationAndVerifiedVotingAreEnforced()throws Exception{
        var tanidik=actor("TANIDIK");var member=actor("MEMBER");var manager=actor("MANAGER");var ids=education(manager);String university=ids.get("universityId").asText(),department=ids.get("departmentId").asText();
        var relation=mapper.readTree(mvc.perform(write("POST","/api/manager/university-departments",manager,Map.of("universityId",university,"departmentId",department))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());String program=relation.get("id").asText();
        for(var actor:List.of(tanidik,member)){var profile=profile("UNIVERSITE_OGRENCISI",0);profile.put("universityId",university);profile.put("departmentId",department);profile.put("classYear",2);mvc.perform(write("PUT","/api/me/profile",actor,profile)).andExpect(status().isOk());}
        UUID application=UUID.randomUUID();jdbc.update("INSERT INTO admin_applications(id,applicant_id,request_id,submitted_first_name,submitted_last_name,education_status,university_department_id,university_name,department_name,profile_version,status,reviewed_by,reviewed_at,university_id,department_id,cover_letter) VALUES (?,?,?,?,?,?,?,?,?,?,'APPROVED',?,CURRENT_TIMESTAMP,?,?,?)",application,tanidik.id(),UUID.randomUUID(),"Ada","Yılmaz","UNIVERSITE_OGRENCISI",UUID.fromString(program),"Üniversite","Bölüm",0,manager.id(),UUID.fromString(university),UUID.fromString(department),"Doğrulanmış deneyim paylaşımı için yeterli açıklama metnidir.");
        jdbc.update("UPDATE users SET active_verification_application_id=? WHERE id=?",application,tanidik.id());
        jdbc.update("INSERT INTO education_verifications(id,user_id,verification_type,verified_at,university_department_id) VALUES (?,?, 'MANAGER_REVIEW',CURRENT_TIMESTAMP,?)",UUID.randomUUID(),tanidik.id(),UUID.fromString(program));
        var created=mapper.readTree(mvc.perform(write("POST","/api/polls",tanidik,Map.of("universityId",university,"programId",program,"question","Kampüse ulaşımda en iyi seçenek hangisi?","options",List.of("Metro","Otobüs","Yürüyüş"),"verifiedOnly",true))).andExpect(status().isCreated()).andExpect(jsonPath("$.options.length()").value(3)).andReturn().getResponse().getContentAsString());
        String poll=created.get("id").asText(),option=created.get("options").get(0).get("id").asText();
        mvc.perform(write("PUT","/api/polls/"+poll+"/vote",member,Map.of("optionId",option))).andExpect(status().isForbidden());
        mvc.perform(write("PUT","/api/polls/"+poll+"/vote",tanidik,Map.of("optionId",option))).andExpect(status().isOk()).andExpect(jsonPath("$.totalVotes").value(1)).andExpect(jsonPath("$.verifiedVoteCount").value(1));
        mvc.perform(get("/api/polls").param("universityId",university).param("programId",program)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/tanidiklar").param("universityId",university).param("departmentId",department).param("classYear","2").param("verified","true")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].activeTanidik").value(true)).andExpect(jsonPath("$.items[0].educationVerified").value(true)).andExpect(jsonPath("$.items[0].classYear").value(2));
    }
    @Test void adminsOnlyCreateTagsAndNeedACompletedProfile()throws Exception{
        var admin=actor("TANIDIK");var manager=actor("MANAGER");
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
        assertThatThrownBy(()->jdbc.execute("TRUNCATE answer_likes, question_reports, question_likes, question_views, user_profiles,tags,management_actions, questions, question_tags, answers, question_assignments")).isInstanceOf(org.springframework.dao.DataAccessException.class);
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
        var admin=actor("TANIDIK");mvc.perform(write("PUT","/api/me/profile",admin,profile("YKS_ADAYI",0))).andExpect(status().isOk());
        var content=new com.tanidikvar.api.question.dto.QuestionContent("Admin soru oluşturamaz",null,com.tanidikvar.api.question.entity.QuestionScope.GENERAL,null,null,null,List.of());
        var request=new com.tanidikvar.api.question.dto.QuestionCreateRequest(UUID.randomUUID(),content);
        mvc.perform(write("POST","/api/questions",admin,request)).andExpect(status().isCreated());
        var second=new com.tanidikvar.api.question.dto.QuestionCreateRequest(UUID.randomUUID(),content);
        org.assertj.core.api.Assertions.assertThat(questionService.create(admin.id(),second).authorId()).isEqualTo(admin.id());
    }
    @Test void graduateEducationVerificationIsSeparateReviewedAndVersioned()throws Exception{
        var graduate=actor("MEMBER");var manager=actor("MANAGER");var ids=education(manager);String university=ids.get("universityId").asText(),department=ids.get("departmentId").asText();
        mvc.perform(write("POST","/api/manager/university-departments",manager,Map.of("universityId",university,"departmentId",department))).andExpect(status().isCreated());
        var body=profile("MEZUN",0);body.put("universityId",university);body.put("departmentId",department);body.put("graduationYear",2025);mvc.perform(write("PUT","/api/me/profile",graduate,body)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT education_status||':'||(university_id IS NOT NULL)||':'||(department_id IS NOT NULL)||':'||(deleted_at IS NULL) FROM user_profiles WHERE user_id=?",String.class,graduate.id())).isEqualTo("MEZUN:true:true:true");
        mvc.perform(write("POST","/api/me/education-verification/graduate-review",graduate,Map.of("evidence","Mezuniyet belgesi referansı ve doğrulanabilir mezun bilgileri."))).andExpect(status().isAccepted());
        var review=mapper.readTree(mvc.perform(get("/api/manager/education-verifications").cookie(manager.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].status").value("PENDING")).andReturn().getResponse().getContentAsString()).get("items").get(0);
        mvc.perform(write("PUT","/api/manager/education-verifications/"+review.get("id").asText(),manager,Map.of("status","APPROVED","reason","Mezuniyet kaydı doğrulandı.","version",review.get("version").asLong()))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM management_actions WHERE target_id=? AND action='APPROVED_EDUCATION_VERIFICATION'",Long.class,UUID.fromString(review.get("id").asText()))).isEqualTo(1);
        mvc.perform(get("/api/me/education-verification").cookie(graduate.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true)).andExpect(jsonPath("$.type").value("MANAGER_REVIEW"));
    }
    @Test void achievementShowcaseIsOwnedLimitedAndPublic()throws Exception{
        var member=actor("MEMBER");
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        jdbc.update("INSERT INTO user_achievements(id,user_id,achievement_key,title) VALUES (?,?,?,?),(?,?,?,?)",first,member.id(),"TEST_ONE","Birinci Rozet",second,member.id(),"TEST_TWO","İkinci Rozet");
        mvc.perform(get("/api/gamification/profiles/"+member.id()+"/achievements")).andExpect(status().isOk()).andExpect(jsonPath("$[0].featured").value(false));
        mvc.perform(write("PUT","/api/me/gamification/showcase",member,Map.of("achievementIds",List.of(first,second)))).andExpect(status().isOk()).andExpect(jsonPath("$[0].featured").value(true));
        mvc.perform(get("/api/gamification/profiles/"+member.id())).andExpect(status().isOk()).andExpect(jsonPath("$.badges.length()").value(2));
        mvc.perform(write("PUT","/api/me/gamification/showcase",member,Map.of("achievementIds",List.of(first,second,UUID.randomUUID(),UUID.randomUUID())))).andExpect(status().isBadRequest());
    }
    @Test void questionDiscoveryFiltersAnswerVerificationAndCity()throws Exception{
        var manager=actor("MANAGER");var asker=actor("MEMBER");var responder=actor("MEMBER");var ids=education(manager);UUID university=UUID.fromString(ids.get("universityId").asText()),department=UUID.fromString(ids.get("departmentId").asText());jdbc.update("UPDATE universities SET city='İstanbul' WHERE id=?",university);
        for(var actor:List.of(asker,responder)){var body=profile("UNIVERSITE_OGRENCISI",0);body.put("universityId",university);body.put("departmentId",department);body.put("classYear",2);mvc.perform(write("PUT","/api/me/profile",actor,body)).andExpect(status().isOk());}
        var question=mapper.readTree(mvc.perform(write("POST","/api/questions",asker,Map.of("requestId",UUID.randomUUID(),"content",Map.of("title","Şehir filtresi için cevaplanan örnek soru","scope","UNIVERSITY","universityId",university,"tagIds",List.of())))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/questions").param("city","istanbul").param("answered","false")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(write("POST","/api/questions/"+question.get("id").asText()+"/answers",responder,Map.of("body","Bu soru için yeterince ayrıntılı ve gerçek bir öğrenci cevabıdır."))).andExpect(status().isCreated());
        mvc.perform(get("/api/questions").param("city","İSTANBUL").param("answered","true").param("verifiedAnswer","false")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/questions").param("verifiedAnswer","true")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void lowQualityDuplicateAndSelfVoteDoNotAwardPointsAndCreateFraudSignals()throws Exception{
        var author=actor("MEMBER");var responder=actor("MEMBER");var profile=profile("YKS_ADAYI",0);mvc.perform(write("PUT","/api/me/profile",author,profile)).andExpect(status().isOk());mvc.perform(write("PUT","/api/me/profile",responder,profile("YKS_ADAYI",0))).andExpect(status().isOk());
        String first=mapper.readTree(mvc.perform(write("POST","/api/questions",author,Map.of("requestId",UUID.randomUUID(),"content",Map.of("title","Kalite koruması birinci test sorusu","scope","GENERAL","tagIds",List.of())))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String second=mapper.readTree(mvc.perform(write("POST","/api/questions",author,Map.of("requestId",UUID.randomUUID(),"content",Map.of("title","Kalite koruması ikinci test sorusu","scope","GENERAL","tagIds",List.of())))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(write("POST","/api/questions/"+first+"/answers",author,Map.of("body","Kısa cevap"))).andExpect(status().isCreated());
        String body="Bu yanıt yalnız sayı üretmek yerine gerçek bağlam, gerekçe ve öğrenci deneyimi sunacak kadar ayrıntılı hazırlanmıştır.";
        var answer=mapper.readTree(mvc.perform(write("POST","/api/questions/"+first+"/answers",responder,Map.of("body",body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(write("POST","/api/questions/"+second+"/answers",responder,Map.of("body",body))).andExpect(status().isCreated());
        jdbc.update("INSERT INTO answer_likes(answer_id,user_id) VALUES (?,?)",UUID.fromString(answer.get("id").asText()),responder.id());
        assertThat(jdbc.queryForObject("SELECT coalesce(sum(points),0) FROM point_events WHERE user_id=? AND event_type='ANSWER_CREATED'",Long.class,responder.id())).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gamification_fraud_signals WHERE signal_type IN ('LOW_QUALITY_ANSWER','DUPLICATE_CONTENT','SELF_VOTE')",Long.class)).isGreaterThanOrEqualTo(3);
    }
    @Test void followedContextActivitiesAchievementsAndTitlesCreatePreferenceAwareNotifications()throws Exception{
        var follower=actor("MEMBER");var contributor=actor("TANIDIK");var manager=actor("MANAGER");var ids=education(manager);UUID university=UUID.fromString(ids.get("universityId").asText());
        mvc.perform(write("PUT","/api/me/follows",follower,Map.of("targetType","UNIVERSITY","targetId",university,"active",true))).andExpect(status().isOk());
        var contributorProfile=profile("UNIVERSITE_OGRENCISI",0);contributorProfile.put("universityId",university);contributorProfile.put("departmentId",ids.get("departmentId").asText());contributorProfile.put("classYear",2);mvc.perform(write("PUT","/api/me/profile",contributor,contributorProfile)).andExpect(status().isOk());
        mvc.perform(write("PUT","/api/evaluations",contributor,Map.of("universityId",university,"rating",4,"body","Takip bildirimi için yeterli değerlendirme açıklaması."))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND notification_type='NEW_EVALUATION'",Long.class,follower.id())).isEqualTo(1);
        jdbc.update("INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version) VALUES (?,?,?,?,?,?,1)",UUID.randomUUID(),follower.id(),"TEST_TITLE",100,"TEST",UUID.randomUUID());
        jdbc.update("INSERT INTO user_achievements(id,user_id,achievement_key,title) VALUES (?,?,?,?)",UUID.randomUUID(),follower.id(),"TEST_NOTIFICATION","Bildirim Rozeti");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND notification_type='TITLE_UPGRADED'",Long.class,follower.id())).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND notification_type='ACHIEVEMENT'",Long.class,follower.id())).isGreaterThanOrEqualTo(1);
        jdbc.update("UPDATE notification_preferences SET in_app_enabled=false WHERE user_id=?",follower.id());
        mvc.perform(write("PUT","/api/evaluations",contributor,Map.of("universityId",university,"rating",5,"body","Güncellenen kayıt yeni insert üretmemeli ve bildirim tekrarlanmamalı."))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND notification_type='NEW_EVALUATION'",Long.class,follower.id())).isEqualTo(1);
    }
    @Test void careerPrivacyThresholdAndSentimentSummariesProtectContributors()throws Exception{
        var manager=actor("MANAGER");var ids=education(manager);String university=ids.get("universityId").asText(),department=ids.get("departmentId").asText();
        String program=mapper.readTree(mvc.perform(write("POST","/api/manager/university-departments",manager,Map.of("universityId",university,"departmentId",department))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        var graduates=new ArrayList<Actor>();
        for(int i=0;i<5;i++){
            var graduate=actor("TANIDIK");graduates.add(graduate);var body=profile("MEZUN",0);body.put("universityId",university);body.put("departmentId",department);body.put("graduationYear",2025);
            mvc.perform(write("PUT","/api/me/profile",graduate,body)).andExpect(status().isOk());
            mvc.perform(write("PUT","/api/career-outcomes",graduate,Map.of("universityId",university,"programId",program,"sector",i<3?"Yazılım":"Finans","firstRole","Mühendis","companyType","Özel sektör","graduateStudy",i==4,"jobSearchMonths",i+1))).andExpect(status().isNoContent());
            if(i==3)mvc.perform(get("/api/career-outcomes").param("universityId",university).param("programId",program)).andExpect(status().isOk()).andExpect(jsonPath("$.sampleSize").value(4)).andExpect(jsonPath("$.privacyThresholdMet").value(false)).andExpect(jsonPath("$.sectors").isEmpty());
        }
        mvc.perform(get("/api/career-outcomes").param("universityId",university).param("programId",program)).andExpect(status().isOk()).andExpect(jsonPath("$.sampleSize").value(5)).andExpect(jsonPath("$.privacyThresholdMet").value(true)).andExpect(jsonPath("$.sectors[0].label").value("Yazılım"));
        for(var sentiment:List.of("POSITIVE","NEGATIVE"))mvc.perform(write("POST","/api/experiences",graduates.get(0),Map.of("universityId",university,"programId",program,"templateType","WISH_I_KNEW","title",sentiment.equals("POSITIVE")?"Güçlü mezun ağı":"Yoğun proje takvimi","body","Karar verecek adaylar için ayrıntılı ve karşılaştırılabilir gerçek deneyim açıklaması.","sentiment",sentiment))).andExpect(status().isCreated());
        mvc.perform(get("/api/experience-sentiments").param("universityId",university).param("programId",program)).andExpect(status().isOk()).andExpect(jsonPath("$.positives[0].label").value("Güçlü mezun ağı")).andExpect(jsonPath("$.negatives[0].label").value("Yoğun proje takvimi"));
    }
    @Autowired com.tanidikvar.api.question.service.QuestionService questionService;
}
