package com.tanidikvar.api.catalog.entity;
/** Table names come exclusively from this server-side allowlist. */
public enum CatalogKind {
    UNIVERSITY("universities"), DEPARTMENT("departments"), TAG("tags");
    private final String table;
    CatalogKind(String table) { this.table=table; }
    public String table() { return table; }
}
