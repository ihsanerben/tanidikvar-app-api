package com.tanidikvar.api.common.error;
import java.util.Map;
public class DomainException extends RuntimeException {
    private final int status;
    private final String code;
    private final Map<String,String> fields;
    public DomainException(int status, String code, String message) { this(status, code, message, Map.of()); }
    public DomainException(int status, String code, String message, Map<String,String> fields) {
        super(message); this.status=status; this.code=code; this.fields=Map.copyOf(fields);
    }
    public int status() { return status; }
    public String code() { return code; }
    public Map<String,String> fields() { return fields; }
}
