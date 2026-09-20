package com.tanidikvar.api.catalog.sync.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import com.tanidikvar.api.catalog.sync.model.*;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class YokAtlasClientTest {
    private static final String RESPONSE="""
            {"content":[{"osymKilavuzId":301826,"yil":2026,"birimId":256098,"kilavuzKodu":203110477,
            "universiteId":163888,"universiteAdi":"ÖRNEK ÜNİVERSİTESİ","universiteTuru":"DEVLET","uniIlAdi":"ANKARA",
            "fymkId":258562,"fymkAdi":"MÜHENDİSLİK FAKÜLTESİ","birimGrupId":5370,"birimGrupAdi":"Bilgisayar Mühendisliği",
            "birimAdi":"Bilgisayar Mühendisliği (İngilizce)","birimTuruId":46,"puanTuru":"SAY","ogrenimSuresi":4,
            "kontenjan":50,"gkY":49,"minPuan":"450.2","basariSirasi":1000,"gk1":48,"gkY1":47,"minPuan1":"440.1","basariSirasi1":1200,
            "akreditasyon":"MÜDEK"}],
            "totalElements":1,"totalPages":1,"yil":2026}
            """;

    @Test void downloadsOfficialJsonAndExpandsHistoricalYears(){
        RestClient.Builder builder=RestClient.builder();MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        expectReferences(server,5370);
        server.expect(requestTo("https://dataset.example/api/tercih-kilavuz/search")).andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(RESPONSE,MediaType.APPLICATION_JSON));
        expectNets(server);
        var snapshot=fetch(new YokAtlasClient(builder.build(),new ObjectMapper(),"https://dataset.example/api",500));
        assertThat(snapshot.programs()).hasSize(1);var program=snapshot.programs().getFirst();
        assertThat(program.sourceProgramId()).isEqualTo(256098);assertThat(program.guideCode()).isEqualTo("203110477");
        assertThat(program.statistics()).extracting(s->s.year()).containsExactly(2026,2025);
        assertThat(program.statistics().get(1).minimumScore()).isEqualByComparingTo("440.1");
        assertThat(program.sourcePayload()).contains("akreditasyon");
        assertThat(snapshot.netStatistics()).singleElement().satisfies(net->{
            assertThat(net.year()).isEqualTo(2025);assertThat(net.tytTurkish()).isEqualByComparingTo("30.5");
        });server.verify();
    }

    @Test void rejectsChangedJsonContract(){
        RestClient.Builder builder=RestClient.builder();MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        expectReferences(server,5370);
        server.expect(requestTo("https://dataset.example/api/tercih-kilavuz/search"))
                .andRespond(withSuccess("{\"content\":[{}],\"totalElements\":1,\"totalPages\":1,\"yil\":2026}",MediaType.APPLICATION_JSON));
        assertThatThrownBy(()->fetch(new YokAtlasClient(builder.build(),new ObjectMapper(),"https://dataset.example/api",500)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JSON sozlesmesi degisti");
    }

    @Test void keepsProgramsWhenOptionalYokIdentifiersAreMissing(){
        String response=RESPONSE.replace("\"birimId\":256098,", "\"birimId\":null,")
                .replace("\"fymkId\":258562,", "\"fymkId\":0,")
                .replace("\"birimGrupId\":5370,", "\"birimGrupId\":null,");
        RestClient.Builder builder=RestClient.builder();MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        expectReferences(server,null);
        server.expect(requestTo("https://dataset.example/api/tercih-kilavuz/search"))
                .andRespond(withSuccess(response,MediaType.APPLICATION_JSON));
        expectNets(server);
        var program=fetch(new YokAtlasClient(builder.build(),new ObjectMapper(),"https://dataset.example/api",500)).programs().getFirst();
        assertThat(program.sourceProgramId()).isNull();
        assertThat(program.academicUnitId()).isNull();
        assertThat(program.programGroupId()).isGreaterThanOrEqualTo(8_000_000_000_000_000_000L);
    }

    @Test void treatsYokZeroScoreAsMissing(){
        String response=RESPONSE.replace("\"minPuan1\":\"440.1\"", "\"minPuan1\":\"0\"");
        RestClient.Builder builder=RestClient.builder();MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        expectReferences(server,5370);
        server.expect(requestTo("https://dataset.example/api/tercih-kilavuz/search"))
                .andRespond(withSuccess(response,MediaType.APPLICATION_JSON));
        expectNets(server);
        var historical=fetch(new YokAtlasClient(builder.build(),new ObjectMapper(),"https://dataset.example/api",500)).programs().getFirst().statistics().get(1);
        assertThat(historical.minimumScore()).isNull();
    }

    @Test void previewStreamsProgramPagesAndDoesNotDownloadNetDataset(){
        String first=RESPONSE.replace("\"totalElements\":1,\"totalPages\":1", "\"totalElements\":2,\"totalPages\":2");
        String second=first.replace("203110477", "203110478");
        RestClient.Builder builder=RestClient.builder();MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        expectReferences(server,5370);
        server.expect(requestTo("https://dataset.example/api/tercih-kilavuz/search")).andRespond(withSuccess(first,MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://dataset.example/api/tercih-kilavuz/search")).andRespond(withSuccess(second,MediaType.APPLICATION_JSON));
        var batches=new ArrayList<Integer>();var nets=new ArrayList<YokAtlasNetStats>();
        var result=new YokAtlasClient(builder.build(),new ObjectMapper(),"https://dataset.example/api",1)
                .fetch(rows->batches.add(rows.size()),nets::addAll,false);
        assertThat(batches).containsExactly(1,1);assertThat(result.programCount()).isEqualTo(2);
        assertThat(result.netCount()).isZero();assertThat(nets).isEmpty();server.verify();
    }

    private static void expectReferences(MockRestServiceServer server,Integer groupId){
        server.expect(requestTo("https://dataset.example/api/parameters/yil")).andRespond(withSuccess("2026",MediaType.TEXT_PLAIN));
        server.expect(requestTo("https://dataset.example/api/parameters/sonuc-aciklandi")).andRespond(withSuccess("1",MediaType.TEXT_PLAIN));
        server.expect(requestTo("https://dataset.example/api/tercih-kilavuz/universiteler"))
                .andRespond(withSuccess("[{\"universiteId\":163888}]",MediaType.APPLICATION_JSON));
        String groups=groupId==null?"[{\"birimGrupId\":9999}]":"[{\"birimGrupId\":"+groupId+"}]";
        server.expect(requestTo("https://dataset.example/api/tercih-kilavuz/universite-programlar"))
                .andRespond(withSuccess(groups,MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://dataset.example/api/tercih-kilavuz/universite-iller"))
                .andRespond(withSuccess("[{\"ilKodu\":6,\"ilAdi\":\"ANKARA\"}]",MediaType.APPLICATION_JSON));
    }

    private static YokAtlasSnapshot fetch(YokAtlasClient client){
        var programs=new ArrayList<YokAtlasProgram>();var nets=new ArrayList<YokAtlasNetStats>();
        var result=client.fetch(programs::addAll,nets::addAll,true);
        return new YokAtlasSnapshot(result.checksum(),null,programs,nets);
    }

    private static void expectNets(MockRestServiceServer server){
        server.expect(requestTo("https://dataset.example/api/netler/search")).andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"content":[{"yil":2025,"kilavuzKodu":203110477,"tabanPuan":440.1,"obp":480,
                        "katsayi":0.12,"tytTrkNet":30.5}],"totalElements":1,"totalPages":1}
                        """,MediaType.APPLICATION_JSON));
    }
}
