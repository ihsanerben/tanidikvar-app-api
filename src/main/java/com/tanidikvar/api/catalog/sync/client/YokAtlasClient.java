package com.tanidikvar.api.catalog.sync.client;

import com.tanidikvar.api.catalog.sync.model.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import tools.jackson.databind.*;

/** Downloads the complete preference-guide snapshot used by the official YOK Atlas SPA. */
@Component
public class YokAtlasClient {
    private static final Set<String> REQUIRED=Set.of("yil","kilavuzKodu","universiteId","universiteAdi","birimGrupAdi","birimAdi","birimTuruId");
    private static final int MAX_OPTIONS=100_000;
    private final RestClient client;private final ObjectMapper json;private final String baseUrl,searchUrl,netSearchUrl;private final int pageSize;

    @Autowired public YokAtlasClient(RestClient.Builder builder,ObjectMapper json,
            @Value("${app.catalog.yok-atlas-base-url:https://yokatlas.yok.gov.tr/api}") String baseUrl,
            @Value("${app.catalog.yok-atlas-page-size:500}") int pageSize,
            @Value("${app.catalog.yok-atlas-timeout:120s}") Duration timeout){this(configuredClient(builder,timeout),json,baseUrl,pageSize);}
    YokAtlasClient(RestClient client,ObjectMapper json,String baseUrl,int pageSize){
        if(baseUrl==null||baseUrl.isBlank()||pageSize<1||pageSize>2_000)throw new IllegalArgumentException("YOK Atlas configuration is invalid");
        this.client=client;this.json=json;this.baseUrl=baseUrl.replaceAll("/+$","");
        this.searchUrl=this.baseUrl+"/tercih-kilavuz/search";this.netSearchUrl=this.baseUrl+"/netler/search";this.pageSize=pageSize;
    }
    private static RestClient configuredClient(RestClient.Builder builder,Duration timeout){
        if(timeout.isNegative()||timeout.isZero())throw new IllegalArgumentException("YOK Atlas timeout is invalid");
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(timeout);factory.setReadTimeout(timeout);
        return builder.clone().requestFactory(factory)
                .defaultHeader("Accept","*/*")
                .defaultHeader("Origin","https://yokatlas.yok.gov.tr")
                .defaultHeader("User-Agent","Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36")
                .build();
    }

    public YokAtlasSnapshot fetchCompleteSnapshot(){
        int activeYear=plainInt("/parameters/yil");
        plainInt("/parameters/sonuc-aciklandi");
        Set<Long> officialUniversities=idSet(getArray("/tercih-kilavuz/universiteler"),"universiteId");
        Set<Long> officialGroups=idSet(getArray("/tercih-kilavuz/universite-programlar"),"birimGrupId");
        getArray("/tercih-kilavuz/universite-iller");
        List<JsonNode> rows=new ArrayList<>();Integer expectedTotal=null,guideYear=null;
        for(int page=0;;page++){
            JsonNode response=search(page);int total=requiredInt(response,"totalElements"),pages=requiredInt(response,"totalPages"),year=requiredInt(response,"yil");
            if(total<1||total>MAX_OPTIONS||pages<1)throw changed("gecersiz toplam veya sayfa sayisi");
            if(year!=activeYear)throw changed("aktif yil ile katalog yili uyusmuyor");
            if(expectedTotal==null){expectedTotal=total;guideYear=year;}else if(expectedTotal!=total||guideYear!=year)throw changed("sayfalama sirasinda snapshot degisti");
            JsonNode content=response.get("content");if(content==null||!content.isArray())throw changed("content dizi degil");content.forEach(rows::add);
            if(page+1>=pages)break;
        }
        if(expectedTotal==null||rows.size()!=expectedTotal)throw changed("tum kayitlar indirilemedi");
        Map<String,YokAtlasProgram> programs=new LinkedHashMap<>();
        for(JsonNode row:rows){
            YokAtlasProgram program=program(row);
            if(!officialUniversities.contains(program.universityId()))throw changed("universite referans listesinde yok: "+program.universityId());
            Long group=nullableId(row,"birimGrupId");if(group!=null&&!officialGroups.contains(group))throw changed("program referans listesinde yok: "+group);
            if(programs.putIfAbsent(program.guideCode(),program)!=null)throw changed("yinelenen kilavuzKodu: "+program.guideCode());
        }
        List<YokAtlasNetStats> nets=fetchNetStatistics();
        byte[] checksumPayload=json.writeValueAsBytes(Map.of("programs",rows,"nets",nets,"year",activeYear,"schemaVersion",2));
        return new YokAtlasSnapshot(sha256(checksumPayload),null,List.copyOf(programs.values()),nets);
    }
    private JsonNode getArray(String path){
        try{JsonNode value=json.readTree(client.get().uri(baseUrl+path).retrieve().body(String.class));if(value==null||!value.isArray()||value.isEmpty())throw changed("bos referans listesi: "+path);return value;}
        catch(RestClientException e){throw new IllegalStateException("YOK Atlas referans verisi indirilemedi.",e);}
    }
    private int plainInt(String path){
        try{String value=client.get().uri(baseUrl+path).retrieve().body(String.class);return Integer.parseInt(Objects.requireNonNull(value).strip());}
        catch(RuntimeException e){throw new IllegalStateException("YOK Atlas parametresi indirilemedi: "+path,e);}
    }
    private static Set<Long> idSet(JsonNode rows,String field){Set<Long> result=new HashSet<>();rows.forEach(row->result.add(requiredLong(row,field)));return result;}
    private List<YokAtlasNetStats> fetchNetStatistics(){
        List<YokAtlasNetStats> result=new ArrayList<>();Integer expectedTotal=null;
        for(int page=0;;page++){
            JsonNode response=post(netSearchUrl,page);int total=requiredInt(response,"totalElements"),pages=requiredInt(response,"totalPages");
            if(total<1||total>300_000||pages<1)throw changed("netler toplam veya sayfa sayisi gecersiz");
            if(expectedTotal==null)expectedTotal=total;else if(expectedTotal!=total)throw changed("netler sayfalama sirasinda degisti");
            JsonNode content=response.get("content");if(content==null||!content.isArray())throw changed("netler content dizi degil");
            content.forEach(row->result.add(netStats(row)));
            if(page+1>=pages)break;
        }
        if(expectedTotal==null||result.size()!=expectedTotal)throw changed("tum net kayitlari indirilemedi");
        return List.copyOf(result);
    }
    private YokAtlasNetStats netStats(JsonNode row){
        String payload=row.toString();return new YokAtlasNetStats(requiredText(row,"kilavuzKodu"),requiredInt(row,"yil"),decimal(row,"tabanPuan"),decimal(row,"obp"),decimal(row,"katsayi"),
                decimalSigned(row,"tytTrkNet"),decimalSigned(row,"tytSosNet"),decimalSigned(row,"tytMatNet"),decimalSigned(row,"tytFenNet"),decimalSigned(row,"aytMatNet"),decimalSigned(row,"aytFizNet"),decimalSigned(row,"aytKimNet"),decimalSigned(row,"aytBioNet"),
                decimalSigned(row,"aytTdeNet"),decimalSigned(row,"aytTrh1Net"),decimalSigned(row,"aytCog1Net"),decimalSigned(row,"aytTrh2Net"),decimalSigned(row,"aytCog2Net"),decimalSigned(row,"aytFelNet"),decimalSigned(row,"aytDinNet"),decimalSigned(row,"ydtYdilNet"),sha256(payload.getBytes(StandardCharsets.UTF_8)),payload);
    }
    private JsonNode search(int page){
        return post(searchUrl,page);
    }
    private JsonNode post(String url,int page){
        try{
            String body=json.writeValueAsString(Map.of("filters",Map.of(),"page",page,"size",pageSize,"sortBy","kilavuzKodu","direction","ASC"));
            String referer=url.equals(netSearchUrl)?"https://yokatlas.yok.gov.tr/net-sihirbazi":"https://yokatlas.yok.gov.tr/";
            String response=client.post().uri(url).header("Content-Type","application/json").header("Referer",referer).body(body).retrieve().body(String.class);
            if(response==null||response.isBlank())throw new IllegalStateException("YOK Atlas bos yanit dondurdu.");return json.readTree(response);
        }catch(RestClientResponseException e){throw new IllegalStateException("YOK Atlas HTTP "+e.getStatusCode().value()+" dondurdu.",e);}
        catch(RestClientException e){throw new IllegalStateException("YOK Atlas indirilemedi.",e);}
    }
    private YokAtlasProgram program(JsonNode row){
        for(String field:REQUIRED)if(row.get(field)==null||row.get(field).isNull())throw changed("zorunlu alan eksik: "+field);
        int year=requiredInt(row,"yil");Long optionId=nullableId(row,"birimId");long universityId=requiredLong(row,"universiteId");
        String code=requiredText(row,"kilavuzKodu"),degree=switch(requiredInt(row,"birimTuruId")){case 46->"LISANS";case 47->"ONLISANS";default->throw changed("bilinmeyen birimTuruId");};
        String groupName=requiredText(row,"birimGrupAdi");Long officialGroupId=nullableId(row,"birimGrupId");
        long groupId=officialGroupId!=null?officialGroupId:syntheticGroupId(groupName,degree);
        List<YokAtlasYearStats> statistics=new ArrayList<>();statistics.add(stats(row,year,"kontenjan","gkY","minPuan","basariSirasi"));
        for(int offset=1;offset<=3;offset++){YokAtlasYearStats previous=stats(row,year-offset,"gk"+offset,"gkY"+offset,"minPuan"+offset,"basariSirasi"+offset);if(hasValues(previous))statistics.add(previous);}
        return new YokAtlasProgram(nullableId(row,"osymKilavuzId"),optionId,code,universityId,requiredText(row,"universiteAdi"),universityType(text(row,"universiteTuru")),text(row,"uniIlAdi"),
                nullableId(row,"fymkId"),text(row,"fymkAdi"),null,text(row,"ilAdi"),text(row,"ilceAdi"),groupId,groupName,requiredText(row,"birimAdi"),degree,
                text(row,"puanTuru"),text(row,"ogrenimTuruAdi"),text(row,"ogrenimDiliAdi"),text(row,"bursOraniAdi"),integer(row,"ogrenimSuresi"),List.copyOf(statistics),row.toString());
    }
    private static boolean hasValues(YokAtlasYearStats s){return s.quota()!=null||s.placed()!=null||s.minimumScore()!=null||s.successRank()!=null;}
    private YokAtlasYearStats stats(JsonNode row,int year,String quota,String placed,String score,String rank){
        String payload=year+"|"+value(row,quota)+"|"+value(row,placed)+"|"+value(row,score)+"|"+value(row,rank);
        return new YokAtlasYearStats(year,integer(row,quota),integer(row,placed),decimal(row,score),integer(row,rank),null,null,null,null,null,null,null,integer(row,quota),null,null,null,null,"YOK_ATLAS",sha256(payload.getBytes(StandardCharsets.UTF_8)));
    }
    private static String universityType(String v){return switch(v==null?"":v){case "DEVLET"->"DEVLET";case "VAKIF","VAKIF MYO"->"VAKIF";case "KKTC"->"KKTC";case "YURTDIŞI","YURT DISI","YURT_DIŞI"->"YURT_DISI";default->"BELIRTILMEMIS";};}
    private static int requiredInt(JsonNode n,String f){Integer v=integer(n,f);if(v==null)throw changed("sayisal alan eksik: "+f);return v;}
    private static long requiredLong(JsonNode n,String f){Long v=nullableLong(n,f);if(v==null||v<1)throw changed("kimlik alani gecersiz: "+f);return v;}
    private static Long nullableId(JsonNode n,String f){Long v=nullableLong(n,f);return v==null||v<1?null:v;}
    private static Long nullableLong(JsonNode n,String f){JsonNode v=n.get(f);if(v==null||v.isNull())return null;try{return v.isNumber()?v.longValue():Long.valueOf(v.asText());}catch(RuntimeException e){throw changed("sayisal alan gecersiz: "+f);}}
    private static Integer integer(JsonNode n,String f){Long v=nullableLong(n,f);if(v==null)return null;if(v<Integer.MIN_VALUE||v>Integer.MAX_VALUE)throw changed("integer alan sinir disi: "+f);return v.intValue();}
    private static BigDecimal decimal(JsonNode n,String f){JsonNode v=n.get(f);if(v==null||v.isNull()||v.asText().isBlank())return null;try{BigDecimal value=new BigDecimal(v.asText().strip());return value.signum()>0?value:null;}catch(RuntimeException e){throw changed("ondalik alan gecersiz: "+f);}}
    private static BigDecimal decimalSigned(JsonNode n,String f){JsonNode v=n.get(f);if(v==null||v.isNull()||v.asText().isBlank())return null;try{return new BigDecimal(v.asText().strip());}catch(RuntimeException e){throw changed("ondalik alan gecersiz: "+f);}}
    private static String requiredText(JsonNode n,String f){String v=text(n,f);if(v==null)throw changed("metin alani eksik: "+f);return v;}
    private static String text(JsonNode n,String f){JsonNode v=n.get(f);if(v==null||v.isNull())return null;String s=v.asText().strip();return s.isEmpty()?null:s;}
    private static String value(JsonNode n,String f){JsonNode v=n.get(f);return v==null||v.isNull()?"":v.asText();}
    private static long syntheticGroupId(String name,String degree){
        String hash=sha256((name.strip().toLowerCase(Locale.ROOT)+"|"+degree).getBytes(StandardCharsets.UTF_8));
        return 8_000_000_000_000_000_000L+Long.parseLong(hash.substring(0,15),16);
    }
    private static IllegalStateException changed(String detail){return new IllegalStateException("YOK Atlas JSON sozlesmesi degisti: "+detail+"; katalog degistirilmedi.");}
    private static String sha256(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception e){throw new IllegalStateException("SHA-256 kullanilamiyor.",e);}}
}
