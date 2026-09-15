package com.tanidikvar.api.catalog.sync.client;

import com.tanidikvar.api.catalog.service.CatalogNames;
import com.tanidikvar.api.catalog.sync.model.YokAtlasProgram;
import com.tanidikvar.api.catalog.sync.model.YokAtlasSnapshot;
import com.tanidikvar.api.catalog.sync.model.YokAtlasYearStats;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Reads the versioned programs.csv snapshot published by cngil/turkiye-university-programs. */
@Component
public class YokAtlasClient {
    private static final List<String> REQUIRED_HEADERS=List.of("source","program_code","year","level","university",
            "university_type","city","faculty","program_name","score_type","duration_years","is_kktc_quota","quota",
            "placed_count","min_placement_score","max_placement_score","last_placed_rank","placed_male","placed_female",
            "avg_secondary_score","total_preferences","demand_per_quota","avg_preference_rank","quota_general",
            "quota_school_first","quota_martyr_veteran","quota_woman_34plus","quota_earthquake");
    private final RestClient client; private final String csvUrl; private final int maxBytes;

    @Autowired
    public YokAtlasClient(RestClient.Builder builder,@Value("${app.catalog.programs-csv-url}") String csvUrl,
            @Value("${app.catalog.programs-csv-max-bytes:104857600}") int maxBytes,
            @Value("${app.catalog.programs-csv-timeout:120s}") Duration timeout) {
        this(configuredClient(builder,timeout),csvUrl,maxBytes);
    }
    YokAtlasClient(RestClient client,String csvUrl,int maxBytes) {
        if(csvUrl==null||csvUrl.isBlank()||maxBytes<1)throw new IllegalArgumentException("Programs CSV configuration is invalid");
        this.client=client;this.csvUrl=csvUrl;this.maxBytes=maxBytes;
    }
    private static RestClient configuredClient(RestClient.Builder builder,Duration timeout) {
        if(timeout.isNegative()||timeout.isZero())throw new IllegalArgumentException("Programs CSV timeout is invalid");
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(timeout);factory.setReadTimeout(timeout);
        return builder.clone().requestFactory(factory).defaultHeader("User-Agent","TanidikVar-Catalog-Sync/2.0").build();
    }

    public YokAtlasSnapshot fetchCompleteSnapshot() {
        byte[] payload=download();require(payload.length>0,"Programs CSV boş döndü; katalog değiştirilmedi.");
        require(payload.length<=maxBytes,"Programs CSV güvenli boyut sınırını aşıyor.");
        return parseSnapshot(payload);
    }
    static YokAtlasSnapshot parseSnapshot(byte[] payload) {
        List<List<String>> records=parseCsv(new String(payload,StandardCharsets.UTF_8));
        require(records.size()>1,"Programs CSV veri satırı içermiyor; katalog değiştirilmedi.");
        Map<String,Integer> columns=headers(records.getFirst());Map<String,ProgramBuilder> programs=new LinkedHashMap<>();
        for(int index=1;index<records.size();index++) {
            List<String> record=records.get(index);require(record.size()==columns.size(),"Programs CSV satır "+(index+1)+" kolon sayısı geçersiz.");
            String code=required(record,columns,"program_code",index);
            ProgramBuilder program=programs.get(code);
            if(program==null){program=new ProgramBuilder(record,columns,index);programs.put(code,program);}
            program.add(record,columns,index);
        }
        List<YokAtlasProgram> rows=programs.values().stream().filter(ProgramBuilder::isInCurrentCatalog)
                .map(ProgramBuilder::build).sorted(Comparator.comparing(YokAtlasProgram::guideCode)).toList();
        require(!rows.isEmpty(),"Programs CSV boş snapshot üretti; katalog değiştirilmedi.");
        return new YokAtlasSnapshot(sha256(payload),null,rows);
    }
    private byte[] download() {
        try {byte[] body=client.get().uri(csvUrl).retrieve().body(byte[].class);if(body==null)throw new IllegalStateException("Programs CSV boş yanıt döndürdü.");return body;}
        catch(RestClientResponseException e){throw new IllegalStateException("Programs CSV HTTP "+e.getStatusCode().value()+" döndürdü.",e);}
        catch(RestClientException e){throw new IllegalStateException("Programs CSV indirilemedi.",e);}
    }
    private static Map<String,Integer> headers(List<String> header) {
        Map<String,Integer> columns=new HashMap<>();for(int i=0;i<header.size();i++)columns.put(header.get(i),i);
        require(columns.keySet().containsAll(REQUIRED_HEADERS),"Programs CSV sözleşmesi beklenen kolonları içermiyor.");
        require(columns.size()==header.size(),"Programs CSV yinelenen kolon içeriyor.");return columns;
    }

    private static final class ProgramBuilder {
        private final String code,university,type,city,faculty,name,scoreType,level;private Integer duration;
        private final List<YokAtlasYearStats> statistics=new ArrayList<>();private final Set<Integer> years=new HashSet<>();
        ProgramBuilder(List<String> row,Map<String,Integer> c,int index) {
            code=required(row,c,"program_code",index);university=required(row,c,"university",index);type=universityType(required(row,c,"university_type",index));
            city=value(row,c,"city");faculty=value(row,c,"faculty");name=required(row,c,"program_name",index);
            scoreType=required(row,c,"score_type",index);level=degree(required(row,c,"level",index));
        }
        void add(List<String> row,Map<String,Integer> c,int index) {
            require(university.equals(required(row,c,"university",index))&&name.equals(required(row,c,"program_name",index)),"Program kimliği yıllar arasında tutarsız: "+code);
            Integer rowDuration=integer(row,c,"duration_years");if(rowDuration!=null)duration=rowDuration;
            int year=requiredInteger(row,c,"year",index);require(years.add(year),"Programs CSV yinelenen program-yıl içeriyor: "+code+"/"+year);
            String source=value(row,c,"source");String rowPayload=String.join("\u001f",row);
            statistics.add(new YokAtlasYearStats(year,integer(row,c,"quota"),integer(row,c,"placed_count"),decimal(row,c,"min_placement_score"),
                    integer(row,c,"last_placed_rank"),decimal(row,c,"max_placement_score"),integer(row,c,"placed_male"),integer(row,c,"placed_female"),
                    decimal(row,c,"avg_secondary_score"),integer(row,c,"total_preferences"),decimal(row,c,"demand_per_quota"),
                    decimal(row,c,"avg_preference_rank"),integer(row,c,"quota_general"),integer(row,c,"quota_school_first"),
                    integer(row,c,"quota_martyr_veteran"),integer(row,c,"quota_woman_34plus"),integer(row,c,"quota_earthquake"),source,
                    sha256(rowPayload.getBytes(StandardCharsets.UTF_8))));
        }
        boolean isInCurrentCatalog(){return years.contains(2025);}
        YokAtlasProgram build() {
            statistics.sort(Comparator.comparingInt(YokAtlasYearStats::year).reversed());long universityKey=key(CatalogNames.normalized(university));
            long familyKey=key(level+":"+CatalogNames.normalized(name));long unitKey=faculty==null?0:key(universityKey+":"+CatalogNames.normalized(faculty));
            return new YokAtlasProgram(null,Long.parseLong(code),code,universityKey,university,type,city,faculty==null?null:unitKey,faculty,null,city,null,
                    familyKey,name,name,level,scoreType,null,null,null,duration,List.copyOf(statistics));
        }
    }

    static List<List<String>> parseCsv(String text) {
        List<List<String>> records=new ArrayList<>();
        try(BufferedReader reader=new BufferedReader(new StringReader(text))) {
            List<String> row=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quoted=false;int value;
            while((value=reader.read())!=-1) {char ch=(char)value;if(quoted){if(ch=='"'){reader.mark(1);int next=reader.read();if(next=='"')field.append('"');else{quoted=false;if(next!=-1)reader.reset();}}else field.append(ch);}
                else if(ch=='"'&&field.isEmpty())quoted=true;else if(ch==','){row.add(field.toString());field.setLength(0);}else if(ch=='\n'){row.add(field.toString());records.add(List.copyOf(row));row.clear();field.setLength(0);}else if(ch!='\r')field.append(ch);}
            require(!quoted,"Programs CSV kapanmamış tırnak içeriyor.");if(!row.isEmpty()||!field.isEmpty()){row.add(field.toString());records.add(List.copyOf(row));}
        }catch(IOException e){throw new IllegalStateException("Programs CSV okunamadı.",e);}return records;
    }
    private static String value(List<String> row,Map<String,Integer> c,String name){String value=row.get(c.get(name)).strip();return value.isEmpty()?null:value;}
    private static String required(List<String> row,Map<String,Integer> c,String name,int index){String value=value(row,c,name);require(value!=null,"Programs CSV satır "+(index+1)+" alanı eksik: "+name);return value;}
    private static Integer integer(List<String> row,Map<String,Integer> c,String name){String value=value(row,c,name);if(value==null)return null;try{return new BigDecimal(value).intValueExact();}catch(ArithmeticException|NumberFormatException e){throw new IllegalStateException("Programs CSV sayısal alanı geçersiz: "+name);}}
    private static int requiredInteger(List<String> row,Map<String,Integer> c,String name,int index){Integer value=integer(row,c,name);require(value!=null,"Programs CSV satır "+(index+1)+" alanı eksik: "+name);return value;}
    private static BigDecimal decimal(List<String> row,Map<String,Integer> c,String name){String value=value(row,c,name);if(value==null)return null;try{return new BigDecimal(value);}catch(NumberFormatException e){throw new IllegalStateException("Programs CSV ondalık alanı geçersiz: "+name);}}
    private static String degree(String value){if("Lisans".equals(value))return "LISANS";if("Ön Lisans".equals(value))return "ONLISANS";throw new IllegalStateException("Bilinmeyen program seviyesi: "+value);}
    private static String universityType(String value){return switch(value){case "Devlet"->"DEVLET";case "Vakıf"->"VAKIF";case "KKTC"->"KKTC";case "Yurt Dışı"->"YURT_DISI";default->throw new IllegalStateException("Bilinmeyen üniversite türü: "+value);};}
    private static long key(String value){byte[] hash=digest(value.getBytes(StandardCharsets.UTF_8));long result=0;for(int i=0;i<8;i++)result=(result<<8)|(hash[i]&255L);if(result==Long.MIN_VALUE)return Long.MAX_VALUE;result=Math.abs(result);return result==0?1:result;}
    private static String sha256(byte[] value){return HexFormat.of().formatHex(digest(value));}
    private static byte[] digest(byte[] value){try{return MessageDigest.getInstance("SHA-256").digest(value);}catch(Exception e){throw new IllegalStateException("SHA-256 kullanılamıyor.",e);}}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
