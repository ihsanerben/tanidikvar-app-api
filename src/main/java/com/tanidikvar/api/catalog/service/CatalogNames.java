package com.tanidikvar.api.catalog.service;
import java.text.Normalizer;
import java.util.Locale;
import com.tanidikvar.api.common.error.DomainException;
public final class CatalogNames {
    private CatalogNames() { }
    public static String clean(String name) {
        String result=Normalizer.normalize(name,Normalizer.Form.NFKC).replaceAll("[\\s\\p{Z}]+"," ").strip();
        if(result.isEmpty() || result.length()>200) throw new DomainException(400,"VALIDATION_FAILED","Ad 1–200 karakter olmalı.",java.util.Map.of("name","Ad 1–200 karakter olmalı."));
        return result;
    }
    public static String normalized(String name) { return clean(name).toLowerCase(Locale.forLanguageTag("tr")); }
    public static String search(String query) {
        if(query==null || query.isBlank()) return "";
        if(query.length()>100) throw new DomainException(400,"INVALID_REQUEST","Arama metni en fazla 100 karakter olabilir.");
        return normalized(query);
    }
}
