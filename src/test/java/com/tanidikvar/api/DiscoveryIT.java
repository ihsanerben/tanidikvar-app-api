package com.tanidikvar.api;
import java.util.*;
import java.time.*;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@ActiveProfiles("local")
@Testcontainers
@Import(DiscoveryIT.TimeConfiguration.class)
class DiscoveryIT {
    static final Instant NOW=Instant.parse("2026-09-05T12:00:00Z");
    @TestConfiguration static class TimeConfiguration {@Bean @Primary Clock fixedClock(){return Clock.fixed(NOW,ZoneOffset.UTC);}}
    @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.9-alpine");
    @DynamicPropertySource static void configuration(DynamicPropertyRegistry p){p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);p.add("app.auth.secret",()->Base64.getEncoder().encodeToString(new byte[48]));}
    @Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;@Autowired ObjectMapper mapper;
    UUID user(){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO users(id,email,password_hash,authority,email_verified_at,created_at,updated_at) VALUES (?,?,'unused-test-hash','MEMBER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,id+"@example.test");jdbc.update("INSERT INTO user_profiles(user_id,first_name,last_name,education_status) VALUES (?,'Ada','Yılmaz','YKS_ADAYI')",id);return id;}
    UUID catalog(String table,String name){UUID id=UUID.randomUUID();if(table.equals("tags"))jdbc.update("INSERT INTO tags(id,name,normalized_name,created_by) VALUES (?,?,?,?)",id,name,id.toString(),user());else jdbc.update("INSERT INTO "+table+"(id,name,normalized_name) VALUES (?,?,?)",id,name,id.toString());return id;}
    UUID education(UUID u,UUID d){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO university_departments(id,university_id,department_id) VALUES (?,?,?)",id,u,d);return id;}
    UUID question(UUID user,String title){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO questions(id,author_id,request_id,title,scope,created_at) VALUES (?,?,?,?,'GENERAL',?)",id,user,UUID.randomUUID(),title,Timestamp.from(NOW.minusSeconds(100000)));return id;}
    UUID view(UUID q,Instant time){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO question_views(opening_event_id,question_id,viewed_at) VALUES (?,?,?)",id,q,Timestamp.from(time));return id;}
    void like(UUID q,UUID u,Instant time){jdbc.update("INSERT INTO question_likes(question_id,user_id,first_liked_at) VALUES (?,?,?)",q,u,Timestamp.from(time));}
    UUID verification(UUID u,String name){UUID uni=catalog("universities","Işık Üniversitesi"),dep=catalog("departments","Bilgisayar Mühendisliği"),edu=education(uni,dep),file=UUID.randomUUID(),v=UUID.randomUUID();
        jdbc.update("INSERT INTO stored_files(id,owner_id,purpose,storage_key,original_name,content_type,byte_size,upload_status) VALUES (?,?,'VERIFICATION',?,'belge.pdf','application/pdf',10,'READY')",file,u,file.toString());
        jdbc.update("INSERT INTO admin_applications(id,applicant_id,request_id,submitted_first_name,submitted_last_name,education_status,university_department_id,university_name,department_name,document_file_id,document_sha256,profile_version,status,reviewed_by,reviewed_at) VALUES (?,?,?,?,'Yılmaz','UNIVERSITE_OGRENCISI',?,'Işık Üniversitesi','Bilgisayar Mühendisliği',?,'test-hash',0,'APPROVED',?,CURRENT_TIMESTAMP)",v,u,UUID.randomUUID(),name,edu,file,u);
        jdbc.update("UPDATE users SET authority='ADMIN',active_verification_application_id=? WHERE id=?",v,u);return v;
    }
    UUID answer(UUID q,UUID u,UUID v,Instant time){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO answers(id,question_id,author_id,answer_kind,body,published_at,verification_application_id) VALUES (?,?,?,?, 'Gerçek deneyim için test cevabı',?,?)",id,q,u,v==null?"COMMUNITY":"ADMIN",Timestamp.from(time),v);return id;}
    JsonNode list(String path,Map<String,String> params)throws Exception {var request=get(path);params.forEach(request::param);return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    List<String> ids(JsonNode page){var result=new ArrayList<String>();page.get("items").forEach(q->result.add(q.get("id").asText()));return result;}
    JsonNode popular(String query,String period)throws Exception{return list("/api/popular",Map.of("q",query,"period",period));}
    @Test void searchFoldsTurkishAndFindsTitleBodyUniversityDepartmentAndTag()throws Exception {
        UUID u=user();String group=UUID.randomUUID().toString();UUID title=question(u,group+" IŞIK kampüsü"),body=question(u,group+" Başka bir soru");jdbc.update("UPDATE questions SET body='Işık yurdu' WHERE id=?",body);
        UUID uni=catalog("universities",group+" Işık Üniversitesi"),dep=catalog("departments",group+" Çağdaş Mühendislik"),edu=education(uni,dep),q=question(u,group+" Eğitim hakkında soru");jdbc.update("UPDATE questions SET scope='UNIVERSITY_DEPARTMENT',university_department_id=? WHERE id=?",edu,q);
        UUID tag=catalog("tags",group+" Çift Anadal"),tagged=question(u,group+" Tag hakkında soru");jdbc.update("INSERT INTO question_tags(question_id,tag_id) VALUES (?,?)",tagged,tag);
        assertThat(ids(list("/api/questions",Map.of("q","isik")))).contains(title.toString(),body.toString(),q.toString());
        assertThat(ids(list("/api/questions",Map.of("q",group+" cagdas")))).containsExactly(q.toString());
        assertThat(ids(list("/api/questions",Map.of("q",group+" cift")))).containsExactly(tagged.toString());
        assertThat(ids(list("/api/universities",Map.of("q",group+" isik")))).containsExactly(uni.toString());
        assertThat(ids(list("/api/departments",Map.of("q",group+" cagdas")))).containsExactly(dep.toString());
        assertThat(ids(list("/api/universities/"+uni+"/departments",Map.of("q","cagdas")))).containsExactly(edu.toString());
        assertThat(ids(list("/api/tags",Map.of("q",group+" cift")))).containsExactly(tag.toString());
    }
    @Test void combinedFiltersAndLiteralSearchDoNotBroadenResults()throws Exception {
        UUID u=user();String group=UUID.randomUUID().toString();UUID uni=catalog("universities",group),dep=catalog("departments",group),edu=education(uni,dep),tag=catalog("tags",group),q=question(u,group+" %_ kampüs");
        jdbc.update("UPDATE questions SET scope='UNIVERSITY_DEPARTMENT',university_department_id=? WHERE id=?",edu,q);jdbc.update("INSERT INTO question_tags(question_id,tag_id) VALUES (?,?)",q,tag);
        var filters=Map.of("q",group,"universityId",uni.toString(),"departmentId",dep.toString(),"universityDepartmentId",edu.toString(),"tagId",tag.toString(),"scope","UNIVERSITY_DEPARTMENT");
        assertThat(ids(list("/api/questions",filters))).containsExactly(q.toString());
        var wrong=new HashMap<>(filters);wrong.put("departmentId",UUID.randomUUID().toString());assertThat(ids(list("/api/questions",wrong))).isEmpty();
        assertThat(ids(list("/api/questions",Map.of("q",group+" %_")))).containsExactly(q.toString());
        assertThat(ids(list("/api/questions",Map.of("q","' OR 1=1 --")))).isEmpty();
        assertThat(ids(list("/api/questions",Map.of("q","  "+group+"   %_  ")))).containsExactly(q.toString());
    }
    @Test void windowsIncludeStartExcludeEndAndCardsKeepAllTimeCounts()throws Exception {
        UUID u=user();for(var period:com.tanidikvar.api.question.dto.PopularPeriod.values()){
            String group=UUID.randomUUID().toString();UUID inside=question(u,group+" Sınır sorusu"),outside=question(u,group+" Dışarıdaki soru");Instant start=NOW.minusSeconds(period.seconds());
            view(inside,start);view(inside,start.minusMillis(1));view(outside,start.minusMillis(1));view(outside,NOW);view(outside,NOW.plusSeconds(1));
            var result=popular(group,period.name());assertThat(ids(result)).containsExactly(inside.toString());assertThat(result.get("totalElements").asInt()).isEqualTo(1);
            assertThat(result.get("items").get(0).get("statistics").get("viewCount").asLong()).isEqualTo(2);
        }
    }
    @Test void approvedWeightsRankAdminCommunityLikeAndView()throws Exception {
        UUID u=user(),v=verification(u,"Sıralama");String group=UUID.randomUUID().toString();UUID viewed=question(u,group+" Görüntülenen soru"),liked=question(u,group+" Beğenilen soru"),community=question(u,group+" Topluluk sorusu"),admin=question(u,group+" Admin sorusu");Instant time=NOW.minusSeconds(60);
        view(viewed,time);like(liked,u,time);answer(community,u,null,time);answer(admin,u,v,time);
        assertThat(ids(popular(group,"DAILY"))).containsExactly(admin.toString(),community.toString(),liked.toString(),viewed.toString());
    }
    @Test void halfWeightAtWindowStartAllowsOldQuestionWithNewActivityToRise()throws Exception {
        UUID u=user();String group=UUID.randomUUID().toString();UUID old=question(u,group+" Eski soru"),recent=question(u,group+" Yeni soru");
        jdbc.update("UPDATE questions SET created_at=? WHERE id=?",Timestamp.from(NOW.minusSeconds(86400L*400)),old);
        like(recent,u,NOW.minusSeconds(86400));for(int i=0;i<3;i++)view(old,NOW.minusSeconds(1));
        assertThat(ids(popular(group,"DAILY"))).containsExactly(old.toString(),recent.toString());
        // The exact start contributes 5/2=2.5; two fresh views contribute just below 2.
        UUID two=question(u,group+" İki görüntülenme");view(two,NOW.minusSeconds(1));view(two,NOW.minusSeconds(1));
        assertThat(ids(popular(group,"DAILY"))).containsExactly(old.toString(),recent.toString(),two.toString());
    }
    @Test void deletedInteractionsArchivedAndHiddenQuestionsAreExcluded()throws Exception {
        UUID u=user();String group=UUID.randomUUID().toString();UUID archived=question(u,group+" Arşiv"),hidden=question(u,group+" Gizli"),removed=question(u,group+" Kaldırılan"),empty=question(u,group+" Etkileşimsiz");
        view(archived,NOW.minusSeconds(1));view(hidden,NOW.minusSeconds(1));UUID event=view(removed,NOW.minusSeconds(1));like(removed,u,NOW.minusSeconds(1));UUID answer=answer(removed,u,null,NOW.minusSeconds(1));
        jdbc.update("UPDATE questions SET archived_at=CURRENT_TIMESTAMP WHERE id=?",archived);jdbc.update("UPDATE questions SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",hidden);
        jdbc.update("UPDATE question_views SET deleted_at=CURRENT_TIMESTAMP WHERE opening_event_id=?",event);jdbc.update("UPDATE question_likes SET deleted_at=CURRENT_TIMESTAMP WHERE question_id=?",removed);jdbc.update("UPDATE answers SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",answer);
        assertThat(ids(popular(group,"WEEKLY"))).isEmpty();assertThat(ids(list("/api/questions",Map.of("q",group)))).containsExactlyInAnyOrder(removed.toString(),empty.toString());
    }
    @Test void restoringAnOldLikeOrEditingAnOldAnswerDoesNotRefreshContributionTime()throws Exception {
        UUID u=user();String group=UUID.randomUUID().toString();UUID q=question(u,group+" Eski katkılar");like(q,u,NOW.minusSeconds(86400*8));UUID a=answer(q,u,null,NOW.minusSeconds(86400*8));
        jdbc.update("UPDATE question_likes SET deleted_at=CURRENT_TIMESTAMP WHERE question_id=?",q);jdbc.update("UPDATE question_likes SET deleted_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE question_id=?",q);
        jdbc.update("UPDATE answers SET body='Yeni düzenleme metni uzunluğu',edited_at=CURRENT_TIMESTAMP WHERE id=?",a);
        assertThat(ids(popular(group,"WEEKLY"))).isEmpty();assertThat(ids(popular(group,"MONTHLY"))).containsExactly(q.toString());
    }
    @Test void paginationIsStableForTiesAndInvalidInputsReturn400()throws Exception {
        UUID u=user();String group=UUID.randomUUID().toString();var expected=new ArrayList<String>();for(int i=0;i<3;i++){UUID q=question(u,group+" Eşit puan "+i);view(q,NOW.minusSeconds(1));expected.add(q.toString());}expected.sort(Comparator.reverseOrder());
        var actual=new ArrayList<String>();for(int page=0;page<3;page++){var result=list("/api/popular",Map.of("q",group,"period","DAILY","page",String.valueOf(page),"size","1"));actual.addAll(ids(result));assertThat(result.get("totalElements").asInt()).isEqualTo(3);}assertThat(actual).isEqualTo(expected);
        for(String path:List.of("/api/questions","/api/popular","/api/admins")){
            mvc.perform(get(path).param("q","x".repeat(101))).andExpect(status().isBadRequest());mvc.perform(get(path).param("page","-1")).andExpect(status().isBadRequest());mvc.perform(get(path).param("size","101")).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/popular").param("period","FOREVER")).andExpect(status().isBadRequest());mvc.perform(get("/api/questions").param("adminId","invalid")).andExpect(status().isBadRequest());
    }
    @Test void adminSearchUsesPublicVerifiedNamesAndAnsweredQuestionFilter()throws Exception {
        String group=UUID.randomUUID().toString();UUID active=user(),former=user(),unverified=user();jdbc.update("UPDATE user_profiles SET first_name=? WHERE user_id=?",group+" IŞIK",active);jdbc.update("UPDATE user_profiles SET first_name=? WHERE user_id=?",group+" Çağrı",former);UUID v=verification(active,group+" IŞIK"),old=verification(former,group+" Çağrı");jdbc.update("UPDATE users SET authority='MEMBER',active_verification_application_id=NULL WHERE id=?",former);
        UUID q=question(active,group+" Ortak cevap"),community=question(active,group+" Topluluk katkısı");answer(q,active,v,NOW.minusSeconds(1));answer(q,former,old,NOW.minusSeconds(1));answer(community,active,null,NOW.minusSeconds(1));
        var result=list("/api/admins",Map.of("q",group));assertThat(ids(result)).containsExactly(active.toString(),former.toString());assertThat(result.get("items").get(1).get("activeAdmin").asBoolean()).isFalse();
        assertThat(ids(list("/api/admins",Map.of("q",group+" isik")))).containsExactly(active.toString());
        assertThat(result.toString()).doesNotContain("email","documentFileId","storageKey","test-hash",unverified.toString());
        assertThat(ids(list("/api/questions",Map.of("adminId",active.toString())))).containsExactly(q.toString());assertThat(ids(list("/api/popular",Map.of("adminId",former.toString())))).containsExactly(q.toString());
        jdbc.update("UPDATE user_profiles SET deleted_at=CURRENT_TIMESTAMP WHERE user_id=?",active);
        assertThat(ids(list("/api/admins",Map.of("q",group)))).containsExactly(former.toString());assertThat(ids(list("/api/questions",Map.of("adminId",active.toString())))).isEmpty();
    }
    @Test void inactiveTagsAndCatalogNamesDoNotProduceTextMatches()throws Exception {
        UUID u=user();String group=UUID.randomUUID().toString();UUID uni=catalog("universities",group),tag=catalog("tags",group),q=question(u,"Referansı pasifleşen soru metni");jdbc.update("UPDATE questions SET scope='UNIVERSITY',university_id=? WHERE id=?",uni,q);jdbc.update("INSERT INTO question_tags(question_id,tag_id) VALUES (?,?)",q,tag);
        assertThat(ids(list("/api/questions",Map.of("q",group)))).containsExactly(q.toString());
        jdbc.update("UPDATE universities SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",uni);jdbc.update("UPDATE tags SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",tag);
        assertThat(ids(list("/api/questions",Map.of("q",group)))).isEmpty();
        assertThat(ids(list("/api/questions",Map.of("tagId",tag.toString())))).isEmpty();
        assertThat(list("/api/questions",Map.of("q","Referansı pasifleşen")).get("items").get(0).get("universityName").asText()).isEqualTo(group);
    }
}
