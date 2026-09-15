package com.tanidikvar.api.catalog.sync.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class YokAtlasClientTest {
    private static final String HEADER="source,program_code,year,level,university,university_type,city,faculty,program_name,score_type,duration_years,is_kktc_quota,quota,placed_count,min_placement_score,max_placement_score,last_placed_rank,placed_male,placed_female,avg_secondary_score,total_preferences,demand_per_quota,avg_preference_rank,quota_general,quota_school_first,quota_martyr_veteran,quota_woman_34plus,quota_earthquake\n";

    @Test void downloadsCsvAndGroupsProgramYears() {
        RestClient.Builder builder=RestClient.builder();MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://dataset.example/programs.csv")).andRespond(withSuccess(HEADER+
                "yokatlas,100110018,2025,Lisans,ÖRNEK ÜNİVERSİTESİ,Devlet,ANKARA,Mühendislik Fakültesi,\"Bilgisayar, Yazılım ve Sistemler\",SAY,4,false,50,49,400.1,520.2,12000,30,19,450.2,1000,20.0,3.2,45,2,1,1,1\n"+
                "izcir,100110018,2023,Lisans,ÖRNEK ÜNİVERSİTESİ,Devlet,ANKARA,Mühendislik Fakültesi,\"Bilgisayar, Yazılım ve Sistemler\",SAY,,,45,44,390.1,,14000,,,,,,,,,,,\n",MediaType.TEXT_PLAIN));
        var snapshot=new YokAtlasClient(builder.build(),"https://dataset.example/programs.csv",1_000_000).fetchCompleteSnapshot();
        assertThat(snapshot.programs()).hasSize(1);assertThat(snapshot.programs().getFirst().statistics()).hasSize(2);
        assertThat(snapshot.programs().getFirst().statistics().getFirst().maximumScore()).isEqualByComparingTo("520.2");
        assertThat(snapshot.programs().getFirst().displayName()).isEqualTo("Bilgisayar, Yazılım ve Sistemler");server.verify();
    }

    @Test void rejectsChangedCsvContract() {
        RestClient.Builder builder=RestClient.builder();MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://dataset.example/programs.csv")).andRespond(withSuccess("program_code,year\n1,2025\n",MediaType.TEXT_PLAIN));
        assertThatThrownBy(()->new YokAtlasClient(builder.build(),"https://dataset.example/programs.csv",1000).fetchCompleteSnapshot())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("beklenen kolonları");
    }
}
