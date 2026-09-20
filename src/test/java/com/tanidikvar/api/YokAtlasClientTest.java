package com.tanidikvar.api.catalog.sync.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
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
        var snapshot=new YokAtlasClient(builder.build(),new ObjectMapper(),"https://dataset.example/api",500).fetchCompleteSnapshot();
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
        assertThatThrownBy(()->new YokAtlasClient(builder.build(),new ObjectMapper(),"https://dataset.example/api",500).fetchCompleteSnapshot())
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
        var program=new YokAtlasClient(builder.build(),new ObjectMapper(),"https://dataset.example/api",500)
                .fetchCompleteSnapshot().programs().getFirst();
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
        var historical=new YokAtlasClient(builder.build(),new ObjectMapper(),"https://dataset.example/api",500)
                .fetchCompleteSnapshot().programs().getFirst().statistics().get(1);
        assertThat(historical.minimumScore()).isNull();
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

    private static void expectNets(MockRestServiceServer server){
        server.expect(requestTo("https://dataset.example/api/netler/search")).andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"content":[{"yil":2025,"kilavuzKodu":203110477,"tabanPuan":440.1,"obp":480,
                        "katsayi":0.12,"tytTrkNet":30.5}],"totalElements":1,"totalPages":1}
                        """,MediaType.APPLICATION_JSON));
    }
}
