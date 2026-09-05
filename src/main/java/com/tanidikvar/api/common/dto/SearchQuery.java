package com.tanidikvar.api.common.dto;
import com.tanidikvar.api.common.error.DomainException;
public final class SearchQuery {
    private SearchQuery() { }
    public static String clean(String value) {
        if(value==null)return "";
        if(value.length()>100)throw new DomainException(400,"INVALID_REQUEST","Arama en fazla 100 karakter olabilir.");
        return value.replaceAll("(?U)\\s+"," ").strip();
    }
    public static void page(int page,int size) {
        if(page<0||page>10000||size<1||size>100)throw new DomainException(400,"INVALID_REQUEST","Sayfa sınırlarını kontrol et.");
    }
}
